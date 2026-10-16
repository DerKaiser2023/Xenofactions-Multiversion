package com.hfr.client.journeymap;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFireworkSparkFX;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/** Client-thread queue and one shared budget for territory and local TDM border sparks. */
@SideOnly(Side.CLIENT)
public final class ClientBorderParticles {
    private static final ArrayDeque<Segment> PENDING = new ArrayDeque<Segment>();
    // ChunkProviderClient.chunkExists always returns true. Track actual Forge load/unload
    // notifications instead of asking a provider to fetch a potentially absent chunk.
    private static final Set<Long> LOADED = new HashSet<Long>();
    private static World world;
    private static int tick;
    private static volatile boolean resetRequested;

    private ClientBorderParticles() { }

    public static void requestClear() { resetRequested = true; }

    private static void useWorld(World current) {
        if (resetRequested || world != current) {
            PENDING.clear(); LOADED.clear(); world = current; tick = 0; resetRequested = false;
        }
    }

    public static void loaded(Chunk chunk) {
        if (!chunk.worldObj.isRemote) return;
        useWorld(chunk.worldObj);
        LOADED.add(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(chunk.xPosition, chunk.zPosition)));
    }

    public static void unloaded(Chunk chunk) {
        if (chunk.worldObj == world)
            LOADED.remove(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(chunk.xPosition, chunk.zPosition)));
    }

    public static void worldUnloaded(World unloaded) {
        if (unloaded == world) requestClear();
    }

    public static void beginTick() {
        useWorld(Minecraft.getMinecraft().theWorld);
        tick++;
    }

    /** Called only on the client thread; invalid or excessive work is discarded. */
    public static void enqueue(World current, int x1, int z1, int x2, int z2, int color) {
        Minecraft mc = Minecraft.getMinecraft();
        if (current == null || current != mc.theWorld || !current.isRemote || mc.thePlayer == null
                || resetRequested || !ClientBorderVisuals.enabled() || mc.gameSettings.particleSetting == 2) return;
        useWorld(current);
        long dx = (long)x2 - x1, dz = (long)z2 - z1;
        long length = Math.max(Math.abs(dx), Math.abs(dz));
        if ((dx != 0 && dz != 0) || length == 0 || length > 128 || PENDING.size() >= 128) return;
        int samples = Math.max(1, Math.min(16, (int)Math.ceil(length / 6D)));
        if (mc.gameSettings.particleSetting == 1) samples = (samples + 1) / 2;
        PENDING.addLast(new Segment(x1, z1, x2, z2, color, samples, tick));
    }

    /** One END call per client tick, after local TDM segments have joined the same queue. */
    public static void endTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (resetRequested || world == null || world != mc.theWorld || mc.thePlayer == null
                || !ClientBorderVisuals.enabled() || mc.gameSettings.particleSetting == 2) {
            PENDING.clear();
            return;
        }
        int budget = mc.gameSettings.particleSetting == 1 ? 48 : 96;
        int attempts = 256;
        while (budget > 0 && attempts-- > 0 && !PENDING.isEmpty()) {
            Segment segment = PENDING.removeFirst();
            if (tick - segment.created > 12) continue;
            double along = (segment.next++ + world.rand.nextDouble()) / segment.samples;
            double x = segment.x1 + ((long)segment.x2 - segment.x1) * along;
            double z = segment.z1 + ((long)segment.z2 - segment.z1) * along;
            if (segment.next < segment.samples) PENDING.addLast(segment);
            double dx = x - mc.thePlayer.posX, dz = z - mc.thePlayer.posZ;
            if (dx * dx + dz * dz > 96D * 96D) continue;
            int bx = (int)Math.floor(x), bz = (int)Math.floor(z - 0.01D);
            if (!LOADED.contains(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(bx >> 4, bz >> 4)))
                    || !LOADED.contains(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(bx >> 4, ((int)Math.floor(z)) >> 4)))) continue;
            // Only query terrain after distance and actual loaded-column checks. No chunk fetch here.
            double y = world.getHeightValue(bx, bz) + 0.25D;
            EntityFireworkSparkFX spark = new EntityFireworkSparkFX(world, x, y, z, 0,
                    0.4 * world.rand.nextGaussian() + 0.6, 0, mc.effectRenderer);
            spark.setColour(segment.color);
            mc.effectRenderer.addEffect(spark);
            budget--;
        }
    }

    private static final class Segment {
        final int x1, z1, x2, z2, color, samples, created;
        int next;
        Segment(int x1, int z1, int x2, int z2, int color, int samples, int created) {
            this.x1 = x1; this.z1 = z1; this.x2 = x2; this.z2 = z2;
            this.color = color; this.samples = samples; this.created = created;
        }
    }
}
