package com.eternalrealm.client;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.CombatRules;
import com.eternalrealm.combat.SkillBinding;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.SkillTree;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.DefenseEvent;
import com.eternalrealm.combat.RequestOutcome;
import com.eternalrealm.network.forge.CombatStatusPacket;
import com.eternalrealm.network.forge.DamageNumberPacket;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The client's view of its own combat state: the predicted {@link Combatant} (same rules as the server), the values
 * received from the server, HUD popups and floating damage numbers. Client thread only.
 */
public final class ClientCombatState {
    /** Ticks a damage number stays visible. */
    public static final int DAMAGE_NUMBER_TICKS = 30;
    private static final int MAX_DAMAGE_NUMBERS = 64;
    private static final int POPUP_TICKS = 24;

    private static CombatStats stats;
    private static Combatant combatant;
    private static long tick;
    private static int lastSentSequence;
    /** State saved just before the latest sent request, to undo it if the server rejects it. */
    private static Combatant.Snapshot latestPrediction;
    private static int latestPredictionSequence;
    private static DefenseEvent popup = DefenseEvent.NONE;
    private static long popupStart;
    private static final Deque<DamageNumber> damageNumbers = new ArrayDeque<>();

    private ClientCombatState() {
    }

    /** A floating number; position already includes a little random spread so stacked hits stay readable. */
    public record DamageNumber(double x, double y, double z, float amount, int flags, long spawnTick) {
        public boolean critical() {
            return (flags & DamageNumberPacket.CRITICAL) != 0;
        }
    }

    public static CombatStats stats() {
        return stats;
    }

    /** The predicted fighter, or {@code null} until the server sent the combat stats. */
    public static Combatant combatant() {
        return combatant;
    }

    /** The client combat clock (client ticks). */
    public static long tick() {
        return tick;
    }

    static long advanceTick() {
        tick++;
        while (!damageNumbers.isEmpty() && tick - damageNumbers.peekFirst().spawnTick() > DAMAGE_NUMBER_TICKS) {
            damageNumbers.pollFirst();
        }
        return tick;
    }

    static int nextSequence() {
        return ++lastSentSequence;
    }

    static void rememberPrediction(int sequence, Combatant.Snapshot before) {
        latestPrediction = before;
        latestPredictionSequence = sequence;
    }

    // ------------------------------------------------------------------ packets (client thread)

    public static void onStats(CombatStats newStats) {
        stats = newStats;
        if (combatant == null) {
            combatant = new Combatant(newStats);
            ClientCharacterState.snapshot().ifPresent(ClientCombatState::onCharacter);
        } else {
            combatant.setStats(newStats);
        }
    }

    /**
     * Job skill slots and the usable weapons follow the character's job, level, invested points and loadout (same
     * rules as the server).
     */
    static void onCharacter(CharacterSnapshot character) {
        if (combatant == null) {
            return;
        }
        JobType job = JobType.fromStored(character.jobId());
        SkillBinding[] bindings = SkillTree.bindings(job, character.level(), character.skillInvestments(),
                character.skillLoadout());
        for (int slot = 1; slot < Combatant.SKILL_SLOTS; slot++) {
            combatant.setJobSkill(slot, bindings[slot]);
        }
        combatant.setAllowedWeapons(com.eternalrealm.job.Jobs.get(job).weapons());
    }

    public static void onStatus(CombatStatusPacket status) {
        if (combatant == null) {
            return;
        }
        boolean upToDate = status.ackSequence() == lastSentSequence;
        if (status.answersRequest() && upToDate && status.outcome().isRejected()
                && latestPrediction != null && latestPredictionSequence == status.ackSequence()) {
            // The server did not do what we predicted for our latest request: undo exactly that prediction.
            combatant.restore(latestPrediction);
            latestPrediction = null;
        }
        // A buffered request pays its stamina/cooldown only when it starts, so while one is pending on either side
        // the server's numbers would refund it; keep the prediction until both sides have started it.
        boolean costPending = (status.answersRequest() && status.outcome() == RequestOutcome.BUFFERED)
                || combatant.hasBufferedInput();
        if (upToDate && !costPending) {
            // No request of ours is still unanswered, so the server's numbers already include all our actions.
            combatant.setStamina(status.stamina());
            int[] cooldowns = status.cooldowns();
            int[] totals = status.cooldownTotals();
            for (int slot = 0; slot < Combatant.SKILL_SLOTS; slot++) {
                combatant.setSkillCooldown(slot, tick, cooldowns[slot], totals[slot]);
            }
        }
        if (status.event() == DefenseEvent.GUARD_BROKEN) {
            combatant.stagger(tick, CombatRules.GUARD_BREAK_STAGGER_TICKS);
        }
        if (status.event() != DefenseEvent.NONE) {
            popup = status.event();
            popupStart = tick;
        }
    }

    public static void onDamageNumber(DamageNumberPacket packet) {
        if ((packet.flags() & DamageNumberPacket.SKILL) != 0) {
            // a skill landing: shake, a kick of the view and a moment of hit-stop on the swing
            CameraShake.add(0.7F, 7);
            CameraShake.kick((packet.flags() & DamageNumberPacket.CRITICAL) != 0 ? 1.6F : 1.1F, 5);
            HitStop.trigger(2);
        } else if ((packet.flags() & DamageNumberPacket.HEAVY) != 0) {
            CameraShake.add(0.5F, 6);
        } else if ((packet.flags() & DamageNumberPacket.CRITICAL) != 0) {
            CameraShake.add(0.25F, 4);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        damageNumbers.addLast(new DamageNumber(packet.x() + random.nextDouble(-0.35, 0.35), packet.y(),
                packet.z() + random.nextDouble(-0.35, 0.35), packet.amount(), packet.flags(), tick));
        while (damageNumbers.size() > MAX_DAMAGE_NUMBERS) {
            damageNumbers.pollFirst();
        }
    }

    // ------------------------------------------------------------------ HUD

    public static Collection<DamageNumber> damageNumbers() {
        return Collections.unmodifiableCollection(damageNumbers);
    }

    /** The current popup, or NONE when it has faded. */
    public static DefenseEvent popup() {
        return tick - popupStart <= POPUP_TICKS ? popup : DefenseEvent.NONE;
    }

    /** Ticks since the popup appeared. */
    public static long popupAge() {
        return tick - popupStart;
    }

    public static int popupTicks() {
        return POPUP_TICKS;
    }

    /** Leaving a world/server: forget everything (the next server sends fresh values). */
    public static void clear() {
        stats = null;
        combatant = null;
        lastSentSequence = 0;
        latestPrediction = null;
        popup = DefenseEvent.NONE;
        damageNumbers.clear();
    }
}
