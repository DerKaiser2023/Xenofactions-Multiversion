package com.hfr.render;

import com.hfr.leaderboard.FactionLeaderboards.Metric;
import com.hfr.tileentity.TileEntityFactionLeaderboard;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

/** Four-block-wide, three-block-high panel above a single solid controller block. */
@SideOnly(Side.CLIENT)
public final class RenderFactionLeaderboard extends TileEntitySpecialRenderer {
    @Override
    public void renderTileEntityAt(TileEntity raw, double x, double y, double z, float partialTicks) {
        TileEntityFactionLeaderboard tile = (TileEntityFactionLeaderboard) raw;
        Metric metric = tile.getMetric();
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x + .5D, y, z + .5D);
            GL11.glRotatef((tile.getBlockMetadata() & 3) * 90F, 0, 1, 0);
            GL11.glDisable(GL11.GL_LIGHTING);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(.035F, .05F, .075F, 1F);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertex(-2, 0, .505D);
            tessellator.addVertex(2, 0, .505D);
            tessellator.addVertex(2, 3, .505D);
            tessellator.addVertex(-2, 3, .505D);
            tessellator.draw();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glTranslated(-1.85D, 2.8D, .51D);
            GL11.glScalef(.01F, -.01F, .01F);
            FontRenderer font = func_147498_b();
            font.drawString("FACTION LEADERBOARD", 0, 0, 0xFFD36A);
            font.drawString(metric.title + (metric.unit.isEmpty() ? "" : " (" + metric.unit + ")"), 0, 18, 0xFFFFFF);
            font.drawString("Rank", 0, 42, 0xAABBDD);
            font.drawString("Faction", 35, 42, 0xAABBDD);
            font.drawString("Value", 370 - font.getStringWidth("Value"), 42, 0xAABBDD);
            if (!metric.unavailable.isEmpty()) font.drawString(metric.unavailable, 0, 70, 0xFFD36A);
            else if (tile.rowCount() == 0) font.drawString("No eligible factions.", 0, 70, 0xCCCCCC);
            for (int i = 0; i < tile.rowCount(); i++) {
                NBTTagCompound row = tile.row(i);
                int line = 64 + i * 18;
                font.drawString(Integer.toString(row.getInteger("rank")), 0, line, 0xFFD36A);
                font.drawString(font.trimStringToWidth(row.getString("name"), 225), 35, line, 0xFFFFFF);
                String value = row.getString("value");
                font.drawString(value, 370 - font.getStringWidth(value), line, 0xFFFFFF);
            }
            font.drawString("Top 10 | Server refresh: 200 ticks", 0, 258, 0xAABBDD);
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
