package com.eternalrealm.entity;

import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.Bosses;
import com.eternalrealm.core.ModInfo;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Default attributes. The spawner is expected to set MAX_HEALTH, ATTACK_DAMAGE, ARMOR and MOVEMENT_SPEED from its
 * templates; these defaults keep unconfigured spawns sane. Boss damage and armor come from the boss module.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EREntityAttributes {
    /** Vanilla's upper bound of MAX_HEALTH. */
    public static final double BOSS_DEFAULT_HEALTH = 1024.0;

    private EREntityAttributes() {
    }

    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(EREntityTypes.HUMANOID_ENEMY.get(), enemy(24.0, 4.0, 2.0, 0.26).build());
        event.put(EREntityTypes.BEAST_ENEMY.get(), enemy(16.0, 3.0, 0.0, 0.32).build());
        event.put(EREntityTypes.GOLEM_ENEMY.get(), enemy(60.0, 8.0, 6.0, 0.22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6).build());
        event.put(EREntityTypes.FLYER_ENEMY.get(), enemy(18.0, 3.0, 0.0, 0.3)
                .add(Attributes.FLYING_SPEED, 0.5).build());
        for (BossKind kind : BossKind.values()) {
            event.put(EREntityTypes.bossType(kind).get(), boss(kind).build());
        }
    }

    private static AttributeSupplier.Builder enemy(double health, double damage, double armor, double speed) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.ARMOR, armor)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    private static AttributeSupplier.Builder boss(BossKind kind) {
        BossDefinition definition = Bosses.byId(kind.id()).orElse(null);
        double damage = definition != null ? definition.damage() : 10.0;
        double armor = definition != null ? definition.armor() : 8.0;
        double speed = switch (kind.rig()) {
            case KING -> 0.28;
            case QUEEN -> 0.27;
            case DRAGON -> 0.26;
            case TREANT -> 0.22;
            case COLOSSUS -> 0.21;
        };
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, BOSS_DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.ARMOR, armor)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }
}
