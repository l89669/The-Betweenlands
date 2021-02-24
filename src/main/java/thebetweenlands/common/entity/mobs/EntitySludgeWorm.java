package thebetweenlands.common.entity.mobs;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityMultiPart;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thebetweenlands.api.entity.IEntityBL;
import thebetweenlands.client.render.particle.BLParticles;
import thebetweenlands.common.entity.EntityTinyWormEggSac;
import thebetweenlands.common.registries.LootTableRegistry;
import thebetweenlands.common.registries.SoundRegistry;

public class EntitySludgeWorm extends EntityMob implements IEntityMultiPart, IMob, IEntityBL {

	public MultiPartEntityPart[] parts;

	public boolean debugHitboxes = false;

	Random rand = new Random();

	private AxisAlignedBB renderBoundingBox;
	
	private int wallInvulnerabilityTicks = 40;

	private boolean doSpawningAnimation = true;
	
	public EntitySludgeWorm(World world) {
		super(world);
		setSize(0.4375F, 0.3125F);
		setPathPriority(PathNodeType.WATER, -10.0F);
		isImmuneToFire = true;
		parts = new MultiPartEntityPart[] {
				new MultiPartEntityPart(this, "part1", 0.4375F, 0.3125F),
				new MultiPartEntityPart(this, "part2", 0.3125F, 0.3125F),
				new MultiPartEntityPart(this, "part3", 0.3125F, 0.3125F),
				new MultiPartEntityPart(this, "part4", 0.3125F, 0.3125F),
				new MultiPartEntityPart(this, "part5", 0.3125F, 0.3125F),
				new MultiPartEntityPart(this, "part6", 0.3125F, 0.3125F),
				new MultiPartEntityPart(this, "part7", 0.3125F, 0.3125F),
				new MultiPartEntityPart(this, "part8", 0.3125F, 0.3125F),
				new MultiPartEntityPart(this, "part9", 0.3125F, 0.3125F) };
		this.renderBoundingBox = this.getEntityBoundingBox();
	}
	
	public EntitySludgeWorm(World world, boolean doSpawningAnimation) {
		this(world);
		this.doSpawningAnimation = doSpawningAnimation;
	}

	@Override
	protected void initEntityAI() {
		tasks.addTask(1, new EntityAIAttackMelee(this, 1, false));
		tasks.addTask(3, new EntityAIWander(this, 0.8D, 1));
		targetTasks.addTask(0, new EntityAIHurtByTarget(this, false));
		targetTasks.addTask(1, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true));
		targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityLivingBase.class, 10, true, false, entity -> entity instanceof IMob == false));
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(10.0D);
		getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(20.0D);
		getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
		getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.21D);
		getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(1.25D);
	}

	@Override
	public void onLivingUpdate() {
		super.onLivingUpdate();
		setMoveForward(0.2F);
		setHitBoxes();
	}

	protected float getHeadMotionYMultiplier() {
		return this.doSpawningAnimation && this.ticksExisted < 20 ? 0.65F : 1.0F;
	}

	protected float getTailMotionYMultiplier() {
		return this.doSpawningAnimation && this.ticksExisted < 20 ? 0.0F : 1.0F;
	}

	@Override
	public void onUpdate() {
		super.onUpdate();
		
		if(this.world.isRemote && this.ticksExisted % 10 == 0) {
			this.spawnParticles(this.world, this.posX, this.posY, this.posZ, this.rand);
		}

		if(this.wallInvulnerabilityTicks > 0) {
			this.wallInvulnerabilityTicks--;
		}
		
		motionY *= this.getHeadMotionYMultiplier();

		this.renderBoundingBox = this.getEntityBoundingBox();
		for(MultiPartEntityPart part : this.parts) {
			this.renderBoundingBox = this.renderBoundingBox.union(part.getEntityBoundingBox());
		}
	}

	@SideOnly(Side.CLIENT)
	public void spawnParticles(World world, double x, double y, double z, Random rand) {
		for (int count = 0; count < 1 + world.rand.nextInt(4); ++count) {
			double a = Math.toRadians(renderYawOffset);
			double offSetX = -Math.sin(a) * 0D + rand.nextDouble() * 0.3D - rand.nextDouble() * 0.3D;
			double offSetZ = Math.cos(a) * 0D + rand.nextDouble() * 0.3D - rand.nextDouble() * 0.3D;
			BLParticles.TAR_BEAST_DRIP.spawn(world , x + offSetX, y, z + offSetZ).setRBGColorF(0.4118F, 0.2745F, 0.1568F);
		}
	}

	// can be set to any part(s) - dunno if we want this either
	@Override
	public boolean attackEntityFromPart(MultiPartEntityPart part, DamageSource source, float dmg) {
		damageWorm(source, dmg * 0.75F);
		return true;
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float amount) {
		if (source == DamageSource.OUT_OF_WORLD || (source instanceof EntityDamageSource && ((EntityDamageSource) source).getIsThornsDamage())) {
			damageWorm(source, amount);
		} else if(source == DamageSource.IN_WALL && this.wallInvulnerabilityTicks > 0) {
			return false;
		} else {
			damageWorm(source, amount);
		}
		return true;
	}

	@Override
	public boolean canAttackClass(Class<? extends EntityLivingBase> entity) {
		return !IEntityBL.class.isAssignableFrom(entity) && EntityTinyWormEggSac.class != entity;
	}

	protected boolean damageWorm(DamageSource source, float amount) {
		return super.attackEntityFrom(source, amount);
	}

	@Override
	public Entity[] getParts() {
		return parts;
	}

	private void setHitBoxes() {
		if (ticksExisted == 1) {
			for(int i = 1; i < this.parts.length; i++) {
				this.parts[i].setLocationAndAngles(posX, posY, posZ, rotationYaw, 0F);
			}
		}

		this.parts[0].setLocationAndAngles(posX, posY, posZ, rotationYaw, 0);

		for(MultiPartEntityPart part : this.parts) {
			part.prevRotationYaw = part.rotationYaw;
			part.prevRotationPitch = part.rotationPitch;

			if(part != this.parts[0]) {
				part.prevPosX = part.lastTickPosX = part.posX;
				part.prevPosY = part.lastTickPosY = part.posY;
				part.prevPosZ = part.lastTickPosZ = part.posZ;

				if(part.posY < this.posY && this.world.collidesWithAnyBlock(part.getEntityBoundingBox())) {
					part.move(MoverType.SELF, 0, 0.1D, 0);
					part.motionY = 0.0D;
				}

				part.move(MoverType.SELF, 0, part.motionY, 0);

				part.motionY -= 0.08D;
				part.motionY *= 0.98D * this.getTailMotionYMultiplier();
			}
		}

		for(int i = 1; i < this.parts.length; i++) {
			this.movePiecePos(this.parts[i], this.parts[i - 1], 4.5F, 2F);
		}
	}

	protected double getMaxPieceDistance() {
		return 0.3D;
	}

	public void movePiecePos(MultiPartEntityPart targetPart, MultiPartEntityPart destinationPart, float speed, float yawSpeed) {
		//TODO make this better and use the parent entities motionY 
		if (destinationPart.posY - targetPart.posY < -0.5D)
			speed = 1.5F;

		double movementTolerance = 0.05D;
		double maxDist = this.getMaxPieceDistance();

		boolean correctY = false;

		for(int i = 0; i < 5; i++) {
			Vec3d diff = destinationPart.getPositionVector().subtract(targetPart.getPositionVector());
			double len = diff.lengthVector();

			if(len > maxDist) {
				Vec3d correction = diff.scale(1.0D / len * (len - maxDist));
				targetPart.posX += correction.x;
				targetPart.posZ += correction.z;

				targetPart.setPosition(targetPart.posX, targetPart.posY, targetPart.posZ);

				double cy = targetPart.posY;

				targetPart.move(MoverType.SELF, 0, correction.y, 0);

				if(Math.abs((targetPart.posY - cy) - correction.y) <= movementTolerance) {
					correctY = true;
					break;
				}
			}
		}

		//Welp, failed to move smoothly along Y, just clip
		if(!correctY) {
			Vec3d diff = destinationPart.getPositionVector().subtract(targetPart.getPositionVector());
			double len = diff.lengthSquared();

			if(len > maxDist) {
				Vec3d correction = diff.scale(1.0D / len * (len - maxDist));

				targetPart.posX += correction.x;
				targetPart.posY += correction.y;
				targetPart.posZ += correction.z;
			}
		}


		Vec3d diff = new Vec3d(destinationPart.posX, 0, destinationPart.posZ).subtract(new Vec3d(targetPart.posX, 0, targetPart.posZ));
		float destYaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90;

		double yawDiff = (destYaw - targetPart.rotationYaw) % 360.0F;
		double yawInterpolant = 2 * yawDiff % 360.0F - yawDiff;

		targetPart.rotationYaw += yawInterpolant / yawSpeed;

		targetPart.rotationPitch = 0;

		targetPart.setPosition(targetPart.posX, targetPart.posY, targetPart.posZ);
	}

	@Override
	public World getWorld() {
		return getEntityWorld();
	}

	// temp Sounds until we have proper ones
	@Override
	protected SoundEvent getAmbientSound() {
		return SoundRegistry.WORM_LIVING;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundRegistry.WORM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundRegistry.WORM_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, Block blockIn) {
		playSound(SoundRegistry.WORM_LIVING, 0.5F, 1F);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return this.renderBoundingBox;
	}
	
	@Override
	protected ResourceLocation getLootTable() {
		return LootTableRegistry.SMALL_SLUDGE_WORM;
	}
}
