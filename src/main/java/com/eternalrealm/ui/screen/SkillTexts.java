package com.eternalrealm.ui.screen;

import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.PassiveEffect;
import com.eternalrealm.job.PassiveTotals;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.SkillEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a skill's data into the lines of the skill tree's hover details: damage, hits, cooldown, stamina, weapons,
 * every effect and passive bonus with its numbers at a given level. Nothing here decides anything; the numbers come
 * from the same definitions the server uses.
 */
final class SkillTexts {
    private static final String P = "skill_info.eternalrealm.";

    private SkillTexts() {
    }

    /** Damage, cooldown, stamina and weapon lines of an active at {@code level} (≥ 1). */
    static List<Component> combat(SkillDefinition skill, int level, PassiveTotals passive) {
        List<Component> lines = new ArrayList<>();
        if (!skill.isUsable()) {
            return lines;
        }
        int shown = Math.max(1, level);
        double power = skill.totalMultiplier() * skill.damageScale(shown) * (1.0 + passive.skillPower()) * 100.0;
        if (skill.hitCount() > 0) {
            lines.add(Component.translatable(P + "damage", Component.translatable(skill.damageType().translationKey()),
                    number(power), skill.hitCount()).withStyle(ChatFormatting.WHITE));
        }
        double cooldown = skill.cooldownTicks(shown) * (1.0 - passive.cooldownReduction()) / 20.0;
        double stamina = skill.action().staminaCost() * (1.0 - passive.staminaReduction());
        lines.add(Component.translatable(P + "cooldown", number(cooldown), number(stamina))
                .withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable(P + "weapons", weapons(skill)).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    /** What the next level adds. */
    static Component next(SkillDefinition skill, int level) {
        if (skill.isPassive()) {
            return Component.translatable(P + "next_passive").withStyle(ChatFormatting.GREEN);
        }
        int next = Math.min(skill.maxLevel(), level + 1);
        return Component.translatable(P + "next", number((skill.damageScale(next) - skill.damageScale(Math.max(1, level)))
                        * 100.0), number((skill.cooldownTicks(Math.max(1, level)) - skill.cooldownTicks(next)) / 20.0),
                number((skill.effectScale(next) - skill.effectScale(Math.max(1, level))) * 100.0))
                .withStyle(ChatFormatting.GREEN);
    }

    /** Effect lines of an active at {@code level} (≥ 1). */
    static List<Component> effects(SkillDefinition skill, int level) {
        List<Component> lines = new ArrayList<>();
        double scale = skill.effectScale(Math.max(1, level));
        for (SkillEffect effect : skill.effects()) {
            Component line = effect(effect, scale);
            if (line != null) {
                lines.add(Component.literal("・").append(line).withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    /** Bonus lines of a passive at {@code level} (≥ 1). */
    static List<Component> passive(SkillDefinition skill, int level) {
        List<Component> lines = new ArrayList<>();
        for (PassiveEffect effect : skill.passives()) {
            for (Component line : passive(effect, Math.max(1, level))) {
                lines.add(Component.literal("・").append(line).withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    private static Component effect(SkillEffect effect, double scale) {
        if (effect instanceof SkillEffect.Heal heal) {
            return heal.radius() > 0.0
                    ? Component.translatable(P + "effect.heal", number(Math.min(100.0, heal.fraction() * scale * 100.0)),
                    number(heal.flat() * scale), number(heal.radius()))
                    : Component.translatable(P + "effect.heal_self",
                    number(Math.min(100.0, heal.fraction() * scale * 100.0)), number(heal.flat() * scale));
        }
        if (effect instanceof SkillEffect.Buff buff) {
            Component name = Component.translatable(buff.kind().translationKey());
            String value = switch (buff.kind()) {
                case SPEED, REGENERATION -> " Lv" + ((int) Math.round(buff.magnitude()) + 1);
                case INVISIBILITY, FLAME_WEAPON, FROST_WEAPON, VENOM_WEAPON, THUNDER_WEAPON -> "";
                default -> " +" + number(buff.magnitude() * scale * 100.0) + "%";
            };
            return buff.target() == SkillEffect.BuffTarget.SELF
                    ? Component.translatable(P + "effect.buff_self", name, value, seconds(buff.durationTicks()))
                    : Component.translatable(P + "effect.buff_allies", number(buff.radius()), name, value,
                    seconds(buff.durationTicks()));
        }
        if (effect instanceof SkillEffect.Shield shield) {
            String amount = number(shield.fraction() * scale * 100.0);
            return shield.radius() > 0.0
                    ? Component.translatable(P + "effect.shield_allies", number(shield.radius()), amount,
                    seconds(shield.durationTicks()))
                    : Component.translatable(P + "effect.shield", amount, seconds(shield.durationTicks()));
        }
        if (effect instanceof SkillEffect.Dash dash) {
            return Component.translatable(P + (dash.backwards() ? "effect.retreat" : "effect.dash"),
                    number(dash.distance()));
        }
        if (effect instanceof SkillEffect.Leap leap) {
            return Component.translatable(P + "effect.leap", number(leap.distance()), number(leap.height()));
        }
        if (effect instanceof SkillEffect.Taunt taunt) {
            return Component.translatable(P + "effect.taunt", number(taunt.radius()), seconds(taunt.durationTicks()));
        }
        if (effect instanceof SkillEffect.DropAggro drop) {
            return Component.translatable(P + "effect.drop_aggro", number(drop.radius()));
        }
        if (effect instanceof SkillEffect.Afflict afflict) {
            return affliction(afflict.kind(), afflict.durationTicks(), afflict.power(), scale);
        }
        if (effect instanceof SkillEffect.Cleanse cleanse) {
            return cleanse.radius() > 0.0 ? Component.translatable(P + "effect.cleanse", number(cleanse.radius()))
                    : Component.translatable(P + "effect.cleanse_self");
        }
        if (effect instanceof SkillEffect.Siphon siphon) {
            return siphon.radius() > 0.0
                    ? Component.translatable(P + "effect.siphon", number(siphon.fraction() * 100.0),
                    number(siphon.radius()))
                    : Component.translatable(P + "effect.siphon_self", number(siphon.fraction() * 100.0));
        }
        if (effect instanceof SkillEffect.Zone zone) {
            return zone(zone, scale);
        }
        if (effect instanceof SkillEffect.RestoreStamina restore) {
            return Component.translatable(P + "effect.stamina", number(restore.amount()));
        }
        if (effect instanceof SkillEffect.CooldownCut cut) {
            return Component.translatable(P + "effect.cooldown_cut", seconds(cut.ticks()));
        }
        if (effect instanceof SkillEffect.Execute execute) {
            return Component.translatable(P + "effect.execute", number(execute.threshold() * 100.0),
                    number(execute.bonus() * 100.0));
        }
        return null; // visuals
    }

    private static Component affliction(SkillEffect.AfflictionKind kind, int ticks, double power, double scale) {
        Component name = Component.translatable(kind.translationKey());
        String duration = seconds((int) Math.round(ticks * scale));
        return switch (kind) {
            case BLEED, POISON -> Component.translatable(P + "effect.afflict_dot", name, duration,
                    number(power * scale * 100.0));
            case WEAKEN, VULNERABLE -> Component.translatable(P + "effect.afflict_power", name, duration,
                    number(Math.min(60.0, power * scale * 100.0)));
            case SLOW -> Component.translatable(P + "effect.afflict_level", name, duration, (int) Math.round(power) + 1);
            case PULL, LAUNCH -> Component.translatable(P + "effect.afflict_instant", name);
            default -> Component.translatable(P + "effect.afflict", name, duration);
        };
    }

    private static Component zone(SkillEffect.Zone zone, double scale) {
        Component where = switch (zone.placement()) {
            case CASTER -> Component.translatable(P + "placement.caster");
            case AHEAD -> Component.translatable(P + "placement.ahead", number(zone.distance()));
            case LOOK -> Component.translatable(P + "placement.look");
        };
        MutableComponent line;
        if (zone.damageMultiplier() > 0.0) {
            line = Component.translatable(P + "effect.zone", where, number(zone.radius()), zone.pulses(),
                    seconds(zone.intervalTicks()), number(zone.damageMultiplier() * 100.0));
            if (zone.healFraction() > 0.0) {
                line.append(Component.translatable(P + "effect.zone_and_heal",
                        number(zone.healFraction() * scale * 100.0)));
            }
        } else {
            line = Component.translatable(P + "effect.zone_heal", where, number(zone.radius()), zone.pulses(),
                    seconds(zone.intervalTicks()), number(zone.healFraction() * scale * 100.0));
        }
        if (zone.affliction() != null) {
            line.append(Component.literal(" ")).append(affliction(zone.affliction(), zone.afflictionTicks(),
                    zone.afflictionPower(), scale));
        }
        if (zone.delayTicks() > 0) {
            line.append(Component.translatable(P + "effect.zone_delay", seconds(zone.delayTicks())));
        }
        return line;
    }

    private static List<Component> passive(PassiveEffect effect, int level) {
        List<Component> lines = new ArrayList<>();
        if (effect instanceof PassiveEffect.Stats stats) {
            CombatModifiers m = stats.perLevel().scaled(level);
            percent(lines, "physical", m.physicalBonus());
            percent(lines, "ranged", m.rangedBonus());
            percent(lines, "magic", m.magicBonus());
            percent(lines, "healing", m.healingBonus());
            flat(lines, "health_flat", m.maxHealthFlat());
            percent(lines, "health_percent", m.maxHealthPercent());
            flat(lines, "defense", m.defenseFlat());
            percent(lines, "crit", m.critChance());
            percent(lines, "crit_damage", m.critDamage());
            percent(lines, "move_speed", m.moveSpeed());
            percent(lines, "guard", m.guardBonus());
            if (m.dodgeCostFactor() < 1.0) {
                lines.add(Component.translatable(P + "passive.dodge_cost", number((1.0 - m.dodgeCostFactor()) * 100.0)));
            }
            percent(lines, "int_to_physical", m.intToPhysical());
            flat(lines, "stamina", m.staminaBonus());
        } else if (effect instanceof PassiveEffect.SkillPower power) {
            lines.add(Component.translatable(P + "passive.skill_power", number(power.perLevel() * level * 100.0)));
        } else if (effect instanceof PassiveEffect.Cooldown cooldown) {
            lines.add(Component.translatable(P + "passive.cooldown", number(cooldown.perLevel() * level * 100.0)));
        } else if (effect instanceof PassiveEffect.StaminaSaver saver) {
            lines.add(Component.translatable(P + "passive.stamina_saver", number(saver.perLevel() * level * 100.0)));
        } else if (effect instanceof PassiveEffect.Lifesteal lifesteal) {
            lines.add(Component.translatable(P + "passive.lifesteal", number(lifesteal.perLevel() * level * 100.0)));
        } else if (effect instanceof PassiveEffect.Proc proc) {
            lines.add(Component.translatable(P + (proc.meleeOnly() ? "passive.proc_melee" : "passive.proc_any"),
                    number(Math.min(100.0, proc.chancePerLevel() * level * 100.0)),
                    Component.translatable(proc.kind().translationKey()), seconds(proc.durationTicks())));
        } else if (effect instanceof PassiveEffect.LastStand last) {
            if (last.damagePerLevel() > 0.0) {
                lines.add(Component.translatable(P + "passive.last_stand_damage",
                        number(last.damagePerLevel() * level * 100.0)));
            }
            if (last.reductionPerLevel() > 0.0) {
                lines.add(Component.translatable(P + "passive.last_stand_reduction",
                        number(last.reductionPerLevel() * level * 100.0)));
            }
        } else if (effect instanceof PassiveEffect.Thorns thorns) {
            lines.add(Component.translatable(P + "passive.thorns", number(thorns.perLevel() * level * 100.0)));
        } else if (effect instanceof PassiveEffect.Evasion evasion) {
            lines.add(Component.translatable(P + "passive.evasion", number(evasion.perLevel() * level * 100.0)));
        } else if (effect instanceof PassiveEffect.HealOnKill heal) {
            lines.add(Component.translatable(P + "passive.heal_on_kill", number(heal.perLevel() * level * 100.0)));
        }
        return lines;
    }

    private static void percent(List<Component> lines, String key, double value) {
        if (value != 0.0) {
            lines.add(Component.translatable(P + "passive." + key, number(value * 100.0)));
        }
    }

    private static void flat(List<Component> lines, String key, double value) {
        if (value != 0.0) {
            lines.add(Component.translatable(P + "passive." + key, number(value)));
        }
    }

    /** "片手剣/両手剣" or "職業の全武器". */
    static Component weapons(SkillDefinition skill) {
        if (skill.weapons().isEmpty()) {
            return Component.translatable(P + "any_job_weapon");
        }
        MutableComponent text = Component.empty();
        boolean first = true;
        for (WeaponType type : WeaponType.values()) {
            if (skill.weapons().contains(type)) {
                if (!first) {
                    text.append("/");
                }
                text.append(Component.translatable(type.translationKey()));
                first = false;
            }
        }
        return text;
    }

    static String seconds(int ticks) {
        return number(ticks / 20.0);
    }

    /** {@code 12} for whole numbers, otherwise one decimal. */
    static String number(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }
}
