package com.hfr.inventory.gui;

import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.shop.PlayerTraderActionPacket;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
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
    private ItemStack[] salePreviews,paymentPreviews;
    private ItemStack draftSale,draftPayment;
    public GUIPlayerTrader(NBTTagCompound data) { this.data = (NBTTagCompound)data.copy(); preparePreviews(); }
    public String token() { return data.getString("token"); }
    public void receive(NBTTagCompound updated) { data = (NBTTagCompound)updated.copy(); preparePreviews(); pending = false; initGui(); }
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
            add(1, 12, 140, 140, I18n.format("gui.hfr.playertrader.set_sale")); add(2, panelWidth - 152, 140, 140, I18n.format("gui.hfr.playertrader.set_payment"));
            add(3, 12, 168, panelWidth - 24, I18n.format("gui.hfr.playertrader.make_offer"));
            add(4, 12, 198, panelWidth - 24, I18n.format("gui.hfr.playertrader.back"));
        } else {
            add(4, 8, 23, 66, I18n.format("gui.hfr.playertrader.market")); add(5, 78, 23, 76, I18n.format("gui.hfr.playertrader.my_offers"));
            if (data.getBoolean("admin")) add(6, 158, 23, 70, I18n.format("gui.hfr.playertrader.admin"));
            NBTTagList rows = data.getTagList("offers", 10);
            for (int i = 0; i < rows.tagCount(); i++) {
                NBTTagCompound row = rows.getCompoundTagAt(i); int y = 49 + i * 47;
                if (data.getInteger("view") == 0) {
                    add(100 + i * 4, panelWidth - 64, y + 9, 56, I18n.format("gui.hfr.playertrader.buy")).enabled = row.getInteger("available") > 0;
                } else if (row.getBoolean("manager")) {
                    add(101 + i * 4, panelWidth - 60, y, 52, I18n.format("gui.hfr.playertrader.cancel")).enabled = row.getBoolean("active");
                    add(102 + i * 4, panelWidth - 116, y + 22, 52, I18n.format("gui.hfr.playertrader.return")).enabled = row.getInteger("stock") > 0;
                    add(103 + i * 4, panelWidth - 60, y + 22, 52, I18n.format("gui.hfr.playertrader.collect")).enabled = row.getInteger("payments") > 0;
                }
            }
            add(7, 8, 198, 36, "<").enabled = data.getInteger("page") > 0;
            add(8, 48, 198, 36, ">").enabled = data.getInteger("page") + 1 < data.getInteger("pages");
            add(9, panelWidth - 126, 198, 118, I18n.format("gui.hfr.playertrader.create"));
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
    private void preparePreviews() {
        draftSale=item(data,"sale");draftPayment=item(data,"payment");
        NBTTagList rows=data.getTagList("offers",10);
        salePreviews=new ItemStack[rows.tagCount()];paymentPreviews=new ItemStack[rows.tagCount()];
        for(int i=0;i<rows.tagCount();i++){NBTTagCompound row=rows.getCompoundTagAt(i);salePreviews[i]=item(row,"sale");paymentPreviews[i]=item(row,"payment");}
    }
    private ItemStack item(NBTTagCompound tag, String key) {
        return tag.hasKey(key, 10) ? ItemStack.loadItemStackFromNBT((NBTTagCompound)tag.getCompoundTag(key).copy()) : null;
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
        drawCenteredString(fontRendererObj, I18n.format(creating() ? "gui.hfr.playertrader.create" : "gui.hfr.playertrader.title"), left + panelWidth / 2, top + 8, 0xffffff);
        if (creating()) {
            ItemStack sale = draftSale, payment = draftPayment;
            icon(sale, left + 12, top + 29, mouseX, mouseY); icon(payment, left + 12, top + 52, mouseX, mouseY);
            text(I18n.format("gui.hfr.playertrader.sale", sale == null ? I18n.format("gui.hfr.playertrader.not_set")
                : I18n.format("gui.hfr.playertrader.item_quantity", data.getInteger("saleQuantity"), sale.getDisplayName())), 35, 33, panelWidth - 47, 0xffffff);
            text(I18n.format("gui.hfr.playertrader.price", payment == null ? I18n.format("gui.hfr.playertrader.not_set")
                : I18n.format("gui.hfr.playertrader.item_quantity", data.getInteger("paymentQuantity"), payment.getDisplayName())), 35, 56, panelWidth - 47, 0xffffff);
            text(I18n.format("gui.hfr.playertrader.template_help"), 12, 79, panelWidth - 24, 0xcccccc);
            text(I18n.format("gui.hfr.playertrader.reserve_help", I18n.format("gui.hfr.playertrader.make_offer")), 12, 91, panelWidth - 24, 0xcccccc);
            text(I18n.format("gui.hfr.playertrader.hotbar"), 12, 103, 73, 0xffffff); text(I18n.format("gui.hfr.playertrader.quantity"), 87, 103, 65, 0xffffff); text(I18n.format("gui.hfr.playertrader.lots"), 162, 103, 65, 0xffffff);
            hotbar.drawTextBox(); quantity.drawTextBox(); lots.drawTextBox();
        } else {
            NBTTagList rows = data.getTagList("offers", 10);
            if (rows.tagCount() == 0) drawCenteredString(fontRendererObj,
                I18n.format(data.getInteger("view") == 1 ? "gui.hfr.playertrader.empty_own" : "gui.hfr.playertrader.empty"), left + panelWidth / 2, top + 103, 0xffffff);
            for (int i = 0; i < rows.tagCount(); i++) {
                NBTTagCompound row = rows.getCompoundTagAt(i); int y = 49 + i * 47;
                ItemStack sale = salePreviews[i], payment = paymentPreviews[i];
                icon(sale, left + 8, top + y, mouseX, mouseY); icon(payment, left + 8, top + y + 21, mouseX, mouseY);
                int limit = panelWidth - (data.getInteger("view") == 0 ? 102 : 154);
                text(I18n.format("gui.hfr.playertrader.item_quantity", row.getInteger("saleQuantity"),
                    sale == null ? I18n.format("gui.hfr.playertrader.unavailable_item") : sale.getDisplayName()), 30, y, limit, 0xffffff);
                text(I18n.format("gui.hfr.playertrader.price", I18n.format("gui.hfr.playertrader.item_quantity", row.getInteger("paymentQuantity"),
                    payment == null ? I18n.format("gui.hfr.playertrader.unavailable_item") : payment.getDisplayName())), 30, y + 11, limit, 0xffffff);
                String seller = row.getString("seller");
                if (seller.equals(row.getString("owner"))) seller = seller.substring(0, Math.min(12, seller.length())) + "...";
                text(I18n.format("gui.hfr.playertrader.seller", seller), 30, y + 23, limit, 0xcccccc);
                text(data.getInteger("view") == 0 ? I18n.format("gui.hfr.playertrader.available_lots", row.getInteger("available"))
                    : I18n.format("gui.hfr.playertrader.storage", I18n.format(row.getBoolean("active") ? "gui.hfr.playertrader.listed" : "gui.hfr.playertrader.cancelled"),
                        row.getInteger("stock"), row.getInteger("payments")),
                    30, y + 34, limit, 0xcccccc);
                // Full UUID is available on hover, including sellers migrated without a saved name.
                if (mouseX >= left + 30 && mouseX < left + 30 + limit && mouseY >= top + y + 23 && mouseY < top + y + 33) {
                    drawHoveringText(java.util.Arrays.asList(row.getString("seller"), row.getString("owner")), mouseX, mouseY, fontRendererObj);
                }
            }
            text(I18n.format("gui.hfr.playertrader.page", data.getInteger("page") + 1, data.getInteger("pages")), 92, 204, panelWidth - 226, 0xffffff);
        }
        super.drawScreen(mouseX, mouseY, partial);
        if (hover != null) renderToolTip(hover, mouseX, mouseY);
    }
    @Override public void onGuiClosed() { send("close", 0, 0); super.onGuiClosed(); }
    @Override public boolean doesGuiPauseGame() { return false; }
}
