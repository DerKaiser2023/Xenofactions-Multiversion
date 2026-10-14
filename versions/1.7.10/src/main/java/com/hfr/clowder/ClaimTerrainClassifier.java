package com.hfr.clowder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.hfr.clowder.ClaimNodeSystem.*;

/** Pure classification of detached column observations. No Minecraft world or chunk access. */
public final class ClaimTerrainClassifier implements Classifier<ClaimTerrainClassifier.Terrain> {
    public static final String ALGORITHM="xf:connected-surface";
    public static final int REVISION=1;
    public static final byte EMPTY=0, LAND=1, PLATFORM=2, WATER=3, SUBMERGED=4;
    public static final class Terrain {
        public final int width,depth,minimumArea,coveragePercent,maxStep;
        public final byte[] kind;
        public final double[] top;
        public final String policy;
        public final long tick;
        public Terrain(int width,int depth,int minimumArea,int coveragePercent,int maxStep,String policy,long tick){
            if(width<1||width>31||depth<1||depth>31||minimumArea<2||minimumArea>961||coveragePercent<1||coveragePercent>100||maxStep<0||maxStep>4||policy==null||tick<0)throw new IllegalArgumentException("Invalid bounded terrain policy/snapshot");
            this.width=width;this.depth=depth;this.minimumArea=minimumArea;this.coveragePercent=coveragePercent;
            this.maxStep=maxStep;this.policy=policy;this.tick=tick;kind=new byte[width*depth];top=new double[kind.length];
        }
    }
    public static final class Result {
        public final Classification classification;
        public final int land,platform,water,submerged,qualifying,connected,required,total;
        Result(Classification classification,int land,int platform,int water,int submerged,int connected,int required,int total){
            this.classification=classification;this.land=land;this.platform=platform;this.water=water;this.submerged=submerged;
            qualifying=land+platform;this.connected=connected;this.required=required;this.total=total;
        }
        public String description(){return "connected surface "+connected+"/"+total+" columns (need "+required+"); dry/filled="+land+", deck over water="+platform+", open water="+water+", submerged="+submerged;}
    }
    public Classification classify(Identity node,Geometry geometry,Terrain terrain){return evaluate(node,geometry,terrain).classification;}
    public Result evaluate(Identity node,Geometry geometry,Terrain t){
        if(t.width!=geometry.maxX-geometry.minX||t.depth!=geometry.maxZ-geometry.minZ||t.kind.length>961)throw new IllegalArgumentException("Invalid territorial surface snapshot");
        int land=0,platform=0,water=0,submerged=0,largest=0;
        boolean[] visited=new boolean[t.kind.length];int[] queue=new int[t.kind.length];
        for(byte kind:t.kind){if(kind==LAND)land++;else if(kind==PLATFORM)platform++;else if(kind==WATER)water++;else if(kind==SUBMERGED)submerged++;}
        for(int start=0;start<t.kind.length;start++){
            if(visited[start]||!surface(t.kind[start]))continue;
            int head=0,end=1;queue[0]=start;visited[start]=true;
            while(head<end){int a=queue[head++],x=a%t.width,z=a/t.width;
                for(int direction=0;direction<4;direction++){
                    int b=direction==0?x>0?a-1:-1:direction==1?x+1<t.width?a+1:-1:direction==2?z>0?a-t.width:-1:z+1<t.depth?a+t.width:-1;
                    if(b>=0&&!visited[b]&&surface(t.kind[b])&&Math.abs(t.top[a]-t.top[b])<=t.maxStep+0.000001){visited[b]=true;queue[end++]=b;}
                }
            }largest=Math.max(largest,end);
        }
        int required=Math.max(t.minimumArea,(t.kind.length*t.coveragePercent+99)/100);
        try{
            MessageDigest hash=MessageDigest.getInstance("SHA-256");hash.update((node+"|"+t.policy).getBytes(StandardCharsets.UTF_8));
            ByteBuffer numbers=ByteBuffer.allocate(t.kind.length*9);for(int i=0;i<t.kind.length;i++){numbers.put(t.kind[i]);numbers.putDouble(t.top[i]);}hash.update(numbers.array());
            StringBuilder evidence=new StringBuilder();for(byte b:hash.digest()){evidence.append(Character.forDigit((b>>4)&15,16));evidence.append(Character.forDigit(b&15,16));}
            Classification classification=new Classification(largest>=required?Eligibility.ELIGIBLE:Eligibility.INELIGIBLE,ALGORITHM,REVISION,t.tick,evidence.toString());
            return new Result(classification,land,platform,water,submerged,largest,required,t.kind.length);
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static boolean surface(byte kind){return kind==LAND||kind==PLATFORM;}
}
