package com.hfr.tileentity.machine;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Insert production batches across existing output slots without raising storage/stack limits. */
final class StructureOutput {
    private StructureOutput() { }

    static int space(ItemStack[] slots, int start, int end, Item item) {
        int space = 0;
        for (int i = start; i < end; i++) {
            ItemStack stack = slots[i];
            if (stack == null) space += Math.min(64, item.getItemStackLimit());
            else if (stack.getItem() == item) space += Math.max(0, Math.min(64, stack.getMaxStackSize()) - stack.stackSize);
        }
        return space;
    }

    static int insert(ItemStack[] slots, int start, int end, Item item, int amount) {
        int remaining = amount;
        for (int i = start; i < end && remaining > 0; i++) {
            ItemStack stack = slots[i];
            if (stack != null && stack.getItem() != item) continue;
            int capacity = stack == null ? Math.min(64, item.getItemStackLimit()) : Math.min(64, stack.getMaxStackSize()) - stack.stackSize;
            int added = Math.min(remaining, Math.max(0, capacity));
            if (added == 0) continue;
            if (stack == null) slots[i] = new ItemStack(item, added);
            else stack.stackSize += added;
            remaining -= added;
        }
        return amount - remaining;
    }
}
