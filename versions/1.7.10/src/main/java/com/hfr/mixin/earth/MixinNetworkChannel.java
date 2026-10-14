package com.hfr.mixin.earth;

import com.cardinalstar.cubicchunks.network.CCPacket;
import com.cardinalstar.cubicchunks.network.NetworkChannel;
import com.hfr.world.earth.network.XFEarthHeightSync;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkChannel.class, remap = false)
public abstract class MixinNetworkChannel {
    @Inject(method = "sendToPlayer(Lcom/cardinalstar/cubicchunks/network/CCPacket;Lnet/minecraft/entity/player/EntityPlayerMP;)V",
            at = @At("HEAD"), cancellable = true, require = 1, expect = 1, allow = 1, remap = false)
    private void xf$sendNative(CCPacket packet, EntityPlayerMP player, CallbackInfo ci) {
        if (XFEarthHeightSync.sendNative(packet, player)) ci.cancel();
    }
}
