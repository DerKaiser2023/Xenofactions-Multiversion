package com.hfr.items;

import com.hfr.blocks.clowder.FactionBanner;
import com.hfr.blocks.clowder.LargeFactionFlag;
import com.hfr.clowder.Clowder;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

public final class ItemBlockFactionDecoration extends ItemBlock {
    public ItemBlockFactionDecoration(Block block) { super(block); }

    @Override public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
                                         int side, float hx, float hy, float hz, int metadata) {
        if(world.isRemote) return false;
        Clowder faction = Clowder.getClowderFromPlayer(player);
        if(faction == null || !Clowder.clowders.contains(faction) || faction.uuid == null || faction.uuid.isEmpty())
            return reject(player, "factiondecoration.error.membership");
        if(field_150939_a instanceof FactionBanner && !field_150939_a.canPlaceBlockOnSide(world, x, y, z, side)) return false;
        if(field_150939_a instanceof LargeFactionFlag) {
            if(!field_150939_a.canPlaceBlockAt(world, x, y, z)) return false;
            int rotation = LargeFactionFlag.rotation(player);
            for(int level = 0; level < LargeFactionFlag.HEIGHT; level++) {
                if(!world.isAirBlock(x, y + level, z) || !player.canPlayerEdit(x, y + level, z, side, stack))
                    return reject(player, "factiondecoration.error.space");
            }
            // The model's cloth occupies Y 8.5..10, extending 2.375 blocks from the pole.
            for(int level = 8; level <= 9; level++) for(int distance = 1; distance <= LargeFactionFlag.CLOTH_REACH; distance++) {
                int cx = x + LargeFactionFlag.clothX(rotation) * distance;
                int cz = z + LargeFactionFlag.clothZ(rotation) * distance;
                // blockExists is a loaded-only check, before any block access across chunk edges.
                if(!world.blockExists(cx, y + level, cz) || !world.isAirBlock(cx, y + level, cz))
                    return reject(player, "factiondecoration.error.space");
            }
            if(!world.checkNoEntityCollision(AxisAlignedBB.getBoundingBox(x + .4375D, y + .25D, z + .4375D,
                    x + .5625D, y + 10.25D, z + .5625D))) return reject(player, "factiondecoration.error.space");
            metadata = rotation; // Core metadata is never taken from an item's saved damage.
        }
        // Forge captures all pole segments and approves/rolls them back together through MultiPlaceEvent.
        return super.placeBlockAt(stack, player, world, x, y, z, side, hx, hy, hz, metadata);
    }

    private boolean reject(EntityPlayer player, String key) {
        player.addChatMessage(new ChatComponentTranslation(key));
        return false;
    }
}
