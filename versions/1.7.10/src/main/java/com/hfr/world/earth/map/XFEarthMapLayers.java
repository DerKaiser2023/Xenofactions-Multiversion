package com.hfr.world.earth.map;

import java.util.*;

/** Bounded public map vocabulary shared by the item, packets and optional render adapters. */
public final class XFEarthMapLayers {
    public static final int VERSION=2,ALL=127,ROADS=0,FIELDS=1,BUILDINGS=2,BUSINESSES=3,ADMIN=4,NODES=5;
    public static final int POINT=0,LINE=1,AREA=2,MAX_FEATURES=128,MAX_VERTICES=4096,PACKET_FEATURES=16,PACKET_VERTICES=512;
    public static final String[] LABELS={"Roads","Fields","Buildings","Businesses","Admin","Ownership","Eligibility"};
    private XFEarthMapLayers(){}
    public static final class Feature {
        public final String key,name,category,source,detail;
        public final int layer,type,color,state;
        public final double[][] paths;
        public Feature(String key,String name,String category,String source,String detail,int layer,int type,int color,int state,double[][] paths){
            this.key=clean(key,64);this.name=clean(name,64);this.category=clean(category,48);this.source=clean(source,64);this.detail=clean(detail,96);
            if(layer<0||layer>NODES||type<0||type>AREA||state<0||state>2||paths==null||paths.length<1||paths.length>64)throw new IllegalArgumentException("Invalid map feature");
            int vertices=0;for(double[] path:paths){if(path==null||path.length%2!=0||path.length<(type==POINT?2:type==AREA?6:4))throw new IllegalArgumentException("Invalid map contour");vertices+=path.length/2;
                for(double value:path)if(!Double.isFinite(value)||Math.abs(value)>30000000)throw new IllegalArgumentException("Invalid map position");}
            if(vertices>PACKET_VERTICES)throw new IllegalArgumentException("Map feature exceeds packet geometry budget");
            this.layer=layer;this.type=type;this.color=color&0xffffff;this.state=state;this.paths=paths;
        }
        public int vertices(){int n=0;for(double[] path:paths)n+=path.length/2;return n;}
        public boolean enabled(int mask){return layer==NODES?(mask&96)!=0:(mask&(1<<layer))!=0;}
        public Feature state(int value){return new Feature(key,name,category,source,detail,layer,type,color,value,paths);}
    }
    public static String clean(String value,int limit){if(value==null)return "";StringBuilder text=new StringBuilder();
        for(int i=0;i<value.length()&&text.length()<limit;i++){char c=value.charAt(i);if(c=='§'){if(i+1<value.length())i++;continue;}if(!Character.isISOControl(c))text.append(c);}return text.toString().trim();}
    public static int displayColor(Feature f,boolean eligibility){return f.layer==NODES&&eligibility?f.state==1?0x33dd66:f.state==2?0xff5555:0x33dddd:f.color;}
}
