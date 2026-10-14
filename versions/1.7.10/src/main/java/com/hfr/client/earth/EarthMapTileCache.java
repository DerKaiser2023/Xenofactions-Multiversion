package com.hfr.client.earth;

import com.hfr.main.MainRegistry;
import com.hfr.world.earth.map.XFEarthMapData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

/** Client-thread texture ownership; bounded offline PNG decoding on one cancellable worker. */
@SideOnly(Side.CLIENT)
public final class EarthMapTileCache {
    private static final long BUDGET = 8L * 1024 * 1024;
    private static final LinkedHashMap<String, Entry> CACHE = new LinkedHashMap<String, Entry>(32, .75F, true);
    private static final Map<String, Load> PENDING = new HashMap<String, Load>();
    private static final Set<String> FAILED = new HashSet<String>();
    private static final boolean[] SLOTS = new boolean[32];
    private static ThreadPoolExecutor worker;
    private static long used;
    private static int epoch;
    private EarthMapTileCache() { }

    public static void registerReloadListener() {
        ((IReloadableResourceManager)Minecraft.getMinecraft().getResourceManager()).registerReloadListener(
                new IResourceManagerReloadListener() { @Override public void onResourceManagerReload(IResourceManager manager) { clear(); } });
    }
    public static ResourceLocation get(XFEarthMapData data, EarthMapImages images, int level, int x, int z) {
        String key = data.identity + "/" + images.sourceSha256 + "/" + level + "/" + x + "/" + z;
        Entry entry = CACHE.get(key);
        if (entry != null) return entry.location;
        if (PENDING.containsKey(key) || FAILED.contains(key)) return null;
        if (PENDING.size() >= 32) return null;
        if (worker == null) worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(32), new ThreadFactory() {
                    @Override public Thread newThread(Runnable task) {
                        Thread thread = new Thread(task, "XF Earth map PNG decoder"); thread.setDaemon(true); return thread;
                    }
                });
        Load load = null;
        try {
            // Acquire the resource on the client thread; only decode its private stream on the worker.
            InputStream stream = Minecraft.getMinecraft().getResourceManager().getResource(images.tile(level, x, z)).getInputStream();
            EarthMapImages.Level size = images.levels[level];
            load = new Load(key, stream, epoch, Math.min(256, size.width - x * 256), Math.min(256, size.height - z * 256));
            PENDING.put(key, load);
            worker.execute(load);
        } catch (Exception failure) {
            if (load != null) load.cancel();
            PENDING.remove(key); FAILED.add(key);
            report(failure);
        }
        return null;
    }
    public static boolean hasFailure() { return !FAILED.isEmpty(); }
    public static int generation() { return epoch; }
    public static void requestClear() {
        Minecraft.getMinecraft().func_152344_a(new Runnable() { @Override public void run() { clear(); } });
    }
    /** Called only on the client thread through disconnect, world unload, or resource reload. */
    public static void clear() {
        EarthMapLayerCache.clear();
        epoch++;
        for (Load load : PENDING.values()) load.cancel();
        PENDING.clear(); FAILED.clear();
        if (worker != null) { worker.shutdownNow(); worker = null; }
        for (Entry entry : CACHE.values()) release(entry);
        CACHE.clear(); used = 0;
    }
    private static void report(Exception failure) {
        if (MainRegistry.logger != null) MainRegistry.logger.warn("[XF EARTH MAP] Prepared image tile unavailable: " + failure.getMessage());
    }
    private static void release(Entry entry) {
        entry.texture.deleteGlTexture();
        // 1.7.10 deleteTexture alone leaves the old DynamicTexture/int[] registered.
        // Replace it with the shared fallback and reuse this bounded registry slot later.
        Minecraft.getMinecraft().getTextureManager().loadTexture(entry.location, TextureUtil.missingTexture);
        SLOTS[entry.slot] = false;
    }
    private static final class Entry {
        final ResourceLocation location; final DynamicTexture texture; final long bytes; final int slot;
        Entry(ResourceLocation location, DynamicTexture texture, long bytes, int slot) {
            this.location = location; this.texture = texture; this.bytes = bytes; this.slot = slot;
        }
    }
    private static final class Load implements Runnable {
        final String key; final InputStream stream; final int generation, width, height;
        volatile boolean cancelled;
        Load(String key, InputStream stream, int generation, int width, int height) {
            this.key = key; this.stream = stream; this.generation = generation; this.width = width; this.height = height;
        }
        void cancel() { cancelled = true; try { stream.close(); } catch (IOException ignored) { } }
        @Override public void run() {
            BufferedImage decoded = null; Exception error = null;
            try {
                if (!cancelled) {
                    ImageInputStream input = ImageIO.createImageInputStream(stream);
                    if (input == null) throw new IOException("Cannot read prepared PNG tile");
                    ImageReader reader = null;
                    try {
                        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                        if (!readers.hasNext()) throw new IOException("Unsupported prepared PNG tile");
                        reader = readers.next(); reader.setInput(input, true, true);
                        if (reader.getWidth(0) != width || reader.getHeight(0) != height)
                            throw new IOException("Invalid prepared tile dimensions");
                        decoded = reader.read(0);
                    } finally {
                        if (reader != null) reader.dispose(); input.close();
                    }
                }
            } catch (Exception failure) { error = failure; }
            finally { try { stream.close(); } catch (IOException ignored) { } }
            final BufferedImage image = decoded; final Exception failure = error;
            Minecraft.getMinecraft().func_152344_a(new Runnable() { @Override public void run() {
                if (cancelled || generation != epoch) { if (image != null) image.flush(); return; }
                PENDING.remove(key);
                if (failure != null || image == null) {
                    FAILED.add(key); if (failure != null) report(failure);
                    if (image != null) image.flush(); return;
                }
                long bytes = (long)width * height * 4;
                while ((used + bytes > BUDGET || CACHE.size() >= SLOTS.length) && !CACHE.isEmpty()) {
                    Map.Entry<String, Entry> oldest = CACHE.entrySet().iterator().next();
                    release(oldest.getValue());
                    used -= oldest.getValue().bytes; CACHE.remove(oldest.getKey());
                }
                int slot = 0; while (SLOTS[slot]) slot++;
                DynamicTexture texture = new DynamicTexture(image);
                ResourceLocation location = new ResourceLocation("hfr", "earthmaps/cache/slot_" + slot);
                Minecraft.getMinecraft().getTextureManager().loadTexture(location, texture);
                SLOTS[slot] = true;
                CACHE.put(key, new Entry(location, texture, bytes, slot)); used += bytes; image.flush();
            } });
        }
    }
}
