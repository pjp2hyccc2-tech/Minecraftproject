package com.eternalrealm.monster;

/**
 * How the Forge layer equips a freshly spawned monster.
 *
 * <p>Templated monsters should not go through vanilla's spawn randomisation ({@code finalizeSpawn}: baby zombies, random
 * armour, spider jockeys), so everything a body needs is listed here, including the weapon its AI depends on (a skeleton
 * without a bow fights in melee, a pillager needs its crossbow).</p>
 *
 * @param headItemId     item worn on the head, or {@code null}. Zombies, drowned, skeletons and strays burn in daylight
 *                       unless they wear one (phantoms burn regardless; see docs/modules/world.md)
 * @param mainHandItemId item held in the main hand, or {@code null} for an empty hand
 * @param size           slime / magma cube size, 2..4 (size 1 slimes deal no damage); 0 for every other body
 */
public record SpawnHints(String headItemId, String mainHandItemId, int size) {
    public static final SpawnHints NONE = new SpawnHints(null, null, 0);

    public SpawnHints {
        if (headItemId != null) {
            Check.resourceId("headItemId", headItemId);
        }
        if (mainHandItemId != null) {
            Check.resourceId("mainHandItemId", mainHandItemId);
        }
        Check.range("size", size, 0, 4);
    }

    public static SpawnHints equipped(String headItemId, String mainHandItemId) {
        return new SpawnHints(headItemId, mainHandItemId, 0);
    }

    public static SpawnHints helmet(String headItemId) {
        return new SpawnHints(headItemId, null, 0);
    }

    public static SpawnHints holding(String mainHandItemId) {
        return new SpawnHints(null, mainHandItemId, 0);
    }

    public static SpawnHints sized(int size) {
        return new SpawnHints(null, null, size);
    }
}
