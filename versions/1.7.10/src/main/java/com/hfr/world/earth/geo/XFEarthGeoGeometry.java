package com.hfr.world.earth.geo;

import com.google.gson.*;
import com.hfr.world.earth.XFEarthProfile;
import java.awt.geom.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Pure two-dimensional projection/clipping. Area contours use explicit even-odd hole semantics. */
public final class XFEarthGeoGeometry {
    public static final int VERSION=1;
    public static final class Part {
        public String type;
        public double[][] points;
        public double[][][] rings;
        public String fillRule;
        public double[] interiorPoint;
    }
    public static final class Unsupported extends IllegalArgumentException {
        Unsupported(String message){super(message);}
    }
    private final XFEarthProfile profile;
    private final XFEarthGeoImporter.Control control;
    private final int limit;
    private int inputVertices,outputVertices;
    private final double minX,maxX,minZ,maxZ,period;
    private final List<Part> parts=new ArrayList<Part>();
    private XFEarthGeoGeometry(XFEarthProfile profile,int limit,XFEarthGeoImporter.Control control) {
        this.profile=profile;this.limit=limit;this.control=control;
        minX=profile.getBounds().minimumX;maxX=(double)profile.getBounds().maximumX+1;
        minZ=profile.getBounds().minimumZ;maxZ=(double)profile.getBounds().maximumZ+1;
        period=profile.blockX(360)-profile.blockX(0);
    }
    public static void validateProfile(XFEarthProfile profile) {
        if(!profile.hasGeographicConversion())throw new IllegalArgumentException("Geographic import requires the shared equirectangular projection");
        double span=profile.longitude((double)profile.getBounds().maximumX+1)-profile.longitude(profile.getBounds().minimumX);
        if(span>360.00000001||profile.latitude(profile.getBounds().minimumZ)>90.00000001
                ||profile.latitude((double)profile.getBounds().maximumZ+1)<-90.00000001)
            throw new IllegalArgumentException("Earth profile extends beyond one geographic world");
    }
    static List<Part> project(JsonObject geometry,XFEarthProfile profile,int limit,XFEarthGeoImporter.Control control) throws IOException {
        XFEarthGeoGeometry projector=new XFEarthGeoGeometry(profile,limit,control);projector.geometry(geometry,0);return projector.parts;
    }
    private void geometry(JsonObject geometry,int depth) throws IOException {
        control.check();if(depth>8)throw new Unsupported("GeometryCollection nesting exceeds eight levels");
        String type=XFEarthGeoJson.text(geometry,"type");
        if("GeometryCollection".equals(type)) {
            for(JsonElement child:array(geometry.get("geometries")))geometry(child.getAsJsonObject(),depth+1);return;
        }
        JsonArray coordinates=array(geometry.get("coordinates"));
        if("Point".equals(type))point(position(coordinates));
        else if("MultiPoint".equals(type)){for(JsonElement p:coordinates)point(position(array(p)));}
        else if("LineString".equals(type))line(path(coordinates,false));
        else if("MultiLineString".equals(type)){for(JsonElement line:coordinates)line(path(array(line),false));}
        else if("Polygon".equals(type))polygon(coordinates);
        else if("MultiPolygon".equals(type)){for(JsonElement polygon:coordinates)polygon(array(polygon));}
        else throw new Unsupported("Unsupported geometry type: "+type);
    }
    private static JsonArray array(JsonElement value) {
        if(value==null||!value.isJsonArray())throw new IllegalArgumentException("Expected coordinate array");return value.getAsJsonArray();
    }
    private double[] position(JsonArray coordinate) throws IOException {
        if(coordinate.size()!=2)throw new Unsupported("Only two-dimensional longitude/latitude positions are supported");
        for(JsonElement value:coordinate)if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Coordinate is not a JSON number");
        if(++inputVertices>limit)throw new Unsupported("Feature vertex limit exceeded");if((inputVertices&127)==0)control.check();
        double lon=coordinate.get(0).getAsDouble(),lat=coordinate.get(1).getAsDouble();
        if(!finite(lon)||!finite(lat)||lon<-180||lon>180||lat<-90||lat>90)throw new IllegalArgumentException("Invalid longitude/latitude");
        return new double[]{lon,lat};
    }
    private double[][] path(JsonArray coordinates,boolean ring) throws IOException {
        if(coordinates.size()<(ring?4:2))throw new IllegalArgumentException("Insufficient path positions");
        double[][] result=new double[coordinates.size()][];double previous=0,firstLon=0,firstLat=0;
        for(int i=0;i<result.length;i++) {
            double[] point=position(array(coordinates.get(i)));double lon=point[0];
            if(i==0){firstLon=lon;firstLat=point[1];}else{while(lon-previous>180)lon-=360;while(lon-previous<-180)lon+=360;}
            previous=lon;result[i]=new double[]{profile.blockX(lon),profile.blockZ(point[1])};
        }
        if(ring&&(Math.abs(previous-firstLon)>0.000000001||firstLat!=coordinates.get(coordinates.size()-1).getAsJsonArray().get(1).getAsDouble()))
            throw new Unsupported("Ring is not closed or winds around the entire world");
        double[] bounds=bounds(result);
        if(bounds[2]-bounds[0]>period+0.00000001)throw new Unsupported("Path spans more than one longitude period");
        return result;
    }
    private void point(double[] geographic) {
        double x=profile.blockX(geographic[0]),z=profile.blockZ(geographic[1]);
        if(profile.containsMapPosition(x,z)){Part part=new Part();part.type="Point";part.points=new double[][]{{x,z}};add(part);}
    }
    private int firstShift(double maximum){return (int)Math.ceil((minX-maximum)/period);}
    private int lastShift(double minimum){return (int)Math.floor((maxX-minimum)/period);}
    private void line(double[][] points) throws IOException {
        double[] bounds=bounds(points);
        for(int shift=firstShift(bounds[2]);shift<=lastShift(bounds[0]);shift++) {
            control.check();List<double[]> current=new ArrayList<double[]>();
            for(int i=1;i<points.length;i++) {
                double[] a={points[i-1][0]+shift*period,points[i-1][1]},b={points[i][0]+shift*period,points[i][1]};
                double[][] clipped=clip(a,b);
                if(clipped==null){flushLine(current);continue;}
                if(!current.isEmpty()&&!same(current.get(current.size()-1),clipped[0]))flushLine(current);
                if(current.isEmpty())current.add(clipped[0]);
                if(!same(current.get(current.size()-1),clipped[1]))current.add(clipped[1]);
            }
            flushLine(current);
        }
    }
    private void flushLine(List<double[]> points) {
        if(points.size()>1){Part part=new Part();part.type="LineString";part.points=points.toArray(new double[points.size()][]);add(part);}points.clear();
    }
    private double[][] clip(double[] a,double[] b) {
        double dx=b[0]-a[0],dz=b[1]-a[1],low=0,high=1;
        double[] p={-dx,dx,-dz,dz},q={a[0]-minX,Math.nextAfter(maxX,minX)-a[0],a[1]-minZ,Math.nextAfter(maxZ,minZ)-a[1]};
        for(int i=0;i<4;i++) {
            if(p[i]==0){if(q[i]<0)return null;continue;}
            double t=q[i]/p[i];if(p[i]<0){if(t>high)return null;low=Math.max(low,t);}else{if(t<low)return null;high=Math.min(high,t);}
        }
        return new double[][]{{a[0]+low*dx,a[1]+low*dz},{a[0]+high*dx,a[1]+high*dz}};
    }
    private void polygon(JsonArray rings) throws IOException {
        if(rings.size()==0)throw new IllegalArgumentException("Polygon has no exterior ring");
        double[][] exterior=path(array(rings.get(0)),true);Area area=ringArea(exterior);
        double[] extBounds=bounds(exterior);Area holes=new Area();
        for(int i=1;i<rings.size();i++) {
            double[][] inner=path(array(rings.get(i)),true);double[] innerBounds=bounds(inner);
            double shift=Math.rint(((extBounds[0]+extBounds[2])-(innerBounds[0]+innerBounds[2]))/(2*period))*period;
            Area hole=ringArea(inner);hole.transform(AffineTransform.getTranslateInstance(shift,0));
            Area outside=new Area(hole);outside.subtract(area);if(!outside.isEmpty())throw new IllegalArgumentException("Interior ring lies outside its exterior");
            Area overlap=new Area(hole);overlap.intersect(holes);if(!overlap.isEmpty())throw new IllegalArgumentException("Overlapping interior rings");holes.add(hole);
        }
        area.subtract(holes);
        for(int shift=firstShift(extBounds[2]);shift<=lastShift(extBounds[0]);shift++) {
            control.check();Area clipped=new Area(area);clipped.transform(AffineTransform.getTranslateInstance(shift*period,0));
            clipped.intersect(new Area(new Rectangle2D.Double(minX,minZ,maxX-minX,maxZ-minZ)));
            if(clipped.isEmpty())continue;
            Part part=new Part();part.type="Area";part.fillRule="even-odd";part.rings=contours(clipped);
            part.interiorPoint=interior(clipped,part.rings);add(part);
        }
    }
    private double[][][] contours(Area area) throws IOException {
        List<double[][]> rings=new ArrayList<double[][]>();List<double[]> ring=null;double[] point=new double[6];
        for(PathIterator path=area.getPathIterator(null);!path.isDone();path.next()) {
            control.check();int type=path.currentSegment(point);
            if(type==PathIterator.SEG_MOVETO){ring=new ArrayList<double[]>();ring.add(new double[]{point[0],point[1]});}
            else if(type==PathIterator.SEG_LINETO)ring.add(new double[]{point[0],point[1]});
            else if(type==PathIterator.SEG_CLOSE){ring.add(ring.get(0).clone());rings.add(ring.toArray(new double[ring.size()][]));ring=null;}
            else throw new Unsupported("Curved area contour");
            if(++outputVertices>limit)throw new Unsupported("Clipped geometry vertex limit exceeded");
        }
        return rings.toArray(new double[rings.size()][][]);
    }
    private void add(Part part) {
        if(part.points!=null){outputVertices+=part.points.length;if(outputVertices>limit)throw new Unsupported("Projected geometry vertex limit exceeded");}
        parts.add(part);
    }
    public static Area area(Part part) {
        Path2D.Double path=new Path2D.Double(Path2D.WIND_EVEN_ODD);
        for(double[][] ring:part.rings){path.moveTo(ring[0][0],ring[0][1]);for(int i=1;i<ring.length;i++)path.lineTo(ring[i][0],ring[i][1]);path.closePath();}
        return new Area(path);
    }
    private static Path2D.Double pathShape(double[][] points) {
        Path2D.Double path=new Path2D.Double();path.moveTo(points[0][0],points[0][1]);for(int i=1;i<points.length;i++)path.lineTo(points[i][0],points[i][1]);path.closePath();return path;
    }
    private Area ringArea(double[][] ring) throws IOException {
        control.check();Area area=new Area(pathShape(ring));
        if(area.isEmpty()||!area.isSingular())throw new IllegalArgumentException("Empty or self-intersecting polygon ring");
        double original=0,originX=ring[0][0],originZ=ring[0][1];
        for(int i=1;i<ring.length;i++)original+=(ring[i-1][0]-originX)*(ring[i][1]-originZ)-(ring[i][0]-originX)*(ring[i-1][1]-originZ);
        double normalized=0,firstX=0,firstZ=0,lastX=0,lastZ=0;double[] point=new double[6];int count=0;
        for(PathIterator iterator=area.getPathIterator(null);!iterator.isDone();iterator.next()) {
            if(++count>limit)throw new Unsupported("Normalized polygon vertex limit exceeded");
            int type=iterator.currentSegment(point);
            if(type==PathIterator.SEG_MOVETO){firstX=lastX=point[0]-originX;firstZ=lastZ=point[1]-originZ;}
            else if(type==PathIterator.SEG_LINETO){double x=point[0]-originX,z=point[1]-originZ;normalized+=lastX*z-x*lastZ;lastX=x;lastZ=z;}
            else if(type==PathIterator.SEG_CLOSE)normalized+=lastX*firstZ-firstX*lastZ;
        }
        if(Math.abs(Math.abs(original)-Math.abs(normalized))>Math.max(Math.abs(normalized)*0.00000001,0.000000000001))
            throw new IllegalArgumentException("Polygon ring changes filled area during normalization");
        return area;
    }
    private static double[] interior(Area area,double[][][] rings) {
        Rectangle2D box=area.getBounds2D();if(area.contains(box.getCenterX(),box.getCenterY()))return new double[]{box.getCenterX(),box.getCenterY()};
        for(double[][] ring:rings)for(int i=1;i<ring.length;i++) {
            double dx=ring[i][0]-ring[i-1][0],dz=ring[i][1]-ring[i-1][1],length=Math.hypot(dx,dz);if(length==0)continue;
            double x=(ring[i][0]+ring[i-1][0])*0.5,z=(ring[i][1]+ring[i-1][1])*0.5;
            double epsilon=Math.max(Math.ulp(Math.max(Math.abs(x),Math.abs(z)))*4,Math.min(length*0.000001,0.000001));
            for(int sign:new int[]{-1,1}){double px=x+sign*dz/length*epsilon,pz=z-sign*dx/length*epsilon;if(area.contains(px,pz))return new double[]{px,pz};}
        }
        return null;
    }
    public static double[] bounds(List<Part> parts) {
        double[] result={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(Part part:parts){if(part.points!=null)include(result,part.points);if(part.rings!=null)for(double[][] ring:part.rings)include(result,ring);}return result;
    }
    private static double[] bounds(double[][] points){double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};include(bounds,points);return bounds;}
    private static void include(double[] bounds,double[][] points){for(double[] point:points){bounds[0]=Math.min(bounds[0],point[0]);bounds[1]=Math.min(bounds[1],point[1]);bounds[2]=Math.max(bounds[2],point[0]);bounds[3]=Math.max(bounds[3],point[1]);}}
    private static boolean same(double[] a,double[] b){return Math.abs(a[0]-b[0])<0.000000001&&Math.abs(a[1]-b[1])<0.000000001;}
    private static boolean finite(double value){return !Double.isNaN(value)&&!Double.isInfinite(value);}
}
