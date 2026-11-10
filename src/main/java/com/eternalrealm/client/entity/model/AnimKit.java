package com.eternalrealm.client.entity.model;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import org.joml.Vector3f;

/**
 * Compact keyframe channels for the generated animation classes: groups of four floats {time (s), x, y, z}.
 * Rotations in degrees, positions as {@link KeyframeAnimations#posVec} (y up), scales as absolute factors.
 *
 * <p>Easing: the boolean form gives every key the same interpolation (Catmull-Rom or linear); the string form gives
 * each key its own curve for the segment that ends at it (vanilla also uses the interpolation of the target key), one
 * character per key, the same codes and curves as tools/art_entities/ermodel.py: {@code c} Catmull-Rom, {@code l}
 * linear, {@code i}/{@code o}/{@code s} cubic ease in/out/in-out, {@code I}/{@code O} quartic ease in/out,
 * {@code b} back-out (overshoots, then settles on the key), {@code h} hold (jumps at the key).</p>
 */
public final class AnimKit {
    private static final float BACK_C1 = 1.70158F;
    private static final AnimationChannel.Interpolation EASE_IN = eased('i');
    private static final AnimationChannel.Interpolation EASE_OUT = eased('o');
    private static final AnimationChannel.Interpolation EASE_IN_OUT = eased('s');
    private static final AnimationChannel.Interpolation STRONG_IN = eased('I');
    private static final AnimationChannel.Interpolation STRONG_OUT = eased('O');
    private static final AnimationChannel.Interpolation BACK_OUT = eased('b');
    private static final AnimationChannel.Interpolation HOLD = eased('h');

    private AnimKit() {
    }

    public static AnimationChannel rot(boolean linear, float... values) {
        return channel(AnimationChannel.Targets.ROTATION, uniform(linear, values), values, 0);
    }

    public static AnimationChannel pos(boolean linear, float... values) {
        return channel(AnimationChannel.Targets.POSITION, uniform(linear, values), values, 1);
    }

    public static AnimationChannel scale(boolean linear, float... values) {
        return channel(AnimationChannel.Targets.SCALE, uniform(linear, values), values, 2);
    }

    public static AnimationChannel rot(String eases, float... values) {
        return channel(AnimationChannel.Targets.ROTATION, eases, values, 0);
    }

    public static AnimationChannel pos(String eases, float... values) {
        return channel(AnimationChannel.Targets.POSITION, eases, values, 1);
    }

    public static AnimationChannel scale(String eases, float... values) {
        return channel(AnimationChannel.Targets.SCALE, eases, values, 2);
    }

    /** The eased fraction of a segment (codes other than Catmull-Rom); public for the model smoke test. */
    public static float ease(char code, float a) {
        return switch (code) {
            case 'l' -> a;
            case 'i' -> a * a * a;
            case 'o' -> 1.0F - cube(1.0F - a);
            case 's' -> a < 0.5F ? 4.0F * a * a * a : 1.0F - cube(-2.0F * a + 2.0F) / 2.0F;
            case 'I' -> a * a * a * a;
            case 'O' -> 1.0F - (1.0F - a) * (1.0F - a) * (1.0F - a) * (1.0F - a);
            case 'b' -> {
                float u = a - 1.0F;
                yield 1.0F + (BACK_C1 + 1.0F) * u * u * u + BACK_C1 * u * u;
            }
            case 'h' -> a >= 1.0F ? 1.0F : 0.0F;
            default -> throw new IllegalArgumentException("unknown ease " + code);
        };
    }

    private static float cube(float v) {
        return v * v * v;
    }

    private static String uniform(boolean linear, float[] values) {
        return String.valueOf(linear ? 'l' : 'c').repeat(values.length / 4);
    }

    private static AnimationChannel.Interpolation eased(char code) {
        return (dest, alpha, keyframes, from, to, scale) -> {
            Vector3f start = keyframes[from].target();
            Vector3f end = keyframes[to].target();
            return start.lerp(end, ease(code, alpha), dest).mul(scale);
        };
    }

    private static AnimationChannel.Interpolation interpolation(char code) {
        return switch (code) {
            case 'c' -> AnimationChannel.Interpolations.CATMULLROM;
            case 'l' -> AnimationChannel.Interpolations.LINEAR;
            case 'i' -> EASE_IN;
            case 'o' -> EASE_OUT;
            case 's' -> EASE_IN_OUT;
            case 'I' -> STRONG_IN;
            case 'O' -> STRONG_OUT;
            case 'b' -> BACK_OUT;
            case 'h' -> HOLD;
            default -> throw new IllegalArgumentException("unknown ease " + code);
        };
    }

    private static AnimationChannel channel(AnimationChannel.Target target, String eases, float[] values, int kind) {
        if (values.length == 0 || values.length % 4 != 0) {
            throw new IllegalArgumentException("keyframes need groups of 4 values, got " + values.length);
        }
        Keyframe[] keyframes = new Keyframe[values.length / 4];
        if (eases.length() != keyframes.length) {
            throw new IllegalArgumentException("one ease per keyframe: " + eases.length() + " for " + keyframes.length);
        }
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
            keyframes[i] = new Keyframe(time, vector, interpolation(eases.charAt(i)));
        }
        return new AnimationChannel(target, keyframes);
    }
}
