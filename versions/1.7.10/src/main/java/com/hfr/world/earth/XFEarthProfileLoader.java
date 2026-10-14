package com.hfr.world.earth;
import java.io.*; import java.util.*; import com.google.gson.Gson; import com.hfr.config.XFConfig;
public final class XFEarthProfileLoader {
 public static final String FILE_NAME="xenoearth-profile.json";
 private static final Map<String,XFEarthProfile> CACHE=new HashMap<String,XFEarthProfile>();
 private XFEarthProfileLoader(){}
 /** Owning-thread lookup only. Sidecars on other world types/dimensions never change their bounds. */
 public static XFEarthProfile forWorld(net.minecraft.world.World world) {
  if(world==null||world.provider.dimensionId!=0||XFEarthRegistry.get()==null||world.getWorldInfo().getTerrainType()!=XFEarthRegistry.get())return null;
  if(world.isRemote)return com.hfr.main.MainRegistry.proxy.getEarthHeightProfile(world);
  File root=world.getSaveHandler().getWorldDirectory();
  try{return load(root);}catch(IOException e){
   if(!exists(root))return null; // Profile-less bootstrap; load() retains an already cached active profile.
   throw new IllegalStateException("Cannot resolve active Earth vertical bounds",e);
  }
 }
 public static synchronized XFEarthProfile load(File saveRoot) throws IOException {
  File file=new File(saveRoot,FILE_NAME); String key=file.getCanonicalPath(); if(CACHE.containsKey(key)) return CACHE.get(key);
  Reader reader=new InputStreamReader(new FileInputStream(file),"UTF-8"); try { XFEarthProfile p=new Gson().fromJson(reader,XFEarthProfile.class); if(p==null) throw new IllegalArgumentException("Invalid XenoEarth profile: empty JSON"); p.validate(XFConfig.earthAllowProfileMinecraftVersion); CACHE.put(key,p); return p; } finally {reader.close();}
 }
 public static File path(File root){return new File(root,FILE_NAME);} public static boolean exists(File root){return path(root).isFile();}
 public static synchronized void clear(){CACHE.clear();}
}
