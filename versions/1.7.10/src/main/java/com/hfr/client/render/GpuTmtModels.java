package com.hfr.client.render;

import com.hfr.render.tmt.*;
import com.hfr.tmt.*;
import java.util.*;

/** Exports authored TMT polygons once. The rotor's common pivot becomes one draw-time transform. */
public final class GpuTmtModels {
    public static final float ROTOR_Y=83.5F/16F, ROTOR_Z=-26F/16F;
    private GpuTmtModels() { }
    public static Map<String,float[]> mill() {
        ModelRendererTurbo[] parts=new ModelGrainmill().baseModel;
        Map<String,float[]> result=new LinkedHashMap<String,float[]>();
        result.put("Body",prepare(parts,0,37,false));
        result.put("Rotor",prepare(parts,37,parts.length,true));
        return result;
    }
    public static Map<String,float[]> hesco(){return all(new ModelHescoBlock().baseModel);}
    public static Map<String,float[]> tent(){return all(new ModelTpTent().baseModel);}
    private static Map<String,float[]> all(ModelRendererTurbo[] parts) {
        return Collections.singletonMap("Body",prepare(parts,0,parts.length,false));
    }
    private static float[] prepare(ModelRendererTurbo[] parts,int from,int to,boolean rotor) {
        GpuModelManager.Builder out=new GpuModelManager.Builder();
        for(int i=from;i<to;i++) {
            ModelRendererTurbo part=parts[i];
            if(rotor&&(part.rotationPointX!=0||part.rotationPointY!=83.5F||part.rotationPointZ!=-26F
                ||part.rotateAngleY!=0||part.rotateAngleX!=0))throw new IllegalArgumentException("Grainmill rotor no longer shares its pivot");
            for(TexturedPolygon polygon:part.preparedFaces()) {
                float[][] vertices=polygon.preparedVertices();
                for(float[] v:vertices) {
                    rotate(v,0,part);rotate(v,7,part);
                    v[0]=v[0]/16F+(rotor?0:part.rotationPointX/16F);
                    v[1]=v[1]/16F+(rotor?0:part.rotationPointY/16F);
                    v[2]=v[2]/16F+(rotor?0:part.rotationPointZ/16F);
                }
                out.triangle(vertices[0],vertices[1],vertices[2]);
                if(vertices.length==4)out.triangle(vertices[0],vertices[2],vertices[3]);
            }
        }
        return out.finish();
    }
    // TMT's default GL order is Y,Z,X, so local coordinates are transformed X,Z,Y.
    private static void rotate(float[] v,int offset,ModelRendererTurbo part) {
        double x=v[offset],y=v[offset+1],z=v[offset+2];
        double sin=Math.sin(part.rotateAngleX),cos=Math.cos(part.rotateAngleX);
        double ny=y*cos-z*sin,nz=y*sin+z*cos;y=ny;z=nz;
        sin=Math.sin(part.rotateAngleZ);cos=Math.cos(part.rotateAngleZ);
        double nx=x*cos-y*sin;ny=x*sin+y*cos;x=nx;y=ny;
        sin=Math.sin(part.rotateAngleY);cos=Math.cos(part.rotateAngleY);
        nx=x*cos+z*sin;nz=-x*sin+z*cos;
        v[offset]=(float)nx;v[offset+1]=(float)y;v[offset+2]=(float)nz;
    }
}
