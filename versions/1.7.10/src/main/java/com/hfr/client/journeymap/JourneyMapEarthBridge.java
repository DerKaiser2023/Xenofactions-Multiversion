package com.hfr.client.journeymap;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.client.Minecraft;

/** Reflection boundary for the inspected Deadhour Forge 1.7.10 JourneyMap 6.0.7 build. */
final class JourneyMapEarthBridge {
    private final Object client, cache, lodRegistry;
    private final Method started, worldId, worldDir, region, day, night, imageSet, holder, existingHolder;
    private final Method getImage, getTexture, setImage, lockImage, unlockImage, invalidate, write, imageFile, isBound, clear;
    private final Field writeLock, dirty, blank, imagePath, lastBound, partialUpdate;

    JourneyMapEarthBridge() throws Exception {
        Class<?> clientClass = type("JourneymapClient"), files = type("io.FileHandler");
        Class<?> coord = type("model.region.RegionCoord"), map = type("model.map.MapType");
        Class<?> cacheClass = type("model.region.RegionImageCache"), set = type("model.region.RegionImageSet");
        Class<?> image = type("model.region.ImageHolder"), texture = type("texture.RegionTexture");
        Class<?> lod = type("model.region.LodChainRegistry");
        client = clientClass.getMethod("getInstance").invoke(null);
        cache = cacheClass.getField("INSTANCE").get(null); lodRegistry = lod.getField("INSTANCE").get(null);
        started = clientClass.getMethod("started"); worldId = clientClass.getMethod("getCurrentWorldId");
        worldDir = files.getMethod("getJMWorldDir", Minecraft.class);
        region = coord.getMethod("fromRegionPos", File.class, int.class, int.class, int.class);
        day = map.getMethod("day", int.class); night = map.getMethod("night", int.class);
        imageSet = cacheClass.getMethod("getRegionImageSet", coord);
        holder = set.getMethod("getHolder", map); existingHolder = set.getMethod("getExistingHolder", map);
        getImage = image.getMethod("getImage"); getTexture = image.getMethod("getTexture");
        setImage = image.getDeclaredMethod("setImage", BufferedImage.class); setImage.setAccessible(true);
        lockImage = texture.getMethod("lockImage"); unlockImage = texture.getMethod("unlockImage");
        invalidate = lod.getMethod("invalidate", map, int.class, int.class, File.class);
        write = image.getMethod("writeNextIO");
        imageFile = type("io.RegionImageHandler").getMethod("getRegionImageFile", coord, map);
        isBound = texture.getMethod("isBound"); clear = image.getMethod("clear");
        writeLock = field(image, "writeLock"); dirty = field(image, "dirty"); blank = field(image, "blank");
        imagePath = field(image, "imagePath"); lastBound = field(texture, "lastBound"); partialUpdate = field(image, "partialUpdate");
    }
    private static Class<?> type(String name) throws ClassNotFoundException { return Class.forName("journeymap.client." + name); }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    // JourneyMap world discovery and client state are queried only on the client thread.
    File currentDirectory() throws Exception {
        return Boolean.TRUE.equals(started.invoke(client)) ? (File)worldDir.invoke(null, Minecraft.getMinecraft()) : null;
    }
    String currentId() throws Exception { return String.valueOf(worldId.invoke(client)); }
    File path(File directory, int dimension, int x, int z, boolean isNight) throws Exception {
        return (File)imageFile.invoke(null, region.invoke(null, directory, x, z, dimension),
                (isNight ? night : day).invoke(null, dimension));
    }

    /** getHolder uses JourneyMap's own thread-safe publication and PNG load, without a GL upload. */
    Tile load(File directory, int dimension, int x, int z, boolean isNight) throws Exception {
        Object coord = region.invoke(null, directory, x, z, dimension);
        Object map = (isNight ? night : day).invoke(null, dimension);
        Object set = imageSet.invoke(cache, coord);
        Object image = holder.invoke(set, map);
        Path path = (Path)imagePath.get(image);
        if (path.toFile().isFile() && blank.getBoolean(image))
            throw new IOException("Refusing to replace unreadable JourneyMap region " + path);
        return new Tile(set, map, image, x, z, path);
    }

    /** Client-thread publication: lock out cartography and writers, preserve any changed 16x16 area. */
    boolean apply(Tile tile, BufferedImage background, BufferedImage previous, BufferedImage pending,
            BufferedImage ownership, boolean publish) throws Exception {
        if (existingHolder.invoke(tile.set, tile.map) != tile.image)
            throw new IOException("JourneyMap evicted the pending region " + tile.path);
        ReentrantLock mutex = (ReentrantLock)writeLock.get(tile.image);
        if (!mutex.tryLock()) return false;
        try {
            Object texture = getTexture.invoke(tile.image);
            lockImage.invoke(texture);
            try {
                BufferedImage live = (BufferedImage)getImage.invoke(tile.image);
                if (live == null || live.getWidth() != 512 || live.getHeight() != 512)
                    throw new IOException("Invalid JourneyMap region dimensions: " + tile.path);
                BufferedImage merged = publish ? new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB) : null;
                if (publish) merged.setRGB(0, 0, 512, 512, live.getRGB(0, 0, 512, 512, null, 0, 512), 0, 512);
                for (int z = 0; z < 512; z += 16) for (int x = 0; x < 512; x += 16) {
                    boolean replace = empty(live, x, z) || matches(live, previous, x, z) || matches(live, pending, x, z);
                    for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
                        int color = replace ? background.getRGB(x + dx, z + dz) : 0;
                        if (replace && publish) merged.setRGB(x + dx, z + dz, color);
                        ownership.setRGB(x + dx, z + dz, color);
                    }
                }
                if (!publish) return true;
                // setImage notifies RegionTile listeners. Its GL work is deferred to bind on the client thread.
                // Dirty LOD invalidation does disk work, so perform that explicitly on the worker in persist.
                blank.setBoolean(tile.image, false); dirty.setBoolean(tile.image, true);
                // bindRegionTexture chooses a full upload when lastBound == 0, even if cartography
                // finishes a previously queued partial update before the next render.
                lastBound.setLong(texture, 0L);
                setImage.invoke(tile.image, merged);
                return true;
            } finally { unlockImage.invoke(texture); }
        } finally { mutex.unlock(); }
    }
    private static boolean empty(BufferedImage image, int x, int z) {
        for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++)
            if ((image.getRGB(x + dx, z + dz) >>> 24) != 0) return false;
        return true;
    }
    private static boolean matches(BufferedImage live, BufferedImage owned, int x, int z) {
        if (owned == null) return false;
        boolean any = false;
        for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
            int color = owned.getRGB(x + dx, z + dz);
            any |= (color >>> 24) != 0;
            if (color != live.getRGB(x + dx, z + dz)) return false;
        }
        return any;
    }
    /** Same write lock and native atomic PNG writer used by JourneyMap, on the import worker. */
    void persist(Tile tile) throws Exception {
        ReentrantLock mutex = (ReentrantLock)writeLock.get(tile.image);
        mutex.lockInterruptibly();
        try {
            invalidate.invoke(lodRegistry, tile.map, tile.x, tile.z, tile.path.getParent().toFile());
            write.invoke(tile.image);
            // writeNextIO returns false even for a swallowed I/O failure; dirty is cleared only on success.
            if (dirty.getBoolean(tile.image) || !tile.path.toFile().isFile())
                throw new IOException("JourneyMap could not persist " + tile.path);
        } finally { mutex.unlock(); }
    }
    /** Do not retain an entire planet's RGBA buffers in JourneyMap's 30-second cache. Client thread only. */
    void release(Tile tile) throws Exception {
        ReentrantLock mutex = (ReentrantLock)writeLock.get(tile.image);
        if (!mutex.tryLock()) return;
        try {
            if (!dirty.getBoolean(tile.image) && !partialUpdate.getBoolean(tile.image)
                    && !Boolean.TRUE.equals(isBound.invoke(getTexture.invoke(tile.image)))) clear.invoke(tile.image);
        } finally { mutex.unlock(); }
    }
    static final class Tile {
        final Object set, map, image; final int x, z; final Path path;
        Tile(Object set, Object map, Object image, int x, int z, Path path) {
            this.set = set; this.map = map; this.image = image; this.x = x; this.z = z; this.path = path;
        }
    }
}
