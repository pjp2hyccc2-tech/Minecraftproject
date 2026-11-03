package com.hitori.game;

import com.hitori.entity.OniDoll;
import com.hitori.registry.ModSounds;
import com.hitori.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Hiding (docs/DESIGN.md §3.2): in the 押し入れ (the lower level, crouched, the fusuma shut) or the western room's
 * closet (forbidden by the printout: the doll grows suspicious twice as fast). Inside you are safe from sight; the
 * doll finds you by sound, or by light from the gap. Peek (hold right mouse facing the fusuma): the fusuma opens a
 * crack and you look through the slit — a soft sliding noise (2), and light moves. Leaving: the fusuma slides open
 * (noise 6). The world's fusuma block asks through {@code FixtureEvents.hide / unhide}.
 */
public final class Hiding {
    private static int closeFusumaIn = -1;

    private Hiding() {}

    /** Into a closet: "oshiire" or "western". */
    public static boolean enter(ServerPlayer p, BlockPos spot, String kind) {
        PlayerState ps = GameManager.state(p);
        if (ps.hiding() || ps.caught() || ps.locked || ps.busy() || !GameManager.inGame(p)) return false;
        ServerLevel level = p.serverLevel();
        Vec3 at;
        float yaw;
        if (kind.equals("oshiire")) {
            Layout.Marker m = nearest(Layout.markers("closet_hide"), p.position());
            at = m.center();
            yaw = m.yaw();
            Apt.setClosetFusuma(level, 0);
            closeFusumaIn = -1;
            ps.hidePos = m.pos();
        } else {
            Layout.Marker m = Layout.marker("western_closet");
            at = m.center();
            yaw = m.yaw();
            ps.hidePos = m.pos();
        }
        // the doll saw you go in
        OniDoll doll = Doll.get(level);
        if (doll != null && GameManager.running(level) && doll.distanceTo(p) < 12.0
                && Sight.clear(level, doll.getEyePosition(), p.getEyePosition()) && doll.state() != OniDoll.State.WAIT) {
            doll.suspicion = Math.max(doll.suspicion, 0.85f);
        }
        ps.hideSpot = kind;
        ps.hideAt = at;
        ps.hiddenTicks = 0;
        ps.peeking = false;
        ps.lostWaterThisHide = false;
        ps.breathHolding = false;
        GameManager.teleport(p, level, at, yaw, 8.0f);
        level.playSound(null, at.x, at.y + 1.0, at.z, ModSounds.DOOR_FUSUMA.get(), SoundSource.BLOCKS, p.isShiftKeyDown() ? 0.5f : 0.9f, 1.0f);
        NoiseSystem.emit(level, at.add(0, 1, 0), p.isShiftKeyDown() ? 4.0f : 6.0f, "fusuma", p);
        GameManager.event(p, GameManager.EV_HIDE, kind.equals("oshiire") ? 1 : 2, "");
        GameManager.savePlayer(p);
        return true;
    }

    /** Out of the closet; sound = by the player's own hand (the fusuma slides: noise 6). */
    public static void exit(ServerPlayer p, boolean sound) {
        PlayerState ps = GameManager.state(p);
        if (!ps.hiding()) return;
        ServerLevel level = p.serverLevel();
        String kind = ps.hideSpot;
        Vec3 out;
        float yaw = p.getYRot();
        if ("oshiire".equals(kind)) {
            List<Layout.Marker> f = Layout.markers("closet_fusuma");
            Layout.Marker m = nearest(f, ps.hideAt != null ? ps.hideAt : p.position());
            out = Vec3.atBottomCenterOf(m.pos().relative(m.facing()));
            yaw = m.yaw();
            Apt.setClosetFusuma(level, 2);
            closeFusumaIn = 40;
        } else {
            Layout.Marker m = Layout.marker("western_closet");
            out = Vec3.atBottomCenterOf(m.pos().relative(m.facing()));
            yaw = m.yaw();
        }
        ps.hideSpot = null;
        ps.hideAt = null;
        ps.hidePos = null;
        ps.peeking = false;
        ps.appCamera = 0;
        GameManager.teleport(p, level, out, yaw, 0.0f);
        if (sound) {
            level.playSound(null, out.x, out.y + 1, out.z, ModSounds.DOOR_FUSUMA.get(), SoundSource.BLOCKS, p.isShiftKeyDown() ? 0.5f : 0.9f, 0.95f);
            NoiseSystem.emit(level, out.add(0, 1, 0), p.isShiftKeyDown() ? 4.0f : 6.0f, "fusuma", p);
        }
        GameManager.event(p, GameManager.EV_HIDE, 0, "");
        GameManager.event(p, GameManager.EV_APP, 0, "");
        GameManager.savePlayer(p);
        if (sound) Ritual.leftCloset(p);
    }

    /** The hiding state ends where you are (an ending takes over). */
    public static void exitQuietly(ServerPlayer p, PlayerState ps) {
        if (!ps.hiding()) return;
        ps.hideSpot = null;
        ps.hideAt = null;
        ps.hidePos = null;
        ps.peeking = false;
        GameManager.event(p, GameManager.EV_HIDE, 0, "");
    }

    /** Peeking through the gap of the 押し入れ's fusuma (hold right mouse). */
    public static void peek(ServerPlayer p, boolean on) {
        PlayerState ps = GameManager.state(p);
        if (!ps.inCloset() || ps.peeking == on) return;
        ServerLevel level = p.serverLevel();
        ps.peeking = on;
        ps.peekTicks = 0;
        if (on) ps.appCamera = 0;
        Apt.setClosetFusuma(level, on ? 1 : 0);
        Vec3 gap = peekEye();
        level.playSound(null, gap.x, gap.y, gap.z, ModSounds.DOOR_FUSUMA_CRACK.get(), SoundSource.BLOCKS, 0.5f, on ? 1.0f : 0.92f);
        NoiseSystem.emit(level, gap, 2.0f, "peek", p);
        // the doll's eye right at the crack
        OniDoll doll = Doll.get(level);
        if (on && doll != null && (doll.state() == OniDoll.State.STANDOFF || doll.state() == OniDoll.State.FUSUMA) && doll.distanceTo(p) < 3.0) {
            GameManager.event(p, GameManager.EV_SCARE, 1, "");
            GameManager.addFear(p, 0.35f);
            doll.suspicion = Math.min(1.0f, doll.suspicion + 0.25f);
        }
    }

    /** The eye at the gap of the fusuma (from the {@code closet_peek} marker). */
    /**
     * The eye at the crack: the fusuma panels stand in the closet cells' front edge, the crack opens at the row's end to
     * the left of closet_peek's facing (a quarter block wide): just behind it, crouched.
     */
    public static Vec3 peekEye() {
        Layout.Marker m = Layout.marker("closet_peek");
        Direction f = m.facing();
        Direction left = f.getCounterClockWise();
        double side = 0.5 - 0.125;
        return new Vec3(m.pos().getX() + 0.5 + f.getStepX() * 0.33 + left.getStepX() * side, m.pos().getY() + 1.15,
                m.pos().getZ() + 0.5 + f.getStepZ() * 0.33 + left.getStepZ() * side);
    }

    /** The middle of the crack's slit, at the panels' back face (where the TV's light comes through). */
    public static Vec3 crackCentre() {
        Layout.Marker m = Layout.marker("closet_peek");
        Direction f = m.facing();
        Direction left = f.getCounterClockWise();
        double side = 0.5 - 0.125;
        return new Vec3(m.pos().getX() + 0.5 + f.getStepX() * 0.375 + left.getStepX() * side, m.pos().getY() + 1.0,
                m.pos().getZ() + 0.5 + f.getStepZ() * 0.375 + left.getStepZ() * side);
    }

    public static float peekYaw() {
        return Layout.marker("closet_peek").yaw();
    }

    static void tick(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        if (closeFusumaIn > 0 && --closeFusumaIn == 0 && !anyoneInCloset(level)) Apt.setClosetFusuma(level, 0);
        if (!ps.hiding()) return;
        ps.hiddenTicks++;
        if (ps.peeking) ps.peekTicks++;
        if (ps.hideAt != null && p.position().distanceToSqr(ps.hideAt) > 0.09) {
            if (p.position().distanceToSqr(ps.hideAt) > 9.0) {
                // moved away by something else (a command): no longer hidden
                ps.hideSpot = null;
                ps.hideAt = null;
                ps.peeking = false;
                GameManager.event(p, GameManager.EV_HIDE, 0, "");
                return;
            }
            GameManager.setPos(p, ps.hideAt.x, ps.hideAt.y, ps.hideAt.z);
            p.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static boolean anyoneInCloset(ServerLevel level) {
        for (ServerPlayer p : GameManager.players(level)) if (GameManager.state(p).inCloset()) return true;
        return false;
    }

    /** After save and quit inside a closet: back in, quietly. */
    public static void resume(ServerPlayer p, PlayerState ps) {
        if (ps.hideSpot == null) return;
        String kind = ps.hideSpot;
        ServerLevel level = p.serverLevel();
        Vec3 at = ps.hidePos != null ? Vec3.atBottomCenterOf(ps.hidePos) : p.position();
        ps.hideAt = at;
        ps.peeking = false;
        if (kind.equals("oshiire")) Apt.setClosetFusuma(level, 0);
        float yaw = kind.equals("oshiire") ? Layout.marker("closet_hide").yaw() : Layout.marker("western_closet").yaw();
        GameManager.teleport(p, level, at, yaw, 8.0f);
        GameManager.event(p, GameManager.EV_HIDE, kind.equals("oshiire") ? 1 : 2, "");
    }

    static Layout.Marker nearest(List<Layout.Marker> list, Vec3 at) {
        Layout.Marker best = list.get(0);
        double bd = Double.MAX_VALUE;
        for (Layout.Marker m : list) {
            double d = m.center().distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }
}
