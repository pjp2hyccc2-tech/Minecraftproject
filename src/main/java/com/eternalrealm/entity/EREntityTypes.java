package com.eternalrealm.entity;

import com.eternalrealm.core.ModInfo;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Entity types of the enemy and boss module. The integrator wires it with one call:
 * {@code EREntityTypes.register(modBus)} in the mod constructor.
 *
 * <p>Spawning: {@code HumanoidEnemy e = EREntityTypes.HUMANOID_ENEMY.get().create(level); e.setVariant("ghoul");}
 * then position, attributes and {@code level.addFreshEntity(e)}; bosses: {@code boss("b3").get().create(level)}.</p>
 */
public final class EREntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ModInfo.MOD_ID);

    public static final RegistryObject<EntityType<HumanoidEnemy>> HUMANOID_ENEMY = ENTITY_TYPES.register("humanoid_enemy",
            () -> EntityType.Builder.<HumanoidEnemy>of(HumanoidEnemy::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).build(id("humanoid_enemy")));
    public static final RegistryObject<EntityType<BeastEnemy>> BEAST_ENEMY = ENTITY_TYPES.register("beast_enemy",
            () -> EntityType.Builder.<BeastEnemy>of(BeastEnemy::new, MobCategory.MONSTER)
                    .sized(0.9F, 0.9F).clientTrackingRange(8).build(id("beast_enemy")));
    public static final RegistryObject<EntityType<GolemEnemy>> GOLEM_ENEMY = ENTITY_TYPES.register("golem_enemy",
            () -> EntityType.Builder.<GolemEnemy>of(GolemEnemy::new, MobCategory.MONSTER)
                    .sized(1.3F, 2.5F).clientTrackingRange(10).build(id("golem_enemy")));
    public static final RegistryObject<EntityType<FlyerEnemy>> FLYER_ENEMY = ENTITY_TYPES.register("flyer_enemy",
            () -> EntityType.Builder.<FlyerEnemy>of(FlyerEnemy::new, MobCategory.MONSTER)
                    .sized(0.7F, 1.5F).clientTrackingRange(8).build(id("flyer_enemy")));
    public static final RegistryObject<EntityType<EnemyBolt>> ENEMY_BOLT = ENTITY_TYPES.register("enemy_bolt",
            () -> EntityType.Builder.<EnemyBolt>of(EnemyBolt::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F).clientTrackingRange(4).updateInterval(10).noSave().build(id("enemy_bolt")));

    private static final Map<BossKind, RegistryObject<EntityType<ERBoss>>> BOSSES = new EnumMap<>(BossKind.class);

    public static final RegistryObject<EntityType<ERBoss>> BOSS_B1 = boss(BossKind.B1);
    public static final RegistryObject<EntityType<ERBoss>> BOSS_B2 = boss(BossKind.B2);
    public static final RegistryObject<EntityType<ERBoss>> BOSS_B3 = boss(BossKind.B3);
    public static final RegistryObject<EntityType<ERBoss>> BOSS_B4 = boss(BossKind.B4);
    public static final RegistryObject<EntityType<ERBoss>> BOSS_B5 = boss(BossKind.B5);
    public static final RegistryObject<EntityType<ERBoss>> BOSS_B6 = boss(BossKind.B6);
    public static final RegistryObject<EntityType<ERBoss>> BOSS_B7 = boss(BossKind.B7);
    public static final RegistryObject<EntityType<ERBoss>> BOSS_B8 = boss(BossKind.B8);
    public static final RegistryObject<EntityType<ERBoss>> BOSS_FINAL = boss(BossKind.FINAL);

    private EREntityTypes() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }

    private static String id(String path) {
        return ModInfo.MOD_ID + ":" + path;
    }

    private static RegistryObject<EntityType<ERBoss>> boss(BossKind kind) {
        RegistryObject<EntityType<ERBoss>> object = ENTITY_TYPES.register(kind.typeName(), () -> {
            EntityType.Builder<ERBoss> builder = EntityType.Builder.<ERBoss>of(
                            (type, level) -> new ERBoss(type, level, kind), MobCategory.MONSTER)
                    .sized(kind.width(), kind.height()).clientTrackingRange(10);
            if (kind.fireImmune()) {
                builder = builder.fireImmune();
            }
            return builder.build(id(kind.typeName()));
        });
        BOSSES.put(kind, object);
        return object;
    }

    /** The entity type of a boss. */
    public static RegistryObject<EntityType<ERBoss>> boss(String bossId) {
        BossKind kind = BossKind.byId(bossId).orElseThrow(() -> new IllegalArgumentException("unknown boss " + bossId));
        return BOSSES.get(kind);
    }

    public static RegistryObject<EntityType<ERBoss>> bossType(BossKind kind) {
        return BOSSES.get(kind);
    }

    /** All boss types in BossKind order. */
    public static List<RegistryObject<EntityType<ERBoss>>> bosses() {
        List<RegistryObject<EntityType<ERBoss>>> list = new ArrayList<>();
        for (BossKind kind : BossKind.values()) {
            list.add(BOSSES.get(kind));
        }
        return list;
    }

    /** The entity type of an enemy rig. */
    public static RegistryObject<? extends EntityType<? extends ERMonster>> enemy(EnemyRig rig) {
        return switch (rig) {
            case HUMANOID -> HUMANOID_ENEMY;
            case BEAST -> BEAST_ENEMY;
            case GOLEM -> GOLEM_ENEMY;
            case FLYER -> FLYER_ENEMY;
        };
    }
}
