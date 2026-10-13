package com.hfr.clowder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.CompressedStreamTools;
import org.junit.Test;
import static org.junit.Assert.*;

public class FactionInvitationsTest {
    private final String faction = UUID.randomUUID().toString();
    private final UUID player = UUID.randomUUID();
    private final NBTTagList invitations = new NBTTagList();
    private void add(int rank, boolean member) { FactionInvitations.add(invitations, faction, player, "Target", false, rank, member); }
    private static void rejected(Runnable action) {
        try { action.run(); fail("Expected rejection"); } catch (IllegalArgumentException expected) { }
    }
    @Test public void officersAndLeadersCanInviteAndMembersCannot() {
        rejected(() -> add(0, false)); rejected(() -> add(1, false)); assertEquals(0, invitations.tagCount());
        add(2, false); assertEquals(1, invitations.tagCount());
        FactionInvitations.remove(invitations, player, "Target", false); add(3, false); assertEquals(1, invitations.tagCount());
    }
    @Test public void existingMembershipAndDuplicatesAreRejected() {
        rejected(() -> add(2, true)); assertEquals(0, invitations.tagCount()); add(2, false);
        rejected(() -> add(2, false)); assertEquals(1, invitations.tagCount());
        rejected(() -> FactionInvitations.add(invitations, faction, UUID.randomUUID(), "target", true, 2, false));
        assertEquals(1, invitations.tagCount());
    }
    @Test public void failedAcceptanceRetainsInviteAndSuccessCanConsumeIt() {
        add(2, false);
        rejected(() -> FactionInvitations.requireJoin(invitations, faction, player, "Target", false, true));
        rejected(() -> FactionInvitations.requireJoin(invitations, UUID.randomUUID().toString(), player, "Target", false, false));
        assertEquals(1, invitations.tagCount());
        FactionInvitations.requireJoin(invitations, faction, player, "Target", false, false);
        assertTrue(FactionInvitations.remove(invitations, player, "Target", false));
        rejected(() -> FactionInvitations.requireJoin(invitations, faction, player, "Target", false, false));
    }
    @Test public void revocationChecksRankAndSupportsOfflineNames() {
        add(2, false);
        rejected(() -> FactionInvitations.revoke(invitations, faction, null, "target", false, 1)); assertEquals(1, invitations.tagCount());
        FactionInvitations.revoke(invitations, faction, null, "target", false, 2); assertEquals(0, invitations.tagCount());
        rejected(() -> FactionInvitations.revoke(invitations, faction, null, "target", false, 3));
        add(3, false); FactionInvitations.revoke(invitations, faction, player, "NewPlayerName", false, 3); assertEquals(0, invitations.tagCount());
    }
    @Test public void identityModeAndFactionUuidAreAuthoritative() {
        add(2, false);
        assertTrue(FactionInvitations.has(invitations, faction, player, "RenamedPlayer", false));
        assertFalse(FactionInvitations.has(invitations, faction, UUID.randomUUID(), "Target", false));
        assertTrue(FactionInvitations.has(invitations, faction, UUID.randomUUID(), "target", true));
        assertFalse(FactionInvitations.has(invitations, UUID.randomUUID().toString(), player, "Target", false));
    }
    @Test public void joiningAnotherFactionClearsEveryOfferForOnlyThatPlayer() {
        add(2, false); NBTTagList other = new NBTTagList(); String otherId = UUID.randomUUID().toString(); UUID outsider = UUID.randomUUID();
        FactionInvitations.add(other, otherId, player, "Target", false, 2, false);
        FactionInvitations.add(other, otherId, outsider, "Outsider", false, 2, false);
        assertTrue(FactionInvitations.remove(invitations, player, "Target", false));
        assertTrue(FactionInvitations.remove(other, player, "Target", false));
        assertEquals(0, invitations.tagCount()); assertEquals(1, other.tagCount());
        assertTrue(FactionInvitations.has(other, otherId, outsider, "Outsider", false));
    }
    @Test public void savedFactionRenameAndNbtRoundTripPreserveInvitations() throws Exception {
        add(2, false); NBTTagCompound record = new NBTTagCompound(); record.setString("uuid", faction); record.setString("name", "OldName"); record.setTag("identityInvitations", invitations);
        record.setString("name", "NewName"); ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(record, bytes);
        NBTTagCompound loaded = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals("NewName", loaded.getString("name")); FactionInvitations.validate(loaded.getTagList("identityInvitations", 10), loaded.getString("uuid"));
        assertTrue(FactionInvitations.has(loaded.getTagList("identityInvitations", 10), loaded.getString("uuid"), player, "Target", false));
    }
    @Test public void factionSaveModelRetainsInvitesAndDeletionCompactionRemovesOnlyTheirOwner() {
        add(2, false); Clowder first = new Clowder(); first.uuid = faction; first.name = "First"; first.motd = ""; first.leader = ""; first.flag = ClowderFlag.TRICOLOR; first.invitations = invitations;
        Clowder second = new Clowder(); second.uuid = UUID.randomUUID().toString(); second.name = "Second"; second.motd = ""; second.leader = ""; second.flag = ClowderFlag.TRICOLOR;
        Clowder.clowders.clear();
        try {
            Clowder.clowders.add(first); Clowder.clowders.add(second); NBTTagCompound saved = new NBTTagCompound(); Clowder.writeToNBT(saved);
            Clowder loaded = Clowder.loadClowder(0, saved);
            assertTrue(FactionInvitations.has(loaded.invitations, loaded.uuid, player, "Target", false));
            // Disband removes the owning record; the normal saver compacts surviving factions.
            Clowder.clowders.remove(first); NBTTagCompound deleted = new NBTTagCompound(); Clowder.writeToNBT(deleted);
            assertEquals(second.uuid, deleted.getString("0_uuid")); assertEquals(0, deleted.getTagList("0_identityInvitations", 10).tagCount()); assertFalse(deleted.hasKey("1_identityInvitations"));
        } finally { Clowder.clowders.clear(); }
    }
    @Test public void invitationsCannotBeAttachedToDifferentFaction() {
        add(2, false); rejected(() -> FactionInvitations.validate(invitations, UUID.randomUUID().toString()));
    }
}
