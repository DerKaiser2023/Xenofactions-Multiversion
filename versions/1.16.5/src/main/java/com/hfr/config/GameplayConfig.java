package com.hfr.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.common.ForgeConfigSpec;

/** Native settings use the reference names, units and defaults. */
public final class GameplayConfig {
    public static final ForgeConfigSpec SPEC;
    private static final Map<String, ForgeConfigSpec.DoubleValue> NUMBERS = new LinkedHashMap<>();
    private static final Map<String, ForgeConfigSpec.BooleanValue> FLAGS = new LinkedHashMap<>();
    private static final Map<String, ForgeConfigSpec.ConfigValue<List<? extends Number>>> LEVELS = new LinkedHashMap<>();
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> ALLOWED_DIMENSIONS, BLOCKED_DIMENSIONS;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("cities");
        number(b, "minimumCitySpacingChunks", 13, 0, 128); number(b, "maxCityRadius", 6, 1, 32);
        number(b, "claimNameMinLength", 1, 1, 64); number(b, "claimNameMaxLength", 32, 1, 64);
        flag(b, "claimNameRequireUnique", true); flag(b, "claimRenameOfficersAllowed", true);
        flag(b, "enableClaimsInAllDimensions", true); flag(b, "enableSurvivalRecipes", true);
        ALLOWED_DIMENSIONS = b.defineList("allowedClaimDimensions", Arrays.<Integer>asList(), v -> v instanceof Integer);
        BLOCKED_DIMENSIONS = b.defineList("blockedClaimDimensions", Arrays.<Integer>asList(), v -> v instanceof Integer);
        levels(b, "cityRadii", Arrays.asList(2, 3, 4, 5, 6)); levels(b, "cityUpgradeCosts", Arrays.asList(75, 150, 300, 600, 1000));
        levels(b, "cityUpkeep", Arrays.asList(10, 25, 50, 90, 140)); number(b, "cityFoundingCostGrowth", .5, 0, 100);
        number(b, "capitalChangeCooldownHours", 168, 1, 8760);
        flag(b, "cityRelocationEnabled", true); number(b, "cityRelocationFreeDistanceBlocks", 10, 0, 100000);
        number(b, "cityRelocationBasePrestigeCost", 0, 0, 1000000); number(b, "cityRelocationPrestigePerExtraBlock", 30, 0, 100000);
        number(b, "cityRelocationMaxDistanceBlocks", 256, 0, 100000); number(b, "cityRelocationMoveLimit", 3, 1, 100);
        number(b, "cityRelocationWindowHours", 168, 1, 8760); number(b, "cityRelocationRepeatCooldownMinutes", 30, 0, 10080);
        number(b, "cityRelocationPendingMinutes", 30, 1, 10080); b.pop();
        b.push("prestige");
        number(b, "prestigeDelay", 72000, 1, Integer.MAX_VALUE); number(b, "territoryDelay", 5, 1, 1200);
        number(b, "prestigeGenerationCap", 2500, 0, 1000000); number(b, "cityCenterPrestigeGeneration", 0, 0, 100000);
        number(b, "warpCreationCost", 125, 0, 100000); number(b, "warpTentUpkeep", 75, 0, 100000);
        number(b, "settlementCityUpkeep", 10, 0, 100000); flag(b, "freeRaid", false);
        flag(b, "enableNegativePrestigePenalties", true);
        number(b, "financialCrisisThreshold", 0, -1000000, 1000000); number(b, "nationalCollapseThreshold", -500, -1000000, 1000000);
        number(b, "fallenNationThreshold", -1000, -1000000, 1000000);
        number(b, "financialCrisisUpkeepMult", 1.25, 0, 100); number(b, "nationalCollapseUpkeepMult", 1.5, 0, 100); number(b, "fallenNationUpkeepMult", 2, 0, 100); b.pop();
        b.push("war");
        flag(b, "enableConquestFlagsCommand", true); number(b, "onlinePlayerThreshold", 2, 0, 100); number(b, "raidGraceAfterOnlineDropMinutes", 30, 0, 1440);
        number(b, "warDeclarationBaseCost", 150, 0, 1000000); number(b, "warDeclarationTargetPrestigeFactor", .15, 0, 10);
        number(b, "activeWarUpkeep", 75, 0, 1000000); number(b, "warUpkeepHourlyGrowth", .25, 0, 100);
        number(b, "warUpkeepHourlyGrowthSquared", .05, 0, 100); number(b, "enemyKillPrestigeReward", 25, 0, 1000000);
        number(b, "surrenderPrestigeTransferPercent", .5, 0, 1); number(b, "surrenderTributeDurationHours", 84, 0, 8760);
        number(b, "surrenderCooldownHours", 84, 0, 8760); number(b, "peaceCooldownHours", 84, 0, 8760);
        number(b, "ceasefireCooldownHours", 24, 0, 8760); number(b, "allianceBreakCooldownHours", 24, 0, 8760);
        flag(b, "alliesCanJoinWars", true); flag(b, "alliesCanDeclareWarOnEachOther", false);
        flag(b, "peaceCityTransfersEnabled", true); flag(b, "surrenderTransfersCities", true); b.pop();
        b.push("protection"); flag(b, "graceBuildEnabled", true); flag(b, "graceBuildOneTimeUse", true);
        number(b, "graceBuildDurationHours", 48, 0, 8760); b.pop();
        b.push("stoneDrops"); number(b, "coalChance", .04, 0, 1); number(b, "ironChance", .05, 0, 1); number(b, "goldChance", .01, 0, 1); b.pop(); SPEC = b.build();
    }
    private static void number(ForgeConfigSpec.Builder b, String key, double value, double min, double max) { NUMBERS.put(key, b.defineInRange(key, value, min, max)); }
    private static void flag(ForgeConfigSpec.Builder b, String key, boolean value) { FLAGS.put(key, b.define(key, value)); }
    private static void levels(ForgeConfigSpec.Builder b, String key, List<Number> values) { LEVELS.put(key, b.defineList(key, values, v -> v instanceof Number && Double.isFinite(((Number)v).doubleValue()) && ((Number)v).doubleValue() >= 0 && ((Number)v).doubleValue() <= 1000000)); }
    public static double n(String key) { return NUMBERS.get(key).get(); }
    public static int i(String key) { return (int)n(key); }
    public static boolean b(String key) { return FLAGS.get(key).get(); }
    public static long hours(String key) { return (long)(n(key) * 3600000); }
    public static float level(String key, int level) {
        List<? extends Number> list = LEVELS.get(key).get();
        if (list.size() != 5) throw new IllegalStateException(key + " must contain exactly five city levels.");
        float value = list.get(Math.max(0, Math.min(4, level))).floatValue();
        return key.equals("cityRadii") ? Math.max(1, Math.min(6, Math.min(i("maxCityRadius"), (int)value))) : value;
    }
    public static boolean claims(int dimension) { return !BLOCKED_DIMENSIONS.get().contains(dimension) && (b("enableClaimsInAllDimensions") || ALLOWED_DIMENSIONS.get().contains(dimension)); }
    private GameplayConfig() { }
}
