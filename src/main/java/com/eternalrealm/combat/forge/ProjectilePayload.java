package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.DamageRoll;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.ProjectileSpec;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.job.SkillTheme;

import java.util.List;
import java.util.Objects;

/**
 * What a projectile carries from the frame that launched it: the damage rolled at launch and what to do on impact.
 * Server only (projectiles are not saved: they live a few seconds).
 *
 * @param effectScale strength of the afflictions (skill level)
 * @param siphons     heal the caster's party with part of the damage dealt
 * @param executes    bonus damage against weakened targets
 * @param theme       look of the impact, or {@code null} for the plain weapon bolt
 */
public record ProjectilePayload(ProjectileSpec spec, DamageRoll roll, DamageType type, double knockback,
                                ActionKind kind, List<SkillEffect.Afflict> afflictions, double effectScale,
                                List<SkillEffect.Siphon> siphons, List<SkillEffect.Execute> executes,
                                SkillTheme theme) {
    public ProjectilePayload {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(roll, "roll");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        afflictions = List.copyOf(afflictions);
        siphons = List.copyOf(siphons);
        executes = List.copyOf(executes);
    }

    /** A payload without skill extras. */
    public ProjectilePayload(ProjectileSpec spec, DamageRoll roll, DamageType type, double knockback, ActionKind kind,
                             List<SkillEffect.Afflict> afflictions) {
        this(spec, roll, type, knockback, kind, afflictions, 1.0, List.of(), List.of(), null);
    }
}
