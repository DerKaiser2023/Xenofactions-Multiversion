package com.hfr.render.tileentity;

import org.lwjgl.opengl.GL11;
import com.hfr.client.render.RenderState;

import com.hfr.main.ResourceManager;
import com.hfr.tileentity.machine.TileEntityMachineNet;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

public class RenderNet extends TileEntitySpecialRenderer {


    @Override
	public void renderTileEntityAt(TileEntity tileEntity, double x, double y, double z, float f)
    {
        com.hfr.client.render.RenderMetrics.tesr(3);
        try(RenderState state = RenderState.model()) {
        GL11.glTranslated(x + 0.5D, y + 0.0625D, z + 0.5D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_CULL_FACE);

        TileEntityMachineNet net = (TileEntityMachineNet)tileEntity;
        
        if(net.isJammed)
            com.hfr.client.render.RenderMetrics.bind(ResourceManager.net_jammed_tex);
        else
            com.hfr.client.render.RenderMetrics.bind(ResourceManager.net_tex);
        
        ResourceManager.net.renderAll();
        
        }
    }

}
