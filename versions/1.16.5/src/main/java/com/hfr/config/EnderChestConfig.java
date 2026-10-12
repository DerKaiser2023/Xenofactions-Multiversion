package com.hfr.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

public final class EnderChestConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> MODE;
    public static final ForgeConfigSpec.IntValue TOTAL;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEMS, STORAGE, LIMITS;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Restrict deposits only; vanilla saved contents and withdrawals remain valid.").define("enabled", false);
        MODE = b.define("mode", "blacklist", v -> "blacklist".equals(v) || "whitelist".equals(v));
        ITEMS = b.comment("Native registry names. Modern items have no legacy metadata variants.").defineList("itemRules", Collections.emptyList(), v -> v instanceof String);
        STORAGE = b.comment("Optional portable storage deny list; absent items are ignored. Adjust names for your HBM port.").defineList("storageDenyList", Arrays.asList(
            "hbm:item.plastic_bag", "hbm:item.containment_box", "hbm:item.kit_toolbox", "hbm:item.kit_custom",
            "hbm:tile.crate_iron", "hbm:tile.crate_steel", "hbm:tile.crate_desh", "hbm:tile.crate_tungsten", "hbm:tile.crate_template", "hbm:tile.safe"), v -> v instanceof String);
        TOTAL = b.comment("Individual item count across all slots; -1 unlimited.").defineInRange("maxTotalItems", -1, -1, Integer.MAX_VALUE);
        LIMITS = b.comment("Native registry name=quantity; -1 unlimited, 0 denies deposits.").defineList("itemQuantityLimits", Collections.emptyList(), v -> v instanceof String);
        SPEC = b.build();
    }
    private EnderChestConfig() { }
}
