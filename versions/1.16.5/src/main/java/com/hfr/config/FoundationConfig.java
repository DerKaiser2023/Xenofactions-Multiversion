package com.hfr.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

/** Only settings for implemented foundation behavior belong in this spec. */
public final class FoundationConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> IDENTITY_MODE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS;
    public static final ForgeConfigSpec.DoubleValue STARTING_PRESTIGE;
    public static final ForgeConfigSpec.DoubleValue BASE_PRESTIGE;
    public static final ForgeConfigSpec.BooleanValue PLAYER_PROTECTION;
    public static final ForgeConfigSpec.DoubleValue PVP_HOURS;
    public static final ForgeConfigSpec.DoubleValue KEEP_INVENTORY_HOURS;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("foundation");
        IDENTITY_MODE = builder.comment("Faction authority: UUID, NAME, or AUTO. AUTO uses names in development/offline servers, matching 1.7.10.")
                .define("playerIdentityMode", "AUTO", value -> value instanceof String
                        && Arrays.asList("UUID", "NAME", "AUTO").contains(((String)value).toUpperCase(Locale.ROOT)));
        DIMENSIONS = builder.comment("Explicit legacy dimension ID to native dimension key mappings. Unknown IDs are never guessed.")
                .defineList("legacyDimensions", Arrays.asList("0=minecraft:overworld", "-1=minecraft:the_nether", "1=minecraft:the_end"), FoundationConfig::validDimension);
        builder.pop();
        builder.push("factions");
        STARTING_PRESTIGE = builder.comment("Initial stored prestige. Economy ticking is not ported yet.").defineInRange("startingPrestige", 250D, 0D, 1000000D);
        BASE_PRESTIGE = builder.comment("Initial stored hourly generation; passive generation is not ported yet.").defineInRange("basePrestigeGeneration", 25D, 0D, 100000D);
        builder.pop();
        builder.push("protection");
        PLAYER_PROTECTION = builder.comment("Enable new-player PvP grace. The runtime /xc newplayerprotection toggle starts disabled each server session, as in the legacy lifecycle.").define("enableNewPlayerProtection", false);
        PVP_HOURS = builder.defineInRange("pvpGraceDurationHours", 4D, 0D, 8760D);
        KEEP_INVENTORY_HOURS = builder.comment("Legacy keepInvUntil timestamp only: retained for compatibility, without inventory retention (the reference has no enforcement path).").defineInRange("keepInventoryDurationHours", 24D, 0D, 8760D);
        builder.pop();
        SPEC = builder.build();
    }
    private static boolean validDimension(Object value) {
        if (!(value instanceof String)) return false;
        String[] parts = ((String)value).split("=", -1);
        if (parts.length != 2) return false;
        try { Integer.parseInt(parts[0]); return ResourceLocation.tryParse(parts[1]) != null; }
        catch (NumberFormatException invalid) { return false; }
    }
    public static Integer legacyId(ResourceLocation key) {
        Integer result = null;
        for (String entry : DIMENSIONS.get()) {
            String[] pair = entry.split("=", -1);
            if (key.toString().equals(pair[1])) {
                if (result != null) throw new IllegalStateException("Ambiguous legacy dimension mapping for " + key);
                result = Integer.valueOf(pair[0]);
            }
        }
        if (result != null) {
            int occurrences = 0;
            for (String entry : DIMENSIONS.get()) if (Integer.parseInt(entry.split("=", -1)[0]) == result) occurrences++;
            if (occurrences != 1) throw new IllegalStateException("Duplicate legacy dimension ID " + result);
        }
        return result;
    }
    private FoundationConfig() { }
}
