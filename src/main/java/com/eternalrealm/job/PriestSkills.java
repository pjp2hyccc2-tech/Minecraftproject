package com.eternalrealm.job;

import com.eternalrealm.combat.DamageType;

import java.util.List;

import static com.eternalrealm.combat.WeaponType.ONE_HANDED_SWORD;
import static com.eternalrealm.job.Dsl.*;
import static com.eternalrealm.job.SkillEffect.AfflictionKind.*;
import static com.eternalrealm.job.SkillEffect.BuffKind.*;
import static com.eternalrealm.job.SkillEffect.Placement.*;
import static com.eternalrealm.job.SkillTheme.*;

/**
 * プリースト: 癒し (heals, regeneration, cleansing) / 加護 (shields, party buffs) / 審判 (holy attack magic: smites from
 * above, light rays, radiant bursts, piercing lances, the judgment ultimate). Holy damage scales with INT and hurts the
 * undead twice as much; several attacks heal the party with the damage they deal.
 */
final class PriestSkills {
    private PriestSkills() {
    }

    static List<SkillDefinition> tree() {
        Tree t = new Tree(JobType.PRIEST, DamageType.HOLY);

        // ---------------------------------------------------------------- 0: 癒し
        t.active("heal", 0, 0, 1).innate(1, 1).cd(8).theme(LIFE)
                .act(20, 16, 15, pause(8))
                .fx(heal(0, 8.0, 0.20, 4.0), vis(0, "heal"));
        t.passive("devotion", 0, 1, 1).bonus(stats(mods().healing(0.05)));
        t.active("renew", 0, 2, 1).cd(12).theme(LIFE)
                .act(14, 10, 10, pause(5))
                .fx(allies(0, REGENERATION, 1, 200, 10), vis(0, "aura"));

        t.active("greater_heal", 0, 0, 2).req("heal", 3).cd(16).theme(LIFE)
                .act(26, 22, 20, pause(14))
                .fx(heal(0, 8, 0.35, 6), vis(START, "cast"), vis(0, "pillar"));
        t.passive("mercy", 0, 1, 2).req("devotion", 1).bonus(stats(mods().healthPercent(0.02).healing(0.02)));
        t.active("cleansing_light", 0, 2, 2).req("renew", 1).cd(14).theme(HOLY)
                .act(14, 10, 12, pause(5))
                .fx(cleanse(0, 10), heal(0, 10, 0.08, 2), vis(0, "aura"));

        t.active("healing_circle", 0, 0, 3).req("greater_heal", 1).cd(22).theme(LIFE)
                .act(16, 12, 18, pause(5))
                .fx(healZone(0, CASTER, 0, 5.0, 8, 10, 0.05, 0.0), vis(0, "sigil"));
        t.passive("benediction", 0, 1, 3).req("mercy", 1).bonus(cooldown(0.02));
        t.active("prayer_of_mending", 0, 2, 3).req("cleansing_light", 1).cd(20).theme(HOLY)
                .act(20, 16, 18, pause(8))
                .fx(heal(0, 12, 0.15, 3), shieldAllies(0, 0.1, 160, 12), vis(0, "aura"));

        t.active("lifebloom", 0, 0, 4).req("healing_circle", 1).cd(26).theme(LIFE)
                .act(18, 14, 20, pause(6))
                .fx(allies(0, REGENERATION, 2, 160, 10), heal(0, 10, 0.1, 0), vis(0, "aura"), vis(0, "burst"));
        t.passive("serenity", 0, 1, 4).req("benediction", 1).bonus(saver(0.03), stats(mods().stamina(4)));
        t.active("spirit_link", 0, 2, 4).req("prayer_of_mending", 1).cd(40).theme(BLOOD)
                .act(16, 12, 15, pause(6))
                .fx(allies(0, LIFESTEAL, 0.15, 200, 10), vis(0, "aura"));

        t.ultimate("sanctuary", 0, 0, 5).innate(4, 30).cd(100).theme(HOLY)
                .act(40, 36, 40, hit(16, circle(6.0), 2.0, 0.8))
                .fx(heal(0, 10.0, 0.5, 10.0), allies(0, DEFENSE_UP, 0.3, 160, 10.0), vis(0, "sanctuary"));
        t.passive("grace", 0, 1, 5).req("serenity", 1).bonus(stats(mods().healing(0.04).healthFlat(3)));
        t.active("guardian_angel", 0, 2, 5).req("spirit_link", 1).cd(60).theme(HOLY)
                .act(20, 16, 24, pause(8))
                .fx(shieldAllies(0, 0.3, 200, 12), allies(0, DEFENSE_UP, 0.15, 200, 12), vis(0, "pillar"),
                        vis(0, "shield"));

        t.ultimate("miracle", 0, 0, 6).req("sanctuary", 3).cd(120).theme(HOLY)
                .act(30, 26, 38, pause(14))
                .fx(heal(0, 16, 0.8, 10), cleanse(0, 16), shieldAllies(0, 0.25, 200, 16), vis(START, "cast"),
                        vis(0, "pillar"), vis(0, "nova"));
        t.passive("saintly", 0, 1, 6).req("grace", 3).bonus(stats(mods().healing(0.03).magic(0.02)));
        t.active("rebirth_hymn", 0, 2, 6).req("guardian_angel", 1).cd(45).theme(LIFE)
                .act(24, 20, 28, pause(10))
                .fx(healZone(0, CASTER, 0, 6.0, 10, 10, 0.06, 0.4), allies(0, SPEED, 0, 200, 12), vis(0, "sigil"),
                        vis(0, "aura"));

        // ---------------------------------------------------------------- 1: 加護
        t.active("protect", 1, 0, 1).cd(20).theme(HOLY)
                .act(14, 10, 12, pause(5))
                .fx(allies(0, DEFENSE_UP, 0.15, 240, 10), vis(0, "shield"));
        t.passive("piety", 1, 1, 1).bonus(stats(mods().healthFlat(3).defense(2)));
        t.active("haste_prayer", 1, 2, 1).cd(24).theme(WIND)
                .act(14, 10, 12, pause(5))
                .fx(allies(0, SPEED, 1, 200, 10), allies(0, EVASION, 0.1, 200, 10), vis(0, "aura"));

        t.active("holy_shield", 1, 0, 2).req("protect", 1).cd(18).theme(HOLY)
                .act(14, 10, 14, pause(5))
                .fx(shieldAllies(0, 0.15, 200, 8), vis(0, "shield"));
        t.passive("inner_light", 1, 1, 2).req("piety", 1).bonus(stats(mods().magic(0.02).healing(0.02)));
        t.active("battle_hymn", 1, 2, 2).req("haste_prayer", 1).cd(30).theme(HOLY)
                .act(16, 12, 15, pause(6))
                .fx(allies(0, ATTACK_UP, 0.15, 240, 10), vis(0, "aura"), vis(0, "roar"));

        t.active("blessing", 1, 0, 3).innate(3, 18).cd(30).theme(HOLY)
                .act(20, 16, 20, pause(8))
                .fx(allies(0, ATTACK_UP, 0.2, 200, 10.0), allies(0, REGENERATION, 0, 200, 10.0), vis(0, "blessing"));
        t.passive("sacred_vow", 1, 1, 3).req("inner_light", 1)
                .bonus(stats(mods().guard(0.02).healthPercent(0.01)));
        t.active("divine_favor", 1, 2, 3).req("battle_hymn", 1).cd(35).theme(HOLY)
                .act(16, 12, 15, pause(6))
                .fx(allies(0, CRIT_UP, 0.12, 240, 10), allies(0, LIFESTEAL, 0.08, 240, 10), vis(0, "aura"));

        t.active("sanctified_ground", 1, 0, 4).req("blessing", 3).cd(30).theme(HOLY)
                .act(18, 14, 20, pause(6))
                .fx(healZone(0, CASTER, 0, 5.0, 6, 20, 0.04, 0.6), allies(0, DEFENSE_UP, 0.2, 160, 6),
                        vis(0, "sigil"));
        t.passive("aegis_of_faith", 1, 1, 4).req("sacred_vow", 1)
                .bonus(stats(mods().defense(3)), lastStand(0.0, 0.04));
        t.active("dispel_evil", 1, 2, 4).req("divine_favor", 1).cd(20).theme(HOLY)
                .act(16, 12, 18, hit(6, circle(6.0), 1.6, 1.0))
                .fx(afflict(0, WEAKEN, 120, 0.2), cleanse(0, 8), vis(0, "nova"));

        t.active("angelic_wings", 1, 0, 5).req("sanctified_ground", 1).cd(40).theme(WIND)
                .act(16, 12, 18, pause(5))
                .fx(leap(START, 4, 2.0), allies(0, SPEED, 2, 160, 10), allies(0, EVASION, 0.2, 160, 10),
                        vis(0, "aura"));
        t.passive("conviction", 1, 1, 5).req("aegis_of_faith", 1).bonus(power(0.02), cooldown(0.01));
        t.ultimate("holy_barrier", 1, 2, 5).req("dispel_evil", 3).cd(100).theme(HOLY)
                .act(24, 20, 34, pause(8))
                .fx(shieldAllies(0, 0.5, 240, 12), allies(0, DEFENSE_UP, 0.3, 240, 12), cleanse(0, 12),
                        vis(START, "cast"), vis(0, "shield"), vis(0, "sigil"));

        t.active("divine_intervention", 1, 0, 6).req("angelic_wings", 1).cd(60).theme(HOLY)
                .act(20, 16, 26, pause(6))
                .fx(allies(0, DEFENSE_UP, 0.5, 80, 12), heal(0, 12, 0.2, 4), vis(0, "pillar"));
        t.passive("high_priest", 1, 1, 6).req("conviction", 3)
                .bonus(stats(mods().healing(0.03).magic(0.02).stamina(3)));
        t.ultimate("ascension", 1, 2, 6).req("holy_barrier", 1).cd(120).theme(HOLY)
                .act(28, 24, 38, pause(10))
                .fx(allies(0, ATTACK_UP, 0.3, 240, 14), allies(0, CRIT_UP, 0.2, 240, 14),
                        allies(0, REGENERATION, 1, 240, 14), heal(0, 14, 0.3, 4), vis(START, "cast"),
                        vis(0, "pillar"), vis(0, "aura"));

        // ---------------------------------------------------------------- 2: 審判 (attack magic)
        t.active("smite", 2, 0, 1).cd(6).theme(HOLY)
                .act(16, 12, 12, shot(6, orb("smite", 1.6, 40).withExplosion(1.8).fallingFromAbove(), 1.8, 0.3))
                .fx(vis(START, "cast"), vis(0, "smite"));
        t.passive("zeal", 2, 1, 1).bonus(stats(mods().magic(0.03)));
        t.active("consecrated_blade", 2, 2, 1).weapons(ONE_HANDED_SWORD).cd(6).theme(HOLY)
                .act(14, 10, 12, hit(5, arc(3.4, 70), 1.6, 0.4))
                .fx(siphon(0, 0.2, 6), vis(0, "slash"));

        t.active("holy_light", 2, 0, 2).innate(2, 8).cd(5).theme(HOLY)
                .act(14, 10, 10, shot(4, orb("holy_light", 1.8, 24), 1.8, 0.4))
                .fx(vis(START, "cast"));
        t.passive("righteousness", 2, 1, 2).req("zeal", 1).bonus(stats(mods().crit(0.015)));
        t.active("radiant_burst", 2, 2, 2).req("zeal", 1).cd(12).theme(HOLY)
                .act(18, 14, 18, hit(7, circle(4.5), 1.8, 1.2))
                .fx(afflict(0, BLIND, 60, 0), vis(0, "nova"));

        t.active("light_ray", 2, 0, 3).req("holy_light", 3).cd(12).theme(HOLY)
                .act(26, 22, 20, hits(6, 3, 6, beam(12.0, 1.0), 0.5, 0.05))
                .fx(vis(0, "ray"), vis(3, "ray"));
        t.passive("wrath", 2, 1, 3).req("righteousness", 1).bonus(power(0.03));
        t.active("holy_lance", 2, 2, 3).req("radiant_burst", 1).cd(14).theme(HOLY)
                .act(20, 16, 20, shot(8, orb("holy_lance", 3.0, 24).withPierce(10), 2.6, 0.8))
                .fx(afflict(0, VULNERABLE, 100, 0.15), vis(START, "cast"));

        t.active("chains_of_light", 2, 0, 4).req("light_ray", 1).cd(18).theme(HOLY)
                .act(18, 14, 20, hit(7, circle(8.0, 5), 1.4, 0.0))
                .fx(afflict(0, STUN, 40, 0), afflict(0, PULL, 1, 0.5), vis(0, "chains"));
        t.passive("purgatory", 2, 1, 4).req("wrath", 1).bonus(proc(BURN, 0.03, 60, 0, false));
        t.active("penance", 2, 2, 4).req("holy_lance", 1).cd(16).theme(HOLY)
                .act(18, 14, 20, shot(6, orb("holy_light", 2.0, 24).withFan(3, 30), 0.9, 0.3))
                .fx(siphon(0, 0.3, 10), vis(START, "cast"));

        t.ultimate("judgment_day", 2, 0, 5).req("chains_of_light", 3).cd(110).theme(HOLY)
                .act(44, 40, 38, pause(6), shots(14, 5, 5, orb("smite", 1.6, 40).withExplosion(2.5).withFan(2, 40)
                        .fallingFromAbove(), 0.7, 0.5))
                .fx(afflict(5, VULNERABLE, 160, 0.25), afflict(5, STUN, 30, 0), vis(START, "cast"),
                        vis(0, "sigil"), vis(3, "smite"));
        t.passive("divine_power", 2, 1, 5).req("purgatory", 1).bonus(stats(mods().magic(0.02).critDamage(0.04)));
        t.active("purifying_flame", 2, 2, 5).req("penance", 1).cd(22).theme(HOLY)
                .act(18, 14, 24, pause(5))
                .fx(zone(0, LOOK, 14, 4.0, 0, 6, 8, 0.6, BURN, 40, 0), vis(0, "pillar"));

        t.active("seraph_wrath", 2, 0, 6).req("judgment_day", 1).cd(30).theme(HOLY)
                .act(30, 26, 32, pause(6), hit(18, ahead(4.0, 6), 4.0, 1.0))
                .fx(afflict(1, LAUNCH, 1, 0.8), afflict(1, BLIND, 80, 0), vis(1, "smite"), vis(1, "nova"));
        t.passive("avatar", 2, 1, 6).req("divine_power", 3).bonus(power(0.02), stats(mods().crit(0.01)));
        t.ultimate("nova_of_light", 2, 2, 6).req("purifying_flame", 3).cd(120).theme(HOLY)
                .act(34, 30, 38, pause(8), hits(16, 6, 3, circle(7.0), 1.6, 1.0))
                .fx(heal(0, 10, 0.2, 4), afflict(3, BLIND, 80, 0), vis(START, "cast"), vis(1, "nova"),
                        vis(2, "nova"), vis(3, "nova"));

        return t.build();
    }
}
