package com.huntcraft.core;

/** MH sharpness colours with raw / element multipliers. */
public enum Sharpness {
    RED(0.50F, 0.25F, 0xD8322E),
    ORANGE(0.75F, 0.50F, 0xF08A24),
    YELLOW(1.00F, 0.75F, 0xF2D93B),
    GREEN(1.05F, 1.00F, 0x5BCB3C),
    BLUE(1.20F, 1.0625F, 0x3C7BEA),
    WHITE(1.32F, 1.15F, 0xF4F4F4),
    PURPLE(1.39F, 1.25F, 0xB44CE6);

    public final float rawMult;
    public final float elemMult;
    public final int color;

    Sharpness(float raw, float elem, int color) {
        this.rawMult = raw;
        this.elemMult = elem;
        this.color = color;
    }

    public static Sharpness byIndex(int i) {
        Sharpness[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
