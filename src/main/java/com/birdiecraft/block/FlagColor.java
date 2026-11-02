package com.birdiecraft.block;

import net.minecraft.util.StringRepresentable;

/** Pin flag colours (each course theme uses its own). */
public enum FlagColor implements StringRepresentable {
    RED("red", 0xE8433A),
    YELLOW("yellow", 0xF6D232),
    BLUE("blue", 0x3A7BE8),
    PINK("pink", 0xF48FC0),
    ORANGE("orange", 0xF28A2E),
    WHITE("white", 0xF4F4F4);

    public static final FlagColor[] VALUES = values();

    private final String serialized;
    public final int rgb;

    FlagColor(String serialized, int rgb) {
        this.serialized = serialized;
        this.rgb = rgb;
    }

    @Override
    public String getSerializedName() {
        return serialized;
    }

    public static FlagColor byId(int id) {
        if (id < 0 || id >= VALUES.length) return RED;
        return VALUES[id];
    }
}
