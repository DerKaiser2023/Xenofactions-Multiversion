package com.hfr.render.tileentity;

import org.lwjgl.opengl.GL11;
import com.hfr.client.render.RenderState;

import com.hfr.blocks.BlockDummyable;
import com.hfr.blocks.ModBlocks;
import com.hfr.main.ResourceManager;
import com.hfr.render.tmt.*;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

public class RenderProp extends TileEntitySpecialRenderer {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float p_147500_8_) {
        com.hfr.client.render.RenderMetrics.tesr(4);
		
        try(RenderState state = RenderState.model()) {
        GL11.glTranslated(x + 0.5D, y, z + 0.5D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_CULL_FACE);
        
        Block b = tile.getBlockType();
        if(b == ModBlocks.hesco_block || b == ModBlocks.tp_tent) {
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glAlphaFunc(GL11.GL_GREATER,.001F);
        }

        if(b == ModBlocks.hesco_block) GL11.glRotatef(180, 0F, 1F, 0F);

        if(b == ModBlocks.tp_tent) GL11.glRotatef(90, 0F, 1F, 0F);
        if(b == ModBlocks.statue) GL11.glRotatef(90, 0F, 1F, 0F);

		switch(tile.getBlockMetadata() - BlockDummyable.offset)
		{
		case 2: GL11.glRotatef(90, 0F, 1F, 0F); break;
		case 4: GL11.glRotatef(180, 0F, 1F, 0F); break;
		case 3: GL11.glRotatef(270, 0F, 1F, 0F); break;
		case 5: GL11.glRotatef(0, 0F, 1F, 0F); break;
		}
		
		float p = 0.0625F;

        if(b == ModBlocks.palisade) GL11.glTranslated(-p * 0.5, 0, -0.5 - p * 0.5);
        if(b == ModBlocks.stone_wall) GL11.glTranslated(0, 0, 0.5);
        if(b == ModBlocks.brick_wall) GL11.glTranslated(0, 0, 0.5);
        if(b == ModBlocks.great_wall) GL11.glTranslated(0, 0, 0.5);
        if(b == ModBlocks.berlin_wall) GL11.glTranslated(0, 0, 0.5);

        if(b == ModBlocks.tp_tent) GL11.glTranslated(1.5 + p * 2, 0, 2.25 + p * 1.5);
        if(b == ModBlocks.statue) GL11.glTranslated(0, 0, p * 3);
		
        if(b == ModBlocks.hesco_block) {
            com.hfr.client.render.RenderMetrics.bind(ResourceManager.tmt_hescoblock);
            ResourceManager.hesco.renderAll();
        }
		
        if(b == ModBlocks.tp_tent) {
            com.hfr.client.render.RenderMetrics.bind(ResourceManager.tmt_tptent);
            ResourceManager.tent.renderAll();
        }
		
        if(b == ModBlocks.statue) {
            com.hfr.client.render.RenderMetrics.bind(ResourceManager.tmt_statue);
            ResourceManager.statue.renderAll();
        }

        }
	}

}
