package com.hfr.daily;

import com.hfr.util.XFLog;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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

/** Parsed after all mods have registered their items. File edits require a restart. */
public final class DailyRewardConfig {
    public static final String CATEGORY = "XENOFACTIONS_24_DAILY_REWARDS";
    public static ZoneId zone = ZoneId.of("UTC");
    public static boolean ready;
    public static final List<Reward> rewards = new ArrayList<Reward>();
    public static final TreeMap<Integer, Reward> milestones = new TreeMap<Integer, Reward>();
    public static int totalWeight;
    private static String timezone;
    private static String[] definitions, milestoneDefinitions;

    public static final class Reward {
        public final String id;
        public final int weight, count;
        public final ItemStack template;
        Reward(String id, int weight, int count, ItemStack template) {
            this.id = id; this.weight = weight; this.count = count; this.template = template;
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
    public static void configure(Configuration config) {
        timezone = config.get(CATEGORY, "serverTimezone", "UTC", "IANA server timezone for daily calendar resets, e.g. UTC or America/New_York. Restart required.").getString();
        definitions = config.get(CATEGORY, "rewards", new String[] {
            "bread|50|minecraft:bread|0|8|{}", "iron|30|minecraft:iron_ingot|0|4|{}",
            "gold|15|minecraft:gold_ingot|0|2|{}", "diamond|5|minecraft:diamond|0|1|{}"
        }, "1-16 entries: uniqueId|positiveIntegerWeight|registry:item|metadata|count|itemTagSNBT. Count 1-4096, metadata 0-32767, SNBT <= 2048 characters. Invalid configuration disables new claims.").getStringList();
        milestoneDefinitions = config.get(CATEGORY, "streakMilestones", new String[] {"7|diamond", "30|gold"},
            "Up to 32 entries: consecutiveClaimDays|rewardId. Each day-number milestone is granted once per UUID, across all streaks. References rewards above; weight is ignored for milestone grants.").getStringList();
    }
    public static void validate() {
        ready = false; rewards.clear(); milestones.clear(); totalWeight = 0;
        try {
            zone = ZoneId.of(timezone.trim());
            if (definitions.length < 1 || definitions.length > 16) throw new IllegalArgumentException("rewards requires 1-16 entries");
            Set<String> ids = new HashSet<String>();
            for (String definition : definitions) {
                String[] fields = definition.split("\\|", 6);
                if (fields.length != 6) throw new IllegalArgumentException("Expected id|weight|item|metadata|count|SNBT: " + definition);
                String id = fields[0].trim();
                if (!id.matches("[a-zA-Z0-9_-]{1,32}") || !ids.add(id)) throw new IllegalArgumentException("Invalid or duplicate reward ID: " + id);
                int weight = Integer.parseInt(fields[1].trim()), meta = Integer.parseInt(fields[3].trim()), count = Integer.parseInt(fields[4].trim());
                if (weight <= 0 || weight > 1000000 || meta < 0 || meta > 32767 || count < 1 || count > 4096)
                    throw new IllegalArgumentException("Invalid weight (1-1000000), metadata (0-32767), or count (1-4096) for " + id);
                String name = fields[2].trim();
                if (!Item.itemRegistry.containsKey(name)) throw new IllegalArgumentException("Unknown item " + name + " for " + id);
                ItemStack template = new ItemStack((Item)Item.itemRegistry.getObject(name), 1, meta);
                if (fields[5].length() > 2048) throw new IllegalArgumentException("Item NBT too large for " + id);
                NBTBase nbt = JsonToNBT.func_150315_a(fields[5]);
                if (!(nbt instanceof NBTTagCompound)) throw new IllegalArgumentException("Item NBT must be a compound for " + id);
                // {} means an ordinary item, so defaults merge with existing untagged stacks.
                if (!((NBTTagCompound)nbt).hasNoTags()) template.setTagCompound((NBTTagCompound)nbt);
                if (template.getMaxStackSize() < 1) throw new IllegalArgumentException("Item cannot be stacked for " + id);
                rewards.add(new Reward(id, weight, count, template)); totalWeight += weight;
            }
            if (milestoneDefinitions.length > 32) throw new IllegalArgumentException("At most 32 streak milestones are supported");
            for (String definition : milestoneDefinitions) {
                String[] fields = definition.split("\\|", -1);
                if (fields.length != 2) throw new IllegalArgumentException("Expected streakDays|rewardId: " + definition);
                int day = Integer.parseInt(fields[0].trim()); Reward found = null;
                for (Reward reward : rewards) if (reward.id.equals(fields[1].trim())) found = reward;
                if (day < 1 || found == null || milestones.containsKey(day)) throw new IllegalArgumentException("Invalid, duplicate or unknown streak milestone: " + definition);
                milestones.put(day, found);
            }
            // Forge's NBT packet length is a signed short. Bound the actual binary previews,
            // including the saved result, bonus and next milestone, before enabling claims.
            // Keep headroom for saved results from a different, previously valid config.
            NBTTagCompound preview = new NBTTagCompound(); NBTTagList wheel = new NBTTagList();
            int largest = 0;
            for (Reward reward : rewards) {
                NBTTagCompound entry = reward.preview(); wheel.appendTag(entry);
                largest = Math.max(largest, binarySize(entry));
            }
            preview.setTag("wheel", wheel);
            if ((long)binarySize(preview) + 3L * largest > 16000)
                throw new IllegalArgumentException("Combined binary item previews exceed the 16000-byte network budget; reduce reward NBT or entry count");
            ready = true;
        } catch (Exception e) {
            rewards.clear(); milestones.clear(); totalWeight = 0;
            XFLog.error("Daily rewards configuration rejected in " + CATEGORY + "; new claims disabled. " + e.getMessage(), e);
        }
    }
    private static int binarySize(NBTTagCompound tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) { CompressedStreamTools.write(tag, out); }
        return bytes.size();
    }
}
