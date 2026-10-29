package com.huntcraft.core;

/** Quest / monster rank. HIGH rank monsters are tougher and drop "+" materials. */
public enum Rank {
    LOW, HIGH;

    public static Rank byOrdinal(int i) {
        return i == 1 ? HIGH : LOW;
    }

    public String key() {
        return "rank.huntcraft." + name().toLowerCase();
    }
}
