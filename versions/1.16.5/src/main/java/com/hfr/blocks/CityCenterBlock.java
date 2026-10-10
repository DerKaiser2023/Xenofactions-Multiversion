package com.hfr.blocks;

import com.hfr.inventory.CityCenterContainer;
import com.hfr.main.Xenofactions;
import com.hfr.server.FactionGameplay;
import com.hfr.server.FactionRuntime;
import com.hfr.tile.CityCenterTile;
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
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/** Minimum native City Center block; world presence is required for city gameplay. */
public final class CityCenterBlock extends Block {
    public CityCenterBlock() { super(Properties.of(Material.METAL).strength(5, Float.POSITIVE_INFINITY)); }
    @Override public boolean hasTileEntity(BlockState state) { return true; }
    @Override public TileEntity createTileEntity(BlockState state, IBlockReader world) { return new CityCenterTile(); }
    @Override public void setPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack item) {
        if (world.isClientSide || !(placer instanceof ServerPlayerEntity)) return;
        TileEntity t = world.getBlockEntity(pos); if (!(t instanceof CityCenterTile)) return;
        CityCenterTile tile = (CityCenterTile)t;
        tile.cityName = item.getOrCreateTag().getString("cityName");
        tile.factionId = FactionGameplay.get(((ServerPlayerEntity)placer).getServer()).require((ServerPlayerEntity)placer, 1).getString("uuid");
        tile.founding = true; tile.setChanged();
    }
    @Override public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (world.isClientSide) return ActionResultType.SUCCESS;
        TileEntity t = world.getBlockEntity(pos); if (!(t instanceof CityCenterTile)) return ActionResultType.FAIL;
        CityCenterTile tile = (CityCenterTile)t;
        if (tile.factionId.isEmpty() || !tile.validFoundation()) { ((ServerWorld)world).destroyBlock(pos, false); return ActionResultType.CONSUME; }
        if (player instanceof ServerPlayerEntity && !player.isShiftKeyDown()) CityCenterContainer.open((ServerPlayerEntity)player, tile, false);
        return ActionResultType.CONSUME;
    }
    @Override public void onRemove(BlockState state, World world, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && !world.isClientSide && world instanceof ServerWorld && !FactionRuntime.guarded(((ServerWorld)world).getServer())) {
            TileEntity t = world.getBlockEntity(pos); if (t instanceof CityCenterTile) FactionGameplay.get(((ServerWorld)world).getServer()).removeCity((CityCenterTile)t, true);
        }
        super.onRemove(state, world, pos, next, moving);
    }
}
