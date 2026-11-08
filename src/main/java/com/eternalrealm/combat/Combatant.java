package com.eternalrealm.combat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Action state machine of one fighter (docs/COMBAT_SYSTEM.md, section 4).
 *
 * <p>The same class runs on the server (authoritative: hit frames become damage) and on the owning client (prediction:
 * only timings, stamina and cooldowns are used). Time is an abstract tick counter supplied by the caller: the server
 * tick on the server, a client tick counter on the client. Every call that takes {@code now} first brings the state up
 * to {@code now}; time must never go backwards. Not thread-safe: use from one thread (server or client thread).</p>
 *
 * <p>Events (action starts, charge starts and due hit frames) are collected in order and returned by
 * {@link #tick(long)}, which the owner calls exactly once per tick.</p>
 */
public final class Combatant {
    public enum Phase {
        IDLE,
        ACTION,
        CHARGING,
        STAGGERED
    }

    /** Skill slots: 0 = the weapon's skill, 1..4 = job skills (4 = the ultimate). */
    public static final int SKILL_SLOTS = 5;
    public static final int WEAPON_SKILL_SLOT = 0;

    /** "Long ago": far from the counter range, yet safe to subtract from. */
    private static final long NEVER = Long.MIN_VALUE / 4;
    /** Upper bound of actions started within one {@link #advance} (each start is at least one tick long). */
    private static final int MAX_CHAINED_STARTS = 4;

    private CombatStats stats;
    private WeaponProfile weapon;

    private Phase phase = Phase.IDLE;
    private Active active;
    private Charge charge;
    private Buffered buffered;

    private int nextComboStep;
    private long comboExpiresAt = NEVER;

    private double stamina;
    private long lastSpendTick = NEVER;
    private final long[] skillReadyAt = filled(NEVER);
    private final int[] skillCooldownTotal = new int[SKILL_SLOTS];
    private final SkillBinding[] jobSkills = new SkillBinding[SKILL_SLOTS];
    private long invulnerableUntil = NEVER;
    private long staggerUntil = NEVER;
    private long riposteUntil = NEVER;
    private boolean guarding;
    private long guardStartTick;
    private long lastGuardStartTick = NEVER;
    private boolean parryArmed;

    private final List<CombatEvent> outbox = new ArrayList<>();

    public Combatant(CombatStats stats) {
        this.stats = Objects.requireNonNull(stats, "stats");
        this.stamina = stats.maxStamina();
    }

    // ------------------------------------------------------------------ requests

    /**
     * Handles one intent.
     *
     * @param currentWeapon      the weapon in the main hand right now, or {@code null}
     * @param mobilityRestricted the fighter cannot dodge (riding, gliding)
     * @param usingOtherItem     the fighter is using another item (shield, food, bow...): no actions until it stops,
     *                           so nothing can be combined with e.g. a vanilla shield block
     */
    public RequestOutcome submit(CombatRequest request, long now, WeaponProfile currentWeapon,
                                 boolean mobilityRestricted, boolean usingOtherItem) {
        Objects.requireNonNull(request, "request");
        updateWeapon(currentWeapon);
        advance(now);
        if (request == CombatRequest.HEAVY_RELEASE) {
            return release(now);
        }
        if (weapon == null) {
            return RequestOutcome.REJECTED_NO_WEAPON;
        }
        if (now < staggerUntil) {
            return RequestOutcome.REJECTED_STAGGERED;
        }
        if (usingOtherItem) {
            return RequestOutcome.REJECTED_BUSY;
        }
        RequestOutcome invalid = validate(request, now, mobilityRestricted);
        if (invalid != null) {
            return invalid;
        }
        switch (phase) {
            case IDLE -> {
                start(request, now);
                return RequestOutcome.ACCEPTED;
            }
            case CHARGING -> {
                if (request != CombatRequest.DODGE) {
                    return RequestOutcome.REJECTED_BUSY;
                }
                charge = null;
                phase = Phase.IDLE;
                start(request, now);
                return RequestOutcome.ACCEPTED;
            }
            case ACTION -> {
                if (canInterrupt(request, now)) {
                    start(request, now);
                    return RequestOutcome.ACCEPTED;
                }
                if (buffered != null) {
                    return RequestOutcome.REJECTED_BUSY;
                }
                buffered = new Buffered(request);
                return RequestOutcome.BUFFERED;
            }
            default -> throw new IllegalStateException("unexpected phase " + phase);
        }
    }

    private RequestOutcome validate(CombatRequest request, long now, boolean mobilityRestricted) {
        return switch (request) {
            case LIGHT -> guarding ? RequestOutcome.REJECTED_GUARDING : null;
            case HEAVY_START -> {
                if (guarding) {
                    yield RequestOutcome.REJECTED_GUARDING;
                }
                yield stamina < weapon.heavy().strike().staminaCost() ? RequestOutcome.REJECTED_STAMINA : null;
            }
            case DODGE -> {
                if (mobilityRestricted) {
                    yield RequestOutcome.REJECTED_NOT_ALLOWED;
                }
                yield stamina < dodgeCost() ? RequestOutcome.REJECTED_STAMINA : null;
            }
            case SKILL, JOB_SKILL_1, JOB_SKILL_2, JOB_SKILL_3, JOB_SKILL_4 -> validateSkill(request.skillSlot(), now);
            case HEAVY_RELEASE -> null;
        };
    }

    private RequestOutcome validateSkill(int slot, long now) {
        if (guarding) {
            return RequestOutcome.REJECTED_GUARDING;
        }
        ActionDefinition action;
        if (slot == WEAPON_SKILL_SLOT) {
            action = weapon.skill();
        } else {
            SkillBinding binding = jobSkills[slot];
            if (binding == null) {
                return RequestOutcome.REJECTED_NOT_LEARNED;
            }
            if (!binding.allows(weapon)) {
                return RequestOutcome.REJECTED_WRONG_WEAPON;
            }
            action = binding.action();
        }
        if (now < skillReadyAt[slot]) {
            return RequestOutcome.REJECTED_COOLDOWN;
        }
        return stamina < action.staminaCost() ? RequestOutcome.REJECTED_STAMINA : null;
    }

    private double dodgeCost() {
        return CombatRules.DODGE.staminaCost() * stats.dodgeCostFactor();
    }

    /** Whether {@code request} may cut the current action short right now instead of being buffered. */
    private boolean canInterrupt(CombatRequest request, long now) {
        return canInterrupt(active, request, now);
    }

    private static boolean canInterrupt(Active current, CombatRequest request, long now) {
        if (now >= current.cancelTick) {
            return true;
        }
        if (!current.allFramesFired()) {
            return false;
        }
        // Dodges and skills may cancel the recovery after the last hit, but not chain into themselves.
        if (request == CombatRequest.DODGE) {
            return current.action.kind() != ActionKind.DODGE;
        }
        return request.isSkill() && current.action.kind() != ActionKind.SKILL;
    }

    private void start(CombatRequest request, long now) {
        switch (request) {
            case LIGHT -> startLight(now);
            case HEAVY_START -> startCharge(now);
            case DODGE -> {
                guarding = false;
                begin(CombatRules.DODGE, -1, now, 1.0, -1, 0);
            }
            case SKILL -> begin(weapon.skill(), -1, now, 1.0, WEAPON_SKILL_SLOT, weapon.skill().cooldownTicks());
            case JOB_SKILL_1, JOB_SKILL_2, JOB_SKILL_3, JOB_SKILL_4 -> {
                SkillBinding binding = jobSkills[request.skillSlot()];
                begin(binding.action(), -1, now, binding.damageScale(), request.skillSlot(), binding.cooldownTicks());
            }
            case HEAVY_RELEASE -> throw new IllegalArgumentException("release is not an action");
        }
    }

    private void startLight(long now) {
        List<ActionDefinition> combo = weapon.combo();
        int step = now <= comboExpiresAt ? nextComboStep : 0;
        if (step >= combo.size()) {
            step = 0;
        }
        begin(combo.get(step), step, now, 1.0, -1, 0);
        nextComboStep = step + 1 < combo.size() ? step + 1 : 0;
        comboExpiresAt = nextComboStep == 0 ? NEVER : active.endTick + weapon.comboResetTicks();
    }

    private void startCharge(long now) {
        boolean derived = (phase == Phase.ACTION && active.action.kind() == ActionKind.LIGHT)
                || (now <= comboExpiresAt && nextComboStep > 0);
        active = null;
        buffered = null;
        charge = new Charge(weapon, now, derived);
        phase = Phase.CHARGING;
        nextComboStep = 0;
        comboExpiresAt = NEVER;
        outbox.add(new CombatEvent.ChargeStarted(weapon, derived, now));
    }

    private RequestOutcome release(long now) {
        if (phase == Phase.CHARGING) {
            if (chargedTicks(now) >= charge.weapon.heavy().minChargeTicks()) {
                strike(now);
            } else {
                charge.releaseRequested = true;
            }
            return RequestOutcome.ACCEPTED;
        }
        if (phase == Phase.ACTION && buffered != null && buffered.request == CombatRequest.HEAVY_START) {
            buffered.released = true;
            return RequestOutcome.BUFFERED;
        }
        return RequestOutcome.IGNORED;
    }

    private void strike(long now) {
        Charge current = charge;
        HeavyAttack heavy = current.weapon.heavy();
        long charged = Math.min(chargedTicks(now), heavy.maxChargeTicks());
        double scale = heavy.chargeMultiplier(charged) + (current.derived ? heavy.derivedBonus() : 0.0);
        charge = null;
        phase = Phase.IDLE;
        begin(heavy.strike(), -1, now, scale, -1, 0);
    }

    /**
     * @param skillSlot     slot whose cooldown starts ({@code -1} for non-skills)
     * @param cooldownTicks cooldown started in {@code skillSlot}
     */
    private void begin(ActionDefinition action, int comboStep, long now, double damageScale, int skillSlot,
                       int cooldownTicks) {
        ActionDefinition.Timing timing = action.timing(stats.actionSpeedFactor());
        double scale = damageScale;
        if (action.kind() != ActionKind.DODGE && now < riposteUntil) {
            scale *= CombatRules.RIPOSTE_MULTIPLIER;
            riposteUntil = NEVER;
        }
        long[] frameTicks = new long[timing.frameCount()];
        for (int i = 0; i < frameTicks.length; i++) {
            frameTicks[i] = now + timing.frameOffset(i);
        }
        active = new Active(weapon, action, comboStep, now, now + timing.cancel(), now + timing.duration(),
                frameTicks, scale);
        phase = Phase.ACTION;
        buffered = null;
        charge = null;
        spend(action.kind() == ActionKind.DODGE ? dodgeCost() : action.staminaCost(), now);
        if (skillSlot >= 0 && cooldownTicks > 0) {
            int cooldown = Math.max(1, (int) Math.round(cooldownTicks * stats.cooldownFactor()));
            skillReadyAt[skillSlot] = now + cooldown;
            skillCooldownTotal[skillSlot] = cooldown;
        }
        if (action.invulnerableTicks() > 0) {
            invulnerableUntil = Math.max(invulnerableUntil, now + action.invulnerableTicks());
        }
        if (action.kind() != ActionKind.LIGHT) {
            nextComboStep = 0;
            comboExpiresAt = NEVER;
        }
        outbox.add(new CombatEvent.ActionStarted(weapon, action, comboStep, now));
    }

    // ------------------------------------------------------------------ time

    /** Advances to {@code now}, regenerates stamina and returns the events since the previous call. Once per tick. */
    public List<CombatEvent> tick(long now) {
        advance(now);
        regenerate(now);
        if (outbox.isEmpty()) {
            return List.of();
        }
        List<CombatEvent> events = List.copyOf(outbox);
        outbox.clear();
        return events;
    }

    private void advance(long now) {
        if (phase == Phase.CHARGING) {
            HeavyAttack heavy = charge.weapon.heavy();
            long elapsed = chargedTicks(now);
            if ((charge.releaseRequested && elapsed >= heavy.minChargeTicks()) || elapsed >= heavy.maxChargeTicks()) {
                strike(now);
            }
        }
        for (int i = 0; i < MAX_CHAINED_STARTS && phase == Phase.ACTION; i++) {
            Active current = active;
            while (!current.allFramesFired() && current.frameTicks[current.nextFrame] <= now) {
                int index = current.nextFrame++;
                outbox.add(new CombatEvent.Hit(current.weapon, current.action, index,
                        current.action.frames().get(index), current.damageScale, current.frameTicks[index]));
            }
            // A buffered input starts as soon as it would have been accepted directly: at the cancel point, or for
            // dodges/skills right after the last hit (otherwise pressing a tick earlier would delay e.g. the i-frames).
            if (buffered != null && canInterrupt(current, buffered.request, now)) {
                Buffered next = buffered;
                buffered = null;
                startBuffered(next, now);
                if (active != current && phase == Phase.ACTION) {
                    continue; // emit the new action's frames that are already due
                }
            }
            if (phase == Phase.ACTION && active == current && now >= current.endTick) {
                active = null;
                phase = Phase.IDLE;
            }
            break;
        }
    }

    private void startBuffered(Buffered next, long now) {
        if (weapon == null || now < staggerUntil || validate(next.request, now, false) != null) {
            return; // the situation changed (stamina, cooldown): the buffered input is dropped
        }
        start(next.request, now);
        if (next.request == CombatRequest.HEAVY_START && next.released && charge != null) {
            charge.releaseRequested = true;
        }
    }

    private void regenerate(long now) {
        double max = stats.maxStamina();
        if (stamina >= max || now - lastSpendTick < CombatRules.STAMINA_REGEN_DELAY_TICKS) {
            return;
        }
        double rate = stats.staminaRegenPerTick() * (guarding ? CombatRules.GUARD_REGEN_FACTOR : 1.0);
        stamina = Math.min(max, stamina + rate);
    }

    // ------------------------------------------------------------------ weapon, guard, defense

    /** Switching weapons cancels everything in progress (remaining hits never happen). Identity comparison. */
    public void updateWeapon(WeaponProfile currentWeapon) {
        if (currentWeapon == weapon) {
            return;
        }
        weapon = currentWeapon;
        cancelAll();
    }

    /** Guard can start when idle, or in the recovery of an action (after its last hit / at its cancel point). */
    public boolean canStartGuard(long now, WeaponProfile currentWeapon) {
        updateWeapon(currentWeapon);
        advance(now);
        if (weapon == null || now < staggerUntil) {
            return false;
        }
        if (guarding) {
            return true;
        }
        return switch (phase) {
            case IDLE -> true;
            case CHARGING, STAGGERED -> false;
            case ACTION -> now >= active.cancelTick
                    || (active.allFramesFired() && active.action.kind() != ActionKind.DODGE);
        };
    }

    /**
     * Starts guarding (cancelling an action's recovery). A guard already in progress keeps its start tick. The guard
     * opens a parry window only if the previous guard started {@link CombatRules#PARRY_REARM_TICKS} or more ago.
     */
    public boolean startGuard(long now, WeaponProfile currentWeapon) {
        if (!canStartGuard(now, currentWeapon)) {
            return false;
        }
        if (!guarding) {
            active = null;
            charge = null;
            buffered = null;
            phase = Phase.IDLE;
            guarding = true;
            guardStartTick = now;
            parryArmed = now - lastGuardStartTick >= CombatRules.PARRY_REARM_TICKS;
            lastGuardStartTick = now;
        }
        return true;
    }

    public void stopGuard() {
        guarding = false;
    }

    /**
     * Called first for every incoming attack.
     *
     * @param evadable  the damage comes from an attacker, a projectile or an explosion
     * @param guardable the damage can be guarded and comes from inside the guard arc
     */
    public AvoidOutcome checkAvoid(long now, boolean evadable, boolean guardable) {
        advance(now);
        if (evadable && now < invulnerableUntil) {
            return AvoidOutcome.DODGED;
        }
        if (guarding && parryArmed && guardable && weapon != null
                && now - guardStartTick < weapon.guard().parryWindowTicks()) {
            riposteUntil = now + CombatRules.RIPOSTE_WINDOW_TICKS;
            restoreStamina(CombatRules.PARRY_STAMINA_REFUND);
            return AvoidOutcome.PARRIED;
        }
        return AvoidOutcome.NONE;
    }

    /** Called for damage that was not avoided: blocks part of it while guarding, paid with stamina. */
    public GuardResult applyGuard(long now, boolean guardable, double amount) {
        double incoming = Math.max(0.0, amount);
        if (!guarding || !guardable || weapon == null || incoming == 0.0) {
            return new GuardResult(incoming, GuardResult.Outcome.NONE);
        }
        GuardProfile guard = weapon.guard();
        double blocked = incoming * Math.min(0.95, guard.reduction() + stats.guardBonus());
        double cost = blocked * guard.staminaPerDamage();
        if (cost <= stamina) {
            spend(cost, now);
            return new GuardResult(incoming - blocked, GuardResult.Outcome.GUARDED);
        }
        double covered = stamina / guard.staminaPerDamage();
        spend(stamina, now);
        stagger(now, CombatRules.GUARD_BREAK_STAGGER_TICKS);
        return new GuardResult(Math.max(0.0, incoming - covered), GuardResult.Outcome.GUARD_BROKEN);
    }

    /** Interrupts everything (including the guard) and blocks all actions for {@code ticks}. */
    public void stagger(long now, int ticks) {
        cancelAll();
        staggerUntil = Math.max(staggerUntil, now + Math.max(0, ticks));
    }

    /** Cancels the action, charge and buffered input (e.g. on dimension change). The guard is kept. */
    public void cancelAction() {
        active = null;
        charge = null;
        buffered = null;
        phase = Phase.IDLE;
    }

    /** Death / respawn: back to a fresh state with full stamina. The skill cooldown is kept (no reset by dying). */
    public void reset() {
        cancelAll();
        stamina = stats.maxStamina();
        lastSpendTick = NEVER;
        invulnerableUntil = NEVER;
        staggerUntil = NEVER;
        riposteUntil = NEVER;
    }

    private void cancelAll() {
        active = null;
        charge = null;
        buffered = null;
        phase = Phase.IDLE;
        guarding = false;
        nextComboStep = 0;
        comboExpiresAt = NEVER;
    }

    // ------------------------------------------------------------------ stamina & stats

    private void spend(double amount, long now) {
        if (amount <= 0.0) {
            return;
        }
        stamina = Math.max(0.0, stamina - amount);
        lastSpendTick = now;
    }

    private void restoreStamina(double amount) {
        stamina = Math.min(stats.maxStamina(), stamina + amount);
    }

    public void setStats(CombatStats newStats) {
        stats = Objects.requireNonNull(newStats, "stats");
        stamina = Math.min(stamina, stats.maxStamina());
    }

    /** Client only: adopt the server's authoritative stamina. */
    public void setStamina(double value) {
        stamina = Math.max(0.0, Math.min(stats.maxStamina(), value));
    }

    /** Client only: adopt the server's authoritative weapon skill cooldown. */
    public void setSkillCooldown(long now, int remainingTicks, int totalTicks) {
        setSkillCooldown(WEAPON_SKILL_SLOT, now, remainingTicks, totalTicks);
    }

    /** Adopts an authoritative cooldown of one skill slot (client sync, or restoring a remembered cooldown). */
    public void setSkillCooldown(int slot, long now, int remainingTicks, int totalTicks) {
        checkSlot(slot);
        skillReadyAt[slot] = now + Math.max(0, remainingTicks);
        skillCooldownTotal[slot] = Math.max(0, totalTicks);
    }

    /**
     * Places a job skill in slot {@code 1..4}, or empties it ({@code null}). An action of the previous skill that is
     * already running is not interrupted; the slot's cooldown is kept.
     */
    public void setJobSkill(int slot, SkillBinding binding) {
        if (slot < 1 || slot >= SKILL_SLOTS) {
            throw new IllegalArgumentException("job skill slot out of range [1, " + (SKILL_SLOTS - 1) + "]: " + slot);
        }
        jobSkills[slot] = binding;
    }

    /** The job skill in slot {@code 1..4}, or {@code null}. */
    public SkillBinding jobSkill(int slot) {
        checkSlot(slot);
        return jobSkills[slot];
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SKILL_SLOTS) {
            throw new IllegalArgumentException("skill slot out of range [0, " + (SKILL_SLOTS - 1) + "]: " + slot);
        }
    }

    private static long[] filled(long value) {
        long[] array = new long[SKILL_SLOTS];
        Arrays.fill(array, value);
        return array;
    }

    // ------------------------------------------------------------------ prediction rollback

    /** Copy of the complete state (without pending events), so a client can undo a prediction the server rejected. */
    public Snapshot snapshot() {
        return new Snapshot(this);
    }

    /** Restores a {@link #snapshot()}; time-based transitions since then are re-applied on the next call. */
    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        weapon = snapshot.weapon;
        phase = snapshot.phase;
        active = snapshot.active == null ? null : snapshot.active.copy();
        charge = snapshot.charge == null ? null : snapshot.charge.copy();
        buffered = snapshot.buffered == null ? null : snapshot.buffered.copy();
        nextComboStep = snapshot.nextComboStep;
        comboExpiresAt = snapshot.comboExpiresAt;
        stamina = Math.min(snapshot.stamina, stats.maxStamina());
        lastSpendTick = snapshot.lastSpendTick;
        System.arraycopy(snapshot.skillReadyAt, 0, skillReadyAt, 0, SKILL_SLOTS);
        System.arraycopy(snapshot.skillCooldownTotal, 0, skillCooldownTotal, 0, SKILL_SLOTS);
        invulnerableUntil = snapshot.invulnerableUntil;
        staggerUntil = snapshot.staggerUntil;
        riposteUntil = snapshot.riposteUntil;
        guarding = snapshot.guarding;
        guardStartTick = snapshot.guardStartTick;
        lastGuardStartTick = snapshot.lastGuardStartTick;
        parryArmed = snapshot.parryArmed;
    }

    /** Immutable copy of a {@link Combatant}'s state. */
    public static final class Snapshot {
        private final WeaponProfile weapon;
        private final Phase phase;
        private final Active active;
        private final Charge charge;
        private final Buffered buffered;
        private final int nextComboStep;
        private final long comboExpiresAt;
        private final double stamina;
        private final long lastSpendTick;
        private final long[] skillReadyAt;
        private final int[] skillCooldownTotal;
        private final long invulnerableUntil;
        private final long staggerUntil;
        private final long riposteUntil;
        private final boolean guarding;
        private final long guardStartTick;
        private final long lastGuardStartTick;
        private final boolean parryArmed;

        private Snapshot(Combatant source) {
            weapon = source.weapon;
            phase = source.phase;
            active = source.active == null ? null : source.active.copy();
            charge = source.charge == null ? null : source.charge.copy();
            buffered = source.buffered == null ? null : source.buffered.copy();
            nextComboStep = source.nextComboStep;
            comboExpiresAt = source.comboExpiresAt;
            stamina = source.stamina;
            lastSpendTick = source.lastSpendTick;
            skillReadyAt = source.skillReadyAt.clone();
            skillCooldownTotal = source.skillCooldownTotal.clone();
            invulnerableUntil = source.invulnerableUntil;
            staggerUntil = source.staggerUntil;
            riposteUntil = source.riposteUntil;
            guarding = source.guarding;
            guardStartTick = source.guardStartTick;
            lastGuardStartTick = source.lastGuardStartTick;
            parryArmed = source.parryArmed;
        }
    }

    // ------------------------------------------------------------------ queries

    public CombatStats stats() {
        return stats;
    }

    public WeaponProfile weapon() {
        return weapon;
    }

    public Phase phase(long now) {
        return now < staggerUntil ? Phase.STAGGERED : phase;
    }

    public double stamina() {
        return stamina;
    }

    public boolean isGuarding() {
        return guarding;
    }

    public long guardStartTick() {
        return guardStartTick;
    }

    public boolean isInvulnerable(long now) {
        return now < invulnerableUntil;
    }

    /** Remaining cooldown of the weapon skill. */
    public int skillCooldownRemaining(long now) {
        return skillCooldownRemaining(WEAPON_SKILL_SLOT, now);
    }

    public int skillCooldownTotal() {
        return skillCooldownTotal(WEAPON_SKILL_SLOT);
    }

    public int skillCooldownRemaining(int slot, long now) {
        checkSlot(slot);
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, skillReadyAt[slot] - now));
    }

    public int skillCooldownTotal(int slot) {
        checkSlot(slot);
        return skillCooldownTotal[slot];
    }

    /** Effective ticks charged so far (STR charges faster), or 0 when not charging. */
    public long chargeTicks(long now) {
        return phase == Phase.CHARGING ? chargedTicks(now) : 0L;
    }

    private long chargedTicks(long now) {
        return (long) Math.floor(Math.max(0L, now - charge.startTick) / stats.chargeTimeFactor());
    }

    public Optional<ActionDefinition> currentAction() {
        return phase == Phase.ACTION ? Optional.of(active.action) : Optional.empty();
    }

    /** Start tick of the action in progress (for animations), or -1 when idle. */
    public long actionStartTick() {
        return phase == Phase.ACTION ? active.startTick : -1L;
    }

    /** End tick of the action in progress, or -1 when idle. */
    public long actionEndTick() {
        return phase == Phase.ACTION ? active.endTick : -1L;
    }

    /** Start tick of the heavy attack charge in progress, or -1. */
    public long chargeStartTick() {
        return phase == Phase.CHARGING ? charge.startTick : -1L;
    }

    /** Combo step of the light attack in progress, or -1. */
    public int currentComboStep() {
        return phase == Phase.ACTION ? active.comboStep : -1;
    }

    public boolean hasBufferedInput() {
        return buffered != null;
    }

    // ------------------------------------------------------------------ state holders

    private static final class Active {
        final WeaponProfile weapon;
        final ActionDefinition action;
        final int comboStep;
        final long startTick;
        final long cancelTick;
        final long endTick;
        final long[] frameTicks;
        final double damageScale;
        int nextFrame;

        Active(WeaponProfile weapon, ActionDefinition action, int comboStep, long startTick, long cancelTick,
               long endTick, long[] frameTicks, double damageScale) {
            this.weapon = weapon;
            this.action = action;
            this.comboStep = comboStep;
            this.startTick = startTick;
            this.cancelTick = cancelTick;
            this.endTick = endTick;
            this.frameTicks = frameTicks;
            this.damageScale = damageScale;
        }

        boolean allFramesFired() {
            return nextFrame >= frameTicks.length;
        }

        Active copy() {
            Active copy = new Active(weapon, action, comboStep, startTick, cancelTick, endTick, frameTicks, damageScale);
            copy.nextFrame = nextFrame;
            return copy;
        }
    }

    private static final class Charge {
        final WeaponProfile weapon;
        final long startTick;
        final boolean derived;
        boolean releaseRequested;

        Charge(WeaponProfile weapon, long startTick, boolean derived) {
            this.weapon = weapon;
            this.startTick = startTick;
            this.derived = derived;
        }

        Charge copy() {
            Charge copy = new Charge(weapon, startTick, derived);
            copy.releaseRequested = releaseRequested;
            return copy;
        }
    }

    private static final class Buffered {
        final CombatRequest request;
        boolean released;

        Buffered(CombatRequest request) {
            this.request = request;
        }

        Buffered copy() {
            Buffered copy = new Buffered(request);
            copy.released = released;
            return copy;
        }
    }
}
