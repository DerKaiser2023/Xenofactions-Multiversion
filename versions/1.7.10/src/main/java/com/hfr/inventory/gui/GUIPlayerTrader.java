package com.hfr.inventory.gui;

import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.shop.PlayerTraderActionPacket;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Previews only. Row indices refer to the exact bounded page retained by the server session. */
public final class GUIPlayerTrader extends GuiScreen {
    private NBTTagCompound data;
    private GuiTextField hotbar, quantity, lots;
    private int left, top, panelWidth;
    private boolean pending;
    private ItemStack hover;
    public GUIPlayerTrader(NBTTagCompound data) { this.data = data; }
    public String token() { return data.getString("token"); }
    public void receive(NBTTagCompound updated) { data = updated; pending = false; initGui(); }
    private boolean creating() { return data.getBoolean("creating"); }
    @Override public void initGui() {
        String bar = hotbar == null ? "1" : hotbar.getText();
        String qty = quantity == null ? "1" : quantity.getText();
        String count = lots == null ? "1" : lots.getText();
        panelWidth = Math.min(380, width - 8); left = (width - panelWidth) / 2; top = (height - 226) / 2;
        buttonList.clear();
        if (creating()) {
            hotbar = field(left + 12, top + 115, bar); quantity = field(left + 87, top + 115, qty);
            lots = field(left + 162, top + 115, count);
            add(1, 12, 140, 140, "Set sale"); add(2, panelWidth - 152, 140, 140, "Set payment");
            add(3, 12, 168, panelWidth - 24, "MAKE OFFER");
            add(4, 12, 198, panelWidth - 24, "Back to Market");
        } else {
            add(4, 8, 23, 66, "Market"); add(5, 78, 23, 76, "My offers");
            if (data.getBoolean("admin")) add(6, 158, 23, 70, "Admin");
            NBTTagList rows = data.getTagList("offers", 10);
            for (int i = 0; i < rows.tagCount(); i++) {
                NBTTagCompound row = rows.getCompoundTagAt(i); int y = 49 + i * 47;
                if (data.getInteger("view") == 0) {
                    add(100 + i * 4, panelWidth - 64, y + 9, 56, "Buy one").enabled = row.getInteger("available") > 0;
                } else if (row.getBoolean("manager")) {
                    add(101 + i * 4, panelWidth - 60, y, 52, "Cancel").enabled = row.getBoolean("active");
                    add(102 + i * 4, panelWidth - 116, y + 22, 52, "Return").enabled = row.getInteger("stock") > 0;
                    add(103 + i * 4, panelWidth - 60, y + 22, 52, "Collect").enabled = row.getInteger("payments") > 0;
                }
            }
            add(7, 8, 198, 36, "<").enabled = data.getInteger("page") > 0;
            add(8, 48, 198, 36, ">").enabled = data.getInteger("page") + 1 < data.getInteger("pages");
            add(9, panelWidth - 126, 198, 118, "Make a Trade");
        }
    }
    private GuiButton add(int id, int x, int y, int w, String text) {
        GuiButton button = new GuiButton(id, left + x, top + y, w, 20, text); buttonList.add(button); return button;
    }
    private GuiTextField field(int x, int y, String text) {
        GuiTextField field = new GuiTextField(fontRendererObj, x, y, 62, 18);
        field.setMaxStringLength(4); field.setText(text); return field;
    }
    private int number(GuiTextField field) {
        try { return Integer.parseInt(field.getText()); } catch (NumberFormatException e) { return -1; }
    }
    private void send(String action, int slot, int count) {
        if (pending && !"close".equals(action)) return;
        pending = true;
        for (Object raw : buttonList) ((GuiButton)raw).enabled = false;
        PacketDispatcher.wrapper.sendToServer(new PlayerTraderActionPacket(token(), action, data.getLong("revision"), slot, count));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id >= 100) {
            int offset = button.id - 100;
            send(new String[] {"buy", "cancel", "stock", "collect"}[offset % 4], offset / 4, 0);
        } else if (button.id == 1) send("sale", number(hotbar) - 1, number(quantity));
        else if (button.id == 2) send("payment", number(hotbar) - 1, number(quantity));
        else if (button.id == 3) send("publish", 0, number(lots));
        else if (button.id == 4) send("market", 0, 0);
        else if (button.id == 5) send("mine", 0, 0);
        else if (button.id == 6) send("admin", 0, 0);
        else if (button.id == 7) send("page", data.getInteger("page") - 1, 0);
        else if (button.id == 8) send("page", data.getInteger("page") + 1, 0);
        else if (button.id == 9) send("create", 0, 0);
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        if (creating()) { hotbar.mouseClicked(x, y, button); quantity.mouseClicked(x, y, button); lots.mouseClicked(x, y, button); }
    }
    @Override protected void keyTyped(char key, int code) {
        if (code == 1) { mc.displayGuiScreen(null); return; }
        if (creating() && (hotbar.textboxKeyTyped(key, code) || quantity.textboxKeyTyped(key, code) || lots.textboxKeyTyped(key, code))) return;
        if (code == mc.gameSettings.keyBindInventory.getKeyCode()) { mc.displayGuiScreen(null); return; }
        super.keyTyped(key, code);
    }
    private ItemStack item(NBTTagCompound tag, String key) {
        return tag.hasKey(key, 10) ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag(key)) : null;
    }
    private void icon(ItemStack stack, int x, int y, int mouseX, int mouseY) {
        if (stack == null) return;
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        RenderHelper.disableStandardItemLighting();
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) hover = stack;
    }
    private void text(String value, int x, int y, int limit, int color) {
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(value, limit), left + x, top + y, color);
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground(); drawRect(left, top, left + panelWidth, top + 226, 0xe0202020); hover = null;
        drawCenteredString(fontRendererObj, creating() ? "Make a Trade" : "Global Player Market", left + panelWidth / 2, top + 8, 0xffffff);
        if (creating()) {
            ItemStack sale = item(data, "sale"), payment = item(data, "payment");
            icon(sale, left + 12, top + 29, mouseX, mouseY); icon(payment, left + 12, top + 52, mouseX, mouseY);
            text("Sale: " + (sale == null ? "not set" : data.getInteger("saleQuantity") + " x " + sale.getDisplayName()), 35, 33, panelWidth - 47, 0xffffff);
            text("Price: " + (payment == null ? "not set" : data.getInteger("paymentQuantity") + " x " + payment.getDisplayName()), 35, 56, panelWidth - 47, 0xffffff);
            text("Set templates from your hotbar. Qty is per lot.", 12, 79, panelWidth - 24, 0xcccccc);
            text("MAKE OFFER reserves sale qty x lots from inventory.", 12, 91, panelWidth - 24, 0xcccccc);
            text("Hotbar 1-9", 12, 103, 73, 0xffffff); text("Qty", 87, 103, 65, 0xffffff); text("Lots", 162, 103, 65, 0xffffff);
            hotbar.drawTextBox(); quantity.drawTextBox(); lots.drawTextBox();
        } else {
            NBTTagList rows = data.getTagList("offers", 10);
            if (rows.tagCount() == 0) drawCenteredString(fontRendererObj,
                data.getInteger("view") == 1 ? "You have no stored offers." : "No offers are available.", left + panelWidth / 2, top + 103, 0xffffff);
            for (int i = 0; i < rows.tagCount(); i++) {
                NBTTagCompound row = rows.getCompoundTagAt(i); int y = 49 + i * 47;
                ItemStack sale = item(row, "sale"), payment = item(row, "payment");
                icon(sale, left + 8, top + y, mouseX, mouseY); icon(payment, left + 8, top + y + 21, mouseX, mouseY);
                int limit = panelWidth - (data.getInteger("view") == 0 ? 102 : 154);
                text(row.getInteger("saleQuantity") + " x " + (sale == null ? "unavailable item" : sale.getDisplayName()), 30, y, limit, 0xffffff);
                text("Price: " + row.getInteger("paymentQuantity") + " x " + (payment == null ? "unavailable item" : payment.getDisplayName()), 30, y + 11, limit, 0xffffff);
                String seller = row.getString("seller");
                if (seller.equals(row.getString("owner"))) seller = seller.substring(0, Math.min(12, seller.length())) + "...";
                text("Seller: " + seller, 30, y + 23, limit, 0xcccccc);
                text(data.getInteger("view") == 0 ? "Lots: " + row.getInteger("available")
                    : (row.getBoolean("active") ? "Listed" : "Cancelled") + " | stock " + row.getInteger("stock") + " | paid " + row.getInteger("payments"),
                    30, y + 34, limit, 0xcccccc);
                // Full UUID is available on hover, including sellers migrated without a saved name.
                if (mouseX >= left + 30 && mouseX < left + 30 + limit && mouseY >= top + y + 23 && mouseY < top + y + 33) {
                    drawHoveringText(java.util.Arrays.asList(row.getString("seller"), row.getString("owner")), mouseX, mouseY, fontRendererObj);
                }
            }
            text((data.getInteger("page") + 1) + " / " + data.getInteger("pages"), 92, 204, panelWidth - 226, 0xffffff);
        }
        super.drawScreen(mouseX, mouseY, partial);
        if (hover != null) renderToolTip(hover, mouseX, mouseY);
    }
    @Override public void onGuiClosed() { send("close", 0, 0); super.onGuiClosed(); }
    @Override public boolean doesGuiPauseGame() { return false; }
}
