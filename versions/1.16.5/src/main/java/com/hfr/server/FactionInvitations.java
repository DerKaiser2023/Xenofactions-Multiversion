package com.hfr.server;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;

/** Invitations belong to the saved faction UUID, never its display name. */
public final class FactionInvitations {
    public static void requireOfficer(int rank) {
        if (rank < 2) throw new IllegalArgumentException("Only officers and the leader may manage invitations.");
    }
    private static boolean matches(CompoundNBT entry, UUID player, String name, boolean names) {
        return names ? entry.getString("lastKnownName").equalsIgnoreCase(name) : entry.getString("uuid").equals(player.toString());
    }
    public static boolean has(ListNBT invitations, String factionId, UUID player, String name, boolean names) {
        for (int i = 0; i < invitations.size(); i++) {
            CompoundNBT entry = invitations.getCompound(i);
            if (entry.getString("factionId").equals(factionId) && matches(entry, player, name, names)) return true;
        }
        return false;
    }
    public static void add(ListNBT invitations, String factionId, UUID player, String name, boolean names, int rank, boolean inFaction) {
        requireOfficer(rank);
        UUID.fromString(factionId);
        if (inFaction) throw new IllegalArgumentException("That player is already in a faction.");
        if (has(invitations, factionId, player, name, names)) throw new IllegalArgumentException("That player already has an invitation from this faction.");
        CompoundNBT entry = new CompoundNBT(); entry.putString("factionId", factionId); entry.putString("uuid", player.toString()); entry.putString("lastKnownName", name);
        invitations.add(entry);
    }
    public static void requireJoin(ListNBT invitations, String factionId, UUID player, String name, boolean names, boolean inFaction) {
        if (inFaction) throw new IllegalArgumentException("You are already in a faction.");
        if (!has(invitations, factionId, player, name, names)) throw new IllegalArgumentException("You need an invitation from this faction. Use /c apply to request membership.");
    }
    public static boolean remove(ListNBT invitations, UUID player, String name, boolean names) {
        boolean changed = false;
        for (int i = invitations.size() - 1; i >= 0; i--) if (matches(invitations.getCompound(i), player, name, names)) { invitations.remove(i); changed = true; }
        return changed;
    }
    public static void revoke(ListNBT invitations, String factionId, UUID player, String name, boolean names, int rank) {
        requireOfficer(rank); int found = -1;
        for (int i = 0; i < invitations.size(); i++) {
            CompoundNBT entry = invitations.getCompound(i);
            boolean match = player == null ? entry.getString("lastKnownName").equalsIgnoreCase(name) : matches(entry, player, name, names);
            if (entry.getString("factionId").equals(factionId) && match) {
                if (found >= 0) throw new IllegalArgumentException("Ambiguous invitation name; no changes made.");
                found = i;
            }
        }
        if (found < 0) throw new IllegalArgumentException("No pending invitation for " + name + ".");
        invitations.remove(found);
    }
    public static List<String> names(ListNBT invitations) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < invitations.size(); i++) result.add(invitations.getCompound(i).getString("lastKnownName"));
        return result;
    }
    public static void validate(ListNBT invitations, String factionId) {
        java.util.Set<String> players = new java.util.HashSet<>();
        for (int i = 0; i < invitations.size(); i++) {
            CompoundNBT entry = invitations.getCompound(i);
            UUID.fromString(entry.getString("uuid")); UUID.fromString(entry.getString("factionId"));
            if (!entry.getString("factionId").equals(factionId) || entry.getString("lastKnownName").isEmpty() || !players.add(entry.getString("uuid")))
                throw new IllegalArgumentException("Invalid/duplicate faction invitation.");
        }
    }
    private FactionInvitations() { }
}
