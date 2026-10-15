package com.hfr.clowder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.hfr.clowder.ClaimNodeSystem.Eligibility;
import com.hfr.clowder.ClaimNodeSystem.Geometry;
import com.hfr.clowder.ClowderTerritory.CoordPair;
import com.hfr.config.XFConfig;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.IPlantable;

/** Immediate ownership policy. No saved classification, geographic profile, queue or grant callbacks. */
final class ClaimSurfaceCheck {
    private static final int SURFACE_DEPTH = 32;
    private final World world;
    private final List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
    private boolean unavailable;

    private ClaimSurfaceCheck(World world) { this.world = world; }

    static ClaimTerrainService.Decision check(World world, CoordPair key) {
        if(world == null || world.isRemote || key.dimensionId != world.provider.dimensionId)
            return new ClaimTerrainService.Decision(Eligibility.UNCLASSIFIED, "Owning server dimension unavailable");
        // Enforce the existing owning-thread contract before any terrain access. The optional
        // classification payload need not be available or have a supported saved schema.
        ClaimNodeSystem.store(world);
        return new ClaimSurfaceCheck(world).check(new Geometry(key.x, key.z));
    }

    private ClaimTerrainService.Decision check(Geometry geometry) {
        int width = (int)(geometry.maxX - geometry.minX), depth = (int)(geometry.maxZ - geometry.minZ);
        if(width < 1 || width > 31 || depth < 1 || depth > 31 || world.getHeight() < 1 || world.getHeight() > 256
            || geometry.minX <= -30000000 || geometry.maxX >= 30000000 || geometry.minZ <= -30000000 || geometry.maxZ >= 30000000)
            return new ClaimTerrainService.Decision(Eligibility.UNCLASSIFIED, "Unsupported loaded terrain bounds");
        int total = width * depth;
        int required = Math.max(XFConfig.claimTerrainMinArea, (total * XFConfig.claimTerrainCoveragePercent + 99) / 100);
        int[] parent = new int[total], size = new int[total];
        double[] heights = new double[total];
        Arrays.fill(parent, -1);
        int largest = 0;
        for(int i = 0; i < total; i++) {
            int x = (int)geometry.minX + i % width, z = (int)geometry.minZ + i / width;
            if(!loadedHalo(x, z)) { unavailable = true; continue; }
            double top;
            try {
                Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
                int height = Math.min(world.getHeight() - 1, chunk.getHeightValue(x & 15, z & 15) - 1);
                // The height map excludes transparent decks. Inspect the highest populated
                // section as well, without traversing the air gap down to the height map.
                int upper = Math.min(world.getHeight() - 1, chunk.getTopFilledSegment() + 15);
                top = surface(x, z, upper, Math.max(height + 1, upper - SURFACE_DEPTH + 1));
                if(Double.isNaN(top)) {
                    int bottom = Math.max(0, height - SURFACE_DEPTH + 1);
                    top = surface(x, z, height, bottom);
                    if(Double.isNaN(top) && bottom > 0) unavailable = true;
                }
            } catch(RuntimeException unsupported) {
                unavailable = true;
                continue;
            }
            if(!Double.isFinite(top)) continue;
            heights[i] = top; parent[i] = i; size[i] = 1;
            if(i % width > 0) join(i, i - 1, parent, size, heights);
            if(i >= width) join(i, i - width, parent, size, heights);
            largest = Math.max(largest, size[root(i, parent)]);
            if(largest >= required) return new ClaimTerrainService.Decision(Eligibility.ELIGIBLE,
                "Confirmed connected surface " + largest + "/" + total + " columns (need " + required + ")");
        }
        return new ClaimTerrainService.Decision(unavailable ? Eligibility.UNCLASSIFIED : Eligibility.INELIGIBLE,
            "Confirmed connected surface " + largest + "/" + total + " columns (need " + required + ")"
            + (unavailable ? "; some columns are unloaded or outside the bounded surface check" : ""));
    }

    private boolean loadedHalo(int x, int z) {
        for(int cx = (x - 1) >> 4; cx <= (x + 1) >> 4; cx++)
            for(int cz = (z - 1) >> 4; cz <= (z + 1) >> 4; cz++)
                if(!world.getChunkProvider().chunkExists(cx, cz)) return false;
        return true;
    }

    /** NaN means no surface in this window; -infinity means liquid blocked the column. */
    private double surface(int x, int z, int start, int end) {
        for(int y = start; y >= end; y--) {
            Block block = world.getBlock(x, y, z);
            if(block == Blocks.air) continue;
            if(block.getMaterial().isLiquid()) return Double.NEGATIVE_INFINITY; // Never visit the seabed.
            if(!solid(world, block, x, y, z)) continue;
            boxes.clear();
            block.addCollisionBoxesToList(world, x, y, z, AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 2, z + 1), boxes, null);
            double top = top(boxes, x, y, z);
            if(Double.isFinite(top)) return top; // Support underneath a dry deck is irrelevant.
        }
        return Double.NaN;
    }

    private static int root(int i, int[] parent) {
        while(parent[i] != i) { parent[i] = parent[parent[i]]; i = parent[i]; }
        return i;
    }

    private static void join(int a, int b, int[] parent, int[] size, double[] heights) {
        if(parent[b] < 0 || Math.abs(heights[a] - heights[b]) > XFConfig.claimTerrainMaxStep + .000001) return;
        int ra = root(a, parent), rb = root(b, parent);
        if(ra == rb) return;
        if(size[ra] < size[rb]) { int swap = ra; ra = rb; rb = swap; }
        parent[rb] = ra; size[ra] += size[rb];
    }

    static boolean solid(World world, Block block, int x, int y, int z) {
        Material material = block.getMaterial();
        return material.blocksMovement() && !block.isReplaceable(world, x, y, z)
            && material != Material.leaves && material != Material.plants && material != Material.vine
            && material != Material.cactus && material != Material.gourd && material != Material.ice && material != Material.packedIce
            && !(block instanceof BlockBush) && !(block instanceof IPlantable)
            && !block.isLeaves(world, x, y, z) && !block.isFoliage(world, x, y, z);
    }

    /** Full-cell coplanar collision union; shared with the optional background classifier. */
    static double top(List<AxisAlignedBB> boxes, int x, int y, int z) {
        if(boxes.size() > 32) throw new IllegalStateException("Collision box bound exceeded");
        double highest = Double.NEGATIVE_INFINITY, full = Double.NEGATIVE_INFINITY;
        for(AxisAlignedBB b : boxes) {
            if(b == null || !Double.isFinite(b.minX) || !Double.isFinite(b.minY) || !Double.isFinite(b.minZ)
                || !Double.isFinite(b.maxX) || !Double.isFinite(b.maxY) || !Double.isFinite(b.maxZ)
                || b.maxX < b.minX || b.maxY < b.minY || b.maxZ < b.minZ)
                throw new IllegalStateException("Unsupported collision geometry");
            highest = Math.max(highest, b.maxY);
            if(b.minX <= x + .000001 && b.maxX >= x + 1 - .000001 && b.minZ <= z + .000001 && b.maxZ >= z + 1 - .000001
                && b.minY >= y - .000001 && b.maxY <= y + 1.000001 && b.maxY - b.minY >= .125) full = Math.max(full, b.maxY);
        }
        if(full >= highest - .000001) return full;
        if(!Double.isFinite(highest) || highest > y + 1.000001 || highest < y + .125) return Double.NEGATIVE_INFINITY;
        List<AxisAlignedBB> faces = new ArrayList<AxisAlignedBB>();
        double[] edges = new double[66]; int count = 2; edges[0] = z; edges[1] = z + 1;
        for(AxisAlignedBB b : boxes) if(Math.abs(b.maxY - highest) <= .000001 && b.minY >= y - .000001 && b.maxY - b.minY >= .125
            && b.maxX > x && b.minX < x + 1 && b.maxZ > z && b.minZ < z + 1) {
            faces.add(b); edges[count++] = Math.max(z, b.minZ); edges[count++] = Math.min(z + 1, b.maxZ);
        }
        java.util.Collections.sort(faces, new java.util.Comparator<AxisAlignedBB>() {
            public int compare(AxisAlignedBB a, AxisAlignedBB b) { return Double.compare(a.minX, b.minX); }
        });
        Arrays.sort(edges, 0, count);
        for(int i = 1; i < count; i++) {
            if(edges[i] - edges[i - 1] <= .000001) continue;
            double mid = (edges[i] + edges[i - 1]) * .5, covered = x;
            for(AxisAlignedBB b : faces) if(b.minZ <= mid && b.maxZ >= mid) {
                if(b.minX > covered + .000001) return Double.NEGATIVE_INFINITY;
                covered = Math.max(covered, b.maxX); if(covered >= x + 1 - .000001) break;
            }
            if(covered < x + 1 - .000001) return Double.NEGATIVE_INFINITY;
        }
        return highest;
    }
}
