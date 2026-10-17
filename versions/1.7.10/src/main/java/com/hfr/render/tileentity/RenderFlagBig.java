package com.hfr.render.tileentity;

import org.lwjgl.opengl.GL11;
import com.hfr.client.render.RenderState;

import com.hfr.client.flag.FactionFlagTextureManager;
import com.hfr.clowder.ClowderFlag;
import com.hfr.main.ResourceManager;
import com.hfr.tileentity.clowder.TileEntityFlagBig;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

public class RenderFlagBig extends TileEntitySpecialRenderer {
	private static final Runnable CLOTH = () -> ResourceManager.flag_big.renderPart("Flag");

	@Override
	public void renderTileEntityAt(TileEntity te, double x, double y, double z, float interp) {
        com.hfr.client.render.RenderMetrics.tesr(7);
		
try(RenderState state = RenderState.model()) {
        TileEntityFlagBig flagpole = (TileEntityFlagBig)te;
        com.hfr.client.render.RenderMetrics.bind(FlagBeamRenderer.BEACON_BEAM);
        FlagBeamRenderer.render(te, x, y, z, interp, flagpole.color);


        GL11.glTranslated(x + 0.5D, y, z + 0.5D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_CULL_FACE);
		
		switch(te.getBlockMetadata())
		{
		case 2:
			GL11.glRotatef(90, 0F, 1F, 0F); break;
		case 4:
			GL11.glRotatef(180, 0F, 1F, 0F); break;
		case 3:
			GL11.glRotatef(270, 0F, 1F, 0F); break;
		case 5:
			GL11.glRotatef(0, 0F, 1F, 0F); break;
		}

        com.hfr.client.render.RenderMetrics.bind(ResourceManager.flag_tex);

        GL11.glShadeModel(GL11.GL_SMOOTH);
        ResourceManager.flag_big.renderPart("Pole");
        GL11.glShadeModel(GL11.GL_FLAT);

        GL11.glDisable(GL11.GL_CULL_FACE);

        ClowderFlag flag = flagpole.flag;

        if(flag == ClowderFlag.NONE || flag == null) {
        	flag = ClowderFlag.TRICOLOR;
        }

	    if(!flagpole.isClaimed)
	    	GL11.glTranslatef(0, -8F, 0);

        boolean hasCustomFlag = flagpole.customFlagHash != null && flagpole.customFlagHash.length() > 0;
        com.hfr.client.render.ModelLighting.cloth(te, flagpole.isClaimed ? 9 : 1);
        FlagDesignRenderer.render(flag, flagpole.color, flagpole.ownerName, flagpole.customFlagHash, CLOTH);

        GL11.glEnable(GL11.GL_CULL_FACE);
        }
	}
}
