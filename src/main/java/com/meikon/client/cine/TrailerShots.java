package com.meikon.client.cine;

import com.meikon.block.AndonBlock;
import com.meikon.block.CandleStandBlock;
import com.meikon.block.ToriiGateBlock;
import com.meikon.entity.Hanayome;
import com.meikon.game.Closet;
import com.meikon.game.Mansion;
import com.meikon.registry.ModBlocks;
import com.meikon.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The trailer's shots, in edit order. Coordinates are for the mansion of seed 20261102 (the CI runs pass
 * {@code -Dmeikon.mansionSeed=20261102}); closets and bell stands are looked up in the mansion record.
 * Yaw: 0 south, 90 west, 180 north, 270 east. Pitch: negative looks up.
 */
final class TrailerShots {
    private TrailerShots() {}

    static List<Shot> all() {
        return List.of(new Moon(), new Corridor(), new Wedding(), new Dolls(), new Butsuma(), new Bell(), new BrideFar(),
                new ClosetPeek(), new Shadow(), new LanternOut(), new Chase(), new ChaseLow(), new Torii(), new Face());
    }

    // =========================================================================================== frame / shot
    static final class Frame {
        Vec3 cam = Vec3.ZERO;
        float yaw, pitch, roll, fov, fear, danger;
        boolean hand;
        /** 0 nothing, 1 lit lantern, 2 unlit lantern. A lit lantern lights the scene from the camera (shader). */
        int held;
        float handSwayYaw, handSwayPitch, shakeYaw, shakePitch;

        void reset() {
            cam = Vec3.ZERO;
            yaw = pitch = roll = 0.0f;
            fov = 70.0f;
            fear = danger = 0.0f;
            hand = false;
            held = 0;
            handSwayYaw = handSwayPitch = shakeYaw = shakePitch = 0.0f;
        }

        void look(Vec3 from, Vec3 at) {
            cam = from;
            yaw = yawTo(from, at);
            pitch = pitchTo(from, at);
        }

        void shake(float amp, float t, float seed) {
            shakeYaw += amp * noise(t * 3.1f, seed);
            shakePitch += amp * 0.8f * noise(t * 3.6f, seed + 4.0f);
            roll += amp * 1.3f * noise(t * 1.9f, seed + 9.0f);
        }
    }

    abstract static class Shot {
        final String name;
        final float duration;
        /** Real ticks between set-up and filming: chunk rebuilds, and Iris eases eye brightness (fog) with a 4 s half-life. */
        int setupTicks = 300;
        final List<Trailer.Puppet> puppets = new ArrayList<>();
        Mansion m;

        Shot(String name, float duration) {
            this.name = name;
            this.duration = duration;
        }

        /** Before the first pose: look things up in the mansion. */
        void prepare(Mansion m) {
            this.m = m;
        }

        /** While the game runs: set dressing (server) and actors (client). */
        void setup(Minecraft mc, Mansion m) {
        }

        abstract void frame(float t, Frame f);

        Trailer.Puppet puppet(net.minecraft.world.entity.EntityType<?> type) {
            Trailer.Puppet p = Trailer.puppet(type);
            puppets.add(p);
            return p;
        }

        Hanayome bride(Trailer.Puppet p) {
            return (Hanayome) p.entity();
        }
    }

    // =========================================================================================== shots
    /** A: from the blood moon down to the dark courtyard and the south wing. */
    static final class Moon extends Shot {
        Moon() {
            super("moon", 7.0f);
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(t / duration);
            f.cam = lerp(new Vec3(0.5, 70.0, -8.0), new Vec3(0.5, 66.35, -2.6), k);
            f.yaw = lerp(0.0f, 3.0f, k);
            f.pitch = lerp(-50.0f, -5.0f, easeSine(seg(t, 0.3f, 6.8f)));
            f.fov = 55.0f;
            f.fear = 0.12f;
        }
    }

    /** C: first person, lantern in hand, down the engawa. */
    static final class Corridor extends Shot {
        Corridor() {
            super("corridor", 5.0f);
        }

        @Override
        void frame(float t, Frame f) {
            float step = (float) Math.sin(t * Math.PI * 2.0 * 1.7);
            double x = lerp(-13.0f, -7.4f, t / duration);
            f.cam = new Vec3(x, 66.62 + 0.035 * step, 15.0 + 0.04 * Math.sin(t * Math.PI * 1.7));
            f.yaw = 270.0f + 3.0f * noise(t * 0.6f, 1.0f);
            f.pitch = 2.0f + 1.5f * noise(t * 0.5f, 2.0f) + 0.4f * step;
            f.hand = true;
            f.held = 1;
            f.handSwayYaw = 2.0f * noise(t * 0.8f, 3.0f);
            f.handSwayPitch = 1.5f * step;
            f.fov = 70.0f;
            f.fear = 0.28f;
        }
    }

    /** W: through the open doors of the wedding hall; the bride waits at the sake table and looks back. */
    static final class Wedding extends Shot {
        Trailer.Puppet bride;

        Wedding() {
            super("wedding", 5.0f);
        }

        @Override
        void setup(Minecraft mc, Mansion m) {
            Trailer.server(l -> {
                for (int x = 0; x <= 1; x++) openDoor(l, new BlockPos(x, 65, -18));
                for (int[] c : new int[][]{{-3, -25}, {3, -25}, {-6, -22}, {6, -22}}) lightCandle(l, new BlockPos(c[0], 65, c[1]));
            });
            bride = puppet(ModEntities.HANAYOME.get());
            bride(bride).setClientState(Hanayome.State.WANDER);
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(t / duration);
            Vec3 cam = lerp(new Vec3(1.0, 66.42, -16.8), new Vec3(1.0, 66.32, -19.9), k);
            f.look(cam, new Vec3(0.6, 66.05, -25.5));
            f.fov = 48.0f;
            f.fear = lerp(0.35f, 0.5f, k);
            if (bride != null) {
                float turn = easeSine(seg(t, 2.6f, 4.1f));
                bride.at(0.5, 65.0, -23.3).look(180.0f, 180.0f + 150.0f * turn, 8.0f - 12.0f * turn).walk(0.0f, 0.0f);
            }
        }
    }

    /** D: the doll room; the little doll on the floor turns its head all the way round. */
    static final class Dolls extends Shot {
        Trailer.Puppet doll;

        Dolls() {
            super("dolls", 4.5f);
        }

        @Override
        void setup(Minecraft mc, Mansion m) {
            Trailer.server(l -> {
                placeCandle(l, new BlockPos(-20, 65, 22));
                placeCandle(l, new BlockPos(-25, 65, 22));
            });
            doll = puppet(ModEntities.ICHIMATSU.get());
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(t / duration);
            Vec3 cam = lerp(new Vec3(-22.0, 66.3, 19.75), new Vec3(-22.0, 66.15, 21.05), k);
            Vec3 steps = new Vec3(-22.5, 67.2, 25.5);
            Vec3 head = new Vec3(-22.5, 65.75, 23.15);
            f.look(cam, lerp(steps, head, easeSine(seg(t, 1.9f, 3.7f))));
            f.fov = lerp(50.0f, 46.0f, k);
            f.fear = lerp(0.35f, 0.55f, k);
            if (doll != null) {
                float turn = easeSine(seg(t, 2.0f, 3.3f));
                doll.at(-22.5, 65.0, 23.15).look(0.0f, 180.0f * turn, -14.0f * turn);
            }
        }
    }

    /** E: the family altar room, three candles burning. */
    static final class Butsuma extends Shot {
        Butsuma() {
            super("butsuma", 4.0f);
        }

        @Override
        void setup(Minecraft mc, Mansion m) {
            Trailer.server(l -> {
                for (BlockPos c : m.candles) if (c != null) lightCandle(l, c);
            });
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(t / duration);
            Vec3 cam = lerp(new Vec3(20.7, 66.25, -20.35), new Vec3(24.3, 66.25, -20.55), k);
            f.look(cam, new Vec3(22.6, 65.95, -25.4));
            f.fov = 50.0f;
            f.fear = 0.32f;
        }
    }

    /** F: one of the seven bells, close, turning slowly in the lamplight. */
    static final class Bell extends Shot {
        Vec3 center = new Vec3(0.5, 65.55, 0.5);
        double angle;

        Bell() {
            super("bell", 4.0f);
        }

        @Override
        void prepare(Mansion m) {
            super.prepare(m);
            if (m.bellSpots.isEmpty()) return;
            BlockPos b = m.bellSpots.get(0);
            center = new Vec3(b.getX() + 0.5, b.getY() + 0.55, b.getZ() + 0.5);
            Direction best = Direction.SOUTH;
            int bestScore = -1;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int score = 0;
                for (int k = 1; k <= 2; k++) {
                    BlockPos p = b.relative(d, k);
                    if (open(p) && open(p.above())) score += 2;
                    if (open(p.relative(d.getClockWise())) && open(p.relative(d.getCounterClockWise()))) score++;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = d;
                }
            }
            angle = Math.atan2(best.getStepZ(), best.getStepX());
            Trailer.log("bell at " + b.toShortString() + ", camera side " + best);
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(t / duration);
            double a = angle + Math.toRadians(-16.0 + 32.0 * k);
            double r = 1.42 - 0.12 * k;
            Vec3 cam = center.add(Math.cos(a) * r, 0.42, Math.sin(a) * r);
            f.look(cam, center.add(0, 0.05, 0));
            f.held = 1;
            f.fov = 38.0f;
            f.fear = 0.2f;
        }
    }

    /** G: far down the dark engawa, under one lamp, she stands. Then she looks up. */
    static final class BrideFar extends Shot {
        Trailer.Puppet bride;

        BrideFar() {
            super("bride_far", 5.5f);
        }

        @Override
        void setup(Minecraft mc, Mansion m) {
            Trailer.server(l -> setAndon(l, new BlockPos(0, 65, 17), true));
            bride = puppet(ModEntities.HANAYOME.get());
            bride(bride).setClientState(Hanayome.State.WANDER);
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(t / duration);
            f.cam = lerp(new Vec3(12.8, 66.45, 15.5), new Vec3(10.2, 66.45, 15.5), k);
            f.yaw = 90.0f + 0.6f * noise(t * 0.4f, 5.0f);
            f.pitch = 0.5f + 0.4f * noise(t * 0.35f, 6.0f);
            f.fov = 42.0f;
            f.fear = lerp(0.45f, 0.7f, seg(t, 3.5f, 5.5f));
            if (bride != null) {
                float snap = easeSine(seg(t, 3.7f, 3.95f));
                bride.at(0.5, 65.0, 15.5).look(270.0f, 270.0f + 35.0f * (1.0f - snap), 22.0f * (1.0f - snap) - 2.0f * snap);
                bride(bride).setClientState(t >= 4.15f ? Hanayome.State.SEARCH : Hanayome.State.WANDER);
            }
        }
    }

    /** H: hiding in the oshiire; through the slats she walks past, stops, turns, and comes closer. */
    static final class ClosetPeek extends Shot {
        Trailer.Puppet bride;
        Vec3 cam = Vec3.ZERO, fwd = new Vec3(0, 0, 1), right = new Vec3(1, 0, 0), front = Vec3.ZERO;
        float camYaw;
        BlockPos lamp;

        ClosetPeek() {
            super("closet", 7.0f);
        }

        @Override
        void prepare(Mansion m) {
            super.prepare(m);
            Closet best = null;
            int bestScore = -1;
            for (Closet c : m.closets) {
                Direction d = c.facing(), r = d.getClockWise();
                int score = 0;
                for (int a = 1; a <= 4; a++) {
                    for (int b = -3; b <= 3; b++) {
                        BlockPos p = c.front().relative(d, a).relative(r, b);
                        if (open(p) && open(p.above())) score++;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            if (best == null) return;
            Direction d = best.facing(), r = d.getClockWise();
            fwd = new Vec3(d.getStepX(), 0, d.getStepZ());
            right = new Vec3(r.getStepX(), 0, r.getStepZ());
            BlockPos in = best.inside();
            cam = new Vec3(in.getX() + 0.5, in.getY() + 1.45, in.getZ() + 0.5).add(fwd.scale(0.15));
            camYaw = d.toYRot();
            BlockPos fr = best.front();
            front = new Vec3(fr.getX() + 0.5, fr.getY(), fr.getZ() + 0.5);
            lamp = null;
            for (int side : new int[]{1, -1, 2, -2}) {
                BlockPos p = fr.relative(d, 3).relative(r, side);
                if (open(p) && open(p.above())) {
                    lamp = p;
                    break;
                }
            }
            Trailer.log("closet " + best.index() + " at " + in.toShortString() + " facing " + d + " (open cells " + bestScore + "), lamp " + lamp);
        }

        @Override
        void setup(Minecraft mc, Mansion m) {
            if (lamp != null) {
                BlockPos at = lamp;
                Trailer.server(l -> setAndon(l, at, true));
            }
            bride = puppet(ModEntities.HANAYOME.get());
            bride(bride).setClientState(Hanayome.State.WANDER);
        }

        @Override
        void frame(float t, Frame f) {
            f.cam = cam;
            f.yaw = camYaw + 0.5f * noise(t * 0.5f, 7.0f);
            f.pitch = 3.0f + 0.4f * noise(t * 0.45f, 8.0f);
            f.fov = 62.0f;
            f.fear = lerp(0.7f, 0.95f, seg(t, 0.0f, 6.0f));
            if (bride == null) return;
            Vec3 p0 = front.add(fwd.scale(2.3)).add(right.scale(-3.0));
            Vec3 p1 = front.add(fwd.scale(2.3));
            Vec3 p2 = front.add(fwd.scale(1.2));
            float walkYaw = yawOf(right);
            float faceYaw = yawOf(fwd.scale(-1));
            float w1 = seg(t, 0.3f, 3.4f);
            float w2 = easeSine(seg(t, 4.6f, 6.4f));
            Vec3 pos = t < 4.6f ? lerp(p0, p1, w1) : lerp(p1, p2, w2);
            float turnBody = easeSine(seg(t, 3.4f, 4.3f));
            float turnHead = easeSine(seg(t, 3.3f, 3.9f));
            float body = lerpAngle(walkYaw, faceYaw, turnBody);
            float head = lerpAngle(walkYaw, faceYaw, turnHead);
            // limb swing: speed = blocks per second / 5 (vanilla), position = its integral in ticks
            float s1 = (3.0f / 3.1f) / 5.0f, s2 = (1.1f / 1.8f) / 5.0f;
            float speed = s1 * ramp(t, 0.3f, 3.4f, 0.25f) + s2 * ramp(t, 4.6f, 6.4f, 0.35f);
            float walkPos = 20.0f * (s1 * clampF(t - 0.3f, 0.0f, 3.1f) + s2 * clampF(t - 4.6f, 0.0f, 1.8f));
            bride.at(pos).look(body, head, t > 4.6f ? 14.0f : 4.0f).walk(walkPos, speed);
            bride(bride).setClientState(t >= 4.2f ? Hanayome.State.CHECK_CLOSET : Hanayome.State.WANDER);
        }
    }

    /** I: a shadow steps out of the light at the end of the corridor and comes for you. */
    static final class Shadow extends Shot {
        Trailer.Puppet shade;

        Shadow() {
            super("shadow", 3.0f);
        }

        @Override
        void setup(Minecraft mc, Mansion m) {
            Trailer.server(l -> setAndon(l, new BlockPos(1, 65, -15), true));
            shade = puppet(ModEntities.KAGEBOSHI.get());
        }

        @Override
        void frame(float t, Frame f) {
            float k = t / duration;
            f.cam = lerp(new Vec3(-11.0, 66.42, -15.55), new Vec3(-10.6, 66.42, -15.55), easeSine(k));
            f.yaw = 270.0f + 0.8f * noise(t * 0.5f, 10.0f);
            f.pitch = 1.0f;
            f.fov = 52.0f;
            f.fear = 0.82f;
            if (shade != null) {
                double x = lerp(-2.8f, -7.0f, k);
                shade.at(x, 65.0, -15.6).look(90.0f, 90.0f, 0.0f).walk(20.0f * 0.28f * t, 0.28f);
            }
        }
    }

    /** J: the lantern gutters and dies; in the dark, two red eyes. */
    static final class LanternOut extends Shot {
        Trailer.Puppet bride;

        LanternOut() {
            super("lantern_out", 3.5f);
        }

        @Override
        void setup(Minecraft mc, Mansion m) {
            Trailer.server(l -> {
                for (int x = -17; x <= -14; x++) {
                    for (int z = -17; z <= 17; z++) {
                        BlockPos p = new BlockPos(x, 65, z);
                        if (l.getBlockState(p).is(ModBlocks.ANDON.get())) setAndon(l, p, false);
                    }
                }
            });
            bride = puppet(ModEntities.HANAYOME.get());
            bride(bride).setClientState(Hanayome.State.SEARCH);
        }

        @Override
        void frame(float t, Frame f) {
            float step = (float) Math.sin(t * Math.PI * 2.0 * 1.2);
            double z = lerp(-9.6f, -8.2f, t / duration);
            f.cam = new Vec3(-15.6, 66.62 + 0.02 * step, z);
            f.yaw = 0.0f + 5.0f * noise(t * 0.5f, 11.0f);
            f.pitch = 1.0f + 0.8f * noise(t * 0.6f, 12.0f);
            f.hand = true;
            f.handSwayYaw = 1.5f * noise(t * 0.9f, 13.0f);
            f.handSwayPitch = step;
            f.fov = 70.0f;
            boolean lit = t < 1.25f || (t >= 1.33f && t < 1.47f) || (t >= 1.52f && t < 1.64f) || (t >= 1.70f && t < 1.80f);
            f.held = lit ? 1 : 2;
            f.fear = t < 1.8f ? 0.55f : lerp(0.75f, 0.92f, seg(t, 1.8f, 3.2f));
            if (bride != null) bride.at(-15.5, 65.0, 1.8).look(180.0f, 180.0f, 0.0f);
        }
    }

    /** K: running backwards down the engawa, she is right there, reaching. */
    static final class Chase extends Shot {
        Trailer.Puppet bride;

        Chase() {
            super("chase", 5.0f);
        }

        @Override
        void setup(Minecraft mc, Mansion m) {
            bride = puppet(ModEntities.HANAYOME.get());
            bride(bride).setClientState(Hanayome.State.CHASE);
        }

        @Override
        void frame(float t, Frame f) {
            float run = (float) Math.sin(t * Math.PI * 2.0 * 2.4);
            double camZ = lerp(-11.0f, 11.5f, t / duration);
            f.cam = new Vec3(15.45 + 0.05 * noise(t * 2.0f, 14.0f), 66.5 + 0.06 * run, camZ);
            f.yaw = 180.0f;
            f.pitch = 1.0f;
            f.shake(1.3f, t, 15.0f);
            f.held = 1;
            f.fov = 68.0f;
            f.fear = 0.95f;
            f.danger = 1.0f;
            if (bride != null) {
                double z = lerp(-14.6f, 8.6f, t / duration);
                bride.at(15.45, 65.0, z).look(0.0f, 0.0f, -4.0f).walk(20.0f * 0.93f * t, 0.93f);
            }
        }
    }

    /** K2: low on the floor, she runs over the camera. */
    static final class ChaseLow extends Shot {
        Trailer.Puppet bride;

        ChaseLow() {
            super("chase_low", 2.5f);
        }

        @Override
        void setup(Minecraft mc, Mansion m) {
            bride = puppet(ModEntities.HANAYOME.get());
            bride(bride).setClientState(Hanayome.State.CHASE);
        }

        @Override
        void frame(float t, Frame f) {
            float k = t / duration;
            f.cam = new Vec3(15.5, 65.32, 9.3);
            f.yaw = 180.0f;
            f.pitch = lerp(-16.0f, -26.0f, easeSine(k));
            f.shake(0.6f + 1.4f * k, t, 16.0f);
            f.held = 1;
            f.fov = 74.0f;
            f.fear = 0.95f;
            f.danger = 1.0f;
            if (bride != null) {
                double z = lerp(-2.6f, 9.0f, k);
                bride.at(15.45, 65.0, z).look(0.0f, 0.0f, 10.0f).walk(20.0f * 0.93f * t, 0.93f);
            }
        }
    }

    /** L: the seven bells have rung: the torii blazes, and you run for it. */
    static final class Torii extends Shot {
        Torii() {
            super("torii", 3.5f);
        }

        @Override
        void setup(Minecraft mc, Mansion m) {
            Trailer.server(l -> {
                for (BlockPos g : m.gateBlocks) {
                    BlockState s = l.getBlockState(g);
                    if (s.is(ModBlocks.TORII_GATE.get())) l.setBlock(g, s.setValue(ToriiGateBlock.ACTIVE, true), 3);
                }
            });
        }

        @Override
        void frame(float t, Frame f) {
            float u = t / duration;
            float k = 0.35f * u + 0.65f * u * u;
            f.cam = lerp(new Vec3(0.5, 66.1, 3.6), new Vec3(0.5, 66.55, -6.0), k);
            f.yaw = 180.0f;
            f.pitch = lerp(-1.0f, -11.0f, easeSine(u));
            f.fov = lerp(60.0f, 76.0f, u * u);
            f.shake(0.3f + 1.6f * u, t, 17.0f);
            f.fear = 0.6f;
            f.danger = lerp(0.35f, 0.6f, u);
        }
    }

    /** M: she lunges into the lens. */
    static final class Face extends Shot {
        Trailer.Puppet bride;

        Face() {
            super("face", 2.6f);
        }

        @Override
        void setup(Minecraft mc, Mansion m) {
            bride = puppet(ModEntities.HANAYOME.get());
            bride(bride).setClientState(Hanayome.State.CAPTURE);
        }

        @Override
        void frame(float t, Frame f) {
            f.cam = new Vec3(8.5, 66.42, 15.5);
            f.yaw = 90.0f;
            f.pitch = 0.5f;
            float lunge = seg(t, 1.05f, 1.55f);
            lunge = lunge * lunge * lunge;
            if (t > 1.5f) f.shake(2.2f, t, 18.0f);
            else f.shake(0.25f, t, 18.0f);
            f.held = 1;
            f.fov = 55.0f;
            f.fear = 1.0f;
            f.danger = 1.0f;
            if (bride != null) {
                double x = lerp(5.3f, 7.7f, lunge);
                bride.at(x, 65.0, 15.5).look(270.0f, 270.0f, -6.0f);
            }
        }
    }

    // =========================================================================================== set dressing
    static boolean open(BlockPos p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        BlockState s = mc.level.getBlockState(p);
        return s.getCollisionShape(mc.level, p).isEmpty() && !(s.getBlock() instanceof DoorBlock);
    }

    static void openDoor(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        if (s.getBlock() instanceof DoorBlock door && !s.getValue(DoorBlock.OPEN)) door.setOpen(null, l, s, p, true);
    }

    static void lightCandle(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        if (s.is(ModBlocks.CANDLE_STAND.get())) l.setBlock(p, s.setValue(CandleStandBlock.LIT, true), 3);
    }

    static void placeCandle(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        if (s.isAir()) l.setBlock(p, ModBlocks.CANDLE_STAND.get().defaultBlockState().setValue(CandleStandBlock.LIT, true), 3);
        else lightCandle(l, p);
    }

    static void setAndon(ServerLevel l, BlockPos p, boolean lit) {
        BlockState s = l.getBlockState(p);
        if (s.is(ModBlocks.ANDON.get())) l.setBlock(p, s.setValue(AndonBlock.LIT, lit), 3);
        else if (s.isAir() && lit) l.setBlock(p, ModBlocks.ANDON.get().defaultBlockState().setValue(AndonBlock.LIT, true), 3);
    }

    // =========================================================================================== math
    static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    static float clampF(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** 1 inside [a, b], easing in and out over {@code edge} seconds, 0 outside. */
    static float ramp(float t, float a, float b, float edge) {
        return easeSine(seg(t, a, a + edge)) * (1.0f - easeSine(seg(t, b - edge, b)));
    }

    static float seg(float t, float a, float b) {
        return clamp01((t - a) / (b - a));
    }

    static float easeSine(float x) {
        x = clamp01(x);
        return (float) (0.5 - 0.5 * Math.cos(Math.PI * x));
    }

    static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    static Vec3 lerp(Vec3 a, Vec3 b, float k) {
        return new Vec3(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.z + (b.z - a.z) * k);
    }

    static float lerpAngle(float a, float b, float k) {
        float d = ((b - a) % 360.0f + 540.0f) % 360.0f - 180.0f;
        return a + d * k;
    }

    static float yawTo(Vec3 from, Vec3 to) {
        return yawOf(to.subtract(from));
    }

    static float yawOf(Vec3 d) {
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    static float pitchTo(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        return (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
    }

    /** Smooth pseudo-random wobble in -1..1. */
    static float noise(float t, float seed) {
        return (float) (Math.sin(t * 1.73 + seed) * 0.5 + Math.sin(t * 2.91 + seed * 1.7) * 0.3 + Math.sin(t * 5.37 + seed * 2.3) * 0.2);
    }
}
