package com.hfr.tileentity.machine;

import com.hfr.items.ModItems;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MachineNeiRecipes {
    private MachineNeiRecipes() { }

    public static final class ItemValue {
        public final ItemStack stack;
        public final float value;
        public ItemValue(ItemStack stack, float value) { this.stack = stack; this.value = value; }
    }

    public static final class ChanceEntry {
        public final ItemStack stack;
        public final int chancePercent;
        public ChanceEntry(ItemStack stack, int chancePercent) { this.stack = stack.copy(); this.chancePercent = chancePercent; }
    }

    public static ItemStack grainMillInput() { return new ItemStack(Items.wheat); }
    public static ItemStack grainMillOutput() { return new ItemStack(ModItems.flour, MachineDisplaySnapshot.forClientDisplay().grainmillOutput); }
    public static int grainMillTicks() { return TileEntityMachineGrainmill.maxProgress; }
    public static ItemStack universityOutput() { return new ItemStack(ModItems.science, MachineDisplaySnapshot.forClientDisplay().universityOutput); }
    public static ItemStack productionLineOutput() { return new ItemStack(ModItems.cog, MachineDisplaySnapshot.forClientDisplay().factoryOutput); }
    public static ItemStack templeOutput() { return new ItemStack(ModItems.scroll, MachineDisplaySnapshot.forClientDisplay().templeOutput); }

    public static List<ChanceEntry> universityJams() {
        List<ChanceEntry> list = new ArrayList<ChanceEntry>();
        list.add(new ChanceEntry(new ItemStack(Items.paper).setStackDisplayName("Student Strike"), 50));
        list.add(new ChanceEntry(new ItemStack(Items.gunpowder).setStackDisplayName("Bomb Threat"), 20));
        list.add(new ChanceEntry(new ItemStack(Items.skull).setStackDisplayName("Workplace Accident"), 20));
        list.add(new ChanceEntry(new ItemStack(Items.bone).setStackDisplayName("Skeleton Attack"), 10));
        return list;
    }

    public static List<ChanceEntry> productionLineJams() {
        List<ChanceEntry> list = new ArrayList<ChanceEntry>();
        list.add(new ChanceEntry(new ItemStack(Items.paper).setStackDisplayName("Worker Strike"), 50));
        list.add(new ChanceEntry(new ItemStack(Items.skull).setStackDisplayName("Workplace Accident"), 20));
        list.add(new ChanceEntry(new ItemStack(Items.slime_ball).setStackDisplayName("Chemical Spill"), 20));
        list.add(new ChanceEntry(new ItemStack(Items.potato).setStackDisplayName("Communist Takeover"), 10));
        return list;
    }

    public static List<ItemStack> fishingOutputsAndJams() {
        List<ItemStack> list = new ArrayList<ItemStack>();
        list.add(new ItemStack(Items.fish, MachineDisplaySnapshot.forClientDisplay().fishingOutput));
        list.add(new ItemStack(ModItems.whale_meat, MachineDisplaySnapshot.forClientDisplay().fishingOutput));
        list.add(new ItemStack(Items.stick).setStackDisplayName("Driftwood"));
        list.add(new ItemStack(Blocks.sapling));
        list.add(new ItemStack(Blocks.waterlily));
        list.add(new ItemStack(Items.bone).setStackDisplayName("Rattle Me Bones"));
        return list;
    }

    private static void addIfValid(List<ItemValue> list, ItemStack stack, float value) { if (stack != null && stack.getItem() != null && value > 0) list.add(new ItemValue(stack.copy(), value)); }
    public static void sort(List<ItemValue> list) { Collections.sort(list, new Comparator<ItemValue>() { public int compare(ItemValue a, ItemValue b) { int c = Item.itemRegistry.getNameForObject(a.stack.getItem()).compareTo(Item.itemRegistry.getNameForObject(b.stack.getItem())); return c != 0 ? c : Integer.compare(a.stack.getItemDamage(), b.stack.getItemDamage()); }}); }
}
