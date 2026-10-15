package com.hfr.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hfr.rewards.ChatRewardConfig;
import com.hfr.rewards.ChatRewardService;
import com.hfr.rewards.ChatRewardStore;
import com.hfr.util.XFLog;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

/** Configuration and history administration through the existing level-3 /xc namespace. */
public final class ChatRewardCommandHandler {
    private static final String[] ACTIONS = { "create", "set", "addheld", "enable", "disable", "list", "show", "remove", "reset" };
    private static final String[] FIELDS = { "phrase", "rewards", "repeatable", "cooldownSeconds", "startsAt", "expiresAt", "totalLimit" };
    private ChatRewardCommandHandler() { }
    private static boolean authorized(ICommandSender sender) { return sender.canCommandSenderUseCommand(3, "xclowder"); }
    public static void execute(ICommandSender sender, String[] args) {
        if (!authorized(sender)) throw new CommandException("commands.generic.permission");
        try {
            if (args.length == 0) throw usage();
            String action = args[0].toLowerCase(Locale.ROOT);
            if ("list".equals(action) && args.length == 1) {
                ChatRewardService.say(sender, "admin.list");
                for (ChatRewardConfig.Phrase phrase : ChatRewardConfig.definitions().values())
                    ChatRewardService.say(sender, "admin.entry", phrase.id, phrase.phrase,
                        new ChatComponentTranslation("chatreward.hfr.admin." + (phrase.enabled ? "enabled" : "disabled")),
                        phrase.rewards.size(), ChatRewardStore.get().total(phrase.id));
                return;
            }
            if (args.length < 2) throw usage();
            String id = args[1];
            if (!id.matches("[a-z0-9_-]{1,48}")) throw new IllegalArgumentException("id");
            if ("reset".equals(action)) {
                if (args.length != 3 || !"confirm".equals(args[2])) throw new WrongUsageException("chatreward.hfr.admin.reset_usage");
                ChatRewardStore.get().reset(id); ChatRewardService.say(sender, "admin.reset", id);
                XFLog.info("[ChatRewards] History reset phrase=" + id + " admin=" + sender.getCommandSenderName());
                return;
            }
            if ("create".equals(action)) {
                if (args.length < 3) throw usage();
                ChatRewardConfig.Phrase phrase = new ChatRewardConfig.Phrase(); phrase.id = id; phrase.phrase = join(args, 2);
                ChatRewardConfig.put(phrase, true); ChatRewardService.say(sender, "admin.created", id); return;
            }
            ChatRewardConfig.Phrase phrase = ChatRewardConfig.definition(id);
            if ("show".equals(action) && args.length == 2) {
                JsonObject json = new JsonParser().parse(ChatRewardConfig.json(phrase)).getAsJsonObject();
                JsonArray rewards = json.remove("rewards").getAsJsonArray();
                ChatRewardService.say(sender, "admin.show", id, json.toString(), ChatRewardStore.get().total(id));
                for (int i = 0; i < rewards.size(); i++) {
                    String reward = rewards.get(i).toString();
                    // Bound each chat component even when binary NBT fills the permitted configuration budget.
                    for (int offset = 0; offset < reward.length(); offset += 1000)
                        ChatRewardService.say(sender, "admin.reward", i + 1, offset / 1000 + 1,
                            (reward.length() + 999) / 1000, reward.substring(offset, Math.min(offset + 1000, reward.length())));
                }
                return;
            }
            if ("remove".equals(action) && args.length == 2) {
                ChatRewardConfig.remove(id); ChatRewardService.say(sender, "admin.removed", id); return;
            }
            if (("enable".equals(action) || "disable".equals(action)) && args.length == 2) phrase.enabled = "enable".equals(action);
            else if ("addheld".equals(action) && args.length == 2) {
                if (!(sender instanceof EntityPlayerMP)) throw new IllegalArgumentException("held");
                ItemStack held = ((EntityPlayerMP)sender).getHeldItem();
                if (held == null || held.stackSize < 1) throw new IllegalArgumentException("held");
                phrase.rewards.add(ChatRewardConfig.capture(held));
            } else if ("set".equals(action) && args.length >= 4) {
                String field = args[2], value = join(args, 3);
                if (!Arrays.asList(FIELDS).contains(field)) throw new IllegalArgumentException("field");
                JsonObject json = new JsonParser().parse(ChatRewardConfig.json(phrase)).getAsJsonObject();
                if ("phrase".equals(field)) json.addProperty(field, value);
                else if ("startsAt".equals(field) || "expiresAt".equals(field)) json.addProperty(field, "clear".equals(value) ? "" : value);
                else if ("repeatable".equals(field)) {
                    if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException("boolean");
                    json.addProperty(field, Boolean.parseBoolean(value));
                } else if ("rewards".equals(field)) json.add(field, new JsonParser().parse(value).getAsJsonArray());
                else json.addProperty(field, Long.parseLong(value));
                phrase = ChatRewardConfig.edited(json);
            } else throw usage();
            ChatRewardConfig.put(phrase, false); ChatRewardService.say(sender, "admin.saved", id);
        } catch (WrongUsageException e) { throw e; }
        catch (IOException e) {
            XFLog.error("Chat reward administration save failed; previous committed data retained", e);
            throw new CommandException("chatreward.hfr.admin.save_failed");
        } catch (RuntimeException e) {
            String reason = e.getMessage();
            if (reason == null || !Arrays.asList("unknown_id", "duplicate_id", "id", "phrase", "duplicate_phrase", "limits", "times", "rewards", "item", "nbt", "held", "field", "boolean", "configuration", "ledger", "world").contains(reason)) reason = "input";
            throw new CommandException("chatreward.hfr.admin.invalid", new ChatComponentTranslation("chatreward.hfr.error." + reason));
        }
    }
    private static WrongUsageException usage() { return new WrongUsageException("chatreward.hfr.admin.usage"); }
    private static String join(String[] args, int start) { return String.join(" ", Arrays.copyOfRange(args, start, args.length)); }
    public static List complete(ICommandSender sender, String[] args) {
        if (!authorized(sender)) return null;
        if (args.length == 1) return CommandBase.getListOfStringsMatchingLastWord(args, ACTIONS);
        if (args.length == 3 && "set".equalsIgnoreCase(args[0])) return CommandBase.getListOfStringsMatchingLastWord(args, FIELDS);
        // Phrase text, identifiers and rewards are deliberately absent from public completion paths.
        return null;
    }
}
