package com.hfr.inventory.gui;

import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.daily.DailyActionPacket;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Arrays;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.lwjgl.opengl.GL11;

/** Cosmetic clock only; the server has already saved the claim before spin=true arrives. */
@SideOnly(Side.CLIENT)
public final class GUIDailyReward extends GuiScreen {
    private static final double TAU = Math.PI * 2;
    private static final long SPIN_NANOS = 4200000000L;
    private final RenderItem itemRenderer = new RenderItem();
    private NBTTagCompound data, hover;
    private int left, top, panelWidth;
    private boolean waiting;
    private long animationStart;
    private double startAngle, endAngle;
    private GuiButton spin, collect;

    public GUIDailyReward(NBTTagCompound data) { this.data = data; settle(); }
    public String token() { return data.getString("token"); }
    private int segments() { return data.getTagList("wheel", 10).tagCount(); }
    private void settle() {
        int selected = data.getInteger("selected");
        endAngle = segments() > 0 && selected >= 0 ? -(selected + .5) * TAU / segments() : 0;
        startAngle = endAngle; animationStart = 0;
    }
    public void receive(NBTTagCompound next) {
        if (next.getLong("revision") <= data.getLong("revision")) return;
        double previous = angle(); String claim = data.getString("claim");
        if (animating() && next.getString("claim").equals(claim)) {
            next.setTag("wheel", data.getTag("wheel").copy());
            next.setInteger("selected", data.getInteger("selected"));
        }
        data = next; waiting = false;
        if (next.getBoolean("spin") && !next.getString("claim").equals(claim) && segments() > 0) {
            startAngle = previous % TAU;
            double stop = -(next.getInteger("selected") + .5) * TAU / segments();
            endAngle = startAngle + 5 * TAU + ((stop - startAngle) % TAU + TAU) % TAU;
            animationStart = System.nanoTime();
        } else if (!animating()) settle();
        initGui();
    }
    private boolean animating() { return animationStart != 0 && System.nanoTime() - animationStart < SPIN_NANOS; }
    private double angle() {
        if (animationStart == 0) return endAngle;
        double elapsed = Math.min(1, (double)(System.nanoTime() - animationStart) / SPIN_NANOS);
        return startAngle + (endAngle - startAngle) * (1 - Math.pow(1 - elapsed, 3));
    }
    @Override public void initGui() {
        panelWidth = Math.min(420, width - 8); left = (width - panelWidth) / 2; top = (height - 232) / 2;
        buttonList.clear();
        spin = new GuiButton(1, left + 6, top + 206, (panelWidth - 18) / 2, 20, I18n.format("gui.hfr.daily.spin"));
        collect = new GuiButton(2, left + 12 + (panelWidth - 18) / 2, top + 206, (panelWidth - 18) / 2, 20, I18n.format("gui.hfr.daily.collect"));
        buttonList.add(spin); buttonList.add(collect); buttons();
    }
    private void buttons() {
        spin.enabled = !waiting && !animating() && data.getBoolean("eligible") && data.getInteger("pending") == 0;
        collect.enabled = !waiting && !animating() && data.getInteger("pending") > 0;
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (!button.enabled || waiting) return;
        waiting = true; buttons();
        PacketDispatcher.wrapper.sendToServer(new DailyActionPacket(token(), data.getLong("revision"), button.id));
    }
    @Override public void onGuiClosed() { PacketDispatcher.wrapper.sendToServer(new DailyActionPacket(token(), data.getLong("revision"), 0)); }
    @Override public boolean doesGuiPauseGame() { return false; }
    private ItemStack item(NBTTagCompound preview) {
        String registry = preview.getString("registry");
        if (!Item.itemRegistry.containsKey(registry)) return null;
        NBTTagCompound tag = (NBTTagCompound)preview.getCompoundTag("item").copy();
        tag.setShort("id", (short)Item.getIdFromItem((Item)Item.itemRegistry.getObject(registry)));
        return ItemStack.loadItemStackFromNBT(tag);
    }
    private String reward(NBTTagCompound preview) {
        ItemStack stack = item(preview);
        return I18n.format("gui.hfr.daily.reward", preview.getInteger("amount"), stack == null ? I18n.format("gui.hfr.daily.unknown") : stack.getDisplayName());
    }
    private void text(String text, int x, int y, int maxWidth, int color) { fontRendererObj.drawSplitString(text, x, y, maxWidth, color); }
    private void icon(NBTTagCompound preview, int x, int y, int mouseX, int mouseY) {
        ItemStack stack = item(preview);
        if (stack != null) {
            RenderHelper.enableGUIStandardItemLighting();
            itemRenderer.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
            RenderHelper.disableStandardItemLighting();
        }
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) hover = preview;
    }
    private void wheel(int mouseX, int mouseY) {
        int count = segments(); if (count == 0) return;
        int cx = left + 75, cy = top + 90; double rotation = angle(), step = TAU / count;
        NBTTagList entries = data.getTagList("wheel", 10);
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING);
        Tessellator tess = Tessellator.instance;
        for (int segment = 0; segment < count; segment++) {
            int color = java.awt.Color.HSBtoRGB((float)segment / count, .58F, .72F);
            tess.startDrawing(GL11.GL_TRIANGLE_FAN); tess.setColorOpaque_I(color); tess.addVertex(cx, cy, 0);
            for (int point = 0; point <= 16; point++) {
                double a = rotation + (segment + point / 16D) * step - Math.PI / 2;
                tess.addVertex(cx + Math.cos(a) * 62, cy + Math.sin(a) * 62, 0);
            }
            tess.draw();
        }
        tess.startDrawing(GL11.GL_TRIANGLES); tess.setColorOpaque_I(0xFFF2AB);
        tess.addVertex(cx - 5, cy - 69, 1); tess.addVertex(cx + 5, cy - 69, 1); tess.addVertex(cx, cy - 56, 1); tess.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1, 1, 1, 1);
        // Each segment is an entry, with weighted selection on the server. Segment area is cosmetic.
        for (int segment = 0; segment < count; segment++) {
            double a = rotation + (segment + .5) * step - Math.PI / 2;
            int x = cx + (int)(Math.cos(a) * 44) - 8, y = cy + (int)(Math.sin(a) * 44) - 8;
            icon(entries.getCompoundTagAt(segment), x, y, mouseX, mouseY);
        }
        GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_DEPTH_TEST);
        drawCenteredString(fontRendererObj, I18n.format("gui.hfr.daily.wheel"), cx, top + 159, 0xDDDDDD);
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground(); hover = null; buttons();
        drawRect(left, top, left + panelWidth, top + 232, 0xEE17202B);
        drawCenteredString(fontRendererObj, I18n.format("gui.hfr.daily.title"), width / 2, top + 6, 0xFFFFFF);
        wheel(mouseX, mouseY);
        int x = left + 146, w = panelWidth - 152;
        String status = data.getBoolean("unavailable") ? "unavailable" : data.getBoolean("empty") ? "empty" : data.getBoolean("disabled") ? "disabled"
            : waiting ? "waiting" : animating() ? "spinning" : data.getBoolean("eligible") ? "eligible" : "claimed";
        text(I18n.format("gui.hfr.daily." + status), x, top + 24, w, 0xFFD780);
        text(I18n.format("gui.hfr.daily.streak", data.getInteger("streak"), data.getInteger("nextStreak")), x, top + 48, w, 0xFFFFFF);
        String next = data.hasKey("milestoneReward") ? I18n.format("gui.hfr.daily.milestone", data.getInteger("milestone"), reward(data.getCompoundTag("milestoneReward"))) : I18n.format("gui.hfr.daily.no_milestone");
        text(next, x, top + 70, w, 0xB6D8FF);
        if (!animating() && data.hasKey("result")) {
            text(I18n.format("gui.hfr.daily.result", reward(data.getCompoundTag("result"))), x, top + 101, w, 0xA0F0A0);
            if (data.hasKey("bonus")) text(I18n.format("gui.hfr.daily.bonus", reward(data.getCompoundTag("bonus"))), x, top + 123, w, 0xA0F0A0);
        }
        text(I18n.format("gui.hfr.daily.pending", data.getInteger("pending")), x, top + 147, w, 0xDDDDDD);
        text(I18n.format("gui.hfr.daily.reset", data.getString("reset")), left + 6, top + 174, panelWidth - 12, 0xDDDDDD);
        text(I18n.format("gui.hfr.daily.rules"), left + 6, top + 187, panelWidth - 12, 0xAAAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (hover != null) func_146283_a(Arrays.asList(reward(hover)), mouseX, mouseY);
    }
}
