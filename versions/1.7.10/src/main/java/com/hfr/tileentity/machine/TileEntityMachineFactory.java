package com.hfr.tileentity.machine;

import com.hfr.blocks.FoundationSupport;
import com.hfr.items.ModItems;
import com.hfr.main.MainRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.material.Material;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

public class TileEntityMachineFactory extends TileEntityMachineBase {
	
	private NBTTagCompound legacyData = new NBTTagCompound();

	public TileEntityMachineFactory() {
		super(6);
	}

	@Override
	public String getName() {
		return "container.machineFactory";
	}

	@Override
	public void updateEntity() {

		if(!worldObj.isRemote) {
			
			if(operational() && hasSpace() && slots[4] == null) {
				
				if(worldObj.rand.nextInt(Math.max(1, MainRegistry.factoryRate) * 20) == 0) {
					
					for(int i = 0; i < 4; i++) {
						if(slots[i] == null) {
							slots[i] = new ItemStack(ModItems.cog);
							break;
						} else if(slots[i].getItem() == ModItems.cog && slots[i].stackSize < slots[i].getMaxStackSize()){
							slots[i].stackSize++;
							break;
						}
					}
					this.markDirty();
				}
				if(worldObj.rand.nextInt(Math.max(1, MainRegistry.factoryJamRate) * 20) == 0) {
					
					int i = worldObj.rand.nextInt(10);
					
					if(i < 5)
						slots[4] = new ItemStack(Items.paper).setStackDisplayName("Worker Strike");
					else if(i < 7)
						slots[4] = new ItemStack(Items.skull).setStackDisplayName("Workplace Accident");
					else if(i < 9)
						slots[4] = new ItemStack(Items.slime_ball).setStackDisplayName("Chemical Spill");
					else
						slots[4] = new ItemStack(Items.potato).setStackDisplayName("Communist Takeover");
					this.markDirty();
				}
			}
			
		}
	}
	
	public boolean operational() {

		for(int i = -4; i <= 4; i++)
			for(int j = -4; j <= 4; j++)
				if(worldObj.getBlock(xCoord + i, yCoord + 4, zCoord + j).getMaterial() != Material.air && !(i == 0 && j == 0))
					return false;

		for(int x = -4; x <= 4; x++)
			for(int z = -4; z <= 4; z++)
				if(!FoundationSupport.isValid(worldObj.getBlock(xCoord + x, yCoord - 1, zCoord + z)))
			return false;
		
		if(slots[4] != null)
			return false;
		
		return true;
	}
	
	public boolean hasSpace() {
		
		for(int i = 0; i < 4; i++) {
			
			if((slots[i] == null || (slots[i] != null && slots[i].getItem() == ModItems.cog && slots[i].stackSize < slots[i].getMaxStackSize())))
				return true;
		}
		
		return false;
	}
	
	private static final int[] access = new int[] { 0, 1, 2, 3, 4 };
	@Override
	public int[] getAccessibleSlotsFromSide(int p_94128_1_) {
		return access;
	}
	
	@Override public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return side == 0 && slot >= 0 && slot < 4 && stack != null && stack.getItem() == ModItems.cog;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
        return com.hfr.main.MainRegistry.proxy.modelBounds(this);
    }
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared()
	{
		return 16384.0D;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		
		legacyData = (NBTTagCompound)nbt.copy();
		
		NBTTagList list = nbt.getTagList("items", 10);
		slots = new ItemStack[getSizeInventory()];

		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound nbt1 = list.getCompoundTagAt(i);
			byte b0 = nbt1.getByte("slot");
			if (b0 >= 0 && b0 < slots.length) {
				slots[b0] = ItemStack.loadItemStackFromNBT(nbt1);
			}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		
		for(Object key : legacyData.func_150296_c()) {
			String name = (String)key;
			if(!nbt.hasKey(name)) nbt.setTag(name, legacyData.getTag(name).copy());
		}

		NBTTagList list = new NBTTagList();

		for (int i = 0; i < slots.length; i++) {
			if (slots[i] != null) {
				NBTTagCompound nbt1 = new NBTTagCompound();
				nbt1.setByte("slot", (byte) i);
				slots[i].writeToNBT(nbt1);
				list.appendTag(nbt1);
			}
		}
		nbt.setTag("items", list);
	}

	}
