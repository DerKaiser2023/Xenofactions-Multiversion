package com.hfr.clowder;

import com.hfr.data.ClowderData;
import com.hfr.clowder.ClowderTerritory.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.World;

/** Persistent classification of the existing territorial units; ownership remains in ClowderTerritory. */
public final class ClaimNodeSystem {
    public static final String TAG="ClaimNodes";
    public static final int VERSION=1;
    private ClaimNodeSystem(){}
    public enum Eligibility { UNCLASSIFIED, ELIGIBLE, INELIGIBLE }
    public interface Classifier<T> { Classification classify(Identity node,Geometry geometry,T detachedTerrain); }
    public static final class Classification {
        public final Eligibility eligibility;
        public final String algorithm,evidence;
        public final int revision;
        public final long sampledTick;
        public Classification(Eligibility eligibility,String algorithm,int revision,long sampledTick,String evidence){
            if(eligibility==null||algorithm==null||evidence==null||sampledTick<0)throw new IllegalArgumentException("Invalid classification metadata");
            if(eligibility==Eligibility.UNCLASSIFIED){if(!algorithm.isEmpty()||revision!=0||!evidence.isEmpty())throw new IllegalArgumentException("Unclassified nodes have no inferred evidence");}
            else if(!algorithm.matches("[a-z0-9_:/.-]{1,128}")||revision<1||!evidence.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Classification needs a versioned algorithm and evidence hash");
            this.eligibility=eligibility;this.algorithm=algorithm;this.revision=revision;this.sampledTick=sampledTick;this.evidence=evidence;
        }
        public static Classification unclassified(){return new Classification(Eligibility.UNCLASSIFIED,"",0,0,"");}
    }
    public static final class Identity {
        public final UUID world;
        public final int dimension,x,z;
        public Identity(UUID world,int dimension,int x,int z){if(world==null)throw new IllegalArgumentException("Missing world identity");this.world=world;this.dimension=dimension;this.x=x;this.z=z;}
        public CoordPair coordinate(){return new CoordPair(dimension,x,z);}
        public String toString(){return world+":"+dimension+":"+x+","+z;}
        public boolean equals(Object value){if(!(value instanceof Identity))return false;Identity n=(Identity)value;return world.equals(n.world)&&dimension==n.dimension&&x==n.x&&z==n.z;}
        public int hashCode(){return Objects.hash(world,dimension,x,z);}
    }
    public static final class Geometry {
        public final long minX,minZ,maxX,maxZ; // Half-open, exact integer-block protection footprint.
        public Geometry(int x,int z){
            TerritoryCoordinateBounds.Bounds bx=TerritoryCoordinateBounds.forCoordinate(x),bz=TerritoryCoordinateBounds.forCoordinate(z);
            minX=bx.minInclusive+1;maxX=bx.maxExclusive+1;minZ=bz.minInclusive;maxZ=bz.maxExclusive;
        }
        public boolean intersects(long x0,long z0,long x1,long z1){return maxX>x0&&minX<x1&&maxZ>z0&&minZ<z1;}
    }
    public static final class Snapshot {
        public final Identity identity;public final Geometry geometry;public final Classification classification;
        public final Zone zone;public final String factionId,cityId;public final int color;
        Snapshot(Identity id,Classification classification,TerritoryMeta metadata){
            identity=id;geometry=new Geometry(id.x,id.z);this.classification=classification;
            Ownership o=metadata==null?null:metadata.owner;
            zone=o==null||o.zone==Zone.FACTION&&o.owner==null?Zone.WILDERNESS:o.zone;
            factionId=zone==Zone.FACTION?o.owner.uuid.toString():"";cityId=metadata==null?"":metadata.cityId;
            color=zone==Zone.FACTION?o.owner.color&0xffffff:zone==Zone.SAFEZONE?ClowderTerritory.SAFEZONE_COLOR:zone==Zone.WARZONE?ClowderTerritory.WARZONE_COLOR:ClowderTerritory.WILDERNESS_COLOR;
        }
    }
    /** One ownership lookup for all public territory adapters. No node allocation on protection hot paths. */
    public static TerritoryMeta metadata(int dimension,int x,int z){return ClowderTerritory.territories.get(new CoordPair(dimension,x,z));}
    public static CoordPair protectionCoordinate(int dimension,int blockX,int blockZ){return new CoordPair(dimension,blockX/16,(blockZ+1)/16);}
    public static Snapshot at(World world,int blockX,int blockZ){
        Store store=store(world);CoordPair key=protectionCoordinate(world.provider.dimensionId,blockX,blockZ);return store.snapshot(key);
    }
    public static Store store(World world){
        if(world==null||world.isRemote)throw new IllegalArgumentException("Claim nodes belong to the owning server world");
        Store store=ClowderData.getData(world).claimNodes();store.requireThread();return store;
    }
    /** Caller supplies the exemption decision made by the existing entity border handler. Never rewrite stored claim keys. */
    public static Snapshot atWrappedPosition(World world,double x,double z,boolean exempt){
        Store nodes=store(world);
        if(!Double.isFinite(x)||!Double.isFinite(z)||Math.abs(x)>Integer.MAX_VALUE-1.0||Math.abs(z)>Integer.MAX_VALUE-1.0)throw new IllegalArgumentException("Invalid claim position");
        if(world.provider.dimensionId==0&&!exempt&&com.hfr.world.border.EarthBoundaryManager.isBoundaryEnabled(world)){
            x=com.hfr.world.border.EarthBoundaryManager.wrapCoordinate(x,com.hfr.main.MainRegistry.borderNegX,com.hfr.main.MainRegistry.borderPosX);
            z=com.hfr.world.border.EarthBoundaryManager.wrapCoordinate(z,com.hfr.main.MainRegistry.borderNegZ,com.hfr.main.MainRegistry.borderPosZ);
        }
        if(!Double.isFinite(x)||!Double.isFinite(z))throw new IllegalArgumentException("Invalid wrapped claim position");
        return nodes.snapshot(protectionCoordinate(world.provider.dimensionId,(int)x,(int)z)); // Existing callers truncate entity positions; no new floor rule.
    }
    public static final class Store {
        private UUID world=UUID.randomUUID();
        private final Map<CoordPair,Classification> nodes=new HashMap<CoordPair,Classification>();
        private NBTBase preserved;
        private boolean needsSave=true;
        private Thread thread;
        private ClowderData owner;
        public void bind(ClowderData owner,World world){
            if(world.isRemote)return;if(thread==null)thread=Thread.currentThread();requireThread();if(this.owner==owner)return;this.owner=owner;
            if(preserved==null&&migrateOwnership())needsSave=true;if(needsSave&&preserved==null)owner.markDirty();needsSave=false;
        }
        private void requireThread(){if(thread==null||Thread.currentThread()!=thread)throw new IllegalStateException("Claim node access requires its owning server thread");}
        public boolean available(){return preserved==null;}
        private void requireAvailable(){if(!available())throw new IllegalStateException("Unsupported/malformed ClaimNodes data retained; classification unavailable");}
        public UUID worldIdentity(){requireThread();requireAvailable();return world;}
        private boolean migrateOwnership(){
            if(preserved!=null)return false;boolean changed=false;
            for(CoordPair key:ClowderTerritory.territories.keySet())if(!nodes.containsKey(key)){nodes.put(new CoordPair(key.dimensionId,key.x,key.z),Classification.unclassified());changed=true;}
            return changed;
        }
        public Snapshot snapshot(CoordPair key){requireThread();requireAvailable();Classification classification=nodes.get(key);
            return new Snapshot(new Identity(world,key.dimensionId,key.x,key.z),classification==null?Classification.unclassified():classification,metadata(key.dimensionId,key.x,key.z));}
        /** Stage 9 applies a detached result here on the server; this never changes faction ownership or permissions. */
        public void apply(Identity identity,Classification classification){
            requireThread();requireAvailable();if(!ClowderData.isActiveClaimNodeStore(this))throw new IllegalStateException("Claim node world/session ended");
            if(identity==null||classification==null||!world.equals(identity.world))throw new IllegalArgumentException("Classification belongs to another world");
            nodes.put(identity.coordinate(),classification);owner.markDirty();
        }
        public List<Snapshot> snapshot(int dimension,long x0,long z0,long x1,long z1,int limit){
            requireThread();requireAvailable();if(limit<1||limit>512)throw new IllegalArgumentException("Node viewport limit");
            if(x0<Integer.MIN_VALUE||z0<Integer.MIN_VALUE||x1>(long)Integer.MAX_VALUE||z1>(long)Integer.MAX_VALUE||x1<=x0||z1<=z0)throw new IllegalArgumentException("Invalid node viewport bounds");
            CoordPair first=protectionCoordinate(dimension,(int)x0,(int)z0),last=protectionCoordinate(dimension,(int)(x1-1),(int)(z1-1));
            if(((long)last.x-first.x+1)*((long)last.z-first.z+1)>65536)throw new IllegalArgumentException("Node viewport is too wide");
            List<Snapshot> result=new ArrayList<Snapshot>();
            outer:for(int x=first.x;x<=last.x;x++)for(int z=first.z;z<=last.z;z++){
                CoordPair key=new CoordPair(dimension,x,z);if(nodes.containsKey(key)||ClowderTerritory.territories.containsKey(key)){result.add(snapshot(key));if(result.size()>=limit)break outer;}
            }return Collections.unmodifiableList(result);
        }
        public void read(NBTTagCompound root){
            nodes.clear();preserved=null;needsSave=!root.hasKey(TAG);if(!root.hasKey(TAG))return;
            NBTTagCompound tag=root.getCompoundTag(TAG);
            try{
                if(!root.hasKey(TAG,10)||tag.getInteger("version")!=VERSION||!tag.hasKey("world",8)||!tag.hasKey("nodes",9))throw new IllegalArgumentException("Unsupported node schema");
                world=UUID.fromString(tag.getString("world"));NBTTagList list=tag.getTagList("nodes",10);
                NBTTagList original=(NBTTagList)tag.getTag("nodes");if(original.tagCount()!=list.tagCount())throw new IllegalArgumentException("Invalid node list type");
                for(int i=0;i<list.tagCount();i++){
                    NBTTagCompound n=list.getCompoundTagAt(i);for(String name:new String[]{"dimension","x","z","revision"})if(!n.hasKey(name,3))throw new IllegalArgumentException("Incomplete node key/classification");
                    if(!n.hasKey("tick",4)||!n.hasKey("eligibility",8)||!n.hasKey("algorithm",8)||!n.hasKey("evidence",8))throw new IllegalArgumentException("Incomplete node evidence");
                    CoordPair key=new CoordPair(n.getInteger("dimension"),n.getInteger("x"),n.getInteger("z"));
                    Classification value=new Classification(Eligibility.valueOf(n.getString("eligibility")),n.getString("algorithm"),n.getInteger("revision"),n.getLong("tick"),n.getString("evidence"));
                    if(nodes.put(key,value)!=null)throw new IllegalArgumentException("Duplicate node key");
                }
            }catch(RuntimeException unsupported){nodes.clear();preserved=root.getTag(TAG).copy();if(com.hfr.main.MainRegistry.logger!=null)com.hfr.main.MainRegistry.logger.warn("[XF CLAIM NODES] Existing node payload retained; classification unavailable: "+unsupported.getMessage());}
        }
        public void write(NBTTagCompound root){
            if(thread!=null)requireThread();
            if(preserved!=null){root.setTag(TAG,preserved.copy());return;}
            migrateOwnership();NBTTagCompound tag=new NBTTagCompound();tag.setInteger("version",VERSION);tag.setString("world",world.toString());NBTTagList list=new NBTTagList();
            for(Map.Entry<CoordPair,Classification> entry:nodes.entrySet()){
                CoordPair key=entry.getKey();Classification value=entry.getValue();NBTTagCompound n=new NBTTagCompound();n.setInteger("dimension",key.dimensionId);n.setInteger("x",key.x);n.setInteger("z",key.z);
                n.setString("eligibility",value.eligibility.name());n.setString("algorithm",value.algorithm);n.setInteger("revision",value.revision);n.setLong("tick",value.sampledTick);n.setString("evidence",value.evidence);list.appendTag(n);
            }tag.setTag("nodes",list);root.setTag(TAG,tag);
        }
    }
}
