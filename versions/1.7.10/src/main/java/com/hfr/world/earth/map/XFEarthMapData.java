package com.hfr.world.earth.map;

import com.google.gson.Gson;
import com.hfr.world.earth.XFEarthBounds;
import com.hfr.world.earth.XFEarthProfile;

/** Shared map description and projection for the item and future map integrations. No client types. */
public final class XFEarthMapData {
    public final XFEarthProfile profile;
    public final String identity;
    public final String imageId;

    public XFEarthMapData(XFEarthProfile profile, String identity) {
        this.profile = profile;
        this.identity = identity;
        String custom = profile.getMapImageId();
        int sourceScale = profile.getSourceScale();
        imageId = custom != null ? custom : ("equirectangular".equals(profile.getProjection())
                && (sourceScale == 10 || sourceScale == 20 || sourceScale == 40)
                ? "raster-" + sourceScale + "k" : "");
        if (!imageId.isEmpty() && !imageId.matches("[a-z0-9][a-z0-9_-]{0,63}"))
            throw new IllegalArgumentException("Invalid Earth map image ID");
    }

    public static XFEarthMapData decode(String json, String identity) {
        XFEarthProfile profile = new Gson().fromJson(json, XFEarthProfile.class);
        if (profile == null) throw new IllegalArgumentException("Missing Earth profile");
        profile.validate("1.7.10");
        return new XFEarthMapData(profile, identity);
    }

    // Generator: raster pixel (0,0) becomes (minimumX,minimumZ). No rotation or flip.
    public double mapX(double x) { return (x - profile.getBounds().minimumX) / profile.getWidth(); }
    public double mapZ(double z) { return (z - profile.getBounds().minimumZ) / profile.getHeight(); }
    public boolean contains(double x, double z) {
        XFEarthBounds b = profile.getBounds();
        // Inclusive block IDs cover the continuous extent through max+1.
        return finite(x) && finite(z) && x >= b.minimumX && x < (double)b.maximumX + 1
                && z >= b.minimumZ && z < (double)b.maximumZ + 1;
    }
    public boolean hasGeographicConversion() {
        return "equirectangular".equals(profile.getProjection()) && profile.getEffectiveScale() > 0;
    }
    // The source calculator uses 120000 / effectiveScale blocks per degree.
    // Cropped rasters cover 358.4 x 179.2 degrees, not 360 x 180.
    public double longitude(double x) {
        return (x - profile.getBounds().minimumX - profile.getWidth() / 2.0)
                * profile.getEffectiveScale() / 120000.0;
    }
    public double latitude(double z) {
        return -(z - profile.getBounds().minimumZ - profile.getHeight() / 2.0)
                * profile.getEffectiveScale() / 120000.0;
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
