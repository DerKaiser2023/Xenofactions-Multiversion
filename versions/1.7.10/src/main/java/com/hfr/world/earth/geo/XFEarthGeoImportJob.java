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

/** Only start/root resolution touch a world, on its server thread; the worker receives detached inputs. */
public final class XFEarthGeoImportJob implements Runnable,XFEarthGeoImporter.Control {
    private static XFEarthGeoImportJob active;
    private volatile World owner; // Identity token only: never dereferenced by the worker; released on completion.
    private final File source,target;
    private final XFEarthProfile profile;
    private final XFEarthGeoImporter.Limits limits;
    private final Thread worker;
    private volatile boolean cancelled;
    private volatile String state="STARTING";
    private volatile long records,imported,rejected;
    private XFEarthGeoImportJob(World owner,File source,File target,XFEarthProfile profile,XFEarthGeoImporter.Limits limits) {
        this.owner=owner;this.source=source;this.target=target;this.profile=profile;this.limits=limits;
        worker=new Thread(this,"XF geographic import");worker.setDaemon(true);
    }
    public static synchronized void start(World world,String id) throws IOException {
        XFEarthGeoImporter.validateId(id);
        if(world==null||world.isRemote)throw new IOException("Geographic imports require the owning server thread");
        if(active!=null&&active.worker.isAlive())throw new IOException("A geographic import is already running");
        XFEarthProfile current=XFEarthProfileLoader.forWorld(world);
        if(current==null)throw new IOException("Geographic imports require an adopted Earth overworld and its validated profile");
        XFEarthProfile snapshot=XFEarthGeoJson.GSON.fromJson(XFEarthGeoJson.GSON.toJson(current),XFEarthProfile.class);snapshot.validate("1.7.10");
        File root=dataRoot(world);XFEarthGeoImporter.requireExternal(root);
        File sources=XFConfig.earthGeographicSourceDirectory.trim().isEmpty()?new File("xenofactions/earthmaps/geographic-sources"):new File(XFConfig.earthGeographicSourceDirectory);
        XFEarthGeoImporter.Limits limits=new XFEarthGeoImporter.Limits(XFConfig.earthGeoMaxFeatureBytes,XFConfig.earthGeoMaxVertices,XFConfig.earthGeoOpenRegionFiles,XFConfig.earthGeoMaxRegionsPerFeature);
        active=new XFEarthGeoImportJob(world,new File(sources,id).getCanonicalFile(),new File(root,id).getCanonicalFile(),snapshot,limits);
        active.worker.start();
    }
    /** Configured root is namespaced by save path to prevent cross-world dataset reuse. */
    public static File dataRoot(World world) throws IOException {
        if(world==null||world.isRemote||world.provider.dimensionId!=0)throw new IOException("Geographic data belongs to the server overworld");
        File save=world.getSaveHandler().getWorldDirectory().getCanonicalFile();
        if(XFConfig.earthGeographicDataDirectory.trim().isEmpty())return new File(save,"data/xfearth-geography");
        return new File(new File(XFConfig.earthGeographicDataDirectory),XFEarthGeoJson.hash(save.getPath()));
    }
    public static synchronized String status() {return active==null?"idle":active.state+"; dataset="+active.target.getName()+" records="+active.records+" imported="+active.imported+" rejected="+active.rejected;}
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
        state="IMPORTING";
        try {
            JsonObject manifest=XFEarthGeoImporter.importDataset(source,target,profile,limits,this);state="COMPLETE";
            if(MainRegistry.logger!=null)MainRegistry.logger.info("[XF GEO] imported "+target.getName()+" to "+target+"; identity="+manifest.get("datasetIdentity").getAsString()
                    +"; "+XFEarthGeoImporter.ATTRIBUTION+" / "+XFEarthGeoImporter.LICENSE+"; source coverage remains unverified");
        } catch(IOException|RuntimeException e) {
            state=cancelled?"CANCELLED":"FAILED: "+e.getMessage();
            if(MainRegistry.logger!=null)MainRegistry.logger.warn("[XF GEO] "+state,e);
        } finally {owner=null;}
    }
}
