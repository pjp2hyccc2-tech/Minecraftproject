package com.eternalrealm.job.fx;

import com.eternalrealm.job.SkillTheme;
import com.eternalrealm.job.fx.Cue.Anchor;
import com.eternalrealm.job.fx.Cue.Kind;

/**
 * The grand finale of every ultimate (必殺技), on top of what its frames and effects already draw: each has its own
 * centrepiece (walls of a fortress, a giant sword of judgment, an eclipse, a hydra, a sky of falling stars, a great
 * cross, a tear through the world...) timed to the skill's real hits, and the moments that shake the view.
 */
final class Ultimates {
    private Ultimates() {
    }

    static void add(SkillShows.Gen g) {
        int[] t = g.times;
        switch (g.skill.id()) {
            // ------------------------------------------------------------ knight
            case "knight_fortress" -> {
                int f = t[0];
                g.put(g.cue(Kind.WALLS, f - 2, f + 3, f + 36).at(Anchor.ORIGIN).a(3.2).b(3.4).n(8));
                g.put(g.cue(Kind.PILLAR, f - 4, f, f + 16).at(Anchor.ORIGIN).a(0.45).b(6.0).n(4).c(3.2));
                g.put(g.cue(Kind.GLYPH, f, f + 4, f + 32).variant(2).a(1.1).c(1.3));
                g.put(g.cue(Kind.SHOCKWAVE, f, f, f + 18).at(Anchor.ORIGIN).a(7.0).b(1.2).n(3));
                g.shake(f, 1.6F, 10, 1.4F);
            }
            case "knight_aegis" -> {
                int f = t[0];
                g.put(g.cue(Kind.SKY_SIGIL, 0, f, f + 26).at(Anchor.ORIGIN).a(4.2).c(10.0).n(8).variant(g.style));
                g.put(g.cue(Kind.PILLAR, f - 4, f, f + 22).a(1.6).b(16.0));
                g.put(g.cue(Kind.WINGS, f, f + 6, f + 32).a(2.6));
                g.put(g.cue(Kind.HALO, f - 2, f + 2, f + 32).a(0.5));
                g.put(g.cue(Kind.DOME, f, f + 4, f + 34).a(2.8));
                g.put(g.cue(Kind.FLASH, f - 1, f, f + 8).a(4.0));
                g.shake(f, 1.2F, 8, 0.8F);
            }
            case "knight_titan_strike" -> {
                int f = t[0];
                g.put(g.cue(Kind.GIANT_SWORD, f - 14, f, f + 20).frame(0).a(9.0).b(1.6).palette(SkillTheme.HOLY));
                g.put(g.cue(Kind.QUAKE, f, f, f + 26).frame(0).a(8.0).n(14).b(1.6));
                g.put(g.cue(Kind.SPIKES, f, f, f + 18).frame(0).a(6.0).b(2.4).n(18).d(1.6));
                g.put(g.cue(Kind.SHOCKWAVE, f, f, f + 16).frame(0).a(9.5).b(1.6).n(3).d(1.6));
                g.put(g.cue(Kind.FLASH, f - 1, f, f + 8).frame(0).a(5.0).b(1.6));
                g.shake(f, 2.4F, 14, 2.2F);
            }
            case "knight_grand_cross" -> {
                g.put(g.cue(Kind.CROSS, t[0], t[2], t[2] + 26).frame(2).a(10.0).b(0.8));
                g.put(g.cue(Kind.SKY_SIGIL, 0, t[2], t[2] + 20).at(Anchor.ORIGIN).a(5.0).c(12.0).n(8).variant(g.style));
                g.put(g.cue(Kind.FLASH, t[2] - 1, t[2], t[2] + 8).frame(2).a(5.0).b(2.5));
                g.put(g.cue(Kind.PILLAR, t[2] - 2, t[2], t[2] + 18).frame(2).a(0.5).b(10.0).n(4).c(4.0).d(2.5));
                g.shake(t[0], 0.6F, 5, 0.4F);
                g.shake(t[2], 1.8F, 12, 1.6F);
            }
            case "knight_holy_judgment" -> {
                int f = t[0];
                g.put(g.cue(Kind.SKY_SIGIL, 0, f - 2, f + 22).at(Anchor.ORIGIN).a(5.5).c(14.0).n(8).d(2.5)
                        .variant(g.style));
                g.put(g.cue(Kind.GIANT_SWORD, f - 12, f, f + 26).frame(0).a(11.0).b(2.5));
                g.put(g.cue(Kind.NOVA, f, f, f + 16).frame(0).a(7.0).n(22).d(2.5));
                g.put(g.cue(Kind.PILLAR, f, f, f + 16).frame(0).a(1.2).b(14.0).d(2.5));
                g.put(g.cue(Kind.SPIKES, f, f, f + 16).frame(0).a(5.0).b(2.0).n(16).d(2.5));
                g.put(g.cue(Kind.FLASH, f - 1, f, f + 8).frame(0).a(6.0).b(2.5));
                g.shake(f, 2.4F, 14, 2.0F);
            }
            // ------------------------------------------------------------ assassin
            case "assassin_eclipse" -> {
                int last = t[t.length - 1];
                g.put(g.cue(Kind.ECLIPSE, 0, t[1] - 2, last + 18).a(3.2).c(8.0));
                g.put(g.cue(Kind.FOG, 0, t[0], last + 12).a(5.0).n(14));
                for (int i = 1; i < t.length; i++) {
                    g.put(g.cue(Kind.SLASH, t[i] - 2, t[i], t[i] + 7).frame(i).a(4.6).b(80.0).c(i % 2 == 0 ? 58.0 : -48.0)
                            .variant(i % 2 == 0 ? 2 : 3));
                }
                g.put(g.cue(Kind.BURST, last, last, last + 10).frame(t.length - 1).a(6.0).n(24));
                g.put(g.cue(Kind.FLASH, last - 1, last, last + 8).frame(t.length - 1).a(4.0));
                g.shake(last, 1.6F, 10, 1.2F);
            }
            case "assassin_death_mark" -> {
                g.put(g.cue(Kind.SLASH, t[0] - 3, t[0], t[0] + 10).frame(0).a(4.2).b(70.0).c(-46.0).variant(7));
                g.put(g.cue(Kind.SLASH, t[0] - 3, t[0], t[0] + 10).frame(0).a(4.2).b(70.0).c(46.0).variant(6));
                g.put(g.cue(Kind.GLYPH, t[0], t[0] + 2, t[1] + 6).frame(0).variant(9).a(2.4).b(2.8).c(0.1));
                g.put(g.cue(Kind.SIGIL, t[0], t[0] + 3, t[1] + 8).frame(0).a(2.6).b(2.8).n(5).variant(g.style));
                g.put(g.cue(Kind.SPIKES, t[1], t[1], t[1] + 16).frame(1).a(6.0).b(2.0).n(20));
                g.put(g.cue(Kind.BURST, t[1], t[1], t[1] + 12).frame(1).a(5.0).n(24));
                g.put(g.cue(Kind.FLASH, t[1] - 1, t[1], t[1] + 8).frame(1).a(4.0));
                g.shake(t[1], 1.4F, 10, 1.2F);
            }
            case "assassin_crimson_rain" -> {
                g.put(g.cue(Kind.ECLIPSE, 0, t[0] + 4, t[0] + 58).at(Anchor.AIM).a(2.6).c(13.0));
                g.put(g.cue(Kind.SKY_SIGIL, 0, t[0] + 4, t[0] + 58).at(Anchor.AIM).a(6.5).c(12.0).n(8).variant(g.style));
                g.shake(t[0] + 4, 0.7F, 6, 0.4F);
            }
            case "assassin_hydra_venom" -> {
                int last = t[t.length - 1];
                g.put(g.cue(Kind.HYDRA, 0, t[0], last + 16).n(3).a(1.5));
                g.put(g.cue(Kind.SIGIL, 0, t[0], last + 12).at(Anchor.ORIGIN).a(3.0).n(5).variant(g.style));
                g.put(g.cue(Kind.FOG, last, last + 4, last + 24).a(4.0).n(12));
                g.shake(last, 0.8F, 6, 0.5F);
            }
            case "assassin_thousand_blades" -> {
                int last = t[t.length - 1];
                g.put(g.cue(Kind.BLADES, 0, last, last + 6).a(3.0).n(14));
                for (int i = 0; i < t.length; i++) {
                    double tilt = 70.0 * Math.sin(i * 2.3 + g.rnd(i) * 3.0);
                    g.put(g.cue(Kind.SLASH, t[i] - 2, t[i], t[i] + 7).frame(i).a(5.2).b(75.0).c(tilt).variant(i % 2 | 2));
                    g.put(g.cue(Kind.SLASH, t[i] - 1, t[i] + 1, t[i] + 7).frame(i).a(4.2).b(60.0).c(-tilt * 0.7)
                            .variant((i + 1) % 2));
                }
                g.put(g.cue(Kind.BURST, last, last, last + 12).frame(t.length - 1).a(5.5).n(28));
                g.put(g.cue(Kind.FLASH, last - 1, last, last + 8).frame(t.length - 1).a(4.0));
                g.shake(last, 1.8F, 12, 1.5F);
            }
            // ------------------------------------------------------------ mage
            case "mage_meteor" -> {
                int f = t[0];
                g.put(g.cue(Kind.SKY_SIGIL, 0, f - 4, f + 18).at(Anchor.AIM).a(6.0).c(16.0).n(8).variant(g.style));
                g.put(g.cue(Kind.SIGIL, 4, f, f + 16).at(Anchor.AIM).a(6.0).n(7).variant(g.style));
                g.put(g.cue(Kind.MOTES, 4, f, f + 12).at(Anchor.AIM).a(5.0).n(24));
                g.put(g.cue(Kind.FLASH, f - 1, f, f + 6).a(1.6).b(0.7));
                // the meteor falls 14 blocks at 1.2 a tick: the ground shakes when it lands (its blast is drawn there)
                g.shake(f + 12, 2.2F, 14, 1.8F);
            }
            case "mage_armageddon" -> {
                int last = t[t.length - 1];
                g.put(g.cue(Kind.SKY_SIGIL, 0, t[1] - 2, last + 18).at(Anchor.AIM).a(9.0).c(17.0).n(9).variant(g.style));
                g.put(g.cue(Kind.RAIN, t[1] - 4, t[1], last + 16).at(Anchor.AIM).a(8.0).n(4).c(15.0).variant(3));
                g.put(g.cue(Kind.FOG, t[0], t[1], last + 16).at(Anchor.AIM).a(7.0).n(12));
                for (int k = 1; k < t.length; k++) {
                    g.shake(t[k] + 11, 1.0F, 8, 0.7F); // every volley of meteors landing
                }
            }
            case "mage_absolute_zero" -> {
                int f = t[1];
                g.put(g.cue(Kind.SIGIL, 2, f - 2, f + 20).at(Anchor.ORIGIN).a(7.5).n(6).variant(g.style));
                g.put(g.cue(Kind.SPIKES, f + 1, f + 2, f + 24).frame(1).a(7.0).b(3.2).n(26));
                g.put(g.cue(Kind.SHOCKWAVE, f, f, f + 16).frame(1).a(9.0).b(1.2).n(3));
                g.put(g.cue(Kind.RAIN, t[0], t[0] + 4, f + 24).a(7.0).n(3).c(10.0).variant(2));
                g.put(g.cue(Kind.FLASH, f - 1, f, f + 8).frame(1).a(6.0));
                g.shake(f, 2.0F, 12, 1.6F);
            }
            case "mage_eternal_winter" -> {
                int f = t[0];
                g.put(g.cue(Kind.HELIX, f, f + 8, f + 84).a(3.0).b(3.6).n(4));
                g.put(g.cue(Kind.SKY_SIGIL, 0, f + 2, f + 84).a(7.5).c(12.0).n(6).variant(g.style));
                g.put(g.cue(Kind.SPIKES, f, f + 2, f + 24).at(Anchor.ORIGIN).a(7.0).b(1.8).n(20));
                g.shake(f, 1.0F, 8, 0.6F);
            }
            case "mage_thunder_god" -> {
                int last = t[t.length - 1];
                g.put(g.cue(Kind.SKY_SIGIL, 0, t[1] - 4, last + 10).a(8.0).c(14.0).n(7).variant(g.style));
                g.put(g.cue(Kind.HELIX, t[0], t[0] + 4, last + 6).a(1.0).b(2.6).n(3));
                g.put(g.cue(Kind.ORBIT, t[0], t[0] + 4, last + 6).a(1.4).n(4));
                g.put(g.cue(Kind.BOLTS, last - 2, last, last + 8).frame(t.length - 1).a(6.0).n(8).c(14.0));
                g.put(g.cue(Kind.FLASH, last - 1, last, last + 8).frame(t.length - 1).a(6.0));
                g.shake(t[1], 0.8F, 5, 0.5F);
                g.shake(t[3], 0.8F, 5, 0.5F);
                g.shake(last, 2.0F, 12, 1.6F);
            }
            case "mage_singularity" -> {
                int f = t[0];
                int blast = f + 44;
                g.put(g.cue(Kind.VORTEX, f, f + 8, blast).at(Anchor.AIM).a(5.5).b(5.0));
                g.put(g.cue(Kind.SKY_SIGIL, 0, f + 6, blast + 6).at(Anchor.AIM).a(6.0).c(12.0).n(7).variant(g.style));
                g.put(g.cue(Kind.NOVA, blast - 4, blast, blast + 16).at(Anchor.AIM).a(7.0).n(24));
                g.put(g.cue(Kind.FLASH, blast - 1, blast, blast + 8).at(Anchor.AIM).a(7.0));
                g.shake(blast, 2.2F, 14, 1.8F);
            }
            // ------------------------------------------------------------ ranger
            case "ranger_deadeye" -> {
                int last = t[t.length - 1];
                g.put(g.cue(Kind.RETICLE, t[0], t[1] - 2, last + 4).a(1.6).b(9.0));
                g.put(g.cue(Kind.GLYPH, t[0], t[0] + 4, last + 4).variant(4).a(1.0).c(0.8));
                g.put(g.cue(Kind.SIGIL, t[0], t[1] - 2, last + 6).a(1.6).n(4).c(1.4).d(90.0).variant(g.style));
                for (int i = 1; i < t.length; i++) {
                    g.put(g.cue(Kind.BEAM, t[i] - 1, t[i], t[i] + 5).frame(i).a(14.0).b(0.35));
                }
                g.put(g.cue(Kind.BEAM, last - 2, last, last + 12).frame(t.length - 1).a(18.0).b(1.1));
                g.shake(last, 0.9F, 6, 0.7F);
            }
            case "ranger_star_piercer" -> {
                int f = t[0];
                g.put(g.cue(Kind.SIGIL, 0, f - 2, f + 6).a(3.2).n(8).c(1.6).d(90.0).variant(g.style));
                g.put(g.cue(Kind.SIGIL, 6, f - 2, f + 4).a(1.8).n(5).c(2.4).d(90.0).variant(0));
                g.put(g.cue(Kind.CHARGE, 0, f, f + 2).a(1.6));
                g.put(g.cue(Kind.MOTES, 0, f - 4, f + 2).a(4.0).n(24).variant(1));
                g.put(g.cue(Kind.ROAR, f, f, f + 12).a(14.0).n(5));
                g.put(g.cue(Kind.FLASH, f - 1, f, f + 6).a(4.0).b(1.0));
                g.shake(f, 1.8F, 10, 1.6F);
            }
            case "ranger_beast_lord" -> {
                int last = t[t.length - 1];
                g.put(g.cue(Kind.HELIX, 0, 6, last + 8).a(1.2).b(2.8).n(3));
                g.put(g.cue(Kind.GLYPH, 0, 4, last + 6).variant(5).a(1.2).c(1.0));
                for (int i = 1; i < t.length; i++) {
                    g.put(g.cue(Kind.CLAW, t[i] - 2, t[i], t[i] + 8).frame(i).a(4.4).c(i % 2 == 0 ? 52.0 : -46.0));
                    g.put(g.cue(Kind.SHOCKWAVE, t[i], t[i], t[i] + 8).frame(i).a(4.0).n(1));
                }
                g.put(g.cue(Kind.QUAKE, last, last, last + 20).frame(t.length - 1).a(6.0).n(12));
                g.put(g.cue(Kind.BURST, last, last, last + 10).frame(t.length - 1).a(4.0).n(18));
                g.shake(last, 1.4F, 10, 1.2F);
            }
            case "ranger_arrow_rain" -> {
                int last = t[t.length - 1];
                g.put(g.cue(Kind.SKY_SIGIL, 2, t[0], last + 8).at(Anchor.ORIGIN).a(6.0).c(13.0).n(8).d(10.0)
                        .variant(g.style));
                g.put(g.cue(Kind.RAIN, t[0] - 2, t[0], last + 8).at(Anchor.ORIGIN).a(5.0).n(3).b(10.0).c(12.0)
                        .variant(1));
                g.shake(last, 0.8F, 6, 0.5F);
            }
            case "ranger_apocalypse_rain" -> {
                int last = t[t.length - 1];
                g.put(g.cue(Kind.SKY_SIGIL, 0, t[0], last + 14).at(Anchor.AIM).a(8.0).c(16.0).n(10).variant(g.style));
                g.put(g.cue(Kind.SHOCKWAVE, last, last + 8, last + 22).at(Anchor.AIM).a(7.0).n(3).b(1.0));
                g.put(g.cue(Kind.FLASH, last + 7, last + 8, last + 14).at(Anchor.AIM).a(5.0));
                g.shake(last + 8, 1.4F, 10, 1.0F);
            }
            // ------------------------------------------------------------ priest
            case "priest_sanctuary" -> {
                int f = t[0];
                g.put(g.cue(Kind.SIGIL, 0, f - 2, f + 44).at(Anchor.ORIGIN).a(8.0).n(8).variant(g.style));
                g.put(g.cue(Kind.PILLAR, f - 8, f, f + 34).at(Anchor.ORIGIN).a(0.6).b(10.0).n(6).c(6.5));
                g.put(g.cue(Kind.PILLAR, f - 4, f, f + 28).at(Anchor.ORIGIN).a(1.2).b(14.0));
                g.put(g.cue(Kind.WINGS, f, f + 6, f + 36).a(2.6));
                g.put(g.cue(Kind.HALO, f - 6, f, f + 36).a(0.5));
                g.put(g.cue(Kind.RAIN, f, f + 2, f + 40).at(Anchor.ORIGIN).a(8.0).n(2).c(9.0).variant(5));
                g.put(g.cue(Kind.NOVA, f, f, f + 14).frame(0).a(6.0).n(20));
                g.put(g.cue(Kind.FLASH, f - 1, f, f + 8).a(5.0));
                g.shake(f, 1.0F, 8, 0.6F);
            }
            case "priest_miracle" -> {
                int f = t[0];
                g.put(g.cue(Kind.SKY_SIGIL, 0, f - 2, f + 36).at(Anchor.ORIGIN).a(7.0).c(14.0).n(8).variant(g.style));
                g.put(g.cue(Kind.PILLAR, f - 6, f, f + 32).a(2.2).b(18.0));
                g.put(g.cue(Kind.WINGS, f, f + 6, f + 36).a(3.0));
                g.put(g.cue(Kind.RAIN, f, f + 2, f + 38).a(10.0).n(3).c(10.0).variant(5));
                g.put(g.cue(Kind.MOTES, f, f + 2, f + 36).a(8.0).n(30));
                g.put(g.cue(Kind.FLASH, f - 1, f, f + 8).a(7.0));
                g.shake(f, 1.2F, 10, 0.8F);
            }
            case "priest_holy_barrier" -> {
                int f = t[0];
                g.put(g.cue(Kind.DOME, f, f + 4, f + 38).a(6.0));
                g.put(g.cue(Kind.WALLS, f, f + 4, f + 36).at(Anchor.ORIGIN).a(6.0).b(3.0).n(12));
                g.put(g.cue(Kind.PILLAR, f - 2, f + 2, f + 22).at(Anchor.ORIGIN).a(0.5).b(9.0).n(4).c(6.0));
                g.shake(f, 0.8F, 6, 0.5F);
            }
            case "priest_ascension" -> {
                int f = t[0];
                g.put(g.cue(Kind.WINGS, f, f + 6, f + 38).a(3.2));
                g.put(g.cue(Kind.HALO, f - 4, f, f + 38).a(0.55));
                g.put(g.cue(Kind.HELIX, f, f + 4, f + 34).a(1.6).b(6.0).n(4));
                g.put(g.cue(Kind.PILLAR, f - 2, f + 2, f + 30).at(Anchor.ORIGIN).a(0.5).b(12.0).n(6).c(5.0));
                g.put(g.cue(Kind.MOTES, f, f + 2, f + 36).a(6.0).n(30));
                g.put(g.cue(Kind.FLASH, f - 1, f, f + 8).a(4.0));
                g.shake(f, 0.8F, 6, 0.5F);
            }
            case "priest_judgment_day" -> {
                int last = t[t.length - 1];
                g.put(g.cue(Kind.SKY_SIGIL, 0, t[1] - 2, last + 18).at(Anchor.AIM).a(9.0).c(16.0).n(8).variant(g.style));
                g.put(g.cue(Kind.GIANT_SWORD, last - 10, last, last + 24).at(Anchor.AIM).a(10.0));
                g.put(g.cue(Kind.CROSS, last, last, last + 26).at(Anchor.AIM).a(8.0).b(-2.0));
                g.put(g.cue(Kind.NOVA, last, last, last + 16).at(Anchor.AIM).a(7.0).n(22));
                g.put(g.cue(Kind.FLASH, last - 1, last, last + 8).at(Anchor.AIM).a(6.0));
                g.shake(last, 2.2F, 14, 1.8F);
            }
            case "priest_nova_of_light" -> {
                int last = t[t.length - 1];
                g.put(g.cue(Kind.SKY_SIGIL, 0, t[1] - 2, last + 14).a(6.0).c(11.0).n(8).variant(g.style));
                g.put(g.cue(Kind.WINGS, t[0], t[1], last + 14).a(2.4));
                g.put(g.cue(Kind.HALO, 0, t[0], last + 14).a(0.5));
                g.put(g.cue(Kind.FLASH, last - 1, last, last + 8).a(7.0));
                g.shake(t[1], 0.6F, 5, 0.4F);
                g.shake(t[2], 0.8F, 5, 0.5F);
                g.shake(last, 1.6F, 10, 1.2F);
            }
            // ------------------------------------------------------------ spellblade
            case "spellblade_arcane_storm" -> {
                int last = t[t.length - 1];
                g.put(g.cue(Kind.SIGIL, 0, t[0], last + 12).at(Anchor.ORIGIN).a(5.0).n(5).variant(g.style));
                g.put(g.cue(Kind.HELIX, 0, t[0] + 2, last + 10).a(1.2).b(3.0).n(3));
                // the storm itself: arcane wind turning around the blade dancer for the whole flurry
                g.put(g.cue(Kind.VORTEX, t[0] - 2, t[0] + 4, last + 10).at(Anchor.ORIGIN).a(6.5).b(4.5));
                for (int i = 0; i < t.length; i++) {
                    g.put(g.cue(Kind.SLASH, t[i] - 2, t[i], t[i] + 6).frame(i).a(5.0).b(85.0)
                            .c(i % 2 == 0 ? 35.0 : -35.0).variant(i % 2));
                }
                g.put(g.cue(Kind.FLASH, last - 1, last, last + 8).frame(t.length - 1).a(5.0));
                g.shake(last, 1.6F, 10, 1.2F);
            }
            case "spellblade_excalibur" -> {
                int f = t[1];
                g.put(g.cue(Kind.GIANT_SWORD, t[0], f, f + 24).frame(1).a(9.0).variant(1));
                g.put(g.cue(Kind.FLASH, f - 1, f, f + 8).frame(1).a(6.0).b(4.0));
                g.put(g.cue(Kind.SHOCKWAVE, f + 2, f + 2, f + 18).frame(1).a(6.0).n(2).d(7.0));
                g.shake(f, 2.2F, 14, 2.0F);
            }
            case "spellblade_elemental_fury" -> {
                g.paletteFrame(0, SkillTheme.FIRE);
                g.paletteFrame(1, SkillTheme.FROST);
                g.paletteFrame(2, SkillTheme.THUNDER);
                g.put(g.cue(Kind.BREATH, t[0] - 1, t[0], t[0] + 9).frame(0).a(5.0).b(55.0).n(9).palette(SkillTheme.FIRE));
                g.put(g.cue(Kind.SPIKES, t[1], t[1] + 1, t[1] + 16).frame(1).a(4.5).b(2.0).n(18).palette(SkillTheme.FROST));
                g.put(g.cue(Kind.BOLTS, t[2] - 2, t[2], t[2] + 8).frame(2).a(3.0).n(5).b(6.0).c(12.0)
                        .palette(SkillTheme.THUNDER));
                g.shake(t[0], 0.6F, 5, 0.4F);
                g.shake(t[1], 0.8F, 6, 0.5F);
                g.shake(t[2], 1.6F, 10, 1.2F);
            }
            case "spellblade_dimension_slash" -> {
                g.put(g.cue(Kind.RIFT, t[0], t[0] + 3, t[1]).frame(0).a(8.0).c(8.0));
                g.put(g.cue(Kind.RIFT, t[1] - 1, t[1], t[1] + 14).frame(1).a(8.0).c(-24.0));
                g.put(g.cue(Kind.FLASH, t[1] - 1, t[1], t[1] + 8).frame(1).a(5.0).b(4.0));
                g.put(g.cue(Kind.BURST, t[1], t[1], t[1] + 10).frame(1).a(5.0).b(4.0).n(20));
                g.shake(t[1], 1.8F, 12, 1.4F);
            }
            case "spellblade_world_cutter" -> {
                g.put(g.cue(Kind.ECLIPSE, 0, t[0] + 2, t[2] + 18).a(3.5).c(10.0));
                g.put(g.cue(Kind.RIFT, t[1] - 2, t[1], t[1] + 30).frame(1).a(14.0).c(80.0));
                g.put(g.cue(Kind.RIFT, t[2] - 2, t[2], t[2] + 18).frame(2).a(14.0).c(-8.0));
                g.put(g.cue(Kind.FLASH, t[2] - 1, t[2], t[2] + 8).frame(2).a(6.0).b(5.0));
                g.shake(t[1], 1.6F, 10, 1.2F);
                g.shake(t[2], 2.4F, 14, 2.0F);
            }
            case "spellblade_collapse" -> {
                int f = t[0];
                int blast = f + 40;
                g.put(g.cue(Kind.VORTEX, f, f + 8, blast).at(Anchor.AIM).a(5.0).b(4.5));
                g.put(g.cue(Kind.ECLIPSE, 0, f + 6, blast + 4).at(Anchor.AIM).a(2.5).c(8.0));
                g.put(g.cue(Kind.NOVA, blast - 4, blast, blast + 16).at(Anchor.AIM).a(6.0).n(24));
                g.put(g.cue(Kind.SPIKES, blast, blast, blast + 14).at(Anchor.AIM).a(5.0).b(2.5).n(18));
                g.put(g.cue(Kind.FLASH, blast - 1, blast, blast + 8).at(Anchor.AIM).a(7.0));
                g.shake(blast, 2.0F, 12, 1.6F);
            }
            default -> {
                // a new ultimate without its own finale still gets a great circle in the sky and a flash
                int last = t.length == 0 ? g.action.durationTicks() / 2 : t[t.length - 1];
                g.put(g.cue(Kind.SKY_SIGIL, 0, last, last + 16).a(6.0).c(12.0).n(7).variant(g.style));
                g.put(g.cue(Kind.FLASH, last - 1, last, last + 8).a(5.0));
                g.shake(last, 1.4F, 10, 1.0F);
            }
        }
    }
}
