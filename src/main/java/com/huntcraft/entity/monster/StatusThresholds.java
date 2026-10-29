package com.huntcraft.entity.monster;

/**
 * Status buildup thresholds. Each proc raises the threshold by {@code increment} up to {@code max}.
 * KO is built by blunt hits on koZone parts; exhaust by blunt hits anywhere.
 */
public record StatusThresholds(float poison, float paralysis, float sleep, float blast, float ko, float exhaust,
                               float increment, float max) {
    public static final StatusThresholds LARGE_DEFAULT = new StatusThresholds(180, 150, 150, 120, 150, 200, 1.5F, 3.0F);
    public static final StatusThresholds SMALL_DEFAULT = new StatusThresholds(40, 30, 30, 30, 40, 60, 1.2F, 2.0F);

    public float base(int statusIndex) {
        return switch (statusIndex) {
            case 0 -> poison;
            case 1 -> paralysis;
            case 2 -> sleep;
            case 3 -> blast;
            case 4 -> ko;
            default -> exhaust;
        };
    }
}
