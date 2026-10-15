package com.hfr.world.earth.geo;

import com.google.gson.JsonObject;
import com.hfr.config.XFConfig;
import com.hfr.world.earth.XFEarthProfile;
import java.io.*;
import java.nio.file.*;

/** Small save-local selection record; dataset files stay external and immutable. Worker use only. */
public final class XFEarthGeoSelection {
    private static File path(File save){return new File(save,"data/xfearth-geographic-selection.json");}
    private XFEarthGeoSelection(){}
    public static XFEarthRoadSource open(File save,XFEarthProfile profile,XFEarthRoadSource.Policy policy)throws IOException{
        String configured=XFConfig.earthRoadDatasetDirectory.trim();
        if(!configured.isEmpty())return new XFEarthRoadSource(XFEarthRoadSource.configuredDirectory(),profile,policy);
        File file=path(save);
        if(!file.exists())throw new IOException("No completed dataset selected. External source.json + features.geojsonseq required: /xc earth geo import <id>, then /xc earth geo select <id>; /xc earth geo status shows paths. 1:16000 does not download OSM");
        try{
            JsonObject record=XFEarthGeoJson.file(file,32768);
            if(record.get("formatVersion").getAsInt()!=1)throw new IOException("Unsupported saved selection; use /xc earth geo select <id>");
            XFEarthRoadSource source=new XFEarthRoadSource(new File(XFEarthGeoJson.text(record,"directory")),profile,policy);
            if(!XFEarthGeoJson.text(record,"datasetIdentity").equals(XFEarthGeoJson.text(source.dataset.manifest,"datasetIdentity")))
                throw new IOException("Selected dataset identity changed; review it and use /xc earth geo select <id>");
            return source;
        }catch(RuntimeException e){throw new IOException("Invalid saved geographic selection; use /xc earth geo select <id>",e);}
    }
    public static void save(File save,XFEarthGeoDataset dataset)throws IOException{
        JsonObject record=new JsonObject();record.addProperty("formatVersion",1);
        record.addProperty("directory",dataset.directory.getCanonicalPath());
        record.add("datasetIdentity",dataset.manifest.get("datasetIdentity"));
        File file=path(save);Files.createDirectories(file.getParentFile().toPath());
        Path temporary=Files.createTempFile(file.getParentFile().toPath(),"xfearth-selection-",".tmp");
        try{
            try(Writer out=new OutputStreamWriter(new FileOutputStream(temporary.toFile()),"UTF-8")){XFEarthGeoJson.GSON.toJson(record,out);}
            // Same-directory atomic replacement: a failed write keeps the previous choice intact.
            Files.move(temporary,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temporary);}
    }
}
