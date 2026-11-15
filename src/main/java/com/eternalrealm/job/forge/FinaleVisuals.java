package com.eternalrealm.job.forge;

import com.eternalrealm.combat.forge.CombatHits;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.sound.ERSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * The sounds and particles of the finales (秘奥義), named {@code fin_*} in their specs: every client hears them, and
 * with a shader pack (where the skill light is not drawn) the particles alone tell the story. The light itself is
 * {@code job.fx.Finales}. Server thread only.
 */
final class FinaleVisuals {
    /** The seven spirits' colours (fire, ice, thunder, wind, earth, light, darkness). */
    private static final int[] SPIRITS = {0xFF6A1E, 0x7AE0FF, 0xFFE94C, 0x8CFFC8, 0xD8A050, 0xFFF6C0, 0xA060FF};

    private FinaleVisuals() {
    }

    /** Plays the finale visual {@code kind}; false if it is not one. */
    static boolean play(ServerPlayer caster, String kind, List<CombatHits.HitTarget> targets) {
        if (!kind.startsWith("fin_")) {
            return false;
        }
        ServerLevel level = caster.serverLevel();
        Vec3 feet = caster.position();
        Vec3 chest = feet.add(0.0, 1.2, 0.0);
        Vec3 fwd = forward(caster);
        switch (kind) {
            // ---------------------------------------------------------------- 忍者: 八重一閃
            case "fin_eightfold_stance" -> {
                ring(level, ParticleTypes.CLOUD, feet.add(0, 0.1, 0), 2.2, 24, 0.02);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, chest.x, chest.y, chest.z, 20, 0.4, 0.4, 0.4, 0.2);
                sound(level, feet, SoundEvents.ARMOR_EQUIP_CHAIN, 1.0F, 0.6F);
                sound(level, feet, ERSounds.SKILL_SHADOW_WHOOSH.get(), 0.8F, 0.7F);
            }
            case "fin_eightfold_flash" -> {
                double angle = caster.getRandom().nextDouble() * Math.PI;
                Vec3 dir = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
                for (int i = -10; i <= 10; i++) {
                    Vec3 p = chest.add(dir.scale(i * 0.7));
                    level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, chest.x + dir.x * 2.5, chest.y, chest.z + dir.z * 2.5,
                        1, 0.0, 0.0, 0.0, 0.0);
                sound(level, feet, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9F, 1.5F + caster.getRandom().nextFloat() * 0.3F);
                sound(level, feet, ERSounds.SKILL_SLASH.get(), 0.6F, 1.6F);
            }
            case "fin_eightfold_sheathe" -> {
                level.sendParticles(ParticleTypes.END_ROD, chest.x, chest.y, chest.z, 6, 0.2, 0.2, 0.2, 0.02);
                sound(level, feet, SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 1.9F);
                sound(level, feet, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.8F);
            }
            case "fin_eightfold_burst" -> {
                for (CombatHits.HitTarget hit : targets) {
                    Vec3 at = hit.entity().position().add(0.0, hit.entity().getBbHeight() * 0.55, 0.0);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 8, 0.6, 0.6, 0.6, 0.0);
                    level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, chest.x, chest.y, chest.z, 6, 4.0, 0.6, 4.0, 0.0);
                sound(level, feet, SoundEvents.GENERIC_EXPLODE, 0.8F, 1.4F);
                sound(level, feet, ERSounds.SKILL_HEAVY_SLASH.get(), 1.0F, 1.2F);
                sound(level, feet, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.8F);
            }
            // ---------------------------------------------------------------- 死神: 終焉の大鎌
            case "fin_scythe_summon" -> {
                level.sendParticles(ParticleTypes.SOUL, chest.x, chest.y + 1.0, chest.z, 30, 0.8, 1.2, 0.8, 0.05);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, feet.x, feet.y + 0.5, feet.z, 20, 1.0, 0.3, 1.0, 0.02);
                sound(level, feet, SoundEvents.SOUL_ESCAPE, 1.2F, 0.6F);
                sound(level, feet, SoundEvents.WITHER_AMBIENT, 0.5F, 1.5F);
            }
            case "fin_scythe_hook" -> {
                ring(level, ParticleTypes.SCULK_SOUL, feet.add(0, 1.0, 0), 8.5, 36, 0.0);
                sound(level, feet, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
                sound(level, feet, SoundEvents.SOUL_ESCAPE, 1.0F, 1.2F);
            }
            case "fin_scythe_reap" -> {
                ring(level, ParticleTypes.SWEEP_ATTACK, feet.add(0, 1.0, 0), 9.5, 24, 0.0);
                ring(level, ParticleTypes.SOUL_FIRE_FLAME, feet.add(0, 0.3, 0), 9.0, 48, 0.02);
                level.sendParticles(dust(0xC8102C, 2.0F), feet.x, feet.y + 1.0, feet.z, 60, 5.0, 0.5, 5.0, 0.0);
                sound(level, feet, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.45F);
                sound(level, feet, SoundEvents.WITHER_SHOOT, 0.6F, 0.5F);
                sound(level, feet, ERSounds.SKILL_HEAVY_SLASH.get(), 1.0F, 0.7F);
            }
            // ---------------------------------------------------------------- 剣聖: 神剣天墜
            case "fin_holy_sword_call" -> {
                column(level, ParticleTypes.END_ROD, feet, 0.0, 14.0, 28);
                sound(level, feet, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.3F);
            }
            case "fin_holy_sword_raise" -> {
                Vec3 over = feet.add(fwd.scale(5.5));
                column(level, ParticleTypes.END_ROD, over, 2.0, 16.0, 30);
                sound(level, feet, SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.8F);
                sound(level, feet, ERSounds.SKILL_HOLY_CHIME.get(), 1.0F, 0.7F);
            }
            case "fin_holy_sword_fall" -> {
                Vec3 at = feet.add(fwd.scale(5.5));
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 60, 2.5, 0.4, 2.5, 0.25);
                level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.0, at.z, 1, 0.0, 0.0, 0.0, 0.0);
                debris(level, at, 4.0, 40);
                sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.4F);
                sound(level, at, SoundEvents.GENERIC_EXPLODE, 1.2F, 0.8F);
                sound(level, at, SoundEvents.ANVIL_LAND, 0.7F, 0.6F);
            }
            // ---------------------------------------------------------------- 呪剣士: 丑の刻参り
            case "fin_ox_ritual" -> {
                Vec3 at = feet.add(fwd.scale(4.5));
                level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1.5, at.z, 20, 1.0, 1.0, 1.0, 0.02);
                level.sendParticles(ParticleTypes.WITCH, at.x, at.y + 2.5, at.z, 30, 0.6, 0.8, 0.6, 0.05);
                sound(level, at, SoundEvents.BELL_RESONATE, 0.8F, 0.5F);
                sound(level, at, SoundEvents.SOUL_ESCAPE, 1.0F, 0.5F);
            }
            case "fin_ox_nail" -> {
                Vec3 at = feet.add(fwd.scale(4.5)).add(0.0, 1.4, 0.0);
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 20, 0.3, 0.3, 0.3, 0.5);
                level.sendParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 12, 0.4, 0.4, 0.4, 0.1);
                sound(level, at, SoundEvents.ANVIL_LAND, 0.8F, 1.25F);
                sound(level, at, SoundEvents.CHAIN_PLACE, 1.0F, 0.6F);
            }
            case "fin_ox_strike" -> {
                Vec3 at = feet.add(fwd.scale(4.5)).add(0.0, 1.0, 0.0);
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.5, 0.3, 0.5, 0.0);
                level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 40, 1.5, 1.0, 1.5, 0.08);
                level.sendParticles(ParticleTypes.DRAGON_BREATH, at.x, at.y, at.z, 40, 1.2, 0.6, 1.2, 0.02);
                sound(level, at, SoundEvents.ANVIL_LAND, 1.2F, 0.6F);
                sound(level, at, SoundEvents.WITHER_BREAK_BLOCK, 0.8F, 0.6F);
                sound(level, at, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.7F);
            }
            // ---------------------------------------------------------------- 賢者: 蒼焔隕星
            case "fin_azure_call" -> {
                Vec3 at = SkillZones.centerOf(caster, SkillEffect.Placement.LOOK, 22.0);
                ring(level, ParticleTypes.SOUL_FIRE_FLAME, at.add(0.0, 0.2, 0.0), 6.5, 40, 0.01);
                column(level, ParticleTypes.SOUL_FIRE_FLAME, at, 1.0, 14.0, 24);
                sound(level, feet, SoundEvents.BEACON_ACTIVATE, 1.0F, 0.7F);
                sound(level, feet, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.6F);
            }
            case "fin_azure_fall" -> {
                Vec3 at = SkillZones.centerOf(caster, SkillEffect.Placement.LOOK, 22.0);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 14.0, at.z, 60, 1.5, 1.5, 1.5, 0.05);
                sound(level, at, SoundEvents.BLAZE_SHOOT, 1.5F, 0.4F);
                sound(level, at, SoundEvents.FIRECHARGE_USE, 1.2F, 0.5F);
            }
            case "fin_azure_impact" -> {
                Vec3 at = SkillZones.centerOf(caster, SkillEffect.Placement.LOOK, 22.0);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 2, 1.0, 0.2, 1.0, 0.0);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.5, at.z, 160, 4.0, 1.0, 4.0, 0.12);
                debris(level, at, 5.0, 60);
                sound(level, at, SoundEvents.GENERIC_EXPLODE, 2.0F, 0.6F);
                sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.5F, 0.6F);
            }
            // ---------------------------------------------------------------- 冥術士: 骸王顕現
            case "fin_boneking_gather" -> {
                Vec3 heart = feet.add(fwd.scale(3.5)).add(0.0, 1.5, 0.0);
                for (int i = 0; i < 48; i++) {
                    double a = i * 0.7 + caster.getRandom().nextDouble();
                    double r = 9.0 + caster.getRandom().nextDouble() * 6.0;
                    Vec3 from = heart.add(Math.cos(a) * r, caster.getRandom().nextDouble() * 4.0 - 1.0, Math.sin(a) * r);
                    Vec3 v = heart.subtract(from).scale(0.08);
                    level.sendParticles(ParticleTypes.SCULK_SOUL, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
                }
                sound(level, heart, SoundEvents.WARDEN_SONIC_CHARGE, 1.2F, 0.6F);
                sound(level, heart, SoundEvents.SOUL_ESCAPE, 1.0F, 0.4F);
            }
            case "fin_boneking_rise" -> {
                Vec3 at = feet.add(fwd.scale(3.5));
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 4, 1.0, 0.3, 1.0, 0.0);
                level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1.0, at.z, 60, 1.5, 2.0, 1.5, 0.06);
                debris(level, at, 3.0, 50);
                sound(level, at, SoundEvents.WITHER_SPAWN, 0.7F, 1.2F);
                sound(level, at, SoundEvents.GENERIC_EXPLODE, 1.0F, 0.7F);
            }
            // ---------------------------------------------------------------- 狙撃手: 魔弾三連
            case "fin_rifle_form" -> {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, chest.x + fwd.x * 0.6, chest.y + 0.2,
                        chest.z + fwd.z * 0.6, 20, 0.3, 0.2, 0.3, 0.1);
                sound(level, feet, SoundEvents.ARMOR_EQUIP_NETHERITE, 1.0F, 0.8F);
                sound(level, feet, SoundEvents.CROSSBOW_LOADING_END, 1.0F, 0.6F);
            }
            case "fin_rifle_shot" -> {
                Vec3 eye = caster.getEyePosition();
                Vec3 look = caster.getLookAngle();
                Vec3 muzzle = eye.add(look.scale(1.6));
                level.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.15, 0.15, 0.15, 0.02);
                for (int i = 2; i < 48; i += 2) {
                    Vec3 p = eye.add(look.scale(i));
                    level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
                sound(level, feet, SoundEvents.GENERIC_EXPLODE, 0.9F, 1.6F);
                sound(level, feet, SoundEvents.FIREWORK_ROCKET_BLAST, 1.5F, 0.5F);
                sound(level, feet, SoundEvents.CROSSBOW_SHOOT, 1.0F, 0.5F);
            }
            case "fin_rifle_unform" -> {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, chest.x + fwd.x * 0.6, chest.y + 0.2,
                        chest.z + fwd.z * 0.6, 12, 0.3, 0.2, 0.3, 0.1);
                sound(level, feet, SoundEvents.ARMOR_EQUIP_CHAIN, 1.0F, 1.2F);
            }
            // ---------------------------------------------------------------- 精霊弓士: 七精霊の神弓
            case "fin_spirits_call" -> {
                for (int i = 0; i < SPIRITS.length; i++) {
                    double a = Math.PI * 2.0 * i / SPIRITS.length;
                    Vec3 at = chest.add(Math.cos(a) * 2.6, 0.8 + Math.sin(a * 2.0) * 0.4, Math.sin(a) * 2.6);
                    level.sendParticles(dust(SPIRITS[i], 1.8F), at.x, at.y, at.z, 16, 0.2, 0.2, 0.2, 0.0);
                }
                sound(level, feet, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.2F);
                sound(level, feet, SoundEvents.ALLAY_AMBIENT_WITH_ITEM, 1.0F, 1.1F);
            }
            case "fin_spirits_draw" -> {
                Vec3 bow = chest.add(fwd.scale(1.2));
                for (int i = 0; i < SPIRITS.length; i++) {
                    level.sendParticles(dust(SPIRITS[i], 1.4F), bow.x, bow.y, bow.z, 10, 0.6, 0.6, 0.6, 0.0);
                }
                sound(level, feet, SoundEvents.CROSSBOW_LOADING_MIDDLE, 1.0F, 0.5F);
                sound(level, feet, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.4F);
            }
            case "fin_spirits_loose" -> {
                Vec3 eye = caster.getEyePosition();
                Vec3 look = caster.getLookAngle();
                for (int i = 1; i < 40; i++) {
                    Vec3 p = eye.add(look.scale(i * 0.8));
                    level.sendParticles(dust(SPIRITS[i % SPIRITS.length], 2.0F), p.x, p.y, p.z, 2, 0.15, 0.15, 0.15,
                            0.0);
                }
                sound(level, feet, ERSounds.WEAPON_SKILL_ARROW_LAUNCH.get(), 1.2F, 0.8F);
                sound(level, feet, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2F, 0.7F);
                sound(level, feet, ERSounds.SKILL_ARROW_RELEASE.get(), 1.0F, 0.6F);
            }
            case "fin_spirits_wake" -> {
                for (CombatHits.HitTarget hit : targets) {
                    Vec3 at = hit.entity().position().add(0.0, hit.entity().getBbHeight() * 0.5, 0.0);
                    level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.15);
                }
                sound(level, feet, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.2F, 1.0F);
            }
            // ---------------------------------------------------------------- 聖騎士: 天地断つ聖剣
            case "fin_giant_sword_grow" -> {
                column(level, ParticleTypes.END_ROD, feet.add(fwd.scale(0.6)), 1.5, 9.0, 24);
                sound(level, feet, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.0F);
                sound(level, feet, SoundEvents.ANVIL_USE, 0.6F, 0.6F);
            }
            case "fin_giant_sword_strike" -> {
                Vec3 at = feet.add(fwd.scale(6.0));
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
                for (int i = 2; i <= 16; i++) {
                    Vec3 p = feet.add(fwd.scale(i));
                    debris(level, p, 0.6, 6);
                    level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 0.3, p.z, 3, 0.2, 0.6, 0.2, 0.05);
                }
                sound(level, at, SoundEvents.GENERIC_EXPLODE, 1.5F, 0.7F);
                sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 0.8F);
                sound(level, at, SoundEvents.ANVIL_LAND, 0.8F, 0.5F);
            }
            // ---------------------------------------------------------------- 審問官: 神判
            case "fin_verdict_scales" -> {
                ring(level, ParticleTypes.END_ROD, feet.add(0.0, 7.0, 0.0), 4.5, 40, 0.0);
                sound(level, feet, SoundEvents.BEACON_ACTIVATE, 1.0F, 0.9F);
                sound(level, feet, SoundEvents.BELL_BLOCK, 1.2F, 0.6F);
            }
            case "fin_verdict_judge" -> {
                for (CombatHits.HitTarget hit : targets) {
                    column(level, ParticleTypes.END_ROD, hit.entity().position(), 0.4, 12.0, 14);
                }
                sound(level, feet, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.5F);
                sound(level, feet, SoundEvents.BELL_BLOCK, 1.2F, 0.8F);
            }
            case "fin_verdict_execute" -> {
                for (CombatHits.HitTarget hit : targets) {
                    Vec3 at = hit.entity().position().add(0.0, hit.entity().getBbHeight() * 0.5, 0.0);
                    level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 16, 0.4, 0.5, 0.4, 0.3);
                }
                sound(level, feet, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2F, 1.0F);
                sound(level, feet, SoundEvents.GENERIC_EXPLODE, 0.8F, 1.1F);
            }
            // ---------------------------------------------------------------- 守護士: 金剛不壊
            case "fin_aegis_raise" -> {
                level.sendParticles(ParticleTypes.WAX_ON, chest.x + fwd.x, chest.y, chest.z + fwd.z, 30, 0.8, 1.2, 0.8,
                        0.05);
                sound(level, feet, SoundEvents.ARMOR_EQUIP_GOLD, 1.0F, 0.7F);
                sound(level, feet, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.1F);
            }
            case "fin_aegis_ward" -> {
                for (ServerPlayer ally : SkillEffectsExecutor.allies(caster, 16.0)) {
                    ring(level, ParticleTypes.WAX_ON, ally.position().add(0.0, 1.0, 0.0), 1.0, 14, 0.0);
                    level.sendParticles(ParticleTypes.END_ROD, ally.getX(), ally.getY() + 2.2, ally.getZ(), 6, 0.3,
                            0.1, 0.3, 0.01);
                }
                sound(level, feet, SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.2F);
                sound(level, feet, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.8F);
            }
            case "fin_aegis_bash" -> {
                Vec3 at = feet.add(fwd.scale(3.0)).add(0.0, 1.0, 0.0);
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 4, 1.5, 0.4, 1.5, 0.0);
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 40, 2.0, 0.6, 2.0, 0.6);
                sound(level, at, SoundEvents.ANVIL_LAND, 1.0F, 0.7F);
                sound(level, at, SoundEvents.SHIELD_BLOCK, 1.5F, 0.6F);
                sound(level, at, SoundEvents.GENERIC_EXPLODE, 1.0F, 0.9F);
            }
            // ---------------------------------------------------------------- 司教: 天上の大聖堂
            case "fin_cathedral_rise" -> {
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2.0 * i / 8.0;
                    column(level, ParticleTypes.END_ROD, feet.add(Math.cos(a) * 9.0, 0.0, Math.sin(a) * 9.0), 0.3,
                            8.0, 10);
                }
                sound(level, feet, SoundEvents.BEACON_ACTIVATE, 1.0F, 0.8F);
                sound(level, feet, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.5F);
            }
            case "fin_cathedral_bell" -> {
                ring(level, ParticleTypes.END_ROD, feet.add(0.0, 0.4, 0.0), 9.5, 48, 0.0);
                level.sendParticles(ParticleTypes.NOTE, feet.x, feet.y + 9.0, feet.z, 6, 1.5, 0.5, 1.5, 0.5);
                sound(level, feet, SoundEvents.BELL_BLOCK, 2.0F, 0.5F);
                sound(level, feet, SoundEvents.BELL_RESONATE, 1.0F, 1.0F);
            }
            case "fin_cathedral_window" -> {
                column(level, ParticleTypes.END_ROD, feet, 4.0, 16.0, 60);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, feet.x, feet.y + 1.0, feet.z, 40, 6.0, 1.0, 6.0, 0.0);
                sound(level, feet, SoundEvents.BEACON_POWER_SELECT, 1.2F, 0.7F);
                sound(level, feet, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 0.8F);
                sound(level, feet, ERSounds.SKILL_HEAL.get(), 1.0F, 0.8F);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static Vec3 forward(ServerPlayer caster) {
        double yaw = Math.toRadians(caster.getYRot());
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F), size);
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int count,
                             double speed) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2.0 * i / count;
            level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius,
                    1, 0.0, 0.0, 0.0, speed);
        }
    }

    private static void column(ServerLevel level, ParticleOptions particle, Vec3 base, double spread, double height,
                               int count) {
        for (int i = 0; i < count; i++) {
            double y = height * i / Math.max(1, count - 1);
            level.sendParticles(particle, base.x, base.y + y, base.z, 1, spread * 0.3, 0.1, spread * 0.3, 0.0);
        }
    }

    /** Chips of the ground flying up. */
    private static void debris(ServerLevel level, Vec3 at, double spread, int count) {
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2, at.z, count,
                    spread * 0.5, 0.2, spread * 0.5, 0.2);
        }
    }
}
