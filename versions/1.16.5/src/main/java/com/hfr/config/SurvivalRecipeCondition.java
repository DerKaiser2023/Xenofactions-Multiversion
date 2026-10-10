package com.hfr.config;

import com.google.gson.JsonObject;
import com.hfr.main.Xenofactions;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

public final class SurvivalRecipeCondition implements ICondition {
    public static final ResourceLocation ID = new ResourceLocation(Xenofactions.MODID, "survival_recipes");
    @Override public ResourceLocation getID() { return ID; }
    @Override public boolean test() { return GameplayConfig.b("enableSurvivalRecipes"); }
    public static final class Serializer implements IConditionSerializer<SurvivalRecipeCondition> {
        @Override public ResourceLocation getID() { return ID; }
        @Override public SurvivalRecipeCondition read(JsonObject json) { return new SurvivalRecipeCondition(); }
        @Override public void write(JsonObject json, SurvivalRecipeCondition condition) { }
    }
}
