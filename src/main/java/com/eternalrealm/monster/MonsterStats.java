package com.eternalrealm.monster;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Stats of one monster at one level, computed by {@link MonsterScaling#stats}.
 *
 * @param health  vanilla MAX_HEALTH
 * @param damage  damage of one hit before the player's defenses. The Forge layer applies it to every hit the monster
 *                deals (melee via ATTACK_DAMAGE; arrows, fireballs, fangs, potions and explosions by replacing the
 *                amount), so the body's vanilla weapon does not matter
 * @param armor   vanilla ARMOR attribute; reduces the player's hits with the vanilla armor formula
 * @param speed   multiplier of the body's vanilla movement speed
 * @param exp     EXP for the killer
 * @param goldMin inclusive gold drop range
 */
public record MonsterStats(double health, double damage, double armor, double speed, long exp, long goldMin,
                           long goldMax) {
    public MonsterStats {
        Check.range("health", health, 1.0, MonsterScaling.MAX_HEALTH);
        Check.range("damage", damage, 0.0, 10_000.0);
        Check.range("armor", armor, 0.0, MonsterScaling.MAX_ARMOR);
        Check.range("speed", speed, 0.25, 3.0);
        Check.range("exp", exp, 0L, 1_000_000_000L);
        Check.range("goldMin", goldMin, 0L, 1_000_000_000L);
        Check.range("goldMax", goldMax, goldMin, 1_000_000_000L);
    }

    /** A uniformly distributed amount of gold in {@code [goldMin, goldMax]}. */
    public long rollGold(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        return goldMin == goldMax ? goldMin : goldMin + random.nextLong(goldMax - goldMin + 1);
    }
}
