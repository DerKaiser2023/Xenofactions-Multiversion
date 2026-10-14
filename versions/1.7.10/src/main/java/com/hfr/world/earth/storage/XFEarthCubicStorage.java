package com.hfr.world.earth.storage;

import com.google.gson.GsonBuilder;
import com.hfr.world.earth.XFEarthProfile;
import com.hfr.world.earth.XFEarthProfileLoader;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import java.io.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Optional;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/** Storage preparation uses CC's native non-shadow-paging RegionLib path, never Anvil or a live World. */
public final class XFEarthCubicStorage implements Closeable {
    private final File root;
    private final XFEarthProfile profile;
    private final Object storage;
    private final Driver driver;
    private final boolean writable;
    private boolean closed;

    private XFEarthCubicStorage(File root, XFEarthProfile profile, Driver driver, boolean writable) throws IOException {
        this.root = root; this.profile = profile; this.driver = driver; this.writable = writable;
        storage = driver.call(driver.create,null,root.toPath());
    }

    /** Only a new, absent destination is accepted. Errors retain that destination for inspection. */
    public static XFEarthCubicStorage createNew(File root, XFEarthProfile profile, NBTTagCompound levelSnapshot) throws IOException {
        profile.validate("1.7.10"); requireCubic(profile);
        if (!levelSnapshot.hasKey("Data",10)) throw new IOException("A complete detached level.dat snapshot is required");
        Driver driver = new Driver(); // Resolve optional API/version before creating any output.
        Files.createDirectory(root.toPath());
        XFEarthCubicStorage result = null;
        try {
            // Record the guard before a driver can create cube files, including failed/partial preparation.
            Files.createDirectory(new File(root,"data").toPath());
            XFEarthStorageIdentity.writeNew(XFEarthStorageIdentity.path(root),XFEarthStorageIdentity.savedData(XFEarthStorageIdentity.describe(profile)));
            NBTTagCompound nativeHeights = new NBTTagCompound(); nativeHeights.setInteger("minHeight",profile.getMinimumY());
            nativeHeights.setInteger("maxHeight",profile.getMaximumY() + 1); nativeHeights.setTag("fillerBlocks",new NBTTagCompound());
            XFEarthStorageIdentity.writeNew(new File(root,"data/cubicChunksData.dat"),XFEarthStorageIdentity.savedData(nativeHeights));
            try (Writer writer = new OutputStreamWriter(Files.newOutputStream(XFEarthProfileLoader.path(root).toPath(),
                    java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE),"UTF-8")) {
                new GsonBuilder().setPrettyPrinting().create().toJson(profile,writer);
            }
            XFEarthStorageIdentity.writeNew(new File(root,"level.dat"),(NBTTagCompound)levelSnapshot.copy());
            result = new XFEarthCubicStorage(root,profile,driver,true);
            XFEarthStorageIdentity.validateLayout(root,profile,true); return result;
        } catch (IOException | RuntimeException failure) {
            if (result != null) try { result.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    /** Reopen for inspection only. Existing columns/cubes are never overwritten by the preparation API. */
    public static XFEarthCubicStorage openExisting(File root) throws IOException {
        XFEarthProfile profile = XFEarthStorageIdentity.readProfile(root); requireCubic(profile);
        XFEarthStorageIdentity.validateLayout(root,profile,true);
        return new XFEarthCubicStorage(root,profile,new Driver(),false);
    }
    private static void requireCubic(XFEarthProfile profile) throws IOException {
        if (!profile.isExtendedHeight()) throw new IOException("Ordinary worlds must use ordinary Anvil storage");
    }
    public XFEarthProfile getProfile() { return profile; }

    private void check(int x, int z) throws IOException {
        if (closed) throw new IOException("Earth cube storage is closed");
        if (!profile.getBounds().containsChunk(x,z)) throw new IOException("Column outside Earth profile bounds");
    }
    private NBTTagCompound read(boolean cube, int x, int y, int z) throws IOException {
        Object pos = driver.position(cube,x,y,z);
        Optional<?> bytes = (Optional<?>)driver.call(cube ? driver.load3d : driver.load2d,storage,pos,false);
        if (!bytes.isPresent()) return null;
        ByteBuffer buffer = ((ByteBuffer)bytes.get()).duplicate(); byte[] data = new byte[buffer.remaining()]; buffer.get(data);
        try (InputStream in = new ByteArrayInputStream(data)) { return CompressedStreamTools.readCompressed(in); }
    }

    public synchronized XFEarthColumnData readColumn(int x, int z) throws IOException {
        check(x,z); XFEarthStorageIdentity.validateLayout(root,profile,true);
        NBTTagCompound column = read(false,x,0,z); if (column == null) return null;
        XFEarthColumnData result = new XFEarthColumnData(profile,x,z); result.readColumn(column);
        for (int y = profile.getMinimumY() >> 4; y <= profile.getMaximumY() >> 4; y++) {
            NBTTagCompound cube = read(true,x,y,z);
            if (cube == null) throw new IOException("Incomplete Earth native column: missing cube " + x + "," + y + "," + z);
            result.readCube(y,cube);
        }
        return result;
    }

    public synchronized void writeNewColumn(XFEarthColumnData column) throws IOException {
        int x = column.chunkX, z = column.chunkZ; check(x,z);
        if (!writable) throw new IOException("Existing Earth cube storage is open for inspection only");
        XFEarthStorageIdentity.validateLayout(root,profile,true);
        XFEarthStorageIdentity.validate(XFEarthStorageIdentity.describe(column.getProfile()),profile);
        if (read(false,x,0,z) != null) throw new IOException("Refusing to overwrite an existing Earth column");
        for (int y = profile.getMinimumY() >> 4; y <= profile.getMaximumY() >> 4; y++)
            if (read(true,x,y,z) != null) throw new IOException("Refusing to overwrite an existing/partial Earth cube");
        // Detached NBT only. Native RegionLib does not promise multi-entry atomicity.
        for (int y = profile.getMinimumY() >> 4; y <= profile.getMaximumY() >> 4; y++) write(true,x,y,z,column.cubeNbt(y));
        write(false,x,0,z,column.columnNbt()); driver.call(driver.flush,storage);
    }
    private void write(boolean cube, int x, int y, int z, NBTTagCompound tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(tag,bytes);
        driver.call(cube ? driver.save3d : driver.save2d,storage,driver.position(cube,x,y,z),ByteBuffer.wrap(bytes.toByteArray()));
    }
    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true; driver.call(driver.close,storage);
    }

    /** No optional classes in linkage, no dependency/config changes, no CubicChunks coremod activation. */
    private static final class Driver {
        final Constructor<?> pos2d, pos3d;
        final Method create, load2d, load3d, save2d, save3d, flush, close;
        Driver() throws IOException {
            ModContainer mod = Loader.instance().getIndexedModList().get("RegionLib");
            if (mod == null || !("v0.1.0-GTNH".equals(mod.getVersion()) || "0.1.0-GTNH".equals(mod.getVersion())))
                throw new IOException("Extended Earth preparation requires optional RegionLib v0.1.0-GTNH");
            if (Loader.isModLoaded("chunkapi") || Loader.isModLoaded("endlessids"))
                throw new IOException("Extended Earth base-section codec has no verified ChunkAPI/EndlessIDs storage adapter");
            try {
                ClassLoader loader = XFEarthCubicStorage.class.getClassLoader();
                Class<?> save = Class.forName("cubicchunks.regionlib.impl.SaveCubeColumns",true,loader);
                Class<?> p2 = Class.forName("cubicchunks.regionlib.impl.EntryLocation2D",true,loader);
                Class<?> p3 = Class.forName("cubicchunks.regionlib.impl.EntryLocation3D",true,loader);
                pos2d = p2.getConstructor(int.class,int.class); pos3d = p3.getConstructor(int.class,int.class,int.class);
                create = save.getMethod("create",java.nio.file.Path.class);
                load2d = save.getMethod("load",p2,boolean.class); load3d = save.getMethod("load",p3,boolean.class);
                save2d = save.getMethod("save2d",p2,ByteBuffer.class); save3d = save.getMethod("save3d",p3,ByteBuffer.class);
                flush = save.getMethod("flush"); close = save.getMethod("close");
            } catch (ReflectiveOperationException | LinkageError failure) { throw new IOException("Pinned RegionLib storage API unavailable",failure); }
        }
        Object position(boolean cube, int x, int y, int z) throws IOException {
            try { return cube ? pos3d.newInstance(x,y,z) : pos2d.newInstance(x,z); }
            catch (ReflectiveOperationException failure) { throw new IOException("Cannot encode native cube position",failure); }
        }
        Object call(Method method, Object target, Object... args) throws IOException {
            try { return method.invoke(target,args); }
            catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause(); if (cause instanceof IOException) throw (IOException)cause;
                if (cause instanceof Error) throw (Error)cause;
                throw new IOException("Native RegionLib storage operation failed: " + method.getName(),cause);
            } catch (ReflectiveOperationException failure) { throw new IOException("Native RegionLib API invocation failed",failure); }
        }
    }
}
