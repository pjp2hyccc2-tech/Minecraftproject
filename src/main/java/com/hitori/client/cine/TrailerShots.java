package com.hitori.client.cine;

import com.hitori.block.FacingBlock;
import com.hitori.block.Props;
import com.hitori.block.SlidingDoorBlock;
import com.hitori.client.CameraApp;
import com.hitori.entity.OniDoll;
import com.hitori.entity.Woman;
import com.hitori.registry.ModBlocks;
import com.hitori.registry.ModEntities;
import com.hitori.registry.ModItems;
import com.hitori.world.Fixtures;
import com.hitori.world.Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The HITORI trailer's shots, in edit order (room 203 of コーポ白樺, 3 a.m., rain). Yaw: 0 south, 90 west, 180 north,
 * 270 east; pitch: negative looks up. Room 203: floors at y 69 (feet 70), ceiling at 73; the tatami room x 24..27,
 * z 9..12 with the TV at (27, 71, 9) facing west and the 押し入れ in the column x 23, z 10..12 (its fusuma's crack opens
 * at z 10.0..10.25); the bathroom x 31..32, z 1..2 with the tub along x 32; the hall x 26..27, z 1..7.
 */
final class TrailerShots {
    private TrailerShots() {}

    static List<Shot> all() {
        return List.of(new Ext(), new Corridor(), new Desk(), new Plush(), new BathFill(), new CameraClose(), new Tv(), new TubFloat(),
                new LightsOff(), new Eyes(), new Knife(), new Closet(), new PeekOpen(), new CamTub(), new CamHall(), new CamWoman(),
                new SlitDoll(), new Turn(), new Lunge());
    }

    /** The night's four fixed cameras (feet, facing; pitch NaN = the game's suggested pitch). */
    record CamSpot(int number, Vec3 feet, Direction facing, float pitch) {}

    static final List<CamSpot> CAMERAS = List.of(
            new CamSpot(1, new Vec3(31.5, 72.0, 2.5), Direction.EAST, Float.NaN),     // the tub, from the bathroom's high corner
            new CamSpot(2, new Vec3(26.95, 70.0, 1.6), Direction.SOUTH, 3.0f),        // on the hall floor at its north end, down the hall
            new CamSpot(3, new Vec3(27.5, 72.0, 12.5), Direction.WEST, Float.NaN),    // the tatami room and the 押し入れ, from its SE corner
            new CamSpot(4, new Vec3(26.5, 72.0, 2.5), Direction.WEST, Float.NaN));    // the genkan, from the hall

    static final int CAM1 = 1 << 1, CAM2 = 1 << 2, CAM3 = 1 << 3, CAM4 = 1 << 4;

    /** The crack of the 押し入れ's fusuma: its panels' back face (seen from inside), the crack's span along z. */
    static final double PANEL_X = 23.80, CRACK_Z0 = 10.0, CRACK_Z1 = 10.25;

    enum Held {
        NONE("air", () -> ItemStack.EMPTY),
        KNIFE("hitori:knife", () -> new ItemStack(ModItems.KNIFE.get())),
        PHONE_LIGHT("hitori:phone_light", () -> new ItemStack(ModItems.PHONE_LIGHT.get())),
        LAPTOP("hitori:laptop", () -> new ItemStack(ModItems.LAPTOP.get()));

        final String id;
        private final Supplier<ItemStack> make;
        private ItemStack cached;

        Held(String id, Supplier<ItemStack> make) {
            this.id = id;
            this.make = make;
        }

        ItemStack stack() {
            if (cached == null) cached = make.get();
            return cached;
        }
    }

    // =========================================================================================== frame / shot
    static final class Frame {
        Vec3 cam = Vec3.ZERO;
        float yaw, pitch, roll, fov, fear, danger;
        boolean hand;
        Held main = Held.NONE, off = Held.NONE;
        float handSwayYaw, handSwayPitch, shakeYaw, shakePitch;
        /** 0 = your own eyes; n = the camera app's feed of CAM n (night vision on it: the pack's IR look). */
        int view;
        /** Bit n set: CAM n is hidden (out of the way of a shot that passes through its corner). */
        int hide;
        /** The age of the camera being looked through / at (its REC LED blinks on it); -1 = leave it. */
        int camTicks = -1;
        /** A camera filmed from outside, whose LED follows camTicks. */
        int posedCam;
        /** Eyes: 0 open .. 1 shut (the pack's eyelids). */
        float eyes;
        /** The camera app's timestamp (seconds since midnight), -1 = leave it. */
        double clock = -1;
        /** Draw the camera app's window (CamView) over the view; its IR label; its noise 0..1. */
        boolean feed, ir;
        float burst;
        /** The film draws the main hand: the knife lifted (0..1), the walk's sway. */
        boolean customHand;
        float raise, handBob, handBobX;
        /** A black band where a fusuma panel still covers the crack (world z range on the panels' back face). */
        double maskZ0, maskZ1;

        void reset() {
            cam = Vec3.ZERO;
            yaw = pitch = roll = 0.0f;
            fov = 70.0f;
            fear = danger = 0.0f;
            hand = false;
            main = off = Held.NONE;
            handSwayYaw = handSwayPitch = shakeYaw = shakePitch = 0.0f;
            view = 0;
            hide = 0;
            camTicks = -1;
            posedCam = 0;
            eyes = 0.0f;
            clock = -1;
            feed = false;
            ir = true;
            burst = 0.0f;
            customHand = false;
            raise = handBob = handBobX = 0.0f;
            maskZ0 = maskZ1 = 0.0;
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

        /** Handheld: a slow breath lifting the view, a little drift. */
        void breathe(float t, float amount, float seed) {
            float b = (float) Math.sin(t * Math.PI * 2.0 * 0.28);
            cam = cam.add(0.0, 0.012 * amount * b, 0.0);
            shakePitch += -0.35f * amount * b + 0.25f * amount * noise(t * 0.7f, seed);
            shakeYaw += 0.3f * amount * noise(t * 0.55f, seed + 2.0f);
            roll += 0.25f * amount * noise(t * 0.45f, seed + 5.0f);
        }

        /** A walking camera: head bob, a little sway of the hand. */
        void walking(float t, float rate, float amount) {
            float step = (float) Math.sin(t * Math.PI * 2.0 * rate);
            float side = (float) Math.sin(t * Math.PI * rate);
            cam = cam.add(0.0, 0.035 * amount * step, 0.0);
            pitch += 0.45f * amount * step;
            roll += 0.5f * amount * side;
            handSwayPitch += 1.6f * amount * step;
            handSwayYaw += 1.8f * amount * noise(t * 0.8f, 3.0f);
            handBob += amount * step;
            handBobX += amount * side;
        }
    }

    /** A change to the world at a moment of the film: made on the client at once, the server told the same. */
    static final class Event {
        final float time;
        final String label;
        final Runnable client;
        final Consumer<ServerLevel> server;
        boolean fired;

        Event(float time, String label, Runnable client, Consumer<ServerLevel> server) {
            this.time = time;
            this.label = label;
            this.client = client;
            this.server = server;
        }
    }

    /** How 203 is lit and set for a shot (applied on the server during the set-up). */
    static final class Dress {
        final Set<String> lit = new HashSet<>();
        final Set<String> open = new HashSet<>();
        boolean tv, deskLamp, laptop;
        int bath, fusuma;

        Dress lights(String... rooms) {
            lit.addAll(List.of(rooms));
            return this;
        }

        Dress doors(String... doors) {
            open.addAll(List.of(doors));
            return this;
        }

        void apply(ServerLevel l) {
            Fixtures.setBreaker(l, true);
            for (String r : Layout.LIGHT_ROOMS) Fixtures.setLights(l, r, lit.contains(r));
            Fixtures.setTvChannel(l, 0);
            Fixtures.setTv(l, tv);
            Fixtures.setDevice(l, "desk_lamp", deskLamp);
            Fixtures.setDevice(l, "laptop", laptop);
            Fixtures.setBathTap(l, false);
            Fixtures.setBathLid(l, false);
            Fixtures.setBathWater(l, bath);
            Fixtures.setClosetFusuma(l, fusuma);
            for (String d : Layout.DOORS) {
                if (d.equals("front") || d.startsWith("balcony")) continue;
                Fixtures.setDoor(l, d, open.contains(d));
            }
            Fixtures.setCurtains(l, "tatami", true);
        }
    }

    abstract static class Shot {
        final String name;
        final float duration;
        /** Real ticks between set-up and filming: chunk rebuilds, and Iris eases eye brightness (3 s half-life). */
        int setupTicks = 300;
        /** Drive the shader clock from the film's virtual clock. */
        boolean irisClock = true;
        /** Where to wait during the set-up when the first camera position would put the body in the floor / a wall
         *  (the pack settles its exposure on the light at the eye there). */
        Vec3 setupAt = null;
        /** Preview key times (null: start, middle, end). */
        float[] keys = null;
        final Dress set = new Dress();
        final List<Trailer.Puppet> puppets = new ArrayList<>();
        final List<Event> events = new ArrayList<>();

        Shot(String name, float duration) {
            this.name = name;
            this.duration = duration;
        }

        void reset() {
            for (Event e : events) e.fired = false;
        }

        void prepare() {
        }

        /** On the server thread during the set-up. */
        void dress(ServerLevel l) {
            set.apply(l);
        }

        /** While the game runs: actors and props (client). */
        void setup(Minecraft mc) {
        }

        /** The last moment before the pause. */
        void beforeCapture(Minecraft mc) {
        }

        abstract void frame(float t, Frame f);

        /** One film tick on the client: the shot's own particles. */
        void particles(ClientLevel level, RandomSource r) {
        }

        /** Over the frame (GUI coordinates): the camera app's window, the fusuma's sliding edge. */
        void overlay(GuiGraphics g, Minecraft mc, int w, int h, float t, Frame f) {
            if (f.maskZ1 > f.maskZ0 + 1e-4) {
                float[] a = Trailer.project(mc, f, new Vec3(PANEL_X, 69.6, f.maskZ0), w, h);
                float[] b = Trailer.project(mc, f, new Vec3(PANEL_X, 69.6, f.maskZ1), w, h);
                float[] c = Trailer.project(mc, f, new Vec3(PANEL_X, 72.4, f.maskZ1), w, h);
                float[] d = Trailer.project(mc, f, new Vec3(PANEL_X, 72.4, f.maskZ0), w, h);
                Trailer.quad(g, a, b, c, d, 0xFF030303);
            }
            if (f.feed) {
                int ww = Math.min(w, Math.round(h * 16.0f / 9.0f));
                int x0 = (w - ww) / 2;
                if (x0 > 0) {
                    g.fill(0, 0, x0, h, 0xFF050506);
                    g.fill(x0 + ww, 0, w, h, 0xFF050506);
                }
                g.pose().pushPose();
                g.pose().translate(x0, 0, 0);
                float ticks = t * 20.0f;
                CameraApp.renderForFilm(g, mc.font, ww, h, ticks - (float) Math.floor(ticks), f.view, f.ir, f.burst);
                g.pose().popPose();
            }
        }

        Trailer.Puppet puppet(net.minecraft.world.entity.EntityType<?> type) {
            Trailer.Puppet p = Trailer.puppet(type);
            puppets.add(p);
            return p;
        }

        Trailer.Puppet prop(BlockState block, Vec3 at, float yRot, float scale) {
            Trailer.Puppet p = Trailer.display(block, null, "none", at, yRot, 0.0f, 0.0f, scale, new Vec3(-0.5 * scale, 0.0, -0.5 * scale)
                    .yRot((float) Math.toRadians(yRot)));
            puppets.add(p);
            return p;
        }

        /** An item lying flat (its sprite face up), turned about y. */
        Trailer.Puppet flatItem(ItemStack item, Vec3 at, float yRot, float scale) {
            Trailer.Puppet p = Trailer.display(null, item, "none", at, yRot, -90.0f, 0.0f, scale, Vec3.ZERO);
            puppets.add(p);
            return p;
        }

        Event event(float time, String label, Runnable client, Consumer<ServerLevel> server) {
            Event e = new Event(time, label, client, server);
            events.add(e);
            return e;
        }
    }

    static OniDoll doll(Trailer.Puppet p) {
        return (OniDoll) p.entity();
    }

    static Vec3 v(double x, double y, double z) {
        return new Vec3(x, y, z);
    }

    // =========================================================================================== client-side world edits
    /** Every panel of the 押し入れ's fusuma row set to open (0 shut, 1 crack, 2 open), on the client at once. */
    static void fusumaRow(int open) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockPos first = Layout.marker("closet_fusuma").pos();
        for (BlockPos lower : SlidingDoorBlock.row(level, first)) {
            for (BlockPos p : new BlockPos[]{lower, lower.above()}) {
                BlockState s = level.getBlockState(p);
                if (s.hasProperty(Props.OPEN3) && s.getValue(Props.OPEN3) != open) Trailer.clientMirror(p, s.setValue(Props.OPEN3, open));
            }
        }
    }

    /** A room's lamps and switch, on the client at once. */
    static void roomLights(String room, boolean on) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Layout.Marker m : Layout.markers("lights_" + room)) {
            BlockState s = level.getBlockState(m.pos());
            if (s.hasProperty(Props.LIT)) Trailer.clientMirror(m.pos(), s.setValue(Props.LIT, on));
        }
        BlockPos sw = Layout.marker("switch_" + room).pos();
        BlockState s = level.getBlockState(sw);
        if (s.hasProperty(Props.ON)) Trailer.clientMirror(sw, s.setValue(Props.ON, on));
    }

    // =========================================================================================== act 1: the evening
    /** 00: the street at night in the rain, under the sodium lamp, the asphalt shining; the camera cranes up toward 203,
     *  its two windows lit (the corridor and its door are on the far side of the building). Fill: a few invisible lights
     *  under the eaves, on the client only, so the facade reads as a house. */
    static final class Ext extends Shot {
        static final int[][] FILL = {{8, 71, 16, 11}, {16, 71, 16, 12}, {22, 72, 16, 12}, {30, 72, 16, 12}, {37, 71, 16, 12},
                {44, 71, 16, 11}, {20, 67, 16, 10}, {33, 67, 16, 10}};

        Ext() {
            super("00_ext", 6.5f);
            set.lights("western", "tatami");
            setupTicks = 360;
            setupAt = v(31.6, 66.63, 24.9);
            event(0.0f, "fill lights under the eaves", () -> {
                ClientLevel level = Minecraft.getInstance().level;
                for (int[] l : FILL) {
                    BlockPos p = new BlockPos(l[0], l[1], l[2]);
                    if (level == null || !level.getBlockState(p).isAir()) continue;
                    Trailer.clientBlock(p, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, l[3]));
                }
            }, null);
        }

        @Override
        void dress(ServerLevel l) {
            super.dress(l);
            // the tatami room's curtains open: its window glows too
            Fixtures.setCurtains(l, "tatami", false);
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 6.5f));
            Vec3 from = lerp(v(31.6, 65.75, 24.9), v(28.2, 69.0, 20.4), k);
            from = from.add(0.03 * noise(t * 0.4f, 1.0f), 0.02 * noise(t * 0.35f, 2.0f), 0.0);
            Vec3 at = lerp(v(26.5, 69.4, 14.0), v(27.4, 71.2, 13.8), easeSine(seg(t, 0.2f, 6.5f)));
            f.look(from, at);
            f.yaw += 0.6f * noise(t * 0.3f, 3.0f);
            f.fov = lerp(62.0f, 46.0f, k);
            f.fear = 0.08f;
        }
    }

    /** 01: the 2F open-air corridor, rain off the eaves; a slow dolly toward 203's steel door and its plate. */
    static final class Corridor extends Shot {
        Corridor() {
            super("01_corridor", 4.0f);
            set.lights("western");
            setupAt = v(19.4, 70.63, -1.95);
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 4.0f));
            Vec3 from = lerp(v(19.4, 70.52, -1.95), v(21.9, 70.5, -2.0), k);
            Vec3 at = lerp(v(25.2, 70.35, -0.2), v(24.95, 70.3, -0.1), k);
            f.look(from, at);
            f.breathe(t, 0.6f, 4.0f);
            f.fov = 54.0f;
            f.fear = 0.12f;
        }
    }

    /** 02: the western room, only the desk lamp on: the printout and the laptop on the desk; a slow lateral push. */
    static final class Desk extends Shot {
        Desk() {
            super("02_desk", 4.5f);
            set.deskLamp = true;
            set.laptop = true;
            setupAt = v(29.75, 71.63, 8.55);
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 4.5f));
            Vec3 from = lerp(v(29.75, 71.34, 8.55), v(30.55, 71.3, 8.6), k);
            Vec3 at = lerp(v(30.95, 70.82, 7.55), v(31.75, 70.85, 7.85), k);
            f.look(from, at);
            f.breathe(t, 0.4f, 6.0f);
            f.fov = 48.0f;
            f.fear = 0.12f;
        }
    }

    /** 03: the tatami room, the hanging lamp on: the white rabbit on the ちゃぶ台 with the scissors, the red thread, the rice;
     *  a low macro orbit. */
    static final class Plush extends Shot {
        static final Vec3 C = v(25.5, 70.3125, 11.45);

        Plush() {
            super("03_plush", 4.5f);
            set.lights("tatami");
            setupAt = v(26.4, 71.62, 11.5);
        }

        @Override
        void setup(Minecraft mc) {
            BlockState plush = ModBlocks.PLUSH_DOLL.get().defaultBlockState().setValue(Props.PRESENT, true).setValue(FacingBlock.FACING, Direction.EAST);
            prop(plush, v(25.42, 70.3125, 11.44), 12.0f, 0.82f);
            flatItem(new ItemStack(ModItems.SCISSORS.get()), v(25.72, 70.335, 11.7), 35.0f, 0.4f);
            flatItem(new ItemStack(ModItems.RED_THREAD.get()), v(25.7, 70.335, 11.2), -20.0f, 0.3f);
            flatItem(new ItemStack(ModItems.NAIL_CLIPPER.get()), v(25.3, 70.335, 11.8), 70.0f, 0.22f);
            prop(ModBlocks.RICE_BAG.get().defaultBlockState().setValue(FacingBlock.FACING, Direction.EAST), v(24.55, 70.0, 11.35), 0.0f, 0.9f);
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 4.5f));
            double a = Math.toRadians(lerp(68.0f, 104.0f, k));
            double r = 1.0;
            Vec3 from = v(C.x + Math.sin(a) * r, 70.56, C.z + Math.cos(a) * r);
            f.look(from, v(C.x - 0.05, 70.66, C.z));
            f.breathe(t, 0.25f, 8.0f);
            f.hide = CAM3;
            f.fov = 46.0f;
            f.fear = 0.15f;
        }
    }

    /** 04: the bathroom (the washroom's light through the open door): the tap running into the deep square tub, the water
     *  rising (a water surface on the client climbing inside the emptied tub; the tub's own water has only three levels). */
    static final class BathFill extends Shot {
        Trailer.Puppet water;

        BathFill() {
            super("04_bath_fill", 4.0f);
            set.lights("washroom");
            set.doors("bath");
            set.bath = 0;
            setupAt = v(31.4, 71.62, 2.7);
            // the tap runs on the client only: on the server the tub would fill up during the set-up
            event(0.0f, "the tap runs", () -> {
                BlockPos tap = Layout.marker("bath_tap").pos();
                BlockState s = Minecraft.getInstance().level.getBlockState(tap);
                if (s.hasProperty(Props.RUNNING)) Trailer.clientBlock(tap, s.setValue(Props.RUNNING, true));
            }, null);
        }

        @Override
        void setup(Minecraft mc) {
            // the inner area of the tub (both its blocks): x 32 + 1.5..14.5 px, z 1 + 1.5 px .. 2 + 14.5 px
            water = Trailer.display(ModBlocks.PUDDLE.get().defaultBlockState(), null, "none", v(32.09375, 70.3, 1.09375), 0.0f, 0.0f, 0.0f,
                    new Vec3(0.8125, 1.0, 1.8125), Vec3.ZERO);
            puppets.add(water);
        }

        static double level(float t) {
            return 70.24 + 0.36 * easeSine(seg(t, 0.0f, 4.6f));
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 4.0f));
            Vec3 from = lerp(v(31.3, 71.58, 2.86), v(31.5, 71.45, 2.5), k);
            Vec3 at = lerp(v(32.5, 70.55, 1.5), v(32.5, 70.62, 1.4), k);
            f.look(from, at);
            f.breathe(t, 0.35f, 10.0f);
            f.hide = CAM1;
            f.fov = 52.0f;
            f.fear = 0.15f;
            if (water != null) water.at(32.09375, level(t), 1.09375);
        }
    }

    /** 05: close on a placed fixed camera on the hall floor, its REC LED blinking, aimed down the hall. */
    static final class CameraClose extends Shot {
        CameraClose() {
            super("05_camera", 3.5f);
            set.lights("hall");
            set.tv = true;
            set.doors("tatami_hall", "tatami_dk");
            setupAt = v(27.45, 71.62, 1.3);
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 3.5f));
            Vec3 from = lerp(v(27.48, 70.47, 1.18), v(27.36, 70.44, 1.32), k);
            Vec3 at = lerp(v(26.86, 70.18, 2.05), v(26.8, 70.18, 2.2), k);
            f.look(from, at);
            f.breathe(t, 0.3f, 12.0f);
            f.posedCam = 2;
            f.camTicks = (int) Math.floor(t * 20.0f) + 12;
            f.hide = CAM4;
            f.fov = 42.0f;
            f.fear = 0.18f;
        }
    }

    // =========================================================================================== act 2: the ritual
    /** 06: the tatami room with every light off: the CRT's static, its cold flicker on the room; a slow push toward it. */
    static final class Tv extends Shot {
        Tv() {
            super("06_tv", 5.0f);
            set.tv = true;
            setupAt = v(24.4, 71.62, 11.3);
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 5.0f));
            Vec3 from = lerp(v(24.35, 71.28, 11.35), v(25.35, 71.3, 10.3), k);
            f.look(from, v(27.2, 71.36, 9.5));
            f.breathe(t, 0.35f, 14.0f);
            f.fov = lerp(52.0f, 47.0f, k);
            f.fear = 0.25f;
        }
    }

    /** 07: the dark bathroom (only the washroom's light coming through the door): the doll floating face down in the black
     *  water; a slow push from above. */
    static final class TubFloat extends Shot {
        Trailer.Puppet d;

        TubFloat() {
            super("07_tub_float", 5.0f);
            set.lights("washroom");
            set.tv = true;
            set.bath = 2;
            set.doors("bath", "washroom");
            setupAt = v(31.6, 71.62, 2.6);
        }

        @Override
        void setup(Minecraft mc) {
            d = puppet(ModEntities.ONI_DOLL.get());
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 5.0f));
            Vec3 from = lerp(v(31.82, 72.3, 2.78), v(31.95, 71.88, 2.32), k);
            Vec3 at = lerp(v(32.48, 70.62, 1.55), v(32.5, 70.62, 1.5), k);
            f.look(from, at);
            f.breathe(t, 0.3f, 16.0f);
            f.hide = CAM1;
            f.fov = 40.0f;
            f.fear = 0.3f;
            if (d != null) {
                Vec3 p = OniDoll.tubSpot(0);
                d.at(p.x, p.y, p.z + 0.08).look(150.0f, 150.0f, 0.0f).walk(0.0f, 0.0f);
                doll(d).puppetPose(OniDoll.FLOAT, t * 20.0f + 260.0f, 0.0f);
            }
        }
    }

    /** 08: the hall and the kitchen with the light on; it goes out at 1.2 s, leaving only the TV's spill. */
    static final class LightsOff extends Shot {
        LightsOff() {
            super("08_lights_off", 3.5f);
            set.lights("hall", "dk");
            setupAt = v(27.25, 71.63, 2.15);
            set.tv = true;
            set.doors("tatami_dk", "tatami_hall");
            keys = new float[]{0.6f, 1.7f, 3.45f};
            event(1.2f, "the hall and the kitchen go dark", () -> {
                roomLights("hall", false);
                roomLights("dk", false);
            }, l -> {
                Fixtures.setLights(l, "hall", false);
                Fixtures.setLights(l, "dk", false);
            });
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 3.5f));
            Vec3 from = lerp(v(27.25, 71.55, 2.15), v(27.15, 71.52, 2.45), k);
            f.look(from, v(24.95, 70.85, 8.7));
            f.breathe(t, 0.4f, 18.0f);
            f.hide = CAM4 | CAM2;
            f.fov = 56.0f;
            f.fear = lerp(0.28f, 0.36f, seg(t, 1.2f, 2.5f));
        }
    }

    /** 09: eyes shut in front of the TV (the pack's eyelids: black, the static's flicker red through them); they open on
     *  the TV at 2.8 s. */
    static final class Eyes extends Shot {
        Eyes() {
            super("09_eyes", 4.0f);
            set.tv = true;
            setupAt = v(24.75, 71.62, 9.55);
            keys = new float[]{1.0f, 3.0f, 3.95f};
        }

        @Override
        void frame(float t, Frame f) {
            f.look(v(24.78, 70.98, 9.56), v(27.2, 70.86, 9.5));
            f.breathe(t, 0.5f, 20.0f);
            // shut, a flutter, open
            float open = easeSine(seg(t, 2.78f, 3.3f));
            float flutter = 0.18f * (float) Math.exp(-sq((t - 3.02f) / 0.05f));
            f.eyes = clamp01(1.0f - open + flutter);
            f.fov = 54.0f;
            f.fear = 0.35f;
        }
    }

    /** 10: first person, the kitchen knife in your hand, out of the lit washroom into the dark bathroom toward the floating
     *  doll; the knife comes up at the end. */
    static final class Knife extends Shot {
        Trailer.Puppet d;

        Knife() {
            super("10_knife", 4.0f);
            set.lights("washroom");
            set.tv = true;
            set.bath = 2;
            set.doors("bath", "washroom");
            keys = new float[]{0.3f, 2.2f, 3.95f};
        }

        @Override
        void setup(Minecraft mc) {
            d = puppet(ModEntities.ONI_DOLL.get());
        }

        @Override
        void frame(float t, Frame f) {
            // through the doorway east of the open folding door's leaf (it stands at the cell's west edge)
            float w = seg(t, 0.0f, 3.0f);
            float k = w * w * (3.0f - 2.0f * w);
            double x = 30.95 + 0.62 * easeSine(seg(t, 0.0f, 1.4f));
            double z = 5.15 - 2.45 * k;
            Vec3 from = v(x, 71.62, z);
            Vec3 door = v(31.6, 71.1, 2.2);
            Vec3 tub = v(32.45, 70.68, 1.5);
            f.look(from, lerp(door, tub, easeSine(seg(t, 1.3f, 3.0f))));
            f.walking(t, 1.6f, 1.0f - seg(t, 2.4f, 3.0f));
            f.shake(0.15f + 0.25f * seg(t, 3.0f, 3.8f), t, 22.0f);
            f.hand = true;
            f.main = Held.KNIFE;
            f.customHand = true;
            f.raise = seg(t, 3.0f, 3.75f);
            f.hide = CAM1;
            f.fov = 60.0f;
            f.fear = lerp(0.38f, 0.48f, seg(t, 2.5f, 4.0f));
            if (d != null) {
                Vec3 p = OniDoll.tubSpot(0);
                d.at(p.x, p.y, p.z + 0.08).look(150.0f, 150.0f, 0.0f).walk(0.0f, 0.0f);
                doll(d).puppetPose(OniDoll.FLOAT, t * 20.0f + 520.0f, 0.0f);
            }
        }
    }

    // =========================================================================================== act 3: hiding
    /** 11: inside the 押し入れ, the fusuma shut: near darkness, as the game draws it while you hide (its closet layer: the
     *  dark, the futon's edge above) and the thin vertical slit of the TV's light at the gap; handheld breathing. */
    static final class Closet extends Shot {
        static final net.minecraft.resources.ResourceLocation DARK = new net.minecraft.resources.ResourceLocation("hitori", "textures/gui/closet_dark.png");

        Closet() {
            super("11_closet", 4.0f);
            set.tv = true;
            set.fusuma = 0;
            setupAt = v(23.42, 71.62, 11.4);
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 4.0f));
            Vec3 from = lerp(v(23.32, 70.95, 11.62), v(23.36, 70.97, 11.48), k);
            f.look(from, v(23.62, 71.25, 10.2));
            f.breathe(t, 1.0f, 24.0f);
            f.shake(0.12f, t, 25.0f);
            f.fov = 58.0f;
            f.fear = 0.5f;
            f.danger = 0.05f;
        }

        @Override
        void overlay(GuiGraphics g, Minecraft mc, int w, int h, float t, Frame f) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            g.blit(DARK, 0, 0, w, h, 0.0f, 0.0f, 256, 256, 256, 256);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            // the gap of the fusuma: the TV's flicker in a thin line where the panels meet the post
            float[] top = Trailer.project(mc, f, v(23.9, 71.9, 10.12), w, h);
            float[] bot = Trailer.project(mc, f, v(23.9, 70.08, 10.12), w, h);
            if (top == null || bot == null) return;
            float flick = clamp01(0.62f + 0.22f * noise(t * 9.0f, 3.0f) + 0.16f * noise(t * 23.0f, 7.0f));
            float[][] layers = {{4.5f, 0.07f}, {1.8f, 0.22f}, {0.55f, 0.95f}};
            for (float[] l : layers) {
                float hw = l[0] * 0.5f;
                int a = Math.round(255.0f * clamp01(l[1] * flick));
                int col = (a << 24) | 0xBFD4FF;
                Trailer.quad(g, new float[]{top[0] - hw, top[1]}, new float[]{bot[0] - hw, bot[1]}, new float[]{bot[0] + hw, bot[1]},
                        new float[]{top[0] + hw, top[1]}, col);
            }
        }
    }

    /** 12: from inside, the fusuma slides open a crack and the tatami room appears in the TV's flicker — empty. */
    static final class PeekOpen extends Shot {
        static final float OPEN = 1.0f, SLID = 1.55f;

        PeekOpen() {
            super("12_peek", 4.0f);
            set.tv = true;
            set.fusuma = 0;
            setupAt = v(23.42, 71.62, 10.4);
            keys = new float[]{0.5f, 1.4f, 3.95f};
            event(OPEN, "the fusuma opens a crack", () -> fusumaRow(1), l -> Fixtures.setClosetFusuma(l, 1));
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 1.6f, 4.0f));
            Vec3 from = lerp(v(23.1, 70.93, 10.125), v(23.34, 70.94, 10.125), k);
            f.look(from, v(26.0, 70.75, 10.12));
            f.breathe(t, 0.7f, 26.0f);
            f.fov = 48.0f;
            f.fear = lerp(0.52f, 0.6f, seg(t, 1.0f, 3.0f));
            f.danger = 0.08f;
            if (t >= OPEN - 0.001f) {
                float s = easeSine(seg(t, OPEN, SLID));
                if (s < 0.999f) {
                    f.maskZ0 = CRACK_Z0 + (CRACK_Z1 - CRACK_Z0) * s - 0.01;
                    f.maskZ1 = CRACK_Z1 + 0.01;
                }
            }
        }
    }

    /** 13: the camera app, CAM1 浴室, IR: the tub, empty, its water still moving. */
    static final class CamTub extends Shot {
        CamTub() {
            super("13_cam_tub", 3.5f);
            set.tv = true;
            setupAt = v(31.45, 71.63, 2.7);
            set.bath = 2;
            set.doors("bath", "washroom");
        }

        @Override
        void frame(float t, Frame f) {
            f.look(v(31.52, 72.24, 2.62), v(32.42, 70.6, 1.5));
            f.view = 1;
            f.feed = true;
            f.camTicks = (int) Math.floor(t * 20.0f);
            f.clock = 3 * 3600 + 47 * 60 + 12 + t;
            f.fov = 64.0f;
            f.fear = 0.55f;
            f.danger = 0.1f;
        }

    }

    /** 14: the camera app, CAM2 廊下 (on the hall floor), IR: the doll crawling down the hall toward it with the knife, in
     *  stop-motion (the pose and the place held, then a jump, about six times a second). */
    static final class CamHall extends Shot {
        static final int[] HOLDS = {5, 4, 6, 5, 7, 4, 5, 6, 4, 5, 6, 5, 4, 7, 5, 5, 6, 4, 5, 6, 5, 4, 6, 5, 5, 7, 4, 5, 6, 5};
        static final float SPEED = 1.3f;
        Trailer.Puppet d;

        CamHall() {
            super("14_cam_hall", 4.0f);
            set.tv = true;
            set.doors("tatami_dk", "tatami_hall");
            setupAt = v(27.3, 71.63, 1.4);
        }

        @Override
        void setup(Minecraft mc) {
            d = puppet(ModEntities.ONI_DOLL.get());
        }

        /** The film time the stop-motion shows at t: the start of the hold t falls in. */
        static float held(float t) {
            int frame = (int) Math.floor(t * 30.0f + 1e-3);
            int at = 0;
            for (int i = 0; i < 400; i++) {
                int len = HOLDS[i % HOLDS.length];
                if (frame < at + len) return at / 30.0f;
                at += len;
            }
            return t;
        }

        @Override
        void frame(float t, Frame f) {
            // CAM2 on the floor at the hall's north end, set down a little east of its spot, looking down the hall
            f.cam = v(27.32, 70.255, 1.55);
            f.yaw = 0.0f;
            f.pitch = 3.5f;
            f.view = 2;
            f.feed = true;
            f.camTicks = (int) Math.floor(t * 20.0f);
            f.clock = 3 * 3600 + 52 * 60 + 40 + t;
            f.fov = 50.0f;
            f.fear = 0.6f;
            f.danger = lerp(0.15f, 0.3f, seg(t, 0.0f, 4.0f));
            float h = held(t);
            f.burst = Math.abs(t - 2.62f) < 0.05f ? 0.7f : 0.0f;
            if (d != null) {
                // dragging itself toward the lens: 0.034 blocks a tick of its own clock
                double z = 6.75 - 0.034 * 20.0 * h * SPEED;
                d.at(27.42, 70.0, z).look(180.0f, 180.0f, 0.0f).walk(0.0f, 0.0f);
                doll(d).puppetPose(OniDoll.CRAWL, h * 20.0f * SPEED + 40.0f, 0.12f);
            }
        }
    }

    /** 15: the camera app, CAM3 和室, IR: the woman standing in the corner by the pillar, head bowed; one short tear of the
     *  feed. (The camera is set a little nearer to her than its spot in the SE corner.) */
    static final class CamWoman extends Shot {
        static final float TEAR0 = 2.0f, TEAR1 = 2.13f;
        Trailer.Puppet w;

        CamWoman() {
            super("15_cam_woman", 3.5f);
            set.tv = true;
            setupAt = v(26.6, 71.63, 11.9);
            keys = new float[]{0.5f, 2.05f, 3.4f};
        }

        @Override
        void setup(Minecraft mc) {
            w = puppet(ModEntities.WOMAN.get());
        }

        @Override
        void frame(float t, Frame f) {
            Vec3 at = Layout.markers("woman_spots").get(0).center();
            f.look(v(26.62, 71.98, 11.62), v(at.x + 0.12, 70.98, at.z + 0.12));
            f.view = 3;
            f.feed = true;
            f.camTicks = (int) Math.floor(t * 20.0f);
            f.clock = 4 * 3600 + 3 * 60 + 17 + t;
            f.fov = 46.0f;
            f.fear = 0.6f;
            f.danger = 0.08f;
            boolean tear = t > TEAR0 && t < TEAR1;
            f.burst = tear ? 1.0f : 0.0f;
            if (w != null) {
                // facing into the corner, away from the lens
                Vec3 away = at.add(at.subtract(f.cam).multiply(1, 0, 1).normalize());
                float yaw = yawTo(at, away);
                w.at(at).look(yaw, yaw, 0.0f).walk(0.0f, 0.0f);
                ((Woman) w.entity()).puppetPose(Woman.STAND, t * 20.0f + 300.0f, tear ? 0.7f : 0.1f);
            }
        }
    }

    /** 16: through the fusuma's crack from inside the closet: the doll standing just outside in the TV's flicker, its
     *  back to you, perfectly still. */
    static final class SlitDoll extends Shot {
        Trailer.Puppet d;

        SlitDoll() {
            super("16_slit_doll", 3.5f);
            set.tv = true;
            set.fusuma = 1;
            setupAt = v(23.42, 71.62, 10.4);
        }

        @Override
        void setup(Minecraft mc) {
            d = puppet(ModEntities.ONI_DOLL.get());
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 3.5f));
            Vec3 from = lerp(v(23.08, 70.9, 10.125), v(23.16, 70.9, 10.125), k);
            f.look(from, v(24.75, 70.32, 10.13));
            f.breathe(t, 0.5f, 28.0f);
            f.fov = 44.0f;
            f.fear = 0.72f;
            f.danger = 0.45f;
            if (d != null) {
                d.at(24.74, 70.0, 10.14).look(-90.0f, -90.0f, 0.0f).walk(0.0f, 0.0f);
                doll(d).puppetPose(OniDoll.STAND, 140.0f, 0.0f);
            }
        }
    }

    /** 17: closer through the crack: its head turns round to face you, in three jerks; a very slow push. */
    static final class Turn extends Shot {
        static final float START = 0.55f;
        Trailer.Puppet d;

        Turn() {
            super("17_turn", 3.0f);
            set.tv = true;
            set.fusuma = 1;
            setupAt = v(23.42, 71.62, 10.4);
            keys = new float[]{0.3f, 1.05f, 2.95f};
        }

        @Override
        void setup(Minecraft mc) {
            d = puppet(ModEntities.ONI_DOLL.get());
        }

        @Override
        void frame(float t, Frame f) {
            float k = easeSine(seg(t, 0.0f, 3.0f));
            Vec3 from = lerp(v(23.26, 70.9, 10.125), v(23.52, 70.88, 10.125), k);
            f.look(from, v(24.74, 70.4, 10.13));
            f.breathe(t, 0.6f, 30.0f);
            f.shake(0.1f + 0.25f * seg(t, 1.2f, 1.8f), t, 31.0f);
            f.fov = lerp(42.0f, 38.0f, k);
            f.fear = lerp(0.8f, 0.9f, seg(t, 0.8f, 1.8f));
            f.danger = lerp(0.55f, 0.7f, seg(t, 0.8f, 1.8f));
            if (d != null) {
                d.at(24.74, 70.0, 10.14).look(-90.0f, -90.0f, 0.0f).walk(0.0f, 0.0f);
                doll(d).puppetPose(OniDoll.TURN, Math.max(0.0f, t - START) * 20.0f, 0.0f);
            }
        }
    }

    /** 18: the fusuma flung open, the doll lunging into the lens until its face fills the frame (the laptop in your hands,
     *  its screen's glow on the face). */
    static final class Lunge extends Shot {
        static final float OPEN = 0.2f, SLID = 0.34f, SPRING = 0.5f;
        Trailer.Puppet d;

        Lunge() {
            super("18_lunge", 2.0f);
            set.tv = true;
            set.fusuma = 1;
            setupAt = v(23.42, 71.62, 10.4);
            keys = new float[]{0.1f, 0.7f, 1.6f};
            event(OPEN, "the fusuma flung open", () -> fusumaRow(2), l -> Fixtures.setClosetFusuma(l, 2));
        }

        @Override
        void setup(Minecraft mc) {
            d = puppet(ModEntities.ONI_DOLL.get());
        }

        @Override
        void frame(float t, Frame f) {
            float pt = Math.max(0.0f, t - SPRING) * 20.0f;
            float hit = seg(pt, 2.0f, 7.5f);
            float creep = easeSine(seg(t, 0.95f, 2.0f));
            f.look(v(23.2, 70.9, 10.2), lerp(v(24.6, 70.45, 10.3), v(23.9, 70.62, 10.22), easeSine(hit)));
            f.shake(0.25f + 1.6f * hit * (1.0f - 0.6f * creep) + 0.6f * seg(t, OPEN, SLID), t, 32.0f);
            f.breathe(t, 0.6f, 33.0f);
            f.main = Held.LAPTOP;
            f.fov = 54.0f;
            f.fear = 0.85f;
            f.danger = lerp(0.5f, 0.72f, seg(t, OPEN, 0.9f));
            if (t >= OPEN - 0.001f) {
                float s = seg(t, OPEN, SLID);
                s = s * s;
                if (s < 0.999f) {
                    f.maskZ0 = CRACK_Z1 + (11.0 - CRACK_Z1) * s - 0.01;
                    f.maskZ1 = 11.02;
                }
            }
            if (d != null) {
                // the model springs ~0.56 blocks by itself; the body carries it on to the lens, then it creeps closer
                float carry = hit * hit * (3.0f - 2.0f * hit);
                double x = 24.6 - 0.62 * carry - 0.07 * creep;
                double y = 70.0 + 0.12 * carry;
                d.at(x, y, 10.2).look(90.0f, 90.0f, -10.0f * carry).walk(0.0f, 0.0f);
                doll(d).puppetPose(OniDoll.LUNGE, pt, 0.25f);
            }
        }
    }

    /** Where the night's camera n looks from, and how it is aimed (its spot, facing, pitch). */
    static final class CamSpotView {
        static void of(int n, Frame f, float yawOffset, float pitch) {
            for (CamSpot c : CAMERAS) {
                if (c.number() != n) continue;
                f.cam = c.feet().add(0.0, 0.255, 0.0);
                f.yaw = c.facing().toYRot() + yawOffset;
                f.pitch = pitch;
                return;
            }
        }
    }

    // =========================================================================================== math
    static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    static float sq(float v) {
        return v * v;
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
