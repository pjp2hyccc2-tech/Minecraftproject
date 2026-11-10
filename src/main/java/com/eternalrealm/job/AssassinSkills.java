package com.eternalrealm.job;

import com.eternalrealm.combat.DamageType;

import java.util.List;

import static com.eternalrealm.job.Dsl.*;
import static com.eternalrealm.job.SkillEffect.AfflictionKind.*;
import static com.eternalrealm.job.SkillEffect.BuffKind.*;
import static com.eternalrealm.job.SkillEffect.Placement.*;
import static com.eternalrealm.job.SkillTheme.*;

/**
 * アサシン: 影 (stealth, dashes, evasion, control) / 毒 (poison, bleeding, damage over time) / 刃 (blade combos,
 * thrown knives, critical hits and executions).
 */
final class AssassinSkills {
    private AssassinSkills() {
    }

    static List<SkillDefinition> tree() {
        Tree t = new Tree(JobType.ASSASSIN, DamageType.PHYSICAL);

        // ---------------------------------------------------------------- 0: 影
        t.active("shadow_step", 0, 0, 1).innate(1, 1).cd(6).theme(SHADOW)
                .act(14, 10, 15, hit(5, circle(2.5), 1.4, 0.2))
                .fx(dash(START, 6.0), vis(START, "shadow"), vis(0, "impact"));
        t.passive("light_feet", 0, 1, 1).bonus(stats(mods().moveSpeed(0.01).dodgeCost(0.97)));
        t.active("vital_strike", 0, 2, 1).cd(7).theme(SHADOW)
                .act(14, 10, 12, hit(5, arc(2.8, 50), 2.0, 0.1))
                .fx(execute(0, 0.5, 0.3), vis(0, "impact"));

        t.active("phantom_dash", 0, 0, 2).req("shadow_step", 3).cd(9).theme(SHADOW)
                .act(16, 12, 14, hit(4, line(5.0, 1.2), 1.2, 0.2), hit(10, circle(2.6), 1.2, 0.3))
                .fx(dash(START, 7), self(1, EVASION, 0.2, 60), vis(START, "blink"), vis(1, "burst"));
        t.passive("shadow_sense", 0, 1, 2).req("light_feet", 1).bonus(evasion(0.015));
        t.active("caltrops", 0, 2, 2).req("vital_strike", 1).cd(14).theme(STEEL)
                .act(12, 8, 12, pause(4))
                .fx(retreat(START, 3), zone(0, CASTER, 0, 3.5, 0, 8, 10, 0.3, SLOW, 40, 1), vis(0, "sigil"));

        t.active("smoke_bomb", 0, 0, 3).innate(3, 18).cd(25).theme(SHADOW)
                .act(12, 8, 10, pause(2))
                .fx(self(0, INVISIBILITY, 0, 80), self(0, SPEED, 1, 80), vanish(0, 10.0), vis(0, "smoke"));
        t.passive("night_veil", 0, 1, 3).req("shadow_sense", 3)
                .bonus(stats(mods().critDamage(0.04).crit(0.01)));
        t.active("shadow_bind", 0, 2, 3).req("caltrops", 1).cd(16).theme(SHADOW)
                .act(16, 12, 16, shot(5, orb("shadow_bolt", 2.4, 20), 1.5, 0.0))
                .fx(afflict(0, STUN, 30, 0), afflict(0, WEAKEN, 100, 0.2), vis(START, "cast"));

        t.active("mirage", 0, 0, 4).req("smoke_bomb", 3).cd(28).theme(SHADOW)
                .act(12, 8, 12, pause(3))
                .fx(self(0, EVASION, 0.4, 100), self(0, SPEED, 1, 100), vanish(0, 12), vis(0, "mist"));
        t.passive("predator", 0, 1, 4).req("night_veil", 1)
                .bonus(stats(mods().physical(0.02).moveSpeed(0.005)));
        t.active("shadow_claw", 0, 2, 4).req("shadow_bind", 1).cd(12).theme(SHADOW)
                .act(18, 14, 18, hits(4, 4, 3, arc(3.2, 80), 0.8, 0.1))
                .fx(afflict(2, BLIND, 60, 0), vis(0, "slash"), vis(2, "slash"));

        t.ultimate("eclipse", 0, 0, 5).req("mirage", 3).cd(100).theme(SHADOW)
                .act(30, 26, 35, pause(4), hits(10, 3, 5, circle(5.0), 1.0, 0.1))
                .fx(vanish(0, 16), self(0, ATTACK_UP, 0.3, 160), vis(START, "mist"), vis(1, "spin"),
                        vis(5, "burst"));
        t.passive("ghost_step", 0, 1, 5).req("predator", 1).bonus(saver(0.03), stats(mods().stamina(4)));
        t.active("abyss_gate", 0, 2, 5).req("shadow_claw", 3).cd(22).theme(SHADOW)
                .act(24, 20, 26, pause(4))
                .fx(zone(0, LOOK, 12, 4.0, 0, 5, 8, 0.6, PULL, 1, 0.35), vis(0, "vortex"));

        t.ultimate("death_mark", 0, 0, 6).req("eclipse", 1).cd(110).theme(BLOOD)
                .act(24, 20, 35, hit(8, arc(3.4, 70), 2.0, 0.2), hit(18, circle(6.0), 4.0, 0.8))
                .fx(afflict(0, VULNERABLE, 160, 0.3), execute(1, 0.35, 1.0), vis(0, "impact"), vis(1, "nova"));
        t.passive("phantom_master", 0, 1, 6).req("ghost_step", 3).bonus(evasion(0.01), cooldown(0.015));
        t.active("void_rend", 0, 2, 6).req("abyss_gate", 1).cd(26).theme(SHADOW)
                .act(26, 22, 30, hits(6, 4, 4, beam(8.0, 1.2), 1.0, 0.2))
                .fx(dash(START, 6), afflict(3, WEAKEN, 120, 0.2), vis(START, "blink"), vis(0, "ray"),
                        vis(3, "burst"));

        // ---------------------------------------------------------------- 1: 毒
        t.active("poison_dart", 1, 0, 1).cd(6).theme(VENOM)
                .act(12, 8, 10, shot(4, orb("poison_dart", 2.6, 20), 1.0, 0.0))
                .fx(afflict(0, POISON, 100, 0.25), vis(START, "cast"));
        t.passive("toxicology", 1, 1, 1).bonus(proc(POISON, 0.03, 80, 0.2, true));
        t.active("rupture", 1, 2, 1).cd(8).theme(BLOOD)
                .act(14, 10, 12, hit(5, arc(3.0, 60), 1.3, 0.1))
                .fx(afflict(0, BLEED, 120, 0.25), vis(0, "slash"));

        t.active("venom_blade", 1, 0, 2).innate(2, 8).cd(12).theme(VENOM)
                .act(18, 14, 20, hit(3, arc(3.0, 60), 0.8, 0.1), hit(7, arc(3.0, 60), 0.8, 0.1),
                        hit(11, arc(3.2, 60), 1.0, 0.3))
                .fx(afflict(2, BLEED, 100, 0.2), afflict(2, POISON, 100, 0.15), vis(0, "slash"), vis(2, "impact"));
        t.active("venom_coat", 1, 1, 2).req("toxicology", 1).cd(30).theme(VENOM)
                .act(12, 8, 10, pause(3))
                .fx(self(0, VENOM_WEAPON, 0, 240), vis(0, "aura"));
        t.active("hemorrhage", 1, 2, 2).req("rupture", 3).cd(12).theme(BLOOD)
                .act(18, 14, 18, hit(4, arc(3.0, 60), 0.9, 0.1), hit(10, arc(3.2, 70), 1.3, 0.2))
                .fx(afflict(1, BLEED, 160, 0.35), vis(0, "slash"), vis(1, "impact"));

        t.active("toxic_cloud", 1, 0, 3).req("venom_blade", 3).cd(18).theme(VENOM)
                .act(14, 10, 15, pause(4))
                .fx(zone(0, AHEAD, 4, 3.5, 0, 6, 10, 0.4, POISON, 60, 0.15), vis(0, "mist"));
        t.passive("virulence", 1, 1, 3).req("venom_coat", 1).bonus(proc(WEAKEN, 0.03, 80, 0.15, false));
        t.active("serpent_fang", 1, 2, 3).req("hemorrhage", 1).cd(12).theme(VENOM)
                .act(16, 12, 16, hit(6, line(5.0, 0.9), 2.2, 0.3))
                .fx(dash(START, 3), afflict(0, POISON, 100, 0.3), afflict(0, SLOW, 60, 1), vis(0, "thrust"));

        t.active("plague_bomb", 1, 0, 4).req("toxic_cloud", 1).cd(20).theme(VENOM)
                .act(18, 14, 20, shot(6, lob("poison_flask", 1.2, 40).withExplosion(3.5), 1.8, 0.4))
                .fx(afflict(0, POISON, 120, 0.3), afflict(0, WEAKEN, 100, 0.15), vis(START, "cast"));
        t.passive("bloodthirst", 1, 1, 4).req("virulence", 1).bonus(lifesteal(0.01));
        t.active("viper_dance", 1, 2, 4).req("serpent_fang", 3).cd(16).theme(VENOM)
                .act(26, 22, 24, hits(4, 4, 5, circle(3.0), 0.6, 0.1))
                .fx(afflict(4, POISON, 100, 0.2), vis(0, "spin"), vis(4, "spin"));

        t.active("pandemic", 1, 0, 5).req("plague_bomb", 3).cd(30).theme(VENOM)
                .act(20, 16, 26, hit(8, circle(6.0), 2.4, 0.5))
                .fx(afflict(0, POISON, 140, 0.35), afflict(0, WEAKEN, 120, 0.2), vis(0, "nova"), vis(0, "mist"));
        t.passive("blood_frenzy", 1, 1, 5).req("bloodthirst", 3).bonus(lastStand(0.05, 0.0));
        t.ultimate("crimson_rain", 1, 2, 5).req("viper_dance", 3).cd(100).theme(BLOOD)
                .act(36, 32, 35, pause(4))
                .fx(zone(0, LOOK, 14, 5.0, 4, 8, 6, 0.8, BLEED, 100, 0.2), vis(START, "cast"), vis(0, "rain"));

        t.ultimate("hydra_venom", 1, 0, 6).req("pandemic", 1).cd(110).theme(VENOM)
                .act(30, 26, 36, shots(6, 6, 3, orb("poison_dart", 2.4, 24).withFan(3, 30), 0.7, 0.1))
                .fx(afflict(0, POISON, 140, 0.3), afflict(1, POISON, 140, 0.3), afflict(2, POISON, 140, 0.3),
                        vis(START, "cast"), vis(2, "mist"));
        t.passive("venom_lord", 1, 1, 6).req("blood_frenzy", 1)
                .bonus(proc(POISON, 0.03, 120, 0.3, false), stats(mods().crit(0.01)));
        t.active("exsanguinate", 1, 2, 6).req("crimson_rain", 1).cd(28).theme(BLOOD)
                .act(22, 18, 30, hit(8, arc(3.4, 80), 3.0, 0.4))
                .fx(siphon(0, 0.4, 0), afflict(0, BLEED, 160, 0.4), vis(0, "impact"));

        // ---------------------------------------------------------------- 2: 刃
        t.active("quick_slash", 2, 0, 1).cd(5).theme(WIND)
                .act(12, 9, 10, hit(3, arc(3.0, 70), 0.8, 0.1), hit(7, arc(3.0, 70), 0.8, 0.2))
                .fx(vis(0, "slash"), vis(1, "slash"));
        t.passive("twin_mastery", 2, 1, 1).bonus(stats(mods().crit(0.015)));
        t.active("throwing_knives", 2, 2, 1).cd(8).theme(STEEL)
                .act(14, 10, 12, shot(4, orb("knife", 2.8, 18).withFan(3, 20), 0.6, 0.05))
                .fx(vis(START, "cast"));

        t.active("cross_cut", 2, 0, 2).req("quick_slash", 3).cd(9).theme(STEEL)
                .act(16, 12, 15, hit(4, arc(3.2, 50), 1.1, 0.1), hit(9, arc(3.2, 50), 1.3, 0.3))
                .fx(vis(0, "slash"), vis(1, "impact"));
        t.passive("keen_edge", 2, 1, 2).req("twin_mastery", 1).bonus(stats(mods().critDamage(0.06)));
        t.active("fan_of_knives", 2, 2, 2).req("throwing_knives", 3).cd(12).theme(STEEL)
                .act(16, 12, 16, shot(5, orb("knife", 2.6, 16).withFan(7, 80), 0.45, 0.05))
                .fx(vis(START, "cast"));

        t.active("assassinate", 2, 0, 3).req("cross_cut", 3).req("vital_strike", 1).cd(14).theme(BLOOD)
                .act(18, 14, 18, hit(7, arc(2.8, 50), 2.4, 0.2))
                .fx(execute(0, 0.3, 1.0), vis(0, "impact"));
        t.passive("adrenaline", 2, 1, 3).req("keen_edge", 3)
                .bonus(stats(mods().stamina(5).physical(0.01)));
        t.active("blade_flurry", 2, 2, 3).req("fan_of_knives", 1).cd(12).theme(WIND)
                .act(24, 20, 18, hits(3, 3, 6, arc(3.2, 90), 0.45, 0.05))
                .fx(vis(0, "spin"), vis(5, "slash"));

        t.active("heart_piercer", 2, 0, 4).req("assassinate", 1).cd(15).theme(BLOOD)
                .act(18, 14, 20, hit(6, line(5.5, 0.8), 2.8, 0.6))
                .fx(dash(START, 4), afflict(0, BLEED, 100, 0.3), vis(0, "thrust"));
        t.passive("killing_spree", 2, 1, 4).req("adrenaline", 1)
                .bonus(stats(mods().crit(0.01)), healOnKill(0.008));
        t.active("dancing_blades", 2, 2, 4).req("blade_flurry", 3).cd(16).theme(WIND)
                .act(26, 22, 24, hits(4, 4, 5, circle(3.4), 0.65, 0.1))
                .fx(self(START, EVASION, 0.25, 40), vis(0, "spin"), vis(4, "burst"));

        t.ultimate("thousand_blades", 2, 0, 5).innate(4, 30).cd(90).theme(STEEL)
                .act(44, 40, 40, hits(6, 3, 10, circle(4.0), 0.75, 0.05))
                .fx(vis(START, "blade_storm"), vis(9, "burst"));
        t.passive("lethal_precision", 2, 1, 5).req("killing_spree", 1)
                .bonus(stats(mods().crit(0.01).critDamage(0.05)));
        t.active("tempest_edge", 2, 2, 5).req("dancing_blades", 1).cd(20).theme(WIND)
                .act(24, 20, 26, hit(6, line(8.0, 1.4), 1.6, 0.3), hit(14, line(8.0, 1.4), 1.6, 0.3))
                .fx(dash(START, 6), dash(0, 4), vis(START, "blink"), vis(1, "ray"));

        t.active("phantom_assault", 2, 0, 6).req("heart_piercer", 3).cd(30).theme(SHADOW)
                .act(30, 26, 32, hits(6, 5, 4, circle(3.0), 0.9, 0.2))
                .fx(dash(START, 5), dash(1, 3), dash(2, 3), execute(3, 0.3, 0.8), vis(0, "blink"),
                        vis(3, "burst"));
        t.passive("blade_saint", 2, 1, 6).req("lethal_precision", 3).bonus(power(0.03));
        t.active("shadow_requiem", 2, 2, 6).req("tempest_edge", 3).cd(34).theme(SHADOW)
                .act(32, 28, 34, pause(4), hits(12, 4, 4, ahead(3.5, 6), 1.0, 0.2))
                .fx(afflict(4, STUN, 30, 0), vis(START, "cast"), vis(1, "pillar"), vis(4, "burst"));

        return t.build();
    }
}
