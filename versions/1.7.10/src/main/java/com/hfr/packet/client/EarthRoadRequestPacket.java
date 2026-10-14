package com.hfr.packet.client;

import com.hfr.tdm.TDMServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Bounded geometry viewport, with world/profile resolution deferred to the server. */
public final class EarthRoadRequestPacket implements IMessage {
    private int token,dimension,x0,z0,x1,z1,lod,mask;
    public EarthRoadRequestPacket(){}
    public EarthRoadRequestPacket(int token,int dimension,int x0,int z0,int x1,int z1,int lod,int mask){this.token=token;this.dimension=dimension;this.x0=x0;this.z0=z0;this.x1=x1;this.z1=z1;this.lod=lod;this.mask=mask;}
    public void fromBytes(ByteBuf b){if(b.readableBytes()!=30||b.readUnsignedByte()!=com.hfr.world.earth.map.XFEarthMapLayers.VERSION)throw new IllegalArgumentException("Invalid map viewport");token=b.readInt();dimension=b.readInt();x0=b.readInt();z0=b.readInt();x1=b.readInt();z1=b.readInt();lod=b.readInt();mask=b.readUnsignedByte();if(lod<1||lod>65536||mask>127)throw new IllegalArgumentException("Invalid map detail");}
    public void toBytes(ByteBuf b){b.writeByte(com.hfr.world.earth.map.XFEarthMapLayers.VERSION);b.writeInt(token);b.writeInt(dimension);b.writeInt(x0);b.writeInt(z0);b.writeInt(x1);b.writeInt(z1);b.writeInt(lod);b.writeByte(mask);}
    public static final class Handler implements IMessageHandler<EarthRoadRequestPacket,IMessage>{
        public IMessage onMessage(final EarthRoadRequestPacket p,MessageContext context){final EntityPlayerMP player=context.getServerHandler().playerEntity;
            TDMServerTaskQueue.schedule(new Runnable(){public void run(){com.hfr.world.earth.roads.XFEarthRoadMap.request(player,p.token,p.dimension,p.x0,p.z0,p.x1,p.z1,p.lod,p.mask);}});return null;}
    }
}
