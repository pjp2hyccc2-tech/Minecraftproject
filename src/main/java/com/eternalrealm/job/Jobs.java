package com.eternalrealm.job;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.HitShape;
import com.eternalrealm.combat.ProjectileSpec;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.SkillEffect.AfflictionKind;
import com.eternalrealm.job.SkillEffect.BuffKind;
import com.eternalrealm.job.SkillEffect.BuffTarget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The six jobs and their 24 skills (docs/JOB_SYSTEM.md). Defined in code so the client's prediction and the server's
 * validation always agree. Skill slots unlock at levels 1, 8, 18 and 30 (the ultimate).
 */
public final class Jobs {
    public static final int[] UNLOCK_LEVELS = {1, 8, 18, 30};
    public static final int MAX_SKILL_LEVEL = 5;

    private static final Set<WeaponType> ANY = Set.of();
    private static final Set<WeaponType> MELEE = Set.of(WeaponType.ONE_HANDED_SWORD, WeaponType.TWO_HANDED_SWORD,
            WeaponType.DUAL_BLADES, WeaponType.SPEAR);
    private static final Set<WeaponType> SWORDS = Set.of(WeaponType.ONE_HANDED_SWORD, WeaponType.TWO_HANDED_SWORD);
    private static final Set<WeaponType> STAFF = Set.of(WeaponType.STAFF);
    private static final Set<WeaponType> BOW = Set.of(WeaponType.BOW);

    private static final Map<JobType, JobDefinition> BY_TYPE = new EnumMap<>(JobType.class);
    private static final Map<String, SkillDefinition> SKILLS = new LinkedHashMap<>();

    static {
        register(new JobDefinition(JobType.NONE, JobRole.NONE, StatBlock.ZERO, CombatModifiers.NONE, Set.of(), null,
                1.0, List.of()));
        register(knight());
        register(assassin());
        register(mage());
        register(ranger());
        register(priest());
        register(spellblade());
    }

    private Jobs() {
    }

    public static JobDefinition get(JobType type) {
        return BY_TYPE.get(type);
    }

    /** The five real jobs in display order. */
    public static List<JobDefinition> selectable() {
        List<JobDefinition> result = new ArrayList<>();
        for (JobType type : JobType.values()) {
            if (type.isChosen()) {
                result.add(BY_TYPE.get(type));
            }
        }
        return result;
    }

    public static Optional<SkillDefinition> skill(String id) {
        return Optional.ofNullable(SKILLS.get(id));
    }

    /** Skill whose action has {@code actionId} (skill and action ids are equal). */
    public static Optional<SkillDefinition> skillOfAction(String actionId) {
        return skill(actionId);
    }

    public static Map<String, SkillDefinition> allSkills() {
        return Collections.unmodifiableMap(SKILLS);
    }

    private static void register(JobDefinition job) {
        BY_TYPE.put(job.type(), job);
        for (SkillDefinition skill : job.skills()) {
            if (SKILLS.put(skill.id(), skill) != null) {
                throw new ExceptionInInitializerError("Duplicate skill id " + skill.id());
            }
        }
    }

    // ------------------------------------------------------------------ ナイト

    private static JobDefinition knight() {
        JobType job = JobType.KNIGHT;
        return new JobDefinition(job, JobRole.TANK, growth(StatType.STR, 1, StatType.VIT, 1),
                CombatModifiers.builder().healthPercent(0.15).guard(0.10).defense(10).build(),
                Set.of(WeaponType.ONE_HANDED_SWORD, WeaponType.TWO_HANDED_SWORD, WeaponType.SPEAR),
                WeaponType.ONE_HANDED_SWORD, 2.0, List.of(
                skill("knight_shield_bash", job, 1, 160, DamageType.PHYSICAL, MELEE,
                        action("knight_shield_bash", 16, 12, 15, melee(4, HitShape.arc(3.4, 70), 1.6, 1.0)),
                        new SkillEffect.Afflict(0, AfflictionKind.SLOW, 60, 2),
                        new SkillEffect.Visual(0, "slam_small")),
                skill("knight_war_cry", job, 2, 400, DamageType.PHYSICAL, ANY,
                        action("knight_war_cry", 20, 16, 10, HitFrame.effect(6)),
                        new SkillEffect.Taunt(0, 12.0, 120),
                        new SkillEffect.Buff(0, BuffTarget.SELF, BuffKind.DEFENSE_UP, 0.3, 160, 0),
                        new SkillEffect.Visual(0, "war_cry")),
                skill("knight_iron_wall", job, 3, 600, DamageType.PHYSICAL, ANY,
                        action("knight_iron_wall", 14, 10, 20, HitFrame.effect(4)),
                        new SkillEffect.Shield(0, 0.3, 200),
                        new SkillEffect.Buff(0, BuffTarget.SELF, BuffKind.DEFENSE_UP, 0.5, 120, 0),
                        new SkillEffect.Buff(0, BuffTarget.ALLIES, BuffKind.DEFENSE_UP, 0.2, 120, 6.0),
                        new SkillEffect.Visual(0, "iron_wall")),
                skill("knight_holy_judgment", job, 4, 1800, DamageType.PHYSICAL, MELEE,
                        action("knight_holy_judgment", 36, 32, 40, melee(18, HitShape.circle(6.0), 4.0, 1.5)),
                        new SkillEffect.Visual(SkillEffect.ON_START, "judgment_charge"),
                        new SkillEffect.Visual(0, "judgment"))));
    }

    // ------------------------------------------------------------------ アサシン

    private static JobDefinition assassin() {
        JobType job = JobType.ASSASSIN;
        List<HitFrame> blades = new ArrayList<>();
        for (int offset = 6; offset <= 33; offset += 3) {
            blades.add(melee(offset, HitShape.circle(4.0), 0.75, 0.05));
        }
        return new JobDefinition(job, JobRole.MELEE, growth(StatType.AGI, 1, StatType.LUK, 1),
                CombatModifiers.builder().crit(0.08).critDamage(0.25).dodgeCost(0.8).build(),
                Set.of(WeaponType.DUAL_BLADES), WeaponType.DUAL_BLADES, 1.0, List.of(
                skill("assassin_shadow_step", job, 1, 120, DamageType.PHYSICAL, MELEE,
                        action("assassin_shadow_step", 14, 10, 15, melee(5, HitShape.circle(2.5), 1.4, 0.2)),
                        new SkillEffect.Dash(SkillEffect.ON_START, 6.0, false),
                        new SkillEffect.Visual(SkillEffect.ON_START, "shadow")),
                skill("assassin_venom_blade", job, 2, 240, DamageType.PHYSICAL, MELEE,
                        action("assassin_venom_blade", 18, 14, 20,
                                melee(3, HitShape.arc(3.0, 60), 0.8, 0.1),
                                melee(7, HitShape.arc(3.0, 60), 0.8, 0.1),
                                melee(11, HitShape.arc(3.2, 60), 1.0, 0.3)),
                        new SkillEffect.Afflict(2, AfflictionKind.BLEED, 100, 0.2)),
                skill("assassin_smoke_bomb", job, 3, 500, DamageType.PHYSICAL, ANY,
                        action("assassin_smoke_bomb", 12, 8, 10, HitFrame.effect(2)),
                        new SkillEffect.Buff(0, BuffTarget.SELF, BuffKind.INVISIBILITY, 0, 80, 0),
                        new SkillEffect.Buff(0, BuffTarget.SELF, BuffKind.SPEED, 1, 80, 0),
                        new SkillEffect.DropAggro(0, 10.0),
                        new SkillEffect.Visual(0, "smoke")),
                skill("assassin_thousand_blades", job, 4, 1800, DamageType.PHYSICAL, MELEE,
                        action("assassin_thousand_blades", 44, 40, 40, blades.toArray(new HitFrame[0])),
                        new SkillEffect.Visual(SkillEffect.ON_START, "blade_storm"))));
    }

    // ------------------------------------------------------------------ メイジ

    private static JobDefinition mage() {
        JobType job = JobType.MAGE;
        HitShape storm = new HitShape(HitShape.Kind.CIRCLE, 12.0, 180.0, 0.0, 4.0, 6.0, 5);
        return new JobDefinition(job, JobRole.CASTER, growth(StatType.INT, 2),
                CombatModifiers.builder().magic(0.20).stamina(20).build(),
                Set.of(WeaponType.STAFF), WeaponType.STAFF, 1.0, List.of(
                skill("mage_fireball", job, 1, 100, DamageType.MAGIC, STAFF,
                        action("mage_fireball", 16, 12, 15, HitFrame.launch(5,
                                ProjectileSpec.magic("fireball", 1.5, 30).withExplosion(2.5), 2.0, 0.6)),
                        new SkillEffect.Afflict(0, AfflictionKind.BURN, 60, 0)),
                skill("mage_ice_lance", job, 2, 160, DamageType.MAGIC, STAFF,
                        action("mage_ice_lance", 16, 12, 15, HitFrame.launch(5,
                                ProjectileSpec.magic("ice_lance", 2.2, 20).withPierce(5), 1.7, 0.2)),
                        new SkillEffect.Afflict(0, AfflictionKind.FREEZE, 60, 0)),
                skill("mage_thunderstorm", job, 3, 360, DamageType.MAGIC, STAFF,
                        action("mage_thunderstorm", 24, 20, 25,
                                melee(8, storm, 1.5, 0.3), melee(14, storm, 1.5, 0.3)),
                        new SkillEffect.Visual(0, "lightning"),
                        new SkillEffect.Visual(1, "lightning")),
                skill("mage_meteor", job, 4, 2000, DamageType.MAGIC, STAFF,
                        action("mage_meteor", 40, 36, 40, HitFrame.launch(24,
                                ProjectileSpec.magic("meteor", 1.2, 40).withExplosion(6.0).fallingFromAbove(), 5.0, 1.5)),
                        new SkillEffect.Afflict(0, AfflictionKind.BURN, 100, 0),
                        new SkillEffect.Visual(SkillEffect.ON_START, "meteor_cast"))));
    }

    // ------------------------------------------------------------------ レンジャー

    private static JobDefinition ranger() {
        JobType job = JobType.RANGER;
        List<HitFrame> rain = new ArrayList<>();
        List<SkillEffect> rainEffects = new ArrayList<>();
        for (int offset = 10, i = 0; offset <= 38; offset += 4, i++) {
            rain.add(melee(offset, HitShape.circleAhead(5.0, 10.0), 0.8, 0.1));
            rainEffects.add(new SkillEffect.Visual(i, "arrow_rain"));
        }
        return new JobDefinition(job, JobRole.RANGED, growth(StatType.DEX, 1, StatType.AGI, 1),
                CombatModifiers.builder().ranged(0.15).moveSpeed(0.05).build(),
                Set.of(WeaponType.BOW), WeaponType.BOW, 1.0, List.of(
                skill("ranger_rapid_shot", job, 1, 120, DamageType.RANGED, BOW,
                        action("ranger_rapid_shot", 18, 14, 15,
                                HitFrame.launch(3, ProjectileSpec.arrow(3.2), 0.9, 0.2),
                                HitFrame.launch(7, ProjectileSpec.arrow(3.2), 0.9, 0.2),
                                HitFrame.launch(11, ProjectileSpec.arrow(3.2), 0.9, 0.2))),
                skill("ranger_spread_shot", job, 2, 200, DamageType.RANGED, BOW,
                        action("ranger_spread_shot", 16, 12, 20,
                                HitFrame.launch(5, ProjectileSpec.arrow(3.0).withFan(5, 40), 1.0, 0.3))),
                skill("ranger_leap_shot", job, 3, 240, DamageType.RANGED, BOW,
                        action("ranger_leap_shot", 16, 12, 20,
                                HitFrame.launch(6, ProjectileSpec.arrow(3.5), 1.6, 0.6)),
                        new SkillEffect.Dash(SkillEffect.ON_START, 5.0, true),
                        new SkillEffect.Afflict(0, AfflictionKind.SLOW, 60, 2)),
                new SkillDefinition("ranger_arrow_rain", job, 4, UNLOCK_LEVELS[3], MAX_SKILL_LEVEL,
                        action("ranger_arrow_rain", 50, 46, 40, rain.toArray(new HitFrame[0])), 1800,
                        DamageType.RANGED, BOW, rainEffects)));
    }

    // ------------------------------------------------------------------ プリースト

    private static JobDefinition priest() {
        JobType job = JobType.PRIEST;
        return new JobDefinition(job, JobRole.HEALER, growth(StatType.INT, 1, StatType.VIT, 1),
                CombatModifiers.builder().healing(0.30).healthPercent(0.05).magic(0.05).build(),
                Set.of(WeaponType.STAFF), WeaponType.STAFF, 0.8, List.of(
                skill("priest_heal", job, 1, 160, DamageType.HOLY, ANY,
                        action("priest_heal", 20, 16, 15, HitFrame.effect(8)),
                        new SkillEffect.Heal(0, 8.0, 0.20, 4.0),
                        new SkillEffect.Visual(0, "heal")),
                skill("priest_holy_light", job, 2, 100, DamageType.HOLY, ANY,
                        action("priest_holy_light", 14, 10, 10, HitFrame.launch(4,
                                ProjectileSpec.magic("holy_light", 1.8, 24), 1.8, 0.4))),
                skill("priest_blessing", job, 3, 600, DamageType.HOLY, ANY,
                        action("priest_blessing", 20, 16, 20, HitFrame.effect(8)),
                        new SkillEffect.Buff(0, BuffTarget.ALLIES, BuffKind.ATTACK_UP, 0.2, 200, 10.0),
                        new SkillEffect.Buff(0, BuffTarget.ALLIES, BuffKind.REGENERATION, 0, 200, 10.0),
                        new SkillEffect.Visual(0, "blessing")),
                skill("priest_sanctuary", job, 4, 2000, DamageType.HOLY, ANY,
                        action("priest_sanctuary", 40, 36, 40, melee(16, HitShape.circle(6.0), 2.0, 0.8)),
                        new SkillEffect.Heal(0, 10.0, 0.5, 10.0),
                        new SkillEffect.Buff(0, BuffTarget.ALLIES, BuffKind.DEFENSE_UP, 0.3, 160, 10.0),
                        new SkillEffect.Visual(0, "sanctuary"))));
    }

    // ------------------------------------------------------------------ 魔剣士

    private static JobDefinition spellblade() {
        JobType job = JobType.SPELLBLADE;
        return new JobDefinition(job, JobRole.HYBRID, growth(StatType.STR, 1, StatType.INT, 1),
                CombatModifiers.builder().intToPhysical(0.5).magic(0.10).build(),
                Set.of(WeaponType.ONE_HANDED_SWORD, WeaponType.TWO_HANDED_SWORD), WeaponType.ONE_HANDED_SWORD, 1.2,
                List.of(
                skill("spellblade_arcane_edge", job, 1, 120, DamageType.MAGIC, SWORDS,
                        action("spellblade_arcane_edge", 16, 12, 15, melee(5, HitShape.arc(3.6, 70), 1.8, 0.5)),
                        new SkillEffect.Visual(0, "arcane_slash")),
                skill("spellblade_flame_blade", job, 2, 400, DamageType.MAGIC, ANY,
                        action("spellblade_flame_blade", 12, 8, 15, HitFrame.effect(3)),
                        new SkillEffect.Buff(0, BuffTarget.SELF, BuffKind.ATTACK_UP, 0.25, 200, 0),
                        new SkillEffect.Buff(0, BuffTarget.SELF, BuffKind.FLAME_WEAPON, 0, 200, 0),
                        new SkillEffect.Visual(0, "flame_blade")),
                skill("spellblade_mana_wave", job, 3, 240, DamageType.MAGIC, SWORDS,
                        action("spellblade_mana_wave", 18, 14, 20, HitFrame.launch(6,
                                ProjectileSpec.magic("mana_wave", 1.8, 16).withPierce(8), 2.0, 0.5))),
                skill("spellblade_dimension_slash", job, 4, 1800, DamageType.MAGIC, SWORDS,
                        action("spellblade_dimension_slash", 36, 32, 40,
                                melee(8, HitShape.line(8.0, 1.2), 3.0, 0.8),
                                melee(20, HitShape.line(8.0, 1.2), 3.0, 1.2)),
                        new SkillEffect.Dash(SkillEffect.ON_START, 8.0, false),
                        new SkillEffect.Visual(1, "rift"))));
    }

    // ------------------------------------------------------------------ helpers

    private static StatBlock growth(StatType stat, int value) {
        return StatBlock.ZERO.with(stat, value);
    }

    private static StatBlock growth(StatType first, int firstValue, StatType second, int secondValue) {
        return StatBlock.ZERO.with(first, firstValue).with(second, secondValue);
    }

    private static HitFrame melee(int offset, HitShape shape, double multiplier, double knockback) {
        return new HitFrame(offset, shape, multiplier, knockback);
    }

    private static ActionDefinition action(String id, int duration, int cancel, double stamina, HitFrame... frames) {
        return new ActionDefinition(id, ActionKind.SKILL, duration, cancel, List.of(frames), stamina, 0, 0, false);
    }

    private static SkillDefinition skill(String id, JobType job, int slot, int cooldown, DamageType type,
                                         Set<WeaponType> weapons, ActionDefinition action, SkillEffect... effects) {
        return new SkillDefinition(id, job, slot, UNLOCK_LEVELS[slot - 1], MAX_SKILL_LEVEL, action, cooldown, type,
                weapons, List.of(effects));
    }
}
