package thebetweenlands.common.entity.mobs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.common.base.Optional;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thebetweenlands.api.entity.IBLBoss;
import thebetweenlands.api.entity.IEntityMusic;
import thebetweenlands.api.entity.IEntityPreventUnmount;
import thebetweenlands.api.entity.IEntityScreenShake;
import thebetweenlands.api.entity.spawning.IWeightProvider;
import thebetweenlands.client.audio.EntityMusicLayers;
import thebetweenlands.client.audio.EntitySound;
import thebetweenlands.client.render.particle.BLParticles;
import thebetweenlands.client.render.particle.BatchedParticleRenderer;
import thebetweenlands.client.render.particle.DefaultParticleBatches;
import thebetweenlands.client.render.particle.ParticleFactory;
import thebetweenlands.client.render.particle.entity.ParticleGasCloud;
import thebetweenlands.common.entity.EntityTinyWormEggSac;
import thebetweenlands.common.entity.ai.EntityAIHurtByTargetImproved;
import thebetweenlands.common.entity.projectiles.EntitySludgeBall;
import thebetweenlands.common.registries.LootTableRegistry;
import thebetweenlands.common.registries.SoundRegistry;
import thebetweenlands.common.sound.BLSoundEvent;
import thebetweenlands.common.world.storage.BetweenlandsWorldStorage;
import thebetweenlands.common.world.storage.location.EnumLocationType;
import thebetweenlands.common.world.storage.location.LocationSludgeWormDungeon;
import thebetweenlands.common.world.storage.location.LocationStorage;
import thebetweenlands.util.WeightedList;

public class EntitySludgeMenace extends EntityWallLivingRoot implements IEntityScreenShake, IBLBoss, IEntityMusic, IEntityPreventUnmount {
	protected static final byte EVENT_START_ACTION = 90;
	protected static final byte EVENT_SLAM_HIT = 91;

	protected static final byte EVENT_ADD_LEECH_BULGE = 92;
	protected static final byte EVENT_ADD_EGG_SAC_BULGE = 93;
	protected static final byte EVENT_ADD_LARGE_SLUDGE_WORM_BULGE = 94;
	protected static final byte EVENT_ADD_SLUDGE_BALL_SERIES_BULGE = 95;
	protected static final byte EVENT_ADD_SLUDGE_BALL_BULGE = 96;


	public int renderedFrame = -1;

	protected static final DataParameter<Optional<UUID>> BOSSINFO_ID = EntityDataManager.createKey(EntitySludgeMenace.class, DataSerializers.OPTIONAL_UNIQUE_ID);
	protected static final DataParameter<Integer> ACTION_STATE = EntityDataManager.createKey(EntitySludgeMenace.class, DataSerializers.VARINT);

	public static enum ActionState {
		IDLE, SLAM, POKE, SWING, SPIT_MOBS, STUNNED, DEATH;
	}

	private DummyPart[] dummies;

	private AxisAlignedBB renderBoundingBox = new AxisAlignedBB(0, 0, 0, 0, 0, 0);

	protected int actionTimer = 0;
	protected ActionState actionState = ActionState.IDLE;

	protected Vec3d actionTargetPos = null;

	protected int screenShakeTimer = 0;

	protected int hitCounter = 0;
	protected float damageCounter = 0.0f;

	public static enum BulgeType implements IWeightProvider {
		LEECH(EntityLeech.class, 2, 25, 1, 0.01f, 0.8f, 1.0f, EVENT_ADD_LEECH_BULGE),
		EGG_SAC(EntityTinyWormEggSac.class, 3, 15, 1, 0.01f, 0.9f, 1.0f, EVENT_ADD_EGG_SAC_BULGE),
		LARGE_SLUDGE_WORM(EntityLargeSludgeWorm.class, 1, 10, 1, 0.01f, 1.2f, 1.6f, EVENT_ADD_LARGE_SLUDGE_WORM_BULGE),
		SLUDGE_BALL_SERIES(EntitySludgeBall.class, 6, 50, 12, 0.07f, 0.7f, 1.0f, EVENT_ADD_SLUDGE_BALL_SERIES_BULGE),
		SLUDGE_BALL(EntitySludgeBall.class, 6, 30, 1, 0.01f, 0.7f, 1.0f, EVENT_ADD_SLUDGE_BALL_BULGE);

		public final Class<? extends Entity> entityType;
		public final int maxEntityCount;
		public final short spawnWeight;
		public final int spawnSeries;
		public final float speed;
		public final float size;
		public final float length;
		public final byte eventId;

		private BulgeType(Class<? extends Entity> entityType, int maxEntityCount, int spawnWeight, int spawnSeries, float speed, float size, float length, byte eventId) {
			this.entityType = entityType;
			this.maxEntityCount = maxEntityCount;
			this.spawnWeight = (short) spawnWeight;
			this.spawnSeries = spawnSeries;
			this.speed = speed;
			this.size = size;
			this.length = length;
			this.eventId = eventId;
		}

		public static BulgeType byEventId(byte eventId) {
			for(BulgeType type : BulgeType.values()) {
				if(type.eventId == eventId) {
					return type;
				}
			}
			return null;
		}

		@Override
		public short getWeight() {
			return this.spawnWeight;
		}
	}

	public static class Bulge {
		public float renderPosition;
		public float renderSize;

		public BulgeType type;
		protected float prevSize;
		protected float size;
		protected float prevPosition;
		protected float position;
	}

	protected List<Bulge> bulges = new ArrayList<>();
	protected int bulgeSpawnCooldown = 0;
	protected int spawnSeriesCount = 0;
	protected BulgeType spawnSeriesType = null;

	public static class DummyPart extends EntityMultipartDummy implements IEntityPreventUnmount {
		public DummyPart(World world) {
			super(world);
		}

		public DummyPart(World world, MultiPartEntityPart parent) {
			super(world, parent);
		}

		@Override
		public double getMountedYOffset() {
			return -0.4D;
		}

		@Override
		public boolean shouldRiderSit() {
			return false;
		}

		@Override
		public String getName() {
			return getParent() != null ? getParent().getName(): super.getName();
		}

		@Override
		public boolean isUnmountBlocked(EntityPlayer rider) {
			return this.getParent() != null && this.getParent().parent instanceof IEntityPreventUnmount ? ((IEntityPreventUnmount) this.getParent().parent).isUnmountBlocked(rider) : false;
		}
	}

	private final BossInfoServer bossInfo = (BossInfoServer)(new BossInfoServer(this.getDisplayName(), BossInfo.Color.RED, BossInfo.Overlay.PROGRESS)).setDarkenSky(false);

	private int deathTicks = 0;
	private Vec3d deathSpazzMotion = Vec3d.ZERO;

	@SideOnly(Side.CLIENT)
	private ISound livingSound;

	public EntitySludgeMenace(World world) {
		super(world);
		this.dummies = new DummyPart[this.getParts().length];
	}

	@Override
	public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, IEntityLivingData livingdata) {
		this.setPositionToAnchor(new BlockPos(this).down(), EnumFacing.UP, EnumFacing.NORTH);
		return super.onInitialSpawn(difficulty, livingdata);
	}

	@Override
	protected void entityInit() {
		super.entityInit();
		this.dataManager.register(ACTION_STATE, ActionState.IDLE.ordinal());
		this.dataManager.register(BOSSINFO_ID, Optional.absent());
	}

	@Override
	protected void initEntityAI() {
		this.targetTasks.addTask(0, new EntityAIHurtByTargetImproved(this, true) {
			@Override
			protected double getTargetDistance() {
				return 8.0D;
			}
		});
		this.targetTasks.addTask(1, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, 0, true, false, null).setUnseenMemoryTicks(120));

		this.tasks.addTask(1, new AISludgeMenaceArmAttack(this));
		this.tasks.addTask(2, new AIAction(this, 10, 40));
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();

		this.getEntityAttribute(MAX_ARM_LENGTH).setBaseValue(9);
		this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(600.0D);
		this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(6);
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound nbt) {
		super.readEntityFromNBT(nbt);

		if(this.hasCustomName()) {
			this.bossInfo.setName(this.getDisplayName());
		}

		this.deathTicks = nbt.getInteger("deathTicks");
	}

	@Override
	public void writeEntityToNBT(NBTTagCompound nbt) {
		super.writeEntityToNBT(nbt);

		nbt.setInteger("deathTicks", this.deathTicks);
	}

	@Override
	public void setCustomNameTag(String name) {
		super.setCustomNameTag(name);
		this.bossInfo.setName(this.getDisplayName());
	}

	@Override
	public void addTrackingPlayer(EntityPlayerMP player) {
		super.addTrackingPlayer(player);
		this.bossInfo.addPlayer(player);
	}

	@Override
	public void removeTrackingPlayer(EntityPlayerMP player) {
		super.removeTrackingPlayer(player);
		this.bossInfo.removePlayer(player);
	}

	@Override
	protected void updateAITasks() {
		super.updateAITasks();
		this.bossInfo.setPercent(this.getHealth() / this.getMaxHealth());
	}

	@Override
	protected float[][] getArmCrossSection() {
		float width = this.getFullArmWidth();
		return new float[][] {
			{-width, width},
			{-width, -width},
			{width, -width},
			{width, width}
		};
	}

	@Override
	protected float getNodeSize(int node) {
		return 0.2f + node / (float)this.getNumSegments() * 0.75f;
	}

	@Override
	protected int getNumSegments() {
		return 16;
	}

	@Override
	protected float getFullArmWidth() {
		return 0.55F;
	}

	@Override
	protected float getArmLengthSlack() {
		return 0.25f;
	}

	@Override
	public void playLivingSound() {
		if(this.world.isRemote) {
			this.playLivingSoundLoop();
		}
	}

	@SideOnly(Side.CLIENT)
	private void playLivingSoundLoop() {
		if(this.livingSound != null && !Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(this.livingSound)) {
			this.livingSound = null;
		}

		if(this.livingSound == null) {
			this.livingSound = new EntitySound<EntitySludgeMenace>(SoundRegistry.SLUDGE_MENACE_LIVING, SoundCategory.HOSTILE, this, e -> e.isEntityAlive());
		}

		if(!Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(this.livingSound)) {
			Minecraft.getMinecraft().getSoundHandler().playSound(this.livingSound);
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundRegistry.SLUDGE_MENACE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundRegistry.SLUDGE_MENACE_DEATH;
	}

	@Override
	protected void playHurtSound(DamageSource source) {
		this.livingSoundTime = -this.getTalkInterval();

		SoundEvent soundevent = this.getHurtSound(source);

		if (soundevent != null) {
			this.playSound(soundevent, this.getSoundVolume(), this.getSoundPitch());
		}
	}

	@Override
	protected ResourceLocation getLootTable() {
		return LootTableRegistry.SLUDGE_MENACE;
	}

	@Override
	protected boolean isValidBlockForMovement(BlockPos pos, IBlockState state) {
		return state.isOpaqueCube() && state.isNormalCube() && state.isFullCube() && state.getBlockHardness(this.world, pos) > 0;
	}

	@Override
	protected boolean isTravelBlocked() {
		//Don't travel unless obstructed and not in sludgeon
		return this.isMovementBlocked() || this.checkAnchorHere(AnchorChecks.BLOCKS) == 0 ||
				!BetweenlandsWorldStorage.forWorld(this.world).getLocalStorageHandler().getLocalStorages(LocationSludgeWormDungeon.class, this.getEntityBoundingBox(), loc -> loc.isInside(this)).isEmpty();
	}

	@Override
	public float getArmSize(float partialTicks) {
		if(this.ticksExisted < 10) {
			return (float) Math.pow((this.ticksExisted + partialTicks) / 10.0f, 6.0f);
		} else if(!this.isEntityAlive()) {
			return (float) Math.max(0, 1 - Math.pow(this.deathTicks / 130.0f, 8.0f));
		}
		return super.getArmSize(partialTicks);
	}

	@Override
	protected boolean canDespawn() {
		return false;
	}

	@Override
	public boolean isNonBoss() {
		return false;
	}

	@Override
	public void onLivingUpdate() {
		super.onLivingUpdate();

		if (!this.world.isRemote) {
			this.dataManager.set(BOSSINFO_ID, Optional.of(this.bossInfo.getUniqueId()));
		}
	}

	@Override
	public void onUpdate() {
		if(this.world.isRemote) {
			this.actionState = ActionState.values()[this.dataManager.get(ACTION_STATE)];
		}

		if(this.actionState != ActionState.IDLE) {
			this.actionTimer++;
		} else {
			this.actionTimer = 0;
		}

		Vec3d armGravity = new Vec3d(0, 0.5f, 0);

		if(this.actionState == ActionState.SLAM) {
			armGravity = Vec3d.ZERO;
		} else if(this.actionState == ActionState.POKE) {
			armGravity = new Vec3d(0, 0.8f, 0);
		} else if(this.actionState == ActionState.STUNNED) {
			float str = 0.2f;
			armGravity = new Vec3d((this.world.rand.nextFloat() - 0.5f) * str, (this.world.rand.nextFloat() - 0.5f) * str, (this.world.rand.nextFloat() - 0.5f) * str);
		}

		int segmentIndex = 0;
		for(ArmSegment segment : this.armSegments) {
			segment.motion = armGravity.scale(1 - segmentIndex / (float)this.armSegments.size());
			segmentIndex++;
		}

		if(this.screenShakeTimer > 0) {
			this.screenShakeTimer--;
		}

		//Spawn animation
		if(this.ticksExisted < 10) {
			Vec3d straightPos = this.getPositionVector().add(new Vec3d(this.getFacing().getFrontOffsetX(), this.getFacing().getFrontOffsetY(), this.getFacing().getFrontOffsetZ()).scale(this.getArmSize(1) * (this.getEntityAttribute(MAX_ARM_LENGTH).getAttributeValue() + 1)));
			this.rootTip.setPosition(straightPos.x, straightPos.y, straightPos.z);
		}

		super.onUpdate();

		Entity[] parts = this.getParts();

		this.updateBulges(parts);

		if(this.world.isRemote) {
			for(int i = 0; i < parts.length; i++) {
				if(i == 0) {
					this.renderBoundingBox = parts[i].getEntityBoundingBox();
				} else {
					this.renderBoundingBox = this.renderBoundingBox.union(parts[i].getEntityBoundingBox());
				}
			}

			this.spawnTipParticles();
		}

		if(!this.world.isRemote && this.isEntityAlive()) {
			//Heal when no player is nearby
			if(this.ticksExisted % 12 == 0 && this.getHealth() < this.getMaxHealth()) {
				final double rangeXZ = 14;
				final double rangeY = 13;

				List<EntityLivingBase> nearbyPlayers = this.world.getEntitiesWithinAABB(EntityPlayer.class, this.getEntityBoundingBox().grow(rangeXZ, rangeXZ, rangeXZ));

				Iterator<EntityLivingBase> it = nearbyPlayers.iterator();
				while(it.hasNext()) {
					EntityLivingBase living = it.next();
					if(living.getDistance(this.posX, living.posY, this.posZ) > rangeXZ || Math.abs(living.posY - this.posY) > rangeY)
						it.remove();
				}

				if(nearbyPlayers.isEmpty()) {
					this.heal(1);
				}
			}

			for(int i = 0; i < this.dummies.length; i++) {
				DummyPart dummy = this.dummies[i];

				if(dummy == null || !dummy.isEntityAlive()) {
					Entity multipart = parts[i];

					if(multipart instanceof MultiPartEntityPart) {
						this.dummies[i] = dummy = new DummyPart(this.world, (MultiPartEntityPart) multipart);
						this.world.spawnEntity(dummy);
					}
				} else {
					dummy.updatePositioning();
				}
			}
		}
	}

	protected void updateBulges(Entity[] parts) {
		Iterator<Bulge> it = this.bulges.iterator();
		while(it.hasNext()) {
			Bulge bulge = it.next();

			bulge.prevPosition = bulge.position;
			bulge.prevSize = bulge.size;

			bulge.position += bulge.type.speed;

			if(bulge.position >= 0.95f) {
				bulge.size -= 0.1f;

				if(bulge.size < 0.0f) {
					bulge.size = 0.0f;
				}
			}

			if(bulge.position >= 1.0f) {
				bulge.position = 1.0f;

				it.remove();

				if(!this.world.isRemote && parts.length >= 2) {
					Entity mob;

					switch(bulge.type) {
					default:
					case LEECH:
						mob = new EntityLeech(this.world);
						break;
					case EGG_SAC:
						mob = new EntityTinyWormEggSac(this.world);
						break;
					case LARGE_SLUDGE_WORM:
						mob = new EntityLargeSludgeWorm(this.world);
						break;
					case SLUDGE_BALL:
					case SLUDGE_BALL_SERIES:
						mob = new EntitySludgeBall(this.world, this, false);
						break;
					}

					Vec3d dir = parts[parts.length - 1].getPositionVector().subtract(parts[parts.length - 2].getPositionVector()).normalize();

					float spawnOffset = -0.5f;

					mob.setPosition(this.rootTip.posX + dir.x * spawnOffset, this.rootTip.posY + dir.y * spawnOffset, this.rootTip.posZ + dir.z * spawnOffset);

					float speed = 0.85f;

					mob.motionX = dir.x * speed;
					mob.motionY = dir.y * speed;
					mob.motionZ = dir.z * speed;

					if(mob instanceof EntityLeech) {
						EntityLivingBase target = this.getAttackTarget();

						if(target != null) {
							((EntityLeech) mob).setAttackTarget(target);
						}
					}

					this.world.spawnEntity(mob);

					this.playSound(SoundRegistry.SLUDGE_MENACE_SPIT, 1, 1);
				}
			}
		}
	}

	@Override
	protected void onDeathUpdate() {
		this.bossInfo.setPercent(0);

		++this.deathTicks;

		if(!this.world.isRemote) {
			if(this.actionState != ActionState.DEATH) {
				this.startAction(ActionState.DEATH);
			}

			if (this.deathTicks > 100 && this.deathTicks % 5 == 0) {
				int xp = 800;
				while (xp > 0) {
					int dropXP = EntityXPOrb.getXPSplit(xp);
					xp -= dropXP;
					this.world.spawnEntity(new EntityXPOrb(this.world, this.posX, this.posY + this.height / 2.0D, this.posZ, dropXP));
				}
			}

			if(this.deathTicks > 130) {
				int xp = 3000;
				while (xp > 0) {
					int dropXP = EntityXPOrb.getXPSplit(xp);
					xp -= dropXP;
					this.world.spawnEntity(new EntityXPOrb(this.world, this.posX, this.posY + this.height / 2.0D, this.posZ, dropXP));
				}

				List<LocationStorage> locations = LocationStorage.getLocations(this.world, this.getPositionVector().addVector((double) 0, (double) 2, (double) 0));
				for(LocationStorage location : locations) {
					if(location.getType() == EnumLocationType.SLUDGE_WORM_DUNGEON) {
						//Remove block guard
						if(location.getGuard() != null) {
							location.getGuard().clear(this.world);
							location.setDirty(true);
						}

						//Set location to defeated to remove ground fog and mob spawns
						if(location instanceof LocationSludgeWormDungeon) {
							((LocationSludgeWormDungeon) location).setDefeated(true);
						}
					}
				}

				this.setDead();
			}
		}
	}

	@SideOnly(Side.CLIENT)
	protected void spawnTipParticles() {
		Entity[] parts = this.getParts();

		if(parts.length > 2) {
			Entity lastPart = parts[parts.length - 1];
			Entity secondLastPart = parts[parts.length - 2];

			Vec3d dir = lastPart.getPositionVector().subtract(secondLastPart.getPositionVector()).normalize().addVector((double) ((this.rand.nextFloat() - 0.5f) * 0.5f), (double) 0, (double) ((this.rand.nextFloat() - 0.5f) * 0.5f)).scale(0.1D);

			double x = lastPart.posX;
			double y = lastPart.posY + lastPart.height / 2;
			double z = lastPart.posZ;

			double mx = dir.x;
			double my = dir.y;
			double mz = dir.z;

			int[] color = {100, 70, 0, 255};

			ParticleGasCloud hazeParticle = (ParticleGasCloud) BLParticles.GAS_CLOUD
					.create(this.world, x, y, z, ParticleFactory.ParticleArgs.get()
							.withData(null)
							.withMotion(mx, my, mz)
							.withColor(color[0] / 255.0F, color[1] / 255.0F, color[2] / 255.0F, color[3] / 255.0F)
							.withScale(3.5f));

			BatchedParticleRenderer.INSTANCE.addParticle(DefaultParticleBatches.GAS_CLOUDS_HEAT_HAZE, hazeParticle);

			ParticleGasCloud particle = (ParticleGasCloud) BLParticles.GAS_CLOUD
					.create(this.world, x, y, z, ParticleFactory.ParticleArgs.get()
							.withData(null)
							.withMotion(mx, my, mz)
							.withColor(color[0] / 255.0F, color[1] / 255.0F, color[2] / 255.0F, color[3] / 255.0F)
							.withScale(2.5f));

			BatchedParticleRenderer.INSTANCE.addParticle(DefaultParticleBatches.GAS_CLOUDS_TEXTURED, particle);
		}
	}

	@Override
	public boolean attackEntityAsMob(Entity target) {
		boolean damaged = super.attackEntityAsMob(target);

		this.playSound(SoundRegistry.SLUDGE_MENACE_ATTACK, 1, 1);

		if(this.actionState == ActionState.POKE) {
			if(target == this.getAttackTarget() && this.actionTimer >= 40) {
				DummyPart endDummy = this.dummies[this.dummies.length - 1];

				if(endDummy != null && endDummy.isEntityAlive()) {
					List<Entity> passengers = endDummy.getPassengers();

					if(!passengers.isEmpty() && passengers.get(0) != this.getAttackTarget()) {
						endDummy.removePassengers();
					}

					if(target.getRidingEntity() != endDummy) {
						target.dismountRidingEntity();
						target.startRiding(endDummy, true);
					}
				}

				if(damaged) {
					this.heal((float) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getBaseValue() * 1.5f);
				}
			}
		} else if(this.actionState == ActionState.SWING) {
			if(!damaged && target instanceof EntityLivingBase && ((EntityLivingBase) target).isActiveItemStackBlocking() && this.world.rand.nextInt(3) == 0) {
				this.startAction(ActionState.STUNNED);
			} else if(damaged && target instanceof EntityLivingBase) {
				float dx = (float)(this.rootTip.posX - this.posX);
				float dz = (float)(this.rootTip.posZ - this.posZ);

				float len = MathHelper.sqrt(dx*dx + dz*dz);

				dx /= len;
				dz /= len;

				((EntityLivingBase) target).knockBack(this, 1.75f, dz, -dx);
			}
		}

		return damaged;
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float amount) {
		if(source.isProjectile() || source.isExplosion() || source.isFireDamage()) {
			return false;
		}

		if(super.attackEntityFrom(source, amount)) {
			if(!this.world.isRemote && this.actionState == ActionState.POKE) {
				this.damageCounter += amount;
				this.hitCounter++;

				if(this.damageCounter >= 15 || this.hitCounter >= 6) {
					DummyPart endDummy = this.dummies[this.dummies.length - 1];

					if(endDummy != null && !endDummy.getPassengers().isEmpty()) {
						this.startAction(ActionState.IDLE);
						endDummy.removePassengers();
					}
				}
			}

			return true;
		}

		return false;
	}

	@Override
	protected Vec3d updateTargetTipPos(Vec3d armStartWorld, float maxArmLength, Vec3d dirFwd, Vec3d dirUp) {
		switch(this.actionState) {
		default:
		case DEATH:
			return this.updateDeathTipPos(armStartWorld, maxArmLength, dirFwd, dirUp);
		case IDLE:
			return this.updateIdleTargetTipPos(armStartWorld, maxArmLength, dirFwd, dirUp);
		case SLAM:
			return this.updateSlamTargetTipPos(armStartWorld, maxArmLength, dirFwd, dirUp);
		case POKE:
			return this.updatePokeTargetTipPos(armStartWorld, maxArmLength, dirFwd, dirUp);
		case SWING:
			return this.updateSwingTargetTipPos(armStartWorld, maxArmLength, dirFwd, dirUp);
		case SPIT_MOBS:
			return this.updateSpitMobsTipPos(armStartWorld, maxArmLength, dirFwd, dirUp);
		case STUNNED:
			return this.updateStunnedTipPos(armStartWorld, maxArmLength, dirFwd, dirUp);
		}
	}

	protected Vec3d updateDeathTipPos(Vec3d armStartWorld, float maxArmLength, Vec3d dirFwd, Vec3d dirUp) {
		if(this.world.rand.nextInt(2) == 0) {
			float vel = 2.0f + this.world.rand.nextFloat() * 4.0f;
			this.deathSpazzMotion = new Vec3d((this.world.rand.nextFloat() - 0.5f) * vel, (this.world.rand.nextFloat() - 0.5f) * vel + 1.25f, (this.world.rand.nextFloat() - 0.5f) * vel);
		}

		return this.rootTip.getPositionVector().add(this.deathSpazzMotion);
	}

	protected Vec3d updateStunnedTipPos(Vec3d armStartWorld, float maxArmLength, Vec3d dirFwd, Vec3d dirUp) {
		if(this.actionTimer >= 150) {
			this.actionState = ActionState.IDLE;
		}

		Vec3d moveDir = this.getPositionVector().subtract(this.rootTip.getPositionVector()).normalize().scale(0.02f);

		float str = 0.2f;

		return this.rootTip.getPositionVector().addVector((this.world.rand.nextFloat() - 0.5f) * str + moveDir.x, (double) ((this.world.rand.nextFloat() - 0.5f) * str + 0.01f), (this.world.rand.nextFloat() - 0.5f) * str + moveDir.z);
	}

	protected Vec3d updateSpitMobsTipPos(Vec3d armStartWorld, float maxArmLength, Vec3d dirFwd, Vec3d dirUp) {
		if(this.actionTimer < 200 && this.bulgeSpawnCooldown-- <= 0) {
			if(this.spawnSeriesType == null) {
				Set<BulgeType> availableTypes = new HashSet<>();

				AxisAlignedBB checkAabb = this.getEntityBoundingBox().grow(12, 1, 12).expand(0, 11, 0);

				TObjectIntMap<BulgeType> bulgeCounts = new TObjectIntHashMap<>();
				for(Bulge bulge : this.bulges) {
					bulgeCounts.increment(bulge.type);
				}

				for(BulgeType bulgeType : BulgeType.values()) {
					if(this.world.getEntitiesWithinAABB(bulgeType.entityType, checkAabb).size() + bulgeCounts.get(bulgeType) < bulgeType.maxEntityCount && (bulgeType.spawnSeries <= 1 || this.bulges.isEmpty())) {
						availableTypes.add(bulgeType);
					}
				}

				if(!availableTypes.isEmpty()) {
					WeightedList<BulgeType> list = new WeightedList<>();

					list.addAll(availableTypes);
					list.recalculateWeight();

					BulgeType bulgeType = list.getRandomItem(this.rand);

					this.addBulge(bulgeType);

					if(bulgeType.spawnSeries > 1) {
						this.spawnSeriesCount = bulgeType.spawnSeries;
						this.spawnSeriesType = bulgeType;
					}
				}
			} else {
				this.addBulge(this.spawnSeriesType);

				this.spawnSeriesCount--;

				if(this.spawnSeriesCount <= 0) {
					this.spawnSeriesCount = 0;
					this.spawnSeriesType = null;
				}
			}

			if(this.spawnSeriesType == null) {
				this.bulgeSpawnCooldown = 15 + this.world.rand.nextInt(15);
			} else {
				this.bulgeSpawnCooldown = 4;
			}
		}

		if(this.actionTimer >= 200 && this.bulges.isEmpty()) {
			this.startAction(ActionState.IDLE);
		}

		float furthestPos = 0.0f;
		Bulge nextBulge = null;

		for(Bulge bulge : this.bulges) {
			if(bulge.position > furthestPos) {
				furthestPos = bulge.position;
				nextBulge = bulge;
			}
		}

		this.armMovementTicks++;

		Vec3d targetTipPos = armStartWorld.add(dirFwd.scale(maxArmLength + 1.0f));

		Entity target = this.getAttackTarget();

		if(nextBulge != null && nextBulge.type == BulgeType.LARGE_SLUDGE_WORM) {
			float rx = (float) Math.cos(this.ticksExisted * 0.05f);
			float rz = (float) Math.sin(this.ticksExisted * 0.05f);

			targetTipPos = armStartWorld.add(dirFwd.scale(maxArmLength / 2)).addVector((double) (rx * 2), (double) 0, (double) (rz * 2));
		} else if(nextBulge != null && nextBulge.type == BulgeType.LEECH && target != null) {
			Vec3d bendPos = armStartWorld.add(dirFwd.scale(maxArmLength));

			Vec3d targetDir = target.getPositionVector().subtract(bendPos).normalize().scale(5);

			targetTipPos = bendPos.add(targetDir);
		} else {
			float idleX = MathHelper.cos(this.armMovementTicks / 9.0f) * 0.75F;
			float idleY = MathHelper.sin(this.armMovementTicks / 7.0f) * 0.75F;
			float idleZ = (MathHelper.cos(this.armMovementTicks / 15.0f) + 1) * 0.25f;

			float forwardPos = (float) dirFwd.dotProduct(targetTipPos.subtract(armStartWorld));
			float offsetZ = 0.0f;
			if(forwardPos < 1.0F) {
				offsetZ = 1.0F - forwardPos;
			}

			//Idle movement
			targetTipPos = targetTipPos.add(dirUp.scale(idleY)).add(dirFwd.crossProduct(dirUp).scale(idleX)).add(dirFwd.scale(offsetZ - idleZ));
		}

		Vec3d tipPos = this.rootTip.getPositionVector();

		Vec3d diff = targetTipPos.subtract(tipPos);

		targetTipPos = tipPos.add(diff.normalize().scale(Math.min(diff.lengthVector(), 0.5D)));

		return targetTipPos;
	}

	protected Vec3d updateSwingTargetTipPos(Vec3d armStartWorld, float maxArmLength, Vec3d dirFwd, Vec3d dirUp) {
		float prevDrive = (this.actionTimer - 1) * 0.07f * (1.0f + (this.actionTimer - 1) / 300.0f * 2.0f);
		float drive = this.actionTimer * 0.07f * (1.0f + this.actionTimer / 300.0f * 2.0f);

		float prevDriveMod = prevDrive % ((float)Math.PI * 2);
		float driveMod = drive % ((float)Math.PI * 2);

		if(prevDriveMod <= 1f && driveMod > 1f) {
			this.playSound(SoundRegistry.SLUDGE_MENACE_ATTACK, 1, 1);
		}

		if(this.actionTimer >= 300) {
			this.startAction(ActionState.IDLE);
		}

		float swingLength = maxArmLength + 0.2f;

		Vec3d targetTipPos = armStartWorld.addVector(Math.cos(drive) * swingLength, 1.8F + (Math.sin(drive * 0.28f) + 1) * 2.2f, Math.sin(drive) * swingLength);

		Vec3d tipPos = this.rootTip.getPositionVector();

		Vec3d diff = targetTipPos.subtract(tipPos);

		targetTipPos = tipPos.add(diff.normalize().scale(Math.min(diff.lengthVector(), 0.5D + this.actionTimer / 300.0f * 2.5D)));

		return targetTipPos;
	}

	protected Vec3d updatePokeTargetTipPos(Vec3d armStartWorld, float maxArmLength, Vec3d dirFwd, Vec3d dirUp) {
		boolean isSucking = false;

		DummyPart endDummy = this.dummies[this.dummies.length - 1];

		if(endDummy != null) {
			List<Entity> passengers = endDummy.getPassengers();

			if(passengers.isEmpty() || passengers.get(0) != this.getAttackTarget()) {
				if(this.actionTimer >= 70) {
					this.startAction(ActionState.IDLE);
				}
			} else if(!passengers.isEmpty() && passengers.get(0) == this.getAttackTarget()) {
				isSucking = true;
			}
		} else if(this.actionTimer >= 70) {
			this.startAction(ActionState.IDLE);
		}

		if(isSucking) {
			if(endDummy != null && endDummy.getPassengers().get(0) == this.getAttackTarget() && this.getAttackTarget().getHealth() <= 4.0f) {
				this.startAction(ActionState.IDLE);
				endDummy.removePassengers();
			}

			this.armMovementTicks++;

			float idleX = MathHelper.cos(this.armMovementTicks / 4.0f) * 0.75F;
			float idleY = MathHelper.sin(this.armMovementTicks / 3.0f) * 0.75F;
			float idleZ = (MathHelper.cos(this.armMovementTicks / 8.0f) + 1) * 0.25f;

			return this.actionTargetPos.addVector((double) idleX, (double) (idleY + 1.5f), (double) idleZ);
		} else {
			this.damageCounter = 0.0f;
			this.hitCounter = 0;

			float drive = this.actionTimer;

			if(drive < 43 && this.getAttackTarget() != null) {
				this.actionTargetPos = this.getAttackTarget().getPositionVector();
			}

			if(drive < 40) {
				Vec3d bendPos = armStartWorld.add(dirFwd.scale(maxArmLength));

				Vec3d targetDir = new Vec3d(3, 0, 0);

				if(this.getAttackTarget() != null) {
					targetDir = this.getAttackTarget().getPositionVector().subtract(bendPos).normalize().scale(5);
				}

				return bendPos.add(targetDir);
			} else if(drive < 48) {
				float speed = (drive - 40) / 8.0f * 4.0f;

				Vec3d targetTipPos = armStartWorld.add(dirFwd.scale(maxArmLength));

				if(this.actionTargetPos != null) {
					targetTipPos = this.actionTargetPos.addVector((double) 0, (double) (this.getAttackTarget() != null ? this.getAttackTarget().height / 2 : 0), (double) 0);
				}

				Vec3d tipPos = this.rootTip.getPositionVector();

				Vec3d tipDiff = targetTipPos.subtract(tipPos);
				targetTipPos = tipPos.add(tipDiff.normalize().scale(Math.min(tipDiff.lengthVector(), speed)));

				return targetTipPos;
			}
		}

		return this.rootTip.getPositionVector();
	}

	protected Vec3d updateSlamTargetTipPos(Vec3d armStartWorld, float maxArmLength, Vec3d dirFwd, Vec3d dirUp) {
		float drive = this.actionTimer;

		if(drive < 58 && this.getAttackTarget() != null) {
			this.actionTargetPos = this.getAttackTarget().getPositionVector();
		}

		float rot;
		if(drive < 20) {
			rot = (float)Math.PI / 2;
		} else if(drive < 60) {
			float slap = (drive - 20) / 40.0f;
			float s = 6;
			float a = 6;
			rot = (float)Math.pow(slap, a + slap * s) * (float)(Math.PI / 2) + (float)Math.PI / 2;
		} else {
			rot = (float)Math.PI;
		}

		Vec3d targetDir = new Vec3d(maxArmLength + 1, 0, 0);

		if(this.actionTargetPos != null) {
			targetDir = armStartWorld.subtract(this.actionTargetPos).normalize().scale(maxArmLength + 1.0F);
		}

		if(this.actionTimer >= 90) {
			this.startAction(ActionState.IDLE);
		}

		if(!this.world.isRemote && this.actionTimer == 63) {
			this.startSlamHit();

			for(Entity part : this.getParts()) {
				BlockPos pos = new BlockPos(part).down();

				BlockPos spawnPos = null;

				if(!this.world.isAirBlock(pos)) {
					spawnPos = pos;
				} else {
					for(int i = 0; i < 5; i++) {
						pos = pos.up();
						if(this.world.isAirBlock(pos)) {
							spawnPos = pos.down();
							break;
						}
					}
				}

				if(spawnPos != null) {
					EntitySludgeJet jet = new EntitySludgeJet(this.world);
					jet.setLocationAndAngles(part.posX + this.rand.nextFloat() - 0.5f, spawnPos.getY() + 0.5D, part.posZ + this.rand.nextFloat() - 0.5f, 0, 0);
					this.world.spawnEntity(jet);
				}
			}
		}

		Vec3d targetTipPos = armStartWorld.addVector(targetDir.x * Math.cos(rot), Math.sin(rot) * targetDir.lengthVector(), targetDir.z * Math.cos(rot));

		Vec3d tipPos = this.rootTip.getPositionVector();

		Vec3d diff = targetTipPos.subtract(tipPos);

		targetTipPos = tipPos.add(diff.normalize().scale(Math.min(diff.lengthVector(), 0.5D + this.actionTimer / 90.0f * 5.0D)));

		return targetTipPos;
	}

	protected Vec3d updateIdleTargetTipPos(Vec3d armStartWorld, float maxArmLength, Vec3d dirFwd, Vec3d dirUp) {
		float flailingStrength = this.isSwingInProgress ? (1 - this.swingProgress) : this.hurtTime > 0 ? (this.hurtTime / (float)this.maxHurtTime) * 0.5F : 0.0f;

		this.armMovementTicks += 1 + (int)(flailingStrength * 10);

		float idleX = MathHelper.cos(this.armMovementTicks / 9.0f) * 1.25F;
		float idleY = MathHelper.sin(this.armMovementTicks / 7.0f) * 0.25F;
		float idleZ = (MathHelper.cos(this.armMovementTicks / 15.0f) + 1) * 0.75f;

		Vec3d targetTipPos = armStartWorld.add(dirFwd.scale(maxArmLength));

		EntityLivingBase target = this.getAttackTarget();
		if(target != null) {
			targetTipPos = target.getPositionVector().addVector((double) 0, (double) (target.height / 2), (double) 0);
		}

		float forwardPos = (float) dirFwd.dotProduct(targetTipPos.subtract(armStartWorld));
		float offsetZ = 0.0f;
		if(forwardPos < 1.0F) {
			offsetZ = 1.0F - forwardPos;
		}

		//Idle movement
		targetTipPos = targetTipPos.add(dirUp.scale(idleY)).add(dirFwd.crossProduct(dirUp).scale(idleX)).add(dirFwd.scale(offsetZ - idleZ));

		Vec3d tipPos = this.rootTip.getPositionVector();

		Vec3d tipDiff = targetTipPos.subtract(tipPos);
		targetTipPos = tipPos.add(tipDiff.normalize().scale(Math.min(tipDiff.lengthVector(), 0.125D + flailingStrength * 0.8D)));

		return targetTipPos;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return this.renderBoundingBox;
	}

	@Override
	public float getShakeIntensity(Entity viewer, float partialTicks) {
		if(this.screenShakeTimer > 0) {
			float dstMul = MathHelper.clamp(1.0f - this.getDistance(viewer) / 24.0f, 0, 1);
			return dstMul * (this.screenShakeTimer - partialTicks) / 10.0f * 0.2f;
		}
		return 0;
	}

	@Override
	public void handleStatusUpdate(byte id) {
		super.handleStatusUpdate(id);

		BulgeType bulgeType = BulgeType.byEventId(id);
		if(bulgeType != null) {
			this.addBulge(bulgeType);
		} else if(id == EVENT_START_ACTION) {
			this.startAction(ActionState.values()[this.dataManager.get(ACTION_STATE)]);
		} else if(id == EVENT_SLAM_HIT) {
			this.startSlamHit();
		}
	}

	protected void startAction(ActionState action) {
		this.actionState = action;
		this.actionTimer = 0;

		if(!this.world.isRemote) {
			this.world.setEntityState(this, EVENT_START_ACTION);
			this.dataManager.set(ACTION_STATE, this.actionState.ordinal());
		}
	}

	protected void startSlamHit() {
		this.screenShakeTimer = 10;

		this.playSound(SoundRegistry.WALL_SLAM, 2, 1);
		this.playSound(SoundRegistry.SLUDGE_MENACE_ATTACK, 1, 1);

		if(!this.world.isRemote) {
			this.world.setEntityState(this, EVENT_SLAM_HIT);
		}
	}

	protected void addBulge(BulgeType type) {
		Bulge bulge = new Bulge();

		bulge.size = bulge.renderSize = type.size;
		bulge.type = type;

		this.bulges.add(bulge);

		if(!this.world.isRemote) {
			this.world.setEntityState(this, type.eventId);
		}
	}

	@Nullable
	public List<Bulge> getBulges(float partialTicks) {
		for(Bulge bulge : this.bulges) {
			bulge.renderPosition = bulge.prevPosition + (bulge.position - bulge.prevPosition) * partialTicks;
			bulge.renderSize = bulge.prevSize + (bulge.size - bulge.prevSize) * partialTicks;
		}
		return this.bulges;
	}

	protected static class AISludgeMenaceArmAttack extends AIArmAttack {
		protected final EntitySludgeMenace menace;

		protected AISludgeMenaceArmAttack(EntitySludgeMenace entity) {
			super(entity);
			this.menace = entity;
		}

		@Override
		public void updateTask() {
			Entity target = this.entity.getAttackTarget();

			if(this.attackTicks > 0) {
				this.attackTicks--;
			} else if(target != null && this.menace.isEntityAlive()) {
				AxisAlignedBB targetAabb = target.getEntityBoundingBox();

				boolean attacked = false;

				Set<Entity> attackedSet = new HashSet<>();

				for(Entity part : this.menace.getParts()) {
					if(!attackedSet.contains(target) && targetAabb.intersects(part.getEntityBoundingBox())) {
						attacked = true;

						if(this.entity.attackEntityAsMob(target)) {
							attackedSet.add(target);
						}
					}

					List<EntityLivingBase> collidingList = this.menace.getWorld().getEntitiesWithinAABB(EntityLivingBase.class, part.getEntityBoundingBox(), e -> e instanceof IMob == false && e != target && !attackedSet.contains(e));

					for(EntityLivingBase colliding : collidingList) {
						attacked = true;

						if(this.entity.attackEntityAsMob(colliding)) {
							attackedSet.add(colliding);
						}
					}
				}

				if(attacked) {
					this.entity.swingArm(EnumHand.MAIN_HAND);
					this.attackTicks = 20;
				}
			}
		}
	}

	protected static class AIAction extends EntityAIBase {
		protected final EntitySludgeMenace menace;

		protected int cooldown;
		protected int minCooldown, maxCooldown;

		protected AIAction(EntitySludgeMenace entity, int minCooldown, int maxCooldown) {
			this.menace = entity;
			this.minCooldown = minCooldown;
			this.maxCooldown = maxCooldown;
			this.resetTask();
		}

		@Override
		public boolean shouldExecute() {
			if(this.menace.actionState == ActionState.IDLE && this.menace.getAttackTarget() != null && this.menace.isEntityAlive()) {
				return this.cooldown-- <= 0;
			}
			return false;
		}

		@Override
		public boolean shouldContinueExecuting() {
			return false;
		}

		@Override
		public void startExecuting() {
			int nr = this.menace.rand.nextInt(100);

			if(nr <= 17) {
				this.menace.startAction(ActionState.SPIT_MOBS);
			} else if(nr <= 34) {
				this.menace.startAction(ActionState.SWING);
			} else if(nr <= 67) {
				this.menace.startAction(ActionState.POKE);
			} else {
				this.menace.startAction(ActionState.SLAM);
			}
		}

		@Override
		public void resetTask() {
			this.cooldown = this.minCooldown + this.menace.rand.nextInt(this.maxCooldown - this.minCooldown);
		}
	}

	@Override
	public UUID getBossInfoUuid() {
		return this.dataManager.get(BOSSINFO_ID).or(new UUID(0, 0));
	}

	@Override
	public BLSoundEvent getMusicFile(EntityPlayer listener) {
		return SoundRegistry.PIT_OF_DECAY_LOOP;
	}

	@Override
	public double getMusicRange(EntityPlayer listener) {
		return 32.0D;
	}

	@Override
	public boolean isMusicActive(EntityPlayer listener) {
		return this.isEntityAlive();
	}

	@Override
	public int getMusicLayer(EntityPlayer listener) {
		return EntityMusicLayers.BOSS;
	}

	@Override
	public boolean isUnmountBlocked(EntityPlayer rider) {
		//Prevent target from dismounting while sucking out target

		if(!this.world.isRemote && this.actionState == ActionState.POKE) {
			DummyPart endDummy = this.dummies[this.dummies.length - 1];

			if(endDummy != null && endDummy.isEntityAlive()) {
				List<Entity> passengers = endDummy.getPassengers();

				if(!passengers.isEmpty() && passengers.get(0) == rider) {
					return true;
				}
			}
		}

		return false;
	}
}
