package com.reisatsu.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Locale;

/** Block state properties shared by the restaurant's blocks (names as in docs/DESIGN.md §7 / §7.1). */
public final class Props {
    private Props() {}

    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");
    public static final BooleanProperty ON = BooleanProperty.create("on");
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    public static final BooleanProperty POURING = BooleanProperty.create("pouring");
    public static final BooleanProperty RINGING = BooleanProperty.create("ringing");
    public static final BooleanProperty CRACKED = BooleanProperty.create("cracked");
    public static final BooleanProperty FALLEN = BooleanProperty.create("fallen");
    public static final BooleanProperty ALERT = BooleanProperty.create("alert");
    public static final BooleanProperty SEEN = BooleanProperty.create("seen");
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");
    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");
    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");
    /** A downlight set into the eave's fascia band (fascia sides) instead of a ceiling (ceiling-tile sides). */
    public static final BooleanProperty FASCIA = BooleanProperty.create("fascia");

    public static final IntegerProperty MESSAGE = IntegerProperty.create("message", 0, 1);
    public static final IntegerProperty NUMBER = IntegerProperty.create("number", 0, 17);
    public static final IntegerProperty GLASSES = IntegerProperty.create("glasses", 1, 4);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 3);
    public static final IntegerProperty TILE16 = IntegerProperty.create("tile", 0, 15);
    public static final IntegerProperty TILE8 = IntegerProperty.create("tile", 0, 7);
    public static final IntegerProperty SWING = IntegerProperty.create("swing", 0, 2);

    public static final EnumProperty<Side> SIDE = EnumProperty.create("side", Side.class);
    public static final EnumProperty<DoorPart> DOOR_PART = EnumProperty.create("part", DoorPart.class);
    public static final EnumProperty<LampPart> LAMP_PART = EnumProperty.create("part", LampPart.class);
    public static final EnumProperty<PolePart> POLE_PART = EnumProperty.create("part", PolePart.class);
    public static final EnumProperty<SignPart> SIGN_PART = EnumProperty.create("part", SignPart.class);
    public static final EnumProperty<Face> INNER = EnumProperty.create("inner", Face.class);
    public static final EnumProperty<Face> OUTER = EnumProperty.create("outer", Face.class);

    private interface Named extends StringRepresentable {
        @Override
        default String getSerializedName() {
            return ((Enum<?>) this).name().toLowerCase(Locale.ROOT);
        }
    }

    /** Which way an automatic door panel / swing door leaf slides or is hinged. */
    public enum Side implements Named { LEFT, RIGHT }

    /** The three blocks of a 3-high automatic door panel. */
    public enum DoorPart implements Named { BOTTOM, MIDDLE, TOP }

    /** street_lamp: the pole or the head. */
    public enum LampPart implements Named { POLE, HEAD }

    /** utility_pole: the shaft or the top (transformer and crossarm). */
    public enum PolePart implements Named { POLE, TOP }

    /** pole_sign: the pole or one tile of the 4x4 box face. */
    public enum SignPart implements Named { POLE, TILE }

    /** Textures of the two faces of a {@link TwoSidedWallBlock} (each one is an existing wall texture). */
    public enum Face implements Named {
        WALLPAPER, WALLPAPER_STAINED, WAINSCOT, WHITE_TILE, WHITE_TILE_DIRTY, STAINLESS, CONCRETE_WALL, EXTERIOR_WALL, FACADE_BRICK
    }
}
