package com.hfr.command;

import com.hfr.BuildVersion;
import com.hfr.data.FactionSavedData;
import com.hfr.data.MapSavedData;
import com.hfr.server.EditorSessions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;

/** Only implemented commands are offered; unsupported gameplay is not registered as a stub. */
public final class FoundationCommands {
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        FactionCommands.register(dispatcher);
        GameplayCommands.register(dispatcher);
        dispatcher.register(Commands.literal("xc").requires(source -> source.hasPermission(3))
                .then(Commands.literal("foundation").executes(context -> reply(context, "Xenofactions " + BuildVersion.VERSION
                        + ": native factions, cities, capitals, homes, territory protection, diplomacy/war, prestige and chat; map-bounds editing. Matches, kits, shops and map integrations are pending.")))
                .then(Commands.literal("cities").executes(context -> { for (String city : FactionSavedData.get(context.getSource().getServer()).cities()) reply(context, city); return 1; })));
        dispatcher.register(Commands.literal("xclowder").requires(source -> source.hasPermission(3)).redirect(dispatcher.getRoot().getChild("xc")));
        dispatcher.register(Commands.literal("tdm").requires(source -> source.hasPermission(4))
                .then(Commands.literal("editor").then(Commands.literal("gui").executes(context -> open(context, ""))
                        .then(Commands.argument("map", StringArgumentType.word()).executes(context -> open(context, StringArgumentType.getString(context, "map"))))))
                .then(Commands.literal("map")
                        .then(Commands.literal("list").executes(context -> reply(context, String.join(", ", MapSavedData.get(context.getSource().getServer()).names()))))
                        .then(Commands.literal("create").then(Commands.argument("map", StringArgumentType.word()).executes(context -> {
                            try { MapSavedData.get(context.getSource().getServer()).create(StringArgumentType.getString(context, "map")); return reply(context, "Map created with voting disabled. Match gameplay is not ported."); }
                            catch (IllegalArgumentException | IllegalStateException invalid) { context.getSource().sendFailure(new StringTextComponent(invalid.getMessage())); return 0; }
                        })))));
    }
    private static int open(CommandContext<CommandSource> context, String map) throws CommandSyntaxException {
        try { EditorSessions.open(context.getSource().getPlayerOrException(), map); return 1; }
        catch (IllegalArgumentException | IllegalStateException invalid) { context.getSource().sendFailure(new StringTextComponent(invalid.getMessage())); return 0; }
    }
    private static int reply(CommandContext<CommandSource> context, String text) { context.getSource().sendSuccess(new StringTextComponent(text.isEmpty() ? "No entries." : text), false); return 1; }
    private FoundationCommands() { }
}
