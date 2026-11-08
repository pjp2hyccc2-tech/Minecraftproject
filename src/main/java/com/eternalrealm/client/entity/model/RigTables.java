package com.eternalrealm.client.entity.model;

import net.minecraft.client.animation.AnimationDefinition;

import java.util.function.Function;

/**
 * The generated data of one rig (see tools/art_entities/generate.py): variant ids, per-variant part visibility
 * and rest adjustments, model scales, emissive flags and the animation of every slot.
 */
public record RigTables(String rig, String[] variants, String[] toggledParts, boolean[][] visible,
                        String[] posedParts, float[][][] poses, float[] scales, boolean[] glows,
                        Function<String, AnimationDefinition[]> animations) {

    /** Index of a variant (0 for unknown ids). */
    public int indexOf(String variant) {
        for (int i = 0; i < this.variants.length; i++) {
            if (this.variants[i].equals(variant)) {
                return i;
            }
        }
        return 0;
    }
}
