package com.eternalrealm.job.fx;

import com.eternalrealm.job.SkillTheme;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What a skill leaves where it lands (its ground area, the burst of its spell) and the light of its projectiles in
 * flight: every theme draws them, finite and near, for their whole life.
 */
public class FieldsAndTrailsTest {
    private static final V3 GROUND = new V3(-300.5, 70.0, 812.5);

    /** Paints a show at every tick of its life; returns the most quads of a frame. */
    private static int paintAll(SkillShow show, Pose at, double maxDistance) {
        ShowState state = new ShowState(show, at, at.feet());
        int peak = 0;
        int total = 0;
        for (double t = 0.0; t <= show.length(); t += 1.0) {
            state.update(t, at);
            Brush brush = new Brush((x, y, z, argb) -> {
                assertTrue(show.skillId(), Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z));
                assertTrue(show.skillId() + " too far", Math.hypot(Math.hypot(x - GROUND.x(), z - GROUND.z()),
                        y - GROUND.y()) <= maxDistance);
            }, GROUND.add(-6.0, 5.0, -9.0));
            int quads = Painter.paint(state, t, brush);
            peak = Math.max(peak, quads);
            total += quads;
        }
        assertTrue(show.skillId() + " draws nothing", total > 0);
        return peak;
    }

    @Test
    public void aGroundAreaGlowsForItsWholeLifeAndRingsOnEveryPulse() {
        for (SkillTheme theme : SkillTheme.values()) {
            SkillShow show = Fields.zone(theme, 4.0, 0, 6, 10, theme == SkillTheme.LIFE, true, 11L);
            assertEquals(theme, show.theme());
            assertTrue(show.length() >= 50 + 10);
            Cue field = show.cues().get(0);
            assertEquals(Cue.Kind.FIELD, field.kind());
            assertTrue("the field outlives its last pulse", field.end() >= 50 + 10);
            long rings = show.cues().stream().filter(c -> c.kind() == Cue.Kind.SHOCKWAVE).count();
            assertEquals(6, rings);
            int peak = paintAll(show, Fields.at(GROUND, 30.0), 24.0);
            assertTrue(theme + " field: " + peak, peak <= 12_000);
        }
    }

    @Test
    public void aDelayedBlastClosesInThenBurstsAndShakes() {
        SkillShow show = Fields.zone(SkillTheme.FIRE, 3.5, 24, 1, 10, false, true, 3L);
        assertTrue(show.cues().stream().anyMatch(c -> c.kind() == Cue.Kind.BURST && c.peak() == 24.0));
        assertFalse(show.shakes().isEmpty());
        assertEquals(24.0, show.shakes().get(0).time(), 1.0E-9);
        paintAll(show, Fields.at(GROUND, 0.0), 24.0);
        // a lingering area does not shake
        assertTrue(Fields.zone(SkillTheme.VENOM, 3.5, 0, 8, 10, false, true, 3L).shakes().isEmpty());
    }

    @Test
    public void areasAreClampedToWhatTheServerCanMake() {
        SkillShow huge = Fields.zone(SkillTheme.ARCANE, 500.0, 10_000, 1_000, 1_000, true, true, 1L);
        assertTrue(huge.length() <= Fields.MAX_LIFE + 30);
        assertTrue(huge.cues().get(0).a() <= 16.0);
        SkillShow tiny = Fields.zone(null, Double.NaN, -5, 0, 0, false, false, 1L);
        assertTrue(tiny.cues().get(0).a() >= 0.5);
        assertEquals(SkillTheme.ARCANE, tiny.theme());
        paintAll(tiny, Fields.at(GROUND, 0.0), 24.0);
    }

    @Test
    public void everyThemeBurstsWhereItsSpellLands() {
        for (SkillTheme theme : SkillTheme.values()) {
            SkillShow show = Fields.blast(theme, 3.0, 5L);
            assertTrue(theme + " has no mark of its own", show.cues().size() >= 4);
            assertFalse("a big blast shakes the view", show.shakes().isEmpty());
            int peak = paintAll(show, Fields.at(GROUND.add(0.0, -1.0, 0.0), 0.0), 30.0);
            assertTrue(theme + " blast: " + peak, peak <= 12_000);
        }
        assertTrue("a small blast does not shake", Fields.blast(SkillTheme.FIRE, 1.0, 5L).shakes().isEmpty());
    }

    @Test
    public void projectilesCarryTheirElementsLight() {
        for (SkillTheme theme : SkillTheme.values()) {
            for (boolean arrow : List.of(false, true)) {
                V3[] path = new V3[Trails.LENGTH];
                for (int i = 0; i < path.length; i++) {
                    path[i] = GROUND.add(0.0, 1.5 - 0.01 * i * i, -1.3 * i);
                }
                int[] quads = {0};
                Brush brush = new Brush((x, y, z, argb) -> {
                    assertTrue(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z));
                    assertTrue(Math.hypot(Math.hypot(x - GROUND.x(), z - GROUND.z()), y - GROUND.y()) < 20.0);
                }, GROUND.add(3.0, 2.0, 6.0));
                Trails.draw(brush, theme, arrow, path, 17.5, 9L, 1.0);
                quads[0] = brush.quads();
                assertTrue(theme + (arrow ? " arrow" : " spell") + " draws nothing", quads[0] > 10);
                assertTrue(theme + " " + quads[0], quads[0] < 2_000);
                // a projectile just fired (one point), a wake fading out, a broken position
                Brush one = new Brush((x, y, z, argb) -> { }, GROUND);
                Trails.draw(one, theme, arrow, new V3[]{GROUND.add(0.0, 1.0, 3.0)}, 0.0, 1L, 1.0);
                assertTrue(one.quads() > 0);
                Brush gone = new Brush((x, y, z, argb) -> { }, GROUND);
                Trails.draw(gone, theme, arrow, path, 3.0, 1L, 0.0);
                assertEquals(0, gone.quads());
                Brush broken = new Brush((x, y, z, argb) -> { }, GROUND);
                Trails.draw(broken, theme, arrow, new V3[]{new V3(Double.NaN, 0.0, 0.0)}, 3.0, 1L, 1.0);
                assertEquals(0, broken.quads());
            }
        }
    }
}
