package com.hfr.world.earth.roads;

import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.effect.EarthRoadSnapshotPacket;
import com.hfr.tdm.TDMServerTaskQueue;
import com.hfr.world.earth.*;
import com.hfr.world.earth.geo.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

/** Optional Map road layer from the same filtered geometry; this is source data, not surveyed placed blocks. */
public final class XFEarthRoadMap {
    private static ThreadPoolExecutor executor;
    private static final Map<UUID,Long> cooldown=new HashMap<UUID,Long>();
    private static final Set<UUID> pending=new HashSet<UUID>();
    private static long session;
    private XFEarthRoadMap(){}
    public static void request(final EntityPlayerMP player,final int token,final int dimension,final int x0,final int z0,final int x1,final int z1){
        final World world=player.worldObj;final UUID uuid=player.getUniqueID();final long generation=session;
        if(dimension!=0||world.provider.dimensionId!=dimension||player.isDead)return;
        long now=world.getTotalWorldTime();Long last=cooldown.get(uuid);if(pending.contains(uuid)||last!=null&&now-last<40){sendNotice(player,token,dimension,"Road layer busy; retry shortly");return;}cooldown.put(uuid,now);
        final XFEarthProfile profile=XFEarthProfileLoader.forWorld(world);if(profile==null)return;
        if(x1<x0||z1<z0||!profile.containsMapPosition(x0,z0)||!profile.containsMapPosition(x1,z1)
                ||((long)XFEarthGeoImporter.region(x1)-XFEarthGeoImporter.region(x0)+1)*((long)XFEarthGeoImporter.region(z1)-XFEarthGeoImporter.region(z0)+1)>16)return;
        final File directory;final XFEarthRoadSource.Policy policy=new XFEarthRoadSource.Policy();
        try{directory=XFEarthRoadSource.configuredDirectory();}catch(IOException e){sendNotice(player,token,dimension,"Road layer unavailable: no dataset selected");return;}
        final XFEarthFeatureSource.Policy featurePolicy=new XFEarthFeatureSource.Policy();
        final Set<String> recorded=new HashSet<String>();int scanned=0,selected=0;
        if(world instanceof net.minecraft.world.WorldServer)for(Object object:((net.minecraft.world.WorldServer)world).theChunkProviderServer.func_152380_a()){
            if(++scanned>4096||selected>=128||recorded.size()>=8192)break;net.minecraft.world.chunk.Chunk chunk=(net.minecraft.world.chunk.Chunk)object;
            if(chunk.xPosition*16>x1||chunk.xPosition*16+15<x0||chunk.zPosition*16>z1||chunk.zPosition*16+15<z0)continue;selected++;
            XFEarthRoadState.Stamp stamp=XFEarthRoadState.get(chunk);if(stamp==null||stamp.featuresCompleted.isEmpty())continue;
            for(String text:stamp.featureInfo){int end=text.indexOf('|');if(end>0&&recorded.size()<8192)recorded.add(stamp.featuresCompleted+":"+text.substring(0,end));}
        }
        if(executor==null)executor=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(4),new ThreadFactory(){public Thread newThread(Runnable r){Thread t=new Thread(r,"XF Map road geometry");t.setDaemon(true);return t;}});
        pending.add(uuid);
        try{executor.execute(new Runnable(){public void run(){
            final List<EarthRoadSnapshotPacket> packets=new ArrayList<EarthRoadSnapshotPacket>();
            try{
                XFEarthRoadSource roads=new XFEarthRoadSource(directory,profile,policy);List<XFEarthRoadSource.Segment> segments=roads.query(x0,z0,x1,z1,16);
                List<double[]> featureLines=new ArrayList<double[]>();List<Byte> featureStyles=new ArrayList<Byte>();boolean featureFailure=false;
                try{
                    XFEarthFeatureSource features=new XFEarthFeatureSource(roads,featurePolicy);
                    outer:for(XFEarthFeatureSource.Feature feature:features.query(x0-1,z0-1,x1+1,z1+1,25)){
                        int category=feature.structure?"business".equals(feature.kind)?50:40:30;
                        int style=recorded.contains(features.identity+":"+feature.key)?category+30:category+(feature.representable&&featurePolicy.enabled?1:0);
                        if(feature.point){featureLines.add(new double[]{feature.x,feature.z,feature.x,feature.z});featureStyles.add((byte)style);}
                        else{java.awt.geom.PathIterator path=feature.area.getPathIterator(null);double[] position=new double[6];double firstX=0,firstZ=0,lastX=0,lastZ=0;
                            for(;!path.isDone();path.next()){
                                XFEarthFeatureSource.check();int type=path.currentSegment(position);
                                if(type==java.awt.geom.PathIterator.SEG_MOVETO){firstX=lastX=position[0];firstZ=lastZ=position[1];continue;}
                                double bx=type==java.awt.geom.PathIterator.SEG_CLOSE?firstX:position[0],bz=type==java.awt.geom.PathIterator.SEG_CLOSE?firstZ:position[1];
                                if(Math.max(lastX,bx)>=x0&&Math.min(lastX,bx)<=x1+1.0&&Math.max(lastZ,bz)>=z0&&Math.min(lastZ,bz)<=z1+1.0){featureLines.add(new double[]{lastX,lastZ,bx,bz});featureStyles.add((byte)style);}
                                lastX=bx;lastZ=bz;if(featureLines.size()>=2048)break outer;
                            }
                        }if(featureLines.size()>=2048)break;
                    }
                }catch(IOException|RuntimeException e){featureFailure=true;featureLines.clear();featureStyles.clear();}
                int roadCount=Math.min(featureLines.isEmpty()?4096:2048,segments.size()),count=roadCount+featureLines.size(),parts=Math.max(1,(count+511)/512);
                String credit=roads.dataset.manifest.get("attribution").getAsString();if(credit.length()>160)credit=credit.substring(0,160)+"…";
                String notice="© OpenStreetMap contributors / ODbL; "+credit+"; source geometry / generic buildings; bright=recorded generated parts; coverage unverified"
                    +(segments.size()>roadCount||featureLines.size()>=2048?"; layer truncated":"")+(featureFailure?"; feature layer unavailable":"");
                for(int part=0;part<parts;part++){int size=Math.min(512,count-part*512);double[] coords=new double[size*4];byte[] styles=new byte[size];
                    for(int i=0;i<size;i++){int index=part*512+i;if(index<roadCount){XFEarthRoadSource.Segment s=segments.get(index);double[] clipped=XFEarthRoadSource.clip(s,x0,z0,x1+1.0,z1+1.0);
                        System.arraycopy(clipped,0,coords,i*4,4);styles[i]=(byte)("surface".equals(s.kind)?s.rank:"bridge".equals(s.kind)?20:"tunnel".equals(s.kind)?21:22);}
                        else{System.arraycopy(featureLines.get(index-roadCount),0,coords,i*4,4);styles[i]=featureStyles.get(index-roadCount);}}
                    packets.add(new EarthRoadSnapshotPacket(token,dimension,part,parts,coords,styles,notice));}
            }catch(Exception e){packets.clear();packets.add(new EarthRoadSnapshotPacket(token,dimension,0,1,new double[0],new byte[0],"Road layer unavailable; source/profile or bounded query failed"));}
            TDMServerTaskQueue.schedule(new Runnable(){public void run(){if(generation!=session)return;pending.remove(uuid);
                if(player.worldObj!=world||player.isDead||player.playerNetServerHandler.netManager==null||!player.playerNetServerHandler.netManager.isChannelOpen())return;
                XFEarthWorkQueue.submit(new XFEarthWorkQueue.Step(){int index;public boolean run(){
                    if(generation!=session||player.worldObj!=world||!player.playerNetServerHandler.netManager.isChannelOpen())return true;
                    PacketDispatcher.wrapper.sendTo(packets.get(index++),player);return index>=packets.size();
                }});
            }});
        }});}catch(RejectedExecutionException full){pending.remove(uuid);sendNotice(player,token,dimension,"Road layer busy; change the view to retry");}
    }
    private static void sendNotice(EntityPlayerMP player,int token,int dimension,String notice){PacketDispatcher.wrapper.sendTo(new EarthRoadSnapshotPacket(token,dimension,0,1,new double[0],new byte[0],notice),player);}
    public static void clear(){session++;if(executor!=null)executor.shutdownNow();executor=null;pending.clear();cooldown.clear();}
    public static void logout(EntityPlayerMP player){cooldown.remove(player.getUniqueID());}
}
