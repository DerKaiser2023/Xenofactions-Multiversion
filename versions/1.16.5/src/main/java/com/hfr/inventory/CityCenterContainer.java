package com.hfr.inventory;

import com.hfr.data.CityLevel;
import com.hfr.main.Xenofactions;
import com.hfr.server.FactionGameplay;
import com.hfr.tile.CityCenterTile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

/** Vanilla menu-button packets validate container identity, distance, world and fresh leader authority. */
public final class CityCenterContainer extends Container {
    public final CompoundNBT snapshot;
    private final World world;
    public CityCenterContainer(int window, CompoundNBT snapshot, World world) { super(Xenofactions.CITY_MENU.get(), window); this.snapshot = snapshot.copy(); this.world = world; }
    public static void open(ServerPlayerEntity player, CityCenterTile tile, boolean move) {
        CompoundNBT tag = new CompoundNBT(); tag.putLong("position", tile.getBlockPos().asLong()); tag.putString("cityId", tile.cityId); tag.putString("city", tile.cityName); tag.putString("level", CityLevel.at(tile.cityLevel).display); tag.putBoolean("move", move);
        CompoundNBT f = FactionGameplay.get(player.getServer()).find(tile.factionId); tag.putString("owner", f == null ? "Unclaimed" : f.getString("name"));
        tag.putFloat("prestige", f == null ? 0 : f.getFloat("prestige")); tag.putFloat("generation", f == null ? 0 : f.getFloat("prestigeGen")); tag.putFloat("upkeep", f == null ? 0 : f.getFloat("prestigeReq"));
        NetworkHooks.openGui(player, new SimpleNamedContainerProvider((id, inventory, p) -> new CityCenterContainer(id, tag, player.level), new StringTextComponent(move ? "Relocate City Center" : "City Center")), buffer -> buffer.writeNbt(tag));
    }
    @Override public boolean stillValid(PlayerEntity player) {
        BlockPos pos = BlockPos.of(snapshot.getLong("position"));
        if (player.level != world || !player.isAlive() || player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64) return false;
        net.minecraft.tileentity.TileEntity tile = world.getBlockEntity(pos);
        return tile instanceof CityCenterTile && ((CityCenterTile)tile).cityId.equals(snapshot.getString("cityId"));
    }
    @Override public boolean clickMenuButton(PlayerEntity player, int button) {
        if (!(player instanceof ServerPlayerEntity) || player.containerMenu != this || !stillValid(player)) return false;
        ServerPlayerEntity p = (ServerPlayerEntity)player;
        if (button != 0 || !snapshot.getBoolean("move")) return false;
        try { FactionGameplay.message(p, FactionGameplay.get(p.getServer()).startMove(p, BlockPos.of(snapshot.getLong("position")))); p.closeContainer(); return true; }
        catch (IllegalArgumentException | IllegalStateException invalid) { FactionGameplay.message(p, invalid.getMessage()); return false; }
    }
    @Override public ItemStack quickMoveStack(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
}
