package com.hfr.client.render;

import com.hfr.main.MainRegistry;
import java.io.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.*;
import net.minecraft.util.ResourceLocation;

/** Small client-thread registry. Parser objects and packed CPU arrays die after upload. */
public final class GpuModelManager implements IResourceManagerReloadListener {
    public interface Source { Map<String,float[]> load() throws Exception; }
    private static final Map<GpuModel,Source> MODELS=new LinkedHashMap<GpuModel,Source>();
    private static boolean registered, ready;
    private static int generation;
    public static int generation() { return generation; }
    private GpuModelManager() { }
    public static void register() {
        if(registered)return; registered=true;
        ((IReloadableResourceManager)Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new GpuModelManager());
    }
    public static GpuModel obj(String path) { return obj(path,false); }
    public static GpuModel obj(String path, final boolean smooth) {
        final ResourceLocation resource=new ResourceLocation("hfr",path);
        return create(() -> readObj(resource,smooth),true);
    }
    public static GpuModel create(Source source) {
        return create(source,false);
    }
    private static GpuModel create(Source source,boolean shaderFallback) {
        GpuModel model=new GpuModel(shaderFallback); MODELS.put(model,source);
        load(model,source); return model;
    }
    private static void load(GpuModel model,Source source) {
        try { model.replace(source.load()); }
        catch(Exception failure) { model.release(); MainRegistry.logger.error("Could not prepare retained XF model; model omitted",failure); }
    }
    @Override public void onResourceManagerReload(IResourceManager manager) {
        release(); ready=true;generation++;
        for(Map.Entry<GpuModel,Source> entry:MODELS.entrySet())load(entry.getKey(),entry.getValue());
        if(RenderMetrics.ENABLED)RenderMetrics.reloads++;
    }
    public static void release() { for(GpuModel model:MODELS.keySet())model.release();ready=false; }
    public static void resume() {
        if(ready)return;ready=true;
        for(Map.Entry<GpuModel,Source> entry:MODELS.entrySet())load(entry.getKey(),entry.getValue());
    }
    private static Map<String,float[]> readObj(ResourceLocation resource,boolean smooth) throws IOException {
        List<float[]> positions=new ArrayList<float[]>(),uvs=new ArrayList<float[]>(),normals=new ArrayList<float[]>();
        Map<String,Builder> groups=new LinkedHashMap<String,Builder>();String group="default";
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(Minecraft.getMinecraft().getResourceManager().getResource(resource).getInputStream(),"UTF-8"))) {
            String line;int number=0;
            while((line=reader.readLine())!=null) {
                number++;line=line.split("#",2)[0].trim();if(line.isEmpty())continue;
                String[] fields=line.split("\\s+");
                try {
                    if(fields[0].equals("v"))positions.add(vector(fields,3));
                    else if(fields[0].equals("vt")){float[] uv=vector(fields,2);uv[1]=1-uv[1];uvs.add(uv);}
                    else if(fields[0].equals("vn"))normals.add(vector(fields,3));
                    else if(fields[0].equals("o")||fields[0].equals("g"))group=line.substring(1).trim();
                    else if(fields[0].equals("f")) {
                        if(fields.length!=4&&fields.length!=5)throw new IllegalArgumentException("Only triangle and quad OBJ faces are supported");
                        Builder builder=groups.get(group);if(builder==null){builder=new Builder();groups.put(group,builder);}
                        float[][] face=new float[fields.length-1][];
                        for(int i=1;i<fields.length;i++) {
                            String[] indices=fields[i].split("/",-1);float[] p=positions.get(index(indices[0],positions.size()));
                            float[] uv=indices.length>1&&!indices[1].isEmpty()?uvs.get(index(indices[1],uvs.size())):new float[2];
                            float[] n=indices.length>2&&!indices[2].isEmpty()?normals.get(index(indices[2],normals.size())):null;
                            face[i-1]=new float[]{p[0],p[1],p[2],uv[0],uv[1],0,1,n==null?Float.NaN:n[0],n==null?0:n[1],n==null?0:n[2]};
                        }
                        float[] normal=normal(face[0],face[1],face[2]);
                        // Forge OBJ used flat normals plus a small UV inset; HFR models used authored normals and no inset.
                        float u=0,v=0;for(float[] vertex:face){u+=vertex[3];v+=vertex[4];}u/=face.length;v/=face.length;
                        for(float[] vertex:face){
                            if(!smooth||Float.isNaN(vertex[7]))System.arraycopy(normal,0,vertex,7,3);
                            if(!smooth){vertex[3]+=vertex[3]>u?-.0005F:.0005F;vertex[4]+=vertex[4]>v?-.0005F:.0005F;}
                        }
                        builder.triangle(face[0],face[1],face[2]);
                        if(face.length==4)builder.triangle(face[0],face[2],face[3]);
                    }
                } catch(RuntimeException failure) { throw new IOException(resource+":"+number+": "+failure.getMessage(),failure); }
            }
        }
        Map<String,float[]> result=new LinkedHashMap<String,float[]>();
        for(Map.Entry<String,Builder> entry:groups.entrySet())result.put(entry.getKey(),entry.getValue().finish());
        if(result.isEmpty())throw new IOException("No geometry in "+resource);
        return result;
    }
    private static float[] vector(String[] fields,int size) {
        float[] v=new float[size];for(int i=0;i<size;i++){v[i]=Float.parseFloat(fields[i+1]);if(!Float.isFinite(v[i]))throw new IllegalArgumentException("Non-finite vertex");}return v;
    }
    private static int index(String value,int size){int i=Integer.parseInt(value);if(i==0)throw new IllegalArgumentException("OBJ index zero");return i>0?i-1:size+i;}
    public static float[] normal(float[] a,float[] b,float[] c) {
        float x=b[0]-a[0],y=b[1]-a[1],z=b[2]-a[2],u=c[0]-a[0],v=c[1]-a[1],w=c[2]-a[2];
        float nx=y*w-z*v,ny=z*u-x*w,nz=x*v-y*u;double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
        return length==0?new float[]{0,1,0}:new float[]{(float)(nx/length),(float)(ny/length),(float)(nz/length)};
    }
    public static final class Builder {
        private float[] data=new float[1024];private int size;
        public void vertex(float[] v){if(size+10>data.length)data=Arrays.copyOf(data,data.length*2);System.arraycopy(v,0,data,size,10);size+=10;}
        public void triangle(float[] a,float[] b,float[] c){vertex(a);vertex(b);vertex(c);}
        public float[] finish(){return Arrays.copyOf(data,size);}
    }
}
