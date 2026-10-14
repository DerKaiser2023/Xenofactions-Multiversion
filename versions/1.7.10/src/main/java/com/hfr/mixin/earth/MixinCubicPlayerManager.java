package com.hfr.mixin.earth;

import com.cardinalstar.cubicchunks.server.CubicPlayerManager;
import com.hfr.world.earth.network.XFEarthHeightSync;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// These overrides inherit Minecraft's PlayerManager mappings, including in the production JAR.
@Mixin(CubicPlayerManager.class)
public abstract class MixinCubicPlayerManager {
    @Inject(method = "addPlayer(Lnet/minecraft/entity/player/EntityPlayerMP;)V",
            at = @At("HEAD"), cancellable = true, require = 1, expect = 1, allow = 1)
    private void xf$deferSubscription(EntityPlayerMP player, CallbackInfo ci) {
        if (XFEarthHeightSync.deferSubscription(this, player)) ci.cancel();
    }

    // The pinned implementation has two returns, including its missing-player early return.
    @Inject(method = "removePlayer(Lnet/minecraft/entity/player/EntityPlayerMP;)V",
            at = @At("RETURN"), require = 2, expect = 2, allow = 2)
    private void xf$removed(EntityPlayerMP player, CallbackInfo ci) {
        XFEarthHeightSync.removed(this, player);
    }
}
