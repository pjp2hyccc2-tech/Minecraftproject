package com.eternalrealm.dungeon;

import com.eternalrealm.core.ModInfo;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One party dungeon (d1..d8): a chain of combat rooms fought wave by wave, then the boss arena.
 *
 * @param id               "d1".."d8"
 * @param areaId           area (1..7) whose entrance leads here
 * @param recommendedLevel level the monsters and the boss are balanced for
 * @param minPartySize     smallest party allowed to enter (1..{@link #MAX_PARTY_SIZE})
 * @param maxPartySize     largest party allowed to enter
 * @param theme            block palette of the generated instance
 * @param rooms            combat rooms in order ({@link #MIN_ROOMS}..{@link #MAX_ROOMS}); every monster template is
 *                         named "&lt;dungeonId&gt;_&lt;name&gt;" and defined by the monster module
 * @param bossId           boss fought in the arena ("bN" for "dN")
 * @param timeLimitTicks   from the start to the boss kill; the run fails when it runs out (configurable)
 */
public record DungeonDefinition(String id, int areaId, int recommendedLevel, int minPartySize, int maxPartySize,
                                DungeonTheme theme, List<RoomPlan> rooms, String bossId, long timeLimitTicks) {
    public static final int MAX_PARTY_SIZE = 5;
    public static final int MIN_ROOMS = 3;
    public static final int MAX_ROOMS = 6;

    public DungeonDefinition {
        Check.id("id", id);
        Check.range("areaId", areaId, 1, 64);
        Check.range("recommendedLevel", recommendedLevel, 1, 100);
        Check.range("minPartySize", minPartySize, 1, MAX_PARTY_SIZE);
        Check.range("maxPartySize", maxPartySize, minPartySize, MAX_PARTY_SIZE);
        Objects.requireNonNull(theme, "theme");
        rooms = List.copyOf(rooms);
        Check.range(id + ": rooms", rooms.size(), MIN_ROOMS, MAX_ROOMS);
        Check.id("bossId", bossId);
        Check.range("timeLimitTicks", timeLimitTicks, 20L * 60, 20L * 60 * 120);
        for (RoomPlan room : rooms) {
            for (Wave wave : room.waves()) {
                for (MonsterGroup group : wave.groups()) {
                    if (!group.templateId().startsWith(id + "_")) {
                        throw new IllegalArgumentException(id + ": monster templates must be named " + id
                                + "_<name>: " + group.templateId());
                    }
                }
            }
        }
    }

    public String translationKey() {
        return "dungeon." + ModInfo.MOD_ID + "." + id;
    }

    public int roomCount() {
        return rooms.size();
    }

    /** Every monster template the waves use, in order of first appearance (for the monster module). */
    public Set<String> monsterTemplateIds() {
        Set<String> templates = new LinkedHashSet<>();
        for (RoomPlan room : rooms) {
            for (Wave wave : room.waves()) {
                for (MonsterGroup group : wave.groups()) {
                    templates.add(group.templateId());
                }
            }
        }
        return templates;
    }

    /** Whether a party of {@code size} may enter. */
    public boolean allowsPartySize(int size) {
        return size >= minPartySize && size <= maxPartySize;
    }
}
