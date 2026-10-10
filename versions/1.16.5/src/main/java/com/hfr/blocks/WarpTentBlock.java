package com.hfr.blocks;

import com.hfr.main.Xenofactions;
import com.hfr.tile.WarpTentTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The reference tent reserves a five by five by three multiblock, with a core two blocks behind its placement face. */
public final class WarpTentBlock extends Block {
    public static final IntegerProperty X = IntegerProperty.create("x", 0, 4), Y = IntegerProperty.create("y", 0, 2), Z = IntegerProperty.create("z", 0, 4);
    private boolean removing;
    public WarpTentBlock() { super(Properties.of(Material.WOOL).strength(1, 1.5F)); registerDefaultState(stateDefinition.any().setValue(X, 2).setValue(Y, 0).setValue(Z, 2)); }
    @Override protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) { builder.add(X, Y, Z); }
    public static BlockPos core(BlockPos pos, BlockState state) { return pos.offset(2 - state.getValue(X), -state.getValue(Y), 2 - state.getValue(Z)); }
    private static boolean core(BlockState state) { return state.getValue(X) == 2 && state.getValue(Y) == 0 && state.getValue(Z) == 2; }
    @Override public boolean hasTileEntity(BlockState state) { return core(state); }
    @Override public TileEntity createTileEntity(BlockState state, net.minecraft.world.IBlockReader world) { return new WarpTentTile(); }
    @Override public BlockState getStateForPlacement(BlockItemUseContext context) {
        net.minecraft.util.Direction facing = context.getHorizontalDirection();
        return defaultBlockState().setValue(X, 2 - facing.getStepX() * 2).setValue(Z, 2 - facing.getStepZ() * 2);
    }
    @Override public void setPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity player, ItemStack stack) {
        if (world.isClientSide) return; BlockPos center = core(pos, state);
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 2; y++) for (int z = -2; z <= 2; z++) {
            BlockPos part = center.offset(x, y, z); if (part.equals(pos)) continue;
            world.setBlock(part, defaultBlockState().setValue(X, x + 2).setValue(Y, y).setValue(Z, z + 2), 3);
        }
    }
    @Override public void onRemove(BlockState state, World world, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && !world.isClientSide && !removing) {
            BlockPos center = core(pos, state); TileEntity tile = world.getBlockEntity(center);
            if (tile instanceof WarpTentTile) ((WarpTentTile)tile).detach();
            removing = true;
            try {
                for (int x = -2; x <= 2; x++) for (int y = 0; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                    BlockPos part = center.offset(x, y, z); BlockState found = world.getBlockState(part);
                    if (!part.equals(pos) && found.getBlock() == this && core(part, found).equals(center)) world.removeBlock(part, false);
                }
            } finally { removing = false; }
        }
        super.onRemove(state, world, pos, next, moving);
    }
}
