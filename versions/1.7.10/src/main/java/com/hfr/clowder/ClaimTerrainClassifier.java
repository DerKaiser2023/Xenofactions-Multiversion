package com.hfr.clowder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.hfr.clowder.ClaimNodeSystem.*;

/** Pure classification of detached column observations. No Minecraft world or chunk access. */
public final class ClaimTerrainClassifier implements Classifier<ClaimTerrainClassifier.Terrain> {
    public static final String ALGORITHM="xf:water-depth";
    public static final int REVISION=1;
    public static final byte UNKNOWN=0, ELIGIBLE=1, DEEP_WATER=2;
    public static final class Terrain {
        public final int width,depth;
        public final byte[] kind;
        public final String policy;
        public final long tick;
        public Terrain(int width,int depth,String policy,long tick){
            if(width<1||width>31||depth<1||depth>31||policy==null||tick<0)throw new IllegalArgumentException("Invalid bounded terrain snapshot");
            this.width=width;this.depth=depth;this.policy=policy;this.tick=tick;kind=new byte[width*depth];
        }
    }
    public static final class Result {
        public final Classification classification;
        public final int eligible,water,unavailable,total;
        Result(Classification classification,int eligible,int water,int unavailable,int total){
            this.classification=classification;this.eligible=eligible;this.water=water;this.unavailable=unavailable;this.total=total;
        }
        public String description(){return "Open water at least three consecutive water blocks deep: "+water+"/"+total
            +" columns; eligible="+eligible+", unavailable/unobserved="+unavailable+". Only an entirely deep-water surface is rejected.";}
    }
    public Classification classify(Identity node,Geometry geometry,Terrain terrain){return evaluate(node,geometry,terrain).classification;}
    public Result evaluate(Identity node,Geometry geometry,Terrain t){
        if(t.width!=geometry.maxX-geometry.minX||t.depth!=geometry.maxZ-geometry.minZ||t.kind.length>961)throw new IllegalArgumentException("Invalid territorial surface snapshot");
        int eligible=0,water=0,unavailable=0;
        for(byte kind:t.kind){if(kind==ELIGIBLE)eligible++;else if(kind==DEEP_WATER)water++;else unavailable++;}
        Eligibility eligibility=eligible>0?Eligibility.ELIGIBLE:water==t.kind.length?Eligibility.INELIGIBLE:Eligibility.UNCLASSIFIED;
        if(eligibility==Eligibility.UNCLASSIFIED)return new Result(Classification.unclassified(),eligible,water,unavailable,t.kind.length);
        try{
            MessageDigest hash=MessageDigest.getInstance("SHA-256");hash.update((node+"|"+t.policy).getBytes(StandardCharsets.UTF_8));hash.update(t.kind);
            StringBuilder evidence=new StringBuilder();for(byte b:hash.digest()){evidence.append(Character.forDigit((b>>4)&15,16));evidence.append(Character.forDigit(b&15,16));}
            Classification classification=new Classification(eligibility,ALGORITHM,REVISION,t.tick,evidence.toString());
            return new Result(classification,eligible,water,unavailable,t.kind.length);
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
