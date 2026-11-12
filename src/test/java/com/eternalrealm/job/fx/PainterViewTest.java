package com.eternalrealm.job.fx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Light that reads from where the viewer stands: cuts are turned to show their face (never a thin line seen edge-on),
 * pushed off the caster's own eye in first person, and light right in front of the camera thins out instead of
 * covering the screen.
 */
public class PainterViewTest {
    private static final V3 FEET = new V3(10.5, 64.0, 20.5);

    @Test
    public void aCutSeenEdgeOnTurnsItsFaceToTheViewer() {
        Pose pose = Pose.of(FEET, 0.0, 0.0, 1.62, 1);
        V3 center = pose.at(0.1, 1.05, 0.0);
        V3 upright = Painter.tilted(pose, 90.0);
        // straight behind the caster at eye height: an upright cut's plane runs through the camera
        V3 behind = pose.eye().plus(pose.fwd(), -4.0);
        assertTrue(Painter.elevation(behind, center, upright, pose.fwd(), 2.5) < Math.toRadians(5.0));
        Painter.Plane plane = Painter.facing(behind, center, upright, pose.fwd(), 2.5);
        double seen = Painter.elevation(behind, plane.center(), plane.a(), plane.bb(), 2.5);
        assertTrue("seen at " + Math.toDegrees(seen), seen >= Painter.FACE_MIN - 1.0E-6);
        // a cut already facing the viewer is left alone
        V3 side = center.plus(pose.right(), 6.0);
        Painter.Plane same = Painter.facing(side, center, upright, pose.fwd(), 2.5);
        assertEquals(upright, same.a());
        assertEquals(pose.fwd(), same.bb());
        assertEquals(center, same.center());
    }

    @Test
    public void theCastersOwnEyeIsKeptOffItsCut() {
        Pose pose = Pose.of(FEET, 45.0, 10.0, 1.62, 1);
        V3 center = pose.at(0.1, 1.05, 0.0);
        for (double tilt : new double[]{0.0, 20.0, 45.0, 70.0, 90.0}) {
            Painter.Plane plane = Painter.facing(pose.eye(), center, Painter.tilted(pose, tilt), pose.fwd(), 2.8);
            double off = Math.abs(pose.eye().sub(plane.center()).dot(plane.normal()));
            assertTrue("tilt " + tilt + ": the eye is " + off + " off the cut", off >= Painter.CLEAR_EYE - 1.0E-6);
            assertTrue("the cut stays on the caster", plane.center().distance(center) <= 1.4);
            assertTrue(plane.widen() > 1.0);
        }
        // a far onlooker gets no push and no widening
        Painter.Plane far = Painter.facing(FEET.add(0.0, 30.0, -30.0), center, Painter.tilted(pose, 30.0), pose.fwd(),
                2.8);
        assertEquals(center, far.center());
        assertEquals(1.0, far.widen(), 1.0E-9);
    }

    @Test
    public void lightRightInFrontOfTheCameraThinsOut() {
        V3 camera = new V3(0.0, 0.0, 0.0);
        int[] alpha = new int[4];
        int[] count = {0};
        Brush brush = new Brush((x, y, z, argb) -> alpha[count[0]++ % 4] = argb >>> 24, camera);
        // a quad touching the camera is not drawn at all
        brush.quad(new V3(0.1, 0.0, 0.1), new V3(-0.1, 0.0, 0.1), new V3(-0.1, 0.1, 0.1), new V3(0.1, 0.1, 0.1),
                0xFFFFFF, 1.0, 1.0);
        assertEquals(0, brush.quads());
        // half-way in, half the strength; far out, all of it
        brush.quad(new V3(0.0, 0.0, 0.85), new V3(0.1, 0.0, 0.85), new V3(0.1, 0.0, 5.0), new V3(0.0, 0.0, 5.0),
                0xFFFFFF, 1.0, 1.0);
        assertEquals(1, brush.quads());
        assertTrue("near: " + alpha[0], alpha[0] > 60 && alpha[0] < 200);
        assertEquals(255, alpha[2]);
        // the caster's own body light thins out further in first person
        brush.nearFade(Painter.HUG_GONE, Painter.HUG_CLEAR);
        brush.quad(new V3(0.0, 0.0, 1.0), new V3(0.1, 0.0, 1.0), new V3(0.1, 0.0, 5.0), new V3(0.0, 0.0, 5.0),
                0xFFFFFF, 1.0, 1.0);
        assertTrue("hugging: " + alpha[0], alpha[0] < 60);
        brush.nearFade();
    }

    @Test
    public void inFirstPersonTheBodysOwnLightNeverCoversTheScreen() {
        // a priest's blessing (aura, halo, helix around the body) seen through the caster's own eyes
        SkillShow show = SkillShows.of(com.eternalrealm.job.Jobs.skill("priest_battle_hymn").orElseThrow());
        Pose pose = Pose.of(FEET, 0.0, 0.0, 1.62, 1);
        ShowState state = new ShowState(show, pose, FEET.plus(pose.fwd(), 8.0));
        double[] nearest = {Double.MAX_VALUE};
        for (double t = 0.0; t <= show.length(); t += 1.0) {
            state.update(t, pose);
            Brush brush = new Brush((x, y, z, argb) -> {
                if ((argb >>> 24) > 64) {
                    nearest[0] = Math.min(nearest[0], new V3(x, y, z).distance(pose.eye()));
                }
            }, pose.eye());
            Painter.paint(state, t, brush, true);
        }
        assertTrue("bright light " + nearest[0] + " from the eye", nearest[0] >= Painter.HUG_GONE);
    }
}
