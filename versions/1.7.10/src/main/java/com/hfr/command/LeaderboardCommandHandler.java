package com.hfr.command;

import com.hfr.blocks.ModBlocks;
import com.hfr.leaderboard.FactionLeaderboards.Metric;
import com.hfr.tileentity.TileEntityFactionLeaderboard;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/** Runs through /xc, retaining the existing level-3 command permission. */
public final class LeaderboardCommandHandler {
    private LeaderboardCommandHandler() { }

    public static void execute(ICommandSender sender, String[] args) {
        if (!sender.canCommandSenderUseCommand(3, "xclowder")) return;
        if (args.length == 1) {
            sender.addChatMessage(new ChatComponentText("/xc leaderboard give | /xc leaderboard <x> <y> <z> <territory|prestige|money|kills>"));
            return;
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[1])) {
            EntityPlayerMP player = CommandBase.getCommandSenderAsPlayer(sender);
            ItemStack stack = new ItemStack(ModBlocks.factionLeaderboard);
            if (!player.inventory.addItemStackToInventory(stack) && stack.stackSize > 0)
                player.dropPlayerItemWithRandomChoice(stack, false);
            sender.addChatMessage(new ChatComponentText("Place the Faction Leaderboard; sneak-right-click with an empty hand to select its metric."));
            return;
        }
        if (args.length != 5) throw new WrongUsageException("/xc leaderboard <x> <y> <z> <territory|prestige|money|kills>");
        int x = CommandBase.parseInt(sender, args[1]);
        int y = CommandBase.parseIntBounded(sender, args[2], 0, 255);
        int z = CommandBase.parseInt(sender, args[3]);
        Metric metric = Metric.parse(args[4]);
        if (metric == null) throw new WrongUsageException("Metric: territory, prestige, money or kills");
        World world = sender.getEntityWorld();
        if (!world.blockExists(x, y, z)) throw new WrongUsageException("Leaderboard must be in a loaded chunk in your dimension");
        TileEntity raw = world.getTileEntity(x, y, z);
        if (!(raw instanceof TileEntityFactionLeaderboard)) throw new WrongUsageException("No faction leaderboard at these coordinates");
        ((TileEntityFactionLeaderboard) raw).configure(metric);
        sender.addChatMessage(new ChatComponentText("Leaderboard metric: " + metric.title
            + (metric.unavailable.isEmpty() ? "" : ". " + metric.unavailable)));
    }
}
