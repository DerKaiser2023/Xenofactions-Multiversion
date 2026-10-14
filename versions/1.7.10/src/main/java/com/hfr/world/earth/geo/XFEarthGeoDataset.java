package com.hfr.world.earth.geo;

import com.google.gson.*;
import com.hfr.world.earth.XFEarthProfile;
import java.io.*;
import java.util.List;

/** Versioned offline data boundary. Empty candidates never certify geographic absence. */
public final class XFEarthGeoDataset {
    public static final int VERSION=1, INDEX_MAGIC=0x58464749, INDEX_HEADER=48, MAX_RECORD=8*1024*1024;
    public static final String COVERAGE="SOURCE_CONTENT_ONLY_UNVERIFIED";
    public static final class Feature {
        public long sourceRecord;
        public String sourceType, sourceId, sourceTimestamp;
        public JsonObject properties,sourceAttributes,tags,sourceExtras;
        public List<String> categories;
        public List<XFEarthGeoGeometry.Part> geometry;
        public List<Location> businessLocations;
        public double[] bounds;
    }
    public static final class Location {
        public double x,z;
        public String kind;
        Location(double[] position,String kind){x=position[0];z=position[1];this.kind=kind;}
    }
    public interface Visitor { void accept(Feature feature) throws IOException; }
    public enum Availability { AVAILABLE_SOURCE_ONLY, NO_DATASET, INCOMPATIBLE_OR_DAMAGED }
    public final File directory;
    public final JsonObject manifest;
    private final String identity;
    private XFEarthGeoDataset(File directory,JsonObject manifest){this.directory=directory;this.manifest=manifest;identity=XFEarthGeoJson.text(manifest,"datasetIdentity");}
    public static String profileIdentity(XFEarthProfile profile){return XFEarthGeoJson.hash(XFEarthGeoJson.canonical(XFEarthGeoJson.GSON.toJsonTree(profile)));}
    public static String datasetIdentity(String sourceHash,String profileHash,JsonObject metadata) {
        return XFEarthGeoJson.hash("XF-GEO\n"+VERSION+"\n"+XFEarthGeoGeometry.VERSION+"\n"+XFEarthGeoImporter.CLASSIFICATION_VERSION
                +"\n"+sourceHash+"\n"+profileHash+"\n"+XFEarthGeoJson.canonical(metadata));
    }
    public static XFEarthGeoDataset open(File directory,XFEarthProfile profile) throws IOException {
        if(!directory.isDirectory())throw new FileNotFoundException("No imported geographic dataset: "+directory.getName());
        JsonObject m=XFEarthGeoJson.file(new File(directory,"dataset.json"),524288);
        try {
            if(m.get("formatVersion").getAsInt()!=VERSION||m.get("projectionVersion").getAsInt()!=XFEarthGeoGeometry.VERSION
                    ||m.get("classificationVersion").getAsInt()!=XFEarthGeoImporter.CLASSIFICATION_VERSION
                    ||!"COMPLETE".equals(XFEarthGeoJson.text(m,"state"))||!"1.7.10".equals(XFEarthGeoJson.text(m,"minecraftVersion"))
                    ||!COVERAGE.equals(XFEarthGeoJson.text(m,"coverage"))||!profileIdentity(profile).equals(XFEarthGeoJson.text(m,"profileIdentity")))
                throw new IOException("Geographic dataset version/profile mismatch or incomplete import");
            JsonObject source=m.getAsJsonObject("source");XFEarthGeoImporter.validateSource(source,XFEarthGeoJson.text(m,"datasetId"));
            if(!directory.getName().equals(XFEarthGeoJson.text(m,"datasetId"))||!XFEarthGeoImporter.LICENSE.equals(XFEarthGeoJson.text(m,"license"))
                    ||!XFEarthGeoImporter.LICENSE_URL.equals(XFEarthGeoJson.text(m,"licenseUrl"))
                    ||!XFEarthGeoJson.text(source,"attribution").equals(XFEarthGeoJson.text(m,"attribution"))
                    ||!XFEarthGeoJson.text(m,"sourceSha256").matches("[0-9a-f]{64}")
                    ||!XFEarthGeoJson.hash(XFEarthGeoJson.canonical(m.get("profile"))).equals(XFEarthGeoJson.text(m,"profileIdentity")))
                throw new IOException("Geographic snapshot/attribution identity mismatch");
            String identity=datasetIdentity(XFEarthGeoJson.text(m,"sourceSha256"),XFEarthGeoJson.text(m,"profileIdentity"),source);
            if(!identity.equals(XFEarthGeoJson.text(m,"datasetIdentity"))||!new File(directory,"features.dat").isFile()
                    ||!new File(directory,"regions").isDirectory())throw new IOException("Geographic dataset identity/files mismatch");
            return new XFEarthGeoDataset(directory,m);
        } catch(RuntimeException e){throw new IOException("Malformed geographic dataset manifest",e);}
    }
    /** Pure disk read; caller chooses its data-processing thread. Returns conservative region candidates. */
    public long readRegion(int regionX,int regionZ,Visitor visitor) throws IOException {
        File index=new File(new File(directory,"regions"),"r."+regionX+"."+regionZ+".xgi");
        if(!index.exists())return 0; // No imported candidates, not confirmed absence in OSM or the real world.
        long count=0;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(index)));
                RandomAccessFile features=new RandomAccessFile(new File(directory,"features.dat"),"r")) {
            if(index.length()<INDEX_HEADER||(index.length()-INDEX_HEADER)%12!=0||in.readInt()!=INDEX_MAGIC||in.readInt()!=VERSION
                    ||in.readInt()!=regionX||in.readInt()!=regionZ)throw new IOException("Invalid geographic region index header");
            byte[] digest=new byte[32];in.readFully(digest);
            if(!identity.equals(XFEarthGeoJson.hex(digest)))throw new IOException("Geographic region belongs to another dataset");
            long entries=(index.length()-INDEX_HEADER)/12;
            for(long i=0;i<entries;i++) {
                long offset=in.readLong();int length=in.readInt();
                if(offset<0||length<1||length>MAX_RECORD||offset>features.length()-4L-length)throw new IOException("Invalid geographic feature reference");
                features.seek(offset);if(features.readInt()!=length)throw new IOException("Geographic feature length mismatch");
                byte[] bytes=new byte[length];features.readFully(bytes);
                try {
                    Feature feature=XFEarthGeoJson.GSON.fromJson(XFEarthGeoJson.object(bytes),Feature.class);
                    if(feature==null||feature.bounds==null||feature.geometry==null||feature.categories==null
                            ||feature.sourceType==null||feature.sourceId==null||feature.sourceTimestamp==null)
                        throw new IOException("Incomplete derived geographic feature");
                    visitor.accept(feature);count++;
                } catch(RuntimeException e){throw new IOException("Malformed derived geographic feature or failed visitor",e);}
            }
        }
        return count;
    }
    public static Availability availability(File directory,XFEarthProfile profile) {
        if(!directory.exists())return Availability.NO_DATASET;
        try{open(directory,profile);return Availability.AVAILABLE_SOURCE_ONLY;}catch(IOException|RuntimeException e){return Availability.INCOMPATIBLE_OR_DAMAGED;}
    }
    static byte[] identityBytes(String identity) {
        if(!identity.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid dataset SHA-256");
        byte[] result=new byte[32];for(int i=0;i<32;i++)result[i]=(byte)Integer.parseInt(identity.substring(i*2,i*2+2),16);return result;
    }
}
