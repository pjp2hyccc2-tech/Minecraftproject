package com.hitori.game;

import com.hitori.Hitori;
import com.hitori.entity.FixedCamera;
import com.hitori.entity.OniDoll;
import com.hitori.network.S2C;
import com.hitori.registry.ModSounds;
import com.hitori.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The four endings (docs/DESIGN.md §1), staged on the server; the client shows them ({@code client/gui/EndingScreen})
 * with the last feeds it captured under its black / white screen ({@link S2C.Capture}).
 * <ul>
 * <li><b>終了</b> (good): the fire has done its work. The lights come on; morning. The cameras' recordings are played back
 * the next afternoon — and in CAM 3's last seconds, after the doll burned, the closet's fusuma slides open by itself.</li>
 * <li><b>鬼</b>: it found you. Its face; black; 「つぎは、あなたがおに」. The cameras keep recording an empty room… and a
 * figure that is you, standing in the bathtub.</li>
 * <li><b>外へ</b>: you opened the front door. The outdoor corridor goes on forever; your phone's map shows you inside 203.</li>
 * <li><b>未完</b>: dawn, and it was never ended. The doll is gone; the last feed shows it on the balcony, looking in.</li>
 * </ul>
 */
public final class Endings {
    private static int kind;
    private static long t = -1;
    @Nullable private static UUID who;
    /** The corridor loops between these x (two apartments apart). */
    private static final double LOOP_MIN = 11.5, LOOP_MAX = 33.5, LOOP = 22.0;

    private Endings() {}

    public static void reset() {
        kind = 0;
        t = -1;
        who = null;
    }

    public static boolean playing() {
        return t >= 0;
    }

    public static void begin(ServerLevel level, @Nullable ServerPlayer p, int ending) {
        GameData d = GameData.get(level.getServer());
        if (t >= 0 || d.phase == GameData.PHASE_ENDING) return;
        d.phase = GameData.PHASE_ENDING;
        d.ending = ending;
        d.endingTick = d.playTicks;
        d.setDirty();
        kind = ending;
        t = 0;
        who = p == null ? null : p.getUUID();
        Doll.save(level, d);
        Hitori.LOGGER.info("[hitori] ending {} at {}", ending, Clock.hhmm(d.clock));
        for (ServerPlayer q : GameManager.players(level)) {
            if (!GameManager.inGame(q)) continue;
            PlayerState ps = GameManager.state(q);
            ps.appCamera = 0;
            ps.breathHolding = false;
            ps.ringTicks = -1;  // a call still ringing stops
            GameManager.event(q, GameManager.EV_APP, 0, "");
            if (ending != GameData.ENDING_SOTO) {
                Hiding.exitQuietly(q, ps);
                ps.endingTicks = 0;
                ps.lockPos = q.position();
            } else {
                ps.sotoTicks = 0;
                GameManager.event(q, GameManager.EV_FADE, 40, "");
            }
        }
    }

    public static void tick(ServerLevel level, GameData d) {
        if (t < 0) return;
        t++;
        ServerPlayer p = who == null ? Ritual.firstPlayer(level) : GameManager.player(level, who);
        switch (kind) {
            case GameData.ENDING_ONI -> oni(level, d, p);
            case GameData.ENDING_SOTO -> soto(level, d, p);
            case GameData.ENDING_MIKAN -> mikan(level, d, p);
            case GameData.ENDING_GOOD -> good(level, d, p);
            default -> {
            }
        }
    }

    private static void all(ServerLevel level, int ev, int arg, String text) {
        for (ServerPlayer q : GameManager.players(level)) if (GameManager.inGame(q)) GameManager.event(q, ev, arg, text);
    }

    private static void showEnding(ServerLevel level, GameData d) {
        for (ServerPlayer q : GameManager.players(level)) {
            if (!GameManager.inGame(q)) continue;
            GameManager.event(q, GameManager.EV_ENDING, kind, String.valueOf(d.broken.size()));
        }
        t = -2; // waits for the client to finish the ending screen
    }

    // ------------------------------------------------------------------------------------------------ 鬼
    private static void oni(ServerLevel level, GameData d, @Nullable ServerPlayer p) {
        if (t == GameManager.LUNGE_CUT + 16) all(level, GameManager.EV_CAUGHT, 0, "");
        if (t == 40 && p != null) {
            GameManager.privateSound(p, ModSounds.DOLL_VOICE_TSUGI.get(), p.getEyePosition(), 1.0f, 1.0f);
            GameManager.say(p, "doll", "say.hitori.tsugi", 70);
        }
        if (t == 80) {
            Doll.removeAll(level);
            Apt.setClosetFusuma(level, 2);
            capture(level, d, OniDoll.tubSpot(0).add(0.0, 0.6, 0.0), "you_tub", 3, 16);
        }
        if (t == 170) showEnding(level, d);
    }

    // ------------------------------------------------------------------------------------------------ 外へ
    private static void soto(ServerLevel level, GameData d, @Nullable ServerPlayer p) {
        if (p == null) {
            if (t > 40) showEnding(level, d);
            return;
        }
        PlayerState ps = GameManager.state(p);
        if (t == 20) {
            Layout.Marker door = Layout.marker("door_front");
            BlockPos out = door.pos().relative(door.facing(), 2);
            Hiding.exitQuietly(p, ps);
            GameManager.teleport(p, level, new Vec3(out.getX() + 0.5, out.getY(), out.getZ() + 0.5), 90.0f, 0.0f);
            GameManager.thought(p, "thought.hitori.soto_1");
        }
        if (t > 20) {
            // the corridor goes on forever: the same two doors, again and again
            double x = p.getX(), z = p.getZ();
            Layout.Marker door = Layout.marker("door_front");
            double zDoor = door.pos().relative(door.facing()).getZ() + 0.5;
            boolean jump = false;
            if (x < LOOP_MIN) {
                x += LOOP;
                jump = true;
            } else if (x > LOOP_MAX) {
                x -= LOOP;
                jump = true;
            }
            if (Math.abs(z - zDoor) < 0.6 && door.facing().getStepZ() < 0 && z > zDoor - 0.1) {
                z = zDoor - 0.8;
                jump = true;
                if (t % 40 == 0) GameManager.thought(p, "thought.hitori.soto_locked");
            }
            if (jump) GameManager.setPos(p, x, p.getY(), z);
            ps.sotoTicks++;
        }
        if (t == 20 * 12) GameManager.thought(p, "thought.hitori.soto_2");
        if (t == 20 * 24) {
            p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), ModSounds.PHONE_VIBRATE.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.0f);
            GameManager.event(p, GameManager.EV_MAP, 1, "");
        }
        if (t == 20 * 33) GameManager.event(p, GameManager.EV_FADE, 60, "");
        if (t == 20 * 35) {
            ps.sotoTicks = -1;
            ps.endingTicks = 0;
            ps.lockPos = p.position();
            showEnding(level, d);
        }
    }

    // ------------------------------------------------------------------------------------------------ 未完
    private static void mikan(ServerLevel level, GameData d, @Nullable ServerPlayer p) {
        if (t == 1) all(level, GameManager.EV_MORNING, 1, "");
        if (t == 30) {
            Layout.Marker b = Layout.marker("balcony");
            OniDoll doll = Doll.get(level);
            if (doll == null) doll = Doll.spawnAt(level, d, b.center(), b.yaw());
            if (doll != null) doll.script(b.center(), b.yaw(), OniDoll.STAND);
        }
        if (t == 40) capture(level, d, Layout.marker("balcony").center().add(0.0, 0.4, 0.0), "balcony", 3, 14);
        if (t == 130) showEnding(level, d);
    }

    // ------------------------------------------------------------------------------------------------ 終了
    private static void good(ServerLevel level, GameData d, @Nullable ServerPlayer p) {
        if (t == 1) {
            Apt.setBreaker(level, true);
            Apt.allLights(level, true);
            Apt.setTv(level, false);
            all(level, GameManager.EV_MORNING, 0, "");
        }
        if (t == 40) {
            Apt.setClosetFusuma(level, 0);
            capture(level, d, OniDoll.fusumaCentre(), "fusuma", 4, 14);
        }
        if (t == 50) Apt.setClosetFusuma(level, 1);
        if (t == 64) Apt.setClosetFusuma(level, 2);
        if (t == 120) showEnding(level, d);
    }

    // ------------------------------------------------------------------------------------------------ the last feeds
    /** The camera that sees this point (nearest first), or a virtual camera at a suggested spot looking at it. */
    private static void capture(ServerLevel level, GameData d, Vec3 target, String label, int frames, int interval) {
        FixedCamera best = null;
        double bd = Double.MAX_VALUE;
        for (FixedCamera c : Cameras.all(level)) {
            if (!Watch.cameraSees(c, target)) continue;
            double dist = c.position().distanceToSqr(target);
            if (dist < bd) {
                bd = dist;
                best = c;
            }
        }
        S2C.Capture msg;
        if (best != null) {
            msg = new S2C.Capture(best.number(), best.getX(), best.getEyeY(), best.getZ(), best.getYRot(), best.getXRot(), label, frames, interval);
        } else {
            Vec3 eye = virtualEye(level, target);
            double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
            float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            msg = new S2C.Capture(0, eye.x, eye.y, eye.z, yaw, pitch, label, frames, interval);
        }
        for (ServerPlayer q : GameManager.players(level)) if (GameManager.inGame(q)) GameManager.send(q, msg);
    }

    private static Vec3 virtualEye(ServerLevel level, Vec3 target) {
        Vec3 best = null;
        double bd = -1;
        for (Layout.Marker m : Layout.markers("cam_suggest")) {
            Vec3 eye = m.center().add(0.0, 0.25, 0.0);
            double dist = eye.distanceTo(target);
            if (dist < 1.5 || dist > 9.0 || !Sight.clear(level, eye, target)) continue;
            if (best == null || dist < bd) {
                best = eye;
                bd = dist;
            }
        }
        if (best != null) return best;
        // three blocks into the room from the target, at eye height
        Layout.Room r = Layout.roomAt(BlockPos.containing(target));
        Vec3 c = r != null ? r.box().getCenter() : target.add(2.0, 0.0, 0.0);
        Vec3 dir = new Vec3(c.x - target.x, 0.0, c.z - target.z);
        if (dir.lengthSqr() < 0.01) dir = new Vec3(1.0, 0.0, 0.0);
        Vec3 eye = target.add(dir.normalize().scale(3.0));
        return new Vec3(eye.x, Layout.FEET_Y + 2.2, eye.z);
    }

    // ------------------------------------------------------------------------------------------------ after the screen
    /** The client finished the ending screen (and the playback): back to your own world. */
    public static void done(ServerPlayer p) {
        GameData d = GameData.get(p.server);
        if (d.phase != GameData.PHASE_ENDING) return;
        p.getPersistentData().putBoolean("hitori_done", true);
        d.started = false;
        d.phase = GameData.PHASE_IDLE;
        d.setDirty();
        t = -1;
        ServerLevel level = GameManager.level(p.server);
        if (level != null) {
            Doll.removeAll(level);
            Doll.forget(d);
            Cameras.removeAll(level);
            var w = Director.womanEntity(level);
            if (w != null) w.discard();
        }
        if (p.getPersistentData().contains("hitori_return")) GameManager.leaveGame(p);
    }

    public static void skip(ServerPlayer p) {
    }

    /** Logged in during an ending: show it. */
    public static void showAgain(ServerPlayer p, GameData d) {
        PlayerState ps = GameManager.state(p);
        ps.endingTicks = 0;
        ps.lockPos = p.position();
        GameManager.event(p, GameManager.EV_ENDING, d.ending, String.valueOf(d.broken.size()));
    }
}
