package com.hfr.server;

import com.hfr.data.TerritoryRecord;
import com.hfr.inventory.CityCenterContainer;
import com.hfr.main.Xenofactions;
import com.hfr.tile.CityCenterTile;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ThrowableEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;

/** Native bindings only for behavior enforced by the reference faction handlers. */
public final class FactionEvents {
    private static final UUID SAFE_KNOCKBACK = UUID.fromString("d2ff445d-0b34-4dc8-ab18-dcba85a03854");
    private final Set<Entity> projectiles = new HashSet<>();
    private final BorderFeedback borders = new BorderFeedback();
    @SubscribeEvent public void start(FMLServerStartedEvent event) { borders.clear(); FactionRuntime.start(event.getServer()); StoneDrops.load(); ChatModeration.start(event.getServer()); }
    @SubscribeEvent public void stop(FMLServerStoppedEvent event) { borders.clear(); FactionRuntime.stop(); projectiles.clear(); StoneDrops.clear(); ChatModeration.clear(); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void stone(BlockEvent.BreakEvent e) {
        if (e.isCanceled() || e.getPlayer() == null || e.getPlayer().isCreative() || e.getState().getBlock() != Blocks.STONE || !(e.getWorld() instanceof ServerWorld) || FactionRuntime.guarded(((ServerWorld)e.getWorld()).getServer())) return;
        ServerWorld world = (ServerWorld)e.getWorld();
        if (world.random.nextDouble() < com.hfr.config.GameplayConfig.n("coalChance")) StoneDrops.spawn(world, e.getPos(), new net.minecraft.item.ItemStack(net.minecraft.item.Items.COAL));
        if (world.random.nextDouble() < com.hfr.config.GameplayConfig.n("ironChance")) StoneDrops.spawn(world, e.getPos(), new net.minecraft.item.ItemStack(Blocks.IRON_ORE));
        if (world.random.nextDouble() < com.hfr.config.GameplayConfig.n("goldChance")) StoneDrops.spawn(world, e.getPos(), new net.minecraft.item.ItemStack(Blocks.GOLD_ORE));
        StoneDrops.drops(world, e.getPos());
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer(); if (server == null) return;
        if (!FactionRuntime.ready(server)) return;
        FactionRuntime.tick(server);
        FactionGameplay g = FactionRuntime.view(server);
        for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) if (player.isAlive()) borders.tick(g, player);
        projectiles.removeIf(e -> { if (!e.isAlive() || !(e.level instanceof ServerWorld)) return true; if (g.zone(g.at(e.level, e.blockPosition())) == 0) { e.remove(); return true; } return false; });
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void projectile(EntityJoinWorldEvent event) {
        Entity e = event.getEntity();
        if (!(e.level instanceof ServerWorld) || !(e instanceof AbstractArrowEntity || e instanceof ThrowableEntity || e instanceof FireballEntity)) return;
        if (!FactionRuntime.ready(((ServerWorld)e.level).getServer())) { projectiles.add(e); return; }
        FactionGameplay g = FactionRuntime.view(((ServerWorld)e.level).getServer());
        if (g.zone(g.at(e.level, e.blockPosition())) == 0) { e.remove(); event.setCanceled(true); } else projectiles.add(e);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getPlayer() instanceof ServerPlayerEntity) { FactionRuntime.cancel((ServerPlayerEntity)e.getPlayer()); borders.forget((ServerPlayerEntity)e.getPlayer()); } }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getPlayer() instanceof ServerPlayerEntity) FactionRuntime.cancel((ServerPlayerEntity)e.getPlayer()); }
    @SubscribeEvent public void death(LivingDeathEvent e) {
        if (!(e.getEntityLiving() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity victim = (ServerPlayerEntity)e.getEntityLiving(); FactionRuntime.cancel(victim); borders.forget(victim);
        if (!(e.getSource().getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity killer = (ServerPlayerEntity)e.getSource().getEntity(); if (killer == victim) return;
        FactionGameplay g = FactionGameplay.get(killer.getServer()); CompoundNBT a = g.faction(killer), b = g.faction(victim);
        if (a == null || b == null || a == b || !g.relation(a, b).getBoolean("enemy")) return;
        CompoundNBT relation = g.relation(a, b); if (relation.getLong("enemyRemovalAt") > 0 && relation.getLong("enemyRemovalAt") <= System.currentTimeMillis()) return;
        float reward = (float)com.hfr.config.GameplayConfig.n("enemyKillPrestigeReward"); if (reward <= 0) return;
        FactionGameplay.addPrestige(a, reward); g.commit(); g.notify(a, killer.getGameProfile().getName() + " killed enemy " + victim.getGameProfile().getName() + ": +" + reward + " prestige.");
    }
    @SubscribeEvent public void breakBlock(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayerEntity)) return; ServerPlayerEntity p = (ServerPlayerEntity)e.getPlayer(); FactionGameplay g = FactionRuntime.view(p.getServer());
        if (FactionRuntime.guarded(p.getServer())) return;
        if (e.getState().getBlock() == Xenofactions.CITY.get() && !FactionGameplay.bypass(p)) {
            e.setCanceled(true); TileEntity tile = p.level.getBlockEntity(e.getPos());
            if (tile instanceof CityCenterTile) {
                CityCenterTile city = (CityCenterTile)tile;
                if (g.find(city.factionId) == null) { p.getLevel().destroyBlock(e.getPos(), false); return; }
                if (g.rank(p) < 3 || !city.factionId.equals(FactionGameplay.id(g.faction(p)))) { FactionGameplay.message(p, "Only the owning faction leader may move a City Center."); return; }
                CityCenterContainer.open(p, city, true);
            }
            return;
        }
        if (!g.canEdit(p, e.getPos(), "DESTROY", p.level) || !FactionGameplay.bypass(p) && foundationBelow(p.level, e.getPos())) e.setCanceled(true);
    }
    private boolean foundationBelow(net.minecraft.world.World world, BlockPos pos) {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (world.getBlockState(pos.offset(x, 1, z)).getBlock() == Xenofactions.CITY.get()) return true;
        return false;
    }
    @SubscribeEvent public void place(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayerEntity)) return; ServerPlayerEntity p = (ServerPlayerEntity)e.getEntity(); FactionGameplay g = FactionRuntime.view(p.getServer());
        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        if (e instanceof BlockEvent.EntityMultiPlaceEvent) for (net.minecraftforge.common.util.BlockSnapshot s : ((BlockEvent.EntityMultiPlaceEvent)e).getReplacedBlockSnapshots()) positions.add(s.getPos()); else positions.add(e.getPos());
        for (BlockPos pos : positions) {
            if (!g.canEdit(p, pos, "BUILD", p.level)) { e.setCanceled(true); return; }
            if (FactionGameplay.bypass(p)) continue;
            Block block = p.level.getBlockState(pos).getBlock();
            if (g.zone(g.at(p.level, pos)) == 2 && (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST)) { e.setCanceled(true); FactionGameplay.message(p, "Chests are disabled outside claims."); return; }
            if (block != Xenofactions.CITY.get()) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y <= 12; y++) if (p.level.getBlockState(pos.offset(x, -y, z)).getBlock() == Xenofactions.CITY.get()) { e.setCanceled(true); FactionGameplay.message(p, "Do not obstruct a City Center."); return; }
        }
    }
    @SubscribeEvent public void interact(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getPlayer() instanceof ServerPlayerEntity)) return; ServerPlayerEntity p = (ServerPlayerEntity)e.getPlayer();
        FactionGameplay g = FactionRuntime.view(p.getServer()); Block b = p.level.getBlockState(e.getPos()).getBlock(); TileEntity t = p.level.getBlockEntity(e.getPos());
        if (b == Xenofactions.TRADER.get() && t instanceof com.hfr.tile.PlayerTraderTile) {
            if (!com.hfr.shop.PlayerTraderService.near(p, (com.hfr.tile.PlayerTraderTile)t)) e.setCanceled(true);
            return;
        }
        String action = t instanceof IInventory ? "CONTAINER" : b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof AbstractButtonBlock || b instanceof LeverBlock || b instanceof AbstractPressurePlateBlock ? "SWITCH" : "INTERACT";
        TerritoryRecord r = g.at(p.level, e.getPos());
        // Reference safe/war zones permit interaction; their restrictions concern editing and damage.
        if (g.zone(r) == 3 && !g.canEdit(p, e.getPos(), action, p.level)) e.setCanceled(true);
    }
    @SubscribeEvent public void explosion(ExplosionEvent.Detonate e) {
        if (!(e.getWorld() instanceof ServerWorld)) return; FactionGameplay g = FactionRuntime.view(((ServerWorld)e.getWorld()).getServer()); Vector3d origin = e.getExplosion().getPosition(); BlockPos pos = new BlockPos((int)origin.x, (int)origin.y, (int)origin.z);
        TerritoryRecord r = g.at(e.getWorld(), pos); int zone = g.zone(r);
        if (zone == 0 || zone == 1 || zone == 3 && (!g.raidable(g.owner(r)) || foundationBelow(e.getWorld(), pos))) e.getAffectedBlocks().clear();
    }
    @SubscribeEvent public void attack(LivingAttackEvent e) {
        if (!(e.getEntityLiving() instanceof ServerPlayerEntity)) return; ServerPlayerEntity p = (ServerPlayerEntity)e.getEntityLiving(); FactionGameplay g = FactionRuntime.view(p.getServer());
        if (g.zone(g.at(p.level, p.blockPosition())) == 0) { e.setCanceled(true); return; }
        // Build-grace early ending is gated by the same enabled starter-protection module in the reference.
        if (!com.hfr.config.FoundationConfig.PLAYER_PROTECTION.get() || !PlayerLifecycle.enabled() || !(e.getSource().getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity attacker = (ServerPlayerEntity)e.getSource().getEntity();
        if (PlayerLifecycle.timers(p.getServer()).grace(p.getUUID()) > System.currentTimeMillis() || PlayerLifecycle.timers(p.getServer()).grace(attacker.getUUID()) > System.currentTimeMillis()) return;
        if (g.faction(p) != null && g.faction(p) == g.faction(attacker) && FactionGameplay.grace(g.faction(p))) { FactionGameplay update = FactionGameplay.get(p.getServer()); CompoundNBT f = update.faction(p); f.putLong("buildGraceUntil", 0); update.commit(); update.notify(f, "Build grace ended because a member attacked a faction member."); }
    }
    @SubscribeEvent public void hurt(LivingHurtEvent e) { if (e.getEntityLiving() instanceof ServerPlayerEntity) { ServerPlayerEntity p = (ServerPlayerEntity)e.getEntityLiving(); FactionGameplay g = FactionRuntime.view(p.getServer()); if (g.zone(g.at(p.level, p.blockPosition())) == 0) e.setCanceled(true); } }
    @SubscribeEvent public void living(LivingEvent.LivingUpdateEvent e) {
        if (!(e.getEntityLiving() instanceof ServerPlayerEntity)) return; ServerPlayerEntity p = (ServerPlayerEntity)e.getEntityLiving(); FactionGameplay g = FactionRuntime.view(p.getServer()); boolean safe = g.zone(g.at(p.level, p.blockPosition())) == 0;
        net.minecraft.entity.ai.attributes.ModifiableAttributeInstance knockback = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (safe) { p.addEffect(new EffectInstance(Effects.REGENERATION, 40)); p.addEffect(new EffectInstance(Effects.DAMAGE_RESISTANCE, 40)); p.heal(5); if (knockback != null && !knockback.hasModifier(new AttributeModifier(SAFE_KNOCKBACK, "Xenofactions safezone", 1, AttributeModifier.Operation.ADDITION))) knockback.addTransientModifier(new AttributeModifier(SAFE_KNOCKBACK, "Xenofactions safezone", 1, AttributeModifier.Operation.ADDITION)); }
        else if (knockback != null) knockback.removeModifier(SAFE_KNOCKBACK);
    }
    @SubscribeEvent public void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayerEntity)) return; ServerPlayerEntity p = (ServerPlayerEntity)e.player; FactionGameplay g = FactionRuntime.view(p.getServer());
        TerritoryRecord r = g.at(p.level, p.blockPosition()); CompoundNBT moving = g.faction(p), territory = g.owner(r);
        if (moving == null && territory != null) { p.addEffect(new EffectInstance(Effects.DIG_SLOWDOWN, 20, 2)); p.addEffect(new EffectInstance(Effects.WEAKNESS, 20, 2)); p.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 20, 1)); }
        boolean blocked = FactionGameplay.grace(moving) && (g.zone(r) == 2 || territory != null && territory != moving) || FactionGameplay.grace(territory) && moving != territory;
        CompoundNBT data = p.getPersistentData();
        if (blocked) {
            int corrections = data.getInt("buildGraceCorrections") + 1; long now = System.currentTimeMillis();
            if (now - data.getLong("buildGraceLastCorrection") > 10000 || p.distanceToSqr(data.getDouble("buildGraceStuckX"), p.getY(), data.getDouble("buildGraceStuckZ")) > 25) { corrections = 1; data.putDouble("buildGraceStuckX", p.getX()); data.putDouble("buildGraceStuckZ", p.getZ()); }
            data.putLong("buildGraceLastCorrection", now); data.putInt("buildGraceCorrections", corrections);
            if (corrections >= 25) {
                try { if (FactionGameplay.grace(moving) && g.validHome(moving)) FactionRuntime.safeTeleport(p, g.world(moving.getInt("homeDim")), FactionGameplay.home(moving), true); else FactionRuntime.safeTeleport(p, p.getLevel(), p.getLevel().getSharedSpawnPos(), true); }
                catch (IllegalArgumentException unsafe) { FactionGameplay.message(p, "Build grace return point is unsafe: " + unsafe.getMessage()); }
                data.putInt("buildGraceCorrections", 0);
            }
        }
        String key = r == null ? "wilderness" : g.zone(r) + ":" + r.faction + ":" + r.cityId;
        if (!data.getString("nativeTerritoryPopup").equals(key)) { data.putString("nativeTerritoryPopup", key); p.displayClientMessage(new StringTextComponent(g.zone(r) == 0 ? "Safezone" : g.zone(r) == 1 ? "Warzone" : g.zone(r) == 2 ? "Wilderness" : territory.getString("name") + " — " + r.name), true); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void chat(ServerChatEvent e) {
        if (e.isCanceled()) return;
        String blocked = ChatModeration.blocked(e.getPlayer().getUUID()); if (blocked != null) { e.setCanceled(true); FactionGameplay.message(e.getPlayer(), blocked); return; }
        ServerPlayerEntity p = e.getPlayer(); FactionGameplay g = FactionRuntime.view(p.getServer()); CompoundNBT faction = g.faction(p); int mode = p.getPersistentData().getInt("clowderChat"), rank = g.rank(p);
        e.setCanceled(true); String role = rank == 3 ? "Leader" : rank == 2 ? "Officer" : "Citizen";
        if (faction != null && mode > 0) {
            String own = (mode == 1 ? TextFormatting.DARK_GREEN : TextFormatting.BLUE) + "<" + role + "> [" + p.getDisplayName().getString() + "] " + e.getMessage();
            g.notify(faction, own);
            if (mode == 2) for (CompoundNBT other : g.factions) if (other != faction && g.relation(faction, other).getBoolean("ally")) g.notify(other, TextFormatting.BLUE + "<Ally> [" + p.getDisplayName().getString() + "] " + e.getMessage());
            Xenofactions.LOGGER.info("Faction chat {}: {}", p.getGameProfile().getName(), e.getMessage()); return;
        }
        if (faction == null) p.getPersistentData().putInt("clowderChat", 0);
        for (ServerPlayerEntity recipient : p.getServer().getPlayerList().getPlayers()) {
            if (recipient.getPersistentData().getBoolean("clowderMute") || ChatModeration.ignoring(recipient.getUUID(), p.getUUID())) continue;
            if (faction != null) recipient.sendMessage(new StringTextComponent((rank == 3 ? TextFormatting.GOLD : rank == 2 ? TextFormatting.BLUE : TextFormatting.DARK_GREEN) + "[ " + FactionGameplay.decorated(faction) + " " + role + " ]"), ChatType.CHAT, p.getUUID());
            recipient.sendMessage(new StringTextComponent(TextFormatting.WHITE + p.getGameProfile().getName() + ": " + e.getMessage()), ChatType.CHAT, p.getUUID());
        }
    }
    public static String chatMode(ServerPlayerEntity p, String action) {
        FactionGameplay g = FactionRuntime.view(p.getServer());
        if (g.faction(p) == null) { p.getPersistentData().putInt("clowderChat", 0); throw new IllegalArgumentException("You are not in a faction; chat reset to public."); }
        if (action.equals("mute") || action.equals("m") || action.equals("unmute") || action.equals("u")) { boolean mute = action.equals("mute") || action.equals("m"); p.getPersistentData().putBoolean("clowderMute", mute); return "Public chat " + (mute ? "muted." : "unmuted."); }
        int mode = action.isEmpty() ? (p.getPersistentData().getInt("clowderChat") + 1) % 3 : action.equals("public") || action.equals("p") ? 0 : action.equals("faction") || action.equals("f") ? 1 : action.equals("alliance") || action.equals("a") ? 2 : -1;
        if (mode < 0) throw new IllegalArgumentException("Use public, faction, alliance, mute or unmute."); p.getPersistentData().putInt("clowderChat", mode); return "Chat mode: " + new String[]{"public", "faction", "alliance"}[mode] + ".";
    }
}
