package com.hfr.server;

import com.hfr.config.FoundationConfig;
import com.hfr.config.GameplayConfig;
import com.hfr.data.CityLevel;
import com.hfr.data.TerritoryKey;
import com.hfr.data.TerritoryRecord;
import com.hfr.main.Xenofactions;
import com.hfr.tile.CityCenterTile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayer;

/** Owning-thread gameplay transactions. FactionSavedData remains the sole persistent authority. */
public final class FactionGameplay {
    final MinecraftServer server;
    final FactionService members;
    final List<CompoundNBT> factions = new ArrayList<>();
    final Map<TerritoryKey, TerritoryRecord> territory;
    private final CompoundNBT rootEdits;
    private boolean territoryChanged;
    private final List<Runnable> committedUpdates = new ArrayList<>();
    private FactionGameplay(MinecraftServer server) {
        this.server = server; members = FactionService.get(server); rootEdits = members.state.copy();
        for (int i = 0; i < members.state.getInt("clowderCount"); i++) factions.add(FactionService.record(members.state, i));
        territory = TerritoryRecord.read(members.state, name -> { CompoundNBT f = find(name); return f == null ? "" : id(f); });
    }
    public static FactionGameplay get(MinecraftServer server) { return new FactionGameplay(server); }
    static String id(CompoundNBT f) { return f == null ? "" : f.getString("uuid"); }
    public CompoundNBT find(String nameOrId) {
        if (nameOrId == null || nameOrId.isEmpty()) return null;
        for (CompoundNBT f : factions) if (id(f).equals(nameOrId) || FactionService.canonical(f.getString("name")).equalsIgnoreCase(FactionService.canonical(nameOrId))) return f;
        return null;
    }
    public CompoundNBT faction(ServerPlayerEntity player) {
        CompoundNBT match = null;
        try { for (CompoundNBT f : factions) if (members.memberIndex(f, player) >= 0) { if (match != null) return null; match = f; } }
        catch (IllegalArgumentException ambiguous) { return null; }
        return match;
    }
    public CompoundNBT require(ServerPlayerEntity player, int rank) { return factions.get(members.require(player, rank)); }
    public int rank(ServerPlayerEntity player) {
        CompoundNBT f = faction(player); if (f == null) return 0;
        return com.hfr.clowder.FactionRole.valueOf(f.getList("identityMembers", 10).getCompound(members.memberIndex(f, player)).getString("role")).getPermissionLevel();
    }
    public static int dimension(World world) {
        Integer dim = FoundationConfig.legacyId(world.dimension().location());
        if (dim == null) throw new IllegalArgumentException("Configure an explicit legacy dimension mapping for " + world.dimension().location()); return dim;
    }
    public ServerWorld world(int dimension) {
        ResourceLocation key = null;
        for (String entry : FoundationConfig.DIMENSIONS.get()) {
            String[] parts = entry.split("=", -1);
            if (Integer.parseInt(parts[0]) != dimension) continue;
            if (key != null) throw new IllegalArgumentException("Ambiguous legacy dimension mapping: " + dimension);
            key = new ResourceLocation(parts[1]);
        }
        if (key == null) throw new IllegalArgumentException("No native mapping for legacy dimension " + dimension);
        ServerWorld world = server.getLevel(RegistryKey.create(Registry.DIMENSION_REGISTRY, key));
        if (world == null) throw new IllegalArgumentException("The destination dimension is unavailable."); return world;
    }
    public TerritoryRecord at(World world, BlockPos position) {
        Integer dim = FoundationConfig.legacyId(world.dimension().location());
        return dim == null ? null : territory.get(TerritoryKey.lookup(dim, position.getX(), position.getZ()));
    }
    public TerritoryRecord territoryAt(TerritoryKey key) { return territory.get(key); }
    public CompoundNBT owner(TerritoryRecord r) { return r == null || r.zone != 3 ? null : find(r.faction.isEmpty() ? r.legacyOwner : r.faction); }
    public int zone(TerritoryRecord r) { return r == null || r.zone == 3 && owner(r) == null ? 2 : r.zone; }
    public List<TerritoryRecord> cities(CompoundNBT f) {
        Map<String, TerritoryRecord> cities = new LinkedHashMap<>();
        for (TerritoryRecord r : territory.values()) if (r.city() && owner(r) == f) cities.put(r.cityId, r);
        return new ArrayList<>(cities.values());
    }
    private int cityDimension(TerritoryRecord city) {
        for (Map.Entry<TerritoryKey, TerritoryRecord> e : territory.entrySet()) if (e.getValue() == city) return e.getKey().dimension;
        throw new IllegalArgumentException("The city has no territory.");
    }
    public TerritoryRecord city(CompoundNBT f, String nameOrId) {
        for (TerritoryRecord r : cities(f)) if (r.cityId.equals(nameOrId) || r.name.equalsIgnoreCase(nameOrId)) return r;
        throw new IllegalArgumentException("Unknown owned city: " + nameOrId);
    }
    public void commit() {
        CompoundNBT next = rootEdits.copy();
        for (int i = factions.size(); i < next.getInt("clowderCount"); i++) FactionService.put(next, i, new CompoundNBT());
        next.putInt("clowderCount", factions.size());
        for (int i = 0; i < factions.size(); i++) { mirrorRelations(factions.get(i)); FactionService.put(next, i, factions.get(i)); }
        if (territoryChanged) TerritoryRecord.write(next, territory, uuid -> { CompoundNBT f = find(uuid); return f == null ? "" : f.getString("name"); });
        if (!next.equals(members.state)) members.commit(next);
        for (Runnable update : committedUpdates) update.run(); committedUpdates.clear();
    }
    public void notify(CompoundNBT f, String message) {
        if (f == null) return;
        for (ServerPlayerEntity p : server.getPlayerList().getPlayers()) if (members.memberIndex(f, p) >= 0) message(p, message);
    }
    public static void message(ServerPlayerEntity player, String text) { player.sendMessage(new StringTextComponent(text), Util.NIL_UUID); }
    private void broadcast(String message) { server.getPlayerList().broadcastMessage(new StringTextComponent(message), net.minecraft.util.text.ChatType.SYSTEM, Util.NIL_UUID); }
    public boolean capital(CompoundNBT f, int dim, BlockPos pos) {
        TerritoryRecord r = territory.get(TerritoryKey.lookup(dim, pos.getX(), pos.getZ()));
        return f != null && f.getBoolean("capitalDesignated") && r != null && r.city() && id(f).equals(r.faction) && r.cityId.equals(f.getString("capitalCityId"));
    }
    public boolean validHome(CompoundNBT f) { return f != null && f.getBoolean("homeSet") && GameplayConfig.claims(f.getInt("homeDim")) && capital(f, f.getInt("homeDim"), home(f)); }
    public static BlockPos home(CompoundNBT f) { return new BlockPos(f.getInt("homeX"), f.getInt("homeY"), f.getInt("homeZ")); }
    private void reconcile(CompoundNBT f) {
        List<TerritoryRecord> cities = cities(f); f.putInt("citiesFounded", cities.size());
        if (f.getBoolean("capitalDesignated")) {
            boolean exists = false;
            for (TerritoryRecord c : cities) if (c.cityId.equals(f.getString("capitalCityId"))) exists = true;
            if (!exists) { f.putString("capitalCityId", ""); f.putBoolean("capitalDesignated", false); f.putBoolean("homeSet", false); clearTerminal(f); }
        }
        if (!validHome(f)) { f.putBoolean("homeSet", false); if (grace(f)) { f.putLong("buildGraceUntil", 0); notify(f, "Build grace ended because the capital home is no longer valid."); } }
    }
    private void clearTerminal(CompoundNBT f) {
        ListNBT terminals = rootEdits.getList("FactionMarketTerminals", 10);
        for (int i = terminals.size() - 1; i >= 0; i--) if (id(f).equals(terminals.getCompound(i).getString("factionId"))) terminals.remove(i);
    }
    public String capitalCommand(ServerPlayerEntity player, String name) {
        CompoundNBT f = require(player, name.isEmpty() ? 1 : 3);
        if (name.isEmpty()) {
            String capital = "Not designated";
            for (TerritoryRecord c : cities(f)) if (c.cityId.equals(f.getString("capitalCityId"))) capital = c.name;
            return "Capital: " + capital + ". Cooldown: " + Math.max(0, (f.getLong("capitalChangeAfter") - System.currentTimeMillis() + 59999) / 60000) + " minutes.";
        }
        TerritoryRecord city = city(f, name);
        if (city.cityId.equals(f.getString("capitalCityId"))) throw new IllegalArgumentException("That city is already the capital.");
        if (f.getLong("capitalChangeAfter") > System.currentTimeMillis()) throw new IllegalArgumentException("The capital change cooldown is still active.");
        f.putString("capitalCityId", city.cityId); f.putBoolean("capitalDesignated", true);
        f.putLong("capitalChangeAfter", System.currentTimeMillis() + GameplayConfig.hours("capitalChangeCooldownHours"));
        reconcile(f); clearTerminal(f); commit(); notify(f, "Capital changed to " + city.name + ". Set a home there."); return "Capital updated.";
    }
    public static Set<TerritoryKey> shape(int dim, BlockPos center, int radius) {
        Set<TerritoryKey> result = new LinkedHashSet<>();
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) if (x * x + z * z < radius * radius)
            result.add(TerritoryKey.generated(dim, center.getX() + x * 16, center.getZ() + z * 16));
        return result;
    }
    public void foundation(ServerWorld world, BlockPos pos) {
        if (pos.getY() < 45 || pos.getY() > 200) throw new IllegalArgumentException("City Centers must be between Y 45 and Y 200.");
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos foot = pos.offset(x, -1, z);
            if (!world.canSeeSky(pos.offset(x, 1, z)) || !world.getBlockState(foot).isFaceSturdy(world, foot, Direction.UP))
                throw new IllegalArgumentException("A City Center needs a solid, sky-accessible 5 by 5 foundation.");
        }
    }
    private void placement(int dim, BlockPos pos, int level, String ignored, boolean moving) {
        if (!GameplayConfig.claims(dim)) throw new IllegalArgumentException("City claims are disabled in this dimension.");
        TerritoryKey origin = moving ? new TerritoryKey(dim, pos.getX() >> 4, pos.getZ() >> 4) : TerritoryKey.generated(dim, pos.getX(), pos.getZ());
        Set<String> seen = new LinkedHashSet<>();
        for (Map.Entry<TerritoryKey, TerritoryRecord> entry : territory.entrySet()) { TerritoryRecord r = entry.getValue(); if (entry.getKey().dimension != dim || !r.city() || r.cityId.equals(ignored) || !seen.add(r.cityId)) continue;
            TerritoryKey other = TerritoryKey.generated(dim, r.center.getX(), r.center.getZ());
            if (Math.hypot(origin.x - other.x, origin.z - other.z) < GameplayConfig.n("minimumCitySpacingChunks")) throw new IllegalArgumentException("City Center is too close to " + r.name + ".");
        }
        for (TerritoryKey k : shape(dim, pos, (int)GameplayConfig.level("cityRadii", level))) {
            TerritoryRecord r = territory.get(k);
            if (r != null && zone(r) != 2 && !r.cityId.equals(ignored)) throw new IllegalArgumentException("The city radius intersects another city, faction claim or protected zone.");
        }
    }
    private void rebuild(TerritoryRecord city, int dim, BlockPos destination, int level) {
        Set<TerritoryKey> expected = shape(dim, destination, (int)GameplayConfig.level("cityRadii", level));
        Map<TerritoryKey, TerritoryRecord> retained = new LinkedHashMap<>(); for (Map.Entry<TerritoryKey, TerritoryRecord> e : territory.entrySet()) if (e.getValue().cityId.equals(city.cityId)) retained.put(e.getKey(), e.getValue().copy());
        for (TerritoryKey k : expected) { TerritoryRecord existing = territory.get(k); if (existing != null && zone(existing) != 2 && !existing.cityId.equals(city.cityId)) throw new IllegalArgumentException("City territory overlaps occupied land."); }
        territory.entrySet().removeIf(e -> e.getValue().cityId.equals(city.cityId));
        for (TerritoryKey k : expected) { TerritoryRecord r = retained.getOrDefault(k, new TerritoryRecord()); r.zone = 3; r.conquest = false; r.center = destination; r.cityId = city.cityId; r.name = city.name; r.faction = city.faction; r.level = level; territory.put(k, r); }
        territoryChanged = true;
    }
    private String cityName(String name, String ignored) {
        name = name.trim();
        if (name.length() < GameplayConfig.i("claimNameMinLength") || name.length() > GameplayConfig.i("claimNameMaxLength") || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid city name length or characters.");
        if (GameplayConfig.b("claimNameRequireUnique")) for (TerritoryRecord r : territory.values()) if (r.city() && r.name.equalsIgnoreCase(name) && !r.cityId.equals(ignored)) throw new IllegalArgumentException("That city name is already taken.");
        return name;
    }
    private float foundingCost(CompoundNBT f) { int count = cities(f).size(); return count == 0 ? 0 : GameplayConfig.level("cityUpgradeCosts", 0) * (float)(1 + count * GameplayConfig.n("cityFoundingCostGrowth")); }
    private void foundingCapacity(CompoundNBT f) {
        float cost = foundingCost(f), upkeep = GameplayConfig.level("cityUpkeep", 0);
        if (activeWars(f) || pending(f)) throw new IllegalArgumentException("City Centers cannot be founded during war or a pending move.");
        if (f.getFloat("prestige") < cost || !cities(f).isEmpty() && f.getFloat("prestigeReq") + upkeep > f.getFloat("prestige") - cost) throw new IllegalArgumentException("Founding requires " + cost + " prestige and " + upkeep + " upkeep capacity.");
    }
    public String claim(ServerPlayerEntity player, String name) {
        CompoundNBT f = require(player, 1); name = cityName(name, ""); foundingCapacity(f);
        if (!GameplayConfig.b("enableSurvivalRecipes")) for (ItemStack s : player.inventory.items) if (s.getItem() == Xenofactions.CITY_ITEM.get()) throw new IllegalArgumentException("You already have a City Center in your inventory.");
        ItemStack stack = new ItemStack(Xenofactions.CITY_ITEM.get()); stack.getOrCreateTag().putString("cityName", name); stack.setHoverName(new StringTextComponent("City Center: " + name));
        if (!player.inventory.add(stack)) throw new IllegalArgumentException("Make room for the City Center first."); player.inventory.setChanged();
        return "Place the City Center to found " + name + ". Founding cost: " + foundingCost(f) + " prestige.";
    }
    public void checkFounding(ServerPlayerEntity player, ServerWorld world, BlockPos pos, String name) {
        CompoundNBT f = require(player, 1); cityName(name, ""); foundingCapacity(f); foundation(world, pos); placement(dimension(world), pos, 0, "", false);
        if (!canEdit(player, pos, "BUILD", world)) throw new IllegalArgumentException("You cannot place a City Center here.");
    }
    public void found(CityCenterTile tile) {
        CompoundNBT f = find(tile.factionId); if (f == null) throw new IllegalArgumentException("The founding faction no longer exists.");
        ServerWorld world = (ServerWorld)tile.getLevel(); int dim = dimension(world);
        tile.cityName = cityName(tile.cityName, ""); foundingCapacity(f); foundation(world, tile.getBlockPos()); placement(dim, tile.getBlockPos(), 0, "", false);
        float cost = foundingCost(f); addPrestige(f, -cost);
        tile.cityId = UUID.randomUUID().toString(); tile.cityLevel = 0;
        TerritoryRecord city = new TerritoryRecord(); city.zone = 3; city.faction = id(f); city.cityId = tile.cityId; city.name = tile.cityName;
        rebuild(city, dim, tile.getBlockPos(), 0);
        infrastructure(f, (float)GameplayConfig.n("cityCenterPrestigeGeneration"), GameplayConfig.level("cityUpkeep", 0));
        f.putInt("flags", f.getInt("flags") + 1);
        if (!f.getBoolean("capitalDesignated")) { f.putString("capitalCityId", tile.cityId); f.putBoolean("capitalDesignated", true); }
        reconcile(f); commit(); tile.registered = true; tile.setChanged(); notify(f, "Founded " + tile.cityName + ".");
    }
    public void capture(CityCenterTile tile) {
        CompoundNBT f = find(tile.factionId); if (f == null) throw new IllegalArgumentException("No capturing faction.");
        ServerWorld w = (ServerWorld)tile.getLevel(); foundation(w, tile.getBlockPos());
        if (tile.cityId.isEmpty()) tile.cityId = UUID.randomUUID().toString();
        TerritoryRecord city = new TerritoryRecord(); city.cityId = tile.cityId; city.name = tile.cityName; city.faction = id(f);
        rebuild(city, dimension(w), tile.getBlockPos(), tile.cityLevel);
        if (!tile.registered) { infrastructure(f, (float)GameplayConfig.n("cityCenterPrestigeGeneration"), GameplayConfig.level("cityUpkeep", tile.cityLevel)); f.putInt("flags", f.getInt("flags") + 1); }
        reconcile(f); commit(); tile.registered = true; tile.setChanged(); notify(f, "Captured City Center " + tile.cityName + ".");
    }
    public void capturingOwner(CityCenterTile tile, String uuid) {
        CompoundNBT former = find(tile.factionId), next = find(uuid); if (next == null) throw new IllegalArgumentException("The capturing faction no longer exists.");
        if (tile.registered && former != null) {
            infrastructure(former, -(float)GameplayConfig.n("cityCenterPrestigeGeneration"), -GameplayConfig.level("cityUpkeep", tile.cityLevel)); former.putInt("flags", Math.max(0, former.getInt("flags") - 1)); former.putFloat("prestige", (float)(Math.floor(former.getFloat("prestige") * .95 * 10) / 10));
            if (territory.entrySet().removeIf(e -> e.getValue().cityId.equals(tile.cityId))) territoryChanged = true;
            clearMove(former); reconcile(former);
        }
        infrastructure(next, (float)GameplayConfig.n("cityCenterPrestigeGeneration"), GameplayConfig.level("cityUpkeep", tile.cityLevel)); next.putInt("flags", next.getInt("flags") + 1); commit();
        tile.factionId = uuid; tile.registered = true; tile.setChanged();
    }
    public CityCenterTile cityTile(TerritoryRecord r) {
        ServerWorld w = world(cityDimension(r)); w.getChunkAt(r.center);
        TileEntity t = w.getBlockEntity(r.center);
        if (!(t instanceof CityCenterTile) || !((CityCenterTile)t).cityId.equals(r.cityId) || !((CityCenterTile)t).factionId.equals(r.faction)) throw new IllegalArgumentException("The City Center no longer matches this city.");
        return (CityCenterTile)t;
    }
    public String renameCity(ServerPlayerEntity player, String name) {
        CompoundNBT f = require(player, GameplayConfig.b("claimRenameOfficersAllowed") ? 2 : 3); TerritoryRecord city = at(player.level, player.blockPosition());
        if (city == null || !city.city() || !id(f).equals(city.faction)) throw new IllegalArgumentException("Stand in one of your city claims.");
        name = cityName(name, city.cityId); CityCenterTile tile = cityTile(city);
        for (TerritoryRecord r : territory.values()) if (r.cityId.equals(city.cityId)) r.name = name;
        territoryChanged = true; commit(); tile.cityName = name; tile.setChanged(); return "City renamed to " + name + ".";
    }
    public String upgrade(ServerPlayerEntity player) {
        CompoundNBT f = require(player, 2); TerritoryRecord city = at(player.level, player.blockPosition());
        if (city == null || !city.city() || !city.faction.equals(id(f))) throw new IllegalArgumentException("Stand inside one of your city claims.");
        CityCenterTile tile = cityTile(city); int next = tile.cityLevel + 1;
        if (next > 4) throw new IllegalArgumentException("This city is already a Capital.");
        float cost = GameplayConfig.level("cityUpgradeCosts", next), upkeep = GameplayConfig.level("cityUpkeep", next);
        if (!FactionRuntime.confirm(server, player, "upgrade:" + id(f) + ":" + city.cityId + ":" + next)) return "Upgrade " + city.name + " to " + CityLevel.at(next).display + " for " + cost + " prestige and " + upkeep + " upkeep. Repeat /c city upgrade within 10 seconds.";
        if (f.getFloat("prestige") < cost || f.getFloat("prestigeReq") - GameplayConfig.level("cityUpkeep", tile.cityLevel) + upkeep > f.getFloat("prestige") - cost) throw new IllegalArgumentException("Insufficient prestige or upkeep capacity.");
        placement(cityDimension(city), city.center, next, city.cityId, false);
        rebuild(city, cityDimension(city), city.center, next); addPrestige(f, -cost); infrastructure(f, 0, upkeep - GameplayConfig.level("cityUpkeep", tile.cityLevel));
        reconcile(f); commit(); tile.cityLevel = next; tile.setChanged(); return "City upgraded to " + CityLevel.at(next).display + ".";
    }
    public void removeCity(CityCenterTile tile, boolean penalty) {
        if (!tile.registered || tile.cityId.isEmpty()) return;
        CompoundNBT f = find(tile.factionId); boolean removed = territory.entrySet().removeIf(e -> e.getValue().cityId.equals(tile.cityId));
        if (removed) territoryChanged = true;
        if (f != null) {
            infrastructure(f, -(float)GameplayConfig.n("cityCenterPrestigeGeneration"), -GameplayConfig.level("cityUpkeep", tile.cityLevel)); f.putInt("flags", Math.max(0, f.getInt("flags") - 1));
            if (penalty) f.putFloat("prestige", (float)(Math.floor(f.getFloat("prestige") * .95 * 10) / 10));
            if (tile.cityId.equals(f.getString("relocationCityId"))) clearMove(f); reconcile(f);
        }
        commit();
    }
    public void transfer(TerritoryRecord city, CompoundNBT recipient) { transfer(city, recipient, true); }
    private void transfer(TerritoryRecord city, CompoundNBT recipient, boolean penalty) {
        CompoundNBT former = owner(city); if (former == recipient) return;
        int dim = cityDimension(city); CityCenterTile tile = cityTile(city);
        for (TerritoryRecord r : territory.values()) if (r.cityId.equals(city.cityId)) r.faction = id(recipient);
        territoryChanged = true;
        if (former != null) { clearMove(former); infrastructure(former, -(float)GameplayConfig.n("cityCenterPrestigeGeneration"), -GameplayConfig.level("cityUpkeep", tile.cityLevel)); if (penalty) former.putFloat("prestige", (float)(Math.floor(former.getFloat("prestige") * .95 * 10) / 10)); former.putInt("flags", Math.max(0, former.getInt("flags") - 1)); moveHistory(former, recipient, city.cityId); reconcile(former); }
        infrastructure(recipient, (float)GameplayConfig.n("cityCenterPrestigeGeneration"), GameplayConfig.level("cityUpkeep", tile.cityLevel)); recipient.putInt("flags", recipient.getInt("flags") + 1); reconcile(recipient);
        // Applied only after the saved-data transaction succeeds, including transfers in another dimension.
        String cityId = city.cityId, recipientId = id(recipient); BlockPos center = city.center;
        committedUpdates.add(() -> FactionRuntime.deferTileOwner(server, dim, center, cityId, recipientId));
    }
    public static void addPrestige(CompoundNBT f, float amount) { f.putFloat("prestige", Math.min(10000, f.getFloat("prestige") + amount)); }
    private static void infrastructure(CompoundNBT f, float generation, float upkeep) { f.putFloat("prestigeGen", Math.max(0, Math.min((float)GameplayConfig.n("prestigeGenerationCap"), f.getFloat("prestigeGen") + generation))); f.putFloat("prestigeReq", Math.max(0, f.getFloat("prestigeReq") + upkeep)); }
    public static int bankruptcy(CompoundNBT f) {
        if (f == null || !GameplayConfig.b("enableNegativePrestigePenalties")) return 0;
        float p = f.getFloat("prestige"); return p < GameplayConfig.n("fallenNationThreshold") ? 3 : p < GameplayConfig.n("nationalCollapseThreshold") ? 2 : p < GameplayConfig.n("financialCrisisThreshold") ? 1 : 0;
    }
    public static boolean grace(CompoundNBT f) { return f != null && f.getLong("buildGraceUntil") > System.currentTimeMillis(); }
    public String buildGrace(ServerPlayerEntity player) {
        CompoundNBT f = require(player, 3);
        if (!GameplayConfig.b("graceBuildEnabled") || GameplayConfig.b("graceBuildOneTimeUse") && f.getBoolean("buildGraceUsed")) throw new IllegalArgumentException("Build grace is disabled or already used.");
        if (activeWars(f) || !validHome(f)) throw new IllegalArgumentException("Build grace needs a capital home and no active war.");
        f.putBoolean("buildGraceUsed", true); f.putLong("buildGraceUntil", System.currentTimeMillis() + GameplayConfig.hours("graceBuildDurationHours")); commit(); notify(f, "Faction build grace activated."); return "Build grace activated.";
    }
    public String setHome(ServerPlayerEntity player, boolean ally) {
        CompoundNBT f = require(player, 2); int dim = dimension(player.level); BlockPos pos = player.blockPosition(); TerritoryRecord at = at(player.level, pos);
        if (ally ? at == null || !id(f).equals(at.faction) : !capital(f, dim, pos)) throw new IllegalArgumentException(ally ? "Set ally warp inside your territory." : "Set home inside your designated capital.");
        if (FactionRuntime.homeCooldown(server, id(f)) > System.currentTimeMillis()) throw new IllegalArgumentException("Wait before changing home or ally warp again.");
        String prefix = ally ? "allyWarp" : "home";
        f.putInt(prefix + "X", ally ? (int)player.getX() : pos.getX()); f.putInt(prefix + "Y", ally ? (int)player.getY() : pos.getY()); f.putInt(prefix + "Z", ally ? (int)player.getZ() : pos.getZ()); f.putInt(prefix + "Dim", dim);
        if (!ally) f.putBoolean("homeSet", true); commit(); FactionRuntime.setHomeCooldown(server, id(f)); notify(f, ally ? "Ally warp set." : "Home set."); return ally ? "Ally warp set." : "Home set.";
    }
    public String homeCommand(ServerPlayerEntity player, String allyName) {
        CompoundNBT f = require(player, 1), destination = allyName.isEmpty() ? f : find(allyName);
        if (destination == null) throw new IllegalArgumentException("Unknown ally faction.");
        boolean ally = !allyName.isEmpty();
        if (bankruptcy(f) >= (ally ? 1 : 2) || bankruptcy(destination) >= (ally ? 1 : 2)) throw new IllegalArgumentException("Bankruptcy disables this warp.");
        if (ally && !relation(destination, f).getBoolean("ally")) throw new IllegalArgumentException("That faction is not your ally.");
        if (!ally && !validHome(f)) throw new IllegalArgumentException("Set a valid home inside your capital first.");
        if (ally && (destination.getInt("allyWarpX") == 0 || destination.getInt("allyWarpY") == 0 || destination.getInt("allyWarpZ") == 0)) throw new IllegalArgumentException("The ally has not set a rally point.");
        TerritoryRecord current = at(player.level, player.blockPosition()); CompoundNBT currentOwner = owner(current);
        if (zone(current) == 1 || currentOwner != null && currentOwner != f && !relation(f, currentOwner).getBoolean("ally")) throw new IllegalArgumentException("You cannot warp from foreign territory.");
        String prefix = ally ? "allyWarp" : "home"; int dim = destination.getInt(prefix + "Dim"); world(dim);
        FactionRuntime.scheduleHome(server, player, id(f), id(destination), dim, new BlockPos(destination.getInt(prefix + "X"), destination.getInt(prefix + "Y"), destination.getInt(prefix + "Z")), ally);
        return "Please stand still for 10 seconds.";
    }
    public String describe(ServerPlayerEntity player, String requested) {
        CompoundNBT f = requested.isEmpty() ? require(player, 1) : find(requested); if (f == null) throw new IllegalArgumentException("Unknown faction.");
        String capital = "Not designated"; for (TerritoryRecord c : cities(f)) if (c.cityId.equals(f.getString("capitalCityId"))) capital = c.name;
        List<String> allies = new ArrayList<>(), enemies = new ArrayList<>(), wars = new ArrayList<>(), offers = new ArrayList<>();
        for (CompoundNBT other : factions) if (other != f) { CompoundNBT r = storedRelation(f, other); if (r.getBoolean("ally")) allies.add(other.getString("name")); if (r.getBoolean("enemy")) enemies.add(other.getString("name")); if (declaredWar(f, other)) wars.add(other.getString("name")); for (String kind : new String[]{"peace", "ceasefire", "surrender"}) if (storedRelation(other, f).getBoolean(kind + "Requested")) offers.add(kind + " from " + other.getString("name")); }
        return decorated(f) + "\n" + f.getString("motd") + "\nLeader: " + f.getString("leader") + " | members: " + f.getList("identityMembers", 10).size() + " | online: " + online(f) + " | cities: " + cities(f).size() + "\nCapital: " + capital + " | home: " + (validHome(f) ? home(f) + " in dimension " + f.getInt("homeDim") : "unset") + " | build grace: " + (grace(f) ? "active" : "inactive") + "\n" + balance(f) + "\nAllies: " + allies + " | enemies: " + enemies + " | active wars: " + wars + "\nTreaty offers: " + offers + " | alliance offers: " + f.getCompound("nativeAllianceOffers").getAllKeys();
    }
    public static String decorated(CompoundNBT f) {
        float p = f.getFloat("prestige"); String title = p < 0 ? "Bankrupt State" : p < 100 ? "Unorganized Mob" : p < 250 ? "Clan" : p < 500 ? "Tribe" : p < 1000 ? "Chiefdom" : p < 1750 ? "City-State" : p < 2750 ? "Sheikhdom" : p < 4000 ? "Emirate" : p < 6000 ? "Sultanate" : p < 8500 ? "Empire" : "Caliphate";
        return f.getString("name").replace('_', ' ') + " - " + title;
    }

    // Diplomacy uses stable faction UUIDs internally. Legacy names are import/export fields only.
    public CompoundNBT relation(CompoundNBT f, CompoundNBT other) {
        if (f == null || other == null || id(other).isEmpty()) return new CompoundNBT();
        CompoundNBT relations = f.getCompound("nativeRelations"), result = relations.getCompound(id(other));
        relations.put(id(other), result); f.put("nativeRelations", relations); return result;
    }
    public boolean war(CompoundNBT a, CompoundNBT b) { return a != null && b != null && (relation(a, b).getBoolean("war") || relation(b, a).getBoolean("war") || relation(a, b).getBoolean("enemy") || relation(b, a).getBoolean("enemy")); }
    private boolean declaredWar(CompoundNBT a, CompoundNBT b) { return a != null && b != null && (storedRelation(a, b).getBoolean("war") || storedRelation(b, a).getBoolean("war")); }
    public boolean activeWars(CompoundNBT f) { CompoundNBT relations = f.getCompound("nativeRelations"); for (String id : relations.getAllKeys()) if (relations.getCompound(id).getBoolean("war")) return true; return false; }
    public int online(CompoundNBT f) { int count = 0; for (ServerPlayerEntity p : server.getPlayerList().getPlayers()) if (members.memberIndex(f, p) >= 0) count++; return count; }
    public boolean raidable(CompoundNBT f) {
        if (f == null) return false;
        if (GameplayConfig.b("freeRaid")) return true;
        if (online(f) >= GameplayConfig.i("onlinePlayerThreshold")) return true;
        long below = f.getLong("belowOnlineSince");
        return below <= 0 || System.currentTimeMillis() - below <= GameplayConfig.n("raidGraceAfterOnlineDropMinutes") * 60000;
    }
    public boolean permission(CompoundNBT owner, CompoundNBT actor, String action) {
        if (owner == actor) return true; if (actor != null && war(owner, actor)) return false;
        boolean ally = actor != null && relation(owner, actor).getBoolean("ally"); String key = "permission" + (ally ? "Ally_" : "Neutral_") + action;
        return owner.contains(key) ? owner.getBoolean(key) : ally && (action.equals("INTERACT") || action.equals("SWITCH"));
    }
    public boolean canEdit(ServerPlayerEntity player, BlockPos pos, String action, World world) {
        if (bypass(player)) return true; TerritoryRecord r = at(world, pos); int zone = zone(r);
        if (zone == 0 || zone == 1) return false; if (zone == 2) return true;
        CompoundNBT actor = faction(player), owner = owner(r);
        if (actor == owner && bankruptcy(owner) >= 3) return false;
        if (actor == owner && (action.equals("DESTROY") || action.equals("CONTAINER") || action.equals("INTERACT")) && world.getBlockState(pos).getBlock() == Xenofactions.OFFICER_CHEST.get() && rank(player) < 2) return false;
        if (actor != owner) {
            if (action.equals("BUILD") && world.getBlockState(pos).getBlock() == Xenofactions.CONQUEST.get()) return true;
            if (!war(actor, owner)) return permission(owner, actor, action);
            if (action.equals("BUILD")) return raidable(actor);
            if (!raidable(actor) || !raidable(owner)) return false;
            if (!action.equals("DESTROY") && player.getMainHandItem().getItem() == Xenofactions.CONQUEST_ITEM.get()) return true;
            if (player.getMainHandItem().getItem() != Xenofactions.MACE.get()) return false;
            net.minecraft.block.Block b = world.getBlockState(pos).getBlock();
            boolean container = b == net.minecraft.block.Blocks.CHEST || b == net.minecraft.block.Blocks.TRAPPED_CHEST || b == net.minecraft.block.Blocks.DISPENSER || b == net.minecraft.block.Blocks.DROPPER || b == net.minecraft.block.Blocks.HOPPER;
            return container || !action.equals("DESTROY") && b == Xenofactions.CITY.get();
        }
        return true;
    }
    public static boolean bypass(ServerPlayerEntity player) {
        for (ItemStack stack : player.inventory.items) if (stack.getItem() == Xenofactions.DEBUG.get()) return true;
        return player.getOffhandItem().getItem() == Xenofactions.DEBUG.get();
    }
    public String permissions(ServerPlayerEntity player, String relationship, String action, Boolean allowed) {
        CompoundNBT f = require(player, allowed == null ? 1 : 2);
        if (allowed == null) { StringBuilder text = new StringBuilder(); for (String rel : new String[]{"Ally", "Neutral"}) for (String a : new String[]{"BUILD", "DESTROY", "CONTAINER", "INTERACT", "SWITCH"}) text.append(rel).append(' ').append(a).append('=').append(f.contains("permission" + rel + "_" + a) ? f.getBoolean("permission" + rel + "_" + a) : rel.equals("Ally") && (a.equals("INTERACT") || a.equals("SWITCH"))).append(' '); return text.toString(); }
        f.putBoolean("permission" + (relationship.equalsIgnoreCase("ally") ? "Ally_" : "Neutral_") + action.toUpperCase(java.util.Locale.ROOT), allowed); commit(); return "Territory permission updated.";
    }
    public String diplomacy(ServerPlayerEntity player, String action, String targetName, String cityName) {
        CompoundNBT me = require(player, action.equals("alliance") || action.equals("wars") ? 1 : action.equals("befriend") || action.equals("acceptfriend") || action.equals("unfriend") ? 2 : 3);
        if (action.equals("alliance") || action.equals("wars")) {
            List<String> names = new ArrayList<>(); for (CompoundNBT other : factions) if (other != me && (action.equals("alliance") ? relation(me, other).getBoolean("ally") : declaredWar(me, other))) names.add(other.getString("name") + (action.equals("wars") ? " (" + (canRaid(me, other) ? "raidable" : "raid-gated") + ", " + Math.max(0, (System.currentTimeMillis() - relation(me, other).getLong("declaredAt")) / 60000) + " minutes)" : "")); if (action.equals("alliance") && !me.getCompound("nativeAllianceOffers").isEmpty()) names.add("Pending offers: " + me.getCompound("nativeAllianceOffers").getAllKeys()); return names.isEmpty() ? "None." : String.join(", ", names);
        }
        if (action.equals("acceptfriend")) {
            CompoundNBT offers = me.getCompound("nativeAllianceOffers"), offer = offers.getCompound(targetName.toLowerCase(java.util.Locale.ROOT));
            CompoundNBT friend = find(offer.getString("factionId"));
            if (friend == null || friend == me || !me.getString("suzerain").isEmpty() && !me.getString("suzerain").equals("nobody2584369")) throw new IllegalArgumentException("No valid alliance offer from this player, or your faction is a tributary.");
            boolean envoyPresent = false; ListNBT envoys = friend.getList("identityMembers", 10);
            for (int i = 0; i < envoys.size(); i++) if (envoys.getCompound(i).getString("uuid").equals(offer.getString("playerId"))) envoyPresent = true;
            if (!envoyPresent) throw new IllegalArgumentException("The offering player has left that faction; request a new offer.");
            if (war(me, friend)) throw new IllegalArgumentException("End enemy/war relations before forming an alliance.");
            relation(me, friend).putBoolean("ally", true); relation(friend, me).putBoolean("ally", true); offers.remove(targetName.toLowerCase(java.util.Locale.ROOT)); me.put("nativeAllianceOffers", offers); commit(); notify(friend, "Alliance accepted by " + me.getString("name") + "."); return "Alliance formed.";
        }
        CompoundNBT target = find(targetName); if (target == null || target == me || id(target).isEmpty()) throw new IllegalArgumentException("Choose another faction with a stable identity.");
        CompoundNBT outward = relation(me, target), inward = relation(target, me); long now = System.currentTimeMillis();
        switch (action) {
        case "befriend":
            if (outward.getBoolean("ally")) throw new IllegalArgumentException("You are already allies.");
            CompoundNBT offers = target.getCompound("nativeAllianceOffers"), offer = new CompoundNBT(); offer.putString("factionId", id(me)); offer.putString("playerId", player.getUUID().toString()); offer.putString("playerName", player.getGameProfile().getName());
            offers.put(player.getGameProfile().getName().toLowerCase(java.util.Locale.ROOT), offer); target.put("nativeAllianceOffers", offers); notify(target, "Alliance offer from " + player.getGameProfile().getName() + " of " + me.getString("name") + ". Use /c acceptfriend " + player.getGameProfile().getName()); break;
        case "unfriend": outward.putBoolean("ally", false); inward.putBoolean("ally", false); outward.putLong("formerAllyUntil", now + GameplayConfig.hours("allianceBreakCooldownHours")); inward.putLong("formerAllyUntil", outward.getLong("formerAllyUntil")); break;
        case "enemy":
            if (outward.getBoolean("ally")) throw new IllegalArgumentException("You cannot mark an ally as an enemy.");
            if (outward.getBoolean("enemy") && outward.getLong("enemyRemovalAt") == 0) throw new IllegalArgumentException("Already marked as an enemy."); outward.putBoolean("enemy", true); outward.putLong("enemyRemovalAt", 0); break;
        case "unenemy":
            if (!outward.getBoolean("enemy")) throw new IllegalArgumentException("That faction is not marked as an enemy.");
            if (outward.getLong("enemyRemovalAt") > now) return "Removal already pending for " + ((outward.getLong("enemyRemovalAt") - now + 59999) / 60000) + " minutes.";
            outward.putLong("enemyRemovalAt", now + 72L * 3600000); break;
        case "declarewar":
            if (!FactionRuntime.warsEnabled(server)) throw new IllegalArgumentException("War declarations are disabled by administrators.");
            if (declaredWar(me, target)) throw new IllegalArgumentException("You are already at war with that faction.");
            if (!FactionRuntime.onlineIgnored(server) && online(target) < GameplayConfig.i("onlinePlayerThreshold")) throw new IllegalArgumentException("The target needs " + GameplayConfig.i("onlinePlayerThreshold") + " online members.");
            if (!GameplayConfig.b("alliesCanDeclareWarOnEachOther") && outward.getBoolean("ally")) throw new IllegalArgumentException("You cannot declare war on an ally.");
            if (!FactionRuntime.cooldownsIgnored(server) && (outward.getLong("noWarUntil") > now || outward.getLong("formerAllyUntil") > now)) throw new IllegalArgumentException("A peace or former-ally cooldown prevents war.");
            if (bankruptcy(me) >= 3) throw new IllegalArgumentException("Fallen Nations cannot declare war.");
            float cost = (float)(GameplayConfig.n("warDeclarationBaseCost") + Math.max(0, target.getFloat("prestige")) * GameplayConfig.n("warDeclarationTargetPrestigeFactor"));
            if (me.getFloat("prestige") < cost) throw new IllegalArgumentException("War requires " + cost + " prestige.");
            if (!FactionRuntime.confirm(server, player, "war:" + id(me) + ":" + id(target))) return "War costs " + cost + " prestige; upkeep starts at " + GameplayConfig.n("activeWarUpkeep") + "/h and rises hourly. Repeat the command within 10 seconds.";
            addPrestige(me, -cost); outward.putBoolean("war", true); inward.putBoolean("war", true); outward.putLong("declaredAt", now); inward.putLong("declaredAt", now); clearMove(me); clearMove(target); broadcast("[WAR] " + me.getString("name") + " declared war on " + target.getString("name") + "."); break;
        case "defendally":
            if (!GameplayConfig.b("alliesCanJoinWars") || !outward.getBoolean("ally")) throw new IllegalArgumentException("Choose an ally; allied war participation must be enabled.");
            for (CompoundNBT enemy : factions) if (enemy != me && relation(target, enemy).getBoolean("war")) { relation(me, enemy).putBoolean("war", true); relation(me, enemy).putLong("declaredAt", now); relation(enemy, me).putBoolean("war", true); relation(enemy, me).putLong("declaredAt", now); clearMove(enemy); } clearMove(me); outward.putBoolean("defending", true); break;
        case "peace": case "ceasefire": case "surrender":
            negotiations(); if (!action.equals("peace") && !FactionRuntime.stateIgnored(server) && !war(me, target)) throw new IllegalArgumentException("You are not at war.");
            if (action.equals("peace") && !cityName.isEmpty()) { if (!GameplayConfig.b("peaceCityTransfersEnabled")) throw new IllegalArgumentException("Peace city transfers are disabled."); addPeaceCity(outward, city(me, cityName).cityId); }
            outward.putBoolean(action + "Requested", true); notify(target, me.getString("name") + " offers " + action + ". Use /c accept" + action + " " + me.getString("name")); break;
        case "acceptpeace": case "acceptceasefire": case "acceptsurrender":
            negotiations(); String kind = action.substring(6);
            if (!inward.getBoolean(kind + "Requested")) throw new IllegalArgumentException("No matching request.");
            if (kind.equals("peace") && !FactionRuntime.stateIgnored(server) && !war(me, target)) throw new IllegalArgumentException("You are not at war.");
            if (kind.equals("peace") && GameplayConfig.b("peaceCityTransfersEnabled")) { Set<String> offered = new LinkedHashSet<>(inward.getCompound("peaceCities").getAllKeys()); if (!inward.getString("peaceCity").isEmpty()) offered.add(inward.getString("peaceCity")); for (TerritoryRecord c : cities(target)) if (offered.contains(c.cityId)) transfer(c, me); }
            if (kind.equals("surrender")) { if (GameplayConfig.b("surrenderTransfersCities")) for (TerritoryRecord c : cities(target)) transfer(c, me); target.putString("nativeTributeFactionId", id(me)); target.putString("surrenderTributeTo", me.getString("name")); target.putLong("surrenderTributeUntil", now + GameplayConfig.hours("surrenderTributeDurationHours")); }
            clearWar(outward); clearWar(inward);
            if (!FactionRuntime.cooldownsIgnored(server)) { outward.putLong("noWarUntil", now + GameplayConfig.hours(kind + "CooldownHours")); inward.putLong("noWarUntil", outward.getLong("noWarUntil")); }
            broadcast("[WAR] " + me.getString("name") + " and " + target.getString("name") + " agreed to " + kind + "."); break;
        default: throw new IllegalArgumentException("Unknown diplomacy action.");
        }
        commit(); FactionRuntime.applyTileOwners(server); notify(me, "Diplomacy updated with " + target.getString("name") + "."); return action.equals("unenemy") ? "Enemy removal started; relation remains active for 72 hours." : "Diplomacy updated.";
    }
    private void negotiations() { if (FactionRuntime.legacyWar(server)) throw new IllegalArgumentException("Peace, ceasefire and surrender are disabled in legacy war mode."); }
    private static void addPeaceCity(CompoundNBT r, String city) { CompoundNBT cities = r.getCompound("peaceCities"); cities.putBoolean(city, true); r.put("peaceCities", cities); r.putString("peaceCity", city); }
    private static void clearWar(CompoundNBT relation) { for (String field : new String[]{"war", "declaredAt", "peaceRequested", "peaceCity", "peaceCities", "ceasefireRequested", "surrenderRequested"}) relation.remove(field); }
    private float warCost(CompoundNBT f) { float cost = 0; CompoundNBT relations = f.getCompound("nativeRelations"); for (String id : relations.getAllKeys()) { CompoundNBT r = relations.getCompound(id); if (!r.getBoolean("war")) continue; long hours = r.getLong("declaredAt") <= 0 ? 0 : Math.max(0, (System.currentTimeMillis() - r.getLong("declaredAt")) / 3600000); cost += GameplayConfig.n("activeWarUpkeep") * (1 + hours * GameplayConfig.n("warUpkeepHourlyGrowth") + (double)hours * hours * GameplayConfig.n("warUpkeepHourlyGrowthSquared")); } return cost; }
    private float upkeep(CompoundNBT f) { int stage = bankruptcy(f); return f.getFloat("prestigeReq") * (float)(stage == 3 ? GameplayConfig.n("fallenNationUpkeepMult") : stage == 2 ? GameplayConfig.n("nationalCollapseUpkeepMult") : stage == 1 ? GameplayConfig.n("financialCrisisUpkeepMult") : 1); }
    private float net(CompoundNBT f) { return (bankruptcy(f) >= 3 ? 0 : f.getFloat("prestigeGen")) - upkeep(f) - warCost(f); }
    private String balance(CompoundNBT f) { return "Prestige " + f.getFloat("prestige") + " | generation " + f.getFloat("prestigeGen") + "/h | upkeep " + upkeep(f) + "/h | war " + warCost(f) + "/h | net " + net(f) + "/h | " + new String[]{"Stable Nation", "Financial Crisis", "National Collapse", "Fallen Nation"}[bankruptcy(f)]; }
    public String economy(ServerPlayerEntity player, String action, int amount) {
        CompoundNBT f = require(player, 1); if (action.equals("balance")) return balance(f);
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive.");
        if (!action.equals("deposit")) throw new IllegalArgumentException("Unknown economy action.");
        members.prepareEdit(); List<ItemStack> original = new ArrayList<>(); for (ItemStack stack : player.inventory.items) original.add(stack.copy());
        int moved = 0;
        try {
            for (ItemStack s : player.inventory.items) if (s.getItem() == Xenofactions.PRESTIGE.get()) { int take = Math.min(s.getCount(), amount - moved); s.shrink(take); moved += take; if (moved == amount) break; }
            addPrestige(f, moved);
            commit();
        } catch (RuntimeException failure) { for (int i = 0; i < original.size(); i++) player.inventory.items.set(i, original.get(i)); player.inventoryMenu.broadcastChanges(); throw failure; }
        player.inventory.setChanged(); player.inventoryMenu.broadcastChanges(); return "Deposited " + moved + " prestige.";
    }
    public void economyTick() {
        for (CompoundNBT f : factions) {
            int old = f.getInt("lastBankruptcyStage"); float change = net(f);
            CompoundNBT target = find(f.getString("nativeTributeFactionId"));
            if (target != null && f.getLong("surrenderTributeUntil") > System.currentTimeMillis()) { float tribute = (bankruptcy(f) >= 3 ? 0 : Math.max(0, f.getFloat("prestigeGen"))) * (float)GameplayConfig.n("surrenderPrestigeTransferPercent"); change -= tribute; addPrestige(target, tribute); }
            else if (f.getLong("surrenderTributeUntil") > 0 && f.getLong("surrenderTributeUntil") <= System.currentTimeMillis()) { f.putString("nativeTributeFactionId", ""); f.putString("surrenderTributeTo", ""); f.putLong("surrenderTributeUntil", 0); }
            addPrestige(f, change); int stage = bankruptcy(f); f.putInt("lastBankruptcyStage", stage);
            if (old != stage) notify(f, "Prestige status changed: " + balance(f)); if (stage == 1) notify(f, "Prestige warning: net " + net(f) + "/h. Reduce upkeep or end wars."); if (old != stage && stage >= 2) broadcast("[PRESTIGE] " + f.getString("name") + " entered " + (stage == 3 ? "Fallen Nation" : "National Collapse") + ".");
        }
        commit();
    }
    public void maintenance(boolean starting) {
        for (CompoundNBT f : factions) {
            if (f.getInt("nativeGameplayVersion") > 1) throw new IllegalArgumentException("Unsupported native faction gameplay schema.");
            if (!f.contains("nativeRelations", 10)) importRelations(f);
            f.putInt("nativeGameplayVersion", 1);
            if (!f.contains("nativeTributeFactionId")) { CompoundNBT target = find(f.getString("surrenderTributeTo")); f.putString("nativeTributeFactionId", id(target)); }
            CompoundNBT relations = f.getCompound("nativeRelations");
            for (String uuid : new ArrayList<>(relations.getAllKeys())) {
                if (find(uuid) == null || uuid.equals(id(f))) { relations.remove(uuid); continue; }
                CompoundNBT r = relations.getCompound(uuid);
                if (r.getLong("enemyRemovalAt") > 0 && r.getLong("enemyRemovalAt") <= System.currentTimeMillis()) { r.putBoolean("enemy", false); r.putLong("enemyRemovalAt", 0); notify(f, "Enemy relation removal completed."); }
            }
            f.put("nativeRelations", relations);
            if (starting && !f.getBoolean("capitalDesignated")) {
                List<TerritoryRecord> cities = cities(f); cities.sort(Comparator.comparingInt(this::cityDimension).thenComparingInt(c -> c.center.getX()).thenComparingInt(c -> c.center.getY()).thenComparingInt(c -> c.center.getZ()));
                TerritoryRecord oldHome = territory.get(TerritoryKey.lookup(f.getInt("homeDim"), f.getInt("homeX"), f.getInt("homeZ")));
                if (!cities.isEmpty()) { TerritoryRecord chosen = oldHome != null && oldHome.city() && id(f).equals(oldHome.faction) ? oldHome : cities.get(0); f.putString("capitalCityId", chosen.cityId); f.putBoolean("capitalDesignated", true); }
            }
            if (!f.contains("lastBankruptcyStage")) f.putInt("lastBankruptcyStage", bankruptcy(f));
            reconcile(f); if (!f.getString("relocationId").isEmpty() && (activeWars(f) || f.getLong("relocationExpires") <= System.currentTimeMillis())) clearMove(f);
            if (online(f) >= GameplayConfig.i("onlinePlayerThreshold")) f.putLong("belowOnlineSince", 0);
            else if (f.getLong("belowOnlineSince") <= 0) f.putLong("belowOnlineSince", System.currentTimeMillis());
        }
        commit();
    }
    private void importRelations(CompoundNBT f) {
        f.put("nativeRelations", new CompoundNBT());
        for (String[] group : new String[][]{{"allies", "ally", "ally"}, {"activeWars", "war", "war"}, {"enemyFactionUuids", "enemyFactionUuid", "enemy"}, {"defendingAllies", "def", "defending"}, {"surrenderReq", "sreq", "surrenderRequested"}, {"ceaseReq", "creq", "ceasefireRequested"}, {"peaceReq", "preq", "peaceRequested"}})
            for (int i = 0; i < f.getInt(group[0]); i++) { String value = f.getString(i + "_" + group[1]); String[] peaceCity = value.split(":city:", 2); CompoundNBT target = find(peaceCity[0]); if (target != null && target != f) { CompoundNBT r = relation(f, target); r.putBoolean(group[2], true); if (peaceCity.length == 2) { try { addPeaceCity(r, city(f, peaceCity[1]).cityId); } catch (IllegalArgumentException absent) { } } } }
        for (String[] group : new String[][]{{"warDeclaredAt", "wdecl_n", "wdecl_t", "declaredAt"}, {"noWarUntil", "nwu_n", "nwu_t", "noWarUntil"}, {"formerAllyNoWar", "fanwu_n", "fanwu_t", "formerAllyUntil"}, {"pendingEnemyRemovalTimes", "pendingEnemyUuid", "pendingEnemyTime", "enemyRemovalAt"}})
            for (int i = 0; i < f.getInt(group[0]); i++) { CompoundNBT target = find(f.getString(i + "_" + group[1])); if (target != null && target != f) relation(f, target).putLong(group[3], f.getLong(i + "_" + group[2])); }
        CompoundNBT offers = new CompoundNBT(); for (int i = 0; i < f.getInt("potentialFriends"); i++) { String player = f.getString(i + "_pfriend"); for (CompoundNBT sender : factions) { ListNBT ms = sender.getList("identityMembers", 10); for (int j = 0; j < ms.size(); j++) if (ms.getCompound(j).getString("lastKnownName").equalsIgnoreCase(player)) { CompoundNBT o = new CompoundNBT(); o.putString("factionId", id(sender)); o.putString("playerId", ms.getCompound(j).getString("uuid")); o.putString("playerName", player); offers.put(player.toLowerCase(java.util.Locale.ROOT), o); } } } f.put("nativeAllianceOffers", offers);
    }
    private void mirrorRelations(CompoundNBT f) {
        for (String[] group : new String[][]{{"allies", "ally", "ally"}, {"activeWars", "war", "war"}, {"enemyFactionUuids", "enemyFactionUuid", "enemy"}, {"defendingAllies", "def", "defending"}, {"surrenderReq", "sreq", "surrenderRequested"}, {"ceaseReq", "creq", "ceasefireRequested"}, {"peaceReq", "preq", "peaceRequested"}}) {
            int old = f.getInt(group[0]), count = 0;
            for (CompoundNBT other : factions) if (other != f && storedRelation(f, other).getBoolean(group[2])) {
                f.putString(count++ + "_" + group[1], group[0].equals("enemyFactionUuids") ? id(other) : other.getString("name"));
                if (group[0].equals("peaceReq")) { CompoundNBT r = storedRelation(f, other); Set<String> offered = new LinkedHashSet<>(r.getCompound("peaceCities").getAllKeys()); if (!r.getString("peaceCity").isEmpty()) offered.add(r.getString("peaceCity")); for (TerritoryRecord c : cities(f)) if (offered.contains(c.cityId)) f.putString(count++ + "_" + group[1], other.getString("name") + ":city:" + c.name); }
            }
            for (int i = count; i < old; i++) f.remove(i + "_" + group[1]); f.putInt(group[0], count);
        }
        for (String[] group : new String[][]{{"warDeclaredAt", "wdecl_n", "wdecl_t", "declaredAt"}, {"noWarUntil", "nwu_n", "nwu_t", "noWarUntil"}, {"formerAllyNoWar", "fanwu_n", "fanwu_t", "formerAllyUntil"}, {"pendingEnemyRemovalTimes", "pendingEnemyUuid", "pendingEnemyTime", "enemyRemovalAt"}}) {
            int count = 0, old = f.getInt(group[0]); for (CompoundNBT other : factions) if (storedRelation(f, other).getLong(group[3]) > 0) { f.putString(count + "_" + group[1], group[0].equals("pendingEnemyRemovalTimes") ? id(other) : other.getString("name")); f.putLong(count++ + "_" + group[2], storedRelation(f, other).getLong(group[3])); } for (int i = count; i < old; i++) { f.remove(i + "_" + group[1]); f.remove(i + "_" + group[2]); } f.putInt(group[0], count);
        }
        CompoundNBT tribute = find(f.getString("nativeTributeFactionId")); if (tribute != null) f.putString("surrenderTributeTo", tribute.getString("name"));
    }
    private static CompoundNBT storedRelation(CompoundNBT f, CompoundNBT other) { return f.getCompound("nativeRelations").getCompound(id(other)); }

    public boolean pending(CompoundNBT f) { return f != null && !f.getString("relocationId").isEmpty() && f.getLong("relocationExpires") > System.currentTimeMillis() && !activeWars(f); }
    private static void clearMove(CompoundNBT f) { f.putString("relocationId", ""); f.putString("relocationCityId", ""); f.putLong("relocationStarted", 0); f.putLong("relocationExpires", 0); }
    private void moveHistory(CompoundNBT from, CompoundNBT to, String cityId) { int count = to.getInt("relocationHistoryCount"); for (int i = 0; i < from.getInt("relocationHistoryCount"); i++) if (from.getString("relocationHistoryCity_" + i).equals(cityId)) { to.putString("relocationHistoryCity_" + count, cityId); to.putLong("relocationHistoryTime_" + count++, from.getLong("relocationHistoryTime_" + i)); } to.putInt("relocationHistoryCount", count); }
    public String startMove(ServerPlayerEntity player, BlockPos pos) {
        CompoundNBT f = require(player, 3); if (player instanceof FakePlayer || !GameplayConfig.b("cityRelocationEnabled")) throw new IllegalArgumentException("City relocation is disabled or requires a real leader.");
        if (activeWars(f) || pending(f) || bankruptcy(f) >= 3) throw new IllegalArgumentException("A war, Fallen Nation or another pending move prevents relocation.");
        TileEntity t = player.level.getBlockEntity(pos); if (!(t instanceof CityCenterTile)) throw new IllegalArgumentException("City Center missing."); CityCenterTile tile = (CityCenterTile)t;
        if (!id(f).equals(tile.factionId) || !tile.registered || !tile.claimed || tile.height < 1) throw new IllegalArgumentException("Only a fully raised, owned city may move.");
        city(f, tile.cityId); relocationCooldown(f, tile.cityId); long now = System.currentTimeMillis();
        f.putString("relocationId", UUID.randomUUID().toString()); f.putString("relocationCityId", tile.cityId); f.putInt("relocationDim", dimension(player.level));
        f.putInt("relocationX", pos.getX()); f.putInt("relocationY", pos.getY()); f.putInt("relocationZ", pos.getZ()); f.putLong("relocationStarted", now); f.putLong("relocationExpires", now + (long)(GameplayConfig.n("cityRelocationPendingMinutes") * 60000L)); commit(); issueToken(player, f);
        return "Move started. The old city remains active. Place the token or use /c city cancelmove.";
    }
    private void relocationCooldown(CompoundNBT f, String cityId) {
        List<Long> times = new ArrayList<>(); long now = System.currentTimeMillis(), window = GameplayConfig.hours("cityRelocationWindowHours");
        for (int i = 0; i < f.getInt("relocationHistoryCount"); i++) if (cityId.equals(f.getString("relocationHistoryCity_" + i)) && f.getLong("relocationHistoryTime_" + i) > now - window) times.add(f.getLong("relocationHistoryTime_" + i)); times.sort(Long::compare);
        if (times.size() >= GameplayConfig.i("cityRelocationMoveLimit") || times.size() == 2 && times.get(1) + GameplayConfig.n("cityRelocationRepeatCooldownMinutes") * 60000 > now) throw new IllegalArgumentException("This city is still within its move-limit cooldown.");
    }
    private void issueToken(ServerPlayerEntity player, CompoundNBT f) {
        ItemStack token = new ItemStack(Xenofactions.CITY_ITEM.get()); CompoundNBT tag = token.getOrCreateTag(); tag.putBoolean("xenoCityRelocation", true); tag.putString("relocationId", f.getString("relocationId")); tag.putString("cityId", f.getString("relocationCityId")); tag.putString("factionUuid", id(f)); token.setHoverName(new StringTextComponent("City Center Relocation Token"));
        if (!player.inventory.add(token)) player.drop(token, false); player.inventoryMenu.broadcastChanges();
    }
    public String moveCommand(ServerPlayerEntity player, boolean recover) { CompoundNBT f = require(player, 3); if (!pending(f)) throw new IllegalArgumentException("No pending city move."); if (recover) issueToken(player, f); else { clearMove(f); commit(); } return recover ? "Replacement relocation token issued." : "Move canceled; city and claims unchanged."; }
    public void relocate(ServerPlayerEntity player, ItemStack token, BlockPos destination, Direction face) {
        CompoundNBT f = require(player, 3); CompoundNBT tag = token.getOrCreateTag();
        if (player instanceof FakePlayer || token != player.getMainHandItem() || !pending(f) || !f.getString("relocationId").equals(tag.getString("relocationId")) || !id(f).equals(tag.getString("factionUuid")) || !f.getString("relocationCityId").equals(tag.getString("cityId"))) throw new IllegalArgumentException("This token is no longer authorized for the current faction leader.");
        ServerWorld w = player.getLevel(); int dim = dimension(w);
        if (dim != f.getInt("relocationDim")) throw new IllegalArgumentException("Move within the source dimension.");
        BlockPos source = new BlockPos(f.getInt("relocationX"), f.getInt("relocationY"), f.getInt("relocationZ"));
        TerritoryRecord city = city(f, f.getString("relocationCityId")); CityCenterTile old = cityTile(city);
        if (!old.getBlockPos().equals(source) || !old.claimed || old.height < 1) throw new IllegalArgumentException("The source city changed.");
        double distance = Math.hypot((double)destination.getX() - source.getX(), (double)destination.getZ() - source.getZ());
        if (distance > GameplayConfig.n("cityRelocationMaxDistanceBlocks")) throw new IllegalArgumentException("Maximum move distance exceeded.");
        TerritoryRecord at = at(w, destination); if (at == null || !city.cityId.equals(at.cityId) || !id(f).equals(at.faction)) throw new IllegalArgumentException("The destination must be inside the moved city's existing territory.");
        if (!w.getBlockState(destination).getMaterial().isReplaceable() || destination.equals(source)) throw new IllegalArgumentException("Destination is not replaceable.");
        if (!w.mayInteract(player, source) || !w.mayInteract(player, destination) || !player.mayUseItemAt(destination, face, token) || !canEdit(player, destination, "BUILD", w)) throw new IllegalArgumentException("This location is protected.");
        foundation(w, destination); placement(dim, destination, old.cityLevel, city.cityId, true); relocationCooldown(f, city.cityId);
        float cost = distance <= GameplayConfig.n("cityRelocationFreeDistanceBlocks") ? 0 : (float)(GameplayConfig.n("cityRelocationBasePrestigeCost") + Math.ceil(distance - GameplayConfig.n("cityRelocationFreeDistanceBlocks")) * GameplayConfig.n("cityRelocationPrestigePerExtraBlock"));
        if (f.getFloat("prestige") < cost) throw new IllegalArgumentException("This move costs " + cost + " prestige.");
        CompoundNBT oldTile = old.save(new CompoundNBT()); net.minecraft.block.BlockState sourceState = w.getBlockState(source), destinationState = w.getBlockState(destination);
        CompoundNBT before = members.state.copy(); ItemStack originalToken = token.copy();
        try {
            FactionRuntime.guard(server, true);
            net.minecraftforge.common.util.BlockSnapshot placement = net.minecraftforge.common.util.BlockSnapshot.create(w.dimension(), w, destination);
            if (!w.setBlock(destination, sourceState, 3)) throw new IllegalStateException("Destination could not be placed.");
            if (net.minecraftforge.event.ForgeEventFactory.onBlockPlace(player, placement, face) || net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.BlockEvent.BreakEvent(w, source, sourceState, player))) throw new IllegalArgumentException("Another server protection refused this move.");
            TileEntity t = w.getBlockEntity(destination); if (!(t instanceof CityCenterTile)) throw new IllegalStateException("Destination tile missing.");
            CompoundNBT restored = oldTile.copy(); restored.putInt("x", destination.getX()); restored.putInt("y", destination.getY()); restored.putInt("z", destination.getZ()); ((CityCenterTile)t).load(sourceState, restored); t.setChanged();
            rebuild(city, dim, destination, old.cityLevel);
            if (f.getBoolean("homeSet")) {
                BlockPos home = home(f); TerritoryRecord oldHome = TerritoryRecord.read(before, name -> id(find(name))).get(TerritoryKey.generated(f.getInt("homeDim"), home.getX(), home.getZ()));
                TerritoryRecord current = territory.get(TerritoryKey.lookup(f.getInt("homeDim"), home.getX(), home.getZ()));
                if (oldHome != null && oldHome.cityId.equals(city.cityId) && (current == null || !current.cityId.equals(city.cityId))) {
                    BlockPos translated = home.offset(destination.getX() - source.getX(), 0, destination.getZ() - source.getZ()); TerritoryRecord tr = at(w, translated);
                    if (tr == null || !tr.cityId.equals(city.cityId)) translated = destination.above();
                    f.putInt("homeX", translated.getX()); f.putInt("homeY", translated.getY()); f.putInt("homeZ", translated.getZ()); f.putInt("homeDim", dim);
                }
            }
            if (!w.removeBlock(source, false)) throw new IllegalStateException("Source removal failed.");
            addPrestige(f, -cost); int count = f.getInt("relocationHistoryCount"); f.putString("relocationHistoryCity_" + count, city.cityId); f.putLong("relocationHistoryTime_" + count, System.currentTimeMillis()); f.putInt("relocationHistoryCount", count + 1); clearMove(f); reconcile(f); commit(); token.shrink(1); player.inventoryMenu.broadcastChanges(); message(player, "City moved for " + cost + " prestige.");
        } catch (RuntimeException failure) {
            w.setBlock(destination, destinationState, 3); w.setBlock(source, sourceState, 3); TileEntity restored = w.getBlockEntity(source); if (restored instanceof CityCenterTile) restored.load(sourceState, oldTile);
            if (!members.state.equals(before)) members.commit(before); player.inventory.items.set(player.inventory.selected, originalToken); player.inventoryMenu.broadcastChanges(); throw new IllegalStateException("Move failed; original city and token restored.", failure);
        } finally { FactionRuntime.guard(server, false); }
    }
    public String setZone(ServerPlayerEntity player, String zoneName, String shape, int radius) {
        int zone = zoneName.equals("safe") ? 0 : zoneName.equals("war") ? 1 : zoneName.equals("wild") ? 2 : -1;
        if (zone < 0 || !shape.equals("s") && !shape.equals("c")) throw new IllegalArgumentException("Use safe, war or wild and s (square) or c (circle).");
        if (shape.equals("s")) radius--; if (radius < 0 || radius > 25) throw new IllegalArgumentException("Invalid radius (1-25).");
        int dim = dimension(player.level); if (!GameplayConfig.claims(dim)) throw new IllegalArgumentException("Claims are disabled in this dimension.");
        Set<TerritoryKey> keys = new LinkedHashSet<>();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) if (shape.equals("s") || x * x + z * z < radius * radius) keys.add(TerritoryKey.generated(dim, (int)player.getX() + x * 16, (int)player.getZ() + z * 16));
        return setZones(dim, zone, keys);
    }
    public String setZoneArea(ServerPlayerEntity player, String type, BlockPos a, BlockPos b) {
        int dim = dimension(player.level); if (!GameplayConfig.claims(dim)) throw new IllegalArgumentException("Claims are disabled in this dimension.");
        int x1 = Math.floorDiv(Math.min(a.getX(), b.getX()), 16), x2 = Math.floorDiv(Math.max(a.getX(), b.getX()), 16), z1 = Math.floorDiv(Math.min(a.getZ(), b.getZ()), 16), z2 = Math.floorDiv(Math.max(a.getZ(), b.getZ()), 16);
        if ((long)x2 - x1 + 1 > 51 || (long)z2 - z1 + 1 > 51) throw new IllegalArgumentException("Zone selection exceeds the 51 by 51 cell limit.");
        int zone = type.equals("safezone") ? 0 : type.equals("warzone") ? 1 : type.equals("wilderness") ? 2 : -1; if (zone < 0) throw new IllegalArgumentException("Select safezone, warzone or wilderness.");
        Set<TerritoryKey> keys = new LinkedHashSet<>(); for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) keys.add(new TerritoryKey(dim, x, z));
        return setZones(dim, zone, keys);
    }
    private String setZones(int dim, int zone, Set<TerritoryKey> keys) {
        Map<String, TerritoryRecord> removedCities = new LinkedHashMap<>(), originalCities = new LinkedHashMap<>();
        for (TerritoryRecord r : territory.values()) if (r.city()) originalCities.put(r.cityId, r);
        int count = 0;
        for (TerritoryKey k : keys) {
            TerritoryRecord old = territory.get(k);
            if (old != null && old.city() && TerritoryKey.generated(dim, old.center.getX(), old.center.getZ()).equals(k)) removedCities.put(old.cityId, old);
            if (zone == 2) territory.remove(k); else { TerritoryRecord r = new TerritoryRecord(); r.zone = zone; territory.put(k, r); } count++;
        }
        for (Map.Entry<String, TerritoryRecord> e : originalCities.entrySet()) if (territory.values().stream().noneMatch(r -> r.cityId.equals(e.getKey()))) removedCities.put(e.getKey(), e.getValue());
        for (Map.Entry<String, TerritoryRecord> e : removedCities.entrySet()) {
            String cityId = e.getKey(); TerritoryRecord source = e.getValue(); CompoundNBT owner = owner(source);
            if (owner != null) { infrastructure(owner, -(float)GameplayConfig.n("cityCenterPrestigeGeneration"), -GameplayConfig.level("cityUpkeep", source.level)); owner.putFloat("prestige", (float)(Math.floor(owner.getFloat("prestige") * .95 * 10) / 10)); owner.putInt("flags", Math.max(0, owner.getInt("flags") - 1)); if (cityId.equals(owner.getString("relocationCityId"))) clearMove(owner); }
            territory.entrySet().removeIf(cell -> cell.getValue().cityId.equals(cityId));
            committedUpdates.add(() -> FactionRuntime.afterCommit(server, () -> { ServerWorld w = world(dim); w.getChunkAt(source.center); TileEntity tile = w.getBlockEntity(source.center); if (tile instanceof CityCenterTile && ((CityCenterTile)tile).cityId.equals(cityId)) { CityCenterTile city = (CityCenterTile)tile; city.factionId = ""; city.registered = city.claimed = false; city.setChanged(); } }));
        }
        territoryChanged = true; for (CompoundNBT f : factions) reconcile(f); commit(); return "Changed " + count + " territory cells to " + (zone == 0 ? "safezone" : zone == 1 ? "warzone" : "wilderness") + ".";
    }
    public String admin(String action, String target, int amount) {
        if (action.equals("addprestige")) { CompoundNBT f = find(target); if (f == null) throw new IllegalArgumentException("Unknown faction."); addPrestige(f, amount); commit(); return "Prestige updated."; }
        if (action.equals("resetbuildgrace") || action.equals("endbuildgrace")) { CompoundNBT f = find(target); if (f == null) throw new IllegalArgumentException("Unknown faction."); f.putLong("buildGraceUntil", action.equals("endbuildgrace") ? 0 : System.currentTimeMillis() + 24L * 3600000); if (action.equals("resetbuildgrace")) f.putBoolean("buildGraceUsed", false); commit(); notify(f, "Build grace changed by an administrator."); return "Build grace updated."; }
        if (action.equals("wardisable")) for (CompoundNBT f : factions) { CompoundNBT relations = f.getCompound("nativeRelations"); for (String uuid : relations.getAllKeys()) { CompoundNBT r = relations.getCompound(uuid); r.remove("war"); r.remove("declaredAt"); r.remove("defending"); } f.put("nativeRelations", relations); }
        if (action.equals("skipwarcooldown")) for (CompoundNBT f : factions) { CompoundNBT rs = f.getCompound("nativeRelations"); for (String uuid : rs.getAllKeys()) { rs.getCompound(uuid).remove("noWarUntil"); rs.getCompound(uuid).remove("formerAllyUntil"); } }
        commit(); return FactionRuntime.admin(server, action);
    }
    public String presentation(ServerPlayerEntity player, String action, String value) {
        CompoundNBT f = require(player, action.equals("color") ? 2 : 3);
        if (action.equals("color")) {
            if (!value.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use six hexadecimal color digits.");
            f.putInt("color", Integer.parseInt(value, 16));
        } else {
            String[] flags = {"none", "tri", "vtri", "bcross", "wcross", "julia", "ussr", "asterisk", "pc"}; int chosen = -1;
            for (int i = 1; i < flags.length; i++) if (flags[i].equalsIgnoreCase(value)) chosen = i;
            if (chosen < 0) throw new IllegalArgumentException("Use tri, vtri, bcross, wcross, julia, ussr or asterisk."); f.putInt("flag", chosen);
        }
        commit(); return "Faction " + action + " updated.";
    }
    private boolean canRaid(CompoundNBT attacker, CompoundNBT defender) {
        long declaration = relation(attacker, defender).getLong("declaredAt");
        return war(attacker, defender) && raidable(attacker) && (raidable(defender) || declaration > 0 && System.currentTimeMillis() - declaration >= 12L * 3600000);
    }
    public void checkConquest(ServerWorld world, BlockPos pos, CompoundNBT faction) {
        if (faction == null || !FactionRuntime.warsEnabled(server)) throw new IllegalArgumentException("Conquest needs a faction and enabled war declarations.");
        int dim = dimension(world); if (!GameplayConfig.claims(dim)) throw new IllegalArgumentException("Claims are disabled in this dimension.");
        TerritoryKey key = TerritoryKey.generated(dim, pos.getX(), pos.getZ()); CompoundNBT defender = owner(territory.get(key));
        if (defender == null || id(defender).equals(id(faction)) || !canRaid(faction, defender)) throw new IllegalArgumentException("Choose foreign, raidable territory with an enemy or active war relation.");
        boolean border = false;
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            TerritoryRecord neighbor = territory.get(TerritoryKey.generated(dim, pos.getX() + d.getStepX() * 16, pos.getZ() + d.getStepZ() * 16));
            if (zone(neighbor) == 2 || owner(neighbor) != defender) border = true;
        }
        if (!border || !world.canSeeSky(pos.above())) throw new IllegalArgumentException("A Conquest Flag needs a foreign border claim and sky access.");
        for (int x = -4; x <= 4; x++) for (int y = -3; y <= 3; y++) for (int z = -4; z <= 4; z++) if ((x != 0 || y != 0 || z != 0) && world.getBlockState(pos.offset(x, y, z)).getBlock() == Xenofactions.CONQUEST.get()) throw new IllegalArgumentException("Another Conquest Flag is too close.");
    }
    public void checkConquest(com.hfr.tile.ConquestTile tile) { checkConquest((ServerWorld)tile.getLevel(), tile.getBlockPos(), find(tile.ownerId)); }
    public void foundConquest(com.hfr.tile.ConquestTile tile) {
        checkConquest(tile); CompoundNBT faction = find(tile.ownerId); infrastructure(faction, 0, (float)GameplayConfig.n("settlementCityUpkeep")); commit();
        tile.claimId = UUID.randomUUID().toString(); tile.registered = true; tile.setChanged(); broadcast("[WAR] " + faction.getString("name") + " placed a Conquest Flag at " + tile.getBlockPos() + " in dimension " + dimension(tile.getLevel()) + ".");
    }
    public void removeConquest(com.hfr.tile.ConquestTile tile) {
        if (!tile.registered) return; CompoundNBT faction = find(tile.ownerId); if (faction != null) infrastructure(faction, 0, -(float)GameplayConfig.n("settlementCityUpkeep"));
        if (territory.entrySet().removeIf(e -> e.getValue().conquest && e.getValue().cityId.equals(tile.claimId))) territoryChanged = true;
        commit(); tile.registered = false;
    }
    public void conquer(com.hfr.tile.ConquestTile tile) {
        ServerWorld world = (ServerWorld)tile.getLevel(); BlockPos pos = tile.getBlockPos(); int dim = dimension(world);
        TerritoryKey key = TerritoryKey.generated(dim, pos.getX(), pos.getZ()); TerritoryRecord existing = territory.get(key); CompoundNBT attacker = find(tile.ownerId), defender = owner(existing);
        if (attacker == null || defender == null || attacker == defender || !canRaid(attacker, defender)) { world.destroyBlock(pos, false); return; }
        if (existing.city() && key.equals(TerritoryKey.generated(dim, existing.center.getX(), existing.center.getZ()))) {
            transfer(existing, attacker); commit(); FactionRuntime.applyTileOwners(server); notify(attacker, "Captured city " + existing.name + ". Officers may use /c nameclaim to rename it."); world.destroyBlock(pos, false); return;
        }
        if (existing.conquest && key.equals(TerritoryKey.generated(dim, existing.center.getX(), existing.center.getZ()))) { BlockPos previous = existing.center; committedUpdates.add(() -> FactionRuntime.afterCommit(server, () -> { TileEntity t = world.getBlockEntity(previous); if (t instanceof com.hfr.tile.ConquestTile) world.destroyBlock(previous, false); })); }
        TerritoryRecord claim = new TerritoryRecord(); claim.zone = 3; claim.conquest = true; claim.faction = id(attacker); claim.cityId = tile.claimId; claim.center = pos; territory.put(key, claim); territoryChanged = true;
        reconcile(defender); commit(); FactionRuntime.applyTileOwners(server); notify(attacker, "Captured territory cell " + key + ".");
    }
    private static Map<String, int[]> warps(CompoundNBT faction) {
        Map<String, int[]> result = new LinkedHashMap<>();
        for (int i = 0; i < faction.getInt("warps"); i++) {
            String name = faction.getString(i + "_name");
            if (name.isEmpty() || result.containsKey(name)) throw new IllegalArgumentException("Invalid or duplicate saved faction warp.");
            result.put(name, new int[]{faction.getInt(i + "_x"), faction.getInt(i + "_y"), faction.getInt(i + "_z"), faction.getInt(i + "_dim")});
        }
        return result;
    }
    private static void writeWarps(CompoundNBT faction, Map<String, int[]> warps) {
        for (int i = 0; i < faction.getInt("warps"); i++) for (String field : new String[]{"name", "x", "y", "z", "dim"}) faction.remove(i + "_" + field);
        int i = 0; for (Map.Entry<String, int[]> entry : warps.entrySet()) { int[] pos = entry.getValue(); faction.putString(i + "_name", entry.getKey()); faction.putInt(i + "_x", pos[0]); faction.putInt(i + "_y", pos[1]); faction.putInt(i + "_z", pos[2]); faction.putInt(i++ + "_dim", pos[3]); }
        faction.putInt("warps", i);
    }
    public boolean hasWarp(CompoundNBT faction, String name) { return warps(faction).containsKey(name); }
    private com.hfr.tile.WarpTentTile nearbyTent(ServerWorld world, BlockPos pos, String name) {
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos part = pos.relative(direction, 2); world.getChunkAt(part); net.minecraft.block.BlockState state = world.getBlockState(part);
            if (state.getBlock() != Xenofactions.TENT.get()) continue;
            BlockPos core = com.hfr.blocks.WarpTentBlock.core(part, state); world.getChunkAt(core); TileEntity t = world.getBlockEntity(core);
            if (t instanceof com.hfr.tile.WarpTentTile) { com.hfr.tile.WarpTentTile tent = (com.hfr.tile.WarpTentTile)t; if (tent.operational() && tent.warp.equals(name)) return tent; }
        }
        return null;
    }
    public void updateTent(com.hfr.tile.WarpTentTile tent, String owner) {
        CompoundNBT former = find(tent.ownerId), next = find(owner);
        boolean changed = !tent.ownerId.equals(owner);
        if (!tent.warp.isEmpty()) { if (former != null) { Map<String, int[]> warps = warps(former); warps.remove(tent.warp); writeWarps(former, warps); } }
        if (changed) { if (former != null) infrastructure(former, (float)GameplayConfig.n("warpTentUpkeep"), 0); if (next != null) infrastructure(next, -(float)GameplayConfig.n("warpTentUpkeep"), 0); }
        commit(); tent.ownerId = owner; tent.warp = ""; tent.setChanged();
    }
    private void renameTentWarp(int[] pos, String old, String name, String owner) {
        ServerWorld w = world(pos[3]); com.hfr.tile.WarpTentTile tent = nearbyTent(w, new BlockPos(pos[0], pos[1], pos[2]), old);
        if (tent != null) committedUpdates.add(() -> FactionRuntime.afterCommit(server, () -> { if (!tent.isRemoved()) { tent.warp = name; tent.ownerId = owner; tent.setChanged(); } }));
    }
    public String warp(ServerPlayerEntity player, String action, String name) {
        CompoundNBT f = require(player, 1); Map<String, int[]> warps = warps(f);
        if (action.equals("warps")) { List<String> result = new ArrayList<>(); for (Map.Entry<String, int[]> e : warps.entrySet()) { int[] p = e.getValue(); result.add(e.getKey() + " at " + p[0] + ", " + p[1] + ", " + p[2] + " (dimension " + p[3] + ")"); } return result.isEmpty() ? "No faction warps." : String.join("; ", result); }
        if (action.equals("delwarp")) { if (warps.remove(name) == null) throw new IllegalArgumentException("Unknown warp."); writeWarps(f, warps); commit(); return "Warp deleted."; }
        if (bankruptcy(f) >= 2) throw new IllegalArgumentException("Faction warps are disabled by bankruptcy.");
        if (action.equals("addwarp")) {
            if (name.isEmpty() || name.length() > 80 || warps.containsKey(name)) throw new IllegalArgumentException("Invalid or duplicate warp name.");
            if (owner(at(player.level, player.blockPosition())) != f) throw new IllegalArgumentException("Create a warp inside your territory.");
            float cost = (float)GameplayConfig.n("warpCreationCost"); if (f.getFloat("prestige") < cost) throw new IllegalArgumentException("A warp costs " + cost + " prestige.");
            BlockPos base = new BlockPos((int)player.getX(), (int)player.getY(), (int)player.getZ()); com.hfr.tile.WarpTentTile tent = nearbyTent(player.getLevel(), base, "");
            if (tent == null) throw new IllegalArgumentException("No nearby operational, unused Warp Tent.");
            if (owner(at(player.level, tent.getBlockPos())) != f) throw new IllegalArgumentException("The Warp Tent must belong to your faction.");
            if (!tent.ownerId.equals(id(f))) { CompoundNBT previous = find(tent.ownerId); if (previous != null) infrastructure(previous, (float)GameplayConfig.n("warpTentUpkeep"), 0); infrastructure(f, -(float)GameplayConfig.n("warpTentUpkeep"), 0); }
            warps.put(name, new int[]{base.getX(), base.getY() + 1, base.getZ(), dimension(player.level)}); writeWarps(f, warps); addPrestige(f, -cost); commit(); tent.ownerId = id(f); tent.warp = name; tent.setChanged(); notify(f, "Created warp " + name + "."); return "Warp created.";
        }
        int[] pos = warps.get(name); if (pos == null) throw new IllegalArgumentException("Unknown warp.");
        TerritoryRecord origin = at(player.level, player.blockPosition()); CompoundNBT originOwner = owner(origin);
        if (zone(origin) == 1 || originOwner != null && originOwner != f && !relation(f, originOwner).getBoolean("ally")) throw new IllegalArgumentException("You cannot warp from unfriendly territory.");
        BlockPos destination = new BlockPos(pos[0], pos[1], pos[2]); if (!validWarp(f, name, pos[3], destination)) throw new IllegalArgumentException("Warp Tent missing or no longer operational.");
        FactionRuntime.scheduleWarp(server, player, id(f), name, pos[3], destination); return "Please stand still for 10 seconds.";
    }
    public boolean validWarp(CompoundNBT faction, String name, int dim, BlockPos destination) {
        int[] saved = warps(faction).get(name); if (saved == null || saved[3] != dim || !destination.equals(new BlockPos(saved[0], saved[1], saved[2]))) return false;
        ServerWorld world = world(dim); com.hfr.tile.WarpTentTile tent = nearbyTent(world, destination, name);
        return tent != null && owner(at(world, tent.getBlockPos())) == faction;
    }
    public String merge(ServerPlayerEntity player, String name, boolean accept) {
        CompoundNBT mine = require(player, 3), other = find(name);
        if (other == null || other == mine || id(other).isEmpty()) throw new IllegalArgumentException("Choose another resolved faction.");
        if (!accept) { FactionRuntime.offerMerge(server, id(mine), id(other)); notify(other, mine.getString("name") + " requests to merge into your faction. Your leader may use /c acceptmerge " + mine.getString("name")); return "Merge request sent."; }
        if (!FactionRuntime.hasMerge(server, id(other), id(mine))) throw new IllegalArgumentException("No pending merge request from that faction.");
        ListNBT ms = mine.getList("identityMembers", 10), incoming = other.getList("identityMembers", 10);
        for (int i = 0; i < incoming.size(); i++) { CompoundNBT member = incoming.getCompound(i).copy(); if (member.getString("role").equals("OWNER")) member.putString("role", "OFFICER"); ms.add(member); }
        // Keep unresolved legacy names as staging data; merging never silently binds them to UUIDs.
        for (String suffix : new String[]{"", "_off"}) {
            String countKey = suffix.isEmpty() ? "members" : "officers"; int count = mine.getInt(countKey);
            Set<String> names = new HashSet<>(); for (int i = 0; i < count; i++) names.add(mine.getString(i + suffix).toLowerCase(Locale.ROOT));
            for (int i = 0; i < other.getInt(countKey); i++) { String old = other.getString(i + suffix); if (!old.isEmpty() && names.add(old.toLowerCase(Locale.ROOT))) mine.putString(count++ + suffix, old); }
            mine.putInt(countKey, count);
        }
        mine.put("identityMembers", ms); FactionService.mirror(mine);
        for (TerritoryRecord c : cities(other)) transfer(c, mine, false);
        for (TerritoryRecord r : territory.values()) if (r.faction.equals(id(other))) { r.faction = id(mine); territoryChanged = true; }
        // City accounting moved above; add the absorbed base/building generation exactly once.
        addPrestige(mine, other.getFloat("prestige")); infrastructure(mine, other.getFloat("prestigeGen"), other.getFloat("prestigeReq"));
        Map<String, int[]> destination = warps(mine);
        for (Map.Entry<String, int[]> e : warps(other).entrySet()) { String warp = e.getKey(); while (destination.containsKey(warp)) warp += "_m"; destination.put(warp, e.getValue()); renameTentWarp(e.getValue(), e.getKey(), warp, id(mine)); }
        writeWarps(mine, destination);
        for (CompoundNBT f : factions) if (f != other && f != mine) {
            CompoundNBT absorbed = relation(other, f), target = relation(mine, f);
            for (String flag : new String[]{"ally", "enemy"}) if (absorbed.getBoolean(flag)) target.putBoolean(flag, true);
            if (absorbed.getLong("enemyRemovalAt") > 0 && target.getLong("enemyRemovalAt") == 0) target.putLong("enemyRemovalAt", absorbed.getLong("enemyRemovalAt"));
            CompoundNBT reverse = relation(f, other);
            for (String flag : new String[]{"ally", "enemy"}) if (reverse.getBoolean(flag)) relation(f, mine).putBoolean(flag, true);
        }
        String former = other.getString("name"); deleteFaction(other); reconcile(mine); commit(); FactionRuntime.applyTileOwners(server); notify(mine, former + " merged into " + mine.getString("name") + "."); return "Factions merged.";
    }
    private void deleteFaction(CompoundNBT removed) {
        String uuid = id(removed); clearTerminal(removed); factions.remove(removed);
        territory.entrySet().removeIf(e -> e.getValue().faction.equals(uuid)); territoryChanged = true;
        for (CompoundNBT f : factions) { FactionService.references(f, removed.getString("name"), uuid, ""); reconcile(f); }
        FactionRuntime.forgetFaction(server, uuid);
    }
    public String delete(String name, boolean all) {
        if (all) { for (CompoundNBT f : new ArrayList<>(factions)) deleteFaction(f); territory.clear(); territoryChanged = true; }
        else { CompoundNBT f = find(name); if (f == null) throw new IllegalArgumentException("Unknown faction."); deleteFaction(f); }
        commit(); return all ? "All faction and territory data deleted." : "Faction disbanded by an administrator.";
    }
}
