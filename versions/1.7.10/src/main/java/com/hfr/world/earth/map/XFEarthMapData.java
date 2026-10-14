package com.hfr.world.earth.map;

import com.google.gson.Gson;
import com.hfr.world.earth.XFEarthProfile;

/** Shared map description and projection for the item and JourneyMap preload. No client types. */
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
    public double mapX(double x) { return profile.mapX(x); }
    public double mapZ(double z) { return profile.mapZ(z); }
    public boolean contains(double x, double z) {
        return profile.containsMapPosition(x,z);
    }
    public boolean hasGeographicConversion() {
        return profile.hasGeographicConversion();
    }
    // The source calculator uses 120000 / effectiveScale blocks per degree.
    // Cropped rasters cover 358.4 x 179.2 degrees, not 360 x 180.
    public double longitude(double x) {
        return profile.longitude(x);
    }
    public double latitude(double z) {
        return profile.latitude(z);
    }
}
