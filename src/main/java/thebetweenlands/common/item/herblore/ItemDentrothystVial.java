package thebetweenlands.common.item.herblore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStackSimple;
import net.minecraftforge.items.ItemHandlerHelper;
import thebetweenlands.client.tab.BLCreativeTabs;
import thebetweenlands.common.registries.BlockRegistry;
import thebetweenlands.common.registries.FluidRegistry;
import thebetweenlands.common.registries.ItemRegistry;
import thebetweenlands.common.world.storage.location.LocationSludgeWormDungeon;
import thebetweenlands.common.world.storage.location.LocationStorage;

public class ItemDentrothystVial extends Item implements ItemRegistry.IBlockStateItemModelDefinition {
	private static final class DentrothystVialFluidHandler extends FluidHandlerItemStackSimple {
		public DentrothystVialFluidHandler(final ItemStack container, final int capacity) {
			super(container, capacity);
		}

		@Override
		public boolean canFillFluidType(FluidStack fluid) {
			return ItemRegistry.DENTROTHYST_FLUID_VIAL.canFillWith(container, fluid);
		}

		@Nullable
		@Override
		public FluidStack getFluid() {
			return null;
		}

		@Override
		protected void setContainerToEmpty() {
			
		}

		@Override
		public int fill(FluidStack resource, boolean doFill) {
			if(this.container.getItemDamage() == 1) {
				//Dirty vial can't be filled
				return 0;
			}
			ItemStack fluidVial = new ItemStack(ItemRegistry.DENTROTHYST_FLUID_VIAL, 1, this.container.getItemDamage() == 2 ? 1 : 0);
			IFluidHandlerItem handler = FluidUtil.getFluidHandler(fluidVial);
			int filled = handler.fill(resource, doFill);
			if(filled > 0 && doFill) {
				this.container = fluidVial;
			}
			return filled;
		}
	}
	
    public ItemDentrothystVial() {
        this.setCreativeTab(BLCreativeTabs.HERBLORE);
        this.setHasSubtypes(true);
        this.setMaxDamage(0);
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> list) {
        if (isInCreativeTab(tab)) {
            list.add(new ItemStack(this, 1, 0)); //green
            list.add(new ItemStack(this, 1, 1)); //green dirty
            list.add(new ItemStack(this, 1, 2)); //orange
        }
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        String s = "item.thebetweenlands.elixir.dentrothyst_vial.";
        try {
            switch(stack.getItemDamage()) {
                case 0:
                    return s + "green";
                case 1:
                    return s + "dirty";
                case 2:
                    return s + "orange";
            }
        } catch (Exception e) { }
        return "item.thebetweenlands.unknown";
    }

    /**
     * Creates an item stack of the specified vial type.
     * Vial types: 0 = green, 1 = dirty, 2 = orange
     * @param vialType
     * @return
     */
    public ItemStack createStack(int vialType) {
        return new ItemStack(this, 1, vialType);
    }

    /**
     * Creates an item stack of the specified vial type.
     * Vial types: 0 = green, 1 = dirty, 2 = orange
     * @param vialType
     * @param size
     * @return
     */
    public ItemStack createStack(int vialType, int size) {
        return new ItemStack(this, size, vialType);
    }

    @Override
    public Map<Integer, String> getVariants() {
        Map<Integer, String> variants = new HashMap<>();
        variants.put(0, "green");
        variants.put(1, "dirty");
        variants.put(2, "orange");
        return variants;
    }
    
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, IBlockAccess world, BlockPos pos, EntityPlayer player) {
        return world.getBlockState(pos).getBlock() == BlockRegistry.ASPECT_VIAL_BLOCK;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if(player.isSneaking() && facing == EnumFacing.UP && stack.getItemDamage() != 1) {
            if(world.isAirBlock(pos.up()) && BlockRegistry.ASPECT_VIAL_BLOCK.canPlaceBlockAt(world, pos.up())) {
                if(!world.isRemote) {
                    ItemAspectVial.placeAspectVial(world, pos.up(), stack.getItemDamage() == 2 ? 1 : 0, null);
                    stack.shrink(1);
                }
                return EnumActionResult.SUCCESS;
            }
        } else if(!player.isSneaking() && stack.getItemDamage() != 1) {
        	List<LocationStorage> locations = LocationStorage.getLocations(world, new AxisAlignedBB(pos));
        	
        	for(LocationStorage location : locations) {
        		if(location instanceof LocationSludgeWormDungeon) {
        			LocationSludgeWormDungeon dungeon = (LocationSludgeWormDungeon) location;
        			
        			if(dungeon.hasGroundFog(pos)) {
	        			int floor = dungeon.getFloor(pos);
	        			
	        			if(floor == 5 || floor == 6) {
	        				if(!world.isRemote) {
	        					stack.shrink(1);
	        					world.playSound(null, pos, FluidRegistry.FOG.getFillSound(new FluidStack(FluidRegistry.FOG, 1000)), SoundCategory.BLOCKS, 1.0F, 1.0F);
	        					ItemHandlerHelper.giveItemToPlayer(player, ItemRegistry.DENTROTHYST_FLUID_VIAL.withFluid(stack.getItemDamage() == 2 ? 1 : 0, FluidRegistry.FOG));
	        				}
	        				
	        				return EnumActionResult.SUCCESS;
	        			} else if(!world.isRemote) {
	        				player.sendStatusMessage(new TextComponentTranslation("chat.not_enough_fog_for_vial"), true);
	        			}
        			}
        		}
        	}
        }
        return EnumActionResult.FAIL;
    }
    
    @Override
	public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
		return new DentrothystVialFluidHandler(stack, ItemRegistry.DENTROTHYST_FLUID_VIAL.getCapacity());
	}
}
