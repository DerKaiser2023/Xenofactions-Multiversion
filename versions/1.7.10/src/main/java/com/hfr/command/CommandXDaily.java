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
    public String getCommandUsage(ICommandSender sender) { return "commands.hfr.daily.usage"; }
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 0) throw new WrongUsageException(getCommandUsage(sender));
        DailyRewardService.open(getCommandSenderAsPlayer(sender));
    }
}
