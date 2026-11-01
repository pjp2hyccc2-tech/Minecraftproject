package com.frostbound.client;

import com.frostbound.core.city.City;
import com.frostbound.world.Terrain;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.lwjgl.glfw.GLFW;

/**
 * Top-down management camera: a client-only invisible marker armor stand used as the camera entity, orbiting
 * a focus point on the ground. WASD / arrows pan, Q/E rotate, mouse wheel zooms, right-drag rotates and
 * middle-drag pans.
 */
public final class CameraController {
    private static ArmorStand cam;
    private static boolean active;
    private static CameraType prevType;
    public static double fx;
    public static double fz;
    public static float yaw = 180f;
    public static float pitch = 58f;
    public static float dist = 70f;
    private static long lastNanos;
    private static boolean placed;

    private CameraController() {
    }

    public static boolean active() {
        return active;
    }

    public static void begin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || active) {
            return;
        }
        cam = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
        CompoundTag flags = new CompoundTag();
        cam.addAdditionalSaveData(flags);
        flags.putBoolean("Marker", true);
        flags.putBoolean("Invisible", true);
        cam.readAdditionalSaveData(flags);
        cam.setInvisible(true);
        cam.setNoGravity(true);
        prevType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (!placed && ClientCity.hasCity()) {
            CompoundTag g = ClientCity.generator();
            fx = g.getInt("x") + 0.5;
            fz = g.getInt("z") + 8.5;
            placed = true;
        } else if (!placed) {
            fx = mc.player.getX();
            fz = mc.player.getZ();
        }
        active = true;
        lastNanos = Util.getNanos();
        update();
        mc.setCameraEntity(cam);
    }

    public static void end() {
        Minecraft mc = Minecraft.getInstance();
        if (!active) {
            return;
        }
        active = false;
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        if (prevType != null) {
            mc.options.setCameraType(prevType);
        }
        cam = null;
    }

    /** Clears the remembered focus (new world). */
    public static void forget() {
        placed = false;
        yaw = 180f;
        pitch = 58f;
        dist = 70f;
    }

    public static void focus(double x, double z) {
        fx = x;
        fz = z;
        placed = true;
    }

    public static void zoom(double scroll) {
        dist = Mth.clamp(dist * (float) Math.pow(0.88, scroll), 12f, 130f);
    }

    public static void rotate(double dyaw, double dpitch) {
        yaw = Mth.wrapDegrees(yaw + (float) dyaw);
        pitch = Mth.clamp(pitch + (float) dpitch, 25f, 89f);
    }

    public static void pan(double right, double forward) {
        double yr = Math.toRadians(yaw);
        double fwdX = -Math.sin(yr);
        double fwdZ = Math.cos(yr);
        double rightX = -Math.cos(yr);
        double rightZ = -Math.sin(yr);
        fx += rightX * right + fwdX * forward;
        fz += rightZ * right + fwdZ * forward;
        double cx = 0;
        double cz = 0;
        if (ClientCity.hasCity()) {
            cx = ClientCity.generator().getInt("x");
            cz = ClientCity.generator().getInt("z");
        }
        double lim = City.CITY_RADIUS + 48;
        double dx = fx - cx;
        double dz = fz - cz;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > lim) {
            fx = cx + dx / d * lim;
            fz = cz + dz / d * lim;
        }
    }

    /** Moves the camera with held keys and positions the marker; called at the start of every frame. */
    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (!active || cam == null || mc.level == null) {
            return;
        }
        long now = Util.getNanos();
        double dt = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (mc.screen instanceof ManagementScreen ms && !ms.typing()) {
            long win = mc.getWindow().getWindow();
            double speed = dist * 0.9 * dt * (InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_SHIFT) ? 2.5 : 1.0);
            double f = 0;
            double r = 0;
            if (InputConstants.isKeyDown(win, GLFW.GLFW_KEY_W) || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_UP)) {
                f += speed;
            }
            if (InputConstants.isKeyDown(win, GLFW.GLFW_KEY_S) || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_DOWN)) {
                f -= speed;
            }
            if (InputConstants.isKeyDown(win, GLFW.GLFW_KEY_D) || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT)) {
                r += speed;
            }
            if (InputConstants.isKeyDown(win, GLFW.GLFW_KEY_A) || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT)) {
                r -= speed;
            }
            if (f != 0 || r != 0) {
                pan(r, f);
            }
            if (InputConstants.isKeyDown(win, GLFW.GLFW_KEY_Q)) {
                rotate(-90 * dt, 0);
            }
            if (InputConstants.isKeyDown(win, GLFW.GLFW_KEY_E)) {
                rotate(90 * dt, 0);
            }
        }
        if (mc.getCameraEntity() != cam && mc.player != null) {
            mc.setCameraEntity(cam);
        }
        double ground = Terrain.height((int) Math.floor(fx), (int) Math.floor(fz)) + 1;
        float yr = yaw * Mth.DEG_TO_RAD;
        float pr = pitch * Mth.DEG_TO_RAD;
        double dx = -Mth.sin(yr) * Mth.cos(pr);
        double dy = -Mth.sin(pr);
        double dz = Mth.cos(yr) * Mth.cos(pr);
        double x = fx - dx * dist;
        double y = ground - dy * dist;
        double z = fz - dz * dist;
        cam.setPos(x, y, z);
        cam.xo = x;
        cam.yo = y;
        cam.zo = z;
        cam.xOld = x;
        cam.yOld = y;
        cam.zOld = z;
        cam.setYRot(yaw);
        cam.setXRot(pitch);
        cam.yRotO = yaw;
        cam.xRotO = pitch;
        cam.setYHeadRot(yaw);
        cam.yHeadRotO = yaw;
    }
}
