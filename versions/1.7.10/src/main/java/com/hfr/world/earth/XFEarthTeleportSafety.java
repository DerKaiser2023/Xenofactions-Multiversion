package com.hfr.world.earth;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** Earth-only terrain/collision search. Every call belongs to the owning server thread. */
public final class XFEarthTeleportSafety {
    private XFEarthTeleportSafety() { }

    /** Returns entity position Y, or NaN when no clear destination exists. Never fabricates a safe fallback. */
    public static double find(World world,XFEarthProfile profile,Entity entity,double x,double desiredY,double z,boolean ground) {
        if(world==null||world.isRemote||profile==null||entity==null) throw new IllegalArgumentException("Earth teleport search requires a server world, profile and entity");
        AxisAlignedBB original=entity.boundingBox;
        if(original==null||!finite(x)||!finite(z)||!finite(entity.posX)||!finite(entity.posY)||!finite(entity.posZ)) return Double.NaN;
        double footOffset=original.minY-entity.posY;
        AxisAlignedBB horizontal=original.getOffsetBoundingBox(x-entity.posX,0,z-entity.posZ);
        if(!finite(horizontal.minX)||!finite(horizontal.maxX)||!finite(horizontal.minZ)||!finite(horizontal.maxZ)
                ||!finite(footOffset)||!finite(original.maxY)||original.maxY<=original.minY) return Double.NaN;
        // Standing atop the last legal block puts feet at maxY+1; the body may extend into open sky.
        int minFoot=profile.getMinimumY()+1,maxFoot=profile.getMaximumY()+1;
        int minX=MathHelper.floor_double(horizontal.minX),maxX=MathHelper.floor_double(horizontal.maxX-0.000001D);
        int minZ=MathHelper.floor_double(horizontal.minZ),maxZ=MathHelper.floor_double(horizontal.maxZ-0.000001D);
        for(int cx=minX>>4;cx<=(maxX>>4);cx++) for(int cz=minZ>>4;cz<=(maxZ>>4);cz++) world.getChunkFromChunkCoords(cx,cz);
        // Build bounds are not a flight ceiling. Preserve clear border flight, including open sky above maxY.
        if(!ground&&finite(desiredY)&&desiredY+footOffset>=minFoot
                &&clear(world,profile,entity,horizontal.getOffsetBoundingBox(0,desiredY-entity.posY,0),false))return desiredY;
        double foot=finite(desiredY)?Math.max(minFoot,Math.min(maxFoot,desiredY+footOffset)):profile.getSeaLevel()+1.0D;
        double target=foot-footOffset;
        if(clear(world,profile,entity,horizontal.getOffsetBoundingBox(0,target-entity.posY,0),ground))return target;
        int start=Math.max(minFoot,Math.min(maxFoot,(int)Math.ceil(foot)));
        for(int y=start;y<=maxFoot;y++) {
            target=y-footOffset;
            if(clear(world,profile,entity,horizontal.getOffsetBoundingBox(0,target-entity.posY,0),ground))return target;
        }
        for(int y=start-1;y>=minFoot;y--) {
            target=y-footOffset;
            if(clear(world,profile,entity,horizontal.getOffsetBoundingBox(0,target-entity.posY,0),ground))return target;
        }
        return Double.NaN;
    }
    private static boolean clear(World world,XFEarthProfile profile,Entity entity,AxisAlignedBB box,boolean ground) {
        if(!world.getCollidingBoundingBoxes(entity,box).isEmpty()||world.isAnyLiquid(box))return false;
        int minX=MathHelper.floor_double(box.minX),maxX=MathHelper.floor_double(box.maxX-0.000001D);
        int minZ=MathHelper.floor_double(box.minZ),maxZ=MathHelper.floor_double(box.maxZ-0.000001D);
        int minY=Math.max(profile.getMinimumY(),MathHelper.floor_double(box.minY));
        int maxY=Math.min(profile.getMaximumY(),MathHelper.floor_double(box.maxY-0.000001D));
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=minY;y<=maxY;y++) {
            Block block=world.getBlock(x,y,z);
            if(block==Blocks.fire||block==Blocks.cactus)return false;
        }
        if(!ground)return true; // Border wrapping must preserve clear flight positions.
        int x=MathHelper.floor_double((box.minX+box.maxX)*0.5D),z=MathHelper.floor_double((box.minZ+box.maxZ)*0.5D);
        int y=MathHelper.floor_double(box.minY-0.000001D);
        Block support=world.getBlock(x,y,z);
        return profile.containsBlockY(y)&&support!=Blocks.cactus&&support!=Blocks.fire
                &&!support.getMaterial().isLiquid()&&support.isSideSolid(world,x,y,z,ForgeDirection.UP);
    }
    private static boolean finite(double value){return !Double.isNaN(value)&&!Double.isInfinite(value);}
}
