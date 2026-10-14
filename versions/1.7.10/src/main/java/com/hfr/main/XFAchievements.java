package com.hfr.main;

import com.hfr.blocks.ModBlocks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.AchievementList;
import net.minecraftforge.common.AchievementPage;

/** Acquisition achievements backed by Minecraft's player statistics. */
public final class XFAchievements {

	public static final Achievement windmill = new Achievement("achievement.hfr.windmill", "hfr.windmill",
			-1, 3, new ItemStack(ModBlocks.machine_grainmill), AchievementList.acquireIron).registerStat();
	public static final Achievement fishingNet = new Achievement("achievement.hfr.fishingNet", "hfr.fishingNet",
			3, 3, new ItemStack(ModBlocks.machine_net), AchievementList.acquireIron).registerStat();

	private XFAchievements() { }

	public static void register() {
		AchievementPage.registerAchievementPage(new AchievementPage("Xenofactions", windmill, fishingNet));
	}

	public static void onAcquired(EntityPlayer player, ItemStack stack) {
		if(player.worldObj.isRemote || stack == null)
			return;
		// FML's successful pickup event sees the remaining entity stack, which
		// can have size zero after the items have entered the player's inventory.
		if(stack.getItem() == Item.getItemFromBlock(ModBlocks.machine_grainmill))
			player.triggerAchievement(windmill);
		else if(stack.getItem() == Item.getItemFromBlock(ModBlocks.machine_net))
			player.triggerAchievement(fishingNet);
	}
}
