package com.hfr.items;

import java.util.ArrayList;
import java.util.List;

import com.hfr.blocks.ModBlocks;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

public class ItemMace extends ItemMultitool {

	public static final List<Block> interactOverride = new ArrayList() {{
		add(Blocks.chest);
		add(Blocks.trapped_chest);
		add(Blocks.dispenser);
		add(Blocks.dropper);
		add(Blocks.hopper);

		//props
		add(ModBlocks.machine_uni);
		add(ModBlocks.clowder_flag);
		add(ModBlocks.statue);
		add(ModBlocks.machine_grainmill);
	}};

	public static final List<Block> breakOverride = new ArrayList() {{
		add(Blocks.chest);
		add(Blocks.trapped_chest);
		add(Blocks.dispenser);
		add(Blocks.dropper);
		add(Blocks.hopper);
		
		//props
		add(ModBlocks.machine_uni);
		add(ModBlocks.statue);
		add(ModBlocks.machine_grainmill);
	}};

	public static final List<Block> placeOverride = new ArrayList() {{
		add(ModBlocks.clowder_conquerer);
	}};

}
