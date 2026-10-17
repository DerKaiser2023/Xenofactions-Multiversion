package com.hfr.client.wallart;

import com.hfr.client.render.ClientRenderSession;
import com.hfr.client.render.TextureSlots;
import com.hfr.main.MainRegistry;
import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.wallart.WallArtImageRequestPacket;
import com.hfr.wallart.WallArtConstants;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/** Client-thread metadata/GL ownership, bounded network assemblies and worker-only PNG decoding. */
public final class WallArtTextureCache {
    private static final long BUDGET=64L*1024*1024, ASSEMBLY_BUDGET=8L*1024*1024;
    private static final TextureSlots SLOTS=new TextureSlots("wall_art",64);
    private static final LinkedHashMap<String,Entry> CACHE=new LinkedHashMap<String,Entry>(32,.75F,true);
    private static final Map<String,Pending> PENDING=new HashMap<String,Pending>();
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(2),r -> {Thread t=new Thread(r,"XF wall art decoder");t.setDaemon(true);return t;});
    private static final LinkedHashMap<String,Long> RETRY=new LinkedHashMap<String,Long>();
    private static long used, assembled, nextToken;
    private static World world;
    private static volatile boolean resetRequested;
    private WallArtTextureCache() { }
    public static int size(){return CACHE.size();}
    public static int pending(){return PENDING.size();}
    public static ResourceLocation get(String hash) {
        if(!WallArtConstants.validHash(hash)||resetRequested)return null;
        Entry entry=CACHE.get(hash);if(entry!=null)return SLOTS.location(entry.slot);
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.theWorld==null||mc.theWorld!=world||PENDING.containsKey(hash)||PENDING.size()>=8)return null;
        Long next=RETRY.get(hash);if(next!=null&&next>System.currentTimeMillis())return null;
        if(RETRY.size()>=256)RETRY.remove(RETRY.keySet().iterator().next());
        RETRY.put(hash,System.currentTimeMillis()+15000);
        Pending request=new Pending(++nextToken,mc.theWorld.provider.dimensionId);
        PENDING.put(hash,request);
        PacketDispatcher.wrapper.sendToServer(new WallArtImageRequestPacket(hash,request.token,request.dimension));
        return null;
    }
    public static void accept(String hash,long token,int dimension,int index,int total,int full,byte[] bytes) {
        Pending p=PENDING.get(hash);
        if(resetRequested||p==null||p.token!=token||p.dimension!=dimension||p.decoding
            ||world!=Minecraft.getMinecraft().theWorld||p.session!=ClientRenderSession.generation())return;
        if(p.parts==null){p.parts=new byte[total][];p.full=full;}
        if(p.parts.length!=total||p.full!=full||p.parts[index]!=null)return;
        if(assembled+bytes.length>ASSEMBLY_BUDGET||p.received+bytes.length>full){remove(hash,p);return;}
        p.parts[index]=bytes;p.received+=bytes.length;assembled+=bytes.length;p.count++;
        if(p.count!=total)return;
        if(p.received!=full){remove(hash,p);return;}
        final byte[] png=new byte[full];int offset=0;
        for(byte[] part:p.parts){System.arraycopy(part,0,png,offset,part.length);offset+=part.length;}
        assembled-=p.received;p.received=0;p.parts=null;p.decoding=true;
        try {
            WORKER.execute(() -> {
                BufferedImage decoded=null;
                try { decoded=decode(hash,png); }
                catch(Exception failure){MainRegistry.logger.warn("Could not decode wall art "+hash+": "+failure.getMessage());}
                final BufferedImage image=decoded;
                Minecraft.getMinecraft().func_152344_a(() -> {
                    try {
                        if(PENDING.get(hash)!=p||resetRequested||p.session!=ClientRenderSession.generation()
                            ||world!=Minecraft.getMinecraft().theWorld)return;
                        PENDING.remove(hash);
                        if(image!=null)upload(hash,image);
                    } finally {if(image!=null)image.flush();}
                });
            });
        } catch(RejectedExecutionException fullQueue){remove(hash,p);}
    }
    private static BufferedImage decode(String hash,byte[] png)throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(png);
        StringBuilder actual=new StringBuilder(64);for(byte b:digest)actual.append(String.format("%02x",b&255));
        if(!hash.equals(actual.toString()))throw new IOException("Image hash mismatch");
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Unsupported image");
            ImageReader reader=readers.next();
            try {
                reader.setInput(input,true,true);
                if(!"png".equalsIgnoreCase(reader.getFormatName())||reader.getWidth(0)<1||reader.getWidth(0)>640
                    ||reader.getHeight(0)<1||reader.getHeight(0)>640)throw new IOException("Invalid wall image dimensions");
                return reader.read(0);
            } finally {reader.dispose();}
        }
    }
    private static void upload(String hash,BufferedImage image) {
        long size=(long)image.getWidth()*image.getHeight()*4;
        while(used+size>BUDGET||SLOTS.free()<0) {
            String oldest=CACHE.keySet().iterator().next();Entry entry=CACHE.remove(oldest);
            used-=entry.bytes;SLOTS.release(entry.slot);
        }
        int slot=SLOTS.free();SLOTS.upload(slot,image);CACHE.put(hash,new Entry(slot,size));used+=size;
    }
    private static void remove(String hash,Pending p){PENDING.remove(hash);assembled-=p.received;}
    public static void requestClear(){resetRequested=true;}
    public static void tick() {
        World current=Minecraft.getMinecraft().theWorld;
        if(resetRequested||world!=current){clear();world=current;resetRequested=false;}
        long now=System.currentTimeMillis();
        for(Iterator<Pending> it=PENDING.values().iterator();it.hasNext();){Pending p=it.next();if(now>=p.expires){assembled-=p.received;it.remove();}}
    }
    public static void clear() {
        SLOTS.clear();CACHE.clear();PENDING.clear();RETRY.clear();WORKER.getQueue().clear();used=assembled=0;
    }
    private static final class Entry {final int slot;final long bytes;Entry(int slot,long bytes){this.slot=slot;this.bytes=bytes;}}
    private static final class Pending {
        final long token,session=ClientRenderSession.generation(),expires=System.currentTimeMillis()+15000;
        final int dimension;byte[][] parts;int full,received,count;boolean decoding;
        Pending(long token,int dimension){this.token=token;this.dimension=dimension;}
    }
}
