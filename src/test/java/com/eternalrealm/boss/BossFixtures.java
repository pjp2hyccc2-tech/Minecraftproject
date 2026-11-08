package com.eternalrealm.boss;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Small hand-made bosses for behaviour tests (the real content is tested separately). */
final class BossFixtures {
    static final BossPosition AT_ORIGIN = new BossPosition(0, 64, 0, 0);

    private BossFixtures() {
    }

    static BossAbility ability(String id, AbilityKind kind, int telegraph, int cast, int cooldown, int weight,
                               TargetRule rule) {
        double multiplier = kind.damaging() ? 2.0 : 0.0;
        return new BossAbility(id, kind, telegraph, cast, cooldown, multiplier, weight, rule);
    }

    static BossAbility slam(String id, int telegraph, int cast, int cooldown) {
        return ability(id, new AbilityKind.CircleAroundBoss(5), telegraph, cast, cooldown, 10, TargetRule.NEAREST);
    }

    /** One phase using all {@code abilities}; NORMAL/HARD/EX identical unless {@code ex} is given. */
    static BossDefinition boss(int globalCooldown, List<BossAbility> abilities) {
        List<String> ids = new ArrayList<>();
        for (BossAbility ability : abilities) {
            ids.add(ability.id());
        }
        return boss(List.of(new BossPhase(1.0, ids, globalCooldown, 1.0)), abilities, DifficultyTuning.NORMAL);
    }

    static BossDefinition boss(List<BossPhase> phases, List<BossAbility> abilities, DifficultyTuning ex) {
        return new BossDefinition("test_boss", "minecraft:zombie", 10, 1000, 10, 2, 0.5, 1, 5, phases, abilities,
                6000, summons(abilities), DifficultyTuning.NORMAL, DifficultyTuning.hard(), ex);
    }

    static List<String> summons(List<BossAbility> abilities) {
        Set<String> templates = new LinkedHashSet<>();
        for (BossAbility ability : abilities) {
            if (ability.kind() instanceof AbilityKind.Summon summon) {
                templates.add(summon.templateId());
            } else if (ability.kind() instanceof AbilityKind.Shield shield && shield.addCount() > 0) {
                templates.add(shield.addTemplateId());
            }
        }
        return List.copyOf(templates);
    }

    static UUID player(int n) {
        return new UUID(0x1234L, n);
    }

    static TargetSnapshot target(int n, double x, double z) {
        return new TargetSnapshot(player(n), x, 64, z, 0);
    }

    static TargetSnapshot target(int n, double x, double z, double threat) {
        return new TargetSnapshot(player(n), x, 64, z, threat);
    }
}
