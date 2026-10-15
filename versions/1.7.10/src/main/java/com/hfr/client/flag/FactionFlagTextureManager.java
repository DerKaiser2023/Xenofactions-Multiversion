package com.hfr.client.flag;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import com.hfr.clowder.Clowder;
import com.hfr.main.MainRegistry;
import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.effect.FactionFlagMetadataPacket;
import com.hfr.render.hud.RenderFlagOverlay;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

@SideOnly(Side.CLIENT)
public class FactionFlagTextureManager {
	// All maps and GL operations belong to the client thread; workers only read/decode PNGs.
	private static final Map<String, String> HASHES = new HashMap<String, String>();
	private static final Map<String, ResourceLocation> TEXTURES = new HashMap<String, ResourceLocation>();
	private static final Map<String, Long> REQUESTS = new HashMap<String, Long>();
	private static final Set<String> CHECKED = new HashSet<String>();
	private static final Set<String> LOADING = new HashSet<String>();
	private static final ResourceLocation FALLBACK = new ResourceLocation("hfr:textures/flags/flag_tri.png");
	private static final int MAX_BYTES = 1024 * 1024;
	private static int generation;
	private static volatile boolean clearRequested;
	private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS,
		new ArrayBlockingQueue<Runnable>(32), runnable -> {
			Thread thread = new Thread(runnable, "Xenofactions flag cache");
			thread.setDaemon(true);
			return thread;
		});

	public static ResourceLocation getFlagTexture(Clowder faction) {
		return faction == null ? FALLBACK : getFlagTexture(faction.name);
	}

	public static ResourceLocation getFlagTexture(String factionName) {
		ResourceLocation texture = TEXTURES.get(HASHES.get(factionName));
		return texture == null ? FALLBACK : texture;
	}

	public static void requestFlagIfMissing(Clowder faction) {
		if(faction != null) requestFlagIfMissing(faction.name);
	}

	public static void requestFlagIfMissing(String factionName) {
		String hash = HASHES.get(factionName);
		if(hash == null || TEXTURES.containsKey(hash) || LOADING.contains(hash)) return;
		long now = System.currentTimeMillis();
		Long next = REQUESTS.get(hash);
		if(next != null && next.longValue() > now) return;
		REQUESTS.put(hash, now + 30000L);
		PacketDispatcher.wrapper.sendToServer(new FactionFlagMetadataPacket(factionName, hash));
	}

	public static void clearFlag(Clowder faction) {
		if(faction != null) clearFlag(faction.name);
	}

	public static void clearFlag(String factionName) {
		release(HASHES.remove(factionName));
		refreshOverlay();
	}

	private static void release(String hash) {
		if(hash == null || HASHES.containsValue(hash)) return;
		ResourceLocation old = TEXTURES.remove(hash);
		if(old != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(old);
		REQUESTS.remove(hash);
	}

	public static void handleMetadata(String factionName, String hash) {
		if(factionName == null || factionName.length() == 0) return;
		if(hash == null || !validHash(hash)) {
			clearFlag(factionName);
			return;
		}
		String old = HASHES.put(factionName, hash);
		if(!hash.equals(old)) release(old);
		refreshOverlay();
		if(TEXTURES.containsKey(hash) || LOADING.contains(hash)) return;
		if(CHECKED.add(hash)) load(hash, null);
		else requestFlagIfMissing(factionName);
	}

	public static void handleBytes(String factionName, String hash, byte[] png) {
		if(hash == null || !hash.equals(HASHES.get(factionName)) || png == null || png.length == 0
				|| png.length > MAX_BYTES || TEXTURES.containsKey(hash) || LOADING.contains(hash)) return;
		load(hash, png);
	}

	private static void load(final String hash, final byte[] supplied) {
		final int epoch = generation;
		LOADING.add(hash);
		try {
			WORKER.execute(() -> {
				BufferedImage decoded = null;
				try {
					File file = new File(getCacheDir(), hash + ".png");
					byte[] bytes = supplied;
					if(bytes == null && file.isFile() && file.length() <= MAX_BYTES) {
						try(java.io.InputStream input = Files.newInputStream(file.toPath())) {
							java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
							byte[] buffer = new byte[8192];
							int count;
							while((count = input.read(buffer)) != -1) {
								if(out.size() + count > MAX_BYTES) throw new java.io.IOException("cache exceeds size limit");
								out.write(buffer, 0, count);
							}
							bytes = out.toByteArray();
						}
					}
					if(bytes != null) {
						if(!hash.equals(sha256(bytes))) throw new java.io.IOException("PNG hash mismatch");
						decoded = decode(bytes);
						if(supplied != null) {
							try {
								Files.createDirectories(getCacheDir().toPath());
								Files.write(file.toPath(), bytes);
							} catch(Exception e) {
								MainRegistry.logger.warn("Could not cache faction flag " + hash + " (" + e.getClass().getSimpleName() + ")");
							}
						}
					}
				} catch(Exception e) {
					MainRegistry.logger.warn("Could not decode faction flag " + hash + " (" + e.getClass().getSimpleName() + ")");
				}
				final BufferedImage image = decoded;
				Minecraft.getMinecraft().func_152344_a(() -> {
					if(epoch != generation) { if(image != null) image.flush(); return; }
					LOADING.remove(hash);
					if(!HASHES.containsValue(hash)) { if(image != null) image.flush(); return; }
					if(image != null) {
						ResourceLocation location = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation(
							"xenofactions_flag_" + hash, new DynamicTexture(image));
						ResourceLocation old = TEXTURES.put(hash, location);
						if(old != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(old);
						refreshOverlay();
						image.flush();
					} else if(supplied == null) {
						for(Map.Entry<String, String> entry : HASHES.entrySet())
							if(hash.equals(entry.getValue())) { requestFlagIfMissing(entry.getKey()); break; }
					}
				});
			});
		} catch(java.util.concurrent.RejectedExecutionException e) {
			LOADING.remove(hash);
			MainRegistry.logger.warn("Faction flag decode queue is full; using fallback");
		}
	}

	private static BufferedImage decode(byte[] bytes) throws Exception {
		try(ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
			if(!readers.hasNext()) throw new java.io.IOException("unsupported image");
			ImageReader reader = readers.next();
			try {
				reader.setInput(stream, true, true);
				if(!"png".equalsIgnoreCase(reader.getFormatName()) || reader.getWidth(0) != 64 || reader.getHeight(0) != 64)
					throw new java.io.IOException("expected sanitized 64x64 PNG");
				return reader.read(0);
			} finally { reader.dispose(); }
		}
	}

	public static void requestClear() { clearRequested = true; }

	private static boolean validHash(String hash) {
		if(hash.length() != 64) return false;
		for(int i = 0; i < hash.length(); i++) {
			char c = hash.charAt(i);
			if(!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
		}
		return true;
	}

	private static void refreshOverlay() {
		if(RenderFlagOverlay.customFlagName.length() > 0) {
			RenderFlagOverlay.flag = getFlagTexture(RenderFlagOverlay.customFlagName);
			RenderFlagOverlay.overlay = null;
			RenderFlagOverlay.tintFlag = false;
		}
	}

	public static void tick() {
		if(clearRequested) {
			clearRequested = false;
			generation++;
			for(ResourceLocation texture : TEXTURES.values()) Minecraft.getMinecraft().getTextureManager().deleteTexture(texture);
			HASHES.clear(); TEXTURES.clear(); REQUESTS.clear(); CHECKED.clear(); LOADING.clear();
			RenderFlagOverlay.customFlagName = "";
			WORKER.getQueue().clear();
		}
	}

	private static File getCacheDir() {
		return new File(Loader.instance().getConfigDir(), "xenofactions/flag_cache");
	}

	private static String sha256(byte[] data) throws Exception {
		StringBuilder sb = new StringBuilder();
		for(byte b : MessageDigest.getInstance("SHA-256").digest(data)) sb.append(String.format("%02x", b & 0xff));
		return sb.toString();
	}
}
