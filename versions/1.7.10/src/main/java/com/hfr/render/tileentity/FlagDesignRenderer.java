package com.hfr.render.tileentity;

import com.hfr.client.flag.FactionFlagTextureManager;
import com.hfr.clowder.ClowderFlag;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;

/** Shared built-in tint/overlay and hash-cached custom-image passes. Geometry supplies the same UVs. */
@SideOnly(Side.CLIENT)
public final class FlagDesignRenderer {
    private FlagDesignRenderer() { }

    public static void render(ClowderFlag flag, int color, String owner, String hash, Runnable geometry) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            boolean custom = owner != null && !owner.isEmpty() && hash != null && !hash.isEmpty();
            if(!custom && (flag == null || flag == ClowderFlag.NONE)) {
                // An unbound/deleted faction has a plain neutral cloth, with no stale design.
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(.65F, .65F, .65F, 1);
                geometry.run();
                return;
            }
            Minecraft.getMinecraft().getTextureManager().bindTexture(custom ? FactionFlagTextureManager.getFlagTexture(owner) : flag.getFlag());
            if(custom) GL11.glColor4f(1, 1, 1, 1);
            else GL11.glColor3b((byte)(((color >> 16) & 255) / 2), (byte)(((color >> 8) & 255) / 2), (byte)((color & 255) / 2));
            geometry.run();
            if(!custom) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(flag.getFlagOverlay());
                GL11.glColor4f(1, 1, 1, 1);
                geometry.run();
            }
        } finally { GL11.glPopAttrib(); }
    }
}
