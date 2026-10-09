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
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("foundation");
        IDENTITY_MODE = builder.comment("Faction inspection identity: UUID, NAME, or AUTO. AUTO uses names in development/offline servers, matching 1.7.10.")
                .define("playerIdentityMode", "AUTO", value -> value instanceof String
                        && Arrays.asList("UUID", "NAME", "AUTO").contains(((String)value).toUpperCase(Locale.ROOT)));
        DIMENSIONS = builder.comment("Explicit legacy dimension ID to native dimension key mappings. Unknown IDs are never guessed.")
                .defineList("legacyDimensions", Arrays.asList("0=minecraft:overworld", "-1=minecraft:the_nether", "1=minecraft:the_end"), FoundationConfig::validDimension);
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
