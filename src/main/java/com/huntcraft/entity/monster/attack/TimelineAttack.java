package com.huntcraft.entity.monster.attack;

import com.huntcraft.core.Element;
import com.huntcraft.entity.misc.HazardEntity;
import com.huntcraft.entity.monster.HuntMonster;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Declarative attack built from timed events. Example:
 * <pre>
 * TimelineAttack.builder("bite", 30).range(0, 5).angle(40).weight(12).cooldown(40)
 *     .track(0, 10, 8)                         // turn toward target 8 deg/tick during wind-up
 *     .move(10, 13, 0.5, 0)                    // lunge forward
 *     .hit(13, 0, 1.6, 3.5, 1.8F, 12, Element.NONE, 1.0F)
 *     .sound(12, HCSounds.BITE, 1.5F, 0.9F)
 *     .build();
 * </pre>
 * Offsets (side, up, forward) are in blocks at scale 1 and are multiplied by the monster's size scale.
 * Damage values are LOW rank values; rank/enrage multipliers are applied automatically.
 */
public class TimelineAttack extends MonsterAttack {
    @FunctionalInterface
    public interface Action {
        void run(HuntMonster monster, @Nullable LivingEntity target, int t);
    }

    private record Event(int from, int to, Action action) {
    }

    private final List<Event> events;
    private final List<Action> startActions;
    private final List<Action> endActions;

    protected TimelineAttack(Builder b) {
        super(b.name, b.duration);
        this.anim = b.anim;
        this.cooldown = b.cooldown;
        this.minRange = b.minRange;
        this.maxRange = b.maxRange;
        this.maxAngle = b.maxAngle;
        this.weight = b.weight;
        this.enragedOnly = b.enragedOnly;
        this.groundOnly = b.groundOnly;
        this.airOnly = b.airOnly;
        this.allowedWhenTired = b.allowedWhenTired;
        this.events = List.copyOf(b.events);
        this.startActions = List.copyOf(b.startActions);
        this.endActions = List.copyOf(b.endActions);
    }

    public static Builder builder(String name, int duration) {
        return new Builder(name, duration);
    }

    @Override
    public void start(HuntMonster monster, @Nullable LivingEntity target) {
        for (Action a : startActions) a.run(monster, target, 0);
    }

    @Override
    public void tick(HuntMonster monster, @Nullable LivingEntity target, int t) {
        for (Event e : events) {
            if (t >= e.from && t <= e.to) e.action.run(monster, target, t);
        }
    }

    @Override
    public void end(HuntMonster monster) {
        for (Action a : endActions) a.run(monster, null, duration);
    }

    public static final class Builder {
        private final String name;
        private final int duration;
        private String anim;
        private int cooldown = 60;
        private float minRange = 0, maxRange = 6, maxAngle = 50;
        private int weight = 10;
        private boolean enragedOnly, groundOnly = true, airOnly, allowedWhenTired = true;
        private final List<Event> events = new ArrayList<>();
        private final List<Action> startActions = new ArrayList<>();
        private final List<Action> endActions = new ArrayList<>();

        private Builder(String name, int duration) {
            this.name = name;
            this.anim = name;
            this.duration = duration;
        }

        public Builder anim(String a) { this.anim = a; return this; }
        public Builder cooldown(int c) { this.cooldown = c; return this; }
        public Builder range(float min, float max) { this.minRange = min; this.maxRange = max; return this; }
        public Builder angle(float deg) { this.maxAngle = deg; return this; }
        public Builder weight(int w) { this.weight = w; return this; }
        public Builder enragedOnly() { this.enragedOnly = true; return this; }
        /** Usable both on ground and in the air. */
        public Builder anywhere() { this.groundOnly = false; this.airOnly = false; return this; }
        public Builder airOnly() { this.groundOnly = false; this.airOnly = true; return this; }
        /** Breath attacks: fizzle (not selected) while tired. */
        public Builder notWhenTired() { this.allowedWhenTired = false; return this; }

        public Builder at(int tick, Action action) { events.add(new Event(tick, tick, action)); return this; }
        public Builder span(int from, int to, Action action) { events.add(new Event(from, to, action)); return this; }
        public Builder onStart(Action a) { startActions.add(a); return this; }
        public Builder onEnd(Action a) { endActions.add(a); return this; }

        /** Turn toward the target at up to degPerTick between ticks [from, to]. */
        public Builder track(int from, int to, float degPerTick) {
            return span(from, to, (m, target, t) -> { if (target != null) m.turnTowards(target.position(), degPerTick); });
        }

        /** One-tick sphere hit. */
        public Builder hit(int tick, double side, double up, double forward, float radius, float damage, Element element, float knockback) {
            return at(tick, (m, target, t) -> m.hitSphere(m.localPos(side, up, forward), radius * m.getMonsterScale(), damage, element, knockback, 0));
        }

        /** Sphere hit checked every tick in [from, to]; each entity is hit at most once per attack. */
        public Builder hitSpan(int from, int to, double side, double up, double forward, float radius, float damage, Element element, float knockback) {
            return span(from, to, (m, target, t) -> m.hitSphere(m.localPos(side, up, forward), radius * m.getMonsterScale(), damage, element, knockback, 0));
        }

        /** Hit with extra flags (MonsterDamage.FLAG_*). */
        public Builder hitFlags(int tick, double side, double up, double forward, float radius, float damage, Element element, float knockback, int flags) {
            return at(tick, (m, target, t) -> m.hitSphere(m.localPos(side, up, forward), radius * m.getMonsterScale(), damage, element, knockback, flags));
        }

        /**
         * Arc sweep around the monster (tail spin / wide swipe). Angles in degrees relative to the facing at the
         * time of each tick: 0 = front, 90 = right, 180 = back, -90 = left. The arc is swept progressively.
         */
        public Builder sweep(int from, int to, float innerR, float outerR, float startDeg, float endDeg, float height, float damage, Element element, float knockback) {
            return span(from, to, (m, target, t) -> {
                float p0 = to == from ? 0 : (float) (t - from) / (to - from + 1);
                float p1 = to == from ? 1 : (float) (t - from + 1) / (to - from + 1);
                float a0 = startDeg + (endDeg - startDeg) * p0;
                float a1 = startDeg + (endDeg - startDeg) * p1;
                float s = m.getMonsterScale();
                m.hitArc(innerR * s, outerR * s, Math.min(a0, a1), Math.max(a0, a1), height * s, damage, element, knockback, 0);
            });
        }

        /** Sets the monster's velocity each tick: forward and up speed in blocks/tick (scaled by size). */
        public Builder move(int from, int to, double forwardSpeed, double upSpeed) {
            return span(from, to, (m, target, t) -> m.moveLocal(forwardSpeed, upSpeed));
        }

        /** Lunges toward the target's position each tick (blocks/tick). */
        public Builder lunge(int from, int to, double speed) {
            return span(from, to, (m, target, t) -> { if (target != null) m.lungeTowards(target.position(), speed); });
        }

        public Builder projectile(int tick, double side, double up, double forward, ProjectileSpec spec) {
            return at(tick, (m, target, t) -> m.shootProjectile(m.localPos(side, up, forward), target, spec));
        }

        /** Cone breath: damage + particles every tick in [from, to]. Length / offsets scaled by size. */
        public Builder breath(int from, int to, double side, double up, double forward, float length, float coneDeg, float damage, Element element, ParticleOptions particle) {
            return span(from, to, (m, target, t) -> m.breathCone(m.localPos(side, up, forward), length * m.getMonsterScale(), coneDeg, damage, element, particle, t - from));
        }

        public Builder sound(int tick, Supplier<SoundEvent> sound, float volume, float pitch) {
            return at(tick, (m, target, t) -> m.playSound(sound.get(), volume, pitch));
        }

        public Builder roar(int tick, float radius) {
            return at(tick, (m, target, t) -> m.doRoar(radius * m.getMonsterScale()));
        }

        public Builder tremor(int tick, float radius) {
            return at(tick, (m, target, t) -> m.doTremor(radius * m.getMonsterScale()));
        }

        public Builder wind(int tick, float radius) {
            return at(tick, (m, target, t) -> m.doWindPressure(radius * m.getMonsterScale()));
        }

        public Builder particles(int tick, ParticleOptions particle, double side, double up, double forward, int count, double spread) {
            return at(tick, (m, target, t) -> m.burstParticles(particle, m.localPos(side, up, forward), count, spread * m.getMonsterScale()));
        }

        public Builder particlesSpan(int from, int to, ParticleOptions particle, double side, double up, double forward, int count, double spread) {
            return span(from, to, (m, target, t) -> m.burstParticles(particle, m.localPos(side, up, forward), count, spread * m.getMonsterScale()));
        }

        /** Spawns a hazard at an offset from the monster (or at the target when atTarget). */
        public Builder hazard(int tick, HazardEntity.Kind kind, boolean atTarget, double side, double forward, float radius, int warmup, int active, float damage, Element element) {
            return at(tick, (m, target, t) -> {
                Vec3 pos = atTarget && target != null ? target.position() : m.localPos(side, 0, forward);
                HazardEntity.spawn(m, pos, kind, radius * m.getMonsterScale(), warmup, active, damage * m.damageMultiplier(), element);
            });
        }

        public Builder flying(int tick, boolean flying) {
            return at(tick, (m, target, t) -> m.setFlying(flying));
        }

        public TimelineAttack build() {
            return new TimelineAttack(this);
        }
    }
}
