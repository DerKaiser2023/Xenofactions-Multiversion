package com.hfr.nei;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.hfr.blocks.ModBlocks;
import com.hfr.items.ModItems;
import com.hfr.tileentity.machine.MachineDisplaySnapshot;
import com.hfr.tileentity.machine.MachineNeiRecipes;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

@SideOnly(Side.CLIENT)
public abstract class XenoFactionsMachineHandler extends TemplateRecipeHandler {

    public static final String NET = "hfr.fishing_net";
    public static final String GRAIN_MILL = "hfr.grain_mill";
    public static final String UNIVERSITY = "hfr.university";
    public static final String PRODUCTION_LINE = "hfr.production_line";
    public static final String TEMPLE = "hfr.temple";

    private static final DecimalFormat NUM = new DecimalFormat("0.######", DecimalFormatSymbols.getInstance(Locale.US));
    private static final int ITEM_ROW_Y = 24;
    private static final int ITEM_SLOT_SIZE = 18;
    private static final int INFO_TEXT_GAP = 8;
    private static final int INFO_TEXT_X = 6;
    private static final int INFO_TEXT_START_Y = ITEM_ROW_Y + ITEM_SLOT_SIZE + INFO_TEXT_GAP;
    private static final int INFO_LINE_HEIGHT = 10;
    private static final int INFO_TEXT_WIDTH = 142;
    private static final int DEFAULT_INFO_TEXT_Y = 4;
    private static final int DEFAULT_INFO_TEXT_WIDTH = 160;
    private final String id;

    protected XenoFactionsMachineHandler(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("NEI machine handler id must not be null or empty");
        }
        this.id = id;
    }
    public String getRecipeName() { return tr("hfr.nei." + key() + ".title"); }
    public String getGuiTexture() { return "textures/gui/options_background.png"; }
    public String getOverlayIdentifier() { return id; }
    @Override public int getRecipeHeight(int recipe) {
        return INFO_TEXT_START_Y + (((LineRecipe) arecipes.get(recipe)).lines().length + 2) * INFO_LINE_HEIGHT + 4;
    }
    @Override public int recipiesPerPage() { return 1; }

    public void drawBackground(int recipe) { XenoFactionsStoneDropHandler.drawCleanRecipeBackground(30, ITEM_ROW_Y, 116, ITEM_ROW_Y); }

    public void loadCraftingRecipes(String outputId, Object... results) { if (id.equals(outputId)) loadAll(); else super.loadCraftingRecipes(outputId, results); }
    public void loadCraftingRecipes(ItemStack result) { if (result == null) return; loadMatching(result, false); }
    public void loadUsageRecipes(ItemStack ingredient) { if (ingredient == null) return; loadMatching(ingredient, true); }

    private void loadAll() {

        if (NET.equals(id)) {
            arecipes.add(new SimpleRecipe(new ItemStack(Blocks.water), new ItemStack(Items.fish, MachineDisplaySnapshot.forClientDisplay().fishingOutput), netLines(false)));
            arecipes.add(new SimpleRecipe(new ItemStack(Blocks.water), new ItemStack(ModItems.whale_meat, MachineDisplaySnapshot.forClientDisplay().fishingOutput), netLines(true)));
            for (ItemStack jam : MachineNeiRecipes.fishingOutputsAndJams().subList(2, 6)) {
                arecipes.add(new SimpleRecipe(new ItemStack(Blocks.water), jam, jamLines(jam)));
            }
        } else if (GRAIN_MILL.equals(id)) {
            arecipes.add(new SimpleRecipe(MachineNeiRecipes.grainMillInput(), MachineNeiRecipes.grainMillOutput(), grainMillLines()));
        } else if (UNIVERSITY.equals(id)) {
            addGeneratedRecipes(new ItemStack(ModBlocks.machine_uni), MachineNeiRecipes.universityOutput(), uniLines());
            for (MachineNeiRecipes.ChanceEntry jam : MachineNeiRecipes.universityJams()) {
                addGeneratedRecipes(new ItemStack(ModBlocks.machine_uni), jam.stack, uniJamLines(jam));
            }
        } else if (PRODUCTION_LINE.equals(id)) {
            addGeneratedRecipes(new ItemStack(ModBlocks.machine_factory), MachineNeiRecipes.productionLineOutput(), factoryLines());
            for (MachineNeiRecipes.ChanceEntry jam : MachineNeiRecipes.productionLineJams()) {
                addGeneratedRecipes(new ItemStack(ModBlocks.machine_factory), jam.stack, factoryJamLines(jam));
            }
        } else if (TEMPLE.equals(id)) {
            addGeneratedRecipes(new ItemStack(ModBlocks.machine_temple), MachineNeiRecipes.templeOutput(), templeLines());
        }
    }

    private void loadMatching(ItemStack stack, boolean usage) {

        if (NET.equals(id)) {
            for (ItemStack fishingOutput : MachineNeiRecipes.fishingOutputsAndJams()) {
                if (same(stack, fishingOutput) && !usage) {
                    boolean isWhale = fishingOutput.getItem() == ModItems.whale_meat;
                    boolean isNormalOutput = fishingOutput.getItem() == Items.fish || isWhale;

                    arecipes.add(new SimpleRecipe(
                        new ItemStack(Blocks.water),
                        fishingOutput,
                        isNormalOutput ? netLines(isWhale) : jamLines(fishingOutput)
                    ));
                }
            }
        } else if (GRAIN_MILL.equals(id)) {
            if (!usage && same(stack, MachineNeiRecipes.grainMillOutput())) { loadAll(); }
            if (usage && same(stack, MachineNeiRecipes.grainMillInput())) { loadAll(); }
        } else if (UNIVERSITY.equals(id)) {
            if (!usage && same(stack, MachineNeiRecipes.universityOutput())) { addGeneratedRecipes(new ItemStack(ModBlocks.machine_uni), MachineNeiRecipes.universityOutput(), uniLines()); }
            if (!usage) { for (MachineNeiRecipes.ChanceEntry jam : MachineNeiRecipes.universityJams()) { if (same(stack, jam.stack)) { addGeneratedRecipes(new ItemStack(ModBlocks.machine_uni), jam.stack, uniJamLines(jam)); } } }
        } else if (PRODUCTION_LINE.equals(id)) {
            if (!usage && same(stack, MachineNeiRecipes.productionLineOutput())) { addGeneratedRecipes(new ItemStack(ModBlocks.machine_factory), MachineNeiRecipes.productionLineOutput(), factoryLines()); }
            if (!usage) { for (MachineNeiRecipes.ChanceEntry jam : MachineNeiRecipes.productionLineJams()) { if (same(stack, jam.stack)) { addGeneratedRecipes(new ItemStack(ModBlocks.machine_factory), jam.stack, factoryJamLines(jam)); } } }
        } else if (TEMPLE.equals(id)) {
            if (!usage && same(stack, MachineNeiRecipes.templeOutput())) { loadAll(); }
        }
    }

    private void addGeneratedRecipes(ItemStack machine, ItemStack output, String[] lines) { arecipes.add(new SimpleRecipe(machine, output, lines)); }

    public void drawExtras(int recipe) {
        if (recipe < 0 || recipe >= arecipes.size()) {
            return;
        }
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        LineRecipe r = (LineRecipe) arecipes.get(recipe);
        boolean belowItemTextLayout = usesBelowItemTextLayout();
        int y = belowItemTextLayout ? INFO_TEXT_START_Y : DEFAULT_INFO_TEXT_Y;
        int textWidth = belowItemTextLayout ? INFO_TEXT_WIDTH : DEFAULT_INFO_TEXT_WIDTH;
        for (String line : r.lines()) {
            fr.drawString(trim(fr, line, textWidth), INFO_TEXT_X, y, 0x404040);
            y += INFO_LINE_HEIGHT;
        }
        net.minecraft.block.Block structure = NET.equals(id) ? ModBlocks.machine_net : GRAIN_MILL.equals(id) ? ModBlocks.machine_grainmill
            : UNIVERSITY.equals(id) ? ModBlocks.machine_uni : PRODUCTION_LINE.equals(id) ? ModBlocks.machine_factory : ModBlocks.machine_temple;
        fr.drawString(com.hfr.blocks.StructureInfo.size(structure), INFO_TEXT_X, y, 0x404040);
        fr.drawString(com.hfr.blocks.StructureInfo.axes(), INFO_TEXT_X, y + INFO_LINE_HEIGHT, 0x404040);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private boolean usesBelowItemTextLayout() {
        return NET.equals(id)
            || GRAIN_MILL.equals(id)
            || UNIVERSITY.equals(id)
            || PRODUCTION_LINE.equals(id)
            || TEMPLE.equals(id);
    }

    private String[] netLines(boolean whale) { MachineDisplaySnapshot s = MachineDisplaySnapshot.forClientDisplay(); return new String[] { tr("hfr.nei.output_chance") + ": " + (whale ? s.whaleChance : 100 - s.whaleChance) + "%", tr("hfr.nei.biome") + ": " + s.superFishrate + "/" + s.goodFishrate + "/" + s.averageFishrate + "/" + s.crapFishrate + "s", tr("hfr.nei.requirements") + ": " + tr("hfr.nei.net.requirements") }; }
    private String[] jamLines(ItemStack jam) { MachineDisplaySnapshot s = MachineDisplaySnapshot.forClientDisplay(); String chance = jam.getItem() == Items.stick ? "50%" : jam.getItem() == Items.bone ? "10%" : "20%"; return new String[] { tr("hfr.nei.expected_interval") + ": " + s.jamRate + "s", tr("hfr.nei.jam_chance") + ": " + chance, tr("hfr.nei.net.jam_note") }; }
    private String[] grainMillLines() { return new String[] { tr("hfr.nei.processing_time") + ": " + ticks(MachineNeiRecipes.grainMillTicks()), tr("hfr.nei.operational_requirements") + ": " + tr("hfr.nei.grain_mill.requirements"), tr("hfr.nei.foundation_requirements") + ": " + tr("hfr.nei.grain_mill.foundation"), tr("hfr.nei.clearance_requirements") + ": " + tr("hfr.nei.grain_mill.clearance") }; }
    private String[] uniLines() { MachineDisplaySnapshot s = MachineDisplaySnapshot.forClientDisplay(); return new String[] { tr("hfr.nei.no_item_input"), tr("hfr.nei.generated_output"), tr("hfr.nei.expected_output_interval") + ": " + seconds(s.uniRate), tr("hfr.nei.jam_interval") + ": " + seconds(s.uniJamRate), tr("hfr.nei.foundation_requirements") + ": " + tr("hfr.nei.university.foundation"), tr("hfr.nei.clearance_requirements") + ": " + tr("hfr.nei.university.clearance") }; }
    private String[] uniJamLines(MachineNeiRecipes.ChanceEntry jam) { MachineDisplaySnapshot s = MachineDisplaySnapshot.forClientDisplay(); return new String[] { tr("hfr.nei.jam_interval") + ": " + seconds(s.uniJamRate), tr("hfr.nei.jam_chance") + ": " + jam.chancePercent + "%", tr("hfr.nei.production_blocked_by_jam") + ": " + tr("hfr.nei.university.jam_note") }; }
    private String[] factoryLines() { MachineDisplaySnapshot s = MachineDisplaySnapshot.forClientDisplay(); return new String[] { tr("hfr.nei.no_item_input"), tr("hfr.nei.generated_output"), tr("hfr.nei.expected_output_interval") + ": " + seconds(s.factoryRate), tr("hfr.nei.jam_interval") + ": " + seconds(s.factoryJamRate), tr("hfr.nei.foundation_requirements") + ": " + tr("hfr.nei.production_line.foundation"), tr("hfr.nei.clearance_requirements") + ": " + tr("hfr.nei.production_line.clearance") }; }
    private String[] factoryJamLines(MachineNeiRecipes.ChanceEntry jam) { MachineDisplaySnapshot s = MachineDisplaySnapshot.forClientDisplay(); return new String[] { tr("hfr.nei.jam_interval") + ": " + seconds(s.factoryJamRate), tr("hfr.nei.jam_chance") + ": " + jam.chancePercent + "%", tr("hfr.nei.production_blocked_by_jam") + ": " + tr("hfr.nei.production_line.jam_note") }; }
    private String[] templeLines() { MachineDisplaySnapshot s = MachineDisplaySnapshot.forClientDisplay(); return new String[] { tr("hfr.nei.no_item_input"), tr("hfr.nei.generated_output"), tr("hfr.nei.expected_output_interval") + ": " + seconds(s.temple), tr("hfr.nei.operational_requirements") + ": " + tr("hfr.nei.temple.requirements") }; }

    private static String seconds(int seconds) { return seconds + "s"; }

    private String key() { return id.substring(4).replace('.', '_'); }
    private static String ticks(int t) { return t + " ticks / " + NUM.format(t / 20D) + "s"; }
    private static boolean same(ItemStack a, ItemStack b) { return a != null && b != null && a.getItem() == b.getItem() && (b.getItemDamage() == 32767 || a.getItemDamage() == b.getItemDamage()); }
    private static String tr(String key) { String v = StatCollector.translateToLocal(key); return v == null ? key : v; }
    private static String trim(FontRenderer fr, String text, int width) { return fr.getStringWidth(text) <= width ? text : fr.trimStringToWidth(text, width - fr.getStringWidth("...")) + "..."; }

    public interface LineRecipe { String[] lines(); }

    public class SimpleRecipe extends CachedRecipe implements LineRecipe {
        private final PositionedStack in; private final PositionedStack out; private final String[] lines;
        public SimpleRecipe(ItemStack in, ItemStack out, String[] lines) { this.in = new PositionedStack(in.copy(), 30, ITEM_ROW_Y); this.out = new PositionedStack(out.copy(), 116, ITEM_ROW_Y); this.lines = lines; }
        public PositionedStack getIngredient() { return in; }
        public PositionedStack getResult() { return out; }
        public String[] lines() { return lines; }
    }

    public class MultiInputRecipe extends CachedRecipe implements LineRecipe {
        private final List<PositionedStack> in; private final PositionedStack out; private final String[] lines;
        public MultiInputRecipe(ItemStack[] inputs, ItemStack out, String[] lines) {
            this.in = new java.util.ArrayList<PositionedStack>();
            int x = 12;
            for (ItemStack input : inputs) {
                if (input != null && input.getItem() != null) {
                    this.in.add(new PositionedStack(input.copy(), x, ITEM_ROW_Y));
                    x += 20;
                }
            }
            this.out = new PositionedStack(out.copy(), 116, ITEM_ROW_Y);
            this.lines = lines;
        }
        public List<PositionedStack> getIngredients() { return in; }
        public PositionedStack getResult() { return out; }
        public String[] lines() { return lines; }
    }
}
