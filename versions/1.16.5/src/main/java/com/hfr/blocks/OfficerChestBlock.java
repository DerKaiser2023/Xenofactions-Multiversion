package com.hfr.blocks;

import com.hfr.main.Xenofactions;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;

public final class OfficerChestBlock extends ChestBlock {
    public OfficerChestBlock() { super(Properties.of(Material.WOOD).strength(5, 10), () -> Xenofactions.OFFICER_CHEST_TILE.get()); }
    // The reference has one 27-slot inventory. Vanilla joining would expose a claimed half through an adjacent half.
    @Override public BlockState getStateForPlacement(net.minecraft.item.BlockItemUseContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(TYPE, net.minecraft.state.properties.ChestType.SINGLE)
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == net.minecraft.fluid.Fluids.WATER);
    }
    @Override public BlockState updateShape(BlockState state, net.minecraft.util.Direction side, BlockState neighbor, net.minecraft.world.IWorld world, net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.BlockPos other) {
        return super.updateShape(state, side, neighbor, world, pos, other).setValue(TYPE, net.minecraft.state.properties.ChestType.SINGLE);
    }
    @Override public TileEntity createTileEntity(BlockState state, net.minecraft.world.IBlockReader world) { return new com.hfr.tile.OfficerChestTile(); }
}
