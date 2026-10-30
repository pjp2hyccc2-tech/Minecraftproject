package com.mcbesiege.part;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every machine part (Besiege block) with its physical data, connection rules and configuration schema.
 * Shapes are given in "canonical" pixel coordinates: the part faces UP (+Y), its back (attachment) face is y=0.
 */
public enum PartType {
    // ---------------------------------------------------------------- basic
    STARTING_BLOCK(d(Cat.BASIC).cube().mass(0.25).hp(1e9).place(Place.PLAYER_H)),
    WOODEN_BLOCK(d(Cat.BASIC).cube().mass(0.3).hp(30).flammable()),
    REINFORCED_WOOD(d(Cat.BASIC).cube().mass(0.5).hp(70).flammable().burnRate(0.08)),
    WOODEN_POLE(d(Cat.BASIC).box(5, 0, 5, 11, 16, 11).mass(0.2).hp(25).flammable()),
    WOODEN_PANEL(d(Cat.BASIC).box(0, 0, 0, 16, 4, 16).mass(0.2).hp(15).flammable().connect(Connect.BACK)),
    SEAT(d(Cat.BASIC).box(1, 0, 1, 15, 8, 15).mass(0.3).hp(30).flammable().connect(Connect.BACK)),
    // ---------------------------------------------------------------- armour
    IRON_BLOCK(d(Cat.ARMOR).cube().mass(1.0).hp(220).friction(0.5)),
    STEEL_BLOCK(d(Cat.ARMOR).cube().mass(1.6).hp(450).friction(0.5)),
    IRON_PLATE(d(Cat.ARMOR).box(0, 0, 0, 16, 4, 16).mass(0.5).hp(160).friction(0.5).connect(Connect.BACK)),
    STONE_BLOCK(d(Cat.ARMOR).cube().mass(0.8).hp(100).friction(0.7)),
    // ---------------------------------------------------------------- locomotion
    WHEEL(d(Cat.LOCOMOTION).wheel(0.9, 0.2).mass(0.5).hp(80).friction(1.0).flammable().burnRate(0.05).headFrac(0.8)
            .keys(k("forward", Keys.UP), k("backward", Keys.DOWN)).slider("speed", 0, 2, 1).toggle("automatic", false)
            .toggle("toggle", false).toggle("autobrake", true).toggle("flip", false).toggle("powered", true).torque(40)),
    IRON_WHEEL(d(Cat.LOCOMOTION).wheel(0.9, 0.2).mass(0.9).hp(180).friction(0.85).headFrac(0.85)
            .keys(k("forward", Keys.UP), k("backward", Keys.DOWN)).slider("speed", 0, 2, 1).toggle("automatic", false)
            .toggle("toggle", false).toggle("autobrake", true).toggle("flip", false).toggle("powered", true).torque(70)),
    LARGE_TIRE(d(Cat.LOCOMOTION).wheel(1.4, 0.3).mass(1.2).hp(120).friction(1.3).headFrac(0.85)
            .keys(k("forward", Keys.UP), k("backward", Keys.DOWN)).slider("speed", 0, 2, 1).toggle("automatic", false)
            .toggle("toggle", false).toggle("autobrake", true).toggle("flip", false).toggle("powered", true).torque(110)),
    SMALL_WHEEL(d(Cat.LOCOMOTION).wheel(0.45, 0.15).mass(0.2).hp(50).friction(0.9).headFrac(0.7)),
    GRIP_PAD(d(Cat.LOCOMOTION).box(0, 0, 0, 16, 4, 16).mass(0.3).hp(40).friction(2.0).connect(Connect.BACK)),
    ICE_PAD(d(Cat.LOCOMOTION).box(0, 0, 0, 16, 4, 16).mass(0.3).hp(40).friction(0.02).connect(Connect.BACK)),
    // ---------------------------------------------------------------- mechanical
    HINGE(d(Cat.MECHANICAL).half().mass(0.5).hp(60).flammable().joint(JointKind.PERP).twist()
            .toggle("limits", false).slider("angle", 0, 180, 90)),
    STEERING_HINGE(d(Cat.MECHANICAL).half().mass(0.5).hp(60).flammable().joint(JointKind.PERP).twist()
            .keys(k("left", Keys.LEFT), k("right", Keys.RIGHT)).slider("speed", 0, 2, 1).slider("limit", 0, 180, 45)
            .toggle("return", true).toggle("flip", false)),
    STEERING_BLOCK(d(Cat.MECHANICAL).half().mass(0.5).hp(70).joint(JointKind.AXIAL)
            .keys(k("left", Keys.LEFT), k("right", Keys.RIGHT)).slider("speed", 0, 2, 1).toggle("flip", false)),
    SPINNING_BLOCK(d(Cat.MECHANICAL).half().mass(0.6).hp(70).joint(JointKind.AXIAL)
            .keys(k("spin")).slider("speed", 0, 2, 1).toggle("automatic", true).toggle("toggle", false).toggle("flip", false).torque(40)),
    BALL_JOINT(d(Cat.MECHANICAL).half().mass(0.5).hp(60).joint(JointKind.BALL)),
    SUSPENSION(d(Cat.MECHANICAL).half().mass(0.5).hp(60).joint(JointKind.SLIDER).slider("spring", 0.5f, 3, 1).slider("damper", 0, 3, 1)),
    PISTON(d(Cat.MECHANICAL).half().mass(0.6).hp(80).flammable().burnRate(0.05).joint(JointKind.SLIDER)
            .keys(k("extend", Keys.H)).slider("speed", 0.1f, 2, 1).toggle("toggle", true)),
    DECOUPLER(d(Cat.MECHANICAL).half().mass(0.3).hp(50).joint(JointKind.FIXED).keys(k("detach", Keys.J)).slider("force", 0, 2, 1)),
    GRABBER(d(Cat.MECHANICAL).box(2, 0, 2, 14, 12, 14).mass(0.5).hp(60).friction(1.2).connect(Connect.BACK).keys(k("release", Keys.V))),
    // ---------------------------------------------------------------- weapons
    CANNON(d(Cat.WEAPONS).box(3, 0, 3, 13, 16, 13).mass(1.0).hp(90).connect(Connect.BACK)
            .keys(k("fire", Keys.C)).slider("power", 0.25f, 2, 1)),
    CROSSBOW(d(Cat.WEAPONS).box(2, 0, 2, 14, 10, 14).mass(0.6).hp(50).flammable().connect(Connect.BACK)
            .keys(k("fire", Keys.C)).slider("power", 0.25f, 2, 1).slider("ammo", 1, 50, 15)),
    FLAMETHROWER(d(Cat.WEAPONS).box(4, 0, 4, 12, 16, 12).mass(0.6).hp(60).connect(Connect.BACK)
            .keys(k("fire", Keys.Y)).slider("fuel", 2, 30, 10).toggle("toggle", false)),
    BOMB(d(Cat.WEAPONS).box(2, 0, 2, 14, 12, 14).mass(0.6).hp(20).flammable().explosive(3.0).connect(Connect.BACK)
            .keys(k("detonate")).slider("power", 0.5f, 2, 1)),
    POWDER_BARREL(d(Cat.WEAPONS).box(2, 0, 2, 14, 16, 14).mass(1.0).hp(15).flammable().explosive(5.0)),
    BOULDER(d(Cat.WEAPONS).sphereHead(0.45).mass(3.0).hp(600).friction(0.8).restitution(0.1).joint(JointKind.RELEASE).headFrac(1.0)
            .keys(k("release", Keys.X))),
    ROCKET(d(Cat.WEAPONS).box(5, 0, 5, 11, 16, 11).mass(0.5).hp(30).flammable().connect(Connect.BACK)
            .keys(k("launch", Keys.K)).slider("thrust", 0.25f, 2, 1).slider("duration", 0.5f, 6, 2).toggle("explode", true)),
    SAW_BLADE(d(Cat.WEAPONS).disc(0.8, 0.06, 5).mass(0.8).hp(150).joint(JointKind.AXIAL).headFrac(0.7).damage(4)
            .keys(k("spin")).slider("speed", 0, 2, 1).toggle("automatic", true).toggle("toggle", false).toggle("flip", false).torque(30)),
    DRILL(d(Cat.WEAPONS).drill().mass(0.8).hp(150).joint(JointKind.AXIAL).headFrac(0.6).damage(3)
            .keys(k("spin")).slider("speed", 0, 2, 1).toggle("automatic", true).toggle("toggle", false).toggle("flip", false).torque(30)),
    HAMMER(d(Cat.WEAPONS).box(2, 0, 2, 14, 16, 14).mass(2.0).hp(300).connect(Connect.BACK).damage(2)),
    SPIKE(d(Cat.WEAPONS).box(2, 0, 2, 14, 14, 14).mass(0.4).hp(80).connect(Connect.BACK).damage(3)),
    // ---------------------------------------------------------------- flight
    PROPELLER(d(Cat.FLIGHT).box(-8, 6, 5, 24, 9, 11).mass(0.3).hp(30).flammable().connect(Connect.BACK).twist().toggle("flip", false)),
    WING(d(Cat.FLIGHT).box(0, 0, 0, 16, 2, 16).mass(0.3).hp(30).flammable().connect(Connect.BACK)),
    FLYING_BLOCK(d(Cat.FLIGHT).box(3, 0, 3, 13, 14, 13).mass(0.5).hp(50).connect(Connect.BACK)
            .keys(k("thrust", Keys.U)).slider("power", 0, 2, 1).toggle("toggle", false).toggle("automatic", false)),
    BALLOON(d(Cat.FLIGHT).sphere(0.48).mass(0.2).hp(4).flammable().connect(Connect.BACK).slider("buoyancy", 0, 2, 1)),
    BALLAST(d(Cat.FLIGHT).cube().mass(1.0).hp(200).slider("mass", 0.2f, 3, 1)),
    // ---------------------------------------------------------------- automation
    TIMER(d(Cat.AUTOMATION).box(0, 0, 0, 16, 6, 16).mass(0.2).hp(30).connect(Connect.BACK)
            .keys(k("trigger", Keys.T), k("output", Keys.KP_1)).slider("delay", 0, 10, 1).slider("duration", 0.05f, 10, 0.5f)),
    SENSOR(d(Cat.AUTOMATION).box(2, 0, 2, 14, 10, 14).mass(0.2).hp(30).connect(Connect.BACK)
            .keys(k("output", Keys.KP_2)).slider("range", 1, 16, 5).toggle("entities", true).toggle("blocks", true).toggle("invert", false)),
    ALTIMETER(d(Cat.AUTOMATION).box(0, 0, 0, 16, 6, 16).mass(0.2).hp(30).connect(Connect.BACK)
            .keys(k("output", Keys.KP_3)).slider("height", 0, 100, 10).toggle("above", true)),
    LOGIC_GATE(d(Cat.AUTOMATION).box(0, 0, 0, 16, 6, 16).mass(0.2).hp(30).connect(Connect.BACK)
            .keys(k("input_a", Keys.KP_1), k("input_b", Keys.KP_2), k("output", Keys.KP_4)).slider("mode", 0, 5, 0));

    // ==================================================================================================================

    public enum Cat { BASIC, ARMOR, LOCOMOTION, MECHANICAL, WEAPONS, FLIGHT, AUTOMATION }

    public enum Connect { ALL, BACK, BACK_FRONT }

    public enum Place { CLICKED, PLAYER_H }

    public enum JointKind { NONE, AXIAL, PERP, BALL, SLIDER, FIXED, RELEASE }

    public enum HeadShape { BOX, CYLINDER, SPHERE }

    public record KeySpec(String id, int[] defaults) {}

    public record SliderSpec(String id, float min, float max, float def) {}

    public record ToggleSpec(String id, boolean def) {}

    public final Cat cat;
    public final double mass, hp, friction, restitution, burnRate, explosive, damageMul, torque;
    public final boolean flammable, twistable;
    public final Connect connect;
    public final Place place;
    public final JointKind joint;
    public final double headFrac;
    /** Canonical pixel boxes {x0,y0,z0,x1,y1,z1}: the whole part (non-joint) or the base half (joint parts). */
    public final double[] baseBox;
    /** Head box for joint parts with a box head. */
    public final double[] headBox;
    public final HeadShape headShape;
    /** Cylinder / sphere head: radius, half height (cylinder) and center (canonical pixels). */
    public final double headRadius, headHalfHeight;
    public final double[] headCenter;
    /** Whole-part sphere (balloon). */
    public final double sphereRadius;
    public final List<KeySpec> keys;
    public final List<SliderSpec> sliders;
    public final List<ToggleSpec> toggles;

    PartType(Def d) {
        this.cat = d.cat;
        this.mass = d.mass;
        this.hp = d.hp;
        this.friction = d.friction;
        this.restitution = d.restitution;
        this.burnRate = d.burnRate;
        this.explosive = d.explosive;
        this.damageMul = d.damageMul;
        this.torque = d.torque;
        this.flammable = d.flammable;
        this.twistable = d.twist;
        this.connect = d.joint != JointKind.NONE ? (d.joint == JointKind.RELEASE ? Connect.BACK : Connect.BACK_FRONT) : d.connect;
        this.place = d.place;
        this.joint = d.joint;
        this.headFrac = d.headFrac;
        this.baseBox = d.baseBox;
        this.headBox = d.headBox;
        this.headShape = d.headShape;
        this.headRadius = d.headRadius;
        this.headHalfHeight = d.headHalfHeight;
        this.headCenter = d.headCenter;
        this.sphereRadius = d.sphereRadius;
        this.keys = List.copyOf(d.keys);
        this.sliders = List.copyOf(d.sliders);
        this.toggles = List.copyOf(d.toggles);
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public boolean isJoint() { return joint != JointKind.NONE; }

    public boolean isWheel() { return this == WHEEL || this == IRON_WHEEL || this == LARGE_TIRE || this == SMALL_WHEEL; }

    public boolean isPoweredWheel() { return this == WHEEL || this == IRON_WHEEL || this == LARGE_TIRE; }

    public boolean isMetal() {
        return this == IRON_BLOCK || this == STEEL_BLOCK || this == IRON_PLATE || this == IRON_WHEEL || this == CANNON || this == HAMMER
                || this == SPIKE || this == SAW_BLADE || this == DRILL || this == FLAMETHROWER || this == STEERING_BLOCK || this == SPINNING_BLOCK
                || this == BALLAST || this == DECOUPLER || this == GRABBER || this == FLYING_BLOCK;
    }

    public boolean isStone() { return this == STONE_BLOCK || this == BOULDER; }

    public boolean hasConfig() { return !keys.isEmpty() || !sliders.isEmpty() || !toggles.isEmpty(); }

    public static PartType byId(String id) {
        for (PartType t : values()) if (t.id().equals(id)) return t;
        return null;
    }

    public SliderSpec slider(String id) {
        for (SliderSpec s : sliders) if (s.id.equals(id)) return s;
        return null;
    }

    public ToggleSpec toggle(String id) {
        for (ToggleSpec s : toggles) if (s.id.equals(id)) return s;
        return null;
    }

    public KeySpec key(String id) {
        for (KeySpec s : keys) if (s.id.equals(id)) return s;
        return null;
    }

    // ==================================================================================================================

    private static Def d(Cat c) {
        Def d = new Def();
        d.cat = c;
        return d;
    }

    private static KeySpec k(String id, int... keys) { return new KeySpec(id, keys); }

    private static final class Def {
        Cat cat;
        double mass = 0.3, hp = 50, friction = 0.6, restitution = 0.05, burnRate = 0.15, explosive = 0, damageMul = 1, torque = 40;
        boolean flammable, twist;
        Connect connect = Connect.ALL;
        Place place = Place.CLICKED;
        JointKind joint = JointKind.NONE;
        double headFrac = 0.5;
        double[] baseBox = {0, 0, 0, 16, 16, 16};
        double[] headBox = null;
        HeadShape headShape = HeadShape.BOX;
        double headRadius, headHalfHeight;
        double[] headCenter = {8, 12, 8};
        double sphereRadius = 0;
        final List<KeySpec> keys = new ArrayList<>();
        final List<SliderSpec> sliders = new ArrayList<>();
        final List<ToggleSpec> toggles = new ArrayList<>();

        Def cube() {
            baseBox = new double[]{0, 0, 0, 16, 16, 16};
            return this;
        }

        Def box(double x0, double y0, double z0, double x1, double y1, double z1) {
            baseBox = new double[]{x0, y0, z0, x1, y1, z1};
            return this;
        }

        Def half() {
            baseBox = new double[]{0, 0, 0, 16, 8, 16};
            headBox = new double[]{0, 8, 0, 16, 16, 16};
            headShape = HeadShape.BOX;
            return this;
        }

        Def wheel(double radius, double halfWidth) {
            joint = JointKind.AXIAL;
            baseBox = new double[]{6, 0, 6, 10, 6, 10};
            headShape = HeadShape.CYLINDER;
            headRadius = radius;
            headHalfHeight = halfWidth;
            headCenter = new double[]{8, 6 + halfWidth * 16, 8};
            return this;
        }

        Def disc(double radius, double halfWidth, double y) {
            baseBox = new double[]{5, 0, 5, 11, y, 11};
            headShape = HeadShape.CYLINDER;
            headRadius = radius;
            headHalfHeight = halfWidth;
            headCenter = new double[]{8, y + halfWidth * 16, 8};
            return this;
        }

        Def drill() {
            baseBox = new double[]{3, 0, 3, 13, 5, 13};
            headShape = HeadShape.CYLINDER;
            headRadius = 0.32;
            headHalfHeight = 0.45;
            headCenter = new double[]{8, 5 + 0.45 * 16, 8};
            return this;
        }

        Def sphereHead(double r) {
            baseBox = new double[]{6, 0, 6, 10, 1, 10};
            headShape = HeadShape.SPHERE;
            headRadius = r;
            headCenter = new double[]{8, 8, 8};
            return this;
        }

        Def sphere(double r) {
            sphereRadius = r;
            baseBox = new double[]{1, 0, 1, 15, 15, 15};
            return this;
        }

        Def mass(double m) {
            mass = m;
            return this;
        }

        Def hp(double h) {
            hp = h;
            return this;
        }

        Def friction(double f) {
            friction = f;
            return this;
        }

        Def restitution(double r) {
            restitution = r;
            return this;
        }

        Def flammable() {
            flammable = true;
            return this;
        }

        Def burnRate(double r) {
            burnRate = r;
            return this;
        }

        Def explosive(double p) {
            explosive = p;
            return this;
        }

        Def damage(double m) {
            damageMul = m;
            return this;
        }

        Def torque(double t) {
            torque = t;
            return this;
        }

        Def twist() {
            twist = true;
            return this;
        }

        Def connect(Connect c) {
            connect = c;
            return this;
        }

        Def place(Place p) {
            place = p;
            return this;
        }

        Def joint(JointKind j) {
            joint = j;
            return this;
        }

        Def headFrac(double f) {
            headFrac = f;
            return this;
        }

        Def keys(KeySpec... k) {
            keys.addAll(List.of(k));
            return this;
        }

        Def slider(String id, float min, float max, float def) {
            sliders.add(new SliderSpec(id, min, max, def));
            return this;
        }

        Def toggle(String id, boolean def) {
            toggles.add(new ToggleSpec(id, def));
            return this;
        }
    }
}
