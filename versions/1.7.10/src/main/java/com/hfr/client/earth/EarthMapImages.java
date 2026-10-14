package com.hfr.client.earth;

import com.google.gson.Gson;
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

    public ResourceLocation tile(int level, int x, int z) { return path(id, level + "/" + x + "-" + z + ".png"); }
    private static ResourceLocation path(String id, String file) { return new ResourceLocation("hfr", "earthmaps/images/" + id + "/" + file); }
}
