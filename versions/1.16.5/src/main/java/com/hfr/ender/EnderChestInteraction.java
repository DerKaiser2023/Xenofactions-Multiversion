package com.hfr.ender;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.tileentity.EnderChestTileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class EnderChestInteraction {
    @SubscribeEvent(priority = EventPriority.LOWEST) public void interact(PlayerInteractEvent.RightClickBlock e) {
        if (!EnderChestRestrictions.isEnabled() || e.isCanceled() || e.getUseBlock() == Event.Result.DENY
            || !(e.getPlayer() instanceof ServerPlayerEntity) || e.getWorld().getBlockState(e.getPos()).getBlock() != Blocks.ENDER_CHEST) return;
        ServerPlayerEntity player = (ServerPlayerEntity)e.getPlayer();
        if (player.isSecondaryUseActive() && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())
            && !(player.getMainHandItem().doesSneakBypassUse(e.getWorld(), e.getPos(), player) && player.getOffhandItem().doesSneakBypassUse(e.getWorld(), e.getPos(), player))) return;
        BlockPos above = e.getPos().above();
        if (e.getWorld().getBlockState(above).isRedstoneConductor(e.getWorld(), above)
            || !(e.getWorld().getBlockEntity(e.getPos()) instanceof EnderChestTileEntity)) return;
        EnderChestInventory ender = player.getEnderChestInventory();
        ender.setActiveChest((EnderChestTileEntity)e.getWorld().getBlockEntity(e.getPos()));
        player.openMenu(new SimpleNamedContainerProvider((id, inv, p) -> new RestrictedEnderChestContainer(id, inv, ender), new TranslationTextComponent("container.enderchest")));
        player.awardStat(net.minecraft.stats.Stats.OPEN_ENDERCHEST);
        e.setCancellationResult(ActionResultType.SUCCESS); e.setCanceled(true);
    }
}
