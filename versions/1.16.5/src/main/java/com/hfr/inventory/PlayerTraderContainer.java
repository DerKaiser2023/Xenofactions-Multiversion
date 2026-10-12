package com.hfr.inventory;

import com.hfr.main.Xenofactions;
import com.hfr.shop.PlayerTraderService;
import com.hfr.tile.PlayerTraderTile;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;

/** No transferable slots: purchases and owner storage operations are complete transactions. */
public final class PlayerTraderContainer extends Container {
    public final PlayerTraderTile tile;
    public final String token;
    public CompoundNBT snapshot;
    public PlayerTraderContainer(int id, CompoundNBT data) { super(Xenofactions.TRADER_MENU.get(), id); tile = null; snapshot = data.copy(); token = data.getString("token"); }
    public PlayerTraderContainer(int id, PlayerTraderTile tile) { this(id, tile, UUID.randomUUID().toString()); }
    public PlayerTraderContainer(int id, PlayerTraderTile tile, String token) { super(Xenofactions.TRADER_MENU.get(), id); this.tile = tile; this.token = token; snapshot = new CompoundNBT(); }
    public void receive(CompoundNBT data) { if (token.equals(data.getString("token"))) snapshot = data.copy(); }
    @Override public boolean stillValid(PlayerEntity player) { return tile == null ? player.level.isClientSide : PlayerTraderService.near(player, tile); }
    @Override public ItemStack quickMoveStack(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
}
