package com.hfr.client.render;

import java.util.*;
import net.minecraft.util.AxisAlignedBB;

/** Stable resource handle; replacement meshes and bounds are published together on reload. */
public final class GpuModel {
    private final boolean shaderFallback;
    GpuModel(boolean shaderFallback) { this.shaderFallback=shaderFallback; }
    private final Map<String, Part> parts = new LinkedHashMap<String, Part>();
    private Part[] drawable=new Part[0];
    private AxisAlignedBB bounds = AxisAlignedBB.getBoundingBox(-.5,0,-.5,.5,1,.5);
    public static final class Part {
        private GpuMesh mesh;
        public void draw() { if(mesh != null) mesh.draw(0,count); }
        private int count;
        private AxisAlignedBB bounds=AxisAlignedBB.getBoundingBox(0,0,0,0,0,0);
        private double radius;
    }
    public Part part(String name) {
        String key = name;
        Part part = parts.get(key);
        if(part == null) { part = new Part(); parts.put(key,part); }
        return part;
    }
    public void renderPart(String name) { Part part=parts.get(name); if(part!=null) part.draw(); }
    public void renderAll() { for(Part part:drawable) part.draw(); }
    public AxisAlignedBB partBounds(String name) { return part(name).bounds; }
    public double partRadius(String name) { return part(name).radius; }
    public AxisAlignedBB bounds() { return bounds; }
    void replace(Map<String,float[]> data) {
        Map<String,GpuMesh> loaded = new LinkedHashMap<String,GpuMesh>();
        double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=-x0,y1=-x0,z1=-x0;
        try {
            for(Map.Entry<String,float[]> entry:data.entrySet()) {
                float[] vertices=entry.getValue();
                if(vertices.length==0) continue;
                loaded.put(entry.getKey(),new GpuMesh(vertices,shaderFallback));
                for(int i=0;i<vertices.length;i+=10) {
                    x0=Math.min(x0,vertices[i]);x1=Math.max(x1,vertices[i]);
                    y0=Math.min(y0,vertices[i+1]);y1=Math.max(y1,vertices[i+1]);
                    z0=Math.min(z0,vertices[i+2]);z1=Math.max(z1,vertices[i+2]);
                }
            }
        } catch(RuntimeException failure) { for(GpuMesh mesh:loaded.values())mesh.release(); throw failure; }
        release();parts.clear();
        for(Map.Entry<String,GpuMesh> entry:loaded.entrySet()) {
            Part part=part(entry.getKey());part.mesh=entry.getValue();float[] vertices=data.get(entry.getKey());part.count=vertices.length/10;
            double ax=Double.POSITIVE_INFINITY,ay=ax,az=ax,bx=-ax,by=-ax,bz=-ax;part.radius=0;
            for(int i=0;i<vertices.length;i+=10){ax=Math.min(ax,vertices[i]);bx=Math.max(bx,vertices[i]);ay=Math.min(ay,vertices[i+1]);by=Math.max(by,vertices[i+1]);az=Math.min(az,vertices[i+2]);bz=Math.max(bz,vertices[i+2]);part.radius=Math.max(part.radius,Math.hypot(vertices[i],vertices[i+1]));}
            part.bounds=AxisAlignedBB.getBoundingBox(ax,ay,az,bx,by,bz);
        }
        drawable=parts.values().toArray(new Part[parts.size()]);
        if(!loaded.isEmpty()) bounds=AxisAlignedBB.getBoundingBox(x0,y0,z0,x1,y1,z1);
    }
    public void release() { for(Part part:parts.values()) if(part.mesh!=null) {part.mesh.release();part.mesh=null;part.count=0;} }
}
