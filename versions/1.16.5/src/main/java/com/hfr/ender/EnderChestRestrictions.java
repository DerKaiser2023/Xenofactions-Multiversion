package com.hfr.ender;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.hfr.main.Xenofactions;
import com.hfr.config.EnderChestConfig;

import net.minecraftforge.fml.ModList;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;


/** Server-side rules for deposits into the player's vanilla ender inventory. */
public final class EnderChestRestrictions {

    public static final String CATEGORY = "XENOFACTIONS_21_ENDER_CHEST";
    private static final String[] DEFAULT_STORAGE = {
        "hbm:item.plastic_bag", "hbm:item.containment_box", "hbm:item.kit_toolbox", "hbm:item.kit_custom",
        "hbm:tile.crate_iron", "hbm:tile.crate_steel", "hbm:tile.crate_desh",
        "hbm:tile.crate_tungsten", "hbm:tile.crate_template", "hbm:tile.safe"
    };

    private static boolean enabled;
    private static boolean whitelist;
    private static int maxTotal = -1;
    private static List<Rule> items = new ArrayList<Rule>();
    private static List<Rule> storage = new ArrayList<Rule>();
    private static List<Limit> limits = new ArrayList<Limit>();

    private EnderChestRestrictions() { }

    public static boolean isEnabled() { return enabled; }

    public static void load() {
        enabled = EnderChestConfig.ENABLED.get();
        whitelist = EnderChestConfig.MODE.get().equals("whitelist");
        items = parseRules(EnderChestConfig.ITEMS.get().toArray(new String[0]), "itemRules");
        storage = parseRules(EnderChestConfig.STORAGE.get().toArray(new String[0]), "storageDenyList");
        maxTotal = EnderChestConfig.TOTAL.get();
        limits = parseLimits(EnderChestConfig.LIMITS.get().toArray(new String[0]));
        validateRegistry();
    }

    /** Run after all mods have registered their items, including optional storage mods. */
    public static void validateRegistry() {
        validateRules(items, "itemRules");
        validateRules(storage, "storageDenyList");
        for (Iterator<Limit> it = limits.iterator(); it.hasNext();) {
            Limit limit = it.next();
            if (!limit.rule.isRegistered()) {
                warn("itemQuantityLimits entry '" + limit.rule + "' is not a registered item; ignoring it");
                it.remove();
            }
        }
    }

    private static void validateRules(List<Rule> rules, String setting) {
        for (Iterator<Rule> it = rules.iterator(); it.hasNext();) {
            Rule rule = it.next();
            if (rule.isRegistered()) continue;
            if (!("storageDenyList".equals(setting) && !ModList.get().isLoaded("hbm") && isDefaultStorage(rule.name)))
                warn(setting + " entry '" + rule + "' is not a registered item; ignoring it");
            it.remove();
        }
    }

    private static boolean isDefaultStorage(String name) {
        for (String known : DEFAULT_STORAGE) if (known.equals(name)) return true;
        return false;
    }

    private static List<Rule> parseRules(String[] entries, String setting) {
        List<Rule> out = new ArrayList<Rule>();
        for (String entry : entries) {
            Rule rule = Rule.parse(entry);
            if (rule == null) warn(setting + " entry '" + entry + "' is invalid; expected modid:registry_name[@metadata]");
            else out.add(rule);
        }
        return out;
    }

    private static List<Limit> parseLimits(String[] entries) {
        List<Limit> out = new ArrayList<Limit>();
        for (String entry : entries) {
            String[] parts = entry == null ? new String[0] : entry.split("=", -1);
            Rule rule = parts.length == 2 ? Rule.parse(parts[0]) : null;
            int amount = parts.length == 2 ? quantity(parts[1].trim(), "itemQuantityLimits entry '" + entry + "'") : -2;
            if (rule == null || amount == -2) {
                warn("itemQuantityLimits entry '" + entry + "' is invalid; expected modid:registry_name[@metadata]=quantity");
            } else if (amount >= 0) {
                out.add(new Limit(rule, amount));
            }
        }
        return out;
    }

    private static int quantity(String value, String setting) {
        try {
            int n = Integer.parseInt(value);
            if (n >= -1) return n;
        } catch (NumberFormatException ignored) { }
        warn(setting + " has invalid quantity '" + value + "'; using -1 (unlimited)");
        return -2;
    }

    public static String rejection(ItemStack[] openingContents, IInventory after) {
        if (!enabled) return null;
        int oldTotal = total(openingContents);
        int newTotal = total(after);
        boolean newItem = hasNewItems(openingContents, after);

        for (int i = 0; i < after.getContainerSize(); i++) {
            ItemStack stack = after.getItem(i);
            if (stack == null || stack.isEmpty() || countExact(stack, after) <= countExact(stack, openingContents)) continue;
            if (matches(storage, stack)) return "Portable storage is blocked from the ender chest.";
            if (whitelist && !matches(items, stack)) return "This item is not on the ender chest whitelist.";
            if (!whitelist && matches(items, stack)) return "This item is on the ender chest blacklist.";
        }

        if (maxTotal >= 0 && newItem && (oldTotal > maxTotal || newTotal > maxTotal))
            return "Ender chest limit: maximum " + maxTotal + " items.";

        for (Limit limit : limits) {
            int oldCount = count(limit.rule, openingContents);
            int newCount = count(limit.rule, after);
            if (newCount > limit.max && newCount > oldCount)
                return "Ender chest item limit: " + limit.rule + " may have at most " + limit.max + ".";
            if (oldCount > limit.max && newItem)
                return "Ender chest item limit: withdraw " + limit.rule + " before depositing.";
        }
        return null;
    }

    private static boolean hasNewItems(ItemStack[] before, IInventory after) {
        for (int i = 0; i < after.getContainerSize(); i++) {
            ItemStack stack = after.getItem(i);
            if (stack != null && !stack.isEmpty() && countExact(stack, after) > countExact(stack, before)) return true;
        }
        return false;
    }

    private static int countExact(ItemStack target, ItemStack[] stacks) {
        int n = 0;
        for (ItemStack stack : stacks) if (sameStack(target, stack)) n += stack.getCount();
        return n;
    }

    private static int countExact(ItemStack target, IInventory inventory) {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (sameStack(target, stack)) n += stack.getCount();
        }
        return n;
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        return b != null && !b.isEmpty() && a.getItem() == b.getItem() && a.getDamageValue() == b.getDamageValue()
            && ItemStack.tagMatches(a, b);
    }

    private static int total(ItemStack[] stacks) {
        int n = 0; for (ItemStack stack : stacks) if (stack != null && !stack.isEmpty()) n += stack.getCount(); return n;
    }

    private static int total(IInventory inventory) {
        int n = 0; for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i); if (stack != null && !stack.isEmpty()) n += stack.getCount();
        } return n;
    }

    private static int count(Rule rule, ItemStack[] stacks) {
        int n = 0; for (ItemStack stack : stacks) if (rule.matches(stack)) n += stack.getCount(); return n;
    }

    private static int count(Rule rule, IInventory inventory) {
        int n = 0; for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i); if (rule.matches(stack)) n += stack.getCount();
        } return n;
    }

    private static boolean matches(List<Rule> rules, ItemStack stack) {
        for (Rule rule : rules) if (rule.matches(stack)) return true;
        return false;
    }

    private static void warn(String message) {
        if (Xenofactions.LOGGER != null) Xenofactions.LOGGER.warn("Xenofactions ender chest config: " + message);
    }

    private static final class Limit {
        final Rule rule; final int max;
        Limit(Rule rule, int max) { this.rule = rule; this.max = max; }
    }

    private static final class Rule {
        final String name; final int metadata;
        Rule(String name, int metadata) { this.name = name; this.metadata = metadata; }

        static Rule parse(String raw) {
            if (raw == null) return null;
            String value = raw.trim();
            int at = value.indexOf('@');
            String name = at < 0 ? value : value.substring(0, at);
            if (!name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return null;
            if (at < 0) return new Rule(name, -1);
            try {
                int meta = Integer.parseInt(value.substring(at + 1));
                warn("Metadata selector is unsupported on 1.16.5: " + value + "; use the native item registry name.");
                return null;
            } catch (NumberFormatException ignored) { return null; }
        }

        boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;
            Item item = stack.getItem();
            String registered = item == null ? null : String.valueOf(item.getRegistryName());
            return name.equals(registered) && (metadata < 0 || metadata == stack.getDamageValue());
        }

        boolean isRegistered() {
            Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.util.ResourceLocation(name));
            return item != null && name.equals(String.valueOf(item.getRegistryName()));
        }

        @Override public String toString() { return name + (metadata < 0 ? "" : "@" + metadata); }
    }
}
