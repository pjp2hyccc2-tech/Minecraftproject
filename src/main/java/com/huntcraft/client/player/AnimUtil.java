package com.huntcraft.client.player;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** Keyframe sampling like vanilla KeyframeAnimations.animate, with explicit time, weight and per-bone weights. */
@OnlyIn(Dist.CLIENT)
public final class AnimUtil {
    private static final Vector3f CACHE = new Vector3f();

    private AnimUtil() {
    }

    /**
     * Adds the animation's offsets (rotation / position / scale) at `seconds` to the posed model.
     *
     * @param weight     global multiplier (1 = full pose)
     * @param boneWeight optional extra multiplier per bone name
     */
    public static void animate(HunterModel model, AnimationDefinition def, float seconds, float weight, @Nullable ToDoubleFunction<String> boneWeight) {
        if (weight <= 0.0001F) return;
        float len = def.lengthInSeconds();
        float f = def.looping() && len > 0 ? seconds % len : seconds;
        if (f < 0) f = 0;
        for (Map.Entry<String, List<AnimationChannel>> e : def.boneAnimations().entrySet()) {
            ModelPart part = model.bone(e.getKey());
            if (part == null) continue;
            float w = weight * (boneWeight == null ? 1.0F : (float) boneWeight.applyAsDouble(e.getKey()));
            if (w <= 0.0001F) continue;
            for (AnimationChannel ch : e.getValue()) {
                Keyframe[] kfs = ch.keyframes();
                if (kfs.length == 0) continue;
                final float ff = f;
                int i = Math.max(0, Mth.binarySearch(0, kfs.length, idx -> ff <= kfs[idx].timestamp()) - 1);
                int j = Math.min(kfs.length - 1, i + 1);
                Keyframe k0 = kfs[i];
                Keyframe k1 = kfs[j];
                float f1 = f - k0.timestamp();
                float f2 = j != i ? Mth.clamp(f1 / (k1.timestamp() - k0.timestamp()), 0.0F, 1.0F) : 0.0F;
                k1.interpolation().apply(CACHE, f2, kfs, i, j, w);
                ch.target().apply(part, CACHE);
            }
        }
    }
}
