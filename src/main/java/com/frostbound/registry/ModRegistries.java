package com.frostbound.registry;

import com.frostbound.FrostboundMod;
import com.frostbound.block.BuildingCoreBlock;
import com.frostbound.block.GeneratorCoreBlock;
import com.frostbound.block.GeneratorCoreBlockEntity;
import com.frostbound.block.GrateBlock;
import com.frostbound.entity.CitizenEntity;
import com.frostbound.world.FrozenChunkGenerator;
import com.frostbound.world.RingBiomeSource;
import com.mojang.serialization.Codec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every registry entry of the mod. */
public final class ModRegistries {
    private static final String ID = FrostboundMod.MOD_ID;

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, ID);
    public static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, ID);

    private static BlockBehaviour.Properties structure(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0F, 3_600_000.0F).noLootTable().sound(sound);
    }

    public static final RegistryObject<Block> GENERATOR_CORE = block("generator_core",
            () -> new GeneratorCoreBlock(structure(MapColor.COLOR_ORANGE, SoundType.METAL).lightLevel(s -> 12).noOcclusion()));
    public static final RegistryObject<Block> GENERATOR_CASING = block("generator_casing",
            () -> new Block(structure(MapColor.COLOR_GRAY, SoundType.METAL)));
    public static final RegistryObject<Block> GENERATOR_PIPE = block("generator_pipe",
            () -> new RotatedPillarBlock(structure(MapColor.COLOR_BROWN, SoundType.COPPER)));
    public static final RegistryObject<Block> GENERATOR_GRATE = block("generator_grate",
            () -> new GrateBlock(structure(MapColor.COLOR_ORANGE, SoundType.METAL)
                    .lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 14 : 0)));
    public static final RegistryObject<Block> CHIMNEY_BRICK = block("chimney_brick",
            () -> new Block(structure(MapColor.COLOR_BROWN, SoundType.STONE)));
    public static final RegistryObject<Block> BUILDING_CORE = block("building_core",
            () -> new BuildingCoreBlock(structure(MapColor.WOOD, SoundType.WOOD)));
    public static final RegistryObject<Block> FROST_LAMP = block("frost_lamp",
            () -> new Block(structure(MapColor.COLOR_ORANGE, SoundType.LANTERN).lightLevel(s -> 15)));

    public static final RegistryObject<BlockEntityType<GeneratorCoreBlockEntity>> GENERATOR_CORE_BE = BLOCK_ENTITIES.register(
            "generator_core", () -> BlockEntityType.Builder.of(GeneratorCoreBlockEntity::new, GENERATOR_CORE.get()).build(null));

    public static final RegistryObject<EntityType<CitizenEntity>> CITIZEN = ENTITIES.register("citizen",
            () -> EntityType.Builder.<CitizenEntity>of(CitizenEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).clientTrackingRange(8).noSave().build(ID + ":citizen"));

    public static final Map<String, RegistryObject<SoundEvent>> SOUND_EVENTS = new LinkedHashMap<>();

    static {
        for (String s : new String[] {"generator_hum", "steam_hiss", "wind_loop", "blizzard_loop", "build", "mining", "alert",
            "event_major", "ui_click", "ui_open", "city_murmur"}) {
            SOUND_EVENTS.put(s, SOUNDS.register(s, () -> SoundEvent.createVariableRangeEvent(FrostboundMod.id(s))));
        }
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("frostbound", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.frostbound"))
            .icon(() -> new ItemStack(GENERATOR_CORE.get()))
            .displayItems((params, out) -> ITEMS.getEntries().forEach(i -> out.accept(i.get())))
            .build());

    public static final RegistryObject<Codec<? extends ChunkGenerator>> FROZEN_GENERATOR = CHUNK_GENERATORS.register(
            "frozen_wastes", () -> FrozenChunkGenerator.CODEC);
    public static final RegistryObject<Codec<? extends BiomeSource>> FROZEN_RINGS = BIOME_SOURCES.register(
            "frozen_rings", () -> RingBiomeSource.CODEC);

    private ModRegistries() {
    }

    private static RegistryObject<Block> block(String name, Supplier<Block> factory) {
        RegistryObject<Block> b = BLOCKS.register(name, factory);
        ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
        return b;
    }

    public static SoundEvent sound(String name) {
        RegistryObject<SoundEvent> s = SOUND_EVENTS.get(name);
        return s == null ? null : s.get();
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
        SOUNDS.register(bus);
        TABS.register(bus);
        CHUNK_GENERATORS.register(bus);
        BIOME_SOURCES.register(bus);
    }

    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(CITIZEN.get(), CitizenEntity.createAttributes().build());
    }
}
