package com.hfr.command;

import java.io.IOException;
import java.util.List;
import com.hfr.config.PlayerPresenceConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentTranslation;

public final class PlayerPresenceCommandHandler {
    public static final String USAGE = "commands.hfr.presence.usage";
    private PlayerPresenceCommandHandler() { }

    public static void execute(ICommandSender sender, String[] args) {
        if (!sender.canCommandSenderUseCommand(3, "xclowder")) throw new CommandException("commands.generic.permission");
        if (args.length == 0 || (args.length == 1 && "show".equalsIgnoreCase(args[0]))) {
            for (String name : PlayerPresenceConfig.SETTINGS)
                sender.addChatMessage(new ChatComponentTranslation("commands.hfr.presence.value", name, PlayerPresenceConfig.value(name)));
            return;
        }
        if (args.length != 3 || !"set".equalsIgnoreCase(args[0])) throw new WrongUsageException(USAGE);
        try {
            PlayerPresenceConfig.update(args[1], args[2]);
        } catch (IllegalArgumentException failure) {
            throw new WrongUsageException(USAGE);
        } catch (IOException | RuntimeException failure) {
            com.hfr.main.MainRegistry.logger.error("Could not save player presence settings", failure);
            throw new CommandException("commands.hfr.presence.save_failed");
        }
        sender.addChatMessage(new ChatComponentTranslation("commands.hfr.presence.saved", args[1], PlayerPresenceConfig.value(args[1])));
    }

    public static List complete(ICommandSender sender, String[] args) {
        if (!sender.canCommandSenderUseCommand(3, "xclowder")) return null;
        if (args.length == 1) return CommandBase.getListOfStringsMatchingLastWord(args, "show", "set");
        if (args.length == 2 && "set".equalsIgnoreCase(args[0])) return CommandBase.getListOfStringsMatchingLastWord(args, PlayerPresenceConfig.SETTINGS);
        if (args.length == 3 && "set".equalsIgnoreCase(args[0]))
            return CommandBase.getListOfStringsMatchingLastWord(args, "afkTimeoutSeconds".equals(args[1]) ? new String[] { "300" } : new String[] { "true", "false" });
        return null;
    }
}
