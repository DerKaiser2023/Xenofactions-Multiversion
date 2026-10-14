package com.hfr.world.earth.roads;

import com.hfr.blocks.ModBlocks;
import com.hfr.world.earth.XFEarthProfile;
import com.hfr.world.earth.geo.XFEarthRoadSource;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/** Deterministic local grade function: identical samples yield identical targets on both sides of a seam. */
public final class XFEarthRoadPlanner {
    public interface Terrain {
        int height(int x,int z);
        boolean wet(int x,int z);
        Block block(int x,int y,int z);
        int metadata(int x,int y,int z);
        boolean protectedColumn(int x,int z);
    }
    public static final class Edit {
        public final int x,y,z,metadata;public final Block before,after;
        Edit(int x,int y,int z,Block before,int metadata,Block after){this.x=x;this.y=y;this.z=z;this.before=before;this.metadata=metadata;this.after=after;}
    }
    public static final class Plan {public final List<Edit> edits=new ArrayList<Edit>();public int eligible,blocked,water,steep;}
    private XFEarthRoadPlanner(){}
    private static int grade(Terrain terrain,int x,int z,XFEarthRoadSource.Policy policy){
        int h=terrain.height(x,z);if(h<0)return -1;int[] heights=new int[9];int i=0;
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){int n=terrain.height(x+dx,z+dz);if(n<0)return -1;heights[i++]=n;}
        Arrays.sort(heights);return Math.max(h-policy.cut,Math.min(h+policy.fill,heights[4]));
    }
    public static Plan plan(Map<Long,XFEarthRoadSource.Cell> cells,int cx,int cz,Terrain terrain,XFEarthProfile profile,XFEarthRoadSource.Policy policy){
        Plan result=new Plan();
        for(XFEarthRoadSource.Cell cell:cells.values()){
            int x=cell.x,z=cell.z;if((x>>4)!=cx||(z>>4)!=cz)continue;
            int top=terrain.height(x,z),target=grade(terrain,x,z,policy);
            if(terrain.protectedColumn(x,z)||top<profile.getMinimumY()+1||target<profile.getMinimumY()+1||target+2>profile.getMaximumY()){result.blocked++;continue;}
            if(terrain.wet(x,z)){result.water++;continue;}
            boolean steep=false;
            for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)if((dx!=0||dz!=0)&&cells.containsKey(XFEarthRoadSource.key(x+dx,z+dz))){
                int other=grade(terrain,x+dx,z+dz,policy);if(other<0||Math.abs(target-other)>policy.slope)steep=true;
            }
            if(steep){result.steep++;continue;}
            boolean safe=true;int low=Math.min(top,target),high=Math.max(top,target+2);
            for(int y=low;y<=high;y++)if(!natural(terrain.block(x,y,z))){safe=false;break;}
            if(!safe){result.blocked++;continue;}result.eligible++;
            Block palette=cell.road.gravel?Blocks.gravel:ModBlocks.asphalt;
            for(int y=low;y<=high;y++){
                Block after=y<target?Blocks.stone:y==target?palette:Blocks.air,old=terrain.block(x,y,z);int meta=terrain.metadata(x,y,z);
                if(old!=after||meta!=0)result.edits.add(new Edit(x,y,z,old,meta,after));
            }
        }return result;
    }
    public static boolean natural(Block b){return b==Blocks.air||b==Blocks.grass||b==Blocks.dirt||b==Blocks.stone||b==Blocks.sand||b==Blocks.gravel
            ||b==Blocks.hardened_clay||b==Blocks.snow||b==Blocks.snow_layer||b==Blocks.tallgrass||b==Blocks.deadbush||b==Blocks.red_flower||b==Blocks.yellow_flower;}
}
