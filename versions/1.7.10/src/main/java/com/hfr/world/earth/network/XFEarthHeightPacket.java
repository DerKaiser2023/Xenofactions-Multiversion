package com.hfr.world.earth.network;

import com.hfr.main.MainRegistry;
import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;

/** Session envelope around the pinned implementation's bytes, not another cube format. */
public final class XFEarthHeightPacket implements IMessage {
    public static final int PROTOCOL = 1, HELLO = 0, CUBIC = 1, DESCRIPTION = 2;
    public static final int MAX_PAYLOAD = 1048576;
    public int kind, dimension;
    public long session;
    public byte[] payload;
    public XFEarthHeightPacket() { }
    public XFEarthHeightPacket(int kind, int dimension, long session, byte[] payload) {
        this.kind=kind; this.dimension=dimension; this.session=session; this.payload=payload;
    }
    @Override public void fromBytes(ByteBuf buf) {
        if (buf.readInt()!=PROTOCOL) throw new IllegalArgumentException("Unsupported Earth height protocol");
        kind=buf.readUnsignedByte(); dimension=buf.readInt(); session=buf.readLong();
        int size=buf.readInt();
        if(kind>DESCRIPTION||dimension!=0||session<=0||size<1||size>MAX_PAYLOAD||size!=buf.readableBytes())
            throw new IllegalArgumentException("Invalid Earth height envelope");
        payload=new byte[size]; buf.readBytes(payload);
    }
    @Override public void toBytes(ByteBuf buf) {
        if(payload==null||payload.length<1||payload.length>MAX_PAYLOAD) throw new IllegalArgumentException("Oversize Earth height payload");
        buf.writeInt(PROTOCOL).writeByte(kind).writeInt(dimension).writeLong(session).writeInt(payload.length).writeBytes(payload);
    }
    public static final class Handler implements IMessageHandler<XFEarthHeightPacket,IMessage> {
        @Override public IMessage onMessage(XFEarthHeightPacket packet, MessageContext context) {
            MainRegistry.proxy.receiveEarthHeight(packet,context.netHandler); return null;
        }
    }
}
