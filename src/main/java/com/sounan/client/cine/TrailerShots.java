package com.sounan.client.cine;

import com.sounan.entity.Koyanushi;
import com.sounan.entity.Nozoki;
import com.sounan.entity.Oyamasama;
import com.sounan.entity.SounanMonster;
import com.sounan.entity.Ushiro;
import com.sounan.entity.Utsurumono;
import com.sounan.entity.Yobigoe;
import com.sounan.registry.ModEntities;
import com.sounan.world.PoiLayout;
import com.sounan.world.TerrainData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The SOUNAN trailer's shots, in edit order. Positions come from the baked mountain (fixed for every world): trunks
 * and clearings were picked offline from the decorator's hashes. Yaw: 0 south, 90 west, 180 north, 270 east.
 * Pitch: negative looks up.
 */
final class TrailerShots {
    static final int NONE = 0, FLASHLIGHT_ON = 1, FLASHLIGHT_OFF = 2, CAMERA = 3, FLARE = 4, PHONE_LIGHT = 5, FLASHLIGHT_LOW = 6;

    private TrailerShots() {}

    static List<Shot> all() {
        return List.of(new Descent(), new Wake(), new Walk(), new Bag(), new LampFar(), new Peek(), new Behind(), new Hut(),
                new Village(), new CameraGhost(), new Bridge(), new Loom(), new Turn(), new Tunnel(), new Rush(), new Face());
    }

    // =========================================================================================== frame / shot
    static final class Frame {
        Vec3 cam = Vec3.ZERO;
        float yaw, pitch, roll, fov, fear, danger;
        boolean hand;
        int held;
        float handSwayYaw, handSwayPitch, shakeYaw, shakePitch;

        void reset() {
            cam = Vec3.ZERO;
            yaw = pitch = roll = 0.0f;
            fov = 70.0f;
            fear = danger = 0.0f;
            hand = false;
            held = NONE;
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

        /** A walking camera: head bob, a little sway of the hand. */
        void walking(float t, float rate, float amount) {
            float step = (float) Math.sin(t * Math.PI * 2.0 * rate);
            cam = cam.add(0.0, 0.04 * amount * step, 0.0);
            pitch += 0.5f * amount * step;
            handSwayPitch += 1.6f * amount * step;
            handSwayYaw += 1.8f * amount * noise(t * 0.8f, 3.0f);
        }
    }

    abstract static class Shot {
        final String name;
        final float duration;
        /** Real ticks between set-up and filming: chunk rebuilds, and Iris eases eye brightness (fog) with a 4 s half-life. */
        int setupTicks = 300;
        final List<Trailer.Puppet> puppets = new ArrayList<>();

        Shot(String name, float duration) {
            this.name = name;
            this.duration = duration;
        }

        void prepare() {
        }

        /** While the game runs: set dressing (server) and actors (client). */
        void setup(Minecraft mc) {
        }

        abstract void frame(float t, Frame f);

        Trailer.Puppet puppet(net.minecraft.world.entity.EntityType<?> type) {
            Trailer.Puppet p = Trailer.puppet(type);
            puppets.add(p);
            return p;
        }
    }

    static SounanMonster mon(Trailer.Puppet p) {
        return (SounanMonster) p.entity();
    }

    /** Ground surface Y (where feet stand), smoothed between block centres so cameras glide over the steps. */
    static double ground(double x, double z) {
        TerrainData t = TerrainData.get();
        double fx = x - 0.5, fz = z - 0.5;
        int x0 = Mth.floor(fx), z0 = Mth.floor(fz);
        double ax = fx - x0, az = fz - z0;
        double h00 = t.height(x0, z0), h10 = t.height(x0 + 1, z0), h01 = t.height(x0, z0 + 1), h11 = t.height(x0 + 1, z0 + 1);
        return 1.0 + Mth.lerp(az, Mth.lerp(ax, h00, h10), Mth.lerp(ax, h01, h11));
    }

    /** Ground under a block column, unsmoothed (for actors standing on one block). */
    static double groundAt(double x, double z) {
        return TerrainData.get().height(Mth.floor(x), Mth.floor(z)) + 1.0;
    }

    static Vec3 eye(double x, double z, double height) {
        return new Vec3(x, ground(x, z) + height, z);
    }

    // =========================================================================================== shots
    /** A: out of the moonlit sky, down through a gap in the cedar crowns, onto the trail in the fog. */
    static final class Descent extends Shot {
        Descent() {
            super("descent", 8.0f);
            setupTicks = 360;
        }

        @Override
        void frame(float t, Frame f) {
            double x = -92.5, z = -86.5;
            double g = ground(x, z);
            float k = easeSine(seg(t, 0.2f, 7.7f));
            f.cam = new Vec3(x + 0.3 * noise(t * 0.2f, 1.0f), lerp((float) (g + 34.0), (float) (g + 1.62), k), z + 0.3 * noise(t * 0.17f, 2.0f));
            f.yaw = lerpAngle(-4.0f, -50.0f, easeSine(seg(t, 2.2f, 7.9f)));
            f.pitch = lerp(-48.0f, 3.0f, easeSine(seg(t, 0.6f, 7.4f)));
            f.fov = 56.0f;
            f.fear = 0.12f;
        }
    }

    /** B: on your back where you fell, looking up; you sit up, find the flashlight, click it on. */
    static final class Wake extends Shot {
        Wake() {
            super("wake", 5.0f);
        }

        @Override
        void frame(float t, Frame f) {
            double x = -198.6, z = -210.3;
            double g = ground(x, z);
            float up = easeSine(seg(t, 1.3f, 3.1f));
            float breath = (float) Math.sin(t * 2.4);
            f.cam = new Vec3(x, g + lerp(0.34f, 1.05f, up) + 0.015 * breath, z + 0.15 * up);
            f.yaw = lerpAngle(-62.0f, -34.0f, up);
            f.pitch = lerp(-76.0f, 7.0f, up) + 1.2f * breath * (1.0f - up);
            f.roll = lerp(16.0f, 0.0f, up);
            f.hand = t > 2.1f;
            f.held = t < 2.1f ? NONE : (t < 3.05f ? FLASHLIGHT_OFF : FLASHLIGHT_ON);
            f.shake(0.25f + 0.3f * up, t, 2.0f);
            f.fov = 64.0f;
            f.fear = lerp(0.2f, 0.4f, up);
        }
    }

    /** C: walking down the trail, the beam ahead; a stone jizo by the fork, facing you. */
    static final class Walk extends Shot {
        Walk() {
            super("walk", 5.5f);
        }

        @Override
        void frame(float t, Frame f) {
            float u = t / duration;
            float k = (u - 0.18f * u * u * u) / 0.82f;          // slowing down towards the statue
            double x = lerp(-69.0f, -62.4f, k), z = lerp(-67.6f, -62.0f, k);
            f.cam = eye(x, z, 1.62);
            Vec3 ahead = new Vec3(x + 6.0, f.cam.y - 0.6, z + 5.2);
            Vec3 jizo = new Vec3(-56.5, groundAt(-56.5, -56.5) + 0.7, -56.5);
            Vec3 at = lerp(ahead, jizo, easeSine(seg(t, 3.2f, 5.3f)));
            f.yaw = yawTo(f.cam, at) + 2.5f * noise(t * 0.6f, 4.0f);
            f.pitch = pitchTo(f.cam, at) + 1.2f * noise(t * 0.5f, 5.0f);
            f.walking(t, 1.7f, 1.0f - 0.5f * u);
            f.hand = true;
            f.held = FLASHLIGHT_ON;
            f.fov = 62.0f;
            f.fear = 0.3f;
        }
    }

    /** D: by the stream, the beam crosses the stones and stops on your bag. */
    static final class Bag extends Shot {
        Bag() {
            super("bag", 3.5f);
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 3.5f));
            double x = lerp(-165.6f, -164.7f, k), z = lerp(-167.4f, -168.3f, k);
            f.cam = eye(x, z, 1.55);
            Vec3 bag = new Vec3(-161.5, 182.35, -171.5);
            Vec3 stream = new Vec3(x - 0.5, f.cam.y - 2.4, z - 5.0);
            Vec3 at = lerp(stream, bag, easeSine(seg(t, 0.3f, 2.2f)));
            f.yaw = yawTo(f.cam, at) + 1.5f * noise(t * 0.7f, 6.0f);
            f.pitch = pitchTo(f.cam, at) + 1.0f * noise(t * 0.6f, 7.0f);
            f.hand = true;
            f.held = FLASHLIGHT_ON;
            f.handSwayYaw = 2.0f * noise(t * 1.1f, 8.0f);
            f.fov = 56.0f;
            f.fear = 0.25f;
        }
    }

    /** E: far off between the trunks, a rescuer's headlamp, waving you over. */
    static final class LampFar extends Shot {
        Trailer.Puppet yobi;

        LampFar() {
            super("lamp_far", 5.5f);
        }

        @Override
        void setup(Minecraft mc) {
            yobi = puppet(ModEntities.YOBIGOE.get());
        }

        @Override
        void frame(float t, Frame f) {
            double cz = lerp(-51.5f, -50.7f, easeSine(seg(t, 0.0f, 5.5f)));
            f.cam = eye(-51.5, cz, 1.62);
            Vec3 lamp = new Vec3(-51.5, groundAt(-51.5, -21.5) + 1.7, -21.5);
            f.yaw = yawTo(f.cam, lamp) + 1.2f * noise(t * 0.5f, 9.0f);
            f.pitch = pitchTo(f.cam, lamp) + 2.0f + 0.8f * noise(t * 0.4f, 10.0f);
            f.hand = true;
            f.held = FLASHLIGHT_OFF;
            f.handSwayPitch = 1.0f * noise(t * 0.7f, 11.0f);
            f.fov = lerp(50.0f, 41.0f, easeSine(seg(t, 0.5f, 5.5f)));
            f.fear = 0.3f;
            if (yobi != null) {
                yobi.at(-51.5, groundAt(-51.5, -21.5), -21.5).look(180.0f, 180.0f, -2.0f).walk(0.0f, 0.0f);
                mon(yobi).puppetPose(Yobigoe.CALL, t * 20.0f);
            }
        }
    }

    /** F: the beam sweeps the trunks and finds a pale face leaning out from behind one. */
    static final class Peek extends Shot {
        Trailer.Puppet noz;

        Peek() {
            super("peek", 5.0f);
        }

        @Override
        void setup(Minecraft mc) {
            noz = puppet(ModEntities.NOZOKI.get());
            ((Nozoki) noz.entity()).setLeftHanded(true);
        }

        @Override
        void frame(float t, Frame f) {
            f.cam = eye(-67.5, -66.5, 1.62);
            Vec3 trunk = new Vec3(-73.5, f.cam.y + 0.55, -66.5);
            float pan = easeSine(seg(t, 0.5f, 2.3f));
            f.yaw = lerpAngle(48.0f, yawTo(f.cam, trunk) - 3.0f, pan) + 1.0f * noise(t * 0.6f, 12.0f);
            f.pitch = lerp(5.0f, pitchTo(f.cam, trunk), pan) + 0.8f * noise(t * 0.5f, 13.0f);
            f.hand = true;
            f.held = FLASHLIGHT_ON;
            f.fov = lerp(54.0f, 44.0f, easeSine(seg(t, 2.3f, 5.0f)));
            f.fear = lerp(0.3f, 0.55f, seg(t, 2.0f, 5.0f));
            if (noz != null) {
                double x = -74.45, z = -66.72;
                noz.at(x, groundAt(x, z), z).look(-90.0f, -90.0f, 0.0f).walk(0.0f, 0.0f);
                if (t < 4.1f) mon(noz).puppetPose(Nozoki.PEEK_LEFT, t * 20.0f + 40.0f);
                else mon(noz).puppetPose(Nozoki.RETREAT, (t - 4.1f) * 20.0f);
            }
        }
    }

    /** G: walking on; something walks with you. You look back over your shoulder. */
    static final class Behind extends Shot {
        Trailer.Puppet ush;

        Behind() {
            super("behind", 5.0f);
        }

        @Override
        void setup(Minecraft mc) {
            ush = puppet(ModEntities.USHIRO.get());
        }

        @Override
        void frame(float t, Frame f) {
            float walkYaw = yawTo(new Vec3(-94.5, 0, -88.5), new Vec3(-91.3, 0, -86.0));
            float w = Math.min(t, 2.4f) / 2.4f;
            float k = w - 0.25f * easeSine(w) * w;                 // easing to a stop
            k /= 0.75f;
            double x = lerp(-94.5f, -91.3f, k), z = lerp(-88.5f, -86.0f, k);
            f.cam = eye(x, z, 1.62);
            float turn = easeSine(seg(t, 2.55f, 3.45f));
            f.yaw = walkYaw - 165.0f * turn + 2.0f * noise(t * 0.7f, 14.0f);
            f.pitch = lerp(4.0f, -7.0f, turn) + 1.0f * noise(t * 0.6f, 15.0f);
            if (t < 2.4f) f.walking(t, 1.7f, 1.0f);
            f.shake(0.2f + 0.5f * turn, t, 16.0f);
            f.hand = true;
            f.held = FLASHLIGHT_ON;
            f.handSwayYaw += 6.0f * (turn - easeSine(seg(t, 2.75f, 3.65f)));
            f.fov = 60.0f;
            f.fear = lerp(0.35f, 0.75f, turn);
            if (ush != null) {
                // a pace behind your back, along the way you walk
                double r = Math.toRadians(walkYaw);
                double bx = x + Math.sin(r) * 1.35, bz = z - Math.cos(r) * 1.35;
                ush.at(bx, ground(bx, bz) - 0.05, bz).look(walkYaw, walkYaw + 6.0f * turn, 8.0f).walk(0.0f, 0.0f);
                mon(ush).puppetPose(Ushiro.LEAN, t * 20.0f);
            }
        }
    }

    /** H: creeping into the hut, crouched; the beam finds the futon, and what sleeps on it wakes. */
    static final class Hut extends Shot {
        Trailer.Puppet sleeper;

        Hut() {
            super("hut", 5.5f);
        }

        @Override
        void setup(Minecraft mc) {
            PoiLayout L = PoiLayout.get();
            Trailer.server(l -> openDoor(l, L.hutDoor));
            sleeper = puppet(ModEntities.KOYANUSHI.get());
        }

        @Override
        void frame(float t, Frame f) {
            PoiLayout L = PoiLayout.get();
            double y = L.hut.floorY() + 1 + 1.27;
            float k = easeSine(seg(t, 0.0f, 2.8f));
            double x = lerp(L.hut.x2() + 1.35f, L.hut.x2() - 2.0f, k), z = L.hut.cz() + 0.5 + 0.1 * k;
            f.cam = new Vec3(x, y + 0.03 * Math.sin(t * Math.PI * 2.0 * 1.1) * (1.0f - seg(t, 2.6f, 3.0f)), z);
            Vec3 wall = new Vec3(L.hut.x1() - 1.0, y - 0.2, L.hut.cz() + 0.5);
            Vec3 head = new Vec3(L.hutSleeper.getX() + 0.1, L.hutSleeper.getY() + 0.45, L.hutSleeper.getZ() + 0.5);
            Vec3 at = lerp(wall, head, easeSine(seg(t, 1.4f, 3.3f)));
            f.yaw = yawTo(f.cam, at) + 1.0f * noise(t * 0.6f, 17.0f);
            f.pitch = pitchTo(f.cam, at) + 0.8f * noise(t * 0.5f, 18.0f);
            f.shake(0.15f + 0.6f * seg(t, 3.3f, 4.2f), t, 19.0f);
            f.hand = true;
            f.held = FLASHLIGHT_ON;
            f.fov = lerp(60.0f, 52.0f, easeSine(seg(t, 2.8f, 5.5f)));
            f.fear = lerp(0.45f, 0.8f, seg(t, 3.0f, 4.5f));
            f.danger = lerp(0.0f, 0.5f, seg(t, 3.4f, 4.6f));
            if (sleeper != null) {
                sleeper.at(L.hutSleeper.getX() + 0.5, L.hutSleeper.getY(), L.hutSleeper.getZ() + 0.5).look(-90.0f, -90.0f, 0.0f).walk(0.0f, 0.0f);
                if (t < 3.2f) mon(sleeper).puppetPose(Koyanushi.SLEEP, t * 20.0f);
                else mon(sleeper).puppetPose(Koyanushi.WAKE, (t - 3.2f) * 20.0f);
            }
        }
    }

    /** I: the empty village street in the fog; at the far end, something too tall stands in the road. */
    static final class Village extends Shot {
        Trailer.Puppet tall;

        Village() {
            super("village", 4.5f);
        }

        @Override
        void setup(Minecraft mc) {
            tall = puppet(ModEntities.NOZOKI.get());
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 4.5f));
            double x = lerp(1.0f, 5.6f, k), z = lerp(6.6f, 7.0f, k);
            f.cam = eye(x, z, 1.62);
            Vec3 far = new Vec3(25.5, groundAt(25.5, 7.5) + 1.6, 7.5);
            f.yaw = yawTo(f.cam, far) + 1.5f * noise(t * 0.5f, 20.0f);
            f.pitch = pitchTo(f.cam, far) + 3.0f + 0.8f * noise(t * 0.4f, 21.0f);
            f.walking(t, 1.5f, 0.6f);
            f.hand = true;
            f.held = FLASHLIGHT_ON;
            f.fov = 52.0f;
            f.fear = 0.35f;
            if (tall != null) {
                tall.at(25.5, groundAt(25.5, 7.5), 7.5).look(90.0f, 90.0f, 0.0f).walk(0.0f, 0.0f);
                mon(tall).puppetPose(Nozoki.STAND, t * 20.0f);
            }
        }
    }

    /** J: through the phone's night camera, a figure among the trees, pointing at you. Not there without it. */
    static final class CameraGhost extends Shot {
        Trailer.Puppet ghost;

        CameraGhost() {
            super("camera", 4.5f);
        }

        @Override
        void setup(Minecraft mc) {
            ghost = puppet(ModEntities.UTSURUMONO.get());
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 4.5f));
            Vec3 g = new Vec3(-52.9, groundAt(-52.9, -63.0), -63.0);
            double x = lerp(-60.5f, -59.9f, k), z = lerp(-60.5f, -60.7f, k);
            f.cam = eye(x, z, 1.62);
            Vec3 at = g.add(0.0, 1.25, 0.0);
            f.yaw = yawTo(f.cam, at) + 1.0f * noise(t * 0.6f, 22.0f);
            f.pitch = pitchTo(f.cam, at) + 0.6f * noise(t * 0.5f, 23.0f);
            f.hand = true;
            f.held = CAMERA;
            f.fov = lerp(52.0f, 44.0f, k);
            f.fear = 0.45f;
            f.danger = lerp(0.0f, 0.7f, seg(t, 2.8f, 4.4f));
            if (ghost != null) {
                float yaw = yawTo(g, f.cam);
                ghost.at(g).look(yaw, yaw, 0.0f).walk(0.0f, 0.0f);
                mon(ghost).puppetPose(Utsurumono.POINT, t * 20.0f + 30.0f);
            }
        }
    }

    /** K: the rope bridge over the gorge, the fog below; on the far side, something waits. */
    static final class Bridge extends Shot {
        Trailer.Puppet oya;

        Bridge() {
            super("bridge", 4.5f);
        }

        @Override
        void setup(Minecraft mc) {
            oya = puppet(ModEntities.OYAMASAMA.get());
        }

        @Override
        void frame(float t, Frame f) {
            PoiLayout L = PoiLayout.get();
            float k = easeSine(seg(t, 0.0f, 4.5f));
            double x = L.bridgeX + 0.5, z = lerp(L.bridgeZ0 - 3.2f, L.bridgeZ0 - 0.4f, k);
            f.cam = new Vec3(x, L.bridgeY + 1 + 1.62, z);
            Vec3 far = new Vec3(x, L.bridgeY + 1 + 2.4, L.bridgeZ1 + 1.8);
            f.yaw = yawTo(f.cam, far) + 1.0f * noise(t * 0.5f, 24.0f);
            f.pitch = pitchTo(f.cam, far) + 2.0f + 0.8f * noise(t * 0.4f, 25.0f);
            f.walking(t, 1.4f, 0.5f);
            f.hand = true;
            f.held = FLASHLIGHT_ON;
            f.fov = 48.0f;
            f.fear = 0.5f;
            if (oya != null) {
                oya.at(x, L.bridgeY + 1, L.bridgeZ1 + 1.8).look(180.0f, 180.0f, 0.0f).walk(0.0f, 0.0f);
                mon(oya).puppetPose(Oyamasama.STAND, t * 20.0f);
            }
        }
    }

    /** L: 御山様, right there: the camera can only look up, and up. */
    static final class Loom extends Shot {
        Trailer.Puppet oya;

        Loom() {
            super("loom", 4.0f);
        }

        @Override
        void setup(Minecraft mc) {
            oya = puppet(ModEntities.OYAMASAMA.get());
        }

        @Override
        void frame(float t, Frame f) {
            Vec3 o = new Vec3(-48.2, groundAt(-48.2, -49.5), -49.5);
            float k = easeSine(seg(t, 0.2f, 3.4f));
            double x = lerp(-51.5f, -51.85f, k), z = lerp(-52.5f, -52.85f, k);
            f.cam = eye(x, z, 1.62);
            Vec3 at = o.add(0.0, lerp(1.8f, 3.85f, k), 0.0);
            f.yaw = yawTo(f.cam, at) + 1.2f * noise(t * 0.8f, 26.0f);
            f.pitch = pitchTo(f.cam, at) + 1.0f * noise(t * 0.7f, 27.0f);
            f.shake(0.35f + 0.5f * k, t, 28.0f);
            f.hand = true;
            f.held = FLASHLIGHT_ON;
            f.fov = 62.0f;
            f.fear = 0.85f;
            f.danger = lerp(0.4f, 0.8f, k);
            if (oya != null) {
                float yaw = yawTo(o, f.cam);
                oya.at(o).look(yaw, yaw, 20.0f).walk(0.0f, 0.0f);
                mon(oya).puppetPose(Oyamasama.LOOM, t * 20.0f);
            }
        }
    }

    /** M: close: it stops waving, the body turns away and the head stays on you. Then the lamp goes out. */
    static final class Turn extends Shot {
        Trailer.Puppet yobi;

        Turn() {
            super("turn", 3.2f);
        }

        @Override
        void setup(Minecraft mc) {
            yobi = puppet(ModEntities.YOBIGOE.get());
        }

        @Override
        void frame(float t, Frame f) {
            Vec3 y = new Vec3(-40.3, groundAt(-40.3, -40.2), -40.2);
            f.cam = eye(-44.5, -44.5, 1.62);
            Vec3 at = y.add(0.0, 1.45, 0.0);
            f.yaw = yawTo(f.cam, at) + 0.8f * noise(t * 0.6f, 29.0f);
            f.pitch = pitchTo(f.cam, at) + 0.6f * noise(t * 0.5f, 30.0f);
            f.shake(0.2f, t, 31.0f);
            f.fov = lerp(46.0f, 40.0f, easeSine(seg(t, 0.0f, 2.4f)));
            f.fear = lerp(0.5f, 0.85f, seg(t, 0.9f, 2.0f));
            if (yobi != null) {
                float yaw = yawTo(y, f.cam);
                yobi.at(y).look(yaw, yaw, -4.0f).walk(0.0f, 0.0f);
                yobi.entity().setInvisible(t > 2.35f);
                if (t < 0.9f) mon(yobi).puppetPose(Yobigoe.CALL, t * 20.0f + 60.0f);
                else mon(yobi).puppetPose(Yobigoe.TURN, (t - 0.9f) * 20.0f);
            }
        }
    }

    /** N: the tunnel, the flare's red light, running backwards; it comes on all fours, faster than you. */
    static final class Tunnel extends Shot {
        Trailer.Puppet oya;

        Tunnel() {
            super("tunnel", 4.5f);
        }

        @Override
        void setup(Minecraft mc) {
            oya = puppet(ModEntities.OYAMASAMA.get());
        }

        @Override
        void frame(float t, Frame f) {
            PoiLayout L = PoiLayout.get();
            double x = L.tunnelX + 0.5;
            double z = lerp(L.tunnelZ0 + 10.0f, L.tunnelZ0 + 22.0f, t / duration);
            float run = (float) Math.sin(t * Math.PI * 2.0 * 2.3);
            f.cam = new Vec3(x + 0.08 * noise(t * 2.0f, 32.0f), L.roadY(Mth.floor(z)) + 1 + 1.62 + 0.06 * run, z);
            f.yaw = 180.0f + 2.0f * noise(t * 1.3f, 33.0f);
            f.pitch = 3.0f + 1.5f * run;
            f.shake(1.2f, t, 34.0f);
            f.hand = true;
            f.held = FLARE;
            f.handSwayPitch = 3.0f * run;
            f.fov = 66.0f;
            f.fear = 0.95f;
            f.danger = 1.0f;
            if (oya != null) {
                double oz = lerp(L.tunnelZ0 + 3.5f, L.tunnelZ0 + 18.5f, t / duration);
                oya.at(x - 0.2, L.roadY(Mth.floor(oz)) + 1, oz).look(0.0f, 0.0f, -10.0f).walk(20.0f * 1.0f * t, 1.0f);
                mon(oya).puppetPose(Oyamasama.CRAWL, t * 20.0f);
            }
        }
    }

    /** O: low on the ground, the beam on the trail; it comes crawling, far too fast. */
    static final class Rush extends Shot {
        Trailer.Puppet noz;

        Rush() {
            super("rush", 2.6f);
        }

        @Override
        void setup(Minecraft mc) {
            noz = puppet(ModEntities.NOZOKI.get());
        }

        @Override
        void frame(float t, Frame f) {
            f.cam = eye(-93.5, -87.5, 0.95);
            f.yaw = 135.0f + 1.0f * noise(t * 0.9f, 35.0f);
            f.pitch = 4.0f + 1.0f * noise(t * 0.8f, 36.0f);
            float c = seg(t, 0.35f, 1.75f);
            float cc = c * c * (1.6f - 0.6f * c);
            f.shake(0.3f + 1.6f * c * c, t, 37.0f);
            f.hand = true;
            f.held = FLASHLIGHT_ON;
            f.fov = 64.0f;
            f.fear = lerp(0.6f, 1.0f, c);
            f.danger = lerp(0.5f, 1.0f, c);
            if (noz != null) {
                double d = lerp(10.5f, 1.3f, cc);
                double r = Math.toRadians(135.0);
                double nx = -93.5 - Math.sin(r) * d, nz = -87.5 + Math.cos(r) * d;
                noz.at(nx, ground(nx, nz), nz).look(-45.0f, -45.0f, -15.0f).walk(20.0f * 1.0f * t, 1.0f);
                mon(noz).puppetPose(Nozoki.CRAWL, t * 20.0f);
            }
        }
    }

    /** P: it rises up into the lens. */
    static final class Face extends Shot {
        Trailer.Puppet noz;

        Face() {
            super("face", 2.6f);
        }

        @Override
        void setup(Minecraft mc) {
            noz = puppet(ModEntities.NOZOKI.get());
        }

        @Override
        void frame(float t, Frame f) {
            f.cam = eye(-93.5, -87.5, 1.62);
            f.yaw = 135.0f;
            float lunge = seg(t, 1.0f, 1.4f);
            lunge = lunge * lunge * lunge;
            f.pitch = lerp(-14.0f, -22.0f, lunge);
            if (t > 1.35f) f.shake(2.2f, t, 38.0f);
            else f.shake(0.25f, t, 38.0f);
            f.hand = true;
            f.held = FLASHLIGHT_ON;
            f.fov = 55.0f;
            f.fear = 1.0f;
            f.danger = 1.0f;
            if (noz != null) {
                double d = lerp(2.3f, 0.35f, lunge);
                double r = Math.toRadians(135.0);
                double nx = -93.5 - Math.sin(r) * d, nz = -87.5 + Math.cos(r) * d;
                noz.at(nx, ground(nx, nz) - 0.55 * lunge, nz).look(-45.0f, -45.0f, 12.0f).walk(0.0f, 0.0f);
                mon(noz).puppetPose(Nozoki.STAND, t * 20.0f);
            }
        }
    }

    // =========================================================================================== set dressing
    static void openDoor(ServerLevel l, BlockPos p) {
        for (BlockPos q : new BlockPos[]{p, p.above(), p.below()}) {
            BlockState s = l.getBlockState(q);
            if (s.getBlock() instanceof DoorBlock door && !s.getValue(DoorBlock.OPEN)) {
                door.setOpen(null, l, s, q, true);
                return;
            }
        }
    }

    // =========================================================================================== math
    static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
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
        Vec3 d = to.subtract(from);
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
