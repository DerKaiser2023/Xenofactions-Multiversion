package com.hfr.handler;

import com.hfr.inventory.container.*;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.hfr.blocks.ModBlocks;
import com.hfr.inventory.gui.GUIFactory;
import com.hfr.inventory.gui.GUIGrainmill;
import com.hfr.inventory.gui.GUIMachineNet;
import com.hfr.inventory.gui.GUIMachineUni;
import com.hfr.inventory.gui.GUITemple;
import com.hfr.inventory.gui.GuiFlagBig;
import com.hfr.tileentity.clowder.*;
import com.hfr.tileentity.machine.*;

import cpw.mods.fml.common.network.IGuiHandler;

public class GUIHandler implements IGuiHandler {

	@Override
	public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
		TileEntity entity = world.getTileEntity(x, y, z);
		switch(ID)
		{

			case ModBlocks.guiID_net:
			{
				if(entity instanceof TileEntityMachineNet)
				{
					return new ContainerMachineNet(player.inventory, (TileEntityMachineNet) entity);
				}
				return null;
			}

			case ModBlocks.guiID_uni:
			{
				if(entity instanceof TileEntityMachineUni)
				{
					return new ContainerMachineUni(player.inventory, (TileEntityMachineUni) entity);
				}
				return null;
			}

			case ModBlocks.guiID_grainmill:
			{
				if(entity instanceof TileEntityMachineGrainmill)
				{
					return new ContainerGrainMill(player.inventory, (TileEntityMachineGrainmill) entity);
				}
				return null;
			}

			case ModBlocks.guiID_factory:
			{
				if(entity instanceof TileEntityMachineFactory)
				{
					return new ContainerFactory(player.inventory, (TileEntityMachineFactory) entity);
				}
				return null;
			}

			case ModBlocks.guiID_temple:
			{
				if(entity instanceof TileEntityMachineTemple)
				{
					return new ContainerTemple(player.inventory, (TileEntityMachineTemple) entity);
				}
				return null;
			}

			case ModBlocks.guiID_chest:
			{
				if(entity instanceof TileEntityOfficerChest)
				{
					return new ContainerChest(player.inventory, (TileEntityOfficerChest) entity);
				}
				return null;
			}

			case ModBlocks.guiID_flag_big:
			{
				if(entity instanceof TileEntityFlagBig)
				{
					return new ContainerFlagBig(player.inventory, (TileEntityFlagBig) entity);
				}
				return null;
			}

		}
		return null;
	}

	@Override
	public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
		TileEntity entity = world.getTileEntity(x, y, z);
		if(entity != null)
		{
			switch(ID)
			{
				
				case ModBlocks.guiID_net:
				{
					if(entity instanceof TileEntityMachineNet)
					{
						return new GUIMachineNet(player.inventory, (TileEntityMachineNet) entity);
					}
					return null;
				}
				
				case ModBlocks.guiID_uni:
				{
					if(entity instanceof TileEntityMachineUni)
					{
						return new GUIMachineUni(player.inventory, (TileEntityMachineUni) entity);
					}
					return null;
				}
				
				case ModBlocks.guiID_grainmill:
				{
					if(entity instanceof TileEntityMachineGrainmill)
					{
						return new GUIGrainmill(player.inventory, (TileEntityMachineGrainmill) entity);
					}
					return null;
				}
				
				case ModBlocks.guiID_factory:
				{
					if(entity instanceof TileEntityMachineFactory)
					{
						return new GUIFactory(player.inventory, (TileEntityMachineFactory) entity);
					}
					return null;
				}
				
				case ModBlocks.guiID_temple:
				{
					if(entity instanceof TileEntityMachineTemple)
					{
						return new GUITemple(player.inventory, (TileEntityMachineTemple) entity);
					}
					return null;
				}

				case ModBlocks.guiID_chest:
				{
					if(entity instanceof TileEntityOfficerChest)
					{
						return new GuiChest(player.inventory, (TileEntityOfficerChest) entity);
					}
					return null;
				}

				case ModBlocks.guiID_flag_big:
				{
					if(entity instanceof TileEntityFlagBig)
					{
						return new GuiFlagBig(player.inventory, (TileEntityFlagBig) entity);
					}
					return null;
				}
				
				case ModBlocks.guiID_market:
				{
					// Shop screens are opened exclusively by validated server snapshots.
					return null;
				}
				
			}
		}
		return null;
	}

}
