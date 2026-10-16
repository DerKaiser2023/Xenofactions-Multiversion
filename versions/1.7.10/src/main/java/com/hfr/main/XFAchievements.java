package com.hfr.main;

import com.hfr.blocks.BlockDummyable;
import com.hfr.blocks.ModBlocks;
import com.hfr.daily.DailyRewardConfig;
import com.hfr.daily.DailyRewardService;
import com.hfr.daily.DailyRewardStore;
import com.hfr.handler.MultiblockHandler;
import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.daily.DailySnapshotPacket;
import com.hfr.util.XFLog;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.AchievementPage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;

/** Native statistics remain authoritative; only successful XF actions need additional records. */
public final class XFAchievements {
    private static final List<Goal> GOALS = new ArrayList<Goal>();
    private static final Map<UUID, Set<String>> PENDING_ACTIONS = new HashMap<UUID, Set<String>>();
    private static final Set<UUID> FAILED = new HashSet<UUID>();
    private static final ArrayDeque<Runnable> ACTIONS = new ArrayDeque<Runnable>();
    private static Set<StatBase> miningStats;
    private int ticks;

    public static final Achievement windmill = goal("windmill", -1, 3, new ItemStack(ModBlocks.machine_grainmill), "acquired", 1);
    public static final Achievement fishingNet = goal("fishingNet", 3, 3, new ItemStack(ModBlocks.machine_net), "acquired", 1);
    public static final Achievement mining = goal("mine500", -3, 0, new ItemStack(Items.iron_pickaxe), "mining", 500);
    public static final Achievement kill1 = goal("kill1", -3, -3, new ItemStack(Items.iron_sword), "kills", 1);
    public static final Achievement kill20 = goal("kill20", -1, -3, new ItemStack(Items.iron_sword), "kills", 20);
    public static final Achievement kill50 = goal("kill50", 1, -3, new ItemStack(Items.iron_sword), "kills", 50);
    public static final Achievement kill100 = goal("kill100", 3, -3, new ItemStack(Items.iron_sword), "kills", 100);
    public static final Achievement kill150 = goal("kill150", -3, -5, new ItemStack(Items.diamond_sword), "kills", 150);
    public static final Achievement kill200 = goal("kill200", -1, -5, new ItemStack(Items.diamond_sword), "kills", 200);
    public static final Achievement kill500 = goal("kill500", 1, -5, new ItemStack(Items.diamond_sword), "kills", 500);
    public static final Achievement city = goal("city", -1, 0, new ItemStack(ModBlocks.clowder_flag), "action", 1);
    public static final Achievement statue = goal("statue", 1, 0, new ItemStack(ModBlocks.statue), "action", 1);
    public static final Achievement university = goal("university", 3, 0, new ItemStack(ModBlocks.machine_uni), "action", 1);
    public static final Achievement warpTent = goal("warpTent", -3, 5, new ItemStack(ModBlocks.tp_tent), "action", 1);
    public static final Achievement temple = goal("temple", -1, 5, new ItemStack(ModBlocks.machine_temple), "action", 1);
    public static final Achievement officerChest = goal("officerChest", 1, 5, new ItemStack(ModBlocks.officer_chest), "action", 1);
    public static final Achievement mapInspector = goal("mapInspector", 3, 5, new ItemStack(Items.map), "travel", 0);

    private static Achievement goal(String id, int x, int y, ItemStack icon, String source, long target) {
        Goal goal = new Goal(id, x, y, icon, source, target);
        GOALS.add(goal); return goal.registerStat();
    }
    private static final class Goal extends Achievement {
        final String id, source;
        final long amount;
        Goal(String id, int x, int y, ItemStack icon, String source, long amount) {
            super("achievement.hfr." + id, "hfr." + id, x, y, icon, null);
            this.id = id; this.source = source; this.amount = amount;
        }
        long target() { return source.equals("travel") ? 100L * DailyRewardConfig.achievementTravelBlocks : amount; }
        @Override @SideOnly(Side.CLIENT) public IChatComponent func_150951_e() {
            IChatComponent name = super.func_150951_e();
            if (FMLCommonHandler.instance().getEffectiveSide() != Side.CLIENT) return name;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            // Vanilla never wraps its title. Preserve the full name in the wrapped description and chat.
            if (mc.currentScreen instanceof net.minecraft.client.gui.achievement.GuiAchievements
                && mc.fontRenderer.getStringWidth(name.getUnformattedText()) > 160)
                return new ChatComponentText(mc.fontRenderer.trimStringToWidth(name.getUnformattedText(), 148) + "...");
            return name;
        }
        @Override @SideOnly(Side.CLIENT) public String getDescription() {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            String text = super.getDescription(), name = super.func_150951_e().getUnformattedText();
            if (mc.fontRenderer.getStringWidth(name) > 160) text = name + "\n" + text;
            if (mc.thePlayer == null || clientPlayer == null || clientPlayer.get() != mc.thePlayer || clientData == null)
                return text + "\n" + net.minecraft.client.resources.I18n.format("achievement.hfr.waiting");
            NBTTagCompound entry = clientData.getCompoundTag(id);
            long target = entry.getLong("target"), current = Math.min(target, entry.getLong("progress"));
            String progress = source.equals("travel") ? String.format(java.util.Locale.ROOT, "%.2f / %,d", current / 100D, target / 100)
                : String.format(java.util.Locale.ROOT, "%,d / %,d", current, target);
            return text + "\n" + net.minecraft.client.resources.I18n.format("achievement.hfr.progress", progress)
                + "\n" + net.minecraft.client.resources.I18n.format(entry.getBoolean("complete") ? "achievement.hfr.complete" : "achievement.hfr.incomplete")
                + "\n" + net.minecraft.client.resources.I18n.format(entry.getBoolean("rewarded") ? "achievement.hfr.rewarded" : "achievement.hfr.reward");
        }
    }
    @SideOnly(Side.CLIENT) private static java.lang.ref.WeakReference<EntityPlayer> clientPlayer;
    @SideOnly(Side.CLIENT) private static NBTTagCompound clientData;
    @SideOnly(Side.CLIENT) public static void receiveClient(EntityPlayer player, NBTTagCompound data) {
        clientPlayer = new java.lang.ref.WeakReference<EntityPlayer>(player); clientData = data;
    }
    private XFAchievements() { }
    public static void register() {
        AchievementPage.registerAchievementPage(new AchievementPage("Xenofactions", GOALS.toArray(new Achievement[GOALS.size()])));
        XFAchievements events = new XFAchievements();
        MinecraftForge.EVENT_BUS.register(events); FMLCommonHandler.instance().bus().register(events);
    }
    public static void clearTracking() { ACTIONS.clear(); PENDING_ACTIONS.clear(); FAILED.clear(); miningStats = null; }
    /** Server thread only; also drained before logout and shutdown so accepted actions cannot be skipped. */
    public static void flushActions() { Runnable action; while ((action = ACTIONS.poll()) != null) action.run(); }
    public static void onAcquired(EntityPlayer player, ItemStack stack) {
        if (player.worldObj.isRemote || stack == null) return;
        // Successful pickup can leave the entity stack at size zero.
        if (stack.getItem() == Item.getItemFromBlock(ModBlocks.machine_grainmill)) player.triggerAchievement(windmill);
        else if (stack.getItem() == Item.getItemFromBlock(ModBlocks.machine_net)) player.triggerAchievement(fishingNet);
    }
    /** Called only after the city item completes its protected founding transaction. */
    public static void cityFounded(final EntityPlayer player) {
        // The item must finish consumption before a reward save snapshots the inventory.
        ACTIONS.add(new Runnable() { public void run() { action(player, "city"); } });
    }
    private static void action(EntityPlayer player, String id) {
        if (!(player instanceof EntityPlayerMP) || player.worldObj.isRemote) return;
        Set<String> actions = PENDING_ACTIONS.get(player.getUniqueID());
        if (actions == null) { actions = new HashSet<String>(); PENDING_ACTIONS.put(player.getUniqueID(), actions); }
        actions.add(id);
        // A deferred placement may outlive its player entity (logout, death or dimension respawn).
        // Retain the observation by UUID and save only the current online entity.
        for (Object raw : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP current = (EntityPlayerMP)raw;
            if (!current.isDead && current.getUniqueID().equals(player.getUniqueID())) { update(current); break; }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void placed(final BlockEvent.PlaceEvent event) {
        if (event.world.isRemote || event.world.restoringBlockSnapshots || !(event.player instanceof EntityPlayerMP) || event.itemInHand == null) return;
        final Block block = Block.getBlockFromItem(event.itemInHand.getItem());
        final String id = block == ModBlocks.statue ? "statue" : block == ModBlocks.machine_uni ? "university"
            : block == ModBlocks.tp_tent ? "warpTent" : block == ModBlocks.machine_temple ? "temple"
            : block == ModBlocks.officer_chest ? "officerChest" : null;
        if (id == null) return;
        List<BlockSnapshot> snapshots = event instanceof BlockEvent.MultiPlaceEvent
            ? ((BlockEvent.MultiPlaceEvent)event).getReplacedBlockSnapshots() : Collections.singletonList(event.blockSnapshot);
        for (BlockSnapshot snapshot : snapshots) {
            if (!event.world.getChunkProvider().chunkExists(snapshot.x >> 4, snapshot.z >> 4)) return;
            if (event.world.getBlock(snapshot.x, snapshot.y, snapshot.z) != block) continue;
            if (!(block instanceof BlockDummyable) || completedBuilding(event.world, snapshot.x, snapshot.y, snapshot.z, (BlockDummyable)block)) {
                // The assembly exists now. Award only after ALL protection listeners finish.
                // Do not require it to remain placed until next tick (place-then-break still counts).
                ACTIONS.add(new Runnable() { public void run() {
                    if (!event.isCanceled()) action(event.player, id);
                } });
                return; // One placement, irrespective of dummy block count.
            }
        }
    }
    private static boolean completedBuilding(World world, int x, int y, int z, BlockDummyable block) {
        int meta = world.getBlockMetadata(x, y, z);
        if (meta < 12 || meta > 15 || world.getTileEntity(x, y, z) == null) return false;
        int[] dim = MultiblockHandler.rotate(block.getDimensions(), ForgeDirection.getOrientation(meta - BlockDummyable.offset));
        for (int a = x - dim[4]; a <= x + dim[5]; a++)
            for (int b = y - dim[1]; b <= y + dim[0]; b++)
                for (int c = z - dim[2]; c <= z + dim[3]; c++)
                    if (!world.getChunkProvider().chunkExists(a >> 4, c >> 4) || world.getBlock(a, b, c) != block) return false;
        return true;
    }
    private static long stat(EntityPlayerMP player, StatBase stat) { return Math.max(0, player.func_147099_x().writeStat(stat)); }
    public static void update(EntityPlayerMP player) {
        try {
            NBTTagCompound saved = DailyRewardService.achievementState(player);
            NBTTagCompound achievements = saved.getCompoundTag("Achievements"), before = (NBTTagCompound)achievements.copy();
            NBTTagCompound progress = achievements.getCompoundTag("Progress"), complete = achievements.getCompoundTag("Completed"), rewarded = achievements.getCompoundTag("Rewarded");
            Set<String> actions = PENDING_ACTIONS.get(player.getUniqueID());
            if (actions != null) for (String id : actions) progress.setLong(id, 1);
            if (miningStats == null) {
                miningStats = new LinkedHashSet<StatBase>();
                for (StatBase stat : StatList.mineBlockStatArray) if (stat != null) miningStats.add(stat);
            }
            long mined = 0, traveled = 0, kills = stat(player, StatList.playerKillsStat);
            for (StatBase stat : miningStats) mined += stat(player, stat);
            // Exclusive movement branches; falling is omitted because it overlaps airborne travel.
            for (StatBase stat : new StatBase[] { StatList.distanceWalkedStat, StatList.distanceSwumStat, StatList.distanceDoveStat,
                StatList.distanceClimbedStat, StatList.distanceFlownStat, StatList.distanceByMinecartStat,
                StatList.distanceByBoatStat, StatList.distanceByPigStat, StatList.field_151185_q }) traveled += stat(player, stat);
            NBTTagCompound snapshot = new NBTTagCompound(); snapshot.setBoolean("achievementProgress", true);
            int spins = saved.getInteger("AchievementSpins");
            for (Goal goal : GOALS) {
                long value = goal.source.equals("mining") ? mined : goal.source.equals("kills") ? kills
                    : goal.source.equals("travel") ? traveled : goal.source.equals("action") ? progress.getLong(goal.id) : stat(player, goal);
                if (value >= goal.target()) complete.setBoolean(goal.id, true);
                if (complete.getBoolean(goal.id) && !rewarded.getBoolean(goal.id)) {
                    spins = Math.addExact(spins, 1); rewarded.setBoolean(goal.id, true);
                }
                NBTTagCompound entry = new NBTTagCompound(); entry.setLong("target", goal.target());
                entry.setLong("progress", complete.getBoolean(goal.id) ? goal.target() : Math.min(value, goal.target()));
                entry.setBoolean("complete", complete.getBoolean(goal.id)); entry.setBoolean("rewarded", rewarded.getBoolean(goal.id));
                snapshot.setTag(goal.id, entry);
            }
            achievements.setTag("Progress", progress); achievements.setTag("Completed", complete); achievements.setTag("Rewarded", rewarded);
            if (!achievements.equals(before)) {
                saved.setTag("Achievements", achievements); saved.setInteger("AchievementSpins", spins);
                DailyRewardStore.commit(player, saved); // Receipts and balance become durable together.
                DailyRewardService.achievementBalanceChanged(player);
            }
            PENDING_ACTIONS.remove(player.getUniqueID()); FAILED.remove(player.getUniqueID());
            for (Goal goal : GOALS)
                if (complete.getBoolean(goal.id) && !player.func_147099_x().hasAchievementUnlocked(goal)) player.triggerAchievement(goal);
            // Periodic snapshots also recover packets dropped during client login/world replacement.
            PacketDispatcher.wrapper.sendTo(new DailySnapshotPacket(snapshot), player);
        } catch (IOException | RuntimeException e) {
            // Keep successful actions for retry without publishing an unsaved reward.
            if (FAILED.add(player.getUniqueID())) XFLog.error("Achievement save/update failed for " + player.getUniqueID() + "; pending actions retained", e);
        }
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) update((EntityPlayerMP)event.player);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        flushActions();
        if (event.player instanceof EntityPlayerMP) update((EntityPlayerMP)event.player);
    }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) update((EntityPlayerMP)event.player);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        flushActions();
        if (++ticks % 100 != 0) return;
        for (Object player : MinecraftServer.getServer().getConfigurationManager().playerEntityList) update((EntityPlayerMP)player);
    }
}
