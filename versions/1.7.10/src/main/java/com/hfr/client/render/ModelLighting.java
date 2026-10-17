package com.hfr.client.render;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.tileentity.TileEntity;

public final class ModelLighting {
    private ModelLighting() { }
    public static void cloth(TileEntity tile,double height) {
        int y=(int)Math.floor(tile.yCoord+height);
        int light=tile.getWorldObj().getLightBrightnessForSkyBlocks(tile.xCoord,y,tile.zCoord,0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
    }
}
