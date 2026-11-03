package com.hitori.registry;

import com.hitori.Hitori;
import com.hitori.block.BalconyDoorBlock;
import com.hitori.block.BathtubBlock;
import com.hitori.block.Behaviours;
import com.hitori.block.HitoriDoorBlock;
import com.hitori.block.LayerBlock;
import com.hitori.block.LowBlock;
import com.hitori.block.PropBlock;
import com.hitori.block.Props;
import com.hitori.block.SlidingDoorBlock;
import com.hitori.block.TatamiBlock;
import com.hitori.block.TwoSidedWallBlock;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

/**
 * All blocks of the apartment and its street (owner: world; docs/DESIGN.md §7). Block items are registered here too.
 * World-only blocks (not in §7, models by {@code tools/gen_assets.py} from existing textures): {@code two_sided_wall},
 * {@code genkan_step}, {@code unit_door}, {@code power_wire}, {@code laundry_hanging}, {@code trash_bag}. {@code printout} has no
 * block item (hitori:printout is the game's item).
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Hitori.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Hitori.MODID);

    private ModBlocks() {}

    /** Registers a block and its block item. */
    static <T extends Block> RegistryObject<T> block(String id, Supplier<T> block) {
        RegistryObject<T> b = BLOCKS.register(id, block);
        ITEMS.register(id, () -> new BlockItem(b.get(), new Item.Properties()));
        return b;
    }

    // ------------------------------------------------------------------------------------------------ sound types
    /** Footsteps of the softer floors (the sound worker's floor types are in ModSounds; vanilla plays steps at volume × 0.15). */
    private static SoundType steps(Supplier<SoundEvent> step, SoundType base, float volume) {
        return new ForgeSoundType(volume, 1.0f, base::getBreakSound, step, base::getPlaceSound, base::getHitSound, step);
    }

    static final SoundType TATAMI = ModSounds.SOUND_TYPE_TATAMI;
    static final SoundType FLOOR = ModSounds.SOUND_TYPE_FLOOR;
    /** The cushion floor of the DK and the washroom: the flooring step, softer. */
    static final SoundType VINYL = steps(ModSounds.PLAYER_STEP_FLOOR, SoundType.WOOD, 2.6f);
    static final SoundType TILE = ModSounds.SOUND_TYPE_TILE;
    static final SoundType WET = ModSounds.SOUND_TYPE_WET;
    /** Cushions and futons muffle the tatami step. */
    static final SoundType SOFT = steps(ModSounds.PLAYER_STEP_TATAMI, SoundType.WOOL, 1.6f);

    // ------------------------------------------------------------------------------------------------ properties
    private static BlockBehaviour.Properties p(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).sound(sound).strength(1.5f, 6.0f);
    }

    /** Furniture / props: no occlusion, not a full view blocker for light. */
    private static BlockBehaviour.Properties prop(MapColor color, SoundType sound) {
        return p(color, sound).noOcclusion().isViewBlocking((s, l, pos) -> false).isSuffocating((s, l, pos) -> false)
                .isRedstoneConductor((s, l, pos) -> false).pushReaction(PushReaction.BLOCK);
    }

    private static ToIntFunction<BlockState> when(BooleanProperty prop, int level) {
        return s -> s.getValue(prop) ? level : 0;
    }

    private static RegistryObject<Block> cube(String id, MapColor color, SoundType sound) {
        return block(id, () -> new Block(p(color, sound)));
    }

    private static RegistryObject<PropBlock> prop(String id, MapColor color, SoundType sound, UnaryOperator<PropBlock.Builder> cfg) {
        return block(id, () -> cfg.apply(PropBlock.builder(id, prop(color, sound))).build());
    }

    /** A prop registered without a block item (its id is also one of the game's items). */
    private static RegistryObject<PropBlock> propNoItem(String id, MapColor color, SoundType sound, UnaryOperator<PropBlock.Builder> cfg) {
        return BLOCKS.register(id, () -> cfg.apply(PropBlock.builder(id, prop(color, sound))).build());
    }

    private static RegistryObject<PropBlock> litProp(String id, MapColor color, SoundType sound, ToIntFunction<BlockState> light,
                                                     UnaryOperator<PropBlock.Builder> cfg) {
        return block(id, () -> cfg.apply(PropBlock.builder(id, prop(color, sound).lightLevel(light))).build());
    }

    private static BlockBehaviour.Properties door(MapColor color, SoundType sound) {
        return p(color, sound).noOcclusion().strength(-1.0f, 3600000.0f).isViewBlocking((s, l, pos) -> false)
                .isSuffocating((s, l, pos) -> false).pushReaction(PushReaction.BLOCK);
    }

    /** Sliding doors: their collision depends on the neighbouring panel (no shape cache). */
    private static BlockBehaviour.Properties sliding(MapColor color, SoundType sound) {
        return door(color, sound).dynamicShape();
    }

    // ================================================================================================ surfaces
    public static final RegistryObject<TatamiBlock> TATAMI_FLOOR = block("tatami", () -> new TatamiBlock(p(MapColor.COLOR_YELLOW, TATAMI)));
    public static final RegistryObject<TatamiBlock> TATAMI_WORN = block("tatami_worn", () -> new TatamiBlock(p(MapColor.COLOR_YELLOW, TATAMI)));
    public static final RegistryObject<Block> OLD_FLOORING = cube("old_flooring", MapColor.COLOR_BROWN, FLOOR);
    public static final RegistryObject<Block> VINYL_FLOOR = cube("vinyl_floor", MapColor.SAND, VINYL);
    public static final RegistryObject<Block> GENKAN_TILE = cube("genkan_tile", MapColor.COLOR_GRAY, TILE);
    public static final RegistryObject<Block> BATH_TILE_FLOOR = cube("bath_tile_floor", MapColor.COLOR_LIGHT_BLUE, TILE);
    public static final RegistryObject<Block> BATH_TILE_WALL = cube("bath_tile_wall", MapColor.COLOR_LIGHT_BLUE, TILE);
    public static final RegistryObject<Block> PLASTER_WALL = cube("plaster_wall", MapColor.SAND, SoundType.STONE);
    public static final RegistryObject<Block> PLASTER_WALL_STAINED = cube("plaster_wall_stained", MapColor.SAND, SoundType.STONE);
    public static final RegistryObject<Block> WALLPAPER_OLD = cube("wallpaper_old", MapColor.SAND, SoundType.WOOD);
    public static final RegistryObject<Block> WOOD_WAINSCOT = cube("wood_wainscot", MapColor.COLOR_BROWN, SoundType.WOOD);
    public static final RegistryObject<Block> CEILING_BOARD = cube("ceiling_board", MapColor.WOOD, SoundType.WOOD);
    public static final RegistryObject<Block> CEILING_BOARD_STAINED = cube("ceiling_board_stained", MapColor.WOOD, SoundType.WOOD);
    public static final RegistryObject<Block> OSHIIRE_FLOOR = cube("oshiire_floor", MapColor.WOOD, FLOOR);
    public static final RegistryObject<Block> APARTMENT_SIDING = cube("apartment_siding", MapColor.COLOR_GRAY, SoundType.METAL);
    public static final RegistryObject<Block> APARTMENT_DOOR_METAL = cube("apartment_door_metal", MapColor.COLOR_GRAY, SoundType.METAL);
    public static final RegistryObject<Block> CORRIDOR_CONCRETE = cube("corridor_concrete", MapColor.STONE, WET);
    public static final RegistryObject<Block> CONCRETE = cube("concrete", MapColor.STONE, WET);
    public static final RegistryObject<Block> ASPHALT_WET = cube("asphalt_wet", MapColor.COLOR_BLACK, WET);
    public static final RegistryObject<Block> BLOCK_WALL = cube("block_wall", MapColor.STONE, SoundType.STONE);
    public static final RegistryObject<Block> ROOF_SLATE = cube("roof_slate", MapColor.COLOR_GRAY, SoundType.STONE);
    public static final RegistryObject<StairBlock> ROOF_SLATE_STAIRS = block("roof_slate_stairs",
            () -> new StairBlock(() -> ROOF_SLATE.get().defaultBlockState(), p(MapColor.COLOR_GRAY, SoundType.STONE)));
    public static final RegistryObject<SlabBlock> ROOF_SLATE_SLAB = block("roof_slate_slab", () -> new SlabBlock(p(MapColor.COLOR_GRAY, SoundType.STONE)));
    /** World-only: a one-block wall between two different rooms (textures of the existing wall blocks). */
    public static final RegistryObject<TwoSidedWallBlock> TWO_SIDED_WALL = block("two_sided_wall",
            () -> new TwoSidedWallBlock(p(MapColor.SAND, SoundType.STONE)));
    /** World-only: the genkan's wooden half step (上がり框) between the tiles and the hall's flooring. */
    public static final RegistryObject<LowBlock> GENKAN_STEP = block("genkan_step", () -> new LowBlock(p(MapColor.COLOR_BROWN, FLOOR), 8));
    public static final RegistryObject<LayerBlock> PUDDLE = block("puddle", () -> new LayerBlock(p(MapColor.WATER, WET).noOcclusion()
            .noCollission().strength(0.2f).replaceable(), 1));
    public static final RegistryObject<PropBlock> RAIN_WINDOW = prop("rain_window", MapColor.NONE, SoundType.GLASS,
            b -> b.seeThrough());

    // ================================================================================================ doors
    public static final RegistryObject<HitoriDoorBlock> FRONT_DOOR = block("front_door",
            () -> new HitoriDoorBlock(door(MapColor.COLOR_GRAY, SoundType.METAL), BlockSetType.IRON, HitoriDoorBlock.Kind.FRONT));
    public static final RegistryObject<HitoriDoorBlock> INTERIOR_DOOR = block("interior_door",
            () -> new HitoriDoorBlock(door(MapColor.WOOD, SoundType.WOOD), BlockSetType.OAK, HitoriDoorBlock.Kind.INTERIOR));
    public static final RegistryObject<HitoriDoorBlock> TOILET_DOOR = block("toilet_door",
            () -> new HitoriDoorBlock(door(MapColor.WOOD, SoundType.WOOD), BlockSetType.OAK, HitoriDoorBlock.Kind.TOILET));
    public static final RegistryObject<HitoriDoorBlock> BATH_DOOR = block("bath_door",
            () -> new HitoriDoorBlock(door(MapColor.NONE, SoundType.GLASS), BlockSetType.OAK, HitoriDoorBlock.Kind.BATH));
    /** World-only: the locked steel doors of the other units (texture apartment_door_metal). */
    public static final RegistryObject<HitoriDoorBlock> UNIT_DOOR = block("unit_door",
            () -> new HitoriDoorBlock(door(MapColor.COLOR_GRAY, SoundType.METAL), BlockSetType.IRON, HitoriDoorBlock.Kind.UNIT));
    public static final RegistryObject<SlidingDoorBlock> FUSUMA = block("fusuma",
            () -> new SlidingDoorBlock(sliding(MapColor.SAND, SoundType.WOOD), SlidingDoorBlock.Kind.ROOM));
    public static final RegistryObject<SlidingDoorBlock> OSHIIRE_FUSUMA = block("oshiire_fusuma",
            () -> new SlidingDoorBlock(sliding(MapColor.SAND, SoundType.WOOD), SlidingDoorBlock.Kind.CLOSET));
    public static final RegistryObject<BalconyDoorBlock> BALCONY_DOOR = block("balcony_door",
            () -> new BalconyDoorBlock(sliding(MapColor.NONE, SoundType.GLASS)));

    // ================================================================================================ the tatami room
    /** The ritual table: low (6 px) but not to be walked over (10 px collision). */
    public static final RegistryObject<PropBlock> CHABUDAI = prop("chabudai", MapColor.COLOR_BROWN, SoundType.WOOD,
            b -> b.box(1, 0, 1, 15, 6, 15).collision(1, 0, 1, 15, 10, 15).use(Behaviours.tell("table", null, 0)));
    public static final RegistryObject<PropBlock> ZABUTON = prop("zabuton", MapColor.COLOR_RED, SOFT,
            b -> b.box(1, 0, 1, 15, 2, 15).noCollision().seeThrough());
    /** The CRT sits on the 6 px stand: its model reaches 10 px down into the stand's block. */
    public static final RegistryObject<PropBlock> CRT_TV = litProp("crt_tv", MapColor.COLOR_BLACK, SoundType.METAL, when(Props.ON, 8),
            b -> b.box(1, 0, 2, 15, 6, 14).props(Props.ON, Props.CHANNEL).use(Behaviours::tv).animate(Behaviours::tvStatic));
    /** The stand's outline covers the TV's lower body too (clicking it uses the TV). */
    public static final RegistryObject<PropBlock> TV_STAND = prop("tv_stand", MapColor.COLOR_BROWN, SoundType.WOOD,
            b -> b.box(0, 0, 2, 16, 16, 14).use(Behaviours::tvStand));
    public static final RegistryObject<PropBlock> HANGING_LAMP = litProp("hanging_lamp", MapColor.COLOR_ORANGE, SoundType.GLASS, when(Props.LIT, 13),
            b -> b.box(3, 6, 3, 13, 16, 13).props(Props.LIT, Props.SWING).noCollision().seeThrough().use(Behaviours::pullSwitch));
    public static final RegistryObject<PropBlock> FLUORESCENT_RING = litProp("fluorescent_ring", MapColor.QUARTZ, SoundType.GLASS, when(Props.LIT, 13),
            b -> b.box(2, 13, 2, 14, 16, 14).props(Props.LIT).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> PULL_SWITCH = prop("pull_switch", MapColor.QUARTZ, SoundType.WOOL,
            b -> b.box(6.5, 3, 6.5, 9.5, 16, 9.5).noCollision().seeThrough().use(Behaviours::pullSwitch));
    public static final RegistryObject<PropBlock> LIGHT_SWITCH = prop("light_switch", MapColor.QUARTZ, SoundType.STONE,
            b -> b.box(5, 3, 15, 11, 11, 16).props(Props.ON).noCollision().seeThrough().use(Behaviours::lightSwitch));
    public static final RegistryObject<PropBlock> CURTAIN = prop("curtain", MapColor.COLOR_GRAY, SoundType.WOOL,
            b -> b.box(0, 0, 14, 16, 16, 16).props(Props.CLOSED).dampen(Props.CLOSED, 3).noCollision().use(Behaviours::curtain));
    /** shelf = true: lying on the closet's middle shelf (the blockstate adds the shelf board under it). */
    public static final RegistryObject<PropBlock> FUTON_FOLDED = prop("futon_folded", MapColor.WOOL, SOFT,
            b -> b.box(1, 0, 1, 15, 8, 15).props(Props.SHELF));
    public static final RegistryObject<PropBlock> CARDBOARD_BOX = prop("cardboard_box", MapColor.WOOD, SoundType.WOOL,
            b -> b.box(2, 0, 2, 14, 10, 14).props(Props.SHELF));
    public static final RegistryObject<PropBlock> ALTAR_SHELF = prop("altar_shelf", MapColor.COLOR_BROWN, SoundType.WOOD,
            b -> b.box(2, 0, 5, 14, 14, 16).use(Behaviours.tell("altar_shelf", null, 0)));

    // ================================================================================================ bath and washroom
    public static final RegistryObject<BathtubBlock> BATHTUB = block("bathtub", () -> new BathtubBlock(prop(MapColor.COLOR_LIGHT_BLUE, TILE)));
    public static final RegistryObject<PropBlock> BATH_TAP = prop("bath_tap", MapColor.METAL, SoundType.METAL,
            b -> b.box(5, 4, 11, 11, 12, 16).props(Props.RUNNING).noCollision().seeThrough().use(Behaviours::bathTap).animate(Behaviours::tapWater));
    public static final RegistryObject<PropBlock> SHOWER = prop("shower", MapColor.METAL, SoundType.METAL,
            b -> b.box(5, 0, 12, 11, 16, 16).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> BATH_STOOL = prop("bath_stool", MapColor.COLOR_LIGHT_BLUE, SoundType.STONE,
            b -> b.box(4, 0, 4, 12, 6, 12));
    public static final RegistryObject<PropBlock> BUCKET = prop("bucket", MapColor.COLOR_YELLOW, SoundType.STONE,
            b -> b.box(4, 0, 4, 12, 6, 12).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> WASHING_MACHINE = prop("washing_machine", MapColor.QUARTZ, SoundType.METAL,
            b -> b.box(1, 0, 1, 15, 16, 15).props(Props.RUNNING).use(Behaviours::washingMachine).animate(Behaviours::washing));
    public static final RegistryObject<PropBlock> WASHBASIN = prop("washbasin", MapColor.QUARTZ, TILE,
            b -> b.box(1, 0, 2, 15, 16, 16).props(Props.RUNNING).use(Behaviours::washbasin).animate(Behaviours::tapWater));
    /** The washroom's mirror cabinet holds the nail clipper (the hall's mirror is just a mirror). */
    public static final RegistryObject<PropBlock> MIRROR_SMALL = prop("mirror_small", MapColor.QUARTZ, SoundType.GLASS,
            b -> b.box(2, 2, 15, 14, 14, 16).noCollision().seeThrough().use(Behaviours::mirror));
    public static final RegistryObject<PropBlock> TOILET_SEAT = prop("toilet_seat", MapColor.QUARTZ, TILE,
            b -> b.box(3, 0, 1, 13, 14, 16).collision(3, 0, 1, 13, 8, 16).collision(3, 0, 12, 13, 14, 16).use(Behaviours.tell("toilet", null, 4)));

    // ================================================================================================ the DK
    public static final RegistryObject<PropBlock> KITCHEN_SINK = prop("kitchen_sink", MapColor.METAL, SoundType.METAL,
            b -> b.box(0, 0, 0, 16, 14, 16).props(Props.RUNNING).use(Behaviours::kitchenSink).animate(Behaviours::tapWater));
    public static final RegistryObject<PropBlock> GAS_STOVE = litProp("gas_stove", MapColor.METAL, SoundType.METAL, when(Props.LIT, 6),
            b -> b.box(0, 0, 0, 16, 16, 16).props(Props.LIT).use(Behaviours::gasStove).animate(Behaviours::gasFlames));
    /** on = powered (the hum; internal, same models). */
    public static final RegistryObject<PropBlock> FRIDGE_OLD = prop("fridge_old", MapColor.QUARTZ, SoundType.METAL,
            b -> b.box(1, 0, 1, 15, 16, 15).props(Props.OPEN, Props.ON).use(Behaviours::fridge));
    public static final RegistryObject<PropBlock> RICE_BAG = prop("rice_bag", MapColor.QUARTZ, SoundType.WOOL,
            b -> b.box(3, 0, 3, 13, 10, 13).use(Behaviours.tell("rice", ModSounds.RITUAL_RICE, 0)));
    public static final RegistryObject<PropBlock> RICE_COOKER = prop("rice_cooker", MapColor.QUARTZ, SoundType.METAL,
            b -> b.box(4, 0, 4, 12, 8, 12).seeThrough().use(Behaviours.tell("rice_cooker", null, 0)));
    public static final RegistryObject<PropBlock> SALT_SHAKER = prop("salt_shaker", MapColor.QUARTZ, SoundType.GLASS,
            b -> b.box(6, 0, 6, 10, 6, 10).noCollision().seeThrough().use(Behaviours.tell("salt", null, 0)));
    public static final RegistryObject<PropBlock> CUP_GLASS = prop("cup_glass", MapColor.NONE, SoundType.GLASS,
            b -> b.box(6, 0, 6, 10, 5, 10).noCollision().seeThrough().use(Behaviours.tell("cup", ModSounds.ITEM_CUP, 0)));
    public static final RegistryObject<PropBlock> KNIFE_RACK = prop("knife_rack", MapColor.METAL, SoundType.METAL,
            b -> b.box(2, 4, 14, 14, 12, 16).props(Props.HAS_KNIFE).noCollision().seeThrough().use(Behaviours.tell("knife_rack", null, 0)));
    public static final RegistryObject<PropBlock> MICROWAVE = prop("microwave", MapColor.QUARTZ, SoundType.METAL,
            b -> b.box(2, 0, 3, 14, 9, 14).use(Behaviours.tell("microwave", null, 0)));
    public static final RegistryObject<PropBlock> DINING_TABLE = prop("dining_table", MapColor.COLOR_BROWN, SoundType.WOOD,
            b -> b.box(0, 0, 0, 16, 12, 16).collision(0, 10.5, 0, 16, 12, 16).collision(6.5, 0, 6.5, 9.5, 10.5, 9.5));
    public static final RegistryObject<PropBlock> DINING_CHAIR = prop("dining_chair", MapColor.COLOR_BROWN, SoundType.WOOD,
            b -> b.box(3, 0, 3, 13, 15, 13).collision(3, 0, 3, 13, 9, 13).collision(3, 9, 11.5, 13, 15, 13));
    /** A kitchen cabinet with a drawer (top at 14 px); the one by the stove holds the lighter. */
    public static final RegistryObject<PropBlock> DRAWER = prop("drawer", MapColor.COLOR_BROWN, SoundType.WOOD,
            b -> b.box(0, 0, 1, 16, 14, 16).props(Props.OPEN).use(Behaviours::drawer));

    // ================================================================================================ the western room
    /** Desk top at 12 px: the laptop, the sewing box, the printout and the lamp reach 4 px down into it. */
    public static final RegistryObject<PropBlock> DESK = prop("desk", MapColor.COLOR_BROWN, SoundType.WOOD,
            b -> b.box(0, 0, 0, 16, 12, 16));
    public static final RegistryObject<PropBlock> DESK_CHAIR = prop("desk_chair", MapColor.COLOR_GRAY, SoundType.WOOL,
            b -> b.box(3, 0, 3, 13, 16, 13).collision(3, 0, 3, 13, 9, 13));
    public static final RegistryObject<PropBlock> LAPTOP_BLOCK = litProp("laptop_block", MapColor.COLOR_GRAY, SoundType.METAL, when(Props.ON, 4),
            b -> b.box(3, 0, 4, 13, 2, 12).altBox(Props.OPEN, 3, 0, 4, 13, 8, 12).props(Props.ON).noCollision().seeThrough()
                    .use(Behaviours.tell("laptop", null, 0)));
    public static final RegistryObject<PropBlock> SEWING_BOX = prop("sewing_box", MapColor.COLOR_RED, SoundType.WOOD,
            b -> b.box(3, 0, 4, 13, 5, 12).props(Props.OPEN).noCollision().seeThrough().use(Behaviours::sewingBox));
    /** The printout lying on the desk; no block item: hitori:printout is the game's item (the paper in your hand). */
    public static final RegistryObject<PropBlock> PRINTOUT = propNoItem("printout", MapColor.QUARTZ, SoundType.WOOL,
            b -> b.box(3, 0, 3, 13, 1, 13).noCollision().seeThrough().use(Behaviours::printout));
    public static final RegistryObject<PropBlock> BED = prop("bed", MapColor.COLOR_LIGHT_BLUE, SoundType.WOOL,
            b -> b.box(0, 0, 0, 16, 9, 16).props(Props.PART));
    /** A low shelf (top at 16 px; the plush sits on it). */
    public static final RegistryObject<PropBlock> SHELF = prop("shelf", MapColor.COLOR_BROWN, SoundType.WOOD,
            b -> b.box(0, 0, 4, 16, 16, 16));
    public static final RegistryObject<PropBlock> PLUSH_DOLL = prop("plush_doll", MapColor.WOOL, SoundType.WOOL,
            b -> b.box(4, 0, 4, 12, 9, 12).props(Props.PRESENT).noCollision().seeThrough().use(Behaviours::plush));
    public static final RegistryObject<PropBlock> CAMERA_BAG = prop("camera_bag", MapColor.COLOR_BLACK, SoundType.WOOL,
            b -> b.box(3, 0, 4, 13, 8, 12).use(Behaviours.tell("camera_bag", null, 0)));
    public static final RegistryObject<PropBlock> POSTER = prop("poster", MapColor.QUARTZ, SoundType.WOOL,
            b -> b.box(1, 1, 15, 15, 15, 16).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> DESK_LAMP = litProp("desk_lamp", MapColor.COLOR_GRAY, SoundType.METAL, when(Props.LIT, 10),
            b -> b.box(4, 0, 4, 12, 12, 12).props(Props.LIT).noCollision().seeThrough().use(Behaviours::deskLamp));

    // ================================================================================================ the genkan
    public static final RegistryObject<PropBlock> SHOE_CABINET = prop("shoe_cabinet", MapColor.COLOR_BROWN, SoundType.WOOD,
            b -> b.box(0, 0, 3, 16, 14, 16));
    public static final RegistryObject<PropBlock> SHOES = prop("shoes", MapColor.COLOR_BLACK, SoundType.WOOL,
            b -> b.box(2, 0, 2, 14, 3, 14).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> UMBRELLA = prop("umbrella", MapColor.COLOR_BLACK, SoundType.WOOL,
            b -> b.box(5, 0, 11, 11, 16, 16).noCollision().seeThrough());
    /** lit = the breaker is on (the indicator; internal, same models). */
    public static final RegistryObject<PropBlock> BREAKER_PANEL = prop("breaker_panel", MapColor.QUARTZ, SoundType.METAL,
            b -> b.box(2, 2, 13, 14, 14, 16).props(Props.OPEN, Props.LIT).noCollision().use(Behaviours::breaker));
    public static final RegistryObject<PropBlock> INTERCOM = litProp("intercom", MapColor.QUARTZ, SoundType.STONE, when(Props.RINGING, 3),
            b -> b.box(5, 3, 14, 11, 13, 16).props(Props.RINGING).noCollision().seeThrough().use(Behaviours::intercom));
    public static final RegistryObject<PropBlock> CALENDAR = prop("calendar", MapColor.QUARTZ, SoundType.WOOL,
            b -> b.box(3, 1, 15, 13, 15, 16).noCollision().seeThrough().use(Behaviours.tell("calendar", null, 0)));
    public static final RegistryObject<PropBlock> WALL_CLOCK = prop("wall_clock", MapColor.QUARTZ, SoundType.WOOD,
            b -> b.box(3, 3, 15, 13, 13, 16).noCollision().seeThrough().use(Behaviours.tell("wall_clock", null, 0)));

    // ================================================================================================ outside
    public static final RegistryObject<PropBlock> LAUNDRY_POLE = prop("laundry_pole", MapColor.METAL, SoundType.METAL,
            b -> b.box(0, 11, 6, 16, 16, 10).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> AC_OUTDOOR_UNIT = prop("ac_outdoor_unit", MapColor.QUARTZ, SoundType.METAL,
            b -> b.box(1, 0, 3, 15, 12, 15));
    /** 28 px tall: the block above is kept clear. */
    public static final RegistryObject<PropBlock> VENDING_MACHINE_SMALL = litProp("vending_machine_small", MapColor.QUARTZ, SoundType.METAL, s -> 11,
            b -> b.box(1, 0, 2, 15, 16, 16).collision(1, 0, 2, 15, 24, 16));
    /** Mounted on the north side of a pole block (at facing north), facing away from it. */
    public static final RegistryObject<PropBlock> STREET_LAMP_OLD = litProp("street_lamp_old", MapColor.METAL, SoundType.METAL, when(Props.LIT, 12),
            b -> b.box(4, 2, 0, 12, 10, 16).props(Props.LIT).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> UTILITY_POLE = prop("utility_pole", MapColor.STONE, SoundType.STONE,
            b -> b.box(5, 0, 5, 11, 16, 11).props(Props.TOP));
    public static final RegistryObject<PropBlock> MAILBOXES = prop("mailboxes", MapColor.COLOR_GRAY, SoundType.METAL,
            b -> b.box(0, 0, 8, 16, 14, 16).use(Behaviours.tell("mailboxes", null, 2)));
    public static final RegistryObject<PropBlock> BICYCLE = prop("bicycle", MapColor.COLOR_GRAY, SoundType.METAL,
            b -> b.box(1, 0, 6, 15, 14, 10));
    public static final RegistryObject<PropBlock> STAIRS_RAIL = prop("stairs_rail", MapColor.METAL, SoundType.METAL,
            b -> b.box(0, 0, 7, 16, 16, 9).collision(0, 0, 7, 16, 24, 9));
    public static final RegistryObject<PropBlock> NAMEPLATE = prop("nameplate", MapColor.QUARTZ, SoundType.METAL,
            b -> b.box(4, 6, 15, 12, 10, 16).props(Props.UNIT).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> VACANCY_SIGN = prop("vacancy_sign", MapColor.QUARTZ, SoundType.WOOL,
            b -> b.box(1, 2, 15, 15, 14, 16).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> SWING_SET = prop("swing_set", MapColor.METAL, SoundType.METAL,
            b -> b.box(0, 0, 0, 16, 16, 16).collision(0, 0, 6, 2, 24, 10).collision(14, 0, 6, 16, 24, 10));
    /** World-only (model from vanilla cloth textures): a towel and a shirt drying on a hanger (部屋干し, and on the balcony). */
    public static final RegistryObject<PropBlock> LAUNDRY_HANGING = prop("laundry_hanging", MapColor.WOOL, SoundType.WOOL,
            b -> b.box(1, 2, 6, 15, 16, 10).noCollision().seeThrough());
    /** World-only (model from vanilla textures): a tied plastic rubbish bag. */
    public static final RegistryObject<PropBlock> TRASH_BAG = prop("trash_bag", MapColor.SNOW, SoundType.WOOL,
            b -> b.box(3, 0, 3, 13, 10, 13).collision(3, 0, 3, 13, 6, 13).use(Behaviours.tell("trash_bag", null, 3)));
    /** World-only: a span of the wires between two utility poles. */
    public static final RegistryObject<PropBlock> POWER_WIRE = prop("power_wire", MapColor.COLOR_BLACK, SoundType.METAL,
            b -> b.box(0, 10, 0, 16, 14, 16).noCollision().seeThrough());

    // ------------------------------------------------------------------------------------------------ queries
    /**
     * What kind of floor a block is for footsteps and noise (docs §3.3): "tatami", "floor" (flooring, vinyl,
     * plywood), "tile" (tiles, concrete, the genkan), "wet" (puddles, wet asphalt), or null.
     */
    public static String floorKind(BlockState s) {
        Block b = s.getBlock();
        if (b == TATAMI_FLOOR.get() || b == TATAMI_WORN.get() || b == ZABUTON.get() || b == FUTON_FOLDED.get()) return "tatami";
        if (b == OLD_FLOORING.get() || b == VINYL_FLOOR.get() || b == OSHIIRE_FLOOR.get() || b == GENKAN_STEP.get()) return "floor";
        if (b == GENKAN_TILE.get() || b == BATH_TILE_FLOOR.get() || b == CONCRETE.get() || b == CORRIDOR_CONCRETE.get() || b == BLOCK_WALL.get()) return "tile";
        if (b == PUDDLE.get() || b == ASPHALT_WET.get()) return "wet";
        return null;
    }
}
