package com.eternalrealm.mount;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * What the stablemaster shows: {@value MountRules#OFFERS} new horses or griffins, each with a rolled look and rolled
 * values. The values stay with the server until one is bought (docs/modules/mounts.md).
 */
public final class MountOffers {
    private MountOffers() {
    }

    public static List<MountIndividual> roll(MountKind kind, SplittableRandom random) {
        List<MountIndividual> offers = new ArrayList<>(MountRules.OFFERS);
        for (int i = 0; i < MountRules.OFFERS; i++) {
            offers.add(MountIndividual.roll(new UUID(random.nextLong(), random.nextLong()), kind, random));
        }
        return List.copyOf(offers);
    }
}
