package com.huntcraft.monster.small;

import com.huntcraft.HuntCraft;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.registry.HCCreativeTabs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Module entry point: 10 small monsters. Called from the HuntCraft constructor. */
public final class SmallMonsters {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HuntCraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HuntCraft.MODID);
    private static final List<RegistryObject<Item>> EGGS = new ArrayList<>();

    // ---------------------------------------------------------------- hostile
    public static final RegistryObject<EntityType<JagraptorEntity>> JAGRAPTOR = ENTITIES.register("jagraptor",
            () -> EntityType.Builder.of(JagraptorEntity::new, MobCategory.MONSTER).sized(0.9F, 1.5F)
                    .clientTrackingRange(10).build("jagraptor"));
    public static final RegistryObject<EntityType<BlizraptorEntity>> BLIZRAPTOR = ENTITIES.register("blizraptor",
            () -> EntityType.Builder.of(BlizraptorEntity::new, MobCategory.MONSTER).sized(0.9F, 1.5F)
                    .clientTrackingRange(10).build("blizraptor"));
    public static final RegistryObject<EntityType<BoarfangEntity>> BOARFANG = ENTITIES.register("boarfang",
            () -> EntityType.Builder.of(BoarfangEntity::new, MobCategory.MONSTER).sized(1.1F, 1.1F)
                    .clientTrackingRange(10).build("boarfang"));
    public static final RegistryObject<EntityType<BuzzlingEntity>> BUZZLING = ENTITIES.register("buzzling",
            () -> EntityType.Builder.of(BuzzlingEntity::new, MobCategory.MONSTER).sized(0.9F, 0.8F)
                    .clientTrackingRange(10).build("buzzling"));
    public static final RegistryObject<EntityType<HeatgeckoEntity>> HEATGECKO = ENTITIES.register("heatgecko",
            () -> EntityType.Builder.of(HeatgeckoEntity::new, MobCategory.MONSTER).sized(0.8F, 0.6F)
                    .clientTrackingRange(10).fireImmune().build("heatgecko"));
    public static final RegistryObject<EntityType<GansekiganiEntity>> GANSEKIGANI = ENTITIES.register("gansekigani",
            () -> EntityType.Builder.of(GansekiganiEntity::new, MobCategory.MONSTER).sized(1.2F, 1.1F)
                    .clientTrackingRange(10).build("gansekigani"));
    public static final RegistryObject<EntityType<NussuttoMofuEntity>> NUSSUTTO_MOFU = ENTITIES.register("nussutto_mofu",
            () -> EntityType.Builder.of(NussuttoMofuEntity::new, MobCategory.MONSTER).sized(0.6F, 0.95F)
                    .clientTrackingRange(10).build("nussutto_mofu"));
    // ---------------------------------------------------------------- herbivores
    public static final RegistryObject<EntityType<GrasicaEntity>> GRASICA = ENTITIES.register("grasica",
            () -> EntityType.Builder.of(GrasicaEntity::new, MobCategory.CREATURE).sized(0.9F, 1.5F)
                    .clientTrackingRange(10).build("grasica"));
    public static final RegistryObject<EntityType<GamenosEntity>> GAMENOS = ENTITIES.register("gamenos",
            () -> EntityType.Builder.of(GamenosEntity::new, MobCategory.CREATURE).sized(1.5F, 1.7F)
                    .clientTrackingRange(10).build("gamenos"));
    public static final RegistryObject<EntityType<YukimammoEntity>> YUKIMAMMO = ENTITIES.register("yukimammo",
            () -> EntityType.Builder.of(YukimammoEntity::new, MobCategory.CREATURE).sized(1.5F, 2.3F)
                    .clientTrackingRange(10).build("yukimammo"));

    // ---------------------------------------------------------------- spawn eggs
    public static final RegistryObject<Item> JAGRAPTOR_EGG = egg("jagraptor", JAGRAPTOR, 0x21486F, 0xD6672E);
    public static final RegistryObject<Item> BLIZRAPTOR_EGG = egg("blizraptor", BLIZRAPTOR, 0xDDE9F2, 0x3A8FC4);
    public static final RegistryObject<Item> BOARFANG_EGG = egg("boarfang", BOARFANG, 0x523620, 0xE6DCC4);
    public static final RegistryObject<Item> GRASICA_EGG = egg("grasica", GRASICA, 0x93683B, 0xF6EEDC);
    public static final RegistryObject<Item> GAMENOS_EGG = egg("gamenos", GAMENOS, 0x30503D, 0xA5A178);
    public static final RegistryObject<Item> BUZZLING_EGG = egg("buzzling", BUZZLING, 0xC9A227, 0x1C1912);
    public static final RegistryObject<Item> YUKIMAMMO_EGG = egg("yukimammo", YUKIMAMMO, 0x7D6656, 0xEFE3C6);
    public static final RegistryObject<Item> HEATGECKO_EGG = egg("heatgecko", HEATGECKO, 0xB04C13, 0xFFAB3D);
    public static final RegistryObject<Item> GANSEKIGANI_EGG = egg("gansekigani", GANSEKIGANI, 0x736A61, 0xBF6741);
    public static final RegistryObject<Item> NUSSUTTO_MOFU_EGG = egg("nussutto_mofu", NUSSUTTO_MOFU, 0x585B64, 0x3E8E3E);

    private SmallMonsters() {
    }

    private static RegistryObject<Item> egg(String id, Supplier<? extends EntityType<? extends Mob>> type, int bg, int fg) {
        RegistryObject<Item> ro = ITEMS.register(id + "_spawn_egg", () -> new ForgeSpawnEggItem(type, bg, fg, new Item.Properties()));
        EGGS.add(ro);
        return ro;
    }

    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(SmallMonsters::attributes);
        modBus.addListener(SmallMonsters::spawnPlacements);
        modBus.addListener(SmallMonsters::tabs);
        MonsterRegistry.register("jagraptor", JAGRAPTOR, JagraptorEntity.PROFILE);
        MonsterRegistry.register("blizraptor", BLIZRAPTOR, BlizraptorEntity.PROFILE);
        MonsterRegistry.register("boarfang", BOARFANG, BoarfangEntity.PROFILE);
        MonsterRegistry.register("grasica", GRASICA, GrasicaEntity.PROFILE);
        MonsterRegistry.register("gamenos", GAMENOS, GamenosEntity.PROFILE);
        MonsterRegistry.register("buzzling", BUZZLING, BuzzlingEntity.PROFILE);
        MonsterRegistry.register("yukimammo", YUKIMAMMO, YukimammoEntity.PROFILE);
        MonsterRegistry.register("heatgecko", HEATGECKO, HeatgeckoEntity.PROFILE);
        MonsterRegistry.register("gansekigani", GANSEKIGANI, GansekiganiEntity.PROFILE);
        MonsterRegistry.register("nussutto_mofu", NUSSUTTO_MOFU, NussuttoMofuEntity.PROFILE);
    }

    private static AttributeSupplier.Builder attrs(double hp, double speed, double knockbackResist, double followRange) {
        return HuntMonster.baseAttributes(hp, speed, knockbackResist).add(Attributes.FOLLOW_RANGE, followRange);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(JAGRAPTOR.get(), attrs(20, 0.32, 0.0, 24).build());
        event.put(BLIZRAPTOR.get(), attrs(26, 0.32, 0.0, 24).build());
        event.put(BOARFANG.get(), attrs(30, 0.28, 0.3, 18).build());
        event.put(GRASICA.get(), attrs(12, 0.30, 0.0, 16).build());
        event.put(GAMENOS.get(), attrs(45, 0.20, 0.9, 20).build());
        event.put(BUZZLING.get(), attrs(10, 0.30, 0.0, 20).build());
        event.put(YUKIMAMMO.get(), attrs(50, 0.22, 0.8, 24).build());
        event.put(HEATGECKO.get(), attrs(18, 0.27, 0.1, 16).build());
        event.put(GANSEKIGANI.get(), attrs(34, 0.22, 0.6, 12).build());
        event.put(NUSSUTTO_MOFU.get(), attrs(14, 0.30, 0.0, 20).build());
    }

    private static void spawnPlacements(SpawnPlacementRegisterEvent event) {
        ground(event, JAGRAPTOR.get());
        ground(event, BLIZRAPTOR.get());
        ground(event, BOARFANG.get());
        ground(event, GRASICA.get());
        ground(event, GAMENOS.get());
        ground(event, YUKIMAMMO.get());
        ground(event, HEATGECKO.get());
        ground(event, GANSEKIGANI.get());
        ground(event, NUSSUTTO_MOFU.get());
        event.register(BUZZLING.get(), SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mob::checkMobSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    private static <T extends Mob> void ground(SpawnPlacementRegisterEvent event, EntityType<T> type) {
        event.register(type, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mob::checkMobSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(HCCreativeTabs.MONSTERS.getKey())) {
            for (RegistryObject<Item> egg : EGGS) event.accept(egg);
        }
    }
}
