package com.hfr.world.earth.geo;

import com.google.gson.JsonObject;
import com.hfr.config.XFConfig;
import com.hfr.main.MainRegistry;
import com.hfr.world.earth.XFEarthProfile;
import com.hfr.world.earth.XFEarthProfileLoader;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import net.minecraft.world.World;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import com.hfr.tdm.TDMServerTaskQueue;

/** Start/root resolution and completion callbacks own world access; workers parse detached files only. */
public final class XFEarthGeoImportJob implements Runnable,XFEarthGeoImporter.Control {
    private static XFEarthGeoImportJob active;
    private volatile World owner; // Identity token only: never dereferenced by the worker; released on completion.
    private final File source,target,save;
    private final String operation;
    private ICommandSender sender; // Released into the server-thread completion callback.
    private final XFEarthProfile profile;
    private final XFEarthGeoImporter.Limits limits;
    private final Thread worker;
    private volatile boolean cancelled;
    private volatile String state="STARTING";
    private volatile long records,imported,rejected;
    private XFEarthGeoImportJob(World owner,File source,File target,File save,XFEarthProfile profile,XFEarthGeoImporter.Limits limits,String operation,ICommandSender sender) {
        this.owner=owner;this.source=source;this.target=target;this.save=save;this.profile=profile;this.limits=limits;this.operation=operation;this.sender=sender;
        worker=new Thread(this,"XF geographic import");worker.setDaemon(true);
    }
    public static synchronized void start(World world,String id,String operation,ICommandSender sender) throws IOException {
        if(!java.util.Arrays.asList("import","select","info","status").contains(operation))throw new IllegalArgumentException("Unsupported geographic operation");
        if(!"status".equals(operation))XFEarthGeoImporter.validateId(id);
        if(world==null||world.isRemote)throw new IOException("Geographic imports require the owning server thread");
        if(active!=null&&active.worker.isAlive())throw new IOException("A geographic operation is already running; /xc earth geo status or geo cancel");
        XFEarthProfile current=XFEarthProfileLoader.forWorld(world);
        if(current==null)throw new IOException("Geographic imports require an adopted Earth overworld and its validated profile");
        XFEarthProfile snapshot=XFEarthGeoJson.GSON.fromJson(XFEarthGeoJson.GSON.toJson(current),XFEarthProfile.class);snapshot.validate("1.7.10");
        File root=dataRoot(world);
        File sources=XFConfig.earthGeographicSourceDirectory.trim().isEmpty()?new File("xenofactions/earthmaps/geographic-sources"):new File(XFConfig.earthGeographicSourceDirectory);
        XFEarthGeoImporter.Limits limits=new XFEarthGeoImporter.Limits(XFConfig.earthGeoMaxFeatureBytes,XFConfig.earthGeoMaxVertices,XFConfig.earthGeoOpenRegionFiles,XFConfig.earthGeoMaxRegionsPerFeature);
        active=new XFEarthGeoImportJob(world,new File(sources,id).getCanonicalFile(),new File(root,id).getCanonicalFile(),world.getSaveHandler().getWorldDirectory(),snapshot,limits,operation,sender);
        active.worker.start();
    }
    /** Configured root is namespaced by save path to prevent cross-world dataset reuse. */
    public static File dataRoot(World world) throws IOException {
        if(world==null||world.isRemote||world.provider.dimensionId!=0)throw new IOException("Geographic data belongs to the server overworld");
        File save=world.getSaveHandler().getWorldDirectory().getCanonicalFile();
        if(XFConfig.earthGeographicDataDirectory.trim().isEmpty())return new File(save,"data/xfearth-geography");
        return new File(new File(XFConfig.earthGeographicDataDirectory),XFEarthGeoJson.hash(save.getPath()));
    }
    public static synchronized String status() {return active==null?"idle":active.state+"; "+active.operation+" dataset="+active.target.getName()+" records="+active.records+" imported="+active.imported+" rejected="+active.rejected;}
    public static synchronized void cancel() {if(active!=null&&active.worker.isAlive())active.requestCancel();}
    private synchronized void requestCancel(){cancelled=true;worker.interrupt();}
    public static synchronized void stopForWorld(World world) {if(active!=null&&active.owner==world)cancel();}
    public static synchronized void shutdown(){cancel();}
    @Override public void check() throws IOException {if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Geographic import cancelled; incomplete output is retained outside the repository");}
    @Override public void progress(long records,long imported,long rejected){this.records=records;this.imported=imported;this.rejected=rejected;}
    @Override public synchronized void publish(File partial,File destination) throws IOException {
        check();java.nio.file.Files.move(partial.toPath(),destination.toPath()); // No replacement of concurrent destinations.
    }
    @Override public void run() {
        state=operation.toUpperCase(java.util.Locale.ROOT);
        final World owningWorld=owner;
        try {
            if("import".equals(operation)){
                JsonObject manifest=XFEarthGeoImporter.importDataset(source,target,profile,limits,this);
                state="COMPLETE; published "+target+"; select explicitly with /xc earth geo select "+target.getName();
                if(MainRegistry.logger!=null)MainRegistry.logger.info("[XF GEO] "+state+"; identity="+manifest.get("datasetIdentity").getAsString());
                complete(owningWorld,false,state);return;
            }
            if("status".equals(operation)){
                String selected;
                try{XFEarthRoadSource roads=XFEarthGeoSelection.open(save,profile,new XFEarthRoadSource.Policy());selected="Selected: "+roads.dataset.directory+"; identity="+roads.dataset.manifest.get("datasetIdentity").getAsString();}
                catch(IOException e){selected="Unavailable: "+e.getMessage();}
                String output;
                try{XFEarthGeoImporter.requireExternal(target.getParentFile());output="Import output root: "+target.getParentFile();}
                catch(IOException e){output=e.getMessage();}
                state="COMPLETE";
                complete(owningWorld,false,selected,"Source root (earthGeographicSourceDirectory): "+source.getParentFile()+"; acquire/export OSM externally, no automatic download.",
                    "Source files: <id>/source.json + features.geojsonseq. /xc earth geo import <id>, then /xc earth geo select <id>.",output+" (earthGeographicDataDirectory)",
                    "earthRoadDatasetDirectory="+XFConfig.earthRoadDatasetDirectory+" (nonblank overrides the saved world selection)");return;
            }
            XFEarthGeoDataset dataset=XFEarthGeoDataset.open(target,profile);
            if("select".equals(operation)){
                synchronized(this){check();if(!XFConfig.earthRoadDatasetDirectory.trim().isEmpty())throw new IOException("Explicit earthRoadDatasetDirectory overrides world selection; clear it in hfr.cfg and restart before /xc earth geo select <id>");XFEarthGeoSelection.save(save,dataset);}
                state="COMPLETE; selected "+target.getName();
                complete(owningWorld,true,state+"; source overlays available independently of block generation. No blocks placed.");
            }else{
                state="COMPLETE";
                complete(owningWorld,false,"Dataset "+target.getName()+"; identity="+dataset.manifest.get("datasetIdentity").getAsString()+"; imported="+dataset.manifest.get("imported").getAsLong(),
                    "Source snapshot: "+dataset.manifest.getAsJsonObject("source").get("snapshotTimestamp").getAsString(),dataset.manifest.get("attribution").getAsString()+" / ODbL-1.0: https://www.openstreetmap.org/copyright",
                    "Coverage is unverified; empty results mean no imported matches, not confirmed absence.");
            }
        } catch(IOException|RuntimeException e) {
            state=cancelled?"CANCELLED":"FAILED: "+e.getMessage()+"; /xc earth geo status shows setup paths";
            if(MainRegistry.logger!=null)MainRegistry.logger.warn("[XF GEO] "+state,e);
            complete(owningWorld,false,state);
        } finally {owner=null;}
    }
    private void complete(final World world,final boolean changed,final String... messages){
        final ICommandSender recipient=sender;sender=null;
        TDMServerTaskQueue.schedule(new Runnable(){public void run(){
            if(cancelled||net.minecraft.server.MinecraftServer.getServer()==null||net.minecraft.server.MinecraftServer.getServer().worldServerForDimension(0)!=world)return;
            if(changed){com.hfr.world.earth.roads.XFEarthRoadMap.selectionChanged(world);com.hfr.world.earth.roads.XFEarthRoadImport.cancelAll();}
            if(recipient!=null&&recipient.canCommandSenderUseCommand(4,"xc"))for(String message:messages)recipient.addChatMessage(new ChatComponentText(message));
        }});
    }
}
