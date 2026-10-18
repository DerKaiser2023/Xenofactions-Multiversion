package com.hfr.packet.client;

import com.hfr.main.PlayerActivityTracker;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Cosmetic activity intent, never a position, permission, or gameplay mutation. */
public final class PlayerActivityPacket implements IMessage {
    public void fromBytes(ByteBuf buffer) { }
    public void toBytes(ByteBuf buffer) { }
    public static final class Handler implements IMessageHandler<PlayerActivityPacket, IMessage> {
        public IMessage onMessage(PlayerActivityPacket message, MessageContext context) {
            PlayerActivityTracker.signal(context.getServerHandler().playerEntity);
            return null;
        }
    }
}
