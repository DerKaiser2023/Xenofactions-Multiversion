package com.hfr.server;

import com.google.gson.*;
import com.hfr.data.PlayerTimers;
import com.hfr.main.Xenofactions;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.command.CommandSource;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Save-owned moderation uses the reference UUID JSON formats, including absolute mute expirations. */
public final class ChatModeration {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static Store mutes, ignores;
    private static String failure;
    public static void start(MinecraftServer server) {
        clear(); Path root = server.getWorldPath(FolderName.ROOT);
        try { mutes = new Store(root.resolve("mutelist.json"), true); ignores = new Store(root.resolve("ignorelist.json"), false); }
        catch (Exception invalid) { failure = "Xenofactions chat moderation data needs repair: " + invalid.getMessage(); Xenofactions.LOGGER.error(failure, invalid); }
    }
    public static void clear() { mutes = null; ignores = null; failure = null; }
    private static void ready() { if (failure != null) throw new IllegalStateException(failure); if (mutes == null || ignores == null) throw new IllegalStateException("Chat moderation is not ready."); }
    private static final class Store {
        final Path file; JsonObject state = new JsonObject();
        Store(Path file, boolean mute) throws IOException {
            this.file = file; if (!Files.exists(file)) return;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement root = new JsonParser().parse(reader); if (!root.isJsonObject()) throw new IllegalArgumentException("Expected UUID object in " + file.getFileName()); state = root.getAsJsonObject();
                for (java.util.Map.Entry<String, JsonElement> entry : state.entrySet()) {
                    String key = UUID.fromString(entry.getKey()).toString(); if (!key.equals(entry.getKey())) throw new IllegalArgumentException("Noncanonical moderation UUID.");
                    if (mute) {
                        JsonObject value = entry.getValue().getAsJsonObject();
                        if (!key.equals(value.get("uuid").getAsString()) || value.get("expiresAt").getAsBigDecimal().longValueExact() < 0 || !value.getAsJsonPrimitive("reason").isString()) throw new IllegalArgumentException("Invalid mute record.");
                    } else for (JsonElement target : entry.getValue().getAsJsonArray()) if (!UUID.fromString(target.getAsString()).toString().equals(target.getAsString())) throw new IllegalArgumentException("Invalid ignored UUID.");
                }
            }
        }
        JsonObject copy() { return (JsonObject)PlayerTimers.copyElement(state); }
        void write(JsonObject next) {
            ready(); Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try {
                Path backup = file.resolveSibling(file.getFileName() + ".migration-1.16.5.bak"); if (Files.exists(file) && !Files.exists(backup)) Files.copy(file, backup);
                Files.write(temp, JSON.toJson(next).getBytes(StandardCharsets.UTF_8));
                try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
                state = next;
            } catch (IOException invalid) { throw new IllegalStateException("Moderation data was not changed: " + invalid.getMessage(), invalid); }
            finally { try { Files.deleteIfExists(temp); } catch (IOException ignored) { Xenofactions.LOGGER.warn("Could not remove moderation temporary file."); } }
        }
    }
    public static String blocked(UUID player) {
        if (failure != null) return failure; ready(); String key = player.toString();
        if (!mutes.state.has(key)) return null; JsonObject value = mutes.state.getAsJsonObject(key); long until = value.get("expiresAt").getAsLong();
        if (until != Long.MAX_VALUE && until <= System.currentTimeMillis()) {
            try { JsonObject next = mutes.copy(); next.remove(key); mutes.write(next); } catch (IllegalStateException invalid) { Xenofactions.LOGGER.error("Expired mute could not be saved", invalid); }
            return null;
        }
        return (until == Long.MAX_VALUE ? "You are permanently muted." : "You are muted for " + (until - System.currentTimeMillis()) / 1000 + " more seconds.") + " Reason: " + value.get("reason").getAsString();
    }
    public static boolean ignoring(UUID recipient, UUID sender) {
        ready(); if (!ignores.state.has(recipient.toString())) return false;
        for (JsonElement target : ignores.state.getAsJsonArray(recipient.toString())) if (target.getAsString().equals(sender.toString())) return true; return false;
    }
    public static String mute(ServerPlayerEntity target, String duration, String reason) {
        ready(); int seconds;
        try { seconds = duration.equalsIgnoreCase("perm") ? -1 : Integer.parseInt(duration); } catch (NumberFormatException invalid) { throw new IllegalArgumentException("Use seconds or perm."); }
        JsonObject next = mutes.copy(), value = next.has(target.getUUID().toString()) ? next.getAsJsonObject(target.getUUID().toString()) : new JsonObject();
        value.addProperty("uuid", target.getUUID().toString()); value.addProperty("expiresAt", seconds < 0 ? Long.MAX_VALUE : System.currentTimeMillis() + seconds * 1000L); value.addProperty("reason", reason); next.add(target.getUUID().toString(), value); mutes.write(next);
        FactionGameplay.message(target, "You have been muted. Reason: " + reason); return target.getGameProfile().getName() + " muted " + (seconds < 0 ? "permanently." : "for " + seconds + " seconds.");
    }
    public static String unmute(ServerPlayerEntity target) { ready(); JsonObject next = mutes.copy(); next.remove(target.getUUID().toString()); mutes.write(next); FactionGameplay.message(target, "You have been unmuted."); return target.getGameProfile().getName() + " unmuted."; }
    public static String toggle(ServerPlayerEntity player, ServerPlayerEntity target) {
        ready(); if (target == player) throw new IllegalArgumentException("You cannot ignore yourself."); boolean remove = ignoring(player.getUUID(), target.getUUID());
        JsonObject next = ignores.copy(); JsonArray values = new JsonArray(); if (next.has(player.getUUID().toString())) for (JsonElement value : next.getAsJsonArray(player.getUUID().toString())) if (!value.getAsString().equals(target.getUUID().toString())) values.add(value);
        if (!remove) values.add(new JsonPrimitive(target.getUUID().toString())); next.add(player.getUUID().toString(), values); ignores.write(next); return (remove ? "Stopped ignoring " : "Ignoring ") + target.getGameProfile().getName() + ".";
    }
    @SubscribeEvent public void command(CommandEvent event) {
        if (event.isCanceled() || !event.getParseResults().getExceptions().isEmpty() || event.getParseResults().getReader().canRead()) return;
        com.mojang.brigadier.context.CommandContextBuilder<CommandSource> builder = event.getParseResults().getContext();
        if (!(builder.getSource().getEntity() instanceof ServerPlayerEntity) || builder.getNodes().isEmpty()) return;
        String command = builder.getNodes().get(0).getNode().getName(); if (!builder.getNodes().get(0).getNode().canUse(builder.getSource())) return;
        boolean direct = command.equals("msg") || command.equals("tell") || command.equals("w"), broadcast = command.equals("say") || command.equals("me"); if (!direct && !broadcast) return;
        ServerPlayerEntity player = (ServerPlayerEntity)builder.getSource().getEntity(); String blocked = blocked(player.getUUID());
        if (blocked != null) { event.setCanceled(true); FactionGameplay.message(player, blocked); return; }
        if (direct) {
            try {
                CommandContext<CommandSource> context = builder.build(event.getParseResults().getReader().getString());
                while (context.getChild() != null) context = context.getChild();
                for (ServerPlayerEntity target : EntityArgument.getPlayers(context, "targets")) if (ignoring(target.getUUID(), player.getUUID())) { event.setCanceled(true); FactionGameplay.message(player, "That player is ignoring you."); return; }
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) { /* Let the native command report an invalid target. */ }
        } else {
            String input = event.getParseResults().getReader().getString(), message = input.substring(builder.getNodes().get(0).getRange().getEnd()).trim();
            for (ServerPlayerEntity recipient : player.getServer().getPlayerList().getPlayers()) if (!ignoring(recipient.getUUID(), player.getUUID())) FactionGameplay.message(recipient, command.equals("say") ? "<" + player.getDisplayName().getString() + "> " + message : "* " + player.getDisplayName().getString() + " " + message);
            event.setCanceled(true);
        }
    }
}
