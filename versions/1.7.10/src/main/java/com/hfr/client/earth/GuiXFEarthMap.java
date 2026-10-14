package com.hfr.client.earth;

import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.client.EarthMapRequestPacket;
import com.hfr.world.earth.map.XFEarthMapData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.nio.IntBuffer;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class GuiXFEarthMap extends GuiScreen {
    private static int nextRequest;
    // LWJGL 2 requires 16 remaining integers even though GL_SCISSOR_BOX returns four.
    private final IntBuffer scissor = BufferUtils.createIntBuffer(16);
    private XFEarthMapData data;
    private EarthMapImages images;
    private World requestedWorld;
    private int request, dimension, cacheGeneration;
    private String message = "gui.hfr.earthmap.loading";
    private double zoom = 1, centerX = .5, centerZ = .5;
    private int lastX, lastY;
    private boolean dragging;
    private int roadRequest,roadTicks;private String roadView="",roadNotice="";
    private double[][] roadParts;private byte[][] roadStyles;

    @Override public void initGui() {
        buttonList.clear();
        int start = width / 2 - 139;
        buttonList.add(new GuiButton(0, start, height - 24, 26, 20, "+"));
        buttonList.add(new GuiButton(1, start + 30, height - 24, 26, 20, "-"));
        buttonList.add(new GuiButton(2, start + 60, height - 24, 86, 20, I18n.format("gui.hfr.earthmap.center")));
        buttonList.add(new GuiButton(3, start + 150, height - 24, 70, 20, I18n.format("gui.hfr.earthmap.full")));
        buttonList.add(new GuiButton(4, start + 224, height - 24, 54, 20, I18n.format("gui.done")));
        if (requestedWorld != mc.theWorld) requestProfile();
        clampView();
    }
    private void requestProfile() {
        requestedWorld = mc.theWorld; data = null; images = null; dragging = false;
        roadView="";roadNotice="";roadParts=null;roadStyles=null;
        message = "gui.hfr.earthmap.loading"; zoom = 1; centerX = centerZ = .5;
        if (mc.thePlayer == null || requestedWorld == null) { message = "gui.hfr.earthmap.unavailable"; return; }
        dimension = requestedWorld.provider.dimensionId; request = ++nextRequest;
        PacketDispatcher.wrapper.sendToServer(new EarthMapRequestPacket(request, dimension));
    }
    public void receive(int token, int dimension, String profile, String identity, String error) {
        if (token != request || dimension != this.dimension || requestedWorld != mc.theWorld) return;
        if (!error.isEmpty()) { message = error; return; }
        cacheGeneration = EarthMapTileCache.generation();
        try {
            data = XFEarthMapData.decode(profile, identity);
            images = EarthMapImages.load(data);
            message = "";
        } catch (Exception failure) { images = null; message = "gui.hfr.earthmap.images_missing"; }
        clampView();
    }
    @Override public void updateScreen() {
        if (mc.theWorld != requestedWorld || mc.theWorld != null && mc.theWorld.provider.dimensionId != dimension) requestProfile();
        if (data != null && cacheGeneration != EarthMapTileCache.generation()) {
            cacheGeneration = EarthMapTileCache.generation();
            try { images = EarthMapImages.load(data); message = ""; }
            catch (Exception failure) { images = null; message = "gui.hfr.earthmap.images_missing"; }
        }
        if(data!=null&&images!=null&&++roadTicks%40==0)requestRoads();
    }
    private void requestRoads(){
        int x0=Math.max(data.profile.getBounds().minimumX,(int)Math.floor(data.profile.getBounds().minimumX+(left()-originX())/mapWidth()*data.profile.getWidth()));
        int z0=Math.max(data.profile.getBounds().minimumZ,(int)Math.floor(data.profile.getBounds().minimumZ+(top()-originZ())/mapHeight()*data.profile.getHeight()));
        int x1=Math.min(data.profile.getBounds().maximumX,(int)Math.floor(data.profile.getBounds().minimumX+(left()+viewWidth()-originX())/mapWidth()*data.profile.getWidth()));
        int z1=Math.min(data.profile.getBounds().maximumZ,(int)Math.floor(data.profile.getBounds().minimumZ+(top()+viewHeight()-originZ())/mapHeight()*data.profile.getHeight()));
        String view=x0+":"+z0+":"+x1+":"+z1;if(view.equals(roadView))return;roadView=view;roadParts=null;roadStyles=null;roadRequest=++nextRequest;
        if(x1<x0||z1<z0)return;
        long regions=((long)com.hfr.world.earth.geo.XFEarthGeoImporter.region(x1)-com.hfr.world.earth.geo.XFEarthGeoImporter.region(x0)+1)
            *((long)com.hfr.world.earth.geo.XFEarthGeoImporter.region(z1)-com.hfr.world.earth.geo.XFEarthGeoImporter.region(z0)+1);
        if(regions>16){roadNotice="Source geography: zoom in to view the bounded layer";return;}roadNotice="Loading source geography…";
        PacketDispatcher.wrapper.sendToServer(new com.hfr.packet.client.EarthRoadRequestPacket(roadRequest,dimension,x0,z0,x1,z1));
    }
    public void receiveRoads(int token,int dimension,int part,int parts,double[] coords,byte[] styles,String notice){
        if(token!=roadRequest||dimension!=this.dimension||requestedWorld!=mc.theWorld||data==null)return;
        if(roadParts==null){roadParts=new double[parts][];roadStyles=new byte[parts][];}if(roadParts.length!=parts)return;
        roadParts[part]=coords;roadStyles[part]=styles;roadNotice=notice;
        if(notice.contains("busy"))roadView="";
    }
    @Override public boolean doesGuiPauseGame() { return false; }
    private int left() { return 10; }
    private int top() { return 38; }
    private int viewWidth() { return Math.max(1, width - 20); }
    private int viewHeight() { return Math.max(1, height - 110); }
    private double mapWidth() { return data.profile.getWidth() * fit() * zoom; }
    private double mapHeight() { return data.profile.getHeight() * fit() * zoom; }
    private double fit() { return Math.min((double)viewWidth() / data.profile.getWidth(), (double)viewHeight() / data.profile.getHeight()); }
    private double originX() { return left() + viewWidth() / 2.0 - centerX * mapWidth(); }
    private double originZ() { return top() + viewHeight() / 2.0 - centerZ * mapHeight(); }
    private static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
    private void clampView() {
        if (data == null) return;
        // Permit the viewport to overhang an edge so Center on you really centers edge positions.
        centerX = clamp(centerX, 0, 1); centerZ = clamp(centerZ, 0, 1);
    }
    private boolean inside(int x, int y) { return x >= left() && x < left() + viewWidth() && y >= top() && y < top() + viewHeight(); }
    private void changeZoom(double factor, int x, int y) {
        if (images == null) return;
        double u = (x - originX()) / mapWidth(), v = (y - originZ()) / mapHeight();
        zoom = clamp(zoom * factor, 1, 32);
        centerX = u - (x - left() - viewWidth() / 2.0) / mapWidth();
        centerZ = v - (y - top() - viewHeight() / 2.0) / mapHeight();
        clampView();
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 4) { mc.displayGuiScreen(null); return; }
        if (images == null) return;
        if (button.id == 0 || button.id == 1) changeZoom(button.id == 0 ? 1.5 : 1 / 1.5, width / 2, top() + viewHeight() / 2);
        if (button.id == 2 && mc.thePlayer != null) {
            centerX = data.mapX(mc.thePlayer.posX); centerZ = data.mapZ(mc.thePlayer.posZ); clampView();
        }
        if (button.id == 3) { zoom = 1; centerX = centerZ = .5; }
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        if (button == 0 && images != null && inside(x, y)) { dragging = true; lastX = x; lastY = y; }
    }
    @Override protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (dragging && button == 0 && images != null) {
            centerX -= (x - lastX) / mapWidth(); centerZ -= (y - lastY) / mapHeight();
            lastX = x; lastY = y; clampView();
        }
    }
    @Override protected void mouseMovedOrUp(int x, int y, int button) { super.mouseMovedOrUp(x, y, button); if (button == 0) dragging = false; }
    @Override public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        int x = Mouse.getEventX() * width / mc.displayWidth, y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (wheel != 0 && inside(x, y)) changeZoom(wheel > 0 ? 1.25 : 1 / 1.25, x, y);
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("item.earth_map.name"), width / 2, 7, 0xffffff);
        if (data != null) drawCenteredString(fontRendererObj, fontRendererObj.trimStringToWidth(data.profile.getProfile()
                + "  1:" + data.profile.getEffectiveScale(), viewWidth()), width / 2, 22, 0xcccccc);
        drawRect(left() - 1, top() - 1, left() + viewWidth() + 1, top() + viewHeight() + 1, 0xff777777);
        drawRect(left(), top(), left() + viewWidth(), top() + viewHeight(), 0xff182333);
        if (images != null && requestedWorld == mc.theWorld) drawMap();
        else drawStatus(message);
        if (mc.thePlayer != null) {
            double x = mc.thePlayer.posX, z = mc.thePlayer.posZ;
            String coords = String.format(Locale.ROOT, "X: %.1f   Z: %.1f", x, z);
            drawCenteredString(fontRendererObj, coords, width / 2, height - 65, 0xffffff);
            if (data != null && data.hasGeographicConversion() && data.contains(x, z))
                drawCenteredString(fontRendererObj, I18n.format("gui.hfr.earthmap.geographic",
                        String.format(Locale.ROOT, "%.5f", data.latitude(z)), String.format(Locale.ROOT, "%.5f", data.longitude(x))), width / 2, height - 53, 0xffffff);
            else if (data != null && !data.contains(x, z))
                drawCenteredString(fontRendererObj, I18n.format("gui.hfr.earthmap.outside"), width / 2, height - 53, 0xffcc66);
        }
        drawCenteredString(fontRendererObj, I18n.format("gui.hfr.earthmap.controls"), width / 2, height - 37, 0xaaaaaa);
        for (Object object : buttonList) { GuiButton button = (GuiButton)object; if (button.id != 4) button.enabled = images != null; }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    private void drawStatus(String key) {
        java.util.List<?> lines = fontRendererObj.listFormattedStringToWidth(I18n.format(key), Math.max(1, viewWidth() - 16));
        int y = top() + (viewHeight() - lines.size() * 11) / 2;
        for (Object line : lines) { drawCenteredString(fontRendererObj, line.toString(), width / 2, y, 0xffffff); y += 11; }
    }
    private void drawMap() {
        boolean wasScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        // Native writes do not advance the position; restore the four values with indexed reads.
        scissor.clear(); GL11.glGetInteger(GL11.GL_SCISSOR_BOX, scissor);
        int scale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        try {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(left() * scale, mc.displayHeight - (top() + viewHeight()) * scale, viewWidth() * scale, viewHeight() * scale);
            double w = mapWidth(), h = mapHeight(), ox = originX(), oz = originZ();
            int level = 0;
            for (int i = 1; i < images.levels.length; i++) {
                EarthMapImages.Level candidate = images.levels[i];
                // Keep a whole visible level within the 32-tile texture budget, including square maps.
                if ((long)((candidate.width + 255) / 256) * ((candidate.height + 255) / 256) <= 32
                        && images.levels[level].width < w) level = i;
            }
            EarthMapImages.Level size = images.levels[level];
            int minX = Math.max(0, (int)Math.floor((left() - ox) / w * size.width / 256));
            int minZ = Math.max(0, (int)Math.floor((top() - oz) / h * size.height / 256));
            int maxX = Math.min((size.width - 1) / 256, (int)Math.floor((left() + viewWidth() - ox) / w * size.width / 256));
            int maxZ = Math.min((size.height - 1) / 256, (int)Math.floor((top() + viewHeight() - oz) / h * size.height / 256));
            boolean loading = false;
            for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
                ResourceLocation texture = EarthMapTileCache.get(data, images, level, x, z);
                if (texture == null) { loading = true; continue; }
                mc.getTextureManager().bindTexture(texture); GL11.glColor4f(1, 1, 1, 1);
                double x0 = ox + x * 256.0 / size.width * w, z0 = oz + z * 256.0 / size.height * h;
                double x1 = ox + Math.min(size.width, (x + 1) * 256.0) / size.width * w;
                double z1 = oz + Math.min(size.height, (z + 1) * 256.0) / size.height * h;
                Tessellator t = Tessellator.instance; t.startDrawingQuads();
                t.addVertexWithUV(x0, z1, 0, 0, 1); t.addVertexWithUV(x1, z1, 0, 1, 1);
                t.addVertexWithUV(x1, z0, 0, 1, 0); t.addVertexWithUV(x0, z0, 0, 0, 0); t.draw();
            }
            if (loading) drawString(fontRendererObj, I18n.format(EarthMapTileCache.hasFailure()
                    ? "gui.hfr.earthmap.images_missing" : "gui.hfr.earthmap.loading"), left() + 4, top() + 4, 0xffffff);
            if(roadParts!=null){
                GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_LINE_BIT|GL11.GL_CURRENT_BIT);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glLineWidth(2);
                try{GL11.glBegin(GL11.GL_LINES);
                    for(int part=0;part<roadParts.length;part++)if(roadParts[part]!=null)for(int i=0;i<roadStyles[part].length;i++){
                        int style=roadStyles[part][i]&255;GL11.glColor3f(style>=20?.5F:1F,style==21?.5F:.8F,style>=20?1F:.25F);
                        int category=style>=60?style-30:style;
                        if(style>=30)GL11.glColor3f(category>=50?1F:category>=40?.8F:.35F,category>=50?.45F:category>=40?.65F:.9F,style>=60?1F:category>=50?.8F:.35F);
                        double[] c=roadParts[part];double ax=ox+data.mapX(c[i*4])*w,az=oz+data.mapZ(c[i*4+1])*h,bx=ox+data.mapX(c[i*4+2])*w,bz=oz+data.mapZ(c[i*4+3])*h;
                        if(style>=30&&c[i*4]==c[i*4+2]&&c[i*4+1]==c[i*4+3]){GL11.glVertex2d(ax-3,az);GL11.glVertex2d(ax+3,az);GL11.glVertex2d(ax,az-3);GL11.glVertex2d(ax,az+3);}
                        else{GL11.glVertex2d(ax,az);GL11.glVertex2d(bx,bz);}
                    }GL11.glEnd();
                }finally{GL11.glPopAttrib();}
            }
            if(!roadNotice.isEmpty())drawString(fontRendererObj,fontRendererObj.trimStringToWidth(roadNotice,viewWidth()-8),left()+4,top()+viewHeight()-12,0xffffff);
            if (mc.thePlayer != null) {
                double actualX = ox + data.mapX(mc.thePlayer.posX) * w, actualZ = oz + data.mapZ(mc.thePlayer.posZ) * h;
                int px = (int)clamp(actualX, left() + 5, left() + viewWidth() - 6);
                int pz = (int)clamp(actualZ, top() + 5, top() + viewHeight() - 6);
                drawRect(px - 4, pz - 4, px + 5, pz + 5, 0xff000000);
                drawRect(px - 2, pz - 2, px + 3, pz + 3, 0xffff4444);
                String label = I18n.format("gui.hfr.earthmap.you");
                if (actualX < left() || actualX > left() + viewWidth() || actualZ < top() || actualZ > top() + viewHeight())
                    label += " " + I18n.format("gui.hfr.earthmap.offscreen");
                int length = fontRendererObj.getStringWidth(label);
                int lx = (int)clamp(px + 8, left() + 2, left() + viewWidth() - length - 2);
                int ly = (int)clamp(pz - 13, top() + 2, top() + viewHeight() - 11);
                drawRect(lx - 1, ly - 1, lx + length + 1, ly + 10, 0xcc000000);
                drawString(fontRendererObj, label, lx, ly, 0xffffff);
            }
        } finally {
            GL11.glScissor(scissor.get(0), scissor.get(1), scissor.get(2), scissor.get(3));
            if (wasScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColor4f(1, 1, 1, 1);
        }
    }
}
