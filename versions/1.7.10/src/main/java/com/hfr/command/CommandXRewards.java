package com.hfr.command;

import com.hfr.rewards.ChatRewardService;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;

/** Public collection only; this command cannot inspect definitions or supply reward data. */
public final class CommandXRewards extends CommandBase {
    public String getCommandName() { return "xrewards"; }
    public int getRequiredPermissionLevel() { return 0; }
    public String getCommandUsage(ICommandSender sender) { return "chatreward.hfr.collect_usage"; }
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1 || !"collect".equalsIgnoreCase(args[0])) throw new WrongUsageException(getCommandUsage(sender));
        ChatRewardService.collect(getCommandSenderAsPlayer(sender));
    }
}
