package com.hfr.inventory.gui;

import java.util.Arrays;

import org.lwjgl.opengl.GL11;

import com.hfr.inventory.container.ContainerFactory;
import com.hfr.lib.RefStrings;
import com.hfr.tileentity.machine.TileEntityMachineFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

public class GUIFactory extends GuiContainer {

	public static ResourceLocation texture = new ResourceLocation(RefStrings.MODID + ":textures/gui/gui_factory_new.png");
	private TileEntityMachineFactory diFurnace;
	
	public GUIFactory(InventoryPlayer invPlayer, TileEntityMachineFactory tedf) {
		super(new ContainerFactory(invPlayer, tedf));
		diFurnace = tedf;

		this.xSize = 176;
		this.ySize = 168;
	}
	
	@Override
	protected void drawGuiContainerForegroundLayer(int x, int y) {
		String name = this.diFurnace.hasCustomInventoryName() ? this.diFurnace.getInventoryName() : I18n.format(this.diFurnace.getInventoryName());
		
		this.fontRendererObj.drawString(name, this.xSize / 2 - this.fontRendererObj.getStringWidth(name) / 2, 6, 4210752);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, this.ySize - 96 + 2, 4210752);
        if (x >= guiLeft && x < guiLeft + xSize && y >= guiTop + 4 && y < guiTop + 17)
            this.func_146283_a(java.util.Arrays.asList(com.hfr.blocks.StructureInfo.size(diFurnace.getBlockType()), com.hfr.blocks.StructureInfo.axes()), x - guiLeft, y - guiTop);

		if(!diFurnace.operational()) {
			if(guiLeft + 99 <= x && guiLeft + 99 + 10 > x && guiTop + 38 < y && guiTop + 38 + 10 >= y) {
				this.func_146283_a(Arrays.asList(new String[] {"-Factory needs to be placed on top of foundation", "-Factory requires sky access"}), x - guiLeft, y - guiTop);
			}
		}
	}
	
	@Override
	protected void drawGuiContainerBackgroundLayer(float p_146976_1_, int p_146976_2_, int p_146976_3_) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
		drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
		// Cover the retired power gauge using the panel background.
		drawRect(guiLeft + 133, guiTop + 33, guiLeft + 169, guiTop + 52, 0xFFC6C6C6);
		
		if(!diFurnace.operational() || !diFurnace.hasSpace())
			drawTexturedModalRect(guiLeft + 99, guiTop + 38, 176, 0, 10, 10);
	}
}
