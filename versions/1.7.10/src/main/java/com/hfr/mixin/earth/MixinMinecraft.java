package com.hfr.mixin.earth;

import com.hfr.world.earth.storage.XFEarthStorageIdentity;
import java.io.File;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.world.WorldSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Shadow @Final public File mcDataDir;

    @Inject(method = "launchIntegratedServer(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V",
            at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void xf$preflightSave(String folderName, String worldName, WorldSettings settings, CallbackInfo ci)
            throws IOException {
        XFEarthStorageIdentity.preflightClient(mcDataDir, folderName);
    }
}
