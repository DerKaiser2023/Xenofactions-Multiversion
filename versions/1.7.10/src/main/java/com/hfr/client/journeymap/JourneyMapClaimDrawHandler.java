package com.hfr.client.journeymap;

import java.awt.geom.Point2D;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import com.hfr.client.render.RenderState;

import org.lwjgl.opengl.GL11;

import com.hfr.client.journeymap.ClientClaimOverlayCache.Snapshot;
import com.hfr.client.journeymap.ClientClaimOverlayCache.TerritoryGroup;
import com.hfr.clowder.TerritoryCoordinateBounds;
import com.hfr.clowder.TerritoryCoordinateBounds.Bounds;
import com.hfr.config.XFConfig;
import com.hfr.tdm.BlockAreaEdges;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

final class JourneyMapClaimDrawHandler implements InvocationHandler {
	private final boolean minimap; private final JourneyMapReflection reflection; private final XFJourneyMapIntegration integration;
	private boolean renderFailureLogged;
    private final List<PreparedClaims.Cell> visible=new ArrayList<PreparedClaims.Cell>();
    private final List<PreparedClaims.Group> labels=new ArrayList<PreparedClaims.Group>();
    private double originX,originZ,baseX,baseY,ax,az,bx,bz;
	JourneyMapClaimDrawHandler(boolean minimap, JourneyMapReflection reflection, XFJourneyMapIntegration integration) { this.minimap = minimap; this.reflection = reflection; this.integration = integration; }
	@Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		String name = method.getName();
		if("equals".equals(name)) return Boolean.valueOf(proxy == args[0]);
		if("hashCode".equals(name)) return Integer.valueOf(System.identityHashCode(proxy));
		if("toString".equals(name)) return "Xenofactions JourneyMap " + (minimap ? "minimap" : "fullscreen") + " overlay";
		if("draw".equals(name) && args != null && args.length == 6) { try { draw(((Double)args[0]).doubleValue(), ((Double)args[1]).doubleValue(), args[2]); renderFailureLogged = false; } catch(Throwable failure) { if(!renderFailureLogged) { renderFailureLogged = true; integration.renderFail("render invocation failed", failure); } } return null; }
		Class<?> type = method.getReturnType();
		if(type == boolean.class) return Boolean.FALSE; if(type == byte.class) return Byte.valueOf((byte)0);
		if(type == short.class) return Short.valueOf((short)0); if(type == int.class) return Integer.valueOf(0);
		if(type == long.class) return Long.valueOf(0); if(type == float.class) return Float.valueOf(0);
		if(type == double.class) return Double.valueOf(0); if(type == char.class) return Character.valueOf('\0'); return null;
	}
	private void draw(double xOffset, double yOffset, Object grid) throws Exception {
		Minecraft mc = Minecraft.getMinecraft();
		if(!XFJourneyMapIntegration.isUsable() || mc == null || mc.theWorld == null || mc.thePlayer == null) return;
		boolean claimsEnabled = minimap ? XFConfig.journeyMapShowMinimapClaims : XFConfig.journeyMapShowFullscreenClaims;
		Snapshot snapshot = claimsEnabled ? ClientClaimOverlayCache.get(mc.thePlayer.dimension) : null;
		NBTTagCompound map = ClientTDMMapOverlay.snapshot();
		NBTTagCompound publicMap = map.getCompoundTag("public"), preview = map.getCompoundTag("preview");
		boolean mapEnabled = ClientTDMMapOverlay.visible()
				&& (publicMap.hasKey("bounds") || publicMap.hasKey("bombA") || publicMap.hasKey("bombB")
						|| preview.getBoolean("overlay") && preview.hasKey("map"));
		if((snapshot == null || snapshot.claims.isEmpty()) && !mapEnabled) return;
		int width = ((Integer)reflection.getWidth.invoke(grid)).intValue(), height = ((Integer)reflection.getHeight.invoke(grid)).intValue();
        prepareProjection(grid,mc.thePlayer.posX,mc.thePlayer.posZ);
        if(snapshot!=null)select(snapshot,xOffset,yOffset,width,height);
        // JourneyMap retains depth ownership: its depth buffer supplies minimap clipping.
        try(RenderState state=new RenderState()) {
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_LIGHTING); GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA); GL11.glDisable(GL11.GL_ALPHA_TEST);
			if(snapshot != null) renderClaims(xOffset,yOffset);
			if(mapEnabled) {
				if(publicMap.hasKey("map")) renderMap(grid, publicMap, xOffset, yOffset, width, height, false, false, publicMap);
				if(preview.getBoolean("overlay") && preview.hasKey("map"))
					renderMap(grid, preview, xOffset, yOffset, width, height, snapshot == null, true, publicMap);
			}
			if(snapshot != null && XFConfig.journeyMapShowTerritoryLabels) {
				GL11.glEnable(GL11.GL_TEXTURE_2D);
				GL11.glColor4f(1F, 1F, 1F, 1F);
				for(int i=0;i<labels.size();i++) renderLabel(grid, labels.get(i), xOffset, yOffset, width, height);
			}
        } finally {visible.clear();labels.clear();}
	}
	private void renderMap(Object grid, NBTTagCompound map, double xo, double yo, int width, int height,
			boolean drawZones, boolean preview, NBTTagCompound publicMap) throws Exception {
		NBTTagList zones = map.getTagList("zones", 10);
		for(int i = 0; drawZones && i < zones.tagCount(); i++) {
			NBTTagCompound zone = zones.getCompoundTagAt(i);
			Bounds x = TerritoryCoordinateBounds.forCoordinate(map.getInteger("zoneCX") + zone.getByte("x"));
			Bounds z = TerritoryCoordinateBounds.forCoordinate(map.getInteger("zoneCZ") + zone.getByte("z"));
			rectangle(grid, x.minInclusive, z.minInclusive, x.maxExclusive, z.maxExclusive,
					zone.getInteger("color"), 0.14F, xo, yo, width, height);
		}
		boolean sameMap = preview && map.getString("map").equals(publicMap.getString("map"));
		if(!sameMap || !publicMap.hasKey("bounds")) mapArea(grid, map.getCompoundTag("bounds"), 0x6BC8FF, 0F, xo, yo, width, height);
		if(!sameMap || !publicMap.hasKey("bombA")) mapArea(grid, map.getCompoundTag("bombA"), 0xFFD262, 0.2F, xo, yo, width, height);
		if(!sameMap || !publicMap.hasKey("bombB")) mapArea(grid, map.getCompoundTag("bombB"), 0xFF965E, 0.2F, xo, yo, width, height);
		NBTTagList exemptions = map.getTagList("exemptions", 10);
		for(int i = 0; preview && i < exemptions.tagCount(); i++) {
			NBTTagCompound a = exemptions.getCompoundTagAt(i);
			rectangle(grid, a.getInteger("x1"), a.getInteger("z1"), a.getInteger("x2") + 1D,
					a.getInteger("z2") + 1D, 0xBF9BFF, 0F, xo, yo, width, height);
		}
		NBTTagList spawns = map.getTagList("spawns", 10);
		for(int i = 0; preview && i < spawns.tagCount(); i++) {
			NBTTagCompound spawn = spawns.getCompoundTagAt(i);
			String team = spawn.getString("team");
			int color = "red".equals(team) ? 0xFF6565 : "blue".equals(team) ? 0x6BA9FF : 0xFFFFFF;
			rectangle(grid, spawn.getInteger("x") - 2D, spawn.getInteger("z") - 2D,
					spawn.getInteger("x") + 3D, spawn.getInteger("z") + 3D, color, 0.75F, xo, yo, width, height);
		}
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		try {
			if(!sameMap || !publicMap.hasKey("bombA")) labelArea(grid, map.getCompoundTag("bombA"), "A", 0xFFD262, xo, yo, width, height);
			if(!sameMap || !publicMap.hasKey("bombB")) labelArea(grid, map.getCompoundTag("bombB"), "B", 0xFF965E, xo, yo, width, height);
			for(int i = 0; preview && i < spawns.tagCount(); i++) {
				NBTTagCompound spawn = spawns.getCompoundTagAt(i);
				String team = spawn.getString("team");
				label(grid, spawn.getInteger("x") + 3D, spawn.getInteger("z"), "red".equals(team) ? "R" : "blue".equals(team) ? "B" : "F",
						"red".equals(team) ? 0xFF6565 : "blue".equals(team) ? 0x6BA9FF : 0xFFFFFF, xo, yo, width, height);
			}
		} finally { GL11.glDisable(GL11.GL_TEXTURE_2D); }
	}
	private void labelArea(Object grid, NBTTagCompound area, String text, int color, double xo, double yo, int width, int height) throws Exception {
		if(!ClientTDMMapOverlay.areaVisible(area)) return;
		label(grid, ((long)area.getInteger("x1") + area.getInteger("x2")) / 2D,
				((long)area.getInteger("z1") + area.getInteger("z2")) / 2D, text, color, xo, yo, width, height);
	}
	private void label(Object grid, double x, double z, String text, int color, double xo, double yo, int width, int height) throws Exception {
		Point2D p = point(grid, x, z);
		int px = (int)Math.round(p.getX() + xo), py = (int)Math.round(p.getY() + yo);
		if(px < 0 || py < 0 || px >= width || py >= height) return;
		Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(text, px, py, color);
	}
	private void mapArea(Object grid, NBTTagCompound area, int color, float fill, double xo, double yo, int width, int height) throws Exception {
		if(!ClientTDMMapOverlay.areaVisible(area)) return;
		BlockAreaEdges edges = BlockAreaEdges.of(area.getInteger("x1"), area.getInteger("z1"),
				area.getInteger("x2"), area.getInteger("z2"));
		rectangle(grid, edges.minX, edges.minZ, edges.maxXExclusive, edges.maxZExclusive,
				color, fill, xo, yo, width, height);
	}
	private void rectangle(Object grid, double x1, double z1, double x2, double z2, int color, float fill,
			double xo, double yo, int width, int height) throws Exception {
		Point2D p0 = point(grid, x1, z1), p1 = point(grid, x2, z1), p2 = point(grid, x2, z2), p3 = point(grid, x1, z2);
		double minX = Math.min(Math.min(p0.getX(), p1.getX()), Math.min(p2.getX(), p3.getX())) + xo;
		double maxX = Math.max(Math.max(p0.getX(), p1.getX()), Math.max(p2.getX(), p3.getX())) + xo;
		double minY = Math.min(Math.min(p0.getY(), p1.getY()), Math.min(p2.getY(), p3.getY())) + yo;
		double maxY = Math.max(Math.max(p0.getY(), p1.getY()), Math.max(p2.getY(), p3.getY())) + yo;
		if(maxX < 0 || maxY < 0 || minX > width || minY > height) return;
		float r = ((color >> 16) & 255) / 255F, g = ((color >> 8) & 255) / 255F, b = (color & 255) / 255F;
		Tessellator t = Tessellator.instance;
		if(fill > 0F) { GL11.glColor4f(r,g,b,fill); t.startDrawingQuads(); vertex(t,p0,xo,yo); vertex(t,p1,xo,yo); vertex(t,p2,xo,yo); vertex(t,p3,xo,yo); t.draw(); }
		GL11.glColor4f(r,g,b,0.9F); GL11.glLineWidth(2F); t.startDrawing(GL11.GL_LINES);
		edge(t,p0,p1,xo,yo); edge(t,p1,p2,xo,yo); edge(t,p2,p3,xo,yo); edge(t,p3,p0,xo,yo); t.draw();
	}
    private void prepareProjection(Object grid,double x,double z)throws Exception {
        originX=x;originZ=z;
        Point2D zero=(Point2D)reflection.getBlockPixel.invoke(grid,x,z);
        Point2D east=(Point2D)reflection.getBlockPixel.invoke(grid,x+1024,z);
        Point2D south=(Point2D)reflection.getBlockPixel.invoke(grid,x,z+1024);
        baseX=zero.getX();baseY=zero.getY();
        ax=(east.getX()-baseX)/1024;bx=(east.getY()-baseY)/1024;
        az=(south.getX()-baseX)/1024;bz=(south.getY()-baseY)/1024;
        if(!Double.isFinite(ax)||!Double.isFinite(az)||!Double.isFinite(bx)||!Double.isFinite(bz)||Math.abs(ax*bz-az*bx)<1e-20)
            throw new IllegalStateException("JourneyMap projection is not invertible");
        Point2D diagonal=(Point2D)reflection.getBlockPixel.invoke(grid,x+512,z+512);
        if(Math.abs(diagonal.getX()-px(x+512,z+512))>1.01 || Math.abs(diagonal.getY()-py(x+512,z+512))>1.01)
            throw new IllegalStateException("Unsupported non-affine JourneyMap projection");
    }
    private double px(double x,double z){return baseX+(x-originX)*ax+(z-originZ)*az;}
    private double py(double x,double z){return baseY+(x-originX)*bx+(z-originZ)*bz;}
    private void select(Snapshot snapshot,double xo,double yo,int width,int height) {
        double determinant=ax*bz-az*bx;
        double x0=Double.POSITIVE_INFINITY,z0=x0,x1=-x0,z1=-x0;
        for(int i=0;i<4;i++) {
            double x=((i&1)==0?-8:width+8)-xo-baseX,y=((i&2)==0?-8:height+8)-yo-baseY;
            double wx=originX+(x*bz-y*az)/determinant,wz=originZ+(y*ax-x*bx)/determinant;
            x0=Math.min(x0,wx);x1=Math.max(x1,wx);z0=Math.min(z0,wz);z1=Math.max(z1,wz);
        }
        snapshot.prepared.select(x0,z0,x1,z1,visible,labels);
    }
    private void vertex(Tessellator t,double x,double z,double xo,double yo){t.addVertex(px(x,z)+xo,py(x,z)+yo,0);}
    private void segment(Tessellator t,double x,double z,double xx,double zz,double xo,double yo){vertex(t,x,z,xo,yo);vertex(t,xx,zz,xo,yo);}
    private void renderClaims(double xo,double yo) {
        Tessellator t=Tessellator.instance;
        for(int pass=0;pass<2;pass++) {
            if(pass==1)GL11.glLineWidth((float)XFConfig.journeyMapClaimBorderWidth);
            t.startDrawing(pass==0?GL11.GL_QUADS:GL11.GL_LINES);
            for(int i=0;i<visible.size();i++) {
                PreparedClaims.Cell cell=visible.get(i);int color=cell.claim.color;
                t.setColorRGBA_F((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,
                    (float)(pass==0?XFConfig.journeyMapClaimFillOpacity:XFConfig.journeyMapClaimBorderOpacity));
                if(pass==0){vertex(t,cell.x0,cell.z0,xo,yo);vertex(t,cell.x1,cell.z0,xo,yo);vertex(t,cell.x1,cell.z1,xo,yo);vertex(t,cell.x0,cell.z1,xo,yo);}
                else {
                    if((cell.edges&1)!=0)segment(t,cell.x0,cell.z0,cell.x1,cell.z0,xo,yo);
                    if((cell.edges&2)!=0)segment(t,cell.x1,cell.z0,cell.x1,cell.z1,xo,yo);
                    if((cell.edges&4)!=0)segment(t,cell.x1,cell.z1,cell.x0,cell.z1,xo,yo);
                    if((cell.edges&8)!=0)segment(t,cell.x0,cell.z1,cell.x0,cell.z0,xo,yo);
                }
            }
            t.draw();
        }
    }
	private void renderLabel(Object grid, PreparedClaims.Group prepared, double xo, double yo, int width, int height) throws Exception {
		TerritoryGroup group=prepared.group;
		if(group.label == null || group.label.length() == 0) return;
        double minWorldX=prepared.x0,maxWorldX=prepared.x1,minWorldZ=prepared.z0,maxWorldZ=prepared.z1;
		Point2D b0 = point(grid, minWorldX, minWorldZ), b1 = point(grid, maxWorldX, minWorldZ);
		Point2D b2 = point(grid, maxWorldX, maxWorldZ), b3 = point(grid, minWorldX, maxWorldZ);
		double minX = Math.min(Math.min(b0.getX(), b1.getX()), Math.min(b2.getX(), b3.getX())) + xo, maxX = Math.max(Math.max(b0.getX(), b1.getX()), Math.max(b2.getX(), b3.getX())) + xo;
		double minY = Math.min(Math.min(b0.getY(), b1.getY()), Math.min(b2.getY(), b3.getY())) + yo, maxY = Math.max(Math.max(b0.getY(), b1.getY()), Math.max(b2.getY(), b3.getY())) + yo;
		if(maxX < 0 || maxY < 0 || minX > width || minY > height || maxX - minX < 12D || maxY - minY < 8D) return;
		Minecraft mc = Minecraft.getMinecraft(); if(mc == null || mc.fontRenderer == null) return;
		String text = fit(group.label, Math.max(0, (int)Math.floor(maxX - minX) - 4), mc.fontRenderer);
		if(text.length() == 0) return;
		Point2D label = point(grid, group.labelX, group.labelZ);
		int textWidth = mc.fontRenderer.getStringWidth(text);
		int x = (int)Math.round(label.getX() + xo - textWidth / 2D), y = (int)Math.round(label.getY() + yo - mc.fontRenderer.FONT_HEIGHT / 2D);
		if(x + textWidth < 0 || y + mc.fontRenderer.FONT_HEIGHT < 0 || x > width || y > height) return;
		mc.fontRenderer.drawStringWithShadow(text, x, y, 0xFFFFFF);
	}
	private String fit(String label, int maxWidth, net.minecraft.client.gui.FontRenderer font) {
		if(maxWidth <= 0 || font.getStringWidth(label) <= maxWidth) return maxWidth <= 0 ? "" : label;
		String ellipsis = "..."; int ellipsisWidth = font.getStringWidth(ellipsis); if(ellipsisWidth > maxWidth) return "";
		String text = label;
		while(text.length() > 0 && font.getStringWidth(text) + ellipsisWidth > maxWidth) text = text.substring(0, text.length() - 1);
		return text.length() == 0 ? "" : text + ellipsis;
	}
	private Point2D point(Object grid,double x,double z) throws Exception { return new Point2D.Double(px(x,z),py(x,z)); }
	private static void vertex(Tessellator t,Point2D p,double x,double y){t.addVertex(p.getX()+x,p.getY()+y,0);}
	private static void edge(Tessellator t,Point2D a,Point2D b,double x,double y){vertex(t,a,x,y);vertex(t,b,x,y);}
}
