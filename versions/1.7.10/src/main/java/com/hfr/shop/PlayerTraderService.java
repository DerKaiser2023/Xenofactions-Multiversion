package com.hfr.shop;

import com.hfr.blocks.machine.PlayerTrader.Tile;
import com.hfr.clowder.Clowder;
import com.hfr.clowder.ClowderTerritory;
import com.hfr.clowder.ClowderTerritory.Ownership;
import com.hfr.clowder.ClowderTerritory.Zone;
import com.hfr.clowder.FactionPermission;
import com.hfr.data.PlayerMarketData;
import com.hfr.items.ModItems;
import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.shop.PlayerTraderSnapshotPacket;
import com.hfr.tdm.TDMAdminKitEdit;
import com.hfr.util.XFLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;

/** All inventory mutations and durable market commits run on the owning server thread. */
public final class PlayerTraderService {
    public static final int PAGE_SIZE = 3;
    private static final Map<EntityPlayerMP, Session> SESSIONS = new WeakHashMap<EntityPlayerMP, Session>();
    private static final class Session {
        final Tile tile;
        final String token = UUID.randomUUID().toString();
        long revision;
        int page, view; // 0: market, 1: own inventories, 2: administrator inventories
        boolean creating;
        ItemStack sale, payment;
        int saleQuantity = 1, paymentQuantity = 1;
        final List<String> rows = new ArrayList<String>();
        Session(Tile tile) { this.tile = tile; }
    }
    private PlayerTraderService() { }
    public static void logout(EntityPlayer player) { SESSIONS.remove(player); }
    public static void clearSessions() { SESSIONS.clear(); PlayerMarketData.release(); }
    public static void broken(Tile tile) {
        for (Iterator<Map.Entry<EntityPlayerMP, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<EntityPlayerMP, Session> entry = it.next();
            if (entry.getValue().tile == tile) { close(entry.getKey(), entry.getValue()); it.remove(); }
        }
    }
    private static void close(EntityPlayerMP player, Session session) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("token", session.token); tag.setBoolean("closed", true);
        PacketDispatcher.wrapper.sendTo(new PlayerTraderSnapshotPacket(tag), player);
    }
    private static boolean admin(EntityPlayer player) { return player.canCommandSenderUseCommand(3, "xshop"); }
    private static boolean manager(EntityPlayer player, NBTTagCompound offer) {
        return admin(player) || offer.getString("Owner").equals(player.getUniqueID().toString());
    }
    public static boolean mayAccess(EntityPlayer player, int x, int z) {
        if (player == null || player.worldObj == null) return false;
        if (admin(player) || player.inventory.hasItem(ModItems.debug)) return true;
        Ownership land = ClowderTerritory.getOwnerFromInts(player.worldObj, x, z);
        if (land == null || land.zone != Zone.FACTION) return true;
        Clowder faction = Clowder.getClowderFromPlayer(player);
        if (land.owner == null) return false;
        if (faction == land.owner) return !land.owner.isInfrastructureDisabled();
        return land.owner.canVisitorAccess(faction, FactionPermission.INTERACT);
    }
    private static boolean near(EntityPlayerMP player, Tile tile) {
        return !player.isDead && !TDMAdminKitEdit.isEditing(player) && tile != null && tile.valid()
            && player.worldObj == tile.getWorldObj()
            && player.getDistanceSq(tile.xCoord + .5, tile.yCoord + .5, tile.zCoord + .5) <= 64
            && mayAccess(player, tile.xCoord, tile.zCoord);
    }
    public static void open(EntityPlayerMP player, Tile tile) {
        if (!near(player, tile)) return;
        String problem = migrate(tile);
        if (problem != null) say(player, problem);
        try {
            PlayerMarketData.get();
            Session session = new Session(tile); SESSIONS.put(player, session); snapshot(player, session, true);
        } catch (IllegalStateException e) { say(player, e.getMessage()); }
    }
    public static void action(EntityPlayerMP player, String token, String action, long revision, int slot, int quantity) {
        Session session = SESSIONS.get(player);
        if (session == null || !session.token.equals(token)) return;
        if ("close".equals(action)) { SESSIONS.remove(player); return; }
        if (!near(player, session.tile)) { SESSIONS.remove(player); close(player, session); return; }
        try {
            PlayerMarketData data = PlayerMarketData.get();
            if (revision != session.revision) {
                say(player, "The market changed. Review the refreshed offers."); snapshot(player, session, false); return;
            }
            // Consume every intent, including failed requests. Replaying a packet cannot perform it twice.
            session.revision++;
            String error = null;
            boolean changed = false;
            if ("create".equals(action)) session.creating = true;
            else if ("market".equals(action) || "mine".equals(action) || "admin".equals(action)) {
                session.creating = false; session.page = 0;
                session.view = "mine".equals(action) ? 1 : "admin".equals(action) && admin(player) ? 2 : 0;
            } else if ("page".equals(action)) session.page = Math.max(0, slot);
            else if (session.creating) {
                if ("sale".equals(action) || "payment".equals(action)) error = configure(player, session, action, slot, quantity);
                else if ("publish".equals(action)) {
                    error = publish(player, session, data, quantity);
                    changed = error == null;
                    if (changed) { session.creating = false; session.view = 1; session.page = 0; }
                }
            } else if (slot >= 0 && slot < session.rows.size()) {
                String id = session.rows.get(slot);
                NBTTagCompound offer = data.offer(id);
                if (offer == null) error = "That offer is no longer available.";
                else if ("buy".equals(action)) { error = buy(player, data, id, offer); changed = error == null; }
                else if (manager(player, offer)) {
                    if ("cancel".equals(action)) {
                        offer.setBoolean("Active", false); data.put(id, offer); changed = true;
                    } else if ("stock".equals(action) || "collect".equals(action)) {
                        error = withdraw(player, data, id, offer, "stock".equals(action)); changed = error == null;
                    }
                } else error = "You do not own that offer.";
            }
            if (error != null) say(player, error);
            if (changed) refresh(); else snapshot(player, session, false);
        } catch (IOException e) {
            XFLog.error("Player market commit failed; transaction not applied to player inventory", e);
            say(player, "Could not save the market. Your inventory was not changed.");
            snapshot(player, session, false);
        } catch (IllegalStateException e) { say(player, e.getMessage()); SESSIONS.remove(player); close(player, session); }
    }
    private static void say(EntityPlayer player, String message) {
        player.addChatMessage(new ChatComponentText("[Player Trader] " + message));
    }
    private static void snapshot(EntityPlayerMP player, Session session, boolean opening) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("token", session.token); tag.setBoolean("opening", opening);
        tag.setLong("revision", ++session.revision); tag.setBoolean("admin", admin(player));
        tag.setBoolean("creating", session.creating); tag.setInteger("view", session.view);
        tag.setInteger("saleQuantity", session.saleQuantity); tag.setInteger("paymentQuantity", session.paymentQuantity);
        template(tag, "sale", session.sale); template(tag, "payment", session.payment);
        NBTTagCompound offers = PlayerMarketData.get().offers();
        List<String> ids = new ArrayList<String>();
        for (Object key : offers.func_150296_c()) {
            String id = (String)key; NBTTagCompound offer = offers.getCompoundTag(id);
            if (session.view == 2 && admin(player) || session.view == 1 && managerOwner(player, offer)
                || session.view == 0 && available(offer) > 0) ids.add(id);
        }
        Collections.sort(ids);
        int pages = Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        session.page = Math.min(session.page, pages - 1);
        tag.setInteger("page", session.page); tag.setInteger("pages", pages); tag.setInteger("total", ids.size());
        NBTTagList rows = new NBTTagList(); session.rows.clear();
        for (int i = session.page * PAGE_SIZE; i < Math.min(ids.size(), (session.page + 1) * PAGE_SIZE); i++) {
            String id = ids.get(i); NBTTagCompound offer = offers.getCompoundTag(id), row = new NBTTagCompound();
            row.setString("seller", offer.getString("Seller")); row.setString("owner", offer.getString("Owner"));
            row.setBoolean("manager", manager(player, offer)); row.setBoolean("active", offer.getBoolean("Active"));
            row.setInteger("saleQuantity", offer.getInteger("SaleQuantity"));
            row.setInteger("paymentQuantity", offer.getInteger("PaymentQuantity"));
            if (offer.hasKey("Sale", 10)) row.setTag("sale", offer.getCompoundTag("Sale").copy());
            if (offer.hasKey("Payment", 10)) row.setTag("payment", offer.getCompoundTag("Payment").copy());
            row.setInteger("available", available(offer));
            row.setInteger("stock", total(offer.getTagList("Stock", 10)));
            row.setInteger("payments", total(offer.getTagList("Payments", 10)));
            rows.appendTag(row); session.rows.add(id);
        }
        tag.setTag("offers", rows);
        PacketDispatcher.wrapper.sendTo(new PlayerTraderSnapshotPacket(tag), player);
    }
    private static boolean managerOwner(EntityPlayer player, NBTTagCompound offer) {
        return player.getUniqueID().toString().equals(offer.getString("Owner"));
    }
    private static void refresh() {
        for (Iterator<Map.Entry<EntityPlayerMP, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<EntityPlayerMP, Session> entry = it.next();
            if (!near(entry.getKey(), entry.getValue().tile)) { close(entry.getKey(), entry.getValue()); it.remove(); }
            else snapshot(entry.getKey(), entry.getValue(), false);
        }
    }
    private static String configure(EntityPlayerMP player, Session session, String action, int slot, int quantity) {
        if (slot < 0 || slot >= 9 || quantity <= 0 || quantity > 576)
            return "Choose hotbar slot 1-9 and a quantity from 1 to 576.";
        ItemStack source = player.inventory.mainInventory[slot];
        if (source == null || source.stackSize <= 0) return "That hotbar slot is empty.";
        if (quantity > 9 * Math.min(64, source.getMaxStackSize())) return "That quantity exceeds offer storage capacity.";
        ItemStack template = source.copy(); template.stackSize = 1;
        if ("sale".equals(action)) { session.sale = template; session.saleQuantity = quantity; }
        else { session.payment = template; session.paymentQuantity = quantity; }
        return null;
    }
    private static String publish(EntityPlayerMP player, Session session, PlayerMarketData data, int lots) throws IOException {
        if (session.sale == null || session.payment == null) return "Set both the sale and payment first.";
        if (lots < 1 || lots > 576 || (long)lots * session.saleQuantity > 9 * Math.min(64, session.sale.getMaxStackSize()))
            return "Choose a positive lot count that fits the offer's nine stock slots.";
        ItemStack[] inventory = copy(player.inventory.mainInventory), stock = new ItemStack[9];
        int count = lots * session.saleQuantity;
        if (!remove(inventory, session.sale, count)) return "You do not have enough sale items.";
        if (!insert(stock, session.sale, count)) return "Offer storage is full.";
        NBTTagCompound offer = new NBTTagCompound();
        offer.setString("Owner", player.getUniqueID().toString()); offer.setString("Seller", player.getCommandSenderName());
        offer.setBoolean("Active", true); template(offer, "Sale", session.sale); template(offer, "Payment", session.payment);
        offer.setInteger("SaleQuantity", session.saleQuantity); offer.setInteger("PaymentQuantity", session.paymentQuantity);
        offer.setTag("Stock", list(stock)); offer.setTag("Payments", new NBTTagList());
        data.put(UUID.randomUUID().toString(), offer);
        apply(player, inventory); say(player, "Offer published; sale items reserved."); return null;
    }
    private static String buy(EntityPlayerMP player, PlayerMarketData data, String id, NBTTagCompound offer) throws IOException {
        ItemStack sale = item(offer, "Sale"), payment = item(offer, "Payment");
        int saleQuantity = offer.getInteger("SaleQuantity"), price = offer.getInteger("PaymentQuantity");
        if (available(offer) <= 0 || sale == null || payment == null) return "That offer is unavailable.";
        ItemStack[] stock = read(offer.getTagList("Stock", 10)), payments = read(offer.getTagList("Payments", 10));
        ItemStack[] inventory = copy(player.inventory.mainInventory);
        if (!remove(stock, sale, saleQuantity)) return "Insufficient stock.";
        if (!remove(inventory, payment, price)) return "Insufficient payment.";
        if (!insert(inventory, sale, saleQuantity)) return "Your inventory is full.";
        if (!insert(payments, payment, price)) return "Seller payment storage is full.";
        offer.setTag("Stock", list(stock)); offer.setTag("Payments", list(payments));
        data.put(id, offer); apply(player, inventory); say(player, "Purchase complete."); return null;
    }
    private static String withdraw(EntityPlayerMP player, PlayerMarketData data, String id, NBTTagCompound offer, boolean stock) throws IOException {
        String key = stock ? "Stock" : "Payments";
        ItemStack[] source = read(offer.getTagList(key, 10)), inventory = copy(player.inventory.mainInventory);
        if (total(offer.getTagList(key, 10)) == 0) return "That storage is empty.";
        for (ItemStack stack : source) if (stack != null && !insert(inventory, stack, stack.stackSize)) return "Your inventory is full.";
        offer.setTag(key, new NBTTagList());
        if (stock) offer.setBoolean("Active", false);
        boolean empty = total(offer.getTagList("Stock", 10)) == 0 && total(offer.getTagList("Payments", 10)) == 0;
        data.put(id, empty ? null : offer); apply(player, inventory); return null;
    }
    private static void apply(EntityPlayerMP player, ItemStack[] inventory) {
        System.arraycopy(inventory, 0, player.inventory.mainInventory, 0, inventory.length);
        player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges();
    }
    private static void template(NBTTagCompound tag, String key, ItemStack item) {
        if (item != null) { NBTTagCompound n = new NBTTagCompound(); item.writeToNBT(n); tag.setTag(key, n); }
    }
    private static ItemStack item(NBTTagCompound tag, String key) {
        return tag.hasKey(key, 10) ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag(key)) : null;
    }
    private static int total(NBTTagList list) {
        int total = 0;
        for (int i = 0; i < list.tagCount(); i++) total += Math.max(0, list.getCompoundTagAt(i).getByte("Count"));
        return total;
    }
    private static int available(NBTTagCompound offer) {
        if (!offer.getBoolean("Active")) return 0;
        ItemStack sale = item(offer, "Sale"), payment = item(offer, "Payment");
        int quantity = offer.getInteger("SaleQuantity"), price = offer.getInteger("PaymentQuantity");
        if (sale == null || payment == null || quantity < 1 || price < 1
            || quantity > 9 * Math.min(64, sale.getMaxStackSize()) || price > 9 * Math.min(64, payment.getMaxStackSize())) return 0;
        try {
            int count = 0;
            for (ItemStack stack : read(offer.getTagList("Stock", 10))) if (same(stack, sale)) count += stack.stackSize;
            read(offer.getTagList("Payments", 10)); // Missing mod items cannot be discarded by a later commit.
            return count / quantity;
        } catch (IllegalStateException e) { return 0; }
    }
    private static NBTTagList list(ItemStack[] items) {
        NBTTagList result = new NBTTagList();
        for (int i = 0; i < items.length; i++) if (items[i] != null) {
            NBTTagCompound n = new NBTTagCompound(); n.setByte("Slot", (byte)i); items[i].writeToNBT(n); result.appendTag(n);
        }
        return result;
    }
    private static ItemStack[] read(NBTTagList list) {
        ItemStack[] result = new ItemStack[9];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound n = list.getCompoundTagAt(i); int slot = n.getByte("Slot") & 255;
            ItemStack stack = ItemStack.loadItemStackFromNBT(n);
            if (slot >= 9 || result[slot] != null || stack == null || stack.stackSize <= 0
                || stack.stackSize > Math.min(64, stack.getMaxStackSize()))
                throw new IllegalStateException("Stored offer items are unavailable or invalid; inventory preserved.");
            result[slot] = stack;
        }
        return result;
    }
    /** Import only after an atomic durable write; keep the tile's original NBT as a sealed archive. */
    public static String migrate(Tile tile) {
        if (tile.legacyArchive == null || tile.getWorldObj() == null || tile.getWorldObj().isRemote) return null;
        try {
            PlayerMarketData data = PlayerMarketData.get();
            NBTTagCompound receipt = data.receipt(tile.legacySource());
            if (receipt != null) {
                if (!receipt.getCompoundTag("Archive").equals(tile.legacyArchive))
                    return migrationProblem(tile, "source receipt differs from the preserved tile inventory");
                tile.marketMigrated = true; tile.markDirty(); return null;
            }
            // A sealed tile can never republish its archive after data was removed/restored separately.
            if (tile.marketMigrated) return migrationProblem(tile, "migration receipt is missing; restore matching market data");
            if (!tile.legacyArchive.hasKey("Items", 9)) return migrationProblem(tile, "legacy inventory list is missing");
            NBTTagList rawItems = (NBTTagList)tile.legacyArchive.getTag("Items");
            if (rawItems.tagCount() > 0 && rawItems.func_150303_d() != 10)
                return migrationProblem(tile, "legacy inventory list has an invalid element type");
            NBTTagList old = tile.legacyArchive.getTagList("Items", 10), stock = new NBTTagList(), payments = new NBTTagList();
            for (int i = 0; i < old.tagCount(); i++) {
                NBTTagCompound n = (NBTTagCompound)old.getCompoundTagAt(i).copy(); int slot = n.getByte("Slot") & 255;
                if (slot >= 18) return migrationProblem(tile, "invalid legacy inventory slot");
                n.setByte("Slot", (byte)(slot % 9)); (slot < 9 ? stock : payments).appendTag(n);
            }
            read(stock); read(payments);
            NBTTagCompound offer = null; String id = "";
            if (old.tagCount() > 0) {
                String owner = tile.legacyArchive.getString("OwnerUUID"); UUID.fromString(owner);
                offer = new NBTTagCompound(); offer.setString("Owner", owner);
                offer.setString("Seller", owner); // No profile lookup or guessed offline identity.
                offer.setBoolean("Active", true);
                if (tile.legacyArchive.hasKey("Sale", 10)) offer.setTag("Sale", tile.legacyArchive.getCompoundTag("Sale").copy());
                if (tile.legacyArchive.hasKey("Payment", 10)) offer.setTag("Payment", tile.legacyArchive.getCompoundTag("Payment").copy());
                offer.setInteger("SaleQuantity", tile.legacyArchive.getInteger("SaleQuantity"));
                offer.setInteger("PaymentQuantity", tile.legacyArchive.getInteger("PaymentQuantity"));
                offer.setTag("Stock", stock); offer.setTag("Payments", payments);
                if (available(offer) == 0) offer.setBoolean("Active", false);
                id = UUID.randomUUID().toString();
            }
            data.importLegacy(tile.legacySource(), tile.legacyArchive, id, offer);
            tile.marketMigrated = true; tile.markDirty();
            XFLog.info("Player market imported legacy trader " + tile.legacySource() + "; original inventory archived"
                + (offer != null && !offer.getBoolean("Active") ? "; no complete purchasable lot, stored items remain reclaimable" : ""));
            refresh(); return null;
        } catch (Exception e) { return migrationProblem(tile, e.getMessage() == null ? e.toString() : e.getMessage()); }
    }
    private static String migrationProblem(Tile tile, String reason) {
        String problem = "Legacy trader " + tile.legacySource() + " preserved: " + reason;
        if (!problem.equals(tile.migrationProblem)) { XFLog.warn(problem); tile.migrationProblem = problem; }
        return problem;
    }
    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < result.length; i++) if (source[i] != null) result[i] = source[i].copy();
        return result;
    }
    private static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null && a.getItem() == b.getItem()
            && a.getItemDamage() == b.getItemDamage() && ItemStack.areItemStackTagsEqual(a, b);
    }
    private static boolean remove(ItemStack[] items, ItemStack template, int amount) {
        for (int i = 0; i < items.length && amount > 0; i++) if (same(items[i], template)) {
            int taken = Math.min(amount, items[i].stackSize); amount -= taken; items[i].stackSize -= taken;
            if (items[i].stackSize == 0) items[i] = null;
        }
        return amount == 0;
    }
    private static boolean insert(ItemStack[] items, ItemStack template, int amount) {
        for (int pass = 0; pass < 2 && amount > 0; pass++) for (int i = 0; i < items.length && amount > 0; i++) {
            ItemStack stack = items[i];
            if (pass == 0 && !same(stack, template) || pass == 1 && stack != null) continue;
            int limit = Math.min(64, template.getMaxStackSize());
            int space = stack == null ? limit : limit - stack.stackSize;
            if (space <= 0) continue;
            int added = Math.min(amount, space);
            if (stack == null) { items[i] = template.copy(); items[i].stackSize = added; }
            else stack.stackSize += added;
            amount -= added;
        }
        return amount == 0;
    }
}
