package com.eternalrealm.job.fx;

import com.eternalrealm.job.SkillTheme;
import com.eternalrealm.job.fx.Cue.Anchor;
import com.eternalrealm.job.fx.Cue.Kind;
import com.eternalrealm.job.fx.SkillShow.Screen;

/**
 * The finales (秘奥義, owner 2026-11-15: 「この必殺技はctを90秒にして、アニメーションとエフェクト、視覚的効果や視線のブレなどを
 * 入れて高クオリティで派手なものをつくって」): each is directed from its first tick to its last like a short film, on the
 * skill's own hit frames: the props it holds, the light around them, the moments that shake the view, cinema bars, a
 * zoom, a tint at the edges and the flashes of the big blows, and when the caster's weapon (or the caster) steps aside
 * for a prop or an after-image. The caster's arms follow the finale's choreography ({@code item.ArtChoreography}).
 *
 * <p>The server plays the same moments in particles and sounds ({@code job.forge.FinaleVisuals}) from the skills'
 * {@code Visual} effects, whose kinds are {@link #VISUALS}.</p>
 */
public final class Finales {
    /** The kinds of the finales' {@code Visual} effects (the server's {@code FinaleVisuals} plays every one). */
    public static final java.util.Set<String> VISUALS = java.util.Set.of(
            "fin_eightfold_stance", "fin_eightfold_flash", "fin_eightfold_sheathe", "fin_eightfold_burst",
            "fin_scythe_summon", "fin_scythe_hook", "fin_scythe_reap",
            "fin_holy_sword_call", "fin_holy_sword_raise", "fin_holy_sword_fall",
            "fin_ox_ritual", "fin_ox_nail", "fin_ox_strike",
            "fin_azure_call", "fin_azure_fall", "fin_azure_impact",
            "fin_boneking_gather", "fin_boneking_rise",
            "fin_rifle_form", "fin_rifle_shot", "fin_rifle_unform",
            "fin_spirits_call", "fin_spirits_draw", "fin_spirits_loose", "fin_spirits_wake",
            "fin_giant_sword_grow", "fin_giant_sword_strike",
            "fin_verdict_scales", "fin_verdict_judge", "fin_verdict_execute",
            "fin_aegis_raise", "fin_aegis_ward", "fin_aegis_bash",
            "fin_cathedral_rise", "fin_cathedral_bell", "fin_cathedral_window");
    private static final int WHITE = 0xFFFFFF;

    private Finales() {
    }

    static void add(SkillShows.Gen g) {
        int[] t = g.times;
        switch (g.skill.id()) {
            case "shinobi_eightfold_flash" -> eightfoldFlash(g, t);
            case "reaper_scythe_of_the_end" -> scytheOfTheEnd(g, t);
            case "sword_saint_heavenfall_blade" -> heavenfallBlade(g, t);
            case "hexblade_hour_of_the_ox" -> hourOfTheOx(g, t);
            case "sage_azure_starfall" -> azureStarfall(g, t);
            case "necromancer_advent_of_the_bone_king" -> boneKing(g, t);
            case "sniper_three_magic_bullets" -> threeBullets(g, t);
            case "spirit_archer_bow_of_seven_spirits" -> sevenSpirits(g, t);
            case "paladin_earthsunder" -> earthsunder(g, t);
            case "inquisitor_divine_verdict" -> divineVerdict(g, t);
            case "guardian_adamant_bulwark" -> adamantBulwark(g, t);
            case "bishop_celestial_cathedral" -> celestialCathedral(g, t);
            default -> generic(g, t);
        }
    }

    /** The cinema of a finale: bars for its whole length and a tint at the edges. */
    private static void cinema(SkillShows.Gen g, int ticks, int tint, double vignette) {
        g.screen(0, Screen.Kind.BARS, ticks, 0.11, 0);
        g.screen(0, Screen.Kind.VIGNETTE, ticks, vignette, tint);
    }

    // ------------------------------------------------------------------ 忍者: 八重一閃

    private static void eightfoldFlash(SkillShows.Gen g, int[] t) {
        int stance = t[0];
        int sheathe = t[9];
        int burst = t[10];
        g.put(g.cue(Kind.SIGIL, 0, stance, burst + 8).at(Anchor.ORIGIN).a(7.5).n(8).variant(g.style));
        g.put(g.cue(Kind.FOG, 2, stance, burst + 10).at(Anchor.ORIGIN).a(6.0).n(14));
        g.put(g.cue(Kind.CHARGE, 2, stance, stance + 4).a(0.9));
        g.put(g.cue(Kind.FLASH_CUTS, t[1] - 2, burst, burst + 14).at(Anchor.ORIGIN).a(7.0).n(8).variant(1));
        // the caster is gone from the first flash until the blade is sheathed
        g.hide(t[1] - 2, sheathe - 1, true);
        for (int i = 1; i <= 8; i++) {
            g.shake(t[i], 0.45F, 3, 0.25F);
            g.screen(t[i], Screen.Kind.FLASH, 3, 0.12, 0xD8E8FF);
        }
        g.put(g.cue(Kind.FLASH, sheathe - 1, sheathe, sheathe + 5).a(0.8));
        g.put(g.cue(Kind.GLYPH, sheathe, sheathe + 2, burst).variant(1).a(0.7).c(1.2));
        // the burst
        g.put(g.cue(Kind.BURST, burst, burst, burst + 14).at(Anchor.ORIGIN).a(8.5).n(24));
        g.put(g.cue(Kind.SHOCKWAVE, burst, burst, burst + 16).at(Anchor.ORIGIN).a(9.0).b(1.4).n(3));
        g.put(g.cue(Kind.NOVA, burst, burst, burst + 12).at(Anchor.ORIGIN).a(8.0).n(24));
        for (int i = 0; i < 6; i++) {
            double tilt = 70.0 * Math.sin(i * 2.1 + g.rnd(i) * 3.0);
            g.put(g.cue(Kind.SLASH, burst - 1, burst, burst + 9).at(Anchor.ORIGIN).a(5.5 + g.rnd(i + 9) * 2.0)
                    .b(80.0).c(tilt).variant(i % 2 | 6));
        }
        g.put(g.cue(Kind.FLASH, burst - 1, burst, burst + 8).at(Anchor.ORIGIN).a(3.5));
        g.shake(stance, 0.4F, 6, 0.2F);
        g.shake(burst, 2.4F, 16, 2.0F);
        cinema(g, burst + 8, 0x061428, 0.6);
        g.screen(stance - 4, Screen.Kind.ZOOM, 14, 0.9, 0);
        g.screen(burst, Screen.Kind.FLASH, 10, 0.6, WHITE);
    }

    // ------------------------------------------------------------------ 死神: 終焉の大鎌

    private static void scytheOfTheEnd(SkillShows.Gen g, int[] t) {
        int hook = t[1];
        int reap = t[2];
        g.put(g.cue(Kind.SCYTHE, 0, reap, reap + 16).a(6.5).variant(1));
        g.hide(1, reap + 14, false);
        g.put(g.cue(Kind.ECLIPSE, 0, reap - 4, reap + 20).at(Anchor.ORIGIN).a(3.0).c(11.0));
        g.put(g.cue(Kind.FOG, 2, reap, reap + 20).at(Anchor.ORIGIN).a(8.0).n(16));
        g.put(g.cue(Kind.SIGIL, 0, t[0], reap + 12).at(Anchor.ORIGIN).a(9.5).n(7).variant(g.style)
                .palette(SkillTheme.BLOOD));
        // the hook: souls dragged in
        g.put(g.cue(Kind.SPIN, hook - 2, hook, hook + 8).at(Anchor.ORIGIN).a(8.5).b(1.2).d(0.6).variant(1));
        g.put(g.cue(Kind.MOTES, hook - 2, hook + 4, hook + 12).at(Anchor.ORIGIN).a(9.0).n(48).variant(1));
        // the reaping
        g.put(g.cue(Kind.SPIN, reap - 3, reap, reap + 10).at(Anchor.ORIGIN).a(10.0).b(1.3).d(1.4)
                .palette(SkillTheme.BLOOD));
        g.put(g.cue(Kind.SPIN, reap - 2, reap + 1, reap + 10).at(Anchor.ORIGIN).a(8.0).b(0.9).d(0.8));
        g.put(g.cue(Kind.SHOCKWAVE, reap, reap, reap + 16).at(Anchor.ORIGIN).a(10.5).b(1.2).n(3)
                .palette(SkillTheme.BLOOD));
        g.put(g.cue(Kind.NOVA, reap, reap, reap + 12).at(Anchor.ORIGIN).a(9.0).n(20));
        g.put(g.cue(Kind.MOTES, reap, reap + 2, reap + 18).at(Anchor.ORIGIN).a(10.0).n(40).variant(0));
        g.shake(hook, 0.8F, 6, 0.5F);
        g.shake(reap, 2.6F, 16, 2.2F);
        cinema(g, reap + 18, 0x24020A, 0.65);
        g.screen(6, Screen.Kind.ZOOM, reap - 6, 0.88, 0);
        g.screen(reap, Screen.Kind.FLASH, 9, 0.4, 0x9C1028);
    }

    // ------------------------------------------------------------------ 剣聖: 神剣天墜

    private static void heavenfallBlade(SkillShows.Gen g, int[] t) {
        int call = t[0];
        int fall = t[1];
        g.put(g.cue(Kind.SKY_SIGIL, 0, call, fall + 10).at(Anchor.ORIGIN).a(5.5).c(16.0).n(8).variant(g.style));
        g.put(g.cue(Kind.PILLAR, 0, 6, call + 12).at(Anchor.ORIGIN).a(1.4).b(18.0));
        g.put(g.cue(Kind.GREATBLADE, 2, fall, fall + 16).a(11.0).variant(1));
        g.hide(4, fall + 14, false);
        g.put(g.cue(Kind.HALO, call, call + 4, fall).a(0.5));
        g.put(g.cue(Kind.MOTES, call, fall - 2, fall).a(3.0).n(30).variant(1));
        g.put(g.cue(Kind.SHOCKWAVE, fall, fall, fall + 16).at(Anchor.ORIGIN).a(7.5).b(1.6).n(3).d(5.5));
        g.put(g.cue(Kind.QUAKE, fall, fall, fall + 24).at(Anchor.ORIGIN).a(7.0).n(16).b(5.5));
        g.put(g.cue(Kind.PILLAR, fall, fall, fall + 16).at(Anchor.ORIGIN).a(1.6).b(20.0).d(5.5));
        g.put(g.cue(Kind.NOVA, fall, fall, fall + 12).at(Anchor.ORIGIN).a(6.5).n(24).d(5.5));
        g.put(g.cue(Kind.SPIKES, fall, fall, fall + 18).at(Anchor.ORIGIN).a(5.0).b(2.2).n(16).d(5.5));
        g.put(g.cue(Kind.FLASH, fall - 1, fall, fall + 8).at(Anchor.ORIGIN).a(3.5).b(5.5));
        g.shake(call, 0.5F, 6, 0.2F);
        g.shake(fall, 2.8F, 18, 2.6F);
        cinema(g, fall + 18, 0x2A1C04, 0.45);
        g.screen(call + 2, Screen.Kind.ZOOM, fall - call - 2, 0.85, 0);
        g.screen(fall, Screen.Kind.FLASH, 12, 0.7, 0xFFF4D0);
    }

    // ------------------------------------------------------------------ 呪剣士: 丑の刻参り

    private static void hourOfTheOx(SkillShows.Gen g, int[] t) {
        int last = t[3];
        g.put(g.cue(Kind.SIGIL, 0, t[0], last + 14).at(Anchor.ORIGIN).a(3.2).b(4.5).n(5).variant(g.style));
        g.put(g.cue(Kind.FOG, 0, t[0], last + 16).at(Anchor.ORIGIN).a(4.0).b(4.5).n(10));
        g.put(g.cue(Kind.NAIL, 0, last, last + 14).at(Anchor.ORIGIN).a(2.8).b(4.5).n(3).variant(1));
        g.put(g.cue(Kind.HAMMER, 0, last, last + 12).at(Anchor.ORIGIN).a(1.3).b(4.5).c(2.8).n(3).variant(1));
        for (int i = 1; i <= 2; i++) {
            g.put(g.cue(Kind.IMPACT, t[i], t[i], t[i] + 6).at(Anchor.ORIGIN).a(1.4).b(4.5));
            g.shake(t[i], 0.9F, 6, 0.8F);
            g.screen(t[i], Screen.Kind.FLASH, 4, 0.15, 0xB066FF);
        }
        // the last blow: always a critical hit, and the curse breaks loose
        g.put(g.cue(Kind.NOVA, last, last, last + 12).at(Anchor.ORIGIN).a(5.0).n(20).d(4.5));
        g.put(g.cue(Kind.SHOCKWAVE, last, last, last + 14).at(Anchor.ORIGIN).a(6.0).b(1.0).n(2).d(4.5));
        g.put(g.cue(Kind.SPIKES, last, last, last + 18).at(Anchor.ORIGIN).a(3.5).b(2.0).n(12).d(4.5));
        g.put(g.cue(Kind.CHAINS, last, last + 3, last + 20).at(Anchor.ORIGIN).a(4.5).n(4));
        g.put(g.cue(Kind.GLYPH, last, last + 3, last + 26).at(Anchor.ORIGIN).variant(9).a(1.2).b(4.5).c(1.8));
        g.put(g.cue(Kind.FLASH, last - 1, last, last + 8).at(Anchor.ORIGIN).a(3.5).b(4.5));
        g.shake(last, 2.4F, 16, 2.2F);
        cinema(g, last + 14, 0x1A0428, 0.7);
        g.screen(last - 8, Screen.Kind.ZOOM, 18, 0.86, 0);
        g.screen(last, Screen.Kind.FLASH, 10, 0.5, 0x8A2CFF);
    }

    // ------------------------------------------------------------------ 賢者: 蒼焔隕星

    private static void azureStarfall(SkillShows.Gen g, int[] t) {
        int impact = t[2];
        g.put(g.cue(Kind.CAST, 0, 4, t[0] + 6).a(1.2).variant(g.style));
        g.put(g.cue(Kind.SKY_SIGIL, 0, t[0], impact + 8).at(Anchor.AIM).a(9.0).c(22.0).n(9).variant(g.style));
        g.put(g.cue(Kind.SIGIL, 2, impact, impact + 10).at(Anchor.AIM).a(7.0).n(7).variant(g.style));
        g.put(g.cue(Kind.STAR_ROCK, t[0], impact, impact + 1).at(Anchor.AIM).a(4.2).c(30.0));
        g.put(g.cue(Kind.MOTES, t[1], impact, impact + 2).at(Anchor.AIM).a(7.0).n(30).variant(1));
        // the blast
        g.put(g.cue(Kind.FLASH, impact - 1, impact, impact + 10).at(Anchor.AIM).a(3.5));
        g.put(g.cue(Kind.SHOCKWAVE, impact, impact, impact + 20).at(Anchor.AIM).a(13.0).b(2.0).n(3));
        g.put(g.cue(Kind.NOVA, impact, impact, impact + 16).at(Anchor.AIM).a(10.0).n(28));
        g.put(g.cue(Kind.PILLAR, impact, impact, impact + 20).at(Anchor.AIM).a(3.2).b(22.0));
        g.put(g.cue(Kind.QUAKE, impact, impact, impact + 28).at(Anchor.AIM).a(9.0).n(18));
        g.put(g.cue(Kind.BURST, impact, impact, impact + 12).at(Anchor.AIM).a(9.0).n(32));
        // pale fire burning on where it fell (the burning ground of the skill: six pulses, ten ticks apart)
        g.put(g.cue(Kind.FLAMES, impact, impact + 8, impact + 74).at(Anchor.AIM).a(6.5).n(40));
        g.shake(t[1], 0.5F, 10, 0.2F);
        g.shake(impact, 3.0F, 22, 2.4F);
        cinema(g, impact + 16, 0x04142E, 0.5);
        g.screen(t[0] + 6, Screen.Kind.ZOOM, impact - t[0] + 4, 1.12, 0);
        g.screen(impact, Screen.Kind.FLASH, 14, 0.8, 0xB8EEFF);
    }

    // ------------------------------------------------------------------ 冥術士: 骸王顕現

    private static void boneKing(SkillShows.Gen g, int[] t) {
        int rise = t[1];
        g.put(g.cue(Kind.MANA_STREAMS, 2, rise - 2, rise + 6).at(Anchor.ORIGIN).a(16.0).b(3.5).c(1.6).n(26));
        g.put(g.cue(Kind.SIGIL, 0, t[0], rise + 16).at(Anchor.ORIGIN).a(4.5).b(3.5).n(7).variant(g.style));
        g.put(g.cue(Kind.VORTEX, t[0] + 10, rise - 4, rise + 4).at(Anchor.ORIGIN).a(3.0).b(4.5).d(3.5));
        g.put(g.cue(Kind.CAST, 0, 6, rise).a(1.1).variant(g.style));
        g.put(g.cue(Kind.FLASH, rise - 1, rise, rise + 10).at(Anchor.ORIGIN).a(3.5).b(3.5));
        g.put(g.cue(Kind.SHOCKWAVE, rise, rise, rise + 16).at(Anchor.ORIGIN).a(7.0).b(1.6).n(3).d(3.5));
        g.put(g.cue(Kind.PILLAR, rise, rise, rise + 18).at(Anchor.ORIGIN).a(1.4).b(12.0).d(3.5));
        g.put(g.cue(Kind.QUAKE, rise, rise, rise + 22).at(Anchor.ORIGIN).a(5.0).n(12).b(3.5));
        g.shake(t[0], 0.4F, 10, 0.2F);
        g.shake(rise, 2.2F, 16, 1.8F);
        cinema(g, rise + 16, 0x0C0418, 0.6);
        g.screen(rise - 16, Screen.Kind.ZOOM, 34, 1.12, 0);
        g.screen(rise, Screen.Kind.FLASH, 10, 0.45, 0x9A5CFF);
    }

    // ------------------------------------------------------------------ 狙撃手: 魔弾三連

    private static void threeBullets(SkillShows.Gen g, int[] t) {
        int form = t[0];
        int unform = t[4];
        g.put(g.cue(Kind.RIFLE, 1, form + 6, unform + 8).a(1.95).n(3).variant(1));
        g.hide(1, unform + 8, false);
        g.put(g.cue(Kind.SIGIL, 0, form + 6, unform + 10).at(Anchor.ORIGIN).a(2.6).n(6).variant(g.style));
        g.put(g.cue(Kind.RETICLE, form + 6, t[1], t[3] + 6).a(0.9).b(10.0));
        g.put(g.cue(Kind.WEAPON, 0, form, form + 10).a(1.0));
        g.put(g.cue(Kind.MOTES, 0, form + 4, form + 12).a(1.2).n(30).variant(1));
        for (int i = 1; i <= 3; i++) {
            boolean last = i == 3; // the third round hits hardest (and stuns longest)
            g.put(g.cue(Kind.TRACER, t[i], t[i], t[i] + 12).frame(i).a(56.0));
            g.shake(t[i], last ? 2.0F : 1.4F, last ? 9 : 7, last ? 3.0F : 2.4F);
            g.screen(t[i], Screen.Kind.FLASH, last ? 7 : 4, last ? 0.4 : 0.22, 0xFFE2A8);
        }
        g.put(g.cue(Kind.MOTES, unform, unform + 4, unform + 12).a(1.2).n(24).variant(0));
        // the scope: the view closes in, dark at the edges, while the rifle is up
        g.screen(form + 8, Screen.Kind.ZOOM, unform - form - 8, 0.62, 0);
        g.screen(form + 8, Screen.Kind.VIGNETTE, unform - form - 8, 0.75, 0x000000);
    }

    // ------------------------------------------------------------------ 精霊弓士: 七精霊の神弓

    private static void sevenSpirits(SkillShows.Gen g, int[] t) {
        int loose = t[2];
        g.put(g.cue(Kind.SPIRITS, 0, loose, loose + 1).a(2.6).n(7));
        g.put(g.cue(Kind.SIGIL, 0, t[0], loose + 10).at(Anchor.ORIGIN).a(3.5).n(7).variant(g.style));
        g.put(g.cue(Kind.GREAT_BOW, t[0], loose, loose + 16).a(3.2));
        g.hide(t[0], loose + 12, false);
        g.put(g.cue(Kind.MOTES, t[1], loose - 2, loose).a(2.0).n(36).variant(1));
        g.put(g.cue(Kind.SKY_SIGIL, 0, t[0], loose + 10).at(Anchor.ORIGIN).a(4.0).c(7.0).n(7).variant(g.style));
        g.put(g.cue(Kind.FLASH, loose - 1, loose, loose + 7).frame(2).a(3.5).b(1.5));
        g.put(g.cue(Kind.SHOCKWAVE, loose, loose, loose + 14).frame(2).a(5.0).b(0.8).n(2));
        g.put(g.cue(Kind.ROAR, t[3] - 2, t[3], t[3] + 12).frame(3).a(16.0).n(5));
        g.put(g.cue(Kind.BEAM, t[3] - 1, t[3], t[3] + 10).frame(3).a(18.0).b(1.4));
        g.shake(loose, 1.9F, 10, 1.8F);
        g.shake(t[3], 0.9F, 8, 0.4F);
        cinema(g, loose + 14, 0x100822, 0.45);
        g.screen(t[1] - 4, Screen.Kind.ZOOM, loose - t[1] + 8, 0.84, 0);
        g.screen(loose, Screen.Kind.FLASH, 9, 0.55, 0xFFFFFF);
    }

    // ------------------------------------------------------------------ 聖騎士: 天地断つ聖剣

    private static void earthsunder(SkillShows.Gen g, int[] t) {
        int strike = t[1];
        g.put(g.cue(Kind.GREATBLADE, 0, strike, strike + 18).a(9.0).variant(0));
        g.hide(1, strike + 16, false);
        g.put(g.cue(Kind.HELIX, 0, t[0], strike).a(1.0).b(3.0).n(3));
        g.put(g.cue(Kind.SKY_SIGIL, 0, t[0], strike + 6).at(Anchor.ORIGIN).a(4.5).c(12.0).n(8).variant(g.style));
        // the strike and the rift it leaves (the holy field: ten pulses, ten ticks apart, from six ticks after it)
        g.put(g.cue(Kind.FISSURE, strike, strike + 4, strike + 6 + 100).frame(1).a(14.0).b(0.55).d(1.5));
        g.put(g.cue(Kind.QUAKE, strike, strike, strike + 26).frame(1).a(8.0).n(18).b(6.0));
        g.put(g.cue(Kind.SHOCKWAVE, strike, strike, strike + 18).frame(1).a(9.0).b(1.8).n(3).d(6.0));
        g.put(g.cue(Kind.NOVA, strike, strike, strike + 12).frame(1).a(7.0).n(24).d(6.0));
        g.put(g.cue(Kind.PILLAR, strike, strike, strike + 18).frame(1).a(0.5).b(10.0).n(5).c(4.5).d(8.0));
        g.put(g.cue(Kind.FLASH, strike - 1, strike, strike + 9).frame(1).a(3.5).b(6.0));
        g.shake(t[0], 0.5F, 8, 0.3F);
        g.shake(strike, 3.0F, 20, 2.8F);
        cinema(g, strike + 20, 0x281804, 0.45);
        g.screen(t[0] - 4, Screen.Kind.ZOOM, strike - t[0] + 6, 0.86, 0);
        g.screen(strike, Screen.Kind.FLASH, 12, 0.7, 0xFFE8A0);
    }

    // ------------------------------------------------------------------ 審問官: 神判

    private static void divineVerdict(SkillShows.Gen g, int[] t) {
        int verdict = t[1];
        int execute = t[2];
        g.put(g.cue(Kind.SCALES, 0, verdict, execute + 12).at(Anchor.ORIGIN).a(2.6).b(3.5).c(6.2).variant(1));
        g.put(g.cue(Kind.SKY_SIGIL, 0, t[0], execute + 10).at(Anchor.ORIGIN).a(12.0).c(14.0).n(9).variant(g.style));
        g.put(g.cue(Kind.SIGIL, 0, t[0], execute + 10).at(Anchor.ORIGIN).a(12.0).n(9).variant(g.style));
        g.put(g.cue(Kind.NOVA, verdict, verdict, verdict + 12).at(Anchor.ORIGIN).a(12.0).n(24));
        g.put(g.cue(Kind.CHAINS, verdict, verdict + 4, execute).at(Anchor.ORIGIN).a(6.0).n(4));
        g.put(g.cue(Kind.HALO, t[0], t[0] + 4, execute + 8).a(0.55));
        g.put(g.cue(Kind.MOTES, verdict, verdict + 6, execute + 6).at(Anchor.ORIGIN).a(10.0).n(36).variant(0));
        g.put(g.cue(Kind.SHOCKWAVE, execute, execute, execute + 16).at(Anchor.ORIGIN).a(12.5).b(1.6).n(3));
        g.put(g.cue(Kind.FLASH, execute - 1, execute, execute + 10).at(Anchor.ORIGIN).a(3.5));
        g.shake(verdict, 1.2F, 8, 0.8F);
        g.shake(execute, 2.4F, 14, 2.0F);
        cinema(g, execute + 14, 0x241A06, 0.45);
        g.screen(2, Screen.Kind.ZOOM, execute + 4, 1.1, 0);
        g.screen(verdict, Screen.Kind.FLASH, 6, 0.35, 0xFFE070);
        g.screen(execute, Screen.Kind.FLASH, 11, 0.6, WHITE);
    }

    // ------------------------------------------------------------------ 守護士: 金剛不壊

    private static void adamantBulwark(SkillShows.Gen g, int[] t) {
        int ward = t[0];
        int bash = t[1];
        g.put(g.cue(Kind.AEGIS, 0, bash, bash + 14).a(4.4).variant(0));
        g.put(g.cue(Kind.PILLAR, ward - 4, ward, ward + 16).at(Anchor.ORIGIN).a(0.6).b(12.0).n(6).c(4.0));
        g.put(g.cue(Kind.DOME, ward, ward + 4, ward + 30).a(3.0));
        g.put(g.cue(Kind.HALO, ward, ward + 3, bash + 10).a(0.55));
        g.put(g.cue(Kind.AURA, ward, ward + 4, ward + 26).a(16.0).variant(2));
        g.put(g.cue(Kind.SHOCKWAVE, ward, ward, ward + 16).at(Anchor.ORIGIN).a(16.0).b(1.0).n(2));
        // the bash
        g.put(g.cue(Kind.SHOCKWAVE, bash, bash, bash + 16).frame(1).a(8.5).b(1.6).n(3).d(3.0));
        g.put(g.cue(Kind.NOVA, bash, bash, bash + 12).frame(1).a(6.0).n(20).d(3.5));
        g.put(g.cue(Kind.IMPACT, bash, bash, bash + 8).frame(1).a(2.0).b(3.5));
        g.put(g.cue(Kind.FLASH, bash - 1, bash, bash + 8).frame(1).a(3.5).b(3.5));
        g.shake(ward, 1.3F, 8, 0.8F);
        g.shake(bash, 2.6F, 16, 2.4F);
        cinema(g, bash + 14, 0x241C06, 0.4);
        g.screen(ward, Screen.Kind.FLASH, 7, 0.35, 0xFFD870);
        g.screen(bash - 10, Screen.Kind.ZOOM, 16, 0.88, 0);
        g.screen(bash, Screen.Kind.FLASH, 9, 0.5, 0xFFF2C0);
    }

    // ------------------------------------------------------------------ 司教: 天上の大聖堂

    private static void celestialCathedral(SkillShows.Gen g, int[] t) {
        int window = t[4];
        g.put(g.cue(Kind.CATHEDRAL, 0, window, window + 22).at(Anchor.ORIGIN).a(9.5).b(9.0).n(10).variant(1));
        g.put(g.cue(Kind.SIGIL, 0, t[0], window + 16).at(Anchor.ORIGIN).a(10.0).n(10).variant(g.style));
        g.put(g.cue(Kind.CAST, 0, 6, t[0] + 4).a(1.0).variant(g.style));
        for (int i = 1; i <= 3; i++) {
            g.put(g.cue(Kind.HEAL, t[i], t[i] + 3, t[i] + 16).a(9.0));
            g.shake(t[i], 0.7F, 6, 0.3F);
            g.screen(t[i], Screen.Kind.FLASH, 4, 0.12, 0xFFF4D0);
        }
        g.put(g.cue(Kind.NOVA, window, window, window + 14).at(Anchor.ORIGIN).a(10.0).n(28));
        g.put(g.cue(Kind.WINGS, window, window + 4, window + 24).a(2.4));
        g.put(g.cue(Kind.FLASH, window - 1, window, window + 10).at(Anchor.ORIGIN).a(3.5));
        g.shake(window, 1.8F, 12, 1.0F);
        cinema(g, window + 18, 0x261C08, 0.35);
        g.screen(4, Screen.Kind.ZOOM, window + 8, 1.14, 0);
        g.screen(window, Screen.Kind.FLASH, 12, 0.65, WHITE);
    }

    /** A finale without its own script yet: a flash and a shake on its last hit (never shown for the twelve). */
    private static void generic(SkillShows.Gen g, int[] t) {
        int last = t.length == 0 ? g.action.durationTicks() / 2 : t[t.length - 1];
        g.put(g.cue(Kind.NOVA, last, last, last + 12).a(6.0).n(20));
        g.put(g.cue(Kind.FLASH, last - 1, last, last + 8).a(3.5));
        g.shake(last, 1.6F, 10, 1.2F);
    }
}
