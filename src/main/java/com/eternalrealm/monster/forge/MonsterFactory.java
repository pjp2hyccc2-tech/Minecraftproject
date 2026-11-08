package com.eternalrealm.monster.forge;

import com.eternalrealm.monster.MonsterRole;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.MonsterStats;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.SpawnHints;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * Builds a monster of the monster module: the template's vanilla body without vanilla's spawn randomisation (no
 * babies, jockeys or random armour), equipped from its {@link SpawnHints}, with health, armor, damage and speed of
 * its level ({@link MonsterSpawn#stats()}), its name plate and the {@link MonsterTags}.
 */
public final class MonsterFactory {
    private static final double MIN_FOLLOW_RANGE = 24.0;

    private MonsterFactory() {
    }

    /** Creates and adds the monster at the position; empty if the body cannot be built. */
    public static Optional<Mob> spawn(ServerLevel level, MonsterSpawn spawn, double x, double y, double z, float yaw) {
        Optional<Mob> mob = create(level, spawn, true);
        mob.ifPresent(body -> {
            body.moveTo(x, y, z, yaw, 0.0F);
            body.setYHeadRot(yaw);
            level.addFreshEntity(body);
        });
        return mob;
    }

    /** The configured body, not yet in the level. */
    public static Optional<Mob> create(ServerLevel level, MonsterSpawn spawn, boolean rewards) {
        MonsterTemplate template = spawn.template();
        Optional<EntityType<?>> type = EntityType.byString(template.baseEntityType());
        if (type.isEmpty()) {
            return Optional.empty();
        }
        Entity entity = type.get().create(level);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return Optional.empty();
        }
        SpawnHints hints = template.spawnHints();
        if (mob instanceof Slime slime && hints.size() > 0) {
            slime.setSize(hints.size(), true); // resets max health, speed and damage: set ours afterwards
        }
        if (mob instanceof AbstractPiglin piglin) {
            piglin.setImmuneToZombification(true);
        }
        equip(mob, EquipmentSlot.HEAD, hints.headItemId());
        equip(mob, EquipmentSlot.MAINHAND, hints.mainHandItemId());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setDropChance(slot, 0.0F);
        }
        MonsterStats stats = spawn.stats();
        setBase(mob, Attributes.MAX_HEALTH, stats.health());
        setBase(mob, Attributes.ARMOR, stats.armor());
        setBase(mob, Attributes.ATTACK_DAMAGE, stats.damage());
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * stats.speed());
        }
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null && follow.getBaseValue() < MIN_FOLLOW_RANGE) {
            follow.setBaseValue(MIN_FOLLOW_RANGE);
        }
        mob.setHealth(mob.getMaxHealth());
        MonsterTags.mark(mob, template.id(), spawn.level(), stats.damage(), rewards);
        mob.setCustomName(nameplate(template, spawn.level()));
        mob.setCustomNameVisible(template.role() == MonsterRole.ELITE);
        return Optional.of(mob);
    }

    /** "Lv12 森グモ" in the role's colour (elites gold, minions grey). */
    public static MutableComponent nameplate(MonsterTemplate template, int level) {
        ChatFormatting color = switch (template.role()) {
            case ELITE -> ChatFormatting.GOLD;
            case MINION -> ChatFormatting.GRAY;
            default -> ChatFormatting.WHITE;
        };
        return Component.translatable(template.role().nameplateKey(), level,
                Component.translatable(template.translationKey())).withStyle(color);
    }

    private static void equip(Mob mob, EquipmentSlot slot, String itemId) {
        if (itemId == null) {
            return;
        }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item == null) {
            return;
        }
        ItemStack stack = new ItemStack(item);
        if (stack.isDamageableItem()) {
            stack.getOrCreateTag().putBoolean("Unbreakable", true); // the sun must not wear the helmet away
        }
        mob.setItemSlot(slot, stack);
    }

    private static void setBase(Mob mob, Attribute attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null && Double.isFinite(value) && value > 0.0) {
            instance.setBaseValue(value);
        }
    }
}
