package com.eternalrealm.block.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Building blocks of the royal capital (white castle stone, blue roof tiles, gold trim, plaster), its furnishings
 * (chandelier, gilded finial) and the light shaft emitter. Textures, models and data come from
 * {@code tools/art/capital_blocks.py}; the capital build ({@code tools/capital}) uses exactly these ids.
 */
public final class ERBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ModInfo.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModInfo.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ModInfo.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModInfo.MOD_ID);
    private static final List<RegistryObject<Item>> TAB_ITEMS = new ArrayList<>();

    // ---- castle stone
    public static final RegistryObject<Block> CASTLE_BRICKS = block("castle_bricks", () -> new Block(stone(MapColor.QUARTZ)));
    public static final RegistryObject<Block> CASTLE_BRICK_STAIRS = block("castle_brick_stairs",
            () -> new StairBlock(() -> CASTLE_BRICKS.get().defaultBlockState(), stone(MapColor.QUARTZ)));
    public static final RegistryObject<Block> CASTLE_BRICK_SLAB = block("castle_brick_slab",
            () -> new SlabBlock(stone(MapColor.QUARTZ)));
    public static final RegistryObject<Block> CASTLE_BRICK_WALL = block("castle_brick_wall",
            () -> new WallBlock(stone(MapColor.QUARTZ).forceSolidOn()));
    public static final RegistryObject<Block> SMOOTH_CASTLE_STONE = block("smooth_castle_stone",
            () -> new Block(stone(MapColor.QUARTZ)));
    public static final RegistryObject<Block> SMOOTH_CASTLE_STONE_STAIRS = block("smooth_castle_stone_stairs",
            () -> new StairBlock(() -> SMOOTH_CASTLE_STONE.get().defaultBlockState(), stone(MapColor.QUARTZ)));
    public static final RegistryObject<Block> SMOOTH_CASTLE_STONE_SLAB = block("smooth_castle_stone_slab",
            () -> new SlabBlock(stone(MapColor.QUARTZ)));
    public static final RegistryObject<Block> CASTLE_PILLAR = block("castle_pillar",
            () -> new RotatedPillarBlock(stone(MapColor.QUARTZ)));
    public static final RegistryObject<Block> CARVED_CASTLE_STONE = block("carved_castle_stone",
            () -> new Block(stone(MapColor.QUARTZ)));
    public static final RegistryObject<Block> CASTLE_CREST_STONE = block("castle_crest_stone",
            () -> new CrestStoneBlock(stone(MapColor.QUARTZ)));

    // ---- roofs and trim
    public static final RegistryObject<Block> ROYAL_ROOF_TILES = block("royal_roof_tiles",
            () -> new Block(tiles(MapColor.COLOR_BLUE)));
    public static final RegistryObject<Block> ROYAL_ROOF_TILE_STAIRS = block("royal_roof_tile_stairs",
            () -> new StairBlock(() -> ROYAL_ROOF_TILES.get().defaultBlockState(), tiles(MapColor.COLOR_BLUE)));
    public static final RegistryObject<Block> ROYAL_ROOF_TILE_SLAB = block("royal_roof_tile_slab",
            () -> new SlabBlock(tiles(MapColor.COLOR_BLUE)));
    public static final RegistryObject<Block> PALE_ROOF_TILES = block("pale_roof_tiles",
            () -> new Block(tiles(MapColor.COLOR_LIGHT_BLUE)));
    public static final RegistryObject<Block> PALE_ROOF_TILE_STAIRS = block("pale_roof_tile_stairs",
            () -> new StairBlock(() -> PALE_ROOF_TILES.get().defaultBlockState(), tiles(MapColor.COLOR_LIGHT_BLUE)));
    public static final RegistryObject<Block> PALE_ROOF_TILE_SLAB = block("pale_roof_tile_slab",
            () -> new SlabBlock(tiles(MapColor.COLOR_LIGHT_BLUE)));
    public static final RegistryObject<Block> GILDED_TRIM = block("gilded_trim", () -> new Block(gold()));
    public static final RegistryObject<Block> GILDED_TRIM_STAIRS = block("gilded_trim_stairs",
            () -> new StairBlock(() -> GILDED_TRIM.get().defaultBlockState(), gold()));
    public static final RegistryObject<Block> GILDED_TRIM_SLAB = block("gilded_trim_slab", () -> new SlabBlock(gold()));
    public static final RegistryObject<Block> GILDED_FINIAL = block("gilded_finial",
            () -> new FinialBlock(gold().noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final RegistryObject<Block> WHITE_PLASTER = block("white_plaster", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1.0F, 3.0F).sound(SoundType.CALCITE)));

    // ---- furnishings and light
    public static final RegistryObject<Block> CHANDELIER = block("chandelier", () -> new ChandelierBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(0.8F).sound(SoundType.LANTERN)
                    .lightLevel(state -> 15).noCollission().noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final RegistryObject<Block> LIGHT_SHAFT = block("light_shaft", () -> new LightShaftBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.0F, 3600000.0F).noCollission()
                    .noOcclusion().noParticlesOnBreak().pushReaction(PushReaction.DESTROY)));

    @SuppressWarnings("DataFlowIssue") // the data fixer type may be null
    public static final RegistryObject<BlockEntityType<LightShaftBlockEntity>> LIGHT_SHAFT_ENTITY =
            BLOCK_ENTITIES.register("light_shaft", () -> BlockEntityType.Builder.of(LightShaftBlockEntity::new,
                    LIGHT_SHAFT.get()).build(null));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("building", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + ModInfo.MOD_ID + ".building"))
            .icon(() -> new ItemStack(CASTLE_BRICKS.get()))
            .displayItems((parameters, output) -> TAB_ITEMS.forEach(item -> output.accept(item.get())))
            .build());

    private ERBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
    }

    private static RegistryObject<Block> block(String name, Supplier<Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(name, factory);
        TAB_ITEMS.add(ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
        return block;
    }

    private static BlockBehaviour.Properties stone(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE);
    }

    private static BlockBehaviour.Properties tiles(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.DEEPSLATE_TILES);
    }

    private static BlockBehaviour.Properties gold() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).requiresCorrectToolForDrops()
                .strength(2.0F, 6.0F).sound(SoundType.METAL);
    }
}
