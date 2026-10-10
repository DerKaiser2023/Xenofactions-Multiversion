package com.hfr.items;

import java.util.List;


import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class ItemLore extends Item {
	
	@Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean bool) {

		if(this == ModItems.scroll) {
			list.add("In old times, ancient knowledge was just called knowledge.");
		}

		if(this == ModItems.components_wood) {
			list.add("Wood, but long");
		}
		if(this == ModItems.components_scaffold) {
			list.add("If only my self-worth were");
			list.add("as stable as this scaffold");
		}
		if(this == ModItems.components_mechanical) {
			list.add("Stainless steel cogs and");
			list.add("stained steel hinges");
		}
		if(this == ModItems.components_electronics) {
			list.add("Now with 20% more random transistors");
		}
		
		
		if(this == ModItems.province_point) {
			list.add("Province currency");
			/*list.add("it's a literal bitcoin");
			list.add("");
			list.add("...");
			list.add("please don't hit me");*/
		}
	}
}
