package com.hfr.client;

import com.hfr.inventory.FoundationContainer;
import com.hfr.network.FoundationNetwork;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

/** A deliberately bounded first native editor: map bounds only. */
public final class FoundationScreen extends ContainerScreen<FoundationContainer> {
    public FoundationScreen(FoundationContainer container, PlayerInventory inventory, ITextComponent title) {
        super(container, inventory, title); imageWidth = 360; imageHeight = 190;
    }
    @Override protected void init() {
        super.init();
        addButton(new Button(leftPos + 12, topPos + 151, 100, 20, new StringTextComponent("Use wand"), button -> action(1)));
        addButton(new Button(leftPos + 120, topPos + 151, 100, 20, new StringTextComponent("Save bounds"), button -> action(2)));
        addButton(new Button(leftPos + 228, topPos + 151, 120, 20, new StringTextComponent("Cancel selection"), button -> action(3)));
    }
    private void action(int action) { FoundationNetwork.action(menu.containerId, menu.snapshot().getString("token"), action); }
    @Override protected void renderBg(MatrixStack stack, float partialTicks, int x, int y) { fill(stack, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE20242B); }
    @Override protected void renderLabels(MatrixStack stack, int x, int y) {
        CompoundNBT snapshot = menu.snapshot();
        font.draw(stack, title, 12, 10, 0xFFFFFF);
        drawLine(stack, "Map: " + snapshot.getString("map"), 33);
        drawLine(stack, "Dimension: " + snapshot.getString("dimension"), 49);
        drawLine(stack, "Saved: " + snapshot.getString("bounds"), 65);
        drawLine(stack, "Point A: " + snapshot.getString("a"), 85);
        drawLine(stack, "Point B: " + snapshot.getString("b"), 101);
        font.draw(stack, "Foundation build: match gameplay is not ported.", 12, 123, 0xE8B56A);
    }
    private void drawLine(MatrixStack stack, String text, int y) { font.draw(stack, font.plainSubstrByWidth(text, imageWidth - 24), 12, y, 0xDDDDDD); }
    @Override public void render(MatrixStack stack, int x, int y, float partialTicks) { renderBackground(stack); super.render(stack, x, y, partialTicks); }
}
