package com.hfr.client.render;

import com.hfr.main.MainRegistry;

/** Opt-in aggregate diagnostics: -Dhfr.renderMetrics=true. No per-frame logging. */
public final class RenderMetrics {
    public static final boolean ENABLED = Boolean.getBoolean("hfr.renderMetrics");
    public static long draws, uploads, deletes, reloads, tesrs, mills, structures, claimsVisible, claimsTotal,
        claimBuildNanos, particles, textureBinds;
    private static long next;
    private static final long[] TYPES=new long[16];
    public static void tesr(int type){if(ENABLED){tesrs++;TYPES[type]++;}}
    public static void bind(net.minecraft.util.ResourceLocation location) {
        if(ENABLED)textureBinds++;
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(location);
    }
    private RenderMetrics() { }
    public static void tick(int flags, int art, int pending) {
        if(!ENABLED || System.nanoTime() < next) return;
        next = System.nanoTime() + 10000000000L;
        MainRegistry.logger.info("[XF rendering/10s] TESRs=" + tesrs + " byType=" + java.util.Arrays.toString(TYPES) + " retainedDraws=" + draws
            + " uploads/deletes=" + uploads + "/" + deletes + " reloads=" + reloads
            + " mills/structureChecks=" + mills + "/" + structures + " claims selected/total="
            + claimsVisible + "/" + claimsTotal + " preparationNs=" + claimBuildNanos
            + " particles=" + particles + " binds=" + textureBinds + " live flags/art=" + flags + "/" + art
            + " slots flags/art=256/64 pendingArt=" + pending);
        draws=uploads=deletes=reloads=tesrs=mills=structures=claimsVisible=claimsTotal=claimBuildNanos=particles=textureBinds=0;
        java.util.Arrays.fill(TYPES,0);
    }
}
