package com.mcbesiege.registry;

import com.mcbesiege.MCBesiege;
import com.mcbesiege.block.HangingBannerBlock;
import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.block.MissionTableBlock;
import com.mcbesiege.part.PartType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MCBesiege.MODID);

    public static final Map<PartType, RegistryObject<Block>> PARTS = new EnumMap<>(PartType.class);

    static {
        for (PartType t : PartType.values()) {
            PARTS.put(t, BLOCKS.register(t.id(), () -> new MachinePartBlock(t, partProps(t))));
        }
    }

    // fortress / mission blocks
    public static final RegistryObject<Block> CASTLE_STONE = BLOCKS.register("castle_stone",
            () -> new Block(stone(MapColor.STONE, 3.0f)));
    public static final RegistryObject<Block> CASTLE_BRICKS = BLOCKS.register("castle_bricks",
            () -> new Block(stone(MapColor.STONE, 3.0f)));
    public static final RegistryObject<Block> CASTLE_BRICK_STAIRS = BLOCKS.register("castle_brick_stairs",
            () -> new StairBlock(() -> CASTLE_BRICKS.get().defaultBlockState(), stone(MapColor.STONE, 3.0f)));
    public static final RegistryObject<Block> CASTLE_BRICK_SLAB = BLOCKS.register("castle_brick_slab",
            () -> new SlabBlock(stone(MapColor.STONE, 3.0f)));
    public static final RegistryObject<Block> TIMBER_FRAME = BLOCKS.register("timber_frame",
            () -> new Block(wood(MapColor.SNOW, 1.5f)));
    public static final RegistryObject<Block> TIMBER_FRAME_PLAIN = BLOCKS.register("timber_frame_plain",
            () -> new Block(wood(MapColor.SNOW, 1.5f)));
    public static final RegistryObject<Block> THATCH = BLOCKS.register("thatch",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.6f).sound(SoundType.GRASS).ignitedByLava()));
    public static final RegistryObject<Block> THATCH_STAIRS = BLOCKS.register("thatch_stairs",
            () -> new StairBlock(() -> THATCH.get().defaultBlockState(),
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.6f).sound(SoundType.GRASS).ignitedByLava()));
    public static final RegistryObject<Block> THATCH_SLAB = BLOCKS.register("thatch_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.6f).sound(SoundType.GRASS).ignitedByLava()));
    public static final RegistryObject<Block> CASTLE_GATE = BLOCKS.register("castle_gate",
            () -> new Block(wood(MapColor.WOOD, 2.5f)));
    public static final RegistryObject<Block> BANNER_BLUE = BLOCKS.register("banner_blue",
            () -> new HangingBannerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(0.5f).sound(SoundType.WOOL).noOcclusion().noCollission().ignitedByLava()));
    public static final RegistryObject<Block> BANNER_RED = BLOCKS.register("banner_red",
            () -> new HangingBannerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.5f).sound(SoundType.WOOL).noOcclusion().noCollission().ignitedByLava()));
    public static final RegistryObject<Block> SIEGE_TARGET = BLOCKS.register("siege_target",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5f, 1.0f).sound(SoundType.WOOD).lightLevel(s -> 6)));
    public static final RegistryObject<Block> MISSION_TABLE = BLOCKS.register("mission_table",
            () -> new MissionTableBlock(wood(MapColor.WOOD, 2.0f).noOcclusion()));

    // render-only pieces for two-point connectors (never placed by players)
    public static final RegistryObject<Block> BRACE_VISUAL = BLOCKS.register("brace_visual",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion().noCollission().noLootTable()));
    public static final RegistryObject<Block> SPRING_VISUAL = BLOCKS.register("spring_visual",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion().noCollission().noLootTable()));
    public static final RegistryObject<Block> ROPE_VISUAL = BLOCKS.register("rope_visual",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion().noCollission().noLootTable()));

    public static Block part(PartType t) { return PARTS.get(t).get(); }

    public static List<Block> partBlocks() {
        List<Block> l = new ArrayList<>();
        for (RegistryObject<Block> r : PARTS.values()) l.add(r.get());
        return l;
    }

    private static boolean isFullCube(PartType t) {
        return t == PartType.STARTING_BLOCK || t == PartType.WOODEN_BLOCK || t == PartType.REINFORCED_WOOD || t == PartType.IRON_BLOCK
                || t == PartType.STEEL_BLOCK || t == PartType.STONE_BLOCK || t == PartType.BALLAST;
    }

    private static BlockBehaviour.Properties partProps(PartType t) {
        BlockBehaviour.Properties p;
        if (t.isMetal()) p = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.2f, 4.0f).sound(SoundType.METAL);
        else if (t.isStone()) p = BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.0f, 4.0f).sound(SoundType.STONE);
        else if (t == PartType.BALLOON || t == PartType.WING) p = BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.4f).sound(SoundType.WOOL);
        else if (t == PartType.BOMB || t == PartType.POWDER_BARREL || t == PartType.ROCKET)
            p = BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).strength(0.4f).sound(SoundType.GRASS);
        else if (t.cat == PartType.Cat.AUTOMATION) p = BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(0.6f).sound(SoundType.STONE);
        else p = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.8f, 3.0f).sound(SoundType.WOOD);
        if (!isFullCube(t)) p = p.noOcclusion();
        return p;
    }

    private static BlockBehaviour.Properties stone(MapColor c, float hardness) {
        return BlockBehaviour.Properties.of().mapColor(c).strength(hardness, 6.0f).sound(SoundType.STONE).requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties wood(MapColor c, float hardness) {
        return BlockBehaviour.Properties.of().mapColor(c).strength(hardness, 3.0f).sound(SoundType.WOOD).ignitedByLava();
    }
}
