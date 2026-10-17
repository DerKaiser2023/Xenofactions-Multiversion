package com.hfr.client.render;

import com.hfr.blocks.BlockDummyable;
import com.hfr.blocks.ModBlocks;
import com.hfr.main.ResourceManager;
import com.hfr.tileentity.*;
import com.hfr.tileentity.clowder.*;
import com.hfr.tileentity.machine.*;
import com.hfr.tileentity.prop.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

/** Uses the same orientation/offset rules as the native TESRs. Resource reload replaces model bounds. */
public final class ModelBounds {
    private ModelBounds() { }
    public static int rotation(int metadata) { return metadata==2?90:metadata==4?180:metadata==3?270:0; }
    public static AxisAlignedBB forTile(TileEntity tile) {
        GpuModel model;int angle=rotation(tile.getBlockMetadata()-BlockDummyable.offset);
        double ox=.5,oy=0,oz=.5,dx=0,dz=0;
        if(tile instanceof TileEntityFlag){model=ResourceManager.flag;angle=rotation(tile.getBlockMetadata());}
        else if(tile instanceof TileEntityFlagBig){model=ResourceManager.flag_big;angle=rotation(tile.getBlockMetadata());}
        else if(tile instanceof TileEntityConquerer){model=ResourceManager.flag;angle=rotation(tile.getBlockMetadata());}
        else if(tile instanceof TileEntityCap){model=ResourceManager.cap;angle=0;}
        else if(tile instanceof TileEntityMachineNet){model=ResourceManager.net;angle=0;oy=.0625;}
        else if(tile instanceof TileEntityMachineUni)model=ResourceManager.uni;
        else if(tile instanceof TileEntityMachineFactory)model=ResourceManager.factory;
        else if(tile instanceof TileEntityMachineTemple)model=ResourceManager.temple;
        else if(tile instanceof TileEntityMachineGrainmill){
            AxisAlignedBB body=ResourceManager.mill.partBounds("Body"), rotor=ResourceManager.mill.partBounds("Rotor");
            double radius=ResourceManager.mill.partRadius("Rotor");
            AxisAlignedBB envelope=AxisAlignedBB.getBoundingBox(Math.min(body.minX,-radius),Math.min(body.minY,GpuTmtModels.ROTOR_Y-radius),
                Math.min(body.minZ,GpuTmtModels.ROTOR_Z+rotor.minZ),Math.max(body.maxX,radius),Math.max(body.maxY,GpuTmtModels.ROTOR_Y+radius),
                Math.max(body.maxZ,GpuTmtModels.ROTOR_Z+rotor.maxZ));
            return transform(envelope,angle-90,tile.xCoord+.5,tile.yCoord,tile.zCoord+.5,0,0);
        }
        else if(tile instanceof TileEntityDebug){model=ResourceManager.devon;angle=180;}
        else if(tile instanceof TileEntityFactionDecoration){
            if(tile.getBlockType()!=ModBlocks.large_faction_flag)return AxisAlignedBB.getBoundingBox(tile.xCoord,tile.yCoord,tile.zCoord,tile.xCoord+1,tile.yCoord+1,tile.zCoord+1);
            model=ResourceManager.flag_big;angle=tile.getBlockMetadata()*90;
        }
        else if(tile instanceof TileEntityStatue){model=ResourceManager.statue;angle+=90;dz=3D/16;}
        else if(tile instanceof TileEntityProp){
            if(tile.getBlockType()==ModBlocks.tp_tent){model=ResourceManager.tent;angle+=90;dx=1.625;dz=2.34375;}
            else if(tile.getBlockType()==ModBlocks.hesco_block){model=ResourceManager.hesco;angle+=180;}
            else return AxisAlignedBB.getBoundingBox(tile.xCoord,tile.yCoord,tile.zCoord,tile.xCoord+1,tile.yCoord+1,tile.zCoord+1);
        } else return AxisAlignedBB.getBoundingBox(tile.xCoord,tile.yCoord,tile.zCoord,tile.xCoord+1,tile.yCoord+1,tile.zCoord+1);
        AxisAlignedBB local=model.bounds();
        if(tile instanceof TileEntityFlag||tile instanceof TileEntityConquerer||tile instanceof TileEntityFlagBig) {
            AxisAlignedBB cloth=model.partBounds("Flag");
            double lower=tile instanceof TileEntityFlagBig?8:4;
            local=union(local,cloth.minX,cloth.minY-lower,cloth.minZ,cloth.maxX,cloth.maxY,cloth.maxZ);
        }
        AxisAlignedBB bounds=transform(local,angle,tile.xCoord+ox,tile.yCoord+oy,tile.zCoord+oz,dx,dz);
        if(tile instanceof TileEntityFlag||tile instanceof TileEntityFlagBig) {
            bounds=union(bounds,tile.xCoord,0,tile.zCoord,tile.xCoord+1,256,tile.zCoord+1);
        } else if(tile instanceof TileEntityConquerer&&((TileEntityConquerer)tile).height<1) {
            bounds=union(bounds,tile.xCoord-2,tile.yCoord+4.25,tile.zCoord-2,tile.xCoord+3,tile.yCoord+261,tile.zCoord+3);
        }
        return bounds;
    }
    private static AxisAlignedBB union(AxisAlignedBB b,double x,double y,double z,double xx,double yy,double zz) {
        return AxisAlignedBB.getBoundingBox(Math.min(b.minX,x),Math.min(b.minY,y),Math.min(b.minZ,z),Math.max(b.maxX,xx),Math.max(b.maxY,yy),Math.max(b.maxZ,zz));
    }
    private static AxisAlignedBB transform(AxisAlignedBB b,double degrees,double x,double y,double z,double dx,double dz) {
        double sin=Math.sin(Math.toRadians(degrees)),cos=Math.cos(Math.toRadians(degrees));
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=-minX,maxZ=-minX;
        for(int i=0;i<4;i++) {
            double vx=((i&1)==0?b.minX:b.maxX)+dx,vz=((i&2)==0?b.minZ:b.maxZ)+dz;
            double xx=vx*cos+vz*sin,zz=vz*cos-vx*sin;
            minX=Math.min(minX,xx);maxX=Math.max(maxX,xx);minZ=Math.min(minZ,zz);maxZ=Math.max(maxZ,zz);
        }
        return AxisAlignedBB.getBoundingBox(x+minX-.002,y+b.minY-.002,z+minZ-.002,x+maxX+.002,y+b.maxY+.002,z+maxZ+.002);
    }
}
