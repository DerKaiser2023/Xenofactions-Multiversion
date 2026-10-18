package com.hfr.client;

import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.client.PlayerActivityPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Inspect controls rather than position: water, gravity, vehicles and knockback are not input. */
@SideOnly(Side.CLIENT)
public final class PlayerActivityInput {
    private static NetHandlerPlayClient connection;
    private static EntityClientPlayerMP player;
    private static float mouseYaw, mousePitch;
    private static long nextSend;
    private static boolean pending;
    private PlayerActivityInput() { }

    public static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() != connection || mc.thePlayer != player) {
            connection = mc.getNetHandler(); player = mc.thePlayer;
            nextSend = 0; pending = false; mouseYaw = mousePitch = 0;
            return;
        }
        if (mc.currentScreen != null || !mc.inGameHasFocus) return;
        if (player.movementInput.moveForward != 0 || player.movementInput.moveStrafe != 0
                || player.movementInput.jump || player.movementInput.sneak) pending = true;
        long now = System.nanoTime();
        if (pending && now >= nextSend) {
            pending = false;
            nextSend = now + 1000000000L;
            PacketDispatcher.wrapper.sendToServer(new PlayerActivityPacket());
        }
    }

    public static void mouse(int dx, int dy) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null || !mc.inGameHasFocus) return;
        // Match vanilla mouse sensitivity in degrees; never infer input from server/camera rotation.
        float sensitivity = mc.gameSettings.mouseSensitivity * .6F + .2F;
        float degrees = sensitivity * sensitivity * sensitivity * 8F * .15F;
        mouseYaw += dx * degrees; mousePitch += dy * degrees;
        if (Math.abs(mouseYaw) >= 1F || Math.abs(mousePitch) >= 1F) {
            pending = true;
            mouseYaw = mousePitch = 0;
        }
    }
}
