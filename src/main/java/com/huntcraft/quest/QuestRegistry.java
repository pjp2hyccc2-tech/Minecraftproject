package com.huntcraft.quest;

import com.huntcraft.HuntCraft;
import com.huntcraft.quest.block.CampFlagBlock;
import com.huntcraft.quest.block.DeliveryBoxBlock;
import com.huntcraft.quest.block.GatherKind;
import com.huntcraft.quest.block.GatheringBlock;
import com.huntcraft.quest.block.QuestBoardBlock;
import com.huntcraft.quest.block.SupplyBoxBlock;
import com.huntcraft.quest.item.QuestMapItem;
import com.huntcraft.quest.world.QuestBiomeSource;
import com.huntcraft.quest.world.QuestChunkGenerator;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** Blocks, items and worldgen codecs (quest-map chunk generator + biome source) of the quest module. */
public final class QuestRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HuntCraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HuntCraft.MODID);
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, HuntCraft.MODID);
    public static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, HuntCraft.MODID);

    private QuestRegistry() {
    }

    private static BlockBehaviour.Properties gatherProps(MapColor color, SoundType sound, boolean solid, int light) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(color).strength(-1.0F, 3600000.0F)
                .sound(sound).noOcclusion().noLootTable()
                .lightLevel(s -> s.getValue(GatheringBlock.CHARGES) > 0 ? light : 0);
        if (!solid) p = p.noCollission();
        return p;
    }

    private static BlockBehaviour.Properties boxProps(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0F, 3600000.0F).sound(SoundType.WOOD).noOcclusion().noLootTable();
    }

    // ---------------------------------------------------------------- gathering points
    public static final RegistryObject<Block> GATHER_HERB = BLOCKS.register("gather_herb",
            () -> new GatheringBlock(GatherKind.HERB, gatherProps(MapColor.PLANT, SoundType.GRASS, false, 0)));
    public static final RegistryObject<Block> GATHER_MUSHROOM = BLOCKS.register("gather_mushroom",
            () -> new GatheringBlock(GatherKind.MUSHROOM, gatherProps(MapColor.COLOR_LIGHT_BLUE, SoundType.FUNGUS, false, 2)));
    public static final RegistryObject<Block> GATHER_BUG = BLOCKS.register("gather_bug",
            () -> new GatheringBlock(GatherKind.BUG, gatherProps(MapColor.PLANT, SoundType.SWEET_BERRY_BUSH, false, 5)));
    public static final RegistryObject<Block> GATHER_BONE = BLOCKS.register("gather_bone",
            () -> new GatheringBlock(GatherKind.BONE, gatherProps(MapColor.SAND, SoundType.BONE_BLOCK, true, 0)));
    public static final RegistryObject<Block> GATHER_MINING = BLOCKS.register("gather_mining",
            () -> new GatheringBlock(GatherKind.MINING, gatherProps(MapColor.STONE, SoundType.STONE, true, 3)));

    // ---------------------------------------------------------------- camp / guild blocks
    public static final RegistryObject<Block> SUPPLY_BOX = BLOCKS.register("supply_box", () -> new SupplyBoxBlock(boxProps(MapColor.COLOR_BLUE)));
    public static final RegistryObject<Block> DELIVERY_BOX = BLOCKS.register("delivery_box", () -> new DeliveryBoxBlock(boxProps(MapColor.COLOR_RED)));
    public static final RegistryObject<Block> QUEST_BOARD = BLOCKS.register("quest_board", () -> new QuestBoardBlock(boxProps(MapColor.WOOD)));
    public static final RegistryObject<Block> CAMP_FLAG = BLOCKS.register("camp_flag", () -> new CampFlagBlock(boxProps(MapColor.COLOR_BLUE).noCollission()));

    // ---------------------------------------------------------------- items
    public static final RegistryObject<Item> QUEST_MAP = ITEMS.register("quest_map", () -> new QuestMapItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GATHER_HERB_ITEM = blockItem("gather_herb", GATHER_HERB);
    public static final RegistryObject<Item> GATHER_MUSHROOM_ITEM = blockItem("gather_mushroom", GATHER_MUSHROOM);
    public static final RegistryObject<Item> GATHER_BUG_ITEM = blockItem("gather_bug", GATHER_BUG);
    public static final RegistryObject<Item> GATHER_BONE_ITEM = blockItem("gather_bone", GATHER_BONE);
    public static final RegistryObject<Item> GATHER_MINING_ITEM = blockItem("gather_mining", GATHER_MINING);
    public static final RegistryObject<Item> SUPPLY_BOX_ITEM = blockItem("supply_box", SUPPLY_BOX);
    public static final RegistryObject<Item> DELIVERY_BOX_ITEM = blockItem("delivery_box", DELIVERY_BOX);
    public static final RegistryObject<Item> QUEST_BOARD_ITEM = blockItem("quest_board", QUEST_BOARD);
    public static final RegistryObject<Item> CAMP_FLAG_ITEM = blockItem("camp_flag", CAMP_FLAG);

    public static final List<RegistryObject<Item>> TAB_ITEMS = List.of(QUEST_MAP, QUEST_BOARD_ITEM, SUPPLY_BOX_ITEM, DELIVERY_BOX_ITEM, CAMP_FLAG_ITEM,
            GATHER_HERB_ITEM, GATHER_MUSHROOM_ITEM, GATHER_BUG_ITEM, GATHER_BONE_ITEM, GATHER_MINING_ITEM);

    private static RegistryObject<Item> blockItem(String id, RegistryObject<Block> block) {
        return ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    // ---------------------------------------------------------------- worldgen (dimension json: generator type huntcraft:quest_map)
    public static final RegistryObject<Codec<? extends ChunkGenerator>> QUEST_MAP_GENERATOR = CHUNK_GENERATORS.register("quest_map", () -> QuestChunkGenerator.CODEC);
    public static final RegistryObject<Codec<? extends BiomeSource>> QUEST_MAP_BIOMES = BIOME_SOURCES.register("quest_map", () -> QuestBiomeSource.CODEC);
}
