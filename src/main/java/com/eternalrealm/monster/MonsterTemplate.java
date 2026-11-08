package com.eternalrealm.monster;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.LevelRange;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A kind of monster: which vanilla body it uses and how its stats grow with level ({@link MonsterScaling}).
 *
 * <p>The mod spawns {@code baseEntityType} with a custom name, level and attributes; the body only provides model, AI and
 * sounds. Health, damage and armor grow linearly: {@code value(level) = base + perLevel × (level - 1)}. EXP and gold use
 * fixed growth curves shared by every template.</p>
 *
 * @param levels          levels the template is designed for; monster tables clamp spawn levels into it. Stats can be
 *                        computed for any level (bosses may scale their summons to the party)
 * @param speedMultiplier multiplier of the body's vanilla movement speed; it does not grow with level so high-level
 *                        monsters stay dodgeable
 * @param baseExp         EXP at level 1 before {@link MonsterScaling#EXP_LEVEL_EXPONENT} growth
 * @param baseGoldMin     gold range at level 1 before {@link MonsterScaling#GOLD_GROWTH_PER_LEVEL} growth
 * @param dropTableId     {@link DropTables} id rolled when the monster is killed
 * @param areaIds         areas the monster lives in (dungeon monsters and minions: the area of their dungeon)
 */
public record MonsterTemplate(String id, String baseEntityType, MonsterRole role, LevelRange levels,
                              double baseHealth, double healthPerLevel, double baseDamage, double damagePerLevel,
                              double baseArmor, double armorPerLevel, double speedMultiplier,
                              double baseExp, long baseGoldMin, long baseGoldMax,
                              String dropTableId, Set<Integer> areaIds, SpawnHints spawnHints) {
    /** The vanilla bodies the Forge layer supports for templated monsters (hostile mobs with attack AI). */
    public static final Set<String> BASE_ENTITY_TYPES = Set.of(
            "minecraft:zombie", "minecraft:husk", "minecraft:skeleton", "minecraft:stray", "minecraft:spider",
            "minecraft:cave_spider", "minecraft:drowned", "minecraft:wither_skeleton", "minecraft:vindicator",
            "minecraft:pillager", "minecraft:witch", "minecraft:blaze", "minecraft:slime", "minecraft:magma_cube",
            "minecraft:phantom", "minecraft:ravager", "minecraft:evoker", "minecraft:piglin_brute", "minecraft:creeper",
            "minecraft:enderman", "minecraft:silverfish");
    /** Bodies whose vanilla stats depend on a size that must be set before the attributes. */
    public static final Set<String> SIZED_ENTITY_TYPES = Set.of("minecraft:slime", "minecraft:magma_cube");

    public MonsterTemplate {
        Check.id("id", id);
        if (!BASE_ENTITY_TYPES.contains(baseEntityType)) {
            throw new IllegalArgumentException(id + ": unsupported base entity type " + baseEntityType);
        }
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(levels, "levels");
        Check.range("baseHealth", baseHealth, 1.0, MonsterScaling.MAX_HEALTH);
        Check.range("healthPerLevel", healthPerLevel, 0.0, 100.0);
        Check.range("baseDamage", baseDamage, 0.0, 500.0);
        Check.range("damagePerLevel", damagePerLevel, 0.0, 50.0);
        Check.range("baseArmor", baseArmor, 0.0, MonsterScaling.MAX_ARMOR);
        Check.range("armorPerLevel", armorPerLevel, 0.0, 5.0);
        Check.range("speedMultiplier", speedMultiplier, 0.25, 3.0);
        Check.range("baseExp", baseExp, 0.0, 100_000.0);
        Check.range("baseGoldMin", baseGoldMin, 0L, 1_000_000L);
        Check.range("baseGoldMax", baseGoldMax, baseGoldMin, 1_000_000L);
        Check.id("dropTableId", dropTableId);
        Objects.requireNonNull(areaIds, "areaIds");
        TreeSet<Integer> areas = new TreeSet<>();
        for (Integer area : areaIds) {
            if (area == null || !Areas.exists(area)) {
                throw new IllegalArgumentException(id + ": unknown area id " + area);
            }
            areas.add(area);
        }
        if (areas.isEmpty()) {
            throw new IllegalArgumentException(id + ": a monster must live in at least one area");
        }
        areaIds = Collections.unmodifiableSet(areas);
        Objects.requireNonNull(spawnHints, "spawnHints");
        boolean sized = SIZED_ENTITY_TYPES.contains(baseEntityType);
        if (sized ? spawnHints.size() < 2 : spawnHints.size() != 0) {
            throw new IllegalArgumentException(id + ": slimes and magma cubes need size 2..4, other bodies size 0");
        }
    }

    public String translationKey() {
        return "monster." + ModInfo.MOD_ID + "." + id;
    }

    public boolean livesIn(int areaId) {
        return areaIds.contains(areaId);
    }
}
