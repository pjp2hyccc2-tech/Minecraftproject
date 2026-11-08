package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.DamageRoll;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.ProjectileSpec;
import com.eternalrealm.job.SkillEffect;

import java.util.List;
import java.util.Objects;

/**
 * What a projectile carries from the frame that launched it: the damage rolled at launch and what to do on impact.
 * Server only (projectiles are not saved: they live a few seconds).
 */
public record ProjectilePayload(ProjectileSpec spec, DamageRoll roll, DamageType type, double knockback,
                                ActionKind kind, List<SkillEffect.Afflict> afflictions) {
    public ProjectilePayload {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(roll, "roll");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        afflictions = List.copyOf(afflictions);
    }
}
