package com.hfr.command;

import com.hfr.server.FactionEvents;
import com.hfr.server.FactionGameplay;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;

/** Native command bindings retain reference command names; every mutation checks fresh server authority. */
public final class GameplayCommands {
    private interface Action { String run(FactionGameplay gameplay, ServerPlayerEntity player) throws CommandSyntaxException; }
    private interface AdminAction { String run(FactionGameplay gameplay); }
    private interface ModerationAction { String run() throws CommandSyntaxException; }
    private static int moderation(CommandContext<CommandSource> c, ModerationAction action) throws CommandSyntaxException {
        try { c.getSource().sendSuccess(new StringTextComponent(action.run()), false); return 1; }
        catch (IllegalArgumentException | IllegalStateException invalid) { c.getSource().sendFailure(new StringTextComponent(invalid.getMessage())); return 0; }
    }
    private static int admin(CommandContext<CommandSource> c, AdminAction action) {
        try { c.getSource().sendSuccess(new StringTextComponent(action.run(FactionGameplay.get(c.getSource().getServer()))), true); return 1; }
        catch (IllegalArgumentException | IllegalStateException invalid) { c.getSource().sendFailure(new StringTextComponent(invalid.getMessage())); return 0; }
    }
    private static int run(CommandContext<CommandSource> c, Action action) throws CommandSyntaxException {
        try { String result = action.run(FactionGameplay.get(c.getSource().getServer()), c.getSource().getPlayerOrException()); c.getSource().sendSuccess(new StringTextComponent(result), false); return 1; }
        catch (IllegalArgumentException | IllegalStateException invalid) { c.getSource().sendFailure(new StringTextComponent(invalid.getMessage())); return 0; }
    }
    private static RequiredArgumentBuilder<CommandSource, String> name(String argument) { return Commands.argument(argument, StringArgumentType.greedyString()); }
    private static RequiredArgumentBuilder<CommandSource, String> faction() { return name("faction").suggests((c, b) -> ISuggestionProvider.suggest(com.hfr.data.FactionSavedData.get(c.getSource().getServer()).names(), b)); }
    private static int help(CommandContext<CommandSource> c, int page, boolean admin) {
        String[] text = admin ? new String[]{
                "/xc forcejoin <faction>, forcekick <player>, forcedisband <faction>, forcerename <name>, hijack, deletedata; rename <name>, disband <own faction>.",
                "/xc setclaim <safe|war|wild> <s|c> <radius>; editor <status|select safezone/warzone/wilderness|point a/b|commit|cancel>; addprestige <faction> <amount>.",
                "/xc warenable, wardisable, skipwarcooldowns, skipwarcooldown, ignorewarcooldowncheck, ignorewaronlinecheck, ignorewarstatecheck, enablelegacywar; resetbuildgrace/endbuildgrace <faction>.",
                "/xc clearcreationcooldown <player-or-uuid>, factiontimeoutcreationreset <player>, newplayerprotection, resetnewplayerprotection, endnewplayerprotection, cities, foundation."
        } : new String[]{
                "/c create <name>, info [faction], list, comrades, leave, disband <own faction>; /cc [public|faction|alliance|mute|unmute].",
                "/c apply <faction>, applicants, accept/deny/kick <player>, promote/demote/owner <player>.",
                "/c rename <name>, motd <text>, color <hex>, flag <pattern>, listflags, gracebuild; permissions [ally|neutral] <build|destroy|container|interact|switch> <true|false>.",
                "/c claim <city name>, city upgrade/cancelmove/recovermove, nameclaim <name>, capital [set <city>], sethome, home, setwarp/delwarp/warp <name>, warps; /xmap.",
                "/c balance, deposit <amount>, alliance, befriend <faction|player>, acceptfriend <faction|player>, unfriend <faction>, setallywarp, allywarp <faction>, merge/acceptmerge <faction>, enemy/unenemy <faction>.",
                "/c declarewar <faction>, listwars, peace <faction> [city], acceptpeace/ceasefire/acceptceasefire/surrender/acceptsurrender/defendally <faction>, retreat, stonedrops [page]; /xflags, /xmulti, /xignore <player>."
        };
        int selected = page < 1 || page > text.length ? 1 : page; c.getSource().sendSuccess(new StringTextComponent("Page " + selected + "/" + text.length + "\n" + text[selected - 1]), false); return 1;
    }
    public static void register(CommandDispatcher<CommandSource> d) {
        for (String root : new String[]{"c", "xc"}) for (String alias : new String[]{"help", "man"}) d.register(Commands.literal(root).requires(s -> root.equals("c") || s.hasPermission(3)).then(Commands.literal(alias).executes(c -> help(c, 1, root.equals("xc"))).then(Commands.argument("page", IntegerArgumentType.integer()).executes(c -> help(c, IntegerArgumentType.getInteger(c, "page"), root.equals("xc"))))));
        for (String command : new String[]{"xmap", "xmulti", "xflags"}) d.register(Commands.literal(command).executes(c -> run(c, (g, p) -> {
            if (command.equals("xflags") && (!com.hfr.config.GameplayConfig.b("enableConquestFlagsCommand") || !com.hfr.server.FactionRuntime.warsEnabled(p.getServer()))) throw new IllegalArgumentException("Conquest flag distribution requires enabled flags and war declarations.");
            net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(command.equals("xmap") ? com.hfr.main.Xenofactions.CLAIM_MAP.get() : command.equals("xmulti") ? com.hfr.main.Xenofactions.MULTITOOL.get() : com.hfr.main.Xenofactions.CONQUEST_ITEM.get(), command.equals("xflags") ? 64 : 1);
            if (!p.inventory.add(stack)) throw new IllegalArgumentException("Make room in your inventory."); p.inventoryMenu.broadcastChanges(); return command.equals("xmap") ? "Claim map given; right-click to inspect territory." : command.equals("xmulti") ? "Multitool given." : "Conquest flags given.";
        })));
        d.register(Commands.literal("xc").requires(s -> s.hasPermission(3)).then(Commands.literal("editor").executes(c -> run(c, (g, p) -> com.hfr.server.EditorSessions.zoneCommand(p, "status", ""))).then(name("action").executes(c -> run(c, (g, p) -> { String[] parts = StringArgumentType.getString(c, "action").trim().split("\\s+", 2); return com.hfr.server.EditorSessions.zoneCommand(p, parts[0], parts.length == 2 ? parts[1] : ""); })))));
        d.register(Commands.literal("xmute").requires(s -> s.hasPermission(2)).then(Commands.argument("player", net.minecraft.command.arguments.EntityArgument.player()).then(Commands.argument("duration", StringArgumentType.word())
                .executes(c -> moderation(c, () -> com.hfr.server.ChatModeration.mute(net.minecraft.command.arguments.EntityArgument.getPlayer(c, "player"), StringArgumentType.getString(c, "duration"), "No reason provided")))
                .then(name("reason").executes(c -> moderation(c, () -> com.hfr.server.ChatModeration.mute(net.minecraft.command.arguments.EntityArgument.getPlayer(c, "player"), StringArgumentType.getString(c, "duration"), StringArgumentType.getString(c, "reason"))))))));
        d.register(Commands.literal("xunmute").requires(s -> s.hasPermission(2)).then(Commands.argument("player", net.minecraft.command.arguments.EntityArgument.player()).executes(c -> moderation(c, () -> com.hfr.server.ChatModeration.unmute(net.minecraft.command.arguments.EntityArgument.getPlayer(c, "player"))))));
        d.register(Commands.literal("xignore").then(Commands.argument("player", net.minecraft.command.arguments.EntityArgument.player()).executes(c -> moderation(c, () -> com.hfr.server.ChatModeration.toggle(c.getSource().getPlayerOrException(), net.minecraft.command.arguments.EntityArgument.getPlayer(c, "player"))))));
        d.register(Commands.literal("c").then(Commands.literal("info").executes(c -> run(c, (g, p) -> g.describe(p, ""))).then(faction().executes(c -> admin(c, g -> g.describe(null, StringArgumentType.getString(c, "faction")))))));
        for (String command : new String[]{"claim", "city"}) {
            d.register(Commands.literal("c").then(Commands.literal(command).then(name("city").executes(c -> run(c, (g, p) -> g.claim(p, StringArgumentType.getString(c, "city")))))
                    .then(Commands.literal("upgrade").executes(c -> run(c, FactionGameplay::upgrade)))));
        }
        d.register(Commands.literal("c").then(Commands.literal("city")
                .then(Commands.literal("cancelmove").executes(c -> run(c, (g, p) -> g.moveCommand(p, false))))
                .then(Commands.literal("recovermove").executes(c -> run(c, (g, p) -> g.moveCommand(p, true))))));
        d.register(Commands.literal("c").then(Commands.literal("capital").executes(c -> run(c, (g, p) -> g.capitalCommand(p, "")))
                .then(Commands.literal("set").then(name("city").executes(c -> run(c, (g, p) -> g.capitalCommand(p, StringArgumentType.getString(c, "city"))))))));
        d.register(Commands.literal("c").then(Commands.literal("nameclaim").then(name("city").executes(c -> run(c, (g, p) -> g.renameCity(p, StringArgumentType.getString(c, "city")))))));
        for (String command : new String[]{"sethome", "setallywarp"}) d.register(Commands.literal("c").then(Commands.literal(command).executes(c -> run(c, (g, p) -> g.setHome(p, command.equals("setallywarp"))))));
        d.register(Commands.literal("c").then(Commands.literal("home").executes(c -> run(c, (g, p) -> g.homeCommand(p, "")))));
        d.register(Commands.literal("c").then(Commands.literal("allywarp").then(faction().executes(c -> run(c, (g, p) -> g.homeCommand(p, StringArgumentType.getString(c, "faction")))))));
        d.register(Commands.literal("c").then(Commands.literal("gracebuild").executes(c -> run(c, FactionGameplay::buildGrace))));
        d.register(Commands.literal("c").then(Commands.literal("balance").executes(c -> run(c, (g, p) -> g.economy(p, "balance", 0)))));
        d.register(Commands.literal("c").then(Commands.literal("deposit").then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(c -> run(c, (g, p) -> g.economy(p, "deposit", IntegerArgumentType.getInteger(c, "amount")))))));
        for (String[] commands : new String[][]{{"alliance", "alliance"}, {"allies", "alliance"}, {"allylist", "alliance"}, {"listwars", "wars"}}) d.register(Commands.literal("c").then(Commands.literal(commands[0]).executes(c -> run(c, (g, p) -> g.diplomacy(p, commands[1], "", "")))));
        for (String[] commands : new String[][]{{"befriend", "befriend"}, {"ally", "befriend"}, {"acceptfriend", "acceptfriend"}, {"acceptally", "acceptfriend"}, {"unfriend", "unfriend"}, {"unally", "unfriend"}, {"enemy", "enemy"}, {"unenemy", "unenemy"}, {"declarewar", "declarewar"}, {"war", "declarewar"}, {"acceptpeace", "acceptpeace"}, {"ceasefire", "ceasefire"}, {"acceptceasefire", "acceptceasefire"}, {"surrender", "surrender"}, {"acceptsurrender", "acceptsurrender"}, {"defendally", "defendally"}})
            d.register(Commands.literal("c").then(Commands.literal(commands[0]).then(faction().executes(c -> run(c, (g, p) -> g.diplomacy(p, commands[1], StringArgumentType.getString(c, "faction"), ""))))));
        d.register(Commands.literal("c").then(Commands.literal("peace").then(Commands.argument("faction", StringArgumentType.word())
                .executes(c -> run(c, (g, p) -> g.diplomacy(p, "peace", StringArgumentType.getString(c, "faction"), "")))
                .then(name("city").executes(c -> run(c, (g, p) -> g.diplomacy(p, "peace", StringArgumentType.getString(c, "faction"), StringArgumentType.getString(c, "city"))))))));
        for (String command : new String[]{"permissions", "perms"}) {
            com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> node = Commands.literal(command).executes(c -> run(c, (g, p) -> g.permissions(p, "", "", null)));
            for (String relationship : new String[]{"ally", "neutral"}) {
                com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> rel = Commands.literal(relationship);
                for (String action : new String[]{"build", "destroy", "container", "interact", "switch"}) rel.then(Commands.literal(action).then(Commands.argument("allowed", BoolArgumentType.bool()).executes(c -> run(c, (g, p) -> g.permissions(p, relationship, action, BoolArgumentType.getBool(c, "allowed"))))));
                node.then(rel);
            }
            d.register(Commands.literal("c").then(node));
        }
        d.register(Commands.literal("cc").executes(c -> run(c, (g, p) -> FactionEvents.chatMode(p, ""))).then(Commands.argument("mode", StringArgumentType.word()).suggests((c, b) -> ISuggestionProvider.suggest(new String[]{"public", "faction", "alliance", "mute", "unmute"}, b)).executes(c -> run(c, (g, p) -> FactionEvents.chatMode(p, StringArgumentType.getString(c, "mode"))))));
        for (String action : new String[]{"warenable", "wardisable", "ignorewaronlinecheck", "ignorewarcooldowncheck", "ignorewarstatecheck", "skipwarcooldowns", "skipwarcooldown", "enablelegacywar"}) d.register(Commands.literal("xc").requires(s -> s.hasPermission(3)).then(Commands.literal(action).executes(c -> admin(c, g -> g.admin(action, "", 0)))));
        for (String action : new String[]{"resetbuildgrace", "endbuildgrace"}) d.register(Commands.literal("xc").requires(s -> s.hasPermission(3)).then(Commands.literal(action).then(faction().executes(c -> admin(c, g -> g.admin(action, StringArgumentType.getString(c, "faction"), 0))))));
        d.register(Commands.literal("xc").requires(s -> s.hasPermission(3)).then(Commands.literal("addprestige").then(Commands.argument("faction", StringArgumentType.word()).then(Commands.argument("amount", IntegerArgumentType.integer()).executes(c -> run(c, (g, p) -> g.admin("addprestige", StringArgumentType.getString(c, "faction"), IntegerArgumentType.getInteger(c, "amount"))))))));
        d.register(Commands.literal("xc").requires(s -> s.hasPermission(3)).then(Commands.literal("setclaim").then(Commands.argument("zone", StringArgumentType.word()).then(Commands.argument("shape", StringArgumentType.word()).then(Commands.argument("radius", IntegerArgumentType.integer(0, 26)).executes(c -> run(c, (g, p) -> g.setZone(p, StringArgumentType.getString(c, "zone"), StringArgumentType.getString(c, "shape"), IntegerArgumentType.getInteger(c, "radius")))))))));
        for (String action : new String[]{"color", "flag"}) d.register(Commands.literal("c").then(Commands.literal(action).then(Commands.argument("value", StringArgumentType.word()).executes(c -> run(c, (g, p) -> g.presentation(p, action, StringArgumentType.getString(c, "value")))))));
        d.register(Commands.literal("c").then(Commands.literal("listflags").executes(c -> { c.getSource().sendSuccess(new StringTextComponent("tri, vtri, bcross, wcross, julia, ussr, asterisk"), false); return 1; }).then(Commands.argument("page", IntegerArgumentType.integer()).executes(c -> { c.getSource().sendSuccess(new StringTextComponent("tri, vtri, bcross, wcross, julia, ussr, asterisk"), false); return 1; }))));
        for (String action : new String[]{"merge", "acceptmerge"}) d.register(Commands.literal("c").then(Commands.literal(action).then(faction().executes(c -> run(c, (g, p) -> g.merge(p, StringArgumentType.getString(c, "faction"), action.equals("acceptmerge")))))));
        for (String action : new String[]{"addwarp", "setwarp", "delwarp", "warp"}) d.register(Commands.literal("c").then(Commands.literal(action).then(Commands.argument("warp", StringArgumentType.word()).executes(c -> run(c, (g, p) -> g.warp(p, action.equals("setwarp") ? "addwarp" : action, StringArgumentType.getString(c, "warp")))))));
        d.register(Commands.literal("c").then(Commands.literal("warps").executes(c -> run(c, (g, p) -> g.warp(p, "warps", "")))));
        d.register(Commands.literal("c").then(Commands.literal("retreat").executes(c -> run(c, (g, p) -> { g.require(p, 1); return "POV: I mog you"; }))));
        for (String[] actions : new String[][]{{"forcejoin", "forcejoin"}, {"fj", "forcejoin"}, {"forcekick", "forcekick"}, {"fk", "forcekick"}, {"forcerename", "forcerename"}, {"fr", "forcerename"}, {"rename", "rename"}})
            d.register(Commands.literal("xc").requires(s -> s.hasPermission(3)).then(Commands.literal(actions[0]).then(name("value").executes(c -> run(c, (g, p) -> com.hfr.server.FactionService.get(p.getServer()).admin(p, actions[1], StringArgumentType.getString(c, "value")))))));
        for (String action : new String[]{"hijack", "hi"}) d.register(Commands.literal("xc").requires(s -> s.hasPermission(3)).then(Commands.literal(action).executes(c -> run(c, (g, p) -> com.hfr.server.FactionService.get(p.getServer()).admin(p, "hijack", "")))));
        for (String action : new String[]{"forcedisband", "fd"}) d.register(Commands.literal("xc").requires(s -> s.hasPermission(3)).then(Commands.literal(action).then(faction().executes(c -> run(c, (g, p) -> g.delete(StringArgumentType.getString(c, "faction"), false))))));
        d.register(Commands.literal("xc").requires(s -> s.hasPermission(3)).then(Commands.literal("disband").then(faction().executes(c -> run(c, (g, p) -> { String name = StringArgumentType.getString(c, "faction"); if (g.find(name) != g.require(p, 3)) throw new IllegalArgumentException("Confirm your own faction name."); return g.delete(name, false); })))));
        for (String action : new String[]{"deletedata", "deldat"}) d.register(Commands.literal("xc").requires(s -> s.hasPermission(3)).then(Commands.literal(action).executes(c -> run(c, (g, p) -> g.delete("", true)))));
        d.register(Commands.literal("c").then(Commands.literal("stonedrops").executes(c -> admin(c, g -> com.hfr.server.StoneDrops.list(1))).then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(c -> admin(c, g -> com.hfr.server.StoneDrops.list(IntegerArgumentType.getInteger(c, "page")))))));
        d.register(Commands.literal("stonedrop").requires(s -> s.hasPermission(4))
                .then(Commands.literal("list").executes(c -> admin(c, g -> com.hfr.server.StoneDrops.list(1))))
                .then(Commands.literal("remove").then(Commands.argument("index", IntegerArgumentType.integer(1)).executes(c -> admin(c, g -> com.hfr.server.StoneDrops.remove(IntegerArgumentType.getInteger(c, "index"))))))
                .then(Commands.argument("chance", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                        .executes(c -> run(c, (g, p) -> com.hfr.server.StoneDrops.add(p.getMainHandItem(), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "chance"), null, null)))
                        .then(Commands.argument("minY", IntegerArgumentType.integer()).then(Commands.argument("maxY", IntegerArgumentType.integer()).executes(c -> run(c, (g, p) -> com.hfr.server.StoneDrops.add(p.getMainHandItem(), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "chance"), IntegerArgumentType.getInteger(c, "minY"), IntegerArgumentType.getInteger(c, "maxY"))))))));
        for (String[] alias : new String[][]{{"sc", "setclaim"}, {"ap", "addprestige"}, {"addprestig", "addprestige"}, {"rbg", "resetbuildgrace"}, {"ebg", "endbuildgrace"}, {"factiontimeoutcreationreset", "clearcreationcooldown"}}) d.register(Commands.literal("xc").requires(s -> s.hasPermission(3)).then(Commands.literal(alias[0]).redirect(d.getRoot().getChild("xc").getChild(alias[1]))));
    }
    private GameplayCommands() { }
}
