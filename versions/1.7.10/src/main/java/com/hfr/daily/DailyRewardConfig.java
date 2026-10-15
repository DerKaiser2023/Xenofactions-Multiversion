package com.hfr.daily;

import com.hfr.util.XFLog;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.config.Configuration;

/** Server-thread definitions: validate, durably replace, then publish. Player history lives elsewhere. */
public final class DailyRewardConfig {
    public static final String CATEGORY = "XENOFACTIONS_24_DAILY_REWARDS";
    private static Path file;
    private static boolean writable;
    private static boolean loaded;
    private static Table active = new Table();
    private static Configuration importConfig;
    private static Failure firstFailure, lastFailure;
    private static String timezone;
    private static String[] definitions, milestoneDefinitions;

    public static final class Table {
        public ZoneId zone = ZoneId.of("UTC"), legacyZone = zone;
        public boolean enabled = true;
        public final List<Reward> rewards = new ArrayList<Reward>();
        public final TreeMap<Integer, String> milestones = new TreeMap<Integer, String>();
        public int totalWeight;
        public Reward reward(String id) {
            for (Reward reward : rewards) if (reward.id.equals(id)) return reward;
            throw new IllegalArgumentException("unknown_id");
        }
    }
    public static final class Reward {
        public final String id;
        public final int weight, count;
        public final ItemStack template;
        public Reward(String id, int weight, int count, ItemStack template) {
            this.id = id; this.weight = weight; this.count = count; this.template = template.copy();
            this.template.stackSize = 1;
        }
        public NBTTagCompound preview() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag("item", template.writeToNBT(new NBTTagCompound()));
            tag.setInteger("amount", count); tag.setString("id", id);
            tag.setString("registry", Item.itemRegistry.getNameForObject(template.getItem()));
            return tag;
        }
        public void append(NBTTagList pending) {
            int remaining = count;
            while (remaining > 0) {
                ItemStack stack = template.copy();
                stack.stackSize = Math.min(remaining, Math.min(64, stack.getMaxStackSize()));
                NBTTagCompound tag = stack.writeToNBT(new NBTTagCompound());
                tag.setString("Registry", Item.itemRegistry.getNameForObject(template.getItem()));
                pending.appendTag(tag);
                remaining -= stack.stackSize;
            }
        }
    }
    private DailyRewardConfig() { }
    public static void configure(File directory) { file = new File(directory, "xenofactions-daily-rewards.dat").toPath().toAbsolutePath(); }
    public static void configure(Configuration config) {
        importConfig = config;
        timezone = config.get(CATEGORY, "serverTimezone", "UTC", "Initial import only: IANA timezone, e.g. UTC or America/New_York. Use /xc daily timezone for live management after import.").getString();
        definitions = config.get(CATEGORY, "rewards", new String[] {
            "bread|50|minecraft:bread|0|8|{}", "iron|30|minecraft:iron_ingot|0|4|{}",
            "gold|15|minecraft:gold_ingot|0|2|{}", "diamond|5|minecraft:diamond|0|1|{}"
        }, "Initial import only: 0-16 entries uniqueId|positiveIntegerWeight|registry:item|metadata|count|itemTagSNBT. Count 1-4096, metadata 0-32767, SNBT <= 2048 characters. After import use /xc daily commands.").getStringList();
        milestoneDefinitions = config.get(CATEGORY, "streakMilestones", new String[] {"7|diamond", "30|gold"},
            "Initial import only: up to 32 consecutiveClaimDays|rewardId entries. Once per UUID across all streaks. After import use /xc daily milestone commands.").getStringList();
    }
    public static void load() {
        writable = false; loaded = false; active = new Table(); active.enabled = false;
        firstFailure = null; lastFailure = null;
        reload(false);
    }
    public static final class Failure {
        public final String stage, reason, path;
        public final Exception cause;
        private Failure(String stage, String reason, String path, Exception cause) {
            this.stage = stage; this.reason = reason; this.path = path; this.cause = cause;
        }
        public String details() {
            StringBuilder details = new StringBuilder();
            for (Throwable error = cause; error != null; error = error.getCause()) {
                if (details.length() > 0) details.append(" -> ");
                details.append(error.getClass().getSimpleName()).append(": ").append(error.getMessage());
            }
            return details.toString().replace('\n', ' ').replace('\r', ' ');
        }
    }
    private static final class MissingItemException extends IllegalArgumentException {
        MissingItemException(String detail) { super(detail); }
    }
    public static String dataPath() { return file == null ? "(unconfigured)" : file.toString(); }
    public static String importPath() { return importConfig == null ? "(unconfigured)" : importConfig.getConfigFile().getAbsolutePath(); }
    public static Failure firstFailure() { return firstFailure; }
    public static Failure lastFailure() { return lastFailure; }
    public static String status() {
        return !loaded ? "unavailable" : !writable ? "retained" : !active.enabled ? "disabled"
            : active.rewards.isEmpty() ? "empty" : "ready";
    }
    public static boolean reload() { return reload(true); }
    private static boolean reload(boolean refreshImport) {
        String stage = "initialization", path = dataPath();
        try {
            if (file == null || importConfig == null || definitions == null || milestoneDefinitions == null || timezone == null)
                throw new IllegalStateException("Daily definition paths or import settings were not initialized");
            Table next;
            // exists() also returns false for inaccessible paths: only confirmed absence permits import.
            if (!Files.notExists(file)) {
                stage = "saved_read";
                NBTTagCompound root;
                try (FileInputStream in = new FileInputStream(file.toFile())) { root = CompressedStreamTools.readCompressed(in); }
                stage = "saved_entries"; next = validate(decode(root));
            } else {
                stage = "import_entries"; path = importPath();
                if (refreshImport) {
                    // Reuse the configured Forge object: its constructor can rename malformed files.
                    // Preflight reads because Configuration.load() swallows IOExceptions. Never save hfr.cfg here.
                    Files.readAllBytes(importConfig.getConfigFile().toPath());
                    importConfig.load(); configure(importConfig);
                }
                next = validate(imported());
                stage = "import_save"; path = dataPath();
                save(next); // Import exactly once, before making any spins available.
            }
            active = next; writable = true; loaded = true; lastFailure = null;
            DailyRewardService.definitionsChanged();
            return true;
        } catch (Exception e) {
            String reason = e instanceof MissingItemException ? "missing_item"
                : e instanceof java.io.FileNotFoundException || e instanceof java.nio.file.FileSystemException
                    || e instanceof java.io.SyncFailedException || e instanceof SecurityException
                    || e instanceof IOException && !"saved_read".equals(stage) ? "filesystem" : stage;
            lastFailure = new Failure(stage, reason, path, e);
            if (firstFailure == null) firstFailure = lastFailure;
            writable = false; // Keep a previous valid table readable, but never overwrite unreadable saved data.
            XFLog.error("Daily definitions load failed at " + stage + ": " + path
                + (loaded ? "; previous valid table retained" : "; new spins disabled"), e);
            return false;
        }
    }
    static Table active() { return active; }
    public static boolean ready() { return loaded && active.enabled && !active.rewards.isEmpty(); }
    public static Table definitions() {
        if (!loaded) throw new IllegalStateException("configuration");
        return copy(active);
    }
    public static void put(Table proposed) throws IOException {
        if (!writable) throw new IllegalStateException("configuration");
        Table next = validate(proposed);
        save(next); active = next;
        DailyRewardService.definitionsChanged();
    }
    public static Reward capture(String id, int weight, int count, ItemStack held) {
        if (held == null || held.stackSize < 1) throw new IllegalArgumentException("held");
        if (held.hasTagCompound() && held.getTagCompound().toString().length() > 2048) throw new IllegalArgumentException("nbt");
        return new Reward(id, weight, count, held); // ItemStack.copy deeply copies binary NBT, including arrays.
    }
    private static Table imported() throws Exception {
        Table next = new Table(); next.zone = ZoneId.of(timezone.trim()); next.legacyZone = next.zone;
        if (definitions.length > 16) throw new IllegalArgumentException("rewards");
        for (String definition : definitions) {
            String[] fields = definition.split("\\|", 6);
            if (fields.length != 6) throw new IllegalArgumentException("Expected id|weight|item|metadata|count|SNBT: " + definition);
            String id = fields[0].trim();
            int weight = Integer.parseInt(fields[1].trim()), meta = Integer.parseInt(fields[3].trim()), count = Integer.parseInt(fields[4].trim());
            if (weight <= 0 || weight > 1000000 || meta < 0 || meta > 32767 || count < 1 || count > 4096)
                throw new IllegalArgumentException("Invalid weight (1-1000000), metadata (0-32767), or count (1-4096) for " + id);
            String name = fields[2].trim();
            if (!Item.itemRegistry.containsKey(name)) throw new MissingItemException("Unknown item " + name + " for reward " + id);
            ItemStack template = new ItemStack((Item)Item.itemRegistry.getObject(name), 1, meta);
            if (fields[5].length() > 2048) throw new IllegalArgumentException("Item NBT too large for " + id);
            NBTBase nbt = JsonToNBT.func_150315_a(fields[5]);
            if (!(nbt instanceof NBTTagCompound)) throw new IllegalArgumentException("Item NBT must be a compound for " + id);
            // {} means an ordinary item, so defaults merge with existing untagged stacks.
            if (!((NBTTagCompound)nbt).hasNoTags()) template.setTagCompound((NBTTagCompound)nbt);
            if (template.getMaxStackSize() < 1) throw new IllegalArgumentException("Item cannot be stacked for " + id);
            next.rewards.add(new Reward(id, weight, count, template));
        }
        if (milestoneDefinitions.length > 32) throw new IllegalArgumentException("At most 32 streak milestones are supported");
        for (String definition : milestoneDefinitions) {
            String[] fields = definition.split("\\|", -1);
            if (fields.length != 2) throw new IllegalArgumentException("Expected streakDays|rewardId: " + definition);
            int day = Integer.parseInt(fields[0].trim());
            if (next.milestones.put(day, fields[1].trim()) != null) throw new IllegalArgumentException("milestones");
        }
        return next;
    }
    private static Table copy(Table table) {
        Table next = new Table(); next.zone = table.zone; next.legacyZone = table.legacyZone; next.enabled = table.enabled;
        next.totalWeight = table.totalWeight;
        for (Reward reward : table.rewards) next.rewards.add(new Reward(reward.id, reward.weight, reward.count, reward.template));
        next.milestones.putAll(table.milestones); return next;
    }
    private static Table validate(Table proposed) throws IOException {
        Table next = copy(proposed);
        next.totalWeight = 0;
        if (next.zone == null || next.legacyZone == null) throw new IllegalArgumentException("timezone");
        if (next.rewards.size() > 16) throw new IllegalArgumentException("rewards");
        Set<String> ids = new HashSet<String>();
        for (Reward reward : next.rewards) {
            if (!reward.id.matches("[a-zA-Z0-9_-]{1,32}")) throw new IllegalArgumentException("id: " + reward.id);
            if (!ids.add(reward.id)) throw new IllegalArgumentException("duplicate_id: " + reward.id);
            if (reward.weight < 1 || reward.weight > 1000000) throw new IllegalArgumentException("weight: " + reward.id);
            if (reward.count < 1 || reward.count > 4096) throw new IllegalArgumentException("count: " + reward.id);
            ItemStack stack = reward.template;
            String registry = Item.itemRegistry.getNameForObject(stack.getItem());
            if (registry == null || !Item.itemRegistry.containsKey(registry) || stack.getItemDamage() < 0
                || stack.getItemDamage() > 32767 || stack.getMaxStackSize() < 1) throw new IllegalArgumentException("item: " + reward.id);
            if (stack.hasTagCompound() && stack.getTagCompound().toString().length() > 2048)
                throw new IllegalArgumentException("nbt: " + reward.id);
            next.totalWeight += reward.weight;
        }
        if (next.milestones.size() > 32) throw new IllegalArgumentException("milestones");
        for (java.util.Map.Entry<Integer, String> milestone : next.milestones.entrySet())
            if (milestone.getKey() < 1 || !ids.contains(milestone.getValue()))
                throw new IllegalArgumentException("milestones: " + milestone.getKey() + " -> " + milestone.getValue());
        // Forge's NBT packet length is a signed short. Bound the actual binary previews,
        // including the saved result, bonus and next milestone, before enabling claims.
        // Keep headroom for saved results from a different, previously valid config.
        NBTTagCompound preview = new NBTTagCompound(); NBTTagList wheel = new NBTTagList();
        int largest = 0;
        for (Reward reward : next.rewards) {
            NBTTagCompound entry = reward.preview(); wheel.appendTag(entry);
            largest = Math.max(largest, binarySize(entry));
        }
        preview.setTag("wheel", wheel);
        if ((long)binarySize(preview) + 3L * largest > 16000)
            throw new IllegalArgumentException("packet");
        return next;
    }
    private static NBTTagCompound encode(Table table) {
        NBTTagCompound root = new NBTTagCompound(); root.setInteger("Version", 1);
        root.setString("Timezone", table.zone.getId()); root.setString("LegacyTimezone", table.legacyZone.getId());
        root.setBoolean("Enabled", table.enabled);
        NBTTagList rewards = new NBTTagList(), milestones = new NBTTagList();
        for (Reward reward : table.rewards) {
            NBTTagCompound entry = reward.preview(); entry.setInteger("weight", reward.weight); rewards.appendTag(entry);
        }
        for (java.util.Map.Entry<Integer, String> milestone : table.milestones.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound(); entry.setInteger("days", milestone.getKey());
            entry.setString("reward", milestone.getValue()); milestones.appendTag(entry);
        }
        root.setTag("Rewards", rewards); root.setTag("Milestones", milestones); return root;
    }
    private static Table decode(NBTTagCompound root) {
        if (root.getInteger("Version") != 1)
            throw new IllegalArgumentException("Unsupported saved daily Version: " + root.getInteger("Version"));
        if (!root.hasKey("Timezone", 8) || !root.hasKey("LegacyTimezone", 8)
            || !root.hasKey("Enabled", 1) || !root.hasKey("Rewards", 9) || !root.hasKey("Milestones", 9))
            throw new IllegalArgumentException("Missing or mistyped saved Timezone, LegacyTimezone, Enabled, Rewards or Milestones");
        Table table = new Table(); table.zone = ZoneId.of(root.getString("Timezone"));
        table.legacyZone = ZoneId.of(root.getString("LegacyTimezone")); table.enabled = root.getBoolean("Enabled");
        NBTTagList rewards = (NBTTagList)root.getTag("Rewards"), milestones = (NBTTagList)root.getTag("Milestones");
        if (rewards.tagCount() > 16 || rewards.tagCount() > 0 && rewards.func_150303_d() != 10
            || milestones.tagCount() > 32 || milestones.tagCount() > 0 && milestones.func_150303_d() != 10)
            throw new IllegalArgumentException("Invalid saved Rewards or Milestones list type or size");
        for (int i = 0; i < rewards.tagCount(); i++) {
            NBTTagCompound entry = rewards.getCompoundTagAt(i);
            if (!entry.hasKey("id", 8) || !entry.hasKey("weight", 3) || !entry.hasKey("amount", 3)
                || !entry.hasKey("registry", 8) || !entry.hasKey("item", 10))
                throw new IllegalArgumentException("Missing or mistyped fields in saved reward index " + i);
            String registry = entry.getString("registry");
            if (!Item.itemRegistry.containsKey(registry)) throw new MissingItemException("Unknown item " + registry + " for saved reward " + entry.getString("id"));
            NBTTagCompound raw = entry.getCompoundTag("item");
            if (!raw.hasKey("Damage", 2) || raw.hasKey("tag") && !raw.hasKey("tag", 10))
                throw new IllegalArgumentException("Invalid saved item Damage or tag for reward " + entry.getString("id"));
            ItemStack stack = new ItemStack((Item)Item.itemRegistry.getObject(registry), 1, raw.getShort("Damage"));
            if (raw.hasKey("tag")) stack.setTagCompound((NBTTagCompound)raw.getCompoundTag("tag").copy());
            table.rewards.add(new Reward(entry.getString("id"), entry.getInteger("weight"), entry.getInteger("amount"), stack));
        }
        for (int i = 0; i < milestones.tagCount(); i++) {
            NBTTagCompound entry = milestones.getCompoundTagAt(i);
            if (!entry.hasKey("days", 3) || !entry.hasKey("reward", 8)
                || table.milestones.put(entry.getInteger("days"), entry.getString("reward")) != null)
                throw new IllegalArgumentException("Invalid or duplicate saved milestone index " + i);
        }
        return table;
    }
    private static void save(Table table) throws IOException {
        Files.createDirectories(file.getParent()); Path temporary = file.resolveSibling(file.getFileName() + ".daily.tmp");
        try (FileOutputStream out = new FileOutputStream(temporary.toFile())) { CompressedStreamTools.writeCompressed(encode(table), out); }
        // 1.7.10 writeCompressed closes its output; sync a newly opened descriptor after gzip finishes.
        try (FileOutputStream out = new FileOutputStream(temporary.toFile(), true)) { out.getFD().sync(); }
        // Never delete the previous file or fall back to a non-atomic replacement.
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    private static int binarySize(NBTTagCompound tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) { CompressedStreamTools.write(tag, out); }
        return bytes.size();
    }
}
