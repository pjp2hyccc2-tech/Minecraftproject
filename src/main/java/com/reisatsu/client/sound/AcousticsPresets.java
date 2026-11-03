package com.reisatsu.client.sound;

import java.util.HashMap;
import java.util.Map;

/**
 * The room reverb presets of docs/DESIGN.md §10 as EAX reverb parameter vectors (the standard-reverb fallback uses
 * the subset it knows). Values are in EFX units; {@link #SLOT_GAIN} is the auxiliary slot's gain (0 = reverb off).
 * Vectors are cross-faded component-wise, so every preset defines every component.
 */
final class AcousticsPresets {
    static final int DENSITY = 0, DIFFUSION = 1, GAIN = 2, GAIN_HF = 3, GAIN_LF = 4, DECAY = 5, DECAY_HF = 6,
            DECAY_LF = 7, REFL_GAIN = 8, REFL_DELAY = 9, LATE_GAIN = 10, LATE_DELAY = 11, ECHO_TIME = 12,
            ECHO_DEPTH = 13, MOD_TIME = 14, MOD_DEPTH = 15, AIR_HF = 16, HF_REF = 17, LF_REF = 18, SLOT_GAIN = 19, N = 20;

    private static final Map<String, float[]> PRESETS = new HashMap<>();

    private AcousticsPresets() {}

    private static void def(String key, float... v) {
        if (v.length != N) throw new IllegalStateException("preset " + key + " has " + v.length + " values");
        PRESETS.put(key, v);
    }

    static {
        //                 dens   diff   gain    gHF    gLF   decay  dHF   dLF   rGain  rDelay  lGain  lDelay eTime  eDepth mTime mDepth airHF   hfRef  lfRef  slot
        // 客席: a big low room, vinyl booths and acoustic ceiling tiles - medium, soft, the highs die first
        def("dining",     0.85f, 0.90f, 0.316f, 0.55f, 1.0f, 0.90f, 0.50f, 1.1f, 0.28f, 0.012f, 1.40f, 0.018f, 0.25f, 0.00f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        // 厨房: stainless and quarry tiles - bright, hard, short, a slight flutter between the steel walls
        def("kitchen",    0.45f, 0.75f, 0.316f, 0.90f, 0.9f, 0.70f, 0.95f, 0.9f, 0.55f, 0.006f, 1.25f, 0.009f, 0.10f, 0.08f, 0.25f, 0.02f, 0.994f, 5000f, 250f, 1.0f);
        // トイレ: glazed tiles everywhere - very reflective, grainy small-room density
        def("restroom",   0.20f, 1.00f, 0.316f, 0.75f, 1.0f, 1.45f, 0.85f, 1.0f, 0.65f, 0.006f, 2.60f, 0.010f, 0.25f, 0.00f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        // 風除室 / 倉庫: granite, glass, concrete, steel shelves - small and hard
        def("small_hard", 0.40f, 0.90f, 0.316f, 0.70f, 1.0f, 0.55f, 0.80f, 1.0f, 0.50f, 0.004f, 1.00f, 0.006f, 0.25f, 0.00f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        // 休憩室 / 事務所: lockers, chairs, paper, a worn floor - small and soft
        def("small_soft", 0.90f, 1.00f, 0.316f, 0.35f, 1.0f, 0.32f, 0.35f, 1.0f, 0.20f, 0.003f, 0.35f, 0.005f, 0.25f, 0.00f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        // 通路: narrow, long, tiled - a hallway with a flutter echo between the walls
        def("corridor",   0.36f, 0.80f, 0.316f, 0.70f, 1.0f, 1.10f, 0.60f, 1.0f, 0.30f, 0.007f, 1.50f, 0.011f, 0.085f, 0.25f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        // 冷蔵室: a small insulated box packed with boxes - almost dead, one tight metallic slap
        def("tiny_dead",  0.20f, 0.60f, 0.250f, 0.60f, 1.0f, 0.12f, 0.50f, 1.0f, 0.35f, 0.002f, 0.12f, 0.002f, 0.25f, 0.00f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        // 待合・レジ: medium room, mixed surfaces
        def("medium",     0.70f, 1.00f, 0.316f, 0.60f, 1.0f, 0.75f, 0.70f, 1.0f, 0.30f, 0.008f, 1.00f, 0.012f, 0.25f, 0.00f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        // outside: no room, only a faint slap back from the building's facade
        def("outside",    0.30f, 0.40f, 0.220f, 0.50f, 1.0f, 0.30f, 0.60f, 1.0f, 0.45f, 0.060f, 0.06f, 0.040f, 0.18f, 0.15f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        // off: other dimensions / acoustics disabled (only the slot gain matters)
        def("off",        0.30f, 0.40f, 0.220f, 0.50f, 1.0f, 0.30f, 0.60f, 1.0f, 0.45f, 0.060f, 0.06f, 0.040f, 0.18f, 0.15f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 0.0f);
    }

    /** A copy of the preset with this key; unknown keys fall back to {@code medium}. */
    static float[] get(String key) {
        float[] p = PRESETS.get(key);
        return (p != null ? p : PRESETS.get("medium")).clone();
    }

    static boolean known(String key) {
        return PRESETS.containsKey(key);
    }
}
