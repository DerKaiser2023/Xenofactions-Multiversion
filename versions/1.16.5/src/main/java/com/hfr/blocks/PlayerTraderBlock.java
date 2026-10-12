package com.hfr.blocks;

import com.hfr.shop.PlayerTraderService;
import com.hfr.tile.PlayerTraderTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;

public final class PlayerTraderBlock extends Block {
    public PlayerTraderBlock() { super(Properties.of(Material.METAL).strength(3, 10)); }
    @Override public boolean hasTileEntity(BlockState state) { return true; }
    @Override public TileEntity createTileEntity(BlockState state, net.minecraft.world.IBlockReader world) { return new PlayerTraderTile(); }
    @Override public void setPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        TileEntity raw = world.getBlockEntity(pos);
        if (!world.isClientSide && placer instanceof PlayerEntity && raw instanceof PlayerTraderTile) {
            PlayerTraderTile tile = (PlayerTraderTile)raw; tile.owner = placer.getUUID().toString(); tile.setChanged();
        }
    }
    @Override public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (!world.isClientSide && player instanceof ServerPlayerEntity && world.getBlockEntity(pos) instanceof PlayerTraderTile)
            PlayerTraderService.open((ServerPlayerEntity)player, (PlayerTraderTile)world.getBlockEntity(pos));
        return ActionResultType.SUCCESS;
    }
    @Override public void onRemove(BlockState state, World world, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && !world.isClientSide && world.getBlockEntity(pos) instanceof PlayerTraderTile) {
            PlayerTraderTile tile = (PlayerTraderTile)world.getBlockEntity(pos);
            for (ItemStack stack : tile.stock) if (stack != null) popResource(world, pos, stack.copy());
            for (ItemStack stack : tile.payments) if (stack != null) popResource(world, pos, stack.copy());
            tile.stock = new ItemStack[9]; tile.payments = new ItemStack[9];
        }
        super.onRemove(state, world, pos, next, moving);
    }
}
