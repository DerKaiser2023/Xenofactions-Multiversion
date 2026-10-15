package com.hfr.daily;

import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.daily.DailySnapshotPacket;
import com.hfr.tdm.TDMAdminKitEdit;
import com.hfr.tdm.TDMManager;
import com.hfr.util.XFLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

/** All state, selection and inventory mutation is confined to the owning server thread. */
public final class DailyRewardService {
    private static final Map<EntityPlayerMP, Session> SESSIONS = new WeakHashMap<EntityPlayerMP, Session>();
    private static final Map<EntityPlayer, Boolean> BLOCKED = new WeakHashMap<EntityPlayer, Boolean>();
    private static final Random RANDOM = new Random();
    private int ticks;
    private static final class Session {
        final String token = UUID.randomUUID().toString();
        long revision, day;
    }
    private static long today() { return LocalDate.now(DailyRewardConfig.zone).toEpochDay(); }
    private static boolean eligible(NBTTagCompound state, long day) { return !state.hasKey("Day") || state.getLong("Day") < day; }
    private static int nextStreak(NBTTagCompound state, long day) {
        return state.hasKey("Day") && state.getLong("Day") == day - 1
            ? state.getInteger("Streak") == Integer.MAX_VALUE ? Integer.MAX_VALUE : state.getInteger("Streak") + 1 : 1;
    }
    private static void say(EntityPlayer player, String key, Object... args) { player.addChatMessage(new ChatComponentTranslation("daily.hfr." + key, args)); }
    private static NBTTagCompound state(EntityPlayer player) {
        if (BLOCKED.containsKey(player)) throw new IllegalStateException("Daily player data failed to load");
        return DailyRewardStore.read(player);
    }
    public static void clear() { SESSIONS.clear(); BLOCKED.clear(); }
    public static void open(EntityPlayerMP player) {
        if (!online(player)) return;
        player.closeScreen();
        Session session = new Session(); SESSIONS.put(player, session);
        snapshot(player, session, true, false);
    }
    private static boolean online(EntityPlayerMP player) {
        return !player.isDead && MinecraftServer.getServer().getConfigurationManager().playerEntityList.contains(player)
            && player.playerNetServerHandler.netManager.isChannelOpen();
    }
    public static void action(EntityPlayerMP player, String token, long revision, int action) {
        Session session = SESSIONS.get(player);
        if (session == null || !session.token.equals(token) || !online(player)) return;
        if (action == 0) { SESSIONS.remove(player); return; }
        if (revision != session.revision) return;
        session.revision++; // Every intent is single use, even a rejected claim or failed save.
        boolean spin = false;
        try {
            NBTTagCompound saved = state(player);
            if (action == 1) {
                long day = today();
                if (!DailyRewardConfig.ready) say(player, "disabled");
                else if (!eligible(saved, day)) say(player, "claimed");
                else if (saved.getTagList("Pending", 10).tagCount() > 0) say(player, "collect_first");
                else {
                    int chosen = RANDOM.nextInt(DailyRewardConfig.totalWeight), selected = 0;
                    for (; selected < DailyRewardConfig.rewards.size() - 1; selected++) {
                        chosen -= DailyRewardConfig.rewards.get(selected).weight;
                        if (chosen < 0) break;
                    }
                    DailyRewardConfig.Reward reward = DailyRewardConfig.rewards.get(selected);
                    NBTTagList pending = saved.getTagList("Pending", 10), wheel = new NBTTagList();
                    for (DailyRewardConfig.Reward entry : DailyRewardConfig.rewards) wheel.appendTag(entry.preview());
                    reward.append(pending);
                    int streak = nextStreak(saved, day);
                    NBTTagCompound reached = saved.getCompoundTag("Milestones");
                    DailyRewardConfig.Reward bonus = DailyRewardConfig.milestones.get(streak);
                    if (bonus != null && !reached.hasKey(Integer.toString(streak))) {
                        bonus.append(pending); reached.setBoolean(Integer.toString(streak), true);
                        saved.setTag("Bonus", bonus.preview());
                    } else saved.removeTag("Bonus");
                    if (pending.tagCount() > 8192) throw new IllegalStateException("Daily escrow capacity exceeded");
                    saved.setLong("Day", day); saved.setInteger("Streak", streak);
                    saved.setString("Claim", UUID.randomUUID().toString()); saved.setTag("Result", reward.preview());
                    saved.setTag("Wheel", wheel); saved.setInteger("Selected", selected);
                    saved.setTag("Pending", pending); saved.setTag("Milestones", reached);
                    // Acceptance is durable BEFORE any inventory grant or client animation.
                    DailyRewardStore.commit(player, saved); spin = true;
                    deliver(player);
                }
            } else if (action == 2) deliver(player);
        } catch (IOException e) {
            XFLog.error("Daily reward save failed for " + player.getUniqueID() + "; uncommitted changes rolled back", e);
            say(player, "save_failed");
        } catch (IllegalStateException e) {
            XFLog.error("Daily rewards unavailable for " + player.getUniqueID(), e); say(player, "unavailable");
        }
        snapshot(player, session, false, spin);
    }
    /** Never use vanilla addItemStackToInventory: creative mode can consume leftovers there. */
    private static void deliver(EntityPlayerMP player) throws IOException {
        NBTTagCompound saved = state(player); NBTTagList pending = saved.getTagList("Pending", 10);
        if (pending.tagCount() == 0) return;
        if (TDMAdminKitEdit.isEditing(player) || TDMManager.isEnabled(player.worldObj)
            || player.openContainer != player.inventoryContainer || player.inventory.getItemStack() != null) {
            say(player, "delivery_deferred"); return;
        }
        ItemStack[] before = new ItemStack[player.inventory.mainInventory.length];
        for (int i = 0; i < before.length; i++) before[i] = ItemStack.copyItemStack(player.inventory.mainInventory[i]);
        NBTTagList remaining = new NBTTagList(); boolean changed = false, missing = false;
        try {
            for (int i = 0; i < pending.tagCount(); i++) {
                NBTTagCompound raw = pending.getCompoundTagAt(i);
                String registry = raw.getString("Registry");
                if (registry.isEmpty() || !Item.itemRegistry.containsKey(registry)) {
                    remaining.appendTag(raw.copy()); missing = true; continue;
                }
                NBTTagCompound resolved = (NBTTagCompound)raw.copy();
                resolved.setShort("id", (short)Item.getIdFromItem((Item)Item.itemRegistry.getObject(registry)));
                ItemStack stack = ItemStack.loadItemStackFromNBT(resolved);
                if (stack == null) { remaining.appendTag(raw.copy()); missing = true; continue; }
                int original = stack.stackSize;
                for (int pass = 0; pass < 2 && stack.stackSize > 0; pass++) {
                    for (int slot = 0; slot < before.length && stack.stackSize > 0; slot++) {
                        ItemStack existing = player.inventory.mainInventory[slot];
                        if (pass == 0 && existing != null && existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack)) {
                            int move = Math.min(stack.stackSize, Math.max(0, Math.min(64, existing.getMaxStackSize()) - existing.stackSize));
                            existing.stackSize += move; stack.stackSize -= move;
                        } else if (pass == 1 && existing == null) {
                            int move = Math.min(stack.stackSize, Math.min(64, stack.getMaxStackSize()));
                            if (move > 0) { ItemStack part = stack.copy(); part.stackSize = move; player.inventory.mainInventory[slot] = part; stack.stackSize -= move; }
                        }
                    }
                }
                changed |= stack.stackSize != original;
                if (stack.stackSize > 0) {
                    NBTTagCompound leftover = stack.writeToNBT(new NBTTagCompound()); leftover.setString("Registry", registry);
                    remaining.appendTag(leftover);
                }
            }
            if (changed) {
                saved.setTag("Pending", remaining);
                // Both the inventory afterimage and reduced escrow become visible in one atomic file replacement.
                DailyRewardStore.commit(player, saved);
            }
        } catch (IOException | RuntimeException e) {
            for (int slot = 0; slot < before.length; slot++) player.inventory.mainInventory[slot] = before[slot];
            throw e;
        }
        if (changed) { player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges(); say(player, "delivered"); }
        if (remaining.tagCount() > 0) say(player, missing ? "missing_item" : "inventory_full");
    }
    private static void snapshot(EntityPlayerMP player, Session session, boolean opening, boolean spin) {
        NBTTagCompound out = new NBTTagCompound();
        out.setString("token", session.token); out.setLong("revision", ++session.revision);
        out.setBoolean("opening", opening); out.setBoolean("spin", spin);
        long day = today(); session.day = day;
        out.setString("reset", LocalDate.ofEpochDay(day).plusDays(1).atStartOfDay(DailyRewardConfig.zone)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")));
        try {
            NBTTagCompound saved = state(player);
            int current = saved.hasKey("Day") && saved.getLong("Day") >= day - 1 ? saved.getInteger("Streak") : 0;
            out.setInteger("streak", current);
            out.setInteger("nextStreak", eligible(saved, day) ? nextStreak(saved, day) : current == Integer.MAX_VALUE ? current : current + 1);
            out.setBoolean("eligible", eligible(saved, day) && DailyRewardConfig.ready);
            out.setInteger("pending", saved.getTagList("Pending", 10).tagCount());
            out.setBoolean("disabled", !DailyRewardConfig.ready);
            out.setString("claim", saved.getString("Claim"));
            if (saved.hasKey("Result")) out.setTag("result", saved.getTag("Result").copy());
            if (saved.hasKey("Bonus")) out.setTag("bonus", saved.getTag("Bonus").copy());
            NBTTagList wheel = new NBTTagList();
            if (saved.hasKey("Day") && saved.getLong("Day") == day) {
                wheel = saved.getTagList("Wheel", 10); out.setInteger("selected", saved.getInteger("Selected"));
            } else {
                for (DailyRewardConfig.Reward reward : DailyRewardConfig.rewards) wheel.appendTag(reward.preview());
                out.setInteger("selected", -1);
            }
            out.setTag("wheel", wheel);
            for (Map.Entry<Integer, DailyRewardConfig.Reward> milestone : DailyRewardConfig.milestones.entrySet()) {
                if (milestone.getKey() > current && !saved.getCompoundTag("Milestones").hasKey(milestone.getKey().toString())) {
                    out.setInteger("milestone", milestone.getKey()); out.setTag("milestoneReward", milestone.getValue().preview()); break;
                }
            }
        } catch (IllegalStateException e) { out.setBoolean("unavailable", true); }
        PacketDispatcher.wrapper.sendTo(new DailySnapshotPacket(out), player);
    }
    @SubscribeEvent public void load(net.minecraftforge.event.entity.player.PlayerEvent.LoadFromFile event) {
        if (event.entityPlayer.worldObj.isRemote) return;
        try { DailyRewardStore.reconcileLoad(event.entityPlayer); state(event.entityPlayer); }
        catch (Exception e) { BLOCKED.put(event.entityPlayer, true); XFLog.error("Daily player data could not be validated; claims disabled for " + event.playerUUID, e); }
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        try {
            NBTTagCompound saved = state(event.player);
            if (DailyRewardConfig.ready && eligible(saved, today())) say(event.player, "login");
            if (saved.getTagList("Pending", 10).tagCount() > 0) say(event.player, "pending");
        } catch (IllegalStateException e) { say(event.player, "unavailable"); }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { SESSIONS.remove(event.player); BLOCKED.remove(event.player); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { SESSIONS.remove(event.player); }
    @SubscribeEvent public void clone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        SESSIONS.remove(event.original);
        if (BLOCKED.containsKey(event.original)) BLOCKED.put(event.entityPlayer, true);
        // Forge clonePlayer already carries PlayerPersisted, including escrow and milestone receipts.
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 20 != 0 || SESSIONS.isEmpty()) return;
        long day = today();
        for (Map.Entry<EntityPlayerMP, Session> entry : SESSIONS.entrySet())
            if (entry.getValue().day != day && online(entry.getKey())) snapshot(entry.getKey(), entry.getValue(), false, false);
    }
}
