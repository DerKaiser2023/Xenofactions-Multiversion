package com.hfr.mixin.earth;

import com.cardinalstar.cubicchunks.server.WorldSyncStateMachine;
import com.hfr.world.earth.network.XFEarthHeightSync;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = WorldSyncStateMachine.class, remap = false)
public abstract class MixinWorldSyncStateMachine {
    @Redirect(method = "flush()V", remap = false, require = 1, expect = 1, allow = 1,
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/network/NetHandlerPlayServer;sendPacket(Lnet/minecraft/network/Packet;)V"))
    private void xf$flushDescription(NetHandlerPlayServer handler, Packet packet) {
        XFEarthHeightSync.sendDescription(handler, packet);
    }

    // In v0.1.23-alpha-pre, sendCubeData(Cube)'s tile callback compiles to this exact method.
    @Redirect(method = "lambda$sendCubeData$3(Lnet/minecraft/world/ChunkPosition;Lnet/minecraft/tileentity/TileEntity;)V",
            remap = false, require = 1, expect = 1, allow = 1,
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/network/NetHandlerPlayServer;sendPacket(Lnet/minecraft/network/Packet;)V"))
    private void xf$cubeDescription(NetHandlerPlayServer handler, Packet packet) {
        XFEarthHeightSync.sendDescription(handler, packet);
    }
}
