package com.eternalrealm.social.phone;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneTextTest {
    @Test
    public void formattingCodesAndControlCharactersAreDropped() {
        assertEquals("赤い文字", PhoneText.clean("§c赤い§l文字§", false));
        assertEquals("ab", PhoneText.clean("a\u0000\u0007b", false));
        assertEquals("abc", PhoneText.clean("a‮b​c", false)); // right-to-left override, zero width space
        assertEquals("a b", PhoneText.clean("a\tb", false));
        assertEquals("ok", PhoneText.clean("o\uD800k", false)); // a lone surrogate half
        assertEquals("😀", PhoneText.clean("😀", false)); // a whole pair stays
    }

    @Test
    public void newlinesOnlyWhereAllowedAndNeverBlankRuns() {
        assertEquals("one two", PhoneText.clean("one\ntwo", false));
        assertEquals("one\ntwo", PhoneText.clean("one  \n\n\n\r\ntwo", true));
        assertEquals("a\nb\nc\nd\ne f g", PhoneText.clean("a\nb\nc\nd\ne\nf\ng", true));
        assertEquals("x", PhoneText.clean("\n\n  x  \n", true));
        assertEquals("", PhoneText.clean("   \n ", true));
        assertEquals("", PhoneText.clean(null, true));
    }

    @Test
    public void lengthIsCountedInCharactersNotUnits() {
        assertTrue(PhoneText.fits("😀😀", 2));
        assertFalse(PhoneText.fits("abc", 2));
        assertEquals("ab…", PhoneText.preview("abcdef", 3));
        assertEquals("a b", PhoneText.preview("a\nb", 10));
    }

    @Test
    public void playerNames() {
        assertTrue(PhoneText.validName("Steve_01"));
        assertFalse(PhoneText.validName("bad name"));
        assertFalse(PhoneText.validName("seventeen_chars__"));
        assertFalse(PhoneText.validName(""));
        assertFalse(PhoneText.validName(null));
    }
}
