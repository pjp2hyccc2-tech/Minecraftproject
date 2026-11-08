package com.eternalrealm.client.entity.model;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import org.joml.Vector3f;

/**
 * Compact keyframe channels for the generated animation classes: groups of four floats {time (s), x, y, z}.
 * Rotations in degrees, positions as {@link KeyframeAnimations#posVec} (y up), scales as absolute factors.
 */
public final class AnimKit {
    private AnimKit() {
    }

    public static AnimationChannel rot(boolean linear, float... values) {
        return channel(AnimationChannel.Targets.ROTATION, linear, values, 0);
    }

    public static AnimationChannel pos(boolean linear, float... values) {
        return channel(AnimationChannel.Targets.POSITION, linear, values, 1);
    }

    public static AnimationChannel scale(boolean linear, float... values) {
        return channel(AnimationChannel.Targets.SCALE, linear, values, 2);
    }

    private static AnimationChannel channel(AnimationChannel.Target target, boolean linear, float[] values, int kind) {
        if (values.length == 0 || values.length % 4 != 0) {
            throw new IllegalArgumentException("keyframes need groups of 4 values, got " + values.length);
        }
        AnimationChannel.Interpolation interpolation =
                linear ? AnimationChannel.Interpolations.LINEAR : AnimationChannel.Interpolations.CATMULLROM;
        Keyframe[] keyframes = new Keyframe[values.length / 4];
        for (int i = 0; i < keyframes.length; i++) {
            float time = values[i * 4];
            float x = values[i * 4 + 1];
            float y = values[i * 4 + 2];
            float z = values[i * 4 + 3];
            Vector3f vector = switch (kind) {
                case 0 -> KeyframeAnimations.degreeVec(x, y, z);
                case 1 -> KeyframeAnimations.posVec(x, y, z);
                default -> KeyframeAnimations.scaleVec(x, y, z);
            };
            keyframes[i] = new Keyframe(time, vector, interpolation);
        }
        return new AnimationChannel(target, keyframes);
    }
}
