package com.reisatsu.registry;

import com.reisatsu.Reisatsu;
import com.reisatsu.block.AutoDoorBlock;
import com.reisatsu.block.Behaviours;
import com.reisatsu.block.LayerBlock;
import com.reisatsu.block.LightBlock;
import com.reisatsu.block.LineBlock;
import com.reisatsu.block.LowBlock;
import com.reisatsu.block.PropBlock;
import com.reisatsu.block.Props;
import com.reisatsu.block.RestaurantDoorBlock;
import com.reisatsu.block.ShopWindowBlock;
import com.reisatsu.block.SwingDoorBlock;
import com.reisatsu.block.TwoSidedWallBlock;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** All blocks of the restaurant and its surroundings (owner: world). Block items are registered here too. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Reisatsu.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Reisatsu.MODID);

    private ModBlocks() {}

    /** Registers a block and its block item. */
    static <T extends Block> RegistryObject<T> block(String id, Supplier<T> block) {
        RegistryObject<T> b = BLOCKS.register(id, block);
        ITEMS.register(id, () -> new BlockItem(b.get(), new Item.Properties()));
        return b;
    }

    // ------------------------------------------------------------------------------------------------ sound types
    private static SoundType steps(float volume, Supplier<SoundEvent> step, SoundType base) {
        return new ForgeSoundType(volume, 1.0f, base::getBreakSound, step, base::getPlaceSound, base::getHitSound, base::getFallSound);
    }

    // the floor types are the sound worker's (ModSounds, step volume 4.0); mats muffle the vinyl step to 40 %
    static final SoundType VINYL = ModSounds.SOUND_TYPE_VINYL;
    static final SoundType SOFT = steps(1.6f, ModSounds.PLAYER_STEP_VINYL, SoundType.WOOL);
    static final SoundType TILE = ModSounds.SOUND_TYPE_TILE;
    static final SoundType KITCHEN = ModSounds.SOUND_TYPE_KITCHEN;
    static final SoundType WET = ModSounds.SOUND_TYPE_WET;
    static final SoundType SHARDS = ModSounds.SOUND_TYPE_GLASS;
    static final SoundType PAPER = ModSounds.SOUND_TYPE_PAPER;

    // ------------------------------------------------------------------------------------------------ properties
    private static BlockBehaviour.Properties p(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).sound(sound).strength(1.5f, 6.0f);
    }

    /** Furniture / props: no occlusion, not a full view blocker for light. */
    private static BlockBehaviour.Properties prop(MapColor color, SoundType sound) {
        return p(color, sound).noOcclusion().isViewBlocking((s, l, pos) -> false).isSuffocating((s, l, pos) -> false)
                .isRedstoneConductor((s, l, pos) -> false).pushReaction(PushReaction.BLOCK);
    }

    private static BlockBehaviour.Properties lit(BlockBehaviour.Properties props, ToIntFunction<BlockState> light) {
        return props.lightLevel(light);
    }

    private static ToIntFunction<BlockState> when(net.minecraft.world.level.block.state.properties.BooleanProperty prop, int level) {
        return s -> s.getValue(prop) ? level : 0;
    }

    private static RegistryObject<Block> cube(String id, MapColor color, SoundType sound) {
        return block(id, () -> new Block(p(color, sound)));
    }

    private static RegistryObject<PropBlock> prop(String id, MapColor color, SoundType sound, java.util.function.UnaryOperator<PropBlock.Builder> cfg) {
        return block(id, () -> cfg.apply(PropBlock.builder(id, prop(color, sound))).build());
    }

    private static RegistryObject<PropBlock> litProp(String id, MapColor color, SoundType sound, ToIntFunction<BlockState> light,
                                                     java.util.function.UnaryOperator<PropBlock.Builder> cfg) {
        return block(id, () -> cfg.apply(PropBlock.builder(id, lit(prop(color, sound), light))).build());
    }

    // ================================================================================================ surfaces
    public static final RegistryObject<Block> DINING_FLOOR = cube("dining_floor", MapColor.COLOR_BROWN, VINYL);
    /** The staff room's and the office's floor: the dining floor, worn through. */
    public static final RegistryObject<Block> DINING_FLOOR_WORN = cube("dining_floor_worn", MapColor.COLOR_BROWN, VINYL);
    public static final RegistryObject<Block> KITCHEN_FLOOR = cube("kitchen_floor", MapColor.TERRACOTTA_RED, KITCHEN);
    public static final RegistryObject<Block> RESTROOM_FLOOR = cube("restroom_floor", MapColor.QUARTZ, TILE);
    public static final RegistryObject<Block> RESTROOM_FLOOR_DARK = cube("restroom_floor_dark", MapColor.COLOR_GRAY, TILE);
    public static final RegistryObject<Block> VESTIBULE_FLOOR = cube("vestibule_floor", MapColor.COLOR_GRAY, TILE);
    public static final RegistryObject<PropBlock> ENTRANCE_MAT = prop("entrance_mat", MapColor.COLOR_BLACK, SOFT,
            b -> b.box(0, 0, 0, 16, 1, 16).noCollision().seeThrough());
    public static final RegistryObject<Block> WALLPAPER = cube("wallpaper", MapColor.SAND, SoundType.WOOD);
    public static final RegistryObject<Block> WALLPAPER_STAINED = cube("wallpaper_stained", MapColor.SAND, SoundType.WOOD);
    public static final RegistryObject<Block> WAINSCOT = cube("wainscot", MapColor.COLOR_BROWN, SoundType.WOOD);
    public static final RegistryObject<Block> WHITE_TILE = cube("white_tile", MapColor.QUARTZ, TILE);
    public static final RegistryObject<Block> WHITE_TILE_DIRTY = cube("white_tile_dirty", MapColor.QUARTZ, TILE);
    public static final RegistryObject<Block> CEILING_TILE = cube("ceiling_tile", MapColor.SAND, SoundType.WOOL);
    public static final RegistryObject<Block> CEILING_TILE_STAINED = cube("ceiling_tile_stained", MapColor.SAND, SoundType.WOOL);
    public static final RegistryObject<LightBlock> CEILING_LIGHT = block("ceiling_light",
            () -> new LightBlock.Panel(lit(p(MapColor.QUARTZ, SoundType.GLASS), s -> LightBlock.level(s, 13))));
    public static final RegistryObject<LightBlock> DOWNLIGHT = block("downlight",
            () -> new LightBlock.Down(lit(p(MapColor.QUARTZ, SoundType.GLASS), s -> LightBlock.level(s, s.getValue(Props.FASCIA) ? 13 : 11))));
    public static final RegistryObject<LightBlock> PENDANT_LAMP = block("pendant_lamp",
            () -> new LightBlock(lit(prop(MapColor.COLOR_ORANGE, SoundType.GLASS).noCollission(), s -> LightBlock.level(s, 12)), false,
                    Block.box(4, 6, 4, 12, 16, 12)));
    public static final RegistryObject<PropBlock> EXIT_SIGN = litProp("exit_sign", MapColor.COLOR_GREEN, SoundType.GLASS, s -> 7,
            b -> b.box(2, 8, 14, 14, 14, 16).altBox(Props.HANGING, 2, 6, 7, 14, 16, 9).noCollision().seeThrough());
    public static final RegistryObject<Block> EXTERIOR_WALL = cube("exterior_wall", MapColor.SAND, SoundType.STONE);
    public static final RegistryObject<Block> FACADE_BRICK = cube("facade_brick", MapColor.TERRACOTTA_BROWN, SoundType.STONE);
    public static final RegistryObject<LowBlock> BRICK_SILL = block("brick_sill", () -> new LowBlock(p(MapColor.TERRACOTTA_BROWN, SoundType.STONE), 10));
    public static final RegistryObject<Block> ROOF_TILE = cube("roof_tile", MapColor.TERRACOTTA_ORANGE, SoundType.STONE);
    public static final RegistryObject<StairBlock> ROOF_TILE_STAIRS = block("roof_tile_stairs",
            () -> new StairBlock(() -> ROOF_TILE.get().defaultBlockState(), p(MapColor.TERRACOTTA_ORANGE, SoundType.STONE)));
    public static final RegistryObject<SlabBlock> ROOF_TILE_SLAB = block("roof_tile_slab", () -> new SlabBlock(p(MapColor.TERRACOTTA_ORANGE, SoundType.STONE)));
    public static final RegistryObject<Block> FASCIA = cube("fascia", MapColor.COLOR_ORANGE, SoundType.STONE);
    public static final RegistryObject<ShopWindowBlock> SHOP_WINDOW = block("shop_window",
            () -> new ShopWindowBlock(p(MapColor.NONE, SoundType.GLASS).noOcclusion().isViewBlocking((s, l, pos) -> false)
                    .isSuffocating((s, l, pos) -> false).isRedstoneConductor((s, l, pos) -> false)));
    public static final RegistryObject<PropBlock> WINDOW_BLIND = prop("window_blind", MapColor.SAND, SoundType.WOOL,
            b -> b.box(0, 0, 14, 16, 16, 16).props(Props.CLOSED).noCollision());
    public static final RegistryObject<Block> ASPHALT = cube("asphalt", MapColor.COLOR_BLACK, WET);
    public static final RegistryObject<LineBlock> PARKING_LINE = block("parking_line", () -> new LineBlock(p(MapColor.COLOR_BLACK, WET)));
    public static final RegistryObject<LineBlock> ROAD_LINE = block("road_line", () -> new LineBlock(p(MapColor.COLOR_BLACK, WET)));
    public static final RegistryObject<LowBlock> CURB = block("curb", () -> new LowBlock(p(MapColor.STONE, WET), 8));
    public static final RegistryObject<Block> SIDEWALK = cube("sidewalk", MapColor.STONE, WET);
    public static final RegistryObject<LayerBlock> PUDDLE = block("puddle", () -> new LayerBlock(p(MapColor.WATER, WET).noOcclusion()
            .noCollission().strength(0.2f).replaceable(), 1));
    public static final RegistryObject<Block> RICE_PADDY = cube("rice_paddy", MapColor.DIRT, SoundType.MUD);
    public static final RegistryObject<Block> HEDGE = block("hedge", () -> new Block(p(MapColor.PLANT, SoundType.AZALEA_LEAVES).noOcclusion()
            .isViewBlocking((s, l, pos) -> false).isSuffocating((s, l, pos) -> false)));
    public static final RegistryObject<Block> CONCRETE_WALL = cube("concrete_wall", MapColor.STONE, SoundType.STONE);
    public static final RegistryObject<Block> GRANITE_COUNTER = cube("granite_counter", MapColor.COLOR_GRAY, SoundType.STONE);
    public static final RegistryObject<Block> STAINLESS = cube("stainless", MapColor.METAL, SoundType.METAL);
    public static final RegistryObject<Block> PILLAR = cube("pillar", MapColor.COLOR_BROWN, SoundType.WOOD);
    /** World-only: a one-block wall between two different rooms (textures of the existing wall blocks). */
    public static final RegistryObject<TwoSidedWallBlock> TWO_SIDED_WALL = block("two_sided_wall",
            () -> new TwoSidedWallBlock(p(MapColor.SAND, SoundType.STONE)));

    // ================================================================================================ doors
    public static final RegistryObject<AutoDoorBlock> AUTO_DOOR = block("auto_door", () -> new AutoDoorBlock(p(MapColor.NONE, SoundType.GLASS)
            .noOcclusion().isViewBlocking((s, l, pos) -> false).isSuffocating((s, l, pos) -> false).strength(-1.0f, 3600000.0f)));
    public static final RegistryObject<PropBlock> DOOR_SWITCH = prop("door_switch", MapColor.METAL, SoundType.METAL,
            b -> b.box(5, 3, 13, 11, 13, 16).props(Props.ON).noCollision().use(Behaviours::doorSwitch));
    public static final RegistryObject<SwingDoorBlock> SWING_DOOR = block("swing_door", () -> new SwingDoorBlock(p(MapColor.METAL, SoundType.METAL)
            .noOcclusion().isViewBlocking((s, l, pos) -> false).isSuffocating((s, l, pos) -> false)));
    public static final RegistryObject<RestaurantDoorBlock> STAFF_DOOR = block("staff_door",
            () -> new RestaurantDoorBlock(p(MapColor.COLOR_GRAY, SoundType.METAL).noOcclusion(), BlockSetType.OAK, RestaurantDoorBlock.Kind.STAFF));
    public static final RegistryObject<RestaurantDoorBlock> OFFICE_DOOR = block("office_door",
            () -> new RestaurantDoorBlock.Locked(p(MapColor.WOOD, SoundType.WOOD).noOcclusion(), BlockSetType.OAK, RestaurantDoorBlock.Kind.OFFICE, "office"));
    public static final RegistryObject<RestaurantDoorBlock> RESTROOM_DOOR_M = block("restroom_door_m",
            () -> new RestaurantDoorBlock(p(MapColor.WOOD, SoundType.WOOD).noOcclusion(), BlockSetType.OAK, RestaurantDoorBlock.Kind.RESTROOM));
    public static final RegistryObject<RestaurantDoorBlock> RESTROOM_DOOR_W = block("restroom_door_w",
            () -> new RestaurantDoorBlock(p(MapColor.WOOD, SoundType.WOOD).noOcclusion(), BlockSetType.OAK, RestaurantDoorBlock.Kind.RESTROOM));
    public static final RegistryObject<RestaurantDoorBlock> BACK_DOOR = block("back_door",
            () -> new RestaurantDoorBlock.Locked(p(MapColor.COLOR_GRAY, SoundType.METAL).noOcclusion(), BlockSetType.IRON, RestaurantDoorBlock.Kind.BACK, "back_door"));
    public static final RegistryObject<RestaurantDoorBlock> FRIDGE_DOOR = block("fridge_door",
            () -> new RestaurantDoorBlock(p(MapColor.QUARTZ, SoundType.METAL).noOcclusion(), BlockSetType.IRON, RestaurantDoorBlock.Kind.FRIDGE));
    public static final RegistryObject<RestaurantDoorBlock> STALL_DOOR = block("stall_door",
            () -> new RestaurantDoorBlock.Stall(p(MapColor.SAND, SoundType.WOOD).noOcclusion(), BlockSetType.OAK));

    // ================================================================================================ dining
    public static final RegistryObject<PropBlock> BOOTH_SEAT = prop("booth_seat", MapColor.COLOR_RED, SoundType.WOOL,
            b -> b.box(0, 0, 1, 16, 16, 16).collision(0, 0, 1, 16, 7.5, 13).collision(0, 0, 11, 16, 16, 16));
    public static final RegistryObject<PropBlock> BOOTH_PARTITION = prop("booth_partition", MapColor.NONE, SoundType.GLASS,
            b -> b.box(0, 0, 12, 16, 16, 15.5));
    /** The table top is at 12 px: things on it (menu stand, call button, glasses) are blocks above with models from y -4. */
    public static final RegistryObject<PropBlock> RESTAURANT_TABLE = prop("restaurant_table", MapColor.COLOR_BROWN, SoundType.WOOD,
            b -> b.box(0, 0, 0, 16, 12, 16).collision(0, 10.5, 0, 16, 12, 16).collision(6.5, 0, 6.5, 9.5, 10.5, 9.5));
    public static final RegistryObject<PropBlock> RESTAURANT_CHAIR = prop("restaurant_chair", MapColor.COLOR_BROWN, SoundType.WOOD,
            b -> b.box(3, 0, 2.5, 13, 15.5, 13).collision(3, 0, 2.5, 13, 9, 13).collision(3, 9, 11.5, 13, 15.5, 13)
                    .props(Props.FALLEN).inside(Behaviours::chairBump));
    public static final RegistryObject<PropBlock> CALL_BUTTON = litProp("call_button", MapColor.QUARTZ, SoundType.STONE, when(Props.LIT, 3),
            b -> b.box(5.5, -4, 9.5, 10.5, -1, 13).props(Props.LIT).noCollision().seeThrough().use(Behaviours::callButton));
    public static final RegistryObject<PropBlock> MENU_STAND = prop("menu_stand", MapColor.COLOR_BROWN, SoundType.WOOD,
            b -> b.box(3.5, -4, 8.4, 13, 5, 14.5).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> MENU_SCATTERED = prop("menu_scattered", MapColor.COLOR_ORANGE, PAPER,
            b -> b.box(0, 0, 0, 16, 1, 16).props(Props.VARIANT).noCollision().seeThrough().inside(Behaviours::menus));
    public static final RegistryObject<PropBlock> WATER_GLASS = prop("water_glass", MapColor.NONE, SoundType.GLASS,
            b -> b.box(3, -4, 3, 13, 0, 13).props(Props.GLASSES).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> BROKEN_PLATE = prop("broken_plate", MapColor.QUARTZ, SHARDS,
            b -> b.box(0, 0, 0, 16, 1, 16).noCollision().seeThrough().inside(Behaviours::shards));
    public static final RegistryObject<PropBlock> REGISTER = prop("register", MapColor.COLOR_GRAY, SoundType.METAL,
            b -> b.box(2, 0, 2, 14, 10, 14).props(Props.OPEN).seeThrough().use(Behaviours::register));
    public static final RegistryObject<PropBlock> SERVICE_BELL = prop("service_bell", MapColor.GOLD, SoundType.METAL,
            b -> b.box(5, 0, 5, 11, 4, 11).noCollision().seeThrough().use(Behaviours.tell("service_bell", ModSounds.FIXTURE_SERVICE_BELL, 16)));
    public static final RegistryObject<PropBlock> COUNTER = prop("counter", MapColor.COLOR_BROWN, SoundType.WOOD, b -> b);
    public static final RegistryObject<PropBlock> CAKE_CASE = litProp("cake_case", MapColor.NONE, SoundType.GLASS, s -> 9, b -> b);
    public static final RegistryObject<PropBlock> BUSINESS_PHONE = prop("business_phone", MapColor.SAND, SoundType.STONE,
            b -> b.box(3, 0, 3, 13, 5, 13).props(Props.RINGING).noCollision().seeThrough().use(Behaviours::phone));
    public static final RegistryObject<PropBlock> RESERVATION_BOOK = prop("reservation_book", MapColor.COLOR_RED, PAPER,
            b -> b.box(2, 0, 3, 14, 2, 13).noCollision().seeThrough().use(Behaviours::note));
    public static final RegistryObject<PropBlock> CAPSULE_MACHINE = prop("capsule_machine", MapColor.COLOR_RED, SoundType.METAL,
            b -> b.box(1.5, 0, 2.2, 14.5, 23, 13));
    public static final RegistryObject<PropBlock> WAITING_BENCH = prop("waiting_bench", MapColor.COLOR_RED, SoundType.WOOL,
            b -> b.box(0, 0, 3, 16, 7.5, 13));
    public static final RegistryObject<PropBlock> MAGAZINE_RACK = prop("magazine_rack", MapColor.COLOR_BROWN, SoundType.WOOD,
            b -> b.box(1, 0, 6, 15, 16, 16));
    public static final RegistryObject<PropBlock> UMBRELLA_STAND = prop("umbrella_stand", MapColor.METAL, SoundType.METAL,
            b -> b.box(3, 0, 3, 13, 14, 13));
    public static final RegistryObject<PropBlock> NOTICE_BOARD = prop("notice_board", MapColor.WOOD, PAPER,
            b -> b.box(0, 0, 15, 16, 16, 16).noCollision().seeThrough().use(Behaviours::note));
    public static final RegistryObject<PropBlock> DRINK_BAR = litProp("drink_bar", MapColor.COLOR_GRAY, SoundType.METAL, s -> 6,
            b -> b.box(0, 0, 2, 16, 16, 16).props(Props.POURING).use(Behaviours::drinkBar).animate(Behaviours::pour));
    public static final RegistryObject<PropBlock> COFFEE_MACHINE = prop("coffee_machine", MapColor.COLOR_BLACK, SoundType.METAL,
            b -> b.box(1, 0, 2, 15, 16, 16));
    public static final RegistryObject<PropBlock> ICE_MACHINE = prop("ice_machine", MapColor.METAL, SoundType.METAL,
            b -> b.box(0, 0, 2, 16, 16, 16).use(Behaviours.tell("ice_machine", ModSounds.FIXTURE_ICE_DROP, 6)));
    public static final RegistryObject<PropBlock> CUP_SHELF = prop("cup_shelf", MapColor.METAL, SoundType.METAL,
            b -> b.box(0, 0, 2, 16, 16, 16));
    public static final RegistryObject<PropBlock> SOUP_WARMER = prop("soup_warmer", MapColor.METAL, SoundType.METAL,
            b -> b.box(2, 0, 2, 14, 12, 14));
    public static final RegistryObject<PropBlock> SERVER_STATION = prop("server_station", MapColor.COLOR_BROWN, SoundType.WOOD, b -> b);
    public static final RegistryObject<PropBlock> CALL_DISPLAY = litProp("call_display", MapColor.COLOR_BLACK, SoundType.METAL, s -> 4,
            b -> b.box(0, 4, 13, 16, 14, 16).props(Props.NUMBER, Props.CLOSED).noCollision());
    public static final RegistryObject<PropBlock> PLANTER = prop("planter", MapColor.PLANT, SoundType.AZALEA_LEAVES,
            b -> b.box(0, 0, 2.5, 16, 24, 13.5));
    public static final RegistryObject<PropBlock> KIDS_MAT = prop("kids_mat", MapColor.COLOR_LIGHT_BLUE, SOFT,
            b -> b.box(0, 0, 0, 16, 1, 16).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> TOY_SHELF = prop("toy_shelf", MapColor.COLOR_YELLOW, SoundType.WOOD,
            b -> b.box(1, 0, 6, 15, 11.5, 13));
    public static final RegistryObject<PropBlock> STUFFED_BEAR = prop("stuffed_bear", MapColor.COLOR_BROWN, SoundType.WOOL,
            b -> b.box(3, 0, 3, 13, 11, 13).noCollision().seeThrough().use(Behaviours::bear));
    public static final RegistryObject<PropBlock> KIDS_FENCE = prop("kids_fence", MapColor.COLOR_YELLOW, SoundType.WOOD,
            b -> b.box(0, 0, 7, 16, 11, 9));
    public static final RegistryObject<PropBlock> HIGH_CHAIR = prop("high_chair", MapColor.WOOD, SoundType.WOOD,
            b -> b.box(2.5, 0, 1.5, 13.5, 16, 12.5));

    // ================================================================================================ kitchen
    public static final RegistryObject<PropBlock> GAS_RANGE = litProp("gas_range", MapColor.METAL, SoundType.METAL, when(Props.LIT, 9),
            b -> b.props(Props.LIT).use(Behaviours::gasRange).animate(Behaviours::gasFlames));
    public static final RegistryObject<PropBlock> FRYER = prop("fryer", MapColor.METAL, SoundType.METAL,
            b -> b.props(Props.ON).animate(Behaviours::fryer));
    public static final RegistryObject<PropBlock> GRIDDLE = prop("griddle", MapColor.METAL, SoundType.METAL, b -> b);
    public static final RegistryObject<PropBlock> KITCHEN_COUNTER = prop("kitchen_counter", MapColor.METAL, SoundType.METAL, b -> b);
    public static final RegistryObject<PropBlock> EXHAUST_HOOD = prop("exhaust_hood", MapColor.METAL, SoundType.METAL,
            b -> b.box(0, 5, 0, 16, 16, 16).noCollision());
    /** A wall shelf (the wall on its back): hang it at chest height. */
    public static final RegistryObject<PropBlock> POT_SHELF = prop("pot_shelf", MapColor.METAL, SoundType.METAL,
            b -> b.box(0, 3, 8, 16, 13.5, 16));
    public static final RegistryObject<PropBlock> DISH_RACK = prop("dish_rack", MapColor.METAL, SoundType.METAL,
            b -> b.box(1, 0, 2, 15, 7.5, 14));
    public static final RegistryObject<PropBlock> KITCHEN_TRASH = prop("kitchen_trash", MapColor.COLOR_GRAY, SoundType.METAL,
            b -> b.box(2, 0, 2, 14, 14, 14));
    public static final RegistryObject<PropBlock> PREP_TABLE = prop("prep_table", MapColor.METAL, SoundType.METAL,
            b -> b.box(0, 0, 0, 16, 16, 16).collision(0, 14, 0, 16, 16, 16).collision(0, 0, 0, 16, 14, 2).collision(0, 0, 14, 16, 14, 16));
    public static final RegistryObject<PropBlock> CUTTING_BOARD = prop("cutting_board", MapColor.WOOD, SoundType.WOOD,
            b -> b.box(1, 0, 3, 15, 3, 13).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> KITCHEN_SINK = prop("kitchen_sink", MapColor.METAL, SoundType.METAL,
            b -> b.props(Props.RUNNING).use(Behaviours::tap).animate(Behaviours::tapWater));
    public static final RegistryObject<PropBlock> DISHWASHER = prop("dishwasher", MapColor.METAL, SoundType.METAL,
            b -> b.props(Props.RUNNING).animate(Behaviours::steam));
    /** The pass: a shelf at counter height with the heat lamp over it, set into the pass window (one block high opening). */
    public static final RegistryObject<PropBlock> PASS_SHELF = litProp("pass_shelf", MapColor.METAL, SoundType.METAL, when(Props.LIT, 8),
            b -> b.collision(0, 0, 0, 16, 1.5, 16).collision(0, 13, 3, 16, 16, 13).props(Props.LIT).seeThrough());
    /** The pickup bell with its own segment of the pass shelf (it takes one shelf position in the pass window). */
    public static final RegistryObject<PropBlock> PASS_BELL = prop("pass_bell", MapColor.GOLD, SoundType.METAL,
            b -> b.box(0, 0, 0, 16, 5.3, 16).collision(0, 0, 0, 16, 1.5, 16).seeThrough().use(Behaviours.tell("pass_bell", ModSounds.FIXTURE_PASS_BELL, 16)));
    public static final RegistryObject<PropBlock> WALKIN_SHELF = prop("walkin_shelf", MapColor.METAL, SoundType.METAL,
            b -> b.box(0, 0, 2, 16, 16, 16));
    public static final RegistryObject<PropBlock> FROST = prop("frost", MapColor.ICE, SoundType.GLASS,
            b -> b.box(0, 0, 0, 16, 1, 16).noCollision().seeThrough());

    // ================================================================================================ back of house
    public static final RegistryObject<PropBlock> LOCKER = prop("locker", MapColor.COLOR_GRAY, SoundType.METAL,
            b -> b.box(0, 0, 3, 16, 16, 16).props(BlockStateProperties.DOUBLE_BLOCK_HALF, Props.OPEN).use(Behaviours::locker));
    public static final RegistryObject<PropBlock> TIME_RECORDER = prop("time_recorder", MapColor.SAND, SoundType.METAL,
            b -> b.box(3, 2, 10, 13, 14, 16).noCollision().use(Behaviours.tell("time_recorder", ModSounds.FIXTURE_TIME_CARD, 6)));
    public static final RegistryObject<PropBlock> STAFF_TABLE = prop("staff_table", MapColor.SAND, SoundType.WOOD,
            b -> b.box(0, 0, 0, 16, 12, 16).collision(0, 10.5, 0, 16, 12, 16).collision(1, 0, 1, 15, 10.5, 15));
    public static final RegistryObject<PropBlock> FOLDING_CHAIR = prop("folding_chair", MapColor.METAL, SoundType.METAL,
            b -> b.box(3, 0, 3, 13, 15, 13.5).collision(3, 0, 3, 13, 9, 13.5).inside(Behaviours::chairBump));
    public static final RegistryObject<PropBlock> BULLETIN_BOARD = prop("bulletin_board", MapColor.WOOD, PAPER,
            b -> b.box(0, 0, 15, 16, 16, 16).noCollision().seeThrough().use(Behaviours::note));
    public static final RegistryObject<PropBlock> CARDBOARD_BOXES = prop("cardboard_boxes", MapColor.WOOD, SoundType.WOOL, b -> b);
    public static final RegistryObject<PropBlock> CLEANING_CART = prop("cleaning_cart", MapColor.COLOR_YELLOW, SoundType.METAL,
            b -> b.box(2, 0, 1, 14, 15, 15));
    public static final RegistryObject<PropBlock> FILING_CABINET = prop("filing_cabinet", MapColor.COLOR_GRAY, SoundType.METAL,
            b -> b.box(1, 0, 2, 15, 16, 16));
    public static final RegistryObject<PropBlock> SAFE = prop("safe", MapColor.COLOR_BLACK, SoundType.METAL,
            b -> b.box(2, 0, 3, 14, 12, 15));
    public static final RegistryObject<PropBlock> CRT_TV = litProp("crt_tv", MapColor.COLOR_BLACK, SoundType.METAL, when(Props.ON, 5),
            b -> b.box(1, 0, 3, 15, 16, 15).props(Props.ON).use(Behaviours::tv));
    /** The desk top is at 12 px; the pc on it is a block above with a model from y -4. */
    public static final RegistryObject<PropBlock> OFFICE_DESK = prop("office_desk", MapColor.COLOR_GRAY, SoundType.WOOD,
            b -> b.box(0, 0, 0, 16, 12, 16));
    public static final RegistryObject<PropBlock> OFFICE_CHAIR = prop("office_chair", MapColor.COLOR_BLACK, SoundType.WOOL,
            b -> b.box(2, 0, 2, 14, 16, 14).collision(2, 0, 2, 14, 9, 14));
    public static final RegistryObject<PropBlock> PC = litProp("pc", MapColor.COLOR_BLACK, SoundType.METAL, s -> 4,
            b -> b.box(3, -4, 1, 14.7, 4.5, 15).seeThrough());
    public static final RegistryObject<PropBlock> SECURITY_MONITORS = litProp("security_monitors", MapColor.COLOR_BLACK, SoundType.METAL,
            when(Props.ON, 5), b -> b.box(0, 0, 4, 16, 16, 16).props(Props.ON, Props.ALERT));
    public static final RegistryObject<PropBlock> SECURITY_CAMERA = prop("security_camera", MapColor.COLOR_BLACK, SoundType.METAL,
            b -> b.box(4, 9, 4, 12, 16, 12).props(Props.ON, Props.SEEN).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> BREAKER_PANEL = prop("breaker_panel", MapColor.COLOR_GRAY, SoundType.METAL,
            b -> b.box(2, 2, 13, 14, 16, 16).props(Props.OPEN).use(Behaviours::breaker));

    // ================================================================================================ restrooms
    public static final RegistryObject<PropBlock> RESTROOM_SINK = prop("restroom_sink", MapColor.QUARTZ, TILE,
            b -> b.box(0, 6, 3, 16, 16, 16).collision(0, 6, 3, 16, 13.5, 16).props(Props.RUNNING).use(Behaviours::tap).animate(Behaviours::tapWater));
    public static final RegistryObject<PropBlock> MIRROR = prop("mirror", MapColor.NONE, SoundType.GLASS,
            b -> b.props(Props.CRACKED).seeThrough());
    public static final RegistryObject<PropBlock> RESTROOM_MIRROR = prop("restroom_mirror", MapColor.QUARTZ, SoundType.GLASS,
            b -> b.box(1, 1, 15, 15, 15, 16).noCollision());
    public static final RegistryObject<PropBlock> TOILET = prop("toilet", MapColor.QUARTZ, TILE,
            b -> b.box(3.8, 0, 1.8, 14, 15.2, 16).collision(3.8, 0, 1.8, 14, 9, 16).collision(3.8, 0, 12, 14, 15.2, 16).use(Behaviours::toilet));
    public static final RegistryObject<PropBlock> URINAL = prop("urinal", MapColor.QUARTZ, TILE,
            b -> b.box(3, 2, 10, 13, 16, 16));
    /** A partition panel through the middle of its block (along the facing's left-right axis). */
    public static final RegistryObject<PropBlock> STALL_WALL = prop("stall_wall", MapColor.SAND, SoundType.WOOD, b -> b.box(0, 0, 6.75, 16, 16, 9.25));
    public static final RegistryObject<PropBlock> HAND_DRYER = prop("hand_dryer", MapColor.QUARTZ, SoundType.METAL,
            b -> b.box(3, 3, 9, 13, 13, 16).props(Props.RUNNING).noCollision().use(Behaviours::handDryer));
    public static final RegistryObject<PropBlock> PAPER_TOWEL = prop("paper_towel", MapColor.QUARTZ, SoundType.METAL,
            b -> b.box(3, 3, 11, 13, 15, 16).noCollision());

    // ================================================================================================ outside
    public static final RegistryObject<PropBlock> VENDING_MACHINE = litProp("vending_machine", MapColor.QUARTZ, SoundType.METAL, s -> 12,
            b -> b.box(0, 0, 1, 16, 16, 16).props(BlockStateProperties.DOUBLE_BLOCK_HALF));
    public static final RegistryObject<PropBlock> POLE_SIGN = litProp("pole_sign", MapColor.COLOR_ORANGE, SoundType.METAL,
            s -> s.getValue(Props.SIGN_PART) == Props.SignPart.TILE && s.getValue(Props.LIT) ? 15 : 0,
            b -> b.props(Props.SIGN_PART, Props.TILE16, Props.LIT));
    public static final RegistryObject<PropBlock> ROOF_SIGN = litProp("roof_sign", MapColor.COLOR_ORANGE, SoundType.METAL, when(Props.LIT, 12),
            b -> b.box(0, 0, 6, 16, 16, 10).props(Props.TILE8, Props.LIT));
    public static final RegistryObject<PropBlock> MENU_BOARD = prop("menu_board", MapColor.COLOR_BLACK, SoundType.WOOD,
            b -> b.box(2, 0, 4, 14, 16, 12).props(Props.MESSAGE).use(Behaviours.tell("menu_board", null, 0)));
    public static final RegistryObject<PropBlock> BIKE_RACK = prop("bike_rack", MapColor.METAL, SoundType.METAL,
            b -> b.box(0, 0, 2, 16, 14, 14));
    public static final RegistryObject<PropBlock> TRASH_STATION = prop("trash_station", MapColor.COLOR_GRAY, SoundType.METAL,
            b -> b.box(0, 0, 3, 16, 14, 16));
    public static final RegistryObject<PropBlock> AC_UNIT = prop("ac_unit", MapColor.QUARTZ, SoundType.METAL,
            b -> b.box(0, 0, 4, 16, 14, 16));
    public static final RegistryObject<PropBlock> GAS_CYLINDER = prop("gas_cylinder", MapColor.COLOR_GRAY, SoundType.METAL,
            b -> b.box(4, 0, 4, 12, 16, 12));
    public static final RegistryObject<PropBlock> DUMPSTER = prop("dumpster", MapColor.COLOR_GREEN, SoundType.METAL, b -> b);
    public static final RegistryObject<PropBlock> WHEEL_STOP = prop("wheel_stop", MapColor.STONE, SoundType.STONE,
            b -> b.box(0, 0, 5, 16, 3, 11));
    public static final RegistryObject<PropBlock> OUTDOOR_PLANTER = prop("outdoor_planter", MapColor.PLANT, SoundType.AZALEA_LEAVES,
            b -> b.box(1, 0, 1, 15, 14, 15));
    public static final RegistryObject<PropBlock> STREET_LAMP = litProp("street_lamp", MapColor.METAL, SoundType.METAL,
            s -> s.getValue(Props.LAMP_PART) == Props.LampPart.HEAD && s.getValue(Props.LIT) ? 14 : 0,
            b -> b.box(6, 0, 6, 10, 16, 10).props(Props.LAMP_PART, Props.LIT));
    public static final RegistryObject<PropBlock> UTILITY_POLE = prop("utility_pole", MapColor.STONE, SoundType.STONE,
            b -> b.box(5, 0, 5, 11, 16, 11).props(Props.POLE_PART));
    /** World-only: a span of the power lines between two utility poles (the same four wires as the pole's top). */
    public static final RegistryObject<PropBlock> POWER_WIRE = prop("power_wire", MapColor.COLOR_BLACK, SoundType.METAL,
            b -> b.box(0, 11, 0, 16, 13, 16).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> GUARDRAIL = prop("guardrail", MapColor.METAL, SoundType.METAL,
            b -> b.box(0, 0, 6, 16, 13.5, 10.5).collision(0, 0, 6, 16, 24, 10.5));
    public static final RegistryObject<PropBlock> CHEVRON_SIGN = prop("chevron_sign", MapColor.COLOR_YELLOW, SoundType.METAL,
            b -> b.box(2, 0, 7.5, 14, 13, 9.5));
    /** Two blocks high (pole and mirror in one model). */
    public static final RegistryObject<PropBlock> CURVE_MIRROR = prop("curve_mirror", MapColor.COLOR_ORANGE, SoundType.METAL,
            b -> b.box(2, 0, 5, 14, 30.5, 10.5).collision(6, 0, 6, 10, 24, 10));
    public static final RegistryObject<PropBlock> TRAFFIC_LIGHT = litProp("traffic_light", MapColor.COLOR_YELLOW, SoundType.METAL, when(Props.LIT, 8),
            b -> b.box(0, 5, 4, 16, 16, 10).props(Props.LIT));
    public static final RegistryObject<PropBlock> FLOWER_OFFERING = prop("flower_offering", MapColor.PLANT, SoundType.GRASS,
            b -> b.box(2, 0, 2, 14, 10, 14).noCollision().seeThrough());
    public static final RegistryObject<PropBlock> NOTE_PAPER = prop("note_paper", MapColor.QUARTZ, PAPER,
            b -> b.box(3, 0, 3, 13, 1, 13).altBox(Props.HANGING, 3, 1, 15, 13, 15, 16).noCollision().seeThrough().use(Behaviours::note));
}
