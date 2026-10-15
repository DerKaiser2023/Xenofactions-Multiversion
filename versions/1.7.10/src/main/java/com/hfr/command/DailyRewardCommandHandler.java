package com.hfr.command;

import com.hfr.daily.DailyRewardConfig;
import com.hfr.daily.DailyRewardConfig.Reward;
import com.hfr.daily.DailyRewardConfig.Table;
import com.hfr.daily.DailyRewardService;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

/** Live wheel management through the existing level-3 /xc permission boundary. */
public final class DailyRewardCommandHandler {
    private static final String[] ACTIONS = { "help", "list", "show", "status", "reload", "reset", "addheld", "setheld",
        "setweight", "setcount", "remove", "milestone", "enable", "disable", "settings", "timezone" };
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
    private static void help(ICommandSender sender) {
        say(sender, "help");
        for (String action : ACTIONS) {
            if ("milestone".equals(action)) {
                say(sender, "usage.milestone_list"); say(sender, "usage.milestone_set"); say(sender, "usage.milestone_remove");
            } else say(sender, "usage." + action);
        }
        say(sender, "help_ids"); say(sender, "help_weights"); say(sender, "help_held"); say(sender, "help_example");
    }
    private static void failure(ICommandSender sender, String key, DailyRewardConfig.Failure failure) {
        say(sender, key, new ChatComponentTranslation("daily.hfr.admin.stage." + failure.stage), failure.path, failure.details());
        say(sender, "recovery." + failure.reason);
    }
    private static void status(ICommandSender sender) {
        say(sender, "status", new ChatComponentTranslation("daily.hfr.admin.state." + DailyRewardConfig.status()), DailyRewardConfig.dataPath());
        say(sender, "import_path", DailyRewardConfig.importPath());
        if (!"unavailable".equals(DailyRewardConfig.status())) settings(sender, DailyRewardConfig.definitions());
        DailyRewardConfig.Failure latest = DailyRewardConfig.lastFailure(), first = DailyRewardConfig.firstFailure();
        if (latest != null) failure(sender, "load_failure", latest);
        else say(sender, "no_failure");
        if (first != null && first != latest) failure(sender, "original_failure", first);
    }
    private static void settings(ICommandSender sender, Table table) {
        say(sender, "settings", new ChatComponentTranslation("daily.hfr.admin." + (table.enabled ? "enabled" : "disabled")),
            new ChatComponentTranslation("daily.hfr.admin." + (DailyRewardConfig.ready() ? "enabled" : "disabled")),
            table.zone.getId(), table.rewards.size(), table.totalWeight, table.milestones.size());
    }
    /** Check action and arity before any definition or identifier lookup. */
    private static String validateSyntax(String[] args) {
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!Arrays.asList(ACTIONS).contains(action)) throw new CommandException("daily.hfr.admin.unknown_action", args[0]);
        int min = 1, max = 1;
        if (Arrays.asList("show", "remove", "timezone", "reset").contains(action)) min = max = 2;
        else if ("addheld".equals(action)) { min = 3; max = 4; }
        else if ("setheld".equals(action)) { min = 2; max = 3; }
        else if ("setweight".equals(action) || "setcount".equals(action)) min = max = 3;
        else if ("milestone".equals(action)) {
            if (args.length < 2) throw usage("milestone");
            String sub = args[1].toLowerCase(Locale.ROOT);
            if ("list".equals(sub)) min = max = 2;
            else if ("set".equals(sub)) min = max = 4;
            else if ("remove".equals(sub)) min = max = 3;
            else throw usage("milestone");
            if (args.length != min) throw usage("milestone_" + sub);
        }
        if (args.length != 0 && (args.length < min || args.length > max)) throw usage(action);
        return action;
    }
    public static void execute(ICommandSender sender, String[] args) {
        if (!authorized(sender)) throw new CommandException("commands.generic.permission");
        String action = validateSyntax(args);
        if ("help".equals(action)) { help(sender); return; }
        if ("status".equals(action)) { status(sender); return; }
        if ("reload".equals(action)) {
            say(sender, DailyRewardConfig.reload() ? "reloaded" : "reload_failed"); status(sender); return;
        }
        try {
            if ("reset".equals(action)) {
                // Name lookup only: selectors must never resolve to an unintended player.
                EntityPlayerMP target = MinecraftServer.getServer().getConfigurationManager().func_152612_a(args[1]);
                if (target == null) throw new CommandException("daily.hfr.admin.unknown_player", args[1]);
                int pending = DailyRewardService.reset(sender.getCommandSenderName(), target);
                say(sender, "reset", target.getCommandSenderName(), target.getUniqueID(), pending);
                if (pending > 0) say(sender, "reset_pending");
                return;
            }
            Table next = DailyRewardConfig.definitions();
            if ("list".equals(action)) {
                say(sender, "list", next.rewards.size(), next.totalWeight);
                for (Reward reward : next.rewards) entry(sender, reward, next);
                if (next.rewards.isEmpty()) say(sender, "empty");
                return;
            }
            if ("settings".equals(action)) {
                settings(sender, next);
                return;
            }
            String changedId = null;
            if ("enable".equals(action) || "disable".equals(action)) next.enabled = "enable".equals(action);
            else if ("timezone".equals(action)) {
                try { next.zone = ZoneId.of(args[1]); }
                catch (DateTimeException e) { throw new IllegalArgumentException("timezone"); }
            } else if ("milestone".equals(action)) {
                if ("list".equalsIgnoreCase(args[1])) {
                    say(sender, "milestones", next.milestones.size());
                    for (Map.Entry<Integer, String> milestone : next.milestones.entrySet())
                        say(sender, "milestone", milestone.getKey(), milestone.getValue());
                    return;
                }
                int days = positiveDays(args[2]);
                if ("set".equalsIgnoreCase(args[1])) { next.reward(args[3]); next.milestones.put(days, args[3]); }
                else if (next.milestones.remove(days) == null) throw new IllegalArgumentException("unknown_milestone");
            } else {
                String id = args[1];
                if (!id.matches("[a-zA-Z0-9_-]{1,32}")) throw new IllegalArgumentException("id");
                if ("addheld".equals(action)) {
                    for (Reward reward : next.rewards) if (reward.id.equals(id)) throw new IllegalArgumentException("duplicate_id");
                    ItemStack held = held(sender);
                    next.rewards.add(DailyRewardConfig.capture(id, Integer.parseInt(args[2]),
                        args.length == 4 ? Integer.parseInt(args[3]) : held.stackSize, held));
                    changedId = id;
                } else {
                    Reward reward = next.reward(id);
                    if ("show".equals(action)) {
                        entry(sender, reward, next);
                        for (Map.Entry<Integer, String> milestone : next.milestones.entrySet())
                            if (milestone.getValue().equals(id)) say(sender, "milestone", milestone.getKey(), id);
                        return;
                    }
                    if ("remove".equals(action)) {
                        boolean referenced = false;
                        for (Map.Entry<Integer, String> milestone : next.milestones.entrySet()) {
                            if (milestone.getValue().equals(id)) { say(sender, "milestone", milestone.getKey(), id); referenced = true; }
                        }
                        if (referenced) throw new IllegalArgumentException("referenced");
                        next.rewards.remove(reward);
                    } else if ("setheld".equals(action)) {
                        ItemStack held = held(sender);
                        next.rewards.set(next.rewards.indexOf(reward), DailyRewardConfig.capture(id, reward.weight,
                            args.length == 3 ? Integer.parseInt(args[2]) : held.stackSize, held));
                        changedId = id;
                    } else if ("setweight".equals(action)) {
                        next.rewards.set(next.rewards.indexOf(reward), new Reward(id, Integer.parseInt(args[2]), reward.count, reward.template));
                    } else if ("setcount".equals(action)) {
                        next.rewards.set(next.rewards.indexOf(reward), new Reward(id, reward.weight, Integer.parseInt(args[2]), reward.template));
                    }
                }
            }
            DailyRewardConfig.put(next); say(sender, "saved");
            if (changedId != null) {
                Table committed = DailyRewardConfig.definitions(); entry(sender, committed.reward(changedId), committed);
            }
            if (next.rewards.isEmpty()) say(sender, "empty");
        } catch (CommandException e) { throw e; }
        catch (IOException e) {
            XFLog.error("Daily administration save failed; previous committed state retained", e);
            throw new CommandException("daily.hfr.admin." + ("reset".equals(action) ? "reset_save_failed" : "save_failed"));
        } catch (IllegalStateException e) {
            if ("configuration".equals(e.getMessage())) throw new CommandException("daily.hfr.admin.unavailable");
            XFLog.error("Daily player reset failed; previous state retained", e);
            throw new CommandException("daily.hfr.admin.reset_unavailable");
        } catch (RuntimeException e) {
            String reason = e instanceof NumberFormatException ? "number" : e.getMessage();
            if (reason != null && reason.contains(":")) reason = reason.substring(0, reason.indexOf(':'));
            if (!Arrays.asList("unknown_id", "duplicate_id", "id", "weight", "count", "item", "nbt", "held", "player", "rewards",
                "milestones", "days", "unknown_milestone", "referenced", "packet", "timezone", "number").contains(reason)) {
                XFLog.error("Unexpected daily administration failure during " + action, e); reason = "input";
            }
            throw new CommandException("daily.hfr.admin.invalid", new ChatComponentTranslation("daily.hfr.error." + reason));
        }
    }
    private static ItemStack held(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) throw new IllegalArgumentException("player");
        ItemStack stack = ((EntityPlayerMP)sender).getHeldItem();
        if (stack == null || stack.stackSize < 1) throw new IllegalArgumentException("held");
        return stack.copy();
    }
    private static int positiveDays(String value) {
        int days = Integer.parseInt(value); if (days < 1) throw new IllegalArgumentException("days"); return days;
    }
    private static WrongUsageException usage(String action) { return new WrongUsageException("daily.hfr.admin.usage." + action); }
    public static List complete(ICommandSender sender, String[] args) {
        if (!authorized(sender)) return null;
        if (args.length == 1) return CommandBase.getListOfStringsMatchingLastWord(args, ACTIONS);
        if (args.length == 2 && "reset".equalsIgnoreCase(args[0]))
            return CommandBase.getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
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
