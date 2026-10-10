package com.hfr.tile;

import com.hfr.main.Xenofactions;
import net.minecraft.tileentity.ChestTileEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

public final class OfficerChestTile extends ChestTileEntity {
    public OfficerChestTile() { super(Xenofactions.OFFICER_CHEST_TILE.get()); }
    @Override protected ITextComponent getDefaultName() { return new TranslationTextComponent("block.hfr.tile.officer_chest"); }
}
