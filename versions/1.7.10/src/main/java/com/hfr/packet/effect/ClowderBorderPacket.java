package com.hfr.packet.effect;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;

public class ClowderBorderPacket implements IMessage {
	long subscription;
	int dimension;

	int x1;
	int z1;
	int x2;
	int z2;
	int color;

	public ClowderBorderPacket()
	{
		
	}

	public ClowderBorderPacket(long subscription, int dimension, int x1, int z1, int x2, int z2, int color)
	{
		this.subscription = subscription;
		this.dimension = dimension;
		this.x1 = x1;
		this.z1 = z1;
		this.x2 = x2;
		this.z2 = z2;
		this.color = color;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		if(buf.readableBytes() != 32) throw new IllegalArgumentException("Invalid border effect packet");
		subscription = buf.readLong();
		dimension = buf.readInt();
		x1 = buf.readInt();
		z1 = buf.readInt();
		x2 = buf.readInt();
		z2 = buf.readInt();
		color = buf.readInt();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeLong(subscription);
		buf.writeInt(dimension);
		buf.writeInt(x1);
		buf.writeInt(z1);
		buf.writeInt(x2);
		buf.writeInt(z2);
		buf.writeInt(color);
	}

	public static class Handler implements IMessageHandler<ClowderBorderPacket, IMessage> {
		
		@Override
		@SideOnly(Side.CLIENT)
		public IMessage onMessage(final ClowderBorderPacket m, MessageContext ctx) {
			final net.minecraft.client.network.NetHandlerPlayClient connection = ctx.getClientHandler();
			Minecraft.getMinecraft().func_152344_a(new Runnable() { @Override public void run() {
				if (Minecraft.getMinecraft().getNetHandler() == connection
						&& com.hfr.client.journeymap.ClientClaimOverlayCache.isCurrentSubscription(m.subscription, m.dimension))
					com.hfr.client.journeymap.ClientBorderParticles.enqueue(Minecraft.getMinecraft().theWorld,
							m.x1, m.z1, m.x2, m.z2, m.color);
			} });
			
			return null;
		}
	}
}
