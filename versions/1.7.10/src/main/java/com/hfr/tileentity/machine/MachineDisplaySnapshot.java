package com.hfr.tileentity.machine;

import com.hfr.main.MainRegistry;
import com.hfr.config.XFConfig;

public final class MachineDisplaySnapshot {
    public static final int MIN_SECONDS = 0;
    public static final int MAX_SECONDS = 1000000;

    private static volatile MachineDisplaySnapshot clientSnapshot;

    public final int superFishrate;
    public final int goodFishrate;
    public final int averageFishrate;
    public final int crapFishrate;
    public final int jamRate;
    public final int whaleChance;
    public final int uniRate;
    public final int uniJamRate;
    public final int factoryRate;

    public final int factoryJamRate;
    public final int temple;
    public final int fishingOutput, grainmillOutput, universityOutput, factoryOutput, templeOutput;
    public final float grainmillPrestige, universityPrestige, templePrestige, statuePrestige, cityCenterPrestige, tentUpkeep;

    public MachineDisplaySnapshot(int superFishrate, int goodFishrate, int averageFishrate, int crapFishrate, int jamRate, int whaleChance, int uniRate, int uniJamRate, int factoryRate, int factoryJamRate, int temple, int[] outputs, float[] prestige) {
        this.superFishrate = clamp(superFishrate, MIN_SECONDS, MAX_SECONDS);
        this.goodFishrate = clamp(goodFishrate, MIN_SECONDS, MAX_SECONDS);
        this.averageFishrate = clamp(averageFishrate, MIN_SECONDS, MAX_SECONDS);
        this.crapFishrate = clamp(crapFishrate, MIN_SECONDS, MAX_SECONDS);
        this.jamRate = clamp(jamRate, MIN_SECONDS, MAX_SECONDS);
        this.whaleChance = clamp(whaleChance, 0, 100);
        this.uniRate = clamp(uniRate, MIN_SECONDS, MAX_SECONDS);
        this.uniJamRate = clamp(uniJamRate, MIN_SECONDS, MAX_SECONDS);
        this.factoryRate = clamp(factoryRate, MIN_SECONDS, MAX_SECONDS);

        this.factoryJamRate = clamp(factoryJamRate, MIN_SECONDS, MAX_SECONDS);
        this.temple = clamp(temple, MIN_SECONDS, MAX_SECONDS);
        fishingOutput = clamp(outputs[0], 1, 64); grainmillOutput = clamp(outputs[1], 1, 64);
        universityOutput = clamp(outputs[2], 1, 64); factoryOutput = clamp(outputs[3], 1, 64); templeOutput = clamp(outputs[4], 1, 64);
        grainmillPrestige = prestige[0]; universityPrestige = prestige[1]; templePrestige = prestige[2];
        statuePrestige = prestige[3]; cityCenterPrestige = prestige[4]; tentUpkeep = prestige[5];

    }

    public static MachineDisplaySnapshot fromRuntime() {
        return new MachineDisplaySnapshot(MainRegistry.superFishrate, MainRegistry.goodFishrate, MainRegistry.averageFishrate, MainRegistry.crapFishrate, MainRegistry.jamRate, MainRegistry.whaleChance, MainRegistry.uniRate, MainRegistry.uniJamRate, MainRegistry.factoryRate, MainRegistry.factoryJamRate, MainRegistry.temple,
            new int[] { XFConfig.fishingOutputAmount, XFConfig.grainmillOutputAmount, XFConfig.universityOutputAmount, XFConfig.factoryOutputAmount, XFConfig.templeOutputAmount },
            new float[] { XFConfig.grainmillPrestige, XFConfig.universityPrestige, XFConfig.templePrestige, XFConfig.statuePrestige, XFConfig.cityCenterPrestige, XFConfig.warpTentUpkeep });
    }

    public static MachineDisplaySnapshot forClientDisplay() {
        MachineDisplaySnapshot snapshot = clientSnapshot;
        return snapshot == null ? fromRuntime() : snapshot;
    }

    public static void replaceClientSnapshot(MachineDisplaySnapshot snapshot) { clientSnapshot = snapshot; }
    public static void clearClientSnapshot() { clientSnapshot = null; }

    public static int fishRateForBiomeGroup(String group, MachineDisplaySnapshot values) {
        if ("ocean".equals(group)) return values.superFishrate;
        if ("river".equals(group)) return values.goodFishrate;
        if ("dry".equals(group)) return values.crapFishrate;
        return values.averageFishrate;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
