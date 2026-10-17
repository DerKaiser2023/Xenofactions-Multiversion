package com.hfr.client.render;

import com.hfr.client.flag.FactionFlagTextureManager;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Observes equipment snapshots on ticks, never from an armor render invocation. */
public final class ClientPresentation {
    private static final Map<EntityPlayer,String[]> BANNERS=new WeakHashMap<EntityPlayer,String[]>();
    private static World world;private static int ticks;
    private ClientPresentation() { }
    public static void tick() {
        Minecraft mc=Minecraft.getMinecraft();
        if(world!=mc.theWorld){BANNERS.clear();world=mc.theWorld;ticks=0;}
        if(world==null||ticks++%20!=0)return;
        for(Object object:world.playerEntities) {
            EntityPlayer player=(EntityPlayer)object;ItemStack stack=player.inventory.armorInventory[0];
            if(stack==null||!stack.hasTagCompound()){BANNERS.remove(player);continue;}
            NBTTagCompound tag=stack.getTagCompound();String faction=tag.getString("flagFaction"),hash=tag.getString("flagHash");
            String[] previous=BANNERS.get(player);
            if(previous!=null&&previous[0].equals(faction)&&previous[1].equals(hash))continue;
            BANNERS.put(player,new String[]{faction,hash});
            if(!faction.isEmpty())FactionFlagTextureManager.handleMetadata(faction,hash);
        }
    }
}
