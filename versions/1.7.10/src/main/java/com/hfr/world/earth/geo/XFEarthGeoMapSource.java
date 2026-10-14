package com.hfr.world.earth.geo;

import com.google.gson.JsonObject;
import com.hfr.world.earth.XFEarthProfile;
import com.hfr.world.earth.map.XFEarthMapLayers;
import com.hfr.world.earth.map.XFEarthMapLayers.Feature;
import java.awt.geom.*;
import java.io.*;
import java.util.*;

/** Pure viewport preparation from the versioned Stage 5 index; no live world/chunk access. */
public final class XFEarthGeoMapSource {
    public static final class View {
        public final List<Feature> features=new ArrayList<Feature>();
        public final Map<String,String> generationKeys=new HashMap<String,String>();
        public String dataset="",roadIdentity="",featureIdentity="",notice="Geographic data unavailable";
        public String attribution="© OpenStreetMap contributors / ODbL";
        public boolean truncated;
    }
    private static final class Stop extends IOException {private static final long serialVersionUID=1L;}
    public static View read(File directory,XFEarthProfile profile,XFEarthRoadSource.Policy roadPolicy,XFEarthFeatureSource.Policy featurePolicy,
            final int x0,final int z0,final int x1,final int z1,final int lod,final int mask)throws IOException{
        final XFEarthRoadSource roads=new XFEarthRoadSource(directory,profile,roadPolicy);final View view=new View();
        view.dataset=roads.dataset.manifest.get("datasetIdentity").getAsString();view.roadIdentity=roads.identity;view.featureIdentity=new XFEarthFeatureSource(roads,featurePolicy).identity;
        String attribution=roads.dataset.manifest.get("attribution").getAsString();if(attribution.length()>1024)throw new IOException("Attribution exceeds overview packet limit; source withheld rather than dropping credit");view.attribution=XFEarthMapLayers.clean(attribution,1024);
        final Set<String> seen=new HashSet<String>();final int[] records={0},vertices={0},omitted={0},malformed={0};
        try{for(int rz=XFEarthGeoImporter.region(z0);rz<=XFEarthGeoImporter.region(z1);rz++)for(int rx=XFEarthGeoImporter.region(x0);rx<=XFEarthGeoImporter.region(x1);rx++){
            roads.dataset.readRegion(rx,rz,new XFEarthGeoDataset.Visitor(){public void accept(XFEarthGeoDataset.Feature f)throws IOException{
                XFEarthFeatureSource.check();if(++records[0]>8192)throw new Stop();
                String base=f.sourceType+":"+f.sourceId+":"+f.sourceRecord;if(!seen.add(base))return;
                if(f.bounds.length!=4||f.bounds[2]<x0||f.bounds[0]>x1+1.0||f.bounds[3]<z0||f.bounds[1]>z1+1.0)return;
                try{
                    int rank=roads.rankFor(f);String roadKind=XFEarthRoadSource.kindFor(f);
                    for(int partIndex=0;partIndex<f.geometry.size();partIndex++){
                        XFEarthGeoGeometry.Part part=f.geometry.get(partIndex);String partKey=base+":"+partIndex;
                        for(int layer=0;layer<5;layer++){
                            if((mask&(1<<layer))==0||!category(f,layer))continue;
                            if(layer==XFEarthMapLayers.ROADS&&!"LineString".equals(part.type))continue;
                            if(layer==XFEarthMapLayers.ROADS&&(rank==0||rank>(lod>=64?2:lod>=16?4:lod>=4?5:8))){omitted[0]++;continue;}
                            double[][] paths=paths(part,x0,z0,x1+1.0,z1+1.0,lod);
                            if(paths.length==0)continue;int type="Area".equals(part.type)?XFEarthMapLayers.AREA:"Point".equals(part.type)?XFEarthMapLayers.POINT:XFEarthMapLayers.LINE;
                            if(type==XFEarthMapLayers.AREA&&layer!=XFEarthMapLayers.ADMIN&&f.bounds[2]-f.bounds[0]<lod&&f.bounds[3]-f.bounds[1]<lod){omitted[0]++;continue;}
                            String category=layer==0?"highway="+tag(f.tags,"highway")+" / "+roadKind:layer==1?"landuse="+tag(f.tags,"landuse"):layer==2?"building="+(tag(f.tags,"building").isEmpty()?tag(f.tags,"building:part"):tag(f.tags,"building")):
                                layer==3?business(f.tags):"admin_level="+tag(f.tags,"admin_level");
                            String detail=layer==4?"Informational boundary; no faction ownership":layer==2?"Source footprint; Minecraft appearance is generic":layer==3?(type==0?"Source business point; no exact architecture":"Source business geometry; generic appearance"):"Imported source geometry; coverage unverified";
                            Feature feature=new Feature(XFEarthGeoJson.hash(partKey+":"+layer),tag(f.tags,"name"),category,f.sourceType+":"+f.sourceId,detail,layer,type,color(layer),0,paths);
                            if(view.features.size()>=96||vertices[0]+feature.vertices()>3072)throw new Stop();view.features.add(feature);view.generationKeys.put(feature.key,partKey);vertices[0]+=feature.vertices();
                        }
                    }
                }catch(Stop stop){throw stop;}catch(RuntimeException invalid){malformed[0]++;}
            }});
        }}catch(Stop bounded){view.truncated=true;}
        Collections.sort(view.features,new Comparator<Feature>(){public int compare(Feature a,Feature b){int layer=Integer.compare(a.layer,b.layer);return layer!=0?layer:a.key.compareTo(b.key);}});
        view.notice="© OpenStreetMap contributors / ODbL; "+XFEarthMapLayers.clean(roads.dataset.manifest.get("attribution").getAsString(),96)
            +"; source coverage unverified"+(view.truncated?"; bounded view truncated":"")+(omitted[0]>0?"; low-zoom detail omitted":"")+(malformed[0]>0?"; malformed/oversize parts skipped":"");return view;
    }
    private static boolean category(XFEarthGeoDataset.Feature f,int layer){return f.categories.contains(layer==0?"road":layer==1?"landuse":layer==2?"building":layer==3?"business":"administrative")
        &&(layer!=1||Arrays.asList("farmland","meadow","farmyard","orchard","vineyard").contains(tag(f.tags,"landuse")));}
    private static int color(int layer){return layer==0?0xffcc55:layer==1?0x55cc66:layer==2?0xddaa77:layer==3?0xee77dd:0xb999ff;}
    private static String tag(JsonObject tags,String key){return tags!=null&&tags.has(key)&&tags.get(key).isJsonPrimitive()?tags.get(key).getAsString():"";}
    private static String business(JsonObject tags){for(String key:new String[]{"shop","office","craft","amenity","tourism"})if(!tag(tags,key).isEmpty())return key+"="+tag(tags,key);return "business (category unspecified)";}
    private static double[][] paths(XFEarthGeoGeometry.Part part,double x0,double z0,double x1,double z1,int lod)throws IOException{
        List<double[]> paths=new ArrayList<double[]>();
        if("Point".equals(part.type)){double[] p=part.points[0];if(p[0]>=x0&&p[0]<x1&&p[1]>=z0&&p[1]<z1)paths.add(p.clone());}
        else if("Area".equals(part.type)){
            Area area=XFEarthGeoGeometry.area(part);area.intersect(new Area(new Rectangle2D.Double(x0,z0,x1-x0,z1-z0)));
            PathIterator iterator=area.getPathIterator(null);List<Double> values=null;double[] p=new double[6];
            for(;!iterator.isDone();iterator.next()){
                XFEarthFeatureSource.check();
                if(iterator.currentSegment(p)==PathIterator.SEG_MOVETO){if(values!=null&&values.size()>=6)paths.add(array(values));values=new ArrayList<Double>();}
                int kind=iterator.currentSegment(p);if(kind==PathIterator.SEG_MOVETO||kind==PathIterator.SEG_LINETO){values.add(p[0]);values.add(p[1]);}
            }if(values!=null&&values.size()>=6)paths.add(array(values));
        }else if("LineString".equals(part.type)){
            List<Double> values=new ArrayList<Double>();double lastX=Double.NaN,lastZ=Double.NaN;
            for(int i=1;i<part.points.length;i++){
                XFEarthFeatureSource.check();
                double[] clipped=XFEarthRoadSource.clip(new XFEarthRoadSource.Segment(part.points[i-1],part.points[i],1,1,"","",false),x0,z0,x1,z1);if(clipped==null){if(values.size()>=4)paths.add(array(values));values.clear();lastX=lastZ=Double.NaN;continue;}
                if(values.isEmpty()||Math.abs(lastX-clipped[0])>.000001||Math.abs(lastZ-clipped[1])>.000001){if(values.size()>=4)paths.add(array(values));values.clear();values.add(clipped[0]);values.add(clipped[1]);}
                // Keep endpoints and simplify only near-collinear display vertices, never generated block routes.
                if(values.size()>=4){int n=values.size();double ax=values.get(n-4),az=values.get(n-3),bx=values.get(n-2),bz=values.get(n-1);
                    double cross=Math.abs((bx-ax)*(clipped[3]-az)-(bz-az)*(clipped[2]-ax));double length=Math.hypot(clipped[2]-ax,clipped[3]-az);
                    double along=(bx-ax)*(clipped[2]-ax)+(bz-az)*(clipped[3]-az);
                    if(length>0&&along>=0&&along<=length*length&&cross/length<lod*.25){values.remove(n-1);values.remove(n-2);}}
                values.add(clipped[2]);values.add(clipped[3]);lastX=clipped[2];lastZ=clipped[3];
            }if(values.size()>=4)paths.add(array(values));
        }return paths.toArray(new double[paths.size()][]);
    }
    private static double[] array(List<Double> values){double[] result=new double[values.size()];for(int i=0;i<result.length;i++)result[i]=values.get(i);return result;}
}
