package com.hfr.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.CompressedStreamTools;
import org.junit.Test;
import static org.junit.Assert.*;

public class FactionInvitationsTest {
    private final String faction = UUID.randomUUID().toString();
    private final UUID player = UUID.randomUUID();
    private final ListNBT invitations = new ListNBT();
    private void add(int rank, boolean member) { FactionInvitations.add(invitations, faction, player, "Target", false, rank, member); }
    private static void rejected(Runnable action) {
        try { action.run(); fail("Expected rejection"); } catch (IllegalArgumentException expected) { }
    }
    @Test public void officersAndLeadersCanInviteAndMembersCannot() {
        rejected(() -> add(0, false)); rejected(() -> add(1, false)); assertEquals(0, invitations.size());
        add(2, false); assertEquals(1, invitations.size());
        FactionInvitations.remove(invitations, player, "Target", false); add(3, false); assertEquals(1, invitations.size());
    }
    @Test public void existingMembershipAndDuplicatesAreRejected() {
        rejected(() -> add(2, true)); assertEquals(0, invitations.size()); add(2, false);
        rejected(() -> add(2, false)); assertEquals(1, invitations.size());
        rejected(() -> FactionInvitations.add(invitations, faction, UUID.randomUUID(), "target", true, 2, false));
        assertEquals(1, invitations.size());
    }
    @Test public void failedAcceptanceRetainsInviteAndSuccessCanConsumeIt() {
        add(2, false);
        rejected(() -> FactionInvitations.requireJoin(invitations, faction, player, "Target", false, true));
        rejected(() -> FactionInvitations.requireJoin(invitations, UUID.randomUUID().toString(), player, "Target", false, false));
        assertEquals(1, invitations.size());
        FactionInvitations.requireJoin(invitations, faction, player, "Target", false, false);
        assertTrue(FactionInvitations.remove(invitations, player, "Target", false));
        rejected(() -> FactionInvitations.requireJoin(invitations, faction, player, "Target", false, false));
    }
    @Test public void revocationChecksRankAndSupportsOfflineNames() {
        add(2, false);
        rejected(() -> FactionInvitations.revoke(invitations, faction, null, "target", false, 1)); assertEquals(1, invitations.size());
        FactionInvitations.revoke(invitations, faction, null, "target", false, 2); assertEquals(0, invitations.size());
        rejected(() -> FactionInvitations.revoke(invitations, faction, null, "target", false, 3));
        add(3, false); FactionInvitations.revoke(invitations, faction, player, "NewPlayerName", false, 3); assertEquals(0, invitations.size());
    }
    @Test public void identityModeAndFactionUuidAreAuthoritative() {
        add(2, false);
        assertTrue(FactionInvitations.has(invitations, faction, player, "RenamedPlayer", false));
        assertFalse(FactionInvitations.has(invitations, faction, UUID.randomUUID(), "Target", false));
        assertTrue(FactionInvitations.has(invitations, faction, UUID.randomUUID(), "target", true));
        assertFalse(FactionInvitations.has(invitations, UUID.randomUUID().toString(), player, "Target", false));
    }
    @Test public void joiningAnotherFactionClearsEveryOfferForOnlyThatPlayer() {
        add(2, false); ListNBT other = new ListNBT(); String otherId = UUID.randomUUID().toString(); UUID outsider = UUID.randomUUID();
        FactionInvitations.add(other, otherId, player, "Target", false, 2, false);
        FactionInvitations.add(other, otherId, outsider, "Outsider", false, 2, false);
        assertTrue(FactionInvitations.remove(invitations, player, "Target", false));
        assertTrue(FactionInvitations.remove(other, player, "Target", false));
        assertEquals(0, invitations.size()); assertEquals(1, other.size());
        assertTrue(FactionInvitations.has(other, otherId, outsider, "Outsider", false));
    }
    @Test public void savedFactionRenameAndNbtRoundTripPreserveInvitations() throws Exception {
        add(2, false); CompoundNBT record = new CompoundNBT(); record.putString("uuid", faction); record.putString("name", "OldName"); record.put("identityInvitations", invitations);
        record.putString("name", "NewName"); ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(record, bytes);
        CompoundNBT loaded = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals("NewName", loaded.getString("name")); FactionInvitations.validate(loaded.getList("identityInvitations", 10), loaded.getString("uuid"));
        assertTrue(FactionInvitations.has(loaded.getList("identityInvitations", 10), loaded.getString("uuid"), player, "Target", false));
    }
    @Test public void factionSaveModelRetainsInvitesAndDeletionCompactionRemovesOnlyTheirOwner() {
        add(2, false); CompoundNBT first = new CompoundNBT(); first.putString("uuid", faction); first.putString("name", "First"); first.put("identityInvitations", invitations);
        String secondId = UUID.randomUUID().toString(); CompoundNBT second = new CompoundNBT(); second.putString("uuid", secondId); second.putString("name", "Second");
        CompoundNBT root = new CompoundNBT(); root.putInt("clowderCount", 2); FactionService.put(root, 0, first); FactionService.put(root, 1, second);
        com.hfr.data.FactionSavedData data = new com.hfr.data.FactionSavedData(); data.load(root);
        CompoundNBT saved = data.save(new CompoundNBT());
        assertTrue(FactionInvitations.has(saved.getList("0_identityInvitations", 10), faction, player, "Target", false));
        // Use the same record replacement operation as regular and administrative disband.
        FactionService.put(saved, 0, FactionService.record(saved, 1)); FactionService.put(saved, 1, new CompoundNBT()); saved.putInt("clowderCount", 1);
        data.load(saved); CompoundNBT deleted = data.save(new CompoundNBT());
        assertEquals(secondId, deleted.getString("0_uuid")); assertFalse(deleted.contains("0_identityInvitations")); assertFalse(deleted.contains("1_identityInvitations"));
    }
    @Test public void invitationsCannotBeAttachedToDifferentFaction() {
        add(2, false); rejected(() -> FactionInvitations.validate(invitations, UUID.randomUUID().toString()));
    }
}
