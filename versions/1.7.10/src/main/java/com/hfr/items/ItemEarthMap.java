package com.hfr.items;

import com.hfr.main.MainRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public final class ItemEarthMap extends Item {
    @Override public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) MainRegistry.proxy.openEarthMap();
        return stack;
    }
}
