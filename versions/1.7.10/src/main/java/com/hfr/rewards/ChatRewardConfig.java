package com.hfr.rewards;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hfr.util.XFLog;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatAllowedCharacters;

/** Server-only definitions. Publish immutable snapshots to the network chat hook. */
public final class ChatRewardConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Path file;
    private static volatile Map<String, Phrase> phrases = Collections.emptyMap();
    private static volatile Map<String, Phrase> matches = Collections.emptyMap();
    private static boolean writable;

    public static final class Phrase {
        public String id, phrase;
        public boolean enabled, repeatable;
        public long cooldownSeconds = 60, totalLimit = -1;
        public String startsAt = "", expiresAt = "";
        public List<Reward> rewards = new ArrayList<Reward>();
        transient long start, expiry;
        public NBTTagList pending() {
            NBTTagList pending = new NBTTagList();
            for (Reward reward : rewards) {
                int count = reward.count;
                while (count > 0) {
                    ItemStack stack = reward.template.copy();
                    stack.stackSize = Math.min(count, Math.min(64, stack.getMaxStackSize()));
                    NBTTagCompound raw = stack.writeToNBT(new NBTTagCompound());
                    raw.setString("Registry", reward.item); pending.appendTag(raw); count -= stack.stackSize;
                }
            }
            return pending;
        }
    }
    public static final class Reward {
        public String item, nbt = "{}", nbtBase64 = "";
        public int metadata, count = 1;
        transient ItemStack template;
    }
    private ChatRewardConfig() { }
    public static void configure(File directory) { file = new File(directory, "xenofactions-chat-rewards.json").toPath(); }
    public static String normalize(String phrase) { return phrase.trim().toLowerCase(Locale.ROOT); }
    public static Phrase match(String message) {
        if (message == null) return null;
        String key = normalize(message);
        return key.startsWith("/") ? null : matches.get(key);
    }
    public static void load() {
        writable = false; phrases = Collections.emptyMap(); matches = Collections.emptyMap();
        try {
            JsonArray definitions = new JsonArray();
            if (Files.exists(file)) {
                JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
                schema(root, "version", "phrases"); integer(root, "version", true);
                if (root.get("version").getAsLong() != 1L) throw new IllegalArgumentException("version");
                definitions = root.getAsJsonArray("phrases");
            }
            Map<String, Phrase> parsed = parse(definitions);
            if (!Files.exists(file)) save(parsed);
            publish(parsed); writable = true;
        } catch (Exception e) { XFLog.error("Chat reward configuration rejected; repair " + file + " and restart. Original file retained", e); }
    }
    public static Map<String, Phrase> definitions() { requireWritable(); return copies(phrases); }
    public static Phrase definition(String id) {
        requireWritable(); Phrase phrase = phrases.get(id);
        if (phrase == null) throw new IllegalArgumentException("unknown_id");
        return copy(phrase);
    }
    static Phrase active(String id) {
        requireWritable(); Phrase phrase = phrases.get(id);
        if (phrase == null) throw new IllegalArgumentException("unknown_id");
        return phrase;
    }
    public static String json(Phrase phrase) { return GSON.toJson(phrase); }
    public static Reward capture(ItemStack held) throws java.io.IOException {
        Reward reward = new Reward(); reward.item = Item.itemRegistry.getNameForObject(held.getItem());
        reward.metadata = held.getItemDamage(); reward.count = held.stackSize;
        if (held.hasTagCompound()) {
            byte[] bytes = binary(held.getTagCompound());
            if (bytes.length > 16384) throw new IllegalArgumentException("nbt");
            reward.nbtBase64 = Base64.getEncoder().encodeToString(bytes);
        }
        return reward;
    }
    /** Validate raw JSON before Gson can coerce fractional/null item numbers into integers. */
    public static Phrase edited(JsonObject json) { return parse(single(json)).values().iterator().next(); }
    private static JsonArray single(JsonObject json) { JsonArray array = new JsonArray(); array.add(json); return array; }
    public static void put(Phrase phrase, boolean create) throws java.io.IOException {
        requireWritable();
        if (create && phrases.containsKey(phrase.id)) throw new IllegalArgumentException("duplicate_id");
        Map<String, Phrase> next = copies(phrases); next.put(phrase.id, phrase);
        next = parse(GSON.toJsonTree(next.values()).getAsJsonArray()); // Validate every item and duplicate before saving or enabling.
        save(next); publish(next);
    }
    public static void remove(String id) throws java.io.IOException {
        definition(id); Map<String, Phrase> next = copies(phrases); next.remove(id);
        next = parse(GSON.toJsonTree(next.values()).getAsJsonArray());
        save(next); publish(next); // History is deliberately owned by the world ledger, independently of definitions.
    }
    private static void requireWritable() { if (!writable) throw new IllegalStateException("configuration"); }
    private static Phrase copy(Phrase phrase) { return GSON.fromJson(GSON.toJsonTree(phrase), Phrase.class); }
    private static Map<String, Phrase> copies(Map<String, Phrase> source) {
        Map<String, Phrase> result = new LinkedHashMap<String, Phrase>();
        for (Phrase phrase : source.values()) result.put(phrase.id, copy(phrase));
        return result;
    }
    private static void publish(Map<String, Phrase> parsed) {
        Map<String, Phrase> byText = new LinkedHashMap<String, Phrase>();
        for (Phrase phrase : parsed.values()) byText.put(normalize(phrase.phrase), phrase);
        phrases = Collections.unmodifiableMap(parsed); matches = Collections.unmodifiableMap(byText);
    }
    private static void save(Map<String, Phrase> parsed) throws java.io.IOException {
        JsonObject root = new JsonObject(); root.addProperty("version", 1); root.add("phrases", GSON.toJsonTree(parsed.values()));
        ChatRewardStore.atomic(file, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
    }
    private static Map<String, Phrase> parse(JsonArray definitions) {
        if (definitions == null || definitions.size() > 256) throw new IllegalArgumentException("definitions");
        Map<String, Phrase> result = new LinkedHashMap<String, Phrase>();
        java.util.Set<String> texts = new java.util.HashSet<String>();
        for (JsonElement entry : definitions) {
            JsonObject json = entry.getAsJsonObject();
            schema(json, "id", "phrase", "enabled", "repeatable", "cooldownSeconds", "totalLimit", "startsAt", "expiresAt", "rewards");
            string(json, "id", true); string(json, "phrase", true); string(json, "startsAt", false); string(json, "expiresAt", false);
            bool(json, "enabled"); bool(json, "repeatable"); integer(json, "cooldownSeconds", false); integer(json, "totalLimit", false);
            if (json.has("rewards")) for (JsonElement rewardEntry : json.getAsJsonArray("rewards")) {
                JsonObject reward = rewardEntry.getAsJsonObject(); schema(reward, "item", "metadata", "count", "nbt", "nbtBase64");
                string(reward, "item", true); string(reward, "nbt", false); string(reward, "nbtBase64", false);
                integer(reward, "metadata", false); integer(reward, "count", false);
            }
            Phrase phrase = GSON.fromJson(entry, Phrase.class);
            if (phrase == null || phrase.id == null || !phrase.id.matches("[a-z0-9_-]{1,48}")) throw new IllegalArgumentException("id");
            if (result.put(phrase.id, phrase) != null) throw new IllegalArgumentException("duplicate_id");
            if (phrase.phrase == null) throw new IllegalArgumentException("phrase");
            phrase.phrase = phrase.phrase.trim();
            if (phrase.phrase.isEmpty() || phrase.phrase.length() > 100 || phrase.phrase.startsWith("/")) throw new IllegalArgumentException("phrase");
            for (int i = 0; i < phrase.phrase.length(); i++)
                if (!ChatAllowedCharacters.isAllowedCharacter(phrase.phrase.charAt(i))) throw new IllegalArgumentException("phrase");
            if (!texts.add(normalize(phrase.phrase))) throw new IllegalArgumentException("duplicate_phrase");
            if (phrase.cooldownSeconds < 1 || phrase.cooldownSeconds > 315360000L || phrase.totalLimit < -1) throw new IllegalArgumentException("limits");
            phrase.start = time(phrase.startsAt); phrase.expiry = time(phrase.expiresAt);
            if (phrase.expiry != 0 && phrase.start >= phrase.expiry) throw new IllegalArgumentException("times");
            if (phrase.rewards == null || phrase.rewards.size() > 16 || phrase.enabled && phrase.rewards.isEmpty()) throw new IllegalArgumentException("rewards");
            for (Reward reward : phrase.rewards) {
                if (reward == null || reward.item == null || !Item.itemRegistry.containsKey(reward.item)
                    || reward.metadata < 0 || reward.metadata > 32767 || reward.count < 1 || reward.count > 4096
                    || reward.nbt == null || reward.nbt.length() > 8192 || reward.nbtBase64 == null
                    || reward.nbtBase64.length() > 21848) throw new IllegalArgumentException("item");
                NBTTagCompound tag;
                try {
                    if (reward.nbtBase64.isEmpty()) tag = (NBTTagCompound)JsonToNBT.func_150315_a(reward.nbt);
                    else {
                        if (!"{}".equals(reward.nbt.trim())) throw new IllegalArgumentException("nbt");
                        byte[] bytes = Base64.getDecoder().decode(reward.nbtBase64);
                        if (bytes.length > 16384) throw new IllegalArgumentException("nbt");
                        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                            tag = CompressedStreamTools.func_152456_a(input, new NBTSizeTracker(65536L));
                            if (input.available() != 0) throw new IllegalArgumentException("nbt");
                        }
                    }
                    if (binary(tag).length > 16384) throw new IllegalArgumentException("nbt");
                }
                catch (Exception e) { throw new IllegalArgumentException("nbt"); }
                reward.template = new ItemStack((Item)Item.itemRegistry.getObject(reward.item), 1, reward.metadata);
                if (!tag.hasNoTags() || !reward.nbtBase64.isEmpty()) reward.template.setTagCompound(tag);
                if (reward.template.getMaxStackSize() < 1) throw new IllegalArgumentException("item");
            }
            if (phrase.pending().tagCount() > 8192) throw new IllegalArgumentException("rewards");
        }
        return result;
    }
    private static byte[] binary(NBTTagCompound tag) throws java.io.IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) { CompressedStreamTools.write(tag, out); }
        return bytes.toByteArray();
    }
    private static void schema(JsonObject object, String... names) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet())
            if (!Arrays.asList(names).contains(entry.getKey())) throw new IllegalArgumentException("field");
    }
    private static void string(JsonObject object, String key, boolean required) {
        if (!object.has(key)) { if (required) throw new IllegalArgumentException("input"); else return; }
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("input");
    }
    private static void integer(JsonObject object, String key, boolean required) {
        if (!object.has(key)) { if (required) throw new IllegalArgumentException("input"); else return; }
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)"))
            throw new IllegalArgumentException("input");
        if ("metadata".equals(key) || "count".equals(key)) Integer.parseInt(value.getAsString());
        else Long.parseLong(value.getAsString());
    }
    private static void bool(JsonObject object, String key) {
        if (object.has(key) && (!object.get(key).isJsonPrimitive() || !object.get(key).getAsJsonPrimitive().isBoolean()))
            throw new IllegalArgumentException("boolean");
    }
    private static long time(String value) {
        try {
            if (value != null && value.isEmpty()) return 0;
            long time = Instant.parse(value).toEpochMilli();
            if (time <= 0) throw new IllegalArgumentException();
            return time;
        } catch (Exception e) { throw new IllegalArgumentException("times"); }
    }
}
