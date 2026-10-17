package com.hfr.packet.tile;

import com.hfr.client.flag.FactionFlagTextureManager;
import com.hfr.tileentity.clowder.TileEntityFlag;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

public class CityCenterPacket implements IMessage {
    int dimension;

	public int x;
	public int y;
	public int z;
	public String cityName;
	public String ownerName;
	public String flagHash;

	public CityCenterPacket() { }

	public CityCenterPacket(int dimension, int x, int y, int z, String cityName, String ownerName) {
		this.dimension = dimension;
		this.x = x;
		this.y = y;
		this.z = z;
		this.cityName = cityName == null ? "" : cityName;
		this.ownerName = ownerName == null ? "" : ownerName;
		this.flagHash = "";
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		dimension = buf.readInt();
		x = buf.readInt();
		y = buf.readInt();
		z = buf.readInt();
		cityName = ByteBufUtils.readUTF8String(buf);
		ownerName = ByteBufUtils.readUTF8String(buf);
		flagHash = ByteBufUtils.readUTF8String(buf);
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(dimension);
		buf.writeInt(x);
		buf.writeInt(y);
		buf.writeInt(z);
		ByteBufUtils.writeUTF8String(buf, cityName == null ? "" : cityName);
		ByteBufUtils.writeUTF8String(buf, ownerName == null ? "" : ownerName);
		ByteBufUtils.writeUTF8String(buf, flagHash == null ? "" : flagHash);
	}

	public static class Handler implements IMessageHandler<CityCenterPacket, IMessage> {

		@Override
		@SideOnly(Side.CLIENT)
		public IMessage onMessage(final CityCenterPacket m, MessageContext ctx) {
            com.hfr.client.render.ClientRenderSession.enqueue(ctx.netHandler,m.dimension,() -> applyMetadata(m));
			return null;
		}

		@SideOnly(Side.CLIENT)
		private void applyMetadata(CityCenterPacket m) {
			try {
				TileEntity te = Minecraft.getMinecraft().theWorld.getTileEntity(m.x, m.y, m.z);
                if(te==null||te.isInvalid())return;
                if(te instanceof TileEntityFlag) {
                    TileEntityFlag flag=(TileEntityFlag)te;flag.name=m.cityName;flag.ownerName=m.ownerName;flag.customFlagHash=m.flagHash;
                } else if(te instanceof com.hfr.tileentity.clowder.TileEntityFlagBig) {
                    com.hfr.tileentity.clowder.TileEntityFlagBig flag=(com.hfr.tileentity.clowder.TileEntityFlagBig)te;
                    flag.ownerName=m.ownerName;flag.customFlagHash=m.flagHash;
                } else if(te instanceof com.hfr.tileentity.clowder.TileEntityConquerer) {
                    com.hfr.tileentity.clowder.TileEntityConquerer flag=(com.hfr.tileentity.clowder.TileEntityConquerer)te;
                    flag.ownerName=m.ownerName;flag.customFlagHash=m.flagHash;
                } else return;
                FactionFlagTextureManager.handleMetadata(m.ownerName,m.flagHash);
			} catch(Exception e) { }
		}
	}
}
