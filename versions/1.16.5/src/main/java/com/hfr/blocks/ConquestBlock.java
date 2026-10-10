package com.hfr.blocks;

import com.hfr.main.Xenofactions;
import com.hfr.server.FactionGameplay;
import com.hfr.server.FactionRuntime;
import com.hfr.tile.ConquestTile;
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
import net.minecraft.world.server.ServerWorld;

public final class ConquestBlock extends Block {
    public ConquestBlock() { super(Properties.of(Material.WOOL).strength(.8F, 4)); }
    @Override public boolean hasTileEntity(BlockState state) { return true; }
    @Override public TileEntity createTileEntity(BlockState state, net.minecraft.world.IBlockReader world) { return new ConquestTile(); }
    @Override public void setPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack item) {
        if (!(placer instanceof ServerPlayerEntity)) return; TileEntity t = world.getBlockEntity(pos);
        if (t instanceof ConquestTile) { ConquestTile tile = (ConquestTile)t; tile.ownerId = FactionGameplay.get(((ServerPlayerEntity)placer).getServer()).require((ServerPlayerEntity)placer, 1).getString("uuid"); tile.founding = true; tile.setChanged(); }
    }
    @Override public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (world instanceof ServerWorld) { ConquestTile tile = (ConquestTile)world.getBlockEntity(pos); if (tile != null) { FactionGameplay g = FactionRuntime.view(((ServerWorld)world).getServer()); try { g.checkConquest(tile); } catch (IllegalArgumentException invalid) { world.destroyBlock(pos, false); } } }
        return ActionResultType.SUCCESS;
    }
    @Override public void onRemove(BlockState state, World world, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && world instanceof ServerWorld && FactionRuntime.ready(((ServerWorld)world).getServer())) { TileEntity tile = world.getBlockEntity(pos); if (tile instanceof ConquestTile) FactionGameplay.get(((ServerWorld)world).getServer()).removeConquest((ConquestTile)tile); }
        super.onRemove(state, world, pos, next, moving);
    }
}
