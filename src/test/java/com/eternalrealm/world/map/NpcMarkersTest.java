package com.eternalrealm.world.map;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The minimap's NPC icons and labels in the capital. */
public class NpcMarkersTest {
    private static final List<String> SERVICES = List.of("quest_board", "item_shop", "blacksmith", "job_master",
            "auction", "mailbox", "guild_registrar", "gatekeeper", "arena_master", "hall_of_fame", "stable_master",
            "innkeeper", "guild_estates");

    @Test
    public void everyServiceHasItsOwnIconAndLabel() throws IOException {
        Set<Integer> icons = new HashSet<>();
        for (String role : SERVICES) {
            assertTrue(role, icons.add(NpcMarkers.icon(role)));
            assertEquals("map.eternalrealm.npc." + role, NpcMarkers.labelKey(role));
        }
        // story characters (and anything unknown) share the speech bubble and carry their own name
        int story = NpcMarkers.icon(NpcMarkers.STORY);
        assertFalse(icons.contains(story));
        assertEquals(story, NpcMarkers.icon("wanderer"));
        assertEquals("", NpcMarkers.labelKey(NpcMarkers.STORY));
        assertEquals("", NpcMarkers.labelKey(null));
        // the atlas drawn by tools/art/map_npc_icons.py has one icon per role, in this order
        Path atlas = Paths.get("src", "main", "resources", "assets", "eternalrealm").resolve(NpcMarkers.ATLAS);
        BufferedImage image = ImageIO.read(atlas.toFile());
        assertEquals(NpcMarkers.ICON_SIZE * NpcMarkers.roles().size(), image.getWidth());
        assertEquals(NpcMarkers.ICON_SIZE, image.getHeight());
        for (int i = 0; i < NpcMarkers.roles().size(); i++) {
            boolean drawn = false;
            for (int x = 0; x < NpcMarkers.ICON_SIZE && !drawn; x++) {
                for (int y = 0; y < NpcMarkers.ICON_SIZE && !drawn; y++) {
                    drawn = (image.getRGB(i * NpcMarkers.ICON_SIZE + x, y) >>> 24) > 0;
                }
            }
            assertTrue("icon " + NpcMarkers.roles().get(i) + " is drawn", drawn);
        }
    }

    @Test
    public void labelsNeverOverlapAndStayOnTheMap() {
        List<float[]> boxes = new ArrayList<>();
        boxes.add(new float[] {10, 10, 30, 16});      // nearest: drawn
        boxes.add(new float[] {20, 12, 40, 18});      // overlaps the first: left out
        boxes.add(new float[] {35, 30, 55, 36});      // free: drawn
        boxes.add(new float[] {90, 30, 130, 36});     // leaves the map (right edge 100): left out
        boxes.add(new float[] {30, 10, 45, 16});      // touches the first only at its edge: drawn
        assertEquals(List.of(0, 2, 4), NpcMarkers.place(boxes, 0, 0, 100, 100));
        List<float[]> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            many.add(new float[] {0, i * 10, 10, i * 10 + 8});
        }
        assertEquals(NpcMarkers.MAX_LABELS, NpcMarkers.place(many, 0, 0, 100, 1000).size());
        assertEquals(List.of(), NpcMarkers.place(List.of(), 0, 0, 1, 1));
    }
}
