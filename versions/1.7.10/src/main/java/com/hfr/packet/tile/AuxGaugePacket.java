package com.hfr.packet.tile;

import com.hfr.tileentity.machine.*;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

public class AuxGaugePacket implements IMessage {
    int dimension;

	int x;
	int y;
	int z;
	int value;
	int meta;

	public AuxGaugePacket()
	{
		
	}

	public AuxGaugePacket(int dimension, int x, int y, int z, int value, int meta)
	{
		this.dimension = dimension;
		this.x = x;
		this.y = y;
		this.z = z;
		this.value = value;
		this.meta = meta;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		dimension = buf.readInt();
		x = buf.readInt();
		y = buf.readInt();
		z = buf.readInt();
		value = buf.readInt();
		meta = buf.readInt();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(dimension);
		buf.writeInt(x);
		buf.writeInt(y);
		buf.writeInt(z);
		buf.writeInt(value);
		buf.writeInt(meta);
	}

	public static class Handler implements IMessageHandler<AuxGaugePacket, IMessage> {
		
		@Override
		@SideOnly(Side.CLIENT)
		public IMessage onMessage(final AuxGaugePacket m, MessageContext ctx) {
            final net.minecraft.world.World receiving=Minecraft.getMinecraft().theWorld;
            com.hfr.client.render.ClientRenderSession.enqueue(ctx.netHandler,m.dimension,() -> {
                final TileEntity tile=receiving.getTileEntity(m.x,m.y,m.z);
                if(tile==null||tile.isInvalid())return;
                if(tile instanceof TileEntityMachineNet)((TileEntityMachineNet)tile).isJammed=m.value==1;
                if(tile instanceof TileEntityMachineBase)((TileEntityMachineBase)tile).processGauge(m.value,m.meta);
            });

			return null;
		}
	}
}
