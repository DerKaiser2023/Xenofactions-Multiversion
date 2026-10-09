package com.hfr.command;

import com.hfr.data.FactionSavedData;
import com.hfr.server.FactionService;
import com.hfr.server.PlayerLifecycle;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;

/** Brigadier bindings expose only implemented gameplay; service methods own authorization. */
public final class FactionCommands {
    private interface Action { String run(FactionService factions, ServerPlayerEntity player) throws CommandSyntaxException; }
    private static int run(CommandContext<CommandSource> context, Action action) throws CommandSyntaxException {
        try { return reply(context, action.run(FactionService.get(context.getSource().getServer()), context.getSource().getPlayerOrException())); }
        catch (IllegalArgumentException | IllegalStateException invalid) { context.getSource().sendFailure(new StringTextComponent(invalid.getMessage())); return 0; }
    }
    private static int reply(CommandContext<CommandSource> context, String text) { context.getSource().sendSuccess(new StringTextComponent(text.isEmpty() ? "None." : text), false); return 1; }
    private static RequiredArgumentBuilder<CommandSource, String> faction() {
        return Commands.argument("faction", StringArgumentType.greedyString()).suggests((context, builder) -> ISuggestionProvider.suggest(FactionSavedData.get(context.getSource().getServer()).names(), builder));
    }
    private static RequiredArgumentBuilder<CommandSource, String> player(boolean applicants) {
        return Commands.argument("player", StringArgumentType.word()).suggests((context, builder) -> {
            try { return ISuggestionProvider.suggest(FactionService.get(context.getSource().getServer()).names(context.getSource().getPlayerOrException(), applicants), builder); }
            catch (CommandSyntaxException | IllegalArgumentException | IllegalStateException denied) { return builder.buildFuture(); }
        });
    }
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("c")
                .executes(context -> run(context, (factions, player) -> factions.membership(player)))
                .then(Commands.literal("help").executes(context -> reply(context,
                        "/c list, info [faction], create <name>, apply <faction>, comrades, leave; officers: applicants, accept/deny/kick <player>, rename <name>, motd <text>; leader: promote/demote/owner <player>, disband <faction name>. Cities, claims, diplomacy, economy and matches are not ported yet.")))
                .then(Commands.literal("list").executes(context -> reply(context, String.join(", ", FactionSavedData.get(context.getSource().getServer()).names()))))
                .then(Commands.literal("info").executes(context -> run(context, (factions, player) -> factions.membership(player)))
                        .then(faction().executes(context -> {
                            try { return reply(context, FactionSavedData.get(context.getSource().getServer()).describe(StringArgumentType.getString(context, "faction"))); }
                            catch (IllegalArgumentException | IllegalStateException invalid) { context.getSource().sendFailure(new StringTextComponent(invalid.getMessage())); return 0; }
                        })))
                .then(Commands.literal("create").then(faction().executes(context -> run(context, (factions, player) -> factions.create(player, StringArgumentType.getString(context, "faction"))))))
                .then(Commands.literal("apply").then(faction().executes(context -> run(context, (factions, player) -> factions.apply(player, StringArgumentType.getString(context, "faction"))))))
                .then(Commands.literal("rename").then(faction().executes(context -> run(context, (factions, player) -> factions.rename(player, StringArgumentType.getString(context, "faction"))))))
                .then(Commands.literal("disband").then(faction().executes(context -> run(context, (factions, player) -> factions.disband(player, StringArgumentType.getString(context, "faction"))))))
                .then(Commands.literal("comrades").executes(context -> run(context, (factions, player) -> String.join(", ", factions.names(player, false)))))
                .then(Commands.literal("applicants").executes(context -> run(context, (factions, player) -> String.join(", ", factions.names(player, true)))))
                .then(Commands.literal("leave").executes(context -> run(context, (factions, player) -> factions.remove(player, "", true))))
                .then(Commands.literal("accept").then(player(true).executes(context -> run(context, (factions, player) -> factions.application(player, StringArgumentType.getString(context, "player"), true)))))
                .then(Commands.literal("deny").then(player(true).executes(context -> run(context, (factions, player) -> factions.application(player, StringArgumentType.getString(context, "player"), false)))))
                .then(Commands.literal("kick").then(player(false).executes(context -> run(context, (factions, player) -> factions.remove(player, StringArgumentType.getString(context, "player"), false))))));
        for (String action : new String[]{"promote", "demote", "owner"}) dispatcher.register(Commands.literal("c")
                .then(Commands.literal(action).then(player(false).executes(context -> run(context, (factions, player) -> factions.rank(player, StringArgumentType.getString(context, "player"), action))))));
        dispatcher.register(Commands.literal("c").then(Commands.literal("motd").then(Commands.argument("text", StringArgumentType.greedyString())
                .executes(context -> run(context, (factions, player) -> factions.motd(player, StringArgumentType.getString(context, "text")))))));
        dispatcher.register(Commands.literal("clowder").executes(context -> run(context, (factions, player) -> factions.membership(player))).redirect(dispatcher.getRoot().getChild("c")));
        dispatcher.register(Commands.literal("xc").requires(source -> source.hasPermission(3))
                .then(Commands.literal("newplayerprotection").executes(context -> admin(context, "toggle", "")))
                .then(Commands.literal("resetnewplayerprotection").executes(context -> admin(context, "reset", "")))
                .then(Commands.literal("endnewplayerprotection").executes(context -> admin(context, "end", "")))
                .then(Commands.literal("clearcreationcooldown").then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> admin(context, "clear", StringArgumentType.getString(context, "player"))))));
        dispatcher.register(Commands.literal("xc").requires(source -> source.hasPermission(3))
                .then(Commands.literal("resetcreationcooldown").then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> admin(context, "clear", StringArgumentType.getString(context, "player"))))));
    }
    private static int admin(CommandContext<CommandSource> context, String action, String target) {
        try {
            if (action.equals("toggle")) return reply(context, PlayerLifecycle.toggle(context.getSource().getServer()));
            if (action.equals("clear")) return reply(context, PlayerLifecycle.timers(context.getSource().getServer()).clearCooldown(context.getSource().getServer(), target));
            PlayerLifecycle.timers(context.getSource().getServer()).resetProtection(action.equals("end"));
            return reply(context, action.equals("end") ? "Existing player protection timers ended." : "Existing player protection timers reset.");
        } catch (IllegalArgumentException | IllegalStateException invalid) { context.getSource().sendFailure(new StringTextComponent(invalid.getMessage())); return 0; }
    }
    private FactionCommands() { }
}
