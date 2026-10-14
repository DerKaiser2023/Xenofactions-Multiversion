package com.hfr.world.earth.geo;

import com.hfr.config.XFConfig;
import com.hfr.world.earth.XFEarthProfile;
import java.io.*;
import java.util.*;

/** Bounded, detached road geometry. No terrain/world access or inferred connections. */
public final class XFEarthRoadSource {
    public static final int VERSION=1, MAX_SEGMENTS=32768, MAX_FEATURES=8192;
    public final XFEarthGeoDataset dataset;
    public final XFEarthProfile profile;
    public final Policy policy;
    public final String identity;
    private final LinkedHashMap<String,List<Segment>> cache=new LinkedHashMap<String,List<Segment>>(8,.75F,true);
    public static final class Policy {
        public final int cut,fill,slope,width,coarseScale;
        public Policy(){cut=XFConfig.earthRoadMaxCut;fill=XFConfig.earthRoadMaxFill;slope=XFConfig.earthRoadMaxSlope;
            width=XFConfig.earthRoadMaxWidth;coarseScale=XFConfig.earthRoadCoarseScale;}
        public String key(){return cut+":"+fill+":"+slope+":"+width+":"+coarseScale;}
    }
    public static final class Segment {
        public final double ax,az,bx,bz;
        public final int rank,width;
        public final String key,kind;
        public final boolean gravel;
        Segment(double[] a,double[] b,int rank,int width,String key,String kind,boolean gravel){ax=a[0];az=a[1];bx=b[0];bz=b[1];this.rank=rank;this.width=width;this.key=key;this.kind=kind;this.gravel=gravel;}
    }
    public static final class Cell {
        public final int x,z;
        public final Segment road;
        Cell(int x,int z,Segment road){this.x=x;this.z=z;this.road=road;}
    }
    public XFEarthRoadSource(File directory,XFEarthProfile profile,Policy policy)throws IOException{
        this.profile=profile;this.policy=policy;dataset=XFEarthGeoDataset.open(directory,profile);
        identity=XFEarthGeoJson.hash("XF-ROADS:"+VERSION+":"+dataset.manifest.get("datasetIdentity").getAsString()+":"+policy.key());
    }
    public static File configuredDirectory()throws IOException{
        String directory=XFConfig.earthRoadDatasetDirectory.trim();
        if(directory.isEmpty())throw new IOException("No road dataset selected: configure earthRoadDatasetDirectory to a completed Stage 5 dataset");
        return new File(directory).getCanonicalFile();
    }
    private static String tag(XFEarthGeoDataset.Feature f,String key){return f.tags!=null&&f.tags.has(key)?f.tags.get(key).getAsString():"";}
    private static int rank(String value){
        if(value.endsWith("_link"))value=value.substring(0,value.length()-5);
        String[] classes={"motorway","trunk","primary","secondary","tertiary"};
        for(int i=0;i<classes.length;i++)if(classes[i].equals(value))return i+1;
        if(Arrays.asList("residential","unclassified","living_street").contains(value))return 6;
        if("service".equals(value))return 7;
        return Arrays.asList("track","path","footway","cycleway","pedestrian").contains(value)?8:0;
    }
    private List<Segment> region(final int rx,final int rz)throws IOException{
        String key=rx+","+rz;List<Segment> result=cache.get(key);if(result!=null)return result;
        final List<Segment> segments=new ArrayList<Segment>();final int[] count={0};
        dataset.readRegion(rx,rz,new XFEarthGeoDataset.Visitor(){public void accept(XFEarthGeoDataset.Feature f)throws IOException{
            check();
            if(!f.categories.contains("road"))return;
            int rank=rank(tag(f,"highway"));int scale=profile.getEffectiveScale();
            int highest=scale>=policy.coarseScale*4L?2:scale>=policy.coarseScale?4:scale>=250?5:8;
            if(rank==0||rank>highest||"yes".equals(tag(f,"area"))||!tag(f,"construction").isEmpty()||!tag(f,"proposed").isEmpty())return;
            boolean hasLine=false;for(XFEarthGeoGeometry.Part part:f.geometry)hasLine|="LineString".equals(part.type);if(!hasLine)return;
            if(++count[0]>MAX_FEATURES)throw new IOException("Road region exceeds bounded selected-road limit; use a smaller/simplified extract");
            String bridge=tag(f,"bridge"),tunnel=tag(f,"tunnel"),layer=tag(f,"layer");
            String kind=!bridge.isEmpty()&&!"no".equals(bridge)?"bridge":!tunnel.isEmpty()&&!"no".equals(tunnel)?"tunnel":
                !layer.isEmpty()&&!"0".equals(layer)?"unresolved-layer":"surface";
            double metres=rank<=2?14:rank<=4?8:rank<=6?6:3;
            try{String w=tag(f,"width").trim().replaceFirst("\\s*m$","");if(!w.isEmpty()){double n=Double.parseDouble(w);if(n>0&&n<=100&&Double.isFinite(n))metres=n;}}catch(NumberFormatException ignored){}
            int width=Math.min(policy.width,Math.max(rank<=2?3:1,(int)Math.ceil(metres/Math.max(1,scale))));
            if((width&1)==0)width=Math.max(1,width-1);
            String surface=tag(f,"surface");boolean gravel=Arrays.asList("unpaved","gravel","fine_gravel","ground","dirt","earth","sand").contains(surface)||rank==8;
            String sourceKey=f.sourceType+":"+f.sourceId+":"+f.sourceRecord;
            for(XFEarthGeoGeometry.Part part:f.geometry)if("LineString".equals(part.type)&&part.points!=null)
                for(int i=1;i<part.points.length;i++){
                    double[] a=part.points[i-1],b=part.points[i];
                    if(a.length!=2||b.length!=2||!Double.isFinite(a[0])||!Double.isFinite(a[1])||!Double.isFinite(b[0])||!Double.isFinite(b[1]))throw new IOException("Invalid derived road coordinates");
                    Segment segment=new Segment(a,b,rank,width,sourceKey+":"+i,kind,gravel);
                    if(intersects(segment,rx*512.0-4,rz*512.0-4,(rx+1)*512.0+4,(rz+1)*512.0+4)){
                        if(segments.size()>=MAX_SEGMENTS)throw new IOException("Road region exceeds bounded segment limit");segments.add(segment);
                    }
                }
        }});
        if(cache.size()>=4){Iterator<String> it=cache.keySet().iterator();it.next();it.remove();}cache.put(key,segments);return segments;
    }
    public List<Segment> query(int minX,int minZ,int maxX,int maxZ,int maxRegions)throws IOException{
        int rx0=XFEarthGeoImporter.region(minX),rz0=XFEarthGeoImporter.region(minZ),rx1=XFEarthGeoImporter.region(maxX),rz1=XFEarthGeoImporter.region(maxZ);
        if(((long)rx1-rx0+1)*((long)rz1-rz0+1)>maxRegions)throw new IOException("Road view is too wide; zoom in");
        List<Segment> result=new ArrayList<Segment>();Set<String> seen=new HashSet<String>();
        for(int rz=rz0;rz<=rz1;rz++)for(int rx=rx0;rx<=rx1;rx++)for(Segment segment:region(rx,rz)){
            check();if(intersects(segment,minX,minZ,maxX,maxZ)){
                String key=segment.key+":"+segment.ax+":"+segment.az+":"+segment.bx+":"+segment.bz;
                if(seen.add(key)){if(result.size()>=MAX_SEGMENTS)throw new IOException("Road query exceeds bounded segment limit");result.add(segment);}
            }
        }
        Collections.sort(result,new Comparator<Segment>(){public int compare(Segment a,Segment b){int c=Integer.compare(a.rank,b.rank);return c!=0?c:a.key.compareTo(b.key);}});return result;
    }
    /** A segment touches every intersected unit square, including both cells at diagonal corners. */
    public Map<Long,Cell> cells(int cx,int cz)throws IOException{
        int x0=cx*16-1,z0=cz*16-1,x1=cx*16+16,z1=cz*16+16;Map<Long,Cell> result=new LinkedHashMap<Long,Cell>();
        for(Segment road:query(x0-4,z0-4,x1+4,z1+4,9)){
            if(!"surface".equals(road.kind))continue;int radius=road.width/2;
            int minX=Math.max(x0,(int)Math.floor(Math.min(road.ax,road.bx))-radius),maxX=Math.min(x1,(int)Math.floor(Math.max(road.ax,road.bx))+radius);
            int minZ=Math.max(z0,(int)Math.floor(Math.min(road.az,road.bz))-radius),maxZ=Math.min(z1,(int)Math.floor(Math.max(road.az,road.bz))+radius);
            for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++)if(profile.containsMapPosition(x+.5,z+.5)
                    &&clip(road,x-radius,z-radius,x+1.0+radius,z+1.0+radius)!=null){long key=key(x,z);if(!result.containsKey(key))result.put(key,new Cell(x,z,road));}
        }return result;
    }
    public static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static boolean intersects(Segment s,double x0,double z0,double x1,double z1){return clip(s,x0,z0,x1,z1)!=null;}
    public static double[] clip(Segment s,double x0,double z0,double x1,double z1){
        double dx=s.bx-s.ax,dz=s.bz-s.az,lo=0,hi=1;double[] p={-dx,dx,-dz,dz},q={s.ax-x0,x1-s.ax,s.az-z0,z1-s.az};
        for(int i=0;i<4;i++){if(p[i]==0){if(q[i]<0)return null;continue;}double t=q[i]/p[i];if(p[i]<0){if(t>hi)return null;lo=Math.max(lo,t);}else{if(t<lo)return null;hi=Math.min(hi,t);}}
        return new double[]{s.ax+lo*dx,s.az+lo*dz,s.ax+hi*dx,s.az+hi*dz};
    }
    private static void check()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Road data processing cancelled");}
}
