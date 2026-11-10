package com.eternalrealm.npc;

import com.eternalrealm.story.StoryNpc;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Who stands at the capital's crowd and guard spots, what they are called and what they say; who wears which body. */
public class TownsfolkTest {
    private static final Path I18N = Paths.get("docs", "i18n", "npc_bodies.json");
    private static final Path PORTRAITS = Paths.get("src", "main", "resources", "assets", "eternalrealm", "textures", "gui",
            "portrait");

    private static JsonObject i18n() throws IOException {
        return JsonParser.parseString(Files.readString(I18N, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    public void parsesCrowdAndGuardSpots() {
        Townsfolk.Spot market = Townsfolk.parse("crowd.market.12").orElseThrow();
        assertEquals(Townsfolk.Kind.CROWD, market.kind());
        assertEquals("market", market.area());
        assertEquals(12, market.number());
        Townsfolk.Spot wall = Townsfolk.parse("guard.wall_north.3").orElseThrow();
        assertEquals(Townsfolk.Kind.GUARD, wall.kind());
        assertEquals("wall_north", wall.area());
        Townsfolk.Spot named = Townsfolk.parse("crowd.harbour.fisher").orElseThrow();
        assertEquals("harbour", named.area());
        assertEquals(-1, named.number());
        assertEquals(Townsfolk.Kind.GUARD, Townsfolk.parse("castle.gate_guard_w").orElseThrow().kind());
        assertTrue(Townsfolk.parse("npc.quest_board").isEmpty());
        assertTrue(Townsfolk.parse("story.lilia").isEmpty());
        assertTrue(Townsfolk.parse("crowd.").isEmpty());
        assertTrue(Townsfolk.parse(null).isEmpty());
        assertFalse(Townsfolk.isSpot("spawn"));
    }

    @Test
    public void appearancesAreDeterministicAndVaried() {
        for (String area : Townsfolk.areas()) {
            Set<String> seen = new HashSet<>();
            for (int n = 1; n <= 6; n++) {
                String spot = "crowd." + area + "." + n;
                String look = Townsfolk.appearanceFor(spot);
                assertEquals("deterministic", look, Townsfolk.appearanceFor(spot));
                assertTrue(spot + " -> " + look, NpcBodies.exists(look));
                seen.add(look);
            }
            assertTrue(area + " looks too uniform: " + seen, seen.size() >= 3);
        }
        assertNotEquals("areas start at different people", Townsfolk.appearanceFor("crowd.plaza.1"),
                Townsfolk.appearanceFor("crowd.residential.1"));
        assertEquals(NpcBodies.DEFAULT, Townsfolk.appearanceFor("spawn"));
    }

    @Test
    public void areasGetSuitablePeople() {
        assertTrue(Townsfolk.pool("harbour").contains("sailor"));
        assertTrue(Townsfolk.pool("harbour_east").contains("sailor2"));
        assertTrue(Townsfolk.pool("farm").contains("farmer"));
        assertTrue(Townsfolk.pool("cathedral").containsAll(List.of("priest", "nun")));
        assertTrue(Townsfolk.pool("castle").containsAll(List.of("maid", "noble_lady", "noble_gent")));
        assertTrue(Townsfolk.pool("guild").contains("adventurer"));
        assertFalse("no sailors on the farm", Townsfolk.pool("farm").contains("sailor"));
        assertFalse("unknown areas get commoners", Townsfolk.pool("somewhere").isEmpty());
        assertEquals("archer", Townsfolk.guardFor("wall_north"));
        assertEquals("archer", Townsfolk.guardFor("tower_east"));
        assertEquals("knight", Townsfolk.guardFor("castle"));
        assertEquals("knight", Townsfolk.guardFor("throne_room"));
        assertEquals("guard", Townsfolk.guardFor("castle_gate"));
        assertEquals("guard", Townsfolk.guardFor("plaza"));
        assertEquals("guard", Townsfolk.appearanceFor("castle.gate_guard_e"));
        assertEquals("archer", Townsfolk.appearanceFor("guard.wall_west.2"));
    }

    @Test
    public void everyTownspersonHasABodyANamePortraitAndGreetings() throws IOException {
        JsonObject texts = i18n();
        JsonObject lang = JsonParser.parseString(Files.readString(Paths.get("src", "main", "resources", "assets",
                "eternalrealm", "lang", "en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        for (String look : Townsfolk.appearances()) {
            assertTrue("no body " + look, NpcBodies.exists(look));
            String name = Townsfolk.nameKey(look);
            assertTrue("no name " + name, texts.has(name) || name.startsWith("story.") && lang.has(name));
            String type = name.substring(name.lastIndexOf('.') + 1);
            assertTrue("no portrait for " + type, Files.isRegularFile(PORTRAITS.resolve(type + ".png")));
            assertTrue(Townsfolk.greetingCount(look) >= 4);
            for (int roll = -3; roll < 12; roll++) {
                String line = Townsfolk.greetingKey(look, roll);
                assertTrue("missing greeting " + line, texts.has(line));
            }
        }
        assertEquals("merchant", Townsfolk.type("merchant2"));
        assertEquals("noble_lady", Townsfolk.type("noble_lady2"));
        assertEquals("citizen", Townsfolk.type("citizen"));
        assertEquals("npc.eternalrealm.folk.citizen", Townsfolk.nameKey("lilia"));
    }

    @Test
    public void theKingLivesInTheThroneRoom() throws IOException {
        Townsfolk.Spot throne = Townsfolk.parse("castle.king").orElseThrow();
        assertEquals(Townsfolk.Kind.RESIDENT, throne.kind());
        assertEquals("king", Townsfolk.appearanceFor("castle.king"));
        assertTrue(Townsfolk.residentSpots().contains("castle.king"));
        String name = Townsfolk.nameKey("king");
        assertEquals("story.eternalrealm.speaker.king", name);
        JsonObject lang = JsonParser.parseString(Files.readString(Paths.get("src", "main", "resources", "assets",
                "eternalrealm", "lang", "ja_jp.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue("the king's name", lang.has(name));
        assertTrue("the king's portrait", Files.isRegularFile(PORTRAITS.resolve("king.png")));
        for (int roll = 0; roll < Townsfolk.greetingCount("king"); roll++) {
            assertTrue(lang.has(Townsfolk.greetingKey("king", roll)));
        }
        assertTrue(Townsfolk.parse("castle.throne").isEmpty());
    }

    @Test
    public void translationsAreCompleteAndUsed() throws IOException {
        JsonObject texts = i18n();
        Set<String> expected = new LinkedHashSet<>(Townsfolk.translationKeys());
        expected.add("entity.eternalrealm.npc");
        assertEquals(expected, texts.keySet());
        for (Map.Entry<String, JsonElement> entry : texts.entrySet()) {
            JsonObject text = entry.getValue().getAsJsonObject();
            assertEquals(entry.getKey(), Set.of("ja", "en"), text.keySet());
            String ja = text.get("ja").getAsString();
            String en = text.get("en").getAsString();
            assertFalse(entry.getKey(), ja.isBlank() || en.isBlank());
            assertTrue(entry.getKey() + " too long for the dialogue box", ja.length() <= 60);
        }
    }

    @Test
    public void namedNpcsWearTheirSpeakersBody() {
        for (StoryNpc npc : StoryNpc.values()) {
            String look = npc.kind() == StoryNpc.Kind.SERVICE ? NpcLooks.forRole(npc.id()) : NpcLooks.forCharacter(npc.id());
            assertEquals(npc.id(), npc.speaker().id(), look);
            assertTrue(NpcBodies.exists(look));
        }
        assertEquals("adel", NpcLooks.forCharacter("arcelia_knight_captain"));
        assertEquals("gant", NpcLooks.forRole("gatekeeper"));
        assertTrue(NpcBodies.exists(NpcLooks.WARDEN));
        for (int area = 1; area <= 8; area++) {
            assertEquals("warden_d" + area, NpcLooks.forEntrance("d" + area));
        }
        assertEquals(NpcLooks.WARDEN, NpcLooks.forEntrance("final"));
        assertEquals(NpcLooks.WARDEN, NpcLooks.forEntrance(null));
        assertEquals(NpcBodies.DEFAULT, NpcLooks.forRole("story"));
        assertEquals(NpcBodies.DEFAULT, NpcLooks.forCharacter(null));
        assertEquals(NpcBodies.DEFAULT, NpcLooks.forCharacter("someone_new"));
    }

    @Test
    public void hashIsStable() {
        assertEquals(0x811C9DC5, Townsfolk.hash(""));
        assertEquals(Townsfolk.hash("market"), Townsfolk.hash("market"));
        assertNotEquals(Townsfolk.hash("market"), Townsfolk.hash("harbour"));
    }
}
