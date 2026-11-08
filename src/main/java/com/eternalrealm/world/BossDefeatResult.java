package com.eternalrealm.world;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of {@link WorldProgress#recordBossDefeat}. Only a first defeat changes the world; later defeats of the same boss
 * are reported with {@code firstDefeat == false} and no unlocks.
 *
 * @param newlyUnlockedAreas     areas that opened for everyone because of this defeat (announce them server-wide)
 * @param finalBossNewlyUnlocked this defeat completed {@code b1}..{@code b8}, so the final boss can now be fought
 */
public record BossDefeatResult(String bossId, boolean firstDefeat, List<AreaDefinition> newlyUnlockedAreas,
                               boolean finalBossNewlyUnlocked) {
    public BossDefeatResult {
        Objects.requireNonNull(bossId, "bossId");
        newlyUnlockedAreas = List.copyOf(newlyUnlockedAreas);
        if (!firstDefeat && (!newlyUnlockedAreas.isEmpty() || finalBossNewlyUnlocked)) {
            throw new IllegalArgumentException("only a first defeat can unlock anything");
        }
    }
}
