package com.eternalrealm.boss;

import com.eternalrealm.combat.HitGeometry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 *     <li>One ability at a time. An ability is a list of waves: each wave places its shapes (telegraphs) at its place
 *         tick and hits exactly those shapes at its fire tick, so players who saw the warning can move out. Single-hit
 *         kinds have one wave placed at the start that hits {@code telegraphTicks} later; patterns
 *         ({@link AbilityKind#extraTicks()}) add later waves, each with its own warning of at least the minimum.</li>
 *     <li>After an ability the phase's global cooldown passes before the next one; each ability also has its own
 *         cooldown. Choice: the phase's openers first, then a weighted random pick among the ready abilities.</li>
 *     <li>Crossing a phase threshold cancels the current ability and starts an invulnerable transition of
 *         {@link BossDefinition#transitionTicks(int)} ticks during which the boss uses the phase's transition ability
 *         (if any); a phase may enrage the boss.</li>
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
    private Point arenaCenter;
    private double arenaHalfSize;
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

    /**
     * Tells the brain where the arena is: a square floor of {@code 2 × halfSize} centred on {@code center} (the floor
     * height). Optional; with it checkerboards line up with the floor and safe zones and scattered hits stay inside.
     */
    public void setArena(Point center, double halfSize) {
        this.arenaCenter = Objects.requireNonNull(center, "center");
        this.arenaHalfSize = Check.range("halfSize", halfSize, 2.0, 256.0);
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
        updatePhase(now, hpFraction, self, sorted);
        updateEnrage(now);
        expire(now);
        advanceActive(now, self, sorted);
        syncShield(now); // a shield that ended is reported before the next ability starts
        if (active == null && !sorted.isEmpty() && now >= nextAbilityTick && now >= transitionUntil) {
            BossAbility next = choose(now, hpFraction, sorted.size());
            if (next != null) {
                start(next, now, self, sorted, false);
                advanceActive(now, self, sorted); // places the first wave; a wave without telegraph acts at once
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
        enterPhase(definition.phaseIndexFor(hpFraction), false);
        nextAbilityTick = now + scaled(currentPhase().globalCooldownTicks());
    }

    private void enterPhase(int index, boolean passedEnragingPhase) {
        phaseIndex = index;
        openers.clear();
        openers.addAll(currentPhase().openerIds());
        outbox.add(new BossCommand.PhaseChanged(index, currentPhase()));
        if ((passedEnragingPhase || currentPhase().enrages()) && !enraged) {
            enraged = true;
            outbox.add(new BossCommand.Enraged());
        }
    }

    private void updatePhase(long now, double hpFraction, BossPosition self, List<TargetSnapshot> targets) {
        int target = definition.phaseIndexFor(hpFraction);
        if (target <= phaseIndex) {
            return; // phases never go back, even if the boss heals above a threshold
        }
        boolean skippedEnrage = false;
        for (int i = phaseIndex + 1; i < target; i++) {
            skippedEnrage |= definition.phases().get(i).enrages();
        }
        cancelActive();
        shield = null;
        enterPhase(target, skippedEnrage);
        transitionUntil = now + definition.transitionTicks(target);
        nextAbilityTick = Math.max(nextAbilityTick, transitionUntil);
        Optional<BossAbility> transition = definition.transitionAbility(target);
        if (transition.isPresent()) {
            start(transition.get(), now, self, targets, true);
        }
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

    /**
     * Stops the current ability; reports it if a placed wave had not hit yet (its markers must go). Between two waves of
     * a pattern nothing is on the ground, so nothing is reported.
     */
    private void cancelActive() {
        if (active != null && active.markersDown()) {
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

    private void advanceActive(long now, BossPosition self, List<TargetSnapshot> targets) {
        if (active == null) {
            return;
        }
        Active current = active;
        boolean progress = true;
        while (progress && active == current) {
            progress = false;
            for (Wave wave : current.waves) {
                if (wave.placed == null && now >= wave.placeTick) {
                    place(current, wave, now, self, targets);
                    progress = true;
                }
                if (wave.placed != null && !wave.fired && now >= wave.fireTick) {
                    fire(current, wave, now);
                    progress = true;
                }
            }
        }
        if (active == current && current.allFired() && now >= current.endTick) {
            if (current.transition) {
                nextAbilityTick = Math.max(nextAbilityTick, transitionUntil);
            } else {
                nextAbilityTick = Math.max(nextAbilityTick,
                        current.endTick + scaled(currentPhase().globalCooldownTicks()));
            }
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
        if (kind instanceof AbilityKind.Sequence sequence) {
            for (AbilityKind.Part part : sequence.parts()) {
                if (part.kind() instanceof AbilityKind.Soak soak && targetCount < soak.minPlayers()) {
                    return false;
                }
            }
            return true;
        }
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

    private void start(BossAbility ability, long now, BossPosition self, List<TargetSnapshot> targets,
                       boolean transition) {
        List<Wave> waves = schedule(ability, now, self, targets);
        long lastFire = now;
        for (Wave wave : waves) {
            lastFire = Math.max(lastFire, wave.fireTick);
        }
        active = new Active(ability, waves, now, lastFire + ability.castTicks(), transition);
        if (!transition) {
            readyAt.put(ability.id(), now + scaled(ability.cooldownTicks()));
        }
        outbox.add(new BossCommand.AbilityStarted(ability, ability.telegraphTicks()));
    }

    /** Shows a wave: its shapes are fixed now and telegraphed until its fire tick. */
    private void place(Active current, Wave wave, long now, BossPosition self, List<TargetSnapshot> targets) {
        wave.placed = wave.placer.place(self, targets);
        int ticks = (int) (wave.fireTick - now);
        for (BossShape shape : wave.placed.shapes()) {
            outbox.add(new BossCommand.Telegraph(current.ability, shape, ticks, wave.kind));
        }
        for (BossShape safe : wave.placed.safe()) {
            outbox.add(new BossCommand.SafeSpot(current.ability, safe, ticks));
        }
    }

    private void fire(Active current, Wave wave, long now) {
        wave.fired = true;
        BossAbility ability = current.ability;
        AbilityKind kind = wave.kind;
        Placed placed = wave.placed;
        double multiplier = ability.damageMultiplier() * wave.damageScale * damageFactor(now);
        double damage = definition.damage() * multiplier;
        if (kind instanceof AbilityKind.ProjectileVolley volley) {
            outbox.add(new BossCommand.Volley(ability, placed.origin(), placed.yaws(), volley.speed(), multiplier,
                    damage));
        } else if (kind.damaging()) {
            for (BossShape shape : placed.shapes()) {
                outbox.add(new BossCommand.Execute(ability, shape, multiplier, damage, kind));
            }
            if (kind instanceof AbilityKind.Knockback knockback) {
                outbox.add(new BossCommand.Displace(ability, placed.origin(), knockback.radius(),
                        knockback.strength()));
            }
            if (kind instanceof AbilityKind.Charge && placed.dashTo() != null) {
                outbox.add(new BossCommand.Dash(ability, placed.origin(), placed.dashTo()));
            }
            int linger = lingerTicks(kind);
            if (linger > 0 && damage > 0.0) {
                for (BossShape shape : placed.shapes()) {
                    outbox.add(new BossCommand.Hazard(ability, shape, linger, BossRules.HAZARD_PULSE_TICKS,
                            damage * BossRules.HAZARD_DAMAGE_FRACTION));
                }
            }
        } else if (kind instanceof AbilityKind.Summon summon) {
            outbox.add(new BossCommand.Summon(summon.templateId(), summon.count(), placed.positions(), false));
        } else if (kind instanceof AbilityKind.TeleportToTarget) {
            if (placed.target() != null) {
                outbox.add(new BossCommand.Teleport(placed.target(), placed.destination()));
            }
        } else if (kind instanceof AbilityKind.Shield shieldKind) {
            shield = new ShieldState(now + shieldKind.timeoutTicks(), shieldKind.addCount());
            if (shieldKind.addCount() > 0) {
                outbox.add(new BossCommand.Summon(shieldKind.addTemplateId(), shieldKind.addCount(),
                        placed.positions(), true));
            }
        } else if (kind instanceof AbilityKind.Heal heal) {
            outbox.add(new BossCommand.Heal(heal.fraction()));
        } else if (kind instanceof AbilityKind.Buff buff) {
            buffMultiplier = buff.damageMultiplier();
            buffUntil = now + buff.durationTicks();
            outbox.add(new BossCommand.Buffed(buff.damageMultiplier(), buff.durationTicks()));
        }
    }

    private static int lingerTicks(AbilityKind kind) {
        if (kind instanceof AbilityKind.Trail trail) {
            return trail.lingerTicks();
        }
        if (kind instanceof AbilityKind.Scatter scatter) {
            return scatter.lingerTicks();
        }
        return 0;
    }

    // ------------------------------------------------------------------ planning

    /** The waves of one use of {@code ability} that starts now; later waves are placed when their time comes. */
    private List<Wave> schedule(BossAbility ability, long now, BossPosition self, List<TargetSnapshot> targets) {
        AbilityKind kind = ability.kind();
        TargetRule rule = ability.targetRule();
        long first = now + ability.telegraphTicks();
        Point origin = Point.of(self);
        List<Wave> waves = new ArrayList<>();
        if (kind instanceof AbilityKind.Checkerboard board) {
            Point center = floorCenter(origin);
            for (int k = 0; k < board.waves(); k++) {
                int parity = k % 2;
                long fire = first + (long) k * board.intervalTicks();
                waves.add(new Wave(k == 0 ? now : fire - board.intervalTicks(), fire, kind, 1.0,
                        (s, t) -> Placed.of(origin, tiles(center, board, parity))));
            }
        } else if (kind instanceof AbilityKind.Crosses crosses) {
            for (int k = 0; k < crosses.waves(); k++) {
                double yaw = 45.0 * k;
                long fire = first + (long) k * crosses.intervalTicks();
                waves.add(new Wave(k == 0 ? now : fire - crosses.intervalTicks(), fire, kind, 1.0,
                        (s, t) -> Placed.of(origin, List.of(new BossShape.Cross(origin, yaw, crosses.armLength(),
                                crosses.armWidth())))));
            }
        } else if (kind instanceof AbilityKind.Trail trail) {
            List<UUID> chased = new ArrayList<>();
            for (TargetSnapshot target : pick(rule, trail.targets(), origin, targets)) {
                chased.add(target.id());
            }
            for (int k = 0; k < trail.drops(); k++) {
                long place = now + (long) k * trail.intervalTicks();
                waves.add(new Wave(place, place + ability.telegraphTicks(), kind, 1.0,
                        (s, t) -> Placed.of(Point.of(s), drops(chased, t, trail.radius()))));
            }
        } else if (kind instanceof AbilityKind.Exaflare exaflare) {
            double baseYaw = yawTowards(origin, pickOne(rule, origin, targets), self.yawDegrees());
            for (int k = 0; k < exaflare.rows(); k++) {
                List<BossShape> row = row(origin, baseYaw, exaflare, k, bodyRadius());
                waves.add(new Wave(now, first + (long) k * exaflare.intervalTicks(), kind, 1.0,
                        (s, t) -> Placed.of(origin, row)));
            }
        } else if (kind instanceof AbilityKind.Scatter scatter) {
            List<Point> spots = scatter(origin, scatter.count(), scatter.spread(), scatter.radius());
            for (int i = 0; i < spots.size(); i++) {
                BossShape circle = new BossShape.Circle(spots.get(i), scatter.radius());
                waves.add(new Wave(now, first + (long) i * scatter.intervalTicks(), kind, 1.0,
                        (s, t) -> Placed.of(origin, List.of(circle))));
            }
        } else if (kind instanceof AbilityKind.Charge charge) {
            long fire = first;
            for (int k = 0; k < charge.charges(); k++) {
                long place = k == 0 ? now : fire - charge.intervalTicks() + BossRules.DASH_TICKS + 1;
                waves.add(new Wave(place, fire, kind, 1.0, (s, t) -> placeSingle(kind, rule, s, t)));
                fire += charge.intervalTicks();
            }
        } else if (kind instanceof AbilityKind.RotatingBeam beam) {
            double startYaw = yawTowards(origin, pickOne(rule, origin, targets), self.yawDegrees());
            double direction = random.nextBoolean() ? 1.0 : -1.0;
            for (int k = 0; k < beam.steps(); k++) {
                double yaw = startYaw + direction * beam.stepDegrees() * k;
                long fire = first + (long) k * beam.intervalTicks();
                waves.add(new Wave(k == 0 ? now : fire - beam.intervalTicks(), fire, kind, 1.0,
                        (s, t) -> Placed.of(origin, beams(origin, yaw, beam))));
            }
        } else if (kind instanceof AbilityKind.Sequence sequence) {
            long fire = first;
            long place = now;
            for (int i = 0; i < sequence.parts().size(); i++) {
                AbilityKind.Part part = sequence.parts().get(i);
                if (i > 0) {
                    place = fire;
                    fire += part.telegraphTicks();
                }
                waves.add(new Wave(place, fire, part.kind(), part.damageScale(),
                        (s, t) -> placeSingle(part.kind(), rule, s, t)));
            }
        } else {
            waves.add(new Wave(now, first, kind, 1.0, (s, t) -> placeSingle(kind, rule, s, t)));
        }
        return waves;
    }

    /** Places a single-hit kind (or one wave of a charge) from where the boss stands now. */
    private Placed placeSingle(AbilityKind kind, TargetRule rule, BossPosition self, List<TargetSnapshot> targets) {
        Point origin = Point.of(self);
        if (kind instanceof AbilityKind.CircleAroundBoss circle) {
            return Placed.of(origin, List.of(new BossShape.Circle(origin, circle.radius())));
        }
        if (kind instanceof AbilityKind.Knockback knockback) {
            return Placed.of(origin, List.of(new BossShape.Circle(origin, knockback.radius())));
        }
        if (kind instanceof AbilityKind.CircleAtTargets circles) {
            List<BossShape> shapes = new ArrayList<>();
            for (TargetSnapshot target : pick(rule, circles.maxTargets(), origin, targets)) {
                shapes.add(new BossShape.Circle(Point.of(target), circles.radius()));
            }
            return Placed.of(origin, shapes);
        }
        if (kind instanceof AbilityKind.Cone cone) {
            double yaw = yawTowards(origin, pickOne(rule, origin, targets), self.yawDegrees());
            return Placed.of(origin, List.of(new BossShape.Cone(origin, yaw, cone.range(), cone.halfAngleDegrees())));
        }
        if (kind instanceof AbilityKind.Line line) {
            double yaw = yawTowards(origin, pickOne(rule, origin, targets), self.yawDegrees());
            return Placed.of(origin, List.of(new BossShape.Line(origin, ahead(origin, yaw, line.length()),
                    line.width())));
        }
        if (kind instanceof AbilityKind.Charge charge) {
            double yaw = yawTowards(origin, pickOne(rule, origin, targets), self.yawDegrees());
            Point end = ahead(origin, yaw, charge.length());
            return new Placed(List.of(new BossShape.Line(origin, end, charge.width())), List.of(), List.of(), null,
                    null, List.of(), origin, end);
        }
        if (kind instanceof AbilityKind.Ring ring) {
            return Placed.of(origin, List.of(new BossShape.Ring(origin, ring.inner(), ring.outer())));
        }
        if (kind instanceof AbilityKind.ArenaWide) {
            return Placed.of(origin, List.of(new BossShape.Arena(origin)));
        }
        if (kind instanceof AbilityKind.Soak soak) {
            TargetSnapshot target = pickOne(rule, origin, targets);
            Point center = target == null ? origin : Point.of(target);
            return Placed.of(origin, List.of(new BossShape.Circle(center, soak.radius())));
        }
        if (kind instanceof AbilityKind.SafeZones zones) {
            List<BossShape> safe = safeZones(origin, zones);
            return new Placed(List.of(new BossShape.Outside(floorCenter(origin), safe)), safe, List.of(), null, null,
                    List.of(), origin, null);
        }
        if (kind instanceof AbilityKind.ProjectileVolley volley) {
            double yaw = yawTowards(origin, pickOne(rule, origin, targets), self.yawDegrees());
            double halfAngle = Math.max(volley.spreadDegrees() / 2.0, BossRules.VOLLEY_MIN_HALF_ANGLE);
            return new Placed(List.of(new BossShape.Cone(origin, yaw, BossRules.VOLLEY_RANGE, halfAngle)), List.of(),
                    List.of(), null, null, fan(yaw, volley.count(), volley.spreadDegrees()), origin, null);
        }
        if (kind instanceof AbilityKind.Summon summon) {
            return Placed.markers(origin, around(origin, self.yawDegrees(), summon.count(), summonRadius()));
        }
        if (kind instanceof AbilityKind.Shield shieldKind) {
            return Placed.markers(origin, shieldKind.addCount() > 0
                    ? around(origin, self.yawDegrees(), shieldKind.addCount(), summonRadius()) : List.of());
        }
        if (kind instanceof AbilityKind.TeleportToTarget) {
            TargetSnapshot target = pickOne(rule, origin, targets);
            if (target == null) {
                return Placed.of(origin, List.of());
            }
            Point destination = Point.of(target);
            return new Placed(List.of(new BossShape.Circle(destination, Math.max(BossRules.MARKER_RADIUS,
                    bodyRadius()))), List.of(),
                    List.of(), target.id(), destination, List.of(), origin, null);
        }
        return Placed.of(origin, List.of()); // heal, buff: a cast bar only
    }

    /** The arena's centre at the boss's floor height, or the boss's own position when the arena is unknown. */
    private Point floorCenter(Point origin) {
        return arenaCenter == null ? origin : new Point(arenaCenter.x(), origin.y(), arenaCenter.z());
    }

    private static List<BossShape> tiles(Point center, AbilityKind.Checkerboard board, int parity) {
        List<BossShape> tiles = new ArrayList<>();
        int n = board.tilesPerSide();
        double size = board.tileSize();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((i + j + parity) % 2 == 0) {
                    tiles.add(new BossShape.Square(new Point(center.x() + (i - (n - 1) / 2.0) * size, center.y(),
                            center.z() + (j - (n - 1) / 2.0) * size), 0.0, size / 2.0));
                }
            }
        }
        return tiles;
    }

    /** Circles under the chased players who are still in the fight. */
    private static List<BossShape> drops(List<UUID> chased, List<TargetSnapshot> targets, double radius) {
        List<BossShape> circles = new ArrayList<>();
        for (UUID id : chased) {
            for (TargetSnapshot target : targets) {
                if (target.id().equals(id)) {
                    circles.add(new BossShape.Circle(Point.of(target), radius));
                }
            }
        }
        return circles;
    }

    /** Row {@code k} of an exaflare in every direction: strips across the direction of travel. */
    private static List<BossShape> row(Point origin, double baseYaw, AbilityKind.Exaflare exaflare, int k,
                                       double body) {
        List<BossShape> segments = new ArrayList<>();
        double distance = Math.max(1.0, body) + exaflare.spacing() * (k + 0.5); // the first row starts at the body
        for (int d = 0; d < exaflare.directions(); d++) {
            double yaw = baseYaw + 360.0 * d / exaflare.directions();
            Point center = ahead(origin, yaw, distance);
            double[] side = HitGeometry.look(yaw + 90.0, 0.0);
            double half = exaflare.rowLength() / 2.0;
            segments.add(new BossShape.Line(new Point(center.x() - side[0] * half, origin.y(),
                    center.z() - side[2] * half), new Point(center.x() + side[0] * half, origin.y(),
                    center.z() + side[2] * half), exaflare.spacing()));
        }
        return segments;
    }

    private static List<BossShape> beams(Point origin, double yaw, AbilityKind.RotatingBeam beam) {
        List<BossShape> lines = new ArrayList<>();
        for (int j = 0; j < beam.beams(); j++) {
            double angle = yaw + 360.0 * j / beam.beams();
            lines.add(new BossShape.Line(origin, ahead(origin, angle, beam.length()), beam.width()));
        }
        return lines;
    }

    /** {@code count} random points within {@code spread} of the arena centre (or the boss), inside the arena. */
    private List<Point> scatter(Point origin, int count, double spread, double radius) {
        Point center = floorCenter(origin);
        double reach = spread;
        if (arenaCenter != null) {
            reach = Math.min(spread, Math.max(0.0, arenaHalfSize - BossRules.ARENA_MARGIN));
        }
        List<Point> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = reach * Math.sqrt(random.nextDouble());
            points.add(clampToArena(new Point(center.x() + Math.cos(angle) * distance, center.y(),
                    center.z() + Math.sin(angle) * distance), radius * 0.5));
        }
        return points;
    }

    /** Safe circles evenly spread around the arena centre (or the boss), each fully inside the arena. */
    private List<BossShape> safeZones(Point origin, AbilityKind.SafeZones zones) {
        Point center = floorCenter(origin);
        double start = random.nextDouble() * 360.0;
        List<BossShape> circles = new ArrayList<>(zones.count());
        for (int i = 0; i < zones.count(); i++) {
            double distance = zones.count() == 1 ? zones.spread() * random.nextDouble() : zones.spread();
            Point spot = zones.spread() == 0.0 ? center
                    : ahead(center, start + 360.0 * i / zones.count(), distance);
            circles.add(new BossShape.Circle(clampToArena(spot, zones.radius()), zones.radius()));
        }
        return circles;
    }

    /** Keeps a circle of {@code radius} around {@code point} inside the arena square (when known). */
    private Point clampToArena(Point point, double radius) {
        if (arenaCenter == null) {
            return point;
        }
        double limit = Math.max(0.0, arenaHalfSize - radius - BossRules.ARENA_MARGIN);
        double x = Math.max(arenaCenter.x() - limit, Math.min(arenaCenter.x() + limit, point.x()));
        double z = Math.max(arenaCenter.z() - limit, Math.min(arenaCenter.z() + limit, point.z()));
        return new Point(x, point.y(), z);
    }

    // ------------------------------------------------------------------ targeting & geometry

    /** The first target by {@code rule}, or null when nobody is there. */
    private TargetSnapshot pickOne(TargetRule rule, Point origin, List<TargetSnapshot> targets) {
        List<TargetSnapshot> picked = pick(rule, 1, origin, targets);
        return picked.isEmpty() ? null : picked.get(0);
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

    /**
     * Minecraft yaw from {@code origin} towards the target; the boss's own yaw if there is no target or it stands on
     * the boss.
     */
    static double yawTowards(Point origin, TargetSnapshot target, double fallbackYaw) {
        if (target == null) {
            return fallbackYaw;
        }
        double dx = target.x() - origin.x();
        double dz = target.z() - origin.z();
        if (dx * dx + dz * dz < 1.0e-6) {
            return fallbackYaw;
        }
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** The point {@code distance} blocks from {@code origin} in the direction {@code yaw} faces (same height). */
    private static Point ahead(Point origin, double yaw, double distance) {
        double[] direction = HitGeometry.look(yaw, 0.0);
        return new Point(origin.x() + direction[0] * distance, origin.y(), origin.z() + direction[2] * distance);
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
    private static List<Point> around(Point center, double startYaw, int count, double radius) {
        List<Point> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(ahead(center, startYaw + 360.0 * i / count, radius));
        }
        return positions;
    }

    /** Half the width of the boss's body in the current form (attacks around it start there). */
    private double bodyRadius() {
        return BossRigData.bodyRadius(definition.id(), phaseIndex);
    }

    /** Adds appear on a circle around the boss, clear of its body. */
    private double summonRadius() {
        return Math.max(BossRules.SUMMON_RADIUS, bodyRadius() + BossRules.SUMMON_CLEARANCE);
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

    /** Whether the boss is in the invulnerable transition into a new phase (its transformation). */
    public boolean isTransforming(long now) {
        return !dead && now < transitionUntil;
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

    /**
     * The ability being prepared (the cast bar): when it started, when its first hit lands and when the boss is free
     * again. Empty between abilities and during form changes.
     */
    public Optional<Cast> cast() {
        if (active == null || active.transition) {
            return Optional.empty();
        }
        long firstHit = Long.MAX_VALUE;
        for (Wave wave : active.waves) {
            firstHit = Math.min(firstHit, wave.fireTick);
        }
        if (firstHit == Long.MAX_VALUE) {
            firstHit = active.startTick + active.ability.telegraphTicks();
        }
        return Optional.of(new Cast(active.ability, active.startTick, firstHit, active.endTick));
    }

    /** An ability in preparation; see {@link #cast()}. */
    public record Cast(BossAbility ability, long startTick, long firstHitTick, long endTick) {
    }

    /** Tick of the soft enrage (or {@link Long#MAX_VALUE} before the fight started). */
    public long enrageTick() {
        return started ? fightStartTick + definition.enrageAfterTicks() : Long.MAX_VALUE;
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

    /** Computes where a wave acts, from the boss's position and the targets at its place tick. */
    @FunctionalInterface
    private interface Placer {
        Placed place(BossPosition self, List<TargetSnapshot> targets);
    }

    /**
     * Where and how a wave acts, fixed when it is placed: damage shapes, safe shapes, summon positions, teleport
     * target, volley yaws, the boss's position ({@code origin}) and the end of a charge.
     */
    private record Placed(List<BossShape> shapes, List<BossShape> safe, List<Point> positions, UUID target,
                          Point destination, List<Double> yaws, Point origin, Point dashTo) {
        Placed {
            shapes = List.copyOf(shapes);
            safe = List.copyOf(safe);
            positions = List.copyOf(positions);
            yaws = List.copyOf(yaws);
        }

        static Placed of(Point origin, List<BossShape> shapes) {
            return new Placed(shapes, List.of(), List.of(), null, null, List.of(), origin, null);
        }

        static Placed markers(Point origin, List<Point> positions) {
            List<BossShape> shapes = new ArrayList<>();
            for (Point position : positions) {
                shapes.add(new BossShape.Circle(position, BossRules.MARKER_RADIUS));
            }
            return new Placed(shapes, List.of(), positions, null, null, List.of(), origin, null);
        }
    }

    /** One hit of an ability: placed (telegraphed) at {@code placeTick}, hits at {@code fireTick}. */
    private static final class Wave {
        final long placeTick;
        final long fireTick;
        final AbilityKind kind;
        final double damageScale;
        final Placer placer;
        Placed placed;
        boolean fired;

        Wave(long placeTick, long fireTick, AbilityKind kind, double damageScale, Placer placer) {
            this.placeTick = placeTick;
            this.fireTick = fireTick;
            this.kind = kind;
            this.damageScale = damageScale;
            this.placer = placer;
        }
    }

    private static final class Active {
        final BossAbility ability;
        final List<Wave> waves;
        final long startTick;
        final long endTick;
        final boolean transition;

        Active(BossAbility ability, List<Wave> waves, long startTick, long endTick, boolean transition) {
            this.ability = ability;
            this.waves = waves;
            this.startTick = startTick;
            this.endTick = endTick;
            this.transition = transition;
        }

        /** Whether a wave has been placed (its marker is on the ground) and has not hit yet. */
        boolean markersDown() {
            for (Wave wave : waves) {
                if (wave.placed != null && !wave.fired) {
                    return true;
                }
            }
            return false;
        }

        boolean allFired() {
            for (Wave wave : waves) {
                if (!wave.fired) {
                    return false;
                }
            }
            return true;
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

    /** Ids of the abilities this brain may ever use (rotations of all phases, transitions); for platform caches. */
    public Set<String> knownAbilityIds() {
        Set<String> ids = new HashSet<>();
        for (List<BossAbility> rotation : rotations) {
            for (BossAbility ability : rotation) {
                ids.add(ability.id());
            }
        }
        for (BossPhase phase : definition.phases()) {
            if (phase.hasTransition()) {
                ids.add(phase.transitionId());
            }
        }
        return ids;
    }
}
