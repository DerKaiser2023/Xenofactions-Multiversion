package com.hfr.clowder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Invitations belong to the saved faction UUID, never its display name. */
public final class FactionInvitations {
    public static void requireOfficer(int rank) {
        if (rank < 2) throw new IllegalArgumentException("Only officers and the leader may manage invitations.");
    }
    private static boolean matches(NBTTagCompound entry, UUID player, String name, boolean names) {
        return names ? entry.getString("lastKnownName").equalsIgnoreCase(name) : entry.getString("uuid").equals(player.toString());
    }
    public static boolean has(NBTTagList invitations, String factionId, UUID player, String name, boolean names) {
        for (int i = 0; i < invitations.tagCount(); i++) {
            NBTTagCompound entry = invitations.getCompoundTagAt(i);
            if (entry.getString("factionId").equals(factionId) && matches(entry, player, name, names)) return true;
        }
        return false;
    }
    public static void add(NBTTagList invitations, String factionId, UUID player, String name, boolean names, int rank, boolean inFaction) {
        requireOfficer(rank);
        UUID.fromString(factionId);
        if (inFaction) throw new IllegalArgumentException("That player is already in a faction.");
        if (has(invitations, factionId, player, name, names)) throw new IllegalArgumentException("That player already has an invitation from this faction.");
        NBTTagCompound entry = new NBTTagCompound(); entry.setString("factionId", factionId); entry.setString("uuid", player.toString()); entry.setString("lastKnownName", name);
        invitations.appendTag(entry);
    }
    public static void requireJoin(NBTTagList invitations, String factionId, UUID player, String name, boolean names, boolean inFaction) {
        if (inFaction) throw new IllegalArgumentException("You are already in a faction.");
        if (!has(invitations, factionId, player, name, names)) throw new IllegalArgumentException("You need an invitation from this faction. Use /c apply to request membership.");
    }
    public static boolean remove(NBTTagList invitations, UUID player, String name, boolean names) {
        boolean changed = false;
        for (int i = invitations.tagCount() - 1; i >= 0; i--) if (matches(invitations.getCompoundTagAt(i), player, name, names)) { invitations.removeTag(i); changed = true; }
        return changed;
    }
    public static void revoke(NBTTagList invitations, String factionId, UUID player, String name, boolean names, int rank) {
        requireOfficer(rank); int found = -1;
        for (int i = 0; i < invitations.tagCount(); i++) {
            NBTTagCompound entry = invitations.getCompoundTagAt(i);
            boolean match = player == null ? entry.getString("lastKnownName").equalsIgnoreCase(name) : matches(entry, player, name, names);
            if (entry.getString("factionId").equals(factionId) && match) {
                if (found >= 0) throw new IllegalArgumentException("Ambiguous invitation name; no changes made.");
                found = i;
            }
        }
        if (found < 0) throw new IllegalArgumentException("No pending invitation for " + name + ".");
        invitations.removeTag(found);
    }
    public static List<String> names(NBTTagList invitations) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < invitations.tagCount(); i++) result.add(invitations.getCompoundTagAt(i).getString("lastKnownName"));
        return result;
    }
    public static void validate(NBTTagList invitations, String factionId) {
        java.util.Set<String> players = new java.util.HashSet<>();
        for (int i = 0; i < invitations.tagCount(); i++) {
            NBTTagCompound entry = invitations.getCompoundTagAt(i);
            UUID.fromString(entry.getString("uuid")); UUID.fromString(entry.getString("factionId"));
            if (!entry.getString("factionId").equals(factionId) || entry.getString("lastKnownName").isEmpty() || !players.add(entry.getString("uuid")))
                throw new IllegalArgumentException("Invalid/duplicate faction invitation.");
        }
    }
    private FactionInvitations() { }
}
