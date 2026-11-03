package com.hitori.game;

import com.hitori.entity.FixedCamera;
import com.hitori.registry.ModEntities;
import com.hitori.registry.ModItems;
import com.hitori.registry.ModSounds;
import com.hitori.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The fixed cameras (docs/DESIGN.md §3.1): placed on a block face looking where you look, up to four, numbered in
 * placement order and named after their room (「CAM1 浴室」); picked up again (sneak + right-click) before 3:00.
 * The camera app on the laptop or the phone tells the server which camera is watched (the doll moves stiffly on it,
 * the woman lives in it, the laptop's light leaks from the closet).
 */
public final class Cameras {
    public static final int MAX = 4;

    private Cameras() {}

    public static String room(Vec3 at) {
        Layout.Room r = Layout.roomAt(BlockPos.containing(at.x, at.y + 0.2, at.z));
        return r == null ? "outside" : r.key();
    }

    public static List<FixedCamera> all(ServerLevel level) {
        List<FixedCamera> l = new ArrayList<>(level.getEntitiesOfClass(FixedCamera.class, Doll.AREA, Entity::isAlive));
        l.sort(Comparator.comparingInt(FixedCamera::number));
        return l;
    }

    @Nullable
    public static FixedCamera entity(ServerLevel level, int number) {
        for (FixedCamera c : level.getEntitiesOfClass(FixedCamera.class, Doll.AREA, Entity::isAlive)) if (c.number() == number) return c;
        return null;
    }

    @Nullable
    public static GameData.CamSpot spot(GameData d, int number) {
        for (GameData.CamSpot c : d.cameras) if (c.number() == number) return c;
        return null;
    }

    /** Right-click a block face with a camera: it stands there, looking where you look. */
    public static boolean place(ServerPlayer p, UseOnContext ctx) {
        if (!GameManager.inGame(p)) return false;
        GameData d = GameData.get(p.server);
        if (d.phase != GameData.PHASE_PREP) {
            GameManager.thought(p, "thought.hitori.cameras_fixed");
            return false;
        }
        if (d.cameras.size() >= MAX) {
            GameManager.thought(p, "thought.hitori.cameras_max");
            return false;
        }
        Vec3 hit = ctx.getClickLocation();
        Direction face = ctx.getClickedFace();
        Vec3 at;
        if (face == Direction.UP) {
            at = hit;
        } else if (face == Direction.DOWN) {
            at = hit.add(0.0, -0.32, 0.0);
        } else {
            at = new Vec3(hit.x + face.getStepX() * 0.16, hit.y - 0.15, hit.z + face.getStepZ() * 0.16);
        }
        int number = 1;
        while (spot(d, number) != null) number++;
        float yaw = p.getYRot();
        float pitch = Mth.clamp(p.getXRot(), -60.0f, 70.0f);
        GameData.CamSpot c = new GameData.CamSpot(number, at.x, at.y, at.z, yaw, pitch, false);
        d.cameras.add(c);
        d.setDirty();
        spawn(p.serverLevel(), c);
        ctx.getItemInHand().shrink(1);
        p.serverLevel().playSound(null, at.x, at.y, at.z, ModSounds.CAMERA_PLACE.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        GameManager.event(p, GameManager.EV_THOUGHT, number, "thought.hitori.camera_placed|" + number + "|@room.hitori." + room(at));
        return true;
    }

    /** Sneak + right-click: back into your hands (before 3:00 only). */
    public static boolean pickUp(ServerPlayer p, FixedCamera cam) {
        if (!GameManager.inGame(p)) return false;
        GameData d = GameData.get(p.server);
        if (d.phase != GameData.PHASE_PREP) {
            GameManager.thought(p, "thought.hitori.cameras_fixed");
            return false;
        }
        d.cameras.removeIf(c -> c.number() == cam.number());
        d.setDirty();
        cam.discard();
        GameManager.give(p, new ItemStack(ModItems.FIXED_CAMERA.get()));
        p.serverLevel().playSound(null, cam.getX(), cam.getY(), cam.getZ(), ModSounds.CAMERA_PLACE.get(), SoundSource.BLOCKS, 0.5f, 1.3f);
        return true;
    }

    @Nullable
    private static FixedCamera spawn(ServerLevel level, GameData.CamSpot c) {
        FixedCamera e = ModEntities.CAMERA.get().create(level);
        if (e == null) return null;
        e.moveTo(c.x(), c.y(), c.z(), c.yaw(), c.pitch());
        e.aim(c.yaw(), c.pitch());
        e.setNumber(c.number());
        e.setCamPose(c.fallen() ? FixedCamera.POSE_FALLEN : FixedCamera.POSE_IDLE);
        level.addFreshEntity(e);
        return e;
    }

    /** The cameras of the data are in the level (after a restart, a teleport...), and nothing else. */
    public static void ensure(ServerLevel level, GameData d) {
        List<FixedCamera> there = all(level);
        for (FixedCamera e : there) {
            GameData.CamSpot c = spot(d, e.number());
            if (c == null) e.discard();
        }
        for (GameData.CamSpot c : d.cameras) {
            boolean found = false;
            for (FixedCamera e : there) {
                if (e.isAlive() && e.number() == c.number()) {
                    if (found) e.discard();
                    found = true;
                }
            }
            if (!found) spawn(level, c);
        }
    }

    public static void removeAll(ServerLevel level) {
        for (FixedCamera e : all(level)) e.discard();
    }

    /** Something knocked the camera over (霊障): it lies on its side, its feed tilted. */
    public static void knockOver(ServerLevel level, GameData d, int number) {
        GameData.CamSpot c = spot(d, number);
        FixedCamera e = entity(level, number);
        if (c == null || e == null || c.fallen()) return;
        d.cameras.replaceAll(s -> s.number() == number ? s.withFallen(true) : s);
        d.setDirty();
        e.setCamPose(FixedCamera.POSE_FALLEN);
        level.playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.CAMERA_FALL.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
    }

    /**
     * A pitch that frames what a suggested spot faces (the cameras hang high in the corners): aimed at the floor 60 %
     * of the way to the wall it looks at - steep in the little bathroom, shallow down the hall.
     */
    public static float suggestedPitch(ServerLevel level, Vec3 feet, Direction facing) {
        Vec3 eye = feet.add(0.0, 0.25, 0.0);
        double dist = 6.0;
        for (double d = 0.5; d <= 6.0; d += 0.25) {
            BlockPos b = BlockPos.containing(eye.x + facing.getStepX() * d, eye.y, eye.z + facing.getStepZ() * d);
            if (!level.getBlockState(b).getCollisionShape(level, b).isEmpty()) {
                dist = d;
                break;
            }
        }
        double ahead = Math.max(0.8, dist * 0.6);
        BlockPos b = BlockPos.containing(eye.x + facing.getStepX() * ahead, eye.y, eye.z + facing.getStepZ() * ahead);
        for (int i = 0; i < 6 && level.getBlockState(b.below()).getCollisionShape(level, b.below()).isEmpty(); i++) b = b.below();
        double drop = eye.y - (b.getY() + 0.4);
        return (float) Mth.clamp(Math.toDegrees(Math.atan2(drop, ahead)), 10.0, 70.0);
    }

    // ------------------------------------------------------------------------------------------------ the app
    /** The client opened / switched / closed the camera app (number 0 = closed; device 0 laptop, 1 phone). */
    public static void app(ServerPlayer p, PlayerState ps, int number, int device) {
        GameData d = GameData.get(p.server);
        int old = ps.appCamera;
        if (number <= 0 || !GameManager.inGame(p) || ps.caught() || ps.endingTicks >= 0) {
            ps.appCamera = 0;
            return;
        }
        boolean hasDevice = GameManager.has(p, device == 0 ? ModItems.LAPTOP.get() : ModItems.PHONE.get())
                || (device == 1 && GameManager.has(p, ModItems.PHONE_LIGHT.get()));
        if (!hasDevice || entity(p.serverLevel(), number) == null) {
            ps.appCamera = 0;
            GameManager.event(p, GameManager.EV_APP, 0, "");
            return;
        }
        ps.appCamera = number;
        ps.appDevice = device;
        ps.peeking = false;
        if (old == 0) ps.appTicks = 0;
        d.flag("app_opened");
        if (old != number) Director.onCameraShown(p.serverLevel(), p, ps, old, number);
    }

    static void tickPlayer(ServerLevel level, ServerPlayer p, PlayerState ps, GameData d) {
        if (!ps.inApp()) return;
        ps.appTicks++;
        Director.watching(level, ps);
        if (ps.appTicks % 20 == 0 && entity(level, ps.appCamera) == null) {
            ps.appCamera = 0;
            GameManager.event(p, GameManager.EV_APP, 0, "");
        }
    }
}
