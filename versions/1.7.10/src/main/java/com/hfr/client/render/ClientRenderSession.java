package com.hfr.client.render;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.world.World;

/** Network callbacks invalidate immediately; all world/GL changes are deferred to the client thread. */
public final class ClientRenderSession {
    private static final AtomicLong GENERATION=new AtomicLong();
    private ClientRenderSession() { }
    public static long generation() { return GENERATION.get(); }
    public static void invalidate() { GENERATION.incrementAndGet(); }
    public static void enqueue(final INetHandler handler, final int dimension, final Runnable action) {
        final Minecraft mc=Minecraft.getMinecraft();final World world=mc.theWorld;final long epoch=generation();
        if(world==null||mc.getNetHandler()!=handler||world.provider.dimensionId!=dimension)return;
        mc.func_152344_a(() -> {
            if(epoch==generation()&&mc.theWorld==world&&mc.getNetHandler()==handler)action.run();
        });
    }
}
