package com.hfr.render.tileentity;

import com.hfr.blocks.ModBlocks;
import com.hfr.main.ResourceManager;
import com.hfr.tileentity.clowder.TileEntityFactionDecoration;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class RenderFactionDecoration extends TileEntitySpecialRenderer {
    private static final Runnable LARGE_CLOTH = () -> ResourceManager.flag_big.renderOnly("Flag");
    private static final Runnable BANNER_CLOTH = () -> {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setNormal(0, 0, 1);
        tess.addVertexWithUV(-.3125, .03125, -.4375, 0, 1);
        tess.addVertexWithUV(.3125, .03125, -.4375, 1, 1);
        tess.addVertexWithUV(.3125, .96875, -.4375, 1, 0);
        tess.addVertexWithUV(-.3125, .96875, -.4375, 0, 0);
        tess.draw();
    };

    @Override public void renderTileEntityAt(TileEntity raw, double x, double y, double z, float partialTick) {
        TileEntityFactionDecoration tile = (TileEntityFactionDecoration)raw;
        boolean large = tile.getBlockType() == ModBlocks.large_faction_flag;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x + .5D, y, z + .5D);
            GL11.glEnable(GL11.GL_LIGHTING);
            if(large) {
                GL11.glRotatef(tile.getBlockMetadata() * 90, 0, 1, 0);
                bindTexture(ResourceManager.flag_tex);
                GL11.glColor4f(1, 1, 1, 1);
                GL11.glPushMatrix();
                // Keep the original pole height; fit its foot into the occupied base block.
                GL11.glScalef(.5F, 1, .5F);
                ResourceManager.flag_big.renderOnly("Pole");
                GL11.glPopMatrix();
            } else {
                int facing = tile.getBlockMetadata();
                GL11.glRotatef(facing == 2 ? 180 : facing == 4 ? 270 : facing == 5 ? 90 : 0, 0, 1, 0);
            }
            FlagDesignRenderer.render(tile.getFlag(), tile.getColor(), tile.getFactionName(), tile.getCustomFlagHash(),
                large ? LARGE_CLOTH : BANNER_CLOTH);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
