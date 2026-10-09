package com.hfr.items;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class AdminSelectionWand extends Item {
    public AdminSelectionWand() { super(new Item.Properties().stacksTo(1)); }
    @Override public boolean canAttackBlock(BlockState state, World world, BlockPos position, PlayerEntity player) { return false; }
}
