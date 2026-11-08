package com.eternalrealm.client;

import net.minecraftforge.client.event.ViewportEvent;

/**
 * Short camera shakes for impact (heavy hits, skills, boss slams). Client thread only. Respects a strength cap and the
 * client config switch.
 */
public final class CameraShake {
    private static float strength;
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

    static void tick() {
        if (remaining > 0) {
            remaining--;
        }
    }

    static void apply(ViewportEvent.ComputeCameraAngles event) {
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
    }
}
