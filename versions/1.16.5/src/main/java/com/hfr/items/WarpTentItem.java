package com.hfr.items;

import com.hfr.blocks.WarpTentBlock;
import com.hfr.server.FactionGameplay;
import com.hfr.server.FactionRuntime;
import net.minecraft.block.Block;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.math.BlockPos;

public final class WarpTentItem extends BlockItem {
    public WarpTentItem(Block block) { super(block, new Properties().tab(com.hfr.main.Xenofactions.TAB)); }
    @Override public ActionResultType place(BlockItemUseContext context) {
        if (!context.getLevel().isClientSide) {
            if (!(context.getPlayer() instanceof ServerPlayerEntity)) return ActionResultType.FAIL;
            ServerPlayerEntity player = (ServerPlayerEntity)context.getPlayer();
            FactionGameplay g = FactionRuntime.view(player.getServer()); BlockPos core = context.getClickedPos().relative(context.getHorizontalDirection(), 2);
            for (int x = -2; x <= 2; x++) for (int y = 0; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                BlockPos part = core.offset(x, y, z);
                if (!context.getLevel().getBlockState(part).getMaterial().isReplaceable() || !g.canEdit(player, part, "BUILD", player.level) || !context.getLevel().mayInteract(player, part) || !player.mayUseItemAt(part, context.getClickedFace(), context.getItemInHand())) {
                    FactionGameplay.message(player, "A Warp Tent needs an accessible, empty five by five by three space."); return ActionResultType.FAIL;
                }
            }
        }
        // Forge's normal ItemStack placement transaction captures the entire multiblock and rolls it back on a cancelled place event.
        return super.place(context);
    }
}
