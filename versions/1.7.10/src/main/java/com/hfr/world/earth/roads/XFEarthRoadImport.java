package com.hfr.world.earth.roads;

import com.hfr.clowder.ClowderTerritory;
import com.hfr.config.XFConfig;
import com.hfr.world.earth.*;
import com.hfr.world.earth.geo.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/** One bounded reviewed import. Minecraft ownership stays on the shared server queue. */
public final class XFEarthRoadImport implements Runnable {
    private static XFEarthRoadImport active;
    private final WorldServer world;
    private final UUID operator;
    private final XFEarthProfile profile;
    private final XFEarthRoadSource.Policy policy;
    private final XFEarthFeatureSource.Policy featurePolicy;
    private final boolean featuresOnly;
    private final File source;
    private final int x0,z0,x1,z1;
    private final List<Reviewed> preview=new ArrayList<Reviewed>();
    private final String token=UUID.randomUUID().toString();
    private volatile boolean cancelled;
    private volatile String state="PREPARING";
    private volatile int edits,skipped,placed,eligible,water,steep;
    private String identity,acknowledged;
    private boolean inspected;
    private long reviewedAt;
    private Thread worker;
    private volatile CompletableFuture<Void> pending;
    private final List<String> findings=new ArrayList<String>();
    private XFEarthRoadImport(EntityPlayerMP player,int x0,int z0,int x1,int z1,boolean featuresOnly)throws IOException{
        world=(WorldServer)player.worldObj;operator=player.getUniqueID();XFEarthProfile current=XFEarthProfileLoader.forWorld(world);
        profile=current==null?null:new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(current),XFEarthProfile.class);
        if(profile==null)throw new IOException("An adopted Earth overworld is required");profile.validate("1.7.10");profile.requireGameplayHeight();
        this.x0=x0;this.z0=z0;this.x1=x1;this.z1=z1;source=XFEarthRoadSource.configuredDirectory();policy=new XFEarthRoadSource.Policy();
        this.featuresOnly=featuresOnly;featurePolicy=new XFEarthFeatureSource.Policy();
        if(featuresOnly&&!featurePolicy.enabled)throw new IOException("Geographic feature generation is disabled");
    }
    private static final class Reviewed {final Snapshot snapshot;final XFEarthRoadPlanner.Plan plan;Reviewed(Snapshot s,XFEarthRoadPlanner.Plan p){snapshot=s;plan=p;}}
    private static final class Snapshot implements XFEarthRoadPlanner.Terrain {
        final int cx,cz;final String baseline;final XFEarthRoadState.Stamp stamp;
        final byte[][] low=new byte[16][],high=new byte[16][],meta=new byte[16][];
        final int[] heights=new int[24*24];final boolean[] wet=new boolean[24*24],protectedColumns=new boolean[256];
        String fingerprint;
        Snapshot(Chunk c,XFEarthRoadState.Stamp stamp){cx=c.xPosition;cz=c.zPosition;this.stamp=stamp.copy();baseline=stamp.baseline;Arrays.fill(heights,-1);}
        int at(int x,int z){int lx=x-cx*16+4,lz=z-cz*16+4;return lx<0||lx>=24||lz<0||lz>=24?-1:lz*24+lx;}
        public int height(int x,int z){int i=at(x,z);return i<0?-1:heights[i];}
        public boolean wet(int x,int z){int i=at(x,z);return i<0||wet[i];}
        public boolean protectedColumn(int x,int z){return protectedColumns[(z&15)*16+(x&15)];}
        private int offset(int x,int y,int z){return (y&15)*256+(z&15)*16+(x&15);}
        private int nibble(byte[] bytes,int i){return bytes==null?0:(bytes[i>>1]>>((i&1)*4))&15;}
        public Block block(int x,int y,int z){if(y<0||y>255)return Blocks.air;int s=y>>4,i=offset(x,y,z);return Block.getBlockById((low[s]==null?0:low[s][i]&255)|(nibble(high[s],i)<<8));}
        public int metadata(int x,int y,int z){return y<0||y>255?0:nibble(meta[y>>4],offset(x,y,z));}
    }
    public static void command(ICommandSender sender,String[] args)throws CommandException{
        if(!sender.canCommandSenderUseCommand(4,"xc"))throw new CommandException("Road administration requires permission level 4");
        if(!(sender instanceof EntityPlayerMP))throw new CommandException("Road previews and backup acknowledgement require an in-world administrator");
        EntityPlayerMP player=(EntityPlayerMP)sender;
        if(active!=null&&args.length>1&&!"preview".equalsIgnoreCase(args[1])&&!"status".equalsIgnoreCase(args[1])
                &&active.featuresOnly!="features".equalsIgnoreCase(args[0]))throw new CommandException("Use the current job's /xc earth "+active.mode()+" command tree");
        try{
            if(args.length==2&&"status".equalsIgnoreCase(args[1])){message(sender,active==null?"Road import idle":active.status());return;}
            if((args.length==3||args.length==4)&&"inspect".equalsIgnoreCase(args[1])){
                XFEarthRoadImport job=owned(player);job.reviewed(args[2]);message(sender,job.status());
                int offset=args.length==4?Integer.parseInt(args[3]):0;if(offset<0||offset>job.edits)throw new IOException("Invalid preview block offset");
                if(offset==0)for(String finding:job.findings)message(sender,finding);int count=0;
                for(Reviewed item:job.preview)for(XFEarthRoadPlanner.Edit e:item.plan.edits){if(count>=offset&&count<offset+12)message(sender,"Planned "+e.x+","+e.y+","+e.z+": "+e.before.getUnlocalizedName()+" -> "+e.after.getUnlocalizedName());count++;}
                message(sender,"Changed blocks "+offset+".."+Math.min(job.edits,offset+12)+" of "+job.edits+"; inspect the same token with an optional block offset to review more.");job.inspected=true;return;
            }
            if(args.length==2&&"cancel".equalsIgnoreCase(args[1])){owned(player).cancel();message(sender,"Road import cancelled. Blocks already placed are NOT undone; restore the manual backup if needed.");return;}
            if(args.length==6&&"preview".equalsIgnoreCase(args[1])){
                int x0=Integer.parseInt(args[2]),z0=Integer.parseInt(args[3]),x1=Integer.parseInt(args[4]),z1=Integer.parseInt(args[5]);
                if(x1<x0||z1<z0||((long)x1-x0+1)*((long)z1-z0+1)>XFConfig.earthRoadMaxImportChunks)throw new IOException("Preview exceeds bounded chunk rectangle");
                if(active!=null&&(active.worker!=null&&active.worker.isAlive()||"APPLYING".equals(active.state)))throw new IOException("A road job is running");
                active=new XFEarthRoadImport(player,x0,z0,x1,z1,"features".equalsIgnoreCase(args[0]));
                if(!active.profile.getBounds().containsChunk(x0,z0)||!active.profile.getBounds().containsChunk(x1,z1))throw new IOException("Preview is outside the active Earth bounds");
                active.worker=new Thread(active,"XF road preview");active.worker.setDaemon(true);active.worker.start();message(sender,"Read-only road preview started; no chunks will be force-loaded.");return;
            }
            if(args.length==3&&"backup".equalsIgnoreCase(args[1])){
                XFEarthRoadImport job=owned(player);job.reviewed(args[2]);if(!job.inspected)throw new IOException("Inspect the current preview before acknowledging backup");job.acknowledged=job.token;
                com.hfr.main.MainRegistry.logger.info("[XF ROADS] "+operatorName(player)+" states a current manual backup exists; world="+job.world.getSaveHandler().getWorldDirectory()+" job="+job.token+" at="+java.time.Instant.now());
                message(sender,"Manual backup acknowledgement bound to this world/job. It is an operator statement, not automated backup verification.");return;
            }
            if(args.length==3&&"apply".equalsIgnoreCase(args[1])){
                XFEarthRoadImport job=owned(player);job.reviewed(args[2]);
                if(job.featuresOnly?!XFConfig.earthFeatureExistingImports:!XFConfig.earthRoadExistingImports)throw new IOException("Existing-world application is disabled for this job type");
                if(!job.token.equals(job.acknowledged))throw new IOException("Acknowledge a current manual world backup with /xc earth "+job.mode()+" backup "+job.token);
                XFEarthRoadSource current=new XFEarthRoadSource(job.source,job.profile,new XFEarthRoadSource.Policy());
                String identity=job.featuresOnly?new XFEarthFeatureSource(current,new XFEarthFeatureSource.Policy()).identity:current.identity;
                if(!identity.equals(job.identity))throw new IOException("Dataset/policy changed; create a new preview");
                job.state="APPLYING";job.pending=XFEarthWorkQueue.submit(job.new Apply(player));
                job.pending.whenComplete(new java.util.function.BiConsumer<Void,Throwable>(){public void accept(Void ignored,Throwable failure){if(failure!=null&&!job.cancelled)job.state="FAILED: "+failure.getMessage();}});
                message(sender,"Applying reviewed preview under the shared road budget. Cancellation does not undo placed blocks.");return;
            }
        }catch(IOException|IllegalArgumentException e){throw new CommandException("Road import: "+e.getMessage());}
        throw new WrongUsageException("/xc earth <roads|features> preview <minChunkX> <minChunkZ> <maxChunkX> <maxChunkZ> | status | inspect <job-token> [block-offset] | cancel | backup <job-token> | apply <job-token>");
    }
    private static String operatorName(EntityPlayerMP p){return p.getCommandSenderName()+" ("+p.getUniqueID()+")";}
    private static XFEarthRoadImport owned(EntityPlayerMP player)throws IOException{
        if(active==null||active.world!=player.worldObj||!active.operator.equals(player.getUniqueID()))throw new IOException("No road preview belongs to this operator/world");return active;
    }
    private void reviewed(String supplied)throws IOException{if(cancelled||!"REVIEWED".equals(state)||!token.equals(supplied)||System.currentTimeMillis()-reviewedAt>300000)throw new IOException("Preview token is stale, expired or not ready; create a new preview");}
    private String mode(){return featuresOnly?"features":"roads";}
    private String status(){return mode()+" "+state+" job="+token+" chunks="+x0+","+z0+".."+x1+","+z1+" changes="+edits+" placed="+placed+" eligible columns="+eligible+" blocked chunks="+skipped+" water columns="+water+" steep columns="+steep+"; coverage unverified";}
    private static void message(ICommandSender sender,String text){sender.addChatMessage(new ChatComponentText(text));}
    private void check()throws InterruptedException{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException("Road job cancelled");}
    private void worldCheck()throws InterruptedException{
        if(cancelled||MinecraftServer.getServer()==null||MinecraftServer.getServer().worldServerForDimension(0)!=world||!MinecraftServer.getServer().isServerRunning())throw new InterruptedException("Road world/session ended");
    }
    private void await(CompletableFuture<Void> future)throws Exception{pending=future;try{for(;;){check();try{future.get(100,TimeUnit.MILLISECONDS);return;}catch(TimeoutException waiting){}}}finally{pending=null;}}
    public void run(){
        try{
            XFEarthRoadSource roads=new XFEarthRoadSource(source,profile,policy);XFEarthFeatureSource features=new XFEarthFeatureSource(roads,featurePolicy);identity=featuresOnly?features.identity:roads.identity;
            for(int cz=z0;cz<=z1;cz++)for(int cx=x0;cx<=x1;cx++){
                check();Capture capture=new Capture(cx,cz);await(XFEarthWorkQueue.submit(capture));
                Snapshot snapshot=capture.snapshot;
                if(snapshot==null||!snapshot.baseline.equals(snapshot.fingerprint)){skipped++;findings.add("Chunk "+cx+","+cz+": skipped (unloaded, unverified/edited, completed, pending, tile entity or baseline mismatch)");continue;}
                XFEarthRoadPlanner.Plan plan=featuresOnly?XFEarthFeaturePlanner.plan(features,cx,cz,snapshot,true):XFEarthRoadPlanner.plan(roads.cells(cx,cz),cx,cz,snapshot,profile,policy);
                if(edits+plan.edits.size()>65536)throw new IOException("Preview exceeds 65,536 changed-block limit");
                preview.add(new Reviewed(snapshot,plan));edits+=plan.edits.size();eligible+=plan.eligible;water+=plan.water;steep+=plan.steep;
                findings.add("Chunk "+cx+","+cz+": "+plan.edits.size()+" changes; "+plan.eligible+" road columns; protected/obstructed="+plan.blocked+" water="+plan.water+" steep="+plan.steep);
            }
            await(XFEarthWorkQueue.submit(new XFEarthWorkQueue.Step(){public boolean run()throws Exception{
                worldCheck();reviewedAt=System.currentTimeMillis();state="REVIEWED";
                net.minecraft.entity.player.EntityPlayer player=world.func_152378_a(operator);if(player!=null){message(player,status());message(player,"Review with /xc earth "+mode()+" inspect "+token+". If acceptable, acknowledge a current manual world backup with /xc earth "+mode()+" backup "+token+"; then apply the same token.");}
                return true;
            }}));
        }catch(Exception e){state=cancelled?"CANCELLED":"FAILED: "+e.getMessage();if(com.hfr.main.MainRegistry.logger!=null)com.hfr.main.MainRegistry.logger.warn("[XF ROADS] "+state,e);}
    }
    /** Captures loaded arrays/terrain/claims incrementally on the server; worker consumes detached snapshots. */
    private final class Capture implements XFEarthWorkQueue.Step {
        final int cx,cz;Chunk chunk;Snapshot snapshot;MessageDigest digest;int section,sample;boolean started;
        Capture(int cx,int cz){this.cx=cx;this.cz=cz;}
        public boolean run()throws Exception{
            worldCheck();if(!world.getChunkProvider().chunkExists(cx,cz)){snapshot=null;return true;}
            Chunk current=world.getChunkFromChunkCoords(cx,cz);
            if(!started){started=true;chunk=current;XFEarthRoadState.Stamp stamp=XFEarthRoadState.get(chunk);
                if(stamp==null||stamp.edited||!stamp.pending.isEmpty()||!(featuresOnly?stamp.featuresCompleted:stamp.completed).isEmpty()||!stamp.profile.equals(XFEarthGeoDataset.profileIdentity(profile))||!chunk.chunkTileEntityMap.isEmpty())return true;
                snapshot=new Snapshot(chunk,stamp);digest=XFEarthRoadState.digest();
            }
            if(snapshot==null)return true;if(current!=chunk){snapshot=null;return true;}
            if(section<16){ExtendedBlockStorage s=chunk.getBlockStorageArray()[section];XFEarthRoadState.section(digest,s);
                snapshot.low[section]=s==null?null:s.getBlockLSBArray().clone();snapshot.high[section]=s==null||s.getBlockMSBArray()==null?null:s.getBlockMSBArray().data.clone();snapshot.meta[section]=s==null?null:s.getMetadataArray().data.clone();section++;return false;}
            if(sample<24*24){int x=cx*16-4+sample%24,z=cz*16-4+sample/24;
                if(world.getChunkProvider().chunkExists(x>>4,z>>4)&&profile.containsMapPosition(x+.5,z+.5)){
                    Chunk c=world.getChunkFromChunkCoords(x>>4,z>>4);int y=c.getHeightValue(x&15,z&15)-1;
                    snapshot.heights[sample]=y;Block b=y>=profile.getMinimumY()?c.getBlock(x&15,y,z&15):Blocks.air;snapshot.wet[sample]=b.getMaterial()==Material.water||b==Blocks.ice;
                }sample++;return false;}
            for(int z=0;z<16;z++)for(int x=0;x<16;x++)snapshot.protectedColumns[z*16+x]=ClowderTerritory.getMetaFromIntCoords(world,cx*16+x,cz*16+z)!=null;
            digest.update(chunk.getBiomeArray());snapshot.fingerprint=XFEarthRoadState.finish(digest);return true;
        }
    }
    private final class Apply implements XFEarthWorkQueue.Step {
        final EntityPlayerMP player;int item,section,edit;Chunk chunk;MessageDigest digest;boolean began;
        Apply(EntityPlayerMP player){this.player=player;}
        public boolean run()throws Exception{
            try{
                worldCheck();if(player.worldObj!=world||player.isDead||world.func_152378_a(operator)!=player||!player.playerNetServerHandler.netManager.isChannelOpen()||!player.canCommandSenderUseCommand(4,"xc"))throw new IOException("Operator/world permission ended");
                if(item>=preview.size()){state="COMPLETE";message(player,status());return true;}
                Reviewed review=preview.get(item);Snapshot snapshot=review.snapshot;
                if(!world.getChunkProvider().chunkExists(snapshot.cx,snapshot.cz)){next();skipped++;return false;}
                Chunk current=world.getChunkFromChunkCoords(snapshot.cx,snapshot.cz);XFEarthRoadState.Stamp stamp=XFEarthRoadState.get(current);
                if(chunk==null){chunk=current;digest=XFEarthRoadState.digest();}
                if(current!=chunk||stamp==null||stamp.edited||!chunk.chunkTileEntityMap.isEmpty()||(!began&&(!stamp.pending.isEmpty()||!(featuresOnly?stamp.featuresCompleted:stamp.completed).isEmpty()))){next();skipped++;return false;}
                if(section<16){XFEarthRoadState.section(digest,chunk.getBlockStorageArray()[section++]);return false;}
                if(!began){digest.update(chunk.getBiomeArray());if(!snapshot.fingerprint.equals(XFEarthRoadState.finish(digest))){next();skipped++;return false;}
                    stamp.pending=token;chunk.setChunkModified();began=true;}
                if(edit>=review.plan.edits.size()){stamp.pending="";if(featuresOnly){stamp.featuresCompleted=identity;stamp.featureInfo.clear();stamp.featureInfo.addAll(review.plan.generatedFeatures);}else stamp.completed=identity;chunk.setChunkModified();next();return false;}
                XFEarthRoadPlanner.Edit e=review.plan.edits.get(edit++);
                if(ClowderTerritory.getMetaFromIntCoords(world,e.x,e.z)!=null||world.getBlock(e.x,e.y,e.z)!=e.before||world.getBlockMetadata(e.x,e.y,e.z)!=e.metadata
                        ||e.before.hasTileEntity(e.metadata)||world.getTileEntity(e.x,e.y,e.z)!=null){stamp.edited=true;next();skipped++;return false;}
                if(!world.setBlock(e.x,e.y,e.z,e.after,e.afterMetadata,2))throw new IOException("Minecraft refused a reviewed geographic block");
                com.hfr.clowder.ClaimTerrainService.invalidate(world,e.x,e.z);placed++;return false;
            }catch(Exception e){state=cancelled?"CANCELLED":"FAILED: "+e.getMessage();message(player,state+". Blocks already placed are not undone.");throw e;}
        }
        private void next(){item++;section=edit=0;chunk=null;digest=null;began=false;}
    }
    private void cancel(){cancelled=true;state="CANCELLED";acknowledged=null;if(worker!=null)worker.interrupt();if(pending!=null)pending.cancel(false);}
    public static void cancelAll(){if(active!=null)active.cancel();active=null;}
}
