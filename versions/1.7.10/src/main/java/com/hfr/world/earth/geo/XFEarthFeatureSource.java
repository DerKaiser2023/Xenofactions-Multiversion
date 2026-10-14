package com.hfr.world.earth.geo;

import com.hfr.config.XFConfig;
import java.awt.geom.*;
import java.io.*;
import java.util.*;

/** Interpretation of the same Stage 5 dataset. Geometry is source-derived; palettes are generic choices. */
public final class XFEarthFeatureSource {
    public static final int VERSION=1;
    public final XFEarthRoadSource roads;
    public final Policy policy;
    public final String identity;
    private final LinkedHashMap<String,List<Feature>> cache=new LinkedHashMap<String,List<Feature>>(4,.75F,true);
    public static final class Policy {
        public final boolean enabled;
        public final int scale,height,span;
        public Policy(){enabled=XFConfig.earthGenerateGeographicFeatures;scale=XFConfig.earthFeatureBuildingMaxScale;height=XFConfig.earthFeatureMaxHeight;span=XFConfig.earthFeatureMaxSpan;}
        public String key(){return enabled+":"+scale+":"+height+":"+span;}
    }
    public static final class Feature {
        public String key,kind,appearance,crop;
        public Area area;
        public int priority,height;
        public boolean structure,point,representable;
        public double x,z;
        public Rectangle2D bounds(){return area.getBounds2D();}
    }
    public XFEarthFeatureSource(XFEarthRoadSource roads,Policy policy){this.roads=roads;this.policy=policy;identity=XFEarthGeoJson.hash("XF-FEATURES:"+VERSION+":"+roads.identity+":"+policy.key());}
    private static String tag(XFEarthGeoDataset.Feature f,String key){return f.tags!=null&&f.tags.has(key)?f.tags.get(key).getAsString():"";}
    private static boolean active(String value){return !value.isEmpty()&&!Arrays.asList("no","vacant","disused","abandoned").contains(value);}
    private static String business(XFEarthGeoDataset.Feature f){
        if(active(tag(f,"shop")))return "shop";
        if(active(tag(f,"office"))||active(tag(f,"craft")))return "workshop";
        String a=tag(f,"amenity");if(Arrays.asList("restaurant","cafe","fast_food","bar","pub","food_court","ice_cream").contains(a))return "food";
        if(Arrays.asList("hotel","motel","guest_house","hostel").contains(tag(f,"tourism")))return "lodging";
        return "";
    }
    private List<Feature> region(int rx,int rz)throws IOException{
        String key=rx+","+rz;List<Feature> present=cache.get(key);if(present!=null)return present;
        final List<Feature> result=new ArrayList<Feature>();final int[] vertices={0};
        roads.dataset.readRegion(rx,rz,new XFEarthGeoDataset.Visitor(){public void accept(XFEarthGeoDataset.Feature f)throws IOException{
            check();boolean building=f.categories.contains("building"),biz=f.categories.contains("business");String landuse=tag(f,"landuse"),buildingTag=tag(f,"building"),business=business(f);
            boolean field=Arrays.asList("farmland","meadow","farmyard").contains(landuse);
            if(!building&&!biz&&!f.categories.contains("landuse"))return;
            boolean unavailable="yes".equals(tag(f,"disused"))||"yes".equals(tag(f,"abandoned"))||!tag(f,"construction").isEmpty()
                ||"construction".equals(buildingTag)||"ruins".equals(buildingTag)
                ||!tag(f,"min_height").isEmpty()&&!"0".equals(tag(f,"min_height"))||!tag(f,"building:min_level").isEmpty()&&!"0".equals(tag(f,"building:min_level"));
            int partIndex=0;
            for(XFEarthGeoGeometry.Part part:f.geometry){
                String partKey=f.sourceType+":"+f.sourceId+":"+f.sourceRecord+":"+partIndex++;
                if(!"Area".equals(part.type)&&!(biz&&"Point".equals(part.type)))continue;
                if(result.size()>=2048)throw new IOException("Geographic feature region exceeds 2,048 interpreted parts");
                Feature feature=new Feature();feature.key=partKey;feature.crop=tag(f,"crop");feature.point="Point".equals(part.type);
                if(feature.point){if(part.points==null||part.points.length!=1||part.points[0].length!=2)throw new IOException("Malformed business point");
                    feature.x=part.points[0][0];feature.z=part.points[0][1];if(!Double.isFinite(feature.x)||!Double.isFinite(feature.z))throw new IOException("Invalid business point");
                    int x=(int)Math.floor(feature.x),z=(int)Math.floor(feature.z);feature.area=new Area(new Rectangle2D.Double(x-1,z-1,3,3));vertices[0]+=4;
                }else{
                    if(!"even-odd".equals(part.fillRule)||part.rings==null)throw new IOException("Unsupported feature fill rule");
                    for(double[][] ring:part.rings){vertices[0]+=ring.length;for(double[] position:ring)if(position.length!=2||!Double.isFinite(position[0])||!Double.isFinite(position[1]))throw new IOException("Invalid feature contour");}
                    if(vertices[0]>65536)throw new IOException("Feature region exceeds bounded contour budget");feature.area=XFEarthGeoGeometry.area(part);
                    Rectangle2D b=feature.bounds();feature.x=b.getCenterX();feature.z=b.getCenterY();
                }
                feature.structure=building||biz;feature.priority=building?1:biz?2:"farmyard".equals(landuse)?3:"farmland".equals(landuse)?4:5;
                feature.kind=building?"building":biz?"business":landuse;
                feature.appearance=feature.structure?(business.isEmpty()?Arrays.asList("barn","farm_auxiliary","farm","stable","cowshed","shed").contains(buildingTag)?"farm":"generic":business):"generic-field";
                if("greenhouse".equals(buildingTag))feature.appearance="greenhouse";
                feature.height=Math.min(policy.height,"farm".equals(feature.appearance)?4:3);
                try{String levels=tag(f,"building:levels");if(!levels.isEmpty()){int n=Integer.parseInt(levels);if(n>0&&n<=100)feature.height=Math.min(policy.height,Math.max(3,n*3));}}catch(NumberFormatException ignored){}
                Rectangle2D b=feature.bounds();feature.representable=!unavailable&&(feature.structure||field)&&(!feature.structure||(!feature.point||roads.profile.getEffectiveScale()<=policy.scale)&&b.getWidth()>=3&&b.getHeight()>=3
                    &&b.getWidth()<=policy.span&&b.getHeight()<=policy.span&&!feature.area.isEmpty());
                if(biz&&!building&&business.isEmpty())feature.representable=false;
                if(roads.profile.getEffectiveScale()>policy.scale)feature.height=Math.min(feature.height,3);
                if(vertices[0]>65536)throw new IOException("Feature region exceeds bounded contour budget");
                // A generic node kiosk has no inferred source footprint; crop details only apply to farmland.
                result.add(feature);
            }
        }});
        if(cache.size()>=4){Iterator<String> it=cache.keySet().iterator();it.next();it.remove();}cache.put(key,result);return result;
    }
    public List<Feature> query(int minX,int minZ,int maxX,int maxZ,int maxRegions)throws IOException{
        int x0=XFEarthGeoImporter.region(minX),z0=XFEarthGeoImporter.region(minZ),x1=XFEarthGeoImporter.region(maxX),z1=XFEarthGeoImporter.region(maxZ);
        if(((long)x1-x0+1)*((long)z1-z0+1)>maxRegions)throw new IOException("Feature view exceeds region limit");
        List<Feature> result=new ArrayList<Feature>();Set<String> seen=new HashSet<String>();
        for(int z=z0;z<=z1;z++)for(int x=x0;x<=x1;x++)for(Feature feature:region(x,z))if(feature.bounds().intersects(minX,minZ,(double)maxX-minX+1,(double)maxZ-minZ+1)&&seen.add(feature.key)){
            check();if(result.size()>=8192)throw new IOException("Feature query exceeds bounded part limit");result.add(feature);
        }
        Collections.sort(result,new Comparator<Feature>(){public int compare(Feature a,Feature b){int rank=Integer.compare(a.priority,b.priority);return rank==0?a.key.compareTo(b.key):rank;}});return result;
    }
    public static void check()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Geographic feature processing cancelled");}
}
