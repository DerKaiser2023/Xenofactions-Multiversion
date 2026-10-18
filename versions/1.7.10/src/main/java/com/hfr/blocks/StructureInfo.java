package com.hfr.blocks;

import com.hfr.blocks.clowder.LargeFactionFlag;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/** Placement extents, not visual model bounds or the separately required foundation/clearance. */
public final class StructureInfo {
    private StructureInfo() { }

    public static String size(Block block) {
        if (block instanceof BlockDummyable) {
            int[] bounds = ((BlockDummyable) block).getDimensions(); // U,D,N,S,W,E, inclusive.
            return size(bounds[4] + bounds[5] + 1, bounds[0] + bounds[1] + 1, bounds[2] + bounds[3] + 1);
        }
        if (block instanceof LargeFactionFlag)
            return size(LargeFactionFlag.CLOTH_REACH + 1, LargeFactionFlag.HEIGHT, 1);
        // These structures place one block; surrounding water/foundation/sky are operating requirements.
        if (block == ModBlocks.machine_net || block == ModBlocks.clowder_flag || block == ModBlocks.clowder_conquerer
                || block == ModBlocks.clowder_cap || block == ModBlocks.clowder_flag_big || block == ModBlocks.barricade
                || block == ModBlocks.faction_banner || block == ModBlocks.officer_chest
                || block == ModBlocks.machine_market || block == ModBlocks.faction_market || block == ModBlocks.player_trader)
            return size(1, 1, 1);
        return null;
    }

    public static String size(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemBlock ? size(Block.getBlockFromItem(stack.getItem())) : null;
    }

    private static String size(int width, int height, int depth) {
        return StatCollector.translateToLocalFormatted("hfr.structure.size", width, height, depth);
    }

    public static String axes() { return StatCollector.translateToLocal("hfr.structure.axes"); }
}
