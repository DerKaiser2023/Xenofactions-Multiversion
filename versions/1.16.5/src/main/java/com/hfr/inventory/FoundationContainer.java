package com.hfr.inventory;

import com.hfr.main.Xenofactions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;

/** No inventory slots or leases until native kit editing is ported. */
public final class FoundationContainer extends Container {
    private CompoundNBT snapshot;
    public FoundationContainer(int id, CompoundNBT snapshot) {
        super(Xenofactions.EDITOR.get(), id);
        this.snapshot = snapshot == null ? new CompoundNBT() : snapshot.copy();
    }
    public CompoundNBT snapshot() { return snapshot.copy(); }
    public void receive(CompoundNBT value) { if (snapshot.getString("token").equals(value.getString("token"))) snapshot = value.copy(); }
    @Override public boolean stillValid(PlayerEntity player) { return player.isAlive() && player.hasPermissions(4); }
    @Override public ItemStack quickMoveStack(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
}
