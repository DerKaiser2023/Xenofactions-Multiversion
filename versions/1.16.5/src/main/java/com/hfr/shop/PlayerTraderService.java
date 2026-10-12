package com.hfr.shop;

import com.hfr.inventory.PlayerTraderContainer;
import com.hfr.network.FoundationNetwork;
import com.hfr.server.FactionGameplay;
import com.hfr.server.FactionRuntime;
import com.hfr.tile.PlayerTraderTile;
import java.util.Arrays;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.network.NetworkHooks;

public final class PlayerTraderService {
    private PlayerTraderService() { }
    public static boolean owner(PlayerEntity player, PlayerTraderTile tile) {
        return player != null && tile != null && tile.owner.equals(player.getUUID().toString());
    }
    public static boolean manager(PlayerEntity player, PlayerTraderTile tile) {
        return owner(player, tile) || player != null && player.hasPermissions(3);
    }

    public static boolean near(PlayerEntity player, PlayerTraderTile tile) {
        if (!(player instanceof ServerPlayerEntity) || !player.isAlive() || tile == null || !tile.valid() || player.level != tile.getLevel()
            || player.distanceToSqr(tile.getBlockPos().getX() + .5, tile.getBlockPos().getY() + .5, tile.getBlockPos().getZ() + .5) > 64) return false;
        if (player.hasPermissions(3)) return true;
        FactionGameplay g = FactionRuntime.view(((ServerPlayerEntity)player).getServer());
        return g.zone(g.at(player.level, tile.getBlockPos())) != 3 || g.canEdit((ServerPlayerEntity)player, tile.getBlockPos(), "INTERACT", player.level);
    }
    public static void open(ServerPlayerEntity player, PlayerTraderTile tile) {
        if (!near(player, tile)) return;
        PlayerTraderContainer opening = new PlayerTraderContainer(0, tile);
        CompoundNBT data = snapshot(player, opening);
        NetworkHooks.openGui(player, new SimpleNamedContainerProvider((id, inv, p) ->
            new PlayerTraderContainer(id, tile, opening.token), new StringTextComponent("Player Trader")), buffer -> buffer.writeNbt(data));
    }
    public static void action(ServerPlayerEntity player, int id, String token, String action, long revision, int slot, int quantity) {
        if (!(player.containerMenu instanceof PlayerTraderContainer) || player.containerMenu.containerId != id) return;
        PlayerTraderContainer menu = (PlayerTraderContainer)player.containerMenu;
        if (!menu.token.equals(token)) return;
        if (!near(player, menu.tile)) { player.closeContainer(); return; }
        PlayerTraderTile tile = menu.tile;
        if (revision != tile.offerRevision) { say(player, "The offer changed. Review the refreshed shop."); refresh(tile); return; }
        String error = null;
        if ("buy".equals(action)) error = buy(player, tile);
        else if (manager(player, tile)) {
            if ("sale".equals(action) || "payment".equals(action)) error = configure(player, tile, action, slot, quantity);
            else if ("deposit".equals(action)) error = deposit(player, tile, slot);
            else if ("stock".equals(action) || "collect".equals(action)) error = withdraw(player, tile, action, slot);
        }
        if (error != null) say(player, error); refresh(tile);
    }
    private static void say(ServerPlayerEntity player, String message) { FactionGameplay.message(player, "[Player Trader] " + message); }
    public static CompoundNBT snapshot(ServerPlayerEntity player, PlayerTraderContainer menu) {
        PlayerTraderTile tile = menu.tile; CompoundNBT tag = new CompoundNBT();
        tag.putString("token", menu.token); tag.putLong("revision", tile.offerRevision); tag.putBoolean("manager", manager(player, tile));
        tag.putInt("saleQuantity", tile.saleQuantity); tag.putInt("paymentQuantity", tile.paymentQuantity);
        if (tile.sale != null) tag.put("sale", tile.sale.save(new CompoundNBT()));
        if (tile.payment != null) tag.put("payment", tile.payment.save(new CompoundNBT()));
        int stock = 0; if (tile.sale != null) for (ItemStack stack : tile.stock) if (same(stack, tile.sale)) stock += stack.getCount();
        tag.putInt("available", tile.saleQuantity > 0 ? stock / tile.saleQuantity : 0);
        if (manager(player, tile)) { tag.put("stock", list(tile.stock)); tag.put("payments", list(tile.payments)); tag.put("hotbar", list(Arrays.copyOf(player.inventory.items.toArray(new ItemStack[0]), 9))); }
        menu.snapshot = tag.copy(); return tag;
    }
    private static ListNBT list(ItemStack[] stacks) {
        ListNBT list = new ListNBT(); for (int i = 0; i < stacks.length; i++) if (stacks[i] != null && !stacks[i].isEmpty()) {
            CompoundNBT n = stacks[i].save(new CompoundNBT()); n.putByte("Slot", (byte)i); list.add(n);
        } return list;
    }
    private static void refresh(PlayerTraderTile tile) {
        for (ServerPlayerEntity player : tile.getLevel().getServer().getPlayerList().getPlayers())
            if (player.containerMenu instanceof PlayerTraderContainer && ((PlayerTraderContainer)player.containerMenu).tile == tile) {
                if (!near(player, tile)) { player.closeContainer(); continue; }
                PlayerTraderContainer menu = (PlayerTraderContainer)player.containerMenu;
                FoundationNetwork.traderSnapshot(player, menu.containerId, snapshot(player, menu));
            }
    }
    private static String configure(ServerPlayerEntity player, PlayerTraderTile tile, String action, int slot, int quantity) {
        if (slot < 0 || slot >= 9 || quantity <= 0 || quantity > 576) return "Choose hotbar slot 1-9 and a quantity from 1 to 576.";
        ItemStack source = player.inventory.items.get(slot);
        if (source.isEmpty()) return "That hotbar slot is empty.";
        if (quantity > 9 * Math.min(64, source.getMaxStackSize()))
            return "That quantity exceeds the shop's storage capacity for this item.";
        ItemStack template = source.copy(); template.setCount(1);
        if ("sale".equals(action)) { tile.sale = template; tile.saleQuantity = quantity; }
        else { tile.payment = template; tile.paymentQuantity = quantity; }
        tile.offerRevision++; tile.setChanged();
        return null;
    }
    private static String deposit(ServerPlayerEntity player, PlayerTraderTile tile, int slot) {
        if (slot < 0 || slot >= 9) return "Choose hotbar slot 1-9.";
        ItemStack source = player.inventory.items.get(slot);
        if (source.isEmpty() || tile.sale == null || !same(source, tile.sale))
            return "Put the configured sale item in that hotbar slot.";
        ItemStack[] stock = copy(tile.stock);
        if (!insert(stock, source, source.getCount())) return "Sale stock is full.";
        player.inventory.items.set(slot, ItemStack.EMPTY);
        tile.stock = stock; changed(player, tile); return null;
    }
    private static String withdraw(ServerPlayerEntity player, PlayerTraderTile tile, String action, int slot) {
        if (slot < 0 || slot >= 9) return "Choose storage slot 1-9.";
        ItemStack[] source = "stock".equals(action) ? tile.stock : tile.payments;
        if (source[slot] == null) return "That storage slot is empty.";
        ItemStack[] inventory = copy(player.inventory.items.toArray(new ItemStack[0]));
        if (!insert(inventory, source[slot], source[slot].getCount())) return "Your inventory is full.";
        for (int i = 0; i < inventory.length; i++) player.inventory.items.set(i, inventory[i] == null ? ItemStack.EMPTY : inventory[i]);
        source[slot] = null; changed(player, tile); return null;
    }
    private static String buy(ServerPlayerEntity player, PlayerTraderTile tile) {
        if (tile.sale == null || tile.payment == null || tile.saleQuantity <= 0 || tile.paymentQuantity <= 0)
            return "This shop has no valid offer.";
        ItemStack[] stock = copy(tile.stock), payments = copy(tile.payments);
        ItemStack[] inventory = copy(player.inventory.items.toArray(new ItemStack[0]));
        if (!remove(stock, tile.sale, tile.saleQuantity)) return "Insufficient stock.";
        if (!remove(inventory, tile.payment, tile.paymentQuantity)) return "Insufficient payment.";
        if (!insert(inventory, tile.sale, tile.saleQuantity)) return "Your inventory is full.";
        if (!insert(payments, tile.payment, tile.paymentQuantity)) return "Shop payment storage is full.";
        // All capacity and identity checks succeeded. No callback runs between these assignments.
        tile.stock = stock; tile.payments = payments;
        for (int i = 0; i < inventory.length; i++) player.inventory.items.set(i, inventory[i] == null ? ItemStack.EMPTY : inventory[i]);
        changed(player, tile); say(player, "Purchase complete."); return null;
    }
    private static void changed(ServerPlayerEntity player, PlayerTraderTile tile) {
        tile.setChanged(); player.inventory.setChanged(); player.inventoryMenu.broadcastChanges();
    }
    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < result.length; i++) if (source[i] != null && !source[i].isEmpty()) result[i] = source[i].copy();
        return result;
    }
    private static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null && !a.isEmpty() && !b.isEmpty() && a.getItem() == b.getItem()
            && a.getDamageValue() == b.getDamageValue() && ItemStack.tagMatches(a, b);
    }
    private static boolean remove(ItemStack[] items, ItemStack template, int amount) {
        for (int i = 0; i < items.length && amount > 0; i++) if (same(items[i], template)) {
            int taken = Math.min(amount, items[i].getCount()); amount -= taken; items[i].shrink(taken);
            if (items[i].getCount() == 0) items[i] = null;
        }
        return amount == 0;
    }
    private static boolean insert(ItemStack[] items, ItemStack template, int amount) {
        for (int pass = 0; pass < 2 && amount > 0; pass++) for (int i = 0; i < items.length && amount > 0; i++) {
            ItemStack stack = items[i];
            if (pass == 0 && !same(stack, template) || pass == 1 && stack != null) continue;
            int limit = Math.min(64, template.getMaxStackSize());
            int space = stack == null ? limit : limit - stack.getCount();
            if (space <= 0) continue;
            int added = Math.min(amount, space);
            if (stack == null) { items[i] = template.copy(); items[i].setCount(added); }
            else stack.grow(added);
            amount -= added;
        }
        return amount == 0;
    }
}
