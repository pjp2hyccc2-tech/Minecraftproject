package com.eternalrealm.job.fx;

import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.job.JobClass;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.SkillEffect;
import org.junit.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The finales' shows (owner, 2026-11-15: 「アニメーションとエフェクト、視覚的効果や視線のブレなどを入れて高クオリティで派手な
 * ものをつくって」): each directs the screen (cinema bars, a zoom, a flash on its big blow), shakes the view hard, hides the
 * weapon only while a prop or after-images stand in for it, and its props and light stay finite and within budget at
 * every moment, from every side and through the caster's own eyes.
 */
public class FinaleShowTest {
    private static final int QUAD_BUDGET = 16_000;
    /** The layers that can stand in for a hidden weapon or body. */
    private static final Set<Cue.Kind> STAND_INS = EnumSet.of(Cue.Kind.SCYTHE, Cue.Kind.GREATBLADE, Cue.Kind.NAIL,
            Cue.Kind.HAMMER, Cue.Kind.STAR_ROCK, Cue.Kind.RIFLE, Cue.Kind.AEGIS, Cue.Kind.GREAT_BOW,
            Cue.Kind.FLASH_CUTS);

    private static List<SkillDefinition> finales() {
        return Jobs.allSkills().values().stream().filter(SkillDefinition::isFinale)
                .sorted((a, b) -> a.id().compareTo(b.id())).toList();
    }

    @Test
    public void everyFinaleDirectsTheScreen() {
        List<SkillDefinition> finales = finales();
        assertEquals(JobClass.values().length, finales.size());
        for (SkillDefinition skill : finales) {
            SkillShow show = SkillShows.of(skill);
            assertTrue(skill.id(), show.ultimate());
            Set<SkillShow.Screen.Kind> kinds = EnumSet.noneOf(SkillShow.Screen.Kind.class);
            for (SkillShow.Screen screen : show.screens()) {
                kinds.add(screen.kind());
                assertTrue(skill.id() + " " + screen, screen.time() >= 0.0
                        && screen.time() + screen.ticks() <= show.length() + 1e-9);
            }
            // cinema bars for all but the sniper, who looks through its scope instead (a zoom and dark edges)
            Set<SkillShow.Screen.Kind> expected = EnumSet.allOf(SkillShow.Screen.Kind.class);
            if (skill.id().equals("sniper_three_magic_bullets")) {
                expected.remove(SkillShow.Screen.Kind.BARS);
            }
            assertEquals(skill.id(), expected, kinds);
            // the biggest blow flashes and shakes the view hard
            double last = lastBlow(skill);
            assertTrue(skill.id() + " flashes on its blow", show.screens().stream().anyMatch(screen ->
                    screen.kind() == SkillShow.Screen.Kind.FLASH && Math.abs(screen.time() - last) <= 1.0
                            && screen.strength() >= 0.3F));
            assertTrue(skill.id() + " shakes", show.shakes().stream().anyMatch(shake -> shake.intensity() >= 1.2F));
            // the screen as it plays: bounded, the bars in by the blow, nothing left once it is over
            for (double t = 0.0; t <= show.length() + 2.0; t += 0.5) {
                SkillShow.ScreenState s = show.screenAt(t);
                assertTrue(skill.id() + " at " + t, s.flash() >= 0.0F && s.flash() <= 1.0F);
                assertTrue(skill.id() + " at " + t, s.bars() >= 0.0F && s.bars() <= 0.25F);
                assertTrue(skill.id() + " at " + t, s.zoom() >= 0.5F && s.zoom() <= 1.5F);
                assertTrue(skill.id() + " at " + t, s.vignette() >= 0.0F && s.vignette() <= 1.0F);
            }
            SkillShow.ScreenState blow = show.screenAt(last);
            assertTrue(skill.id(), blow.bars() > 0.05F || blow.zoom() < 0.8F);
            assertTrue(skill.id(), show.screenAt(show.length() + 1.0).none());
            assertTrue(skill.id(), show.screenEnd() <= show.length());
        }
    }

    /** The moment of the finale's biggest hit (its zones' first pulse or its summon's rising for those without). */
    private static double lastBlow(SkillDefinition skill) {
        List<HitFrame> frames = skill.action().frames();
        double best = -1.0;
        double at = 0.0;
        for (HitFrame frame : frames) {
            if (frame.damaging() && frame.multiplier() >= best) {
                best = frame.multiplier();
                at = frame.offsetTicks();
            }
        }
        for (SkillEffect effect : skill.effects()) {
            if (effect instanceof SkillEffect.Zone zone && zone.damageMultiplier() * zone.pulses() > best) {
                best = zone.damageMultiplier() * zone.pulses();
                at = zone.delayTicks();
            }
        }
        return at;
    }

    @Test
    public void aHiddenWeaponOrBodyAlwaysHasAStandIn() {
        Set<String> hiders = new HashSet<>();
        for (SkillDefinition skill : finales()) {
            SkillShow show = SkillShows.of(skill);
            for (SkillShow.Window window : show.hidden()) {
                hiders.add(skill.id());
                assertTrue(skill.id() + " " + window, window.start() >= 0.0 && window.end() > window.start()
                        && window.end() <= show.duration());
                for (double t = window.start(); t <= window.end(); t += 1.0) {
                    double at = t;
                    assertTrue(skill.id() + " hides with nothing in its place at " + t, show.cues().stream()
                            .anyMatch(cue -> STAND_INS.contains(cue.kind()) && cue.start() <= at && cue.end() >= at));
                }
                assertTrue(skill.id() + " hides its weapon", show.hidesWeapon(window.start()));
                assertEquals(skill.id(), window.body(), show.hidesBody(window.start()));
            }
            assertFalse(skill.id() + " gives the weapon back", show.hidesWeapon(show.duration() + 1.0));
        }
        // the shinobi vanishes into its flashes; the rifle, the scythe, the giant swords and the great bow replace
        // the weapon
        assertTrue(SkillShows.of(Jobs.skill("shinobi_eightfold_flash").orElseThrow()).hidden().get(0).body());
        assertTrue(hiders.containsAll(Set.of("shinobi_eightfold_flash", "sniper_three_magic_bullets",
                "reaper_scythe_of_the_end", "sword_saint_heavenfall_blade", "paladin_earthsunder",
                "spirit_archer_bow_of_seven_spirits")));
    }

    /** Props and light, painted together, from around the caster and through its eyes: finite and within budget. */
    @Test
    public void propsAndLightStayFiniteAndWithinBudget() {
        V3[] cameras = {new V3(4.0, 2.4, -5.0), new V3(-6.0, 3.0, 6.0), new V3(0.0, 1.62, 0.0)};
        for (SkillDefinition skill : finales()) {
            SkillShow show = SkillShows.of(skill);
            Pose pose = Pose.of(new V3(0.0, 0.0, 0.0), 0.0, 8.0, 1.62, 1);
            ShowState state = new ShowState(show, pose, new V3(0.0, 0.0, 8.0));
            boolean anySolid = false;
            for (double t = 0.0; t <= show.length(); t += 2.0) {
                state.update(t, pose);
                for (int c = 0; c < cameras.length; c++) {
                    boolean ownEyes = c == 2;
                    Counter solids = new Counter();
                    Counter light = new Counter();
                    Painter.paintSolids(state, t, new Brush(Brush.NOWHERE, solids, cameras[c]), ownEyes);
                    Painter.paint(state, t, new Brush(light, cameras[c]), ownEyes);
                    assertTrue(skill.id() + " at " + t + ": " + solids.quads + " solid + " + light.quads,
                            solids.quads + light.quads <= QUAD_BUDGET);
                    assertTrue(skill.id() + " at " + t + " is not finite", solids.finite && light.finite);
                    anySolid |= solids.quads > 0;
                }
            }
            boolean hasProp = show.cues().stream().anyMatch(cue -> cue.kind().solid());
            assertEquals(skill.id(), hasProp, anySolid);
        }
    }

    /** The necromancer's king: finite, near its feet, within budget as it rises, cleaves, sweeps and crumbles. */
    @Test
    public void theBoneKingIsDrawnWholeAndFadesAway() {
        V3 feet = new V3(3.0, 64.0, -2.0);
        double[][] moments = {{4, -1, 0, -1}, {20, -1, 0, -1}, {60, 6, 0, -1}, {64, 12, 0, -1}, {90, 12, 1, -1},
                {120, -1, 0, 10}, {150, -1, 0, BoneKingFigure.FADE_TICKS + 1}};
        for (double[] m : moments) {
            BoneKingFigure.State s = new BoneKingFigure.State(feet, 135.0, m[0], m[1], (int) m[2], m[3], m[0] * 0.3,
                    0.5, 7L);
            Counter solids = new Counter();
            Counter light = new Counter();
            BoneKingFigure.paint(new Brush(Brush.NOWHERE, solids, new V3(0.0, 66.0, -8.0)), s);
            BoneKingFigure.paint(new Brush(light, new V3(0.0, 66.0, -8.0)), s);
            assertTrue(solids.finite && light.finite);
            assertTrue("within 6 blocks of its feet", solids.farthest(feet) <= 6.5);
            assertTrue(solids.quads + light.quads <= 4_000);
            boolean gone = m[3] > BoneKingFigure.FADE_TICKS;
            assertEquals("age " + m[0] + " fade " + m[3], gone, solids.quads == 0);
            if (m[0] >= 20 && !gone) {
                assertTrue(solids.quads > 300);
            }
        }
        assertEquals(0.0, BoneKingFigure.presence(new BoneKingFigure.State(feet, 0.0, 0.0, -1, 0, -1, 0, 0, 1L)),
                1e-9);
    }

    /** Each sentence of the inquisitor's verdict has its own sign and a pillar that comes down on the foe. */
    @Test
    public void theSentencesAreShownOverTheirFoes() {
        V3 head = new V3(0.0, 66.6, 0.0);
        Set<Integer> looks = new HashSet<>();
        for (int sentence = 0; sentence < SkillEffect.Sentence.values().length; sentence++) {
            SkillShow show = Fields.sentence(sentence, 2.0, 5L);
            assertTrue(show.length() >= 30);
            ShowState state = new ShowState(show, Fields.at(head, 0.0), head);
            Counter at4 = new Counter();
            Painter.paint(state, 4.0, new Brush(at4, new V3(5.0, 66.0, 5.0)));
            Counter at24 = new Counter();
            Painter.paint(state, 24.0, new Brush(at24, new V3(5.0, 66.0, 5.0)));
            assertTrue(at4.quads > 20 && at24.quads > 20 && at4.finite && at24.finite);
            looks.add(at4.quads * 31 + at24.quads);
        }
        assertTrue("six signs, not one", looks.size() >= 4);
    }

    /** Several flashes blend, the strongest bars and vignette win, zooms multiply. */
    @Test
    public void screenMomentsMix() {
        SkillShow show = new SkillShow("test", null, com.eternalrealm.job.SkillTheme.HOLY, true, 40, 10, new int[]{0},
                List.of(Cue.of(Cue.Kind.FLASH, 0, 0, 5).a(1.0).build()), List.of(),
                List.of(new SkillShow.Screen(10, SkillShow.Screen.Kind.FLASH, 10, 0.5F, 0xFF0000),
                        new SkillShow.Screen(10, SkillShow.Screen.Kind.FLASH, 10, 0.5F, 0x0000FF),
                        new SkillShow.Screen(0, SkillShow.Screen.Kind.BARS, 40, 0.1F, 0),
                        new SkillShow.Screen(0, SkillShow.Screen.Kind.BARS, 40, 0.2F, 0),
                        new SkillShow.Screen(0, SkillShow.Screen.Kind.ZOOM, 40, 0.8F, 0),
                        new SkillShow.Screen(0, SkillShow.Screen.Kind.ZOOM, 40, 0.9F, 0),
                        new SkillShow.Screen(0, SkillShow.Screen.Kind.VIGNETTE, 40, 0.6F, 0x112233)),
                List.of());
        SkillShow.ScreenState at10 = show.screenAt(10.0);
        assertEquals(0.75F, at10.flash(), 1e-5F); // 1 - (1 - 0.5)^2
        assertEquals(0x800080, at10.flashRgb());
        assertEquals(0.2F, at10.bars(), 1e-5F);
        assertEquals(0.72F, at10.zoom(), 1e-5F);
        assertEquals(0.6F, at10.vignette(), 1e-5F);
        assertEquals(0x112233, at10.vignetteRgb());
        assertTrue(show.screenAt(-1.0).none());
        assertTrue(show.screenAt(41.0).none());
        assertEquals(40.0, show.screenEnd(), 1e-9);
        assertTrue(SkillShow.ScreenState.NONE.none());
    }

    /** Counts quads and checks every vertex is a real number. */
    private static final class Counter implements Light {
        int vertices;
        int quads;
        boolean finite = true;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;

        @Override
        public void vertex(double x, double y, double z, int argb) {
            finite &= Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            if (++vertices % 4 == 0) {
                quads++;
            }
        }

        double farthest(V3 from) {
            if (quads == 0) {
                return 0.0;
            }
            return Math.max(Math.max(Math.abs(maxX - from.x()), Math.abs(minX - from.x())),
                    Math.max(Math.abs(maxZ - from.z()), Math.abs(minZ - from.z())));
        }
    }
}
