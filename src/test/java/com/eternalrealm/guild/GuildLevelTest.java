package com.eternalrealm.guild;

import com.eternalrealm.season.SeasonId;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Guild levels: merit sources, perks by level, guild-wide merit and the level-up event (2026-11-15). */
public class GuildLevelTest {
    private static final UUID MASTER = new UUID(5, 1);

    @Test
    public void meritOfDeeds() {
        assertEquals(1, GuildMerit.monster(false));
        assertEquals(3, GuildMerit.monster(true));
        assertEquals(50, GuildMerit.dungeon("NORMAL", false));
        assertEquals(80, GuildMerit.dungeon("hard", false));
        assertEquals(120, GuildMerit.dungeon("EX", false));
        assertEquals(300, GuildMerit.dungeon("EX", true));
        assertEquals(0, GuildMerit.donation(99));
        assertEquals(12, GuildMerit.donation(1_250));
        assertEquals(0, GuildMerit.donation(-5));
        assertEquals(GuildMerit.BATTLE_WIN, GuildMerit.battle(5, 3));
        assertEquals(GuildMerit.BATTLE_DRAW, GuildMerit.battle(4, 4));
        assertEquals(GuildMerit.BATTLE_LOSS, GuildMerit.battle(1, 3));
    }

    @Test
    public void perksGrowWithTheLevel() {
        assertEquals(0.0, GuildPerks.experienceBonus(1), 1e-12);
        assertEquals(0.045, GuildPerks.experienceBonus(10), 1e-12);
        assertEquals(0.095, GuildPerks.experienceBonus(20), 1e-12);
        assertEquals(0.0, GuildPerks.houseDiscount(4), 1e-12);
        assertEquals(0.05, GuildPerks.houseDiscount(5), 1e-12);
        assertEquals(0.10, GuildPerks.houseDiscount(14), 1e-12);
        assertEquals(0.20, GuildPerks.houseDiscount(20), 1e-12);
        assertEquals(90_000, GuildPerks.discounted(100_000, 10));
        assertEquals(18_000, GuildPerks.discounted(18_000, 1));
        assertEquals(1, GuildPerks.discounted(1, 20));
        assertEquals(GuildPerks.Standing.FLEDGLING, GuildPerks.standing(4));
        assertEquals(GuildPerks.Standing.SEASONED, GuildPerks.standing(5));
        assertEquals(GuildPerks.Standing.ELITE, GuildPerks.standing(12));
        assertEquals(GuildPerks.Standing.RENOWNED, GuildPerks.standing(19));
        assertEquals(GuildPerks.Standing.LEGENDARY, GuildPerks.standing(20));
        assertEquals("9.5%", GuildPerks.percent(0.095));
        assertEquals("10%", GuildPerks.percent(0.1));
        List<GuildPerks.Perk> perks = GuildPerks.at(20, GuildRules.defaults());
        assertEquals("68", perks.get(0).value());
        assertEquals("legendary", perks.get(3).value());
    }

    @Test
    public void guildMeritRaisesTheLevel() {
        GuildService service = new GuildService(GuildRules.defaults(), new SplittableRandom(1), List.of());
        Instant now = Instant.parse("2026-11-15T00:00:00Z");
        Guild guild = service.create(MASTER, "Lanterns", "LTN", now).guild().orElseThrow();
        SeasonId season = SeasonId.of("s1");
        assertEquals(1, service.levelOf(guild.id()));
        GuildResult first = service.addGuildContribution(guild.id(), season, GuildMerit.BATTLE_WIN);
        assertTrue(first.events().get(0) instanceof GuildEvent.LevelUp);
        assertEquals(2, service.levelOf(guild.id()));
        GuildResult member = service.addContribution(MASTER, season, 1_000);
        assertEquals(3, ((GuildEvent.LevelUp) member.events().get(0)).newLevel());
        assertEquals(GuildOutcome.NOT_IN_GUILD,
                service.addGuildContribution(new UUID(6, 6), season, 5).outcome());
        assertEquals(1, service.levelOf(new UUID(6, 6)));
    }
}
