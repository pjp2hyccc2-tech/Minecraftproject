package com.eternalrealm.item;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.HeavyAttack;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.HitShape;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The combat profile of a named weapon: its type's profile with the melee shapes of the combo, the heavy attack and
 * the weapon skill scaled by the design's reach, and for a boss weapon its {@link WeaponArt} as the weapon skill.
 * Built once per design (identity matters: the combat system detects weapon switches by profile identity) and the same
 * on the server and the client, which both read the design from the item stack.
 */
public final class DesignProfiles {
    private static final Map<String, WeaponProfile> PROFILES;

    static {
        Map<String, WeaponProfile> profiles = new HashMap<>();
        for (WeaponDesign design : Armory.weapons()) {
            profiles.put(design.id(), build(design));
        }
        PROFILES = Collections.unmodifiableMap(profiles);
    }

    private DesignProfiles() {
    }

    /** The profile of a named weapon. */
    public static WeaponProfile of(WeaponDesign design) {
        WeaponProfile profile = PROFILES.get(design.id());
        if (profile == null) {
            throw new IllegalArgumentException("Not a catalogue design: " + design.id());
        }
        return profile;
    }

    /** The profile of a named weapon by design id; empty for unknown ids and armor. */
    public static Optional<WeaponProfile> byDesign(String designId) {
        return Optional.ofNullable(designId == null ? null : PROFILES.get(designId));
    }

    /** The boss weapon art whose action has {@code actionId}, if any (client animations, server effects). */
    public static Optional<WeaponArt> artOfAction(String actionId) {
        return WeaponArts.byId(actionId);
    }

    private static WeaponProfile build(WeaponDesign design) {
        WeaponProfile base = WeaponProfiles.of(design.type());
        double reach = design.reach();
        List<ActionDefinition> combo = new ArrayList<>();
        for (ActionDefinition step : base.combo()) {
            combo.add(scaled(step, reach));
        }
        HeavyAttack heavy = base.heavy();
        HeavyAttack scaledHeavy = new HeavyAttack(heavy.minChargeTicks(), heavy.maxChargeTicks(), heavy.minMultiplier(),
                heavy.maxMultiplier(), heavy.derivedBonus(), scaled(heavy.strike(), reach));
        // boss weapons bring their boss art, every other named weapon its own art (GeneratedArts)
        ActionDefinition skill = design.art() != null
                ? WeaponArts.byId(design.art()).orElseThrow(() -> new IllegalStateException("Unknown art "
                        + design.art())).action()
                : GeneratedArts.forDesign(design.id()).map(WeaponArt::action)
                        .orElseThrow(() -> new IllegalStateException("No art for " + design.id()));
        return new WeaponProfile(design.id(), design.type(), base.baseAttack(), base.critBonus(), combo,
                base.comboResetTicks(), scaledHeavy, skill, base.guard());
    }

    /** The action with every melee shape's reach multiplied (projectile and effect frames unchanged). */
    static ActionDefinition scaled(ActionDefinition action, double factor) {
        if (factor == 1.0) {
            return action;
        }
        List<HitFrame> frames = new ArrayList<>();
        for (HitFrame frame : action.frames()) {
            if (frame.isProjectile() || !frame.damaging()) {
                frames.add(frame);
                continue;
            }
            HitShape shape = frame.shape();
            double reach = Math.min(HitShape.MAX_REACH, Math.round(shape.reach() * factor * 100.0) / 100.0);
            HitShape longer = new HitShape(shape.kind(), reach, shape.halfAngleDegrees(), shape.width(),
                    shape.verticalUp(), shape.verticalDown(), shape.maxTargets(), shape.offsetForward());
            frames.add(new HitFrame(frame.offsetTicks(), longer, frame.multiplier(), frame.knockback(), null, true));
        }
        return new ActionDefinition(action.id(), action.kind(), action.durationTicks(), action.cancelTicks(), frames,
                action.staminaCost(), action.cooldownTicks(), action.invulnerableTicks(), action.speedScaled());
    }
}
