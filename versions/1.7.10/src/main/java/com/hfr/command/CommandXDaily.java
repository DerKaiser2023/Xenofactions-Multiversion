package com.hfr.command;

import com.hfr.daily.DailyRewardService;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;

public final class CommandXDaily extends CommandBase {
    public String getCommandName() { return "xdaily"; }
    public List getCommandAliases() { return Collections.singletonList("daily"); }
    public int getRequiredPermissionLevel() { return 0; }
    // 1.7.10's player sender still requires operator status for unknown level-0 commands.
    public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
    public String getCommandUsage(ICommandSender sender) { return "commands.hfr.daily.usage"; }
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 0) throw new WrongUsageException(getCommandUsage(sender));
        DailyRewardService.open(getCommandSenderAsPlayer(sender));
    }
}
