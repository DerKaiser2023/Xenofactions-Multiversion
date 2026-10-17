package com.hfr.client.render;

import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

/** Bounded TextureManager registry ownership. All calls, including clear, are client-thread calls. */
public final class TextureSlots {
    private final ResourceLocation[] locations;
    private final DynamicTexture[] textures;
    public TextureSlots(String name, int count) {
        locations = new ResourceLocation[count]; textures = new DynamicTexture[count];
        for(int i=0;i<count;i++) locations[i] = new ResourceLocation("hfr", "client_cache/" + name + "/" + i);
    }
    public int free() { for(int i=0;i<textures.length;i++) if(textures[i]==null) return i; return -1; }
    public ResourceLocation location(int slot) { return locations[slot]; }
    public void upload(int slot, BufferedImage image) {
        release(slot);
        DynamicTexture texture = new DynamicTexture(image);
        try {
            if(!Minecraft.getMinecraft().getTextureManager().loadTexture(locations[slot], texture))
                throw new IllegalStateException("Could not register texture slot");
            textures[slot] = texture;
        } catch(RuntimeException failure) { texture.deleteGlTexture(); Minecraft.getMinecraft().getTextureManager().loadTexture(locations[slot], TextureUtil.missingTexture); throw failure; }
    }
    public void release(int slot) {
        DynamicTexture texture = textures[slot];
        if(texture == null) return;
        textures[slot] = null;
        texture.deleteGlTexture();
        // Vanilla 1.7.10 deleteTexture does not remove the object or its int[].
        Minecraft.getMinecraft().getTextureManager().loadTexture(locations[slot], TextureUtil.missingTexture);
    }
    public void clear() { for(int i=0;i<textures.length;i++) release(i); }
}
