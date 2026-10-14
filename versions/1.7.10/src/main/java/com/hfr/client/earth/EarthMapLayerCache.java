package com.hfr.client.earth;

import com.hfr.packet.effect.EarthRoadSnapshotPacket;
import com.hfr.world.earth.map.*;
import com.hfr.world.earth.map.XFEarthMapLayers.Feature;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/** Client-thread assembly, normalized render geometry and bounded GL display-list ownership. */
@SideOnly(Side.CLIENT)
public final class EarthMapLayerCache {
    private static final LinkedHashMap<String,Frame> cache=new LinkedHashMap<String,Frame>(8,.75F,true);
    private static Assembly pending;
    private static Frame current;
    private EarthMapLayerCache(){}
    private static final class Assembly {
        final int token;final World world;final XFEarthMapData data;final String profile,view;final long started=System.nanoTime();
        EarthRoadSnapshotPacket first;final Map<Integer,EarthRoadSnapshotPacket> parts=new HashMap<Integer,EarthRoadSnapshotPacket>();
        Assembly(int token,World world,XFEarthMapData data,String profile,String view){this.token=token;this.world=world;this.data=data;this.profile=profile;this.view=view;}
    }
    public static void begin(int token,World world,XFEarthMapData data,String profile,String view){pending=new Assembly(token,world,data,profile,view);
        if(current!=null&&(current.clientWorld!=world||!current.profile.equals(profile)))current=null;}
    public static boolean waiting(){if(pending!=null&&System.nanoTime()-pending.started>10000000000L)pending=null;return pending!=null;}
    public static void cancel(){pending=null;}
    public static Frame current(){return current!=null&&current.clientWorld==Minecraft.getMinecraft().theWorld&&current.clientWorld.provider.dimensionId==current.dimension?current:null;}
    public static Frame find(World world,String profile,String view){for(Frame f:cache.values())if(f.clientWorld==world&&f.profile.equals(profile)&&f.view.equals(view))return f;return null;}
    public static Frame receive(EarthRoadSnapshotPacket p){
        Assembly a=pending;if(a==null||p.token!=a.token||a.world!=Minecraft.getMinecraft().theWorld||p.dimension!=a.world.provider.dimensionId||!p.profile.equals(a.profile)||!p.view.equals(a.view))return null;
        if(a.first==null)a.first=p;EarthRoadSnapshotPacket first=a.first;
        if(first.parts!=p.parts||first.mask!=p.mask||first.lod!=p.lod||!first.world.equals(p.world)||!first.dataset.equals(p.dataset)||!first.notice.equals(p.notice)||!first.attribution.equals(p.attribution))return null;
        a.parts.put(p.part,p);if(a.parts.size()!=p.parts)return null;List<Feature> features=new ArrayList<Feature>();int vertices=0;
        for(int i=0;i<p.parts;i++){EarthRoadSnapshotPacket part=a.parts.get(i);if(part==null)return null;for(Feature f:part.features){vertices+=f.vertices();features.add(f);}}
        if(vertices>4096||features.size()>XFEarthMapLayers.MAX_FEATURES){pending=null;return null;}
        if(p.notice.contains("busy")){pending=null;return new Frame(a.world,a.data,p,features);}
        String key=p.world+":"+p.profile+":"+p.dataset+":"+XFEarthMapLayers.VERSION+":"+p.view;
        Frame previous=cache.remove(key);Frame frame=new Frame(a.world,a.data,p,features);
        if(previous!=null){if(previous.same(frame)){frame.release();frame=previous;}else previous.release();}
        while(cache.size()>=8){String oldest=cache.keySet().iterator().next();cache.remove(oldest).release();}
        cache.put(key,frame);current=frame;pending=null;return frame;
    }
    public static void clear(){pending=null;current=null;for(Frame frame:cache.values())frame.release();cache.clear();}
    public static final class Frame {
        public final World clientWorld;public final String world,profile,dataset,view,notice,attribution;public final int mask,lod,dimension;
        public final List<Feature> features;
        private final List<double[][]> normalized=new ArrayList<double[][]>();private int lists;private boolean released;
        Frame(World clientWorld,XFEarthMapData data,EarthRoadSnapshotPacket p,List<Feature> features){this.clientWorld=clientWorld;world=p.world;profile=p.profile;dataset=p.dataset;view=p.view;notice=p.notice;attribution=p.attribution;mask=p.mask;lod=p.lod;dimension=p.dimension;this.features=Collections.unmodifiableList(features);
            for(Feature f:features){double[][] paths=new double[f.paths.length][];for(int i=0;i<paths.length;i++){paths[i]=new double[f.paths[i].length];for(int j=0;j<paths[i].length;j+=2){paths[i][j]=data.mapX(f.paths[i][j]);paths[i][j+1]=data.mapZ(f.paths[i][j+1]);}}normalized.add(paths);}}
        private boolean same(Frame other){if(!notice.equals(other.notice)||!attribution.equals(other.attribution)||features.size()!=other.features.size())return false;for(int i=0;i<features.size();i++){Feature a=features.get(i),b=other.features.get(i);
            if(!a.key.equals(b.key)||!a.name.equals(b.name)||!a.category.equals(b.category)||!a.source.equals(b.source)||!a.detail.equals(b.detail)||a.layer!=b.layer||a.type!=b.type||a.color!=b.color||a.state!=b.state||!Arrays.deepEquals(a.paths,b.paths))return false;}return true;}
        private void prepare(){if(released||lists!=0)return;lists=GL11.glGenLists(7);if(lists==0)return;
            for(int layer=0;layer<7;layer++){GL11.glNewList(lists+layer,GL11.GL_COMPILE);
                for(int i=0;i<features.size();i++){Feature f=features.get(i);if(layer<5?f.layer!=layer:f.layer!=5)continue;int color=XFEarthMapLayers.displayColor(f,layer==6);
                    if(f.layer!=5&&f.state>0)color=bright(color);GL11.glColor4f((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,layer==5?.22F:1F);
                    for(double[] path:normalized.get(i)){GL11.glBegin(layer==5?GL11.GL_QUADS:f.type==0?GL11.GL_POINTS:f.type==2?GL11.GL_LINE_LOOP:GL11.GL_LINE_STRIP);
                        for(int j=0;j<path.length;j+=2)GL11.glVertex2d(path[j],path[j+1]);GL11.glEnd();}
                }GL11.glEndList();}}
        private static int bright(int color){return Math.min(255,(color>>16&255)+45)<<16|Math.min(255,(color>>8&255)+45)<<8|Math.min(255,(color&255)+45);}
        public void draw(double x,double z,double width,double height,int enabled){prepare();if(lists==0)return;
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_LINE_BIT|GL11.GL_POINT_BIT|GL11.GL_CURRENT_BIT|GL11.GL_COLOR_BUFFER_BIT);GL11.glPushMatrix();
            try{GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glLineWidth(2);GL11.glPointSize(5);
                GL11.glTranslated(x,z,0);GL11.glScaled(width,height,1);for(int layer=0;layer<7;layer++)if((enabled&(1<<layer))!=0)GL11.glCallList(lists+layer);
            }finally{GL11.glPopMatrix();GL11.glPopAttrib();}}
        public Feature select(double u,double v,double toleranceX,double toleranceZ,int enabled,String previous){
            List<Feature> matches=new ArrayList<Feature>();
            for(int i=features.size()-1;i>=0;i--){Feature f=features.get(i);if(!f.enabled(enabled))continue;boolean inside=false,hit=false;
                for(double[] path:normalized.get(i)){
                    if(f.type==0){hit|=Math.abs(path[0]-u)<=toleranceX&&Math.abs(path[1]-v)<=toleranceZ;continue;}
                    for(int j=0,k=path.length-2;j<path.length;k=j,j+=2){double ax=path[k],az=path[k+1],bx=path[j],bz=path[j+1];
                        if(f.type==2&&(az>v)!=(bz>v)&&u<(bx-ax)*(v-az)/(bz-az)+ax)inside=!inside;
                        if(f.type==1&&j==0)continue;double dx=(bx-ax)/toleranceX,dz=(bz-az)/toleranceZ,px=(u-ax)/toleranceX,pz=(v-az)/toleranceZ;
                        double t=Math.max(0,Math.min(1,(px*dx+pz*dz)/Math.max(.000001,dx*dx+dz*dz)));hit|=Math.hypot(px-t*dx,pz-t*dz)<=1;
                    }
                }if(hit||inside)matches.add(f);
            }
            Collections.sort(matches,new Comparator<Feature>(){public int compare(Feature a,Feature b){return Integer.compare(priority(a.layer),priority(b.layer));}});
            if(matches.isEmpty())return null;if(previous!=null)for(int i=0;i<matches.size();i++)if(matches.get(i).key.equals(previous))return matches.get((i+1)%matches.size());return matches.get(0);
        }
        private static int priority(int layer){return layer==3?0:layer==2?1:layer==0?2:layer==1?3:layer==5?4:5;}
        private void release(){released=true;if(lists!=0){GL11.glDeleteLists(lists,7);lists=0;}}
    }
}
