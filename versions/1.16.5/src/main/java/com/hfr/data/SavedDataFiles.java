package com.hfr.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.FolderName;
import net.minecraft.world.storage.WorldSavedData;

/** Do not let Forge's missing/corrupt-file fallback turn an unreadable save into an empty replacement. */
final class SavedDataFiles {
    static Path path(MinecraftServer server, String id) { return server.getWorldPath(FolderName.ROOT).resolve("data").resolve(id + ".dat"); }
    static <T extends WorldSavedData> T get(MinecraftServer server, String id, Supplier<T> factory) {
        DimensionSavedDataManager storage = server.overworld().getDataStorage();
        T existing = storage.get(factory, id);
        if (existing != null) return existing;
        if (Files.exists(path(server, id))) throw new IllegalStateException("Cannot load " + id + ".dat; preserve and repair the existing file before using Xenofactions.");
        return storage.computeIfAbsent(factory, id);
    }
    private SavedDataFiles() { }
}
