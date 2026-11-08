package com.eternalrealm.combat;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ActionDefinitionTest {
    private static final HitFrame FRAME = new HitFrame(3, HitShape.arc(3.0, 45), 1.0, 0.4);

    @Test
    public void rejectsInconsistentDefinitions() {
        // frame at/after the cancel point would be skipped by the next action
        assertThrows(IllegalArgumentException.class, () -> new ActionDefinition("a", ActionKind.LIGHT, 10, 3,
                List.of(FRAME), 0, 0, 0, true));
        // dodge with hits / attack without hits
        assertThrows(IllegalArgumentException.class, () -> new ActionDefinition("a", ActionKind.DODGE, 10, 5,
                List.of(FRAME), 0, 0, 5, false));
        assertThrows(IllegalArgumentException.class, () -> new ActionDefinition("a", ActionKind.LIGHT, 10, 5,
                List.of(), 0, 0, 0, true));
        // cooldown outside skills, invulnerability outside dodges
        assertThrows(IllegalArgumentException.class, () -> new ActionDefinition("a", ActionKind.LIGHT, 10, 5,
                List.of(FRAME), 0, 20, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new ActionDefinition("a", ActionKind.SKILL, 10, 5,
                List.of(FRAME), 0, 20, 3, false));
        // unordered frames, cancel after the end, bad id
        assertThrows(IllegalArgumentException.class, () -> new ActionDefinition("a", ActionKind.LIGHT, 10, 8,
                List.of(new HitFrame(5, HitShape.arc(3, 45), 1, 0), FRAME), 0, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new ActionDefinition("a", ActionKind.LIGHT, 10, 11,
                List.of(FRAME), 0, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new ActionDefinition("Bad Id", ActionKind.LIGHT, 10, 5,
                List.of(FRAME), 0, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new HitFrame(0, HitShape.arc(3, 45), Double.NaN, 0));
    }

    @Test
    public void scalingKeepsFramesBeforeTheCancelPoint() {
        ActionDefinition action = new ActionDefinition("a", ActionKind.LIGHT, 10, 6, List.of(FRAME), 0, 0, 0, true);
        assertEquals(new ActionDefinition.Timing(10, 6, new int[]{3}), action.timing(1.0));
        assertEquals(new ActionDefinition.Timing(6, 4, new int[]{2}), action.timing(0.6));

        // tiny action: duration 2, frame at 1 -> scaled duration must still leave room after the frame
        ActionDefinition tiny = new ActionDefinition("b", ActionKind.LIGHT, 2, 2,
                List.of(new HitFrame(1, HitShape.arc(3, 45), 1, 0)), 0, 0, 0, true);
        ActionDefinition.Timing timing = tiny.timing(0.2);
        assertArrayEquals(new int[]{1}, timing.frameOffsets());
        assertEquals(2, timing.cancel());
        assertEquals(2, timing.duration());
    }

    @Test
    public void unscaledActionsIgnoreTheSpeedFactor() {
        assertEquals(CombatRules.DODGE.timing(1.0), CombatRules.DODGE.timing(0.3));
    }
}
