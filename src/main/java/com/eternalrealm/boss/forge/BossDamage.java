package com.eternalrealm.boss.forge;

import com.eternalrealm.boss.AbilityKind;
import com.eternalrealm.boss.BossTexts;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.DamageCalculator;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.server.ServerContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

/**
 * Damage of boss abilities through the normal defense pipeline, with the defense rules of the kind that hits:
 * <ul>
 *     <li>dodgeable and guardable: a plain mob attack of the boss;</li>
 *     <li>dodgeable only (arena-wide): {@code eternalrealm:boss_unguardable}, caused by the boss but tagged
 *         {@code bypasses_shield}, so guard and parry do not apply while the dodge still does;</li>
 *     <li>neither (stack circles, safe zones) and lasting hazards: {@code eternalrealm:boss_unavoidable} /
 *         {@code boss_hazard} without an attacker, which the dodge cannot avoid. The combat pipeline only applies the
 *         VIT reduction to hits with an attacker, so it is applied here.</li>
 * </ul>
 * If the damage types are missing (a data pack removed them) the plain mob attack is used.
 */
final class BossDamage {
    private BossDamage() {
    }

    /** One hit of {@code kind} on {@code player}. @return whether it landed (false: dodged, immune...) */
    static boolean hit(ServerLevel level, Mob body, ServerPlayer player, AbilityKind kind, double damage) {
        if (!(damage > 0.0)) {
            return false;
        }
        if (kind.dodgeable() && kind.guardable()) {
            return player.hurt(level.damageSources().mobAttack(body), (float) damage);
        }
        if (kind.dodgeable()) {
            Optional<Holder.Reference<DamageType>> type = type(level, BossTexts.UNGUARDABLE);
            DamageSource source = type.isPresent() ? new DamageSource(type.get(), body, body)
                    : level.damageSources().mobAttack(body);
            return player.hurt(source, (float) damage);
        }
        return unavoidable(level, body, player, BossTexts.UNAVOIDABLE, damage);
    }

    /** One pulse of a lasting hazard. */
    static boolean hazard(ServerLevel level, Mob body, ServerPlayer player, double damage) {
        return damage > 0.0 && unavoidable(level, body, player, BossTexts.HAZARD, damage);
    }

    private static boolean unavoidable(ServerLevel level, Mob body, ServerPlayer player, String typeId,
                                       double damage) {
        Optional<Holder.Reference<DamageType>> type = type(level, typeId);
        if (type.isEmpty()) {
            return player.hurt(level.damageSources().mobAttack(body), (float) damage);
        }
        double reduced = ServerContext.current()
                .flatMap(context -> context.combat().service().get(player.getUUID()))
                .map(Combatant::stats).map(CombatStats::damageReduction)
                .map(reduction -> DamageCalculator.reduce(damage, reduction)).orElse(damage);
        return player.hurt(new DamageSource(type.get()), (float) reduced);
    }

    private static Optional<Holder.Reference<DamageType>> type(ServerLevel level, String id) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(ModInfo.MOD_ID, id));
        return level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(key);
    }
}
