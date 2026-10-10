package com.hfr.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;

/** A native logical claim, retaining unknown metadata while indexed records are compacted. */
public final class TerritoryRecord {
    public int zone, level;
    public boolean conquest;
    public String faction = "", legacyOwner = "", cityId = "", name = "";
    public BlockPos center = new BlockPos(-1, -1, -1);
    private final CompoundNBT extras = new CompoundNBT();
    public TerritoryRecord copy() { TerritoryRecord r = new TerritoryRecord(); r.zone = zone; r.level = level; r.conquest = conquest; r.faction = faction; r.legacyOwner = legacyOwner; r.cityId = cityId; r.name = name; r.center = center; r.extras.merge(extras.copy()); return r; }
    public boolean city() { return zone == 3 && !conquest && center.getY() >= 0 && !cityId.isEmpty(); }
    public static Map<TerritoryKey, TerritoryRecord> read(CompoundNBT root, Function<String, String> factionId) {
        Map<TerritoryKey, TerritoryRecord> result = new LinkedHashMap<>();
        for (int i = 0; i < root.getInt("territory_count"); i++) {
            if (!root.contains("code_" + i, 8) && !root.contains("code_" + i, 4)) throw new IllegalArgumentException("Missing or invalid territory coordinate " + i);
            TerritoryKey key = root.contains("code_" + i, 8) ? TerritoryKey.parse(root.getString("code_" + i))
                    : new TerritoryKey(0, (int)(root.getLong("code_" + i) >> 32), (int)root.getLong("code_" + i));
            String code = "meta_" + i; TerritoryRecord r = new TerritoryRecord();
            for (String field : root.getAllKeys()) if (field.matches(".*meta_" + i + "(?:\\D.*)?")) r.extras.put(field.replace(code, "{meta}"), root.get(field).copy());
            r.zone = root.getInt("ownership_" + code + "_zone");
            if (r.zone < 0 || r.zone > 3) throw new IllegalArgumentException("Invalid territory zone at " + key);
            r.faction = root.getString("ownership_" + code + "_ownerUuid");
            r.legacyOwner = root.getString("ownership_" + code + "_owner");
            if (r.faction.isEmpty()) r.faction = factionId.apply(r.legacyOwner);
            r.center = new BlockPos(root.getInt(code + "X"), root.getInt(code + "Y"), root.getInt(code + "Z"));
            r.cityId = root.getString("cityId_" + code); r.name = root.getString("cityName_" + code);
            if (r.name.isEmpty()) r.name = root.getString("name_" + code);
            if (r.cityId.isEmpty() && r.center.getY() >= 0) r.cityId = key.dimension + ":" + r.center.getX() + "," + r.center.getY() + "," + r.center.getZ();
            r.level = root.getInt("cityLevel_" + code);
            r.conquest = root.getBoolean("nativeConquest_" + code);
            if (result.put(key, r) != null) throw new IllegalArgumentException("Duplicate territory coordinate: " + key);
        }
        return result;
    }
    public static void write(CompoundNBT root, Map<TerritoryKey, TerritoryRecord> records, Function<String, String> factionName) {
        for (String field : new ArrayList<>(root.getAllKeys())) if (field.matches("code_\\d+") || field.matches(".*meta_\\d+(?:\\D.*)?")) root.remove(field);
        int i = 0;
        for (Map.Entry<TerritoryKey, TerritoryRecord> entry : records.entrySet()) {
            TerritoryKey k = entry.getKey(); TerritoryRecord r = entry.getValue(); String code = "meta_" + i;
            for (String field : r.extras.getAllKeys()) root.put(field.replace("{meta}", code), r.extras.get(field).copy());
            root.putString("code_" + i++, k.toString()); root.putInt("dim_" + code, k.dimension);
            root.putInt("ownership_" + code + "_zone", r.zone);
            root.putString("ownership_" + code + "_ownerUuid", r.faction);
            root.putString("ownership_" + code + "_owner", r.faction.isEmpty() ? r.legacyOwner : factionName.apply(r.faction));
            root.putInt(code + "X", r.center.getX()); root.putInt(code + "Y", r.center.getY()); root.putInt(code + "Z", r.center.getZ());
            root.putString("name_" + code, r.name); root.putString("cityName_" + code, r.name);
            root.putString("cityId_" + code, r.cityId); root.putInt("cityLevel_" + code, r.level);
            root.putBoolean("nativeConquest_" + code, r.conquest);
        }
        root.putInt("territory_count", i);
    }
}
