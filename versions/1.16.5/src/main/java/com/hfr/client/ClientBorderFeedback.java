package com.hfr.client;

import com.hfr.main.Xenofactions;
import com.hfr.network.FoundationNetwork;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Presentation only: the server supplies transitions and border coordinates. */
@Mod.EventBusSubscriber(modid = Xenofactions.MODID, value = Dist.CLIENT)
public final class ClientBorderFeedback {
    private static ClientWorld world;
    private static FoundationNetwork.Border popup;
    private static long expires;
    private static boolean particles = true;
    public static void toggleParticles() { particles = !particles; }
    public static void receive(FoundationNetwork.Border packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !client.level.dimension().location().equals(packet.dimension)) return;
        if (world != client.level) { world = client.level; popup = null; }
        if (packet.popup) {
            popup = packet; expires = System.currentTimeMillis() + 5000;
            client.player.playSound(Xenofactions.BORDER_SOUND.get(), .5F, 1);
        }
        if (!particles) return;
        for (int i = 0; i < packet.segments.length; i += 5) {
            int x1 = packet.segments[i], z1 = packet.segments[i + 1], x2 = packet.segments[i + 2], z2 = packet.segments[i + 3], color = packet.segments[i + 4];
            int count = Math.max(1, Math.min(16, (int)Math.ceil(Math.max(Math.abs((long)x2 - x1), Math.abs((long)z2 - z1)) / 6D)));
            for (int n = 0; n < count; n++) {
                double along = (n + world.random.nextDouble()) / count;
                double x = x1 + ((long)x2 - x1) * along, z = z1 + ((long)z2 - z1) * along;
                BlockPos column = new BlockPos(Math.floor(x), 0, Math.floor(z - .01));
                if (!world.hasChunkAt(column)) continue;
                double y = world.getHeight(Heightmap.Type.MOTION_BLOCKING, column.getX(), column.getZ()) + .25;
                Particle spark = client.particleEngine.createParticle(ParticleTypes.FIREWORK, x, y, z, 0, .4 * world.random.nextGaussian() + .6, 0);
                if (spark != null) spark.setColor(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F);
            }
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientWorld current = Minecraft.getInstance().level;
        if (current != world) { world = current; popup = null; }
    }
    @SubscribeEvent public static void render(RenderGameOverlayEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || popup == null || client.player == null || client.options.hideGui) return;
        long remaining = expires - System.currentTimeMillis();
        if (remaining <= 0) { popup = null; return; }
        int alpha = (int)Math.min(255, remaining * 255 / 1000);
        if (alpha <= 10) return;
        MatrixStack matrices = event.getMatrixStack(); int center = client.getWindow().getGuiScaledWidth() / 2;
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableDepthTest();
        matrices.pushPose(); matrices.translate(center - 30, 10, 0); matrices.scale(7.5F, 20F / 3, 1);
        if (popup.pattern >= 0) {
            texture(client, matrices, NativeFlagRenderer.flagTexture(popup.pattern, false), popup.color, alpha);
            texture(client, matrices, NativeFlagRenderer.flagTexture(popup.pattern, true), 0xFFFFFF, alpha);
        } else {
            String special = popup.pattern == -2 ? "safezone" : popup.pattern == -3 ? "warzone" : "wilderness";
            texture(client, matrices, new ResourceLocation("hfr", "textures/flags/special_" + special + ".png"), 0xFFFFFF, alpha);
        }
        matrices.popPose(); RenderSystem.color4f(1, 1, 1, 1);
        String title = popup.title.replace('_', ' '), name = popup.name.replace('_', ' ');
        client.font.draw(matrices, title, center - client.font.width(title) / 2F, 60, popup.color | alpha << 24);
        client.font.draw(matrices, name, center - client.font.width(name) / 2F, 70, popup.color | alpha << 24);
        RenderSystem.enableDepthTest(); RenderSystem.disableBlend();
    }
    private static void texture(Minecraft client, MatrixStack matrices, ResourceLocation texture, int color, int alpha) {
        client.getTextureManager().bind(texture);
        RenderSystem.color4f(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, alpha / 255F);
        AbstractGui.blit(matrices, 0, 0, 0F, 0F, 8, 6, 8, 6);
    }
    private ClientBorderFeedback() { }
}
