package com.eternalrealm.boss.forge;

import com.eternalrealm.boss.BossBrain;
import com.eternalrealm.boss.BossDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;

import java.util.Optional;

/**
 * The body of a boss: the animated boss entity {@code eternalrealm:boss_<id>} when it is registered, otherwise the
 * vanilla body named by the boss definition. Health stays full (the fight keeps its own pool); armor, speed and
 * melee damage come from the brain; bosses are not pushed around and never despawn.
 */
public final class BossBodies {
    private BossBodies() {
    }

    public static Optional<Mob> create(ServerLevel level, BossDefinition definition, BossBrain brain) {
        EntityType<?> type = EntityType.byString("eternalrealm:boss_" + definition.id())
                .or(() -> EntityType.byString(definition.entityType())).orElse(null);
        if (type == null) {
            return Optional.empty();
        }
        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return Optional.empty();
        }
        if (mob instanceof AbstractPiglin piglin) {
            piglin.setImmuneToZombification(true);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setDropChance(slot, 0.0F);
        }
        set(mob, Attributes.MAX_HEALTH, 1000.0);
        set(mob, Attributes.ARMOR, Math.min(30.0, brain.armor()));
        set(mob, Attributes.ATTACK_DAMAGE, brain.meleeDamage(0));
        set(mob, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        set(mob, Attributes.FOLLOW_RANGE, 64.0); // the arenas of the big bosses are up to ~60 blocks wide
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * brain.movementSpeedMultiplier());
        }
        mob.setHealth(mob.getMaxHealth());
        mob.setPersistenceRequired();
        mob.addTag(BossFight.TAG);
        mob.setCustomName(Component.translatable(definition.translationKey())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        mob.setCustomNameVisible(true);
        return Optional.of(mob);
    }

    private static void set(Mob mob, Attribute attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null && Double.isFinite(value) && value >= 0.0) {
            instance.setBaseValue(value);
        }
    }
}
