package com.hfr.main;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.hfr.config.PlayerPresenceConfig;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

/** Input callbacks only enqueue connection identities. All timers/state/chat belong to the server tick. */
public final class PlayerActivityTracker {
    private static final Map<UUID, State> states = new HashMap<UUID, State>();
    private static final Map<UUID, NetHandlerPlayServer> pending = new ConcurrentHashMap<UUID, NetHandlerPlayServer>();

    private static final class State {
        long lastActivity = System.nanoTime();
        long vanillaActivity;
        EntityPlayerMP player;
        boolean afk;
        State(EntityPlayerMP player) { this.player = player; vanillaActivity = player.func_154331_x(); }
    }

    public static void signal(EntityPlayerMP player) {
        if (player != null && player.playerNetServerHandler != null)
            pending.put(player.getUniqueID(), player.playerNetServerHandler);
    }

    public static void clear() { states.clear(); pending.clear(); }

    @SubscribeEvent public void login(PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            pending.remove(event.player.getUniqueID());
            states.put(event.player.getUniqueID(), new State((EntityPlayerMP) event.player));
        }
    }

    @SubscribeEvent public void logout(PlayerLoggedOutEvent event) {
        states.remove(event.player.getUniqueID());
        pending.remove(event.player.getUniqueID()); // Disconnect is never a return announcement.
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!PlayerPresenceConfig.enableAfkDetection) { clear(); return; }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;
        long now = System.nanoTime();
        for (Object entry : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) entry;
            UUID id = player.getUniqueID();
            State state = states.get(id);
            if (state == null) { state = new State(player); states.put(id, state); }
            NetHandlerPlayServer input = pending.remove(id);
            long vanilla = player.func_154331_x();
            if (state.player != player) { state.player = player; state.vanillaActivity = vanilla; }
            // Vanilla updates this stamp for chat/commands, digging, use, attacks, hotbar,
            // inventory clicks, signs, sneak/sprint, etc.; never for physics or keepalives.
            boolean active = input == player.playerNetServerHandler || vanilla != state.vanillaActivity;
            state.vanillaActivity = vanilla;
            if (active) {
                state.lastActivity = now;
                if (state.afk) transition(server, player, state, false);
            } else if (!state.afk && now - state.lastActivity >= PlayerPresenceConfig.afkTimeoutSeconds * 1000000000L) {
                transition(server, player, state, true);
            }
        }
        // Drop late packets from disconnected sessions without retaining their player/world.
        for (Map.Entry<UUID, NetHandlerPlayServer> input : pending.entrySet())
            if (!states.containsKey(input.getKey())) pending.remove(input.getKey(), input.getValue());
    }

    private static void transition(MinecraftServer server, EntityPlayerMP player, State state, boolean afk) {
        state.afk = afk;
        if (PlayerPresenceConfig.enableAfkAnnouncements)
            server.getConfigurationManager().sendChatMsg(new ChatComponentText(player.getCommandSenderName()
                + (afk ? " is now afk" : " is no longer afk")));
    }
}
