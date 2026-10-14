package com.hfr.packet.effect;

import com.hfr.main.MainRegistry;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;

/** Server-authoritative profile only; imagery is prepared offline and distributed with the client. */
public final class EarthMapSnapshotPacket implements IMessage {
    private int request, dimension;
    private String profile, identity, error;
    public EarthMapSnapshotPacket() { }
    public EarthMapSnapshotPacket(int request, int dimension, String profile, String identity, String error) {
        this.request = request; this.dimension = dimension; this.profile = profile; this.identity = identity; this.error = error;
    }
    @Override public void fromBytes(ByteBuf buf) {
        request = buf.readInt(); dimension = buf.readInt();
        profile = ByteBufUtils.readUTF8String(buf); identity = ByteBufUtils.readUTF8String(buf); error = ByteBufUtils.readUTF8String(buf);
        if (profile.length() > 16384 || identity.length() > 64 || error.length() > 128)
            throw new IllegalArgumentException("Oversize Earth map description");
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(request); buf.writeInt(dimension);
        ByteBufUtils.writeUTF8String(buf, profile); ByteBufUtils.writeUTF8String(buf, identity); ByteBufUtils.writeUTF8String(buf, error);
    }
    public static final class Handler implements IMessageHandler<EarthMapSnapshotPacket, IMessage> {
        @Override public IMessage onMessage(EarthMapSnapshotPacket packet, MessageContext context) {
            MainRegistry.proxy.receiveEarthMap(packet.request, packet.dimension, packet.profile, packet.identity, packet.error);
            return null;
        }
    }
}
