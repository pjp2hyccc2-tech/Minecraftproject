package com.eternalrealm.boss;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContributionsTest {
    private static final UUID KNIGHT = new UUID(0, 1);
    private static final UUID MAGE = new UUID(0, 2);
    private static final UUID PRIEST = new UUID(0, 3);

    @Test
    public void rankingFollowsDamage() {
        Contributions contributions = new Contributions();
        contributions.damage(KNIGHT, "Knight", "knight", 300);
        contributions.damage(MAGE, "Mage", "mage", 600);
        contributions.damage(KNIGHT, "Knight", "knight", 100);
        List<Contributions.Line> lines = contributions.byDamage();
        assertEquals(MAGE, lines.get(0).id());
        assertEquals(KNIGHT, lines.get(1).id());
        assertEquals(0.6, contributions.share(MAGE), 1e-9);
        assertEquals(0.4, lines.get(1).share(), 1e-9);
        assertEquals(1, contributions.rank(MAGE));
        assertEquals(2, contributions.rank(KNIGHT));
        assertEquals(0, contributions.rank(PRIEST));
        assertEquals(1000.0, contributions.totalDamage(), 1e-9);
    }

    @Test
    public void firstStrikeAndLastHitAreBonuses() {
        Contributions contributions = new Contributions();
        contributions.damage(KNIGHT, "Knight", "knight", 500);
        contributions.damage(MAGE, "Mage", "mage", 500);
        contributions.lastHit(MAGE);
        assertEquals(30.0 + 4.0, contributions.score(KNIGHT), 1e-9);
        assertEquals(30.0 + 8.0, contributions.score(MAGE), 1e-9);
        assertEquals(MAGE, contributions.hero().orElseThrow().id());
        assertTrue(contributions.line(KNIGHT).orElseThrow().firstStrike());
        assertTrue(contributions.line(MAGE).orElseThrow().lastHit());
    }

    @Test
    public void tanksAndHealersCanBeTheHero() {
        Contributions contributions = new Contributions();
        contributions.damage(MAGE, "Mage", "mage", 550);
        contributions.damage(KNIGHT, "Knight", "knight", 450);
        contributions.taken(KNIGHT, "Knight", "knight", 900);
        contributions.taken(MAGE, "Mage", "mage", 100);
        contributions.healing(PRIEST, "Priest", "priest", 800);
        contributions.fell(MAGE, "Mage", "mage");
        // knight: 27 + 18 = 45; mage: 33 + 2 + 4 (first strike) - 2 = 37; priest: 20 (no damage: never hero (殊勲))
        assertEquals(45.0, contributions.score(KNIGHT), 1e-9);
        assertEquals(37.0, contributions.score(MAGE), 1e-9);
        assertEquals(20.0, contributions.score(PRIEST), 1e-9);
        assertEquals(KNIGHT, contributions.hero().orElseThrow().id());
        assertEquals(List.of(KNIGHT, MAGE, PRIEST), contributions.byScore().stream().map(Contributions.Line::id).toList());
    }

    @Test
    public void fallsCostAtMostSixPoints() {
        Contributions contributions = new Contributions();
        contributions.damage(KNIGHT, "Knight", "knight", 100);
        for (int i = 0; i < 10; i++) {
            contributions.fell(KNIGHT, "Knight", "knight");
        }
        assertEquals(60.0 + 4.0 - 6.0, contributions.score(KNIGHT), 1e-9);
    }

    @Test
    public void nothingCountsBeforeTheFirstHit() {
        Contributions contributions = new Contributions();
        assertTrue(contributions.isEmpty());
        assertFalse(contributions.hero().isPresent());
        contributions.damage(KNIGHT, "Knight", "knight", 0);
        contributions.lastHit(MAGE); // not a participant: ignored
        assertTrue(contributions.isEmpty());
        assertEquals(0.0, contributions.share(KNIGHT), 1e-9);
    }
}
