package thebetweenlands.common.world.biome.spawning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import gnu.trove.map.hash.TObjectIntHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import thebetweenlands.api.entity.spawning.IBiomeSpawnEntriesData;
import thebetweenlands.api.entity.spawning.ICustomSpawnEntriesProvider;
import thebetweenlands.api.entity.spawning.ICustomSpawnEntry;
import thebetweenlands.common.config.BetweenlandsConfig;
import thebetweenlands.common.lib.ModInfo;
import thebetweenlands.util.WeightedList;

public abstract class AreaMobSpawner {
	@Nullable
	protected Predicate<EntityLivingBase> entityCountFilter = null;

	protected boolean strictDynamicLimit = true;

	/**
	 * Sets whether the dynamic limit is strict, i.e. enforced and not
	 * just approximated by randomness and weight.
	 * @param strict
	 */
	public void setStrictDynamicLimit(boolean strict) {
		this.strictDynamicLimit = strict;
	}

	/**
	 * Sets the entity count filter. The entity count filter determines which
	 * entities are supposed to be counted towards the entity count.
	 * @param filter
	 */
	public void setEntityCountFilter(@Nullable Predicate<EntityLivingBase> filter) {
		this.entityCountFilter = filter;
	}

	/**
	 * Returns whether the specified position is within this area mob spawner's area
	 * @param world
	 * @param pos
	 * @param entityCount
	 * @return
	 */
	public boolean isInsideSpawningArea(World world, BlockPos pos, boolean entityCount) {
		return entityCount || world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 24D, false) == null;
	}

	/**
	 * How many attempts to reach the desired mob group size
	 * @return
	 */
	public int getSpawningAttemptsPerGroup() {
		return 24;
	}

	/**
	 * How many attempts per spawning run
	 * @return
	 */
	public int getSpawningAttempsPerChunk() {
		return 8;
	}

	/**
	 * Maximum spawns per spawning run
	 * @return
	 */
	public int getMaxSpawnsPerChunk() {
		return 6;
	}

	/**
	 * Total world entity limit
	 */
	public int getHardEntityLimit() {
		return BetweenlandsConfig.MOB_SPAWNING.hardEntityLimit;
	}

	/**
	 * Maximum entities per chunk as a fraction
	 * @return
	 */
	public abstract float getMaxEntitiesPerSpawnChunkFraction(int spawnerChunks);

	/**
	 * Returns the approximate number of loaded (player) areas
	 * @return
	 */
	public float getLoadedAreasCount(int spawnerChunks) {
		return 1.0f;
	}

	/**
	 * Returns a list of all spawn entries at the specified position
	 * @param world
	 * @param pos
	 * @param provider
	 * @return
	 */
	public List<ICustomSpawnEntry> getSpawnEntries(World world, BlockPos pos, @Nullable ICustomSpawnEntriesProvider provider) {
		return provider != null ? provider.getCustomSpawnEntries() : Collections.emptyList();
	}

	/**
	 * Returns the spawn entry data, such as spawning cooldowns etc.
	 * @param world
	 * @param pos
	 * @param provider
	 * @return
	 */
	@Nullable
	public IBiomeSpawnEntriesData getSpawnEntriesData(World world, BlockPos pos, @Nullable ICustomSpawnEntriesProvider provider) {
		return null;
	}

	/**
	 * Default spawning weight is 100
	 */
	public static class BLSpawnEntry implements ICustomSpawnEntry {
		private final Class<? extends EntityLiving> entityType;
		private final Function<World, ? extends EntityLiving> entityCtor;
		private final short baseWeight;
		private short weight;
		private boolean hostile = false;
		private int subChunkLimit = -1;
		private int chunkLimit = -1;
		private int worldLimit = -1;
		private int minGroupSize = 1, maxGroupSize = 1;
		private double spawnCheckRadius = 16.0D;
		private double spawnCheckRangeY = 6.0D;
		private double groupSpawnRadius = 6.0D;
		private int spawningInterval = 0;

		/**
		 * The ID is used to save the spawn entry data such as last spawn time. A negative ID means that no data will be saved
		 */
		public final ResourceLocation id;

		public BLSpawnEntry(Class<? extends EntityLiving> entityType, Function<World, ? extends EntityLiving> entityCtor) {
			this(-1, entityType, entityCtor, (short) 100);
		}

		public BLSpawnEntry(Class<? extends EntityLiving> entityType, Function<World, ? extends EntityLiving> entityCtor, short weight) {
			this(-1, entityType, entityCtor, weight);
		}

		public BLSpawnEntry(int id, Class<? extends EntityLiving> entityType, Function<World, ? extends EntityLiving> entityCtor) {
			this(id, entityType, entityCtor, (short) 100);
		}

		public BLSpawnEntry(int id, Class<? extends EntityLiving> entityType, Function<World, ? extends EntityLiving> entityCtor, short weight) {
			this.id = new ResourceLocation(ModInfo.ID, String.valueOf(id));
			this.entityType = entityType;
			this.entityCtor = entityCtor;
			this.weight = weight;
			this.baseWeight = weight;
		}

		@Override
		public ResourceLocation getID() {
			return this.id;
		}

		@Override
		public boolean isSaved() {
			return !"-1".equals(this.id.getResourcePath());
		}

		@Override
		public boolean canSpawn(World world, Chunk chunk, BlockPos pos, IBlockState blockState, IBlockState surfaceBlockState) {
			return !blockState.isNormalCube() && surfaceBlockState.isNormalCube();
		}

		@Override
		public void update(World world, BlockPos pos) { }

		@Override
		public final short getWeight() {
			return this.weight;
		}

		@Override
		public final BLSpawnEntry setWeight(short weight) {
			this.weight = weight;
			return this;
		}

		@Override
		public final BLSpawnEntry setSpawningInterval(int interval) {
			this.spawningInterval = interval;
			return this;
		}

		@Override
		public final int getSpawningInterval() {
			return this.spawningInterval;
		}

		@Override
		public final short getBaseWeight() {
			return this.baseWeight;
		}

		@Override
		public final BLSpawnEntry setGroupSize(int min, int max) {
			if(max < min) {
				throw new RuntimeException("Maximum group size cannot be smaller than minimum group size!");
			}
			this.minGroupSize = min;
			this.maxGroupSize = max;
			return this;
		}

		@Override
		public final int getMaxGroupSize() {
			return this.maxGroupSize;
		}

		@Override
		public final int getMinGroupSize() {
			return this.minGroupSize;
		}

		@Override
		public final int getChunkLimit() {
			return this.chunkLimit;
		}

		@Override
		public final BLSpawnEntry setChunkLimit(int limit) {
			this.chunkLimit = limit;
			return this;
		}

		@Override
		public final int getWorldLimit() {
			return this.worldLimit;
		}

		@Override
		public final BLSpawnEntry setWorldLimit(int limit) {
			this.worldLimit = limit;
			return this;
		}

		@Override
		public final int getSubChunkLimit() {
			return this.subChunkLimit;
		}

		@Override
		public final BLSpawnEntry setSubChunkLimit(int limit) {
			this.subChunkLimit = limit;
			return this;
		}

		@Override
		public final BLSpawnEntry setHostile(boolean hostile) {
			this.hostile = hostile;
			return this;
		}

		@Override
		public final boolean isHostile() {
			return this.hostile;
		}

		@Override
		public final BLSpawnEntry setSpawnCheckRadius(double radius) {
			this.spawnCheckRadius = radius;
			return this;
		}

		@Override
		public final double getSpawnCheckRadius() {
			return this.spawnCheckRadius;
		}

		@Override
		public final BLSpawnEntry setSpawnCheckRangeY(double y) {
			this.spawnCheckRangeY = y;
			return this;
		}

		@Override
		public final double getSpawnCheckRangeY() {
			return this.spawnCheckRangeY;
		}

		@Override
		public final BLSpawnEntry setGroupSpawnRadius(double radius) {
			this.groupSpawnRadius = radius;
			return this;
		}

		@Override
		public final double getGroupSpawnRadius() {
			return this.groupSpawnRadius;
		}

		@Override
		public boolean shouldCheckExistingGroups() {
			return true;
		}

		@Override
		public EntityLiving createEntity(World world) {
			try {
				return this.entityCtor.apply(world);
			} catch(Exception ex) {
				ex.printStackTrace();
			}
			return null;
		}

		@Override
		public final Class<? extends EntityLiving> getEntityType() {
			return this.entityType;
		}

		public final Function<World, ? extends EntityLiving> getEntityCtor() {
			return this.entityCtor;
		}

		@Override
		public void onSpawned(EntityLivingBase entity) { }
	}

	public void populate(WorldServer world, boolean spawnHostiles, boolean spawnAnimals) {
		int totalWorldEntityCount = 0;
		for(Entity entity : (List<Entity>)world.loadedEntityList) {
			if(entity instanceof EntityLivingBase) {
				totalWorldEntityCount++;
			}
		}

		if(totalWorldEntityCount >= this.getHardEntityLimit()) {
			//Hard limit reached, don't spawn any more entities
			return;
		}

		this.updateSpawnerChunks(world, this.eligibleChunksForSpawning);

		if(this.eligibleChunksForSpawning.isEmpty()) {
			//No spawning chunks
			return;
		}

		List<ChunkPos> spawnerChunks = new ArrayList<ChunkPos>(this.eligibleChunksForSpawning.size());

		//Add valid chunks
		for(ChunkPos chunkPos : this.eligibleChunksForSpawning) {
			//Don't load chunks
			if(world.isBlockLoaded(new BlockPos(chunkPos.x * 16, 64, chunkPos.z * 16))) {
				spawnerChunks.add(chunkPos);
			}
		}

		this.updateEntityCounts(world, this.entityCounts);
		int totalEligibleEntityCount = 0;
		for(int count : this.entityCounts.values()) {
			totalEligibleEntityCount += count;
		}

		int maxEntitiesForLoadedArea = Math.min(this.getHardEntityLimit(), (int) (spawnerChunks.size() * this.getMaxEntitiesPerSpawnChunkFraction(spawnerChunks.size())));

		if(totalEligibleEntityCount >= maxEntitiesForLoadedArea) {
			//Too many entities, don't spawn any more entities
			return;
		}

		Collections.shuffle(spawnerChunks);

		//The approximate number of loaded areas (one area is the area loaded by one player)
		float loadedAreas = Math.max(1.0f, this.getLoadedAreasCount(spawnerChunks.size()));

		for(ChunkPos chunkPos : spawnerChunks) {
			this.populateChunk(world, chunkPos, spawnHostiles, spawnAnimals, true, false, 
					this.getSpawningAttempsPerChunk(), this.getMaxSpawnsPerChunk(), this.getSpawningAttemptsPerGroup(), maxEntitiesForLoadedArea, loadedAreas);
		}
	}

	public int populateChunk(World world, ChunkPos chunkPos, boolean spawnHostiles, boolean spawnAnimals, boolean loadChunks, boolean ignoreRestrictions,
			int attemptsPerChunk, int maxSpawnsPerChunk, int attemptsPerGroup, int entityLimit, float loadedAreas) {
		loadedAreas = Math.max(1.0f, loadedAreas);

		int attempts = 0, chunkSpawnedEntities = 0;

		spawnLoop:
			while(attempts < attemptsPerChunk && chunkSpawnedEntities < maxSpawnsPerChunk) {
				attempts++;
				BlockPos spawnPos = this.getRandomSpawnPosition(world, chunkPos);

				if(!this.isInsideSpawningArea(world, spawnPos, false)) {
					continue;
				}

				Biome biome = world.getBiome(spawnPos);

				int totalBaseWeight = 0;
				int totalWeight = 0;

				//Get possible spawn entries and update weights
				List<ICustomSpawnEntry> possibleSpawns = new ArrayList<>();
				possibleSpawns.addAll(this.getSpawnEntries(world, spawnPos, biome instanceof ICustomSpawnEntriesProvider ? (ICustomSpawnEntriesProvider) biome : null));

				Iterator<ICustomSpawnEntry> spawnEntriesIT = possibleSpawns.iterator();
				while(spawnEntriesIT.hasNext()) {
					ICustomSpawnEntry spawnEntry = spawnEntriesIT.next();

					if((spawnEntry.isHostile() && !spawnHostiles) || (!spawnEntry.isHostile() && !spawnAnimals)) {
						spawnEntriesIT.remove();
						continue;
					}

					spawnEntry.update(world, spawnPos);
					totalBaseWeight += spawnEntry.getBaseWeight();
					totalWeight += spawnEntry.getWeight();
				}

				if(possibleSpawns.isEmpty() || totalWeight == 0 || totalBaseWeight == 0) {
					continue;
				}

				WeightedList<ICustomSpawnEntry> weightedPossibleSpawns = new WeightedList<>();
				weightedPossibleSpawns.addAll(possibleSpawns);
				weightedPossibleSpawns.recalculateWeight();

				ICustomSpawnEntry spawnEntry = weightedPossibleSpawns.getRandomItem(world.rand);
				if(spawnEntry == null) {
					continue;
				}

				int dynamicLimitBase = MathHelper.ceil((double)entityLimit / (double)totalBaseWeight * spawnEntry.getBaseWeight());
				int dynamicLimit = MathHelper.ceil((double)entityLimit / (double)totalWeight * spawnEntry.getWeight());

				int spawnEntityCount = this.entityCounts.get(spawnEntry.getEntityType());

				int spawnEntityCountLimit = this.strictDynamicLimit ? Math.min(dynamicLimit, dynamicLimitBase) : Math.max(dynamicLimit, dynamicLimitBase);

				if(spawnEntityCount >= spawnEntityCountLimit || (spawnEntry.getWorldLimit() >= 0 && spawnEntityCount >= spawnEntry.getWorldLimit())) {
					//Entity reached world spawning limit
					continue;
				}

				int desiredGroupSize = spawnEntry.getMinGroupSize() + world.rand.nextInt(spawnEntry.getMaxGroupSize() - spawnEntry.getMinGroupSize() + 1);
				double groupCheckRadius = spawnEntry.getSpawnCheckRadius();
				//Check whether chunks are loaded in the check radius, prevents entities from spawning somewhere even though the group limit was already reached in an unloaded chunk
				int csx = MathHelper.floor(spawnPos.getX() - groupCheckRadius) >> 4;
				int cex = MathHelper.floor(spawnPos.getX() + groupCheckRadius) >> 4;
				int csz = MathHelper.floor(spawnPos.getZ() - groupCheckRadius) >> 4;
				int cez = MathHelper.floor(spawnPos.getZ() + groupCheckRadius) >> 4;
				for (int cx = csx; cx <= cex; ++cx) {
					for (int cz = csz; cz <= cez; ++cz) {
						if(world.getChunkProvider().getLoadedChunk(cx, cz) == null && (cx != chunkPos.x || cz != chunkPos.z)) {
							continue spawnLoop;
						}
					}
				}
				double groupSpawnRadius = spawnEntry.getGroupSpawnRadius();
				Class<? extends Entity> entityType = spawnEntry.getEntityType();
				boolean checkExistingGroups = spawnEntry.shouldCheckExistingGroups();

				if(checkExistingGroups) {
					List<Entity> foundGroupEntities = world.getEntitiesWithinAABB(entityType, new AxisAlignedBB(
							spawnPos.getX() - groupCheckRadius, spawnPos.getY() - spawnEntry.getSpawnCheckRangeY(), spawnPos.getZ() - groupCheckRadius, 
							spawnPos.getX() + groupCheckRadius, spawnPos.getY() + spawnEntry.getSpawnCheckRangeY(), spawnPos.getZ() + groupCheckRadius));
					for(Entity foundGroupEntity : foundGroupEntities) {
						if(foundGroupEntity.getDistance(spawnPos.getX(), foundGroupEntity.posY + (spawnPos.getY() - foundGroupEntity.posY) / spawnEntry.getSpawnCheckRangeY() * groupCheckRadius, spawnPos.getZ()) <= groupCheckRadius) {
							desiredGroupSize--;
						}
					}
				}

				if(desiredGroupSize > 0) {
					int groupSpawnedEntities = 0, groupSpawnAttempts = 0;
					int maxGroupSpawnAttempts = attemptsPerGroup + desiredGroupSize * 2;

					IBiomeSpawnEntriesData spawnEntriesData = this.getSpawnEntriesData(world, spawnPos, biome instanceof ICustomSpawnEntriesProvider ? (ICustomSpawnEntriesProvider) biome : null);
					long lastSpawn = spawnEntriesData != null ? spawnEntriesData.getLastSpawn(spawnEntry) : -1;

					if(!ignoreRestrictions && lastSpawn >= 0) {
						//Adjust intervals for MP when there are multiple players and the loaded area is bigger -> smaller intervals
						int adjustedInterval = (int)(spawnEntry.getSpawningInterval() / loadedAreas);
						if(spawnEntriesData != null && world.getTotalWorldTime() - lastSpawn < adjustedInterval) {
							//Too early, don't spawn yet
							continue;
						}
					}

					IEntityLivingData groupData = null;

					EntityLiving cachedEntity = null;

					while(groupSpawnAttempts++ < maxGroupSpawnAttempts && groupSpawnedEntities < desiredGroupSize) {
						BlockPos entitySpawnPos = this.getRandomSpawnPosition(world, spawnPos, MathHelper.floor(groupSpawnRadius));

						boolean inChunk = (entitySpawnPos.getX() >> 4) == chunkPos.x && (entitySpawnPos.getZ() >> 4) == chunkPos.z;

						if(!loadChunks && !inChunk) {
							continue;
						}

						if(!this.isInsideSpawningArea(world, entitySpawnPos, false)) {
							continue;
						}

						IBlockState spawnBlockState = world.getBlockState(entitySpawnPos);

						int spawnSegmentY = entitySpawnPos.getY() / 16;
						Chunk spawnChunk = world.getChunkFromBlockCoords(entitySpawnPos);
						ClassInheritanceMultiMap<Entity>[] entityLists = spawnChunk.getEntityLists();
						int chunkEntityCount = 0;
						for(int l = 0; l < entityLists.length; l++) {
							int subChunkEntityCount = 0;
							for(Entity entity : entityLists[l]) {
								if(entity.getClass() == spawnEntry.getEntityType()) {
									subChunkEntityCount++;
									chunkEntityCount++;
								}
							}
							if(l == spawnSegmentY) {
								if(spawnEntry.getSubChunkLimit() >= 0 && subChunkEntityCount >= spawnEntry.getSubChunkLimit()) {
									//Entity reached sub chunk limit
									continue;
								}
							}
						}

						if(spawnEntry.getChunkLimit() >= 0 && chunkEntityCount >= spawnEntry.getChunkLimit()) {
							//Entity reached chunk limit
							continue;
						}

						IBlockState surfaceBlockState = spawnChunk.getBlockState(entitySpawnPos.getX() - spawnChunk.x * 16, entitySpawnPos.getY() - 1, entitySpawnPos.getZ() - spawnChunk.z * 16);

						if(spawnEntry.canSpawn(world, spawnChunk, entitySpawnPos, spawnBlockState, surfaceBlockState)) {
							double sx = entitySpawnPos.getX() + 0.5D;
							double sy = entitySpawnPos.getY();
							double sz = entitySpawnPos.getZ() + 0.5D;
							float yaw = world.rand.nextFloat() * 360.0F;

							EntityLiving spawningEntity;

							//If a a previous attempt created an entity but it was not used then we
							//can reuse it for this attempt, since the spawnEntry doesn't change during group spawning
							if(cachedEntity != null) {
								spawningEntity = cachedEntity;
							} else {
								spawningEntity = cachedEntity = spawnEntry.createEntity(world);
							}

							if(spawningEntity != null) {
								spawningEntity.setLocationAndAngles(sx, sy, sz, yaw, 0.0F);

								Result canSpawn = ForgeEventFactory.canEntitySpawn(spawningEntity, world, (float)sx, (float)sy, (float)sz, null);
								if (canSpawn == Result.ALLOW || (canSpawn == Result.DEFAULT && spawningEntity.getCanSpawnHere() && spawningEntity.isNotColliding())) {
									NBTTagCompound entityNBT = spawningEntity.getEntityData();
									entityNBT.setBoolean("naturallySpawned", true);

									if (!ForgeEventFactory.doSpecialSpawn(spawningEntity, world, (float)sx, (float)sy, (float)sz, null)) {
										groupData = spawningEntity.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(sx, sy, sz)), groupData);
									}

									if(spawningEntity.isNotColliding()) {
										groupSpawnedEntities++;
										chunkSpawnedEntities++;

										world.spawnEntity(spawningEntity);

										spawnEntry.onSpawned(spawningEntity);

										if(this.isCountedEntity(world, spawningEntity)) {
											this.entityCounts.adjustOrPutValue(spawningEntity.getClass(), 1, 1);
										}

										//Entity was spawned so it can't be reused!
										cachedEntity = null;
									} else if(cachedEntity != null) {
										//Cached entity was onInitialSpawned but not spawned so it can't be reused and must be killed.
										cachedEntity.setDead();
										cachedEntity = null;
									}

									if (groupSpawnedEntities >= ForgeEventFactory.getMaxSpawnPackSize(spawningEntity))  {
										break;
									}
								}
							}
						}
					}

					if(cachedEntity != null) {
						cachedEntity.setDead();
					}

					if(spawnEntriesData != null && !ignoreRestrictions && groupSpawnedEntities > 0) {
						spawnEntriesData.setLastSpawn(spawnEntry, world.getTotalWorldTime());
					}
				}
			}
		return chunkSpawnedEntities;
	}

	/**
	 * Generates a random position to potentially spawn a mob at
	 * @param world
	 * @param chunkPos
	 * @return
	 */
	protected BlockPos getRandomSpawnPosition(World world, ChunkPos chunkPos) {
		Chunk chunk = world.getChunkFromChunkCoords(chunkPos.x, chunkPos.z);
		int x = chunkPos.x * 16 + world.rand.nextInt(16);
		int z = chunkPos.z * 16 + world.rand.nextInt(16);
		int y = Math.min(world.rand.nextInt(chunk == null ? world.getActualHeight() : chunk.getTopFilledSegment() + 16 - 1), 256);
		return new BlockPos(x, y, z);
	}

	/**
	 * Generates a random offset position from a group spawn position
	 * @param world
	 * @param centerPos
	 * @param radius
	 * @return
	 */
	protected BlockPos getRandomSpawnPosition(World world, BlockPos centerPos, int radius) {
		return new BlockPos(
				centerPos.getX() + world.rand.nextInt(radius*2) - radius,
				MathHelper.clamp(centerPos.getY() + world.rand.nextInt(4) - 2, 1, world.getHeight()),
				centerPos.getZ() + world.rand.nextInt(radius*2) - radius);
	}

	private final Set<ChunkPos> eligibleChunksForSpawning = new HashSet<>();

	/**
	 * Finds all chunks that are eligible for mob spawning and updates the specified set accordingly
	 * @param world
	 * @param spawnerChunks
	 */
	protected abstract void updateSpawnerChunks(WorldServer world, Set<ChunkPos> spawnerChunks);

	private final TObjectIntHashMap<Class<? extends Entity>> entityCounts = new TObjectIntHashMap<Class<? extends Entity>>();

	/**
	 * Updates the entity counts of the spawner chunks
	 * @param world
	 * @param entityCounts
	 */
	protected void updateEntityCounts(World world, TObjectIntHashMap<Class<? extends Entity>> entityCounts) {
		entityCounts.clear();

		for(ChunkPos chunkPos : this.eligibleChunksForSpawning) {
			if(world.getChunkProvider().getLoadedChunk(chunkPos.x, chunkPos.z) != null) {
				Chunk chunk = world.getChunkFromChunkCoords(chunkPos.x, chunkPos.z);
				ClassInheritanceMultiMap<Entity>[] entityLists = chunk.getEntityLists();

				for(ClassInheritanceMultiMap<Entity> entityList : entityLists) {
					for(Entity entity : entityList) {
						if(this.isCountedEntity(world, entity)) {
							entityCounts.adjustOrPutValue(entity.getClass(), 1, 1);
						}
					}
				}
			}
		}
	}

	private boolean isCountedEntity(World world, Entity entity) {
		return entity instanceof EntityLivingBase && this.isInsideSpawningArea(world, entity.getPosition(), true) && (this.entityCountFilter == null || this.entityCountFilter.test((EntityLivingBase) entity));
	}
}
