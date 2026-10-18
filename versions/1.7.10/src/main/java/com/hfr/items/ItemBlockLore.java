package com.hfr.items;

import java.util.List;

import com.hfr.blocks.ModBlocks;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class ItemBlockLore extends ItemBlock {
	
	public ItemBlockLore(Block b) {
		super(b);
	}

	@Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean bool) {
        com.hfr.tileentity.machine.MachineDisplaySnapshot values = com.hfr.tileentity.machine.MachineDisplaySnapshot.forClientDisplay();

		if(field_150939_a == ModBlocks.barricade) {
			list.add("Can be placed in enemy chunks");
			list.add("if a conquest flag within 8 blocks");
		}

		if(field_150939_a == ModBlocks.uni_foundation) {
			list.add("Increases speed by 15%");
		}
		if(field_150939_a == ModBlocks.asphalt) {
			list.add("Foundation block");
			list.add("Increases speed by 25%");
		}

		if(field_150939_a == ModBlocks.machine_net) {
			list.add("Requires sky access and 3 block deep water");
			list.add("Must be placed on the surface");
		}
		if(field_150939_a == ModBlocks.machine_uni) {
			list.add("Requires sky access and foundation");
			list.add("Generates research points over time");
			list.add(values.universityPrestige +" prestige gen / hour");
		}
		if(field_150939_a == ModBlocks.machine_grainmill) {
			list.add("Requires sky access and foundation");
			list.add("Grinds wheat into flour");
			list.add(values.grainmillPrestige + " prestige gen / hour");
		}

		if(field_150939_a == ModBlocks.machine_temple) {
			list.add("Generates scrolls over time");
			list.add(values.templePrestige + " prestige gen / hour");
		}

		if(field_150939_a == ModBlocks.hesco_block) {
			list.add("HESCO MIL brand collapsable gabion");
			list.add("Filled with 100% fair-trade dirt");
		}
		if(field_150939_a == ModBlocks.palisade) {
			list.add("Prehistoric wooden barricade");
			list.add("Made from only the finest oak logs");
		}
		if(field_150939_a == ModBlocks.stone_wall) {
			list.add("High-tech stone-based fortification");
		}
		if(field_150939_a == ModBlocks.brick_wall) {
			list.add("Classic 20th century ugly-as-shit construction");
		}
		if(field_150939_a == ModBlocks.great_wall) {
			list.add("\"Great wall\"");
			list.add("In every way identical to the stone wall");
		}
		if(field_150939_a == ModBlocks.berlin_wall) {
			list.add("Comes with functional barbed wire");
		}

		if(field_150939_a == ModBlocks.tp_tent) {
			list.add("Requires sky access and foundation");
			list.add("Allows to create faction teleport/TP/waypoints nearby");
			list.add(-values.tentUpkeep + " prestige gen / hour");

		}
		if(field_150939_a == ModBlocks.statue) {
			list.add("Requires sky access and foundation");
			list.add("Low-level prestige generator");
			list.add(values.statuePrestige + " prestige gen / hour");
		}

		if(field_150939_a == ModBlocks.machine_factory) {
			list.add("Connectors: lowest blocks on all sides");
		}

	}

}
