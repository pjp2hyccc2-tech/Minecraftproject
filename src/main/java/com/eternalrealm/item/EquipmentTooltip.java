package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;

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

    /** Every fixed key of this class. */
    public static final List<String> KEYS = List.of(NAME, NAME_ENHANCED, RARITY_TYPE, ATTACK, DEFENSE, MAX_HEALTH,
            ENHANCEMENT, REQUIRED_LEVEL, BOUND, USABLE_BY, JOB_SEPARATOR);

    private EquipmentTooltip() {
    }

    /**
     * Display name in the rarity colour: "{tier}の{noun}" ({@code 鉄の片手剣}, "Iron Longsword"), prefixed with the
     * enhancement when there is one ({@code +7 炎竜の両手剣}).
     */
    public static TooltipLine name(EquipmentData data) {
        Objects.requireNonNull(data, "data");
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
        if (data.isWeapon()) {
            lines.add(stat(ATTACK, EquipmentStats.baseAttack(data)));
        } else {
            lines.add(stat(DEFENSE, EquipmentStats.baseDefense(data)));
            lines.add(stat(MAX_HEALTH, EquipmentStats.baseMaxHealth(data)));
        }
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
