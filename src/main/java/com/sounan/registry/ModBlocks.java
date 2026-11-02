package com.sounan.registry;

import com.sounan.Sounan;
import com.sounan.block.ClimbingRopeBlock;
import com.sounan.block.FacingBlock;
import com.sounan.block.HokoraBlock;
import com.sounan.block.InteractBlock;
import com.sounan.block.KeyHookBlock;
import com.sounan.block.LockedGateBlock;
import com.sounan.block.LostBagBlock;
import com.sounan.block.RopeAnchorBlock;
import com.sounan.block.SounanDoorBlock;
import com.sounan.block.ThinFacingBlock;
import com.sounan.block.TunnelLampBlock;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Sounan.MODID);

    /** Dry cedar needles and dead leaves underfoot. */
    public static final SoundType LEAF_LITTER = new ForgeSoundType(0.7f, 1.0f, () -> SoundEvents.ROOTED_DIRT_BREAK,
            ModSounds.BLOCK_LEAVES_STEP, () -> SoundEvents.ROOTED_DIRT_PLACE, () -> SoundEvents.ROOTED_DIRT_HIT,
            () -> SoundEvents.ROOTED_DIRT_FALL);

    // ---------------------------------------------------------------- nature
    public static final RegistryObject<Block> CEDAR_LOG = BLOCKS.register("cedar_log", () -> new RotatedPillarBlock(wood(MapColor.TERRACOTTA_BROWN)));
    public static final RegistryObject<Block> CEDAR_LOG_RIBBON = BLOCKS.register("cedar_log_ribbon",
            () -> new RotatedPillarBlock(wood(MapColor.TERRACOTTA_BROWN)));
    public static final RegistryObject<Block> CEDAR_LOG_SHIMENAWA = BLOCKS.register("cedar_log_shimenawa",
            () -> new RotatedPillarBlock(wood(MapColor.TERRACOTTA_BROWN)));
    public static final RegistryObject<Block> CEDAR_LEAVES = BLOCKS.register("cedar_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.copy(Blocks.SPRUCE_LEAVES)));
    public static final RegistryObject<Block> FOREST_FLOOR = BLOCKS.register("forest_floor",
            () -> new Block(props(MapColor.TERRACOTTA_BROWN, LEAF_LITTER, 0.6f)));
    public static final RegistryObject<Block> FOREST_DIRT = BLOCKS.register("forest_dirt",
            () -> new Block(props(MapColor.DIRT, SoundType.ROOTED_DIRT, 0.6f)));
    public static final RegistryObject<Block> MOSS_FLOOR = BLOCKS.register("moss_floor",
            () -> new Block(props(MapColor.COLOR_GREEN, SoundType.MOSS, 0.6f)));
    public static final RegistryObject<Block> TRAIL = BLOCKS.register("trail",
            () -> new Block(props(MapColor.DIRT, SoundType.GRAVEL, 0.6f)));
    public static final RegistryObject<Block> MOUNTAIN_ROCK = BLOCKS.register("mountain_rock",
            () -> new Block(props(MapColor.STONE, SoundType.STONE, 1.5f)));
    public static final RegistryObject<Block> MOSSY_ROCK = BLOCKS.register("mossy_rock",
            () -> new Block(props(MapColor.STONE, SoundType.STONE, 1.5f)));
    public static final RegistryObject<Block> RIVER_STONES = BLOCKS.register("river_stones",
            () -> new Block(props(MapColor.STONE, SoundType.GRAVEL, 1.0f)));
    public static final RegistryObject<Block> FALLEN_LEAVES = BLOCKS.register("fallen_leaves",
            () -> new ThinFacingBlock(decor(LEAF_LITTER).replaceable(), 0, 0, 0, 16, 1, 16));

    // ---------------------------------------------------------------- buildings, road, tunnel
    public static final RegistryObject<Block> WEATHERED_PLANKS = BLOCKS.register("weathered_planks", () -> new Block(wood(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> DARK_PLANKS = BLOCKS.register("dark_planks", () -> new Block(wood(MapColor.COLOR_BROWN)));
    public static final RegistryObject<Block> OLD_PLASTER = BLOCKS.register("old_plaster",
            () -> new Block(props(MapColor.TERRACOTTA_WHITE, SoundType.STONE, 1.5f)));
    public static final RegistryObject<Block> TIN_ROOF = BLOCKS.register("tin_roof", () -> new Block(metal()));
    public static final RegistryObject<Block> TIN_ROOF_STAIRS = BLOCKS.register("tin_roof_stairs",
            () -> new StairBlock(() -> TIN_ROOF.get().defaultBlockState(), metal()));
    public static final RegistryObject<Block> TIN_ROOF_SLAB = BLOCKS.register("tin_roof_slab", () -> new SlabBlock(metal()));
    public static final RegistryObject<Block> BLUE_TARP = BLOCKS.register("blue_tarp",
            () -> new ThinFacingBlock(decor(SoundType.WOOL), 0, 0, 0, 16, 1, 16));
    public static final RegistryObject<Block> CONCRETE_OLD = BLOCKS.register("concrete_old",
            () -> new Block(props(MapColor.STONE, SoundType.STONE, 2.0f)));
    public static final RegistryObject<Block> CONCRETE_MOSS = BLOCKS.register("concrete_moss",
            () -> new Block(props(MapColor.STONE, SoundType.STONE, 2.0f)));
    public static final RegistryObject<Block> ASPHALT = BLOCKS.register("asphalt",
            () -> new Block(props(MapColor.COLOR_BLACK, SoundType.STONE, 1.5f)));
    public static final RegistryObject<Block> STEEL_LATTICE = BLOCKS.register("steel_lattice",
            () -> new Block(metal().noOcclusion()));
    public static final RegistryObject<Block> TATAMI_OLD = BLOCKS.register("tatami_old",
            () -> new Block(props(MapColor.COLOR_YELLOW, SoundType.WOOL, 1.0f)));
    public static final RegistryObject<Block> OLD_PLANKS = BLOCKS.register("old_planks", () -> new Block(wood(MapColor.COLOR_BROWN)));
    public static final RegistryObject<Block> ROTTEN_PLANKS = BLOCKS.register("rotten_planks", () -> new Block(wood(MapColor.TERRACOTTA_BROWN)));
    public static final RegistryObject<Block> DARK_BEAM = BLOCKS.register("dark_beam", () -> new RotatedPillarBlock(wood(MapColor.COLOR_BLACK)));
    public static final RegistryObject<Block> PLASTER_WALL = BLOCKS.register("plaster_wall",
            () -> new Block(props(MapColor.TERRACOTTA_WHITE, SoundType.STONE, 1.5f)));
    public static final RegistryObject<Block> SHOJI_TORN = BLOCKS.register("shoji_torn",
            () -> new Block(props(MapColor.TERRACOTTA_WHITE, SoundType.BAMBOO_WOOD, 1.0f).noOcclusion()));
    public static final RegistryObject<Block> FUTON = BLOCKS.register("futon",
            () -> new FacingBlock(props(MapColor.WOOL, SoundType.WOOL, 0.5f).noOcclusion(), 0, 0, 0, 16, 3, 16));
    public static final RegistryObject<Block> STONE_LANTERN = BLOCKS.register("stone_lantern",
            () -> new FacingBlock(props(MapColor.STONE, SoundType.STONE, 2.0f).noOcclusion(), 3, 0, 3, 13, 16, 13));
    public static final RegistryObject<Block> BLOOD_STAIN = BLOCKS.register("blood_stain",
            () -> new ThinFacingBlock(decor(SoundType.SLIME_BLOCK), 0, 0, 0, 16, 0.5, 16));
    public static final RegistryObject<Block> FOOTPRINTS = BLOCKS.register("footprints",
            () -> new ThinFacingBlock(decor(SoundType.SLIME_BLOCK), 0, 0, 0, 16, 0.5, 16));
    public static final RegistryObject<Block> HAIR_STRANDS = BLOCKS.register("hair_strands",
            () -> new ThinFacingBlock(decor(SoundType.WOOL), 0, 0, 0, 16, 0.5, 16));
    public static final RegistryObject<Block> SHIMENAWA = BLOCKS.register("shimenawa",
            () -> new ThinFacingBlock(decor(SoundType.GRASS), 0, 6, 7, 16, 16, 9));

    // ---------------------------------------------------------------- doors
    public static final RegistryObject<Block> HUT_DOOR = BLOCKS.register("hut_door",
            () -> new SounanDoorBlock(door(), BlockSetType.SPRUCE, () -> SoundEvents.WOODEN_DOOR_OPEN, 12.0f));
    public static final RegistryObject<Block> HOUSE_DOOR = BLOCKS.register("house_door",
            () -> new SounanDoorBlock(door(), BlockSetType.OAK, () -> SoundEvents.WOODEN_DOOR_OPEN, 12.0f));
    public static final RegistryObject<Block> IRON_GATE = BLOCKS.register("iron_gate",
            () -> new LockedGateBlock(door().sound(SoundType.METAL).strength(-1.0f, 3600000.0f), () -> SoundEvents.IRON_DOOR_OPEN));

    // ---------------------------------------------------------------- props
    public static final RegistryObject<Block> LOST_BAG = BLOCKS.register("lost_bag",
            () -> new LostBagBlock(props(MapColor.COLOR_ORANGE, SoundType.WOOL, 0.5f).noOcclusion(), "bag", 2, 0, 3, 14, 8, 13));
    public static final RegistryObject<Block> MEMO = BLOCKS.register("memo",
            () -> new InteractBlock(decor(SoundType.WOOL), "memo", 2, 0, 2, 14, 1, 14));
    public static final RegistryObject<Block> MEMO_WALL = BLOCKS.register("memo_wall",
            () -> new InteractBlock(decor(SoundType.WOOL), "memo", 2, 2, 15, 14, 14, 16));
    public static final RegistryObject<Block> LOGBOOK = BLOCKS.register("logbook",
            () -> new InteractBlock(decor(SoundType.WOOL), "logbook", 3, 0, 4, 13, 2, 12));
    public static final RegistryObject<Block> NOTICE_BOARD = BLOCKS.register("notice_board",
            () -> new InteractBlock(wood(MapColor.COLOR_BROWN).noOcclusion(), "notice", 0, 0, 6, 16, 16, 10));
    public static final RegistryObject<Block> SIGNPOST = BLOCKS.register("signpost",
            () -> new InteractBlock(wood(MapColor.COLOR_BROWN).noOcclusion(), "sign", 5, 0, 5, 11, 16, 11));
    public static final RegistryObject<Block> JIZO = BLOCKS.register("jizo",
            () -> new FacingBlock(props(MapColor.STONE, SoundType.STONE, 2.0f).noOcclusion(), 4, 0, 4, 12, 14, 12));
    public static final RegistryObject<Block> HOKORA = BLOCKS.register("hokora",
            () -> new HokoraBlock(wood(MapColor.COLOR_BROWN).noOcclusion().lightLevel(s -> s.getValue(HokoraBlock.OFFERED) ? 7 : 0),
                    "hokora", 2, 0, 2, 14, 15, 14));
    public static final RegistryObject<Block> WARA_NINGYO = BLOCKS.register("wara_ningyo",
            () -> new ThinFacingBlock(decor(SoundType.GRASS), 4, 2, 15, 12, 15, 16));
    public static final RegistryObject<Block> ROPE_ANCHOR = BLOCKS.register("rope_anchor",
            () -> new RopeAnchorBlock(metal().noOcclusion().strength(-1.0f, 3600000.0f), "rope_anchor", 5, 0, 5, 11, 9, 11));
    public static final RegistryObject<Block> CLIMBING_ROPE = BLOCKS.register("climbing_rope",
            () -> new ClimbingRopeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(-1.0f, 3600000.0f)
                    .sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Block> PUBLIC_PHONE = BLOCKS.register("public_phone",
            () -> new InteractBlock(metal().noOcclusion(), "public_phone", 3, 0, 8, 13, 16, 16));
    public static final RegistryObject<Block> BUS_STOP = BLOCKS.register("bus_stop",
            () -> new InteractBlock(metal().noOcclusion(), "bus_stop", 5, 0, 5, 11, 16, 11));
    public static final RegistryObject<Block> TUNNEL_LAMP = BLOCKS.register("tunnel_lamp",
            () -> new TunnelLampBlock(metal().noOcclusion().lightLevel(s -> s.getValue(TunnelLampBlock.LIT) ? 11 : 0),
                    "lamp", 3, 9, 12, 13, 14, 16));
    public static final RegistryObject<Block> GUARDRAIL = BLOCKS.register("guardrail",
            () -> new FacingBlock(metal().noOcclusion(), 0, 0, 6, 16, 12, 10));
    public static final RegistryObject<Block> KEY_HOOK = BLOCKS.register("key_hook",
            () -> new KeyHookBlock(decor(SoundType.WOOD), "key_hook", 3, 4, 15, 13, 12, 16));
    public static final RegistryObject<Block> CAR_DOOR = BLOCKS.register("car_door",
            () -> new InteractBlock(metal().strength(-1.0f, 3600000.0f), "car", 0, 0, 0, 16, 16, 16));
    public static final RegistryObject<Block> OMIKI_BOTTLE = BLOCKS.register("omiki_bottle",
            () -> new InteractBlock(props(MapColor.SNOW, SoundType.GLASS, 0.3f).noOcclusion(), "omiki", 5, 0, 5, 11, 11, 11));

    private ModBlocks() {}

    private static BlockBehaviour.Properties props(MapColor color, SoundType sound, float strength) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(strength, 6.0f).sound(sound);
    }

    private static BlockBehaviour.Properties wood(MapColor color) {
        return props(color, SoundType.WOOD, 2.0f);
    }

    private static BlockBehaviour.Properties metal() {
        return props(MapColor.COLOR_GRAY, SoundType.METAL, 2.0f);
    }

    private static BlockBehaviour.Properties door() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 6.0f).sound(SoundType.WOOD)
                .noOcclusion().pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties decor(SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.2f).sound(sound).noCollission().noOcclusion()
                .pushReaction(PushReaction.DESTROY);
    }
}
