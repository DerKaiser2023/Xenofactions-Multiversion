package com.hfr.world.earth.roads;

import java.security.*;
import java.util.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.world.*;

/** Only native newly prepared terrain receives a baseline. Historical chunks are never assumed pristine. */
public final class XFEarthRoadState {
    private static final String TAG="XFEarthRoads";
    private static final Map<Chunk,Stamp> states=new WeakHashMap<Chunk,Stamp>();
    private static final byte[] ZERO4096=new byte[4096],ZERO2048=new byte[2048];
    public static final class Stamp {
        public String baseline="",profile="",completed="",pending="";
        public boolean edited;
        public Stamp copy(){Stamp s=new Stamp();s.baseline=baseline;s.profile=profile;s.completed=completed;s.pending=pending;s.edited=edited;return s;}
    }
    public static Stamp get(Chunk chunk){return states.get(chunk);}
    public static void prepared(Chunk chunk,String baseline,String profile,String completed){Stamp stamp=new Stamp();stamp.baseline=baseline;stamp.profile=profile;stamp.completed=completed;states.put(chunk,stamp);}
    public static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public static void section(MessageDigest digest,ExtendedBlockStorage section){
        digest.update(section==null?ZERO4096:section.getBlockLSBArray());
        digest.update(section==null||section.getBlockMSBArray()==null?ZERO2048:section.getBlockMSBArray().data);
        digest.update(section==null?ZERO2048:section.getMetadataArray().data);
    }
    public static String finish(MessageDigest digest){StringBuilder b=new StringBuilder();for(byte n:digest.digest())b.append(String.format(Locale.ROOT,"%02x",n&255));return b.toString();}
    @SubscribeEvent public void save(ChunkDataEvent.Save event){
        if(event.world.isRemote)return;Stamp stamp=get(event.getChunk());if(stamp==null)return;
        NBTTagCompound n=new NBTTagCompound();n.setInteger("version",1);n.setString("baseline",stamp.baseline);n.setString("profile",stamp.profile);
        n.setString("completed",stamp.completed);n.setString("pending",stamp.pending);n.setBoolean("edited",stamp.edited);event.getData().setTag(TAG,n);
    }
    @SubscribeEvent public void load(ChunkDataEvent.Load event){
        if(event.world.isRemote||!event.getData().hasKey(TAG,10))return;NBTTagCompound n=event.getData().getCompoundTag(TAG);
        if(n.getInteger("version")!=1)return;Stamp s=new Stamp();s.baseline=n.getString("baseline");s.profile=n.getString("profile");s.completed=n.getString("completed");s.pending=n.getString("pending");s.edited=n.getBoolean("edited");
        if(s.baseline.matches("[0-9a-f]{64}")&&s.profile.matches("[0-9a-f]{64}"))states.put(event.getChunk(),s);
    }
    private static void edited(BlockEvent event){if(!event.world.isRemote&&event.world.getChunkProvider().chunkExists(event.x>>4,event.z>>4)){
        Chunk chunk=event.world.getChunkFromChunkCoords(event.x>>4,event.z>>4);Stamp s=get(chunk);if(s!=null){s.edited=true;chunk.setChunkModified();}}}
    @SubscribeEvent public void place(BlockEvent.PlaceEvent event){if(!event.isCanceled())edited(event);}
    @SubscribeEvent public void broken(BlockEvent.BreakEvent event){if(!event.isCanceled())edited(event);}
    public static void clear(){states.clear();}
}
