package com.hfr.server;

import com.google.gson.*;
import com.hfr.main.Xenofactions;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

/** Native stone rewards keep the existing JSON fields; legacy item variants require explicit native registry names. */
public final class StoneDrops {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Entry> entries = new ArrayList<>();
    private static JsonArray retained = new JsonArray();
    private static String failure;
    private static Path path() { return FMLPaths.CONFIGDIR.get().resolve("stonedrops.json"); }
    private static final class Entry {
        final ItemStack stack; final double chance; final Integer min, max;
        Entry(JsonObject tag) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(tag.get("itemName").getAsString()));
            if (item == null || item == net.minecraft.item.Items.AIR) throw new IllegalArgumentException("Unregistered native stone-drop item.");
            int count = tag.get("count").getAsInt(), meta = tag.has("metadata") ? tag.get("metadata").getAsInt() : 0;
            if (count <= 0 || meta < 0 || meta != 0 && item.getMaxDamage() == 0) throw new IllegalArgumentException("Use native item registry names for legacy metadata variants.");
            stack = new ItemStack(item, count); if (meta != 0) stack.setDamageValue(meta);
            if (tag.has("nbtData") && !tag.get("nbtData").isJsonNull()) stack.setTag(JsonToNBT.parseTag(tag.get("nbtData").getAsString()));
            chance = tag.get("chance").getAsDouble(); if (!Double.isFinite(chance)) throw new IllegalArgumentException("Stone-drop chance must be finite.");
            min = tag.has("minY") && !tag.get("minY").isJsonNull() ? tag.get("minY").getAsInt() : null;
            max = tag.has("maxY") && !tag.get("maxY").isJsonNull() ? tag.get("maxY").getAsInt() : null;
            if (min != null && max != null && min > max) throw new IllegalArgumentException("Invalid stone-drop Y range.");
        }
    }
    public static void clear() { entries.clear(); retained = new JsonArray(); failure = null; }
    public static void load() {
        clear(); if (!Files.exists(path())) return;
        try (Reader reader = Files.newBufferedReader(path(), StandardCharsets.UTF_8)) {
            JsonElement root = new JsonParser().parse(reader); if (!root.isJsonArray()) throw new IllegalArgumentException("Expected a stone-drop list.");
            List<Entry> next = new ArrayList<>(); for (JsonElement tag : root.getAsJsonArray()) next.add(new Entry(tag.getAsJsonObject()));
            retained = root.getAsJsonArray(); entries.addAll(next);
        } catch (Exception invalid) { failure = "Stone drops disabled; preserve and repair config/stonedrops.json: " + invalid.getMessage(); Xenofactions.LOGGER.error(failure); }
    }
    private static void save(JsonArray next) {
        if (failure != null) throw new IllegalStateException(failure);
        Path temporary = path().resolveSibling("stonedrops.json.tmp");
        try {
            List<Entry> decoded = new ArrayList<>(); for (JsonElement tag : next) decoded.add(new Entry(tag.getAsJsonObject()));
            if (Files.exists(path())) { Path backup = path().resolveSibling("stonedrops.json.migration-1.16.5.bak"); if (!Files.exists(backup)) Files.copy(path(), backup); }
            Files.write(temporary, JSON.toJson(next).getBytes(StandardCharsets.UTF_8));
            try { Files.move(temporary, path(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temporary, path(), StandardCopyOption.REPLACE_EXISTING); }
            retained = next; entries.clear(); entries.addAll(decoded);
        } catch (Exception invalid) { throw new IllegalStateException("Stone drops were not changed: " + invalid.getMessage(), invalid); }
        finally { try { Files.deleteIfExists(temporary); } catch (IOException ignored) { Xenofactions.LOGGER.warn("Could not remove stone-drop temporary file."); } }
    }
    public static String add(ItemStack held, double chance, Integer min, Integer max) {
        if (held.isEmpty() || !Double.isFinite(chance) || min != null && max != null && min > max) throw new IllegalArgumentException("Hold an item and use a finite chance and valid Y range.");
        JsonArray next = (JsonArray)com.hfr.data.PlayerTimers.copyElement(retained); JsonObject tag = new JsonObject(); tag.addProperty("itemName", held.getItem().getRegistryName().toString()); tag.addProperty("metadata", held.getDamageValue()); tag.addProperty("count", held.getCount()); tag.addProperty("chance", chance);
        if (held.hasTag()) tag.addProperty("nbtData", held.getTag().toString()); if (min != null) tag.addProperty("minY", min); if (max != null) tag.addProperty("maxY", max);
        next.add(tag); save(next); return "Stone drop added.";
    }
    public static String remove(int index) { if (index < 1 || index > entries.size()) throw new IllegalArgumentException("Stone-drop index is out of range."); JsonArray next = (JsonArray)com.hfr.data.PlayerTimers.copyElement(retained); next.remove(index - 1); save(next); return "Stone drop removed."; }
    public static String list(int requestedPage) {
        if (failure != null) throw new IllegalStateException(failure); if (entries.isEmpty()) return "No custom stone drops are configured.";
        int pages = (entries.size() + 7) / 8, page = Math.min(Math.max(1, requestedPage), pages); StringBuilder text = new StringBuilder("Custom stone drops — page " + page + "/" + pages);
        for (int i = (page - 1) * 8; i < Math.min(entries.size(), page * 8); i++) { Entry e = entries.get(i); text.append('\n').append(i + 1).append(": ").append(e.stack.getHoverName().getString()).append(" x").append(e.stack.getCount()).append(" | ").append(e.stack.getItem().getRegistryName()).append(" | ").append(e.chance * 100).append("% | ").append(e.min == null || e.max == null ? "any height" : "Y " + e.min + "–" + e.max); }
        return text.toString();
    }
    public static void drops(ServerWorld world, BlockPos pos) {
        if (failure != null) return;
        for (Entry entry : entries) if ((entry.min == null || entry.max == null || pos.getY() >= entry.min && pos.getY() <= entry.max) && world.random.nextDouble() < entry.chance) spawn(world, pos, entry.stack.copy());
    }
    public static void spawn(ServerWorld world, BlockPos pos, ItemStack stack) { world.addFreshEntity(new ItemEntity(world, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack)); }
    private StoneDrops() { }
}
