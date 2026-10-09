package com.hfr.server;

import com.hfr.config.FoundationConfig;
import com.hfr.data.PlayerTimers;
import com.hfr.main.Xenofactions;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;

/** Persistent timers belong to one save; protection authorization is server-session state. */
public final class PlayerLifecycle {
    private static MinecraftServer owner;
    private static PlayerTimers timers;
    private static boolean enabled;
    public static PlayerTimers timers(MinecraftServer server) {
        if (server != owner || timers == null) throw new IllegalStateException("Player timer data is unavailable; no faction mutation was authorized.");
        return timers;
    }
    @SubscribeEvent public void start(FMLServerStartedEvent event) {
        owner = event.getServer(); timers = null; enabled = false;
        timers = new PlayerTimers(owner);
        Xenofactions.LOGGER.info("Native Xenofactions faction identity mode: {}", FactionService.usesNames(owner) ? "NAME" : "UUID");
    }
    @SubscribeEvent public void stop(FMLServerStoppedEvent event) { owner = null; timers = null; enabled = false; }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity)event.getPlayer();
        try {
            FactionService.get(player.getServer()).login(player);
            timers(player.getServer()).login(player, enabled && FoundationConfig.PLAYER_PROTECTION.get());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            Xenofactions.LOGGER.error("Could not resolve Xenofactions player lifecycle for {}", player.getUUID(), invalid);
            player.sendMessage(new StringTextComponent("Xenofactions could not resolve your saved state: " + invalid.getMessage()), Util.NIL_UUID);
        }
    }
    @SubscribeEvent public void attack(LivingAttackEvent event) {
        if (!enabled || !FoundationConfig.PLAYER_PROTECTION.get() || !(event.getEntityLiving() instanceof ServerPlayerEntity)
                || !(event.getSource().getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity attacker = (ServerPlayerEntity)event.getSource().getEntity(), victim = (ServerPlayerEntity)event.getEntityLiving();
        long now = System.currentTimeMillis(); PlayerTimers data = timers(victim.getServer());
        if (data.grace(attacker.getUUID()) > now || data.grace(victim.getUUID()) > now) {
            event.setCanceled(true); attacker.sendMessage(new StringTextComponent("PvP is blocked while either player has new-player grace."), Util.NIL_UUID);
        }
    }
    public static String toggle(MinecraftServer server) {
        timers(server);
        if (!FoundationConfig.PLAYER_PROTECTION.get()) throw new IllegalArgumentException("New-player protection is disabled in the config.");
        enabled = !enabled; return "New-player protection is now " + (enabled ? "enabled" : "disabled") + ".";
    }
}
