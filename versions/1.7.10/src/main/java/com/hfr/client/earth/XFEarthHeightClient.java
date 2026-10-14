package com.hfr.client.earth;

import com.google.gson.Gson;
import com.hfr.packet.PacketDispatcher;
import com.hfr.world.earth.XFEarthProfile;
import com.hfr.world.earth.network.*;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/** Network callbacks enqueue detached bytes only; all native decode/application runs on ClientTick. */
public final class XFEarthHeightClient {
    private static final ConcurrentLinkedQueue<Delivery> QUEUE=new ConcurrentLinkedQueue<Delivery>();
    private static final AtomicInteger QUEUED_BYTES=new AtomicInteger();
    private static volatile boolean reset, overflow;
    private static World activeWorld;
    private static INetHandler activeHandler;
    private static INetHandler connection;
    private static XFEarthProfile activeProfile;
    private static long activeSession;
    private static long retiredThrough;
    private static int waitingTicks;
    private static final class Delivery {
        final XFEarthHeightPacket packet; final INetHandler handler;
        Delivery(XFEarthHeightPacket packet,INetHandler handler) { this.packet=packet; this.handler=handler; }
    }
    public static void register() { FMLCommonHandler.instance().bus().register(new XFEarthHeightClient()); }
    /** Client-thread view of the shared validated profile; ordinary worlds keep their existing Map sync. */
    public static XFEarthProfile getActiveProfile(World world) { return world==activeWorld?activeProfile:null; }
    public static void receive(XFEarthHeightPacket packet,INetHandler handler) {
        if(QUEUED_BYTES.addAndGet(packet.payload.length)>16*1048576) {
            QUEUED_BYTES.addAndGet(-packet.payload.length); overflow=true; return;
        }
        QUEUE.add(new Delivery(packet,handler));
    }
    @SubscribeEvent public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) { reset=true; }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.START) return;
        Minecraft mc=Minecraft.getMinecraft();
        if(reset) { reset=false; clearSession(); waitingTicks=0; overflow=false; }
        if(connection!=mc.getNetHandler()) { clearSession(); retiredThrough=0; connection=mc.getNetHandler(); waitingTicks=0; }
        if(activeWorld!=null&&(activeWorld!=mc.theWorld||activeHandler!=mc.getNetHandler())) clearSession();
        if(overflow) { fail(mc,"Earth height packet queue exceeded its limit"); return; }
        for(int handled=0;handled<1024;handled++) {
            Delivery delivery=QUEUE.peek(); if(delivery==null) { waitingTicks=0; return; }
            if(mc.getNetHandler()==null) {
                if(++waitingTicks>400) { clearQueue(); clearSession(); } return;
            }
            if(delivery.handler!=mc.getNetHandler()) { remove(); continue; }
            XFEarthHeightPacket packet=delivery.packet;
            if(packet.session<=retiredThrough) { remove(); continue; }
            // S01/S07 may still be queued by vanilla. Do not touch a world from the network thread.
            if(mc.theWorld==null||mc.theWorld.provider.dimensionId!=packet.dimension) {
                if(++waitingTicks>400) fail(mc,"Earth height descriptor did not reach its dimension"); return;
            }
            remove(); waitingTicks=0;
            try {
                if(packet.kind==XFEarthHeightPacket.HELLO) {
                    if(activeSession!=0&&packet.session<=activeSession) throw new IllegalStateException("Duplicate Earth height session");
                    XFEarthProfile profile=new Gson().fromJson(new String(packet.payload,"UTF-8"),XFEarthProfile.class);
                    if(profile==null) throw new IllegalStateException("Empty Earth height profile");
                    profile.validate("1.7.10"); if(!profile.isExtendedHeight()) throw new IllegalStateException("Ordinary Earth must use its ordinary packet path");
                    XFEarthNativeCodec.requireComponents();
                    if(Loader.isModLoaded("angelica")) throw new IllegalStateException("Angelica has no verified extended-height terrain integration");
                    if(!"com.cardinalstar.cubicchunks.client.CubeProviderClient".equals(mc.theWorld.getChunkProvider().getClass().getName()))
                        throw new IllegalStateException("Native client cube provider is missing");
                    // This unique field is supplied by the selected native RenderGlobal mixin.
                    mc.renderGlobal.getClass().getDeclaredField("RenderDistanceCubesVertical");
                    XFEarthNativeCodec.get(); XFEarthNativeCodec.setBounds(mc.theWorld,profile);
                    XFEarthNativeCodec.requireBounds(mc.theWorld,profile); mc.renderGlobal.loadRenderers();
                    // Same-dimension respawn retains WorldClient but replaces the server player/subscription.
                    clearSession();
                    activeWorld=mc.theWorld; activeHandler=delivery.handler; activeProfile=profile; activeSession=packet.session;
                    PacketDispatcher.wrapper.sendToServer(new XFEarthHeightAck(activeSession));
                } else {
                    if(activeWorld!=mc.theWorld||activeHandler!=delivery.handler||activeSession!=packet.session)
                        throw new IllegalStateException("Earth chunk arrived without its accepted height session");
                    if(packet.kind==XFEarthHeightPacket.CUBIC)
                        XFEarthNativeCodec.get().apply(activeWorld,activeHandler,packet.payload,activeProfile);
                    else description(packet.payload);
                }
            } catch(Exception|LinkageError failure) { fail(mc,"Earth height synchronization rejected: "+failure.getMessage()); return; }
        }
    }
    private static void description(byte[] payload) throws Exception {
        ByteBuf bytes=Unpooled.wrappedBuffer(payload);
        try {
            Packet packet=Packet.generatePacket(EnumConnectionState.PLAY.func_150755_b(),bytes.readInt());
            if(packet==null) throw new IllegalStateException("Unsupported native tile description packet");
            packet.readPacketData(new PacketBuffer(bytes)); if(bytes.isReadable()) throw new IllegalStateException("Trailing tile description data");
            if(packet instanceof S35PacketUpdateTileEntity) {
                S35PacketUpdateTileEntity tile=(S35PacketUpdateTileEntity)packet; int y=tile.func_148855_d();
                if(y<activeProfile.getMinimumY()||y>activeProfile.getMaximumY()) throw new IllegalStateException("Tile description outside Earth Y bounds");
                Object provider=activeWorld.getChunkProvider();
                Object cube=provider.getClass().getMethod("getLoadedCube",int.class,int.class,int.class)
                        .invoke(provider,tile.func_148856_c()>>4,y>>4,tile.func_148854_e()>>4);
                if(cube==null) throw new IllegalStateException("Tile description arrived before its cube");
            }
            packet.processPacket(activeHandler);
        } finally { bytes.release(); }
    }
    private static void remove() { Delivery item=QUEUE.poll(); if(item!=null) QUEUED_BYTES.addAndGet(-item.packet.payload.length); }
    private static void clearQueue() { while(QUEUE.peek()!=null) remove(); waitingTicks=0; }
    private static void clearSession() { retiredThrough=Math.max(retiredThrough,activeSession); activeWorld=null; activeHandler=null; activeProfile=null; activeSession=0; }
    private static void fail(Minecraft mc,String message) {
        clearQueue(); clearSession(); overflow=false;
        if(mc.getNetHandler()!=null) mc.getNetHandler().getNetworkManager().closeChannel(new ChatComponentText(message));
    }
}
