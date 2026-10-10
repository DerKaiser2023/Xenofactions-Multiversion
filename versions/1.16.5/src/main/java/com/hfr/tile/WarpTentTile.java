package com.hfr.tile;

import com.hfr.blocks.WarpTentBlock;
import com.hfr.main.Xenofactions;
import com.hfr.server.FactionGameplay;
import com.hfr.server.FactionRuntime;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

public final class WarpTentTile extends TileEntity implements ITickableTileEntity {
    public String ownerId = "", warp = "";
    private CompoundNBT retained = new CompoundNBT();
    public WarpTentTile() { super(Xenofactions.TENT_TILE.get()); }
    public boolean operational() {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if ((x != 0 || z != 0) && !level.isEmptyBlock(worldPosition.offset(x, 4, z))) return false;
            net.minecraft.block.Block foundation = level.getBlockState(worldPosition.offset(x, -1, z)).getBlock();
            if (foundation != Blocks.HOPPER && foundation != Xenofactions.FOUNDATION.get() && foundation != Xenofactions.ASPHALT.get()) return false;
            for (int y = 0; y <= 2; y++) { BlockPos pos = worldPosition.offset(x, y, z); BlockState state = level.getBlockState(pos); if (state.getBlock() != Xenofactions.TENT.get() || !WarpTentBlock.core(pos, state).equals(worldPosition)) return false; }
        }
        return true;
    }
    @Override public void tick() {
        if (!(level instanceof ServerWorld)) return; ServerWorld world = (ServerWorld)level;
        if (!FactionRuntime.ready(world.getServer())) return;
        FactionGameplay view = FactionRuntime.view(world.getServer()); CompoundNBT faction = operational() ? view.owner(view.at(level, worldPosition)) : null;
        String current = faction == null ? "" : faction.getString("uuid");
        if (!current.equals(ownerId) || !warp.isEmpty() && (faction == null || !view.hasWarp(faction, warp))) FactionGameplay.get(world.getServer()).updateTent(this, current);
    }
    public void detach() { if (level instanceof ServerWorld && FactionRuntime.ready(((ServerWorld)level).getServer())) FactionGameplay.get(((ServerWorld)level).getServer()).updateTent(this, ""); }
    @Override public void load(BlockState state, CompoundNBT tag) { super.load(state, tag); retained = tag.copy(); ownerId = tag.getString("nativeOwnerId"); warp = tag.getString("warp"); }
    @Override public CompoundNBT save(CompoundNBT tag) { tag.merge(retained.copy()); super.save(tag); tag.putString("nativeOwnerId", ownerId); tag.putString("warp", warp); return tag; }
}
