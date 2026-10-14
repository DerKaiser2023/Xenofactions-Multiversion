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
        if(executor==null)executor=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(4),new ThreadFactory(){public Thread newThread(Runnable r){Thread t=new Thread(r,"XF Map road geometry");t.setDaemon(true);return t;}});
        pending.add(uuid);
        try{executor.execute(new Runnable(){public void run(){
            final List<EarthRoadSnapshotPacket> packets=new ArrayList<EarthRoadSnapshotPacket>();
            try{
                XFEarthRoadSource roads=new XFEarthRoadSource(directory,profile,policy);List<XFEarthRoadSource.Segment> segments=roads.query(x0,z0,x1,z1,16);
                int count=Math.min(4096,segments.size()),parts=Math.max(1,(count+511)/512);
                String credit=roads.dataset.manifest.get("attribution").getAsString();if(credit.length()>160)credit=credit.substring(0,160)+"…";
                String notice="© OpenStreetMap contributors / ODbL; "+credit+"; source roads, coverage unverified"+(segments.size()>count?"; layer truncated":"");
                for(int part=0;part<parts;part++){int size=Math.min(512,count-part*512);double[] coords=new double[size*4];byte[] styles=new byte[size];
                    for(int i=0;i<size;i++){XFEarthRoadSource.Segment s=segments.get(part*512+i);double[] clipped=XFEarthRoadSource.clip(s,x0,z0,x1+1.0,z1+1.0);
                        System.arraycopy(clipped,0,coords,i*4,4);styles[i]=(byte)("surface".equals(s.kind)?s.rank:"bridge".equals(s.kind)?20:"tunnel".equals(s.kind)?21:22);}
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
