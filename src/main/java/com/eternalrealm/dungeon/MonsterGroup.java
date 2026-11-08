package com.eternalrealm.dungeon;

/**
 * Monsters of one template in a wave.
 *
 * @param count          monsters for a solo player
 * @param extraPerPlayer added per party member beyond the first, rounded half up (0.5 = one more every two players);
 *                       0 for a fixed group such as a single elite
 */
public record MonsterGroup(String templateId, int count, double extraPerPlayer) {
    public MonsterGroup {
        Check.id("templateId", templateId);
        Check.range("count", count, 1, 64);
        Check.range("extraPerPlayer", extraPerPlayer, 0.0, 16.0);
    }

    /** Number of monsters for a party of {@code partySize} (at least 1). */
    public int countFor(int partySize) {
        Check.range("partySize", partySize, 1, 64);
        return count + (int) Math.round(extraPerPlayer * (partySize - 1));
    }
}
