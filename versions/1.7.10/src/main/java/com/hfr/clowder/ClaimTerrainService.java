package com.hfr.clowder;

import java.util.*;
import com.hfr.clowder.ClaimNodeSystem.*;
import com.hfr.clowder.ClowderTerritory.*;
import com.hfr.config.XFConfig;
import com.hfr.data.ClowderData;
import com.hfr.world.earth.roads.XFEarthWorkQueue;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.World;
import net.minecraft.world.ChunkPosition;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.*;

/** Optional terrain diagnostics run in the shared server queue; ownership uses the immediate surface check. */
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
        boolean queued,running;ClaimTerrainClassifier.Result result;
        Record(CoordPair key){this.key=new CoordPair(key.dimensionId,key.x,key.z);}
    }
    public static final class Decision {
        public final Eligibility eligibility;public final String reason;
        Decision(Eligibility eligibility,String reason){this.eligibility=eligibility;this.reason=reason;}
        public String rejection(){return (eligibility==Eligibility.INELIGIBLE?"Claim rejected: ":"Claim unavailable: ")+reason;}
    }
    private static State state(World world){
        Store store=ClaimNodeSystem.store(world);store.worldIdentity();State state=worlds.get(world);
        if(state==null||state.store!=store){if(state!=null)state.closed=true;state=new State(world);worlds.put(world,state);}return state;
    }
    private static Record record(State state,CoordPair key){
        Record record=state.records.get(key);if(record!=null)return record;
        if(state.records.size()>=MAX_RECORDS){Iterator<Record> i=state.records.values().iterator();int checked=0;boolean removed=false;
            while(i.hasNext()&&checked++<32){Record old=i.next();if(!old.running&&!old.queued){i.remove();removed=true;break;}}
            if(!removed)return null;
        }record=new Record(key);state.records.put(record.key,record);return record;
    }
    private static String policy(World world){
        return ClaimTerrainClassifier.ALGORITHM+":"+ClaimTerrainClassifier.REVISION+":"+world.getHeight();
    }
    private static boolean fresh(State s,Record r,String policy){return r.result!=null&&r.epoch==s.epoch&&r.policy.equals(policy)&&ticks>=r.observed&&ticks-r.observed<=XFConfig.claimTerrainMaxAgeTicks;}
    public static Decision decision(World world,CoordPair key){
        if(world==null||world.isRemote||key.dimensionId!=world.provider.dimensionId)return new Decision(Eligibility.UNCLASSIFIED,"Owning server dimension unavailable");
        try{
            State s=state(world);Record r=record(s,key);if(r==null)return new Decision(Eligibility.UNCLASSIFIED,"Bounded terrain queue is full; retry shortly");
            String policy=policy(world);if(fresh(s,r,policy))return new Decision(r.result.classification.eligibility,r.result.description());
            if(!r.running&&!r.queued&&ticks>=r.nextTry){
                r.queued=true;s.waiting.add(r);r.reason="Background terrain sampling queued";unknown(s,r.key);
            }
            return new Decision(Eligibility.UNCLASSIFIED,r.reason);
        }catch(IllegalStateException e){return new Decision(Eligibility.UNCLASSIFIED,e.getMessage());}
    }
    /** Existing faction ownership/war transfers are retained; only a new territorial grant needs eligibility. */
    public static boolean existingClaim(CoordPair key){TerritoryMeta meta=ClaimNodeSystem.metadata(key.dimensionId,key.x,key.z);return meta!=null&&meta.owner!=null&&meta.owner.zone==Zone.FACTION&&meta.owner.owner!=null;}
    /** Always observes loaded terrain now; background freshness and persisted evidence cannot authorize ownership. */
    public static Decision grantDecision(World world,CoordPair key){return ClaimSurfaceCheck.check(world,key);}
    public static boolean allowGrant(World world,CoordPair key,Clowder owner){
        return owner==null||existingClaim(key)||grantDecision(world,key).eligibility==Eligibility.ELIGIBLE;
    }
    /** Retain the old public signature; source metadata no longer creates a deferred grant. */
    public static boolean allowGrant(World world,CoordPair key,Clowder owner,int x,int y,int z,String name,String cityId){
        return allowGrant(world,key,owner);
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
        for(CoordPair key:keys){
            Record r=s.records.get(key);
            if(r==null&&s.store.tracked(key))r=record(s,key);
            if(r==null)continue;
            r.version++;r.result=null;r.nextTry=0;r.reason="Terrain changed; background refresh required";unknown(s,key);
            // Invalidation may run during placement. Leave profile/capture work to the tick queue.
            if(!r.running&&!r.queued){r.queued=true;s.waiting.add(r);}
        }
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
                try{job=new Job(s,r);}catch(RuntimeException failure){r.result=null;r.nextTry=ticks+40;r.reason="Terrain capture unavailable: "+failure.getClass().getSimpleName();continue;}
                r.running=true;jobs.add(job);r.reason="Background terrain sampling in progress";
                XFEarthWorkQueue.submit(job).whenComplete((done,failure)->{jobs.remove(job);r.running=false;
                    if(failure!=null&&!s.closed){r.result=null;r.nextTry=ticks+40;r.reason="Background terrain queue interrupted/full";}});
            }
        }
    }
    private static final class Job implements XFEarthWorkQueue.Step {
        final State s;final Record r;final long version,epoch;final Identity identity;final Geometry geometry;
        ClaimTerrainClassifier.Terrain terrain;String policy;int column;
        final ClaimSurfaceCheck surface;
        Job(State s,Record r){this.s=s;this.r=r;version=r.version;epoch=s.epoch;Snapshot n=s.store.snapshot(r.key);identity=n.identity;geometry=n.geometry;surface=new ClaimSurfaceCheck(s.world);}
        public boolean run(){
            if(s.closed||!ClowderData.isActiveClaimNodeStore(s.store))return true;
            if(version!=r.version||epoch!=s.epoch)return defer("Terrain changed during sampling; retry",true);
            try{
                if(terrain==null){
                    if(s.world.getHeight()<1||s.world.getHeight()>256)return defer("Unsupported loaded terrain height");
                    if(geometry.minX<=-30000000||geometry.maxX>=30000000||geometry.minZ<=-30000000||geometry.maxZ>=30000000)return defer("Node footprint exceeds supported world coordinates");
                    policy=policy(s.world);terrain=new ClaimTerrainClassifier.Terrain((int)(geometry.maxX-geometry.minX),(int)(geometry.maxZ-geometry.minZ),policy,ticks);
                    return false;
                }
                if(column==terrain.kind.length){
                    if(!policy.equals(policy(s.world)))return defer("Terrain policy or supported height changed");
                    r.result=classifier.evaluate(identity,geometry,terrain);r.observed=ticks;r.epoch=s.epoch;r.policy=policy;r.reason=r.result.description();
                    // Persist the completion tick, not the beginning of a multi-tick capture.
                    Classification c=r.result.classification;s.store.apply(identity,new Classification(c.eligibility,c.algorithm,c.revision,Math.max(0,s.world.getTotalWorldTime()),c.evidence));
                    return true;
                }
                byte kind=surface.column((int)geometry.minX+column%terrain.width,(int)geometry.minZ+column/terrain.width);
                terrain.kind[column++]=kind;
                // Diagnostics use exactly the grant rule and need no further observations once eligible.
                if(kind==ClaimTerrainClassifier.ELIGIBLE)column=terrain.kind.length;
                return false;
            }catch(RuntimeException unsupported){return defer("Terrain/block collision query unavailable: "+unsupported.getClass().getSimpleName());}
        }
        private boolean defer(String reason){return defer(reason,false);}
        private boolean defer(String reason,boolean retry){r.result=null;r.reason=reason;r.nextTry=ticks+40;
            if(retry&&!r.queued){r.queued=true;s.waiting.add(r);}return true;}
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
