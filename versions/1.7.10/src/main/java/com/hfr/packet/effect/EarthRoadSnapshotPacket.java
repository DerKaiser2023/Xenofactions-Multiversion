package com.hfr.packet.effect;

import com.hfr.main.MainRegistry;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;

public final class EarthRoadSnapshotPacket implements IMessage {
    private int token,dimension,part,parts;private double[] coordinates;private byte[] styles;private String notice;
    public EarthRoadSnapshotPacket(){}
    public EarthRoadSnapshotPacket(int token,int dimension,int part,int parts,double[] coords,byte[] styles,String notice){this.token=token;this.dimension=dimension;this.part=part;this.parts=parts;coordinates=coords;this.styles=styles;this.notice=notice;}
    public void fromBytes(ByteBuf b){
        token=b.readInt();dimension=b.readInt();part=b.readInt();parts=b.readInt();int count=b.readUnsignedShort();
        if(parts<1||parts>8||part<0||part>=parts||count>512)throw new IllegalArgumentException("Invalid road layer limits");
        coordinates=new double[count*4];styles=new byte[count];for(int i=0;i<count;i++){for(int j=0;j<4;j++){double n=b.readDouble();if(!Double.isFinite(n)||Math.abs(n)>30000000)throw new IllegalArgumentException("Invalid road coordinate");coordinates[i*4+j]=n;}styles[i]=b.readByte();}
        notice=ByteBufUtils.readUTF8String(b);if(notice.length()>512||b.isReadable())throw new IllegalArgumentException("Invalid road layer notice");
    }
    public void toBytes(ByteBuf b){b.writeInt(token);b.writeInt(dimension);b.writeInt(part);b.writeInt(parts);b.writeShort(styles.length);
        for(int i=0;i<styles.length;i++){for(int j=0;j<4;j++)b.writeDouble(coordinates[i*4+j]);b.writeByte(styles[i]);}ByteBufUtils.writeUTF8String(b,notice);}
    public static final class Handler implements IMessageHandler<EarthRoadSnapshotPacket,IMessage>{
        public IMessage onMessage(EarthRoadSnapshotPacket p,MessageContext ctx){MainRegistry.proxy.receiveEarthRoads(p.token,p.dimension,p.part,p.parts,p.coordinates,p.styles,p.notice);return null;}
    }
}
