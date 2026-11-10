package com.eternalrealm.job;

import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.HitShape;

import java.util.List;

import static com.eternalrealm.job.Dsl.*;
import static com.eternalrealm.job.SkillEffect.AfflictionKind.*;
import static com.eternalrealm.job.SkillEffect.BuffKind.*;
import static com.eternalrealm.job.SkillEffect.Placement.*;
import static com.eternalrealm.job.SkillTheme.*;

/** メイジ: 炎 (explosions, burning ground) / 氷 (control, freezing, shields) / 雷・秘術 (chains, missiles, gravity). */
final class MageSkills {
    private MageSkills() {
    }

    static List<SkillDefinition> tree() {
        Tree t = new Tree(JobType.MAGE, DamageType.MAGIC);

        // ---------------------------------------------------------------- 0: 炎
        t.active("fireball", 0, 0, 1).innate(1, 1).cd(5).theme(FIRE)
                .act(16, 12, 15, shot(5, orb("fireball", 1.5, 30).withExplosion(2.5), 2.0, 0.6))
                .fx(afflict(0, BURN, 60, 0), vis(START, "cast"));
        t.passive("pyromancy", 0, 1, 1).bonus(stats(mods().magic(0.03)));
        t.active("flame_burst", 0, 2, 1).cd(8).theme(FIRE)
                .act(20, 16, 14, hits(4, 3, 4, arc(4.5, 35), 0.5, 0.05))
                .fx(afflict(3, BURN, 60, 0), vis(0, "breath"), vis(2, "breath"));

        t.active("explosion", 0, 0, 2).req("fireball", 5).cd(12).theme(FIRE)
                .act(20, 16, 20, shot(6, orb("fireball", 1.6, 30).withExplosion(4.0), 2.8, 0.9))
                .fx(afflict(0, BURN, 80, 0), vis(START, "cast"));
        t.passive("kindling", 0, 1, 2).req("pyromancy", 1).bonus(proc(BURN, 0.04, 60, 0, false));
        t.active("fire_wall", 0, 2, 2).req("flame_burst", 1).cd(16).theme(FIRE)
                .act(16, 12, 18, pause(5))
                .fx(zone(0, AHEAD, 5, 3.0, 0, 6, 10, 0.5, BURN, 40, 0), vis(0, "sigil"));

        t.active("magma_orb", 0, 0, 3).req("explosion", 1).cd(14).theme(FIRE)
                .act(22, 18, 22, shot(7, orb("magma", 0.9, 50).withPierce(6), 2.6, 0.4))
                .fx(afflict(0, BURN, 100, 0), afflict(0, SLOW, 40, 1), vis(START, "cast"));
        t.passive("heat_haze", 0, 1, 3).req("kindling", 3).bonus(stats(mods().critDamage(0.05)));
        t.active("flame_pillar", 0, 2, 3).req("fire_wall", 1).cd(15).theme(FIRE)
                .act(26, 22, 22, pause(4), hits(12, 4, 3, ahead(2.5, 7), 0.9, 0.3))
                .fx(afflict(3, LAUNCH, 1, 0.6), afflict(3, BURN, 60, 0), vis(1, "pillar"), vis(3, "burst"));

        t.active("phoenix_bolt", 0, 0, 4).req("magma_orb", 1).cd(18).theme(FIRE)
                .act(20, 16, 24, shot(6, orb("phoenix", 2.2, 30).withPierce(8), 3.0, 0.6))
                .fx(siphon(0, 0.15, 0), afflict(0, BURN, 100, 0), vis(START, "cast"));
        t.passive("combustion", 0, 1, 4).req("heat_haze", 1).bonus(power(0.03));
        t.active("inferno_ring", 0, 2, 4).req("flame_pillar", 3).cd(20).theme(FIRE)
                .act(22, 18, 24, hit(8, circle(5.0), 2.2, 1.0), hit(16, circle(5.0), 1.4, 0.4))
                .fx(afflict(0, BURN, 100, 0), vis(0, "nova"), vis(1, "nova"));

        t.ultimate("meteor", 0, 0, 5).innate(4, 30).cd(100).theme(FIRE)
                .act(40, 36, 40, shot(24, orb("meteor", 1.2, 40).withExplosion(6.0).fallingFromAbove(), 5.0, 1.5))
                .fx(afflict(0, BURN, 100, 0), vis(START, "meteor_cast"));
        t.passive("ember_heart", 0, 1, 5).req("combustion", 1).bonus(saver(0.03), stats(mods().stamina(4)));
        t.active("dragon_breath", 0, 2, 5).req("inferno_ring", 1).cd(26).theme(FIRE)
                .act(30, 26, 30, hits(6, 3, 7, arc(6.0, 30), 0.55, 0.1))
                .fx(afflict(6, BURN, 120, 0), vis(0, "breath"), vis(3, "breath"), vis(6, "breath"));

        t.ultimate("armageddon", 0, 0, 6).req("meteor", 3).cd(120).theme(FIRE)
                .act(44, 40, 38, pause(6), shots(14, 6, 4, orb("meteor", 1.3, 40).withExplosion(3.5).withFan(2, 30)
                        .fallingFromAbove(), 0.9, 0.8))
                .fx(afflict(1, BURN, 100, 0), afflict(4, BURN, 100, 0), vis(START, "cast"), vis(0, "sigil"),
                        vis(2, "rain"));
        t.passive("flame_lord", 0, 1, 6).req("ember_heart", 3)
                .bonus(stats(mods().magic(0.02)), proc(BURN, 0.02, 80, 0, false));
        t.active("sunfire", 0, 2, 6).req("dragon_breath", 3).cd(34).theme(FIRE)
                .act(30, 26, 34, pause(6))
                .fx(zone(0, LOOK, 16, 4.5, 10, 5, 6, 1.0, BURN, 60, 0), vis(START, "cast"), vis(0, "pillar"));

        // ---------------------------------------------------------------- 1: 氷
        t.active("frost_bolt", 1, 0, 1).cd(5).theme(FROST)
                .act(14, 10, 10, shot(4, orb("frost_orb", 2.0, 24), 1.4, 0.2))
                .fx(afflict(0, SLOW, 60, 1), vis(START, "cast"));
        t.passive("cryomancy", 1, 1, 1).bonus(stats(mods().magic(0.02).healthPercent(0.01)));
        t.active("frost_nova", 1, 2, 1).cd(14).theme(FROST)
                .act(16, 12, 16, hit(5, circle(4.5), 1.3, 0.8))
                .fx(afflict(0, FREEZE, 60, 0), vis(0, "nova"));

        t.active("ice_lance", 1, 0, 2).innate(2, 8).cd(8).theme(FROST)
                .act(16, 12, 15, shot(5, orb("ice_lance", 2.2, 20).withPierce(5), 1.7, 0.2))
                .fx(afflict(0, FREEZE, 60, 0), vis(START, "cast"));
        t.passive("permafrost", 1, 1, 2).req("cryomancy", 1).bonus(proc(SLOW, 0.05, 40, 1, false));
        t.active("ice_wall", 1, 2, 2).req("frost_nova", 1).cd(22).theme(FROST)
                .act(14, 10, 15, pause(4))
                .fx(shield(0, 0.3, 160), self(0, DEFENSE_UP, 0.2, 160), vis(0, "shield"));

        t.active("glacial_spike", 1, 0, 3).req("ice_lance", 3).cd(12).theme(FROST)
                .act(20, 16, 20, hit(9, ahead(2.0, 6), 2.6, 0.2))
                .fx(afflict(0, LAUNCH, 1, 0.7), afflict(0, FREEZE, 60, 0), vis(0, "pillar"));
        t.passive("cold_blood", 1, 1, 3).req("permafrost", 1).bonus(stats(mods().crit(0.015)));
        t.active("blizzard", 1, 2, 3).req("ice_wall", 1).cd(20).theme(FROST)
                .act(18, 14, 22, pause(5))
                .fx(zone(0, LOOK, 14, 4.5, 0, 8, 8, 0.45, SLOW, 40, 2), vis(0, "rain"));

        t.active("shatter", 1, 0, 4).req("glacial_spike", 1).cd(16).theme(FROST)
                .act(18, 14, 20, hit(7, circle(4.0, 8), 2.4, 0.4))
                .fx(execute(0, 0.5, 0.4), vis(0, "burst"));
        t.passive("frozen_heart", 1, 1, 4).req("cold_blood", 1).bonus(lastStand(0.0, 0.05));
        t.active("ice_prison", 1, 2, 4).req("blizzard", 1).cd(22).theme(FROST)
                .act(18, 14, 22, shot(6, orb("frost_orb", 1.8, 30).withExplosion(3.0), 1.6, 0.0))
                .fx(afflict(0, STUN, 40, 0), afflict(0, FREEZE, 80, 0), vis(START, "cast"));

        t.ultimate("absolute_zero", 1, 0, 5).req("shatter", 3).cd(110).theme(FROST)
                .act(34, 30, 36, pause(6), hit(20, circle(7.0), 4.0, 0.6))
                .fx(afflict(1, FREEZE, 120, 0), afflict(1, STUN, 40, 0), vis(START, "cast"), vis(0, "sigil"),
                        vis(1, "nova"));
        t.passive("winter_soul", 1, 1, 5).req("frozen_heart", 1).bonus(cooldown(0.02));
        t.active("hail_storm", 1, 2, 5).req("ice_prison", 1).cd(24).theme(FROST)
                .act(30, 26, 26, shots(6, 5, 4, orb("hail", 1.6, 30).withExplosion(2.0).fallingFromAbove(), 0.9, 0.3))
                .fx(afflict(3, SLOW, 60, 2), vis(START, "cast"), vis(1, "rain"));

        t.active("frost_giant", 1, 0, 6).req("absolute_zero", 1).cd(30).theme(FROST)
                .act(28, 24, 32, hit(14, ahead(3.5, 4), 4.2, 1.2))
                .fx(afflict(0, FREEZE, 100, 0), afflict(0, STUN, 30, 0), vis(START, "cast"), vis(0, "quake"));
        t.passive("ice_queen", 1, 1, 6).req("winter_soul", 3)
                .bonus(stats(mods().magic(0.02)), proc(FREEZE, 0.02, 40, 0, false));
        t.ultimate("eternal_winter", 1, 2, 6).req("hail_storm", 3).cd(120).theme(FROST)
                .act(30, 26, 38, pause(6))
                .fx(zone(0, CASTER, 0, 7.0, 0, 10, 8, 0.6, FREEZE, 40, 0), self(0, DEFENSE_UP, 0.25, 200),
                        vis(0, "rain"), vis(0, "nova"));

        // ---------------------------------------------------------------- 2: 雷・秘術
        t.active("spark", 2, 0, 1).cd(4).theme(THUNDER)
                .act(12, 8, 8, shot(3, orb("spark", 2.6, 16), 1.1, 0.1))
                .fx(vis(START, "cast"));
        t.passive("arcane_mind", 2, 1, 1).bonus(stats(mods().stamina(6)));
        t.active("mana_shield", 2, 2, 1).cd(24).theme(ARCANE)
                .act(12, 8, 10, pause(3))
                .fx(shield(0, 0.25, 200), vis(0, "shield"));

        t.active("chain_lightning", 2, 0, 2).req("spark", 3).cd(10).theme(THUNDER)
                .act(18, 14, 18, hit(6, circle(10.0, 4), 1.4, 0.1))
                .fx(afflict(0, STUN, 10, 0), vis(0, "storm"), vis(0, "chains"));
        t.passive("focus", 2, 1, 2).req("arcane_mind", 1).bonus(cooldown(0.02));
        t.active("blink", 2, 2, 2).req("mana_shield", 1).cd(12).theme(ARCANE)
                .act(10, 6, 10, pause(2))
                .fx(dash(START, 8), self(0, EVASION, 0.2, 40), vis(START, "blink"));

        t.active("thunderstorm", 2, 0, 3).innate(3, 18).cd(18).theme(THUNDER)
                .act(24, 20, 25, hit(8, new HitShape(HitShape.Kind.CIRCLE, 12.0, 180.0, 0.0, 4.0, 6.0, 5), 1.5, 0.3),
                        hit(14, new HitShape(HitShape.Kind.CIRCLE, 12.0, 180.0, 0.0, 4.0, 6.0, 5), 1.5, 0.3))
                .fx(vis(0, "lightning"), vis(1, "lightning"));
        t.passive("arcane_flow", 2, 1, 3).req("focus", 1).bonus(saver(0.03));
        t.active("arcane_missiles", 2, 2, 3).req("blink", 1).cd(10).theme(ARCANE)
                .act(26, 22, 20, shots(4, 4, 5, orb("arcane", 2.2, 24), 0.55, 0.05))
                .fx(vis(START, "cast"));

        t.active("thunder_spear", 2, 0, 4).req("thunderstorm", 3).cd(16).theme(THUNDER)
                .act(20, 16, 22, shot(7, orb("thunder", 3.0, 20).withPierce(8), 2.8, 0.5))
                .fx(afflict(0, STUN, 16, 0), vis(START, "cast"));
        t.passive("overload", 2, 1, 4).req("arcane_flow", 1).bonus(power(0.03));
        t.active("gravity_well", 2, 2, 4).req("arcane_missiles", 3).cd(22).theme(ARCANE)
                .act(18, 14, 22, pause(5))
                .fx(zone(0, LOOK, 14, 4.5, 0, 6, 8, 0.4, PULL, 1, 0.3), vis(0, "vortex"));

        t.ultimate("thunder_god", 2, 0, 5).req("thunder_spear", 3).cd(110).theme(THUNDER)
                .act(40, 36, 38, pause(6), hits(14, 5, 5, circle(9.0, 6), 1.2, 0.2))
                .fx(self(0, SPEED, 1, 100), afflict(5, STUN, 30, 0), vis(START, "cast"), vis(1, "storm"),
                        vis(3, "storm"), vis(5, "storm"));
        t.passive("mana_surge", 2, 1, 5).req("overload", 1).bonus(stats(mods().magic(0.02).stamina(3)));
        t.active("time_warp", 2, 2, 5).req("gravity_well", 1).cd(45).theme(ARCANE)
                .act(16, 12, 20, pause(5))
                .fx(cdcut(0, 120), stamina(0, 40), vis(0, "sigil"));

        t.active("ball_lightning", 2, 0, 6).req("thunder_god", 1).cd(26).theme(THUNDER)
                .act(22, 18, 30, shot(8, orb("thunder_orb", 0.6, 60).withPierce(16), 3.2, 0.2))
                .fx(afflict(0, STUN, 10, 0), vis(START, "cast"));
        t.passive("archmage", 2, 1, 6).req("mana_surge", 3).bonus(power(0.02), cooldown(0.01));
        t.ultimate("singularity", 2, 2, 6).req("time_warp", 3).cd(120).theme(ARCANE)
                .act(36, 32, 38, pause(4))
                .fx(zone(0, LOOK, 16, 5.0, 0, 7, 6, 0.5, PULL, 1, 0.45), zone(0, LOOK, 16, 5.0, 44, 1, 1, 3.0),
                        vis(START, "cast"), vis(0, "vortex"));

        return t.build();
    }
}
