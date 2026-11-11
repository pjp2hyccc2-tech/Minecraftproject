package com.eternalrealm.item;

import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.WeaponType;
import org.junit.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ArtChoreographyTest {
    private static final Set<ArtChoreography.Strike> BOW = EnumSet.of(ArtChoreography.Strike.LOOSE,
            ArtChoreography.Strike.LOOSE_HEAVY, ArtChoreography.Strike.LOOSE_SWEEP, ArtChoreography.Strike.LOOSE_SKY);

    private static boolean isCast(ArtChoreography.Strike strike) {
        return strike.name().startsWith("CAST_");
    }

    @Test
    public void everyFormHasItsOwnChoreography() {
        Set<String> seen = new HashSet<>();
        for (ArtForge.Form form : ArtForge.Form.values()) {
            ArtChoreography.Choreo choreo = ArtChoreography.of(form);
            assertNotNull(form + " has no choreography", choreo);
            assertTrue(form + " moves like another form of its weapon", seen.add(form.type() + "|" + choreo.windup()
                    + "|" + choreo.strikes() + "|" + choreo.finish()));
        }
    }

    @Test
    public void everyArtStrikesOncePerHitWithItsWeaponsMoves() {
        int checked = 0;
        for (WeaponArt art : GeneratedArts.all()) {
            ArtChoreography.Choreo choreo = ArtChoreography.forAction(art.id()).orElseThrow(() ->
                    new AssertionError(art.id() + " has no choreography"));
            assertEquals(art.id() + ": one strike per hit", art.action().frames().size(), choreo.strikes().size());
            for (ArtChoreography.Strike strike : choreo.strikes()) {
                assertEquals(art.id() + ": " + strike, art.type() == WeaponType.BOW, BOW.contains(strike));
                assertEquals(art.id() + ": " + strike, art.type() == WeaponType.STAFF, isCast(strike));
                assertNotNull(strike.trail());
            }
            checked++;
        }
        assertEquals("all the generated arts", GeneratedArts.all().size(), checked);
        assertTrue(checked > 200);
        for (WeaponArt art : WeaponArts.all()) {
            ArtChoreography.Choreo choreo = ArtChoreography.forAction(art.id()).orElseThrow();
            assertEquals(art.id(), art.action().frames().size(), choreo.strikes().size());
            assertTrue(art.id() + ": boss arts land heavily", choreo.heavy());
        }
        for (WeaponProfile profile : WeaponProfiles.all()) {
            assertTrue(profile.skill().id(), ArtChoreography.forAction(profile.skill().id()).isPresent());
        }
        assertTrue(ArtChoreography.forAction("sword_1").isEmpty());
        assertTrue(ArtChoreography.forAction(null).isEmpty());
    }

    @Test
    public void artsOfOneFormCutBothWays() {
        Map<ArtForge.Form, Set<List<ArtChoreography.Strike>>> ways = new HashMap<>();
        for (WeaponArt art : GeneratedArts.all()) {
            ArtForge.Form form = GeneratedArts.form(art.id()).orElseThrow();
            ways.computeIfAbsent(form, f -> new HashSet<>())
                    .add(ArtChoreography.forAction(art.id()).orElseThrow().strikes());
        }
        assertEquals("both ways round", 2, ways.get(ArtForge.Form.SWORD_COMBO).size());
        assertEquals(2, ways.get(ArtForge.Form.TWIN_FLURRY).size());
        assertEquals("a thrust is a thrust either way", 1, ways.get(ArtForge.Form.SPEAR_LANCE).size());
        assertEquals(ArtChoreography.Strike.SLASH_LEFT, ArtChoreography.Strike.SLASH_RIGHT.mirrored());
        assertEquals(ArtChoreography.Strike.LUNGE, ArtChoreography.Strike.LUNGE.mirrored());
        assertFalse(ArtChoreography.of(ArtForge.Form.SWORD_COMBO).mirrored().equals(
                ArtChoreography.of(ArtForge.Form.SWORD_COMBO)));
    }

    @Test
    public void theTimelineStrikesOnTheHitTicks() {
        int[] hits = {5, 10, 16};
        assertEquals(ArtTimeline.Phase.WINDUP, ArtTimeline.at(hits, 24, 0.0F).phase());
        assertEquals(ArtTimeline.Phase.WINDUP, ArtTimeline.at(hits, 24, 1.9F).phase());
        ArtTimeline.Moment first = ArtTimeline.at(hits, 24, 2.0F);
        assertEquals(ArtTimeline.Phase.STRIKE, first.phase());
        assertEquals(0, first.hit());
        assertEquals(0.0F, first.progress(), 1.0E-6F);
        ArtTimeline.Moment onHit = ArtTimeline.at(hits, 24, 5.0F);
        assertEquals(ArtTimeline.Phase.STRIKE, onHit.phase());
        assertEquals("the blade is at 3/4 of its arc on the hit tick", 0.75F, onHit.progress(), 1.0E-6F);
        ArtTimeline.Moment between = ArtTimeline.at(hits, 24, 6.5F);
        assertEquals(ArtTimeline.Phase.CHAMBER, between.phase());
        assertEquals("chambering the second", 1, between.hit());
        assertEquals(1, ArtTimeline.at(hits, 24, 9.0F).hit());
        assertEquals(ArtTimeline.Phase.STRIKE, ArtTimeline.at(hits, 24, 15.0F).phase());
        assertEquals(2, ArtTimeline.at(hits, 24, 15.0F).hit());
        assertEquals(ArtTimeline.Phase.FINISH, ArtTimeline.at(hits, 24, 18.0F).phase());
        assertEquals(ArtTimeline.Phase.RECOVER, ArtTimeline.at(hits, 24, 23.0F).phase());
        assertEquals(1.0F, ArtTimeline.at(hits, 24, 40.0F).progress(), 1.0E-6F);
    }

    @Test
    public void fastHitsGetShortStrikesWithoutGaps() {
        int[] hits = {3, 6, 9, 12, 15, 18};
        for (int k = 0; k < hits.length; k++) {
            assertEquals(2, ArtTimeline.lead(hits, k));
            assertEquals(ArtTimeline.Phase.STRIKE, ArtTimeline.at(hits, 24, hits[k]).phase());
            assertEquals(k, ArtTimeline.at(hits, 24, hits[k]).hit());
        }
        assertEquals("a hit on the very first tick still has a strike", 1, ArtTimeline.lead(new int[]{1}, 0));
    }
}
