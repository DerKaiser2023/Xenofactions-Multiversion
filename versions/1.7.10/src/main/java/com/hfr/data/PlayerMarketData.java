package com.hfr.data;

import com.hfr.util.XFLog;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

/** One overworld-owned store, including durable receipts for unloaded legacy trader chunks. */
public final class PlayerMarketData {
    private static PlayerMarketData current;
    private final World world;
    private final Path file;
    private NBTTagCompound state = new NBTTagCompound();
    private boolean writable;

    private PlayerMarketData(World world) {
        this.world = world;
        file = new File(world.getSaveHandler().getWorldDirectory(), "data/hfr_player_market.dat").toPath();
        try {
            if (Files.exists(file)) {
                try (FileInputStream in = new FileInputStream(file.toFile())) {
                    state = CompressedStreamTools.readCompressed(in);
                }
                if (state.getInteger("Version") != 1 || !state.hasKey("Offers", 10) || !state.hasKey("Imports", 10))
                    throw new IOException("Unsupported or malformed player market data");
                NBTTagCompound offers = state.getCompoundTag("Offers");
                for (Object key : offers.func_150296_c()) {
                    UUID.fromString((String)key);
                    if (!offers.hasKey((String)key, 10)) throw new IOException("Invalid market offer");
                    NBTTagCompound offer = offers.getCompoundTag((String)key);
                    UUID.fromString(offer.getString("Owner"));
                    validateList(offer, "Stock"); validateList(offer, "Payments");
                }
                NBTTagCompound imports = state.getCompoundTag("Imports");
                for (Object key : imports.func_150296_c()) {
                    if (!imports.hasKey((String)key, 10)) throw new IOException("Invalid migration receipt");
                    NBTTagCompound receipt = imports.getCompoundTag((String)key);
                    if (!receipt.hasKey("Archive", 10) || !receipt.hasKey("Offer", 8))
                        throw new IOException("Incomplete migration receipt");
                    if (!receipt.getString("Offer").isEmpty()) UUID.fromString(receipt.getString("Offer"));
                }
            } else {
                state.setInteger("Version", 1);
                state.setTag("Offers", new NBTTagCompound());
                state.setTag("Imports", new NBTTagCompound());
            }
            writable = true;
        } catch (Exception e) {
            XFLog.error("Player market disabled; original file and trader inventories preserved", e);
        }
    }

    public static PlayerMarketData get() {
        World world = MinecraftServer.getServer().worldServerForDimension(0);
        if (world == null) throw new IllegalStateException("Overworld is unavailable");
        if (current == null || current.world != world) current = new PlayerMarketData(world);
        current.requireWritable();
        return current;
    }
    public static void release() { current = null; }
    private static void validateList(NBTTagCompound tag, String key) throws IOException {
        if (!tag.hasKey(key, 9)) throw new IOException("Missing offer inventory: " + key);
        NBTTagList items = (NBTTagList)tag.getTag(key);
        if (items.tagCount() > 0 && items.func_150303_d() != 10) throw new IOException("Invalid offer inventory: " + key);
    }
    private void requireWritable() {
        if (!writable) throw new IllegalStateException("Player market data is unavailable; repair the saved data and restart.");
    }
    public NBTTagCompound offers() { return (NBTTagCompound)state.getCompoundTag("Offers").copy(); }
    public NBTTagCompound offer(String id) {
        NBTTagCompound offers = state.getCompoundTag("Offers");
        return offers.hasKey(id, 10) ? (NBTTagCompound)offers.getCompoundTag(id).copy() : null;
    }
    public NBTTagCompound receipt(String source) {
        NBTTagCompound imports = state.getCompoundTag("Imports");
        return imports.hasKey(source, 10) ? (NBTTagCompound)imports.getCompoundTag(source).copy() : null;
    }
    public void put(String id, NBTTagCompound offer) throws IOException {
        NBTTagCompound next = (NBTTagCompound)state.copy();
        if (offer == null) next.getCompoundTag("Offers").removeTag(id);
        else next.getCompoundTag("Offers").setTag(id, offer);
        commit(next);
    }
    public void importLegacy(String source, NBTTagCompound archive, String id, NBTTagCompound offer) throws IOException {
        if (receipt(source) != null) throw new IOException("Legacy source already imported");
        NBTTagCompound next = (NBTTagCompound)state.copy();
        NBTTagCompound receipt = new NBTTagCompound();
        receipt.setTag("Archive", archive.copy()); receipt.setString("Offer", id);
        next.getCompoundTag("Imports").setTag(source, receipt);
        if (offer != null) next.getCompoundTag("Offers").setTag(id, offer);
        commit(next);
    }
    private void commit(NBTTagCompound next) throws IOException {
        requireWritable();
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling("hfr_player_market.dat.tmp");
        try (FileOutputStream out = new FileOutputStream(temporary.toFile())) {
            // writeCompressed closes its stream; sync the completed file through a separate descriptor below.
            CompressedStreamTools.writeCompressed(next, out);
        }
        try (FileOutputStream out = new FileOutputStream(temporary.toFile(), true)) { out.getFD().sync(); }
        // Refuse a non-atomic replacement: an unsuccessful transaction must retain its old store.
        try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { throw new IOException("Player market requires atomic file replacement", e); }
        state = next;
    }
}
