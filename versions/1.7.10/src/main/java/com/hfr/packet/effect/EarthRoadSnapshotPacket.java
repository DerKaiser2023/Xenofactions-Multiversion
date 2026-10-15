package com.hfr.packet.effect;

import com.hfr.main.MainRegistry;
import com.hfr.world.earth.map.XFEarthMapLayers;
import com.hfr.world.earth.map.XFEarthMapLayers.Feature;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import java.util.*;

/** The existing Map reply channel, carrying bounded public layer/selection records. */
public final class EarthRoadSnapshotPacket implements IMessage {
    public static final String SELECTION_INVALIDATED="selection-invalidated";
    public int token,dimension,part,parts,lod,mask;
    public String world,profile,dataset,view,notice;
    public String attribution="© OpenStreetMap contributors / ODbL";
    public List<Feature> features;
    public EarthRoadSnapshotPacket(){}
    public EarthRoadSnapshotPacket(int token,int dimension,int part,int parts,int lod,int mask,String world,String profile,String dataset,String view,String notice,List<Feature> features){
        this.token=token;this.dimension=dimension;this.part=part;this.parts=parts;this.lod=lod;this.mask=mask;this.world=world;this.profile=profile;this.dataset=dataset;this.view=view;
        this.notice=XFEarthMapLayers.clean(notice,384);this.features=Collections.unmodifiableList(new ArrayList<Feature>(features));
    }
    private static String text(ByteBuf b,int max){String s=ByteBufUtils.readUTF8String(b);if(s.length()>max)throw new IllegalArgumentException("Oversize map text");return s;}
    public void fromBytes(ByteBuf b){
        if(b.readableBytes()>32700||b.readUnsignedByte()!=XFEarthMapLayers.VERSION)throw new IllegalArgumentException("Invalid map protocol");
        token=b.readInt();dimension=b.readInt();part=b.readUnsignedByte();parts=b.readUnsignedByte();lod=b.readInt();mask=b.readUnsignedByte();
        if(parts<1||parts>8||part>=parts||lod<1||lod>65536||mask>127)throw new IllegalArgumentException("Invalid map frame");
        world=text(b,64);profile=text(b,64);dataset=text(b,64);view=text(b,64);notice=text(b,384);attribution=text(b,1024);
        int count=b.readUnsignedByte(),vertices=0;if(count>XFEarthMapLayers.PACKET_FEATURES)throw new IllegalArgumentException("Map feature limit");features=new ArrayList<Feature>();
        for(int i=0;i<count;i++){
            String key=text(b,64),name=text(b,64),category=text(b,48),source=text(b,64),detail=text(b,96);int layer=b.readUnsignedByte(),type=b.readUnsignedByte(),state=b.readUnsignedByte(),color=b.readInt(),contours=b.readUnsignedByte();
            if(contours<1||contours>64)throw new IllegalArgumentException("Map contour limit");double[][] paths=new double[contours][];
            for(int j=0;j<contours;j++){int size=b.readUnsignedShort();vertices+=size;if(vertices>XFEarthMapLayers.PACKET_VERTICES)throw new IllegalArgumentException("Map vertex limit");paths[j]=new double[size*2];for(int k=0;k<paths[j].length;k++)paths[j][k]=b.readDouble();}
            features.add(new Feature(key,name,category,source,detail,layer,type,color,state,paths));
        }if(b.isReadable())throw new IllegalArgumentException("Unexpected map payload");
    }
    public void toBytes(ByteBuf b){b.writeByte(XFEarthMapLayers.VERSION);b.writeInt(token);b.writeInt(dimension);b.writeByte(part);b.writeByte(parts);b.writeInt(lod);b.writeByte(mask);
        for(String s:new String[]{world,profile,dataset,view,notice,attribution})ByteBufUtils.writeUTF8String(b,s);b.writeByte(features.size());
        for(Feature f:features){for(String s:new String[]{f.key,f.name,f.category,f.source,f.detail})ByteBufUtils.writeUTF8String(b,s);b.writeByte(f.layer);b.writeByte(f.type);b.writeByte(f.state);b.writeInt(f.color);b.writeByte(f.paths.length);
            for(double[] path:f.paths){b.writeShort(path.length/2);for(double value:path)b.writeDouble(value);}}
    }
    public static final class Handler implements IMessageHandler<EarthRoadSnapshotPacket,IMessage>{public IMessage onMessage(EarthRoadSnapshotPacket p,MessageContext ctx){MainRegistry.proxy.receiveEarthLayers(p);return null;}}
}
