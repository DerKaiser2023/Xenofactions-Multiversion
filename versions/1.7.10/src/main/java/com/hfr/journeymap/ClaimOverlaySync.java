package com.hfr.journeymap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import com.hfr.clowder.ClaimOverlayData;
import com.hfr.clowder.ClaimOverlayData.Claim;
import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.effect.ClaimOverlayPacket;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/** Debounced authoritative claim snapshots, independent of optional map integrations. */
public class ClaimOverlaySync {
	private static final Set<Integer> DIRTY = new HashSet<Integer>();
	private static boolean allDirty;
	private static int debounce, generation, ticks;
	private static final Map<UUID, Subscription> subscriptions = new HashMap<UUID, Subscription>();
	private static final class Subscription {
		final long token; final net.minecraft.world.World world; final int tick;
		Subscription(long token, net.minecraft.world.World world) { this.token = token; this.world = world; tick = ticks; }
	}
	public static synchronized void markAllDirty() { allDirty = true; if(debounce == 0) debounce = 20; }
	public static synchronized void markDirty(int dimension) { DIRTY.add(Integer.valueOf(dimension)); if(debounce == 0) debounce = 20; }
	public static synchronized void resetWorldState() { DIRTY.clear(); subscriptions.clear(); allDirty = true; debounce = 0; generation = 0; ticks = 0; }
	public static void subscribe(EntityPlayerMP player, long token, int dimension) {
		if(player.isDead || player.dimension != dimension || player.worldObj.isRemote || !player.playerNetServerHandler.netManager.isChannelOpen()) return;
		Subscription previous = subscriptions.get(player.getUniqueID());
		if(previous != null && previous.world == player.worldObj && ticks - previous.tick < 40) return;
		subscriptions.put(player.getUniqueID(), new Subscription(token, player.worldObj));
		send(player);
	}

	/** Existing per-client-world subscription, also echoed by transient border effects. Server thread only. */
	public static long visualSubscription(EntityPlayerMP player) {
		Subscription subscription = subscriptions.get(player.getUniqueID());
		return subscription != null && subscription.world == player.worldObj ? subscription.token : 0L;
	}

	@SubscribeEvent public void login(PlayerLoggedInEvent event) { if(event.player instanceof EntityPlayerMP) send((EntityPlayerMP)event.player); }
	@SubscribeEvent public void changed(PlayerChangedDimensionEvent event) { if(event.player instanceof EntityPlayerMP) send((EntityPlayerMP)event.player); }
	@SubscribeEvent public void respawn(PlayerRespawnEvent event) { if(event.player instanceof EntityPlayerMP) send((EntityPlayerMP)event.player); }
	@SubscribeEvent public void logout(PlayerLoggedOutEvent event) { subscriptions.remove(event.player.getUniqueID()); }
	@SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
		if(event.phase != TickEvent.Phase.END) return;
		ticks++;
		if(debounce <= 0 || --debounce > 0) return;
		MinecraftServer server = MinecraftServer.getServer(); if(server == null) return;
		for(Object object : server.getConfigurationManager().playerEntityList) {
			EntityPlayerMP player = (EntityPlayerMP)object;
			if(allDirty || DIRTY.contains(Integer.valueOf(player.dimension))) send(player);
		}
		DIRTY.clear(); allDirty = false;
	}
	private static void send(EntityPlayerMP player) {
		Subscription subscription = subscriptions.get(player.getUniqueID());
		if(subscription == null || subscription.world != player.worldObj) return;
		List<Claim> claims = ClaimOverlayData.snapshot(player.dimension);
		int parts = Math.max(1, (claims.size() + ClaimOverlayPacket.MAX_CLAIMS - 1) / ClaimOverlayPacket.MAX_CLAIMS);
		int id = ++generation;
		if(parts > ClaimOverlayPacket.MAX_PARTS) {
			PacketDispatcher.wrapper.sendTo(new ClaimOverlayPacket(subscription.token, player.dimension, id, 0, 0, java.util.Collections.<Claim>emptyList()), player);
			return;
		}
		for(int part = 0; part < parts; part++) {
			int from = part * ClaimOverlayPacket.MAX_CLAIMS, to = Math.min(claims.size(), from + ClaimOverlayPacket.MAX_CLAIMS);
			PacketDispatcher.wrapper.sendTo(new ClaimOverlayPacket(subscription.token, player.dimension, id, part, parts, claims.subList(from, to)), player);
		}
	}
}
