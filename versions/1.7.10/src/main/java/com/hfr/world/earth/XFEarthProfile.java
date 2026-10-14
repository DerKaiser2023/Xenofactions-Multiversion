package com.hfr.world.earth;

/** Gson DTO made immutable to callers after validation by the loader. */
public final class XFEarthProfile {
    public static final String EXTENDED_GAMEPLAY_BLOCKER = "CubicChunks1710 v0.1.23-alpha-pre replaces providers/storage and spawning globally; Earth-only Anvil/cubic isolation and the native Earth cube generator are unsupported";
    int formatVersion; String profile; String targetMinecraftVersion;
    int sourceScale, resize, effectiveScale, width, height;
    int minimumX, maximumX, minimumZ, maximumZ;
    int minimumSurfaceY, maximumSurfaceY, seaLevel;
    // Optional format-1 additions. Absent fields preserve existing Anvil worlds.
    int minimumY = 0, maximumY = 255;
    double verticalElevationScale = 1.0;
    String heightImplementation = "vanilla-anvil";
    String heightImplementationVersion = "1.7.10";
    String projection, populationMode; boolean caves, ores, lava, structures, vegetation;
    String mapImageId;
    private transient XFEarthBounds bounds;
    public void validate(String allowedVersion) {
        if(formatVersion!=1) bad("unsupported formatVersion: "+formatVersion);
        if(!allowedVersion.equals(targetMinecraftVersion)) bad("targetMinecraftVersion must be "+allowedVersion);
        if(width<=0||height<=0||width%16!=0||height%16!=0) bad("width and height must be positive multiples of 16");
        if((long)maximumX-minimumX+1!=width || (long)maximumZ-minimumZ+1!=height) bad("coordinate bounds do not match width/height");
        if("vanilla-anvil".equals(heightImplementation)) {
            if(minimumY!=0||maximumY!=255||!"1.7.10".equals(heightImplementationVersion)) bad("vanilla-anvil requires version 1.7.10 and Y=0..255");
        } else if(com.hfr.world.earth.storage.XFEarthStorageIdentity.CUBIC.equals(heightImplementation)) {
            if(minimumY!=0||maximumY!=511||!com.hfr.world.earth.storage.XFEarthStorageIdentity.CUBIC_VERSION.equals(heightImplementationVersion))
                bad("cubicchunks1710 storage requires v0.1.23-alpha-pre and Y=0..511");
        } else bad("unsupported height implementation: "+heightImplementation);
        if(!finite(verticalElevationScale)||verticalElevationScale<=0) bad("verticalElevationScale must be finite and positive");
        if(minimumSurfaceY<=minimumY||maximumSurfaceY>=maximumY||minimumSurfaceY>maximumSurfaceY) bad("surface range must lie inside block Y bounds");
        if(seaLevel<minimumSurfaceY||seaLevel>maximumSurfaceY) bad("seaLevel must lie inside surface range");
        if(!"pregenerated".equals(populationMode)) bad("populationMode must be pregenerated");
        if(profile==null||profile.trim().isEmpty()) bad("profile name is required");
        bounds=new XFEarthBounds(minimumX,maximumX,minimumZ,maximumZ);
    }
    public void validateForPack(com.hfr.world.earth.pack.XFEarthMapManifest m){validate("1.7.10");requireGameplayHeight();if(width!=m.width||height!=m.height||effectiveScale!=m.effectiveScale)bad("profile dimensions/scale disagree with manifest");}
    private static void bad(String message){throw new IllegalArgumentException("Invalid XenoEarth profile: "+message);}
    public String getProfile(){return profile;} public int getEffectiveScale(){return effectiveScale;} public int getWidth(){return width;} public int getHeight(){return height;}
    public int getSeaLevel(){return seaLevel;} public XFEarthBounds getBounds(){return bounds;}
    public String getProjection(){return projection;} public int getSourceScale(){return sourceScale;} public int getResize(){return resize;}
    public String getMapImageId(){return mapImageId;}
    public int getMinimumY(){return minimumY;} public int getMaximumY(){return maximumY;}
    public int getBlockHeight(){return maximumY-minimumY+1;}
    public int getMinimumSurfaceY(){return minimumSurfaceY;} public int getMaximumSurfaceY(){return maximumSurfaceY;}
    public double getVerticalElevationScale(){return verticalElevationScale;}
    public String getHeightImplementation(){return heightImplementation;}
    public String getHeightImplementationVersion(){return heightImplementationVersion;}
    public boolean isExtendedHeight(){return !"vanilla-anvil".equals(heightImplementation);}
    public boolean containsBlockY(int y){return y>=minimumY&&y<=maximumY;}
    public void requireGameplayHeight(){if(isExtendedHeight())bad("extended Earth gameplay remains disabled: "+EXTENDED_GAMEPLAY_BLOCKER);}

    /** Raster origin is the northwest block, with columns east and rows south. */
    public double mapX(double x){return (x-bounds.minimumX)/width;}
    public double mapZ(double z){return (z-bounds.minimumZ)/height;}
    public boolean containsMapPosition(double x,double z){
        return finite(x)&&finite(z)&&x>=bounds.minimumX&&x<(double)bounds.maximumX+1
                &&z>=bounds.minimumZ&&z<(double)bounds.maximumZ+1;
    }
    public boolean hasGeographicConversion(){return "equirectangular".equals(projection)&&effectiveScale>0;}
    // The source calculator uses 120000/effectiveScale blocks per degree.
    // Standard cropped rasters span 358.4 x 179.2 degrees, not 360 x 180.
    public double longitude(double x){requireGeographic(x);return (x-bounds.minimumX-width/2.0)*effectiveScale/120000.0;}
    public double latitude(double z){requireGeographic(z);return -(z-bounds.minimumZ-height/2.0)*effectiveScale/120000.0;}
    public double blockX(double longitude){requireGeographic(longitude);return bounds.minimumX+width/2.0+longitude*120000.0/effectiveScale;}
    public double blockZ(double latitude){requireGeographic(latitude);return bounds.minimumZ+height/2.0-latitude*120000.0/effectiveScale;}
    private void requireGeographic(double value){
        if(!hasGeographicConversion()||!finite(value)) throw new IllegalArgumentException("Unsupported Earth projection or non-finite coordinate");
    }
    private static boolean finite(double value){return !Double.isNaN(value)&&!Double.isInfinite(value);}

    /** Source encoding is 0..65535 -> nominal Y=0..255, not elevation in metres. */
    public int surfaceY(int sample){
        long nominal=Math.round((sample&0xffff)*255.0/65535.0);
        double scaled=seaLevel+(nominal-seaLevel)*verticalElevationScale;
        return (int)Math.round(Math.max(minimumSurfaceY,Math.min(maximumSurfaceY,scaled)));
    }

    /** Canonical profile for the existing external 20k raster generator. */
    public static XFEarthProfile earth2000(){
        XFEarthProfile p=new XFEarthProfile();
        p.formatVersion=1;p.profile="earth2000";p.targetMinecraftVersion="1.7.10";
        p.sourceScale=20;p.resize=100;p.effectiveScale=2000;p.width=21504;p.height=10752;
        p.minimumX=-10752;p.maximumX=10751;p.minimumZ=-5376;p.maximumZ=5375;
        p.minimumSurfaceY=1;p.maximumSurfaceY=254;p.seaLevel=62;
        p.projection="equirectangular";p.populationMode="pregenerated";
        p.validate("1.7.10");return p;
    }
    /** New storage preparation only; cannot be installed or opened for gameplay. */
    public static XFEarthProfile earth2000Extended(){
        XFEarthProfile p=earth2000();p.maximumY=511;p.maximumSurfaceY=510;
        p.heightImplementation=com.hfr.world.earth.storage.XFEarthStorageIdentity.CUBIC;
        p.heightImplementationVersion=com.hfr.world.earth.storage.XFEarthStorageIdentity.CUBIC_VERSION;
        p.validate("1.7.10");return p;
    }
}
