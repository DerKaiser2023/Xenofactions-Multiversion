package com.hfr.client.earth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.hfr.world.earth.map.XFEarthMapData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/** Reusable image pyramid metadata, independent of the item GUI and JourneyMap. */
@SideOnly(Side.CLIENT)
public final class EarthMapImages {
    public int formatVersion, sourceWidth, sourceHeight, tileSize;
    public String id, sourceFile, sourceSha256, orientation;
    public Level[] levels;
    private transient String directory = "earthmaps/images/";
    public transient String credit = "";
    public static final class Level { public int width, height; }

    public static EarthMapImages load(XFEarthMapData data) throws IOException {
        if (data.imageId.isEmpty()) throw new IOException("No prepared imagery for this profile");
        Reader reader = new InputStreamReader(Minecraft.getMinecraft().getResourceManager()
                .getResource(path(data.imageId, "map-image.json")).getInputStream(), "UTF-8");
        EarthMapImages images;
        try { images = new Gson().fromJson(reader, EarthMapImages.class); } finally { reader.close(); }
        if (images == null || images.formatVersion != 1 || !data.imageId.equals(images.id)
                || !"x-east-z-south".equals(images.orientation) || images.tileSize != 256
                || images.sourceSha256 == null || !images.sourceSha256.matches("[a-f0-9]{64}")
                || images.sourceWidth <= 0 || images.sourceHeight <= 0 || images.levels == null
                || images.levels.length < 1 || images.levels.length > 3)
            throw new IOException("Invalid Earth image manifest");
        int previous = 0;
        for (Level level : images.levels) {
            if (level == null || level.width <= 0 || level.height <= 0 || level.width > 2048 || level.height > 2048
                    || level.width < previous || Math.abs((double)level.width / level.height
                    - (double)data.profile.getWidth() / data.profile.getHeight()) > 0.01)
                throw new IOException("Earth image aspect ratio/level disagrees with the profile");
            previous = level.width;
        }
        // Explicit custom images represent the final profile grid; stock images use the original resize.
        double resize = data.profile.getMapImageId() != null ? 100 : data.profile.getResize();
        if (images.sourceWidth * resize / 100 != data.profile.getWidth()
                || images.sourceHeight * resize / 100 != data.profile.getHeight())
            throw new IOException("Earth image dimensions disagree with the profile");
        return images;
    }

    /** Offline GRIP display package, independent of server OSM selection. No world/block access. */
    public static EarthMapImages loadRoads(XFEarthMapData data) throws IOException {
        String name = data.profile.getProfile();
        if (!name.matches("[a-z0-9][a-z0-9_-]{0,63}")) throw new IOException("Unsupported road profile name");
        ResourceLocation location = new ResourceLocation("hfr", "earthmaps/roads/" + name + "/roads.json");
        JsonObject manifest;
        try (java.io.InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream()) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) {
                if (bytes.size() + count > 65536) throw new IOException("Road manifest exceeds 64 KiB");
                bytes.write(buffer, 0, count);
            }
            manifest = new Gson().fromJson(new String(bytes.toByteArray(), "UTF-8"), JsonObject.class);
        }
        try {
            JsonObject actual = new Gson().toJsonTree(data.profile).getAsJsonObject();
            JsonObject stored = manifest.getAsJsonObject("profile");
            String fields = "formatVersion profile targetMinecraftVersion sourceScale resize effectiveScale width height minimumX maximumX minimumZ maximumZ minimumSurfaceY maximumSurfaceY seaLevel projection populationMode caves ores lava structures vegetation minimumY maximumY heightImplementation heightImplementationVersion verticalElevationScale";
            for (String field : fields.split(" ")) {
                JsonElement value = actual.get(field);
                if (value == null || !value.equals(stored.get(field))) throw new IOException("Road profile mismatch: " + field);
            }
            EarthMapImages images = new Gson().fromJson(manifest, EarthMapImages.class);
            if (images.formatVersion != 1 || manifest.get("processingVersion").getAsInt() != 1
                    || !"GRIP4".equals(manifest.get("source").getAsString())
                    || !"x-east-z-south".equals(images.orientation) || images.tileSize != 256
                    || !"constant-symbolic".equals(manifest.get("widthPolicy").getAsString())
                    || manifest.get("pixelsPerColumn").getAsInt() != 1
                    || manifest.get("width").getAsInt() != data.profile.getWidth()
                    || manifest.get("height").getAsInt() != data.profile.getHeight()
                    || images.sourceWidth != data.profile.getWidth() || images.sourceHeight != data.profile.getHeight()
                    || !("grip-" + name).equals(images.id) || images.levels == null || images.levels.length < 1 || images.levels.length > 2)
                throw new IOException("Invalid GRIP road manifest");
            images.sourceSha256 = manifest.get("maskSha256").getAsString();
            if (!images.sourceSha256.matches("[a-f0-9]{64}")) throw new IOException("Invalid road mask identity");
            for (Level level : images.levels) if (level == null || level.width <= 0 || level.height <= 0
                    || level.width > 1024 || level.height > 1024 || Math.abs((double)level.width / level.height
                    - (double)images.sourceWidth / images.sourceHeight) > .01) throw new IOException("Invalid road display dimensions");
            images.credit = manifest.get("attribution").getAsString();
            if (images.credit.length() > 2048) throw new IOException("Road attribution too long");
            images.directory = "earthmaps/roads/" + name + "/";
            return images;
        } catch (RuntimeException failure) { throw new IOException("Malformed GRIP road manifest", failure); }
    }

    public ResourceLocation tile(int level, int x, int z) {
        return new ResourceLocation("hfr", directory + (directory.startsWith("earthmaps/images/") ? id + "/" : "")
                + level + "/" + x + "-" + z + ".png");
    }
    private static ResourceLocation path(String id, String file) { return new ResourceLocation("hfr", "earthmaps/images/" + id + "/" + file); }
}
