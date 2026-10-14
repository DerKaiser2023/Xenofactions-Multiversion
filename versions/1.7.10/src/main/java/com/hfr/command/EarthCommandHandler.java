package com.hfr.command;
import java.io.File; import com.hfr.config.XFConfig; import com.hfr.world.earth.*; import com.hfr.world.earth.pack.*; import com.hfr.world.earth.pregen.*;
import net.minecraft.command.*; import net.minecraft.server.MinecraftServer; import net.minecraft.util.ChatComponentText; import net.minecraft.world.WorldServer;
public final class EarthCommandHandler {
 private EarthCommandHandler(){}
 public static final String USAGE="/xc earth status | packs | verify <pack-id> | check <chunkX> <chunkZ> | source status | source verify earth2000 | pregen status | pregen start earth2000 | pregen pause | pregen resume | pregen cancel | geo import <dataset-id> | geo status | geo cancel | geo info <dataset-id>";
 public static void execute(ICommandSender s,String[] a){WorldServer w=MinecraftServer.getServer().worldServerForDimension(0);File root=w.getSaveHandler().getWorldDirectory();XFEarthProfile p=null;try{if(XFEarthProfileLoader.exists(root))p=XFEarthProfileLoader.load(root);}catch(Exception e){msg(s,"Profile error: "+e.getMessage());}
  if(a.length>=1&&"geo".equalsIgnoreCase(a[0])){geographic(s,w,a);return;}
  if(a.length>=1&&("source".equalsIgnoreCase(a[0])||"pregen".equalsIgnoreCase(a[0]))&&!s.canCommandSenderUseCommand(4,"xc"))throw new CommandException("Only a level-4 server operator may control Earth raster sources or pregeneration");
  if(a.length==2&&"source".equalsIgnoreCase(a[0])&&"status".equalsIgnoreCase(a[1])){File source=new File(XFEarthPregenerator.sourceRoot(),"earth2000");msg(s,"Earth source directory: "+source.getAbsolutePath());msg(s,"Manifest present: "+new File(source,XFEarthSourceVerifier.MANIFEST).isFile());return;}
  if(a.length==3&&"source".equalsIgnoreCase(a[0])&&"verify".equalsIgnoreCase(a[1])&&"earth2000".equalsIgnoreCase(a[2])){File source=new File(XFEarthPregenerator.sourceRoot(),"earth2000");try{XFEarthSourceManifest m=XFEarthSourceVerifier.verify(source);msg(s,"Verified "+m.profile+" external source: "+m.width+"x"+m.height+", SHA-256 manifest "+XFEarthSourceVerifier.manifestHash(source));}catch(Exception e){throw new CommandException("Source verification failed: "+e.getMessage());}return;}
  if(a.length==2&&"pregen".equalsIgnoreCase(a[0])&&"status".equalsIgnoreCase(a[1])){XFEarthPregenerator g=XFEarthPregenerator.current();msg(s,"Earth pregenerator: "+(g==null?"idle (a resumable state may exist on disk)":g.status()));return;}
  if(a.length==3&&"pregen".equalsIgnoreCase(a[0])&&("start".equalsIgnoreCase(a[1])||"resume".equalsIgnoreCase(a[1]))&&"earth2000".equalsIgnoreCase(a[2])){start(s,w,root);return;}
  if(a.length==2&&"pregen".equalsIgnoreCase(a[0])&&"resume".equalsIgnoreCase(a[1])){XFEarthPregenerator g=XFEarthPregenerator.current();if(g!=null){g.resume();msg(s,"Earth pregeneration resumed");}else start(s,w,root);return;}
  if(a.length==2&&"pregen".equalsIgnoreCase(a[0])&&"pause".equalsIgnoreCase(a[1])){XFEarthPregenerator g=XFEarthPregenerator.current();if(g==null)throw new CommandException("No active Earth pregeneration");g.pause();msg(s,"Earth pregeneration will pause at a safe chunk boundary");return;}
  if(a.length==2&&"pregen".equalsIgnoreCase(a[0])&&"cancel".equalsIgnoreCase(a[1])){XFEarthPregenerator g=XFEarthPregenerator.current();if(g==null)throw new CommandException("No active Earth pregeneration");g.cancel();msg(s,"Earth pregeneration cancelled; the XF-owned partial directory was retained for inspection/resume");return;}
  if(a.length==1&&"packs".equalsIgnoreCase(a[0])){java.util.List<XFEarthMapPack> packs=new XFEarthMapRepository(new File(".")).discover();msg(s,"Available Earth map packs: "+packs.size());for(XFEarthMapPack pack:packs)msg(s,pack.manifest.id+" "+pack.manifest.version+" ["+pack.source.getProvider()+"] "+(pack.isCompatible()?"compatible":"missing mods "+pack.missingMods()));return;}
  if(a.length==2&&"verify".equalsIgnoreCase(a[0])){XFEarthMapRepository repo=new XFEarthMapRepository(new File("."));XFEarthMapPack pack=repo.find(a[1]);if(pack==null)throw new CommandException("Unknown or unavailable Earth pack: "+a[1]);try{XFEarthMapVerifier.verify(repo.materialize(pack),pack.archiveSha256);msg(s,"Verified "+pack.manifest.id+" archive SHA-256 "+pack.archiveSha256);}catch(Exception e){throw new CommandException("Verification failed: "+e.getMessage());}return;}
  if(a.length==1&&"status".equalsIgnoreCase(a[0])){msg(s,"Earth world type registered: "+XFEarthRegistry.isRegistered());msg(s,"Current overworld terrain type: "+w.getWorldInfo().getTerrainType().getWorldTypeName());msg(s,"Profile path: "+XFEarthProfileLoader.path(root)); if(p!=null){msg(s,"Profile name: "+p.getProfile());msg(s,"Effective scale: "+p.getEffectiveScale());msg(s,"Block bounds: "+p.getBounds().blockString());msg(s,"Chunk bounds: "+p.getBounds().chunkString());msg(s,"Sea level: "+p.getSeaLevel());}msg(s,"Missing chunk policy: "+XFConfig.earthMissingChunkPolicy);msg(s,"Boundary mode: "+XFConfig.earthBoundaryMode);msg(s,"Correctly adopted: "+(XFEarthRegistry.get()!=null&&w.getWorldInfo().getTerrainType()==XFEarthRegistry.get()&&p!=null));return;}
  if(a.length==3&&"check".equalsIgnoreCase(a[0])){int x=CommandBase.parseInt(s,a[1]),z=CommandBase.parseInt(s,a[2]);boolean inside=p!=null&&p.getBounds().containsChunk(x,z);File region=new File(new File(root,"region"),"r."+XFEarthBounds.chunkToRegion(x)+"."+XFEarthBounds.chunkToRegion(z)+".mca");msg(s,"Inside profile bounds: "+inside);msg(s,"Region file exists: "+region.isFile()+" (this does not prove that the specific chunk exists)");return;} throw new WrongUsageException(USAGE);
 }
 private static void start(ICommandSender s,WorldServer w,File current){File source=new File(XFEarthPregenerator.sourceRoot(),"earth2000"),target=new File(current.getParentFile(),"earth2000");try{XFEarthPregenerator.start(w,source,target);msg(s,"Earth pregeneration started in "+new File(target.getParentFile(),target.getName()+".xfearth-generating")+"; final world will be "+target);}catch(Exception e){throw new CommandException("Cannot start Earth pregeneration: "+e.getMessage());}}
 public static String[] getPackIds(){java.util.List<XFEarthMapPack> packs=new XFEarthMapRepository(new File(".")).discover();String[] ids=new String[packs.size()];for(int i=0;i<packs.size();i++)ids[i]=packs.get(i).manifest.id;return ids;}
 private static void msg(ICommandSender s,String m){s.addChatMessage(new ChatComponentText(m));}
 private static void geographic(ICommandSender sender,WorldServer world,String[] args) {
  if(!sender.canCommandSenderUseCommand(4,"xc"))throw new CommandException("Only a level-4 operator may manage local geographic imports");
  try {
   if(args.length==2&&"status".equalsIgnoreCase(args[1])){msg(sender,com.hfr.world.earth.geo.XFEarthGeoImportJob.status());return;}
   if(args.length==2&&"cancel".equalsIgnoreCase(args[1])){com.hfr.world.earth.geo.XFEarthGeoImportJob.cancel();msg(sender,"Geographic cancellation requested; incomplete external output is retained and never used as a complete dataset");return;}
   if(args.length==3&&"import".equalsIgnoreCase(args[1])){
    com.hfr.world.earth.geo.XFEarthGeoImportJob.start(world,args[2]);msg(sender,"Started local geographic import: "+args[2]+". © OpenStreetMap contributors / ODbL-1.0. No blocks will be placed.");return;
   }
   if(args.length==3&&"info".equalsIgnoreCase(args[1])){
    com.hfr.world.earth.geo.XFEarthGeoImporter.validateId(args[2]);XFEarthProfile profile=XFEarthProfileLoader.forWorld(world);
    if(profile==null)throw new IllegalArgumentException("An adopted Earth profile is required");
    com.hfr.world.earth.geo.XFEarthGeoDataset dataset=com.hfr.world.earth.geo.XFEarthGeoDataset.open(new File(com.hfr.world.earth.geo.XFEarthGeoImportJob.dataRoot(world),args[2]),profile);
    msg(sender,"Dataset "+args[2]+"; identity="+dataset.manifest.get("datasetIdentity").getAsString()+"; imported="+dataset.manifest.get("imported").getAsLong());
    msg(sender,"Source snapshot: "+dataset.manifest.getAsJsonObject("source").get("snapshotTimestamp").getAsString());
    msg(sender,dataset.manifest.get("attribution").getAsString()+" / ODbL-1.0: https://www.openstreetmap.org/copyright");
    msg(sender,"Coverage is unverified. Empty results mean no imported matches, not confirmed absence.");return;
   }
  }catch(java.io.IOException|IllegalArgumentException e){throw new CommandException("Geographic import error: "+e.getMessage());}
  throw new WrongUsageException(USAGE);
 }
}
