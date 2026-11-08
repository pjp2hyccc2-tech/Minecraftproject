package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.ranking.BossDifficulty;
import com.eternalrealm.ranking.HallOfFame;
import com.eternalrealm.ranking.RankingBoard;
import com.eternalrealm.ranking.RankingFixtures;
import com.eternalrealm.ranking.RankingRules;
import com.eternalrealm.ranking.RankingSnapshot;
import com.eternalrealm.ranking.SeasonArchive;
import com.eternalrealm.ranking.SeasonCounter;
import com.eternalrealm.ranking.SeasonStats;
import com.eternalrealm.ranking.TimeAttackRules;
import com.eternalrealm.season.SeasonId;
import com.eternalrealm.season.SeasonSchedule;
import com.eternalrealm.season.SeasonState;
import com.eternalrealm.season.SeasonStatus;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.eternalrealm.ranking.RankingFixtures.T0;
import static com.eternalrealm.ranking.RankingFixtures.run;
import static com.eternalrealm.ranking.RankingFixtures.uuid;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SeasonJsonCodecsTest {
    private static final SeasonId S1 = SeasonId.of("s1");
    private static final SeasonSchedule SCHEDULE = SeasonSchedule.of(S1, Instant.parse("2026-11-14T01:00:00Z"));
    private static final Instant AT = Instant.parse("2026-11-20T12:34:56.789Z");

    /** Rankings with ranked and unranked players, a guild, time-attack runs and a first clear that is no party best. */
    static RankingSnapshot sampleRankings(boolean frozen) {
        RankingBoard board = new RankingBoard(S1, RankingRules.defaults());
        board.updatePlayer(uuid(1), "Alice", RankingFixtures.statsWith(10, 20_000, SeasonCounter.MONSTERS_KILLED, 300,
                SeasonCounter.BOSSES_KILLED, 2), T0 + 1);
        board.updatePlayer(uuid(2), "Bob", RankingFixtures.distinctStats(3), T0 + 2);
        board.updatePlayer(uuid(3), "Carol", SeasonStats.EMPTY, T0 + 3);
        board.updateGuild("g1", "Knights", 70, T0 + 4);
        TimeAttackRules rules = TimeAttackRules.defaults();
        board.submitTimeAttack(run(1, "b1", BossDifficulty.NORMAL, 3_000, 10, 1), rules);
        board.submitTimeAttack(run(2, "final", BossDifficulty.HARD, 24_000, 30, 1, 2, 3, 4, 5, 6), rules);
        board.submitTimeAttack(run(3, "final", BossDifficulty.HARD, 22_000, 40, 1, 2, 3, 4, 5, 6), rules);
        if (frozen) {
            board.freeze();
        }
        return board.snapshot();
    }

    private static String mutate(String json, Consumer<JsonObject> change) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        change.accept(root);
        return root.toString();
    }

    private static JsonObject firstPlayer(JsonObject root) {
        return root.getAsJsonArray("players").get(0).getAsJsonObject();
    }

    private static JsonObject firstRun(JsonObject root) {
        return root.getAsJsonObject("timeAttack").getAsJsonArray("bestRuns").get(0).getAsJsonObject();
    }

    // ---------------------------------------------------------------------------------------------- rankings

    @Test
    public void rankingsRoundTrip() throws Exception {
        for (boolean frozen : new boolean[]{false, true}) {
            RankingSnapshot snapshot = sampleRankings(frozen);
            String json = SeasonJsonCodecs.encodeRankings(snapshot);
            assertEquals(snapshot, SeasonJsonCodecs.decodeRankings(json, S1));
            assertTrue(json.contains("\"schema\": 1"));
            assertTrue(json.contains("\"seasonId\": \"s1\""));
            assertTrue(json.contains("\"reachedAtMillis\""));
        }
        assertEquals(2, sampleRankings(false).timeAttack().bestRuns().size());
        assertEquals(2, sampleRankings(false).timeAttack().firstClears().size());
    }

    @Test
    public void rankingsOfAnotherSeasonAreCorrupt() {
        String json = SeasonJsonCodecs.encodeRankings(sampleRankings(false));
        assertThrows(CorruptDataException.class, () -> SeasonJsonCodecs.decodeRankings(json, SeasonId.of("s2")));
    }

    @Test
    public void rankingsAreDecodedStrictly() {
        String json = SeasonJsonCodecs.encodeRankings(sampleRankings(false));
        assertCorruptRankings(json.substring(0, json.length() / 2));
        assertCorruptRankings("[]");
        assertCorruptRankings(mutate(json, root -> root.remove("players")));
        assertCorruptRankings(mutate(json, root -> root.addProperty("frozen", "no")));
        assertCorruptRankings(mutate(json, root -> firstPlayer(root).getAsJsonObject("scores").add("pvp", new JsonObject())));
        assertCorruptRankings(mutate(json, root -> firstPlayer(root).addProperty("playerId", "not-a-uuid")));
        assertCorruptRankings(mutate(json, root -> root.getAsJsonArray("players").add(firstPlayer(root))));
        assertCorruptRankings(mutate(json, root -> root.getAsJsonArray("guilds").get(0).getAsJsonObject().addProperty("score", 0)));
        assertCorruptRankings(mutate(json, root -> firstRun(root).addProperty("adminCommandUsed", true)));
        assertCorruptRankings(mutate(json, root -> firstRun(root).addProperty("difficulty", "nightmare")));
        assertCorruptRankings(mutate(json, root -> firstRun(root).addProperty("endTick", 0)));
    }

    @Test
    public void newerRankingSchemaIsIncompatibleNotCorrupt() {
        String json = SeasonJsonCodecs.encodeRankings(sampleRankings(false));
        assertThrows(IncompatibleDataException.class,
                () -> SeasonJsonCodecs.decodeRankings(json.replace("\"schema\": 1", "\"schema\": 2"), S1));
        assertCorruptRankings(json.replace("\"schema\": 1", "\"schema\": 0"));
    }

    @Test
    public void embeddedStatsStayTolerant() throws Exception {
        String json = mutate(SeasonJsonCodecs.encodeRankings(sampleRankings(false)), root -> {
            JsonObject stats = firstPlayer(root).getAsJsonObject("stats");
            stats.remove("pvpLosses");
            stats.addProperty("fishCaught", 3);
        });
        RankingSnapshot decoded = SeasonJsonCodecs.decodeRankings(json, S1);
        assertEquals(0, decoded.players().get(0).stats().pvpLosses());
        assertEquals(300, decoded.players().get(0).stats().monstersKilled());
    }

    private static void assertCorruptRankings(String json) {
        assertThrows(json, CorruptDataException.class, () -> SeasonJsonCodecs.decodeRankings(json, S1));
    }

    // ---------------------------------------------------------------------------------------------- season state

    @Test
    public void seasonStateRoundTripInEveryStatus() throws Exception {
        List<SeasonState> states = List.of(
                SeasonState.preparing(SCHEDULE),
                new SeasonState(SCHEDULE, SeasonStatus.ACTIVE, 3, AT, null, null),
                new SeasonState(SCHEDULE, SeasonStatus.ENDED, 14, null, AT, null),
                new SeasonState(new SeasonSchedule(S1, AT, 1), SeasonStatus.ARCHIVED, 1, AT, AT, AT.plusSeconds(5)));
        for (SeasonState state : states) {
            assertEquals(state, SeasonJsonCodecs.decodeSeasonState(SeasonJsonCodecs.encodeSeasonState(state)));
        }
        String preparing = SeasonJsonCodecs.encodeSeasonState(SeasonState.preparing(SCHEDULE));
        assertTrue(preparing.contains("\"status\": \"preparing\""));
        assertFalse("absent instants are omitted", preparing.contains("endedAt"));
    }

    @Test
    public void seasonStateIsDecodedStrictly() {
        String json = SeasonJsonCodecs.encodeSeasonState(new SeasonState(SCHEDULE, SeasonStatus.ENDED, 14, null, AT, null));
        assertCorruptState(mutate(json, root -> root.addProperty("status", "running")));
        assertCorruptState(mutate(json, root -> root.remove("endedAt")));
        assertCorruptState(mutate(json, root -> root.addProperty("endedAt", "yesterday")));
        assertCorruptState(mutate(json, root -> root.addProperty("lengthDays", 0)));
        assertCorruptState(mutate(json, root -> root.addProperty("lastAnnouncedDay", 15)));
        assertCorruptState(mutate(json, root -> root.addProperty("seasonId", "../s1")));
        assertCorruptState(mutate(json, root -> root.remove("start")));
        assertThrows(IncompatibleDataException.class,
                () -> SeasonJsonCodecs.decodeSeasonState(json.replace("\"schema\": 1", "\"schema\": 2")));
    }

    private static void assertCorruptState(String json) {
        assertThrows(json, CorruptDataException.class, () -> SeasonJsonCodecs.decodeSeasonState(json));
    }

    // ---------------------------------------------------------------------------------------------- hall of fame

    @Test
    public void hallOfFameRoundTrip() throws Exception {
        List<SeasonArchive> archives = List.of(RankingFixtures.archive("s1"), RankingFixtures.archive("s2"),
                new SeasonArchive(SeasonId.of("s3"), AT, Map.of(), List.of(), null));
        HallOfFame decoded = SeasonJsonCodecs.decodeHallOfFame(SeasonJsonCodecs.encodeHallOfFame(archives));
        assertEquals(archives, decoded.archives());
        assertEquals(new HallOfFame(archives).heroes(), decoded.heroes());
        assertEquals(0, SeasonJsonCodecs.decodeHallOfFame(SeasonJsonCodecs.encodeHallOfFame(List.of())).size());
    }

    @Test
    public void hallOfFameIsDecodedStrictly() {
        String json = SeasonJsonCodecs.encodeHallOfFame(List.of(RankingFixtures.archive("s1")));
        String twice = SeasonJsonCodecs.encodeHallOfFame(List.of(RankingFixtures.archive("s1"), RankingFixtures.archive("s1")));
        assertCorruptHall(twice);
        assertCorruptHall(mutate(json, root -> season(root).getAsJsonObject("rankings").getAsJsonArray("level")
                .get(1).getAsJsonObject().addProperty("score", 5_000)));
        assertCorruptHall(mutate(json, root -> season(root).getAsJsonObject("rankings")
                .add("boss_time_attack", new JsonArray())));
        assertCorruptHall(mutate(json, root -> season(root).getAsJsonObject("rankings").add("pvp", new JsonArray())));
        assertCorruptHall(mutate(json, root -> season(root).getAsJsonObject("finalBossFirstClear").addProperty("bossId", "b1")));
        assertCorruptHall(mutate(json, root -> season(root).remove("archivedAt")));
        assertThrows(IncompatibleDataException.class,
                () -> SeasonJsonCodecs.decodeHallOfFame(json.replace("\"schema\": 1", "\"schema\": 2")));
    }

    private static JsonObject season(JsonObject root) {
        return root.getAsJsonArray("seasons").get(0).getAsJsonObject();
    }

    private static void assertCorruptHall(String json) {
        assertThrows(json, CorruptDataException.class, () -> SeasonJsonCodecs.decodeHallOfFame(json));
    }
}
