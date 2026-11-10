package com.eternalrealm.job;

import com.eternalrealm.combat.DamageType;

import java.util.List;

import static com.eternalrealm.combat.WeaponType.BOW;
import static com.eternalrealm.combat.WeaponType.DUAL_BLADES;
import static com.eternalrealm.job.Dsl.*;
import static com.eternalrealm.job.SkillEffect.AfflictionKind.*;
import static com.eternalrealm.job.SkillEffect.BuffKind.*;
import static com.eternalrealm.job.SkillEffect.Placement.*;
import static com.eternalrealm.job.SkillTheme.*;

/**
 * レンジャー: 射撃 (precise bow shots, marks, executions) / 狩猟 (traps, beasts and the twin-blade melee of the hunter)
 * / 弾幕 (volleys, fans and rains of arrows). Bow skills need a bow, the hunter's blade arts the twin blades.
 */
final class RangerSkills {
    private RangerSkills() {
    }

    static List<SkillDefinition> tree() {
        Tree t = new Tree(JobType.RANGER, DamageType.RANGED);

        // ---------------------------------------------------------------- 0: 射撃
        t.active("rapid_shot", 0, 0, 1).innate(1, 1).weapons(BOW).cd(6).theme(WIND)
                .act(18, 14, 15, shot(3, arrow(3.2), 0.9, 0.2), shot(7, arrow(3.2), 0.9, 0.2),
                        shot(11, arrow(3.2), 0.9, 0.2))
                .fx(vis(START, "cast"));
        t.passive("eagle_eye", 0, 1, 1).bonus(stats(mods().ranged(0.03)));
        t.active("aimed_shot", 0, 2, 1).weapons(BOW).cd(7).theme(WIND)
                .act(22, 18, 12, shot(14, arrow(4.2), 2.2, 0.5))
                .fx(vis(START, "cast"), vis(0, "thrust"));

        t.active("piercing_shot", 0, 0, 2).req("rapid_shot", 3).weapons(BOW).cd(9).theme(WIND)
                .act(18, 14, 15, shot(8, arrow(4.0).withPierce(5), 1.8, 0.4))
                .fx(vis(0, "ray"));
        t.passive("steady_aim", 0, 1, 2).req("eagle_eye", 1).bonus(stats(mods().crit(0.015)));
        t.active("crippling_shot", 0, 2, 2).req("aimed_shot", 1).weapons(BOW).cd(10).theme(EARTH)
                .act(16, 12, 14, shot(6, arrow(3.6), 1.5, 0.2))
                .fx(afflict(0, SLOW, 100, 3), afflict(0, WEAKEN, 100, 0.15), vis(START, "cast"));

        t.active("headshot", 0, 0, 3).req("piercing_shot", 3).req("aimed_shot", 3).weapons(BOW).cd(14)
                .theme(BLOOD)
                .act(24, 20, 18, shot(16, arrow(4.6), 2.8, 0.6))
                .fx(execute(0, 0.4, 0.6), vis(START, "cast"), vis(0, "thrust"));
        t.passive("hawk_focus", 0, 1, 3).req("steady_aim", 1).bonus(stats(mods().critDamage(0.06)));
        t.active("mark_prey", 0, 2, 3).req("crippling_shot", 1).weapons(BOW).cd(16).theme(BLOOD)
                .act(16, 12, 15, shot(6, arrow(3.8), 1.2, 0.1))
                .fx(afflict(0, VULNERABLE, 160, 0.2), vis(START, "cast"));

        t.active("power_shot", 0, 0, 4).req("headshot", 1).weapons(BOW).cd(16).theme(WIND)
                .act(26, 22, 22, shot(16, arrow(5.0).withPierce(3), 3.2, 1.6))
                .fx(vis(START, "cast"), vis(0, "ray"));
        t.passive("sniper", 0, 1, 4).req("hawk_focus", 1).bonus(power(0.03));
        t.active("explosive_arrow", 0, 2, 4).req("mark_prey", 1).weapons(BOW).cd(15).theme(FIRE)
                .act(18, 14, 20, shot(7, arrow(3.6).withExplosion(3.0), 2.2, 0.8))
                .fx(afflict(0, BURN, 60, 0), vis(START, "cast"));

        t.ultimate("deadeye", 0, 0, 5).req("power_shot", 3).weapons(BOW).cd(100).theme(WIND)
                .act(36, 32, 34, pause(4), shots(14, 5, 4, arrow(5.0).withPierce(4), 1.6, 0.6))
                .fx(self(0, CRIT_UP, 0.3, 60), vis(START, "cast"), vis(0, "sigil"), vis(4, "ray"));
        t.passive("wind_reader", 0, 1, 5).req("sniper", 1).bonus(stats(mods().moveSpeed(0.01).stamina(3)));
        t.active("thunder_arrow", 0, 2, 5).req("explosive_arrow", 3).weapons(BOW).cd(20).theme(THUNDER)
                .act(20, 16, 24, shot(8, arrow(4.4).withPierce(6), 2.6, 0.4))
                .fx(afflict(0, STUN, 20, 0), vis(START, "cast"), vis(0, "storm"));

        t.ultimate("star_piercer", 0, 0, 6).req("deadeye", 1).weapons(BOW).cd(120).theme(HOLY)
                .act(40, 36, 38, shot(28, orb("star_arrow", 4.0, 40).withPierce(16), 6.0, 2.0))
                .fx(vis(START, "cast"), vis(0, "ray"));
        t.passive("marksman_creed", 0, 1, 6).req("wind_reader", 3)
                .bonus(stats(mods().ranged(0.02).crit(0.01)));
        t.active("chain_volley", 0, 2, 6).req("thunder_arrow", 1).weapons(BOW).cd(22).theme(WIND)
                .act(24, 20, 26, shots(5, 4, 4, arrow(3.6).withFan(2, 16), 0.6, 0.2))
                .fx(vis(START, "cast"), vis(3, "impact"));

        // ---------------------------------------------------------------- 1: 狩猟
        t.active("hunter_slash", 1, 0, 1).weapons(DUAL_BLADES).type(DamageType.PHYSICAL).cd(6).theme(STEEL)
                .act(14, 10, 12, hit(3, arc(3.0, 80), 0.9, 0.2), hit(8, arc(3.0, 80), 0.9, 0.3))
                .fx(vis(0, "slash"), vis(1, "slash"));
        t.passive("survivalist", 1, 1, 1).bonus(stats(mods().healthFlat(3).healthPercent(0.01)));
        t.active("snare_trap", 1, 2, 1).cd(14).theme(EARTH)
                .act(12, 8, 12, pause(4))
                .fx(zone(0, CASTER, 0, 2.5, 20, 1, 1, 1.2, STUN, 40, 0), vis(0, "sigil"));

        t.active("beast_call", 1, 0, 2).req("hunter_slash", 1).cd(20).theme(EARTH)
                .act(14, 10, 12, pause(5))
                .fx(self(0, ATTACK_UP, 0.2, 160), self(0, SPEED, 1, 160), vis(0, "roar"));
        t.passive("camouflage", 1, 1, 2).req("survivalist", 1).bonus(evasion(0.015));
        t.active("venom_trap", 1, 2, 2).req("snare_trap", 1).cd(16).theme(VENOM)
                .act(12, 8, 12, pause(4))
                .fx(zone(0, AHEAD, 3, 3.0, 20, 5, 10, 0.5, POISON, 60, 0.2), vis(0, "sigil"));

        t.active("leap_shot", 1, 0, 3).innate(3, 18).weapons(BOW).cd(12).theme(WIND)
                .act(16, 12, 20, shot(6, arrow(3.5), 1.6, 0.6))
                .fx(retreat(START, 5.0), afflict(0, SLOW, 60, 2), vis(START, "blink"));
        t.passive("tracker", 1, 1, 3).req("camouflage", 1)
                .bonus(stats(mods().moveSpeed(0.01).dodgeCost(0.97)));
        t.active("wild_claws", 1, 2, 3).req("hunter_slash", 3).req("beast_call", 1).weapons(DUAL_BLADES)
                .type(DamageType.PHYSICAL).cd(12).theme(BLOOD)
                .act(22, 18, 18, hits(3, 4, 4, arc(3.2, 90), 0.7, 0.1))
                .fx(afflict(3, BLEED, 100, 0.2), vis(0, "slash"), vis(3, "slash"));

        t.active("hawk_dive", 1, 0, 4).req("leap_shot", 3).weapons(BOW).cd(14).theme(WIND)
                .act(22, 18, 20, shot(12, arrow(4.0).withExplosion(2.5), 2.4, 0.8))
                .fx(leap(START, 2, 3.0), vis(START, "blink"));
        t.passive("natures_blessing", 1, 1, 4).req("tracker", 1)
                .bonus(stats(mods().healing(0.05)), healOnKill(0.008));
        t.active("explosive_trap", 1, 2, 4).req("venom_trap", 3).cd(20).theme(FIRE)
                .act(12, 8, 15, pause(4))
                .fx(zone(0, AHEAD, 4, 3.5, 30, 1, 1, 3.2, LAUNCH, 1, 0.7), vis(0, "sigil"));

        t.active("predator_pounce", 1, 0, 5).req("wild_claws", 3).weapons(DUAL_BLADES).type(DamageType.PHYSICAL)
                .cd(18).theme(EARTH)
                .act(22, 18, 24, hit(10, circle(3.2), 3.0, 1.0))
                .fx(leap(START, 6, 1.5), afflict(0, STUN, 20, 0), vis(0, "impact"), vis(0, "quake"));
        t.passive("apex", 1, 1, 5).req("natures_blessing", 1).bonus(stats(mods().physical(0.02).ranged(0.02)));
        t.active("storm_trap", 1, 2, 5).req("explosive_trap", 1).cd(30).theme(THUNDER)
                .act(14, 10, 20, pause(4))
                .fx(zone(0, AHEAD, 5, 4.5, 20, 6, 6, 0.6, STUN, 10, 0), vis(0, "sigil"));

        t.ultimate("beast_lord", 1, 0, 6).req("predator_pounce", 1).req("hawk_dive", 1).cd(110).theme(EARTH)
                .act(34, 30, 36, hit(6, line(6.0, 1.4), 1.5, 0.4), hits(14, 4, 4, circle(4.0), 1.0, 0.3))
                .fx(dash(START, 6), self(0, ATTACK_UP, 0.3, 160), self(0, LIFESTEAL, 0.15, 160),
                        vis(START, "roar"), vis(0, "blink"), vis(4, "burst"));
        t.passive("wilderness", 1, 1, 6).req("apex", 3).bonus(lastStand(0.04, 0.03));
        t.active("primal_fury", 1, 2, 6).req("predator_pounce", 3).weapons(DUAL_BLADES).type(DamageType.PHYSICAL)
                .cd(24).theme(BLOOD)
                .act(24, 20, 26, hits(4, 4, 4, circle(3.4), 0.8, 0.2))
                .fx(self(START, ATTACK_UP, 0.25, 120), siphon(3, 0.3, 0), vis(0, "spin"), vis(3, "roar"));

        // ---------------------------------------------------------------- 2: 弾幕
        t.active("double_shot", 2, 0, 1).weapons(BOW).cd(4).theme(WIND)
                .act(14, 10, 10, shot(4, arrow(3.4).withFan(2, 8), 0.8, 0.2))
                .fx(vis(START, "cast"));
        t.passive("quiver_mastery", 2, 1, 1).bonus(saver(0.03));
        t.active("scatter_shot", 2, 2, 1).weapons(BOW).cd(8).theme(STEEL)
                .act(14, 10, 12, shot(4, arrow(2.8).withFan(5, 60), 0.4, 0.8))
                .fx(vis(START, "cast"), vis(0, "burst"));

        t.active("spread_shot", 2, 0, 2).innate(2, 8).weapons(BOW).cd(10).theme(WIND)
                .act(16, 12, 20, shot(5, arrow(3.0).withFan(5, 40), 1.0, 0.3))
                .fx(vis(START, "cast"));
        t.passive("rapid_reload", 2, 1, 2).req("quiver_mastery", 1).bonus(cooldown(0.02));
        t.active("fire_volley", 2, 2, 2).req("scatter_shot", 1).weapons(BOW).cd(12).theme(FIRE)
                .act(18, 14, 18, shot(6, arrow(3.2).withFan(3, 24), 0.8, 0.3))
                .fx(afflict(0, BURN, 60, 0), vis(START, "cast"));

        t.active("arrow_storm", 2, 0, 3).req("spread_shot", 3).weapons(BOW).cd(14).theme(WIND)
                .act(26, 22, 20, shots(4, 3, 6, arrow(3.6), 0.5, 0.1))
                .fx(vis(START, "cast"), vis(5, "impact"));
        t.passive("volley_expert", 2, 1, 3).req("rapid_reload", 1).bonus(stats(mods().ranged(0.03)));
        t.active("frost_volley", 2, 2, 3).req("fire_volley", 1).weapons(BOW).cd(14).theme(FROST)
                .act(18, 14, 18, shot(6, arrow(3.2).withFan(4, 36), 0.7, 0.2))
                .fx(afflict(0, SLOW, 80, 2), vis(START, "cast"));

        t.active("cluster_bomb", 2, 0, 4).req("arrow_storm", 1).weapons(BOW).cd(18).theme(FIRE)
                .act(20, 16, 22, shot(7, arrow(3.0).withFan(3, 30).withExplosion(2.2), 1.0, 0.5))
                .fx(vis(START, "cast"));
        t.passive("hail_of_arrows", 2, 1, 4).req("volley_expert", 1)
                .bonus(stats(mods().stamina(5).ranged(0.01)));
        t.active("rain_of_thorns", 2, 2, 4).req("frost_volley", 3).cd(18).theme(BLOOD)
                .act(22, 18, 20, pause(4))
                .fx(zone(0, LOOK, 14, 4.0, 6, 6, 6, 0.5, BLEED, 60, 0.15), vis(0, "rain"));

        t.ultimate("arrow_rain", 2, 0, 5).innate(4, 30).weapons(BOW).cd(90).theme(WIND)
                .act(50, 46, 40, hits(10, 4, 8, ahead(5.0, 10.0), 0.8, 0.1))
                .fx(vis(0, "arrow_rain"), vis(1, "arrow_rain"), vis(2, "arrow_rain"), vis(3, "arrow_rain"),
                        vis(4, "arrow_rain"), vis(5, "arrow_rain"), vis(6, "arrow_rain"), vis(7, "arrow_rain"));
        t.passive("barrage_master", 2, 1, 5).req("hail_of_arrows", 1).bonus(power(0.03));
        t.active("gale_volley", 2, 2, 5).req("rain_of_thorns", 1).weapons(BOW).cd(22).theme(WIND)
                .act(24, 20, 26, shot(6, arrow(4.2).withFan(5, 40).withPierce(3), 0.8, 0.6))
                .fx(self(START, SPEED, 1, 80), vis(START, "cast"));

        t.ultimate("apocalypse_rain", 2, 0, 6).req("arrow_rain", 3).weapons(BOW).cd(120).theme(WIND)
                .act(50, 46, 38, shots(10, 4, 8, arrow(3.0).withFan(3, 40).fallingFromAbove(), 0.3, 0.1))
                .fx(afflict(7, STUN, 20, 0), vis(START, "cast"), vis(0, "rain"), vis(4, "rain"));
        t.passive("endless_quiver", 2, 1, 6).req("barrage_master", 3).bonus(saver(0.02), cooldown(0.015));
        t.active("tempest_arrows", 2, 2, 6).req("gale_volley", 3).weapons(BOW).cd(30).theme(THUNDER)
                .act(30, 26, 30, shots(6, 6, 4, arrow(4.0).withFan(3, 20).withPierce(2), 0.4, 0.3))
                .fx(afflict(3, STUN, 16, 0), vis(START, "cast"), vis(3, "storm"));

        return t.build();
    }
}
