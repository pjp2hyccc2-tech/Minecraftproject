package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.ranking.BossDifficulty;
import com.eternalrealm.ranking.GuildStanding;
import com.eternalrealm.ranking.HallOfFame;
import com.eternalrealm.ranking.PlayerStanding;
import com.eternalrealm.ranking.RankingCategory;
import com.eternalrealm.ranking.RankingEntry;
import com.eternalrealm.ranking.RankingSnapshot;
import com.eternalrealm.ranking.SeasonArchive;
import com.eternalrealm.ranking.SeasonStatsCodec;
import com.eternalrealm.ranking.TimeAttackMember;
import com.eternalrealm.ranking.TimeAttackRun;
import com.eternalrealm.ranking.TimeAttackSnapshot;
import com.eternalrealm.season.SeasonId;
import com.eternalrealm.season.SeasonSchedule;
import com.eternalrealm.season.SeasonState;
import com.eternalrealm.season.SeasonStatus;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned JSON encoding of the season and ranking files (formats: docs/modules/ranking.md):
 * {@code seasons/<id>/rankings.json}, {@code season_state.json} and {@code hall_of_fame.json}.
 *
 * <p>Decoding follows the rules of {@link JsonCodecs}: strict (missing fields, wrong types, unknown enum ids, values
 * that violate a record's invariants and a season id that does not match the file are corruption), except for the
 * embedded season stats, which {@link SeasonStatsCodec} reads tolerantly. A newer schema is
 * {@link IncompatibleDataException} and never quarantined. Instants are ISO-8601 strings; fields named
 * {@code ...Millis} are epoch milliseconds of the server wall clock, as in the API.</p>
 */
public final class SeasonJsonCodecs {
    public static final int RANKING_SCHEMA_VERSION = 1;
    public static final int SEASON_STATE_SCHEMA_VERSION = 1;
    public static final int HALL_OF_FAME_SCHEMA_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private SeasonJsonCodecs() {
    }

    // ---------------------------------------------------------------------------------------------- rankings

    public static String encodeRankings(RankingSnapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", RANKING_SCHEMA_VERSION);
        root.addProperty("seasonId", snapshot.seasonId().value());
        root.addProperty("frozen", snapshot.frozen());
        JsonArray players = new JsonArray();
        for (PlayerStanding player : snapshot.players()) {
            players.add(encodePlayer(player));
        }
        root.add("players", players);
        JsonArray guilds = new JsonArray();
        for (GuildStanding guild : snapshot.guilds()) {
            JsonObject json = new JsonObject();
            json.addProperty("guildId", guild.guildId());
            json.addProperty("name", guild.name());
            json.addProperty("score", guild.score());
            json.addProperty("updatedAtMillis", guild.updatedAtMillis());
            guilds.add(json);
        }
        root.add("guilds", guilds);
        JsonObject timeAttack = new JsonObject();
        timeAttack.add("bestRuns", encodeRuns(snapshot.timeAttack().bestRuns()));
        timeAttack.add("firstClears", encodeRuns(snapshot.timeAttack().firstClears()));
        root.add("timeAttack", timeAttack);
        return GSON.toJson(root) + "\n";
    }

    public static RankingSnapshot decodeRankings(String json, SeasonId expectedSeason)
            throws CorruptDataException, IncompatibleDataException {
        JsonObject root = JsonFields.parseObject(json);
        JsonFields.checkSchema(root, RANKING_SCHEMA_VERSION, "ranking");
        try {
            SeasonId seasonId = new SeasonId(JsonFields.requireString(root, "seasonId"));
            if (!seasonId.equals(expectedSeason)) {
                throw new CorruptDataException("File belongs to season " + seasonId + ", expected " + expectedSeason);
            }
            List<PlayerStanding> players = new ArrayList<>();
            for (JsonElement element : JsonFields.requireArray(root, "players")) {
                players.add(decodePlayer(JsonFields.asObject(element, "players[]")));
            }
            List<GuildStanding> guilds = new ArrayList<>();
            for (JsonElement element : JsonFields.requireArray(root, "guilds")) {
                JsonObject guild = JsonFields.asObject(element, "guilds[]");
                guilds.add(new GuildStanding(JsonFields.requireString(guild, "guildId"),
                        JsonFields.requireString(guild, "name"),
                        JsonFields.requireLong(guild, "score"),
                        JsonFields.requireLong(guild, "updatedAtMillis")));
            }
            JsonObject timeAttack = JsonFields.requireObject(root, "timeAttack");
            TimeAttackSnapshot runs = new TimeAttackSnapshot(decodeRuns(timeAttack, "bestRuns"),
                    decodeRuns(timeAttack, "firstClears"));
            return new RankingSnapshot(seasonId, JsonFields.requireBoolean(root, "frozen"), players, guilds, runs);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new CorruptDataException("Invalid ranking data: " + e.getMessage(), e);
        }
    }

    private static JsonObject encodePlayer(PlayerStanding player) {
        JsonObject json = new JsonObject();
        json.addProperty("playerId", player.playerId().toString());
        json.addProperty("name", player.name());
        json.addProperty("updatedAtMillis", player.updatedAtMillis());
        json.add("stats", SeasonStatsCodec.encode(player.stats()));
        JsonObject scores = new JsonObject();
        for (Map.Entry<RankingCategory, PlayerStanding.RankedScore> entry : player.scores().entrySet()) {
            JsonObject score = new JsonObject();
            score.addProperty("score", entry.getValue().score());
            score.addProperty("reachedAtMillis", entry.getValue().reachedAtMillis());
            scores.add(entry.getKey().id(), score);
        }
        json.add("scores", scores);
        return json;
    }

    private static PlayerStanding decodePlayer(JsonObject json) throws CorruptDataException {
        Map<RankingCategory, PlayerStanding.RankedScore> scores = new EnumMap<>(RankingCategory.class);
        for (Map.Entry<String, JsonElement> entry : JsonFields.requireObject(json, "scores").entrySet()) {
            RankingCategory category = category(entry.getKey());
            JsonObject score = JsonFields.asObject(entry.getValue(), "scores." + entry.getKey());
            scores.put(category, new PlayerStanding.RankedScore(JsonFields.requireLong(score, "score"),
                    JsonFields.requireLong(score, "reachedAtMillis")));
        }
        return new PlayerStanding(JsonFields.requireUuid(json, "playerId"),
                JsonFields.requireString(json, "name"),
                SeasonStatsCodec.decode(JsonFields.requireObject(json, "stats")),
                JsonFields.requireLong(json, "updatedAtMillis"),
                scores);
    }

    // ---------------------------------------------------------------------------------------------- season state

    public static String encodeSeasonState(SeasonState state) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SEASON_STATE_SCHEMA_VERSION);
        root.addProperty("seasonId", state.seasonId().value());
        root.addProperty("start", state.schedule().start().toString());
        root.addProperty("lengthDays", state.schedule().lengthDays());
        root.addProperty("status", state.status().id());
        root.addProperty("lastAnnouncedDay", state.lastAnnouncedDay());
        addInstant(root, "finalBossFirstClearAt", state.finalBossFirstClearAt());
        addInstant(root, "endedAt", state.endedAt());
        addInstant(root, "archivedAt", state.archivedAt());
        return GSON.toJson(root) + "\n";
    }

    public static SeasonState decodeSeasonState(String json) throws CorruptDataException, IncompatibleDataException {
        JsonObject root = JsonFields.parseObject(json);
        JsonFields.checkSchema(root, SEASON_STATE_SCHEMA_VERSION, "season state");
        try {
            SeasonSchedule schedule = new SeasonSchedule(new SeasonId(JsonFields.requireString(root, "seasonId")),
                    JsonFields.requireInstant(root, "start"), JsonFields.requireInt(root, "lengthDays"));
            String statusId = JsonFields.requireString(root, "status");
            SeasonStatus status = SeasonStatus.byId(statusId)
                    .orElseThrow(() -> new CorruptDataException("Unknown season status '" + statusId + "'"));
            return new SeasonState(schedule, status, JsonFields.requireInt(root, "lastAnnouncedDay"),
                    JsonFields.optionalInstant(root, "finalBossFirstClearAt"),
                    JsonFields.optionalInstant(root, "endedAt"),
                    JsonFields.optionalInstant(root, "archivedAt"));
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new CorruptDataException("Invalid season state: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------------------------------------- hall of fame

    public static String encodeHallOfFame(List<SeasonArchive> archives) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", HALL_OF_FAME_SCHEMA_VERSION);
        JsonArray seasons = new JsonArray();
        for (SeasonArchive archive : archives) {
            seasons.add(encodeArchive(archive));
        }
        root.add("seasons", seasons);
        return GSON.toJson(root) + "\n";
    }

    public static HallOfFame decodeHallOfFame(String json) throws CorruptDataException, IncompatibleDataException {
        JsonObject root = JsonFields.parseObject(json);
        JsonFields.checkSchema(root, HALL_OF_FAME_SCHEMA_VERSION, "hall of fame");
        try {
            List<SeasonArchive> archives = new ArrayList<>();
            for (JsonElement element : JsonFields.requireArray(root, "seasons")) {
                archives.add(decodeArchive(JsonFields.asObject(element, "seasons[]")));
            }
            return new HallOfFame(archives);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new CorruptDataException("Invalid hall of fame data: " + e.getMessage(), e);
        }
    }

    private static JsonObject encodeArchive(SeasonArchive archive) {
        JsonObject json = new JsonObject();
        json.addProperty("seasonId", archive.seasonId().value());
        json.addProperty("archivedAt", archive.archivedAt().toString());
        JsonObject rankings = new JsonObject();
        for (Map.Entry<RankingCategory, List<RankingEntry<String>>> entry : archive.rankings().entrySet()) {
            JsonArray entries = new JsonArray();
            for (RankingEntry<String> ranked : entry.getValue()) {
                JsonObject line = new JsonObject();
                line.addProperty("rank", ranked.rank());
                line.addProperty("id", ranked.id());
                line.addProperty("name", ranked.name());
                line.addProperty("score", ranked.score());
                entries.add(line);
            }
            rankings.add(entry.getKey().id(), entries);
        }
        json.add("rankings", rankings);
        json.add("timeAttackBests", encodeRuns(archive.timeAttackBests()));
        if (archive.finalBossFirstClear() != null) {
            json.add("finalBossFirstClear", encodeRun(archive.finalBossFirstClear()));
        }
        return json;
    }

    private static SeasonArchive decodeArchive(JsonObject json) throws CorruptDataException {
        Map<RankingCategory, List<RankingEntry<String>>> rankings = new EnumMap<>(RankingCategory.class);
        for (Map.Entry<String, JsonElement> entry : JsonFields.requireObject(json, "rankings").entrySet()) {
            RankingCategory category = category(entry.getKey());
            if (!entry.getValue().isJsonArray()) {
                throw new CorruptDataException("Ranking '" + entry.getKey() + "' is not an array");
            }
            List<RankingEntry<String>> entries = new ArrayList<>();
            for (JsonElement element : entry.getValue().getAsJsonArray()) {
                JsonObject line = JsonFields.asObject(element, "rankings." + entry.getKey() + "[]");
                entries.add(new RankingEntry<>(JsonFields.requireInt(line, "rank"), JsonFields.requireString(line, "id"),
                        JsonFields.requireString(line, "name"), JsonFields.requireLong(line, "score")));
            }
            rankings.put(category, entries);
        }
        JsonElement firstClear = json.get("finalBossFirstClear");
        TimeAttackRun finalBossFirstClear = firstClear == null || firstClear.isJsonNull() ? null
                : decodeRun(JsonFields.asObject(firstClear, "finalBossFirstClear"));
        return new SeasonArchive(new SeasonId(JsonFields.requireString(json, "seasonId")),
                JsonFields.requireInstant(json, "archivedAt"), rankings,
                decodeRuns(json, "timeAttackBests"), finalBossFirstClear);
    }

    // ---------------------------------------------------------------------------------------------- shared

    private static JsonArray encodeRuns(List<TimeAttackRun> runs) {
        JsonArray array = new JsonArray();
        for (TimeAttackRun run : runs) {
            array.add(encodeRun(run));
        }
        return array;
    }

    private static JsonObject encodeRun(TimeAttackRun run) {
        JsonObject json = new JsonObject();
        json.addProperty("runId", run.runId().toString());
        json.addProperty("bossId", run.bossId());
        json.addProperty("difficulty", run.difficulty().id());
        json.addProperty("startTick", run.startTick());
        json.addProperty("endTick", run.endTick());
        json.addProperty("startedAtMillis", run.startedAtMillis());
        json.addProperty("clearedAtMillis", run.clearedAtMillis());
        JsonArray members = new JsonArray();
        for (TimeAttackMember member : run.members()) {
            JsonObject line = new JsonObject();
            line.addProperty("id", member.id().toString());
            line.addProperty("name", member.name());
            line.addProperty("jobId", member.jobId());
            line.addProperty("level", member.level());
            members.add(line);
        }
        json.add("members", members);
        json.addProperty("creativeOrSpectatorUsed", run.creativeOrSpectatorUsed());
        json.addProperty("adminCommandUsed", run.adminCommandUsed());
        json.addProperty("membersChangedDuringRun", run.membersChangedDuringRun());
        return json;
    }

    private static List<TimeAttackRun> decodeRuns(JsonObject parent, String key) throws CorruptDataException {
        List<TimeAttackRun> runs = new ArrayList<>();
        for (JsonElement element : JsonFields.requireArray(parent, key)) {
            runs.add(decodeRun(JsonFields.asObject(element, key + "[]")));
        }
        return runs;
    }

    private static TimeAttackRun decodeRun(JsonObject json) throws CorruptDataException {
        String difficultyId = JsonFields.requireString(json, "difficulty");
        BossDifficulty difficulty = BossDifficulty.byId(difficultyId)
                .orElseThrow(() -> new CorruptDataException("Unknown difficulty '" + difficultyId + "'"));
        List<TimeAttackMember> members = new ArrayList<>();
        for (JsonElement element : JsonFields.requireArray(json, "members")) {
            JsonObject member = JsonFields.asObject(element, "members[]");
            members.add(new TimeAttackMember(JsonFields.requireUuid(member, "id"),
                    JsonFields.requireString(member, "name"), JsonFields.requireString(member, "jobId"),
                    JsonFields.requireInt(member, "level")));
        }
        UUID runId = JsonFields.requireUuid(json, "runId");
        return new TimeAttackRun(JsonFields.requireString(json, "bossId"), difficulty, runId,
                JsonFields.requireLong(json, "startTick"), JsonFields.requireLong(json, "endTick"),
                JsonFields.requireLong(json, "startedAtMillis"), JsonFields.requireLong(json, "clearedAtMillis"),
                members, JsonFields.requireBoolean(json, "creativeOrSpectatorUsed"),
                JsonFields.requireBoolean(json, "adminCommandUsed"),
                JsonFields.requireBoolean(json, "membersChangedDuringRun"));
    }

    private static RankingCategory category(String id) throws CorruptDataException {
        return RankingCategory.byId(id).orElseThrow(() -> new CorruptDataException("Unknown ranking category '" + id + "'"));
    }

    private static void addInstant(JsonObject json, String key, Instant value) {
        if (value != null) {
            json.addProperty(key, value.toString());
        }
    }
}
