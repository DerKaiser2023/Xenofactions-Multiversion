package com.hfr.client.journeymap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hfr.clowder.ClaimOverlayData;
import com.hfr.clowder.ClaimOverlayData.Claim;
import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.effect.ClaimOverlayPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/** Client-thread-owned, copy-on-write claim snapshots. */
public final class ClientClaimOverlayCache {
	private static volatile Map<Integer, Snapshot> snapshots = Collections.emptyMap();
	private static Assembly assembly;
	private static World world;
	private static long nextSubscription, subscription;
	private static int newestGeneration, retryTicks;
	private static boolean hasGeneration;
	private static volatile boolean resetRequested;
	private static String notice = "";
	private ClientClaimOverlayCache() { }

	/** Always called by the common client tick, even when JourneyMap is absent or disabled. */
	public static void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		if(resetRequested) { clear(); resetRequested = false; }
		if(mc.theWorld != world) {
			clear(); world = mc.theWorld;
			if(world != null) subscription = ++nextSubscription;
		}
		if(world == null || mc.thePlayer == null || mc.thePlayer.dimension != world.provider.dimensionId) return;
		if(retryTicks == 0 || (snapshots.isEmpty() || assembly != null) && retryTicks >= 200 && notice.isEmpty()) {
			PacketDispatcher.wrapper.sendToServer(new ClaimOverlayPacket.Request(subscription, world.provider.dimensionId));
			retryTicks = 1;
		} else if(retryTicks < 200) retryTicks++;
	}
	public static void accept(long token, int dimension, int generation, int part, int parts, List<Claim> claims) {
		if(!isCurrentSubscription(token, dimension)) return;
		if(hasGeneration && generation != newestGeneration && generation - newestGeneration <= 0) return;
		if(!hasGeneration || generation != newestGeneration) {
			if(part != 0) return;
			newestGeneration = generation; hasGeneration = true; assembly = null; notice = "";
			if(parts == 0) { fail("Claim snapshot exceeds the supported memory bound; previous complete claims retained"); return; }
			if(parts > ClaimOverlayPacket.MAX_PARTS) return;
			assembly = new Assembly(parts);
			retryTicks = 1;
		}
		if(assembly == null || part != assembly.nextPart || parts != assembly.parts || claims.size() > ClaimOverlayPacket.MAX_CLAIMS) return;
		assembly.claims.addAll(claims); assembly.nextPart++;
		if(assembly.nextPart == parts) {
			HashMap<Long, Claim> lookup = new HashMap<Long, Claim>();
			HashMap<String, GroupBuilder> groups = new HashMap<String, GroupBuilder>();
			for(Claim claim : assembly.claims) {
				if(claim.dimensionId != dimension || lookup.put(Long.valueOf(ClaimOverlayData.chunkKey(claim.chunkX, claim.chunkZ)), claim) != null) {
					fail("Invalid claim snapshot; previous complete claims retained"); return;
				}
				GroupBuilder group = groups.get(claim.groupId);
				if(group == null) { group = new GroupBuilder(claim.groupId, claim.label, claim.labelX, claim.labelZ); groups.put(claim.groupId, group); }
				group.include(claim.chunkX, claim.chunkZ);
			}
			HashMap<Integer, Snapshot> copy = new HashMap<Integer, Snapshot>(snapshots);
			copy.clear(); // The server sends only the player's authoritative current dimension.
			copy.put(Integer.valueOf(dimension), new Snapshot(assembly.claims, lookup, groups)); snapshots = Collections.unmodifiableMap(copy);
			assembly = null;
		}
	}
	public static boolean isCurrentSubscription(long token, int dimension) {
		return !resetRequested && token != 0 && token == subscription && world != null
				&& world == Minecraft.getMinecraft().theWorld && dimension == world.provider.dimensionId;
	}
	private static void fail(String text) {
		assembly = null; notice = text;
		com.hfr.main.MainRegistry.logger.warn(text);
		if(Minecraft.getMinecraft().thePlayer != null) Minecraft.getMinecraft().thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(text));
	}
	public static Snapshot get(int dimension) { return !resetRequested && world == Minecraft.getMinecraft().theWorld ? snapshots.get(Integer.valueOf(dimension)) : null; }
	public static String notice() { return notice; }
	public static void requestClear() { resetRequested = true; }
	public static void clear() { snapshots = Collections.emptyMap(); assembly = null; world = null; subscription = 0; hasGeneration = false; retryTicks = 0; notice = ""; }

	public static final class Snapshot {
		public final List<Claim> claims; public final Map<Long, Claim> byChunk; public final List<TerritoryGroup> groups;
		private Snapshot(List<Claim> claims, Map<Long, Claim> byChunk, Map<String, GroupBuilder> groups) {
			this.claims = Collections.unmodifiableList(new ArrayList<Claim>(claims));
			this.byChunk = Collections.unmodifiableMap(byChunk);
			ArrayList<TerritoryGroup> built = new ArrayList<TerritoryGroup>();
			for(GroupBuilder group : groups.values()) built.add(group.build());
			this.groups = Collections.unmodifiableList(built);
		}
		public boolean sameGroup(int x, int z, String group) {
			Claim neighbor = byChunk.get(Long.valueOf(ClaimOverlayData.chunkKey(x, z)));
			return neighbor != null && group.equals(neighbor.groupId);
		}
	}
	public static final class TerritoryGroup {
		public final String groupId, label; public final int labelX, labelZ, minChunkX, minChunkZ, maxChunkX, maxChunkZ;
		private TerritoryGroup(String groupId, String label, int labelX, int labelZ, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
			this.groupId = groupId; this.label = label; this.labelX = labelX; this.labelZ = labelZ;
			this.minChunkX = minChunkX; this.minChunkZ = minChunkZ; this.maxChunkX = maxChunkX; this.maxChunkZ = maxChunkZ;
		}
	}
	private static final class GroupBuilder {
		final String groupId, label; final int labelX, labelZ; int minChunkX = Integer.MAX_VALUE, minChunkZ = Integer.MAX_VALUE, maxChunkX = Integer.MIN_VALUE, maxChunkZ = Integer.MIN_VALUE;
		GroupBuilder(String groupId, String label, int labelX, int labelZ) { this.groupId = groupId; this.label = label; this.labelX = labelX; this.labelZ = labelZ; }
		void include(int x, int z) { if(x < minChunkX) minChunkX = x; if(z < minChunkZ) minChunkZ = z; if(x > maxChunkX) maxChunkX = x; if(z > maxChunkZ) maxChunkZ = z; }
		TerritoryGroup build() { return new TerritoryGroup(groupId, label, labelX, labelZ, minChunkX, minChunkZ, maxChunkX, maxChunkZ); }
	}
	private static final class Assembly {
		final int parts; int nextPart; final ArrayList<Claim> claims = new ArrayList<Claim>();
		Assembly(int parts) { this.parts = parts; }
	}
}
