package com.hfr.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.hfr.config.FoundationConfig;
import com.hfr.server.FactionService;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.FolderName;

/** Per-save UUID timers. Expired protection records remain to prevent renewed first-login grants. */
public final class PlayerTimers {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Store cooldowns;
    private final Store protection;
    public PlayerTimers(MinecraftServer server) {
        Path root = server.getWorldPath(FolderName.ROOT);
        cooldowns = new Store(root.resolve("clowder_faction_creation_cooldowns.json"));
        protection = new Store(root.resolve("clowder_player_protection.json"));
        for (String field : new String[]{"uuids", "nameFallbacks"}) {
            JsonObject entries = object(cooldowns.state, field);
            for (String id : keys(entries)) {
                if (field.equals("uuids") && !UUID.fromString(id).toString().equals(id)) throw new IllegalStateException("Noncanonical cooldown UUID");
                if (field.equals("nameFallbacks") && (id.isEmpty() || !nameKey(id).equals(id))) throw new IllegalStateException("Non-normalized cooldown fallback name");
                JsonObject entry = entries.getAsJsonObject(id); timestamp(entry, "expiresAt");
                if (!entry.has("lastKnownName") || !entry.get("lastKnownName").isJsonPrimitive() || !entry.getAsJsonPrimitive("lastKnownName").isString()) throw new IllegalStateException("Invalid cooldown name");
            }
        }
        for (String id : keys(protection.state)) {
            if (!UUID.fromString(id).toString().equals(id)) throw new IllegalStateException("Noncanonical protection UUID");
            JsonObject entry = protection.state.getAsJsonObject(id); timestamp(entry, "pvpGraceUntil"); timestamp(entry, "keepInvUntil");
        }
    }
    private static JsonObject object(JsonObject root, String field) {
        if (!root.has(field)) return new JsonObject();
        if (!root.get(field).isJsonObject()) throw new IllegalStateException("Invalid timer field " + field);
        return root.getAsJsonObject(field);
    }
    private static long timestamp(JsonObject entry, String field) {
        if (entry == null || !entry.has(field) || !entry.get(field).isJsonPrimitive() || !entry.getAsJsonPrimitive(field).isNumber()) throw new IllegalStateException("Invalid timer " + field);
        try { long value = entry.get(field).getAsBigDecimal().longValueExact(); if (value < 0) throw new ArithmeticException(); return value; }
        catch (ArithmeticException invalid) { throw new IllegalStateException("Invalid timer " + field, invalid); }
    }
    private static String nameKey(String name) { return name.trim().toLowerCase(Locale.ROOT); }
    private static List<String> keys(JsonObject object) {
        List<String> result = new ArrayList<>();
        for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) result.add(entry.getKey());
        return result;
    }
    // The pinned Minecraft Gson lacks public deepCopy/keySet; retain unknown JSON without a serialize/parse round trip.
    private static JsonObject copy(JsonObject source) { return (JsonObject)copyElement(source); }
    public static JsonElement copyElement(JsonElement source) {
        if (source.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (java.util.Map.Entry<String, JsonElement> entry : source.getAsJsonObject().entrySet()) result.add(entry.getKey(), copyElement(entry.getValue()));
            return result;
        }
        if (source.isJsonArray()) {
            JsonArray result = new JsonArray(); for (JsonElement element : source.getAsJsonArray()) result.add(copyElement(element)); return result;
        }
        return source; // JsonPrimitive/JsonNull have no mutable child state.
    }
    public long cooldown(ServerPlayerEntity player) {
        JsonObject uuids = object(cooldowns.state, "uuids"), fallbacks = object(cooldowns.state, "nameFallbacks");
        String name = nameKey(player.getGameProfile().getName()); long until = 0;
        if (FactionService.usesNames(player.getServer())) {
            if (fallbacks.has(name)) until = timestamp(fallbacks.getAsJsonObject(name), "expiresAt");
            for (String id : keys(uuids)) { JsonObject entry = uuids.getAsJsonObject(id); if (nameKey(entry.get("lastKnownName").getAsString()).equals(name)) until = Math.max(until, timestamp(entry, "expiresAt")); }
        } else if (uuids.has(player.getUUID().toString())) until = timestamp(uuids.getAsJsonObject(player.getUUID().toString()), "expiresAt");
        return until > System.currentTimeMillis() ? until : 0;
    }
    public void login(ServerPlayerEntity player, boolean protectionEnabled) {
        migrateFallback(player);
        cleanExpiredCooldowns();
        if (!protectionEnabled || protection.state.has(player.getUUID().toString())) return;
        JsonObject next = copy(protection.state), entry = new JsonObject(); long now = System.currentTimeMillis();
        entry.addProperty("pvpGraceUntil", now + (long)(FoundationConfig.PVP_HOURS.get() * 3600000D));
        entry.addProperty("keepInvUntil", now + (long)(FoundationConfig.KEEP_INVENTORY_HOURS.get() * 3600000D));
        next.add(player.getUUID().toString(), entry); protection.write(next);
        player.sendMessage(new net.minecraft.util.text.StringTextComponent("New-player PvP grace granted for " + FoundationConfig.PVP_HOURS.get() + " hours."), net.minecraft.util.Util.NIL_UUID);
    }
    public void migrateFallback(ServerPlayerEntity player) {
        if (FactionService.usesNames(player.getServer())) return;
        String name = nameKey(player.getGameProfile().getName()); JsonObject next = copy(cooldowns.state); JsonObject fallbacks = object(next, "nameFallbacks");
        if (!fallbacks.has(name)) {
            JsonObject uuids = object(next, "uuids"); String uuid = player.getUUID().toString();
            if (uuids.has(uuid) && !uuids.getAsJsonObject(uuid).get("lastKnownName").getAsString().equals(player.getGameProfile().getName())) {
                uuids.getAsJsonObject(uuid).addProperty("lastKnownName", player.getGameProfile().getName()); cooldowns.write(next);
            }
            return;
        }
        JsonObject fallback = fallbacks.getAsJsonObject(name); long expires = timestamp(fallback, "expiresAt");
        JsonObject uuids = object(next, "uuids"); String uuid = player.getUUID().toString();
        JsonObject entry = uuids.has(uuid) ? uuids.getAsJsonObject(uuid) : copy(fallback);
        for (java.util.Map.Entry<String, JsonElement> field : fallback.entrySet()) if (!entry.has(field.getKey())) entry.add(field.getKey(), copyElement(field.getValue()));
        entry.addProperty("expiresAt", Math.max(expires, uuids.has(uuid) ? timestamp(entry, "expiresAt") : 0));
        entry.addProperty("lastKnownName", player.getGameProfile().getName()); uuids.add(uuid, entry); fallbacks.remove(name);
        next.add("uuids", uuids); next.add("nameFallbacks", fallbacks); cooldowns.write(next);
    }
    private void cleanExpiredCooldowns() {
        JsonObject next = copy(cooldowns.state); boolean changed = false; long now = System.currentTimeMillis();
        for (String field : new String[]{"uuids", "nameFallbacks"}) {
            JsonObject entries = object(next, field);
            for (String id : keys(entries)) if (timestamp(entries.getAsJsonObject(id), "expiresAt") <= now) { entries.remove(id); changed = true; }
        }
        if (changed) cooldowns.write(next);
    }
    public void disband(List<CompoundNBT> members) {
        JsonObject next = copy(cooldowns.state), uuids = object(next, "uuids"), fallbacks = object(next, "nameFallbacks"); long now = System.currentTimeMillis();
        for (CompoundNBT member : members) {
            String name = member.getString("lastKnownName"), uuid = member.getString("uuid");
            JsonObject target = uuid.isEmpty() ? fallbacks : uuids; String id = uuid.isEmpty() ? nameKey(name) : UUID.fromString(uuid).toString();
            JsonObject entry = target.has(id) ? target.getAsJsonObject(id) : new JsonObject();
            long expires = now + ("OWNER".equals(member.getString("role")) ? 7L : 3L) * 86400000L;
            if (entry.has("expiresAt")) expires = Math.max(expires, timestamp(entry, "expiresAt"));
            entry.addProperty("expiresAt", expires); entry.addProperty("lastKnownName", name); target.add(id, entry);
        }
        next.add("uuids", uuids); next.add("nameFallbacks", fallbacks); cooldowns.write(next);
    }
    public long grace(UUID player) {
        JsonObject entry = protection.state.has(player.toString()) ? protection.state.getAsJsonObject(player.toString()) : null;
        return entry == null ? 0 : timestamp(entry, "pvpGraceUntil");
    }
    public String clearCooldown(MinecraftServer server, String target) {
        JsonObject next = copy(cooldowns.state), uuids = object(next, "uuids"), fallbacks = object(next, "nameFallbacks"); List<String> matches = new ArrayList<>();
        UUID uuid = null; try { uuid = UUID.fromString(target); } catch (IllegalArgumentException notUuid) { }
        ServerPlayerEntity online = uuid == null ? server.getPlayerList().getPlayerByName(target) : server.getPlayerList().getPlayer(uuid);
        if (online != null) { uuid = online.getUUID(); target = online.getGameProfile().getName(); }
        String name = nameKey(target);
        if (uuid != null && uuids.has(uuid.toString())) { matches.add(uuid.toString()); if (online == null) name = nameKey(uuids.getAsJsonObject(uuid.toString()).get("lastKnownName").getAsString()); }
        for (String id : keys(uuids)) if (name.equals(nameKey(uuids.getAsJsonObject(id).get("lastKnownName").getAsString())) && !matches.contains(id)) matches.add(id);
        if (matches.size() > 1) throw new IllegalArgumentException("Ambiguous cooldown identity; nothing cleared.");
        for (String id : matches) uuids.remove(id); boolean fallback = fallbacks.has(name); fallbacks.remove(name);
        if (!matches.isEmpty() || fallback) { next.add("uuids", uuids); next.add("nameFallbacks", fallbacks); cooldowns.write(next); }
        return "Creation cooldown cleared (" + (matches.size() + (fallback ? 1 : 0)) + " records).";
    }
    public void resetProtection(boolean end) {
        JsonObject next = copy(protection.state); long now = System.currentTimeMillis();
        for (String uuid : keys(next)) {
            JsonObject entry = next.getAsJsonObject(uuid);
            entry.addProperty("pvpGraceUntil", end ? 0 : now + (long)(FoundationConfig.PVP_HOURS.get() * 3600000D));
            entry.addProperty("keepInvUntil", end ? 0 : now + (long)(FoundationConfig.KEEP_INVENTORY_HOURS.get() * 3600000D));
        }
        protection.write(next);
    }
    private static final class Store {
        final Path file; JsonObject state; boolean backedUp;
        Store(Path file) {
            this.file = file; state = new JsonObject(); if (!Files.exists(file)) return;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
                if (parsed == null || !parsed.isJsonObject()) throw new IllegalStateException("Expected a JSON object in " + file);
                state = parsed.getAsJsonObject();
            } catch (IOException | RuntimeException invalid) { throw new IllegalStateException("Cannot read " + file + "; existing data was preserved.", invalid); }
        }
        void write(JsonObject next) {
            Path temporary = null;
            try {
                if (!backedUp && Files.exists(file)) {
                    Path backup = file.resolveSibling(file.getFileName() + ".migration-1.16.5.bak");
                    if (!Files.exists(backup)) Files.copy(file, backup);
                }
                temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
                Files.write(temporary, GSON.toJson(next).getBytes(StandardCharsets.UTF_8));
                try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
                state = next; backedUp = true;
            } catch (IOException failure) { throw new IllegalStateException("Cannot save " + file + "; mutation refused.", failure); }
            finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
        }
    }
}
