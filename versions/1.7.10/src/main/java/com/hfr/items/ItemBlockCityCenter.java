package com.hfr.items;

import com.hfr.clowder.CityCenterRelocationManager;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** Preserves the existing block item ID while intercepting relocation tokens before normal founding. */
public class ItemBlockCityCenter extends ItemBlock {
    public ItemBlockCityCenter(Block block) { super(block); setMaxStackSize(1); }

    @Override
    public boolean placeBlockAt(ItemStack stack,EntityPlayer player,World world,int x,int y,int z,int side,float hitX,float hitY,float hitZ,int metadata){
        if(!world.isRemote){
            com.hfr.clowder.ClowderTerritory.CoordPair node=com.hfr.clowder.ClowderTerritory.getCoordPair(world,x,z);
            com.hfr.clowder.ClaimTerrainService.Decision decision=com.hfr.clowder.ClaimTerrainService.decision(world,node);
            if(decision.eligibility!=com.hfr.clowder.ClaimNodeSystem.Eligibility.ELIGIBLE){player.addChatMessage(new net.minecraft.util.ChatComponentText(decision.rejection()));return false;}
        }
        return super.placeBlockAt(stack,player,world,x,y,z,side,hitX,hitY,hitZ,metadata);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
        if(!CityCenterRelocationManager.isToken(stack)) return super.onItemUse(stack, player, world, x, y, z, side, hitX, hitY, hitZ);
        Block clicked=world.getBlock(x,y,z);
        if(!clicked.isReplaceable(world,x,y,z)) {
            if(side==0)y--; else if(side==1)y++; else if(side==2)z--; else if(side==3)z++; else if(side==4)x--; else if(side==5)x++;
        }
        return CityCenterRelocationManager.relocate(player, stack, world, x, y, z);
    }
}
