package com.eternalrealm.boss;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Everything that defines one boss. Immutable; the fight state lives in {@link BossBrain}.
 *
 * <p>Health: {@code baseHealth × (1 + healthPerExtraPlayer × (partySize − 1)) × difficulty health multiplier}. The
 * party term grows slower than the party's damage on purpose: bigger parties finish a little faster, while a solo
 * player still faces a fight of the intended length.</p>
 *
 * @param id                   "b1".."b8" (boss of dungeon dN) or "final"
 * @param entityType           vanilla entity the platform spawns, e.g. {@code minecraft:ravager}; its AI provides
 *                             movement and the basic melee
 * @param level                level the fight is balanced for
 * @param baseHealth           maximum health for one player on {@link Difficulty#NORMAL}
 * @param damage               base damage of one hit (melee and ability multiplier 1.0) on NORMAL
 * @param armor                vanilla armor attribute value
 * @param healthPerExtraPlayer added health fraction per player beyond the first (0.7 = +70%)
 * @param minPlayers           recommended party size, lower end (informational: any party size 1..
 *                             {@link BossRules#MAX_PARTY_SIZE} works)
 * @param maxPlayers           recommended party size, upper end
 * @param phases               in order; the first starts at 1.0, thresholds strictly decreasing
 * @param abilities            every ability used by the phases or a difficulty, each id once
 * @param enrageAfterTicks     soft enrage this long after the fight started (more damage, shorter cooldowns)
 * @param summonTemplateIds    monster templates this boss summons (SUMMON and SHIELD adds); owned by the monster module
 */
public record BossDefinition(String id, String entityType, int level, double baseHealth, double damage, double armor,
                             double healthPerExtraPlayer, int minPlayers, int maxPlayers, List<BossPhase> phases,
                             List<BossAbility> abilities, int enrageAfterTicks, List<String> summonTemplateIds,
                             DifficultyTuning normal, DifficultyTuning hard, DifficultyTuning ex) {
    private static final Pattern ENTITY_TYPE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+");

    public BossDefinition {
        Check.id("id", id);
        if (entityType == null || !ENTITY_TYPE.matcher(entityType).matches()) {
            throw new IllegalArgumentException(id + ": entityType must look like namespace:path: " + entityType);
        }
        Check.range("level", level, 1, 100);
        Check.range("baseHealth", baseHealth, 1.0, 10_000_000.0);
        Check.range("damage", damage, 0.0, 10_000.0);
        Check.range("armor", armor, 0.0, 30.0);
        Check.range("healthPerExtraPlayer", healthPerExtraPlayer, 0.0, 2.0);
        Check.range("minPlayers", minPlayers, 1, BossRules.MAX_PARTY_SIZE);
        Check.range("maxPlayers", maxPlayers, minPlayers, BossRules.MAX_PARTY_SIZE);
        phases = List.copyOf(phases);
        abilities = List.copyOf(abilities);
        Check.range("enrageAfterTicks", enrageAfterTicks, 200, 20 * 60 * 60);
        summonTemplateIds = List.copyOf(summonTemplateIds);
        Objects.requireNonNull(normal, "normal");
        Objects.requireNonNull(hard, "hard");
        Objects.requireNonNull(ex, "ex");
        validatePhases(id, phases);
        Map<String, BossAbility> byId = indexAbilities(id, abilities);
        Set<String> referenced = new HashSet<>();
        for (BossPhase phase : phases) {
            requireKnown(id, byId, phase.abilityIds(), referenced);
        }
        for (DifficultyTuning tuning : List.of(normal, hard, ex)) {
            requireKnown(id, byId, tuning.extraAbilityIds(), referenced);
        }
        if (!referenced.equals(byId.keySet())) {
            Set<String> unused = new LinkedHashSet<>(byId.keySet());
            unused.removeAll(referenced);
            throw new IllegalArgumentException(id + ": abilities not used by any phase or difficulty: " + unused);
        }
        validateSummons(id, abilities, summonTemplateIds);
    }

    private static void validatePhases(String id, List<BossPhase> phases) {
        if (phases.isEmpty()) {
            throw new IllegalArgumentException(id + ": at least one phase is required");
        }
        if (phases.get(0).startsAtHpFraction() != 1.0) {
            throw new IllegalArgumentException(id + ": the first phase must start at 1.0");
        }
        for (int i = 1; i < phases.size(); i++) {
            if (phases.get(i).startsAtHpFraction() >= phases.get(i - 1).startsAtHpFraction()) {
                throw new IllegalArgumentException(id + ": phase thresholds must strictly decrease");
            }
        }
    }

    private static Map<String, BossAbility> indexAbilities(String id, List<BossAbility> abilities) {
        Map<String, BossAbility> byId = new LinkedHashMap<>();
        for (BossAbility ability : abilities) {
            if (byId.put(ability.id(), ability) != null) {
                throw new IllegalArgumentException(id + ": duplicate ability id " + ability.id());
            }
        }
        return byId;
    }

    private static void requireKnown(String id, Map<String, BossAbility> byId, List<String> abilityIds,
                                     Set<String> referenced) {
        for (String abilityId : abilityIds) {
            if (!byId.containsKey(abilityId)) {
                throw new IllegalArgumentException(id + ": unknown ability " + abilityId);
            }
            referenced.add(abilityId);
        }
    }

    private static void validateSummons(String id, List<BossAbility> abilities, List<String> summonTemplateIds) {
        for (String template : summonTemplateIds) {
            Check.id("summonTemplateId", template);
        }
        Set<String> used = new LinkedHashSet<>();
        for (BossAbility ability : abilities) {
            if (ability.kind() instanceof AbilityKind.Summon summon) {
                used.add(summon.templateId());
            } else if (ability.kind() instanceof AbilityKind.Shield shield && shield.addCount() > 0) {
                used.add(shield.addTemplateId());
            }
        }
        if (!used.equals(new LinkedHashSet<>(summonTemplateIds)) || summonTemplateIds.size() != used.size()) {
            throw new IllegalArgumentException(id + ": summonTemplateIds " + summonTemplateIds
                    + " must list exactly the templates summoned by its abilities " + used);
        }
    }

    public String translationKey() {
        return "boss." + ModInfo.MOD_ID + "." + id;
    }

    public DifficultyTuning tuning(Difficulty difficulty) {
        return switch (Objects.requireNonNull(difficulty, "difficulty")) {
            case NORMAL -> normal;
            case HARD -> hard;
            case EX -> ex;
        };
    }

    /** Maximum health for a party of {@code partySize} (1..{@link BossRules#MAX_PARTY_SIZE}) on a difficulty. */
    public double maxHealth(int partySize, Difficulty difficulty) {
        Check.range("partySize", partySize, 1, BossRules.MAX_PARTY_SIZE);
        return baseHealth * (1.0 + healthPerExtraPlayer * (partySize - 1)) * tuning(difficulty).healthMultiplier();
    }

    /** Base damage of one hit on a difficulty (before enrage and buffs). */
    public double damage(Difficulty difficulty) {
        return damage * tuning(difficulty).damageMultiplier();
    }

    public Optional<BossAbility> ability(String abilityId) {
        for (BossAbility ability : abilities) {
            if (ability.id().equals(abilityId)) {
                return Optional.of(ability);
            }
        }
        return Optional.empty();
    }

    /** The rotation of a phase on a difficulty: the phase's abilities followed by the difficulty's extra abilities. */
    public List<BossAbility> abilitiesFor(int phaseIndex, Difficulty difficulty) {
        Check.range("phaseIndex", phaseIndex, 0, phases.size() - 1);
        List<BossAbility> rotation = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> ids = new ArrayList<>(phases.get(phaseIndex).abilityIds());
        ids.addAll(tuning(difficulty).extraAbilityIds());
        for (String abilityId : ids) {
            if (seen.add(abilityId)) {
                rotation.add(ability(abilityId).orElseThrow());
            }
        }
        return List.copyOf(rotation);
    }

    /** Index of the phase a health fraction belongs to: the last phase whose threshold is at or above it. */
    public int phaseIndexFor(double hpFraction) {
        int index = 0;
        for (int i = 1; i < phases.size(); i++) {
            if (hpFraction <= phases.get(i).startsAtHpFraction()) {
                index = i;
            }
        }
        return index;
    }
}
