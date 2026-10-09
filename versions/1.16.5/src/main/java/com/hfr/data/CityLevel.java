package com.hfr.data;

public enum CityLevel {
    SETTLEMENT("Settlement"), TOWN("Town"), CITY("City"), METROPOLIS("Metropolis"), CAPITAL("Capital");
    public final String display;
    CityLevel(String display) { this.display = display; }
    public static CityLevel at(int level) { return values()[level >= 0 && level < 5 ? level : 0]; }
}
