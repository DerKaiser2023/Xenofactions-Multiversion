package com.hfr.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import com.hfr.server.FactionService;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.WorldSavedData;

/** Native save binding. Only implemented faction fields are edited; other data remains opaque. */
public final class FactionSavedData extends WorldSavedData {
    public static final String ID = "hfr_clowder";
    private CompoundNBT payload = new CompoundNBT();
    private Path backingFile;
    private boolean backupChecked;
    private long revision;
    public long revision() { return revision; }
    public FactionSavedData() { super(ID); }
    public static FactionSavedData get(MinecraftServer server) {
        FactionSavedData data = SavedDataFiles.get(server, ID, FactionSavedData::new);
        data.backingFile = SavedDataFiles.path(server, ID);
        return data;
    }
    @Override public void load(CompoundNBT nbt) {
        if (!nbt.contains("clowderCount", 3) || nbt.getInt("clowderCount") < 0 || nbt.getInt("territory_count") < 0)
            throw new IllegalArgumentException("Invalid Clowder data counts");
        FactionService.validate(nbt);
        payload = nbt.copy();
    }
    public CompoundNBT snapshot() { return payload.copy(); }
    public void edit(CompoundNBT expected, CompoundNBT next) {
        if (!payload.equals(expected)) throw new IllegalStateException("Faction data changed; retry the command.");
        FactionService.validate(next);
        prepareEdit();
        payload = next.copy(); revision++; setDirty();
    }
    public void prepareEdit() {
        if (!backupChecked) {
            try {
                if (Files.exists(backingFile)) {
                    Path backup = backingFile.resolveSibling(backingFile.getFileName() + ".migration-1.16.5.bak");
                    if (!Files.exists(backup)) Files.copy(backingFile, backup);
                }
                backupChecked = true;
            } catch (IOException failure) { throw new IllegalStateException("Cannot back up faction data; no changes made.", failure); }
        }
    }
    @Override public CompoundNBT save(CompoundNBT nbt) { nbt.merge(payload.copy()); return nbt; }
    public List<String> names() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < payload.getInt("clowderCount"); i++) result.add(payload.getString(i + "_name"));
        return Collections.unmodifiableList(result);
    }
    public String describe(String name) {
        for (int i = 0; i < payload.getInt("clowderCount"); i++) {
            if (!FactionService.canonical(payload.getString(i + "_name")).equalsIgnoreCase(FactionService.canonical(name))) continue;
            String prefix = i + "_";
            return payload.getString(prefix + "name") + " | members: " + payload.getInt(prefix + "members")
                    + " | leader: " + payload.getString(prefix + "leader") + " | capital: "
                    + (payload.getBoolean(prefix + "capitalDesignated") ? payload.getString(prefix + "capitalCityId") : "not designated");
        }
        throw new IllegalArgumentException("Unknown faction: " + name);
    }
    public List<String> cities() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < payload.getInt("territory_count"); i++) {
            String code = "meta_" + i;
            if (payload.getInt("ownership_" + code + "_zone") != 3 || payload.getBoolean("nativeConquest_" + code)) continue;
            String id = payload.getString("cityId_" + code);
            if (id.isEmpty()) continue;
            String city = payload.getString("cityName_" + code);
            String line = city + " | " + id + " | " + payload.getString("ownership_" + code + "_owner")
                    + " | legacy dimension " + payload.getInt("dim_" + code);
            if (!result.contains(line)) result.add(line);
        }
        return Collections.unmodifiableList(result);
    }
}
