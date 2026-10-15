package com.hfr.clowder;

import java.util.*;
import com.hfr.clowder.ClaimNodeSystem.*;
import com.hfr.clowder.ClowderTerritory.*;
import com.hfr.config.XFConfig;
import com.hfr.data.ClowderData;
import com.hfr.tileentity.clowder.*;
import com.hfr.world.earth.*;
import com.hfr.world.earth.roads.XFEarthWorkQueue;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.event.world.*;

/** Live terrain capture and claim publication run only in the shared owning-server tick queue. */
public final class ClaimTerrainService {
    private static final int MAX_RECORDS=4096,MAX_JOBS=8;
    private static final Map<World,State> worlds=new IdentityHashMap<World,State>();
    private static final Set<Job> jobs=new HashSet<Job>();
    private static final ClaimTerrainClassifier classifier=new ClaimTerrainClassifier();
    private static CoordPair refreshCursor;
    private static long ticks;
    private static final class State {
        final World world;final Store store;
        final LinkedHashMap<CoordPair,Record> records=new LinkedHashMap<CoordPair,Record>(64,.75F,true);
        final ArrayDeque<Record> waiting=new ArrayDeque<Record>();
        long epoch;boolean closed;
        State(World world){this.world=world;store=ClaimNodeSystem.store(world);}
    }
    private static final class Record {
        final CoordPair key;long version,observed=-1,nextTry,epoch;String policy="",reason="Terrain has not been sampled";
        boolean queued,running;ClaimTerrainClassifier.Result result;Intent intent;
        Record(CoordPair key){this.key=new CoordPair(key.dimensionId,key.x,key.z);}
    }
    private static final class Intent {
        final TileEntity source;final Clowder owner;final String name,cityId;final int x,y,z;
        Intent(TileEntity source,Clowder owner,String name,String cityId){this.source=source;this.owner=owner;this.name=name;this.cityId=cityId;x=source==null?0:source.xCoord;y=source==null?0:source.yCoord;z=source==null?0:source.zCoord;}
    }
    public static final class Decision {
        public final Eligibility eligibility;public final String reason;
        Decision(Eligibility eligibility,String reason){this.eligibility=eligibility;this.reason=reason;}
        public String rejection(){return (eligibility==Eligibility.INELIGIBLE?"Claim rejected: insufficient connected land/platform surface. ":"Claim deferred: ")+reason;}
    }
    private static State state(World world){
        Store store=ClaimNodeSystem.store(world);store.worldIdentity();State state=worlds.get(world);
        if(state==null||state.store!=store){if(state!=null)state.closed=true;state=new State(world);worlds.put(world,state);}return state;
    }
    private static Record record(State state,CoordPair key){
        Record record=state.records.get(key);if(record!=null)return record;
        if(state.records.size()>=MAX_RECORDS){Iterator<Record> i=state.records.values().iterator();int checked=0;boolean removed=false;
            while(i.hasNext()&&checked++<32){Record old=i.next();if(!old.running&&!old.queued&&old.intent==null){i.remove();removed=true;break;}}
            if(!removed)return null;
        }record=new Record(key);state.records.put(record.key,record);return record;
    }
    private static String policy(World world){
        XFEarthProfile profile=XFEarthProfileLoader.forWorld(world);
        return XFConfig.claimTerrainMinArea+":"+XFConfig.claimTerrainCoveragePercent+":"+XFConfig.claimTerrainMaxStep+":"
            +(profile==null?"ordinary:0:"+(world.getHeight()-1):profile.getProfile()+":"+profile.getMinimumY()+":"+profile.getMaximumY()+":"+profile.getSeaLevel()+":"+profile.getHeightImplementation());
    }
    private static boolean fresh(State s,Record r,String policy){return r.result!=null&&r.epoch==s.epoch&&r.policy.equals(policy)&&ticks>=r.observed&&ticks-r.observed<=XFConfig.claimTerrainMaxAgeTicks;}
    public static Decision decision(World world,CoordPair key){
        if(world==null||world.isRemote||key.dimensionId!=world.provider.dimensionId)return new Decision(Eligibility.UNCLASSIFIED,"Owning server dimension unavailable");
        try{
            State s=state(world);Record r=record(s,key);if(r==null)return new Decision(Eligibility.UNCLASSIFIED,"Bounded terrain queue is full; retry shortly");
            String policy=policy(world);if(fresh(s,r,policy))return new Decision(r.result.classification.eligibility,r.result.description());
            if(!r.running&&!r.queued&&ticks>=r.nextTry){
                if(!loaded(world,s.store.snapshot(key).geometry)){r.reason="Required terrain chunks are not loaded; stay nearby and retry";r.nextTry=ticks+40;return new Decision(Eligibility.UNCLASSIFIED,r.reason);}
                r.queued=true;s.waiting.add(r);r.reason="Terrain sampling queued; wait nearby, then retry placement";unknown(s,r.key);
            }
            return new Decision(Eligibility.UNCLASSIFIED,r.reason);
        }catch(IllegalStateException e){return new Decision(Eligibility.UNCLASSIFIED,e.getMessage());}
    }
    /** Existing faction ownership/war transfers are retained; only a new territorial grant needs eligibility. */
    public static boolean existingClaim(CoordPair key){TerritoryMeta meta=ClaimNodeSystem.metadata(key.dimensionId,key.x,key.z);return meta!=null&&meta.owner!=null&&meta.owner.zone==Zone.FACTION&&meta.owner.owner!=null;}
    public static boolean allowGrant(World world,CoordPair key,Clowder owner,int x,int y,int z,String name,String cityId){
        if(owner==null||existingClaim(key))return true;
        Decision decision=decision(world,key);if(decision.eligibility==Eligibility.ELIGIBLE)return true;
        if(decision.eligibility==Eligibility.UNCLASSIFIED){State s=worlds.get(world);Record r=s==null?null:s.records.get(key);
            if(r!=null&&world.getChunkProvider().chunkExists(x>>4,z>>4)){
                TileEntity source=world.getTileEntity(x,y,z);
                if(source instanceof TileEntityFlag||source instanceof TileEntityFlagBig||source instanceof TileEntityCap){
                    r.intent=new Intent(source,owner,name,cityId);
                    if(!r.running&&!r.queued){r.queued=true;s.waiting.add(r);}
                }
            }}
        return false;
    }
    private static void unknown(State s,CoordPair key){
        Snapshot snapshot=s.store.snapshot(key);if(snapshot.classification.eligibility!=Eligibility.UNCLASSIFIED)s.store.apply(snapshot.identity,Classification.unclassified());
    }
    /** Public owning-thread hook for bulk edits that do not post Forge block events. No world-sized iteration. */
    public static void invalidate(World world,int x,int z){
        if(world==null||world.isRemote)return;State s=worlds.get(world);if(s==null)return;
        s.store.worldIdentity();
        Set<CoordPair> keys=new HashSet<CoordPair>();changedKeys(world.provider.dimensionId,x,z,keys);invalidateKeys(s,keys);
    }
    private static void changedKeys(int dimension,int x,int z,Set<CoordPair> keys){
        // A three-column interval can touch at most two units per axis, including the larger zero unit.
        for(int dx=-1;dx<=1;dx+=2)for(int dz=-1;dz<=1;dz+=2)keys.add(ClaimNodeSystem.protectionCoordinate(dimension,x+dx,z+dz));
    }
    private static void invalidateKeys(State s,Set<CoordPair> keys){
        World world=s.world;
        for(CoordPair key:keys){Record r=s.records.get(key);if(r!=null){r.version++;r.result=null;r.nextTry=0;r.reason="Terrain changed; refresh required";decision(world,key);}
            else if(s.store.tracked(key)){unknown(s,key);decision(world,key);}}
    }
    public static void tick(){
        ticks++;
        World overworld=DimensionManager.getWorld(0);
        if(overworld!=null&&ticks%20==0){try{
            Store store=ClaimNodeSystem.store(overworld);for(CoordPair key:store.nextTracked(refreshCursor,XFConfig.claimTerrainRefreshNodes)){
                refreshCursor=key;World world=DimensionManager.getWorld(key.dimensionId);if(world!=null)decision(world,key);
            }
        }catch(IllegalStateException ignored){/* Unknown node schema stays untouched. */}}
        for(State s:worlds.values()){
            if(s.closed)continue;int left=MAX_JOBS;
            while(left-->0&&!s.waiting.isEmpty()&&jobs.size()<MAX_JOBS){Record r=s.waiting.remove();r.queued=false;
                if(ticks<r.nextTry){r.queued=true;s.waiting.add(r);continue;}
                final Job job;
                try{job=new Job(s,r);}catch(RuntimeException failure){r.result=null;r.intent=null;r.nextTry=ticks+40;r.reason="Terrain capture unavailable: "+failure.getClass().getSimpleName();continue;}
                r.running=true;jobs.add(job);r.reason="Terrain sampling in progress; wait nearby, then retry placement";
                XFEarthWorkQueue.submit(job).whenComplete((done,failure)->{jobs.remove(job);r.running=false;if(failure!=null&&!s.closed){r.result=null;r.nextTry=ticks+40;r.reason="Terrain queue interrupted/full; retry shortly";
                    if(r.intent!=null&&!r.queued){r.queued=true;s.waiting.add(r);}}});
            }
        }
    }
    private static final class Job implements XFEarthWorkQueue.Step {
        final State s;final Record r;final long version,epoch;final Identity identity;final Geometry geometry;
        ClaimTerrainClassifier.Terrain terrain;String policy;int minY,maxY,column,y,x,z;
        boolean water,submerged,candidate;boolean started;
        final List<AxisAlignedBB> boxes=new ArrayList<AxisAlignedBB>();
        Job(State s,Record r){this.s=s;this.r=r;version=r.version;epoch=s.epoch;Snapshot n=s.store.snapshot(r.key);identity=n.identity;geometry=n.geometry;}
        public boolean run(){
            if(s.closed||!ClowderData.isActiveClaimNodeStore(s.store))return true;
            Intent intent=r.intent;
            if(intent!=null&&(intent.source.isInvalid()||intent.source.getWorldObj()!=s.world||intent.source.xCoord!=intent.x||intent.source.yCoord!=intent.y||intent.source.zCoord!=intent.z
                ||!s.world.getChunkProvider().chunkExists(intent.x>>4,intent.z>>4)||((ITerritoryProvider)intent.source).getOwner()!=intent.owner
                ||intent.source instanceof TileEntityFlag&&!((TileEntityFlag)intent.source).isClaimed))r.intent=null;
            if(version!=r.version||epoch!=s.epoch)return defer("Terrain changed during sampling; retry",true);
            try{
                if(!started){XFEarthProfile p=XFEarthProfileLoader.forWorld(s.world);minY=p==null?0:p.getMinimumY();maxY=p==null?s.world.getHeight()-1:p.getMaximumY();
                    if(p!=null)p.requireGameplayHeight();if(minY!=0||maxY>255||maxY<1)return defer("Active height implementation has no supported terrain capture path");
                    if(geometry.minX<=-30000000||geometry.maxX>=30000000||geometry.minZ<=-30000000||geometry.maxZ>=30000000)return defer("Node footprint exceeds supported world coordinates");
                    if(!loaded(s.world,geometry))return defer("Required node/one-block-halo chunks are not loaded; stay nearby and retry");
                    policy=policy(s.world);terrain=new ClaimTerrainClassifier.Terrain((int)(geometry.maxX-geometry.minX),(int)(geometry.maxZ-geometry.minZ),XFConfig.claimTerrainMinArea,XFConfig.claimTerrainCoveragePercent,XFConfig.claimTerrainMaxStep,policy,ticks);
                    started=true;beginColumn();return false;
                }
                if(!loaded(s.world,geometry))return defer("Required terrain unloaded during sampling; stay nearby and retry");
                if(column==terrain.kind.length){
                    if(!policy.equals(policy(s.world)))return defer("Terrain policy or active profile changed");
                    r.result=classifier.evaluate(identity,geometry,terrain);r.observed=ticks;r.epoch=s.epoch;r.policy=policy;r.reason=r.result.description();
                    // Persist the completion tick, not the beginning of a multi-tick capture.
                    Classification c=r.result.classification;s.store.apply(identity,new Classification(c.eligibility,c.algorithm,c.revision,Math.max(0,s.world.getTotalWorldTime()),c.evidence));
                    grantIntent();return true;
                }
                if(y<minY){finishColumn();return false;}
                Block block=s.world.getBlock(x,y,z);Material material=block.getMaterial();
                if(block==Blocks.air){y--;return false;}
                if(material.isLiquid()){
                    if(candidate){terrain.kind[column]=material==Material.water?ClaimTerrainClassifier.PLATFORM:ClaimTerrainClassifier.LAND;finishColumn();return false;}
                    water=true;y--;return false;
                }
                boxes.clear();
                block.addCollisionBoxesToList(s.world,x,y,z,AxisAlignedBB.getBoundingBox(x,y,z,x+1,y+2,z+1),boxes,null);
                if(boxes.size()>32)return defer("Block collision decomposition exceeds the supported 32-box bound");
                boolean solid=material.blocksMovement()&&!block.isReplaceable(s.world,x,y,z)&&material!=Material.leaves&&material!=Material.plants&&material!=Material.vine&&material!=Material.cactus&&material!=Material.gourd&&material!=Material.ice&&material!=Material.packedIce
                    &&!(block instanceof BlockBush)&&!(block instanceof IPlantable)&&!block.isLeaves(s.world,x,y,z)&&!block.isFoliage(s.world,x,y,z);
                double top=Double.NEGATIVE_INFINITY,highest=Double.NEGATIVE_INFINITY;
                for(AxisAlignedBB box:boxes){
                    if(box==null||!Double.isFinite(box.minX)||!Double.isFinite(box.minY)||!Double.isFinite(box.minZ)||!Double.isFinite(box.maxX)||!Double.isFinite(box.maxY)||!Double.isFinite(box.maxZ)||box.maxX<box.minX||box.maxY<box.minY||box.maxZ<box.minZ)return defer("Unsupported block collision geometry");
                    highest=Math.max(highest,box.maxY);
                    if(solid&&box.minX<=x+0.000001&&box.maxX>=x+1-0.000001&&box.minZ<=z+0.000001&&box.maxZ>=z+1-0.000001
                        &&box.minY>=y-0.000001&&box.maxY<=y+1.000001&&box.maxY-box.minY>=.125)top=Math.max(top,box.maxY);}
                if(solid&&top<highest-0.000001)top=fullTop(boxes,x,y,z,highest);
                if(candidate&&solid&&!boxes.isEmpty()){terrain.kind[column]=ClaimTerrainClassifier.LAND;finishColumn();return false;}
                if(water&&solid&&!boxes.isEmpty()){submerged=true;finishColumn();return false;}
                if(!candidate&&!water&&Double.isFinite(top)&&top>=highest-0.000001){candidate=true;terrain.top[column]=top;}
                y--;return false;
            }catch(RuntimeException unsupported){return defer("Terrain/block collision query unavailable: "+unsupported.getClass().getSimpleName());}
        }
        /** Union of coplanar support rectangles; permits modded solid decks split into several collision boxes. */
        private double fullTop(List<AxisAlignedBB> boxes,int x,int y,int z,double top){
            if(!Double.isFinite(top)||top>y+1.000001||top<y+.125)return Double.NEGATIVE_INFINITY;
            List<AxisAlignedBB> faces=new ArrayList<AxisAlignedBB>();double[] edges=new double[66];int count=2;edges[0]=z;edges[1]=z+1;
            for(AxisAlignedBB b:boxes)if(Math.abs(b.maxY-top)<=0.000001&&b.minY>=y-0.000001&&b.maxY-b.minY>=.125&&b.maxX>x&&b.minX<x+1&&b.maxZ>z&&b.minZ<z+1){
                faces.add(b);edges[count++]=Math.max(z,b.minZ);edges[count++]=Math.min(z+1,b.maxZ);}
            Collections.sort(faces,new Comparator<AxisAlignedBB>(){public int compare(AxisAlignedBB a,AxisAlignedBB b){return Double.compare(a.minX,b.minX);}});
            Arrays.sort(edges,0,count);
            for(int i=1;i<count;i++){if(edges[i]-edges[i-1]<=.000001)continue;double mid=(edges[i]+edges[i-1])*.5,covered=x;
                for(AxisAlignedBB b:faces)if(b.minZ<=mid&&b.maxZ>=mid){if(b.minX>covered+.000001)return Double.NEGATIVE_INFINITY;covered=Math.max(covered,b.maxX);if(covered>=x+1-.000001)break;}
                if(covered<x+1-.000001)return Double.NEGATIVE_INFINITY;
            }return top;
        }
        private void beginColumn(){if(column==terrain.kind.length)return;x=(int)geometry.minX+column%terrain.width;z=(int)geometry.minZ+column/terrain.width;
            Chunk chunk=s.world.getChunkFromChunkCoords(x>>4,z>>4);y=Math.min(maxY,chunk.getTopFilledSegment()+15);water=submerged=candidate=false;}
        private void finishColumn(){if(terrain.kind[column]==0)terrain.kind[column]=candidate?ClaimTerrainClassifier.LAND:submerged?ClaimTerrainClassifier.SUBMERGED:water?ClaimTerrainClassifier.WATER:ClaimTerrainClassifier.EMPTY;
            column++;if(column%16==0)r.reason="Terrain sampling in progress: "+column+"/"+terrain.kind.length+" columns; retry placement when complete";beginColumn();}
        private boolean defer(String reason){return defer(reason,false);}
        private boolean defer(String reason,boolean retry){r.result=null;r.reason=reason;r.nextTry=ticks+40;
            if((r.intent!=null||retry)&&!r.queued){r.queued=true;s.waiting.add(r);}return true;}
        private void grantIntent(){
            Intent intent=r.intent;r.intent=null;if(intent==null||r.result.classification.eligibility!=Eligibility.ELIGIBLE||intent.source==null||existingClaim(r.key))return;
            TileEntity tile=intent.source;if(tile.isInvalid()||tile.getWorldObj()!=s.world||tile.xCoord!=intent.x||tile.yCoord!=intent.y||tile.zCoord!=intent.z||!s.world.getChunkProvider().chunkExists(tile.xCoord>>4,tile.zCoord>>4)||s.world.getTileEntity(tile.xCoord,tile.yCoord,tile.zCoord)!=tile
                ||!(tile instanceof ITerritoryProvider)||((ITerritoryProvider)tile).getOwner()!=intent.owner||!CityCenterRelocationManager.isLiveFactionOwner(intent.owner,Clowder.clowders))return;
            TerritoryMeta current=ClaimNodeSystem.metadata(r.key.dimensionId,r.key.x,r.key.z);if(current!=null&&current.owner!=null&&current.owner.zone!=Zone.WILDERNESS)return;
            if(tile instanceof TileEntityFlag||tile instanceof TileEntityCap){
                for(int cx=(tile.xCoord-2)>>4;cx<=(tile.xCoord+2)>>4;cx++)for(int cz=(tile.zCoord-2)>>4;cz<=(tile.zCoord+2)>>4;cz++)if(!s.world.getChunkProvider().chunkExists(cx,cz))return;
                if(tile instanceof TileEntityFlag&&!((TileEntityFlag)tile).canSeeSky()||tile instanceof TileEntityCap&&!((TileEntityCap)tile).canSeeSky())return;
            }
            boolean wanted=false;
            if(tile instanceof TileEntityFlag){TileEntityFlag flag=(TileEntityFlag)tile;wanted=flag.isClaimed&&intent.cityId.equals(flag.getCityId())&&ClowderTerritory.getCityClaimCoordinates(r.key.dimensionId,tile.xCoord,tile.zCoord,flag.getRadius()).contains(r.key);}
            else if(tile instanceof TileEntityFlagBig)wanted=((TileEntityFlagBig)tile).claim.contains(r.key);
            else if(tile instanceof TileEntityCap){TileEntityCap cap=(TileEntityCap)tile;CoordPair origin=ClowderTerritory.getCoordPair(s.world,tile.xCoord,tile.zCoord);wanted=cap.progress>=TileEntityCap.maxProgress&&Math.hypot((double)r.key.x-origin.x,(double)r.key.z-origin.z)<cap.getRadius();}
            if(!wanted)return;
            String name=tile instanceof TileEntityFlag?((TileEntityFlag)tile).name:tile instanceof TileEntityFlagBig?((TileEntityFlagBig)tile).provinceName:tile instanceof TileEntityCap?((TileEntityCap)tile).name:intent.name;
            ClowderTerritory.setOwnerForCoord(s.world,r.key,intent.owner,tile.xCoord,tile.yCoord,tile.zCoord,name,intent.cityId);
            TerritoryMeta placed=ClaimNodeSystem.metadata(r.key.dimensionId,r.key.x,r.key.z);if(placed!=null&&tile instanceof TileEntityFlag){placed.cityLevel=((TileEntityFlag)tile).cityLevel.ordinal();placed.cityName=((TileEntityFlag)tile).name;}
        }
    }
    private static boolean loaded(World world,Geometry geometry){
        for(int cx=((int)geometry.minX-1)>>4;cx<=((int)geometry.maxX)>>4;cx++)for(int cz=((int)geometry.minZ-1)>>4;cz<=((int)geometry.maxZ)>>4;cz++)
            if(!world.getChunkProvider().chunkExists(cx,cz))return false;
        return true;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void place(BlockEvent.PlaceEvent e){if(e.world.isRemote||e.isCanceled())return;
        // The item validates and commits this provisional block after all placement listeners return.
        if(!(e instanceof BlockEvent.MultiPlaceEvent)&&com.hfr.items.ItemBlockCityCenter.isFoundingPlacement(e.blockSnapshot))return;
        if(!(e instanceof BlockEvent.MultiPlaceEvent)){invalidate(e.world,e.x,e.z);return;}
        State s=worlds.get(e.world);if(s==null)return;s.store.worldIdentity();
        List<net.minecraftforge.common.util.BlockSnapshot> changes=((BlockEvent.MultiPlaceEvent)e).getReplacedBlockSnapshots();
        if(changes.size()>4096){s.epoch++;return;}Set<CoordPair> keys=new HashSet<CoordPair>();
        for(net.minecraftforge.common.util.BlockSnapshot b:changes){changedKeys(e.world.provider.dimensionId,b.x,b.z,keys);if(keys.size()>256){s.epoch++;return;}}
        invalidateKeys(s,keys);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void broken(BlockEvent.BreakEvent e){if(!e.isCanceled())invalidate(e.world,e.x,e.z);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void explosion(ExplosionEvent.Detonate e){
        if(e.world.isRemote)return;State s=worlds.get(e.world);if(s==null)return;
        s.store.worldIdentity();
        if(e.getAffectedBlocks().size()>4096){s.epoch++;return;}Set<CoordPair> keys=new HashSet<CoordPair>();
        for(ChunkPosition p:e.getAffectedBlocks()){changedKeys(e.world.provider.dimensionId,p.chunkPosX,p.chunkPosZ,keys);if(keys.size()>256){s.epoch++;return;}}
        invalidateKeys(s,keys);
    }
    @SubscribeEvent public void chunkUnload(ChunkEvent.Unload e){
        if(e.world.isRemote)return;State s=worlds.get(e.world);if(s==null)return;s.store.worldIdentity();
        // Only footprints (including their collision halo) touching this chunk become stale.
        // A distant player's chunk unload must not restart this player's terrain capture.
        int x=e.getChunk().xPosition<<4,z=e.getChunk().zPosition<<4,dim=e.world.provider.dimensionId;
        CoordPair first=ClaimNodeSystem.protectionCoordinate(dim,x-1,z-1),last=ClaimNodeSystem.protectionCoordinate(dim,x+16,z+16);
        for(int nx=first.x;nx<=last.x;nx++)for(int nz=first.z;nz<=last.z;nz++){
            CoordPair key=new CoordPair(dim,nx,nz);Record r=s.records.get(key);
            if(r!=null){r.version++;r.result=null;r.nextTry=0;r.reason="Required terrain unloaded; stay nearby and retry";}
            if(r!=null||s.store.tracked(key))unknown(s,key);
        }
    }
    @SubscribeEvent public void unload(WorldEvent.Unload e){if(e.world.isRemote)return;State state=worlds.remove(e.world);if(state!=null)state.closed=true;}
    public static void clear(){for(State state:worlds.values())state.closed=true;worlds.clear();jobs.clear();refreshCursor=null;ticks=0;}
}
