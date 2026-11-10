package com.eternalrealm.client;

import net.minecraftforge.client.event.ViewportEvent;

/**
 * Short camera shakes for impact (heavy hits, skills, boss slams). Client thread only. Respects a strength cap and the
 * client config switch.
 */
public final class CameraShake {
    private static float strength;
    private static float kickDegrees;
    private static int kickRemaining;
    private static int kickTotal;
    private static int remaining;
    private static int total;

    private CameraShake() {
    }

    /** @param intensity degrees of maximum roll, @param ticks duration */
    public static void add(float intensity, int ticks) {
        if (!com.eternalrealm.config.ERClientConfig.CAMERA_SHAKE.get()) {
            return;
        }
        strength = Math.min(4.0F, Math.max(strength * remaining / Math.max(1, total), 0.0F) + intensity);
        remaining = Math.max(remaining, ticks);
        total = remaining;
    }

    /**
     * A camera kick: the view dips by {@code degrees} and springs back over {@code ticks} (the recoil of a heavy hit
     * landing), on top of any shake.
     */
    public static void kick(float degrees, int ticks) {
        if (!com.eternalrealm.config.ERClientConfig.CAMERA_SHAKE.get()) {
            return;
        }
        kickDegrees = Math.min(3.0F, Math.max(kickDegrees * kickRemaining / Math.max(1, kickTotal), 0.0F) + degrees);
        kickRemaining = Math.max(kickRemaining, ticks);
        kickTotal = kickRemaining;
    }

    static void tick() {
        if (remaining > 0) {
            remaining--;
        }
        if (kickRemaining > 0) {
            kickRemaining--;
        }
    }

    static void apply(ViewportEvent.ComputeCameraAngles event) {
        if (kickRemaining > 0 && kickDegrees > 0.0F) {
            // a fast dip that eases back: sin over the first half, then the decay
            float t = 1.0F - (kickRemaining - (float) event.getPartialTick()) / Math.max(1, kickTotal);
            float shape = t < 0.25F ? t / 0.25F : (1.0F - t) / 0.75F;
            event.setPitch(event.getPitch() + kickDegrees * Math.max(0.0F, shape));
        }
        if (remaining <= 0 || strength <= 0.0F) {
            return;
        }
        double time = ClientCombatState.tick() + event.getPartialTick();
        float fade = (remaining - (float) event.getPartialTick()) / Math.max(1, total);
        float amount = strength * Math.max(0.0F, fade);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 2.7) * amount);
        event.setPitch(event.getPitch() + (float) Math.sin(time * 3.9 + 1.3) * amount * 0.6F);
        event.setYaw(event.getYaw() + (float) Math.sin(time * 3.1 + 2.1) * amount * 0.4F);
    }

    static void clear() {
        remaining = 0;
        strength = 0.0F;
        kickRemaining = 0;
        kickDegrees = 0.0F;
        HitStop.clear();
    }
}
