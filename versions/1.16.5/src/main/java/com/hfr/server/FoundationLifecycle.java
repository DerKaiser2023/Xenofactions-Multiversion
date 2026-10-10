package com.hfr.server;

import com.hfr.command.FoundationCommands;
import com.hfr.data.FactionSavedData;
import com.hfr.data.MapSavedData;
import com.hfr.main.Xenofactions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;

public final class FoundationLifecycle {
    @SubscribeEvent public void commands(RegisterCommandsEvent event) { FoundationCommands.register(event.getDispatcher()); }
    @SubscribeEvent public void start(FMLServerStartedEvent event) {
        EditorSessions.clearAll();
        FactionSavedData factions = FactionSavedData.get(event.getServer());
        MapSavedData maps = MapSavedData.get(event.getServer());
        Xenofactions.LOGGER.info("Xenofactions loaded {} factions and {} maps; native faction gameplay is available. Competitive matches and shops are not ported.", factions.names().size(), maps.names().size());
    }
    @SubscribeEvent public void stop(FMLServerStoppedEvent event) { EditorSessions.clearAll(); }
    @SubscribeEvent public void unload(WorldEvent.Unload event) { if (!event.getWorld().isClientSide() && event.getWorld() instanceof net.minecraft.world.server.ServerWorld
            && ((net.minecraft.world.server.ServerWorld)event.getWorld()).dimension().equals(net.minecraft.world.World.OVERWORLD)) EditorSessions.clearAll(); }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getPlayer() instanceof ServerPlayerEntity) EditorSessions.clear((ServerPlayerEntity)event.getPlayer()); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getPlayer() instanceof ServerPlayerEntity) EditorSessions.clear((ServerPlayerEntity)event.getPlayer()); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getPlayer() instanceof ServerPlayerEntity) EditorSessions.clear((ServerPlayerEntity)event.getPlayer()); }
    @SubscribeEvent public void death(LivingDeathEvent event) { if (event.getEntityLiving() instanceof ServerPlayerEntity) EditorSessions.clear((ServerPlayerEntity)event.getEntityLiving()); }
    @SubscribeEvent public void clone(PlayerEvent.Clone event) {
        CompoundNBT old = event.getOriginal().getPersistentData().getCompound(PlayerEntity.PERSISTED_NBT_TAG);
        // Preserve the established player context format, never transient authorization tokens.
        CompoundNBT target = event.getPlayer().getPersistentData().getCompound(PlayerEntity.PERSISTED_NBT_TAG);
        if (old.contains("xfAdminEditorContext", 10)) target.put("xfAdminEditorContext", old.getCompound("xfAdminEditorContext").copy());
        event.getPlayer().getPersistentData().put(PlayerEntity.PERSISTED_NBT_TAG, target);
        if (event.getOriginal() instanceof ServerPlayerEntity) EditorSessions.clear((ServerPlayerEntity)event.getOriginal());
    }
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayerEntity && event.player.tickCount % 20 == 0) EditorSessions.tick((ServerPlayerEntity)event.player);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void left(PlayerInteractEvent.LeftClickBlock event) { click(event, true); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void right(PlayerInteractEvent.RightClickBlock event) { click(event, false); }
    private void click(PlayerInteractEvent event, boolean first) {
        if (event.getItemStack().getItem() != Xenofactions.WAND.get()) return;
        event.setCanceled(true);
        if (event.getPlayer() instanceof ServerPlayerEntity && event.getHand() == net.minecraft.util.Hand.MAIN_HAND)
            EditorSessions.point((ServerPlayerEntity)event.getPlayer(), event.getPos(), first);
    }
}
