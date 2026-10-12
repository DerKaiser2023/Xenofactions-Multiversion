package com.hfr.ender;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.Util;

/** Keeps vanilla chest clicks and synchronization, validating the complete server result. */
public final class RestrictedEnderChestContainer extends ChestContainer {
    private final EnderChestInventory ender;
    public RestrictedEnderChestContainer(int id, PlayerInventory inventory, EnderChestInventory ender) {
        super(ContainerType.GENERIC_9x3, id, inventory, ender, 3); this.ender = ender;
    }
    @Override public ItemStack clicked(int slot, int button, ClickType mode, PlayerEntity player) {
        if (player.level.isClientSide || !EnderChestRestrictions.isEnabled()) return super.clicked(slot, button, mode, player);
        ItemStack[] before = snapshot(ender), inventory = snapshot(player.inventory);
        ItemStack cursor = player.inventory.getCarried().copy();
        ItemStack result = super.clicked(slot, button, mode, player);
        String reason = EnderChestRestrictions.rejection(before, ender);
        if (reason != null) {
            restore(ender, before); restore(player.inventory, inventory); player.inventory.setCarried(cursor);
            player.inventory.setChanged();
            player.sendMessage(new StringTextComponent(reason), Util.NIL_UUID);
            if (player instanceof ServerPlayerEntity) { ((ServerPlayerEntity)player).refreshContainer(this); ((ServerPlayerEntity)player).broadcastCarriedItem(); }
        }
        return result;
    }
    private static ItemStack[] snapshot(IInventory inv) {
        ItemStack[] result = new ItemStack[inv.getContainerSize()];
        for (int i = 0; i < result.length; i++) result[i] = inv.getItem(i).copy(); return result;
    }
    private static void restore(IInventory inv, ItemStack[] before) { for (int i = 0; i < before.length; i++) inv.setItem(i, before[i]); inv.setChanged(); }
}
