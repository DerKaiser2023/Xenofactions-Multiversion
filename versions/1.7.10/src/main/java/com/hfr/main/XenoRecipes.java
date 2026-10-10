package com.hfr.main;

import com.hfr.blocks.ModBlocks;
import com.hfr.config.XFConfig;
import com.hfr.items.ModItems;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;

/** Survival entry points for the established faction, prestige, and support loop. */
public final class XenoRecipes {

	private XenoRecipes() { }

	/** The single boundary for recipes added by the survival-recipes feature. */
	public static void registerSurvivalRecipes() {
		if(!XFConfig.enableSurvivalRecipes)
			return;

		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.barricade, 4),
				" S ", "SWS", " S ", 'S', new ItemStack(Blocks.sand, 1, OreDictionary.WILDCARD_VALUE),
				'W', new ItemStack(Blocks.wool, 1, OreDictionary.WILDCARD_VALUE)));
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.hesco_block, 2),
				"BSB", "ISI", "BSB", 'B', Blocks.iron_bars, 'S', ModBlocks.barricade, 'I', "ingotIron"));
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.stone_wall, 4),
				"S S", "S S", "S S", 'S', "cobblestone"));
		GameRegistry.addRecipe(new ItemStack(ModBlocks.brick_wall, 4), "BBB", "BBB", 'B', Blocks.brick_block);
		GameRegistry.addRecipe(new ItemStack(ModBlocks.great_wall, 4), "SSS", "SSS", 'S', Blocks.stonebrick);
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.palisade, 4),
				"LLL", "SSS", "LLL", 'L', "logWood", 'S', Items.stick));
		GameRegistry.addRecipe(new ItemStack(ModBlocks.berlin_wall, 2), "SBS", "SBS", "SBS",
				'S', Blocks.stone, 'B', Blocks.iron_bars);

		shaped(ModBlocks.clowder_conquerer, " W ", " R ", " R ", 'W', Blocks.wool, 'R', Blocks.fence);

		shaped(ModBlocks.machine_net, "WWW", "RAR", "BBB", 'W', Blocks.ladder, 'R', Blocks.iron_bars, 'B', Blocks.fence, 'A', Blocks.trapped_chest);

        shaped(ModBlocks.faction_market, "IRI", "ECE", "IRI", 'I', Items.iron_ingot,
            'R', Items.redstone, 'E', Items.emerald, 'C', Blocks.chest);

		GameRegistry.addRecipe(new ItemStack(ModBlocks.uni_foundation, 4), "SIS", "III", "SIS",
				'S', Blocks.stonebrick, 'I', Items.iron_ingot);
		shaped(ModBlocks.officer_chest, "I I", "ICI", "IRI", 'I', Items.iron_ingot, 'C', Blocks.chest, 'R', Items.redstone);

		shaped(ModBlocks.tp_tent, "WWW", "PEP", "SRS", 'W', Blocks.wool, 'P', Blocks.diamond_block,
				'E', Blocks.beacon, 'S', Items.stick, 'R', Items.redstone);

		shaped(ModBlocks.machine_factory, "SCS", "MEM", "SPS", 'S', ModItems.components_steel,
				'C', Blocks.crafting_table, 'M', ModItems.components_mechanical, 'E', ModItems.components_electronics, 'P', Blocks.piston);
		shaped(ModBlocks.machine_uni, "BEB", "SDS", "BKB", 'B', Blocks.bookshelf, 'E', Blocks.beacon,
				'S', Blocks.redstone_block, 'D', Blocks.diamond_block, 'K', Blocks.crafting_table);
		shaped(ModBlocks.machine_temple, "QGQ", "SBS", "ODO", 'Q', Blocks.quartz_block, 'G', Blocks.gold_block,
				'S', Blocks.stonebrick, 'B', Blocks.bookshelf, 'O', Blocks.beacon, 'D', Blocks.diamond_block);
		shaped(ModBlocks.statue, " Q ", "QEQ", "SGS", 'Q', Blocks.quartz_block, 'E', Items.emerald,
				'S', Blocks.stonebrick, 'G', Blocks.gold_block);
	}

	private static void shaped(Block output, Object... recipe) {
		GameRegistry.addRecipe(new ItemStack(output), recipe);
	}
}
