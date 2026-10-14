package com.hfr.world.earth.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.NetHandlerPlayServer;

/** Echoed only after native provider, bounds and renderer validation on the client thread. */
public final class XFEarthHeightAck implements IMessage {
    public long session;
    public XFEarthHeightAck() { }
    public XFEarthHeightAck(long session) { this.session=session; }
    @Override public void fromBytes(ByteBuf buf) {
        if(buf.readInt()!=XFEarthHeightPacket.PROTOCOL) throw new IllegalArgumentException("Unsupported Earth height acknowledgement");
        session=buf.readLong(); if(session==0||buf.isReadable()) throw new IllegalArgumentException("Invalid Earth height acknowledgement");
    }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(XFEarthHeightPacket.PROTOCOL).writeLong(session); }
    public static final class Handler implements IMessageHandler<XFEarthHeightAck,IMessage> {
        @Override public IMessage onMessage(XFEarthHeightAck packet, MessageContext context) {
            XFEarthHeightSync.enqueueAck((NetHandlerPlayServer)context.netHandler,packet.session); return null;
        }
    }
}
