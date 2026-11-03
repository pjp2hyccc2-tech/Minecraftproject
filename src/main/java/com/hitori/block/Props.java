package com.hitori.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Locale;

/** Block state properties shared by the apartment's blocks (names as in docs/DESIGN.md §7). */
public final class Props {
    private Props() {}

    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final BooleanProperty ON = BooleanProperty.create("on");
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    public static final BooleanProperty RINGING = BooleanProperty.create("ringing");
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");
    public static final BooleanProperty PRESENT = BooleanProperty.create("present");
    public static final BooleanProperty HAS_KNIFE = BooleanProperty.create("has_knife");
    public static final BooleanProperty LID = BooleanProperty.create("lid");
    /** The hanging lamp swinging (the art's tilted model; the ticker flips its facing to swing it back and forth). */
    public static final BooleanProperty SWING = BooleanProperty.create("swing");
    /** utility_pole: the top segment (crossarm). */
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    /** World-only: a futon / box lying on the closet's middle shelf (the blockstate adds the shelf board under it). */
    public static final BooleanProperty SHELF = BooleanProperty.create("shelf");
    /** Locked doors of the other units (and the front door's chain is drawn by its texture). */
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    /** Sliding doors: 0 shut, 1 a crack (4 px), 2 open. */
    public static final IntegerProperty OPEN3 = IntegerProperty.create("open", 0, 2);
    /** bathtub: 0 empty, 1 half, 2 full. */
    public static final IntegerProperty WATER = IntegerProperty.create("water", 0, 2);
    /** crt_tv: 0 static (砂嵐), 1 a dark room, 2 a face. */
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, 2);
    /** nameplate: 0..3 = 101..104, 4..7 = 201..204. */
    public static final IntegerProperty UNIT = IntegerProperty.create("unit", 0, 7);

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final EnumProperty<Face> INNER = EnumProperty.create("inner", Face.class);
    public static final EnumProperty<Face> OUTER = EnumProperty.create("outer", Face.class);

    private interface Named extends StringRepresentable {
        @Override
        default String getSerializedName() {
            return ((Enum<?>) this).name().toLowerCase(Locale.ROOT);
        }
    }

    /** bed / bathtub: two blocks like a vanilla bed (the head lies in the facing direction from the foot). */
    public enum Part implements Named { HEAD, FOOT }

    /** Textures of the two faces of a {@link TwoSidedWallBlock} (each one is an existing wall texture). */
    public enum Face implements Named {
        PLASTER_WALL, PLASTER_WALL_STAINED, WALLPAPER_OLD, WOOD_WAINSCOT, BATH_TILE_WALL, OSHIIRE_FLOOR, APARTMENT_SIDING, CONCRETE;

        /** The texture (= cube block id) of this face. */
        public String texture() {
            return getSerializedName();
        }
    }
}
