package com.hfr.world.earth.roads;

import com.hfr.blocks.ModBlocks;
import com.hfr.world.earth.geo.*;
import java.awt.geom.*;
import java.io.IOException;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/** Source footprints with deterministic, generic shells/field appearances. No independent world queue. */
public final class XFEarthFeaturePlanner {
    private XFEarthFeaturePlanner(){}
    private static boolean inside(Area area,int x,int z){return area.contains(x,z,1,1);}
    public static XFEarthRoadPlanner.Plan plan(XFEarthFeatureSource source,int cx,int cz,XFEarthRoadPlanner.Terrain terrain,boolean existing)throws IOException{
        XFEarthRoadPlanner.Plan result=new XFEarthRoadPlanner.Plan();if(!source.policy.enabled)return result;
        List<XFEarthFeatureSource.Feature> candidates=source.query(cx*16-source.policy.span,cz*16-source.policy.span,cx*16+15+source.policy.span,cz*16+15+source.policy.span,9);
        Set<Long> occupied=new HashSet<Long>();
        for(XFEarthFeatureSource.Feature feature:candidates){
            XFEarthFeatureSource.check();Rectangle2D b=feature.bounds();
            int minX=(int)Math.floor(b.getMinX()),maxX=(int)Math.ceil(b.getMaxX())-1,minZ=(int)Math.floor(b.getMinY()),maxZ=(int)Math.ceil(b.getMaxY())-1;
            if(!feature.representable){
                if(feature.structure&&!feature.point)for(int z=Math.max(minZ,cz*16);z<=Math.min(maxZ,cz*16+15);z++)for(int x=Math.max(minX,cx*16);x<=Math.min(maxX,cx*16+15);x++)if(inside(feature.area,x,z))occupied.add(XFEarthRoadSource.key(x,z));
                result.blocked++;continue;
            }
            if(feature.structure&&existing&&((minX>>4)!=cx||(maxX>>4)!=cx||(minZ>>4)!=cz||(maxZ>>4)!=cz)){reserve(occupied,feature.area,cx,cz);result.blocked++;continue;}
            List<XFEarthRoadSource.Segment> roads=source.roads.query(Math.max(minX,cx*16)-4,Math.max(minZ,cz*16)-4,Math.min(maxX,cx*16+15)+4,Math.min(maxZ,cz*16+15)+4,9);
            // Buildings are admitted only on entirely dry, near-level footprints; no destructive flattening.
            int floor=-1,doorX=0,doorZ=0,count=0;boolean safe=true,interior=false;
            if(feature.structure){
                for(int z=minZ;z<=maxZ&&safe;z++)for(int x=minX;x<=maxX;x++)if(inside(feature.area,x,z)){
                    if(count++==0){doorX=x;doorZ=z;}interior|=inside(feature.area,x-1,z)&&inside(feature.area,x+1,z)&&inside(feature.area,x,z-1)&&inside(feature.area,x,z+1);
                    int h=terrain.height(x,z);if(h<source.roads.profile.getMinimumY()+1||terrain.wet(x,z)||!source.roads.profile.containsMapPosition(x+.5,z+.5)){safe=false;break;}
                    if(floor<0)floor=h;if(Math.abs(h-floor)>source.roads.policy.fill||h>floor){safe=false;break;}
                    if(existing&&terrain.protectedColumn(x,z)){safe=false;break;}
                }
                if(!safe||count<9||!interior||floor<0||floor+feature.height+1>source.roads.profile.getMaximumY()){reserve(occupied,feature.area,cx,cz);result.blocked++;continue;}
            }
            boolean used=false;
            for(int z=Math.max(minZ,cz*16);z<=Math.min(maxZ,cz*16+15);z++)for(int x=Math.max(minX,cx*16);x<=Math.min(maxX,cx*16+15);x++){
                if(!inside(feature.area,x,z))continue;long key=XFEarthRoadSource.key(x,z);if(!occupied.add(key))continue;
                if(terrain.protectedColumn(x,z)){result.blocked++;continue;}int h=terrain.height(x,z);
                if(terrain.wet(x,z)){result.water++;continue;}if(h<source.roads.profile.getMinimumY()+1){result.blocked++;continue;}
                boolean corridor=false;
                for(XFEarthRoadSource.Segment road:roads)if(XFEarthRoadSource.clip(road,x-road.width/2,z-road.width/2,x+1.0+road.width/2,z+1.0+road.width/2)!=null){corridor=true;break;}
                // No new connecting paths: only a source road touching a boundary opens an entrance/corridor.
                if(corridor)continue;
                int top=feature.structure?floor+feature.height:h+1;
                if(top>source.roads.profile.getMaximumY()){result.blocked++;continue;}
                boolean clear=true;for(int y=h;y<=top;y++)if(!XFEarthRoadPlanner.natural(terrain.block(x,y,z))){clear=false;break;}
                if(!clear){result.blocked++;continue;}
                if(feature.structure){
                    boolean wall=!inside(feature.area,x-1,z)||!inside(feature.area,x+1,z)||!inside(feature.area,x,z-1)||!inside(feature.area,x,z+1);
                    Block material="farm".equals(feature.appearance)||"workshop".equals(feature.appearance)?Blocks.planks:
                        "greenhouse".equals(feature.appearance)?Blocks.glass:Arrays.asList("shop","food","lodging").contains(feature.appearance)?Blocks.brick_block:Blocks.stonebrick;
                    for(int y=h;y<=top;y++){
                        Block after=y<floor?Blocks.stone:y==floor?Blocks.planks:y==top?("greenhouse".equals(feature.appearance)?Blocks.glass:Blocks.planks):wall?material:Blocks.air;
                        if(wall&&y==floor+2&&((x+z)&3)==0&&material!=Blocks.glass)after=Blocks.glass;
                        if(x==doorX&&z==doorZ&&(y==floor+1||y==floor+2))after=Blocks.air; // Procedural opening, not a surveyed entrance.
                        add(result,terrain,x,y,z,after,0);
                    }
                }else{
                    Block crop=null;if(source.roads.profile.getEffectiveScale()<=source.policy.scale&&"farmland".equals(feature.kind)){
                        if("wheat".equals(feature.crop))crop=Blocks.wheat;else if("potato".equals(feature.crop)||"potatoes".equals(feature.crop))crop=Blocks.potatoes;
                        else if("carrot".equals(feature.crop)||"carrots".equals(feature.crop))crop=Blocks.carrots;else if("rice".equals(feature.crop))crop=ModBlocks.rice;
                    }
                    Block ground="meadow".equals(feature.kind)?Blocks.grass:"farmyard".equals(feature.kind)?Blocks.gravel:crop!=null?Blocks.farmland:Math.floorMod(x,4)<2?Blocks.dirt:Blocks.grass;
                    add(result,terrain,x,h,z,ground,0);add(result,terrain,x,h+1,z,crop==null?Blocks.air:crop,crop==null?0:7);
                }
                used=true;result.eligible++;
            }
            if(used)result.generatedFeatures.add(feature.key+"|"+feature.kind+"|"+feature.appearance+(feature.point?"|procedural-point-kiosk":"|source-footprint"));
        }return result;
    }
    private static void add(XFEarthRoadPlanner.Plan plan,XFEarthRoadPlanner.Terrain terrain,int x,int y,int z,Block after,int metadata){
        Block old=terrain.block(x,y,z);int before=terrain.metadata(x,y,z);if(old!=after||before!=metadata)plan.edits.add(new XFEarthRoadPlanner.Edit(x,y,z,old,before,after,metadata));
    }
    private static void reserve(Set<Long> occupied,Area area,int cx,int cz){for(int z=cz*16;z<cz*16+16;z++)for(int x=cx*16;x<cx*16+16;x++)if(inside(area,x,z))occupied.add(XFEarthRoadSource.key(x,z));}
}
