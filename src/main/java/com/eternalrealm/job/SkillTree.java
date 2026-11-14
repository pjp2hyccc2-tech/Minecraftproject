package com.eternalrealm.job;

import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.SkillBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rules of the skill tree and the skill loadout. Pure functions over the character's level, job, invested points and
 * stored loadout, used by the server (validation) and the client (tree screen, predicted skill bindings), so both
 * always compute the same skills in the same slots.
 *
 * <p><b>Loadout.</b> Six job skill slots (keys V, G, B, H, N, J by default): slots 1, 2, 3, 5, 6 take actives, slot
 * {@value #ULTIMATE_SLOT} takes ultimates. A character that never changed the loadout uses the {@link #defaultLoadout
 * default} (the four innate skills in their original slots). Stored entries that are no longer valid (another job,
 * forgotten after a reset) count as empty.</p>
 */
public final class SkillTree {
    /** Job skill slots of the loadout (slot numbers 1..6). */
    public static final int LOADOUT_SLOTS = Combatant.SKILL_SLOTS - 1;
    public static final int ULTIMATE_SLOT = Combatant.ULTIMATE_SLOT;
    /** Empty loadout entry. */
    public static final String EMPTY = "";

    public enum InvestResult {
        OK,
        NO_JOB,
        UNKNOWN_SKILL,
        WRONG_JOB,
        /** The character level is below the node's required level. */
        LOCKED,
        /** A prerequisite skill is not learned to its required level. */
        PREREQUISITE,
        MAX_LEVEL,
        NO_POINTS;

        public String translationKey() {
            return "message.eternalrealm.skill." + name().toLowerCase(Locale.ROOT);
        }
    }

    public enum LoadoutResult {
        OK,
        NO_JOB,
        BAD_SLOT,
        UNKNOWN_SKILL,
        WRONG_JOB,
        PASSIVE,
        NOT_LEARNED,
        /** Only ultimates go into the ultimate slot. */
        NEEDS_ULTIMATE,
        /** Ultimates only go into the ultimate slot. */
        ULTIMATE_ONLY;

        public String translationKey() {
            return "message.eternalrealm.loadout." + name().toLowerCase(Locale.ROOT);
        }
    }

    private SkillTree() {
    }

    // ------------------------------------------------------------------ learning

    /** Level of {@code skill} for the character (0 = not learned). */
    public static int levelOf(SkillDefinition skill, int characterLevel, Map<String, Integer> investments) {
        return skill.level(characterLevel, investments.getOrDefault(skill.id(), 0));
    }

    /** Whether every prerequisite of {@code skill} has its required level. */
    public static boolean prerequisitesMet(SkillDefinition skill, int characterLevel, Map<String, Integer> investments) {
        for (Prerequisite prerequisite : skill.prerequisites()) {
            SkillDefinition required = Jobs.skill(prerequisite.skillId()).orElse(null);
            if (required == null || levelOf(required, characterLevel, investments) < prerequisite.level()) {
                return false;
            }
        }
        return true;
    }

    /** Whether one more point may go into {@code skillId} (a job without its class). */
    public static InvestResult canInvest(JobType job, int characterLevel, Map<String, Integer> investments,
                                         int unspentPoints, String skillId) {
        return canInvest(JobPath.base(job), characterLevel, investments, unspentPoints, skillId);
    }

    /** Whether one more point may go into {@code skillId}: a skill of the job or of its own class. */
    public static InvestResult canInvest(JobPath path, int characterLevel, Map<String, Integer> investments,
                                         int unspentPoints, String skillId) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(investments, "investments");
        if (!path.job().isChosen()) {
            return InvestResult.NO_JOB;
        }
        SkillDefinition skill = Jobs.skill(skillId).orElse(null);
        if (skill == null) {
            return InvestResult.UNKNOWN_SKILL;
        }
        if (!path.owns(skill)) {
            return InvestResult.WRONG_JOB;
        }
        if (characterLevel < skill.requiredLevel()) {
            return InvestResult.LOCKED;
        }
        if (!prerequisitesMet(skill, characterLevel, investments)) {
            return InvestResult.PREREQUISITE;
        }
        if (levelOf(skill, characterLevel, investments) >= skill.maxLevel()) {
            return InvestResult.MAX_LEVEL;
        }
        return unspentPoints < 1 ? InvestResult.NO_POINTS : InvestResult.OK;
    }

    /**
     * Whether stored investments are still valid for the job (after loading or a rule change): every invested skill
     * belongs to the job, has its required level and prerequisites and stays within its maximum level.
     */
    public static boolean isValid(JobType job, int characterLevel, Map<String, Integer> investments) {
        return isValid(JobPath.base(job), characterLevel, investments);
    }

    /** {@link #isValid(JobType, int, Map)} for a job and its class: the class's skills count as its own. */
    public static boolean isValid(JobPath path, int characterLevel, Map<String, Integer> investments) {
        for (Map.Entry<String, Integer> entry : investments.entrySet()) {
            SkillDefinition skill = Jobs.skill(entry.getKey()).orElse(null);
            if (skill == null || !path.owns(skill) || characterLevel < skill.requiredLevel()
                    || entry.getValue() < 1 || skill.pointsFor(skill.maxLevel()) < entry.getValue()
                    || !prerequisitesMet(skill, characterLevel, investments)) {
                return false;
            }
        }
        return true;
    }

    /** Skill points needed to max out every node of {@code job}. */
    public static int pointsToMaster(JobType job) {
        return pointsToMaster(JobPath.base(job));
    }

    /** Skill points needed to max out every node of the job and its class. */
    public static int pointsToMaster(JobPath path) {
        int total = 0;
        for (SkillDefinition skill : path.skills()) {
            total += skill.pointsFor(skill.maxLevel());
        }
        return total;
    }

    /**
     * The investments a character keeps when it grows into (or changes to) {@code path}: those in skills the path
     * owns; the rest (another class's tree) are refunded.
     */
    public static Map<String, Integer> keptInvestments(JobPath path, Map<String, Integer> investments) {
        Map<String, Integer> kept = new java.util.TreeMap<>();
        for (Map.Entry<String, Integer> entry : investments.entrySet()) {
            SkillDefinition skill = Jobs.skill(entry.getKey()).orElse(null);
            if (skill != null && path.owns(skill)) {
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        return kept;
    }

    // ------------------------------------------------------------------ passives

    /** What the character's learned passive skills add up to. */
    public static PassiveTotals passives(JobType job, int characterLevel, Map<String, Integer> investments) {
        return passives(JobPath.base(job), characterLevel, investments);
    }

    /** What the learned passive skills of the job and its class add up to. */
    public static PassiveTotals passives(JobPath path, int characterLevel, Map<String, Integer> investments) {
        if (!path.job().isChosen()) {
            return PassiveTotals.NONE;
        }
        List<SkillDefinition> learned = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        for (SkillDefinition skill : path.skills()) {
            if (skill.isPassive()) {
                int level = levelOf(skill, characterLevel, investments);
                if (level > 0) {
                    learned.add(skill);
                    levels.add(level);
                }
            }
        }
        return learned.isEmpty() ? PassiveTotals.NONE : PassiveTotals.sum(learned, levels);
    }

    // ------------------------------------------------------------------ loadout

    /** The loadout of a character that never changed it: slot {@code k} (1..4) holds the innate skill {@code k}. */
    public static List<String> defaultLoadout(JobType job) {
        List<String> loadout = new ArrayList<>(Collections.nCopies(LOADOUT_SLOTS, EMPTY));
        if (job.isChosen()) {
            for (int slot = 1; slot <= 4; slot++) {
                loadout.set(slot - 1, Jobs.get(job).innate(slot).id());
            }
        }
        return Collections.unmodifiableList(loadout);
    }

    /** Whether {@code skill} may sit in {@code slot} (by kind only). */
    public static boolean fitsSlot(SkillDefinition skill, int slot) {
        if (!skill.isUsable()) {
            return false;
        }
        return (slot == ULTIMATE_SLOT) == (skill.kind() == SkillKind.ULTIMATE);
    }

    /**
     * The loadout in effect: index {@code slot - 1} holds a skill id or {@link #EMPTY}. An empty stored loadout means
     * the default; invalid or duplicated stored entries count as empty. Innate skills may be placed before their level
     * is reached (their slot fills when they unlock); other skills must be learned.
     */
    public static List<String> effectiveLoadout(JobType job, int characterLevel, Map<String, Integer> investments,
                                                List<String> stored) {
        return effectiveLoadout(JobPath.base(job), characterLevel, investments, stored);
    }

    /** {@link #effectiveLoadout(JobType, int, Map, List)} for a job and its class. */
    public static List<String> effectiveLoadout(JobPath path, int characterLevel, Map<String, Integer> investments,
                                                List<String> stored) {
        Objects.requireNonNull(stored, "stored");
        JobType job = path.job();
        if (!job.isChosen()) {
            return Collections.nCopies(LOADOUT_SLOTS, EMPTY);
        }
        if (stored.isEmpty()) {
            return defaultLoadout(job);
        }
        List<String> loadout = new ArrayList<>(Collections.nCopies(LOADOUT_SLOTS, EMPTY));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < Math.min(LOADOUT_SLOTS, stored.size()); i++) {
            String id = stored.get(i);
            if (id == null || id.isEmpty() || !seen.add(id)) {
                continue;
            }
            SkillDefinition skill = Jobs.skill(id).orElse(null);
            if (skill != null && path.owns(skill) && fitsSlot(skill, i + 1)
                    && (skill.isInnate() || levelOf(skill, characterLevel, investments) > 0)) {
                loadout.set(i, id);
            }
        }
        return Collections.unmodifiableList(loadout);
    }

    /**
     * The stored form after removing invalid entries: unchanged if nothing was invalid, otherwise the effective
     * loadout. An empty stored loadout (the default) stays empty.
     */
    public static List<String> sanitize(JobType job, int characterLevel, Map<String, Integer> investments,
                                        List<String> stored) {
        return sanitize(JobPath.base(job), characterLevel, investments, stored);
    }

    /** {@link #sanitize(JobType, int, Map, List)} for a job and its class. */
    public static List<String> sanitize(JobPath path, int characterLevel, Map<String, Integer> investments,
                                        List<String> stored) {
        if (stored.isEmpty()) {
            return stored;
        }
        List<String> effective = effectiveLoadout(path, characterLevel, investments, stored);
        List<String> padded = new ArrayList<>(stored);
        while (padded.size() < LOADOUT_SLOTS) {
            padded.add(EMPTY);
        }
        return padded.equals(effective) ? stored : effective;
    }

    /** Whether {@code skillId} may be put into {@code slot} ({@link #EMPTY} clears a slot and is always fine). */
    public static LoadoutResult canAssign(JobType job, int characterLevel, Map<String, Integer> investments, int slot,
                                          String skillId) {
        return canAssign(JobPath.base(job), characterLevel, investments, slot, skillId);
    }

    /** {@link #canAssign(JobType, int, Map, int, String)} for a job and its class. */
    public static LoadoutResult canAssign(JobPath path, int characterLevel, Map<String, Integer> investments, int slot,
                                          String skillId) {
        if (!path.job().isChosen()) {
            return LoadoutResult.NO_JOB;
        }
        if (slot < 1 || slot > LOADOUT_SLOTS) {
            return LoadoutResult.BAD_SLOT;
        }
        if (skillId.isEmpty()) {
            return LoadoutResult.OK;
        }
        SkillDefinition skill = Jobs.skill(skillId).orElse(null);
        if (skill == null) {
            return LoadoutResult.UNKNOWN_SKILL;
        }
        if (!path.owns(skill)) {
            return LoadoutResult.WRONG_JOB;
        }
        if (!skill.isUsable()) {
            return LoadoutResult.PASSIVE;
        }
        if (!skill.isInnate() && levelOf(skill, characterLevel, investments) == 0) {
            return LoadoutResult.NOT_LEARNED;
        }
        if (slot == ULTIMATE_SLOT && skill.kind() != SkillKind.ULTIMATE) {
            return LoadoutResult.NEEDS_ULTIMATE;
        }
        if (slot != ULTIMATE_SLOT && skill.kind() == SkillKind.ULTIMATE) {
            return LoadoutResult.ULTIMATE_ONLY;
        }
        return LoadoutResult.OK;
    }

    /**
     * The loadout after putting {@code skillId} into {@code slot} (validated with {@link #canAssign} first). A skill
     * already in another slot moves: the two slots swap their contents.
     */
    public static List<String> assign(List<String> effective, int slot, String skillId) {
        List<String> loadout = new ArrayList<>(effective);
        while (loadout.size() < LOADOUT_SLOTS) {
            loadout.add(EMPTY);
        }
        String previous = loadout.get(slot - 1);
        if (!skillId.isEmpty()) {
            int other = loadout.indexOf(skillId);
            if (other >= 0 && other != slot - 1) {
                loadout.set(other, previous);
            }
        }
        loadout.set(slot - 1, skillId);
        return Collections.unmodifiableList(loadout);
    }

    /**
     * Where a newly learned active or ultimate goes automatically: the first free slot that takes it, or {@code -1}
     * (already placed, a passive, or no free slot).
     */
    public static int autoSlot(List<String> effective, SkillDefinition skill) {
        if (!skill.isUsable() || effective.contains(skill.id())) {
            return -1;
        }
        int[] order = {1, 2, 3, 5, 6, 4};
        for (int slot : order) {
            if (fitsSlot(skill, slot) && effective.get(slot - 1).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ bindings

    /** Bindings without a stored loadout (the default slots). */
    public static SkillBinding[] bindings(JobType job, int characterLevel, Map<String, Integer> investments) {
        return bindings(job, characterLevel, investments, List.of());
    }

    /**
     * The bindings for the combat state machine: index {@code slot} (1..6) holds the skill of that loadout slot at its
     * learned level with the passive bonuses, or {@code null} while the slot is empty or its skill is not learned yet.
     * Index 0 (the weapon skill) is always {@code null}.
     */
    public static SkillBinding[] bindings(JobType job, int characterLevel, Map<String, Integer> investments,
                                          List<String> storedLoadout) {
        return bindings(JobPath.base(job), characterLevel, investments, storedLoadout);
    }

    /** {@link #bindings(JobType, int, Map, List)} for a job and its class. */
    public static SkillBinding[] bindings(JobPath path, int characterLevel, Map<String, Integer> investments,
                                          List<String> storedLoadout) {
        SkillBinding[] bindings = new SkillBinding[Combatant.SKILL_SLOTS];
        if (!path.job().isChosen()) {
            return bindings;
        }
        PassiveTotals passive = passives(path, characterLevel, investments);
        List<String> loadout = effectiveLoadout(path, characterLevel, investments, storedLoadout);
        for (int slot = 1; slot <= LOADOUT_SLOTS; slot++) {
            String id = loadout.get(slot - 1);
            if (id.isEmpty()) {
                continue;
            }
            SkillDefinition skill = Jobs.skill(id).orElseThrow();
            int level = levelOf(skill, characterLevel, investments);
            if (level > 0) {
                bindings[slot] = skill.binding(level, passive);
            }
        }
        return bindings;
    }
}
