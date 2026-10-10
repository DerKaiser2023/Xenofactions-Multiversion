package com.hfr.client;

import com.hfr.inventory.CityCenterContainer;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

public final class CityCenterScreen extends ContainerScreen<CityCenterContainer> {
    public CityCenterScreen(CityCenterContainer menu, PlayerInventory inventory, ITextComponent title) { super(menu, inventory, title); imageWidth = 330; imageHeight = 185; }
    @Override protected void init() {
        super.init();
        if (menu.snapshot.getBoolean("move")) addButton(new Button(leftPos + 12, topPos + 150, 150, 20, new StringTextComponent("Begin move"), button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0)));
        addButton(new Button(leftPos + 174, topPos + 150, 140, 20, new StringTextComponent(menu.snapshot.getBoolean("move") ? "Cancel" : "Close"), button -> minecraft.player.closeContainer()));
    }
    @Override protected void renderBg(MatrixStack stack, float partial, int x, int y) { fill(stack, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE20242B); }
    @Override protected void renderLabels(MatrixStack stack, int x, int y) {
        font.draw(stack, title, 12, 10, 0xFFFFFF); line(stack, "City: " + menu.snapshot.getString("city"), 33); line(stack, "Faction: " + menu.snapshot.getString("owner"), 49); line(stack, "Level: " + menu.snapshot.getString("level"), 65);
        line(stack, "Prestige: " + menu.snapshot.getFloat("prestige"), 81); line(stack, "Generation / upkeep: " + menu.snapshot.getFloat("generation") + " / " + menu.snapshot.getFloat("upkeep"), 97);
        line(stack, menu.snapshot.getBoolean("move") ? "Old city stays active until token placement." : "/c city upgrade | /c nameclaim <name>", 122);
    }
    private void line(MatrixStack stack, String text, int y) { font.draw(stack, font.plainSubstrByWidth(text, imageWidth - 24), 12, y, 0xDDDDDD); }
    @Override public void render(MatrixStack stack, int x, int y, float partial) { renderBackground(stack); super.render(stack, x, y, partial); }
}
