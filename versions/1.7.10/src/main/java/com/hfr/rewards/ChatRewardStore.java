package com.hfr.rewards;

import com.hfr.util.XFLog;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

/** Server-thread ledger. Acceptance reserves limits and exact escrow in a single durable write. */
public final class ChatRewardStore {
    private static final String MARKER = "xfChatRewardDelivery";
    private static ChatRewardStore current;
    private final World world;
    private final Path file;
    private NBTTagCompound state;
    private boolean writable;

    private ChatRewardStore(World world) {
        this.world = world;
        file = new File(world.getSaveHandler().getWorldDirectory(), "data/hfr_chat_rewards.dat").toPath();
        try {
            if (Files.exists(file)) {
                try (FileInputStream in = new FileInputStream(file.toFile())) { state = CompressedStreamTools.readCompressed(in); }
                validate(state);
            } else {
                state = new NBTTagCompound(); state.setInteger("Version", 1); state.setTag("Phrases", new NBTTagCompound());
            }
            writable = true;
        } catch (Exception e) { XFLog.error("Chat reward ledger unavailable; original file retained: " + file, e); }
    }
    public static ChatRewardStore get() {
        World world = MinecraftServer.getServer().worldServerForDimension(0);
        if (world == null) throw new IllegalStateException("world");
        if (current == null || current.world != world) current = new ChatRewardStore(world);
        if (!current.writable) throw new IllegalStateException("ledger");
        return current;
    }
    public static void release() { current = null; }
    public long total(String id) { return state.getCompoundTag("Phrases").getCompoundTag(id).getLong("Total"); }
    public NBTTagCompound player(String id, UUID uuid) {
        NBTTagCompound players = state.getCompoundTag("Phrases").getCompoundTag(id).getCompoundTag("Players");
        return players.hasKey(uuid.toString()) ? (NBTTagCompound)players.getCompoundTag(uuid.toString()).copy() : emptyPlayer();
    }
    public String[] ids() { return (String[])state.getCompoundTag("Phrases").func_150296_c().toArray(new String[0]); }
    private static NBTTagCompound emptyPlayer() {
        NBTTagCompound player = new NBTTagCompound(); player.setLong("Count", 0); player.setLong("Last", 0);
        player.setTag("Pending", new NBTTagList()); return player;
    }
    private void put(String id, UUID uuid, NBTTagCompound player, boolean acceptance) throws IOException {
        NBTTagCompound next = (NBTTagCompound)state.copy(), phrases = next.getCompoundTag("Phrases");
        NBTTagCompound history = phrases.getCompoundTag(id), players = history.getCompoundTag("Players");
        history.setLong("Total", history.getLong("Total") + (acceptance ? 1 : 0));
        players.setTag(uuid.toString(), player); history.setTag("Players", players); phrases.setTag(id, history);
        commit(next);
    }
    public void accept(ChatRewardConfig.Phrase phrase, UUID uuid, long now) throws IOException {
        NBTTagCompound player = player(phrase.id, uuid);
        if (player.getTagList("Pending", 10).tagCount() != 0 || player.hasKey("Transfer")) throw new IllegalStateException("pending");
        player.setLong("Count", Math.addExact(player.getLong("Count"), 1)); player.setLong("Last", now);
        player.setTag("Pending", phrase.pending());
        put(phrase.id, uuid, player, true);
    }
    public void prepare(String id, EntityPlayerMP player, NBTTagList remaining, String token) throws IOException {
        NBTTagCompound record = player(id, player.getUniqueID()), transfer = new NBTTagCompound();
        if (record.hasKey("Transfer")) throw new IllegalStateException("transfer");
        transfer.setString("Token", token); transfer.setTag("Remaining", remaining);
        record.setTag("Transfer", transfer); put(id, player.getUniqueID(), record, false);
    }
    /** The marker and inventory were committed together. An absent marker means no items were delivered. */
    public void recover(String id, EntityPlayerMP player) throws IOException {
        NBTTagCompound record = player(id, player.getUniqueID());
        if (!record.hasKey("Transfer")) return;
        NBTTagCompound transfer = record.getCompoundTag("Transfer");
        if (marker(player).getString("Token").equals(transfer.getString("Token"))) record.setTag("Pending", transfer.getTag("Remaining").copy());
        record.removeTag("Transfer"); put(id, player.getUniqueID(), record, false);
    }
    public void reset(String id) throws IOException {
        NBTTagCompound next = (NBTTagCompound)state.copy(), history = next.getCompoundTag("Phrases").getCompoundTag(id);
        if (!next.getCompoundTag("Phrases").hasKey(id)) return;
        history.setLong("Total", 0);
        NBTTagCompound players = history.getCompoundTag("Players");
        for (Object key : players.func_150296_c()) {
            NBTTagCompound player = players.getCompoundTag((String)key);
            player.setLong("Count", 0); player.setLong("Last", 0); // Never discard escrow or an in-flight receipt.
        }
        commit(next);
    }
    private void commit(NBTTagCompound next) throws IOException {
        validate(next);
        try { world.getSaveHandler().checkSessionLock(); }
        catch (net.minecraft.world.MinecraftException e) { throw new IOException("World session lock failed", e); }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(next, bytes);
        atomic(file, bytes.toByteArray()); state = next;
    }
    static void atomic(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent()); Path temporary = target.resolveSibling(target.getFileName() + ".chatrewards.tmp");
        try (FileOutputStream out = new FileOutputStream(temporary.toFile())) { out.write(bytes); out.getFD().sync(); }
        // No delete/rename fallback: retain the previous complete file if atomic replacement is unsupported.
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    private static void validate(NBTTagCompound state) {
        if (state.getInteger("Version") != 1 || !state.hasKey("Phrases", 10)) throw new IllegalStateException("ledger");
        NBTTagCompound phrases = state.getCompoundTag("Phrases");
        for (Object key : phrases.func_150296_c()) {
            String id = (String)key;
            if (!id.matches("[a-z0-9_-]{1,48}") || !phrases.hasKey(id, 10)) throw new IllegalStateException("ledger");
            NBTTagCompound history = phrases.getCompoundTag(id);
            if (!history.hasKey("Total", 4) || history.getLong("Total") < 0 || !history.hasKey("Players", 10)) throw new IllegalStateException("ledger");
            NBTTagCompound players = history.getCompoundTag("Players"); long count = 0;
            for (Object name : players.func_150296_c()) {
                UUID.fromString((String)name);
                if (!players.hasKey((String)name, 10)) throw new IllegalStateException("ledger");
                NBTTagCompound player = players.getCompoundTag((String)name);
                if (!player.hasKey("Count", 4) || player.getLong("Count") < 0 || !player.hasKey("Last", 4)
                    || player.getLong("Last") < 0) throw new IllegalStateException("ledger");
                count = Math.addExact(count, player.getLong("Count")); validateItems(player, "Pending");
                if (player.hasKey("Transfer")) {
                    if (!player.hasKey("Transfer", 10)) throw new IllegalStateException("ledger");
                    NBTTagCompound transfer = player.getCompoundTag("Transfer"); UUID.fromString(transfer.getString("Token"));
                    validateItems(transfer, "Remaining");
                }
            }
            if (count != history.getLong("Total")) throw new IllegalStateException("ledger");
        }
    }
    private static void validateItems(NBTTagCompound record, String key) {
        if (!record.hasKey(key, 9)) throw new IllegalStateException("escrow");
        NBTTagList items = (NBTTagList)record.getTag(key);
        if (items.tagCount() > 8192 || items.tagCount() > 0 && items.func_150303_d() != 10) throw new IllegalStateException("escrow");
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound raw = items.getCompoundTagAt(i);
            if (!raw.hasKey("id", 2) || !raw.hasKey("Damage", 2) || !raw.hasKey("Count", 1)
                || raw.getByte("Count") < 1 || raw.getByte("Count") > 64 || raw.getString("Registry").isEmpty()
                || raw.hasKey("tag") && !raw.hasKey("tag", 10)) throw new IllegalStateException("escrow");
        }
    }
    private static NBTTagCompound marker(EntityPlayer player) {
        NBTTagCompound persisted = player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey(MARKER)) return new NBTTagCompound();
        if (!persisted.hasKey(MARKER, 10)) throw new IllegalStateException("marker");
        NBTTagCompound marker = persisted.getCompoundTag(MARKER); validateMarker(marker); return marker;
    }
    private static void validateMarker(NBTTagCompound marker) {
        if (marker.getInteger("Version") != 1 || !marker.hasKey("Revision", 4) || marker.getLong("Revision") < 1)
            throw new IllegalStateException("marker");
        UUID.fromString(marker.getString("Token"));
    }
    private static void install(EntityPlayer player, NBTTagCompound marker) {
        NBTTagCompound persisted = player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (marker == null) persisted.removeTag(MARKER); else persisted.setTag(MARKER, marker);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }
    private static Path playerFile(EntityPlayer player) {
        return new File(MinecraftServer.getServer().worldServerForDimension(0).getSaveHandler().getWorldDirectory(),
            "playerdata/" + player.getUniqueID() + ".dat").toPath();
    }
    /** Caller restores inventory on failure; nothing after the atomic move may throw. */
    public static void commitInventory(EntityPlayerMP player, String token) throws IOException {
        NBTTagCompound previous = marker(player), next = new NBTTagCompound();
        next.setInteger("Version", 1); next.setLong("Revision", Math.addExact(previous.getLong("Revision"), 1)); next.setString("Token", token);
        install(player, next);
        try {
            NBTTagCompound full = new NBTTagCompound(); player.writeToNBT(full);
            player.worldObj.getSaveHandler().checkSessionLock();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(full, bytes);
            atomic(playerFile(player), bytes.toByteArray());
        } catch (Exception e) { install(player, previous.hasNoTags() ? null : previous); throw new IOException("Chat reward inventory save failed", e); }
    }
    /** Integrated-server level.dat may carry an older Player snapshot than the committed UUID file. */
    public static void reconcileLoad(EntityPlayer player) throws IOException {
        long revision = marker(player).getLong("Revision"); Path file = playerFile(player);
        if (!Files.exists(file)) return;
        NBTTagCompound full;
        try (FileInputStream in = new FileInputStream(file.toFile())) { full = CompressedStreamTools.readCompressed(in); }
        NBTTagCompound persisted = full.getCompoundTag("ForgeData").getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey(MARKER)) return;
        if (!persisted.hasKey(MARKER, 10)) throw new IOException("Malformed chat delivery marker");
        NBTTagCompound disk = persisted.getCompoundTag(MARKER); validateMarker(disk);
        if (disk.getLong("Revision") > revision) {
            if (full.getLong("UUIDMost") != player.getUniqueID().getMostSignificantBits()
                || full.getLong("UUIDLeast") != player.getUniqueID().getLeastSignificantBits()) throw new IOException("Chat reward UUID mismatch");
            player.readFromNBT(full);
        }
    }
}
