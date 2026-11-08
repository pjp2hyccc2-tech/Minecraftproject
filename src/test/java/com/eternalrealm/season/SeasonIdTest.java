package com.eternalrealm.season;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SeasonIdTest {
    @Test
    public void acceptsSimpleIds() {
        assertTrue(SeasonId.isValid("s1"));
        assertTrue(SeasonId.isValid("2026-winter_a"));
        assertTrue(SeasonId.isValid("a".repeat(32)));
        assertEquals("s1", SeasonId.of("s1").value());
    }

    @Test
    public void rejectsIdsThatCouldEscapeAPathOrBreakKeys() {
        for (String invalid : new String[]{"", "S1", "../x", "a/b", "a\\b", ".", "..", "-a", "_a", "a b", "a".repeat(33), "ä"}) {
            assertFalse(invalid, SeasonId.isValid(invalid));
            assertThrows(IllegalArgumentException.class, () -> new SeasonId(invalid));
        }
        assertFalse(SeasonId.isValid(null));
    }
}
