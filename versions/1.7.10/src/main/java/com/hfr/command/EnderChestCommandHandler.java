package com.hfr.command;

import java.util.Arrays;
import java.util.List;

import com.hfr.ender.EnderChestInteraction;
import com.hfr.ender.EnderChestRestrictions;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

/** Global server configuration through the existing level-3 /xc command. */
public final class EnderChestCommandHandler {
    public static final String USAGE = "/xc enderchest [show [setting] | set <setting> <value...|clear>]";

    private EnderChestCommandHandler() { }

    public static void execute(ICommandSender sender, String[] args) {
        if (!sender.canCommandSenderUseCommand(3, "xclowder")) throw new CommandException("commands.generic.permission");
        try {
            if (args.length == 0 || ("show".equalsIgnoreCase(args[0]) && args.length <= 2)) {
                if (args.length == 2) show(sender, EnderChestRestrictions.settingName(args[1]));
                else for (String name : EnderChestRestrictions.settingNames()) show(sender, name);
                send(sender, USAGE + ". Changes apply immediately; lists are replaced, clear empties a list.");
                return;
            }
            if (args.length < 3 || !"set".equalsIgnoreCase(args[0])) throw new WrongUsageException(USAGE);
            String name = EnderChestRestrictions.settingName(args[1]);
            boolean wasEnabled = EnderChestRestrictions.isEnabled();
            EnderChestRestrictions.update(name, Arrays.copyOfRange(args, 2, args.length));
            int closed = !wasEnabled && EnderChestRestrictions.isEnabled() ? EnderChestInteraction.closeUnrestrictedContainers() : 0;
            show(sender, name);
            send(sender, "Applied immediately; persistence: hfr.cfg / " + EnderChestRestrictions.CATEGORY
                + ". No restart required."
                + (closed > 0 ? " Closed " + closed + " previously unrestricted ender chest(s); affected players must reopen them." : " No containers require reopening."));
        } catch (IllegalArgumentException e) {
            throw new WrongUsageException(e.getMessage() + " Usage: " + USAGE);
        } catch (IllegalStateException e) {
            throw new CommandException(e.getMessage());
        }
    }

    private static void show(ICommandSender sender, String name) {
        String effective = EnderChestRestrictions.effectiveValue(name);
        String configured = EnderChestRestrictions.configuredValue(name);
        send(sender, "Ender chest " + name + " = " + effective + " (effective; global: all dimensions, factions and players"
            + "; enabled=" + EnderChestRestrictions.isEnabled() + ")"
            + (effective.equals(configured) ? "" : "; configured=" + configured));
    }

    private static void send(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }

    public static List complete(ICommandSender sender, String[] args) {
        if (!sender.canCommandSenderUseCommand(3, "xclowder")) return null;
        if (args.length == 1) return CommandBase.getListOfStringsMatchingLastWord(args, "show", "set");
        if (args.length == 2 && ("show".equalsIgnoreCase(args[0]) || "set".equalsIgnoreCase(args[0])))
            return CommandBase.getListOfStringsMatchingLastWord(args, EnderChestRestrictions.settingNames());
        if (args.length == 3 && "set".equalsIgnoreCase(args[0])) {
            if ("enabled".equalsIgnoreCase(args[1])) return CommandBase.getListOfStringsMatchingLastWord(args, "true", "false");
            if ("mode".equalsIgnoreCase(args[1])) return CommandBase.getListOfStringsMatchingLastWord(args, "blacklist", "whitelist");
            if ("maxTotalItems".equalsIgnoreCase(args[1])) return CommandBase.getListOfStringsMatchingLastWord(args, "-1", "0");
            if ("itemRules".equalsIgnoreCase(args[1]) || "storageDenyList".equalsIgnoreCase(args[1])
                    || "itemQuantityLimits".equalsIgnoreCase(args[1]))
                return CommandBase.getListOfStringsMatchingLastWord(args, "clear");
        }
        return null;
    }
}
