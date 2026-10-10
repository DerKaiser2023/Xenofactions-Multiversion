package com.hfr.client;

import com.hfr.tile.FlagPresentation;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

/** Native retained pole model plus the reference's tinted flag and untinted pattern overlay. */
public final class NativeFlagRenderer<T extends TileEntity & FlagPresentation> extends TileEntityRenderer<T> {
    private static final String[] FLAGS = {"tri", "tri", "vtri", "bcross", "wcross", "julia", "ussr", "asterisk", "pc"};
    public NativeFlagRenderer(TileEntityRendererDispatcher dispatcher) { super(dispatcher); }
    @Override public void render(T tile, float partial, MatrixStack matrices, IRenderTypeBuffer buffers, int light, int overlay) {
        pole(matrices, buffers, light);
        int pattern = Math.max(0, Math.min(FLAGS.length - 1, tile.flagPattern())); float y = .3F + tile.flagHeight() * 3;
        draw(matrices, buffers, "flag_" + FLAGS[pattern], y, .5F, tile.flagColor(), light);
        draw(matrices, buffers, "flag_" + FLAGS[pattern] + "_overlay", y, .501F, 0xFFFFFF, light);
    }
    private static void pole(MatrixStack stack, IRenderTypeBuffer buffers, int light) {
        IVertexBuilder v = buffers.getBuffer(RenderType.entityCutout(new ResourceLocation("minecraft", "textures/block/iron_block.png")));
        float a = .46F, b = .54F, top = 4.2F;
        vertex(v, stack, a, .25F, a, 0, 1, 0xFFFFFF, light); vertex(v, stack, b, .25F, a, 1, 1, 0xFFFFFF, light); vertex(v, stack, b, top, a, 1, 0, 0xFFFFFF, light); vertex(v, stack, a, top, a, 0, 0, 0xFFFFFF, light);
        vertex(v, stack, b, .25F, b, 0, 1, 0xFFFFFF, light); vertex(v, stack, a, .25F, b, 1, 1, 0xFFFFFF, light); vertex(v, stack, a, top, b, 1, 0, 0xFFFFFF, light); vertex(v, stack, b, top, b, 0, 0, 0xFFFFFF, light);
        vertex(v, stack, a, .25F, b, 0, 1, 0xFFFFFF, light); vertex(v, stack, a, .25F, a, 1, 1, 0xFFFFFF, light); vertex(v, stack, a, top, a, 1, 0, 0xFFFFFF, light); vertex(v, stack, a, top, b, 0, 0, 0xFFFFFF, light);
        vertex(v, stack, b, .25F, a, 0, 1, 0xFFFFFF, light); vertex(v, stack, b, .25F, b, 1, 1, 0xFFFFFF, light); vertex(v, stack, b, top, b, 1, 0, 0xFFFFFF, light); vertex(v, stack, b, top, a, 0, 0, 0xFFFFFF, light);
    }
    private void draw(MatrixStack stack, IRenderTypeBuffer buffers, String texture, float y, float z, int color, int light) {
        IVertexBuilder vertices = buffers.getBuffer(RenderType.entityTranslucent(new ResourceLocation("hfr", "textures/flags/" + texture + ".png")));
        vertex(vertices, stack, .5F, y, z, 0, 1, color, light); vertex(vertices, stack, 1.8F, y, z, 1, 1, color, light);
        vertex(vertices, stack, 1.8F, y + .8F, z, 1, 0, color, light); vertex(vertices, stack, .5F, y + .8F, z, 0, 0, color, light);
    }
    private static void vertex(IVertexBuilder vertices, MatrixStack stack, float x, float y, float z, float u, float v, int color, int light) {
        vertices.vertex(stack.last().pose(), x, y, z).color((color >> 16) & 255, (color >> 8) & 255, color & 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(stack.last().normal(), 0, 0, 1).endVertex();
    }
    @Override public boolean shouldRenderOffScreen(T tile) { return true; }
}
