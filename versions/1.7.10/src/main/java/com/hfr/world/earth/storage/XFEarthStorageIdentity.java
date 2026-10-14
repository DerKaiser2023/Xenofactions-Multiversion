package com.hfr.world.earth.storage;

import com.google.gson.Gson;
import com.hfr.world.earth.XFEarthProfile;
import com.hfr.world.earth.XFEarthProfileLoader;
import cpw.mods.fml.common.Loader;
import java.io.*;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.SaveFormatOld;

/** World metadata, not another chunk format. Extended gameplay stays closed in Stage 2. */
public final class XFEarthStorageIdentity extends WorldSavedData {
    public static final String DATA_NAME = "xenoearth_storage";
    public static final String CUBIC = "cubicchunks1710";
    public static final String CUBIC_VERSION = "v0.1.23-alpha-pre";
    public static final String ANVIL = "vanilla-anvil";
    private NBTTagCompound identity;

    public XFEarthStorageIdentity(String name) { super(name); }

    public static NBTTagCompound describe(XFEarthProfile profile) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("formatVersion", 1);
        tag.setInteger("dimension", 0);
        tag.setString("implementation", profile.getHeightImplementation());
        tag.setString("implementationVersion", profile.getHeightImplementationVersion());
        tag.setInteger("minimumY", profile.getMinimumY());
        tag.setInteger("maximumY", profile.getMaximumY());
        return tag;
    }

    public static File path(File root) { return new File(root, "data/" + DATA_NAME + ".dat"); }

    public static NBTTagCompound read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return CompressedStreamTools.readCompressed(in);
        }
    }

    public static void writeNew(File file, NBTTagCompound tag) throws IOException {
        try (OutputStream out = java.nio.file.Files.newOutputStream(file.toPath(),
                java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)) {
            CompressedStreamTools.writeCompressed(tag, out);
        }
    }

    public static NBTTagCompound savedData(NBTTagCompound data) {
        NBTTagCompound root = new NBTTagCompound(); root.setTag("data", data); return root;
    }

    public static void validate(NBTTagCompound tag, XFEarthProfile profile) throws IOException {
        for (String field : new String[]{"formatVersion", "dimension", "minimumY", "maximumY"})
            if (!tag.hasKey(field, 3)) throw new IOException("Missing/incompatible Earth storage identity field: " + field);
        if (!tag.hasKey("implementation", 8) || !tag.hasKey("implementationVersion", 8)
                || tag.getInteger("formatVersion") != 1 || tag.getInteger("dimension") != 0
                || !profile.getHeightImplementation().equals(tag.getString("implementation"))
                || !profile.getHeightImplementationVersion().equals(tag.getString("implementationVersion"))
                || profile.getMinimumY() != tag.getInteger("minimumY") || profile.getMaximumY() != tag.getInteger("maximumY"))
            throw new IOException("Earth storage identity/version/range disagrees with the profile; conversion is not supported");
    }

    public static XFEarthProfile readProfile(File root) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(XFEarthProfileLoader.path(root)), "UTF-8")) {
            XFEarthProfile profile = new Gson().fromJson(reader, XFEarthProfile.class);
            if (profile == null) throw new IOException("Missing Earth profile");
            profile.validate("1.7.10"); return profile;
        } catch (IllegalArgumentException failure) { throw new IOException("Invalid Earth storage profile", failure); }
    }

    /** Read-only, before SaveHandler construction, session.lock, conversion, or world initialization. */
    public static void preflight(MinecraftServer server) throws IOException {
        if (!(server.getActiveAnvilConverter() instanceof SaveFormatOld))
            throw new IOException("Cannot locate the save root for Earth storage preflight");
        File root = new File(((SaveFormatOld)server.getActiveAnvilConverter()).savesDirectory, server.getFolderName());
        preflightRoot(root);
    }

    /** Called at Minecraft.launchIntegratedServer HEAD, before the client creates its own SaveHandler. */
    public static void preflightClient(File gameDirectory, String folderName) throws IOException {
        preflightRoot(new File(new File(gameDirectory,"saves"),folderName));
    }

    private static void preflightRoot(File root) throws IOException {
        boolean identityPresent = path(root).exists(), profilePresent = XFEarthProfileLoader.exists(root);
        boolean cubeFiles = new File(root,"region2d").exists() || new File(root,"region3d").exists() || new File(root,"data/cubicChunksData.dat").exists();
        if (!identityPresent && !profilePresent && !cubeFiles) return;
        File level = new File(root,"level.dat");
        boolean adopted = level.isFile() && "earthmap".equals(read(level).getCompoundTag("Data").getString("generatorName"));
        boolean earth = identityPresent || adopted || (profilePresent && cubeFiles);
        if (!earth && profilePresent) {
            // Preserve the previous warning-only behavior for ordinary, non-adopted profile sidecars.
            try { earth = readProfile(root).isExtendedHeight(); } catch (IOException invalidSidecar) { return; }
        }
        if (!earth) return;
        XFEarthProfile profile = readProfile(root);
        validateLayout(root, profile, false);
        profile.requireGameplayHeight();
        // This exact CC release unconditionally initializes cube providers, even at nominal height 256.
        if (Loader.isModLoaded("cubicchunks"))
            throw new IOException("CubicChunks1710 cannot preserve the ordinary Earth Anvil path; gameplay integration is disabled until Stages 3 and 4");
    }

    public static void validateLayout(File root, XFEarthProfile profile, boolean requireIdentity) throws IOException {
        File identityFile = path(root);
        if (identityFile.exists()) validate(read(identityFile).getCompoundTag("data"), profile);
        else if (requireIdentity || profile.isExtendedHeight()) throw new IOException("Earth storage identity is required before opening extended storage");
        boolean cubeFiles = new File(root, "region2d").exists() || new File(root, "region3d").exists()
                || new File(root, "data/cubicChunksData.dat").exists();
        File level = new File(root,"level.dat");
        if (level.isFile() && read(level).getCompoundTag("Data").getBoolean("isCubicWorld")) cubeFiles = true;
        if (!profile.isExtendedHeight()) {
            if (cubeFiles) throw new IOException("Cubic storage cannot be opened as ordinary Anvil; no silent conversion");
        } else {
            if (new File(root, "region").exists()) throw new IOException("Mixed Anvil/cube Earth storage is not supported");
            if (!new File(root, "region2d").isDirectory() || !new File(root, "region3d").isDirectory())
                throw new IOException("Extended Earth storage requires native region2d and region3d directories");
            NBTTagCompound heights = read(new File(root, "data/cubicChunksData.dat")).getCompoundTag("data");
            if (!heights.hasKey("minHeight", 3) || !heights.hasKey("maxHeight", 3)
                    || heights.getInteger("minHeight") != profile.getMinimumY()
                    || heights.getInteger("maxHeight") != profile.getMaximumY() + 1)
                throw new IOException("Native cubic height metadata disagrees with Earth storage identity");
        }
    }

    public static void writePreparedIdentity(File root, XFEarthProfile profile) throws IOException {
        File data = new File(root,"data");
        if (!data.isDirectory() && !data.mkdir()) throw new IOException("Cannot create Earth metadata directory");
        File file = path(root);
        if (file.exists()) validate(read(file).getCompoundTag("data"),profile);
        else writeNew(file,savedData(describe(profile)));
    }

    /** Server thread, dimension 0 only. Legacy Anvil worlds acquire identity without changing chunks. */
    public static void attach(World world, XFEarthProfile profile) throws IOException {
        if (world.isRemote || world.provider.dimensionId != 0) throw new IOException("Earth storage identity belongs to the server overworld");
        validateLayout(world.getSaveHandler().getWorldDirectory(), profile, false);
        profile.requireGameplayHeight();
        XFEarthStorageIdentity data = new XFEarthStorageIdentity(DATA_NAME);
        data.identity = describe(profile);
        world.perWorldStorage.setData(DATA_NAME, data); data.markDirty();
    }

    @Override public void readFromNBT(NBTTagCompound tag) { identity = (NBTTagCompound)tag.copy(); }
    @Override public void writeToNBT(NBTTagCompound tag) {
        if (identity == null) throw new IllegalStateException("Uninitialized Earth storage identity");
        for (String key : identity.func_150296_c()) tag.setTag(key, identity.getTag(key).copy());
    }
}
