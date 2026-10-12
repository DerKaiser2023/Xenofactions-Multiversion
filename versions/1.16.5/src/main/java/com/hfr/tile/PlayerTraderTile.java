package com.hfr.tile;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.tileentity.TileEntity;

public final class PlayerTraderTile extends TileEntity {
    public PlayerTraderTile() { super(com.hfr.main.Xenofactions.TRADER_TILE.get()); }
    public String owner = "";
    public ItemStack sale, payment;
    public int saleQuantity = 1, paymentQuantity = 1;
    public long offerRevision;
    public ItemStack[] stock = new ItemStack[9], payments = new ItemStack[9];

    @Override public void load(net.minecraft.block.BlockState state, CompoundNBT tag) {
        super.load(state, tag);
        owner = tag.getString("OwnerUUID");
        sale = tag.contains("Sale", 10) ? readStack(tag.getCompound("Sale")) : null;
        payment = tag.contains("Payment", 10) ? readStack(tag.getCompound("Payment")) : null;
        if (sale != null) sale.setCount(1);
        if (payment != null) payment.setCount(1);
        saleQuantity = tag.getInt("SaleQuantity"); paymentQuantity = tag.getInt("PaymentQuantity");
        offerRevision = tag.getLong("OfferRevision");
        stock = new ItemStack[9]; payments = new ItemStack[9];
        ListNBT items = tag.getList("Items", 10);
        for (int i = 0; i < items.size(); i++) {
            CompoundNBT entry = items.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            ItemStack stack = readStack(entry);
            if (stack != null && stack.getCount() > 0) {
                if (slot < 9) stock[slot] = stack;
                else if (slot < 18) payments[slot - 9] = stack;
            }
        }
    }
    @Override public CompoundNBT save(CompoundNBT tag) {
        super.save(tag);
        tag.putString("OwnerUUID", owner);
        if (sale != null) { CompoundNBT n = new CompoundNBT(); sale.save(n); tag.put("Sale", n); }
        if (payment != null) { CompoundNBT n = new CompoundNBT(); payment.save(n); tag.put("Payment", n); }
        tag.putInt("SaleQuantity", saleQuantity); tag.putInt("PaymentQuantity", paymentQuantity);
        tag.putLong("OfferRevision", offerRevision);
        ListNBT items = new ListNBT();
        for (int i = 0; i < 18; i++) {
            ItemStack stack = i < 9 ? stock[i] : payments[i - 9];
            if (stack != null) { CompoundNBT n = new CompoundNBT(); n.putByte("Slot", (byte)i); stack.save(n); items.add(n); }
        }
        tag.put("Items", items); return tag;
    }
    private static ItemStack readStack(CompoundNBT tag) { ItemStack stack = ItemStack.of(tag); return stack.isEmpty() ? null : stack; }
    public boolean valid() { return !isRemoved() && level != null && level.getBlockEntity(worldPosition) == this && level.getBlockState(worldPosition).getBlock() == com.hfr.main.Xenofactions.TRADER.get(); }
}
