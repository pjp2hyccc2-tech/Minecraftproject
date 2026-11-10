package com.eternalrealm.hud;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HudLayoutTest {
    /** GUI sizes of common windows and GUI scales (1280x720, 1366x768, 1600x900, 1920x1080, 2560x1440, ...). */
    private static final int[][] SCREENS = {
            {320, 240}, {400, 240}, {427, 240}, {455, 256}, {480, 270}, {512, 288}, {533, 300}, {480, 300},
            {640, 360}, {683, 384}, {640, 400}, {800, 450}, {853, 480}, {960, 540}, {1280, 720}};

    private static HudLayout.Input random(Random random, int width, int height) {
        int[] minimaps = {0, 64, 96, 128, 192};
        int[][] chats = {{328, 90}, {328, 180}, {208, 50}, {408, 90}, {168, 20}};
        int[] chat = chats[random.nextInt(chats.length)];
        boolean raid = random.nextInt(4) == 0;
        return HudLayout.Input.of(width, height)
                .withChat(chat[0], chat[1])
                .withMinimap(minimaps[random.nextInt(minimaps.length)], 1 + random.nextInt(3))
                .withThreat(random.nextBoolean(), random.nextInt(4) == 0)
                .withBoss(random.nextBoolean(), random.nextInt(11))
                .withParty(random.nextInt(3) == 0 ? 0 : raid ? 1 + random.nextInt(20) : 1 + random.nextInt(5), raid)
                .withPlayer(random.nextBoolean(), random.nextInt(5) == 0, random.nextInt(4) == 0 ? 0 : random.nextInt(30))
                .withDialogue(random.nextBoolean())
                .withSubtitles(random.nextInt(5) == 0);
    }

    @Test
    public void nothingOverlapsOnAnyScreen() {
        Random random = new Random(20261110L);
        for (int[] screen : SCREENS) {
            for (int i = 0; i < 4000; i++) {
                HudLayout.Input input = random(random, screen[0], screen[1]);
                HudLayout.Layout layout = HudLayout.compute(input);
                List<Rect> parts = layout.elements();
                for (Rect part : parts) {
                    assertTrue(input + ": off screen " + part, part.within(screen[0], screen[1]));
                }
                for (int a = 0; a < parts.size(); a++) {
                    for (int b = a + 1; b < parts.size(); b++) {
                        if (parts.get(a).intersects(parts.get(b))) {
                            fail(input + ": " + parts.get(a) + " overlaps " + parts.get(b) + "\n" + layout);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void onRoomyScreensNothingCoversTheChatOrTheActionBar() {
        Random random = new Random(7L);
        for (int[] screen : SCREENS) {
            if (screen[0] < HudLayout.ROOMY_WIDTH || screen[1] < 240) {
                continue;
            }
            for (int i = 0; i < 4000; i++) {
                HudLayout.Input input = random(random, screen[0], screen[1]).withChat(328, 90);
                HudLayout.Layout layout = HudLayout.compute(input);
                for (Rect part : layout.elements()) {
                    if (part.equals(layout.dialogue()) && layout.dialogueFromBottom()) {
                        continue; // only on screens too narrow for the top box
                    }
                    assertFalse(input + ": " + part + " covers the chat " + layout.chat(), part.intersects(layout.chat()));
                    assertFalse(input + ": " + part + " covers the action bar", part.intersects(layout.actionBar()));
                }
            }
        }
    }

    @Test
    public void theSkillBarHoldsWeaponJobAndQuickSlotsInOrder() {
        HudLayout.Layout layout = HudLayout.compute(HudLayout.Input.of(480, 270));
        assertEquals(HudLayout.JOB_SLOTS, layout.jobSlots().size());
        assertEquals(HudLayout.QUICK_SLOTS, layout.quickSlots().size());
        List<Rect> slots = new ArrayList<>();
        slots.add(layout.weaponSkill());
        slots.addAll(layout.jobSlots());
        slots.addAll(layout.quickSlots());
        for (int i = 0; i < slots.size(); i++) {
            Rect slot = slots.get(i);
            assertTrue(slot + " outside the bar", slot.x() >= layout.skillBar().x()
                    && slot.right() <= layout.skillBar().right() && slot.y() == layout.skillBar().y());
            if (i > 0) {
                assertTrue("slots in order", slot.x() > slots.get(i - 1).right());
            }
        }
        // centred at the bottom, the ultimate a little apart
        assertEquals(480 - layout.skillBar().right(), layout.skillBar().x());
        assertEquals(270 - HudLayout.MARGIN, layout.skillBar().bottom());
        Rect fifth = layout.jobSlots().get(4);
        Rect ultimate = layout.jobSlots().get(5);
        assertEquals(HudLayout.SLOT_GAP + HudLayout.ULTIMATE_GAP, ultimate.x() - fifth.right());
        // the weapon skill's name sits right above it, below the chat
        assertEquals(layout.weaponSkill().x(), layout.weaponLabel().x());
        assertTrue(layout.weaponLabel().bottom() <= layout.skillBar().y());
        assertTrue(layout.weaponLabel().y() >= layout.chat().bottom());
    }

    @Test
    public void theBagIsInTheBottomRightCornerWithTheEquipmentAboveIt() {
        HudLayout.Layout layout = HudLayout.compute(HudLayout.Input.of(640, 360));
        assertEquals(640 - HudLayout.MARGIN, layout.bag().right());
        assertEquals(360 - HudLayout.MARGIN, layout.bag().bottom());
        assertEquals(layout.bag().x(), layout.equipment().x());
        assertEquals(layout.bag().y() - HudLayout.GAP, layout.equipment().bottom());
        // the pickup feed stands right above the equipment icon
        assertEquals(layout.equipment().y() - HudLayout.GAP, layout.pickupFeed().bottom());
        assertEquals(HudLayout.FEED_ROWS * HudLayout.FEED_ROW, layout.pickupFeed().height());
        // subtitles push the icons up
        HudLayout.Layout subtitles = HudLayout.compute(HudLayout.Input.of(640, 360).withSubtitles(true));
        assertEquals(layout.bag().y() - HudLayout.SUBTITLE_SHIFT, subtitles.bag().y());
    }

    @Test
    public void theBossHudSitsBetweenTheColumnsOrAboveThemOnNarrowScreens() {
        HudLayout.Layout wide = HudLayout.compute(HudLayout.Input.of(480, 270).withBoss(true, 5));
        assertEquals(HudLayout.BOSS_TOP, wide.boss().y());
        assertEquals(480 - wide.boss().right(), wide.boss().x());
        assertTrue(wide.boss().width() >= HudLayout.BOSS_MIN_WIDTH);
        assertEquals(HudLayout.MARGIN, wide.player().y());
        assertTrue(wide.rankingRows() > 0);
        assertEquals(wide.ranking().height(), HudLayout.RANKING_HEADER + wide.rankingRows() * HudLayout.RANKING_ROW);

        HudLayout.Layout narrow = HudLayout.compute(HudLayout.Input.of(320, 240).withBoss(true, 5));
        assertEquals(HudLayout.MARGIN, narrow.boss().y());
        assertTrue("the columns move below the boss HUD", narrow.player().y() >= narrow.boss().bottom());
        assertTrue(narrow.minimap().isEmpty() || narrow.minimap().y() >= narrow.boss().bottom());
    }

    @Test
    public void theDialogueStaysAboveTheTitleAndBelowTheBoss() {
        HudLayout.Layout layout = HudLayout.compute(HudLayout.Input.of(640, 360).withDialogue(true));
        assertFalse(layout.dialogueFromBottom());
        assertTrue(layout.dialogue().bottom() <= 360 / 2 - HudLayout.TITLE_CLEARANCE);
        assertTrue(layout.dialogue().width() >= HudLayout.DIALOGUE_MIN_WIDTH);
        HudLayout.Layout boss = HudLayout.compute(HudLayout.Input.of(640, 360).withDialogue(true).withBoss(true, 3));
        assertTrue(boss.dialogue().y() >= boss.boss().bottom());
        assertTrue(boss.dialogue().height() >= HudLayout.DIALOGUE_MIN_HEIGHT);
    }

    @Test
    public void partyFramesShrinkThenSplitBeforeReachingTheChat() {
        HudLayout.Layout five = HudLayout.compute(HudLayout.Input.of(640, 360).withParty(5, false));
        assertEquals(5, five.partyShown());
        assertEquals(HudLayout.PARTY_ROW, five.partyRow());
        assertEquals(HudLayout.PARTY_WIDTH, five.party().width());

        HudLayout.Layout raid = HudLayout.compute(HudLayout.Input.of(480, 270).withParty(20, true));
        assertEquals(HudLayout.RAID_SHOWN, raid.partyShown());
        assertTrue(raid.party().bottom() <= raid.chat().y());
        assertTrue(raid.partyRow() == HudLayout.PARTY_ROW_COMPACT || raid.party().width() > HudLayout.PARTY_WIDTH);
    }

    @Test
    public void effectsWrapUnderThePlayerFrame() {
        HudLayout.Layout none = HudLayout.compute(HudLayout.Input.of(480, 270));
        assertTrue(none.effects().isEmpty());
        HudLayout.Layout many = HudLayout.compute(HudLayout.Input.of(480, 270).withPlayer(true, true, 30));
        assertEquals(HudLayout.EFFECT_ROWS * HudLayout.EFFECT, many.effects().height());
        assertEquals(many.player().bottom() + 2, many.effects().y());
        assertEquals(HudLayout.PLAYER_BASE + HudLayout.AIR_ROW + HudLayout.POINTS_ROW, many.player().height());
    }

    @Test
    public void freeWidthKeepsAGapAndIsZeroWhenTheCentreIsTaken() {
        List<Rect> placed = List.of(new Rect(4, 4, 100, 30), new Rect(400, 4, 76, 76));
        // left edge 108, right edge 396 -> centred on 240: min(132, 156) * 2
        assertEquals(264, HudLayout.freeWidth(placed, 6, 40, 480));
        assertEquals(472, HudLayout.freeWidth(placed, 100, 120, 480));
        assertEquals(0, HudLayout.freeWidth(List.of(new Rect(200, 10, 80, 10)), 0, 40, 480));
    }
}
