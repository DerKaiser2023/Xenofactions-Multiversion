package com.hfr.server;

import com.hfr.config.GameplayConfig;
import com.hfr.data.FactionSavedData;
import com.hfr.data.TerritoryRecord;
import com.hfr.tile.CityCenterTile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.server.ServerWorld;

/** Per-server transient confirmations and travel; no process-global save authority. */
public final class FactionRuntime {
    private static MinecraftServer owner;
    private static final Map<String, Long> confirmations = new HashMap<>(), homeCooldowns = new HashMap<>();
    private static final Map<UUID, Travel> travel = new HashMap<>();
    private static final List<Runnable> tileOwners = new ArrayList<>();
    private static final Map<String, String> merges = new HashMap<>();
    private static boolean enabled, ignoreOnline, ignoreCooldowns, ignoreState, legacy, guarded;
    private static int economyTicks, maintenanceTicks;
    private static FactionGameplay cached;
    private static long cachedRevision = -1;
    public static void start(MinecraftServer server) {
        stop(); owner = server;
        FactionGameplay.get(server).maintenance(true);
    }
    public static void stop() { owner = null; confirmations.clear(); homeCooldowns.clear(); travel.clear(); tileOwners.clear(); merges.clear(); cached = null; cachedRevision = -1; enabled = ignoreOnline = ignoreCooldowns = ignoreState = legacy = guarded = false; economyTicks = maintenanceTicks = 0; }
    public static boolean ready(MinecraftServer server) { return server != null && server == owner; }
    public static void offerMerge(MinecraftServer server, String source, String target) { check(server); merges.put(target + ":" + source, source); }
    public static boolean hasMerge(MinecraftServer server, String source, String target) { check(server); return merges.containsKey(target + ":" + source); }
    public static void forgetFaction(MinecraftServer server, String faction) { check(server); merges.keySet().removeIf(k -> k.startsWith(faction + ":") || k.endsWith(":" + faction)); homeCooldowns.remove(faction); confirmations.keySet().removeIf(k -> k.contains(faction)); travel.entrySet().removeIf(e -> e.getValue().faction.equals(faction) || e.getValue().target.equals(faction)); }
    private static void check(MinecraftServer server) { if (owner != server) throw new IllegalStateException("Faction gameplay is unavailable for this server."); if (!server.isSameThread()) throw new IllegalStateException("Faction gameplay requires the owning thread."); }
    public static FactionGameplay view(MinecraftServer server) {
        check(server); long revision = FactionSavedData.get(server).revision();
        if (cached == null || cachedRevision != revision) { cached = FactionGameplay.get(server); cachedRevision = revision; } return cached;
    }
    public static boolean confirm(MinecraftServer server, ServerPlayerEntity p, String action) {
        check(server); long now = System.currentTimeMillis(); confirmations.entrySet().removeIf(e -> e.getValue() < now);
        String key = p.getUUID() + ":" + action; Long until = confirmations.remove(key);
        if (until != null && until >= now) return true; confirmations.put(key, now + 10000); return false;
    }
    public static long homeCooldown(MinecraftServer server, String faction) { check(server); return homeCooldowns.getOrDefault(faction, 0L); }
    public static void setHomeCooldown(MinecraftServer server, String faction) { check(server); homeCooldowns.put(faction, System.currentTimeMillis() + 600000); }
    public static void cancel(ServerPlayerEntity player) { travel.remove(player.getUUID()); confirmations.keySet().removeIf(k -> k.startsWith(player.getUUID() + ":")); }
    public static void scheduleHome(MinecraftServer server, ServerPlayerEntity player, String faction, String target, int dim, BlockPos pos, boolean ally) {
        check(server); travel.put(player.getUUID(), new Travel(player, faction, target, dim, pos, ally, ""));
    }
    public static void scheduleWarp(MinecraftServer server, ServerPlayerEntity player, String faction, String name, int dim, BlockPos pos) { check(server); travel.put(player.getUUID(), new Travel(player, faction, faction, dim, pos, false, name)); }
    private static final class Travel {
        final String faction, target, warp; final int dim; final BlockPos pos; final boolean ally;
        final double x, z; final long until; final net.minecraft.util.RegistryKey<net.minecraft.world.World> originDimension;
        Travel(ServerPlayerEntity p, String faction, String target, int dim, BlockPos pos, boolean ally, String warp) { this.faction = faction; this.target = target; this.dim = dim; this.pos = pos; this.ally = ally; this.warp = warp; x = p.getX(); z = p.getZ(); until = System.currentTimeMillis() + 10000; originDimension = p.level.dimension(); }
    }
    public static void tick(MinecraftServer server) {
        check(server); long now = System.currentTimeMillis();
        if (++maintenanceTicks >= GameplayConfig.i("territoryDelay")) { maintenanceTicks = 0; FactionGameplay.get(server).maintenance(false); }
        if (economyTicks-- <= 0) { economyTicks = GameplayConfig.i("prestigeDelay"); FactionGameplay.get(server).economyTick(); }
        for (Iterator<Map.Entry<UUID, Travel>> it = travel.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Travel> entry = it.next(); ServerPlayerEntity p = server.getPlayerList().getPlayer(entry.getKey()); Travel t = entry.getValue();
            if (p == null || !p.isAlive()) { it.remove(); continue; }
            if (p.getX() != t.x || p.getZ() != t.z || p.level.dimension() != t.originDimension) { it.remove(); FactionGameplay.message(p, "Warp aborted because you moved."); continue; }
            if (now < t.until) continue; it.remove(); FactionGameplay g = view(server); CompoundNBT f = g.faction(p), target = g.find(t.target);
            try {
                if (f == null || !FactionGameplay.id(f).equals(t.faction) || target == null || FactionGameplay.bankruptcy(f) >= (t.ally ? 1 : 2)) throw new IllegalArgumentException("Faction state changed.");
                if (!t.warp.isEmpty()) { if (!g.validWarp(f, t.warp, t.dim, t.pos)) throw new IllegalArgumentException("The Warp Tent or destination changed."); }
                else if (t.ally) {
                    if (!g.relation(target, f).getBoolean("ally") || FactionGameplay.bankruptcy(target) >= 1 || target.getInt("allyWarpDim") != t.dim || !t.pos.equals(new BlockPos(target.getInt("allyWarpX"), target.getInt("allyWarpY"), target.getInt("allyWarpZ")))) throw new IllegalArgumentException("The alliance or rally point changed.");
                } else if (!g.validHome(f) || f.getInt("homeDim") != t.dim || !t.pos.equals(FactionGameplay.home(f))) throw new IllegalArgumentException("The capital or home changed.");
                ServerWorld world = g.world(t.dim); TerritoryRecord r = g.at(world, t.pos);
                if (!t.ally && t.warp.isEmpty() && g.owner(r) != f) throw new IllegalArgumentException("The destination is outside your territory.");
                safeTeleport(p, world, t.pos, false);
                if (!t.ally && t.warp.isEmpty()) { p.addEffect(new EffectInstance(Effects.DAMAGE_RESISTANCE, enabled ? 60 : 600, 9)); if (!enabled) p.addEffect(new EffectInstance(Effects.REGENERATION, 600, 4)); }
                g.notify(f, p.getGameProfile().getName() + (t.ally ? " warped to an ally rally point." : !t.warp.isEmpty() ? " warped to " + t.warp + "." : " warped home."));
                if (t.ally) g.notify(target, p.getGameProfile().getName() + " arrived from allied faction " + f.getString("name") + ".");
            } catch (IllegalArgumentException invalid) { FactionGameplay.message(p, "Warp aborted: " + invalid.getMessage()); }
        }
        applyTileOwners(server);
    }
    public static void safeTeleport(ServerPlayerEntity p, ServerWorld w, BlockPos pos, boolean above) {
        double x = pos.getX() + .5, y = pos.getY() + (above ? 1 : 0), z = pos.getZ() + .5;
        w.getChunkAt(pos);
        AxisAlignedBB box = p.getBoundingBox().move(x - p.getX(), y - p.getY(), z - p.getZ());
        BlockPos footing = new BlockPos(x, y - .001, z);
        boolean supported = w.getBlockState(footing).isFaceSturdy(w, footing, net.minecraft.util.Direction.UP)
                || w.getBlockState(footing.below()).isFaceSturdy(w, footing.below(), net.minecraft.util.Direction.UP) && w.noCollision(p, box.move(0, -1, 0)) && w.getFluidState(footing).isEmpty();
        if (!w.getWorldBorder().isWithinBounds(pos) || y < 1 || y + p.getBbHeight() >= w.getMaxBuildHeight() || !w.noCollision(p, box) || !w.getFluidState(new BlockPos(x, y, z)).isEmpty() || !supported) throw new IllegalArgumentException("The destination is obstructed or lacks safe footing.");
        p.stopRiding(); p.teleportTo(w, x, y, z, p.yRot, p.xRot); p.setDeltaMovement(net.minecraft.util.math.vector.Vector3d.ZERO); p.fallDistance = 0;
    }
    public static void deferTileOwner(MinecraftServer server, int dim, BlockPos pos, String city, String faction) {
        check(server); tileOwners.add(() -> { FactionGameplay g = view(server); ServerWorld w = g.world(dim); TerritoryRecord r = g.at(w, pos); if (r == null || !r.cityId.equals(city) || !r.faction.equals(faction)) return; TileEntity te = w.getBlockEntity(pos); if (te instanceof CityCenterTile && ((CityCenterTile)te).cityId.equals(city)) { ((CityCenterTile)te).factionId = faction; te.setChanged(); } });
    }
    public static void afterCommit(MinecraftServer server, Runnable action) { check(server); tileOwners.add(action); }
    public static void applyTileOwners(MinecraftServer server) { check(server); List<Runnable> copy = new ArrayList<>(tileOwners); tileOwners.clear(); for (Runnable r : copy) r.run(); }
    public static boolean warsEnabled(MinecraftServer server) { check(server); return enabled; }
    public static boolean onlineIgnored(MinecraftServer server) { check(server); return ignoreOnline || legacy; }
    public static boolean cooldownsIgnored(MinecraftServer server) { check(server); return ignoreCooldowns; }
    public static boolean stateIgnored(MinecraftServer server) { check(server); return ignoreState || legacy; }
    public static boolean legacyWar(MinecraftServer server) { check(server); return legacy; }
    public static void guard(MinecraftServer server, boolean value) { check(server); guarded = value; }
    public static boolean guarded(MinecraftServer server) { return server == owner && guarded; }
    public static String admin(MinecraftServer server, String action) {
        check(server);
        switch (action) {
        case "warenable": enabled = true; break;
        case "wardisable": enabled = false; break;
        case "ignorewaronlinecheck": ignoreOnline = !ignoreOnline; break;
        case "skipwarcooldowns": case "ignorewarcooldowncheck": ignoreCooldowns = !ignoreCooldowns; break;
        case "ignorewarstatecheck": ignoreState = !ignoreState; break;
        case "enablelegacywar": legacy = !legacy; if (legacy) ignoreOnline = ignoreCooldowns = ignoreState = true; break;
        case "skipwarcooldown": break;
        default: throw new IllegalArgumentException("Unknown administrator action.");
        }
        return "War declarations " + (enabled ? "enabled" : "disabled") + "; ignore online=" + ignoreOnline + ", cooldown=" + ignoreCooldowns + ", state=" + ignoreState + ", legacy=" + legacy + ".";
    }
    private FactionRuntime() { }
}
