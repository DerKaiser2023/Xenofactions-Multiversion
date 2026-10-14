package com.hfr.world.earth.network;

import com.google.gson.Gson;
import com.hfr.packet.PacketDispatcher;
import com.hfr.world.earth.XFEarthProfile;
import com.hfr.world.earth.storage.XFEarthStorageIdentity;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Server-thread ownership of extended Earth subscriptions. No world access from ACK callbacks. */
public final class XFEarthHeightSync {
    private static final Map<World,XFEarthProfile> WORLDS=new IdentityHashMap<World,XFEarthProfile>();
    private static final Map<EntityPlayerMP,Session> SESSIONS=new IdentityHashMap<EntityPlayerMP,Session>();
    private static final ConcurrentHashMap<NetHandlerPlayServer,Long> ACKS=new ConcurrentHashMap<NetHandlerPlayServer,Long>();
    private static long nextSession=System.currentTimeMillis(), tick;
    private static final class Session {
        final WorldServer world; final long id=++nextSession, deadline=tick+400;
        boolean accepted;
        Session(WorldServer world) { this.world=world; }
    }
    private XFEarthHeightSync() { }
    public static XFEarthHeightSync events() { return new XFEarthHeightSync(); }
    /** Reached only after the existing gameplay gate, which stays closed until Stage 4. */
    public static void bind(World world,XFEarthProfile profile) throws Exception {
        if(!profile.isExtendedHeight()) return;
        if(world.isRemote||world.provider.dimensionId!=0||!(world instanceof WorldServer))
            throw new IllegalStateException("Extended Earth networking belongs to the server overworld");
        XFEarthStorageIdentity.validateLayout(world.getSaveHandler().getWorldDirectory(),profile,true);
        XFEarthNativeCodec.requireComponents();
        if(!"com.cardinalstar.cubicchunks.server.CubeProviderServer".equals(world.getChunkProvider().getClass().getName()))
            throw new IllegalStateException("Native cube provider is missing");
        XFEarthNativeCodec.get(); XFEarthNativeCodec.requireBounds(world,profile); WORLDS.put(world,profile);
    }
    /** CubicPlayerManager.addPlayer HEAD: return true to defer the native subscription, not its packets. */
    public static boolean deferSubscription(Object manager,EntityPlayerMP player) {
        WorldServer world=((net.minecraft.server.management.PlayerManager)manager).getWorldServer();
        XFEarthProfile profile=WORLDS.get(world); if(profile==null) return false;
        Session current=SESSIONS.get(player);
        if(current!=null&&current.world==world) return !current.accepted;
        Session session=new Session(world); SESSIONS.put(player,session);
        try {
            byte[] json=new Gson().toJson(profile).getBytes("UTF-8");
            if(json.length>16384) throw new IllegalStateException("Oversize Earth profile");
            PacketDispatcher.wrapper.sendTo(new XFEarthHeightPacket(XFEarthHeightPacket.HELLO,0,session.id,json),player);
        } catch(Exception failure) { player.playerNetServerHandler.kickPlayerFromServer("Earth height handshake failed: "+failure.getMessage()); }
        return true;
    }
    /** CubicPlayerManager.removePlayer RETURN, after native unload messages have used the old session. */
    public static void removed(Object manager,EntityPlayerMP player) {
        Session session=SESSIONS.get(player);
        if(session!=null&&session.world.getPlayerManager()==manager) SESSIONS.remove(player);
    }
    /** Native NetworkChannel.sendToPlayer HEAD. Unrelated worlds retain the native channel. */
    public static boolean sendNative(Object packet,EntityPlayerMP player) {
        if(packet==null||player==null) return false;
        Session session=SESSIONS.get(player);
        if(session==null) {
            if(WORLDS.containsKey(player.worldObj)) throw new IllegalStateException("Earth cube sent before a height session");
            return false;
        }
        try {
            // The descriptor supplies the authoritative bounds before subscriptions start.
            if(!session.accepted) {
                int id=((Number)packet.getClass().getMethod("getPacketID").invoke(packet)).intValue();
                if(id!=7) throw new IllegalStateException("Native Earth data sent before height acknowledgement");
                return true;
            }
            PacketDispatcher.wrapper.sendTo(new XFEarthHeightPacket(XFEarthHeightPacket.CUBIC,0,session.id,
                    XFEarthNativeCodec.get().encode(packet)),player); return true;
        } catch(Exception failure) {
            player.playerNetServerHandler.kickPlayerFromServer("Earth cube synchronization failed: "+failure.getMessage()); return true;
        }
    }
    /** Replace only the native sync machine's description sends, to preserve cube/description order. */
    public static void sendDescription(NetHandlerPlayServer handler,Packet packet) {
        Session session=SESSIONS.get(handler.playerEntity);
        if(session==null) { handler.sendPacket(packet); return; }
        ByteBuf bytes=Unpooled.buffer();
        try {
            if(!session.accepted) throw new IllegalStateException("Tile description sent before height acknowledgement");
            if(packet instanceof cpw.mods.fml.common.network.internal.FMLProxyPacket)
                packet=((cpw.mods.fml.common.network.internal.FMLProxyPacket)packet).toS3FPacket();
            Integer id=EnumConnectionState.PLAY.func_150755_b().inverse().get(packet.getClass());
            if(id==null) throw new IllegalStateException("Unregistered tile description packet: "+packet.getClass().getName());
            bytes.writeInt(id); packet.writePacketData(new PacketBuffer(bytes));
            byte[] payload=new byte[bytes.readableBytes()]; bytes.readBytes(payload);
            PacketDispatcher.wrapper.sendTo(new XFEarthHeightPacket(XFEarthHeightPacket.DESCRIPTION,0,session.id,payload),handler.playerEntity);
        } catch(Exception failure) { handler.kickPlayerFromServer("Earth tile synchronization failed: "+failure.getMessage()); }
        finally { bytes.release(); }
    }
    public static void enqueueAck(NetHandlerPlayServer handler,long session) { ACKS.put(handler,session); }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.START) return;
        tick++;
        for(Map.Entry<NetHandlerPlayServer,Long> ack:ACKS.entrySet()) {
            if(!ACKS.remove(ack.getKey(),ack.getValue())) continue;
            NetHandlerPlayServer handler=ack.getKey(); EntityPlayerMP player=handler.playerEntity; Session session=SESSIONS.get(player);
            if(session==null||session.id!=ack.getValue()||session.accepted||player.playerNetServerHandler!=handler
                    ||!handler.netManager.isChannelOpen()||player.worldObj!=session.world) continue;
            session.accepted=true;
            // The original manager now performs its normal column -> cube -> description synchronization.
            session.world.getPlayerManager().addPlayer(player);
        }
        for(Iterator<Map.Entry<EntityPlayerMP,Session>> it=SESSIONS.entrySet().iterator();it.hasNext();) {
            Map.Entry<EntityPlayerMP,Session> entry=it.next(); EntityPlayerMP player=entry.getKey(); Session session=entry.getValue();
            if(!player.playerNetServerHandler.netManager.isChannelOpen()||player.worldObj!=session.world) { it.remove(); continue; }
            if(!session.accepted&&tick>=session.deadline) {
                it.remove(); player.playerNetServerHandler.kickPlayerFromServer("Earth height format/renderer acknowledgement timed out");
                break; // Disconnect may invoke the manager removal hook and mutate this map.
            }
        }
    }
    public static void unload(World world) {
        WORLDS.remove(world);
        for(Iterator<Session> it=SESSIONS.values().iterator();it.hasNext();) if(it.next().world==world) it.remove();
    }
    public static void clear() { WORLDS.clear(); SESSIONS.clear(); ACKS.clear(); tick=0; }
}
