package com.hfr.server;

import com.hfr.config.FoundationConfig;
import com.hfr.data.TerritoryKey;
import com.hfr.data.TerritoryRecord;
import com.hfr.network.FoundationNetwork;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;

/** Server-owned transition tracking and the reference's nearby exposed-edge particles. */
final class BorderFeedback {
    private final Map<UUID, Location> previous = new HashMap<>();
    private static final class Location {
        final ResourceLocation dimension; final int zone; final String faction, name, city;
        Location(ResourceLocation dimension, int zone, String faction, String name, String city) {
            this.dimension = dimension; this.zone = zone; this.faction = faction; this.name = name; this.city = city;
        }
        boolean matches(ResourceLocation dimension, int zone, String faction, String name, String city) {
            return this.dimension.equals(dimension) && this.zone == zone && this.faction.equals(faction) && this.name.equals(name) && this.city.equals(city);
        }
    }
    void clear() { previous.clear(); }
    void forget(ServerPlayerEntity player) { previous.remove(player.getUUID()); }
    void tick(FactionGameplay gameplay, ServerPlayerEntity player) {
        ResourceLocation dimension = player.level.dimension().location();
        Integer legacyDimension = FoundationConfig.legacyId(dimension);
        // Match flagPopup's truncation and Z-1 input; do not change authoritative claim/protection lookup.
        TerritoryRecord claim = legacyDimension == null ? null : gameplay.territoryAt(
                TerritoryKey.lookup(legacyDimension, (int)player.getX(), (int)player.getZ() - 1));
        int zone = gameplay.zone(claim);
        CompoundNBT owner = gameplay.owner(claim);
        String city = zone == 3 && claim != null ? claim.name : "";
        String faction = FactionGameplay.id(owner), factionName = owner == null ? "" : owner.getString("name");
        Location past = previous.get(player.getUUID());
        boolean changed = past == null || !past.matches(dimension, zone, faction, factionName, city);
        if (changed) previous.put(player.getUUID(), new Location(dimension, zone, faction, factionName, city));
        boolean popup = past != null && changed;
        int[] edges = null; int edgeCount = 0;
        if (legacyDimension != null && player.level.random.nextInt(3) == 0) {
            edges = new int[128 * 5];
            TerritoryKey around = TerritoryKey.generated(legacyDimension, (int)Math.floor(player.getX()), (int)Math.floor(player.getZ()));
            for (int x = -4; x < 4; x++) for (int z = -4; z < 4; z++) {
                int tx = around.x + x, tz = around.z + z;
                TerritoryRecord center = gameplay.territoryAt(new TerritoryKey(legacyDimension, tx, tz));
                TerritoryRecord north = gameplay.territoryAt(new TerritoryKey(legacyDimension, tx, tz - 1));
                TerritoryRecord west = gameplay.territoryAt(new TerritoryKey(legacyDimension, tx - 1, tz));
                long x1 = TerritoryKey.min(tx), x2 = TerritoryKey.max(tx), z1 = TerritoryKey.min(tz), z2 = TerritoryKey.max(tz);
                if (x1 < Integer.MIN_VALUE || x2 > Integer.MAX_VALUE || z1 < Integer.MIN_VALUE || z2 > Integer.MAX_VALUE) continue;
                if (different(gameplay, center, north)) edgeCount = edge(edges, edgeCount, (int)x1, (int)z1, (int)x2, (int)z1, edgeColor(gameplay, center, north));
                if (different(gameplay, center, west)) edgeCount = edge(edges, edgeCount, (int)x1, (int)z1, (int)x1, (int)z2, edgeColor(gameplay, center, west));
            }
        }
        if (!popup && edgeCount == 0) return;
        String name = !popup ? "" : zone == 3 ? FactionGameplay.decorated(owner) : zone == 0 ? "Safe Zone" : zone == 1 ? "War Zone" : "Wilderness";
        int[] segments = edgeCount == 0 ? new int[0] : Arrays.copyOf(edges, edgeCount);
        FoundationNetwork.border(player, dimension, popup, zone == 3 ? owner.getInt("flag") : zone == 0 ? -2 : zone == 1 ? -3 : -1,
                color(gameplay, claim), popup ? name : "", popup ? city : "", segments);
    }
    private static boolean different(FactionGameplay g, TerritoryRecord a, TerritoryRecord b) { return g.zone(a) != g.zone(b) || g.owner(a) != g.owner(b); }
    private static int color(FactionGameplay g, TerritoryRecord claim) {
        int zone = g.zone(claim); return zone == 0 ? 0xFF8000 : zone == 1 ? 0xFF0000 : zone == 3 ? g.owner(claim).getInt("color") & 0xFFFFFF : 0xFFFFFF;
    }
    private static int edgeColor(FactionGameplay g, TerritoryRecord a, TerritoryRecord b) {
        int ac = g.zone(a) == 2 ? color(g, b) : color(g, a), bc = g.zone(b) == 2 ? ac : color(g, b);
        return (ac + bc) / 2;
    }
    private static int edge(int[] edges, int index, int x1, int z1, int x2, int z2, int color) {
        edges[index++] = x1; edges[index++] = z1; edges[index++] = x2; edges[index++] = z2; edges[index++] = color;
        return index;
    }
}
