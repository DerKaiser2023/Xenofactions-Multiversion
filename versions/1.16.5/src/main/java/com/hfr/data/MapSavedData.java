package com.hfr.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.WorldSavedData;

/** Map metadata editing only; imported match state is retained but never executed. */
public final class MapSavedData extends WorldSavedData {
    public static final String ID = "xenofactions_tdm";
    private CompoundNBT payload = new CompoundNBT();
    private Path backingFile;
    private boolean backupChecked;
    public MapSavedData() { super(ID); }
    public static MapSavedData get(MinecraftServer server) {
        MapSavedData data = SavedDataFiles.get(server, ID, MapSavedData::new);
        data.backingFile = SavedDataFiles.path(server, ID);
        return data;
    }
    @Override public void load(CompoundNBT nbt) {
        if (!nbt.contains("mapCount", 3) || nbt.getInt("mapCount") < 0) throw new IllegalArgumentException("Invalid map count");
        for (int i = 0; i < nbt.getInt("mapCount"); i++) {
            if (!nbt.contains("map" + i, 10) || normalize(nbt.getCompound("map" + i).getString("name")).isEmpty())
                throw new IllegalArgumentException("Invalid map record " + i);
        }
        payload = nbt.copy();
    }
    @Override public CompoundNBT save(CompoundNBT nbt) { nbt.merge(payload.copy()); return nbt; }
    public static String normalize(String name) { return name == null ? "" : name.trim().toLowerCase(Locale.ROOT); }
    private int index(String name) {
        for (int i = 0; i < payload.getInt("mapCount"); i++)
            if (normalize(payload.getCompound("map" + i).getString("name")).equals(normalize(name))) return i;
        return -1;
    }
    public CompoundNBT map(String name) { int index = index(name); return index < 0 ? null : payload.getCompound("map" + index).copy(); }
    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < payload.getInt("mapCount"); i++) names.add(normalize(payload.getCompound("map" + i).getString("name")));
        return names;
    }
    public void create(String name) {
        name = normalize(name);
        if (name.isEmpty() || name.length() > 80 || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Map names must contain 1-80 printable characters.");
        if (index(name) >= 0) throw new IllegalArgumentException("Map already exists.");
        backupBeforeEdit();
        CompoundNBT map = new CompoundNBT();
        map.putString("name", name); map.putString("mode", "DEATHMATCH"); map.putInt("supportedModes", 1);
        map.putBoolean("votingEnabled", false); map.putString("terroristTeam", "red");
        map.putBoolean("buyScoreEnabled", true); map.putInt("killScoreReward", 1);
        map.putInt("roundLossBuyScoreReward", 1); map.putInt("killBuyScoreReward", 2);
        map.putInt("roundWinBuyScoreReward", 3); map.putInt("bombPlantBuyScoreReward", 1);
        map.putInt("spawnSetsVersion", 1);
        int count = payload.getInt("mapCount"); payload.put("map" + count, map); payload.putInt("mapCount", count + 1);
        if (payload.getString("selectedMap").isEmpty()) { payload.putString("selectedMap", name); payload.putString("selectedMode", "DEATHMATCH"); }
        setDirty();
    }
    public void setBounds(String name, CompoundNBT expected, CompoundNBT bounds) {
        int index = index(name);
        if (index < 0 || !payload.getCompound("map" + index).equals(expected)) throw new IllegalArgumentException("Map changed; reopen the editor before saving.");
        backupBeforeEdit();
        CompoundNBT next = expected.copy(); next.put("bounds", bounds.copy()); payload.put("map" + index, next); setDirty();
    }
    private void backupBeforeEdit() {
        if (backupChecked) return;
        try {
            if (Files.exists(backingFile)) {
                Path backup = backingFile.resolveSibling(backingFile.getFileName() + ".migration-1.16.5.bak");
                if (!Files.exists(backup)) Files.copy(backingFile, backup);
            }
            backupChecked = true;
        } catch (IOException failure) { throw new IllegalStateException("Cannot back up existing map data; no changes made.", failure); }
    }
}
