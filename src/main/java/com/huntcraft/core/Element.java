package com.huntcraft.core;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Elements (FIRE..DRAGON) and statuses (POISON..BLAST) carried by weapons and monster attacks.
 */
public enum Element {
    NONE(0xFFFFFF),
    FIRE(0xFF6A2A),
    WATER(0x3D8BFF),
    THUNDER(0xFFE23D),
    ICE(0x9BE7FF),
    DRAGON(0xB04CFF),
    POISON(0xA14BD8),
    PARALYSIS(0xF2D21B),
    SLEEP(0x7FA8FF),
    BLAST(0xFF9A3D);

    public final int color;

    Element(int color) {
        this.color = color;
    }

    public boolean isElemental() {
        return this == FIRE || this == WATER || this == THUNDER || this == ICE || this == DRAGON;
    }

    public boolean isStatus() {
        return this == POISON || this == PARALYSIS || this == SLEEP || this == BLAST;
    }

    public String key() {
        return "element.huntcraft." + name().toLowerCase();
    }

    public MutableComponent displayName() {
        return Component.translatable(key()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
    }

    public static Element byName(String s) {
        for (Element e : values()) if (e.name().equalsIgnoreCase(s)) return e;
        return NONE;
    }
}
