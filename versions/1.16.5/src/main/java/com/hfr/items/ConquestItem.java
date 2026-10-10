package com.hfr.items;

import com.hfr.server.FactionGameplay;
import net.minecraft.block.Block;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.util.ActionResultType;

public final class ConquestItem extends BlockItem {
    public ConquestItem(Block block) { super(block, new Properties().tab(com.hfr.main.Xenofactions.TAB)); }
    @Override public ActionResultType place(BlockItemUseContext context) {
        if (!context.getLevel().isClientSide) {
            if (!(context.getPlayer() instanceof ServerPlayerEntity)) return ActionResultType.FAIL;
            ServerPlayerEntity player = (ServerPlayerEntity)context.getPlayer();
            try { FactionGameplay g = FactionGameplay.get(player.getServer()); g.checkConquest(player.getLevel(), context.getClickedPos(), g.require(player, 1)); }
            catch (IllegalArgumentException invalid) { FactionGameplay.message(player, invalid.getMessage()); return ActionResultType.FAIL; }
        }
        return super.place(context);
    }
}
