package com.eternalrealm.job;

import com.eternalrealm.combat.DamageType;

import java.util.List;

import static com.eternalrealm.job.Dsl.*;
import static com.eternalrealm.job.SkillEffect.AfflictionKind.*;
import static com.eternalrealm.job.SkillEffect.BuffKind.*;
import static com.eternalrealm.job.SkillEffect.Placement.*;
import static com.eternalrealm.job.SkillTheme.*;

/**
 * 魔剣士: 魔刃 (arcane slashes, runes, mana drain) / 元素 (flame, frost and thunder blades) / 次元 (warps, rifts,
 * gravity and time). Magic damage carried by the sword (INT also counts as STR for the weapon's own attacks).
 */
final class SpellbladeSkills {
    private SpellbladeSkills() {
    }

    static List<SkillDefinition> tree() {
        Tree t = new Tree(JobType.SPELLBLADE, DamageType.MAGIC);

        // ---------------------------------------------------------------- 0: 魔刃
        t.active("arcane_edge", 0, 0, 1).innate(1, 1).cd(6).theme(ARCANE)
                .act(16, 12, 15, hit(5, arc(3.6, 70), 1.8, 0.5))
                .fx(vis(0, "arcane_slash"));
        t.passive("blade_attunement", 0, 1, 1).bonus(stats(mods().intToPhysical(0.05)));
        t.active("mana_slash", 0, 2, 1).cd(6).theme(ARCANE)
                .act(14, 10, 12, hit(4, arc(3.6, 80), 1.0, 0.2), hit(9, arc(3.6, 80), 1.0, 0.3))
                .fx(vis(0, "slash"), vis(1, "slash"));

        t.active("rune_strike", 0, 0, 2).req("arcane_edge", 3).cd(10).theme(ARCANE)
                .act(18, 14, 16, hit(7, arc(3.4, 60), 2.0, 0.4))
                .fx(afflict(0, VULNERABLE, 100, 0.15), vis(START, "sigil"), vis(0, "impact"));
        t.passive("mana_conduit", 0, 1, 2).req("blade_attunement", 1).bonus(stats(mods().magic(0.03)));
        t.active("spell_parry", 0, 2, 2).req("mana_slash", 1).cd(18).theme(ARCANE)
                .act(12, 8, 10, pause(3))
                .fx(self(0, EVASION, 0.3, 60), self(0, THORNS, 0.3, 60), vis(0, "shield"));

        t.active("mana_wave", 0, 0, 3).innate(3, 18).cd(12).theme(ARCANE)
                .act(18, 14, 20, shot(6, orb("mana_wave", 1.8, 16).withPierce(8), 2.0, 0.5))
                .fx(vis(0, "slash"));
        t.passive("arcane_knowledge", 0, 1, 3).req("mana_conduit", 1).bonus(stats(mods().crit(0.015)));
        t.active("arcane_burst", 0, 2, 3).req("spell_parry", 1).cd(14).theme(ARCANE)
                .act(18, 14, 20, hit(6, circle(4.0), 2.2, 1.0))
                .fx(afflict(0, SLOW, 60, 1), vis(0, "burst"));

        t.active("runic_cascade", 0, 0, 4).req("rune_strike", 3).cd(16).theme(ARCANE)
                .act(26, 22, 22, hits(4, 4, 4, arc(3.8, 90), 0.75, 0.2))
                .fx(afflict(3, VULNERABLE, 100, 0.2), vis(0, "slash"), vis(3, "burst"));
        t.passive("spellsword", 0, 1, 4).req("arcane_knowledge", 1).bonus(power(0.03));
        t.active("mana_drain", 0, 2, 4).req("arcane_burst", 1).cd(18).theme(ARCANE)
                .act(16, 12, 16, hit(6, arc(3.4, 70), 1.8, 0.2))
                .fx(siphon(0, 0.35, 0), stamina(0, 25), vis(0, "vortex"));

        t.active("crescent_moon", 0, 0, 5).req("mana_wave", 3).req("runic_cascade", 1).cd(20).theme(ARCANE)
                .act(22, 18, 26, shot(8, orb("crescent", 1.9, 18).withPierce(10), 3.0, 0.8))
                .fx(vis(0, "slash"));
        t.passive("mana_overflow", 0, 1, 5).req("spellsword", 1).bonus(saver(0.03), stats(mods().stamina(4)));
        t.ultimate("arcane_storm", 0, 2, 5).req("mana_drain", 3).cd(100).theme(ARCANE)
                .act(36, 32, 36, hits(6, 4, 6, circle(5.0), 0.9, 0.3))
                .fx(self(START, ATTACK_UP, 0.2, 100), afflict(5, STUN, 30, 0), vis(0, "spin"), vis(5, "nova"));

        t.ultimate("excalibur", 0, 0, 6).req("crescent_moon", 3).cd(120).theme(HOLY)
                .act(40, 36, 38, pause(8), hit(24, beam(14.0, 2.0), 6.5, 1.6))
                .fx(vis(START, "cast"), vis(0, "pillar"), vis(1, "ray"));
        t.passive("sword_sage", 0, 1, 6).req("mana_overflow", 3).bonus(stats(mods().physical(0.02).magic(0.02)));
        t.active("mana_blade_dance", 0, 2, 6).req("arcane_storm", 1).cd(30).theme(ARCANE)
                .act(32, 28, 32, hits(4, 4, 6, circle(3.6), 0.7, 0.2))
                .fx(dash(START, 3), vis(0, "spin"), vis(5, "burst"));

        // ---------------------------------------------------------------- 1: 元素
        t.active("flame_strike", 1, 0, 1).cd(6).theme(FIRE)
                .act(14, 10, 12, hit(5, arc(3.4, 70), 1.5, 0.4))
                .fx(afflict(0, BURN, 60, 0), vis(0, "slash"));
        t.passive("elemental_affinity", 1, 1, 1).bonus(proc(BURN, 0.03, 40, 0, true));
        t.active("frost_strike", 1, 2, 1).cd(6).theme(FROST)
                .act(14, 10, 12, hit(5, arc(3.4, 70), 1.4, 0.3))
                .fx(afflict(0, SLOW, 60, 2), vis(0, "slash"));

        t.active("flame_blade", 1, 0, 2).innate(2, 8).cd(20).theme(FIRE)
                .act(12, 8, 15, pause(3))
                .fx(self(0, ATTACK_UP, 0.25, 200), self(0, FLAME_WEAPON, 0, 200), vis(0, "flame_blade"));
        t.passive("element_surge", 1, 1, 2).req("elemental_affinity", 1)
                .bonus(stats(mods().magic(0.02).critDamage(0.03)));
        t.active("frost_blade", 1, 2, 2).req("frost_strike", 1).cd(40).theme(FROST)
                .act(12, 8, 15, pause(3))
                .fx(self(0, FROST_WEAPON, 0, 200), self(0, DEFENSE_UP, 0.15, 200), vis(0, "aura"));

        t.active("inferno_slash", 1, 0, 3).req("flame_blade", 3).req("flame_strike", 1).cd(12).theme(FIRE)
                .act(20, 16, 20, hit(8, arc(4.2, 100), 2.4, 0.6))
                .fx(afflict(0, BURN, 100, 0), vis(0, "slash"), vis(0, "burst"));
        t.passive("conductor", 1, 1, 3).req("element_surge", 1).bonus(proc(STUN, 0.02, 12, 0, true));
        t.active("glacier_edge", 1, 2, 3).req("frost_blade", 1).cd(14).theme(FROST)
                .act(20, 16, 20, hit(8, beam(7.0, 1.4), 2.2, 0.4))
                .fx(afflict(0, FREEZE, 80, 0), vis(0, "ray"));

        t.active("thunder_blade", 1, 0, 4).req("inferno_slash", 1).cd(40).theme(THUNDER)
                .act(12, 8, 15, pause(3))
                .fx(self(0, THUNDER_WEAPON, 0, 200), self(0, SPEED, 1, 200), vis(0, "storm"));
        t.passive("elemental_mastery", 1, 1, 4).req("conductor", 1).bonus(power(0.03));
        t.active("frozen_tomb", 1, 2, 4).req("glacier_edge", 3).cd(22).theme(FROST)
                .act(20, 16, 22, hit(8, circle(4.0), 1.8, 0.0))
                .fx(afflict(0, STUN, 40, 0), afflict(0, FREEZE, 100, 0), vis(0, "nova"));

        t.active("lightning_slash", 1, 0, 5).req("thunder_blade", 1).cd(18).theme(THUNDER)
                .act(18, 14, 22, hit(6, line(9.0, 1.2), 2.6, 0.4))
                .fx(dash(START, 8), afflict(0, STUN, 16, 0), vis(START, "blink"), vis(0, "storm"));
        t.passive("storm_heart", 1, 1, 5).req("elemental_mastery", 1)
                .bonus(cooldown(0.02), stats(mods().moveSpeed(0.005)));
        t.ultimate("elemental_fury", 1, 2, 5).req("frozen_tomb", 3).cd(110).theme(ARCANE)
                .act(40, 36, 38, hit(8, arc(4.5, 120), 2.0, 0.4), hit(18, circle(4.5), 2.0, 0.6),
                        hit(28, line(9.0, 1.6), 2.4, 1.0))
                .fx(afflict(0, BURN, 80, 0), afflict(1, FREEZE, 80, 0), afflict(2, STUN, 30, 0), vis(0, "slash"),
                        vis(1, "nova"), vis(2, "storm"));

        t.active("raijin", 1, 0, 6).req("lightning_slash", 3).cd(32).theme(THUNDER)
                .act(30, 26, 34, pause(4), hits(12, 4, 4, circle(6.0, 6), 1.0, 0.2))
                .fx(afflict(4, STUN, 30, 0), vis(1, "storm"), vis(4, "storm"));
        t.passive("avatar_of_elements", 1, 1, 6).req("storm_heart", 3)
                .bonus(stats(mods().magic(0.02).physical(0.02)), proc(FREEZE, 0.02, 40, 0, true));
        t.active("meteor_blade", 1, 2, 6).req("elemental_fury", 1).cd(34).theme(FIRE)
                .act(30, 26, 34, hit(20, ahead(4.0, 3), 4.5, 1.4))
                .fx(leap(START, 4, 3.0), afflict(0, BURN, 100, 0), vis(0, "quake"), vis(0, "burst"));

        // ---------------------------------------------------------------- 2: 次元
        t.active("warp_strike", 2, 0, 1).cd(8).theme(ARCANE)
                .act(14, 10, 14, hit(6, arc(3.4, 80), 1.6, 0.3))
                .fx(dash(START, 5), vis(START, "blink"), vis(0, "slash"));
        t.passive("spatial_sense", 2, 1, 1).bonus(stats(mods().dodgeCost(0.96).moveSpeed(0.005)));
        t.active("gravity_slash", 2, 2, 1).cd(10).theme(ARCANE)
                .act(16, 12, 15, hit(6, circle(4.0), 1.5, 0.0))
                .fx(afflict(0, PULL, 1, 0.5), vis(0, "vortex"));

        t.active("phase_shift", 2, 0, 2).req("warp_strike", 1).cd(18).theme(SHADOW)
                .act(10, 6, 12, pause(2))
                .fx(retreat(START, 6), self(0, EVASION, 0.35, 60), vis(START, "blink"));
        t.passive("time_sense", 2, 1, 2).req("spatial_sense", 1).bonus(cooldown(0.02));
        t.active("rift_blade", 2, 2, 2).req("gravity_slash", 1).cd(12).theme(SHADOW)
                .act(18, 14, 18, shot(6, orb("rift", 2.2, 20).withPierce(4), 2.0, 0.3))
                .fx(vis(0, "slash"));

        t.active("echo_slash", 2, 0, 3).req("phase_shift", 1).cd(14).theme(ARCANE)
                .act(28, 24, 20, hit(5, arc(3.6, 80), 1.2, 0.2), hit(20, arc(3.6, 80), 1.6, 0.4))
                .fx(vis(0, "slash"), vis(1, "burst"));
        t.passive("void_walker", 2, 1, 3).req("time_sense", 1).bonus(evasion(0.012));
        t.active("dimension_gate", 2, 2, 3).req("rift_blade", 1).cd(20).theme(SHADOW)
                .act(18, 14, 22, pause(5))
                .fx(zone(0, LOOK, 12, 3.5, 0, 5, 8, 0.6, PULL, 1, 0.3), vis(0, "vortex"));

        t.active("blink_assault", 2, 0, 4).req("echo_slash", 1).cd(16).theme(ARCANE)
                .act(24, 20, 24, hits(4, 5, 3, line(5.0, 1.2), 0.9, 0.2))
                .fx(dash(START, 5), dash(0, 3), dash(1, 3), vis(0, "blink"), vis(2, "slash"));
        t.passive("chronomancy", 2, 1, 4).req("void_walker", 1).bonus(power(0.02), saver(0.02));
        t.active("time_stop", 2, 2, 4).req("dimension_gate", 3).cd(45).theme(ARCANE)
                .act(20, 16, 26, hit(8, circle(7.0), 1.0, 0.0))
                .fx(afflict(0, STUN, 50, 0), cdcut(0, 80), vis(0, "sigil"), vis(0, "nova"));

        t.ultimate("dimension_slash", 2, 0, 5).innate(4, 30).cd(90).theme(SHADOW)
                .act(36, 32, 40, hit(8, line(8.0, 1.2), 3.0, 0.8), hit(20, line(8.0, 1.2), 3.0, 1.2))
                .fx(dash(START, 8.0), vis(START, "blink"), vis(1, "rift"));
        t.passive("eternity", 2, 1, 5).req("chronomancy", 1).bonus(stats(mods().stamina(5)), lastStand(0.04, 0.0));
        t.active("rewind", 2, 2, 5).req("time_stop", 1).cd(60).theme(ARCANE)
                .act(16, 12, 20, pause(5))
                .fx(heal(0, 0, 0.3, 4), cleanse(0, 0), cdcut(0, 100), vis(0, "vortex"));

        t.ultimate("world_cutter", 2, 0, 6).req("dimension_slash", 3).cd(120).theme(SHADOW)
                .act(44, 40, 38, pause(6), hit(20, beam(16.0, 2.4), 3.5, 0.8), hit(32, beam(16.0, 2.4), 3.5, 1.2))
                .fx(afflict(2, VULNERABLE, 120, 0.25), vis(START, "cast"), vis(1, "ray"), vis(2, "ray"));
        t.passive("dimensional_lord", 2, 1, 6).req("eternity", 3).bonus(cooldown(0.015), power(0.015));
        t.ultimate("collapse", 2, 2, 6).req("rewind", 1).cd(120).theme(SHADOW)
                .act(30, 26, 38, pause(4))
                .fx(zone(0, LOOK, 14, 5.0, 0, 6, 6, 0.4, PULL, 1, 0.4), zone(0, LOOK, 14, 5.0, 40, 1, 1, 4.0, STUN, 30, 0),
                        vis(START, "cast"), vis(0, "vortex"));

        return t.build();
    }
}
