package com.hfr.clowder.flag;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Iterator;
import java.util.UUID;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.net.ssl.HttpsURLConnection;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

import com.hfr.clowder.Clowder;
import com.hfr.command.CommandClowder;
import com.hfr.config.XFConfig;
import com.hfr.data.ClowderData;
import com.hfr.main.MainRegistry;
import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.effect.FactionFlagBytesPacket;
import com.hfr.packet.effect.FactionFlagMetadataPacket;

import cpw.mods.fml.common.Loader;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

public class CustomFlagService {

	public static final int FLAG_SIZE = 64;
	private static final Queue<Runnable> MAIN_THREAD_CALLBACKS = new LinkedList<Runnable>();
	private static volatile ServerSession session;

	// Session maps and flag installation are owned by the server thread. Workers only prepare images.
	public static void startServerSession(MinecraftServer server) {
		shutdown();
		synchronized(MAIN_THREAD_CALLBACKS) {
			session = new ServerSession(server);
		}
	}

	public static void shutdown() {
		ServerSession previous;
		synchronized(MAIN_THREAD_CALLBACKS) {
			previous = session;
			session = null;
			MAIN_THREAD_CALLBACKS.clear();
		}
		if(previous != null) {
			for(ImportRequest request : previous.activeImports.values())
				request.cancel();
			previous.activeImports.clear();
			previous.downloads.clear();
			previous.cooldowns.clear();
			previous.executor.shutdownNow();
			previous.dns.shutdownNow();
			previous.timeouts.shutdownNow();
		}
	}

	public static File getServerFlagDir() {
		return new File(Loader.instance().getConfigDir(), "xenofactions/flags");
	}

	public static File getFlagFile(Clowder clowder) {
		return new File(getServerFlagDir(), getFactionFileKey(clowder) + ".png");
	}

	public static String getFactionFileKey(Clowder clowder) {
		String key = clowder.uuid == null || clowder.uuid.length() == 0 ? clowder.name : clowder.uuid;
		return key.replaceAll("[^A-Za-z0-9_.-]", "_");
	}

	public static void tickMainThread() {
		while(true) {
			Runnable runnable;
			synchronized(MAIN_THREAD_CALLBACKS) {
				runnable = MAIN_THREAD_CALLBACKS.poll();
			}
			if(runnable == null)
				return;
			runnable.run();
		}
	}

	public static void importUrl(final EntityPlayerMP player, final Clowder clowder, final String urlText) {
		if(!XFConfig.enableCustomFactionFlags) {
			player.addChatMessage(new ChatComponentText(CommandClowder.ERROR + "Custom faction flags are disabled on this server."));
			return;
		}
		final ServerSession owner = session;
		if(owner == null || owner.server != MinecraftServer.getServer())
			return;
		if(clowder.uuid == null || clowder.uuid.length() == 0 || Clowder.getClowderFromUUID(clowder.uuid) != clowder
				|| Clowder.getClowderFromPlayer(player) != clowder || clowder.getPermLevel(player) <= 2) {
			player.addChatMessage(new ChatComponentText(CommandClowder.ERROR + "You lack the permissions to change this faction's flag!"));
			return;
		}
		final String key = clowder.uuid;
		long now = System.currentTimeMillis();
		Long next = owner.cooldowns.get(key);
		if(next != null && next.longValue() > now) {
			long seconds = (next.longValue() - now + 999L) / 1000L;
			player.addChatMessage(new ChatComponentText(CommandClowder.ERROR + "Please wait " + seconds + " seconds before importing another faction flag."));
			return;
		}
		if(owner.activeImports.containsKey(key)) {
			player.addChatMessage(new ChatComponentText(CommandClowder.ERROR + "A faction flag import is already in progress."));
			return;
		}
		if(urlText == null || urlText.length() > 2048) {
			player.addChatMessage(new ChatComponentText(CommandClowder.ERROR + "Invalid flag URL (maximum 2048 characters)."));
			return;
		}
		final ImportRequest request = new ImportRequest(owner, key, player.getUniqueID(), clowder);
		owner.activeImports.put(key, request);
		final List<ImportRequest> waiting = owner.downloads.get(urlText);
		if(waiting != null) {
			waiting.add(request);
			player.addChatMessage(new ChatComponentText(CommandClowder.INFO + "Waiting for the shared faction flag import."));
			return;
		}
		final List<ImportRequest> subscribers = new ArrayList<ImportRequest>();
		subscribers.add(request);
		owner.downloads.put(urlText, subscribers);
		try {
			owner.executor.submit(new Runnable() {
				@Override
				public void run() {
					ImportResult result = new ImportResult("Failed to import faction flag.", null, null);
					try {
						result = prepareImage(urlText, owner);
					} catch(FlagImportException e) {
						MainRegistry.logger.warn("Rejected faction flag for " + key + ": " + e.getMessage());
						result = new ImportResult(e.getMessage(), null, null);
					} catch(Exception e) {
						MainRegistry.logger.warn("Failed to import faction flag for " + key + " (" + e.getClass().getSimpleName() + ")");
					} finally {
						final ImportResult completed = result;
						enqueueMainThread(owner, new Runnable() {
							@Override
							public void run() {
								owner.downloads.remove(urlText);
								for(ImportRequest subscriber : subscribers) completeImport(subscriber, completed);
							}
						});
					}
				}
			});
		} catch(RuntimeException e) {
			owner.downloads.remove(urlText);
			owner.activeImports.remove(key);
			request.cancel();
			player.addChatMessage(new ChatComponentText(CommandClowder.ERROR + "Could not start faction flag import. Please retry."));
			MainRegistry.logger.warn("Could not submit faction flag import", e);
			return;
		}
		player.addChatMessage(new ChatComponentText(CommandClowder.INFO + "Importing faction flag from HTTPS. The server will validate and cache it first."));
	}

	private static void completeImport(ImportRequest request, ImportResult result) {
		ServerSession owner = request.owner;
		try {
			if(owner != session || owner.server != MinecraftServer.getServer() || request.cancelled
					|| owner.activeImports.get(request.factionId) != request)
				return;
			EntityPlayerMP player = findPlayer(owner, request.playerId);
			if(player == null)
				return;
			ClowderData data = ClowderData.getData(player.worldObj);
			Clowder clowder = Clowder.getClowderFromUUID(request.factionId);
			if(clowder != request.faction || Clowder.getClowderFromPlayer(player) != clowder || clowder.getPermLevel(player) <= 2) {
				player.addChatMessage(new ChatComponentText(CommandClowder.ERROR + "Flag import discarded: faction or flag-change authority changed."));
				return;
			}
			if(!result.success()) {
				player.addChatMessage(new ChatComponentText(CommandClowder.ERROR + result.message));
				return;
			}
			if(!XFConfig.enableCustomFactionFlags) {
				player.addChatMessage(new ChatComponentText(CommandClowder.ERROR + "Custom faction flags are disabled on this server."));
				return;
			}
			try {
				installImage(clowder, result.bytes);
			} catch(Exception e) {
				player.addChatMessage(new ChatComponentText(CommandClowder.ERROR + "Failed to save custom faction flag. The previous flag was preserved."));
				MainRegistry.logger.warn("Could not install faction flag for " + request.factionId, e);
				return;
			}
			clowder.customFlagHash = result.hash;
			data.markDirty();
			owner.cooldowns.put(request.factionId, Long.valueOf(System.currentTimeMillis() + XFConfig.customFlagRateLimitMs));
			broadcastMetadata(clowder);
			player.addChatMessage(new ChatComponentText(CommandClowder.INFO + "Custom faction flag imported."));
		} finally {
			// A cancelled/old callback must never release a newer request's slot.
			if(owner.activeImports.get(request.factionId) == request)
				owner.activeImports.remove(request.factionId);
		}
	}

	private static EntityPlayerMP findPlayer(ServerSession owner, UUID playerId) {
		for(Object value : owner.server.getConfigurationManager().playerEntityList) {
			EntityPlayerMP player = (EntityPlayerMP)value;
			if(playerId.equals(player.getUniqueID()))
				return player;
		}
		return null;
	}

	public static void cancelImport(Clowder clowder) {
		ServerSession owner = session;
		if(owner != null) {
			ImportRequest request = owner.activeImports.remove(clowder.uuid);
			if(request != null)
				request.cancel();
		}
	}

	public static void clearFlag(EntityPlayerMP player, Clowder clowder) {
		ClowderData data = ClowderData.getData(player.worldObj);
		File file = getFlagFile(clowder);
		if(file.exists() && !file.delete()) {
			MainRegistry.logger.warn("Could not delete custom faction flag " + file.getName());
			player.addChatMessage(new ChatComponentText(CommandClowder.ERROR + "Could not clear custom faction flag. The previous flag was preserved."));
			return;
		}
		cancelImport(clowder);
		clowder.customFlagHash = "";
		data.markDirty();
		broadcastMetadata(clowder);
		player.addChatMessage(new ChatComponentText(CommandClowder.INFO + "Custom faction flag cleared."));
	}

	public static void reloadFlag(EntityPlayerMP player, Clowder clowder) {
		try {
			ClowderData data = ClowderData.getData(player.worldObj);
			File file = getFlagFile(clowder);
			String hash = clowder.customFlagHash;

			if(!file.exists()) {
				if(XFConfig.customFlagReloadMissingClearsMetadata)
					hash = "";
			} else {
				hash = sha256(readFile(file));
			}

			cancelImport(clowder);
			clowder.customFlagHash = hash;
			data.markDirty();
			broadcastMetadata(clowder);

			player.addChatMessage(new ChatComponentText(CommandClowder.INFO + "Custom faction flag metadata reloaded."));

		} catch(Exception e) {
			player.addChatMessage(new ChatComponentText(CommandClowder.ERROR + "Failed to reload custom faction flag."));
			MainRegistry.logger.warn("Could not reload faction flag", e);
		}
	}

	public static void broadcastMetadata(Clowder clowder) {
		PacketDispatcher.wrapper.sendToAll(new FactionFlagMetadataPacket(clowder.name, clowder.customFlagHash));
	}

	public static void requestFlagBytes(final EntityPlayerMP player, final String factionName, final String hash) {
		final ServerSession owner = session;
		if(owner == null)
			return;
		enqueueMainThread(owner, new Runnable() {
			@Override
			public void run() {
				if(owner.server != MinecraftServer.getServer() || !owner.server.getConfigurationManager().playerEntityList.contains(player))
					return;
				Clowder clowder = Clowder.getClowderFromName(factionName);
				if(clowder != null && hash != null && hash.length() > 0 && hash.equals(clowder.customFlagHash))
					sendFlagBytes(player, clowder);
			}
		});
	}

	public static void sendFlagBytes(EntityPlayerMP player, Clowder clowder) {
		if(clowder == null || clowder.customFlagHash == null || clowder.customFlagHash.length() == 0)
			return;
		try {
			File file = getFlagFile(clowder);
			if(file.exists()) {
				byte[] bytes = readFile(file);
				if(clowder.customFlagHash.equals(sha256(bytes)))
					PacketDispatcher.wrapper.sendTo(new FactionFlagBytesPacket(clowder.name, clowder.customFlagHash, bytes), player);
			}
		} catch(Exception e) {
			MainRegistry.logger.warn("Could not send faction flag bytes for " + clowder.name + ": " + e.getMessage());
		}
	}

	private static void enqueueMainThread(final ServerSession owner, final Runnable runnable) {
		synchronized(MAIN_THREAD_CALLBACKS) {
			if(owner == session) {
				MAIN_THREAD_CALLBACKS.add(new Runnable() {
					@Override
					public void run() {
						if(owner == session)
							runnable.run();
					}
				});
			}
		}
	}

	private static ImportResult prepareImage(String urlText, ServerSession owner) throws Exception {
		DownloadBudget budget = new DownloadBudget(owner);
		byte[] downloaded = download(validateUrl(urlText, budget), budget, true);
		BufferedImage input = decodeValidatedImage(downloaded);
		BufferedImage output = resizeToFlag(input);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if(!ImageIO.write(output, "png", out))
			throw new FlagImportException("failed to encode image");
		byte[] png = out.toByteArray();
		if(png.length > XFConfig.customFlagMaxFileSizeBytes)
			throw new FlagImportException("image too large");
		String hash = sha256(png);
		return new ImportResult(null, hash, png);
	}

	private static void installImage(Clowder clowder, byte[] png) throws IOException {
		Path dir = getServerFlagDir().toPath();
		Files.createDirectories(dir);
		Path tmp = Files.createTempFile(dir, getFactionFileKey(clowder) + ".", ".tmp");
		try {
			FileOutputStream fos = new FileOutputStream(tmp.toFile());
			try {
				fos.write(png);
				fos.getFD().sync();
			} finally {
				fos.close();
			}
			// No delete/rename fallback: unsupported atomic replacement must preserve the old file.
			Files.move(tmp, getFlagFile(clowder).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			try {
				Files.deleteIfExists(tmp);
			} catch(Exception e) {
				MainRegistry.logger.warn("Could not clean faction flag staging file " + tmp, e);
			}
		}
	}

	private static URL validateUrl(String urlText, DownloadBudget budget) throws Exception {
		URI uri;
		try { uri = new URI(urlText); }
		catch(Exception e) { throw new FlagImportException("invalid HTTPS URL"); }
		if(urlText.length() > 2048 || (uri.getPort() != -1 && uri.getPort() != 443))
			throw new FlagImportException("invalid HTTPS URL");
		if(uri.getUserInfo() != null)
			throw new FlagImportException("invalid URL");
		if(uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme()))
			throw new FlagImportException("invalid URL");
		if(uri.getHost() == null)
			throw new FlagImportException("invalid URL");
		if(!isAllowedHost(uri.getHost()))
			throw new FlagImportException("unsupported image host");
		validateHost(uri.getHost(), budget);
		return uri.toURL();
	}

	private static byte[] download(URL url, DownloadBudget budget, boolean pageAllowed) throws Exception {
		budget.remaining();
		URL safeUrl = validateUrl(url.toString(), budget);
		budget.remaining();
		HttpURLConnection conn = (HttpURLConnection)safeUrl.openConnection();
		if(!(conn instanceof HttpsURLConnection))
			throw new FlagImportException("invalid URL");
		conn.setInstanceFollowRedirects(false);
		conn.setConnectTimeout(budget.remaining());
		conn.setReadTimeout(budget.remaining());
		conn.setRequestProperty("User-Agent", "Mozilla/5.0 XenofactionsFlagImporter/1.0");
		// A read timeout alone can be extended indefinitely by trickled response headers.
		ScheduledFuture<?> timeout = budget.owner.timeouts.schedule(() -> conn.disconnect(), budget.remaining(), TimeUnit.MILLISECONDS);
		try {
			int code = conn.getResponseCode();
			if(code >= 300 && code <= 399) {
				if(budget.redirects++ >= XFConfig.customFlagMaxRedirects)
					throw new FlagImportException("too many HTTPS redirects");
				String location = conn.getHeaderField("Location");
				if(location == null)
					throw new FlagImportException("invalid URL");
				URL next = new URL(safeUrl, location);
				if(!"https".equalsIgnoreCase(next.getProtocol()))
					throw new FlagImportException("invalid URL");
				return download(next, budget, pageAllowed);
			}
			if(code < 200 || code >= 300)
				throw new FlagImportException("image host " + safeUrl.getHost() + " returned HTTP " + code);
			String type = conn.getContentType();
			boolean html = type != null && (type.toLowerCase(Locale.ROOT).startsWith("text/html")
					|| type.toLowerCase(Locale.ROOT).startsWith("application/xhtml+xml"));
			int limit = html ? Math.min(512 * 1024, XFConfig.customFlagMaxFileSizeBytes) : XFConfig.customFlagMaxFileSizeBytes;
			if(conn.getContentLengthLong() > limit)
				throw new FlagImportException("image too large");
			byte[] bytes;
			try(InputStream input = conn.getInputStream()) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buffer = new byte[8192];
				int count;
				while(true) {
					conn.setReadTimeout(budget.remaining());
					count = input.read(buffer);
					if(count < 0) break;
					if(out.size() + count > limit) throw new FlagImportException("image/page too large");
					out.write(buffer, 0, count);
				}
				bytes = out.toByteArray();
			}
			budget.remaining();
			// Only PNG/JPEG signatures ever reach ImageIO, including mislabeled HTML responses.
			if(!html && isImage(bytes)) return bytes;
			String prefix = new String(bytes, 0, Math.min(bytes.length, 512), java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
			if(html || prefix.startsWith("<!doctype html") || prefix.startsWith("<html")) {
				String host = safeUrl.getHost().toLowerCase(Locale.ROOT);
				if(!pageAllowed || !(host.equals("postimg.cc") || host.equals("postimages.org")))
					throw new FlagImportException("HTML page returned instead of a PNG/JPEG image on " + host);
				if(bytes.length > 512 * 1024) throw new FlagImportException("Postimages page too large");
				URL resolved = resolvePage(safeUrl, bytes);
				conn.disconnect();
				return download(resolved, budget, false);
			}
			throw new FlagImportException("unsupported image response on " + safeUrl.getHost() + "; use PNG/JPEG or a Postimages image page");
		} catch(java.net.SocketTimeoutException e) {
			throw new FlagImportException("download timed out");
		} catch(FlagImportException e) {
			throw e;
		} catch(IOException e) {
			throw new FlagImportException("HTTPS download failed on " + safeUrl.getHost() + " (" + e.getClass().getSimpleName() + ")");
		} finally {
			timeout.cancel(false);
			conn.disconnect();
		}
	}

	private static boolean isImage(byte[] b) {
		return b.length >= 8 && ((b[0] == (byte)137 && b[1] == 80 && b[2] == 78 && b[3] == 71
				&& b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10)
				|| (b[0] == (byte)255 && b[1] == (byte)216 && b[2] == (byte)255));
	}

	private static URL resolvePage(final URL page, byte[] html) throws Exception {
		final String[] candidates = new String[3];
		new ParserDelegator().parse(new InputStreamReader(new ByteArrayInputStream(html), "UTF-8"),
			new HTMLEditorKit.ParserCallback() {
				private void inspect(HTML.Tag tag, MutableAttributeSet attributes) {
					Object property = attributes.getAttribute("property");
					if(property == null) property = attributes.getAttribute(HTML.Attribute.NAME);
					Object content = attributes.getAttribute(HTML.Attribute.CONTENT);
					String value = content == null ? null : content.toString().trim();
					if(tag == HTML.Tag.META && "og:image:secure_url".equalsIgnoreCase(String.valueOf(property))) candidates[0] = value;
					if(tag == HTML.Tag.META && "og:image".equalsIgnoreCase(String.valueOf(property))) candidates[1] = value;
					if(tag == HTML.Tag.IMG && "main-image".equals(String.valueOf(attributes.getAttribute(HTML.Attribute.ID)))) {
						Object src = attributes.getAttribute(HTML.Attribute.SRC);
						if(src != null) candidates[2] = src.toString().trim();
					}
				}
				@Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) { inspect(tag, attributes); }
				@Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) { inspect(tag, attributes); }
			}, true);
		for(String candidate : candidates) {
			if(candidate == null || candidate.length() == 0) continue;
			URL resolved;
			try { resolved = new URL(page, candidate); }
			catch(Exception e) { continue; }
			if("i.postimg.cc".equalsIgnoreCase(resolved.getHost())) return resolved;
		}
		throw new FlagImportException("Postimages page has no supported image metadata; use its direct PNG/JPEG link");
	}

	private static class DownloadBudget {
		final ServerSession owner;
		final long deadline = System.nanoTime() + XFConfig.customFlagTimeoutMs * 1000000L;
		int redirects;
		DownloadBudget(ServerSession owner) { this.owner = owner; }
		int remaining() throws FlagImportException {
			long millis = (deadline - System.nanoTime()) / 1000000L;
			if(millis <= 0 || Thread.currentThread().isInterrupted()) throw new FlagImportException("download timed out or cancelled");
			return (int)Math.min(Integer.MAX_VALUE, millis);
		}
	}

	private static BufferedImage decodeValidatedImage(byte[] downloaded) throws IOException {
		ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(downloaded));
		if(stream == null)
			throw new FlagImportException("failed to decode image");
		ImageReader reader = null;
		try {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
			if(!readers.hasNext())
				throw new FlagImportException("failed to decode image");
			reader = readers.next();
			String format = reader.getFormatName();
			if(!"png".equalsIgnoreCase(format) && !"jpeg".equalsIgnoreCase(format))
				throw new FlagImportException("unsupported image type; use PNG/JPEG");
			reader.setInput(stream, true, true);
			int width = reader.getWidth(0);
			int height = reader.getHeight(0);
			if(width <= 0 || height <= 0)
				throw new FlagImportException("failed to decode image");
			if(width > XFConfig.customFlagMaxWidth || height > XFConfig.customFlagMaxHeight)
				throw new FlagImportException("image too large");
			long pixels = (long)width * (long)height;
			long maxPixels = (long)XFConfig.customFlagMaxWidth * (long)XFConfig.customFlagMaxHeight;
			if(pixels > maxPixels)
				throw new FlagImportException("image too large");
			BufferedImage image = reader.read(0);
			if(image == null)
				throw new FlagImportException("failed to decode image");
			return image;
		} finally {
			if(reader != null)
				reader.dispose();
			stream.close();
		}
	}

	private static boolean isAllowedHost(String host) {
		if(host == null)
			return false;
		String normalized = host.toLowerCase(Locale.ROOT);
		if(normalized.endsWith("."))
			normalized = normalized.substring(0, normalized.length() - 1);
		return XFConfig.customFlagAllowedHostSet.contains(normalized);
	}

	private static void validateHost(final String host, DownloadBudget budget) throws Exception {
		// Native DNS has no Java 8 timeout. Bound the import's wait separately, including
		// queue time, and cap resolver workers even when the OS ignores interruption.
		Future<InetAddress[]> lookup;
		try { lookup = budget.owner.dns.submit(() -> InetAddress.getAllByName(host)); }
		catch(java.util.concurrent.RejectedExecutionException e) { throw new FlagImportException("DNS resolver busy"); }
		InetAddress[] addresses;
		try { addresses = lookup.get(budget.remaining(), TimeUnit.MILLISECONDS); }
		catch(java.util.concurrent.TimeoutException e) { throw new FlagImportException("DNS lookup timed out on " + host); }
		catch(java.util.concurrent.ExecutionException e) { throw new FlagImportException("DNS lookup failed on " + host); }
		finally { lookup.cancel(true); budget.owner.dns.purge(); }
		if(addresses == null || addresses.length == 0)
			throw new FlagImportException("unsafe host");
		for(int i = 0; i < addresses.length; i++) {
			if(isUnsafeAddress(addresses[i]))
				throw new FlagImportException("unsafe host");
		}
	}

	public static boolean isUnsafeAddress(InetAddress address) {
		if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress())
			return true;
		byte[] b = address.getAddress();
		if(b.length == 16) {
			int first = b[0] & 0xff;
			return (first & 0xfe) == 0xfc;
		}
		return false;
	}

	private static byte[] readLimited(InputStream input, int max) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int total = 0;
		while(true) {
			int read = input.read(buf);
			if(read < 0)
				break;
			total += read;
			if(total > max)
				throw new FlagImportException("image too large");
			out.write(buf, 0, read);
		}
		return out.toByteArray();
	}

	private static byte[] readFile(File file) throws IOException {
		if(file.length() > XFConfig.customFlagMaxFileSizeBytes)
			throw new EOFException("flag file too large");
		FileInputStream in = new FileInputStream(file);
		try {
			return readLimited(in, XFConfig.customFlagMaxFileSizeBytes);
		} finally {
			in.close();
		}
	}

	private static BufferedImage resizeToFlag(BufferedImage input) {
		BufferedImage output = new BufferedImage(FLAG_SIZE, FLAG_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = output.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			int w = input.getWidth();
			int h = input.getHeight();
			int size = Math.min(w, h);
			int sx = (w - size) / 2;
			int sy = (h - size) / 2;
			g.drawImage(input, 0, 0, FLAG_SIZE, FLAG_SIZE, sx, sy, sx + size, sy + size, null);
		} finally {
			g.dispose();
		}
		return output;
	}

	private static String sha256(byte[] data) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hash = digest.digest(data);
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < hash.length; i++)
			sb.append(String.format("%02x", hash[i] & 0xff));
		return sb.toString();
	}

	private static class ImportResult {
		final String message;
		final String hash;
		final byte[] bytes;
		ImportResult(String message, String hash, byte[] bytes) { this.message = message; this.hash = hash; this.bytes = bytes; }
		boolean success() { return hash != null && hash.length() > 0; }
	}

	private static class ServerSession {
		final MinecraftServer server;
		final Map<String, Long> cooldowns = new HashMap<String, Long>();
		final Map<String, ImportRequest> activeImports = new HashMap<String, ImportRequest>();
		final Map<String, List<ImportRequest>> downloads = new HashMap<String, List<ImportRequest>>();
		final ThreadPoolExecutor dns = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS,
			new ArrayBlockingQueue<Runnable>(8), runnable -> daemon(runnable, "Xenofactions flag DNS"));
		final ScheduledExecutorService timeouts = Executors.newSingleThreadScheduledExecutor(
			runnable -> daemon(runnable, "Xenofactions flag timeout"));
		final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable runnable) {
				Thread thread = new Thread(runnable, "Xenofactions flag importer");
				thread.setDaemon(true);
				return thread;
			}
		});
		ServerSession(MinecraftServer server) { this.server = server; }
	}

	private static Thread daemon(Runnable runnable, String name) {
		Thread thread = new Thread(runnable, name);
		thread.setDaemon(true);
		return thread;
	}

	private static class ImportRequest {
		final ServerSession owner;
		final String factionId;
		final UUID playerId;
		final Clowder faction;
		volatile boolean cancelled;
		ImportRequest(ServerSession owner, String factionId, UUID playerId, Clowder faction) {
			this.owner = owner;
			this.factionId = factionId;
			this.playerId = playerId;
			this.faction = faction;
		}
		void cancel() {
			cancelled = true;
			// A shared download may still be needed by another faction; installation rechecks cancellation.
		}
	}

	private static class FlagImportException extends IOException {
		FlagImportException(String message) { super(message); }
	}
}
