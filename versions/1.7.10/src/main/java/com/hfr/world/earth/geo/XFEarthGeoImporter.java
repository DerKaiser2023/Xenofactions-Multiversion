package com.hfr.world.earth.geo;

import com.google.gson.*;
import com.hfr.world.earth.XFEarthProfile;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Local regional OSM export importer. No Minecraft world, network, blocks or claim mutation. */
public final class XFEarthGeoImporter {
    public static final int CLASSIFICATION_VERSION=1;
    public static final String ATTRIBUTION="© OpenStreetMap contributors", LICENSE="ODbL-1.0";
    public static final String LICENSE_URL="https://opendatacommons.org/licenses/odbl/1-0/";
    public static final class Limits {
        public final int featureBytes,vertices,openRegions,regionsPerFeature;
        public Limits(int bytes,int vertices,int openRegions,int regionsPerFeature) {
            if(bytes<1024||bytes>4*1024*1024||vertices<4||vertices>32768||openRegions<1||openRegions>32||regionsPerFeature<1||regionsPerFeature>4096)
                throw new IllegalArgumentException("Geographic import limits exceed supported bounds");
            featureBytes=bytes;this.vertices=vertices;this.openRegions=openRegions;this.regionsPerFeature=regionsPerFeature;
        }
    }
    public interface Control {
        void check() throws IOException;
        void progress(long records,long imported,long rejected);
        void publish(File partial,File destination) throws IOException;
    }
    private XFEarthGeoImporter() { }
    public static void validateId(String id){if(id==null||!id.matches("[a-z0-9][a-z0-9_-]{0,63}"))throw new IllegalArgumentException("Invalid geographic dataset ID");}
    public static void validateSource(JsonObject m,String id) {
        validateId(id);
        if(m.get("formatVersion").getAsInt()!=1||!id.equals(XFEarthGeoJson.text(m,"datasetId"))
                ||!"OpenStreetMap".equals(XFEarthGeoJson.text(m,"source"))||!"osmium-geojsonseq".equals(XFEarthGeoJson.text(m,"sourceFormat"))
                ||!LICENSE.equals(XFEarthGeoJson.text(m,"license"))||!LICENSE_URL.equals(XFEarthGeoJson.text(m,"licenseUrl"))
                ||!XFEarthGeoJson.text(m,"attribution").contains(ATTRIBUTION))throw new IllegalArgumentException("Source identity/OSM attribution/license mismatch");
        Instant.parse(XFEarthGeoJson.text(m,"snapshotTimestamp"));
        String sourceUrl=XFEarthGeoJson.text(m,"sourceUrl");
        if(!(sourceUrl.startsWith("https://")||sourceUrl.startsWith("http://")))throw new IllegalArgumentException("Source URL is required for provenance (never fetched)");
        XFEarthGeoJson.text(m,"exporter");
        if(m.has("extractSha256")&&!XFEarthGeoJson.text(m,"extractSha256").matches("[0-9a-fA-F]{64}"))throw new IllegalArgumentException("Invalid original extract SHA-256");
    }
    /** Writes only a new external dataset directory, publishing after all data/index files close. */
    public static JsonObject importDataset(File sourceDirectory,File destination,XFEarthProfile profile,Limits limits,Control control) throws IOException {
        profile=XFEarthGeoJson.GSON.fromJson(XFEarthGeoJson.GSON.toJson(profile),XFEarthProfile.class);
        profile.validate("1.7.10");XFEarthGeoGeometry.validateProfile(profile);control.check();
        File target=destination.getCanonicalFile();requireExternal(target);validateId(target.getName());
        File metadata=new File(sourceDirectory,"source.json");
        if(!metadata.isFile())throw new FileNotFoundException("Missing local source.json at "+metadata+"; prepare external OSM export under earthGeographicSourceDirectory/<id>; no automatic download");
        JsonObject source=XFEarthGeoJson.file(metadata,65536);
        try{validateSource(source,target.getName());}catch(RuntimeException e){throw new IOException("Invalid source.json: "+e.getMessage()+"; correct earthGeographicSourceDirectory/<id>/source.json",e);}
        File input=new File(sourceDirectory,"features.geojsonseq");
        if(!input.isFile())throw new FileNotFoundException("Missing local features.geojsonseq at "+input+"; export regional OSM externally under earthGeographicSourceDirectory/<id>");
        if(target.exists())throw new IOException("Existing geographic dataset is never overwritten; use a new dataset ID");
        String sourceHash=XFEarthGeoJson.hash(input,control),profileHash=XFEarthGeoDataset.profileIdentity(profile);
        String identity=XFEarthGeoDataset.datasetIdentity(sourceHash,profileHash,source);
        File parent=target.getParentFile();Files.createDirectories(parent.toPath());
        File partial=new File(parent,target.getName()+".importing-"+UUID.randomUUID());Files.createDirectory(partial.toPath());
        File regions=new File(partial,"regions");Files.createDirectory(regions.toPath());
        JsonObject manifest=new JsonObject();manifest.addProperty("formatVersion",XFEarthGeoDataset.VERSION);
        manifest.addProperty("projectionVersion",XFEarthGeoGeometry.VERSION);manifest.addProperty("classificationVersion",CLASSIFICATION_VERSION);
        manifest.addProperty("minecraftVersion","1.7.10");manifest.addProperty("datasetId",target.getName());manifest.addProperty("datasetIdentity",identity);
        manifest.addProperty("profileIdentity",profileHash);manifest.add("profile",XFEarthGeoJson.GSON.toJsonTree(profile));manifest.add("source",source);
        manifest.addProperty("sourceSha256",sourceHash);manifest.addProperty("coverage",XFEarthGeoDataset.COVERAGE);
        manifest.addProperty("license",LICENSE);manifest.addProperty("licenseUrl",LICENSE_URL);manifest.add("attribution",source.get("attribution"));
        manifest.addProperty("state","IMPORTING");manifest.addProperty("startedAt",Instant.now().toString());
        XFEarthGeoJson.writeNew(new File(partial,"import.json"),manifest);
        long processed=0,imported=0,malformed=0,unsupported=0,outside=0,unclassified=0,indexEntries=0,warnings=0;
        Map<String,Long> categories=new TreeMap<String,Long>();MessageDigest importedHash=XFEarthGeoJson.digest();
        try {
            try(XFEarthGeoJson.Records records=new XFEarthGeoJson.Records(new DigestInputStream(new FileInputStream(input),importedHash),limits.featureBytes,control);
                    RandomAccessFile features=new RandomAccessFile(new File(partial,"features.dat"),"rw");
                    Writer issues=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(partial,"issues.jsonl")),StandardCharsets.UTF_8));
                    RegionWriters writers=new RegionWriters(regions,identity,limits.openRegions)) {
                for(byte[] record;(record=records.next())!=null;) {
                    control.check();processed++;JsonObject original=null;
                    if((processed&255)==0)control.progress(processed,imported,malformed+unsupported);
                    try {
                        if(records.oversize)throw new XFEarthGeoGeometry.Unsupported("Source record byte limit exceeded");
                        original=XFEarthGeoJson.object(record,131072);
                        XFEarthGeoDataset.Feature feature=feature(original,records.ordinal,profile,limits,control);
                        if(feature.geometry.isEmpty()){outside++;continue;}
                        if(feature.categories.isEmpty()){unclassified++;continue;}
                        double[] bounds=feature.bounds;
                        int minRx=region(bounds[0]),maxRx=region(Math.min(bounds[2],profile.getBounds().maximumX));
                        int minRz=region(bounds[1]),maxRz=region(Math.min(bounds[3],profile.getBounds().maximumZ));
                        long count=((long)maxRx-minRx+1)*((long)maxRz-minRz+1);
                        if(count<1||count>limits.regionsPerFeature)throw new XFEarthGeoGeometry.Unsupported("Feature region-index fanout limit exceeded");
                        byte[] bytes=XFEarthGeoJson.GSON.toJson(feature).getBytes(StandardCharsets.UTF_8);
                        if(bytes.length>XFEarthGeoDataset.MAX_RECORD)throw new XFEarthGeoGeometry.Unsupported("Derived record byte limit exceeded");
                        long offset=features.getFilePointer();features.writeInt(bytes.length);features.write(bytes);
                        for(int rx=minRx;rx<=maxRx;rx++)for(int rz=minRz;rz<=maxRz;rz++){control.check();writers.entry(rx,rz,offset,bytes.length);indexEntries++;}
                        imported++;for(String category:feature.categories)categories.put(category,categories.containsKey(category)?categories.get(category)+1:1L);
                        if(feature.categories.contains("business")&&feature.businessLocations.isEmpty()){warnings++;issue(issues,records.ordinal,original,"warning","Business-tagged geometry has no representative point; location remains unknown");}
                    } catch(XFEarthGeoGeometry.Unsupported e){unsupported++;issue(issues,records.ordinal,original,"unsupported",e.getMessage());}
                    catch(IllegalArgumentException|IllegalStateException|ArithmeticException|java.time.DateTimeException|ClassCastException|NullPointerException e){malformed++;issue(issues,records.ordinal,original,"malformed",e.getMessage());}
                    catch(IOException e) {
                        if(e instanceof InterruptedIOException)throw e;
                        // Parsing failures are recoverable; disk/index failures must not publish a partial dataset.
                        if(original!=null)throw e;
                        malformed++;issue(issues,records.ordinal,null,"malformed",e.getMessage());
                    }
                }
                features.getFD().sync();
            }
            control.check();if(!sourceHash.equals(XFEarthGeoJson.hex(importedHash.digest())))throw new IOException("Source changed during geographic import");
            manifest.addProperty("records",processed);manifest.addProperty("imported",imported);manifest.addProperty("malformed",malformed);
            manifest.addProperty("unsupported",unsupported);manifest.addProperty("outsideWorld",outside);manifest.addProperty("unclassified",unclassified);
            manifest.addProperty("indexEntries",indexEntries);manifest.add("categories",XFEarthGeoJson.GSON.toJsonTree(categories));
            manifest.addProperty("warnings",warnings);manifest.addProperty("importIssues",malformed+unsupported+warnings>0);manifest.addProperty("upstreamCompleteness","UNVERIFIED");
            manifest.addProperty("state","COMPLETE");manifest.addProperty("completedAt",Instant.now().toString());
            XFEarthGeoJson.writeNew(new File(partial,"dataset.json"),manifest);control.check();
            control.publish(partial,target);
            control.progress(processed,imported,malformed+unsupported);return manifest;
        } catch(IOException|RuntimeException e) {
            JsonObject failure=new JsonObject();failure.addProperty("state",e instanceof InterruptedIOException?"CANCELLED":"FAILED");
            failure.addProperty("error",String.valueOf(e.getMessage()));failure.addProperty("partialDirectory",partial.getName());
            try{XFEarthGeoJson.writeNew(new File(partial,"failure.json"),failure);}catch(IOException suppressed){e.addSuppressed(suppressed);}
            throw e;
        }
    }
    private static XFEarthGeoDataset.Feature feature(JsonObject original,long record,XFEarthProfile profile,Limits limits,Control control) throws IOException {
        if(!"Feature".equals(XFEarthGeoJson.text(original,"type")))throw new XFEarthGeoGeometry.Unsupported("Only GeoJSON Feature sequence records are supported");
        JsonObject properties=original.getAsJsonObject("properties");
        if(properties==null)throw new IllegalArgumentException("Missing OSM feature properties");
        XFEarthGeoDataset.Feature feature=new XFEarthGeoDataset.Feature();feature.sourceRecord=record;feature.properties=properties;
        feature.sourceType=XFEarthGeoJson.text(properties,"@type");
        if(!Arrays.asList("node","way","relation").contains(feature.sourceType))throw new IllegalArgumentException("Invalid OSM source object type");
        long id=new BigDecimal(XFEarthGeoJson.text(properties,"@id")).longValueExact();if(id==0)throw new IllegalArgumentException("Zero OSM object ID");feature.sourceId=Long.toString(id);
        feature.sourceTimestamp=XFEarthGeoJson.text(properties,"@timestamp");
        try{new BigDecimal(feature.sourceTimestamp).longValueExact();}catch(NumberFormatException|ArithmeticException e){Instant.parse(feature.sourceTimestamp);}
        feature.tags=new JsonObject();feature.sourceAttributes=new JsonObject();feature.sourceExtras=new JsonObject();
        Set<String> attributes=new HashSet<String>(Arrays.asList("@type","@id","@version","@timestamp","@changeset","@uid","@user","@way_nodes"));
        for(Map.Entry<String,JsonElement> entry:properties.entrySet()) {
            if(attributes.contains(entry.getKey()))feature.sourceAttributes.add(entry.getKey(),entry.getValue());
            else {
                if(!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isString())throw new IllegalArgumentException("OSM tag value is not a string");
                feature.tags.add(entry.getKey(),entry.getValue());
            }
        }
        for(Map.Entry<String,JsonElement> entry:original.entrySet())if(!"geometry".equals(entry.getKey())&&!"properties".equals(entry.getKey()))feature.sourceExtras.add(entry.getKey(),entry.getValue());
        JsonElement geometry=original.get("geometry");if(geometry==null||!geometry.isJsonObject())throw new XFEarthGeoGeometry.Unsupported("Missing geographic geometry");
        feature.geometry=XFEarthGeoGeometry.project(geometry.getAsJsonObject(),profile,limits.vertices,control);feature.bounds=XFEarthGeoGeometry.bounds(feature.geometry);
        boolean area=false,line=false;for(XFEarthGeoGeometry.Part part:feature.geometry){area|="Area".equals(part.type);line|="LineString".equals(part.type);}
        feature.categories=new ArrayList<String>();
        if((line||area)&&tag(feature.tags,"highway"))feature.categories.add("road");
        if(area&&tag(feature.tags,"landuse"))feature.categories.add("landuse");
        if(area&&(tag(feature.tags,"building")||tag(feature.tags,"building:part")))feature.categories.add("building");
        if((area||line)&&"administrative".equals(tagValue(feature.tags,"boundary")))feature.categories.add("administrative");
        feature.businessLocations=new ArrayList<XFEarthGeoDataset.Location>();
        if(business(feature.tags)) {
            feature.categories.add("business");
            for(XFEarthGeoGeometry.Part part:feature.geometry) {
                if("Point".equals(part.type))feature.businessLocations.add(new XFEarthGeoDataset.Location(part.points[0],"source-point"));
                else if(part.interiorPoint!=null)feature.businessLocations.add(new XFEarthGeoDataset.Location(part.interiorPoint,"derived-clipped-area-interior"));
            }
        }
        return feature;
    }
    private static boolean tag(JsonObject tags,String key){String value=tagValue(tags,key);return !value.isEmpty()&&!"no".equals(value)&&!"vacant".equals(value)&&!"disused".equals(value)&&!"abandoned".equals(value);}
    private static String tagValue(JsonObject tags,String key){return tags.has(key)?tags.get(key).getAsString():"";}
    private static boolean business(JsonObject tags) {
        if(tag(tags,"shop")||tag(tags,"office")||tag(tags,"craft"))return true;
        if(Arrays.asList("hotel","motel","guest_house","hostel","apartment","camp_site","caravan_site").contains(tagValue(tags,"tourism")))return true;
        return Arrays.asList("restaurant","cafe","fast_food","bar","pub","biergarten","ice_cream","fuel","bank","pharmacy","cinema","theatre","marketplace","car_wash","vehicle_inspection","food_court","internet_cafe","nightclub","casino").contains(tagValue(tags,"amenity"));
    }
    public static int region(double blockCoordinate) {
        if(Double.isNaN(blockCoordinate)||Double.isInfinite(blockCoordinate)||blockCoordinate<Integer.MIN_VALUE||blockCoordinate>=(double)Integer.MAX_VALUE+1)
            throw new IllegalArgumentException("Projected coordinate exceeds the region-index range");
        return com.hfr.world.earth.XFEarthBounds.chunkToRegion(com.hfr.world.earth.XFEarthBounds.floorDiv((int)Math.floor(blockCoordinate),16));
    }
    public static void requireExternal(File directory) throws IOException {
        for(File cursor=directory.getCanonicalFile();cursor!=null;cursor=cursor.getParentFile())
            if(new File(cursor,".git").exists())throw new IOException("Derived geographic data must be outside a Git workspace; configure earthGeographicDataDirectory");
    }
    private static void issue(Writer writer,long ordinal,JsonObject feature,String kind,String reason) throws IOException {
        JsonObject issue=new JsonObject();issue.addProperty("sourceRecord",ordinal);issue.addProperty("kind",kind);issue.addProperty("reason",reason);
        if(feature!=null&&feature.has("id"))issue.add("exportId",feature.get("id"));
        writer.write(XFEarthGeoJson.GSON.toJson(issue));writer.write('\n');
    }
    private static final class RegionWriters implements Closeable {
        final File root;final byte[] identity;final int limit;
        final LinkedHashMap<String,DataOutputStream> open=new LinkedHashMap<String,DataOutputStream>(16,0.75F,true);
        RegionWriters(File root,String identity,int limit){this.root=root;this.identity=XFEarthGeoDataset.identityBytes(identity);this.limit=limit;}
        void entry(int x,int z,long offset,int length) throws IOException {
            String key="r."+x+"."+z+".xgi";DataOutputStream out=open.get(key);
            if(out==null) {
                if(open.size()>=limit){Iterator<DataOutputStream> iterator=open.values().iterator();DataOutputStream oldest=iterator.next();iterator.remove();oldest.close();}
                File file=new File(root,key);boolean fresh=!file.exists();out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file,true)));open.put(key,out);
                if(fresh){out.writeInt(XFEarthGeoDataset.INDEX_MAGIC);out.writeInt(XFEarthGeoDataset.VERSION);out.writeInt(x);out.writeInt(z);out.write(identity);}
            }
            out.writeLong(offset);out.writeInt(length);
        }
        @Override public void close() throws IOException {
            IOException failure=null;for(DataOutputStream out:open.values())try{out.close();}catch(IOException e){if(failure==null)failure=e;else failure.addSuppressed(e);}open.clear();if(failure!=null)throw failure;
        }
    }
}
