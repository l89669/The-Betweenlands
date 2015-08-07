package thebetweenlands.world.biomes.decorators.base;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import thebetweenlands.blocks.BLBlockRegistry;
import thebetweenlands.world.biomes.base.BiomeGenBaseBetweenlands;
import thebetweenlands.world.biomes.base.ChunkDataAccess;
import thebetweenlands.world.biomes.decorators.data.SurfaceType;
import thebetweenlands.world.feature.gen.OreGens;
import thebetweenlands.world.feature.gen.WorldGenMinableBetweenlands;

import java.util.Random;

/**
 *
 * @author The Erebus Team
 *
 */
public class BiomeDecoratorBaseBetweenlands
{
	private int postChunkPopulatePasses = 1;
	private int postChunkGenPasses = 1;
	protected World world;
	protected Random rand;
	protected int x, z;
	protected int xx, yy, zz, attempt;
	
	public final BiomeDecoratorBaseBetweenlands setPostChunkGenPasses(int passes) {
		this.postChunkGenPasses = passes;
		return this;
	}
	
	public final BiomeDecoratorBaseBetweenlands setPostChunkPopulatePasses(int passes) {
		this.postChunkPopulatePasses = passes;
		return this;
	}
	
	public final int getX() {
		return this.x;
	}

	public final int getZ() {
		return this.z;
	}

	public final Random getRNG() {
		return this.rand;
	}

	public final World getWorld() {
		return this.world;
	}

	public final void postChunkPopulate(World world, Random rand, int x, int z) {
		this.x = x;
		this.z = z;
		this.rand = rand;
		this.world = world;
		this.generateOres();
		for(int i = 0; i < this.postChunkPopulatePasses; i++) {
			this.postChunkPopulate(i);
		}
	}
	
	public final void postChunkGen(World world, Random rand, int x, int z) {
		this.x = x;
		this.z = z;
		this.rand = rand;
		this.world = world;
		for(int i = 0; i < this.postChunkGenPasses; i++) {
			this.postChunkGen(i);
		}
	}

	public final void preChunkProvide(World world, Random rand, int chunkX, int chunkZ, Block[] blocks, byte[] metadata, BiomeGenBase[] biomes) {
		this.preChunkProvide(world, rand, new ChunkDataAccess(chunkX, chunkZ, blocks, metadata, biomes));
	}
	
	protected void preChunkProvide(World world, Random rand, ChunkDataAccess dataAccess) { }
	
	protected void postChunkPopulate(int pass) { }

	protected void postChunkGen(int pass) { }
	
	protected final int offsetXZ() {
		return rand.nextInt(16) + 8;
	}

	protected boolean checkSurface(SurfaceType surfaceType, int x, int y, int z) {
		return surfaceType.matchBlock(world.getBlock(x, y - 1, z)) && world.isAirBlock(x, y, z);
	}

	protected void generateOres() {
		this.generateOre(20, OreGens.SULFUR, 0, 128);
		this.generateOre(20, OreGens.OCTINE, 0, 64);
		this.generateOre(20, OreGens.BLURITE, 0, 64);
		this.generateOre(5, OreGens.VALONITE, 0, 32);
		this.generateOre(1, OreGens.LIFE_GEM, 0, 16);
		
		//Generate middle gems
		if(this.rand.nextInt(5) == 0) {
			int xx = this.x + this.rand.nextInt(16);
			int zz = this.z + this.rand.nextInt(16);
			int yy = this.world.getHeightValue(xx, zz);
			boolean hasMud = false;
			if(this.world.getBlock(xx, yy, zz) == BLBlockRegistry.swampWater) {
				while(yy > 0) {
					if(this.world.getBlock(xx, yy, zz) == BLBlockRegistry.mud) {
						hasMud = true;
						break;
					}
					--yy;
				}
			}
			if(hasMud) {
				switch(this.rand.nextInt(3)) {
				case 0:
					this.world.setBlock(xx, yy, zz, BLBlockRegistry.aquaMiddleGemOre);
					break;
				case 1:
					this.world.setBlock(xx, yy, zz, BLBlockRegistry.crimsonMiddleGemOre);
					break;
				case 2:
					this.world.setBlock(xx, yy, zz, BLBlockRegistry.greenMiddleGemOre);
					break;
				}
			}
		}
	}
	
	protected void generateOre(int tries, WorldGenMinableBetweenlands oreGen, int minY, int maxY) {
		for (int i = 0; i < tries; i++) {
			int xx = this.x + this.rand.nextInt(16);
			int yy = this.rand.nextInt(maxY) + this.rand.nextInt(maxY) + (minY - maxY);
			int zz = this.z + this.rand.nextInt(16);
			BiomeGenBaseBetweenlands biome = (BiomeGenBaseBetweenlands)this.getWorld().getBiomeGenForCoords(xx, zz);
			oreGen.prepare(biome.getBaseBlock()).generate(this.world, this.rand, xx, yy, zz);
		}
	}
}
