package com.eternalrealm.guild;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class GuildNamesTest {
    private static boolean valid(String raw) {
        return GuildNames.isValidName(GuildNames.normalizeName(raw));
    }

    @Test
    public void lettersOfAnyScriptDigitsSpacesUnderscoresAndHyphensAreAllowed() {
        for (String name : List.of("黒猫団", "Astria Knights", "ab", "暁の騎士団-02", "ギルド_ワン", "ひかり", "スーパーノヴァ",
                "々木", "ｶﾞﾙﾑ", "𠮷野家", "Ébène", "A-B_C 1", "1234567890123456")) {
            assertTrue(name, valid(name));
        }
    }

    @Test
    public void lengthCountsCharactersNotUtf16Units() {
        assertTrue("16 kanji", valid("一二三四五六七八九十一二三四五六"));
        assertFalse("17 kanji", valid("一二三四五六七八九十一二三四五六七"));
        assertTrue("supplementary characters count once", valid("𠮷𠮷𠮷𠮷𠮷𠮷𠮷𠮷𠮷𠮷𠮷𠮷𠮷𠮷𠮷𠮷"));
        assertFalse(valid("a"));
        assertFalse(valid(""));
        assertFalse(valid("12345678901234567"));
    }

    @Test
    public void spacesAndSymbolsAreRestricted() {
        for (String name : List.of(" 黒猫団", "黒猫団 ", "黒猫  団", "黒猫・団", "Guild!", "a.b", "猫😺", "猫　団",
                "ab‍", "ab\t", "猫‮団", "<b>ab</b>")) {
            assertFalse(name, valid(name));
        }
        assertFalse(GuildNames.isValidName(null));
    }

    @Test
    public void inputIsComposedButStoredNamesMustAlreadyBeComposed() {
        String decomposed = "がき";
        assertEquals("がき", GuildNames.normalizeName(decomposed));
        assertTrue(valid(decomposed));
        assertFalse("combining marks are not letters", GuildNames.isValidName(decomposed));
    }

    @Test
    public void nameKeysIgnoreCaseAndWidth() {
        assertEquals(GuildNames.nameKey("abc"), GuildNames.nameKey("ABC"));
        assertEquals(GuildNames.nameKey("abc"), GuildNames.nameKey("ＡＢＣ"));
        assertEquals(GuildNames.nameKey("ガルム"), GuildNames.nameKey("ｶﾞﾙﾑ"));
        assertEquals(GuildNames.nameKey("Team 1"), GuildNames.nameKey("ＴＥＡＭ １"));
        assertNotEquals(GuildNames.nameKey("ガルム"), GuildNames.nameKey("がるむ"));
        assertNotEquals(GuildNames.nameKey("ab"), GuildNames.nameKey("a b"));
    }

    @Test
    public void tagsAreTwoToFiveAsciiLettersOrDigitsStoredUpperCase() {
        assertEquals(Optional.of("CAT"), GuildNames.normalizeTag("cat"));
        assertEquals(Optional.of("A1B2C"), GuildNames.normalizeTag("a1B2c"));
        assertEquals(Optional.of("AB"), GuildNames.normalizeTag("AB"));
        for (String tag : List.of("A", "ABCDEF", "A-B", "A B", "ＡＢ", "ßß", "猫猫", "")) {
            assertEquals(tag, Optional.empty(), GuildNames.normalizeTag(tag));
        }
        assertEquals(Optional.empty(), GuildNames.normalizeTag(null));
        assertTrue(GuildNames.isValidTag("CAT"));
        assertFalse("stored tags are upper case", GuildNames.isValidTag("cat"));
        assertFalse(GuildNames.isValidTag(null));
    }
}
