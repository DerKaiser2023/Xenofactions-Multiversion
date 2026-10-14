package com.hfr.client.journeymap;

import com.google.gson.Gson;
import com.hfr.client.earth.EarthMapImages;
import com.hfr.client.earth.EarthMapTileCache;
import com.hfr.main.MainRegistry;
import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.client.EarthMapRequestPacket;
import com.hfr.world.earth.XFEarthBounds;
import com.hfr.world.earth.map.XFEarthMapData;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

/** Optional automatic surface preload. Never reads or generates Minecraft chunks. */
public final class XFEarthJourneyMapImport {
    private static XFEarthJourneyMapImport active;
    private JourneyMapEarthBridge bridge;
    private World world;
    private File directory;
    private String jmId;
    private int dimension, request, generation;
    private boolean requested, failed;
    private volatile Job job;

    public static void register() {
        if (!Loader.isModLoaded("journeymap")) return;
        ModContainer mod = Loader.instance().getIndexedModList().get("journeymap");
        if (mod == null || !("1.7.10-6.0.7".equals(mod.getVersion()) || "6.0.7".equals(mod.getVersion()))) {
            if (MainRegistry.logger != null) MainRegistry.logger.info("[XF EARTH JOURNEYMAP] Preload requires the inspected Forge 1.7.10 JourneyMap 6.0.7 build.");
            return;
        }
        active = new XFEarthJourneyMapImport();
        FMLCommonHandler.instance().bus().register(active); MinecraftForge.EVENT_BUS.register(active);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.theWorld || world != null && dimension != world.provider.dimensionId
                || generation != EarthMapTileCache.generation()) {
            stop(); world = mc.theWorld; dimension = world == null ? 0 : world.provider.dimensionId;
            generation = EarthMapTileCache.generation(); requested = failed = false; directory = null; jmId = null;
        }
        if (world == null || mc.thePlayer == null || failed) return;
        try {
            if (bridge == null) bridge = new JourneyMapEarthBridge();
            File current = bridge.currentDirectory(); String id = bridge.currentId();
            if (current == null) return;
            if (directory != null && (!directory.equals(current) || !id.equals(jmId))) {
                stop(); requested = false;
            }
            directory = current; jmId = id;
            if (!requested) {
                requested = true;
                // Negative tokens cannot collide with the item GUI's increasing positive request tokens.
                request = request == Integer.MIN_VALUE ? -1 : Math.min(-1, request - 1);
                PacketDispatcher.wrapper.sendToServer(new EarthMapRequestPacket(request, dimension));
            }
            if (job != null) {
                if (job.error != null) { fail(job.error); return; }
                if (job.done) {
                    if (job.imported && MainRegistry.logger != null)
                        MainRegistry.logger.info("[XF EARTH JOURNEYMAP] Preloaded " + job.regions + " surface regions for "
                                + job.data.profile.getProfile() + " in dimension " + dimension + ".");
                    job.cancel(); job = null;
                } else {
                    FutureTask<?> task = job.clientTask;
                    if (task != null && !task.isDone()) task.run(); // At most one bounded publication/resource operation per tick.
                }
            }
        } catch (Exception failure) { fail(failure); }
        catch (LinkageError failure) { fail(new IOException("JourneyMap 6.0.7 classes could not be linked", failure)); }
    }
    /** Called by the sided proxy on the client thread, independently of the item GUI. */
    public static void receive(int token, int dim, String profile, String identity, String error) {
        XFEarthJourneyMapImport value = active;
        if (value == null || value.failed || value.job != null || !value.requested || token != value.request || dim != value.dimension
                || value.world == null || value.world != Minecraft.getMinecraft().theWorld) return;
        if (!error.isEmpty()) {
            if (!"gui.hfr.earthmap.unavailable".equals(error)) value.fail(new IOException("Active Earth profile unavailable: " + error));
            return;
        }
        try {
            XFEarthMapData data = XFEarthMapData.decode(profile, identity);
            EarthMapImages images = EarthMapImages.load(data);
            value.job = new Job(value.bridge, data, images, value.directory, value.dimension, value.endpoint(), value.jmId);
            value.job.start();
        } catch (Exception failure) { value.fail(failure); }
        catch (LinkageError failure) { value.fail(new IOException("JourneyMap 6.0.7 classes could not be linked", failure)); }
    }
    private String endpoint() throws IOException {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isSingleplayer()) return "sp:" + mc.getIntegratedServer().getFolderName();
        if (mc.func_147104_D() != null) return "mp:" + mc.func_147104_D().serverIP.trim().toLowerCase(Locale.ROOT);
        if (mc.getNetHandler() != null && mc.getNetHandler().getNetworkManager().getSocketAddress() != null)
            return "mp:" + mc.getNetHandler().getNetworkManager().getSocketAddress();
        throw new IOException("No stable multiplayer endpoint for Earth preload");
    }
    private void fail(Exception failure) {
        failed = true; stop();
        if (MainRegistry.logger != null) MainRegistry.logger.warn("[XF EARTH JOURNEYMAP] Preload stopped; the Map item remains available: " + failure.getMessage(), failure);
    }
    @SubscribeEvent public void disconnect(ClientDisconnectionFromServerEvent event) {
        // FML disconnection can arrive on the network thread. Cancellation is thread-safe; state reset is deferred.
        Job current = job; if (current != null) current.cancel();
        Minecraft.getMinecraft().func_152344_a(new Runnable() { @Override public void run() { reset(); } });
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if (event.world == world && event.world.isRemote) reset();
    }
    private void reset() { stop(); world = null; directory = null; jmId = null; requested = failed = false; }
    private void stop() { if (job != null) { job.cancel(); job = null; } }

    private static final class Job implements Runnable {
        // A single shared worker serializes old and new worlds even if cancellation arrives during I/O.
        // Cancelled queued futures are purged; at most one current job and one replacement are retained.
        private static final ThreadPoolExecutor WORKER = worker();
        final JourneyMapEarthBridge bridge; final XFEarthMapData data; final EarthMapImages images;
        final File directory; final int dimension; final String endpoint, jmId;
        private volatile Future<?> future;
        private volatile InputStream openStream;
        volatile boolean cancelled, done; volatile Exception error; volatile FutureTask<?> clientTask;
        boolean imported; long regions; String fingerprint; Path records; BufferedImage raster;
        Job(JourneyMapEarthBridge bridge, XFEarthMapData data, EarthMapImages images, File directory, int dimension, String endpoint, String jmId) {
            this.bridge = bridge; this.data = data; this.images = images; this.directory = directory;
            this.dimension = dimension; this.endpoint = endpoint; this.jmId = jmId;
        }
        private static ThreadPoolExecutor worker() {
            ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1), new ThreadFactory() {
                @Override public Thread newThread(Runnable task) { Thread thread = new Thread(task, "XF Earth JourneyMap import"); thread.setDaemon(true); return thread; }
            });
            worker.allowCoreThreadTimeOut(true); return worker;
        }
        void start() { WORKER.purge(); future = WORKER.submit(this); }
        void cancel() {
            cancelled = true; FutureTask<?> task = clientTask; if (task != null) task.cancel(false);
            Future<?> running = future; if (running != null) running.cancel(true); WORKER.purge();
            InputStream stream = openStream; if (stream != null) try { stream.close(); } catch (IOException ignored) { }
        }
        void check() throws InterruptedException { if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedException(); }
        <T> T client(Callable<T> action) throws Exception {
            check(); FutureTask<T> task = new FutureTask<T>(action); clientTask = task;
            try { return task.get(); } finally { clientTask = null; }
        }
        @Override public void run() {
            try {
                check();
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(("xf-earth-jm607-v1\n" + directory.getCanonicalPath() + "\n" + endpoint + "\n" + jmId
                        + "\n" + dimension + "\n" + data.identity + "\n" + new Gson().toJson(data.profile)
                        + "\n" + new Gson().toJson(images)).getBytes("UTF-8"));
                decodeRaster(digest); check(); fingerprint = hex(digest.digest());
                Path base = directory.toPath().resolve("addon-data/hfr/earth-preload");
                // JourneyMap uses saved server names when it has no server world UUID. Do not share an import
                // silently if two actual endpoints resolve to that same native directory.
                Path owner = base.resolve("endpoint.properties"); Properties binding = read(owner);
                if (binding.containsKey("endpoint") && !endpoint.equals(binding.getProperty("endpoint")))
                    throw new IOException("JourneyMap directory is shared by different servers: " + directory
                            + "; distinct JourneyMap world IDs or distinct saved server names are required");
                binding.setProperty("endpoint", endpoint); save(owner, binding);
                records = base.resolve(hex(MessageDigest.getInstance("SHA-256").digest((endpoint + "\n" + jmId).getBytes("UTF-8"))))
                        .resolve("DIM" + dimension);
                Properties completed = read(records.resolve("complete.properties"));
                XFEarthBounds bounds = data.profile.getBounds();
                int minX = Math.floorDiv(bounds.minimumX, 512), maxX = Math.floorDiv(bounds.maximumX, 512);
                int minZ = Math.floorDiv(bounds.minimumZ, 512), maxZ = Math.floorDiv(bounds.maximumZ, 512);
                boolean cached = fingerprint.equals(completed.getProperty("fingerprint"));
                if (!cached) {
                    // A cancelled changed import must not leave the previous global completion valid.
                    completed.remove("fingerprint"); save(records.resolve("complete.properties"), completed);
                }
                for (int z = minZ;; z++) {
                    for (int x = minX;; x++) {
                        check(); importRegion(x, z, cached); regions++;
                        if (x == maxX) break;
                    }
                    if (z == maxZ) break;
                }
                // Remove only still-owned background beyond changed profile bounds; detailed pixels survive.
                Path old = records.resolve("day");
                if (Files.isDirectory(old)) try (DirectoryStream<Path> files = Files.newDirectoryStream(old, "*.png")) {
                    for (Path file : files) {
                        check(); String[] pair = file.getFileName().toString().replace(".png", "").split(",");
                        if (!file.getFileName().toString().matches("-?\\d+,-?\\d+\\.png")) continue;
                        if (pair.length != 2) throw new IOException("Invalid Earth import ownership file: " + file);
                        int x = Integer.parseInt(pair[0]), z = Integer.parseInt(pair[1]);
                        if (x < minX || x > maxX || z < minZ || z > maxZ) importRegion(x, z, false);
                    }
                }
                check(); completed.setProperty("fingerprint", fingerprint); save(records.resolve("complete.properties"), completed);
                done = true;
            } catch (InterruptedException | CancellationException stopped) { /* Disconnect/reload: no completion marker. */ }
            catch (Exception failure) { if (!cancelled) error = failure; }
            catch (LinkageError failure) { if (!cancelled) error = new IOException("JourneyMap 6.0.7 classes could not be linked", failure); }
            finally { if (raster != null) raster.flush(); }
        }
        private void decodeRaster(MessageDigest digest) throws Exception {
            final int level = images.levels.length - 1;
            EarthMapImages.Level size = images.levels[level];
            raster = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
            for (int z = 0; z < (size.height + 255) / 256; z++) for (int x = 0; x < (size.width + 255) / 256; x++) {
                check(); final int tx = x, tz = z;
                // Resource manager access stays on the client thread. Decoding and hashing use a private stream.
                InputStream stream = client(new Callable<InputStream>() { @Override public InputStream call() throws Exception {
                    InputStream input = Minecraft.getMinecraft().getResourceManager().getResource(images.tile(level, tx, tz)).getInputStream();
                    openStream = input;
                    if (cancelled) { input.close(); throw new InterruptedException(); }
                    return input;
                } });
                byte[] bytes;
                try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096]; int total = 0;
                    for (int count; (count = input.read(buffer)) != -1;) {
                        check(); total += count; if (total > 1024 * 1024) throw new IOException("Oversize prepared Earth tile");
                        output.write(buffer, 0, count);
                    }
                    bytes = output.toByteArray();
                } finally { openStream = null; }
                digest.update(bytes);
                int width = Math.min(256, size.width - x * 256), height = Math.min(256, size.height - z * 256);
                BufferedImage tile;
                try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    if (input == null || !ImageIO.getImageReaders(input).hasNext()) throw new IOException("Invalid prepared Earth tile");
                    ImageReader reader = ImageIO.getImageReaders(input).next();
                    try {
                        reader.setInput(input, true, true);
                        if (reader.getWidth(0) != width || reader.getHeight(0) != height) throw new IOException("Invalid prepared Earth tile dimensions");
                        tile = reader.read(0);
                    } finally { reader.dispose(); }
                }
                raster.setRGB(x * 256, z * 256, width, height, tile.getRGB(0, 0, width, height, null, 0, width), 0, width); tile.flush();
            }
        }
        private void importRegion(int x, int z, boolean cached) throws Exception {
            // Native files are resolved by the inspected classes, never by a guessed storage path.
            for (int mode = 0; mode < 2; mode++) {
                check(); File nativeFile = bridge.path(directory, dimension, x, z, mode == 1);
                Path folder = records.resolve(mode == 1 ? "night" : "day");
                String name = x + "," + z; Path ownedFile = folder.resolve(name + ".png"), pendingFile = folder.resolve(name + ".pending.png");
                Path marker = folder.resolve(name + ".properties");
                if (!Files.exists(pendingFile) && (cached || fingerprint.equals(read(marker).getProperty("fingerprint"))) && nativeFile.isFile()) continue;
                final JourneyMapEarthBridge.Tile tile = bridge.load(directory, dimension, x, z, mode == 1);
                final BufferedImage previous = image(ownedFile), pending = image(pendingFile), background = background(x, z);
                final BufferedImage ownership = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
                boolean applied = false;
                while (!applied) {
                    check(); applied = client(new Callable<Boolean>() { @Override public Boolean call() throws Exception {
                        if (cancelled) return false;
                        return bridge.apply(tile, background, previous, pending, ownership, false);
                    } });
                }
                // A journal written before the native save permits safe restart after interruption.
                check(); png(pendingFile, ownership);
                applied = false;
                while (!applied) {
                    check(); applied = client(new Callable<Boolean>() { @Override public Boolean call() throws Exception {
                        if (cancelled) return false;
                        return bridge.apply(tile, background, previous, pending, ownership, true);
                    } });
                }
                check(); bridge.persist(tile); check();
                // Publication rechecked cartography after the journal; save the actual ownership.
                png(pendingFile, ownership);
                Files.move(pendingFile, ownedFile, StandardCopyOption.REPLACE_EXISTING);
                Properties state = new Properties(); state.setProperty("fingerprint", fingerprint); save(marker, state);
                client(new Callable<Void>() { @Override public Void call() throws Exception { bridge.release(tile); return null; } });
                imported = true;
                background.flush(); ownership.flush(); if (previous != null) previous.flush(); if (pending != null) pending.flush();
            }
        }
        private BufferedImage background(int rx, int rz) throws InterruptedException {
            BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
            for (int z = 0; z < 512; z++) {
                check(); long worldZ = (long)rz * 512 + z;
                for (int x = 0; x < 512; x++) {
                    long worldX = (long)rx * 512 + x;
                    if (data.contains(worldX, worldZ)) {
                        int sx = (int)Math.floor(data.mapX(worldX) * raster.getWidth());
                        int sz = (int)Math.floor(data.mapZ(worldZ) * raster.getHeight());
                        // Native surface RGB.toInteger produces alpha 255. Retaining alpha 254 on our
                        // background makes even an identically colored explored chunk cease matching ownership.
                        image.setRGB(x, z, 0xFE000000 | (raster.getRGB(sx, sz) & 0xFFFFFF));
                    }
                }
            }
            return image;
        }
        private static BufferedImage image(Path path) throws IOException {
            if (!Files.exists(path)) return null;
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null || image.getWidth() != 512 || image.getHeight() != 512)
                throw new IOException("Invalid Earth ownership image: " + path);
            return image;
        }
        private static Properties read(Path path) throws IOException {
            Properties values = new Properties();
            if (Files.exists(path)) try (InputStream input = Files.newInputStream(path)) { values.load(input); }
            return values;
        }
        private static void save(Path path, Properties values) throws IOException {
            Files.createDirectories(path.getParent()); Path temp = path.resolveSibling(path.getFileName() + ".new");
            try (OutputStream output = Files.newOutputStream(temp)) { values.store(output, "XF Earth JourneyMap 6.0.7 preload"); }
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
        private static void png(Path path, BufferedImage image) throws IOException {
            Files.createDirectories(path.getParent()); Path temp = path.resolveSibling(path.getFileName() + ".new");
            if (!ImageIO.write(image, "PNG", temp.toFile())) throw new IOException("PNG writer unavailable");
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
        private static String hex(byte[] bytes) {
            StringBuilder text = new StringBuilder(); for (byte value : bytes) text.append(String.format(Locale.ROOT, "%02x", value & 255)); return text.toString();
        }
    }
}
