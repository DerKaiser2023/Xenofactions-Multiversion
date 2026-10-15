package com.hfr.world.earth.roads;

import com.hfr.clowder.*;
import com.hfr.clowder.ClowderTerritory.*;
import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.effect.EarthRoadSnapshotPacket;
import com.hfr.tdm.TDMServerTaskQueue;
import com.hfr.world.earth.*;
import com.hfr.world.earth.geo.*;
import com.hfr.world.earth.map.XFEarthMapLayers;
import com.hfr.world.earth.map.XFEarthMapLayers.Feature;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/** The shared geographic overview service. Worker caches contain only detached source geometry. */
public final class XFEarthRoadMap {
    private static ThreadPoolExecutor executor;
    private static final Map<UUID,Long> cooldown=new HashMap<UUID,Long>();
    private static final Map<UUID,Future<?>> pending=new HashMap<UUID,Future<?>>();
    private static long session;
    private static LinkedHashMap<String,XFEarthGeoMapSource.View> cache=new LinkedHashMap<String,XFEarthGeoMapSource.View>(8,.75F,true);
    private XFEarthRoadMap(){}
    public static String viewKey(int x0,int z0,int x1,int z1,int lod,int mask){return x0+":"+z0+":"+x1+":"+z1+":"+lod+":"+mask;}
    private static String hash(String text){java.security.MessageDigest d=XFEarthRoadState.digest();d.update(text.getBytes(StandardCharsets.UTF_8));return XFEarthRoadState.finish(d);}
    private static final class Stamp {final int x,z;final String road;Stamp(Chunk c,XFEarthRoadState.Stamp s){x=c.xPosition;z=c.zPosition;road=s.completed;}}
    public static void request(final EntityPlayerMP player,final int token,final int dimension,final int x0,final int z0,final int x1,final int z1,final int lod,final int mask){
        final World world=player.worldObj;final UUID uuid=player.getUniqueID();final long generation=session;
        if(dimension!=0||world.provider.dimensionId!=dimension||world.isRemote||player.isDead||lod<1||lod>65536||mask<0||mask>127)return;
        final XFEarthProfile profile=XFEarthProfileLoader.forWorld(world);if(profile==null)return;
        final String profileId=XFEarthGeoDataset.profileIdentity(profile),view=viewKey(x0,z0,x1,z1,lod,mask);
        String identity;try{identity=ClaimNodeSystem.store(world).worldIdentity().toString();}catch(IllegalStateException unavailable){identity=hash(world.getSaveHandler().getWorldDirectory().getAbsolutePath());}
        final String worldId=identity;
        if(x1<x0||z1<z0||!profile.containsMapPosition(x0,z0)||!profile.containsMapPosition(x1,z1)
            ||((long)XFEarthGeoImporter.region(x1)-XFEarthGeoImporter.region(x0)+1)*((long)XFEarthGeoImporter.region(z1)-XFEarthGeoImporter.region(z0)+1)>16){notice(player,token,dimension,lod,mask,worldId,profileId,view,"Zoom in: geographic overview exceeds 16 regions");return;}
        long now=world.getTotalWorldTime();Long last=cooldown.get(uuid);
        if(pending.containsKey(uuid)||last!=null&&now-last<40){notice(player,token,dimension,lod,mask,worldId,profileId,view,"Map layers busy; retry shortly");return;}cooldown.put(uuid,now);
        final Set<String> recorded=new HashSet<String>();final List<Stamp> stamps=new ArrayList<Stamp>();
        if(world instanceof WorldServer){int scanned=0;for(Object object:((WorldServer)world).theChunkProviderServer.func_152380_a()){
            if(++scanned>4096||stamps.size()>=128||recorded.size()>=8192)break;Chunk c=(Chunk)object;
            if(c.xPosition*16>x1||c.xPosition*16+15<x0||c.zPosition*16>z1||c.zPosition*16+15<z0)continue;
            XFEarthRoadState.Stamp s=XFEarthRoadState.get(c);if(s==null)continue;stamps.add(new Stamp(c,s));
            for(String text:s.featureInfo){int end=text.indexOf('|');if(end>0&&recorded.size()<8192)recorded.add(s.featuresCompleted+":"+text.substring(0,end));}
        }}
        final File save=world.getSaveHandler().getWorldDirectory();
        final XFEarthRoadSource.Policy roadPolicy=new XFEarthRoadSource.Policy();final XFEarthFeatureSource.Policy featurePolicy=new XFEarthFeatureSource.Policy();
        final LinkedHashMap<String,XFEarthGeoMapSource.View> sourceCache=cache;
        if(executor==null)executor=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(4),new ThreadFactory(){public Thread newThread(Runnable task){Thread thread=new Thread(task,"XF geographic overview");thread.setDaemon(true);return thread;}});
        try{Future<?> future=executor.submit(new Runnable(){public void run(){
            XFEarthGeoMapSource.View loaded=new XFEarthGeoMapSource.View();
            if((mask&31)!=0)try{
                XFEarthGeoDataset dataset=XFEarthGeoSelection.open(save,profile,roadPolicy).dataset;
                String key=worldId+":"+profileId+":"+dataset.manifest.get("datasetIdentity").getAsString()+":"+view+":"+roadPolicy.key()+":"+featurePolicy.key();
                loaded=sourceCache.get(key);if(loaded==null){loaded=XFEarthGeoMapSource.read(dataset.directory,profile,roadPolicy,featurePolicy,x0,z0,x1,z1,lod,mask);
                    if(sourceCache.size()>=8)sourceCache.remove(sourceCache.keySet().iterator().next());sourceCache.put(key,loaded);}
            }catch(IOException|RuntimeException unavailable){loaded=new XFEarthGeoMapSource.View();loaded.notice="Geographic data unavailable; /xc earth geo status: "+unavailable.getMessage()+"; not confirmed absence";}
            else loaded.notice="Geographic layers disabled";
            final XFEarthGeoMapSource.View result=loaded;
            final List<Feature> prepared=new ArrayList<Feature>();
            for(Feature f:result.features){int state=recorded.contains(result.featureIdentity+":"+result.generationKeys.get(f.key))?1:0;
                if(f.layer==0&&f.category.contains(" / surface"))for(Stamp stamp:stamps)if(stamp.road.equals(result.roadIdentity)&&intersects(f,stamp.x*16,stamp.z*16,stamp.x*16+16,stamp.z*16+16)){state=2;break;}
                prepared.add(f.state(state));}
            TDMServerTaskQueue.schedule(new Runnable(){public void run(){if(generation!=session)return;pending.remove(uuid);
                if(player.worldObj!=world||player.isDead||!player.playerNetServerHandler.netManager.isChannelOpen())return;
                XFEarthWorkQueue.submit(new XFEarthWorkQueue.Step(){List<EarthRoadSnapshotPacket> packets;int index;
                    public boolean run(){if(generation!=session||player.worldObj!=world||!player.playerNetServerHandler.netManager.isChannelOpen())return true;
                        if(packets==null){List<Feature> features=new ArrayList<Feature>();String nodeNotice="";
                            try{features.addAll(nodes(player,x0,z0,x1,z1,mask));}catch(IllegalStateException unavailable){nodeNotice="Node data unavailable; ";}
                            features.addAll(prepared);
                            packets=pack(token,dimension,lod,mask,worldId,profileId,result.dataset,view,nodeNotice+result.notice,result.attribution,features);return false;
                        }PacketDispatcher.wrapper.sendTo(packets.get(index++),player);return index>=packets.size();}
                });
            }});
        }});pending.put(uuid,future);}catch(RejectedExecutionException busy){notice(player,token,dimension,lod,mask,worldId,profileId,view,"Map layers busy; retry shortly");}
    }
    private static List<Feature> nodes(EntityPlayerMP viewer,int x0,int z0,int x1,int z1,int mask){
        List<Feature> result=new ArrayList<Feature>();if((mask&64)==0)return result;
        // Query the sampler's inverse X conversion for the displayed territorial interval as well.
        for(ClaimNodeSystem.Snapshot node:ClaimOverlayData.nodeSnapshot(viewer.worldObj,x0+1,z0,x1+1,z1,64)){
            TerritoryMeta meta=ClaimNodeSystem.metadata(node.identity.dimension,node.identity.x,node.identity.z);
            if(meta!=null&&meta.owner!=null&&meta.owner.zone!=Zone.WILDERNESS&&!ClaimOverlayData.maySeeClaim(viewer,node.identity.dimension,meta))continue;
            // Display coordinates follow territorial storage conversion, not the protection sampler's X offset.
            TerritoryCoordinateBounds.Bounds x=TerritoryCoordinateBounds.forCoordinate(node.identity.x),z=TerritoryCoordinateBounds.forCoordinate(node.identity.z);
            double ax=Math.max(x0,x.minInclusive),az=Math.max(z0,z.minInclusive),bx=Math.min(x1+1.0,x.maxExclusive),bz=Math.min(z1+1.0,z.maxExclusive);
            if(ax>=bx||az>=bz)continue;
            String name=node.zone==Zone.FACTION?meta.owner.owner.name+" / "+meta.cityName:node.zone.name();
            int state=node.classification.eligibility==ClaimNodeSystem.Eligibility.ELIGIBLE?1:node.classification.eligibility==ClaimNodeSystem.Eligibility.INELIGIBLE?2:0;
            result.add(new Feature("node:"+node.identity.dimension+":"+node.identity.x+","+node.identity.z,name,node.zone.name(),"Terrain diagnostic","Eligibility: "+node.classification.eligibility+"; ownership is independent",XFEarthMapLayers.TERRAIN,2,0x888888,state,new double[][]{{ax,az,bx,az,bx,bz,ax,bz}}));
        }return result;
    }
    private static boolean intersects(Feature f,int x0,int z0,int x1,int z1){for(double[] path:f.paths)for(int i=2;i<path.length;i+=2)if(XFEarthRoadSource.clip(path[i-2],path[i-1],path[i],path[i+1],x0,z0,x1,z1)!=null)return true;return false;}
    private static List<EarthRoadSnapshotPacket> pack(int token,int dimension,int lod,int mask,String world,String profile,String dataset,String view,String notice,String attribution,List<Feature> features){
        List<List<Feature>> groups=new ArrayList<List<Feature>>();List<Feature> group=new ArrayList<Feature>();int vertices=0;boolean truncated=false;
        for(Feature f:features){if(group.size()>=XFEarthMapLayers.PACKET_FEATURES||vertices+f.vertices()>512){groups.add(group);group=new ArrayList<Feature>();vertices=0;}
            if(groups.size()>=8){truncated=true;break;}group.add(f);vertices+=f.vertices();}
        if(!group.isEmpty()&&groups.size()<8)groups.add(group);if(groups.isEmpty())groups.add(group);
        String text=(truncated?"Bounded overview truncated; ":"")+notice+"; terrain diagnostics max 64; generation evidence is not surveyed blocks";
        List<EarthRoadSnapshotPacket> packets=new ArrayList<EarthRoadSnapshotPacket>();for(int i=0;i<groups.size();i++){EarthRoadSnapshotPacket packet=new EarthRoadSnapshotPacket(token,dimension,i,groups.size(),lod,mask,world,profile,dataset,view,text,groups.get(i));packet.attribution=attribution;packets.add(packet);}return packets;
    }
    private static void notice(EntityPlayerMP player,int token,int dimension,int lod,int mask,String world,String profile,String view,String notice){PacketDispatcher.wrapper.sendTo(new EarthRoadSnapshotPacket(token,dimension,0,1,lod,mask,world,profile,"",view,notice,Collections.<Feature>emptyList()),player);}
    public static void clear(){session++;if(executor!=null)executor.shutdownNow();executor=null;cache=new LinkedHashMap<String,XFEarthGeoMapSource.View>(8,.75F,true);pending.clear();cooldown.clear();}
    public static void selectionChanged(World world){
        clear();XFEarthProfile profile=XFEarthProfileLoader.forWorld(world);if(profile==null)return;
        EarthRoadSnapshotPacket invalidation=new EarthRoadSnapshotPacket(0,0,0,1,1,127,"",XFEarthGeoDataset.profileIdentity(profile),"",EarthRoadSnapshotPacket.SELECTION_INVALIDATED,"Geographic dataset selection changed",Collections.<Feature>emptyList());
        for(Object object:world.playerEntities)if(object instanceof EntityPlayerMP)PacketDispatcher.wrapper.sendTo(invalidation,(EntityPlayerMP)object);
    }
    public static void logout(EntityPlayerMP player){Future<?> future=pending.remove(player.getUniqueID());if(future!=null)future.cancel(true);cooldown.remove(player.getUniqueID());}
}
