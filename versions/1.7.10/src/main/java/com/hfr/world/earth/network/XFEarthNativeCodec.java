package com.hfr.world.earth.network;

import com.hfr.world.earth.XFEarthProfile;
import com.hfr.world.earth.storage.XFEarthStorageIdentity;
import cpw.mods.fml.common.Loader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.INetHandler;
import net.minecraft.world.World;

/** Reflection boundary: ordinary worlds do not load any CubicChunks or client classes. */
public final class XFEarthNativeCodec {
    private static final String ROOT="com.cardinalstar.cubicchunks.";
    private final Object[] encoders;
    private final Constructor<?> buffer;
    private final Method write, read, process, setHandler;
    private static XFEarthNativeCodec instance;
    private XFEarthNativeCodec() throws Exception {
        Class<?> entry=Class.forName(ROOT+"network.CCPacketEntry");
        Object[] entries=entry.getEnumConstants();
        String[] names={"Cube","Column","UnloadColumn","UnloadCube","CubeBlockChange","HeightMapUpdate","CubeSkyLightUpdates","WorldHeight"};
        if(entries.length!=names.length) throw new IllegalStateException("Unexpected native packet table");
        encoders=new Object[names.length];
        for(int i=0;i<names.length;i++) {
            if(!((Enum<?>)entries[i]).name().equals(names[i])) throw new IllegalStateException("Unexpected native packet ID");
            encoders[i]=entry.getField("encoder").get(entries[i]);
        }
        Class<?> buf=Class.forName(ROOT+"network.CCPacketBuffer"), packet=Class.forName(ROOT+"network.CCPacket");
        Class<?> encoder=Class.forName(ROOT+"network.CCPacketEncoder");
        buffer=buf.getConstructor(ByteBuf.class);
        write=encoder.getMethod("writePacket",buf,packet); read=encoder.getMethod("readPacket",buf);
        process=encoder.getMethod("process",World.class,packet); setHandler=encoder.getMethod("setINetHandler",INetHandler.class,packet);
    }
    public static void requireComponents() {
        if(!Loader.isModLoaded("cubicchunks")||!XFEarthStorageIdentity.CUBIC_VERSION.equals(
                Loader.instance().getIndexedModList().get("cubicchunks").getVersion()))
            throw new IllegalStateException("Earth height requires CubicChunks1710 "+XFEarthStorageIdentity.CUBIC_VERSION+" on both peers");
        if(!Loader.isModLoaded("gtnhlib")||!"0.11.51".equals(Loader.instance().getIndexedModList().get("gtnhlib").getVersion())
                ||!Loader.isModLoaded("RegionLib")||!("v0.1.0-GTNH".equals(Loader.instance().getIndexedModList().get("RegionLib").getVersion())
                ||"0.1.0-GTNH".equals(Loader.instance().getIndexedModList().get("RegionLib").getVersion())))
            throw new IllegalStateException("Earth height requires GTNHLib 0.11.51 and RegionLib v0.1.0-GTNH");
        if(Loader.isModLoaded("chunkapi")||Loader.isModLoaded("endlessids"))
            throw new IllegalStateException("Alternate native block codecs have not been validated for Earth storage");
    }
    public static synchronized XFEarthNativeCodec get() throws Exception {
        if(instance==null) { requireComponents(); instance=new XFEarthNativeCodec(); } return instance;
    }
    public byte[] encode(Object packet) throws Exception {
        int id=((Number)packet.getClass().getMethod("getPacketID").invoke(packet)).intValue(); checkID(id);
        ByteBuf bytes=Unpooled.buffer();
        try {
            bytes.writeByte(id); write.invoke(encoders[id],buffer.newInstance(bytes),packet);
            if(bytes.readableBytes()>XFEarthHeightPacket.MAX_PAYLOAD) throw new IllegalStateException("Oversize native Earth packet");
            byte[] result=new byte[bytes.readableBytes()]; bytes.readBytes(result); return result;
        } finally { bytes.release(); }
    }
    /** Called only on the client game thread, after the session descriptor has been accepted. */
    public void apply(World world, INetHandler handler, byte[] payload, XFEarthProfile profile) throws Exception {
        ByteBuf bytes=Unpooled.wrappedBuffer(payload);
        try {
            int id=bytes.readUnsignedByte(); checkID(id);
            validateWire(bytes.duplicate(),id,profile);
            Object packet=read.invoke(encoders[id],buffer.newInstance(bytes));
            if(bytes.isReadable()) throw new IllegalStateException("Trailing native Earth packet data");
            Object provider=world.getChunkProvider();
            if(id==0||id==4||id==6) {
                Object pos=packet.getClass().getMethod(id==6?"pos":"cubePos").invoke(packet);
                int x=coord(pos,"getX"), y=coord(pos,"getY"), z=coord(pos,"getZ");
                Object column=provider.getClass().getMethod("getLoadedColumn",int.class,int.class).invoke(provider,x,z);
                if(column==null) throw new IllegalStateException("Native cube arrived before its column");
                if(id!=0&&provider.getClass().getMethod("getLoadedCube",int.class,int.class,int.class).invoke(provider,x,y,z)==null)
                    throw new IllegalStateException("Native update arrived before its cube");
            } else if(id==5) {
                net.minecraft.world.ChunkCoordIntPair pos=(net.minecraft.world.ChunkCoordIntPair)packet.getClass().getMethod("chunk").invoke(packet);
                if(provider.getClass().getMethod("getLoadedColumn",int.class,int.class).invoke(provider,pos.chunkXPos,pos.chunkZPos)==null)
                    throw new IllegalStateException("Native heightmap arrived before its column");
            }
            setHandler.invoke(encoders[id],handler,packet); process.invoke(encoders[id],world,packet);
            // The native null-storage light update returns without invalidating the renderer.
            if(id==6) {
                Object pos=packet.getClass().getMethod("pos").invoke(packet);
                int x=coord(pos,"getX")*16,y=coord(pos,"getY")*16,z=coord(pos,"getZ")*16;
                world.markBlockRangeForRenderUpdate(x,y,z,x+15,y+15,z+15);
            }
        } finally { bytes.release(); }
    }
    public static void setBounds(World world,XFEarthProfile profile) throws Exception {
        Class.forName(ROOT+"mixin.api.ICubicWorldInternal").getMethod("setHeightBounds",int.class,int.class)
                .invoke(world,profile.getMinimumY(),profile.getMaximumY()+1);
    }
    public static void requireBounds(World world,XFEarthProfile profile) throws Exception {
        Class<?> api=Class.forName(ROOT+"world.api.IMinMaxHeight");
        if(((Number)api.getMethod("getMinHeight").invoke(world)).intValue()!=profile.getMinimumY()
                ||((Number)api.getMethod("getMaxHeight").invoke(world)).intValue()!=profile.getMaximumY()+1)
            throw new IllegalStateException("Native world bounds disagree with Earth storage identity");
    }
    private static int coord(Object pos,String method) throws Exception { return ((Number)pos.getClass().getMethod(method).invoke(pos)).intValue(); }
    private static void checkID(int id) { if(id<0||id>7) throw new IllegalArgumentException("Unsupported native Earth packet ID"); }
    private static int varInt(ByteBuf bytes) {
        int value=0;
        for(int shift=0;shift<35;shift+=7) {
            int b=bytes.readUnsignedByte(); if(shift==28&&(b&240)!=0) throw new IllegalArgumentException("Invalid native VarInt");
            value|=(b&127)<<shift; if((b&128)==0) return value;
        }
        throw new IllegalArgumentException("Oversize native VarInt");
    }
    private static ByteBuf array(ByteBuf bytes,int maximum) {
        int size=varInt(bytes);
        if(size<0||size>maximum||size>bytes.readableBytes()) throw new IllegalArgumentException("Invalid native byte array size");
        return bytes.readSlice(size);
    }
    private static void intervals(ByteBuf bytes,XFEarthProfile p) {
        int count=varInt(bytes), last=p.getMinimumY()-2;
        if(count<0||count>p.getBlockHeight()/2) throw new IllegalArgumentException("Invalid native opacity interval count");
        for(int i=0;i<count;i++) {
            int low=varInt(bytes), high=varInt(bytes);
            if(low<p.getMinimumY()||high>p.getMaximumY()||low>high||low<=last+1) throw new IllegalArgumentException("Native opacity intervals outside Earth bounds");
            last=high;
        }
    }
    /** Bound counts before invoking native decoders, which otherwise allocate unchecked arrays/lists. */
    private static void validateWire(ByteBuf bytes,int id,XFEarthProfile p) {
        if(id==7) {
            if(bytes.readInt()!=p.getMinimumY()||bytes.readInt()!=p.getMaximumY()+1||bytes.isReadable())
                throw new IllegalArgumentException("Native height update disagrees with Earth session");
            return;
        }
        int cubeX=0,cubeY=0,cubeZ=0;
        if(id==0||id==3||id==4||id==6) {
            cubeX=bytes.readInt(); cubeY=bytes.readInt(); cubeZ=bytes.readInt();
            if(cubeY<(p.getMinimumY()>>4)||cubeY>(p.getMaximumY()>>4)) throw new IllegalArgumentException("Native cube outside Earth Y bounds");
        } else { bytes.readInt(); bytes.readInt(); }
        if(id==0) {
            // Full cube payload, including native block/light arrays and optional 3D biomes.
            array(bytes,65536);
        } else if(id==1) {
            ByteBuf column=array(bytes,XFEarthHeightPacket.MAX_PAYLOAD); column.skipBytes(256);
            for(int i=0;i<256;i++) intervals(column,p);
            // The pinned column encoder uses buffer.array(), so its capacity padding is intentional.
            while(column.isReadable()) if(column.readByte()!=0) throw new IllegalArgumentException("Invalid native column padding");
        } else if(id==4) {
            int count=varInt(bytes); if(count<0||count>4096) throw new IllegalArgumentException("Oversize native block update list");
            for(int i=0;i<count;i++) {
                int x=bytes.readInt(), y=bytes.readInt(), z=bytes.readInt();
                if((x>>4)!=cubeX||(y>>4)!=cubeY||(z>>4)!=cubeZ) throw new IllegalArgumentException("Native block update outside its cube");
                int block=varInt(bytes), metadata=bytes.readUnsignedByte();
                if(block<0||block>4095||net.minecraft.block.Block.getBlockById(block)==null||metadata>15)
                    throw new IllegalArgumentException("Unsupported native block ID or metadata");
            }
        } else if(id==5) {
            ByteBuf mask=array(bytes,32); // BitSet.toByteArray omits unused trailing bytes.
            int cells=0; while(mask.isReadable()) cells+=Integer.bitCount(mask.readUnsignedByte());
            if(varInt(bytes)!=cells) throw new IllegalArgumentException("Invalid native heightmap update count");
            for(int i=0;i<cells;i++) { ByteBuf cell=array(bytes,4096); intervals(cell,p); if(cell.isReadable()) throw new IllegalArgumentException("Trailing native heightmap interval data"); }
        } else if(id==6) {
            boolean full=bytes.readBoolean(), present=bytes.readBoolean();
            if(present) { ByteBuf light=array(bytes,8192); if(full?light.readableBytes()!=2048:(light.readableBytes()&1)!=0) throw new IllegalArgumentException("Invalid native light update"); }
        }
        if(bytes.isReadable()) throw new IllegalArgumentException("Trailing native Earth packet data");
    }
}
