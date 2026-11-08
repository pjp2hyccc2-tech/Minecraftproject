package com.eternalrealm.world.map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MapViewTest {
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000002a");

    private static MapView sample() {
        MapView.Dungeon dungeon = new MapView.Dungeon("d3", "dungeon.eternalrealm.d3", 1024, 64, 0,
                new MapView.Rect(10, 10, 20, 20), List.of(new MapView.Rect(40, 10, 60, 30)),
                List.of(new MapView.Rect(21, 13, 39, 17)), new MapView.Rect(100, 100, 130, 130), 0, false,
                List.of(new MapView.Trap(1050.5, 20.5, "sand_geyser")));
        return new MapView(new MapView.Realm("eternalrealm:astoria", 0, 0, true), Set.of(1, 2, 3), Set.of(1, 2), false,
                List.of(new MapView.Member(MEMBER, "Alice", "eternalrealm:astoria", 120.25, 70.0, -40.5, 90.0F, 0.75F,
                        true)),
                List.of(new MapView.Npc(10.5, 65.0, -9.5, "story", "{\"translate\":\"npc.eternalrealm.story\"}",
                        MapView.Mark.OFFER)),
                Optional.of(dungeon));
    }

    @Test
    public void roundTrip() {
        MapView view = sample();
        JsonObject json = JsonParser.parseString(view.toJson().toString()).getAsJsonObject();
        MapView back = MapView.fromJson(json);
        assertEquals(view.realm(), back.realm());
        assertEquals(view.unlockedAreas(), back.unlockedAreas());
        assertEquals(view.discoveredAreas(), back.discoveredAreas());
        assertEquals(1, back.party().size());
        MapView.Member member = back.party().get(0);
        assertEquals(MEMBER, member.id());
        assertEquals("Alice", member.name());
        assertEquals(120.3, member.x(), 1.0E-9); // positions are rounded to 0.1 blocks
        assertEquals(0.75F, member.health(), 1.0E-6F);
        assertTrue(member.leader());
        assertEquals(MapView.Mark.OFFER, back.npcs().get(0).mark());
        assertEquals(view.npcs().get(0).name(), back.npcs().get(0).name());
        assertEquals(view.dungeon(), back.dungeon());
        assertTrue(back.isUnlocked(3));
        assertFalse(back.isUnlocked(4));
        assertTrue(back.isDiscovered(2));
    }

    @Test
    public void brokenDocumentsBecomeDefaults() {
        MapView empty = MapView.fromJson(new JsonObject());
        assertEquals(MapView.Realm.DEFAULT, empty.realm());
        assertEquals("the first area is always open", Set.of(1), empty.unlockedAreas());
        assertTrue(empty.party().isEmpty());
        assertTrue(empty.dungeon().isEmpty());
        JsonObject broken = JsonParser.parseString("{\"realm\":7,\"unlocked\":[\"x\",2],\"party\":[{\"id\":\"nope\"},"
                + "{\"id\":\"00000000-0000-0000-0000-000000000001\",\"x\":\"far\",\"hp\":9}],\"npcs\":[3],"
                + "\"dungeon\":{\"hall\":[5,5,1,1],\"arena\":[0,0,1,1]}}").getAsJsonObject();
        MapView view = MapView.fromJson(broken);
        assertEquals(MapView.Realm.DEFAULT, view.realm());
        assertEquals(Set.of(2), view.unlockedAreas());
        assertEquals(1, view.party().size());
        assertEquals(0.0, view.party().get(0).x(), 0.0);
        assertEquals(1.0F, view.party().get(0).health(), 0.0F);
        assertTrue(view.npcs().isEmpty());
        assertTrue("an inverted rectangle is no dungeon", view.dungeon().isEmpty());
    }

    @Test
    public void listsAreCapped() {
        StringBuilder npcs = new StringBuilder("{\"npcs\":[");
        for (int i = 0; i < MapView.MAX_NPCS + 10; i++) {
            npcs.append(i == 0 ? "" : ",").append("{\"x\":").append(i).append(",\"role\":\"story\"}");
        }
        npcs.append("]}");
        assertEquals(MapView.MAX_NPCS, MapView.fromJson(JsonParser.parseString(npcs.toString()).getAsJsonObject())
                .npcs().size());
    }

    @Test
    public void rectanglesAreInclusive() {
        MapView.Rect rect = new MapView.Rect(0, 0, 9, 4);
        assertTrue(rect.contains(9.9, 4.9));
        assertFalse(rect.contains(10.0, 2.0));
        assertFalse(rect.contains(-0.1, 2.0));
    }
}
