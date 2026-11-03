package com.hitori.client.sound;

import java.util.HashMap;
import java.util.Map;

/**
 * The room reverb presets of docs/DESIGN.md §2 / §10 (the rooms of コーポ白樺 203) as EAX reverb parameter vectors
 * (the standard-reverb fallback uses the subset it knows). Values are in EFX units; {@link #SLOT_GAIN} is the
 * auxiliary slot's gain (0 = reverb off). Vectors are cross-faded component-wise, so every preset defines every
 * component. Measured by tools/audio/efx_selftest.py through OpenAL Soft.
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
        // 押し入れ: a box one block deep, futons above, your own body filling it - dead, close, a boxy first reflection
        def("tiny_dead",  0.10f, 0.50f, 0.180f, 0.25f, 1.0f, 0.10f, 0.30f, 1.0f, 0.50f, 0.001f, 0.05f, 0.001f, 0.25f, 0.00f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        def("closet",     0.10f, 0.50f, 0.180f, 0.25f, 1.0f, 0.10f, 0.30f, 1.0f, 0.50f, 0.001f, 0.05f, 0.001f, 0.25f, 0.00f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        // 浴室 / トイレ: tiny, glazed tiles everywhere, a deep tub of water - bright, ringing, grainy small-room density
        def("restroom",   0.25f, 0.90f, 0.316f, 0.80f, 1.0f, 1.10f, 0.85f, 1.0f, 0.90f, 0.003f, 2.20f, 0.006f, 0.25f, 0.00f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        // 和室 / 洋室: tatami, futons, curtains, a bed - short and soft, the highs gone at once
        def("small_soft", 0.90f, 1.00f, 0.300f, 0.40f, 1.0f, 0.38f, 0.40f, 1.1f, 0.25f, 0.003f, 0.45f, 0.005f, 0.25f, 0.00f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        // 玄関 / DK / 脱衣所: tiles, the steel door, vinyl, cabinets, the steel sink - small and hard
        def("small_hard", 0.50f, 0.90f, 0.316f, 0.70f, 1.0f, 0.55f, 0.75f, 1.0f, 0.50f, 0.004f, 1.00f, 0.006f, 0.25f, 0.00f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        // 廊下: two blocks wide, plaster walls, old boards - a flutter echo between the walls
        def("corridor",   0.40f, 0.80f, 0.316f, 0.65f, 1.0f, 0.80f, 0.60f, 1.0f, 0.35f, 0.006f, 1.30f, 0.010f, 0.080f, 0.25f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        // mixed / unknown rooms
        def("medium",     0.70f, 1.00f, 0.316f, 0.60f, 1.0f, 0.70f, 0.70f, 1.0f, 0.30f, 0.008f, 1.00f, 0.012f, 0.25f, 0.00f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
        // 外廊下 / the street: open air in the rain, a slap back from the building across the street
        def("outside",    0.30f, 0.40f, 0.220f, 0.50f, 1.0f, 0.50f, 0.50f, 1.0f, 0.40f, 0.050f, 0.12f, 0.040f, 0.20f, 0.20f, 0.25f, 0.00f, 0.994f, 5000f, 250f, 1.0f);
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
