package com.hfr.client.render;

import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;

/** One scope per renderer/pass, never per vertex or model part. Client thread only. */
public final class RenderState implements AutoCloseable {
    private final float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
    public RenderState() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_FOG_BIT | GL11.GL_LINE_BIT
            | GL11.GL_POLYGON_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
    }
    /** Forge TESR model pass baseline; callers then select transparency/two-sided/lightmap parameters. */
    public static RenderState model() {
        RenderState state=new RenderState();
        GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.1F);
        GL11.glDisable(GL11.GL_BLEND);GL11.glColor4f(1,1,1,1);
        return state;
    }
    @Override public void close() {
        GL11.glPopMatrix();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
        GL11.glPopAttrib();
    }
}
