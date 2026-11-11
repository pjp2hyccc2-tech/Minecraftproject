package com.eternalrealm.item;

import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.PassiveEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * What the client shows for a piece: its display name and the tooltip body, as translation keys with arguments
 * ({@link TooltipLine}). Numbers are formatted here (one decimal at most, {@code Locale.ROOT}) so every client shows
 * the same values the server computed.
 *
 * <pre>
 * +7 炎竜の両手剣                ← {@link #name}
 * エピック・両手剣              ← RARITY
 * 攻撃力 18.6                   ← BASE_STAT (armor: 防御力 / 最大HP)
 * STR +12                        ← AFFIX (one per affix, sorted by kind)
 * 強化 +7/10（基本性能 +42%）    ← ENHANCEMENT (only when enhanced)
 * 必要レベル 30                  ← REQUIREMENT
 * 使用できる職業: ナイト・魔剣士  ← USABLE_BY (weapons; red when the viewer's job cannot use it)
 * バインド済み（取引不可）        ← BINDING (only when bound)
 * </pre>
 *
 * A named design ({@link Armory}) is called by its own name and adds, after the rarity line: where it comes from
 * (DESIGN), its handling, element and fixed bonuses (TRAIT), its perks (PERK), every weapon's art (ART), an armor
 * piece's set with both bonuses (SET), and its flavour text (LORE) before the requirement.
 */
public final class EquipmentTooltip {
    private static final String PREFIX = "tooltip." + ModInfo.MOD_ID + ".equipment.";
    public static final String NAME = "equipment." + ModInfo.MOD_ID + ".name";
    public static final String NAME_ENHANCED = "equipment." + ModInfo.MOD_ID + ".name_enhanced";
    public static final String RARITY_TYPE = PREFIX + "rarity_type";
    public static final String ATTACK = PREFIX + "attack";
    public static final String DEFENSE = PREFIX + "defense";
    public static final String MAX_HEALTH = PREFIX + "max_health";
    public static final String ENHANCEMENT = PREFIX + "enhancement";
    public static final String REQUIRED_LEVEL = PREFIX + "required_level";
    public static final String BOUND = PREFIX + "bound";
    public static final String USABLE_BY = PREFIX + "usable_by";
    public static final String JOB_SEPARATOR = PREFIX + "job_separator";
    public static final String DESIGN_NAME = "equipment." + ModInfo.MOD_ID + ".design_name";
    public static final String DESIGN_NAME_ENHANCED = "equipment." + ModInfo.MOD_ID + ".design_name_enhanced";
    public static final String FROM_FIELD = PREFIX + "from.field";
    public static final String FROM_CRAFT = PREFIX + "from.craft";
    public static final String FROM_DUNGEON = PREFIX + "from.dungeon";
    public static final String FROM_LORD = PREFIX + "from.lord";
    public static final String FROM_CALAMITY = PREFIX + "from.calamity";
    public static final String FROM_BOSS = PREFIX + "from.boss";
    public static final String REACH = PREFIX + "reach";
    public static final String ELEMENT = PREFIX + "element";
    public static final String ART = PREFIX + "art";
    public static final String SET = PREFIX + "set";
    public static final String SET_BONUS = PREFIX + "set_bonus";
    public static final String LORE = PREFIX + "lore";
    public static final String TRAIT_SEPARATOR = PREFIX + "trait_separator";
    /** A line of traits joined with {@link #TRAIT_SEPARATOR}. */
    public static final String TRAITS = PREFIX + "traits";
    /** The secondary bonus lines (shared with the skill tree texts). */
    public static final String MODIFIER = "skill_info." + ModInfo.MOD_ID + ".passive.";

    /** Every fixed key of this class. */
    public static final List<String> KEYS = List.of(NAME, NAME_ENHANCED, RARITY_TYPE, ATTACK, DEFENSE, MAX_HEALTH,
            ENHANCEMENT, REQUIRED_LEVEL, BOUND, USABLE_BY, JOB_SEPARATOR, DESIGN_NAME, DESIGN_NAME_ENHANCED,
            FROM_FIELD, FROM_CRAFT, FROM_DUNGEON, FROM_LORD, FROM_CALAMITY, FROM_BOSS, REACH, ELEMENT, ART, SET,
            SET_BONUS, LORE, TRAIT_SEPARATOR, TRAITS, MODIFIER + "attack_speed");

    private EquipmentTooltip() {
    }

    /**
     * Display name in the rarity colour: "{tier}の{noun}" ({@code 鉄の片手剣}, "Iron Longsword"), prefixed with the
     * enhancement when there is one ({@code +7 炎竜の両手剣}).
     */
    public static TooltipLine name(EquipmentData data) {
        Objects.requireNonNull(data, "data");
        if (data.design() != null) {
            TooltipLine.Key design = new TooltipLine.Key(data.design().translationKey());
            if (data.enhancement() > 0) {
                return TooltipLine.of(TooltipLine.Kind.NAME, DESIGN_NAME_ENHANCED, data.rarity().color(), design,
                        new TooltipLine.Text(Integer.toString(data.enhancement())));
            }
            return TooltipLine.of(TooltipLine.Kind.NAME, DESIGN_NAME, data.rarity().color(), design);
        }
        EquipmentBase base = data.base();
        TooltipLine.Key tier = new TooltipLine.Key(data.tier().translationKey());
        TooltipLine.Key noun = new TooltipLine.Key(base.nounKey());
        int color = data.rarity().color();
        if (data.enhancement() > 0) {
            return TooltipLine.of(TooltipLine.Kind.NAME, NAME_ENHANCED, color, tier, noun,
                    new TooltipLine.Text(Integer.toString(data.enhancement())));
        }
        return TooltipLine.of(TooltipLine.Kind.NAME, NAME, color, tier, noun);
    }

    /** The tooltip body without a viewer (requirement lines in the neutral colour). */
    public static List<TooltipLine> lines(EquipmentData data) {
        return build(data, Integer.MAX_VALUE, JobType.NONE);
    }

    /** The tooltip body; the requirement line turns {@link TooltipLine#COLOR_WARNING} if {@code viewerLevel} is too low. */
    public static List<TooltipLine> lines(EquipmentData data, int viewerLevel) {
        return build(data, viewerLevel, JobType.NONE);
    }

    /**
     * The tooltip body for a viewer: the requirement line is {@link TooltipLine#COLOR_WARNING} if the level is too
     * low, the "usable by" line of a weapon if the viewer's job ({@link JobType#NONE}: none chosen) cannot use it.
     */
    public static List<TooltipLine> lines(EquipmentData data, int viewerLevel, JobType viewerJob) {
        return build(data, viewerLevel, java.util.Objects.requireNonNull(viewerJob, "viewerJob"));
    }

    /** "使用できる職業: ナイト・魔剣士" for a weapon type; red when {@code viewerJob} cannot use it. */
    public static TooltipLine usableBy(com.eternalrealm.combat.WeaponType type, JobType viewerJob) {
        List<TooltipLine.Arg> jobs = new ArrayList<>();
        for (JobType job : Jobs.jobsUsing(type)) {
            jobs.add(new TooltipLine.Key(job.translationKey()));
        }
        int color = viewerJob.isChosen() && !Jobs.canUse(viewerJob, type) ? TooltipLine.COLOR_WARNING
                : TooltipLine.COLOR_TEXT;
        return TooltipLine.of(TooltipLine.Kind.USABLE_BY, USABLE_BY, color, new TooltipLine.Join(jobs, JOB_SEPARATOR));
    }

    private static List<TooltipLine> build(EquipmentData data, int viewerLevel, JobType viewerJob) {
        Objects.requireNonNull(data, "data");
        List<TooltipLine> lines = new ArrayList<>();
        String typeKey = data.isWeapon() ? data.weaponType().translationKey() : data.slot().translationKey();
        lines.add(TooltipLine.of(TooltipLine.Kind.RARITY, RARITY_TYPE, data.rarity().color(),
                new TooltipLine.Key(data.rarity().translationKey()), new TooltipLine.Key(typeKey)));
        if (data.design() != null) {
            lines.add(source(data.design()));
        }
        if (data.isWeapon()) {
            lines.add(stat(ATTACK, EquipmentStats.baseAttack(data)));
        } else {
            lines.add(stat(DEFENSE, EquipmentStats.baseDefense(data)));
            lines.add(stat(MAX_HEALTH, EquipmentStats.baseMaxHealth(data)));
        }
        data.weaponDesign().ifPresent(weapon -> lines.addAll(weaponLines(weapon)));
        artOf(data).ifPresent(art -> lines.addAll(artLines(art)));
        data.armorPiece().ifPresent(piece -> lines.addAll(armorLines(piece)));
        for (Affix affix : data.affixes()) {
            lines.add(TooltipLine.of(TooltipLine.Kind.AFFIX, affix.kind().translationKey(), TooltipLine.COLOR_AFFIX,
                    new TooltipLine.Text(affix.kind().format(affix.value()))));
        }
        if (data.enhancement() > 0) {
            double percent = EquipmentStats.ENHANCEMENT_BONUS_PER_LEVEL * data.enhancement() * 100.0;
            lines.add(TooltipLine.of(TooltipLine.Kind.ENHANCEMENT, ENHANCEMENT, TooltipLine.COLOR_ENHANCEMENT,
                    new TooltipLine.Text(Integer.toString(data.enhancement())),
                    new TooltipLine.Text(Integer.toString(EquipmentData.MAX_ENHANCEMENT)),
                    new TooltipLine.Text(number(percent))));
        }
        if (data.design() != null) {
            lines.add(TooltipLine.of(TooltipLine.Kind.LORE, LORE, TooltipLine.COLOR_LORE,
                    new TooltipLine.Key(data.design().loreKey())));
        }
        int requirementColor = viewerLevel < data.requiredLevel() ? TooltipLine.COLOR_WARNING : TooltipLine.COLOR_TEXT;
        lines.add(TooltipLine.of(TooltipLine.Kind.REQUIREMENT, REQUIRED_LEVEL, requirementColor,
                new TooltipLine.Text(Integer.toString(data.requiredLevel()))));
        if (data.isWeapon()) {
            lines.add(usableBy(data.weaponType(), viewerJob));
        }
        if (data.isBound()) {
            lines.add(TooltipLine.of(TooltipLine.Kind.BINDING, BOUND, TooltipLine.COLOR_WARNING));
        }
        return Collections.unmodifiableList(lines);
    }

    /** "ヌシ「街道荒らしのガルム」の得物", "砂塵の神殿の秘宝", "はじまりの草原の名品" ... */
    static TooltipLine source(Design design) {
        String origin = design.origin();
        TooltipLine line;
        if (design.sources().contains(DesignSource.BOSS)) {
            line = TooltipLine.of(TooltipLine.Kind.DESIGN, FROM_BOSS, TooltipLine.COLOR_ART,
                    new TooltipLine.Key("boss." + ModInfo.MOD_ID + "." + origin));
        } else if (design.sources().contains(DesignSource.LORD)) {
            line = TooltipLine.of(TooltipLine.Kind.DESIGN, FROM_LORD, TooltipLine.COLOR_DESIGN,
                    new TooltipLine.Key("threat." + ModInfo.MOD_ID + ".lord." + origin));
        } else if (design.sources().contains(DesignSource.CALAMITY)) {
            line = TooltipLine.of(TooltipLine.Kind.DESIGN, FROM_CALAMITY, TooltipLine.COLOR_DESIGN,
                    new TooltipLine.Key("threat." + ModInfo.MOD_ID + ".calamity." + origin));
        } else if (design.sources().contains(DesignSource.DUNGEON)) {
            line = TooltipLine.of(TooltipLine.Kind.DESIGN, FROM_DUNGEON, TooltipLine.COLOR_DESIGN,
                    new TooltipLine.Key("dungeon." + ModInfo.MOD_ID + "." + origin));
        } else {
            String area = com.eternalrealm.world.Areas.byId(design.field())
                    .map(definition -> definition.translationKey()).orElse("");
            line = TooltipLine.of(TooltipLine.Kind.DESIGN,
                    design.sources().contains(DesignSource.FIELD) || design.sources().contains(DesignSource.MONSTER)
                            ? FROM_FIELD : FROM_CRAFT, TooltipLine.COLOR_DESIGN, new TooltipLine.Key(area));
        }
        return line;
    }

    /** Handling (crit, speed, reach), element, fixed bonuses, perks and the art of a named weapon. */
    static List<TooltipLine> weaponLines(WeaponDesign weapon) {
        List<TooltipLine> lines = new ArrayList<>();
        List<TooltipLine.Arg> handling = new ArrayList<>();
        modifier(handling, "crit", weapon.crit(), true);
        modifier(handling, "attack_speed", weapon.speed(), true);
        if (weapon.reach() != 1.0) {
            double percent = (weapon.reach() - 1.0) * 100.0;
            handling.add(TooltipLine.Nested.of(REACH,
                    new TooltipLine.Text((percent > 0 ? "+" : "−") + number(Math.abs(percent)))));
        }
        if (!handling.isEmpty()) {
            lines.add(TooltipLine.of(TooltipLine.Kind.TRAIT, TRAITS, TooltipLine.COLOR_TRAIT,
                    new TooltipLine.Join(handling, TRAIT_SEPARATOR)));
        }
        weapon.element().affliction().ifPresent(kind -> lines.add(TooltipLine.of(TooltipLine.Kind.TRAIT, ELEMENT,
                TooltipLine.COLOR_TRAIT, new TooltipLine.Key(weapon.element().translationKey()),
                new TooltipLine.Text(number(weapon.proc() * 100.0)), new TooltipLine.Key(kind.translationKey()),
                new TooltipLine.Text(number(weapon.element().ticks() / 20.0)))));
        List<TooltipLine.Arg> bonus = modifiers(weapon.bonus());
        if (!bonus.isEmpty()) {
            lines.add(TooltipLine.of(TooltipLine.Kind.TRAIT, TRAITS, TooltipLine.COLOR_TRAIT,
                    new TooltipLine.Join(bonus, TRAIT_SEPARATOR)));
        }
        for (PassiveEffect perk : weapon.perks()) {
            for (TooltipLine.Nested text : perk(perk)) {
                lines.add(new TooltipLine(TooltipLine.Kind.PERK, text.translationKey(), text.args(),
                        TooltipLine.COLOR_PERK));
            }
        }
        return lines;
    }

    /** The weapon art on the weapon-skill key (Z): the boss art, the named weapon's art or the tier's art. */
    public static java.util.Optional<WeaponArt> artOf(EquipmentData data) {
        if (!data.isWeapon()) {
            return java.util.Optional.empty();
        }
        java.util.Optional<WeaponDesign> design = data.weaponDesign();
        if (design.isPresent()) {
            WeaponDesign weapon = design.get();
            return weapon.art() != null ? WeaponArts.byId(weapon.art()) : GeneratedArts.forDesign(weapon.id());
        }
        return java.util.Optional.of(GeneratedArts.forTier(data.tier(), data.weaponType()));
    }

    /** Name and cooldown of the art, then what it does. */
    static List<TooltipLine> artLines(WeaponArt art) {
        return List.of(TooltipLine.of(TooltipLine.Kind.ART, ART, TooltipLine.COLOR_ART,
                        new TooltipLine.Key(art.translationKey()),
                        new TooltipLine.Text(number(art.action().cooldownTicks() / 20.0))),
                TooltipLine.of(TooltipLine.Kind.ART, art.descriptionKey(), TooltipLine.COLOR_ART));
    }

    /** The set of an armor piece with its two- and four-piece bonuses. */
    static List<TooltipLine> armorLines(ArmorPiece piece) {
        ArmorSet set = piece.set();
        List<TooltipLine> lines = new ArrayList<>();
        List<TooltipLine.Arg> own = modifiers(set.piece());
        if (!own.isEmpty()) {
            lines.add(TooltipLine.of(TooltipLine.Kind.TRAIT, TRAITS, TooltipLine.COLOR_TRAIT,
                    new TooltipLine.Join(own, TRAIT_SEPARATOR)));
        }
        lines.add(TooltipLine.of(TooltipLine.Kind.SET, SET, TooltipLine.COLOR_SET,
                new TooltipLine.Key(set.translationKey()), new TooltipLine.Key(set.style().translationKey())));
        lines.add(setBonus(2, set.two()));
        lines.add(setBonus(ArmorSet.PIECES, set.four()));
        return lines;
    }

    private static TooltipLine setBonus(int pieces, ArmorSet.SetBonus bonus) {
        List<TooltipLine.Arg> parts = new ArrayList<>(modifiers(bonus.modifiers()));
        for (PassiveEffect perk : bonus.perks()) {
            parts.addAll(perk(perk));
        }
        return TooltipLine.of(TooltipLine.Kind.SET, SET_BONUS, TooltipLine.COLOR_SET,
                new TooltipLine.Text(Integer.toString(pieces)), new TooltipLine.Join(parts, TRAIT_SEPARATOR));
    }

    /** One part per non-zero secondary bonus ("物理ダメージ +4%"). */
    static List<TooltipLine.Arg> modifiers(CombatModifiers m) {
        List<TooltipLine.Arg> parts = new ArrayList<>();
        modifier(parts, "physical", m.physicalBonus(), true);
        modifier(parts, "ranged", m.rangedBonus(), true);
        modifier(parts, "magic", m.magicBonus(), true);
        modifier(parts, "healing", m.healingBonus(), true);
        modifier(parts, "health_flat", m.maxHealthFlat(), false);
        modifier(parts, "health_percent", m.maxHealthPercent(), true);
        modifier(parts, "defense", m.defenseFlat(), false);
        modifier(parts, "crit", m.critChance(), true);
        modifier(parts, "crit_damage", m.critDamage(), true);
        modifier(parts, "move_speed", m.moveSpeed(), true);
        modifier(parts, "guard", m.guardBonus(), true);
        modifier(parts, "stamina", m.staminaBonus(), false);
        modifier(parts, "attack_speed", m.attackSpeed(), true);
        return parts;
    }

    private static void modifier(List<TooltipLine.Arg> parts, String key, double value, boolean percent) {
        if (value > 0.0) {
            parts.add(TooltipLine.Nested.of(MODIFIER + key, new TooltipLine.Text(number(percent ? value * 100.0
                    : value))));
        }
    }

    /** The text of a perk (one part, two for a last stand with both bonuses). */
    static List<TooltipLine.Nested> perk(PassiveEffect perk) {
        List<TooltipLine.Nested> parts = new ArrayList<>();
        if (perk instanceof PassiveEffect.Lifesteal lifesteal) {
            parts.add(TooltipLine.Nested.of(MODIFIER + "lifesteal", percent(lifesteal.perLevel())));
        } else if (perk instanceof PassiveEffect.Proc proc) {
            parts.add(TooltipLine.Nested.of(MODIFIER + (proc.meleeOnly() ? "proc_melee" : "proc_any"),
                    percent(proc.chancePerLevel()), new TooltipLine.Key(proc.kind().translationKey()),
                    new TooltipLine.Text(number(proc.durationTicks() / 20.0))));
        } else if (perk instanceof PassiveEffect.LastStand last) {
            if (last.damagePerLevel() > 0.0) {
                parts.add(TooltipLine.Nested.of(MODIFIER + "last_stand_damage", percent(last.damagePerLevel())));
            }
            if (last.reductionPerLevel() > 0.0) {
                parts.add(TooltipLine.Nested.of(MODIFIER + "last_stand_reduction", percent(last.reductionPerLevel())));
            }
        } else if (perk instanceof PassiveEffect.Thorns thorns) {
            parts.add(TooltipLine.Nested.of(MODIFIER + "thorns", percent(thorns.perLevel())));
        } else if (perk instanceof PassiveEffect.Evasion evasion) {
            parts.add(TooltipLine.Nested.of(MODIFIER + "evasion", percent(evasion.perLevel())));
        } else if (perk instanceof PassiveEffect.HealOnKill heal) {
            parts.add(TooltipLine.Nested.of(MODIFIER + "heal_on_kill", percent(heal.perLevel())));
        } else if (perk instanceof PassiveEffect.Stats stats) {
            parts.addAll(modifiers(stats.perLevel()).stream().map(TooltipLine.Nested.class::cast).toList());
        } else if (perk instanceof PassiveEffect.SkillPower power) {
            parts.add(TooltipLine.Nested.of(MODIFIER + "skill_power", percent(power.perLevel())));
        }
        return parts;
    }

    private static TooltipLine.Text percent(double fraction) {
        return new TooltipLine.Text(number(fraction * 100.0));
    }

    private static TooltipLine stat(String key, double value) {
        return TooltipLine.of(TooltipLine.Kind.BASE_STAT, key, TooltipLine.COLOR_STAT, new TooltipLine.Text(number(value)));
    }

    /** {@code 12} for whole numbers, otherwise one decimal ({@code 54.4}); rounded half up to 0.1. */
    static String number(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }
}
