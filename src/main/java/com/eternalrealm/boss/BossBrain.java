package com.eternalrealm.boss;

import com.eternalrealm.combat.HitGeometry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Decides what one boss does and when (docs/modules/boss.md). One instance per boss entity, server thread only.
 *
 * <p>The platform calls {@link #tick(long, double, BossPosition, List)} exactly once per server tick and carries out
 * the returned {@link BossCommand}s; vanilla AI keeps handling movement and the basic melee in between abilities.
 * Rules:</p>
 * <ul>
 *     <li>Nothing happens before the first tick with a target; that tick starts the fight (phase, enrage timer).
 *         Without targets no new ability starts (one already telegraphed still resolves, so leaving the arena
 *         for a tick cannot cancel an attack).</li>
 *     <li>One ability at a time. Its shapes are placed when the telegraph starts and it executes exactly
 *         {@code telegraphTicks} later with those shapes, so players who saw the warning can move out.</li>
 *     <li>After an ability the phase's global cooldown passes before the next one; each ability also has its own
 *         cooldown. Choice: the phase's openers first, then a weighted random pick among the ready abilities.</li>
 *     <li>Crossing a phase threshold cancels the current ability and starts an invulnerable transition of
 *         {@link BossRules#PHASE_TRANSITION_TICKS} ticks.</li>
 *     <li>Soft enrage {@code enrageAfterTicks} after the fight started: more damage, shorter cooldowns.</li>
 *     <li>Deterministic: the same definition, party size, difficulty, random seed and inputs give the same commands.
 *         Targets are ordered by UUID first, so the platform's list order does not matter.</li>
 * </ul>
 */
public final class BossBrain {
    private static final long NEVER = Long.MIN_VALUE / 4;
    private static final Comparator<TargetSnapshot> BY_ID = Comparator.comparing(TargetSnapshot::id);

    private final BossDefinition definition;
    private final Difficulty difficulty;
    private final int partySize;
    private final RandomGenerator random;
    private final DifficultyTuning tuning;
    private final List<List<BossAbility>> rotations;
    private final double maxHealth;

    private long lastTick = NEVER;
    private boolean started;
    private boolean dead;
    private long fightStartTick = NEVER;
    private int phaseIndex;
    private final ArrayDeque<String> openers = new ArrayDeque<>();
    private Active active;
    private long nextAbilityTick = NEVER;
    private final Map<String, Long> readyAt = new HashMap<>();
    private boolean enraged;
    private long transitionUntil = NEVER;
    private ShieldState shield;
    private double buffMultiplier = 1.0;
    private long buffUntil = NEVER;
    private boolean shieldShown;
    private final List<BossCommand> outbox = new ArrayList<>();

    /**
     * @param partySize players in the fight when it is created (fixes the health; 1..{@link BossRules#MAX_PARTY_SIZE})
     * @param random    source of every random choice; seed it to replay a fight
     */
    public BossBrain(BossDefinition definition, int partySize, Difficulty difficulty, RandomGenerator random) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        this.partySize = Check.range("partySize", partySize, 1, BossRules.MAX_PARTY_SIZE);
        this.random = Objects.requireNonNull(random, "random");
        this.tuning = definition.tuning(difficulty);
        List<List<BossAbility>> perPhase = new ArrayList<>();
        for (int i = 0; i < definition.phases().size(); i++) {
            perPhase.add(definition.abilitiesFor(i, difficulty));
        }
        this.rotations = List.copyOf(perPhase);
        this.maxHealth = definition.maxHealth(partySize, difficulty);
    }

    // ------------------------------------------------------------------ tick

    /**
     * Advances the boss to {@code now} and returns what the platform must do, in order.
     *
     * @param now        server tick; never smaller than in the previous call
     * @param hpFraction current health / maximum health in {@code [0, 1]}; 0 means dead (the brain stops for good)
     * @param self       the boss's position this tick
     * @param targets    the players it may attack (alive, inside the arena); may be empty
     */
    public List<BossCommand> tick(long now, double hpFraction, BossPosition self, List<TargetSnapshot> targets) {
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(targets, "targets");
        Check.range("hpFraction", hpFraction, 0.0, 1.0);
        if (now < lastTick) {
            throw new IllegalArgumentException("time went backwards: " + now + " < " + lastTick);
        }
        lastTick = now;
        if (dead) {
            return List.of();
        }
        if (hpFraction == 0.0) {
            die();
            return drain();
        }
        List<TargetSnapshot> sorted = new ArrayList<>(targets);
        sorted.sort(BY_ID);
        if (!started) {
            if (sorted.isEmpty()) {
                return List.of();
            }
            begin(now, hpFraction);
        }
        updatePhase(now, hpFraction);
        updateEnrage(now);
        expire(now);
        advanceActive(now);
        syncShield(now); // a shield that ended is reported before the next ability starts
        if (active == null && !sorted.isEmpty() && now >= nextAbilityTick && now >= transitionUntil) {
            BossAbility next = choose(now, hpFraction, sorted.size());
            if (next != null) {
                start(next, now, self, sorted);
                advanceActive(now); // an ability without telegraph executes at once
            }
        }
        syncShield(now);
        return drain();
    }

    private List<BossCommand> drain() {
        if (outbox.isEmpty()) {
            return List.of();
        }
        List<BossCommand> commands = List.copyOf(outbox);
        outbox.clear();
        return commands;
    }

    private void begin(long now, double hpFraction) {
        started = true;
        fightStartTick = now;
        enterPhase(definition.phaseIndexFor(hpFraction));
        nextAbilityTick = now + scaled(currentPhase().globalCooldownTicks());
    }

    private void enterPhase(int index) {
        phaseIndex = index;
        openers.clear();
        openers.addAll(currentPhase().openerIds());
        outbox.add(new BossCommand.PhaseChanged(index, currentPhase()));
    }

    private void updatePhase(long now, double hpFraction) {
        int target = definition.phaseIndexFor(hpFraction);
        if (target <= phaseIndex) {
            return; // phases never go back, even if the boss heals above a threshold
        }
        cancelActive();
        shield = null;
        enterPhase(target);
        transitionUntil = now + BossRules.PHASE_TRANSITION_TICKS;
        nextAbilityTick = Math.max(nextAbilityTick, transitionUntil);
    }

    private void updateEnrage(long now) {
        if (!enraged && now - fightStartTick >= definition.enrageAfterTicks()) {
            enraged = true;
            outbox.add(new BossCommand.Enraged());
        }
    }

    private void expire(long now) {
        if (buffUntil != NEVER && now >= buffUntil) {
            buffMultiplier = 1.0;
            buffUntil = NEVER;
        }
        if (shield != null && !shield.holds(now)) {
            shield = null;
        }
    }

    private void die() {
        dead = true;
        cancelActive();
        shield = null;
        transitionUntil = NEVER;
        if (shieldShown) {
            shieldShown = false;
            outbox.add(new BossCommand.ShieldChanged(false));
        }
    }

    private void cancelActive() {
        if (active != null && !active.executed) {
            outbox.add(new BossCommand.Cancelled(active.ability));
        }
        active = null;
    }

    private void syncShield(long now) {
        boolean on = isInvulnerable(now);
        if (on != shieldShown) {
            shieldShown = on;
            outbox.add(new BossCommand.ShieldChanged(on));
        }
    }

    // ------------------------------------------------------------------ abilities

    private void advanceActive(long now) {
        if (active == null) {
            return;
        }
        if (!active.executed && now >= active.executeTick) {
            execute(now);
        }
        if (active.executed && now >= active.endTick) {
            nextAbilityTick = Math.max(nextAbilityTick, active.endTick + scaled(currentPhase().globalCooldownTicks()));
            active = null;
        }
    }

    private BossAbility choose(long now, double hpFraction, int targetCount) {
        while (!openers.isEmpty()) {
            BossAbility opener = definition.ability(openers.poll()).orElseThrow();
            if (usable(opener, now, hpFraction, targetCount)) {
                return opener; // scripted: ignores its own cooldown
            }
        }
        List<BossAbility> ready = new ArrayList<>();
        int totalWeight = 0;
        for (BossAbility ability : rotations.get(phaseIndex)) {
            if (now >= readyAt.getOrDefault(ability.id(), NEVER) && usable(ability, now, hpFraction, targetCount)) {
                ready.add(ability);
                totalWeight += ability.weight();
            }
        }
        if (ready.isEmpty()) {
            return null;
        }
        int roll = random.nextInt(totalWeight);
        for (BossAbility ability : ready) {
            roll -= ability.weight();
            if (roll < 0) {
                return ability;
            }
        }
        throw new IllegalStateException("weighted pick out of range");
    }

    /** Situational rules that keep abilities meaningful and fair. */
    private boolean usable(BossAbility ability, long now, double hpFraction, int targetCount) {
        AbilityKind kind = ability.kind();
        if (kind instanceof AbilityKind.Soak soak) {
            return targetCount >= soak.minPlayers();
        }
        if (kind instanceof AbilityKind.Heal heal) {
            return hpFraction <= 1.0 - heal.fraction();
        }
        if (kind instanceof AbilityKind.Shield) {
            return shield == null;
        }
        if (kind instanceof AbilityKind.Buff) {
            return now >= buffUntil;
        }
        return true;
    }

    private void start(BossAbility ability, long now, BossPosition self, List<TargetSnapshot> targets) {
        Point origin = Point.of(self);
        Plan plan = plan(ability, origin, self.yawDegrees(), targets);
        long executeTick = now + ability.telegraphTicks();
        active = new Active(ability, origin, executeTick, executeTick + ability.castTicks(), plan);
        readyAt.put(ability.id(), now + scaled(ability.cooldownTicks()));
        outbox.add(new BossCommand.AbilityStarted(ability, ability.telegraphTicks()));
        for (BossShape shape : plan.shapes()) {
            outbox.add(new BossCommand.Telegraph(ability, shape, ability.telegraphTicks()));
        }
    }

    /** Places everything an ability will do, at the moment its telegraph starts. */
    private Plan plan(BossAbility ability, Point origin, double bossYaw, List<TargetSnapshot> targets) {
        AbilityKind kind = ability.kind();
        TargetRule rule = ability.targetRule();
        if (kind instanceof AbilityKind.CircleAroundBoss circle) {
            return Plan.of(new BossShape.Circle(origin, circle.radius()));
        }
        if (kind instanceof AbilityKind.CircleAtTargets circles) {
            List<BossShape> shapes = new ArrayList<>();
            for (TargetSnapshot target : pick(rule, circles.maxTargets(), origin, targets)) {
                shapes.add(new BossShape.Circle(Point.of(target), circles.radius()));
            }
            return new Plan(shapes, List.of(), null, null, List.of());
        }
        if (kind instanceof AbilityKind.Cone cone) {
            double yaw = yawTowards(origin, pickOne(rule, origin, targets), bossYaw);
            return Plan.of(new BossShape.Cone(origin, yaw, cone.range(), cone.halfAngleDegrees()));
        }
        if (kind instanceof AbilityKind.Line line) {
            double yaw = yawTowards(origin, pickOne(rule, origin, targets), bossYaw);
            double[] direction = HitGeometry.look(yaw, 0.0);
            Point end = new Point(origin.x() + direction[0] * line.length(), origin.y(),
                    origin.z() + direction[2] * line.length());
            return Plan.of(new BossShape.Line(origin, end, line.width()));
        }
        if (kind instanceof AbilityKind.Ring ring) {
            return Plan.of(new BossShape.Ring(origin, ring.inner(), ring.outer()));
        }
        if (kind instanceof AbilityKind.ArenaWide) {
            return Plan.of(new BossShape.Arena(origin));
        }
        if (kind instanceof AbilityKind.Soak soak) {
            return Plan.of(new BossShape.Circle(Point.of(pickOne(rule, origin, targets)), soak.radius()));
        }
        if (kind instanceof AbilityKind.ProjectileVolley volley) {
            double yaw = yawTowards(origin, pickOne(rule, origin, targets), bossYaw);
            double halfAngle = Math.max(volley.spreadDegrees() / 2.0, BossRules.VOLLEY_MIN_HALF_ANGLE);
            return new Plan(List.of(new BossShape.Cone(origin, yaw, BossRules.VOLLEY_RANGE, halfAngle)), List.of(),
                    null, null, fan(yaw, volley.count(), volley.spreadDegrees()));
        }
        if (kind instanceof AbilityKind.Summon summon) {
            return Plan.markers(around(origin, bossYaw, summon.count()));
        }
        if (kind instanceof AbilityKind.Shield shieldKind) {
            return Plan.markers(shieldKind.addCount() > 0 ? around(origin, bossYaw, shieldKind.addCount()) : List.of());
        }
        if (kind instanceof AbilityKind.TeleportToTarget) {
            TargetSnapshot target = pickOne(rule, origin, targets);
            Point destination = Point.of(target);
            return new Plan(List.of(new BossShape.Circle(destination, BossRules.MARKER_RADIUS)), List.of(),
                    target.id(), destination, List.of());
        }
        return new Plan(List.of(), List.of(), null, null, List.of()); // heal, buff: a cast bar only
    }

    private void execute(long now) {
        Active current = active;
        current.executed = true;
        BossAbility ability = current.ability;
        AbilityKind kind = ability.kind();
        double multiplier = ability.damageMultiplier() * damageFactor(now);
        double damage = definition.damage() * multiplier;
        if (kind instanceof AbilityKind.ProjectileVolley volley) {
            outbox.add(new BossCommand.Volley(ability, current.origin, current.plan.yaws(), volley.speed(), multiplier,
                    damage));
        } else if (kind.damaging()) {
            for (BossShape shape : current.plan.shapes()) {
                outbox.add(new BossCommand.Execute(ability, shape, multiplier, damage));
            }
        } else if (kind instanceof AbilityKind.Summon summon) {
            outbox.add(new BossCommand.Summon(summon.templateId(), summon.count(), current.plan.positions(), false));
        } else if (kind instanceof AbilityKind.TeleportToTarget) {
            outbox.add(new BossCommand.Teleport(current.plan.target(), current.plan.destination()));
        } else if (kind instanceof AbilityKind.Shield shieldKind) {
            shield = new ShieldState(now + shieldKind.timeoutTicks(), shieldKind.addCount());
            if (shieldKind.addCount() > 0) {
                outbox.add(new BossCommand.Summon(shieldKind.addTemplateId(), shieldKind.addCount(),
                        current.plan.positions(), true));
            }
        } else if (kind instanceof AbilityKind.Heal heal) {
            outbox.add(new BossCommand.Heal(heal.fraction()));
        } else if (kind instanceof AbilityKind.Buff buff) {
            buffMultiplier = buff.damageMultiplier();
            buffUntil = now + buff.durationTicks();
            outbox.add(new BossCommand.Buffed(buff.damageMultiplier(), buff.durationTicks()));
        }
    }

    // ------------------------------------------------------------------ targeting & geometry

    private TargetSnapshot pickOne(TargetRule rule, Point origin, List<TargetSnapshot> targets) {
        return pick(rule, 1, origin, targets).get(0);
    }

    /** Up to {@code count} distinct targets by {@code rule}; {@code targets} is sorted by UUID (stable tie-break). */
    private List<TargetSnapshot> pick(TargetRule rule, int count, Point origin, List<TargetSnapshot> targets) {
        List<TargetSnapshot> ordered = new ArrayList<>(targets);
        int n = Math.min(count, ordered.size());
        switch (rule) {
            case NEAREST -> ordered.sort(Comparator.comparingDouble(target -> distanceSquared(origin, target)));
            case FARTHEST -> ordered.sort(Comparator.comparingDouble(
                    (TargetSnapshot target) -> distanceSquared(origin, target)).reversed());
            case HIGHEST_THREAT -> ordered.sort(Comparator.comparingDouble(TargetSnapshot::threat).reversed()
                    .thenComparingDouble(target -> distanceSquared(origin, target)));
            case RANDOM -> {
                for (int i = 0; i < n; i++) {
                    Collections.swap(ordered, i, i + random.nextInt(ordered.size() - i));
                }
            }
        }
        return List.copyOf(ordered.subList(0, n));
    }

    private static double distanceSquared(Point origin, TargetSnapshot target) {
        return origin.horizontalDistanceSquared(target.x(), target.z());
    }

    /** Minecraft yaw from {@code origin} towards the target; the boss's own yaw if the target stands on it. */
    static double yawTowards(Point origin, TargetSnapshot target, double fallbackYaw) {
        double dx = target.x() - origin.x();
        double dz = target.z() - origin.z();
        if (dx * dx + dz * dz < 1.0e-6) {
            return fallbackYaw;
        }
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static List<Double> fan(double centerYaw, int count, double spreadDegrees) {
        if (count == 1) {
            return List.of(centerYaw);
        }
        List<Double> yaws = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            yaws.add(centerYaw - spreadDegrees / 2.0 + spreadDegrees * i / (count - 1));
        }
        return yaws;
    }

    /** {@code count} points evenly spaced on a circle around the boss, the first one in front of it. */
    private static List<Point> around(Point center, double startYaw, int count) {
        List<Point> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double[] direction = HitGeometry.look(startYaw + 360.0 * i / count, 0.0);
            positions.add(new Point(center.x() + direction[0] * BossRules.SUMMON_RADIUS, center.y(),
                    center.z() + direction[2] * BossRules.SUMMON_RADIUS));
        }
        return positions;
    }

    private double damageFactor(long now) {
        double factor = tuning.damageMultiplier();
        if (enraged) {
            factor *= BossRules.ENRAGE_DAMAGE_MULTIPLIER;
        }
        if (now < buffUntil) {
            factor *= buffMultiplier;
        }
        return factor;
    }

    private long scaled(int ticks) {
        double factor = tuning.cooldownMultiplier() * (enraged ? BossRules.ENRAGE_COOLDOWN_MULTIPLIER : 1.0);
        return Math.round(ticks * factor);
    }

    // ------------------------------------------------------------------ platform callbacks & queries

    /**
     * An add summoned by a SHIELD ability died (or could not be spawned). Once all of them are gone the shield drops
     * ({@link #isInvulnerable(long)} turns false at once, {@link BossCommand.ShieldChanged} follows on the next tick).
     * Ignored when no add shield is up.
     */
    public void onAddKilled() {
        if (shield != null && shield.addsRemaining > 0) {
            shield.addsRemaining--;
        }
    }

    /** The boss takes no damage: phase transition or an ability shield. Query it from the damage event. */
    public boolean isInvulnerable(long now) {
        if (dead) {
            return false;
        }
        return now < transitionUntil || (shield != null && shield.holds(now));
    }

    /** Index of the current phase (0 before the fight starts). */
    public int phase() {
        return phaseIndex;
    }

    public BossPhase currentPhase() {
        return definition.phases().get(phaseIndex);
    }

    /** The ability being telegraphed or cast; the boss should stand still while present. */
    public Optional<BossAbility> currentAbility() {
        return active == null ? Optional.empty() : Optional.of(active.ability);
    }

    /** Abilities the boss can pick in the current phase on this difficulty. */
    public List<BossAbility> rotation() {
        return rotations.get(phaseIndex);
    }

    /** Ticks until {@code abilityId} may be picked again from the rotation (0 = ready). */
    public long cooldownRemaining(String abilityId, long now) {
        return Math.max(0L, readyAt.getOrDefault(abilityId, NEVER) - now);
    }

    public boolean hasStarted() {
        return started;
    }

    /** First tick with a target, or {@link Long#MIN_VALUE}/4 before that. */
    public long fightStartTick() {
        return fightStartTick;
    }

    public boolean isEnraged() {
        return enraged;
    }

    public boolean isDead() {
        return dead;
    }

    /** Maximum health for this party and difficulty: set it on the entity when it spawns. */
    public double maxHealth() {
        return maxHealth;
    }

    /** Damage of the vanilla melee hit right now (difficulty, enrage and buff applied). */
    public double meleeDamage(long now) {
        return definition.damage() * damageFactor(now);
    }

    public double armor() {
        return definition.armor();
    }

    /** Multiplier for the entity's base movement speed in the current phase. */
    public double movementSpeedMultiplier() {
        return currentPhase().movementSpeedMultiplier();
    }

    public BossDefinition definition() {
        return definition;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public int partySize() {
        return partySize;
    }

    // ------------------------------------------------------------------ state holders

    /** Where and how an ability will act, fixed when its telegraph starts. */
    private record Plan(List<BossShape> shapes, List<Point> positions, UUID target, Point destination,
                        List<Double> yaws) {
        Plan {
            shapes = List.copyOf(shapes);
            positions = List.copyOf(positions);
            yaws = List.copyOf(yaws);
        }

        static Plan of(BossShape shape) {
            return new Plan(List.of(shape), List.of(), null, null, List.of());
        }

        static Plan markers(List<Point> positions) {
            List<BossShape> shapes = new ArrayList<>();
            for (Point position : positions) {
                shapes.add(new BossShape.Circle(position, BossRules.MARKER_RADIUS));
            }
            return new Plan(shapes, positions, null, null, List.of());
        }
    }

    private static final class Active {
        final BossAbility ability;
        final Point origin;
        final long executeTick;
        final long endTick;
        final Plan plan;
        boolean executed;

        Active(BossAbility ability, Point origin, long executeTick, long endTick, Plan plan) {
            this.ability = ability;
            this.origin = origin;
            this.executeTick = executeTick;
            this.endTick = endTick;
            this.plan = plan;
        }
    }

    private static final class ShieldState {
        final long until;
        final int addCount;
        int addsRemaining;

        ShieldState(long until, int addCount) {
            this.until = until;
            this.addCount = addCount;
            this.addsRemaining = addCount;
        }

        boolean holds(long now) {
            return now < until && (addCount == 0 || addsRemaining > 0);
        }
    }
}
