package com.hfr.items;

import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** The reference's free, unlimited-durability multitool is also the explicit wartime container bypass. */
public final class RaidingMace extends Item {
    public RaidingMace() { super(new Properties().tab(com.hfr.main.Xenofactions.TAB).stacksTo(1)); }
    @Override public float getDestroySpeed(ItemStack stack, BlockState state) { try { return Math.max(state.getDestroySpeed(null, net.minecraft.util.math.BlockPos.ZERO) * 10, 5); } catch (NullPointerException contextual) { return 5; } }
    @Override public boolean isCorrectToolForDrops(BlockState state) { return true; }
    @Override public int getHarvestLevel(ItemStack stack, net.minecraftforge.common.ToolType tool, net.minecraft.entity.player.PlayerEntity player, BlockState state) { return 1000; }
}
