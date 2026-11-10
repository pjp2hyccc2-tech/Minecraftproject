package com.eternalrealm.job;

import com.eternalrealm.combat.DamageType;

import java.util.List;

import static com.eternalrealm.combat.WeaponType.SPEAR;
import static com.eternalrealm.job.Dsl.*;
import static com.eternalrealm.job.SkillEffect.AfflictionKind.*;
import static com.eternalrealm.job.SkillEffect.BuffKind.*;
import static com.eternalrealm.job.SkillEffect.Placement.*;
import static com.eternalrealm.job.SkillTheme.*;

/**
 * ナイト: 守護 (shields, taunts, party protection) / 武技 (sword, greatsword and spear arts, stuns) / 聖騎士 (holy
 * strikes, auras, healing). Physical damage; the paladin lances and smites use the knight's weapon power.
 */
final class KnightSkills {
    private KnightSkills() {
    }

    static List<com.eternalrealm.job.SkillDefinition> tree() {
        Tree t = new Tree(JobType.KNIGHT, DamageType.PHYSICAL);

        // ---------------------------------------------------------------- 0: 守護
        t.active("guard_stance", 0, 0, 1).cd(18).theme(STEEL)
                .act(12, 8, 10, pause(3))
                .fx(self(0, DEFENSE_UP, 0.25, 120), self(0, THORNS, 0.15, 120), vis(0, "shield"));
        t.passive("toughness", 0, 1, 1).bonus(stats(mods().healthPercent(0.03)));
        t.active("provoke", 0, 2, 1).cd(8).theme(STEEL)
                .act(14, 10, 12, hit(4, arc(3.6, 60), 1.2, 0.3))
                .fx(taunt(0, 10, 120), afflict(0, WEAKEN, 100, 0.15), vis(0, "roar"));

        t.active("war_cry", 0, 0, 2).innate(2, 8).cd(20).theme(STEEL)
                .act(20, 16, 10, pause(6))
                .fx(taunt(0, 12.0, 120), self(0, DEFENSE_UP, 0.3, 160), vis(0, "war_cry"));
        t.passive("bulwark", 0, 1, 2).req("toughness", 1).bonus(stats(mods().defense(4)));
        t.active("shield_charge", 0, 2, 2).req("provoke", 1).cd(10).theme(STEEL)
                .act(16, 12, 18, hit(6, circle(2.6), 1.4, 1.2))
                .fx(dash(START, 6), afflict(0, STUN, 20, 0), vis(START, "blink"), vis(0, "impact"));

        t.active("iron_wall", 0, 0, 3).innate(3, 18).cd(30).theme(EARTH)
                .act(14, 10, 20, pause(4))
                .fx(shield(0, 0.3, 200), self(0, DEFENSE_UP, 0.5, 120), allies(0, DEFENSE_UP, 0.2, 120, 6.0),
                        vis(0, "iron_wall"));
        t.passive("guardian_oath", 0, 1, 3).req("bulwark", 3)
                .bonus(stats(mods().guard(0.02).healthFlat(2)));
        t.active("rampart", 0, 2, 3).req("war_cry", 3).cd(30).theme(EARTH)
                .act(16, 12, 15, pause(6))
                .fx(allies(0, DEFENSE_UP, 0.2, 160, 8), taunt(0, 10, 100), vis(0, "sigil"), vis(0, "aura"));

        t.active("retaliation", 0, 0, 4).req("iron_wall", 3).cd(25).theme(BLOOD)
                .act(12, 8, 10, pause(3))
                .fx(self(0, THORNS, 0.35, 200), self(0, DEFENSE_UP, 0.15, 200), vis(0, "shield"));
        t.passive("second_wind", 0, 1, 4).req("guardian_oath", 1)
                .bonus(healOnKill(0.01), stats(mods().stamina(4)));
        t.active("challenge", 0, 2, 4).req("rampart", 1).cd(20).theme(STEEL)
                .act(18, 14, 18, hit(8, circle(4.5), 1.6, 0.5))
                .fx(taunt(0, 16, 160), afflict(0, VULNERABLE, 120, 0.15), vis(0, "nova"));

        t.ultimate("fortress", 0, 0, 5).req("iron_wall", 5).cd(90).theme(EARTH)
                .act(24, 20, 30, pause(10))
                .fx(shield(0, 0.6, 240), allies(0, DEFENSE_UP, 0.3, 240, 10), taunt(0, 16, 200),
                        vis(START, "cast"), vis(0, "quake"), vis(0, "shield"));
        t.passive("unbreakable", 0, 1, 5).req("second_wind", 1)
                .bonus(stats(mods().healthFlat(5).defense(2)));
        t.active("protector", 0, 2, 5).req("challenge", 3).cd(35).theme(HOLY)
                .act(16, 12, 18, pause(6))
                .fx(shieldAllies(0, 0.2, 200, 8), taunt(0, 10, 100), vis(0, "aura"));

        t.ultimate("aegis", 0, 0, 6).req("fortress", 1).cd(120).theme(HOLY)
                .act(20, 16, 30, pause(6))
                .fx(self(0, DEFENSE_UP, 0.9, 80), shield(0, 1.0, 200), taunt(0, 20, 200),
                        allies(0, REGENERATION, 1, 200, 10), vis(START, "cast"), vis(0, "pillar"), vis(0, "shield"));
        t.passive("last_bastion", 0, 1, 6).req("unbreakable", 3).bonus(lastStand(0.0, 0.06));
        t.active("shield_wall", 0, 2, 6).req("protector", 1).cd(22).theme(STEEL)
                .act(24, 20, 25, hits(4, 6, 3, line(4.5, 1.4), 1.0, 1.0))
                .fx(dash(START, 4), allies(2, DEFENSE_UP, 0.15, 120, 6), vis(0, "thrust"), vis(2, "roar"));

        // ---------------------------------------------------------------- 1: 武技
        t.active("shield_bash", 1, 0, 1).innate(1, 1).cd(8).theme(STEEL)
                .act(16, 12, 15, hit(4, arc(3.4, 70), 1.6, 1.0))
                .fx(afflict(0, SLOW, 60, 2), vis(0, "slam_small"));
        t.active("cleave", 1, 1, 1).cd(6).theme(STEEL)
                .act(14, 10, 12, hit(5, arc(4.0, 150), 1.4, 0.7))
                .fx(vis(0, "slash"));
        t.passive("swordsmanship", 1, 2, 1).bonus(stats(mods().physical(0.03)));

        t.active("heavy_slam", 1, 0, 2).req("shield_bash", 3).cd(10).theme(EARTH)
                .act(20, 16, 18, hit(10, circle(3.4), 2.2, 0.8))
                .fx(afflict(0, STUN, 20, 0), vis(0, "quake"));
        t.active("rising_slash", 1, 1, 2).req("cleave", 1).cd(8).theme(WIND)
                .act(16, 12, 15, hit(5, arc(3.4, 70), 1.7, 0.2))
                .fx(afflict(0, LAUNCH, 1, 0.8), vis(0, "slash"));
        t.active("lance_thrust", 1, 2, 2).req("swordsmanship", 1).weapons(SPEAR).cd(9).theme(STEEL)
                .act(16, 12, 16, hit(6, line(6.5, 1.0), 1.9, 0.9))
                .fx(dash(START, 5), vis(START, "blink"), vis(0, "thrust"));

        t.active("armor_break", 1, 0, 3).req("heavy_slam", 3).cd(12).theme(STEEL)
                .act(18, 14, 18, hit(7, arc(3.4, 70), 1.9, 0.5))
                .fx(afflict(0, VULNERABLE, 120, 0.2), vis(0, "impact"));
        t.active("whirl_guard", 1, 1, 3).req("rising_slash", 3).cd(12).theme(WIND)
                .act(22, 18, 20, hit(5, circle(3.6), 1.2, 0.4), hit(12, circle(3.6), 1.2, 0.6))
                .fx(self(START, DEFENSE_UP, 0.15, 80), vis(0, "spin"), vis(1, "spin"));
        t.passive("battle_hardened", 1, 2, 3).req("swordsmanship", 3)
                .bonus(stats(mods().crit(0.015).critDamage(0.05)));

        t.active("earthshaker", 1, 0, 4).req("armor_break", 3).cd(16).theme(EARTH)
                .act(22, 18, 24, hit(10, beam(9.0, 1.6), 2.6, 0.6))
                .fx(afflict(0, SLOW, 80, 2), vis(0, "quake"));
        t.active("foresight_cut", 1, 1, 4).req("whirl_guard", 1).cd(10).theme(STEEL)
                .act(16, 12, 15, hit(7, arc(3.8, 80), 2.1, 0.8))
                .fx(self(START, EVASION, 0.3, 40), vis(0, "slash"));
        t.active("spear_sweep", 1, 2, 4).req("lance_thrust", 3).req("battle_hardened", 1).weapons(SPEAR).cd(14)
                .theme(WIND)
                .act(26, 22, 22, hits(5, 6, 3, circle(4.4), 0.9, 0.4))
                .fx(vis(0, "spin"), vis(2, "spin"));

        t.ultimate("titan_strike", 1, 0, 5).req("earthshaker", 3).cd(90).theme(EARTH)
                .act(36, 32, 35, hit(22, circle(6.0), 4.5, 1.6))
                .fx(leap(START, 4, 2.5), afflict(0, STUN, 50, 0), vis(START, "cast"), vis(0, "quake"), vis(0, "nova"));
        t.passive("relentless", 1, 1, 5).req("foresight_cut", 1).bonus(power(0.03));
        t.active("impale", 1, 2, 5).req("spear_sweep", 1).weapons(SPEAR).cd(16).theme(BLOOD)
                .act(20, 16, 22, hit(8, line(7.5, 1.0), 2.8, 1.0))
                .fx(afflict(0, BLEED, 100, 0.25), vis(0, "thrust"));

        t.ultimate("grand_cross", 1, 0, 6).req("titan_strike", 1).cd(100).theme(HOLY)
                .act(40, 36, 38, hit(10, line(9.0, 1.4), 2.4, 0.5), hit(20, arc(5.0, 180), 2.4, 0.5),
                        hit(30, circle(5.5), 3.0, 1.4))
                .fx(vis(START, "cast"), vis(0, "ray"), vis(1, "spin"), vis(2, "pillar"),
                        afflict(2, VULNERABLE, 100, 0.2));
        t.passive("war_master", 1, 1, 6).req("relentless", 3).bonus(cooldown(0.02), saver(0.03));
        t.active("dragon_lance", 1, 2, 6).req("impale", 3).weapons(SPEAR).cd(24).theme(WIND)
                .act(26, 22, 30, hit(10, line(11.0, 1.2), 3.4, 1.4))
                .fx(dash(START, 8), afflict(0, LAUNCH, 1, 0.9), vis(START, "blink"), vis(0, "ray"));

        // ---------------------------------------------------------------- 2: 聖騎士
        t.active("holy_strike", 2, 0, 1).cd(7).theme(HOLY)
                .act(14, 10, 14, hit(5, arc(3.4, 70), 1.6, 0.5))
                .fx(siphon(0, 0.25, 0), vis(0, "impact"));
        t.passive("faith", 2, 1, 1).bonus(stats(mods().healing(0.05).healthPercent(0.01)));
        t.active("consecrate", 2, 2, 1).cd(20).theme(HOLY)
                .act(14, 10, 15, pause(5))
                .fx(healZone(0, CASTER, 0, 4.0, 6, 20, 0.03, 0.5), vis(0, "sigil"));

        t.active("lay_on_hands", 2, 0, 2).req("holy_strike", 1).req("faith", 1).cd(25).theme(LIFE)
                .act(18, 14, 15, pause(8))
                .fx(heal(0, 6, 0.22, 3), cleanse(0, 6), vis(0, "aura"));
        t.active("divine_shield", 2, 1, 2).req("faith", 1).cd(30).theme(HOLY)
                .act(16, 12, 15, pause(6))
                .fx(shieldAllies(0, 0.15, 160, 8), allies(0, REGENERATION, 0, 160, 8), vis(0, "shield"));
        t.active("judgment_hammer", 2, 2, 2).req("consecrate", 1).cd(12).theme(HOLY)
                .act(18, 14, 18, shot(6, orb("smite", 1.6, 40).withExplosion(2.6).fallingFromAbove(), 1.9, 0.6))
                .fx(afflict(0, STUN, 14, 0), vis(START, "cast"), vis(0, "smite"));

        t.active("aura_of_valor", 2, 0, 3).req("lay_on_hands", 1).cd(40).theme(HOLY)
                .act(16, 12, 15, pause(6))
                .fx(allies(0, ATTACK_UP, 0.15, 240, 10), allies(0, CRIT_UP, 0.1, 240, 10), vis(0, "aura"),
                        vis(0, "roar"));
        t.passive("holy_vigor", 2, 1, 3).req("divine_shield", 1).bonus(stats(mods().healthPercent(0.02)));
        t.active("radiant_wave", 2, 2, 3).req("judgment_hammer", 3).cd(12).theme(HOLY)
                .act(18, 14, 20, shot(6, orb("light_wave", 1.9, 16).withPierce(6), 2.2, 0.5))
                .fx(afflict(0, BLIND, 60, 0), vis(0, "slash"));

        t.active("purify", 2, 0, 4).req("aura_of_valor", 1).cd(30).theme(HOLY)
                .act(16, 12, 15, pause(6))
                .fx(cleanse(0, 10), heal(0, 10, 0.12, 2), allies(0, DEFENSE_UP, 0.1, 160, 10), vis(0, "pillar"));
        t.passive("martyr", 2, 1, 4).req("holy_vigor", 3).bonus(thorns(0.06));
        t.active("sacred_spear", 2, 2, 4).req("radiant_wave", 1).cd(15).theme(HOLY)
                .act(18, 14, 22, shot(7, orb("holy_lance", 2.6, 24).withPierce(4), 2.6, 0.8))
                .fx(afflict(0, VULNERABLE, 100, 0.15), vis(START, "cast"));

        t.ultimate("holy_judgment", 2, 0, 5).innate(4, 30).cd(90).theme(HOLY)
                .act(36, 32, 40, hit(18, circle(6.0), 4.0, 1.5))
                .fx(vis(START, "judgment_charge"), vis(0, "judgment"));
        t.passive("blessed_armor", 2, 1, 5).req("martyr", 1).bonus(stats(mods().defense(4).guard(0.01)));
        t.active("heavens_wrath", 2, 2, 5).req("sacred_spear", 3).cd(22).theme(HOLY)
                .act(24, 20, 28, shot(10, orb("smite", 1.4, 40).withExplosion(2.4).withFan(3, 24)
                        .fallingFromAbove(), 1.5, 0.6))
                .fx(vis(START, "cast"), vis(0, "smite"));

        t.active("resurgence", 2, 0, 6).req("purify", 3).cd(60).theme(HOLY)
                .act(24, 20, 30, pause(10))
                .fx(heal(0, 12, 0.35, 4), cleanse(0, 12), allies(0, REGENERATION, 1, 200, 12), vis(START, "cast"),
                        vis(0, "pillar"), vis(0, "aura"));
        t.passive("crusader", 2, 1, 6).req("blessed_armor", 3)
                .bonus(stats(mods().physical(0.02)), power(0.02));
        t.active("divine_lance", 2, 2, 6).req("heavens_wrath", 1).cd(28).theme(HOLY)
                .act(26, 22, 32, shot(12, orb("holy_lance", 3.2, 28).withPierce(10), 3.6, 1.4))
                .fx(afflict(0, LAUNCH, 1, 0.7), vis(START, "cast"), vis(0, "ray"));

        return t.build();
    }
}
