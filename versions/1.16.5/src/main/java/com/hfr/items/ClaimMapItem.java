package com.hfr.items;

import com.hfr.data.CityLevel;
import com.hfr.data.TerritoryKey;
import com.hfr.data.TerritoryRecord;
import com.hfr.server.FactionGameplay;
import com.hfr.server.FactionRuntime;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/** The reference claim map prints a 15 by 15 territory grid; it is not a map-mod integration. */
public final class ClaimMapItem extends Item {
    public ClaimMapItem() { super(new Properties().stacksTo(1)); }
    @Override public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (player instanceof ServerPlayerEntity) {
            try {
                ServerPlayerEntity p = (ServerPlayerEntity)player; FactionGameplay g = FactionRuntime.view(p.getServer()); CompoundNBT own = g.faction(p); int dim = FactionGameplay.dimension(world);
                FactionGameplay.message(p, "===============");
                for (int z = -7; z <= 7; z++) {
                    StringBuilder row = new StringBuilder();
                    for (int x = -7; x <= 7; x++) {
                        TerritoryRecord r = g.territoryAt(TerritoryKey.generated(dim, (int)p.getX() + x * 16, (int)p.getZ() + z * 16)); int zone = g.zone(r);
                        row.append(zone == 0 ? TextFormatting.GREEN : zone == 1 ? TextFormatting.DARK_RED : zone == 3 ? g.owner(r) == own ? TextFormatting.DARK_BLUE : TextFormatting.GOLD : TextFormatting.RESET).append(x == 0 && z == 0 ? "☺" : "#");
                    }
                    FactionGameplay.message(p, row.toString());
                }
                TerritoryRecord here = g.at(world, p.blockPosition()); if (g.zone(here) == 3) FactionGameplay.message(p, "City: " + here.name + " (" + CityLevel.at(here.level).display + ") - " + g.owner(here).getString("name"));
                FactionGameplay.message(p, "===============");
            } catch (IllegalArgumentException | IllegalStateException invalid) { FactionGameplay.message((ServerPlayerEntity)player, invalid.getMessage()); }
        }
        return ActionResult.success(player.getItemInHand(hand));
    }
}
