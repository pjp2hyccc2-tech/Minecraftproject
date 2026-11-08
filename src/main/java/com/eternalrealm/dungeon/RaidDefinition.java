package com.eternalrealm.dungeon;

import com.eternalrealm.core.ModInfo;

import java.util.Objects;

/**
 * A raid instance: no combat rooms, only a large arena for one boss and many players (the final boss).
 *
 * @param id             instance id (the final raid uses "final", like its boss)
 * @param areaId         area whose entrance leads here
 * @param minPartySize   smallest group allowed to enter
 * @param maxPartySize   largest group allowed to enter (up to {@link #MAX_PARTY_SIZE})
 * @param timeLimitTicks from the start to the boss kill
 */
public record RaidDefinition(String id, int areaId, int recommendedLevel, int minPartySize, int maxPartySize,
                             DungeonTheme theme, String bossId, long timeLimitTicks) {
    public static final int MAX_PARTY_SIZE = 40;

    public RaidDefinition {
        Check.id("id", id);
        Check.range("areaId", areaId, 1, 64);
        Check.range("recommendedLevel", recommendedLevel, 1, 100);
        Check.range("minPartySize", minPartySize, 1, MAX_PARTY_SIZE);
        Check.range("maxPartySize", maxPartySize, minPartySize, MAX_PARTY_SIZE);
        Objects.requireNonNull(theme, "theme");
        Check.id("bossId", bossId);
        Check.range("timeLimitTicks", timeLimitTicks, 20L * 60, 20L * 60 * 120);
    }

    public String translationKey() {
        return "raid." + ModInfo.MOD_ID + "." + id;
    }

    public boolean allowsPartySize(int size) {
        return size >= minPartySize && size <= maxPartySize;
    }
}
