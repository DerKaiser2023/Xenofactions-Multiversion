package com.hfr.packet.effect;

import com.hfr.main.MainRegistry;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class FactionFlagBytesPacket implements IMessage {

	public String factionName;
	public String hash;
	public byte[] png;

	public FactionFlagBytesPacket() { }

	public FactionFlagBytesPacket(String factionName, String hash, byte[] png) {
		this.factionName = factionName == null ? "" : factionName;
		this.hash = hash == null ? "" : hash;
		this.png = png == null ? new byte[0] : png;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		factionName = ByteBufUtils.readUTF8String(buf);
		hash = ByteBufUtils.readUTF8String(buf);
		int len = buf.readInt();
		if(len < 0 || len > 1024 * 1024) {
			png = new byte[0];
			return;
		}
		png = new byte[len];
		buf.readBytes(png);
	}

	@Override
	public void toBytes(ByteBuf buf) {
		ByteBufUtils.writeUTF8String(buf, factionName == null ? "" : factionName);
		ByteBufUtils.writeUTF8String(buf, hash == null ? "" : hash);
		buf.writeInt(png == null ? 0 : png.length);
		if(png != null)
			buf.writeBytes(png);
	}

	public static class Handler implements IMessageHandler<FactionFlagBytesPacket, IMessage> {
		@Override
		public IMessage onMessage(final FactionFlagBytesPacket m, MessageContext ctx) {
			MainRegistry.proxy.receiveFactionFlagBytes(ctx.netHandler, m.factionName, m.hash, m.png);
			return null;
		}
	}
}
