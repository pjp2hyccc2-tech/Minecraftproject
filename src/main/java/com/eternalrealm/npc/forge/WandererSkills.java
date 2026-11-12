package com.eternalrealm.npc.forge;

import com.eternalrealm.monster.forge.MonsterTags;
import com.eternalrealm.npc.Wanderers;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * What the wandering adventurers do in a fight (docs/modules/wanderers.md §3), drawn with particles that every player
 * nearby sees: an ordinary attack each in its own manner, and two signature skills each, choreographed over a second
 * or two (stars lighting up and joining into a figure, a turning compass needle of light, a hawk stooping in a golden
 * streak, a fan of feathered arrows, rings of sound rolling out of a bell, a bell of light falling, waves rolling out
 * of a song, bubbles rising around the hurt). Damage is a share of the enemy's maximum health, so they are useful
 * against any monster without taking the fight from the player; they never hurt players.
 */
public final class WandererSkills {
    private static final DustParticleOptions GOLD = dust(1.0F, 0.82F, 0.32F, 1.3F);
    private static final DustParticleOptions BRASS = dust(0.86F, 0.64F, 0.26F, 1.0F);
    private static final DustParticleOptions STAR = dust(1.0F, 0.96F, 0.7F, 1.6F);
    private static final DustParticleOptions FEATHER = dust(0.96F, 0.94F, 0.88F, 1.1F);
    private static final DustParticleOptions HAWK = dust(0.62F, 0.4F, 0.2F, 1.4F);
    private static final DustParticleOptions LEAF = dust(0.36F, 0.66F, 0.3F, 1.0F);
    private static final DustParticleOptions BRONZE = dust(0.76F, 0.5F, 0.24F, 1.6F);
    private static final DustParticleOptions VERDIGRIS = dust(0.46F, 0.66F, 0.52F, 1.2F);
    private static final DustParticleOptions FOAM = dust(0.94F, 0.98F, 1.0F, 1.2F);
    private static final DustParticleOptions TIDE = dust(0.24F, 0.7F, 0.72F, 1.4F);
    private static final DustParticleOptions CORAL = dust(0.94F, 0.52F, 0.42F, 1.2F);
    private static final List<Show> SHOWS = new ArrayList<>();
    private static final int MAX_SHOWS = 32;

    private WandererSkills() {
    }

    private static DustParticleOptions dust(float r, float g, float b, float size) {
        return new DustParticleOptions(new Vector3f(r, g, b), size);
    }

    // ------------------------------------------------------------------------------------------- schedule

    /** A choreography that plays over a few ticks. */
    private abstract static class Show {
        final ServerLevel level;
        final int length;
        int age;

        Show(ServerLevel level, int length) {
            this.level = level;
            this.length = length;
        }

        abstract void frame(int t);
    }

    /** Server tick ({@link WandererDirector}): every running choreography plays its next frame. */
    static void tick() {
        Iterator<Show> it = SHOWS.iterator();
        while (it.hasNext()) {
            Show show = it.next();
            try {
                show.frame(show.age);
            } catch (RuntimeException e) {
                it.remove();
                continue;
            }
            if (++show.age >= show.length) {
                it.remove();
            }
        }
    }

    static void clear() {
        SHOWS.clear();
    }

    private static void play(Show show) {
        if (SHOWS.size() < MAX_SHOWS) {
            SHOWS.add(show);
            show.frame(0);
            show.age = 1;
        }
    }

    // --------------------------------------------------------------------------------------------- casting

    /** A signature skill on {@code target}: the gesture, the call, the choreography. */
    static void cast(WandererEntity wanderer, Wanderers.Skill skill, LivingEntity target) {
        if (!(wanderer.level() instanceof ServerLevel level)) {
            return;
        }
        level.broadcastEntityEvent(wanderer, NpcEntity.TALK_EVENT);
        WandererDirector.shout(wanderer, skill);
        switch (skill) {
            case STAR_CHART -> play(starChart(level, wanderer, target));
            case COMPASS_RAY -> play(compassRay(level, wanderer));
            case FALCON_STOOP -> play(falconStoop(level, wanderer, target));
            case FEATHER_GALE -> play(featherGale(level, wanderer, target));
            case BELL_TOLL -> play(bellToll(level, wanderer));
            case GREAT_BELL -> play(greatBell(level, wanderer, target));
            case SURF_SONG -> play(surfSong(level, wanderer, target));
            case BUBBLE_WARD -> ward(wanderer);
        }
    }

    /** An ordinary attack in the wanderer's own manner. */
    static void basic(WandererEntity wanderer, LivingEntity target) {
        if (!(wanderer.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 from = hand(wanderer);
        Vec3 to = middle(target);
        switch (wanderer.who()) {
            case BOLD -> {
                line(level, BRASS, from, to, 0.3);
                line(level, ParticleTypes.END_ROD, from, to, 1.2);
                burst(level, ParticleTypes.ENCHANTED_HIT, to, 10, 0.25, 0.3);
                sound(level, from, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.5F);
            }
            case MIRKA -> {
                line(level, ParticleTypes.CRIT, from, to, 0.35);
                line(level, FEATHER, from, to, 0.6);
                burst(level, ParticleTypes.CRIT, to, 8, 0.2, 0.2);
                sound(level, from, SoundEvents.ARROW_SHOOT, 0.8F, 1.3F);
            }
            case GRAUS -> {
                Vec3 look = flat(target.position().subtract(wanderer.position()));
                for (int i = -2; i <= 2; i++) {
                    Vec3 dir = rotate(look, i * 0.4);
                    Vec3 at = wanderer.position().add(dir.scale(1.8)).add(0.0, 1.0, 0.0);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
                burst(level, ParticleTypes.CRIT, to, 10, 0.3, 0.3);
                sound(level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 0.85F);
            }
            case SELE -> {
                line(level, TIDE, from, to, 0.35);
                line(level, ParticleTypes.BUBBLE_POP, from, to, 0.5);
                level.sendParticles(ParticleTypes.NOTE, from.x, from.y + 0.6, from.z, 1, 0.0, 0.0, 0.0, 0.0);
                burst(level, ParticleTypes.SPLASH, to, 14, 0.3, 0.1);
                sound(level, from, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.9F);
            }
        }
        strike(wanderer, target, wanderer.who().basicDamage(), 0.25);
    }

    // ------------------------------------------------------------------------------------------------ Bold

    /** Six stars light up around the enemy, lines of light join them into two triangles, and they burst. */
    private static Show starChart(ServerLevel level, WandererEntity wanderer, LivingEntity target) {
        Vec3 center = target.position();
        double radius = 3.2;
        double turn = wanderer.getRandom().nextDouble() * Math.PI;
        Vec3[] stars = new Vec3[6];
        for (int i = 0; i < stars.length; i++) {
            double a = turn + i * Math.PI / 3.0;
            stars[i] = center.add(Math.cos(a) * radius, 0.3 + (i % 2) * 0.9, Math.sin(a) * radius);
        }
        return new Show(level, 28) {
            @Override
            void frame(int t) {
                int lit = Math.min(stars.length, t / 2 + 1);
                for (int i = 0; i < lit && t < 20; i++) {
                    Vec3 s = stars[i];
                    level.sendParticles(STAR, s.x, s.y, s.z, 2, 0.05, 0.05, 0.05, 0.0);
                    level.sendParticles(ParticleTypes.END_ROD, s.x, s.y, s.z, 1, 0.0, 0.0, 0.0, 0.0);
                    if (t / 2 == i) {
                        sound(level, s, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 0.9F + i * 0.15F);
                    }
                }
                if (t >= 10 && t < 20) {
                    double grown = Math.min(1.0, (t - 9) / 6.0);
                    for (int i = 0; i < stars.length; i++) {
                        Vec3 a = stars[i];
                        Vec3 b = stars[(i + 2) % stars.length];
                        line(level, GOLD, a, a.add(b.subtract(a).scale(grown)), 0.3);
                    }
                }
                if (t == 20) {
                    for (Vec3 s : stars) {
                        level.sendParticles(ParticleTypes.FIREWORK, s.x, s.y, s.z, 10, 0.1, 0.1, 0.1, 0.12);
                    }
                    level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1.0, center.z, 1, 0.0, 0.0, 0.0, 0.0);
                    burst(level, STAR, center.add(0.0, 1.0, 0.0), 40, radius * 0.5, 0.0);
                    sound(level, center, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0F, 1.2F);
                    sound(level, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.4F);
                    for (Mob mob : monsters(level, center, Wanderers.Skill.STAR_CHART.radius())) {
                        strike(wanderer, mob, Wanderers.Skill.STAR_CHART.power(), 0.0);
                        mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, 0.45, 0.0));
                    }
                }
                if (t > 20) {
                    for (Vec3 s : stars) {
                        level.sendParticles(ParticleTypes.WAX_ON, s.x, s.y + (t - 20) * 0.2, s.z, 1, 0.1, 0.1, 0.1,
                                0.0);
                    }
                }
            }
        };
    }

    /** A needle of light turns once around him like a compass and cuts what it passes. */
    private static Show compassRay(ServerLevel level, WandererEntity wanderer) {
        Vec3 center = wanderer.position().add(0.0, 1.0, 0.0);
        double length = Wanderers.Skill.COMPASS_RAY.radius();
        double start = Math.toRadians(wanderer.getYRot() + 90.0);
        Set<Integer> hit = new HashSet<>();
        return new Show(level, 20) {
            @Override
            void frame(int t) {
                if (t == 0) {
                    ring(level, BRASS, center.subtract(0.0, 0.9, 0.0), 1.3, 24);
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4.0;
                        Vec3 tick = center.add(Math.cos(a) * 1.7, -0.9, Math.sin(a) * 1.7);
                        level.sendParticles(ParticleTypes.END_ROD, tick.x, tick.y, tick.z, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                    sound(level, center, SoundEvents.BEACON_ACTIVATE, 0.8F, 1.7F);
                }
                if (t >= 16) {
                    return;
                }
                double a = start + (t / 16.0) * Math.PI * 2.0;
                Vec3 dir = new Vec3(Math.cos(a), 0.0, Math.sin(a));
                Vec3 tip = center.add(dir.scale(length));
                line(level, GOLD, center, tip, 0.35);
                level.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 2, 0.05, 0.05, 0.05, 0.01);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 3, 0.1, 0.1, 0.1, 0.05);
                if (t % 4 == 0) {
                    sound(level, tip, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.2F + t * 0.04F);
                }
                for (Mob mob : monsters(level, center, length + 1.0)) {
                    if (hit.contains(mob.getId())) {
                        continue;
                    }
                    Vec3 rel = flat(mob.position().subtract(center.subtract(0.0, 1.0, 0.0)));
                    double along = rel.dot(dir);
                    double aside = rel.subtract(dir.scale(along)).length();
                    if (along > 0.0 && along <= length + 0.5 && aside <= 1.0 + mob.getBbWidth() * 0.5) {
                        hit.add(mob.getId());
                        strike(wanderer, mob, Wanderers.Skill.COMPASS_RAY.power(), 0.6);
                        burst(level, ParticleTypes.ENCHANTED_HIT, middle(mob), 12, 0.3, 0.3);
                    }
                }
            }
        };
    }

    // ----------------------------------------------------------------------------------------------- Mirka

    /** The hawk climbs out of sight and stoops on the enemy in a golden streak. */
    private static Show falconStoop(ServerLevel level, WandererEntity wanderer, LivingEntity target) {
        Vec3 shoulder = wanderer.position().add(0.0, wanderer.getBbHeight() * 0.9, 0.0);
        return new Show(level, 18) {
            Vec3 last = shoulder;

            @Override
            void frame(int t) {
                Vec3 aim = middle(target);
                if (t == 0) {
                    line(level, ParticleTypes.END_ROD, shoulder, shoulder.add(0.0, 9.0, 0.0), 0.8);
                    sound(level, shoulder, SoundEvents.PHANTOM_SWOOP, 0.9F, 1.7F);
                    sound(level, shoulder, SoundEvents.PARROT_FLY, 0.8F, 1.2F);
                }
                if (t >= 4 && t <= 12) {
                    Vec3 high = aim.add(flat(wanderer.position().subtract(aim)).normalize().scale(4.0))
                            .add(0.0, 12.0, 0.0);
                    double k = (t - 4) / 8.0;
                    double ease = k * k;
                    Vec3 head = high.add(aim.subtract(high).scale(ease));
                    line(level, GOLD, last, head, 0.3);
                    line(level, ParticleTypes.END_ROD, last, head, 0.9);
                    Vec3 dir = head.subtract(last);
                    Vec3 side = dir.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0)
                            : dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
                    for (int s = -1; s <= 1; s += 2) {
                        for (int i = 1; i <= 3; i++) {
                            Vec3 wing = head.add(side.scale(s * i * 0.35)).add(0.0, i * 0.18, 0.0)
                                    .subtract(dir.normalize().scale(i * 0.25));
                            level.sendParticles(HAWK, wing.x, wing.y, wing.z, 1, 0.0, 0.0, 0.0, 0.0);
                        }
                    }
                    last = head;
                }
                if (t == 12) {
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, aim.x, aim.y, aim.z, 3, 0.4, 0.3, 0.4, 0.0);
                    burst(level, ParticleTypes.CRIT, aim, 30, 0.4, 0.5);
                    burst(level, FEATHER, aim, 24, 0.7, 0.15);
                    level.sendParticles(ParticleTypes.CLOUD, aim.x, aim.y, aim.z, 8, 0.3, 0.2, 0.3, 0.05);
                    sound(level, aim, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.1F);
                    sound(level, aim, SoundEvents.PHANTOM_FLAP, 0.9F, 1.6F);
                    strike(wanderer, target, Wanderers.Skill.FALCON_STOOP.power(), 0.4);
                    for (Mob mob : monsters(level, target.position(), Wanderers.Skill.FALCON_STOOP.radius())) {
                        if (mob != target) {
                            strike(wanderer, mob, Wanderers.Skill.FALCON_STOOP.power() * 0.5, 0.4);
                        }
                    }
                }
                if (t > 12) {
                    level.sendParticles(FEATHER, aim.x, aim.y + 0.5 + (t - 12) * 0.1, aim.z, 2, 0.4, 0.1, 0.4, 0.0);
                }
            }
        };
    }

    /** A fan of nine feathered arrows. */
    private static Show featherGale(ServerLevel level, WandererEntity wanderer, LivingEntity target) {
        Vec3 from = hand(wanderer);
        Vec3 forward = flat(target.position().subtract(wanderer.position())).normalize();
        double reach = Wanderers.Skill.FEATHER_GALE.radius();
        Set<Integer> hit = new HashSet<>();
        return new Show(level, 10) {
            @Override
            void frame(int t) {
                if (t == 0) {
                    sound(level, from, SoundEvents.ARROW_SHOOT, 1.0F, 1.0F);
                    sound(level, from, SoundEvents.PHANTOM_FLAP, 0.8F, 1.8F);
                }
                if (t == 2) {
                    sound(level, from, SoundEvents.ARROW_SHOOT, 1.0F, 1.3F);
                }
                if (t > 7) {
                    return;
                }
                double grown = reach * Math.min(1.0, (t + 1) / 6.0);
                for (int i = -4; i <= 4; i++) {
                    Vec3 dir = rotate(forward, i * Math.toRadians(8.5));
                    Vec3 tip = from.add(dir.scale(grown));
                    Vec3 tail = from.add(dir.scale(Math.max(0.0, grown - 2.0)));
                    line(level, (i % 2 == 0) ? FEATHER : LEAF, tail, tip, 0.4);
                    level.sendParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
                for (Mob mob : monsters(level, from, grown + 0.5)) {
                    if (hit.contains(mob.getId())) {
                        continue;
                    }
                    Vec3 rel = flat(mob.position().subtract(wanderer.position()));
                    double angle = Math.acos(Math.max(-1.0, Math.min(1.0, rel.normalize().dot(forward))));
                    if (angle <= Math.toRadians(38.0)) {
                        hit.add(mob.getId());
                        strike(wanderer, mob, Wanderers.Skill.FEATHER_GALE.power(), 0.5);
                        burst(level, FEATHER, middle(mob), 10, 0.3, 0.1);
                    }
                }
            }
        };
    }

    // ----------------------------------------------------------------------------------------------- Graus

    /** He strikes the bell on his back: three rings of sound roll out; the first throws enemies back hard. */
    private static Show bellToll(ServerLevel level, WandererEntity wanderer) {
        Vec3 center = wanderer.position();
        double reach = Wanderers.Skill.BELL_TOLL.radius();
        Set<Integer> hit = new HashSet<>();
        return new Show(level, 26) {
            @Override
            void frame(int t) {
                if (t == 0 || t == 7 || t == 14) {
                    sound(level, center, SoundEvents.BELL_BLOCK, 1.2F, t == 0 ? 0.7F : 0.85F + t * 0.01F);
                    Vec3 back = center.add(0.0, 1.4, 0.0);
                    level.sendParticles(ParticleTypes.FLASH, back.x, back.y, back.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
                if (t == 0) {
                    sound(level, center, SoundEvents.BELL_RESONATE, 1.0F, 0.9F);
                }
                for (int wave = 0; wave < 3; wave++) {
                    int age = t - wave * 7;
                    if (age < 0 || age > 11) {
                        continue;
                    }
                    double radius = 0.6 + (reach - 0.6) * (age / 11.0);
                    ring(level, wave == 0 ? BRONZE : VERDIGRIS, center.add(0.0, 0.25 + wave * 0.35, 0.0), radius,
                            (int) (radius * 9));
                    for (int i = 0; i < 6; i++) {
                        double a = i * Math.PI / 3.0 + age * 0.15;
                        Vec3 n = center.add(Math.cos(a) * radius, 1.2, Math.sin(a) * radius);
                        level.sendParticles(ParticleTypes.NOTE, n.x, n.y, n.z, 0, (i + wave * 4) / 24.0, 0.0, 0.0, 1.0);
                    }
                    for (Mob mob : monsters(level, center, radius + 0.5)) {
                        int key = mob.getId() * 3 + wave;
                        if (hit.contains(key) || mob.position().distanceTo(center) < radius - 1.5) {
                            continue;
                        }
                        hit.add(key);
                        if (wave == 0) {
                            strike(wanderer, mob, Wanderers.Skill.BELL_TOLL.power(), 1.1);
                        } else {
                            push(center, mob, 0.5);
                        }
                    }
                }
            }
        };
    }

    /** A bell of light appears high above the enemy, falls, and rings out as it lands. */
    private static Show greatBell(ServerLevel level, WandererEntity wanderer, LivingEntity target) {
        Vec3 ground = target.position();
        double[][] profile = {{3.2, 0.35}, {2.8, 0.85}, {2.2, 1.1}, {1.6, 1.2}, {1.0, 1.35}, {0.4, 1.75}, {0.1, 1.9}};
        return new Show(level, 24) {
            @Override
            void frame(int t) {
                if (t == 0) {
                    sound(level, ground, SoundEvents.BELL_RESONATE, 1.0F, 0.6F);
                }
                if (t <= 12) {
                    double k = t / 12.0;
                    double drop = 9.0 * (1.0 - k * k);
                    for (double[] row : profile) {
                        ring(level, t % 2 == 0 ? BRONZE : GOLD, ground.add(0.0, row[0] + drop, 0.0), row[1], 12);
                    }
                    Vec3 top = ground.add(0.0, 3.6 + drop, 0.0);
                    level.sendParticles(ParticleTypes.END_ROD, top.x, top.y, top.z, 2, 0.1, 0.1, 0.1, 0.0);
                }
                if (t == 12) {
                    level.sendParticles(ParticleTypes.EXPLOSION, ground.x, ground.y + 0.5, ground.z, 2, 0.6, 0.2, 0.6,
                            0.0);
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, ground.x, ground.y + 0.2, ground.z, 10,
                            1.2, 0.1, 1.2, 0.02);
                    sound(level, ground, SoundEvents.BELL_BLOCK, 1.6F, 0.55F);
                    sound(level, ground, SoundEvents.ANVIL_LAND, 0.6F, 0.6F);
                    for (Mob mob : monsters(level, ground, Wanderers.Skill.GREAT_BELL.radius())) {
                        strike(wanderer, mob, Wanderers.Skill.GREAT_BELL.power(), 0.8);
                    }
                }
                if (t > 12) {
                    double radius = 0.5 + (t - 12) * 0.35;
                    ring(level, BRONZE, ground.add(0.0, 0.15, 0.0), radius, (int) (radius * 10));
                    if (t == 14) {
                        sound(level, ground, SoundEvents.BELL_BLOCK, 1.0F, 0.75F);
                    }
                }
            }
        };
    }

    // ------------------------------------------------------------------------------------------------ Sele

    /** Three waves roll out from her song in a wide arc in front of her and wash enemies back. */
    private static Show surfSong(ServerLevel level, WandererEntity wanderer, LivingEntity target) {
        Vec3 center = wanderer.position();
        Vec3 forward = flat(target.position().subtract(center)).normalize();
        double reach = Wanderers.Skill.SURF_SONG.radius();
        Set<Integer> hit = new HashSet<>();
        return new Show(level, 22) {
            @Override
            void frame(int t) {
                if (t % 3 == 0) {
                    level.sendParticles(ParticleTypes.NOTE, center.x, center.y + 2.2, center.z, 0,
                            (t % 24) / 24.0, 0.0, 0.0, 1.0);
                }
                if (t == 0) {
                    sound(level, center, SoundEvents.GENERIC_SPLASH, 1.0F, 0.8F);
                    sound(level, center, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
                }
                if (t == 4 || t == 8) {
                    sound(level, center, SoundEvents.TRIDENT_RIPTIDE_1, 0.7F, 1.4F);
                }
                for (int wave = 0; wave < 3; wave++) {
                    int age = t - wave * 4;
                    if (age < 0 || age > 12) {
                        continue;
                    }
                    double radius = 1.2 + (reach - 1.2) * (age / 12.0);
                    double crest = 0.3 + 0.6 * Math.sin(Math.PI * age / 12.0);
                    for (int i = -8; i <= 8; i++) {
                        Vec3 dir = rotate(forward, i * Math.toRadians(7.5));
                        Vec3 at = center.add(dir.scale(radius)).add(0.0, crest, 0.0);
                        level.sendParticles(i % 2 == 0 ? FOAM : TIDE, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
                        if (i % 4 == 0) {
                            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.2, at.z, 3, 0.2, 0.1, 0.2, 0.1);
                        }
                    }
                    for (Mob mob : monsters(level, center, radius + 0.6)) {
                        if (hit.contains(mob.getId())) {
                            continue;
                        }
                        Vec3 rel = flat(mob.position().subtract(center));
                        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, rel.normalize().dot(forward))));
                        if (angle <= Math.toRadians(64.0)) {
                            hit.add(mob.getId());
                            strike(wanderer, mob, Wanderers.Skill.SURF_SONG.power(), 1.0);
                            burst(level, ParticleTypes.BUBBLE_POP, middle(mob), 16, 0.4, 0.05);
                        }
                    }
                }
            }
        };
    }

    /**
     * Bubbles rise around every hurt player near Sele and heal them. @return whether anybody needed it (otherwise
     * nothing happens)
     */
    static boolean ward(WandererEntity wanderer) {
        if (!(wanderer.level() instanceof ServerLevel level)) {
            return false;
        }
        double radius = Wanderers.Skill.BUBBLE_WARD.radius();
        List<ServerPlayer> hurt = level.getEntitiesOfClass(ServerPlayer.class,
                wanderer.getBoundingBox().inflate(radius),
                player -> player.isAlive() && !player.isSpectator()
                        && player.getHealth() < player.getMaxHealth() * 0.7F);
        if (hurt.isEmpty()) {
            return false;
        }
        level.broadcastEntityEvent(wanderer, NpcEntity.TALK_EVENT);
        WandererDirector.shout(wanderer, Wanderers.Skill.BUBBLE_WARD);
        sound(level, wanderer.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.6F);
        play(new Show(level, 30) {
            @Override
            void frame(int t) {
                Vec3 self = wanderer.position();
                if (t % 4 == 0) {
                    level.sendParticles(ParticleTypes.NOTE, self.x, self.y + 2.2, self.z, 0, (t % 24) / 24.0, 0.0,
                            0.0, 1.0);
                }
                for (ServerPlayer player : hurt) {
                    if (!player.isAlive() || player.level() != level) {
                        continue;
                    }
                    Vec3 feet = player.position();
                    if (t == 0) {
                        ring(level, CORAL, feet.add(0.0, 0.1, 0.0), 1.1, 18);
                    }
                    for (int i = 0; i < 3; i++) {
                        double a = t * 0.5 + i * Math.PI * 2.0 / 3.0;
                        Vec3 bubble = feet.add(Math.cos(a) * 0.8, 0.1 + (t % 15) * 0.15, Math.sin(a) * 0.8);
                        level.sendParticles(i == 0 ? FOAM : TIDE, bubble.x, bubble.y, bubble.z, 1, 0.0, 0.0, 0.0, 0.0);
                        level.sendParticles(ParticleTypes.BUBBLE_POP, bubble.x, bubble.y + 0.3, bubble.z, 1, 0.05,
                                0.05, 0.05, 0.0);
                    }
                    if (t == 10) {
                        player.heal(player.getMaxHealth() * (float) Wanderers.Skill.BUBBLE_WARD.power());
                        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0, false, true));
                        sound(level, feet, SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.8F);
                        burst(level, ParticleTypes.HAPPY_VILLAGER, feet.add(0.0, 1.0, 0.0), 10, 0.4, 0.0);
                    }
                }
            }
        });
        return true;
    }

    /** Music notes over Sele while she plays at the inn. */
    static void notes(WandererEntity wanderer) {
        if (wanderer.level() instanceof ServerLevel level && level.getRandom().nextInt(2) == 0) {
            Vec3 at = wanderer.position().add(0.0, wanderer.getBbHeight() + 0.3, 0.0);
            level.sendParticles(ParticleTypes.NOTE, at.x, at.y, at.z, 0, level.getRandom().nextDouble(), 0.0, 0.0,
                    1.0);
        }
    }

    /** A purchase at the shop: a few coins between the wanderer and the shopkeeper. */
    static void coins(WandererEntity wanderer, NpcEntity shopkeeper) {
        if (wanderer.level() instanceof ServerLevel level) {
            Vec3 from = hand(wanderer);
            Vec3 to = middle(shopkeeper);
            line(level, dust(1.0F, 0.84F, 0.3F, 0.8F), from, to, 0.4);
            level.sendParticles(ParticleTypes.WAX_ON, to.x, to.y, to.z, 4, 0.2, 0.2, 0.2, 0.0);
        }
    }

    /** Whatever it was doing, it leaves (or arrives): a swirl of light, as a waystone's trip looks. */
    static void trip(WandererEntity wanderer, boolean arriving) {
        if (!(wanderer.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 feet = wanderer.position();
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0;
            double h = (i % 8) * 0.25;
            level.sendParticles(ParticleTypes.END_ROD, feet.x + Math.cos(a) * 0.7, feet.y + h,
                    feet.z + Math.sin(a) * 0.7, 1, 0.0, 0.05, 0.0, 0.02);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, feet.x, feet.y + 1.0, feet.z, 30, 0.4, 0.8, 0.4, 0.05);
        sound(level, feet, arriving ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, 0.5F, 1.6F);
    }

    // ------------------------------------------------------------------------------------------- hits

    /** Monsters near {@code at} (never players, never NPCs). */
    private static List<Mob> monsters(ServerLevel level, Vec3 at, double radius) {
        return level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(radius, 3.0, radius),
                mob -> MonsterTags.isMonster(mob) && mob.isAlive()
                        && mob.position().distanceToSqr(at.x, mob.getY(), at.z) <= radius * radius);
    }

    /** A hit worth {@code share} of the target's maximum health (at least a little), and a push away. */
    private static void strike(WandererEntity wanderer, LivingEntity target, double share, double push) {
        if (!target.isAlive() || target instanceof net.minecraft.world.entity.player.Player) {
            return;
        }
        float amount = (float) Math.max(3.0, target.getMaxHealth() * share);
        target.invulnerableTime = 0; // several sources in one fight must all count
        target.hurt(wanderer.damageSources().mobAttack(wanderer), amount);
        if (push > 0.0) {
            push(wanderer.position(), target, push);
        }
    }

    private static void push(Vec3 from, LivingEntity target, double strength) {
        Vec3 away = flat(target.position().subtract(from));
        if (away.lengthSqr() < 1.0E-6) {
            return;
        }
        away = away.normalize();
        target.knockback(strength, -away.x, -away.z);
    }

    // ------------------------------------------------------------------------------------------- drawing

    private static Vec3 hand(WandererEntity wanderer) {
        Vec3 look = Vec3.directionFromRotation(0.0F, wanderer.getYHeadRot());
        return wanderer.position().add(0.0, wanderer.getBbHeight() * 0.65, 0.0).add(look.scale(0.5));
    }

    private static Vec3 middle(LivingEntity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
    }

    private static Vec3 flat(Vec3 v) {
        return new Vec3(v.x, 0.0, v.z);
    }

    /** {@code v} turned by {@code radians} about the vertical. */
    private static Vec3 rotate(Vec3 v, double radians) {
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    private static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double step) {
        Vec3 span = to.subtract(from);
        int points = Math.max(1, (int) Math.ceil(span.length() / step));
        for (int i = 0; i <= points; i++) {
            Vec3 at = from.add(span.scale(i / (double) points));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points) {
        int n = Math.max(6, points);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2.0 / n;
            level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1,
                    0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void burst(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread,
                              double speed) {
        level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, pitch);
    }
}
