package com.hfr.main;

import com.hfr.blocks.ModBlocks;
import com.hfr.items.ModItems;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;

public class CraftingManager {
	
	public static void mainRegistry()
	{
		AddCraftingRec();
		AddSmeltingRec();
	}

	public static void AddCraftingRec()
	{
		XenoRecipes.registerSurvivalRecipes();

		GameRegistry.addRecipe(new ItemStack(ModItems.rice_bag, 1), new Object[] { "###", "###", "###", '#', ModItems.rice });

		GameRegistry.addRecipe(new ItemStack(ModItems.rice, 9), new Object[] { "#", '#', ModItems.rice_bag });

		GameRegistry.addRecipe(new ItemStack(ModBlocks.machine_grainmill, 1), new Object[] { "WWW", "BWB", "BMB", 'W', ModItems.components_wood, 'M', ModItems.components_mechanical, 'B', Blocks.cobblestone });

		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModItems.components_wood), new Object[] { "WSW", "WSW", "WSW", 'W', "logWood", 'S', Items.stick }));
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModItems.components_scaffold), new Object[] { "IBI", "ISI", "IBI", 'I', "ingotIron", 'B', Blocks.iron_bars, 'S', Blocks.stone }));
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModItems.components_steel), new Object[] { "IBI", "ISI", "IBI", 'I', "ingotIron", 'B', "ingotIron", 'S', ModItems.components_scaffold }));
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModItems.components_mechanical), new Object[] { "FIF", "IGI", "FIF", 'I', "ingotIron", 'F', Items.flint, 'G', "ingotGold" }));
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModItems.components_electronics), new Object[] { "BRD", "RGR", "DRB", 'R', "dustRedstone", 'G', "ingotGold", 'B', Blocks.stone_button, 'D', Items.repeater }));
		
		GameRegistry.addRecipe(new ItemStack(ModItems.clowder_banner), new Object[] { "SS", "WS", "WS", 'W', Blocks.wool, 'S', Items.stick });

}
	
	public static void AddSmeltingRec() {

		GameRegistry.addSmelting(ModItems.flour, new ItemStack(Items.bread), 0.5F);
		GameRegistry.addSmelting(Items.bone, new ItemStack(Items.slime_ball), 0.25F);
		GameRegistry.addSmelting(ModItems.rice, new ItemStack(ModItems.rice_sticky), 0.5F);
		GameRegistry.addSmelting(ModItems.squid_raw, new ItemStack(ModItems.squid_cooked), 0.5F);
	}
}
