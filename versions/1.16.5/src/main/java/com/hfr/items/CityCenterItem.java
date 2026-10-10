package com.hfr.items;

import com.hfr.server.FactionGameplay;
import net.minecraft.block.Block;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.world.server.ServerWorld;

public final class CityCenterItem extends BlockItem {
    public CityCenterItem(Block block) { super(block, new Properties().stacksTo(1)); }
    @Override public ActionResultType useOn(ItemUseContext context) {
        if (context.getItemInHand().getOrCreateTag().getBoolean("xenoCityRelocation")) {
            if (context.getLevel().isClientSide) return ActionResultType.SUCCESS;
            if (!(context.getPlayer() instanceof ServerPlayerEntity)) return ActionResultType.FAIL;
            ServerPlayerEntity p = (ServerPlayerEntity)context.getPlayer();
            // Own the complete relocation transaction; Forge's outer item placement must not roll back only the blocks after the saved-data commit.
            boolean capturing = p.getLevel().captureBlockSnapshots; p.getLevel().captureBlockSnapshots = false;
            try { BlockItemUseContext place = new BlockItemUseContext(context); FactionGameplay.get(p.getServer()).relocate(p, context.getItemInHand(), place.getClickedPos(), context.getClickedFace()); return ActionResultType.CONSUME; }
            catch (IllegalArgumentException | IllegalStateException failure) { FactionGameplay.message(p, failure.getMessage()); return ActionResultType.FAIL; }
            finally { p.getLevel().captureBlockSnapshots = capturing; }
        }
        return super.useOn(context);
    }
    @Override public ActionResultType place(BlockItemUseContext context) {
        if (!context.getLevel().isClientSide) {
            if (!(context.getPlayer() instanceof ServerPlayerEntity)) return ActionResultType.FAIL;
            ServerPlayerEntity p = (ServerPlayerEntity)context.getPlayer();
            try { FactionGameplay.get(p.getServer()).checkFounding(p, (ServerWorld)context.getLevel(), context.getClickedPos(), context.getItemInHand().getOrCreateTag().getString("cityName")); }
            catch (IllegalArgumentException | IllegalStateException failure) { FactionGameplay.message(p, failure.getMessage()); return ActionResultType.FAIL; }
        }
        return super.place(context);
    }
}
