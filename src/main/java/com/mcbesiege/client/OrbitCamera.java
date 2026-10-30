package com.mcbesiege.client;

import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.entity.MachineEntity;
import com.mcbesiege.entity.MachineView;
import com.mcbesiege.part.PartType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Besiege-style orbit camera around the controlled machine (a client-only marker stand used as camera entity). */
public final class OrbitCamera {
    private OrbitCamera() {}

    static ArmorStand cam;
    static boolean active;
    static CameraType prevType;
    public static float yaw, pitch = 22f, dist = 11f;
    static int target = -1;

    public static boolean isActive() { return active; }

    public static void begin(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        target = entityId;
        if (!active) {
            cam = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
            CompoundTag flags = new CompoundTag();
            cam.addAdditionalSaveData(flags);
            flags.putBoolean("Marker", true);
            flags.putBoolean("Invisible", true);
            cam.readAdditionalSaveData(flags);
            cam.setInvisible(true);
            cam.setNoGravity(true);
            prevType = mc.options.getCameraType();
            yaw = mc.player.getYRot();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            active = true;
            update(1f);
            mc.setCameraEntity(cam);
        }
    }

    public static void end() {
        Minecraft mc = Minecraft.getInstance();
        if (!active) return;
        active = false;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (prevType != null) mc.options.setCameraType(prevType);
        cam = null;
        target = -1;
    }

    /** Called at the start of every render frame. */
    public static void update(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || cam == null || mc.level == null) return;
        if (mc.getCameraEntity() != cam && mc.player != null) mc.setCameraEntity(cam);
        Entity e = mc.level.getEntity(target);
        Vec3 center;
        if (e instanceof MachineEntity me) center = machineCenter(me, pt);
        else if (mc.player != null) center = mc.player.getEyePosition(pt);
        else return;
        float yr = yaw * Mth.DEG_TO_RAD, pr = pitch * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yr) * Mth.cos(pr), -Mth.sin(pr), Mth.cos(yr) * Mth.cos(pr));
        Vec3 pos = center.subtract(fwd.scale(dist));
        BlockHitResult hit = mc.level.clip(new ClipContext(center, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, cam));
        if (hit.getType() == HitResult.Type.BLOCK) pos = hit.getLocation().add(fwd.scale(0.4));
        cam.setPos(pos.x, pos.y, pos.z);
        cam.xo = pos.x;
        cam.yo = pos.y;
        cam.zo = pos.z;
        cam.xOld = pos.x;
        cam.yOld = pos.y;
        cam.zOld = pos.z;
        cam.setYRot(yaw);
        cam.setXRot(pitch);
        cam.yRotO = yaw;
        cam.xRotO = pitch;
        cam.setYHeadRot(yaw);
        cam.yHeadRotO = yaw;
    }

    static Vec3 machineCenter(MachineEntity me, float pt) {
        MachineView v = me.view;
        double[] pose = new double[7];
        for (MachineView.SubView s : v.subs) {
            if (s.state.getBlock() instanceof MachinePartBlock b && b.type == PartType.STARTING_BLOCK && v.pose(s.body, pt, pose)) {
                double[] w = MachineView.transform(pose, s.ox, s.oy, s.oz);
                return new Vec3(w[0], w[1], w[2]);
            }
        }
        if (!v.subs.isEmpty() && v.pose(v.subs.get(0).body, pt, pose)) return new Vec3(pose[0], pose[1], pose[2]);
        return me.getPosition(pt).add(0, 0.5, 0);
    }
}
