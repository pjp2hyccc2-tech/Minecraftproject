package com.eternalrealm.npc.forge;

import com.eternalrealm.boss.forge.BossFight;
import com.eternalrealm.combat.forge.ERMobEffects;
import com.eternalrealm.combat.forge.ERParticles;
import com.eternalrealm.monster.forge.MonsterTags;
import com.eternalrealm.network.forge.DamageNumberPacket;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.HeroFxPacket;
import com.eternalrealm.npc.Travellers;
import com.eternalrealm.revival.forge.RevivalService;
import com.eternalrealm.sound.ERSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * What the four travellers' moves do (docs/modules/travellers.md §2): when a move starts every client around is told
 * ({@link HeroFxPacket}: the body plays the move and the client draws its light, client/HeroFx), and the server lands
 * each blow at the moment the animation swings it ({@link Travellers.Move#hitTicks()}), with particles and sounds of
 * its own (so a client with a shader pack, which draws no added light, still sees the move). Blows only ever reach
 * monsters and bosses; a companion's kills are the player's, and the player sees the numbers of their blows.
 */
public final class TravellerMoves {
    /** Action code of the joining gesture in {@link HeroFxPacket} (moves use their ordinal). */
    public static final int JOIN = 100;
    /** Action code: standing up again (a burst of light). */
    public static final int STAND_UP = 101;
    private static final DustParticleOptions STAR = dust(1.0F, 0.92F, 0.62F, 1.6F);
    private static final DustParticleOptions NIGHT = dust(0.56F, 0.42F, 1.0F, 1.4F);
    private static final DustParticleOptions STEEL = dust(0.86F, 0.92F, 1.0F, 1.5F);
    private static final DustParticleOptions FIRE = dust(1.0F, 0.48F, 0.12F, 1.8F);
    private static final DustParticleOptions RUNE = dust(0.36F, 0.94F, 1.0F, 1.3F);
    private static final DustParticleOptions HOLY = dust(1.0F, 0.94F, 0.66F, 1.5F);
    private static final DustParticleOptions SKY = dust(0.62F, 0.86F, 1.0F, 1.3F);
    private static final List<Show> SHOWS = new ArrayList<>();
    private static final int MAX_SHOWS = 48;

    private TravellerMoves() {
    }

    private static DustParticleOptions dust(float r, float g, float b, float size) {
        return new DustParticleOptions(new Vector3f(r, g, b), size);
    }

    // ------------------------------------------------------------------------------------------- a move

    /** A move being played: its target and aim, when it started, which of its blows has landed. */
    public static final class Run {
        private final Travellers.Move move;
        @Nullable
        private final LivingEntity target;
        private final Vec3 aim;
        private final Vec3 origin;
        private final int start;
        private final int[] hits;
        private final int seed;
        private final List<LivingEntity> marked = new ArrayList<>();
        private int nextHit;

        Run(Travellers.Move move, @Nullable LivingEntity target, Vec3 aim, Vec3 origin, int start, int[] hits,
            int seed) {
            this.move = move;
            this.target = target;
            this.aim = aim;
            this.origin = origin;
            this.start = start;
            this.hits = hits;
            this.seed = seed;
        }

        public Travellers.Move move() {
            return move;
        }

        @Nullable
        public LivingEntity target() {
            return target;
        }

        public Vec3 aim() {
            return aim;
        }

        public int start() {
            return start;
        }

        int[] hits() {
            return hits;
        }

        int nextHit() {
            return nextHit;
        }

        void advance() {
            nextHit++;
        }

        /** Where the enemy is now (or was when the move started). */
        Vec3 point() {
            return target != null && target.isAlive() ? target.position() : aim;
        }
    }

    /** A choreography that plays over a few ticks (lasting areas, falling stars). */
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

    /** Server tick ({@link TravellerDirector}). */
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

    /** Tells every client around: the body plays {@code action} (a move's ordinal, {@link #JOIN}...). */
    static void announce(TravellerEntity t, int action, Vec3 aim, int seed) {
        ERNetwork.sendToTracking(t, new HeroFxPacket(t.getId(), action, (float) aim.x, (float) aim.y, (float) aim.z,
                seed));
    }

    // ---------------------------------------------------------------------------------------- the start

    static void begin(TravellerEntity t, Run run) {
        if (!(t.level() instanceof ServerLevel level)) {
            return;
        }
        Travellers.Move move = run.move();
        announce(t, move.ordinal(), run.aim(), run.seed);
        if (move.kind() != Travellers.Kind.ATTACK) {
            TravellerDirector.shout(t, move);
        }
        Vec3 hand = hand(t);
        switch (move) {
            case ROS_STAR_SHOT, ROS_STARDUST -> sound(level, hand, ERSounds.SKILL_MAGIC_BOLT, 0.7F, 1.4F);
            case ROS_STARFALL -> {
                sound(level, hand, ERSounds.WEAPON_SKILL_CHARGE_STAFF, 1.0F, 1.1F);
                Vec3 sky = run.aim().add(0.0, 9.0, 0.0);
                ring(level, ParticleTypes.END_ROD, sky, 3.5, 32);
                ring(level, STAR, sky, 2.4, 24);
            }
            case ROS_STAR_BIND -> sound(level, hand, ERSounds.WEAPON_SKILL_CHARGE_STAFF, 0.9F, 1.3F);
            case ROS_THOUSAND_NIGHTS -> {
                sound(level, t.position(), ERSounds.WEAPON_SKILL_SIGIL_LAUNCH, 1.4F, 0.7F);
                sound(level, t.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 0.6F);
                play(thousandNightsRise(level, t, run));
            }
            case SIEG_CLEAVE, SIEG_RISING ->
                    sound(level, t.position(), ERSounds.WEAPON_SWING_TWO_HANDED_SWORD, 0.9F, 0.85F);
            case SIEG_OATH -> sound(level, t.position(), ERSounds.WEAPON_SKILL_CHARGE_GREATSWORD, 1.0F, 0.9F);
            case SIEG_WHIRLWIND -> sound(level, t.position(), ERSounds.WEAPON_SKILL_WHIRL, 1.0F, 0.9F);
            case SIEG_DRAGON_STROKE -> {
                sound(level, t.position(), ERSounds.WEAPON_SKILL_CHARGE_GREATSWORD, 1.4F, 0.6F);
                play(dragonFire(level, t, run));
            }
            case KAY_SLASH, KAY_CROSS -> sound(level, t.position(), ERSounds.WEAPON_SWING_ONE_HANDED_SWORD, 0.8F,
                    1.25F);
            case KAY_FLASH_STEP -> sound(level, t.position(), ERSounds.SKILL_SHADOW_WHOOSH, 1.0F, 1.5F);
            case KAY_PHANTOM_ARRAY -> {
                sound(level, t.position(), ERSounds.WEAPON_SKILL_CHARGE_SWORD, 1.0F, 1.2F);
                burst(level, RUNE, t.position().add(0.0, 1.6, 0.0), 20, 0.8, 0.02);
            }
            case KAY_INFINITE_BLADES -> {
                sound(level, t.position(), ERSounds.WEAPON_SKILL_CHARGE_DUAL, 1.2F, 0.8F);
                sound(level, t.position(), ERSounds.ART_BLACK_SWORD_RAIN, 0.7F, 1.4F);
            }
            case MAR_HOLY_BOLT, MAR_LIGHT_LANCE -> sound(level, hand, ERSounds.SKILL_HOLY_CHIME, 0.7F, 1.3F);
            case MAR_SANCTUARY -> sound(level, t.position(), ERSounds.SKILL_HOLY_CHIME, 1.0F, 0.9F);
            case MAR_PURIFICATION -> sound(level, t.position(), ERSounds.WEAPON_SKILL_CHARGE_STAFF, 0.9F, 1.4F);
            case MAR_KINGDOM_REBORN -> {
                sound(level, t.position(), ERSounds.SKILL_HOLY_CHIME, 1.4F, 0.6F);
                sound(level, t.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.2F);
                play(prayer(level, t));
            }
        }
    }

    // ----------------------------------------------------------------------------------------- the blows

    static void hit(TravellerEntity t, Run run, int index) {
        if (!(t.level() instanceof ServerLevel level)) {
            return;
        }
        Travellers.Move move = run.move();
        boolean last = index == run.hits().length - 1;
        switch (move) {
            case ROS_STAR_SHOT -> starShot(level, t, run, 1, 0.0);
            case ROS_STARDUST -> starShot(level, t, run, 3, 6.0);
            case ROS_STARFALL -> starfall(level, t, run, index);
            case ROS_STAR_BIND -> starBind(level, t, run);
            case ROS_THOUSAND_NIGHTS -> thousandNights(level, t, run, index, last);
            case SIEG_CLEAVE -> arc(level, t, run, 120.0, STEEL, false);
            case SIEG_RISING -> arc(level, t, run, 90.0, STEEL, last);
            case SIEG_OATH -> oath(level, t, run);
            case SIEG_WHIRLWIND -> arc(level, t, run, 360.0, STEEL, last);
            case SIEG_DRAGON_STROKE -> dragonStroke(level, t, run);
            case KAY_SLASH -> arc(level, t, run, 100.0, RUNE, false);
            case KAY_CROSS -> arc(level, t, run, 80.0, RUNE, last);
            case KAY_FLASH_STEP -> flashStep(level, t, run, index);
            case KAY_PHANTOM_ARRAY -> phantomBlade(level, t, run, index);
            case KAY_INFINITE_BLADES -> infiniteBlades(level, t, run, index, last);
            case MAR_HOLY_BOLT -> holyBolt(level, t, run, false);
            case MAR_LIGHT_LANCE -> holyBolt(level, t, run, true);
            case MAR_SANCTUARY -> sanctuary(level, t);
            case MAR_PURIFICATION -> purification(level, t, run);
            case MAR_KINGDOM_REBORN -> kingdomReborn(level, t, run);
        }
    }

    // ------------------------------------------------------------------------------------------- Rosalind

    private static void starShot(ServerLevel level, TravellerEntity t, Run run, int stars, double spread) {
        List<LivingEntity> targets = new ArrayList<>();
        if (run.target() != null && run.target().isAlive()) {
            targets.add(run.target());
        }
        if (stars > 1) {
            for (Mob mob : foes(level, run.point(), spread)) {
                if (targets.size() >= stars) {
                    break;
                }
                if (!targets.contains(mob)) {
                    targets.add(mob);
                }
            }
        }
        Vec3 from = hand(t).add(0.0, 0.8, 0.0);
        for (LivingEntity target : targets) {
            Vec3 to = middle(target);
            line(level, STAR, from, to, 0.35);
            line(level, ParticleTypes.END_ROD, from, to, 1.0);
            burst(level, ParticleTypes.FIREWORK, to, 10, 0.2, 0.08);
            burst(level, STAR, to, 8, 0.3, 0.0);
            strike(t, target, run.move(), 1.0, 0.2, true);
        }
        sound(level, from, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.6F);
        if (!targets.isEmpty()) {
            sound(level, middle(targets.get(0)), ERSounds.WEAPON_SKILL_IMPACT, 0.6F, 1.6F);
        }
    }

    private static void starfall(ServerLevel level, TravellerEntity t, Run run, int index) {
        Vec3 center = run.aim();
        double radius = run.move().radius();
        java.util.Random random = new java.util.Random(run.seed + index * 31L);
        for (int i = 0; i < 4; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(random.nextDouble()) * radius;
            Vec3 ground = center.add(Math.cos(a) * d, 0.1, Math.sin(a) * d);
            Vec3 sky = ground.add(-2.0 + random.nextDouble() * 4.0, 10.0, -2.0 + random.nextDouble() * 4.0);
            line(level, STAR, sky, ground, 0.5);
            burst(level, ParticleTypes.FIREWORK, ground.add(0.0, 0.3, 0.0), 14, 0.3, 0.12);
            ring(level, STAR, ground, 1.0, 12);
        }
        sound(level, center, ERSounds.WEAPON_SKILL_SIGIL_BURST, 0.9F, 1.2F + index * 0.1F);
        sound(level, center, SoundEvents.FIREWORK_ROCKET_BLAST, 0.8F, 0.8F);
        for (Mob mob : foes(level, center, radius)) {
            strike(t, mob, run.move(), 1.0, 0.0, true);
            mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, 0.18, 0.0));
        }
    }

    private static void starBind(ServerLevel level, TravellerEntity t, Run run) {
        Vec3 center = run.point();
        List<Mob> caught = foes(level, center, run.move().radius());
        Vec3 last = null;
        for (Mob mob : caught) {
            Vec3 at = middle(mob);
            if (last != null) {
                line(level, NIGHT, last, at, 0.3);
                line(level, ParticleTypes.END_ROD, last, at, 0.9);
            }
            last = at;
            ring(level, STAR, mob.position().add(0.0, 0.2, 0.0), 0.9, 14);
            ring(level, STAR, mob.position().add(0.0, 1.2, 0.0), 0.7, 10);
            strike(t, mob, run.move(), 1.0, 0.0, true);
            mob.addEffect(new MobEffectInstance(ERMobEffects.STUNNED.get(), 60, 0, false, true));
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
        }
        sound(level, center, ERSounds.ART_HEAVENS_CHAIN, 1.0F, 1.2F);
        sound(level, center, SoundEvents.CHAIN_PLACE, 1.0F, 0.8F);
    }

    /** The first second of the ultimate: light gathers under her as she rises, stars gather in the sky. */
    private static Show thousandNightsRise(ServerLevel level, TravellerEntity t, Run run) {
        Vec3 feet = t.position();
        Vec3 sky = run.aim().add(0.0, 12.0, 0.0);
        return new Show(level, 46) {
            @Override
            void frame(int f) {
                if (f % 2 == 0 && f < 18) {
                    ring(level, NIGHT, feet.add(0.0, 0.1 + f * 0.12, 0.0), 1.2 - f * 0.03, 16);
                }
                if (f >= 16) {
                    double k = Math.min(1.0, (f - 16) / 20.0);
                    ring(level, STAR, sky, 4.0 + 6.0 * k, 40);
                    if (f % 4 == 0) {
                        ring(level, ParticleTypes.END_ROD, sky.add(0.0, -0.4, 0.0), 2.5 + 4.0 * k, 24);
                    }
                }
                if (f == 32) {
                    sound(level, sky, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6F, 0.5F);
                }
            }
        };
    }

    private static void thousandNights(ServerLevel level, TravellerEntity t, Run run, int index, boolean last) {
        Vec3 center = run.aim();
        double radius = run.move().radius();
        java.util.Random random = new java.util.Random(run.seed + 977L * index);
        for (int i = 0; i < 12; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(random.nextDouble()) * radius;
            Vec3 ground = center.add(Math.cos(a) * d, 0.1, Math.sin(a) * d);
            Vec3 sky = ground.add(-3.0 + random.nextDouble() * 6.0, 14.0, -3.0 + random.nextDouble() * 6.0);
            line(level, i % 2 == 0 ? STAR : NIGHT, sky, ground, 0.7);
            burst(level, ParticleTypes.FIREWORK, ground.add(0.0, 0.3, 0.0), 8, 0.4, 0.15);
        }
        ring(level, STAR, center.add(0.0, 0.15, 0.0), radius * (0.4 + 0.2 * index), 60);
        sound(level, center, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.6F, 0.7F + 0.1F * index);
        sound(level, center, ERSounds.WEAPON_SKILL_SIGIL_BURST, 1.2F, 0.8F);
        if (last) {
            level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1.0, center.z, 3, 1.0, 0.5, 1.0, 0.0);
            burst(level, STAR, center.add(0.0, 1.5, 0.0), 120, radius * 0.4, 0.05);
            sound(level, center, SoundEvents.GENERIC_EXPLODE, 1.2F, 0.6F);
            sound(level, center, ERSounds.ART_GATE_OF_END, 1.0F, 1.3F);
        }
        for (Mob mob : foes(level, center, radius)) {
            strike(t, mob, run.move(), 1.0, last ? 0.8 : 0.0, true);
            if (last) {
                mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, 0.5, 0.0));
            }
        }
    }

    // ------------------------------------------------------------------------------------------- Siegwald

    /** Blows over an arc in front (or all around, 360) at the move's radius. */
    private static void arc(ServerLevel level, TravellerEntity t, Run run, double degrees, ParticleOptions color,
                            boolean heavy) {
        Vec3 origin = t.position();
        Vec3 forward = flat(run.point().subtract(origin));
        if (forward.lengthSqr() < 1.0E-4) {
            forward = Vec3.directionFromRotation(0.0F, t.getYRot());
        }
        forward = forward.normalize();
        double radius = run.move().radius();
        int steps = (int) Math.max(5, degrees / 12.0);
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(-degrees / 2.0 + degrees * i / steps);
            Vec3 at = origin.add(rotate(forward, a).scale(radius * 0.8)).add(0.0, 1.1, 0.0);
            level.sendParticles(color, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        boolean blade = t.who() == Travellers.Traveller.KAYLEN;
        sound(level, origin, blade ? ERSounds.SKILL_SLASH : ERSounds.SKILL_HEAVY_SLASH, 0.9F,
                blade ? 1.3F : (heavy ? 0.75F : 0.9F));
        for (Mob mob : foes(level, origin, radius + 0.6)) {
            Vec3 rel = flat(mob.position().subtract(origin));
            if (degrees < 360.0 && rel.lengthSqr() > 0.25) {
                double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, rel.normalize().dot(forward)))));
                if (angle > degrees / 2.0 + 10.0) {
                    continue;
                }
            }
            strike(t, mob, run.move(), critical(t), heavy ? 0.8 : 0.4, true);
            burst(level, ParticleTypes.CRIT, middle(mob), 10, 0.3, 0.3);
        }
    }

    private static void oath(ServerLevel level, TravellerEntity t, Run run) {
        Vec3 feet = t.position();
        t.guard(160);
        double radius = run.move().radius();
        for (int i = 0; i < 4; i++) {
            ring(level, i % 2 == 0 ? STEEL : SKY, feet.add(0.0, 0.1 + i * 0.5, 0.0), 1.5 + i * 2.2, 24 + i * 10);
        }
        level.sendParticles(ParticleTypes.FLASH, feet.x, feet.y + 1.0, feet.z, 1, 0.0, 0.0, 0.0, 0.0);
        burst(level, ParticleTypes.CLOUD, feet.add(0.0, 0.2, 0.0), 30, 1.2, 0.08);
        sound(level, feet, ERSounds.WEAPON_SKILL_QUAKE, 1.3F, 0.8F);
        sound(level, feet, ERSounds.SKILL_BUFF, 1.0F, 0.7F);
        sound(level, feet, SoundEvents.ANVIL_LAND, 0.7F, 0.6F);
        for (Mob mob : foes(level, feet, radius)) {
            if (!mob.getTags().contains(BossFight.TAG)) {
                mob.setTarget(t);
            }
            mob.setLastHurtByMob(t); // every enemy around turns on him
            if (mob.distanceTo(t) < 4.5) {
                strike(t, mob, run.move(), 1.0, 1.0, true);
            }
            burst(level, SKY, middle(mob), 6, 0.3, 0.0);
        }
        play(new Show(level, 160) {
            @Override
            void frame(int f) {
                if (!t.isAlive() || t.isDown()) {
                    return;
                }
                if (f % 5 == 0) {
                    ring(level, SKY, t.position().add(0.0, 0.1, 0.0), 1.8, 18);
                }
                if (f % 20 == 10) {
                    for (Mob mob : foes(level, t.position(), radius)) {
                        if (mob.getTarget() != t && !mob.getTags().contains(BossFight.TAG)) {
                            mob.setTarget(t);
                        }
                    }
                }
            }
        });
    }

    /** The ultimate's charge: the dragon's fire gathers along the raised blade. */
    private static Show dragonFire(ServerLevel level, TravellerEntity t, Run run) {
        return new Show(level, 36) {
            @Override
            void frame(int f) {
                Vec3 top = t.position().add(0.0, 3.6, 0.0);
                if (f >= 8 && f % 2 == 0) {
                    burst(level, ParticleTypes.FLAME, top, 6, 0.25, 0.04);
                    burst(level, FIRE, top.add(0.0, -0.8, 0.0), 4, 0.2, 0.0);
                    level.sendParticles(ParticleTypes.LAVA, top.x, top.y, top.z, 1, 0.2, 0.2, 0.2, 0.0);
                }
                if (f == 10) {
                    sound(level, top, SoundEvents.ENDER_DRAGON_GROWL, 0.6F, 0.7F);
                    sound(level, top, ERSounds.SKILL_FIRE_CAST, 1.0F, 0.8F);
                }
            }
        };
    }

    private static void dragonStroke(ServerLevel level, TravellerEntity t, Run run) {
        Vec3 origin = t.position();
        Vec3 forward = flat(run.point().subtract(origin));
        if (forward.lengthSqr() < 1.0E-4) {
            forward = Vec3.directionFromRotation(0.0F, t.getYRot());
        }
        forward = forward.normalize();
        double length = run.move().radius();
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        play(new Show(level, 14) {
            final Vec3 dir = forward;

            @Override
            void frame(int f) {
                double from = length * f / 14.0;
                double to = length * (f + 1) / 14.0;
                for (double d = from; d < to; d += 0.5) {
                    Vec3 at = origin.add(dir.scale(d)).add(0.0, 0.2, 0.0);
                    level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 3, 0.4, 0.1, 0.4, 0.02);
                    level.sendParticles(FIRE, at.x, at.y + 1.0, at.z, 2, 0.3, 0.6, 0.3, 0.0);
                    Vec3 edge = at.add(side.scale(1.4));
                    Vec3 edge2 = at.subtract(side.scale(1.4));
                    level.sendParticles(ParticleTypes.LAVA, edge.x, edge.y, edge.z, 1, 0.0, 0.0, 0.0, 0.0);
                    level.sendParticles(ParticleTypes.LAVA, edge2.x, edge2.y, edge2.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        });
        level.sendParticles(ParticleTypes.FLASH, origin.x, origin.y + 1.2, origin.z, 2, 0.5, 0.5, 0.5, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, origin.x + forward.x * 2, origin.y + 0.6, origin.z + forward.z * 2,
                3, 0.6, 0.3, 0.6, 0.0);
        sound(level, origin, ERSounds.SKILL_FIRE_EXPLOSION, 1.6F, 0.7F);
        sound(level, origin, ERSounds.WEAPON_SKILL_IMPACT, 1.4F, 0.6F);
        sound(level, origin, SoundEvents.GENERIC_EXPLODE, 1.0F, 0.8F);
        for (Mob mob : foes(level, origin.add(forward.scale(length / 2.0)), length / 2.0 + 2.0)) {
            Vec3 rel = flat(mob.position().subtract(origin));
            double along = rel.dot(forward);
            double aside = rel.subtract(forward.scale(along)).length();
            if (along < -1.0 || along > length + 1.0 || aside > 1.8 + mob.getBbWidth() * 0.5) {
                continue;
            }
            strike(t, mob, run.move(), 1.0, 1.4, true);
            mob.setSecondsOnFire(4);
            mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, 0.45, 0.0));
            burst(level, ParticleTypes.LAVA, middle(mob), 6, 0.4, 0.0);
        }
    }

    // --------------------------------------------------------------------------------------------- Kaylen

    private static double critical(TravellerEntity t) {
        return t.who() == Travellers.Traveller.KAYLEN && t.getRandom().nextFloat() < 0.25F ? 1.8 : 1.0;
    }

    private static void flashStep(ServerLevel level, TravellerEntity t, Run run, int index) {
        LivingEntity target = run.target();
        if (index == 0) {
            if (target == null || !target.isAlive()) {
                return;
            }
            Vec3 from = t.position();
            Vec3 dir = flat(target.position().subtract(from));
            if (dir.lengthSqr() < 1.0E-4) {
                return;
            }
            Vec3 behind = target.position().add(dir.normalize().scale(target.getBbWidth() * 0.5 + 1.6));
            Vec3 spot = TravellerDirector.standable(level, behind).orElse(null);
            if (spot == null) {
                return;
            }
            line(level, RUNE, from.add(0.0, 1.0, 0.0), spot.add(0.0, 1.0, 0.0), 0.3);
            burst(level, ParticleTypes.END_ROD, from.add(0.0, 1.0, 0.0), 12, 0.3, 0.05);
            t.moveTo(spot.x, spot.y, spot.z, t.getYRot(), 0.0F);
            t.face(target.position());
            sound(level, spot, ERSounds.SKILL_SHADOW_WHOOSH, 0.9F, 1.8F);
            return;
        }
        if (target != null && target.isAlive()) {
            Vec3 at = middle(target);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 a = at.add(-0.9, 0.9 * s, 0.0);
                Vec3 b = at.add(0.9, -0.9 * s, 0.0);
                line(level, RUNE, a, b, 0.15);
            }
            burst(level, ParticleTypes.CRIT, at, 20, 0.3, 0.4);
            strike(t, target, run.move(), critical(t), 0.6, true);
            for (Mob mob : foes(level, target.position(), run.move().radius())) {
                if (mob != target) {
                    strike(t, mob, run.move(), 1.5 / run.move().power(), 0.4, true);
                }
            }
        }
        sound(level, t.position(), ERSounds.SKILL_SLASH, 1.0F, 1.6F);
    }

    private static void phantomBlade(ServerLevel level, TravellerEntity t, Run run, int index) {
        List<Mob> foes = foes(level, run.point(), run.move().radius());
        Vec3 ground = run.point();
        Mob mob = foes.isEmpty() ? null : foes.get(index % foes.size());
        if (mob != null) {
            ground = mob.position();
        }
        Vec3 top = ground.add(0.0, 6.0, 0.0);
        line(level, RUNE, top, ground.add(0.0, 0.3, 0.0), 0.3);
        line(level, ParticleTypes.END_ROD, top, ground.add(0.0, 0.3, 0.0), 1.0);
        ring(level, RUNE, ground.add(0.0, 0.1, 0.0), 0.8, 12);
        burst(level, ParticleTypes.ENCHANTED_HIT, ground.add(0.0, 0.6, 0.0), 10, 0.3, 0.2);
        sound(level, ground, ERSounds.WEAPON_SKILL_IMPACT, 0.6F, 1.5F + index * 0.05F);
        if (mob != null) {
            strike(t, mob, run.move(), critical(t), 0.1, true);
        }
    }

    private static void infiniteBlades(ServerLevel level, TravellerEntity t, Run run, int index, boolean last) {
        if (!last) {
            List<Mob> foes = foes(level, run.aim(), run.move().radius());
            if (foes.isEmpty()) {
                return;
            }
            Mob mob = foes.get(index % foes.size());
            Vec3 from = t.position();
            double angle = (run.seed % 360 + index * 137.0) * Math.PI / 180.0;
            Vec3 side = new Vec3(Math.cos(angle), 0.0, Math.sin(angle)).scale(mob.getBbWidth() * 0.5 + 1.4);
            Vec3 spot = TravellerDirector.standable(level, mob.position().add(side)).orElse(null);
            if (spot != null) {
                line(level, RUNE, from.add(0.0, 1.0, 0.0), spot.add(0.0, 1.0, 0.0), 0.35);
                t.moveTo(spot.x, spot.y, spot.z, t.getYRot(), 0.0F);
                t.face(mob.position());
            }
            Vec3 at = middle(mob);
            line(level, ParticleTypes.END_ROD, at.add(-1.0, 1.0, 0.3), at.add(1.0, -1.0, -0.3), 0.25);
            burst(level, RUNE, at, 10, 0.4, 0.05);
            strike(t, mob, run.move(), critical(t), 0.1, true);
            if (!run.marked.contains(mob)) {
                run.marked.add(mob);
            }
            sound(level, at, ERSounds.SKILL_SLASH, 0.9F, 1.4F + index * 0.08F);
            return;
        }
        // the blade goes home: everything cut falls apart
        sound(level, t.position(), SoundEvents.ARMOR_EQUIP_IRON, 1.2F, 1.6F);
        sound(level, t.position(), ERSounds.WEAPON_SKILL_IMPACT, 1.4F, 0.7F);
        sound(level, t.position(), ERSounds.ART_BLACK_SWORD_RAIN, 1.0F, 0.9F);
        List<LivingEntity> cut = new ArrayList<>(run.marked);
        for (Mob mob : foes(level, run.aim(), run.move().radius())) {
            if (!cut.contains(mob)) {
                cut.add(mob);
            }
        }
        for (LivingEntity mob : cut) {
            if (!mob.isAlive()) {
                continue;
            }
            Vec3 at = middle(mob);
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 4.0;
                Vec3 d = new Vec3(Math.cos(a), Math.sin(a) * 0.8, Math.sin(a * 2.0) * 0.4).scale(1.2);
                line(level, RUNE, at.subtract(d), at.add(d), 0.2);
            }
            burst(level, ParticleTypes.FIREWORK, at, 16, 0.3, 0.15);
            strike(t, mob, run.move(), 5.0 * critical(t), 0.9, true);
        }
    }

    // --------------------------------------------------------------------------------------------- Mariel

    private static double holy(LivingEntity target) {
        return target.getMobType() == MobType.UNDEAD ? 2.0 : 1.0;
    }

    private static void holyBolt(ServerLevel level, TravellerEntity t, Run run, boolean lance) {
        LivingEntity target = run.target();
        if (target == null || !target.isAlive()) {
            return;
        }
        Vec3 to = middle(target);
        if (lance) {
            Vec3 top = target.position().add(0.0, 7.0, 0.0);
            line(level, HOLY, top, target.position(), 0.25);
            line(level, ParticleTypes.END_ROD, top, target.position(), 0.6);
            ring(level, HOLY, target.position().add(0.0, 0.1, 0.0), 1.2, 16);
            for (Mob mob : foes(level, target.position(), run.move().radius())) {
                strike(t, mob, run.move(), holy(mob), 0.3, true);
            }
        } else {
            Vec3 from = hand(t).add(0.0, 0.9, 0.0);
            line(level, HOLY, from, to, 0.3);
            line(level, ParticleTypes.END_ROD, from, to, 0.8);
            strike(t, target, run.move(), holy(target), 0.3, true);
        }
        level.sendParticles(ERParticles.HOLY.get(), to.x, to.y, to.z, 12, 0.3, 0.3, 0.3, 0.05);
        sound(level, to, ERSounds.SKILL_HOLY_CHIME, 0.8F, lance ? 1.1F : 1.5F);
    }

    /** The sanctuary: a circle of light under her that heals, purifies and raises everyone in it for six seconds. */
    private static void sanctuary(ServerLevel level, TravellerEntity t) {
        Vec3 center = t.position();
        double radius = Travellers.Move.MAR_SANCTUARY.radius();
        sound(level, center, ERSounds.SKILL_HEAL, 1.2F, 1.0F);
        play(new Show(level, 120) {
            @Override
            void frame(int f) {
                if (f % 4 == 0) {
                    ring(level, HOLY, center.add(0.0, 0.1, 0.0), radius, 48);
                    ring(level, HOLY, center.add(0.0, 0.1, 0.0), radius * 0.6, 30);
                }
                if (f % 2 == 0) {
                    double a = f * 0.31;
                    Vec3 mote = center.add(Math.cos(a) * radius * 0.8, 0.2 + (f % 20) * 0.1, Math.sin(a) * radius * 0.8);
                    level.sendParticles(ERParticles.HEAL.get(), mote.x, mote.y, mote.z, 1, 0.1, 0.1, 0.1, 0.0);
                }
                if (f % 20 != 0) {
                    return;
                }
                for (LivingEntity ally : t.allies()) {
                    if (ally.position().distanceTo(center) > radius + 0.5 || ally.level() != level) {
                        continue;
                    }
                    if (ally instanceof TravellerEntity other && other.isDown()) {
                        other.standUp(true);
                        continue;
                    }
                    ally.heal((float) (ally.getMaxHealth() * Travellers.Move.MAR_SANCTUARY.power()));
                    cleanse(ally);
                    level.sendParticles(ERParticles.HEAL.get(), ally.getX(), ally.getY() + 1.0, ally.getZ(), 6, 0.3,
                            0.5, 0.3, 0.0);
                }
                if (f == 0) {
                    sound(level, center, SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.6F);
                }
            }
        });
    }

    private static void cleanse(LivingEntity ally) {
        List<MobEffectInstance> bad = ally.getActiveEffects().stream()
                .filter(effect -> effect.getEffect().getCategory() == MobEffectCategory.HARMFUL).toList();
        for (MobEffectInstance effect : bad) {
            ally.removeEffect(effect.getEffect());
        }
        if (ally.isOnFire()) {
            ally.clearFire();
        }
    }

    private static void purification(ServerLevel level, TravellerEntity t, Run run) {
        List<Mob> foes = foes(level, run.point(), run.move().radius());
        int pillars = 0;
        for (Mob mob : foes) {
            if (pillars++ >= 5) {
                break;
            }
            Vec3 base = mob.position();
            for (double y = 0.0; y < 5.0; y += 0.35) {
                level.sendParticles(HOLY, base.x, base.y + y, base.z, 2, 0.25, 0.05, 0.25, 0.0);
            }
            level.sendParticles(ParticleTypes.END_ROD, base.x, base.y + 2.0, base.z, 8, 0.2, 1.5, 0.2, 0.02);
            ring(level, HOLY, base.add(0.0, 0.1, 0.0), 1.0, 14);
            strike(t, mob, run.move(), holy(mob), 0.0, true);
            mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, 0.35, 0.0));
        }
        sound(level, run.point(), ERSounds.WEAPON_SKILL_SIGIL_BURST, 1.0F, 1.4F);
        sound(level, run.point(), ERSounds.SKILL_HOLY_CHIME, 1.0F, 0.9F);
    }

    /** The first seconds of her prayer: light rises around her, feathers begin to fall. */
    private static Show prayer(ServerLevel level, TravellerEntity t) {
        Vec3 feet = t.position();
        return new Show(level, 50) {
            @Override
            void frame(int f) {
                if (f % 3 == 0) {
                    ring(level, HOLY, feet.add(0.0, 0.1, 0.0), 2.0 + f * 0.05, 24);
                }
                if (f >= 16 && f % 2 == 0) {
                    level.sendParticles(ERParticles.HOLY.get(), feet.x, feet.y + 2.5, feet.z, 4, 1.8, 0.6, 0.6, 0.01);
                }
                if (f == 20) {
                    sound(level, feet, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 0.7F);
                }
            }
        };
    }

    private static void kingdomReborn(ServerLevel level, TravellerEntity t, Run run) {
        Vec3 center = t.position();
        sound(level, center, ERSounds.SKILL_HEAL, 1.6F, 0.8F);
        sound(level, center, ERSounds.SKILL_BUFF, 1.4F, 1.0F);
        sound(level, center, SoundEvents.TOTEM_USE, 0.6F, 1.4F);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 2.0, center.z, 2, 0.4, 0.4, 0.4, 0.0);
        for (int i = 0; i < 4; i++) {
            ring(level, HOLY, center.add(0.0, 0.2 + i * 0.4, 0.0), 3.0 + i * 3.5, 40 + i * 12);
        }
        ServerPlayer player = t.owner();
        if (player != null && RevivalService.isDown(player) && player.level() == level
                && player.distanceTo(t) <= 16.0) {
            RevivalService.miracle(player, net.minecraft.network.chat.Component.translatable(t.who().nameKey()));
        }
        for (LivingEntity ally : t.allies()) {
            if (ally.level() != level || ally.distanceTo(t) > 16.0) {
                continue;
            }
            if (ally instanceof TravellerEntity other) {
                other.standUp(true);
            }
            ally.setHealth(ally.getMaxHealth());
            cleanse(ally);
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, true));
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0, false, true));
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, ally.getX(), ally.getY() + 1.0, ally.getZ(), 30, 0.4,
                    0.6, 0.4, 0.3);
        }
        for (Mob mob : foes(level, center, run.move().radius())) {
            strike(t, mob, run.move(), holy(mob), 0.6, true);
            mob.addEffect(new MobEffectInstance(ERMobEffects.STUNNED.get(), 60, 0, false, true));
            level.sendParticles(ERParticles.HOLY.get(), mob.getX(), mob.getY() + 1.0, mob.getZ(), 8, 0.3, 0.5, 0.3,
                    0.02);
        }
        play(new Show(level, 30) {
            @Override
            void frame(int f) {
                level.sendParticles(ERParticles.HOLY.get(), center.x, center.y + 6.0, center.z, 6, 6.0, 1.0, 6.0,
                        0.0);
            }
        });
    }

    /** Mariel, between moves: heals or raises whoever needs it (in a fight or not). */
    static void tend(TravellerEntity mariel) {
        if (mariel.running() != null || mariel.isDown()) {
            return;
        }
        boolean playerDown = false;
        boolean companionDown = false;
        double worst = 1.0;
        for (LivingEntity ally : mariel.allies()) {
            if (ally instanceof ServerPlayer p && RevivalService.isDown(p)) {
                playerDown = true;
            } else if (ally instanceof TravellerEntity t && t.isDown()) {
                companionDown = true;
            } else {
                worst = Math.min(worst, ally.getHealth() / Math.max(1.0F, ally.getMaxHealth()));
            }
        }
        Travellers.Move ultimate = Travellers.Move.MAR_KINGDOM_REBORN;
        Travellers.Move sanctuary = Travellers.Move.MAR_SANCTUARY;
        if (playerDown && mariel.ready(ultimate)) {
            mariel.perform(ultimate, null);
        } else if ((companionDown || worst < 0.6) && mariel.ready(sanctuary)) {
            mariel.perform(sanctuary, null);
        }
    }

    // ------------------------------------------------------------------------------- gestures and arrivals

    /** Joining the party (and a won fight): the body's gesture and a burst of the traveller's own light. */
    static void cheer(TravellerEntity t) {
        if (!(t.level() instanceof ServerLevel level)) {
            return;
        }
        announce(t, JOIN, t.position(), t.getRandom().nextInt());
        Vec3 at = t.position().add(0.0, 1.2, 0.0);
        ParticleOptions color = switch (t.who()) {
            case ROSALIND -> STAR;
            case SIEGWALD -> FIRE;
            case KAYLEN -> RUNE;
            case MARIEL -> HOLY;
        };
        burst(level, color, at, 30, 0.6, 0.02);
        burst(level, ParticleTypes.END_ROD, at, 12, 0.5, 0.05);
        sound(level, at, ERSounds.SKILL_BUFF, 0.8F, 1.3F);
    }

    static void standUp(TravellerEntity t, boolean miracle) {
        if (!(t.level() instanceof ServerLevel level)) {
            return;
        }
        announce(t, STAND_UP, t.position(), 0);
        Vec3 at = t.position().add(0.0, 1.0, 0.0);
        burst(level, miracle ? HOLY : STEEL, at, 24, 0.5, 0.02);
        sound(level, at, miracle ? ERSounds.SKILL_HEAL : SoundEvents.ARMOR_EQUIP_IRON, 0.8F, 1.2F);
    }

    /** A purchase at the shop: a few coins between the traveller and the shopkeeper. */
    static void coins(TravellerEntity traveller, NpcEntity shopkeeper) {
        if (traveller.level() instanceof ServerLevel level) {
            Vec3 from = hand(traveller);
            Vec3 to = middle(shopkeeper);
            line(level, dust(1.0F, 0.84F, 0.3F, 0.8F), from, to, 0.4);
            level.sendParticles(ParticleTypes.WAX_ON, to.x, to.y, to.z, 4, 0.2, 0.2, 0.2, 0.0);
        }
    }

    /** It leaves (or arrives): a swirl of light, as a waystone's trip looks. */
    static void trip(TravellerEntity traveller, boolean arriving) {
        if (!(traveller.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 feet = traveller.position();
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0;
            double h = (i % 8) * 0.25;
            level.sendParticles(ParticleTypes.END_ROD, feet.x + Math.cos(a) * 0.7, feet.y + h,
                    feet.z + Math.sin(a) * 0.7, 1, 0.0, 0.05, 0.0, 0.02);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, feet.x, feet.y + 1.0, feet.z, 30, 0.4, 0.8, 0.4, 0.05);
        level.playSound(null, feet.x, feet.y, feet.z, arriving ? SoundEvents.BEACON_POWER_SELECT
                : SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 0.5F, 1.6F);
    }

    // ----------------------------------------------------------------------------------------------- hits

    /** Monsters and bosses near {@code at}, nearest first. */
    static List<Mob> foes(ServerLevel level, Vec3 at, double radius) {
        List<Mob> found = level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(radius, 4.0, radius),
                mob -> TravellerEntity.isFoe(mob)
                        && mob.position().distanceToSqr(at.x, mob.getY(), at.z) <= radius * radius);
        found.sort(Comparator.comparingDouble(mob -> mob.position().distanceToSqr(at)));
        return found;
    }

    /**
     * One blow of {@code move} times {@code factor} (critical blows, the undead under holy light...). A companion's
     * blow counts as its player's (the kill, the reward), and the player sees its number.
     */
    static void strike(TravellerEntity t, LivingEntity target, Travellers.Move move, double factor, double push,
                       boolean skill) {
        if (!target.isAlive() || !TravellerEntity.isFoe(target)) {
            return;
        }
        boolean boss = target.getTags().contains(BossFight.TAG);
        int level = MonsterTags.isMonster(target) ? MonsterTags.level(target) : bossLevel(t);
        double amount = Travellers.damage(move, level, boss, 1.0) * factor;
        ServerPlayer player = t.owner();
        if (player != null && target instanceof Mob mob) {
            mob.setLastHurtByPlayer(player);
        }
        target.invulnerableTime = 0; // several blows in one move must all count
        if (target.hurt(t.damageSources().mobAttack(t), (float) amount) && player != null
                && player.level() == target.level() && player.distanceTo(target) < 48.0) {
            ERNetwork.sendToPlayer(player, new DamageNumberPacket(target.getId(), (float) target.getX(),
                    (float) (target.getY() + target.getBbHeight()), (float) target.getZ(), (float) amount,
                    (factor > 1.5 && move.kind() != Travellers.Kind.ULTIMATE ? DamageNumberPacket.CRITICAL : 0)
                            | (skill && move.kind() != Travellers.Kind.ATTACK ? DamageNumberPacket.SKILL : 0)));
        }
        if (push > 0.0) {
            Vec3 away = flat(target.position().subtract(t.position()));
            if (away.lengthSqr() > 1.0E-6) {
                away = away.normalize();
                target.knockback(push, -away.x, -away.z);
            }
        }
    }

    /** A boss's level: the player's (the fight is balanced for them). */
    private static int bossLevel(TravellerEntity t) {
        ServerPlayer player = t.owner();
        if (player == null) {
            return 50;
        }
        return com.eternalrealm.server.ServerContext.current()
                .flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(com.eternalrealm.character.CharacterSnapshot::level).orElse(50);
    }

    // ------------------------------------------------------------------------------------------- drawing

    static Vec3 hand(TravellerEntity t) {
        Vec3 look = Vec3.directionFromRotation(0.0F, t.getYHeadRot());
        return t.position().add(0.0, t.getBbHeight() * 0.62, 0.0).add(look.scale(0.6));
    }

    static Vec3 middle(LivingEntity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
    }

    static Vec3 flat(Vec3 v) {
        return new Vec3(v.x, 0.0, v.z);
    }

    /** {@code v} turned by {@code radians} about the vertical. */
    static Vec3 rotate(Vec3 v, double radians) {
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double step) {
        Vec3 span = to.subtract(from);
        int points = Math.max(1, (int) Math.ceil(span.length() / step));
        for (int i = 0; i <= points; i++) {
            Vec3 at = from.add(span.scale(i / (double) points));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points) {
        int n = Math.max(6, points);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2.0 / n;
            level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1,
                    0.0, 0.0, 0.0, 0.0);
        }
    }

    static void burst(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, pitch);
    }

    static void sound(ServerLevel level, Vec3 at, Supplier<SoundEvent> sound, float volume, float pitch) {
        sound(level, at, sound.get(), volume, pitch);
    }
}
