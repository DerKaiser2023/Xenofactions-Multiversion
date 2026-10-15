/* WorldPainter 2.27.0 / Nashorn. Reusable after terrain preparation, before save/export. */
var XFRoads = (function () {
    var Files = Java.type("java.nio.file.Files");
    var File = Java.type("java.io.File");
    var Dimension = Java.type("org.pepsoft.worldpainter.Dimension");
    var Terrain = Java.type("org.pepsoft.worldpainter.Terrain");
    var Frost = Java.type("org.pepsoft.worldpainter.layers.Frost");
    function read(file) {
        if (file.length() > 65536) throw new Error("Road/profile manifest exceeds 64 KiB");
        return JSON.parse(String(new java.lang.String(Files.readAllBytes(file.toPath()), "UTF-8")));
    }
    function hash(file) {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        var input = new java.io.BufferedInputStream(new java.io.FileInputStream(file));
        var ByteArray = Java.type("byte[]");
        var buffer = new ByteArray(65536);
        try { var n; while ((n = input.read(buffer)) !== -1) digest.update(buffer, 0, n); }
        finally { input.close(); }
        var bytes = digest.digest(), text = "";
        for (var i = 0; i < bytes.length; i++) text += ("0" + (bytes[i] & 255).toString(16)).slice(-2);
        return text;
    }
    function apply(world, profile, directory, material, vegetationLayers, waterLayers) {
        var manifest = read(new File(directory, "roads.json")), p = manifest.profile;
        var defaults = {minimumY:0, maximumY:255, heightImplementation:"vanilla-anvil", heightImplementationVersion:"1.7.10", verticalElevationScale:1.0};
        var fields = "formatVersion profile targetMinecraftVersion sourceScale resize effectiveScale width height minimumX maximumX minimumZ maximumZ minimumSurfaceY maximumSurfaceY seaLevel projection populationMode caves ores lava structures vegetation minimumY maximumY heightImplementation heightImplementationVersion verticalElevationScale".split(" ");
        for (var f = 0; f < fields.length; f++) {
            var key = fields[f];
            var actual = typeof profile[key] === "undefined" ? defaults[key] : profile[key];
            if (typeof p[key] === "undefined" || actual !== p[key]) throw new Error("Road/profile mismatch: " + key);
        }
        if (manifest.formatVersion !== 1 || manifest.processingVersion !== 1 || manifest.source !== "GRIP4"
                || manifest.orientation !== "x-east-z-south" || manifest.pixelsPerColumn !== 1
                || manifest.width !== profile.width || manifest.height !== profile.height
                || String(world.getPlatform().id) !== "org.pepsoft.anvil") throw new Error("Unsupported road mask or Minecraft format");
        if (material !== "gravel" && material !== "cobblestone") throw new Error("Choose an explicit supported road material: gravel or cobblestone");
        var terrain = material === "gravel" ? Terrain.GRAVEL : Terrain.COBBLESTONE;
        var dimension = world.getDimension(Dimension.Anchor.NORMAL_DETAIL);
        if (!dimension || dimension.getMinHeight() !== 0 || dimension.getMaxHeight() !== 256) throw new Error("Road application requires existing 0..255 surface terrain");
        var png = new File(directory, "roads.png");
        if (png.length() > 67108864 || hash(png) !== manifest.maskSha256) throw new Error("Road mask identity mismatch");
        // Check dimensions before decoding to reject oversized/corrupt masks.
        var input = javax.imageio.ImageIO.createImageInputStream(png), reader = null, image;
        try {
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new Error("Unreadable road PNG");
            reader = readers.next(); reader.setInput(input);
            if (reader.getWidth(0) !== profile.width || reader.getHeight(0) !== profile.height
                    || profile.width * profile.height > 67108864) throw new Error("Road mask grid mismatch");
            image = reader.read(0);
        } finally { if (reader) reader.dispose(); if (input) input.close(); }
        var raster = image.getRaster(), painted = 0, skipped = 0;
        function hasWaterLayer(x, z) {
            if (!waterLayers) return false;
            for (var i = 0; i < waterLayers.length; i++) {
                var layer = waterLayers[i];
                if (!layer) continue;
                var size = String(layer.getDataSize());
                if (size === "BIT" || size === "BIT_PER_CHUNK") {
                    if (dimension.getBitLayerValueAt(layer,x,z)) return true;
                } else if (dimension.getLayerValueAt(layer,x,z) > 0) return true;
            }
            return false;
        }
        try {
            // Validate the entire categorical mask before editing the project.
            for (var row = 0; row < profile.height; row++) {
                wp.checkForInterrupt();
                for (var col = 0; col < profile.width; col++) if (raster.getSample(col,row,0) > 3) throw new Error("Invalid road category pixel");
            }
            for (var z = 0; z < profile.height; z++) {
                wp.checkForInterrupt();
                for (var x = 0; x < profile.width; x++) {
                    if (raster.getSample(x,z,0) === 0) continue;
                    var bx = profile.minimumX + x, bz = profile.minimumZ + z;
                    if (!dimension.isTilePresent(bx >> 7,bz >> 7)
                            || dimension.getBitLayerValueAt(Java.type("org.pepsoft.worldpainter.layers.Void").INSTANCE,bx,bz)
                            || dimension.getWaterLevelAt(bx,bz) > dimension.getIntHeightAt(bx,bz)
                            || hasWaterLayer(bx,bz)
                            || dimension.getBitLayerValueAt(Frost.INSTANCE,bx,bz)) { skipped++; continue; }
                    dimension.setTerrainAt(bx,bz,terrain);
                    // Only the generator's explicitly supplied vegetation layers are cleared.
                    // All population/export settings and all non-road cells retain their values.
                    if (vegetationLayers) for (var group in vegetationLayers) {
                        var layers = vegetationLayers[group];
                        for (var i = 0; i < layers.length; i++) dimension.setLayerValueAt(layers[i],bx,bz,0);
                    }
                    painted++;
                }
            }
        } finally { image.flush(); }
        print("GRIP4: painted " + painted + " terrain columns; skipped " + skipped + " water/ice/void columns. Symbolic surface terrain only; no grades, bridges or tunnels.");
        print(manifest.attribution + " / " + manifest.license + ". " + manifest.limitations);
    }
    function saving(world, exportDirectory) {
        var operation = wp.saveWorld(world);
        return {
            toFile:function(path) { operation.toFile(path); return this; },
            go:function() {
                if (exportDirectory && exportDirectory.exists()) throw new Error("Export staging directory already exists; choose a new directory outside player saves");
                operation.go();
                if (exportDirectory) {
                    if (!exportDirectory.mkdirs()) throw new Error("Cannot create new export staging directory");
                    // A brand-new parent prevents WorldPainter's automatic existing-world replacement.
                    wp.exportWorld(world).toDirectory(exportDirectory.getAbsolutePath()).go();
                }
            }
        };
    }
    return {read:read, apply:apply, saving:saving};
}());
