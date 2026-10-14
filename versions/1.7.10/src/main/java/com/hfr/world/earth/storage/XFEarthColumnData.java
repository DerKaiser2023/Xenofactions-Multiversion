package com.hfr.world.earth.storage;

import com.hfr.world.earth.XFEarthProfile;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/** Detached native sections; no world access and no allocation for absent block sections. */
public final class XFEarthColumnData {
    public final int chunkX, chunkZ;
    private final XFEarthProfile profile;
    private final ExtendedBlockStorage[] sections;
    private final NBTTagCompound[] cubes;
    private NBTTagCompound column;
    private boolean opacityChanged;
    public XFEarthProfile getProfile(){return profile;}

    public XFEarthColumnData(XFEarthProfile profile, int x, int z) {
        if(profile.getBounds()==null)throw new IllegalArgumentException("A validated Earth profile is required");
        this.profile = profile; chunkX = x; chunkZ = z;
        sections = new ExtendedBlockStorage[profile.getBlockHeight() >> 4];
        cubes = new NBTTagCompound[sections.length];
        column = new NBTTagCompound(); NBTTagCompound level = new NBTTagCompound(); column.setTag("Level", level);
        level.setByte("v", (byte)1); level.setInteger("x", x); level.setInteger("z", z);
        level.setLong("InhabitedTime", 0); level.setByteArray("Biomes", new byte[256]);
        opacityChanged = true;
    }

    private int index(int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < profile.getMinimumY() || y > profile.getMaximumY())
            throw new IndexOutOfBoundsException("Earth local block outside profile: " + x + "," + y + "," + z);
        return (y - profile.getMinimumY()) >> 4;
    }

    public Block getBlock(int x, int y, int z) {
        ExtendedBlockStorage section = sections[index(x,y,z)];
        return section == null ? Blocks.air : section.getBlockByExtId(x,y & 15,z);
    }
    public int getMetadata(int x, int y, int z) {
        ExtendedBlockStorage section = sections[index(x,y,z)];
        return section == null ? 0 : section.getExtBlockMetadata(x,y & 15,z);
    }
    public int getBlockLight(int x, int y, int z) {
        ExtendedBlockStorage section = sections[index(x,y,z)];
        return section == null ? 0 : section.getExtBlocklightValue(x,y & 15,z);
    }
    public int getSkyLight(int x, int y, int z) {
        ExtendedBlockStorage section = sections[index(x,y,z)];
        return section == null ? 0 : section.getExtSkylightValue(x,y & 15,z);
    }

    public void setBlock(int x, int y, int z, Block block, int metadata) {
        int i = index(x,y,z); nibble(metadata);
        if (block == null || Block.getIdFromBlock(block) < 0 || Block.getIdFromBlock(block) > 4095)
            throw new IllegalArgumentException("Native base cube codec requires registered 12-bit block IDs");
        if (block == Blocks.air && metadata == 0 && sections[i] == null) return;
        if (sections[i] == null) sections[i] = new ExtendedBlockStorage(profile.getMinimumY() + i * 16, true);
        sections[i].func_150818_a(x,y & 15,z,block); sections[i].setExtBlockMetadata(x,y & 15,z,metadata);
        opacityChanged = true;
    }

    public void setLight(int x, int y, int z, int blockLight, int skyLight) {
        int i = index(x,y,z); nibble(blockLight); nibble(skyLight);
        if (sections[i] == null && blockLight == 0 && skyLight == 0) return;
        if (sections[i] == null) sections[i] = new ExtendedBlockStorage(profile.getMinimumY() + i * 16, true);
        sections[i].setExtBlocklightValue(x,y & 15,z,blockLight); sections[i].setExtSkylightValue(x,y & 15,z,skyLight);
    }
    private static void nibble(int value) { if (value < 0 || value > 15) throw new IllegalArgumentException("Value outside 4-bit storage"); }

    public void setBiomes(byte[] biomes) {
        if (biomes.length != 256) throw new IllegalArgumentException("Expected 256 column biomes");
        column.getCompoundTag("Level").setByteArray("Biomes", biomes.clone());
    }

    /** Caller snapshots a real tile entity on its owning game thread before handing its NBT here. */
    public void addTileEntity(NBTTagCompound tile) {
        for (String key : new String[]{"x","y","z"}) if (!tile.hasKey(key,3)) throw new IllegalArgumentException("Tile entity needs integer " + key);
        int x = tile.getInteger("x"), y = tile.getInteger("y"), z = tile.getInteger("z");
        if ((x >> 4) != chunkX || (z >> 4) != chunkZ) throw new IllegalArgumentException("Tile entity belongs to another column");
        int i = index(x & 15,y,z & 15);
        NBTTagCompound cube = cube(i); NBTTagList tiles = cube.getCompoundTag("Level").getTagList("TileEntities",10);
        for (int n = 0; n < tiles.tagCount(); n++) {
            NBTTagCompound previous = tiles.getCompoundTagAt(n);
            if (previous.getInteger("x") == x && previous.getInteger("y") == y && previous.getInteger("z") == z)
                throw new IllegalArgumentException("Duplicate tile entity position");
        }
        tiles.appendTag(tile.copy()); cube.getCompoundTag("Level").setTag("TileEntities",tiles);
    }

    private NBTTagCompound cube(int i) {
        if (cubes[i] == null) {
            cubes[i] = new NBTTagCompound(); NBTTagCompound level = new NBTTagCompound(); cubes[i].setTag("Level",level);
            level.setByte("v",(byte)1); level.setInteger("x",chunkX); level.setInteger("y",(profile.getMinimumY() >> 4) + i); level.setInteger("z",chunkZ);
            level.setShort("population",(short)255); level.setBoolean("initLightDone",false);
            level.setString("LightingInfoType","phosphor_cc"); level.setTag("LightingInfo",new NBTTagCompound());
            level.setTag("Entities",new NBTTagList()); level.setTag("TileEntities",new NBTTagList()); level.setTag("TileTicks",new NBTTagList());
        }
        return cubes[i];
    }

    NBTTagCompound columnNbt() {
        NBTTagCompound result = (NBTTagCompound)column.copy();
        if (opacityChanged) result.getCompoundTag("Level").setByteArray("HeightMap3D", opacityIndex());
        return result;
    }

    NBTTagCompound cubeNbt(int cubeY) {
        int i = (cubeY * 16 - profile.getMinimumY()) >> 4;
        NBTTagCompound result = (NBTTagCompound)cube(i).copy(); NBTTagCompound level = result.getCompoundTag("Level");
        NBTTagList list = new NBTTagList(); ExtendedBlockStorage section = sections[i];
        if (section != null) {
            NBTTagList previous = level.getTagList("Sections",10);
            NBTTagCompound tag = previous.tagCount() == 1 ? (NBTTagCompound)previous.getCompoundTagAt(0).copy() : new NBTTagCompound(); list.appendTag(tag);
            tag.setByteArray("Blocks",section.getBlockLSBArray().clone());
            if (section.getBlockMSBArray() != null) tag.setByteArray("Add",section.getBlockMSBArray().data.clone());
            tag.setByteArray("Data",section.getMetadataArray().data.clone());
            tag.setByteArray("BlockLight",section.getBlocklightArray().data.clone());
            tag.setByteArray("SkyLight",section.getSkylightArray().data.clone());
        }
        level.setTag("Sections",list); return result;
    }

    /** Exact pinned HeightMap3D/YIntervalTree stream: Z-major columns, VarInt count and inclusive Y intervals. */
    private byte[] opacityIndex() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int[] ranges = new int[profile.getBlockHeight() * 2];
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int count = 0, start = -1;
            for (int y = profile.getMinimumY(); y <= profile.getMaximumY() + 1; y++) {
                boolean opaque = y <= profile.getMaximumY() && getBlock(x,y,z).getLightOpacity() > 0;
                if (opaque && start < 0) start = y;
                if (!opaque && start >= 0) { ranges[count++] = start; ranges[count++] = y - 1; start = -1; }
            }
            varInt(out,count / 2); for (int n = 0; n < count; n++) varInt(out,ranges[n]);
        }
        return out.toByteArray();
    }
    private static void varInt(ByteArrayOutputStream out, int value) {
        while ((value & ~127) != 0) { out.write((value & 127) | 128); value >>>= 7; } out.write(value);
    }

    void readColumn(NBTTagCompound tag) throws IOException {
        validatePosition(tag,chunkX,0,chunkZ,false);
        NBTTagCompound level = tag.getCompoundTag("Level");
        if (!level.hasKey("HeightMap3D",7) || level.getByteArray("Biomes").length != 256)
            throw new IOException("Missing native Earth heightmap/biomes");
        validateOpacityIndex(level.getByteArray("HeightMap3D"));
        column = (NBTTagCompound)tag.copy(); opacityChanged = false;
    }

    void readCube(int cubeY, NBTTagCompound tag) throws IOException {
        validatePosition(tag,chunkX,cubeY,chunkZ,true);
        int i = (cubeY * 16 - profile.getMinimumY()) >> 4;
        if (i < 0 || i >= sections.length) throw new IOException("Cube outside Earth Y bounds");
        NBTTagCompound level = tag.getCompoundTag("Level"); NBTTagList list = level.getTagList("Sections",10);
        if (level.hasKey("Sections") && !level.hasKey("Sections",9)) throw new IOException("Invalid native section list type");
        if (list.tagCount() > 1) throw new IOException("Native cube must contain at most one block section");
        if (!"phosphor_cc".equals(level.getString("LightingInfoType"))) throw new IOException("Unrecognized native lighting identity; refusing light loss");
        if (list.tagCount() == 1) {
            NBTTagCompound section = list.getCompoundTagAt(0);
            array(section,"Blocks",4096); array(section,"Data",2048); array(section,"BlockLight",2048); array(section,"SkyLight",2048);
            ExtendedBlockStorage storage = new ExtendedBlockStorage(cubeY * 16,true);
            storage.setBlockLSBArray(section.getByteArray("Blocks").clone());
            if (section.hasKey("Add")) { array(section,"Add",2048); storage.setBlockMSBArray(new NibbleArray(section.getByteArray("Add").clone(),4)); }
            storage.setBlockMetadataArray(new NibbleArray(section.getByteArray("Data").clone(),4));
            storage.setBlocklightArray(new NibbleArray(section.getByteArray("BlockLight").clone(),4));
            storage.setSkylightArray(new NibbleArray(section.getByteArray("SkyLight").clone(),4));
            // Reject an unknown ID rather than accepting the registry's default-air lookup.
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                Block block = storage.getBlockByExtId(x,y,z);
                int cell = y << 8 | z << 4 | x;
                int id = (storage.getBlockLSBArray()[cell] & 255) | (storage.getBlockMSBArray() == null ? 0 : storage.getBlockMSBArray().get(x,y,z) << 8);
                if (block == null || Block.getIdFromBlock(block) != id) throw new IOException("Unregistered block in native cube");
            }
            storage.removeInvalidBlocks(); sections[i] = storage;
        }
        NBTTagList tiles = level.getTagList("TileEntities",10);
        if (level.hasKey("TileEntities") && !level.hasKey("TileEntities",9)) throw new IOException("Invalid native tile entity list type");
        for (int n = 0; n < tiles.tagCount(); n++) {
            NBTTagCompound tile = tiles.getCompoundTagAt(n);
            for (String key : new String[]{"x","y","z"}) if (!tile.hasKey(key,3)) throw new IOException("Invalid tile entity coordinates");
            if ((tile.getInteger("x") >> 4) != chunkX || (tile.getInteger("y") >> 4) != cubeY || (tile.getInteger("z") >> 4) != chunkZ)
                throw new IOException("Tile entity does not belong to its native cube");
        }
        cubes[i] = (NBTTagCompound)tag.copy();
    }
    private static void array(NBTTagCompound tag, String key, int size) throws IOException {
        if (!tag.hasKey(key,7) || tag.getByteArray(key).length != size) throw new IOException("Invalid native section " + key);
    }
    private void validateOpacityIndex(byte[] bytes) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        for (int cell = 0; cell < 256; cell++) {
            int count = readVarInt(in), last = profile.getMinimumY() - 2;
            if (count < 0 || count > profile.getBlockHeight() / 2) throw new IOException("Invalid native opacity interval count");
            for (int n = 0; n < count; n++) {
                int low = readVarInt(in), high = readVarInt(in);
                if (low < profile.getMinimumY() || high > profile.getMaximumY() || low > high || low <= last + 1)
                    throw new IOException("Native opacity intervals outside bounds or not ordered/disjoint");
                last = high;
            }
        }
        if (in.available() != 0) throw new IOException("Trailing native opacity data");
    }
    private static int readVarInt(ByteArrayInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.read(); if (b < 0) throw new IOException("Truncated native opacity data");
            if (shift == 28 && (b & 240) != 0) throw new IOException("Invalid native opacity VarInt");
            value |= (b & 127) << shift; if ((b & 128) == 0) return value;
        }
        throw new IOException("Oversize native opacity VarInt");
    }
    private static void validatePosition(NBTTagCompound tag, int x, int y, int z, boolean cube) throws IOException {
        NBTTagCompound level = tag.getCompoundTag("Level");
        if (!level.hasKey("v",1) || level.getByte("v") != 1 || !level.hasKey("x",3) || !level.hasKey("z",3)
                || level.getInteger("x") != x || level.getInteger("z") != z
                || (cube && (!level.hasKey("y",3) || level.getInteger("y") != y))) throw new IOException("Wrong native storage version or position");
    }

    /** Owning game thread only; upper sections can never enter the vanilla Chunk representation. */
    public Chunk createAnvilChunk(World world) {
        profile.requireGameplayHeight();
        if (sections.length != 16) throw new IllegalStateException("Anvil cannot hold extended Earth sections");
        Chunk chunk = new Chunk(world,chunkX,chunkZ); chunk.setStorageArrays(sections.clone());
        chunk.setBiomeArray(column.getCompoundTag("Level").getByteArray("Biomes").clone());
        chunk.generateSkylightMap(); chunk.isTerrainPopulated = true; chunk.isLightPopulated = true; chunk.isModified = true; return chunk;
    }
}
