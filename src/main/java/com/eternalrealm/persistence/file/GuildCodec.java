package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.guild.Guild;
import com.eternalrealm.guild.GuildMember;
import com.eternalrealm.guild.GuildRank;
import com.eternalrealm.guild.GuildService;
import com.eternalrealm.season.SeasonId;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JSON form of the guild directory ({@code guilds.json}, schema 1; docs/modules/party.md):
 * <pre>
 * { "schema": 1,
 *   "guilds": [ { "id": "&lt;uuid&gt;", "name": "黒猫団", "tag": "CAT", "createdAt": "&lt;ISO-8601&gt;",
 *                 "members": [ { "playerId": "&lt;uuid&gt;", "rank": "master", "joinedAt": "&lt;ISO-8601&gt;" } ],
 *                 "contributions": { "s1": 1200 } } ] }
 * </pre>
 * Decoding is strict (see {@link JsonSupport}); invalid guilds and cross-guild conflicts (duplicate ids, names, tags or
 * players) are corruption.
 */
public final class GuildCodec {
    public static final int SCHEMA_VERSION = 1;

    private GuildCodec() {
    }

    /** @throws IllegalArgumentException if the guilds are inconsistent (such a file could not be read back) */
    public static String encode(List<Guild> guilds) {
        GuildService.checkConsistency(guilds);
        JsonArray array = new JsonArray();
        for (Guild guild : guilds) {
            JsonObject json = new JsonObject();
            json.addProperty("id", guild.id().toString());
            json.addProperty("name", guild.name());
            json.addProperty("tag", guild.tag());
            json.addProperty("createdAt", guild.createdAt().toString());
            JsonArray members = new JsonArray();
            for (Map.Entry<UUID, GuildMember> entry : guild.members().entrySet()) {
                JsonObject member = new JsonObject();
                member.addProperty("playerId", entry.getKey().toString());
                member.addProperty("rank", entry.getValue().rank().id());
                member.addProperty("joinedAt", entry.getValue().joinedAt().toString());
                members.add(member);
            }
            json.add("members", members);
            JsonObject contributions = new JsonObject();
            for (Map.Entry<SeasonId, Long> entry : guild.contributions().entrySet()) {
                contributions.addProperty(entry.getKey().value(), entry.getValue());
            }
            json.add("contributions", contributions);
            array.add(json);
        }
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.add("guilds", array);
        return JsonSupport.toJson(root);
    }

    public static List<Guild> decode(String json) throws CorruptDataException, IncompatibleDataException {
        JsonObject root = JsonSupport.parseObject(json);
        JsonSupport.checkSchema(root, SCHEMA_VERSION, "guild");
        try {
            List<Guild> guilds = new ArrayList<>();
            for (JsonElement element : JsonSupport.requireArray(root, "guilds")) {
                guilds.add(decodeGuild(JsonSupport.asObject(element, "guild")));
            }
            GuildService.checkConsistency(guilds);
            return List.copyOf(guilds);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new CorruptDataException("Invalid guild data: " + e.getMessage(), e);
        }
    }

    private static Guild decodeGuild(JsonObject json) throws CorruptDataException {
        Map<UUID, GuildMember> members = new LinkedHashMap<>();
        for (JsonElement element : JsonSupport.requireArray(json, "members")) {
            JsonObject member = JsonSupport.asObject(element, "member");
            UUID playerId = JsonSupport.requireUuid(member, "playerId");
            String rankId = JsonSupport.requireString(member, "rank");
            GuildRank rank = GuildRank.byId(rankId)
                    .orElseThrow(() -> new CorruptDataException("Unknown guild rank '" + rankId + "'"));
            if (members.put(playerId, new GuildMember(rank, JsonSupport.requireInstant(member, "joinedAt"))) != null) {
                throw new CorruptDataException("Duplicate guild member " + playerId);
            }
        }
        Map<SeasonId, Long> contributions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : JsonSupport.requireObject(json, "contributions").entrySet()) {
            contributions.put(new SeasonId(entry.getKey()),
                    JsonSupport.toLong(entry.getValue(), "contributions." + entry.getKey()));
        }
        return new Guild(JsonSupport.requireUuid(json, "id"),
                JsonSupport.requireString(json, "name"),
                JsonSupport.requireString(json, "tag"),
                JsonSupport.requireInstant(json, "createdAt"),
                members,
                contributions);
    }
}
