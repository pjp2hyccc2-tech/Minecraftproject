package com.birdiecraft.client;

import com.birdiecraft.golf.engine.BallPhysics;
import com.birdiecraft.golf.engine.Club;
import com.birdiecraft.golf.engine.ShotCalculator;
import com.birdiecraft.golf.engine.ShotInput;
import com.birdiecraft.golf.engine.ShotLaunch;
import com.birdiecraft.golf.engine.ShotMode;
import com.birdiecraft.golf.engine.Trajectory;
import com.birdiecraft.golf.engine.Wind;
import com.birdiecraft.world.LevelTerrain;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Predicted first landing point of the current swing (no wind, middle of the lie range),
 * recomputed only when the inputs change.
 */
public final class LandingPreview {
    private static Vec3 fullLanding;
    private static Vec3 powerLanding;
    private static String fullKey = "";
    private static String powerKey = "";

    private LandingPreview() {
    }

    public static Vec3 full() {
        return fullLanding;
    }

    public static Vec3 atCurrentPower() {
        return powerLanding;
    }

    public static void clear() {
        fullLanding = null;
        powerLanding = null;
        fullKey = "";
        powerKey = "";
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !SwingController.holdingClub() || SwingController.club.isPutter()) {
            clear();
            return;
        }
        boolean show = SwingController.canSwing() || SwingController.isActive();
        if (!show) {
            clear();
            return;
        }
        Vec3 o = SwingController.origin();
        float yaw = mc.player.getYRot();
        Club club = SwingController.club;
        ShotMode mode = SwingController.mode;
        String base = club.ordinal() + "|" + mode.ordinal() + "|" + SwingController.spinX + "|" + SwingController.spinY + "|"
                + Math.round(yaw * 4) + "|" + Math.round(o.x * 20) + "|" + Math.round(o.y * 20) + "|" + Math.round(o.z * 20)
                + "|" + SwingController.lie.ordinal();
        if (!base.equals(fullKey)) {
            fullKey = base;
            fullLanding = simulate(o, yaw, 1.0);
        }
        if (SwingController.phase == SwingController.Phase.RISING) {
            double p = SwingController.gaugePosition();
            String k = base + "|" + Math.round(p * 50);
            if (!k.equals(powerKey)) {
                powerKey = k;
                powerLanding = simulate(o, yaw, p);
            }
        } else if (SwingController.phase != SwingController.Phase.RETURNING) {
            powerLanding = null;
            powerKey = "";
        }
    }

    private static Vec3 simulate(Vec3 o, float yaw, double power) {
        Minecraft mc = Minecraft.getInstance();
        ShotInput in = new ShotInput(SwingController.club, SwingController.mode, yaw, power, 0.36, SwingController.spinX,
                SwingController.spinY, SwingController.puttRange);
        double lieF = SwingController.lie.midPower(SwingController.club);
        ShotLaunch launch = ShotCalculator.compute(in, SwingController.lie, SwingController.style(), o.x, o.y, o.z, lieF, 1L);
        Trajectory t = BallPhysics.simulateCarry(new LevelTerrain(mc.level), launch, Wind.CALM, 600);
        return new Vec3(t.endX, t.endY, t.endZ);
    }
}
