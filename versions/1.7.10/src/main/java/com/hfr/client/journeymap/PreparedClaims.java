package com.hfr.client.journeymap;

import com.hfr.clowder.ClaimOverlayData;
import com.hfr.clowder.ClaimOverlayData.Claim;
import com.hfr.clowder.TerritoryCoordinateBounds;
import com.hfr.client.render.RenderMetrics;
import java.util.*;

/** Immutable world-space topology and balanced spatial bounds, built only with a complete snapshot. */
public final class PreparedClaims {
    public static final int NORTH=1,EAST=2,SOUTH=4,WEST=8;
    public final List<Cell> cells;
    private final Node<Cell> index;
    private final Node<Group> labels;
    public PreparedClaims(List<Claim> claims,Map<Long,Claim> lookup,List<ClientClaimOverlayCache.TerritoryGroup> groups) {
        long started=RenderMetrics.ENABLED?System.nanoTime():0;
        Cell[] values=new Cell[claims.size()];
        for(int i=0;i<values.length;i++) {
            Claim claim=claims.get(i);int edges=0;
            if(!same(lookup,claim,0,-1))edges|=NORTH;
            if(!same(lookup,claim,1,0))edges|=EAST;
            if(!same(lookup,claim,0,1))edges|=SOUTH;
            if(!same(lookup,claim,-1,0))edges|=WEST;
            values[i]=new Cell(claim,edges);
        }
        cells=Collections.unmodifiableList(Arrays.asList(values.clone()));
        index=Node.build(values,0,values.length);
        Group[] names=new Group[groups.size()];for(int i=0;i<names.length;i++)names[i]=new Group(groups.get(i));
        labels=Node.build(names,0,names.length);
        if(RenderMetrics.ENABLED)RenderMetrics.claimBuildNanos+=System.nanoTime()-started;
    }
    private static boolean same(Map<Long,Claim> lookup,Claim c,int dx,int dz) {
        if(dx<0&&c.chunkX==Integer.MIN_VALUE||dx>0&&c.chunkX==Integer.MAX_VALUE
            ||dz<0&&c.chunkZ==Integer.MIN_VALUE||dz>0&&c.chunkZ==Integer.MAX_VALUE)return false;
        Claim neighbor=lookup.get(ClaimOverlayData.chunkKey(c.chunkX+dx,c.chunkZ+dz));
        return neighbor!=null&&neighbor.groupId.equals(c.groupId);
    }
    public void select(double x0,double z0,double x1,double z1,List<Cell> visible,List<Group> groups) {
        visible.clear();if(groups!=null)groups.clear();
        if(index!=null)index.select(x0,z0,x1,z1,visible);
        if(groups!=null&&labels!=null)labels.select(x0,z0,x1,z1,groups);
        if(RenderMetrics.ENABLED){RenderMetrics.claimsVisible+=visible.size();RenderMetrics.claimsTotal+=cells.size();}
    }
    public static class Box {
        public final double x0,z0,x1,z1;
        Box(double x0,double z0,double x1,double z1){this.x0=x0;this.z0=z0;this.x1=x1;this.z1=z1;}
        boolean overlaps(double x,double z,double xx,double zz){return x1>=x&&z1>=z&&x0<=xx&&z0<=zz;}
    }
    public static final class Cell extends Box {
        public final Claim claim;public final int edges;
        Cell(Claim claim,int edges) {
            this(claim,edges,TerritoryCoordinateBounds.forCoordinate(claim.chunkX),TerritoryCoordinateBounds.forCoordinate(claim.chunkZ));
        }
        private Cell(Claim claim,int edges,TerritoryCoordinateBounds.Bounds x,TerritoryCoordinateBounds.Bounds z) {
            super(x.minInclusive,z.minInclusive,x.maxExclusive,z.maxExclusive);this.claim=claim;this.edges=edges;
        }
    }
    public static final class Group extends Box {
        public final ClientClaimOverlayCache.TerritoryGroup group;
        Group(ClientClaimOverlayCache.TerritoryGroup g) {
            super(TerritoryCoordinateBounds.forCoordinate(g.minChunkX).minInclusive,TerritoryCoordinateBounds.forCoordinate(g.minChunkZ).minInclusive,
                TerritoryCoordinateBounds.forCoordinate(g.maxChunkX).maxExclusive,TerritoryCoordinateBounds.forCoordinate(g.maxChunkZ).maxExclusive);group=g;
        }
    }
    private static final class Node<T extends Box> extends Box {
        final Node<T> left,right;final T[] values;final int from,to;
        Node(double x,double z,double xx,double zz,Node<T> left,Node<T> right,T[] values,int from,int to) {
            super(x,z,xx,zz);this.left=left;this.right=right;this.values=values;this.from=from;this.to=to;
        }
        static <T extends Box> Node<T> build(T[] values,int from,int to) {
            if(from==to)return null;
            double x=Double.POSITIVE_INFINITY,z=x,xx=-x,zz=-x;
            for(int i=from;i<to;i++){Box b=values[i];x=Math.min(x,b.x0);z=Math.min(z,b.z0);xx=Math.max(xx,b.x1);zz=Math.max(zz,b.z1);}
            if(to-from<=24)return new Node<T>(x,z,xx,zz,null,null,values,from,to);
            final boolean axis=xx-x>=zz-z;
            Arrays.sort(values,from,to,(a,b) -> Double.compare(axis?a.x0+a.x1:a.z0+a.z1,axis?b.x0+b.x1:b.z0+b.z1));
            int middle=(from+to)>>>1;
            return new Node<T>(x,z,xx,zz,build(values,from,middle),build(values,middle,to),null,0,0);
        }
        void select(double x,double z,double xx,double zz,List<T> result) {
            if(!overlaps(x,z,xx,zz))return;
            if(values!=null){for(int i=from;i<to;i++)if(values[i].overlaps(x,z,xx,zz))result.add(values[i]);}
            else {left.select(x,z,xx,zz,result);right.select(x,z,xx,zz,result);}
        }
    }
}
