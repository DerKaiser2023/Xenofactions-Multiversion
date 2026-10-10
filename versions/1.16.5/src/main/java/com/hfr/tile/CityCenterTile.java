package com.hfr.tile;

import com.hfr.data.TerritoryRecord;
import com.hfr.main.Xenofactions;
import com.hfr.server.FactionGameplay;
import com.hfr.server.FactionRuntime;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.server.ServerWorld;

public final class CityCenterTile extends TileEntity implements ITickableTileEntity, FlagPresentation {
    public String cityId = "", factionId = "", cityName = "";
    public int cityLevel;
    private int color = 0xFFFFFF, pattern = 1;
    @Override public float flagHeight() { return height; }
    @Override public int flagColor() { return color; }
    @Override public int flagPattern() { return pattern; }
    public float height = 1;
    public boolean registered, founding, claimed = true;
    private CompoundNBT retained = new CompoundNBT();
    public CityCenterTile() { super(Xenofactions.CITY_TILE.get()); }
    public boolean validFoundation() { try { FactionRuntime.view(((ServerWorld)level).getServer()).foundation((ServerWorld)level, worldPosition); return true; } catch (IllegalArgumentException invalid) { return false; } }
    @Override public void tick() {
        if (level == null || level.isClientSide) return; ServerWorld w = (ServerWorld)level;
        if (!FactionRuntime.ready(w.getServer())) return;
        // Found only after the native placement survived every mod's place-event cancellation.
        if (founding) {
            founding = false;
            try { FactionGameplay.get(w.getServer()).found(this); }
            catch (IllegalArgumentException | IllegalStateException invalid) { Xenofactions.LOGGER.warn("City founding at {} refused: {}", worldPosition, invalid.getMessage()); w.destroyBlock(worldPosition, false); }
            return;
        }
        FactionGameplay g = FactionRuntime.view(w.getServer());
        if (w.getGameTime() % 20 == 0) { CompoundNBT owner = g.find(factionId); color = owner == null ? 0xFFFFFF : owner.getInt("color"); pattern = owner == null ? 1 : owner.getInt("flag"); w.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
        if (!factionId.isEmpty() && g.find(factionId) == null) { factionId = ""; registered = false; claimed = false; setChanged(); }
        if (!validFoundation()) {
            if (registered) FactionGameplay.get(w.getServer()).removeCity(this, true);
            factionId = ""; registered = false; claimed = false; height = Math.max(0, height - 2F / 600F); setChanged(); return;
        }
        if (!claimed) {
            List<ServerPlayerEntity> players = w.getEntitiesOfClass(ServerPlayerEntity.class, new AxisAlignedBB(worldPosition.offset(-4, -1, -4), worldPosition.offset(5, 2, 5)));
            CompoundNBT capturer = null;
            for (ServerPlayerEntity p : players) { capturer = g.faction(p); if (capturer != null) break; }
            float before = height;
            if (capturer == null) height = Math.max(0, height - 1F / 600F);
            else if (capturer.getString("uuid").equals(factionId)) height = Math.min(1, height + 1F / 600F);
            else { height = Math.max(0, height - 1F / 600F); if (height == 0) FactionGameplay.get(w.getServer()).capturingOwner(this, capturer.getString("uuid")); }
            if (height >= 1 && capturer != null) {
                try { FactionGameplay.get(w.getServer()).capture(this); claimed = true; }
                catch (IllegalArgumentException invalid) { height = 0; }
            }
            if (before != height) setChanged();
        }
    }
    @Override public void load(BlockState state, CompoundNBT tag) {
        super.load(state, tag); retained = tag.copy(); cityId = tag.getString("cityId"); factionId = tag.getString("clow_uuid"); cityName = tag.getString("name"); cityLevel = Math.max(0, Math.min(4, tag.getInt("cityLevel")));
        height = tag.contains("height") ? tag.getFloat("height") : 1; claimed = tag.getBoolean("isClaimed"); registered = tag.getBoolean("nativeRegistered"); founding = tag.getBoolean("nativeFounding");
        height = Float.isFinite(height) ? Math.max(0, Math.min(1, height)) : 0; color = tag.contains("nativeColor") ? tag.getInt("nativeColor") : 0xFFFFFF; pattern = tag.getInt("nativePattern");
    }
    @Override public CompoundNBT save(CompoundNBT tag) {
        tag.merge(retained.copy()); super.save(tag); tag.putString("cityId", cityId); tag.putString("clow_uuid", factionId); tag.putString("name", cityName); tag.putInt("cityLevel", cityLevel); tag.putFloat("height", height);
        tag.putBoolean("isClaimed", claimed); tag.putBoolean("nativeRegistered", registered); tag.putBoolean("nativeFounding", founding); tag.putInt("nativeColor", color); tag.putInt("nativePattern", pattern); return tag;
    }
    @Override public CompoundNBT getUpdateTag() { return save(new CompoundNBT()); }
    @Override public net.minecraft.network.play.server.SUpdateTileEntityPacket getUpdatePacket() { return new net.minecraft.network.play.server.SUpdateTileEntityPacket(worldPosition, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager network, net.minecraft.network.play.server.SUpdateTileEntityPacket packet) { load(getBlockState(), packet.getTag()); }
    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(worldPosition).expandTowards(2, 5, 1); }
}
