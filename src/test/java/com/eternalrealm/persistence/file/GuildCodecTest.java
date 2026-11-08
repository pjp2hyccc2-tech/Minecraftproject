package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.guild.Guild;
import com.eternalrealm.guild.GuildMember;
import com.eternalrealm.guild.GuildRank;
import com.eternalrealm.season.SeasonId;
import org.junit.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GuildCodecTest {
    static final UUID ALPHA_ID = UUID.fromString("0a000000-0000-4000-8000-000000000001");
    static final UUID BETA_ID = UUID.fromString("0b000000-0000-4000-8000-000000000002");
    static final UUID A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    static final UUID B = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");
    static final UUID C = UUID.fromString("cccccccc-0000-4000-8000-000000000003");
    static final UUID D = UUID.fromString("dddddddd-0000-4000-8000-000000000004");
    static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");
    static final Instant T1 = Instant.parse("2026-11-08T12:34:56.789Z");

    static Guild alpha() {
        Map<UUID, GuildMember> members = new LinkedHashMap<>();
        members.put(A, new GuildMember(GuildRank.MASTER, T0));
        members.put(B, new GuildMember(GuildRank.OFFICER, T1));
        members.put(C, new GuildMember(GuildRank.MEMBER, T1));
        Map<SeasonId, Long> contributions = new LinkedHashMap<>();
        contributions.put(SeasonId.of("s1"), 1200L);
        contributions.put(SeasonId.of("s2"), 5L);
        return new Guild(ALPHA_ID, "黒猫団", "CAT", T0, members, contributions);
    }

    static Guild beta() {
        return new Guild(BETA_ID, "Astria Knights", "AK", T1, Map.of(D, new GuildMember(GuildRank.MASTER, T1)), Map.of());
    }

    private static String json() {
        return GuildCodec.encode(List.of(alpha(), beta()));
    }

    @Test
    public void roundTripKeepsEverythingInOrder() throws Exception {
        String json = json();
        assertTrue(json.contains("\"schema\": 1"));
        assertTrue("names are stored readable", json.contains("\"name\": \"黒猫団\""));
        List<Guild> decoded = GuildCodec.decode(json);
        assertEquals(List.of(alpha(), beta()), decoded);
        assertEquals(List.of(A, B, C), List.copyOf(decoded.get(0).members().keySet()));
        assertEquals(List.of(), GuildCodec.decode(GuildCodec.encode(List.of())));
    }

    @Test
    public void newerSchemaIsIncompatibleNotCorrupt() {
        assertThrows(IncompatibleDataException.class, () -> GuildCodec.decode(json().replace("\"schema\": 1", "\"schema\": 2")));
        assertCorrupt(json().replace("\"schema\": 1", "\"schema\": 0"));
    }

    @Test
    public void malformedDocumentsAreCorrupt() {
        String json = json();
        assertCorrupt(json.substring(0, json.length() / 2));
        assertCorrupt("");
        assertCorrupt("[]");
        assertCorrupt("{}");
        assertCorrupt("{\"schema\": 1}");
        assertCorrupt("{\"schema\": 1, \"guilds\": {}}");
        assertCorrupt("{\"schema\": 1, \"guilds\": [1]}");
        assertCorrupt(json.replace("\"members\": [", "\"members\": [1, "));
        assertCorrupt(json.replace("\"contributions\": {}", "\"contributions\": []"));
    }

    @Test
    public void invalidValuesAreCorrupt() {
        assertCorrupt(json().replace("\"rank\": \"officer\"", "\"rank\": \"captain\""));
        assertCorrupt(json().replace("\"rank\": \"officer\"", "\"rank\": \"master\""));
        assertCorrupt(json().replace("\"name\": \"黒猫団\"", "\"name\": \" 黒猫団\""));
        assertCorrupt(json().replace("\"tag\": \"CAT\"", "\"tag\": \"cat\""));
        assertCorrupt(json().replace("\"s1\": 1200", "\"s1\": -5"));
        assertCorrupt(json().replace("\"s1\": 1200", "\"s1\": 12.5"));
        assertCorrupt(json().replace("\"s1\": 1200", "\"S1\": 1200"));
        assertCorrupt(json().replace("\"createdAt\": \"2026-11-01T10:00:00Z\"", "\"createdAt\": \"yesterday\""));
        assertCorrupt(json().replace(ALPHA_ID.toString(), "not-a-uuid"));
        assertCorrupt("duplicate member", json().replace(B.toString(), A.toString()));
    }

    @Test
    public void conflictsBetweenGuildsAreCorrupt() {
        assertCorrupt("same name", json().replace("\"name\": \"Astria Knights\"", "\"name\": \"黒猫団\""));
        assertCorrupt("same name in another case", json().replace("\"name\": \"黒猫団\"", "\"name\": \"ASTRIA KNIGHTS\""));
        assertCorrupt("same tag", json().replace("\"tag\": \"AK\"", "\"tag\": \"CAT\""));
        assertCorrupt("same id", json().replace(BETA_ID.toString(), ALPHA_ID.toString()));
        assertCorrupt("player in two guilds", json().replace(D.toString(), A.toString()));
        Guild clash = new Guild(BETA_ID, "黒猫団", "AK", T1, Map.of(D, new GuildMember(GuildRank.MASTER, T1)), Map.of());
        assertThrows("never write a file that cannot be read back", IllegalArgumentException.class,
                () -> GuildCodec.encode(List.of(alpha(), clash)));
    }

    private static void assertCorrupt(String json) {
        assertCorrupt("", json);
    }

    private static void assertCorrupt(String message, String json) {
        assertThrows(message, CorruptDataException.class, () -> GuildCodec.decode(json));
    }
}
