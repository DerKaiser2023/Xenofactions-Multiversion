package com.hfr.clowder;

import java.util.ArrayList;
import java.util.List;

import com.hfr.clowder.ClaimNodeSystem.Eligibility;
import com.hfr.clowder.ClaimNodeSystem.Geometry;
import com.hfr.clowder.ClowderTerritory.CoordPair;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.IPlantable;

/** Immediate, loaded-only ownership policy. Diagnostic caches never authorize grants. */
final class ClaimSurfaceCheck {
    private static final int SURFACE_PROBES = 64;
    private final World world;
    private final List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();

    ClaimSurfaceCheck(World world) { this.world = world; }

    static ClaimTerrainService.Decision check(World world, CoordPair key) {
        if(world == null || world.isRemote || key.dimensionId != world.provider.dimensionId)
            return new ClaimTerrainService.Decision(Eligibility.UNCLASSIFIED, "Owning server dimension unavailable");
        // Enforce the owning-thread contract before reading terrain, without requiring saved evidence.
        ClaimNodeSystem.store(world);
        return new ClaimSurfaceCheck(world).check(new Geometry(key.x, key.z));
    }

    private ClaimTerrainService.Decision check(Geometry geometry) {
        int width = (int)(geometry.maxX - geometry.minX), depth = (int)(geometry.maxZ - geometry.minZ);
        if(width < 1 || width > 31 || depth < 1 || depth > 31 || world.getHeight() < 1 || world.getHeight() > 256
            || geometry.minX <= -30000000 || geometry.maxX >= 30000000 || geometry.minZ <= -30000000 || geometry.maxZ >= 30000000)
            return new ClaimTerrainService.Decision(Eligibility.UNCLASSIFIED, "Unsupported loaded terrain bounds");
        int total = width * depth, unavailable = 0;
        for(int i = 0; i < total; i++) {
            byte column = column((int)geometry.minX + i % width, (int)geometry.minZ + i / width);
            if(column == ClaimTerrainClassifier.ELIGIBLE)
                return new ClaimTerrainService.Decision(Eligibility.ELIGIBLE,
                    "A surface column is not open water at least three consecutive water blocks deep");
            if(column == ClaimTerrainClassifier.UNKNOWN) unavailable++;
        }
        if(unavailable > 0) return new ClaimTerrainService.Decision(Eligibility.UNCLASSIFIED,
            unavailable + "/" + total + " surface columns unavailable or outside the bounded surface check; water rejection not established");
        return new ClaimTerrainService.Decision(Eligibility.INELIGIBLE,
            "Every surface column (" + total + "/" + total + ") is open water at least three consecutive water blocks deep");
    }

    /** Shared with diagnostics; at most 64 surface probes and two additional water reads. */
    byte column(int x, int z) {
        if(!world.getChunkProvider().chunkExists(x >> 4, z >> 4)) return ClaimTerrainClassifier.UNKNOWN;
        try {
            Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
            int localX = x & 15, localZ = z & 15;
            // Reuse an already computed, block-write-invalidated precipitation height: unlike
            // the opacity height map it includes glass decks. Never trigger its lazy full scan.
            int rain = chunk.precipitationHeightMap[localX | localZ << 4];
            int y = Math.min(world.getHeight() - 1, rain >= 0 ? rain - 1 : chunk.getTopFilledSegment() + 15);
            ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
            int probes = 0;
            while(y >= 0 && probes < SURFACE_PROBES) {
                // Skip absent sections without probing the air gap or looking underground.
                if(sections[y >> 4] == null) { y = (y & ~15) - 1; continue; }
                Block block = chunk.getBlock(localX, y, localZ);
                probes++;
                Material material = block.getMaterial();
                if(material == Material.water) {
                    // Stop at the first water surface. The seabed is never tested as land.
                    for(int depth = 1; depth < 3; depth++)
                        if(y - depth < 0 || chunk.getBlock(localX, y - depth, localZ).getMaterial() != Material.water)
                            return ClaimTerrainClassifier.ELIGIBLE;
                    return ClaimTerrainClassifier.DEEP_WATER;
                }
                if(material.isLiquid()) return ClaimTerrainClassifier.ELIGIBLE; // Only water can reject.
                if(surfaceCandidate(block)) {
                    // Modded collision callbacks may inspect neighboring blocks. Never request
                    // an unloaded collision halo; ordinary water reads need no neighbors.
                    if(!loadedHalo(x, z)) return ClaimTerrainClassifier.UNKNOWN;
                    if(block.isReplaceable(world, x, y, z) || block.isLeaves(world, x, y, z) || block.isFoliage(world, x, y, z)) { y--; continue; }
                    boxes.clear();
                    block.addCollisionBoxesToList(world, x, y, z,
                        AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 2, z + 1), boxes, null);
                    for(AxisAlignedBB box : boxes) {
                        if(box == null || !Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                            || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)
                            || box.maxX < box.minX || box.maxY < box.minY || box.maxZ < box.minZ)
                            return ClaimTerrainClassifier.UNKNOWN;
                        if(box.maxX > x && box.minX < x + 1 && box.maxZ > z && box.minZ < z + 1
                            && box.maxY > y && box.minY < y + 2 && box.maxY > box.minY)
                            return ClaimTerrainClassifier.ELIGIBLE;
                    }
                }
                y--;
            }
            return y < 0 ? ClaimTerrainClassifier.ELIGIBLE : ClaimTerrainClassifier.UNKNOWN;
        } catch(RuntimeException unsupported) {
            return ClaimTerrainClassifier.UNKNOWN;
        }
    }

    private boolean loadedHalo(int x, int z) {
        for(int cx = (x - 1) >> 4; cx <= (x + 1) >> 4; cx++)
            for(int cz = (z - 1) >> 4; cz <= (z + 1) >> 4; cz++)
                if(!world.getChunkProvider().chunkExists(cx, cz)) return false;
        return true;
    }

    private static boolean surfaceCandidate(Block block) {
        Material material = block.getMaterial();
        return material.blocksMovement()
            && material != Material.leaves && material != Material.plants && material != Material.vine
            && material != Material.cactus && material != Material.gourd
            && !(block instanceof BlockBush) && !(block instanceof IPlantable);
    }
}
