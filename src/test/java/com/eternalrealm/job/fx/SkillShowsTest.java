package com.eternalrealm.job.fx;

import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.job.JobClass;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.SkillKind;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The light of every job skill (owner, 2026-11-12: 「全部のアクティブスキルと必殺技の視覚的効果、エフェクトを作り直し
 * て。高クオリティで派手に」): every active skill and ultimate has a show, something shines on every hit, ultimates end
 * in a grand finale that shakes the view, no two skills of a job look alike, and the light stays finite, near and
 * within its budget at every moment.
 */
public class SkillShowsTest {
    /** Quads one show may draw in a frame (the client's frame budget is 48,000 for all of them). */
    private static final int QUAD_BUDGET = 16_000;
    private static final Set<Cue.Kind> GRAND = Set.of(Cue.Kind.GIANT_SWORD, Cue.Kind.CROSS, Cue.Kind.BLADES,
            Cue.Kind.RIFT, Cue.Kind.ECLIPSE, Cue.Kind.HYDRA, Cue.Kind.METEOR, Cue.Kind.SKY_SIGIL, Cue.Kind.WINGS,
            Cue.Kind.WALLS, Cue.Kind.DOME, Cue.Kind.BOLTS, Cue.Kind.RAIN, Cue.Kind.PILLAR, Cue.Kind.VORTEX);

    static List<SkillDefinition> shown() {
        List<SkillDefinition> out = new ArrayList<>();
        for (SkillDefinition skill : Jobs.allSkills().values()) {
            if (skill.isUsable() && skill.kind() != SkillKind.PASSIVE) {
                out.add(skill);
            }
        }
        out.sort((a, b) -> a.id().compareTo(b.id()));
        return out;
    }

    @Test
    public void everyActiveSkillAndUltimateHasAShow() {
        List<SkillDefinition> skills = shown();
        assertTrue("skills: " + skills.size(), skills.size() >= 200);
        int ultimates = 0;
        for (SkillDefinition skill : skills) {
            SkillShow show = SkillShows.of(skill);
            assertEquals(skill.id(), skill.id(), show.skillId());
            assertEquals(skill.id(), skill.theme(), show.theme());
            assertFalse(skill.id() + " has no light", show.cues().isEmpty());
            assertTrue(skill.id() + " ends before its action", show.duration() >= skill.action().durationTicks());
            assertTrue(skill.id() + " has no afterglow", show.tail() >= 4);
            Optional<SkillShow> byAction = SkillShows.forAction(skill.action().id());
            assertTrue(skill.id(), byAction.isPresent());
            for (Cue cue : show.cues()) {
                assertTrue(skill.id() + " " + cue, cue.start() >= 0.0 && cue.start() <= cue.peak()
                        && cue.peak() <= cue.end());
                assertTrue(skill.id() + " " + cue + " outlives the show", cue.end() <= show.length() + 1.0E-9);
                if (cue.anchor() == Cue.Anchor.FRAME) {
                    assertTrue(skill.id() + " " + cue, cue.frame() >= 0 && cue.frame() < show.frameTimes().length);
                }
            }
            if (skill.kind() == SkillKind.ULTIMATE) {
                ultimates++;
            }
        }
        assertEquals("33 of the six jobs, two of every second class", 33 + 2 * JobClass.values().length, ultimates);
        assertTrue(SkillShows.forAction("no_such_skill").isEmpty());
        assertTrue(SkillShows.forAction(null).isEmpty());
    }

    @Test
    public void somethingShinesOnEveryHit() {
        for (SkillDefinition skill : shown()) {
            SkillShow show = SkillShows.of(skill);
            List<HitFrame> frames = skill.action().frames();
            for (int i = 0; i < frames.size(); i++) {
                HitFrame frame = frames.get(i);
                assertEquals(skill.id(), frame.offsetTicks(), show.frameTimes()[i]);
                if (!frame.damaging()) {
                    continue;
                }
                int t = frame.offsetTicks();
                boolean lit = show.cues().stream().anyMatch(c -> c.start() <= t && c.end() >= t);
                assertTrue(skill.id() + ": nothing shines on hit " + i + " at " + t, lit);
            }
        }
    }

    @Test
    public void ultimatesEndInAGrandFinaleThatShakesTheView() {
        for (SkillDefinition skill : shown()) {
            if (skill.kind() != SkillKind.ULTIMATE) {
                continue;
            }
            SkillShow show = SkillShows.of(skill);
            assertTrue(skill.id(), show.ultimate());
            assertTrue(skill.id() + " has few layers: " + show.cues().size(), show.cues().size() >= 8);
            // a grand shape: a giant sword, a cross, a meteor, a circle in the sky..., a sign twice a man's size, or
            // light reaching six blocks and more (a great beam, a quake, a nova)
            assertTrue(skill.id() + " has no grand shape", show.cues().stream().anyMatch(c -> GRAND.contains(c.kind())
                    || c.kind() == Cue.Kind.GLYPH && c.a() >= 2.0 || c.a() >= 6.0));
            assertFalse(skill.id() + " never shakes the view", show.shakes().isEmpty());
            int lastHit = show.frameTimes().length == 0 ? 0 : show.frameTimes()[show.frameTimes().length - 1];
            double lastLight = show.cues().stream().mapToDouble(Cue::end).max().orElse(0.0);
            assertTrue(skill.id() + " is dark right after its last hit", lastLight >= lastHit + 8.0);
            for (SkillShow.Shake shake : show.shakes()) {
                assertTrue(skill.id(), shake.time() >= 0.0 && shake.time() <= show.length());
                assertTrue(skill.id(), shake.intensity() > 0.0F && shake.intensity() <= 4.0F && shake.ticks() > 0);
            }
        }
    }

    @Test
    public void noTwoSkillsOfAJobLookAlike() {
        Map<String, String> seen = new HashMap<>();
        for (SkillDefinition skill : shown()) {
            SkillShow show = SkillShows.of(skill);
            List<String> layers = new ArrayList<>();
            for (Cue cue : show.cues()) {
                layers.add(cue.kind() + "/" + cue.variant() + "/" + cue.n() + "/" + Math.round(cue.a() * 10.0) + "/"
                        + Math.round(cue.b() * 10.0) + "/" + cue.palette());
            }
            layers.sort(null);
            String signature = skill.job() + ":" + show.theme() + ":" + layers;
            String other = seen.put(signature, skill.id());
            assertTrue(skill.id() + " looks just like " + other, other == null);
        }
    }

    @Test
    public void showsAreBuiltTheSameEveryTime() {
        for (SkillDefinition skill : shown()) {
            SkillShow a = SkillShows.build(skill);
            SkillShow b = SkillShows.build(skill);
            assertEquals(skill.id(), a.cues(), b.cues());
            assertEquals(skill.id(), a.shakes(), b.shakes());
            assertEquals(skill.id(), SkillShows.of(skill).cues(), a.cues());
        }
    }

    @Test
    public void theLightIsFiniteNearAndWithinBudgetAtEveryMoment() {
        V3 feet = new V3(120.5, 64.0, -40.5);
        for (SkillDefinition skill : shown()) {
            SkillShow show = SkillShows.of(skill);
            Pose origin = Pose.of(feet, 37.0, 12.0, 1.62, skill.job() == JobType.ASSASSIN ? -1 : 1);
            V3 aim = feet.plus(origin.fwd(), 14.0);
            ShowState state = new ShowState(show, origin, aim);
            double[] far = {0.0};
            Light light = (x, y, z, argb) -> {
                assertTrue(skill.id() + " drew a point at infinity", Double.isFinite(x) && Double.isFinite(y)
                        && Double.isFinite(z));
                far[0] = Math.max(far[0], Math.hypot(Math.hypot(x - feet.x(), z - feet.z()), y - feet.y()));
            };
            int peak = 0;
            int total = 0;
            for (double t = 0.0; t <= show.length(); t += 1.0) {
                // the caster walks forward through the show (a dash or a step), its strikes stay where they were cut
                Pose now = origin.movedTo(feet.plus(origin.fwd(), Math.min(4.0, t * 0.2)));
                state.update(t, now);
                Brush brush = new Brush(light, feet.add(-3.0, 3.0, -5.0));
                int quads = Painter.paint(state, t, brush);
                peak = Math.max(peak, quads);
                total += quads;
                // through the caster's own eyes too
                Brush own = new Brush(light, now.eye());
                Painter.paint(state, t, own, true);
            }
            assertTrue(skill.id() + " draws " + peak + " quads in a frame", peak <= QUAD_BUDGET);
            assertTrue(skill.id() + " draws nothing", total > 0);
            assertTrue(skill.id() + " reaches " + far[0] + " blocks away", far[0] <= 72.0);
        }
    }
}
