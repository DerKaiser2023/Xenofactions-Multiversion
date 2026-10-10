package com.hfr.tile;

import com.hfr.main.Xenofactions;
import com.hfr.server.FactionGameplay;
import com.hfr.server.FactionRuntime;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.server.ServerWorld;

public final class ConquestTile extends TileEntity implements ITickableTileEntity, FlagPresentation {
    public String ownerId = "", claimId = "";
    public float height;
    public boolean founding, registered;
    private int color = 0xFFFFFF, pattern = 1;
    @Override public float flagHeight() { return height; }
    @Override public int flagColor() { return color; }
    @Override public int flagPattern() { return pattern; }
    private CompoundNBT retained = new CompoundNBT();
    public ConquestTile() { super(Xenofactions.CONQUEST_TILE.get()); }
    @Override public void tick() {
        if (!(level instanceof ServerWorld)) return; ServerWorld world = (ServerWorld)level; if (!FactionRuntime.ready(world.getServer())) return;
        FactionGameplay view = FactionRuntime.view(world.getServer());
        if (view.find(ownerId) == null) { world.destroyBlock(worldPosition, false); return; }
        if (world.getGameTime() % 20 == 0) { CompoundNBT owner = view.find(ownerId); color = owner.getInt("color"); pattern = owner.getInt("flag"); world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
        if (founding) { founding = false; try { FactionGameplay.get(world.getServer()).foundConquest(this); } catch (IllegalArgumentException invalid) { world.destroyBlock(worldPosition, false); } return; }
        if (height >= 1) return;
        boolean present = false;
        for (ServerPlayerEntity player : world.getEntitiesOfClass(ServerPlayerEntity.class, new AxisAlignedBB(worldPosition.getX() + .5 - 32, worldPosition.getY() - 2, worldPosition.getZ() + .5 - 32, worldPosition.getX() + .5 + 32, worldPosition.getY() + 4, worldPosition.getZ() + .5 + 32))) {
            CompoundNBT faction = view.faction(player); if (faction != null && faction.getString("uuid").equals(ownerId)) { present = true; break; }
        }
        height = Math.max(0, Math.min(1, height + (present ? 1 : -1) / 800F)); setChanged();
        if (height >= 1) { try { FactionGameplay.get(world.getServer()).conquer(this); } catch (IllegalArgumentException invalid) { world.destroyBlock(worldPosition, false); } }
    }
    @Override public void load(BlockState state, CompoundNBT tag) { super.load(state, tag); retained = tag.copy(); ownerId = tag.getString("clow_uuid"); claimId = tag.getString("nativeClaimId"); height = tag.getFloat("height"); height = Float.isFinite(height) ? Math.max(0, Math.min(1, height)) : 0; founding = tag.getBoolean("nativeFounding"); registered = tag.getBoolean("nativeRegistered"); color = tag.contains("nativeColor") ? tag.getInt("nativeColor") : 0xFFFFFF; pattern = tag.getInt("nativePattern"); }
    @Override public CompoundNBT save(CompoundNBT tag) { tag.merge(retained.copy()); super.save(tag); tag.putString("clow_uuid", ownerId); tag.putString("nativeClaimId", claimId); tag.putFloat("height", height); tag.putBoolean("nativeFounding", founding); tag.putBoolean("nativeRegistered", registered); tag.putInt("nativeColor", color); tag.putInt("nativePattern", pattern); return tag; }
    @Override public CompoundNBT getUpdateTag() { return save(new CompoundNBT()); }
    @Override public net.minecraft.network.play.server.SUpdateTileEntityPacket getUpdatePacket() { return new net.minecraft.network.play.server.SUpdateTileEntityPacket(worldPosition, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager network, net.minecraft.network.play.server.SUpdateTileEntityPacket packet) { load(getBlockState(), packet.getTag()); }
    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(worldPosition).expandTowards(2, 5, 1); }
}
