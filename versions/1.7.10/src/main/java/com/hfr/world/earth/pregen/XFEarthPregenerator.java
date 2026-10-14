package com.hfr.world.earth.pregen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hfr.config.XFConfig;
import com.hfr.main.MainRegistry;
import com.hfr.tdm.TDMServerTaskQueue;
import com.hfr.world.earth.XFEarthProfile;
import com.hfr.world.earth.XFEarthBounds;
import com.hfr.world.earth.pack.XFEarthMapInstaller;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

/** Single-writer, resumable region-order raster pregenerator. */
public final class XFEarthPregenerator implements Runnable {
 public static final String STATE="xfearth-pregen.json",MARKER=".xfearth-pregen-owned";
 private static XFEarthPregenerator active;
 private final WorldServer world;private final MinecraftServer server;private final File source,target,temp;
 private final XFEarthProfile profile=XFEarthProfile.earth2000();private final XFEarthBounds bounds=profile.getBounds();
 private final XFEarthMapInstaller.Settings settings=new XFEarthMapInstaller.Settings();private final File template;
 private volatile boolean paused,cancelled;private volatile FutureTask<?> pendingWorldTask;private Thread thread;private XFEarthPregenState state;
 /** Called by the command on the server thread; snapshot all world metadata before starting the worker. */
 private XFEarthPregenerator(WorldServer w,File source,File target)throws IOException{
  world=w;server=MinecraftServer.getServer();this.source=source;this.target=target;temp=new File(target.getParentFile(),target.getName()+".xfearth-generating");
  template=new File(w.getSaveHandler().getWorldDirectory(),"level.dat");settings.levelName=target.getName();settings.generatorOptions=profile.getProfile();settings.seed=w.getSeed();settings.gameType=w.getWorldInfo().getGameType().getID();settings.hardcore=w.getWorldInfo().isHardcoreModeEnabled();settings.structures=false;settings.commands=w.getWorldInfo().areCommandsAllowed();settings.bonusChest=false;prepare();
 }
 public static synchronized XFEarthPregenerator start(WorldServer world,File source,File target)throws IOException{if(cpw.mods.fml.common.Loader.isModLoaded("cubicchunks"))throw new IOException("Ordinary Earth pregeneration requires the ordinary Anvil runtime; CubicChunks gameplay integration is disabled");if(active!=null&&active.thread!=null&&active.thread.isAlive())throw new IOException("An Earth pregeneration is already running");active=new XFEarthPregenerator(world,source,target);active.thread=new Thread(active,"XF Earth pregenerator");active.thread.setDaemon(true);active.thread.start();return active;}
 public static synchronized XFEarthPregenerator current(){return active;}
 public synchronized void pause(){if(thread==null||!thread.isAlive()||"COMPLETE".equals(state.completionState))return;paused=true;state.completionState="PAUSED";safeState();}
 public synchronized void resume(){if(thread==null||!thread.isAlive()||"COMPLETE".equals(state.completionState))return;paused=false;state.completionState="RUNNING";safeState();notifyAll();}
 public synchronized void cancel(){if(thread==null||!thread.isAlive()||"COMPLETE".equals(state.completionState))return;cancelled=true;paused=false;FutureTask<?> task=pendingWorldTask;if(task!=null)task.cancel(false);state.completionState="CANCELLED";safeState();notifyAll();}
 public static synchronized void stopForWorld(net.minecraft.world.World world){if(active!=null&&active.world==world)active.cancel();}
 public static synchronized void shutdown(){if(active!=null)active.cancel();}
 private <T> T onWorldThread(final Callable<T> action)throws Exception{
  if(cancelled)throw new InterruptedException("Earth pregeneration cancelled");
  FutureTask<T> task=new FutureTask<T>(new Callable<T>(){public T call()throws Exception{
   if(cancelled||MinecraftServer.getServer()!=server||!server.isServerRunning()||server.worldServerForDimension(0)!=world)
    throw new InterruptedException("Earth pregeneration world/session ended");
   return action.call();
  }});
  pendingWorldTask=task;TDMServerTaskQueue.schedule(task);
  try{while(true){if(cancelled){task.cancel(false);throw new InterruptedException("Earth pregeneration cancelled");}try{return task.get(100,TimeUnit.MILLISECONDS);}catch(TimeoutException waiting){}}}
  finally{pendingWorldTask=null;}
 }
 public synchronized String status(){return state==null?"idle":state.completionState+" "+state.completedChunks+"/"+state.totalChunks+" chunks ("+String.format(java.util.Locale.ROOT,"%.2f",100.0*state.completedChunks/state.totalChunks)+"%), region "+state.currentRegion;}
 private void prepare()throws IOException{
  XFEarthSourceVerifier.verify(source);String hash=XFEarthSourceVerifier.manifestHash(source);if(target.exists())throw new IOException("Existing world will not be overwritten: "+target);
  if(temp.exists()){File marker=new File(temp,MARKER);if(!marker.isFile())throw new IOException("Partial directory is not proven XF-owned: "+temp);state=readState();if(!"earth2000".equals(state.profile)||!hash.equals(state.sourceManifestHash)||!XFEarthTerrainConverter.VERSION.equals(state.generatorVersion))throw new IOException("Partial generation source/profile/generator changed; move it aside before restarting");state.completedChunks=countCompletedChunks(state.completedRegions);state.currentRegion=null;}
  else{if(!temp.mkdirs())throw new IOException("Cannot create partial world: "+temp);state=new XFEarthPregenState();state.profile=profile.getProfile();state.sourceManifestHash=hash;state.generatorVersion=XFEarthTerrainConverter.VERSION;state.totalRegions=(XFEarthBounds.chunkToRegion(bounds.maximumChunkX)-XFEarthBounds.chunkToRegion(bounds.minimumChunkX)+1)*(XFEarthBounds.chunkToRegion(bounds.maximumChunkZ)-XFEarthBounds.chunkToRegion(bounds.minimumChunkZ)+1);state.totalChunks=(bounds.maximumChunkX-bounds.minimumChunkX+1)*(bounds.maximumChunkZ-bounds.minimumChunkZ+1);state.startTime=System.currentTimeMillis();state.completionState="READY";writeText(new File(temp,MARKER),"XenoFactions owned Earth partial\nprofile="+profile.getProfile()+"\nmanifest="+hash+"\n");writeState();}
 }
 public void run(){long started=System.currentTimeMillis();try{
  synchronized(this){if(cancelled)return;state.completionState=paused?"PAUSED":"RUNNING";writeState();}log("source verified: "+source+"; dimensions "+profile.getWidth()+"x"+profile.getHeight()+"; chunks "+bounds.chunkString()+"; resume="+(state.completedChunks>0));
  XFEarthRasterSet rasters=new XFEarthRasterSet(source,XFConfig.earthRasterTileCache);final AnvilChunkLoader loader=new AnvilChunkLoader(temp);try{XFEarthTerrainConverter converter=new XFEarthTerrainConverter(rasters,profile);
   for(int rz=XFEarthBounds.chunkToRegion(bounds.minimumChunkZ);rz<=XFEarthBounds.chunkToRegion(bounds.maximumChunkZ);rz++)for(int rx=XFEarthBounds.chunkToRegion(bounds.minimumChunkX);rx<=XFEarthBounds.chunkToRegion(bounds.maximumChunkX);rx++){String key=rx+","+rz;if(state.completedRegions.contains(key))continue;waitIfPaused();if(cancelled)return;state.currentRegion=key;writeState();int minX=Math.max(bounds.minimumChunkX,rx*32),maxX=Math.min(bounds.maximumChunkX,rx*32+31),minZ=Math.max(bounds.minimumChunkZ,rz*32),maxZ=Math.min(bounds.maximumChunkZ,rz*32+31);
    for(int cz=minZ;cz<=maxZ;cz++)for(int cx=minX;cx<=maxX;cx++){waitIfPaused();if(cancelled)return;final XFEarthTerrainConverter.PreparedChunk prepared=converter.prepare(cx,cz);onWorldThread(new Callable<Void>(){public Void call()throws Exception{Chunk chunk=prepared.create(world);loader.saveChunk(world,chunk);return null;}});state.completedChunks++;}
    loader.saveExtraData();state.completedRegions.add(key);state.currentRegion=null;writeState();double seconds=Math.max(1,(System.currentTimeMillis()-started)/1000.0);log("region "+key+" complete; "+status()+"; "+String.format(Locale.ROOT,"%.1f",state.completedChunks/seconds)+" chunks/s");
   }
   verify(loader);waitIfPaused();if(cancelled)return;writeMetadata();synchronized(this){waitIfPaused();if(cancelled)return;state.completionState="COMPLETE";writeState();if(!new File(temp,MARKER).delete())throw new IOException("Cannot remove ownership marker");if(!temp.renameTo(target))throw new IOException("Cannot atomically finalize Earth world as "+target);}log("final verification succeeded; Earth world ready at "+target);
  }finally{rasters.close();}
 }catch(Throwable e){state.completionState=cancelled?"CANCELLED":"FAILED";safeState();if(MainRegistry.logger!=null)MainRegistry.logger.error("[XF EARTH PREGEN] stopped: "+e.getMessage(),e);}}
 private synchronized void waitIfPaused()throws InterruptedException{while(paused&&!cancelled)wait();}
 private int countCompletedChunks(List<String> regions){int total=0;for(String key:regions){String[]p=key.split(",");int rx=Integer.parseInt(p[0]),rz=Integer.parseInt(p[1]);int minX=Math.max(bounds.minimumChunkX,rx*32),maxX=Math.min(bounds.maximumChunkX,rx*32+31),minZ=Math.max(bounds.minimumChunkZ,rz*32),maxZ=Math.min(bounds.maximumChunkZ,rz*32+31);total+=(maxX-minX+1)*(maxZ-minZ+1);}return total;}
 // Forge 1614 chunkExists ignores its World argument: this checks only detached pending NBT and region files.
 private void verify(AnvilChunkLoader loader)throws IOException,InterruptedException{log("verifying all "+state.totalChunks+" chunks");for(int z=bounds.minimumChunkZ;z<=bounds.maximumChunkZ;z++)for(int x=bounds.minimumChunkX;x<=bounds.maximumChunkX;x++){waitIfPaused();if(cancelled)return;if(!loader.chunkExists(null,x,z))throw new IOException("Final verification found missing chunk "+x+","+z);}}
 private void writeMetadata()throws IOException{
  writeJson(new File(temp,"xenoearth-profile.json"),profile);
  XFEarthMapInstaller.patchLevel(template,new File(temp,"level.dat"),settings);
  com.hfr.world.earth.storage.XFEarthStorageIdentity.writePreparedIdentity(temp,profile);
  Map<String,Object> install=new LinkedHashMap<String,Object>();install.put("formatVersion",1);install.put("sourceMode","RASTER_PREGEN");install.put("profile","earth2000");install.put("sourceManifestSha256",state.sourceManifestHash);install.put("generatorVersion",XFEarthTerrainConverter.VERSION);install.put("installedAt",System.currentTimeMillis());install.put("populationMode","pregenerated");writeJson(new File(temp,"xfearth-install.json"),install);
 }
 private XFEarthPregenState readState()throws IOException{Reader r=new InputStreamReader(new FileInputStream(new File(temp,STATE)),"UTF-8");try{XFEarthPregenState s=new Gson().fromJson(r,XFEarthPregenState.class);if(s==null||s.formatVersion!=1)throw new IOException("Invalid partial generation state");return s;}finally{r.close();}}
 private synchronized void writeState()throws IOException{state.lastUpdateTime=System.currentTimeMillis();writeJson(new File(temp,STATE),state);}
 private void safeState(){try{writeState();}catch(IOException e){if(MainRegistry.logger!=null)MainRegistry.logger.error("[XF EARTH PREGEN] cannot persist state",e);}}
 private static void writeJson(File f,Object value)throws IOException{File t=new File(f.getPath()+".tmp");Writer w=new OutputStreamWriter(new FileOutputStream(t),"UTF-8");try{new GsonBuilder().setPrettyPrinting().create().toJson(value,w);w.write('\n');}finally{w.close();}if(f.exists()&&!f.delete())throw new IOException("Cannot replace "+f);if(!t.renameTo(f))throw new IOException("Cannot atomically write "+f);}
 private static void writeText(File f,String text)throws IOException{Writer w=new OutputStreamWriter(new FileOutputStream(f),"UTF-8");try{w.write(text);}finally{w.close();}}
 private static void log(String s){if(MainRegistry.logger!=null)MainRegistry.logger.info("[XF EARTH PREGEN] "+s);}
 public static File sourceRoot(){return XFConfig.earthSourceDirectory.trim().isEmpty()?new File("xenofactions/earthmaps/sources"):new File(XFConfig.earthSourceDirectory);}
}
