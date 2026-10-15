package com.hfr.command;

import com.hfr.daily.DailyRewardConfig;
import com.hfr.daily.DailyRewardConfig.Reward;
import com.hfr.daily.DailyRewardConfig.Table;
import com.hfr.util.XFLog;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

/** Live wheel management through the existing level-3 /xc permission boundary. */
public final class DailyRewardCommandHandler {
    private static final String[] ACTIONS = { "list", "show", "addheld", "setheld", "setweight", "setcount", "remove",
        "milestone", "enable", "disable", "settings", "timezone", "help" };
    private DailyRewardCommandHandler() { }
    private static boolean authorized(ICommandSender sender) { return sender.canCommandSenderUseCommand(3, "xclowder"); }
    public static void say(ICommandSender sender, String key, Object... args) {
        sender.addChatMessage(new ChatComponentTranslation("daily.hfr.admin." + key, args));
    }
    private static void entry(ICommandSender sender, Reward reward, Table table) {
        say(sender, "entry", reward.id, reward.template.getDisplayName(),
            Item.itemRegistry.getNameForObject(reward.template.getItem()), reward.template.getItemDamage(),
            reward.count, reward.weight, String.format(Locale.ROOT, "%.8f%%", 100D * reward.weight / table.totalWeight));
    }
    public static void execute(ICommandSender sender, String[] args) {
        if (!authorized(sender)) throw new CommandException("commands.generic.permission");
        try {
            if (args.length == 0 || args.length == 1 && "help".equalsIgnoreCase(args[0])) {
                say(sender, "help_rewards"); say(sender, "help_milestones"); say(sender, "help_settings"); return;
            }
            String action = args[0].toLowerCase(Locale.ROOT);
            Table next = DailyRewardConfig.definitions();
            if ("list".equals(action) && args.length == 1) {
                say(sender, "list", next.rewards.size(), next.totalWeight);
                for (Reward reward : next.rewards) entry(sender, reward, next);
                if (next.rewards.isEmpty()) say(sender, "empty");
                return;
            }
            if ("settings".equals(action) && args.length == 1) {
                say(sender, "settings", new ChatComponentTranslation("daily.hfr.admin." + (next.enabled ? "enabled" : "disabled")),
                    new ChatComponentTranslation("daily.hfr.admin." + (DailyRewardConfig.ready() ? "enabled" : "disabled")),
                    next.zone.getId(), next.rewards.size(), next.totalWeight, next.milestones.size());
                return;
            }
            if (("enable".equals(action) || "disable".equals(action)) && args.length == 1) next.enabled = "enable".equals(action);
            else if ("timezone".equals(action) && args.length == 2) {
                try { next.zone = ZoneId.of(args[1]); }
                catch (DateTimeException e) { throw new IllegalArgumentException("timezone"); }
            } else if ("milestone".equals(action)) {
                if (args.length == 2 && "list".equalsIgnoreCase(args[1])) {
                    say(sender, "milestones", next.milestones.size());
                    for (Map.Entry<Integer, String> milestone : next.milestones.entrySet())
                        say(sender, "milestone", milestone.getKey(), milestone.getValue());
                    return;
                }
                if (args.length == 4 && "set".equalsIgnoreCase(args[1])) {
                    int days = positiveDays(args[2]); next.reward(args[3]); next.milestones.put(days, args[3]);
                } else if (args.length == 3 && "remove".equalsIgnoreCase(args[1])) {
                    if (next.milestones.remove(positiveDays(args[2])) == null) throw new IllegalArgumentException("unknown_milestone");
                } else throw usage();
            } else {
                if (args.length < 2) throw usage();
                String id = args[1];
                if (!id.matches("[a-zA-Z0-9_-]{1,32}")) throw new IllegalArgumentException("id");
                if ("addheld".equals(action) && (args.length == 3 || args.length == 4)) {
                    for (Reward reward : next.rewards) if (reward.id.equals(id)) throw new IllegalArgumentException("duplicate_id");
                    ItemStack held = held(sender);
                    next.rewards.add(DailyRewardConfig.capture(id, Integer.parseInt(args[2]),
                        args.length == 4 ? Integer.parseInt(args[3]) : held.stackSize, held));
                } else {
                    Reward reward = next.reward(id);
                    if ("show".equals(action) && args.length == 2) {
                        entry(sender, reward, next);
                        for (Map.Entry<Integer, String> milestone : next.milestones.entrySet())
                            if (milestone.getValue().equals(id)) say(sender, "milestone", milestone.getKey(), id);
                        return;
                    }
                    if ("remove".equals(action) && args.length == 2) {
                        boolean referenced = false;
                        for (Map.Entry<Integer, String> milestone : next.milestones.entrySet()) {
                            if (milestone.getValue().equals(id)) { say(sender, "milestone", milestone.getKey(), id); referenced = true; }
                        }
                        if (referenced) throw new IllegalArgumentException("referenced");
                        next.rewards.remove(reward);
                    } else if ("setheld".equals(action) && (args.length == 2 || args.length == 3)) {
                        ItemStack held = held(sender);
                        next.rewards.set(next.rewards.indexOf(reward), DailyRewardConfig.capture(id, reward.weight,
                            args.length == 3 ? Integer.parseInt(args[2]) : held.stackSize, held));
                    } else if ("setweight".equals(action) && args.length == 3) {
                        next.rewards.set(next.rewards.indexOf(reward), new Reward(id, Integer.parseInt(args[2]), reward.count, reward.template));
                    } else if ("setcount".equals(action) && args.length == 3) {
                        next.rewards.set(next.rewards.indexOf(reward), new Reward(id, reward.weight, Integer.parseInt(args[2]), reward.template));
                    } else throw usage();
                }
            }
            DailyRewardConfig.put(next); say(sender, "saved");
            if (next.rewards.isEmpty()) say(sender, "empty");
        } catch (WrongUsageException e) { throw e; }
        catch (IOException e) {
            XFLog.error("Daily administration save failed; previous committed definitions retained", e);
            throw new CommandException("daily.hfr.admin.save_failed");
        } catch (RuntimeException e) {
            String reason = e instanceof NumberFormatException ? "number" : e.getMessage();
            if (!Arrays.asList("unknown_id", "duplicate_id", "id", "weight", "count", "item", "nbt", "held", "player", "rewards",
                "milestones", "days", "unknown_milestone", "referenced", "packet", "timezone", "configuration", "number").contains(reason)) reason = "input";
            throw new CommandException("daily.hfr.admin.invalid", new ChatComponentTranslation("daily.hfr.error." + reason));
        }
    }
    private static ItemStack held(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) throw new IllegalArgumentException("player");
        ItemStack stack = ((EntityPlayerMP)sender).getHeldItem();
        if (stack == null || stack.stackSize < 1) throw new IllegalArgumentException("held");
        return stack;
    }
    private static int positiveDays(String value) {
        int days = Integer.parseInt(value); if (days < 1) throw new IllegalArgumentException("days"); return days;
    }
    private static WrongUsageException usage() { return new WrongUsageException("daily.hfr.admin.usage"); }
    public static List complete(ICommandSender sender, String[] args) {
        if (!authorized(sender)) return null;
        if (args.length == 1) return CommandBase.getListOfStringsMatchingLastWord(args, ACTIONS);
        if (args.length == 2 && "milestone".equalsIgnoreCase(args[0]))
            return CommandBase.getListOfStringsMatchingLastWord(args, "list", "set", "remove");
        if (args.length == 2 && "timezone".equalsIgnoreCase(args[0]))
            return CommandBase.getListOfStringsFromIterableMatchingLastWord(args, ZoneId.getAvailableZoneIds());
        try {
            Table table = DailyRewardConfig.definitions();
            if (args.length == 2 && Arrays.asList("show", "setheld", "setweight", "setcount", "remove").contains(args[0].toLowerCase(Locale.ROOT))
                || args.length == 4 && "milestone".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
                String[] ids = new String[table.rewards.size()];
                for (int i = 0; i < ids.length; i++) ids[i] = table.rewards.get(i).id;
                return CommandBase.getListOfStringsMatchingLastWord(args, ids);
            }
            if (args.length == 3 && "milestone".equalsIgnoreCase(args[0]) && "remove".equalsIgnoreCase(args[1])) {
                String[] days = new String[table.milestones.size()]; int i = 0;
                for (Integer day : table.milestones.keySet()) days[i++] = day.toString();
                return CommandBase.getListOfStringsMatchingLastWord(args, days);
            }
        } catch (IllegalStateException e) { return null; }
        return null;
    }
}
