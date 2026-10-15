package com.hfr.rewards;

import com.hfr.command.MuteManager;
import com.hfr.tdm.TDMAdminKitEdit;
import com.hfr.tdm.TDMManager;
import com.hfr.tdm.TDMServerTaskQueue;
import com.hfr.util.XFLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

/** Packet matching is read-only; ledger, moderation checks and inventory mutation run on the server tick. */
public final class ChatRewardService {
    private static final ConcurrentMap<String, Object> QUEUED = new ConcurrentHashMap<String, Object>();
    private static final Map<EntityPlayer, Boolean> BLOCKED = new WeakHashMap<EntityPlayer, Boolean>();
    private static volatile Object session = new Object();
    public static void clear() { session = new Object(); QUEUED.clear(); BLOCKED.clear(); ChatRewardStore.release(); }
    public static void say(ICommandSender player, String key, Object... args) {
        player.addChatMessage(new ChatComponentTranslation("chatreward.hfr." + key, args));
    }
    /** True suppresses the entire packet before vanilla normalizes spaces or publishes any chat event. */
    public static boolean intercept(final EntityPlayerMP player, String message) {
        ChatRewardConfig.Phrase phrase = ChatRewardConfig.match(message);
        if (phrase == null) return false;
        final String id = phrase.id, key = player.getUniqueID() + ":" + id;
        final Object owner = session;
        if (QUEUED.putIfAbsent(key, owner) == null) {
            TDMServerTaskQueue.schedule(new Runnable() {
                public void run() {
                    try {
                        if (owner != session) return;
                        if (online(player)) redeem(player, id);
                    } finally { QUEUED.remove(key, owner); }
                }
            });
        }
        return true;
    }
    private static boolean online(EntityPlayerMP player) {
        return !player.isDead && player.playerNetServerHandler.netManager.isChannelOpen()
            && MinecraftServer.getServer().getConfigurationManager().playerEntityList.contains(player);
    }
    private static ChatRewardStore store(EntityPlayer player) {
        if (BLOCKED.containsKey(player)) throw new IllegalStateException("player");
        return ChatRewardStore.get();
    }
    private static void redeem(EntityPlayerMP player, String id) {
        try {
            if (MuteManager.isMuted(player.getUniqueID())) { say(player, "muted"); return; }
            ChatRewardStore store = store(player);
            recoverAll(store, player);
            NBTTagCompound record = store.player(id, player.getUniqueID());
            if (record.getTagList("Pending", 10).tagCount() > 0) { collect(player); return; }
            // Read the current definition on the server thread: an intervening disable/remove takes effect.
            ChatRewardConfig.Phrase phrase;
            try { phrase = ChatRewardConfig.active(id); }
            catch (IllegalArgumentException e) { say(player, "disabled"); return; }
            long now = System.currentTimeMillis(), count = record.getLong("Count"), total = store.total(id);
            if (!phrase.enabled) { say(player, "disabled"); return; }
            if (phrase.start > now) { say(player, "not_started"); return; }
            if (phrase.expiry != 0 && now >= phrase.expiry) { say(player, "expired"); return; }
            if (!phrase.repeatable && count > 0) { say(player, "already"); return; }
            long elapsed = now - record.getLong("Last"), cooldown = phrase.cooldownSeconds * 1000L;
            if (count > 0 && elapsed < cooldown) { say(player, "cooldown", (cooldown - elapsed + 999) / 1000); return; }
            if (count == Long.MAX_VALUE || total == Long.MAX_VALUE || phrase.totalLimit >= 0 && total >= phrase.totalLimit) {
                say(player, "limit"); return;
            }
            store.accept(phrase, player.getUniqueID(), now);
            XFLog.info("[ChatRewards] Redeemed phrase=" + id + " player=" + player.getUniqueID() + " count=" + (count + 1) + " total=" + (total + 1));
            say(player, "accepted");
            collect(player);
        } catch (IOException | RuntimeException e) { failure(player, e); }
    }
    /** Complete every older receipt before overwriting the player's single delivery marker. */
    private static void recoverAll(ChatRewardStore store, EntityPlayerMP player) throws IOException {
        for (String id : store.ids()) store.recover(id, player);
    }
    public static void collect(EntityPlayerMP player) {
        if (!online(player)) return;
        try {
            ChatRewardStore store = store(player); recoverAll(store, player);
            boolean pending = false;
            for (String id : store.ids()) if (store.player(id, player.getUniqueID()).getTagList("Pending", 10).tagCount() > 0) pending = true;
            if (!pending) { say(player, "nothing"); return; }
            if (TDMAdminKitEdit.isEditing(player) || TDMManager.isEnabled(player.worldObj)
                || player.openContainer != player.inventoryContainer || player.inventory.getItemStack() != null) {
                say(player, "deferred"); return;
            }
            boolean changed = false;
            for (String id : store.ids()) changed |= deliver(store, id, player);
            if (changed) { player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges(); say(player, "delivered"); }
            for (String id : store.ids()) if (store.player(id, player.getUniqueID()).getTagList("Pending", 10).tagCount() > 0) {
                say(player, "pending"); return;
            }
        } catch (IOException | RuntimeException e) {
            player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges(); failure(player, e);
        }
    }
    private static boolean deliver(ChatRewardStore store, String id, EntityPlayerMP player) throws IOException {
        NBTTagList pending = store.player(id, player.getUniqueID()).getTagList("Pending", 10);
        if (pending.tagCount() == 0) return false;
        ItemStack[] before = player.inventory.mainInventory.clone(), after = new ItemStack[before.length];
        for (int i = 0; i < after.length; i++) after[i] = ItemStack.copyItemStack(before[i]);
        NBTTagList remaining = new NBTTagList(); boolean changed = false;
        for (int i = 0; i < pending.tagCount(); i++) {
            NBTTagCompound raw = pending.getCompoundTagAt(i); String registry = raw.getString("Registry");
            if (!Item.itemRegistry.containsKey(registry)) { remaining.appendTag(raw.copy()); continue; }
            NBTTagCompound resolved = (NBTTagCompound)raw.copy();
            resolved.setShort("id", (short)Item.getIdFromItem((Item)Item.itemRegistry.getObject(registry)));
            ItemStack stack = ItemStack.loadItemStackFromNBT(resolved);
            if (stack == null || stack.getMaxStackSize() < 1) { remaining.appendTag(raw.copy()); continue; }
            int original = stack.stackSize;
            // Explicit transfer avoids creative mode's vanilla addItemStackToInventory leftover deletion.
            for (int pass = 0; pass < 2 && stack.stackSize > 0; pass++) {
                for (int slot = 0; slot < after.length && stack.stackSize > 0; slot++) {
                    ItemStack existing = after[slot];
                    if (pass == 0 && existing != null && existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack)) {
                        int move = Math.min(stack.stackSize, Math.max(0, Math.min(64, existing.getMaxStackSize()) - existing.stackSize));
                        existing.stackSize += move; stack.stackSize -= move;
                    } else if (pass == 1 && existing == null) {
                        int move = Math.min(stack.stackSize, Math.min(64, stack.getMaxStackSize()));
                        ItemStack part = stack.copy(); part.stackSize = move; after[slot] = part; stack.stackSize -= move;
                    }
                }
            }
            changed |= stack.stackSize != original;
            if (stack.stackSize > 0) {
                NBTTagCompound leftover = stack.writeToNBT(new NBTTagCompound()); leftover.setString("Registry", registry); remaining.appendTag(leftover);
            }
        }
        if (!changed) return false;
        String token = UUID.randomUUID().toString();
        store.prepare(id, player, remaining, token); // Durable intent precedes inventory mutation; original escrow stays intact.
        System.arraycopy(after, 0, player.inventory.mainInventory, 0, after.length);
        try { ChatRewardStore.commitInventory(player, token); }
        catch (IOException | RuntimeException e) {
            System.arraycopy(before, 0, player.inventory.mainInventory, 0, before.length); throw e;
        }
        // Failure here MUST NOT roll back committed inventory. recoverAll will recognize the durable marker.
        store.recover(id, player);
        return true;
    }
    private static void failure(EntityPlayer player, Exception e) {
        XFLog.error("Chat rewards unavailable for " + player.getUniqueID() + "; committed receipts and pending items retained", e);
        say(player, "unavailable");
    }
    @SubscribeEvent public void load(net.minecraftforge.event.entity.player.PlayerEvent.LoadFromFile event) {
        if (event.entityPlayer.worldObj.isRemote) return;
        try { ChatRewardStore.reconcileLoad(event.entityPlayer); }
        catch (Exception e) {
            BLOCKED.put(event.entityPlayer, true);
            // LoadFromFile precedes the network handler; defer player messaging until login.
            XFLog.error("Chat reward player snapshot could not be validated for " + event.playerUUID, e);
        }
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP)event.player;
        try {
            ChatRewardStore store = store(player); recoverAll(store, player);
            for (String id : store.ids()) if (store.player(id, player.getUniqueID()).getTagList("Pending", 10).tagCount() > 0) {
                say(player, "pending"); break;
            }
        } catch (IOException | RuntimeException e) { failure(player, e); }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { BLOCKED.remove(event.player); }
    @SubscribeEvent public void clone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        if (BLOCKED.containsKey(event.original)) BLOCKED.put(event.entityPlayer, true);
        // The receipt marker follows Forge's PlayerPersisted clone path; eligibility and escrow stay in the ledger.
    }
}
