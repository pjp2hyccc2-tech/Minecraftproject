package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.CombatEvent;
import com.eternalrealm.combat.HitGeometry;
import com.eternalrealm.combat.HitShape;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.combat.AttackOrigin;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side sounds and particles. They are broadcast to nearby players, which is how other players see and hear
 * someone else's combat. Sounds the acting player already predicted locally exclude that player.
 */
final class CombatEffects {
    private CombatEffects() {
    }

    /** Swing / dodge sound for everyone around except the actor (who already heard the predicted sound). */
    static void actionStarted(ServerPlayer player, CombatEvent.ActionStarted start) {
        ActionKind kind = start.action().kind();
        SoundEvent sound;
        float pitch;
        float volume = 0.8F;
        switch (kind) {
            case LIGHT -> {
                if (start.weapon().type() == WeaponType.BOW) {
                    sound = SoundEvents.ARROW_SHOOT;
                } else if (start.weapon().type() == WeaponType.STAFF) {
                    sound = SoundEvents.EVOKER_CAST_SPELL;
                } else {
                    boolean thrust = start.action().frames().get(0).shape().kind() == HitShape.Kind.LINE;
                    sound = thrust ? SoundEvents.TRIDENT_THROW : SoundEvents.PLAYER_ATTACK_SWEEP;
                }
                pitch = 1.0F + 0.08F * Math.max(0, start.comboStep());
                volume = 0.6F;
            }
            case HEAVY -> {
                sound = SoundEvents.PLAYER_ATTACK_SWEEP;
                pitch = 0.7F;
            }
            case SKILL -> {
                sound = SoundEvents.PLAYER_ATTACK_SWEEP;
                pitch = 0.55F;
                volume = 1.0F;
            }
            case DODGE -> {
                sound = SoundEvents.PLAYER_ATTACK_NODAMAGE;
                pitch = 1.6F;
                volume = 0.5F;
            }
            default -> {
                return;
            }
        }
        player.level().playSound(player, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS,
                volume, pitch);
        if (kind == ActionKind.SKILL) {
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(),
                    20, 0.6, 0.6, 0.6, 0.4);
        }
    }

    /** Visualises the swept volume of a hit frame (whether or not something was hit). */
    static void hitFrame(ServerPlayer attacker, CombatEvent.Hit hit) {
        ServerLevel level = attacker.serverLevel();
        HitShape shape = hit.frame().shape();
        double y = attacker.getY() + attacker.getBbHeight() * 0.5;
        if (shape.offsetForward() > 0.0) {
            Vec3 eye = attacker.getEyePosition();
            double[] center = HitGeometry.circleCenter(shape,
                    new AttackOrigin(eye.x, eye.y, eye.z, attacker.getYRot(), attacker.getXRot()));
            level.sendParticles(ParticleTypes.CRIT, center[0], attacker.getY() + 0.5, center[1], 30,
                    shape.reach() * 0.5, 0.2, shape.reach() * 0.5, 0.05);
            return;
        }
        switch (shape.kind()) {
            case ARC -> {
                double[] look = HitGeometry.look(attacker.getYRot(), 0.0);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, attacker.getX() + look[0], y, attacker.getZ() + look[2],
                        0, look[0], 0.0, look[2], 0.0);
            }
            case CIRCLE -> {
                for (int i = 0; i < 6; i++) {
                    double angle = Math.PI * 2.0 * i / 6.0;
                    double radius = Math.min(2.0, shape.reach() * 0.6);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, attacker.getX() + Math.cos(angle) * radius, y,
                            attacker.getZ() + Math.sin(angle) * radius, 0, 0.0, 0.0, 0.0, 0.0);
                }
            }
            case LINE -> {
                double[] look = HitGeometry.look(attacker.getYRot(), attacker.getXRot());
                double eyeY = attacker.getEyeY() - 0.2;
                for (double t = 1.0; t <= shape.reach(); t += 0.75) {
                    level.sendParticles(ParticleTypes.CRIT, attacker.getX() + look[0] * t, eyeY + look[1] * t,
                            attacker.getZ() + look[2] * t, 1, 0.02, 0.02, 0.02, 0.0);
                }
            }
        }
    }

    /** Impact feedback on a damaged target, for everyone (including the attacker). */
    static void targetHit(ServerPlayer attacker, LivingEntity target, boolean critical, ActionKind kind) {
        ServerLevel level = attacker.serverLevel();
        SoundEvent sound;
        if (critical) {
            sound = SoundEvents.PLAYER_ATTACK_CRIT;
        } else if (kind == ActionKind.HEAVY || kind == ActionKind.SKILL) {
            sound = SoundEvents.PLAYER_ATTACK_KNOCKBACK;
        } else {
            sound = SoundEvents.PLAYER_ATTACK_STRONG;
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), sound, SoundSource.PLAYERS, 0.9F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        double y = target.getY() + target.getBbHeight() * 0.6;
        if (critical) {
            level.sendParticles(ParticleTypes.CRIT, target.getX(), y, target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
        }
        if (kind == ActionKind.SKILL) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), y, target.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
        }
    }

    static void dodged(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE,
                SoundSource.PLAYERS, 0.6F, 1.8F);
        player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3, player.getZ(),
                6, 0.3, 0.1, 0.3, 0.02);
    }

    static void parried(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 1.0F, 1.6F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, 0.25F, 1.9F);
        double[] look = HitGeometry.look(player.getYRot(), 0.0);
        player.serverLevel().sendParticles(ParticleTypes.CRIT, player.getX() + look[0] * 0.8, player.getEyeY() - 0.3,
                player.getZ() + look[2] * 0.8, 16, 0.2, 0.2, 0.2, 0.4);
    }

    static void guarded(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    static void guardBroken(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BREAK,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
