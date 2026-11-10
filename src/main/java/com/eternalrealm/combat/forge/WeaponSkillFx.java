package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.CombatEvent;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponSkillLooks;
import com.eternalrealm.combat.WeaponSkillLooks.Stage;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.item.WeaponArts;
import com.eternalrealm.item.WeaponDesign;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillTheme;
import com.eternalrealm.sound.ERSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The staging of the weapon skill of each weapon type (the Z key), for everyone around: a wind-up that charges on the
 * weapon with its own sound, a big readable shape for every hit (a ring of wind, a shockwave with ground cracks,
 * crossing slashes, a drilling thrust line, a magic circle and a star bolt that bursts, a shock ring and a streak),
 * an impact burst on every target and a short after-image. Colours follow the element of a held named weapon, else
 * the weapon type ({@link WeaponSkillLooks}). Purely visual and audible: hits and damage are the combat rules'.
 * Weapon arts of boss weapons and job skills keep their own effects.
 */
public final class WeaponSkillFx {
    /** Players whose weapon skill is out (until the tick): their hits get the skill impact. */
    private static final Map<UUID, Long> WINDOWS = new HashMap<>();
    private static final List<Scheduled> QUEUE = new ArrayList<>();
    private static final int DARK = 0x1A1622;

    private record Scheduled(ServerLevel level, long due, Runnable effect) {
    }

    private WeaponSkillFx() {
    }

    /** Whether an action is a weapon type's own skill (not a job skill, not a boss weapon's art). */
    public static boolean isWeaponSkill(ActionDefinition action) {
        return action.kind() == ActionKind.SKILL && Jobs.skillOfAction(action.id()).isEmpty()
                && WeaponArts.byId(action.id()).isEmpty();
    }

    /** The sound a weapon skill's wind-up makes (also predicted by the acting client). */
    public static SoundEvent chargeSound(WeaponType type) {
        return ERSounds.weaponSkillCharge(type).get();
    }

    // ------------------------------------------------------------------ hooks of the combat driver

    /** A new action: a weapon skill starts its wind-up; anything else closes the impact window. */
    static void started(ServerPlayer player, WeaponProfile weapon, ActionDefinition action) {
        if (!isWeaponSkill(action)) {
            WINDOWS.remove(player.getUUID());
            return;
        }
        ServerLevel level = player.serverLevel();
        WINDOWS.put(player.getUUID(), level.getGameTime() + action.durationTicks() + 60L);
        // the acting player already hears the predicted wind-up
        level.playSound(player, player.getX(), player.getY(), player.getZ(), chargeSound(weapon.type()),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        Look look = look(player, weapon.type());
        Vec3 hand = hand(player);
        burst(level, ParticleTypes.END_ROD, hand, 6, 0.15, 0.04);
        burst(level, dust(look.rgb, 1.2F), hand, 8, 0.25, 0.0);
    }

    /** Every tick of a weapon skill: the wind-up before the first hit, after-images right after each hit. */
    static void tick(ServerPlayer player, Combatant combatant, long now) {
        ActionDefinition action = combatant.currentAction().orElse(null);
        WeaponProfile weapon = combatant.weapon();
        if (action == null || weapon == null || !isWeaponSkill(action)) {
            return;
        }
        int age = (int) (now - combatant.actionStartTick());
        int windUp = WeaponSkillLooks.windUpTicks(weapon);
        Look look = look(player, weapon.type());
        if (age >= 0 && age < windUp) {
            windUp(player, look, age, windUp);
        }
    }

    /** A hit (or launch) frame of a weapon skill: its big shape and sound. */
    static void frame(ServerPlayer player, CombatEvent.Hit hit) {
        WeaponProfile weapon = hit.weapon();
        Look look = look(player, weapon.type());
        int[] ticks = WeaponSkillLooks.hitTicks(weapon);
        int index = Math.max(0, hit.frameIndex());
        boolean last = index >= ticks.length - 1;
        switch (look.stage) {
            case WHIRL -> whirl(player, look, hit.frame().shape().reach());
            case QUAKE -> quake(player, look, hit.frame().shape().reach());
            case DANCE -> dance(player, look, index, last);
            case DRILL -> drill(player, look, hit.frame().shape().reach(), index, last);
            case SIGIL -> sigilLaunch(player, look);
            case STREAK -> streak(player, look);
        }
    }

    /** A target damaged while the attacker's weapon skill is out: an impact burst and a layered sound. */
    static void impact(ServerPlayer attacker, LivingEntity target, boolean critical) {
        Long until = WINDOWS.get(attacker.getUUID());
        ServerLevel level = attacker.serverLevel();
        if (until == null || level.getGameTime() > until) {
            return;
        }
        WeaponType type = WeaponItem.profileOf(attacker.getMainHandItem()).type();
        Look look = look(attacker, type);
        Vec3 at = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ());
        level.playSound(null, at.x, at.y, at.z, ERSounds.WEAPON_SKILL_IMPACT.get(), SoundSource.PLAYERS, 1.0F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        burst(level, dust(look.rgb, 1.6F), at, 16, 0.4, 0.0);
        burst(level, ParticleTypes.ENCHANTED_HIT, at, 8, 0.3, 0.3);
        burst(level, dust(look.accent, 1.0F), at, 8, 0.25, 0.0);
        switch (look.stage) {
            case WHIRL -> level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            case QUAKE -> {
                burst(level, ground(level, target.blockPosition()), at.add(0.0, -0.4, 0.0), 14, 0.4, 0.2);
                burst(level, ParticleTypes.POOF, at, 4, 0.3, 0.05);
            }
            case DANCE -> burst(level, ParticleTypes.CRIT, at, 10, 0.35, 0.4);
            case DRILL -> {
                Vec3 through = attacker.getLookAngle();
                for (int i = 1; i <= 6; i++) {
                    Vec3 p = at.add(through.scale(0.25 * i));
                    level.sendParticles(dust(look.rgb, 1.2F), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
                }
            }
            case SIGIL -> burst(level, ERParticles.RUNE.get(), at, 4, 0.3, 0.05);
            case STREAK -> {
                burst(level, ParticleTypes.CRIT, at, 8, 0.3, 0.4);
                burst(level, ParticleTypes.END_ROD, at, 3, 0.2, 0.05);
            }
        }
        if (critical) {
            level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** The star sigil bolt bursts: a magic circle on the ground, a star, a column of light and a second pulse. */
    public static void sigilBurst(ServerPlayer owner, ServerLevel level, Vec3 at, double radius) {
        Look look = look(owner, WeaponType.STAFF);
        level.playSound(null, at.x, at.y, at.z, ERSounds.WEAPON_SKILL_SIGIL_BURST.get(), SoundSource.PLAYERS, 1.4F, 1.0F);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 0.4, 0.2, 0.4, 0.0);
        double y = at.y + 0.1;
        ring(level, dust(look.rgb, 1.8F), at.x, y, at.z, radius, 44);
        ring(level, dust(look.accent, 1.2F), at.x, y, at.z, radius * 0.62, 26);
        star(level, dust(look.accent, 1.1F), at.x, y, at.z, radius * 0.62, 1.0, level.getGameTime() * 0.05);
        for (int i = 0; i < 18; i++) {
            double angle = Math.PI * 2.0 * i / 18.0;
            level.sendParticles(ParticleTypes.END_ROD, at.x + Math.cos(angle) * 0.4, y, at.z + Math.sin(angle) * 0.4,
                    0, Math.cos(angle) * 0.4, 1.0, Math.sin(angle) * 0.4, 0.35);
        }
        burst(level, dust(look.rgb, 1.4F), at.add(0.0, 0.8, 0.0), 30, radius * 0.4, 0.0);
        schedule(level, 3, () -> {
            ring(level, fade(look.rgb, 1.6F), at.x, y + 0.05, at.z, radius * 1.12, 36);
            burst(level, ERParticles.RUNE.get(), at.add(0.0, 0.6, 0.0), 10, radius * 0.5, 0.05);
        });
        schedule(level, 6, () -> ring(level, fade(look.accent, 1.2F), at.x, y + 0.05, at.z, radius * 0.8, 24));
    }

    /** The flight of a weapon-skill arrow: a bright streak along its path. */
    static void arrowTrail(ServerLevel level, Entity arrow, ServerPlayer owner) {
        Look look = look(owner, WeaponType.BOW);
        Vec3 motion = arrow.getDeltaMovement();
        for (int i = 0; i < 3; i++) {
            Vec3 p = arrow.position().subtract(motion.scale(i / 3.0));
            level.sendParticles(dust(look.rgb, 1.3F), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        level.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** Runs the after-images that are due. Called once per server tick. */
    static void tickScheduled() {
        if (QUEUE.isEmpty()) {
            return;
        }
        List<Scheduled> due = new ArrayList<>();
        for (Iterator<Scheduled> it = QUEUE.iterator(); it.hasNext(); ) {
            Scheduled scheduled = it.next();
            if (scheduled.level.getGameTime() >= scheduled.due) {
                due.add(scheduled);
                it.remove();
            }
        }
        for (Scheduled scheduled : due) {
            scheduled.effect.run();
        }
    }

    static void forget(UUID playerId) {
        WINDOWS.remove(playerId);
    }

    static void clear() {
        WINDOWS.clear();
        QUEUE.clear();
    }

    // ------------------------------------------------------------------ the six stagings

    private static void windUp(ServerPlayer player, Look look, int age, int windUp) {
        ServerLevel level = player.serverLevel();
        double progress = (age + 1.0) / Math.max(1, windUp);
        Vec3 feet = player.position();
        Vec3 fwd = forward(player);
        switch (look.stage) {
            case WHIRL -> {
                // wind spirals in onto the blade
                double radius = Math.max(0.5, 2.8 * (1.0 - progress));
                for (int k = 0; k < 3; k++) {
                    double angle = age * 0.9 + k * Math.PI * 2.0 / 3.0;
                    level.sendParticles(dust(look.rgb, 1.2F), feet.x + Math.cos(angle) * radius,
                            feet.y + 0.8 + 0.15 * k, feet.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
                }
                burst(level, ParticleTypes.END_ROD, hand(player), 1, 0.1, 0.01);
                if (age == 1) {
                    ring(level, dust(look.rgb, 0.8F), feet.x, feet.y + 0.1, feet.z, 3.8, 28);
                }
            }
            case QUAKE -> {
                // embers rise around the wielder, the raised blade glows, the area shows on the ground
                for (int k = 0; k < 3; k++) {
                    double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
                    double r = 0.4 + level.getRandom().nextDouble() * 1.2;
                    level.sendParticles(dust(look.rgb, 1.3F), feet.x + Math.cos(angle) * r, feet.y + 0.1,
                            feet.z + Math.sin(angle) * r, 0, 0.0, 1.0, 0.0, 0.12);
                }
                Vec3 blade = feet.add(0.0, 2.5, 0.0).add(fwd.scale(0.3));
                burst(level, dust(look.accent, (float) (0.8 + progress)), blade, 2 + (int) (progress * 4), 0.2, 0.0);
                if (age == 3 || age == 8) {
                    ring(level, dust(look.rgb, 0.9F), feet.x, feet.y + 0.1, feet.z, 4.5, 32);
                }
            }
            case DANCE -> {
                Vec3 side = right(fwd).scale(0.5);
                Vec3 chest = feet.add(0.0, 1.1, 0.0);
                burst(level, ParticleTypes.END_ROD, chest.add(side), 1, 0.05, 0.01);
                burst(level, ParticleTypes.END_ROD, chest.subtract(side), 1, 0.05, 0.01);
                double angle = age * 1.6;
                level.sendParticles(dust(look.rgb, 1.1F), feet.x + Math.cos(angle) * 1.2, feet.y + 1.0,
                        feet.z + Math.sin(angle) * 1.2, 2, 0.05, 0.1, 0.05, 0.0);
            }
            case DRILL -> {
                // a spiral tightens around the spear tip
                Vec3 dir = player.getLookAngle();
                Vec3 tip = player.getEyePosition().add(0.0, -0.3, 0.0).add(dir.scale(1.4));
                double radius = Math.max(0.15, 1.2 * (1.0 - progress));
                Vec3[] basis = basis(dir);
                for (int k = 0; k < 4; k++) {
                    double angle = age * 0.8 + k * Math.PI / 2.0;
                    Vec3 p = tip.add(basis[0].scale(Math.cos(angle) * radius)).add(basis[1].scale(Math.sin(angle) * radius));
                    level.sendParticles(dust(look.rgb, 1.0F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
                if (progress > 0.7) {
                    burst(level, ParticleTypes.END_ROD, tip, 1, 0.03, 0.0);
                }
            }
            case SIGIL -> {
                // a magic circle is drawn in front of the caster, its star filling in
                Vec3 dir = player.getLookAngle();
                Vec3 center = player.getEyePosition().add(dir.scale(1.1));
                Vec3[] basis = basis(dir);
                double spin = age * 0.25;
                for (int k = 0; k < 14; k++) {
                    double angle = spin + k * Math.PI * 2.0 / 14.0;
                    Vec3 p = center.add(basis[0].scale(Math.cos(angle) * 0.95)).add(basis[1].scale(Math.sin(angle) * 0.95));
                    level.sendParticles(dust(look.rgb, 0.9F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
                starOnPlane(level, dust(look.accent, 0.8F), center, basis, 0.9, progress, -spin);
                burst(level, ParticleTypes.ENCHANT, center, 3, 0.6, 0.6);
                if (age % 2 == 0) {
                    ring(level, dust(look.rgb, 0.8F), feet.x, feet.y + 0.1, feet.z, 1.3, 16);
                }
            }
            case STREAK -> {
                // wind is drawn in towards the bow
                Vec3 dir = player.getLookAngle();
                Vec3 center = player.getEyePosition().add(0.0, -0.15, 0.0).add(dir.scale(0.9));
                Vec3[] basis = basis(dir);
                double radius = Math.max(0.2, 1.5 * (1.0 - progress));
                for (int k = 0; k < 10; k++) {
                    double angle = age * 0.5 + k * Math.PI * 2.0 / 10.0;
                    Vec3 p = center.add(basis[0].scale(Math.cos(angle) * radius)).add(basis[1].scale(Math.sin(angle) * radius));
                    level.sendParticles(dust(look.rgb, 0.9F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
                if (progress > 0.7) {
                    burst(level, ParticleTypes.END_ROD, center, 2, 0.05, 0.0);
                }
            }
        }
    }

    /** 片手剣: a full ring of wind and steel, then a fading after-image ring. */
    private static void whirl(ServerPlayer player, Look look, double reach) {
        ServerLevel level = player.serverLevel();
        Vec3 c = player.position();
        sound(level, c, ERSounds.WEAPON_SKILL_WHIRL.get(), 1.2F, 1.0F);
        ring(level, dust(look.rgb, 1.7F), c.x, c.y + 1.0, c.z, reach, 44);
        ring(level, dust(look.accent, 1.0F), c.x, c.y + 0.7, c.z, reach * 0.78, 26);
        for (int i = 0; i < 10; i++) {
            double angle = Math.PI * 2.0 * i / 10.0;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + Math.cos(angle) * reach * 0.65, c.y + 1.0,
                    c.z + Math.sin(angle) * reach * 0.65, 0, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(angle) * 1.2, c.y + 0.4, c.z + Math.sin(angle) * 1.2,
                    0, Math.cos(angle), 0.05, Math.sin(angle), 0.3);
        }
        schedule(level, 1, () -> ring(level, fade(look.rgb, 1.3F), c.x, c.y + 1.05, c.z, reach * 0.9, 28));
        schedule(level, 2, () -> ring(level, fade(look.rgb, 1.0F), c.x, c.y + 1.1, c.z, reach * 0.78, 20));
    }

    /** 両手剣: the ground splits: a boom, debris, three expanding shockwave rings and glowing cracks. */
    private static void quake(ServerPlayer player, Look look, double reach) {
        ServerLevel level = player.serverLevel();
        Vec3 c = player.position();
        Vec3 fwd = forward(player);
        sound(level, c, ERSounds.WEAPON_SKILL_QUAKE.get(), 1.4F, 1.0F);
        Vec3 slam = c.add(fwd.scale(1.0));
        level.sendParticles(ParticleTypes.EXPLOSION, slam.x, slam.y + 0.3, slam.z, 1, 0.0, 0.0, 0.0, 0.0);
        BlockParticleOption debris = ground(level, player.blockPosition());
        level.sendParticles(debris, slam.x, slam.y + 0.2, slam.z, 50, 1.4, 0.2, 1.4, 0.25);
        double y = c.y + 0.15;
        for (int step = 0; step < 3; step++) {
            double r = reach * (step + 1) / 3.0;
            int count = 18 + step * 10;
            schedule(level, step, () -> {
                ring(level, dust(look.rgb, 1.9F), c.x, y, c.z, r, count);
                ringOutward(level, ParticleTypes.CLOUD, c.x, y, c.z, r, 10, 0.15);
            });
        }
        double base = level.getRandom().nextDouble() * Math.PI;
        List<Vec3> cracks = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            double angle = base + k * Math.PI / 4.0 + (level.getRandom().nextDouble() - 0.5) * 0.3;
            double wiggle = 0.0;
            for (double t = 0.8; t <= reach; t += 0.35) {
                wiggle += (level.getRandom().nextDouble() - 0.5) * 0.25;
                cracks.add(new Vec3(c.x + Math.cos(angle + wiggle * 0.2) * t, c.y + 0.05,
                        c.z + Math.sin(angle + wiggle * 0.2) * t));
            }
        }
        for (Vec3 p : cracks) {
            level.sendParticles(dust(DARK, 1.5F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        for (int glow = 1; glow <= 4; glow++) {
            float size = 1.3F - glow * 0.2F;
            schedule(level, glow, () -> {
                for (int i = 0; i < cracks.size(); i += 2) {
                    Vec3 p = cracks.get(i);
                    level.sendParticles(dust(look.rgb, size), p.x, p.y + 0.03, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            });
        }
        burst(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, c.add(0.0, 0.2, 0.0), 6, reach * 0.5, 0.01);
    }

    /** 双剣: one tilted slash arc (alternating), a big X on the last hit, each with a fading after-image. */
    private static void dance(ServerPlayer player, Look look, int index, boolean last) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition().add(0.0, -0.45, 0.0);
        Vec3 fwd = forward(player);
        sound(level, eye, ERSounds.WEAPON_SKILL_DANCE.get(), 1.0F, 0.9F + 0.06F * index);
        double tilt = (index % 2 == 0) ? 1.0 : -1.0;
        double radius = last ? 2.8 : 2.4;
        arc(level, dust(look.rgb, last ? 1.8F : 1.4F), eye, fwd, radius, tilt, 20);
        Vec3 mid = eye.add(fwd.scale(radius * 0.8));
        level.sendParticles(ERParticles.SLASH.get(), mid.x, mid.y, mid.z, 1, 0.0, 0.0, 0.0, 0.0);
        burst(level, ParticleTypes.CRIT, mid, 4, 0.3, 0.3);
        schedule(level, 1, () -> arc(level, fade(look.rgb, 1.1F), eye, fwd, radius * 0.92, tilt, 10));
        if (last) {
            arc(level, dust(look.accent, 1.6F), eye, fwd, radius, -tilt, 20);
            level.sendParticles(ParticleTypes.FLASH, mid.x, mid.y, mid.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, mid.x, mid.y, mid.z, 0, 0.0, 0.0, 0.0, 0.0);
            burst(level, dust(look.rgb, 1.5F), mid, 30, 0.8, 0.0);
            schedule(level, 1, () -> arc(level, fade(look.accent, 1.1F), eye, fwd, radius * 0.92, -tilt, 10));
        }
    }

    /** 槍: a drilling thrust line with a spiral around it and a shock ring at its end; the last thrust is bigger. */
    private static void drill(ServerPlayer player, Look look, double reach, int index, boolean last) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = player.getLookAngle();
        Vec3 start = player.getEyePosition().add(0.0, -0.3, 0.0);
        sound(level, start, ERSounds.WEAPON_SKILL_DRILL.get(), 1.0F, 0.95F + 0.08F * index);
        Vec3[] basis = basis(dir);
        List<Vec3> line = new ArrayList<>();
        for (double t = 0.8; t <= reach; t += 0.25) {
            Vec3 p = start.add(dir.scale(t));
            line.add(p);
            level.sendParticles(dust(look.accent, 1.0F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        for (double t = 0.8; t <= reach; t += 0.18) {
            double angle = t * 2.6 + index;
            Vec3 p = start.add(dir.scale(t)).add(basis[0].scale(Math.cos(angle) * 0.45))
                    .add(basis[1].scale(Math.sin(angle) * 0.45));
            level.sendParticles(dust(look.rgb, 1.3F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        Vec3 end = start.add(dir.scale(reach));
        double cone = last ? 1.4 : 0.9;
        for (int k = 0; k < 16; k++) {
            double angle = k * Math.PI / 8.0;
            Vec3 p = end.add(basis[0].scale(Math.cos(angle) * cone)).add(basis[1].scale(Math.sin(angle) * cone));
            level.sendParticles(dust(look.rgb, 1.5F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.CLOUD, end.x, end.y, end.z, 6, 0.2, 0.2, 0.2, 0.08);
        if (last) {
            level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0.0, 0.0, 0.0, 0.0);
            burst(level, ParticleTypes.CRIT, end, 14, 0.5, 0.5);
        }
        schedule(level, 1, () -> {
            for (int i = 0; i < line.size(); i += 2) {
                Vec3 p = line.get(i);
                level.sendParticles(fade(look.rgb, 1.1F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        });
    }

    /** 杖: the circle fires: it flares out, flashes and throws sparks (the bolt and its burst follow). */
    private static void sigilLaunch(ServerPlayer player, Look look) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = player.getLookAngle();
        Vec3 center = player.getEyePosition().add(dir.scale(1.1));
        Vec3[] basis = basis(dir);
        sound(level, center, ERSounds.WEAPON_SKILL_SIGIL_LAUNCH.get(), 1.2F, 1.0F);
        for (double r : new double[] {1.0, 1.6}) {
            for (int k = 0; k < 20; k++) {
                double angle = k * Math.PI / 10.0;
                Vec3 p = center.add(basis[0].scale(Math.cos(angle) * r)).add(basis[1].scale(Math.sin(angle) * r));
                level.sendParticles(dust(r < 1.2 ? look.accent : look.rgb, 1.4F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0,
                        0.0);
            }
        }
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        for (int k = 0; k < 10; k++) {
            double angle = k * Math.PI / 5.0;
            Vec3 out = basis[0].scale(Math.cos(angle)).add(basis[1].scale(Math.sin(angle)));
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 0, out.x, out.y, out.z, 0.25);
        }
        schedule(level, 1, () -> {
            for (int k = 0; k < 16; k++) {
                double angle = k * Math.PI / 8.0;
                Vec3 p = center.add(basis[0].scale(Math.cos(angle) * 2.0)).add(basis[1].scale(Math.sin(angle) * 2.0));
                level.sendParticles(fade(look.rgb, 1.1F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        });
    }

    /** 弓: a shock ring at the bow and a streak along the shot. */
    private static void streak(ServerPlayer player, Look look) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = player.getLookAngle();
        Vec3 origin = player.getEyePosition().add(0.0, -0.15, 0.0);
        Vec3[] basis = basis(dir);
        sound(level, origin, ERSounds.WEAPON_SKILL_ARROW_LAUNCH.get(), 1.2F, 1.0F);
        Vec3 ringAt = origin.add(dir.scale(1.2));
        for (int k = 0; k < 20; k++) {
            double angle = k * Math.PI / 10.0;
            Vec3 out = basis[0].scale(Math.cos(angle)).add(basis[1].scale(Math.sin(angle)));
            Vec3 p = ringAt.add(out.scale(1.1));
            level.sendParticles(dust(look.accent, 1.3F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            if (k % 2 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 0, out.x, out.y, out.z, 0.2);
            }
        }
        Vec3 boom = origin.add(dir.scale(1.8));
        level.sendParticles(ParticleTypes.SONIC_BOOM, boom.x, boom.y, boom.z, 1, 0.0, 0.0, 0.0, 0.0);
        List<Vec3> line = new ArrayList<>();
        for (double t = 1.5; t <= 9.0; t += 0.5) {
            Vec3 p = origin.add(dir.scale(t));
            line.add(p);
            level.sendParticles(dust(look.rgb, 1.2F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        schedule(level, 2, () -> {
            for (Vec3 p : line) {
                level.sendParticles(fade(look.rgb, 0.9F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private record Look(Stage stage, int rgb, int accent) {
    }

    /** The look of the player's weapon skill: the element of a held named weapon colours it. */
    private static Look look(ServerPlayer player, WeaponType type) {
        SkillTheme theme = EquipmentStacks.designOf(player.getMainHandItem())
                .filter(WeaponDesign.class::isInstance).map(design -> ((WeaponDesign) design).element().theme())
                .orElse(null);
        WeaponSkillLooks.Look base = WeaponSkillLooks.of(type);
        return new Look(base.stage(), WeaponSkillLooks.rgb(type, theme), base.accentRgb());
    }

    private static void schedule(ServerLevel level, int delay, Runnable effect) {
        if (delay <= 0) {
            effect.run();
            return;
        }
        if (QUEUE.size() < 4096) {
            QUEUE.add(new Scheduled(level, level.getGameTime() + delay, effect));
        }
    }

    private static Vec3 forward(ServerPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    private static Vec3 right(Vec3 fwd) {
        return new Vec3(-fwd.z, 0.0, fwd.x);
    }

    /** Two unit vectors across {@code dir}. */
    private static Vec3[] basis(Vec3 dir) {
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
        Vec3 up = Math.abs(d.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 u = d.cross(up).normalize();
        Vec3 v = u.cross(d).normalize();
        return new Vec3[] {u, v};
    }

    private static Vec3 hand(ServerPlayer player) {
        Vec3 fwd = forward(player);
        return player.position().add(0.0, 1.2, 0.0).add(fwd.scale(0.4)).add(right(fwd).scale(0.35));
    }

    private static BlockParticleOption ground(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.isAir()) {
            below = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
        }
        return new BlockParticleOption(ParticleTypes.BLOCK, below);
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void burst(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread,
                              double speed) {
        level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    private static void ring(ServerLevel level, ParticleOptions particle, double x, double y, double z, double radius,
                             int count) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            level.sendParticles(particle, x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius, 1, 0.0, 0.0,
                    0.0, 0.0);
        }
    }

    private static void ringOutward(ServerLevel level, ParticleOptions particle, double x, double y, double z,
                                    double radius, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            level.sendParticles(particle, x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius, 0,
                    Math.cos(angle), 0.1, Math.sin(angle), speed);
        }
    }

    /** A tilted slash arc of 70 degrees in front: {@code tilt} = 1 from upper left to lower right, -1 mirrored. */
    private static void arc(ServerLevel level, ParticleOptions particle, Vec3 eye, Vec3 fwd, double radius, double tilt,
                            int count) {
        Vec3 side = right(fwd);
        for (int i = 0; i < count; i++) {
            double u = i / (double) (count - 1);
            double angle = Math.toRadians(-35.0 + 70.0 * u);
            Vec3 p = eye.add(fwd.scale(Math.cos(angle) * radius)).add(side.scale(Math.sin(angle) * radius))
                    .add(0.0, (0.5 - u) * 1.3 * tilt, 0.0);
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** A five-pointed star lying on the ground. */
    private static void star(ServerLevel level, ParticleOptions particle, double x, double y, double z, double radius,
                             double fraction, double spin) {
        Vec3[] flat = {new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0)};
        starOnPlane(level, particle, new Vec3(x, y, z), flat, radius, fraction, spin);
    }

    /** A five-pointed star in the plane of {@code basis}, drawn up to {@code fraction} of its outline. */
    private static void starOnPlane(ServerLevel level, ParticleOptions particle, Vec3 center, Vec3[] basis,
                                    double radius, double fraction, double spin) {
        Vec3[] points = new Vec3[5];
        for (int k = 0; k < 5; k++) {
            double angle = spin + Math.PI / 2.0 + k * Math.PI * 2.0 / 5.0;
            points[k] = center.add(basis[0].scale(Math.cos(angle) * radius)).add(basis[1].scale(Math.sin(angle) * radius));
        }
        int edges = (int) Math.ceil(5 * Math.min(1.0, Math.max(0.0, fraction)));
        for (int e = 0; e < edges; e++) {
            Vec3 from = points[(e * 2) % 5];
            Vec3 to = points[(e * 2 + 2) % 5];
            for (int i = 0; i <= 6; i++) {
                Vec3 p = from.add(to.subtract(from).scale(i / 6.0));
                level.sendParticles(particle, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(color(rgb), Math.max(0.2F, Math.min(4.0F, size)));
    }

    /** A dust that fades from the colour into the dark: after-images. */
    private static DustColorTransitionOptions fade(int rgb, float size) {
        return new DustColorTransitionOptions(color(rgb), color(DARK), Math.max(0.2F, Math.min(4.0F, size)));
    }

    private static Vector3f color(int rgb) {
        return new Vector3f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
    }
}
