package com.nihonisshu.tabi.client;

import com.nihonisshu.tabi.entity.VehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ViewportEvent;

/** Gentle rocking of the view while riding a moving train or bus, with a small jolt at each rail joint. */
public final class CameraSway {
    private CameraSway() {}

    private static float jolt;
    private static float joltVel;

    public static void jolt(float amount) {
        joltVel += amount;
    }

    public static void apply(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof VehicleEntity v)) {
            jolt = 0;
            joltVel = 0;
            return;
        }
        if (!mc.options.getCameraType().isFirstPerson()) return;
        float speed = Math.min(1f, v.getSpeed() / 1.0f);
        double t = (mc.player.tickCount + e.getPartialTick()) / 20.0;
        float roll = (float) (Math.sin(t * 1.7) * 0.45 + Math.sin(t * 4.3) * 0.12) * speed;
        // damped spring for joint jolts
        joltVel += -jolt * 0.35f;
        joltVel *= 0.78f;
        jolt += joltVel;
        e.setRoll(e.getRoll() + roll + jolt * 0.6f);
        e.setPitch(e.getPitch() + jolt * 0.25f + (float) Math.sin(t * 9.0) * 0.05f * speed);
    }
}
