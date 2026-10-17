package com.hfr.render.tileentity;

import com.hfr.blocks.BlockDummyable;
import com.hfr.client.render.*;
import com.hfr.main.ResourceManager;
import com.hfr.tileentity.machine.TileEntityMachineGrainmill;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

public class RenderMill extends TileEntitySpecialRenderer {
    @Override public void renderTileEntityAt(TileEntity tile,double x,double y,double z,float partial) {
        com.hfr.client.render.RenderMetrics.tesr(9);
        if(RenderMetrics.ENABLED)RenderMetrics.mills++;
        TileEntityMachineGrainmill mill=(TileEntityMachineGrainmill)tile;
        try(RenderState state=RenderState.model()) {
            GL11.glTranslated(x+.5,y,z+.5);GL11.glEnable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glAlphaFunc(GL11.GL_GREATER,.001F);
            GL11.glRotatef(-90+ModelBounds.rotation(tile.getBlockMetadata()-BlockDummyable.offset),0,1,0);
            com.hfr.client.render.RenderMetrics.bind(ResourceManager.tmt_mill);
            ResourceManager.mill.renderPart("Body");
            GL11.glTranslatef(0,GpuTmtModels.ROTOR_Y,GpuTmtModels.ROTOR_Z);
            GL11.glRotatef((mill.previousRotation+(mill.rotation-mill.previousRotation)*partial)*57.29578F,0,0,1);
            ResourceManager.mill.renderPart("Rotor");
        }
    }
}
