package com.hfr.server;

import com.hfr.clowder.FactionMemberRecord;
import com.hfr.clowder.FactionRole;
import com.hfr.config.FoundationConfig;
import com.hfr.data.FactionSavedData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Native, owning-thread faction operations over one saved-data authority. */
public final class FactionService {
    private final MinecraftServer server;
    private final FactionSavedData data;
    CompoundNBT state;
    private FactionService(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Faction operations require the server thread.");
        this.server = server; data = FactionSavedData.get(server); state = data.snapshot();
    }
    public static FactionService get(MinecraftServer server) { return new FactionService(server); }
    public static boolean usesNames(MinecraftServer server) {
        String mode = FoundationConfig.IDENTITY_MODE.get();
        return "NAME".equalsIgnoreCase(mode) || ("AUTO".equalsIgnoreCase(mode) && (!FMLEnvironment.production || !server.usesAuthentication()));
    }
    public static String canonical(String name) {
        String result = name == null ? "" : name.trim();
        if (result.length() >= 2 && ((result.startsWith("\"") && result.endsWith("\"")) || (result.startsWith("'") && result.endsWith("'")))) result = result.substring(1, result.length() - 1).trim();
        return result.replaceAll("\\s+", "_");
    }
    private static String key(String name) { return canonical(name).toLowerCase(Locale.ROOT); }
    static CompoundNBT record(CompoundNBT root, int index) {
        CompoundNBT result = new CompoundNBT(); String prefix = index + "_";
        for (String field : root.getAllKeys()) if (field.startsWith(prefix)) result.put(field.substring(prefix.length()), root.get(field).copy());
        return result;
    }
    static void put(CompoundNBT root, int index, CompoundNBT record) {
        String prefix = index + "_";
        for (String field : new ArrayList<>(root.getAllKeys())) if (field.startsWith(prefix)) root.remove(field);
        for (String field : record.getAllKeys()) root.put(prefix + field, record.get(field).copy());
    }
    private static ListNBT list(CompoundNBT record, String field) {
        if (record.contains(field) && !record.contains(field, 9)) throw new IllegalArgumentException("Invalid faction field: " + field);
        ListNBT list = record.getList(field, 10);
        if (record.contains(field, 9) && list.size() != ((ListNBT)record.get(field)).size()) throw new IllegalArgumentException("Invalid faction entries: " + field);
        return list;
    }
    private static FactionMemberRecord member(CompoundNBT tag) {
        if (tag.getString("lastKnownName").isEmpty()) throw new IllegalArgumentException("Missing member name");
        return new FactionMemberRecord(UUID.fromString(tag.getString("uuid")), tag.getString("lastKnownName"), tag.getLong("joinedAt"), FactionRole.valueOf(tag.getString("role")));
    }
    public static void validate(CompoundNBT root) {
        if (!root.contains("clowderCount", 3) || root.getInt("clowderCount") < 0 || root.getInt("territory_count") < 0) throw new IllegalArgumentException("Invalid faction data counts");
        if (root.getInt("factionIdentityDataVersion") > 2) throw new IllegalArgumentException("Unsupported faction identity schema");
        Set<String> names = new HashSet<>(), factions = new HashSet<>(); Set<UUID> players = new HashSet<>();
        for (int i = 0; i < root.getInt("clowderCount"); i++) {
            CompoundNBT record = record(root, i); String name = key(record.getString("name"));
            if (record.getInt("nativeGameplayVersion") > 1) throw new IllegalArgumentException("Unsupported native faction gameplay schema");
            for (String field : new String[]{"nativeRelations", "nativeAllianceOffers"}) if (record.contains(field) && !record.contains(field, 10)) throw new IllegalArgumentException("Invalid native faction field: " + field);
            for (String field : new String[]{"prestige", "prestigeGen", "prestigeReq"}) if (record.contains(field) && !Float.isFinite(record.getFloat(field))) throw new IllegalArgumentException("Invalid prestige value");
            if (record.getInt("members") < 0 || record.getInt("officers") < 0) throw new IllegalArgumentException("Invalid membership counts");
            if (name.isEmpty() || !names.add(name)) throw new IllegalArgumentException("Missing/duplicate faction name");
            String uuid = record.getString("uuid");
            if (!uuid.isEmpty() && !factions.add(UUID.fromString(uuid).toString())) throw new IllegalArgumentException("Duplicate faction UUID");
            FactionInvitations.validate(list(record, "identityInvitations"), uuid);
            ListNBT members = list(record, "identityMembers"); int owners = 0;
            if (!members.isEmpty() && uuid.isEmpty()) throw new IllegalArgumentException("Resolved faction membership requires a stable faction UUID");
            for (int j = 0; j < members.size(); j++) {
                FactionMemberRecord member = member(members.getCompound(j));
                if (!players.add(member.playerUuid)) throw new IllegalArgumentException("Duplicate faction membership");
                if (member.role == FactionRole.OWNER) owners++;
            }
            if (!members.isEmpty() && owners != 1) throw new IllegalArgumentException("Faction must have exactly one leader");
            Set<UUID> applications = new HashSet<>(); ListNBT pending = list(record, "identityApplications");
            for (int j = 0; j < pending.size(); j++) {
                if (!applications.add(UUID.fromString(pending.getCompound(j).getString("uuid"))) || pending.getCompound(j).getString("lastKnownName").isEmpty()) throw new IllegalArgumentException("Invalid/duplicate application");
            }
        }
    }
    int index(String name) {
        for (int i = 0; i < state.getInt("clowderCount"); i++) if (key(state.getString(i + "_name")).equals(key(name))) return i;
        throw new IllegalArgumentException("Unknown faction: " + name);
    }
    int memberIndex(CompoundNBT record, ServerPlayerEntity player) {
        ListNBT members = list(record, "identityMembers"); int match = -1;
        for (int i = 0; i < members.size(); i++) {
            CompoundNBT tag = members.getCompound(i);
            boolean matches = usesNames(server) ? tag.getString("lastKnownName").equalsIgnoreCase(player.getGameProfile().getName()) : UUID.fromString(tag.getString("uuid")).equals(player.getUUID());
            if (matches) { if (match >= 0) throw new IllegalArgumentException("Ambiguous player identity; authorization denied."); match = i; }
        }
        return match;
    }
    int factionIndex(ServerPlayerEntity player) {
        int match = -1;
        for (int i = 0; i < state.getInt("clowderCount"); i++) if (memberIndex(record(state, i), player) >= 0) {
            if (match >= 0) throw new IllegalArgumentException("Ambiguous faction identity; authorization denied."); match = i;
        }
        return match;
    }
    int require(ServerPlayerEntity player, int permission) {
        int index = factionIndex(player); if (index < 0) throw new IllegalArgumentException("You are not in a resolved faction.");
        CompoundNBT record = record(state, index);
        if (member(list(record, "identityMembers").getCompound(memberIndex(record, player))).role.getPermissionLevel() < permission)
            throw new IllegalArgumentException(permission == 3 ? "Only the faction leader may do that." : "Only officers and the leader may do that.");
        return index;
    }
    private static int namedIndex(ListNBT list, String name) {
        int match = -1;
        for (int i = 0; i < list.size(); i++) if (list.getCompound(i).getString("lastKnownName").equalsIgnoreCase(name)) {
            if (match >= 0) throw new IllegalArgumentException("Ambiguous player name; no changes made."); match = i;
        }
        if (match < 0) throw new IllegalArgumentException("No matching member/application: " + name); return match;
    }
    void commit(CompoundNBT next) { data.edit(state, next); state = next; }
    void prepareEdit() { data.prepareEdit(); }
    private void update(int index, CompoundNBT record) {
        mirror(record); CompoundNBT next = state.copy(); put(next, index, record); next.putInt("factionIdentityDataVersion", 2); commit(next);
    }
    static void mirror(CompoundNBT record) {
        ListNBT members = list(record, "identityMembers"); List<String> names = new ArrayList<>(), officers = new ArrayList<>(); Set<String> canonical = new HashSet<>();
        for (int i = 0; i < members.size(); i++) {
            FactionMemberRecord member = member(members.getCompound(i)); names.add(member.lastKnownName); canonical.add(member.lastKnownName.toLowerCase(Locale.ROOT));
            if (member.role == FactionRole.OWNER) record.putString("leader", member.lastKnownName);
            if (member.role == FactionRole.OFFICER) officers.add(member.lastKnownName);
        }
        // Unknown old members remain staging data. They cannot authorize any operation.
        for (int i = 0; i < record.getInt("members"); i++) { String name = record.getString(Integer.toString(i)); if (!name.isEmpty() && !canonical.contains(name.toLowerCase(Locale.ROOT))) names.add(name); }
        for (int i = 0; i < record.getInt("officers"); i++) { String name = record.getString(i + "_off"); if (!name.isEmpty() && !canonical.contains(name.toLowerCase(Locale.ROOT))) officers.add(name); }
        for (int i = 0; i < record.getInt("members"); i++) record.remove(Integer.toString(i));
        for (int i = 0; i < record.getInt("officers"); i++) record.remove(i + "_off");
        record.putInt("members", names.size()); record.putInt("officers", officers.size());
        for (int i = 0; i < names.size(); i++) record.putString(Integer.toString(i), names.get(i));
        for (int i = 0; i < officers.size(); i++) record.putString(i + "_off", officers.get(i));
    }
    private static void removeName(CompoundNBT record, String name) {
        int count = record.getInt("members"); List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) { String old = record.getString(Integer.toString(i)); if (!old.equalsIgnoreCase(name)) names.add(old); record.remove(Integer.toString(i)); }
        record.putInt("members", names.size()); for (int i = 0; i < names.size(); i++) record.putString(Integer.toString(i), names.get(i));
        List<String> officers = new ArrayList<>(); count = record.getInt("officers");
        for (int i = 0; i < count; i++) { String old = record.getString(i + "_off"); if (!old.equalsIgnoreCase(name)) officers.add(old); record.remove(i + "_off"); }
        record.putInt("officers", officers.size()); for (int i = 0; i < officers.size(); i++) record.putString(i + "_off", officers.get(i));
    }
    private static CompoundNBT newMember(ServerPlayerEntity player, FactionRole role) {
        CompoundNBT tag = new CompoundNBT(); tag.putString("uuid", player.getUUID().toString()); tag.putString("lastKnownName", player.getGameProfile().getName());
        tag.putLong("joinedAt", System.currentTimeMillis()); tag.putString("role", role.name()); return tag;
    }
    private String checkedName(String requested) {
        String name = canonical(requested);
        if (name.isEmpty() || name.length() > 80 || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Faction names must contain 1-80 printable characters.");
        for (String existing : data.names()) if (key(existing).equals(key(name))) throw new IllegalArgumentException("This name is already taken."); return name;
    }
    public String create(ServerPlayerEntity player, String requested) {
        if (factionIndex(player) >= 0) throw new IllegalArgumentException("You are already in a faction.");
        com.hfr.data.PlayerTimers timers = PlayerLifecycle.timers(server);
        timers.migrateFallback(player);
        long until = timers.cooldown(player);
        if (until > System.currentTimeMillis()) throw new IllegalArgumentException("Faction creation is blocked for " + ((until - System.currentTimeMillis() + 59999L) / 60000L) + " minutes. You may still apply to factions.");
        String name = checkedName(requested); CompoundNBT record = new CompoundNBT(); record.putString("uuid", UUID.randomUUID().toString()); record.putString("name", name);
        record.putString("motd", "Message of the day!"); record.putInt("flag", 1); record.putBoolean("canChange", true);
        Set<Integer> colors = new HashSet<>(); colors.add(0xFF8000); colors.add(0xFF0000); colors.add(0xFFFFFF);
        for (int i = 0; i < state.getInt("clowderCount"); i++) colors.add(state.getInt(i + "_color"));
        int color; do { color = player.getRandom().nextInt(0x1000000); } while (colors.contains(color)); record.putInt("color", color);
        record.putFloat("prestige", FoundationConfig.STARTING_PRESTIGE.get().floatValue()); record.putFloat("prestigeGen", FoundationConfig.BASE_PRESTIGE.get().floatValue());
        Integer dimension = FoundationConfig.legacyId(player.level.dimension().location());
        if (dimension == null) throw new IllegalArgumentException("Configure a legacy dimension mapping before creating a faction here.");
        record.putInt("allyWarpDim", dimension); record.putInt("allyWarpX", (int)player.getX()); record.putInt("allyWarpY", (int)player.getY()); record.putInt("allyWarpZ", (int)player.getZ());
        ListNBT members = new ListNBT(); members.add(newMember(player, FactionRole.OWNER)); record.put("identityMembers", members); mirror(record);
        CompoundNBT next = state.copy(); int count = next.getInt("clowderCount"); put(next, count, record); next.putInt("clowderCount", count + 1); next.putInt("factionIdentityDataVersion", 2); commit(next);
        clearApplications(player); return "Created faction " + name + ". Use /c claim <city name> to found your first city.";
    }
    private boolean applicationMatches(CompoundNBT application, ServerPlayerEntity player) {
        return UUID.fromString(application.getString("uuid")).equals(player.getUUID()) || (usesNames(server) && application.getString("lastKnownName").equalsIgnoreCase(player.getGameProfile().getName()));
    }
    public String invitation(ServerPlayerEntity player, String name, boolean revoke) {
        int index = require(player, 2); CompoundNBT record = record(state, index);
        int rank = member(list(record, "identityMembers").getCompound(memberIndex(record, player))).role.getPermissionLevel();
        ServerPlayerEntity target = server.getPlayerList().getPlayerByName(name);
        ListNBT invitations = list(record, "identityInvitations");
        if (revoke) FactionInvitations.revoke(invitations, record.getString("uuid"), target == null ? null : target.getUUID(), target == null ? name : target.getGameProfile().getName(), usesNames(server), rank);
        else {
            if (target == null) throw new IllegalArgumentException("The player must be online.");
            FactionInvitations.add(invitations, record.getString("uuid"), target.getUUID(), target.getGameProfile().getName(), usesNames(server), rank, factionIndex(target) >= 0);
        }
        record.put("identityInvitations", invitations); update(index, record);
        if (target != null) target.sendMessage(new StringTextComponent(revoke ? "Your invitation to " + record.getString("name") + " was revoked." : invitationMessage(record)), Util.NIL_UUID);
        return (revoke ? "Revoked invitation for " : "Invited ") + name + (revoke ? "." : " to " + record.getString("name") + ".");
    }
    private static String invitationMessage(CompoundNBT record) {
        return "You are invited to " + record.getString("name") + ". Accept with /c join " + record.getString("name") + ".";
    }
    public List<String> invitationNames(ServerPlayerEntity player) {
        return FactionInvitations.names(list(record(state, require(player, 2)), "identityInvitations"));
    }
    public String join(ServerPlayerEntity player, String name) {
        int index = index(name); CompoundNBT record = record(state, index);
        FactionInvitations.requireJoin(list(record, "identityInvitations"), record.getString("uuid"), player.getUUID(), player.getGameProfile().getName(), usesNames(server), factionIndex(player) >= 0);
        ListNBT members = list(record, "identityMembers");
        if (members.isEmpty()) throw new IllegalArgumentException("Resolve the faction's leader identity before joining it.");
        members.add(newMember(player, FactionRole.MEMBER)); record.put("identityMembers", members);
        update(index, record); clearApplications(player);
        notifyFaction(index, player.getGameProfile().getName() + " joined the faction.");
        return "Joined " + record.getString("name") + ".";
    }
    public String apply(ServerPlayerEntity player, String name) {
        if (factionIndex(player) >= 0) throw new IllegalArgumentException("You are already in a faction.");
        int index = index(name); CompoundNBT record = record(state, index); ListNBT applications = list(record, "identityApplications");
        for (int i = 0; i < applications.size(); i++) if (applicationMatches(applications.getCompound(i), player)) throw new IllegalArgumentException("You already applied to this faction.");
        CompoundNBT application = new CompoundNBT(); application.putString("uuid", player.getUUID().toString()); application.putString("lastKnownName", player.getGameProfile().getName());
        applications.add(application); record.put("identityApplications", applications); update(index, record); notifyFaction(index, player.getGameProfile().getName() + " applied. Use /c accept or /c deny."); return "Application sent.";
    }
    public String application(ServerPlayerEntity player, String name, boolean accept) {
        int index = require(player, 2); CompoundNBT record = record(state, index); ListNBT applications = list(record, "identityApplications"); int target = namedIndex(applications, name);
        if (accept) {
            CompoundNBT application = applications.getCompound(target);
            ServerPlayerEntity online = usesNames(server) ? server.getPlayerList().getPlayerByName(application.getString("lastKnownName")) : server.getPlayerList().getPlayer(UUID.fromString(application.getString("uuid")));
            if (online == null) throw new IllegalArgumentException("The applicant must be online to join.");
            if (factionIndex(online) >= 0) throw new IllegalArgumentException("That player is already in a faction. Deny the stale application.");
            ListNBT members = list(record, "identityMembers"); members.add(newMember(online, FactionRole.MEMBER)); record.put("identityMembers", members);
            applications.remove(target); record.put("identityApplications", applications); update(index, record); clearApplications(online);
            online.sendMessage(new StringTextComponent("You joined " + record.getString("name") + "."), Util.NIL_UUID);
        } else { applications.remove(target); record.put("identityApplications", applications); update(index, record); }
        return (accept ? "Accepted " : "Denied ") + name + ".";
    }
    private void clearApplications(ServerPlayerEntity player) {
        for (int i = 0; i < state.getInt("clowderCount"); i++) {
            CompoundNBT record = record(state, i); ListNBT applications = list(record, "identityApplications"); boolean changed = false;
            for (int j = applications.size() - 1; j >= 0; j--) if (applicationMatches(applications.getCompound(j), player)) { applications.remove(j); changed = true; }
            ListNBT invitations = list(record, "identityInvitations");
            boolean removed = FactionInvitations.remove(invitations, player.getUUID(), player.getGameProfile().getName(), usesNames(server));
            if (changed || removed) { record.put("identityApplications", applications); record.put("identityInvitations", invitations); update(i, record); }
        }
    }
    public String remove(ServerPlayerEntity player, String name, boolean leave) {
        int index = require(player, leave ? 1 : 2); CompoundNBT record = record(state, index); ListNBT members = list(record, "identityMembers");
        int target = leave ? memberIndex(record, player) : namedIndex(members, name); FactionMemberRecord member = member(members.getCompound(target));
        if (member.role == FactionRole.OWNER) throw new IllegalArgumentException("The leader must transfer ownership or disband; the leader cannot be removed.");
        if (!leave && target == memberIndex(record, player)) throw new IllegalArgumentException("Use /c leave to leave your faction.");
        members.remove(target); record.put("identityMembers", members); removeName(record, member.lastKnownName); update(index, record); return member.lastKnownName + " left the faction.";
    }
    public String rank(ServerPlayerEntity player, String name, String action) {
        int index = require(player, 3); CompoundNBT record = record(state, index); ListNBT members = list(record, "identityMembers"); int target = namedIndex(members, name);
        if ("owner".equals(action)) {
            for (int i = 0; i < members.size(); i++) if (member(members.getCompound(i)).role == FactionRole.OWNER) members.getCompound(i).putString("role", "OFFICER");
            members.getCompound(target).putString("role", "OWNER");
        } else {
            FactionRole expected = "promote".equals(action) ? FactionRole.MEMBER : FactionRole.OFFICER;
            if (member(members.getCompound(target)).role != expected) throw new IllegalArgumentException("This member cannot be " + action + "d from the current rank.");
            members.getCompound(target).putString("role", "promote".equals(action) ? "OFFICER" : "MEMBER");
        }
        record.put("identityMembers", members); update(index, record); notifyFaction(index, "Faction rank updated for " + name + "."); return "Faction rank updated.";
    }
    public String motd(ServerPlayerEntity player, String text) {
        int index = require(player, 2); if (text.length() > 1024) throw new IllegalArgumentException("MOTD is too long.");
        CompoundNBT record = record(state, index); record.putString("motd", text); update(index, record); return "Faction MOTD updated.";
    }
    public List<String> names(ServerPlayerEntity player, boolean applicants) {
        List<String> result = new ArrayList<>(); CompoundNBT record = record(state, require(player, applicants ? 2 : 1));
        ListNBT list = list(record, applicants ? "identityApplications" : "identityMembers");
        for (int i = 0; i < list.size(); i++) result.add(list.getCompound(i).getString("lastKnownName")); return result;
    }
    public String membership(ServerPlayerEntity player) {
        int index = factionIndex(player); if (index < 0) return "No resolved faction membership";
        CompoundNBT record = record(state, index); return record.getString("name") + " / " + member(list(record, "identityMembers").getCompound(memberIndex(record, player))).role;
    }
    public void login(ServerPlayerEntity player) {
        int index = factionIndex(player);
        if (index >= 0) {
            CompoundNBT record = record(state, index); ListNBT members = list(record, "identityMembers"); int target = memberIndex(record, player);
            String old = members.getCompound(target).getString("lastKnownName"), name = player.getGameProfile().getName();
            if (!old.equals(name)) { removeName(record, old); members.getCompound(target).putString("lastKnownName", name); record.put("identityMembers", members); update(index, record); }
            player.sendMessage(new StringTextComponent(record.getString("name") + ": " + record.getString("motd")), Util.NIL_UUID);
        }
        for (int i = 0; i < state.getInt("clowderCount"); i++) {
            CompoundNBT record = record(state, i); ListNBT applications = list(record, "identityApplications"); boolean changed = false;
            for (int j = 0; j < applications.size(); j++) if (applicationMatches(applications.getCompound(j), player) && !applications.getCompound(j).getString("lastKnownName").equals(player.getGameProfile().getName())) {
                applications.getCompound(j).putString("lastKnownName", player.getGameProfile().getName()); changed = true;
            }
            if (changed) { record.put("identityApplications", applications); update(i, record); }
        }
        if (index >= 0) clearApplications(player);
        else for (int i = 0; i < state.getInt("clowderCount"); i++) {
            CompoundNBT record = record(state, i);
            if (FactionInvitations.has(list(record, "identityInvitations"), record.getString("uuid"), player.getUUID(), player.getGameProfile().getName(), usesNames(server)))
                player.sendMessage(new StringTextComponent(invitationMessage(record)), Util.NIL_UUID);
        }
    }
    private void notifyFaction(int index, String text) {
        CompoundNBT record = record(state, index);
        for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) if (memberIndex(record, player) >= 0) player.sendMessage(new StringTextComponent(text), Util.NIL_UUID);
    }
    public List<CompoundNBT> disbandMembers(ServerPlayerEntity player, String confirmation) {
        CompoundNBT record = record(state, require(player, 3));
        if (!key(confirmation).equals(key(record.getString("name")))) throw new IllegalArgumentException("Confirm with /c disband <your faction name>.");
        List<CompoundNBT> result = new ArrayList<>(); ListNBT members = list(record, "identityMembers");
        for (int i = 0; i < members.size(); i++) result.add(members.getCompound(i).copy());
        for (int i = 0; i < record.getInt("members"); i++) {
            String name = record.getString(Integer.toString(i)); boolean resolved = false;
            for (CompoundNBT member : result) if (member.getString("lastKnownName").equalsIgnoreCase(name)) resolved = true;
            if (!resolved && !name.isEmpty()) { CompoundNBT fallback = new CompoundNBT(); fallback.putString("lastKnownName", name); fallback.putString("role", "MEMBER"); result.add(fallback); }
        }
        return result;
    }
    public String disband(ServerPlayerEntity player, String confirmation) {
        disbandMembers(player, confirmation); int index = require(player, 3); CompoundNBT removed = record(state, index); String name = removed.getString("name"), uuid = removed.getString("uuid");
        CompoundNBT next = state.copy(); int count = state.getInt("clowderCount");
        for (int i = index; i < count - 1; i++) put(next, i, record(state, i + 1)); put(next, count - 1, new CompoundNBT()); next.putInt("clowderCount", count - 1);
        for (int i = 0; i < next.getInt("territory_count"); i++) if (next.getString("ownership_meta_" + i + "_owner").equals(name)) {
            next.putInt("ownership_meta_" + i + "_zone", 2); next.remove("ownership_meta_" + i + "_owner");
            next.remove("ownership_meta_" + i + "_ownerUuid"); next.remove("cityId_meta_" + i);
        }
        ListNBT terminals = next.getList("FactionMarketTerminals", 10);
        for (int i = terminals.size() - 1; i >= 0; i--) if (terminals.getCompound(i).getString("factionId").equals(uuid)) terminals.remove(i);
        for (int i = 0; i < next.getInt("clowderCount"); i++) { CompoundNBT record = record(next, i); references(record, name, uuid, ""); put(next, i, record); }
        validate(next);
        data.prepareEdit();
        // Persist penalties first: a failed JSON write must not permit an unpenalized disband.
        PlayerLifecycle.timers(server).disband(disbandMembers(player, confirmation));
        commit(next);
        FactionRuntime.forgetFaction(server, uuid);
        server.overworld().getDataStorage().save();
        return "Faction " + name + " disbanded. Creation cooldowns: leader 7 days, members 3 days.";
    }
    public String rename(ServerPlayerEntity player, String requested) {
        int index = require(player, 2); String name = checkedName(requested); CompoundNBT record = record(state, index); String old = record.getString("name");
        CompoundNBT next = state.copy(); record.putString("name", name); put(next, index, record);
        for (int i = 0; i < next.getInt("territory_count"); i++) if (next.getString("ownership_meta_" + i + "_owner").equals(old)) next.putString("ownership_meta_" + i + "_owner", name);
        for (int i = 0; i < next.getInt("clowderCount"); i++) { CompoundNBT other = record(next, i); references(other, old, record.getString("uuid"), name); put(next, i, other); }
        commit(next); return "Renamed faction to " + name + ".";
    }
    static void references(CompoundNBT record, String name, String uuid, String replacement) {
        String[][] groups = {{"allies", "ally"}, {"activeWars", "war"}, {"defendingAllies", "def"}, {"surrenderReq", "sreq"}, {"ceaseReq", "creq"}, {"peaceReq", "preq"},
                {"warDeclaredAt", "wdecl_n", "wdecl_t"}, {"noWarUntil", "nwu_n", "nwu_t"}, {"formerAllyNoWar", "fanwu_n", "fanwu_t"}};
        for (String[] group : groups) rewrite(record, group, name, replacement);
        if (replacement.isEmpty()) {
            CompoundNBT relations = record.getCompound("nativeRelations"); relations.remove(uuid);
            CompoundNBT offers = record.getCompound("nativeAllianceOffers");
            for (String envoy : new ArrayList<>(offers.getAllKeys())) if (offers.getCompound(envoy).getString("factionId").equals(uuid)) offers.remove(envoy);
            if (record.getString("nativeTributeFactionId").equals(uuid)) { record.putString("nativeTributeFactionId", ""); record.putLong("surrenderTributeUntil", 0); }
            rewrite(record, new String[]{"enemyFactionUuids", "enemyFactionUuid"}, uuid, "");
            rewrite(record, new String[]{"pendingEnemyRemovalTimes", "pendingEnemyUuid", "pendingEnemyTime"}, uuid, "");
            if (record.getString("surrenderTributeTo").equals(uuid)) { record.putString("surrenderTributeTo", ""); record.putLong("surrenderTributeUntil", 0); }
        }
        if (record.getString("surrenderTributeTo").equals(name) || record.getString("surrenderTributeTo").equals(uuid)) {
            record.putString("surrenderTributeTo", replacement);
            if (replacement.isEmpty()) record.putLong("surrenderTributeUntil", 0);
        }
        for (String field : new String[]{"enemy", "suzerain"}) if (record.getString(field).equals(name)) record.putString(field, replacement.isEmpty() ? "nobody2584369" : replacement);
    }
    public String admin(ServerPlayerEntity player, String action, String target) {
        if (action.equals("forcerename")) return renameAt(require(player, 1), target);
        if (action.equals("rename")) return rename(player, target);
        if (action.equals("forcejoin")) {
            if (factionIndex(player) >= 0) throw new IllegalArgumentException("You are already in a faction.");
            int i = index(target); CompoundNBT f = record(state, i); ListNBT ms = list(f, "identityMembers");
            if (ms.isEmpty()) throw new IllegalArgumentException("Resolve the faction's leader identity before joining it.");
            ms.add(newMember(player, FactionRole.MEMBER)); f.put("identityMembers", ms); update(i, f); clearApplications(player); return "Joined " + f.getString("name") + ".";
        }
        if (action.equals("forcekick")) {
            ServerPlayerEntity victim = server.getPlayerList().getPlayerByName(target);
            if (victim == null) throw new IllegalArgumentException("The player must be online.");
            int i = require(victim, 1); CompoundNBT f = record(state, i); ListNBT ms = list(f, "identityMembers"); int m = memberIndex(f, victim);
            if (member(ms.getCompound(m)).role == FactionRole.OWNER) throw new IllegalArgumentException("A leader cannot be kicked.");
            ms.remove(m); f.put("identityMembers", ms); removeName(f, victim.getGameProfile().getName()); update(i, f); FactionRuntime.cancel(victim); return "Player removed from faction.";
        }
        if (action.equals("hijack")) {
            int i = require(player, 1); CompoundNBT f = record(state, i); ListNBT ms = list(f, "identityMembers");
            if (member(ms.getCompound(memberIndex(f, player))).role == FactionRole.OWNER) throw new IllegalArgumentException("You are already the leader.");
            for (int m = 0; m < ms.size(); m++) if (member(ms.getCompound(m)).role == FactionRole.OWNER) ms.getCompound(m).putString("role", "OFFICER");
            ms.getCompound(memberIndex(f, player)).putString("role", "OWNER"); f.put("identityMembers", ms); update(i, f); return "Faction leadership assumed.";
        }
        throw new IllegalArgumentException("Unknown membership administrator action.");
    }
    private String renameAt(int index, String requested) {
        String name = checkedName(requested); CompoundNBT f = record(state, index); String old = f.getString("name"); CompoundNBT next = state.copy(); f.putString("name", name); put(next, index, f);
        for (int i = 0; i < next.getInt("territory_count"); i++) if (next.getString("ownership_meta_" + i + "_owner").equals(old)) next.putString("ownership_meta_" + i + "_owner", name);
        for (int i = 0; i < next.getInt("clowderCount"); i++) { CompoundNBT other = record(next, i); references(other, old, f.getString("uuid"), name); put(next, i, other); }
        commit(next); return "Renamed faction to " + name + ".";
    }
    private static void rewrite(CompoundNBT record, String[] group, String target, String replacement) {
        int count = record.getInt(group[0]), written = 0;
        for (int i = 0; i < count; i++) {
            String value = record.getString(i + "_" + group[1]); boolean matches = value.equals(target); if (matches && replacement.isEmpty()) continue;
            record.putString(written + "_" + group[1], matches ? replacement : value);
            if (group.length > 2 && record.contains(i + "_" + group[2])) record.put(written + "_" + group[2], record.get(i + "_" + group[2]).copy()); written++;
        }
        for (int i = written; i < count; i++) for (int j = 1; j < group.length; j++) record.remove(i + "_" + group[j]);
        if (record.contains(group[0])) record.putInt(group[0], written);
    }
}
