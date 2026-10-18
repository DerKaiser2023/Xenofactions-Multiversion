package com.hfr.mixin;

import com.hfr.rewards.ChatRewardService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.C01PacketChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetHandlerPlayServer.class, priority = 2000)
public abstract class MixinChatRewards {
    @Shadow public EntityPlayerMP playerEntity;

    @Inject(method = "processChatMessage(Lnet/minecraft/network/play/client/C01PacketChatMessage;)V",
            at = @At("HEAD"), cancellable = true, require = 1, expect = 1, allow = 1)
    private void xf$rewardPhrase(C01PacketChatMessage packet, CallbackInfo ci) {
        com.hfr.main.PlayerActivityTracker.signal(playerEntity);
        if (ChatRewardService.intercept(playerEntity, packet.func_149439_c())) ci.cancel();
    }

    @Inject(method = {
            "processCreativeInventoryAction(Lnet/minecraft/network/play/client/C10PacketCreativeInventoryAction;)V",
            "processPlayerAbilities(Lnet/minecraft/network/play/client/C13PacketPlayerAbilities;)V"
        }, at = @At("HEAD"), require = 2, expect = 2, allow = 2)
    private void xf$additionalActivity(CallbackInfo ci) {
        com.hfr.main.PlayerActivityTracker.signal(playerEntity);
    }
}
