package com.hfr.client;

import com.hfr.inventory.PlayerTraderContainer;
import com.hfr.network.FoundationNetwork;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

/** Previews only; inventory storage and purchases are resolved by the server. */
public final class PlayerTraderScreen extends ContainerScreen<PlayerTraderContainer> {
    private TextFieldWidget hotbar, quantity, storage;
    private boolean managing;
    private ItemStack hover = ItemStack.EMPTY;
    public PlayerTraderScreen(PlayerTraderContainer menu, PlayerInventory inventory, ITextComponent title) { super(menu, inventory, title); imageWidth = 320; imageHeight = 280; }
    @Override protected void init() {
        super.init(); hotbar = quantity = storage = null; managing = menu.snapshot.getBoolean("manager");
        addButton(new Button(leftPos + 224, topPos + 56, 88, 20, new StringTextComponent("Buy one"), b -> send("buy", 0, 0)));
        if (managing) {
            hotbar = field(8, "1"); quantity = field(55, "1"); storage = field(102, "1");
            button(8, 222, 96, "Set sale", () -> send("sale", number(hotbar) - 1, number(quantity)));
            button(112, 222, 96, "Set payment", () -> send("payment", number(hotbar) - 1, number(quantity)));
            button(216, 222, 96, "Deposit", () -> send("deposit", number(hotbar) - 1, 0));
            button(8, 250, 149, "Take stock", () -> send("stock", number(storage) - 1, 0));
            button(163, 250, 149, "Take payments", () -> send("collect", number(storage) - 1, 0));
        }
    }
    private TextFieldWidget field(int x, String text) { TextFieldWidget f = new TextFieldWidget(font, leftPos + x, topPos + 198, 39, 18, new StringTextComponent("Quantity")); f.setMaxLength(4); f.setValue(text); addButton(f); return f; }
    private void button(int x, int y, int w, String text, Runnable action) { addButton(new Button(leftPos + x, topPos + y, w, 20, new StringTextComponent(text), b -> action.run())); }
    private int number(TextFieldWidget f) { try { return Integer.parseInt(f.getValue()); } catch (NumberFormatException invalid) { return -1; } }
    private void send(String action, int slot, int count) { FoundationNetwork.traderAction(menu.containerId, menu.token, action, menu.snapshot.getLong("revision"), slot, count); }
    @Override public void tick() { super.tick(); if (menu.snapshot.getBoolean("manager") != managing) init(); if (hotbar != null) { hotbar.tick(); quantity.tick(); storage.tick(); } }
    private ItemStack item(String key) { return menu.snapshot.contains(key, 10) ? ItemStack.of(menu.snapshot.getCompound(key)) : ItemStack.EMPTY; }
    private void icon(ItemStack stack, int x, int y, int mouseX, int mouseY) {
        if (stack.isEmpty()) return;
        itemRenderer.renderAndDecorateItem(stack, x, y); itemRenderer.renderGuiItemDecorations(font, stack, x, y);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) hover = stack;
    }
    private void row(String key, int y, int mouseX, int mouseY) {
        ListNBT list = menu.snapshot.getList(key, 10);
        for (int i = 0; i < list.size(); i++) { CompoundNBT tag = list.getCompound(i); int slot = tag.getByte("Slot") & 255; if (slot < 9) icon(ItemStack.of(tag), leftPos + 8 + slot * 32, topPos + y, mouseX, mouseY); }
    }
    @Override protected void renderBg(MatrixStack matrices, float partial, int x, int y) { fill(matrices, leftPos, topPos, leftPos + imageWidth, topPos + (managing ? 280 : 125), 0xEE20242B); }
    @Override protected void renderLabels(MatrixStack matrices, int x, int y) {
        font.draw(matrices, title, 110, 8, 0xFFFFFF);
        font.draw(matrices, "Receive " + menu.snapshot.getInt("saleQuantity"), 8, 25, 0xFFFFFF);
        font.draw(matrices, "Pay " + menu.snapshot.getInt("paymentQuantity"), 112, 25, 0xFFFFFF);
        font.draw(matrices, "Purchases available: " + menu.snapshot.getInt("available"), 8, 82, 0xFFFFFF);
        if (managing) {
            font.draw(matrices, "Sale stock (slots 1-9)", 8, 101, 0xFFFFFF);
            font.draw(matrices, "Collected payments (slots 1-9)", 8, 137, 0xFFFFFF);
            font.draw(matrices, "Hotbar       Quantity       Storage slot", 8, 185, 0xFFFFFF);
        }
    }
    @Override public void render(MatrixStack matrices, int x, int y, float partial) {
        renderBackground(matrices); super.render(matrices, x, y, partial); hover = ItemStack.EMPTY;
        icon(item("sale"), leftPos + 25, topPos + 40, x, y); icon(item("payment"), leftPos + 130, topPos + 40, x, y);
        if (managing) { row("stock", 115, x, y); row("payments", 151, x, y); }
        if (!hover.isEmpty()) renderTooltip(matrices, hover, x, y);
    }
}
