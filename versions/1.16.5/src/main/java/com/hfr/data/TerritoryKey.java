package com.hfr.data;

import java.util.Objects;

/** Stored Xenofactions coordinates are deliberately not vanilla chunk coordinates. */
public final class TerritoryKey {
    public final int dimension, x, z;
    public TerritoryKey(int dimension, int x, int z) { this.dimension = dimension; this.x = x; this.z = z; }
    public static TerritoryKey generated(int dimension, int x, int z) { return new TerritoryKey(dimension, (x + 1) / 16, (z + 1) / 16); }
    // The reference's block lookup offsets Z only; preserve that distinction from claim generation.
    public static TerritoryKey lookup(int dimension, int x, int z) { return new TerritoryKey(dimension, x / 16, (z + 1) / 16); }
    public static TerritoryKey parse(String value) {
        String[] pair = value.split(":", 2), coords = pair[pair.length - 1].split(",", -1);
        if (coords.length != 2) throw new IllegalArgumentException("Malformed territory coordinate: " + value);
        return new TerritoryKey(pair.length == 2 ? Integer.parseInt(pair[0]) : 0, Integer.parseInt(coords[0]), Integer.parseInt(coords[1]));
    }
    public static long min(int coordinate) { return coordinate > 0 ? coordinate * 16L - 1 : coordinate < 0 ? coordinate * 16L - 16 : -16; }
    public static long max(int coordinate) { return coordinate > 0 ? coordinate * 16L + 15 : coordinate < 0 ? coordinate * 16L : 15; }
    @Override public boolean equals(Object other) { if (!(other instanceof TerritoryKey)) return false; TerritoryKey k = (TerritoryKey)other; return dimension == k.dimension && x == k.x && z == k.z; }
    @Override public int hashCode() { return Objects.hash(dimension, x, z); }
    @Override public String toString() { return dimension + ":" + x + "," + z; }
}
