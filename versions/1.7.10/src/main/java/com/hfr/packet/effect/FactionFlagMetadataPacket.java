package com.hfr.packet.effect;

import com.hfr.client.flag.FactionFlagTextureManager;
import com.hfr.clowder.flag.CustomFlagService;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;

public class FactionFlagMetadataPacket implements IMessage {

	public String factionName;
	public String hash;

	public FactionFlagMetadataPacket() { }

	public FactionFlagMetadataPacket(String factionName, String hash) {
		this.factionName = factionName == null ? "" : factionName;
		this.hash = hash == null ? "" : hash;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		factionName = ByteBufUtils.readUTF8String(buf);
		hash = ByteBufUtils.readUTF8String(buf);
	}

	@Override
	public void toBytes(ByteBuf buf) {
		ByteBufUtils.writeUTF8String(buf, factionName == null ? "" : factionName);
		ByteBufUtils.writeUTF8String(buf, hash == null ? "" : hash);
	}

	public static class ClientHandler implements IMessageHandler<FactionFlagMetadataPacket, IMessage> {
		@Override
		@SideOnly(Side.CLIENT)
		public IMessage onMessage(final FactionFlagMetadataPacket m, MessageContext ctx) {
            net.minecraft.world.World receiving=Minecraft.getMinecraft().theWorld;
            if(receiving!=null)com.hfr.client.render.ClientRenderSession.enqueue(ctx.netHandler,receiving.provider.dimensionId,() -> FactionFlagTextureManager.handleMetadata(m.factionName, m.hash));
			return null;
		}
	}

	public static class ServerHandler implements IMessageHandler<FactionFlagMetadataPacket, IMessage> {
		@Override
		public IMessage onMessage(FactionFlagMetadataPacket m, MessageContext ctx) {
			EntityPlayerMP player = ctx.getServerHandler().playerEntity;
			if(player != null)
				CustomFlagService.requestFlagBytes(player, m.factionName, m.hash);
			return null;
		}
	}
}
