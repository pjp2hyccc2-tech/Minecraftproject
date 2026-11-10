package com.eternalrealm.monster;

import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.LevelRange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The monster templates and area monster tables of the initial season (docs/modules/world.md lists them all).
 *
 * <ul>
 *     <li>Field monsters: three NORMAL templates and one ELITE per area.</li>
 *     <li>Dungeon waves: {@code <dungeonId>_<name>}, three per dungeon (four in the tower), one or two of them ELITE
 *         wave captains. The dungeon module picks them by id.</li>
 *     <li>Boss summons: {@code <bossId>_minion} for {@code b1}..{@code b8} and {@code final}.</li>
 * </ul>
 *
 * <p>Stats are defined relative to a per-role standard (below) so every template is tuned consistently: the standard
 * NORMAL monster takes about 4 reference light hits at any level ({@link MonsterScaling}); a template's tuning multiplies
 * health (and with it EXP), damage and armor and sets the speed. Creepers carry a high damage factor because their one
 * hit is the explosion.</p>
 */
public final class Monsters {
    /** Health, damage and armor at level 1 plus growth per level, EXP at level 1, gold range at level 1. */
    private record Standard(double health, double healthPerLevel, double damage, double damagePerLevel, double armor,
                            double armorPerLevel, double exp, long goldMin, long goldMax) {
    }

    /** About 4 reference hits at every level; 3 damage at level 1 up to about 14 at level 50. */
    private static final Standard NORMAL = new Standard(26.0, 2.65, 3.0, 0.22, 1.0, 0.18, 10.0, 1, 3);
    /** Three times the health, 1.5 times damage and armor, four times the EXP of a NORMAL monster. */
    private static final Standard ELITE = new Standard(78.0, 7.95, 4.5, 0.33, 1.5, 0.27, 40.0, 6, 12);
    /** Half the health, 0.6 times the damage, a fifth of the EXP and no gold (summons must not be farmable). */
    private static final Standard MINION = new Standard(13.0, 1.325, 1.8, 0.132, 0.5, 0.09, 2.0, 0, 0);

    /** Per-template multipliers of the role standard; {@code speed} is the absolute speed multiplier. */
    private record Tuning(double health, double damage, double armor, double speed) {
    }

    private static final String ZOMBIE = "minecraft:zombie";
    private static final String HUSK = "minecraft:husk";
    private static final String SKELETON = "minecraft:skeleton";
    private static final String STRAY = "minecraft:stray";
    private static final String SPIDER = "minecraft:spider";
    private static final String CAVE_SPIDER = "minecraft:cave_spider";
    private static final String DROWNED = "minecraft:drowned";
    private static final String WITHER_SKELETON = "minecraft:wither_skeleton";
    private static final String VINDICATOR = "minecraft:vindicator";
    private static final String PILLAGER = "minecraft:pillager";
    private static final String WITCH = "minecraft:witch";
    private static final String BLAZE = "minecraft:blaze";
    private static final String SLIME = "minecraft:slime";
    private static final String MAGMA_CUBE = "minecraft:magma_cube";
    private static final String PHANTOM = "minecraft:phantom";
    private static final String RAVAGER = "minecraft:ravager";
    private static final String EVOKER = "minecraft:evoker";
    private static final String PIGLIN_BRUTE = "minecraft:piglin_brute";
    private static final String CREEPER = "minecraft:creeper";
    private static final String ENDERMAN = "minecraft:enderman";
    private static final String SILVERFISH = "minecraft:silverfish";

    private static final String LEATHER_HELMET = "minecraft:leather_helmet";
    private static final String CHAINMAIL_HELMET = "minecraft:chainmail_helmet";
    private static final String IRON_HELMET = "minecraft:iron_helmet";
    private static final String GOLDEN_HELMET = "minecraft:golden_helmet";
    private static final String BOW = "minecraft:bow";
    private static final String CROSSBOW = "minecraft:crossbow";
    private static final String TRIDENT = "minecraft:trident";
    private static final String CARVED_PUMPKIN = "minecraft:carved_pumpkin";
    private static final String IRON_AXE = "minecraft:iron_axe";
    private static final String GOLDEN_AXE = "minecraft:golden_axe";
    private static final String STONE_SWORD = "minecraft:stone_sword";
    private static final String GOLDEN_SWORD = "minecraft:golden_sword";
    private static final String IRON_SWORD = "minecraft:iron_sword";
    private static final String DIAMOND_SWORD = "minecraft:diamond_sword";
    private static final String NETHERITE_SWORD = "minecraft:netherite_sword";

    private static final SpawnHints NONE = SpawnHints.NONE;

    private static final List<MonsterTemplate> ALL = List.of(
            // ---- Field 1: はじまりの草原 (Lv5-15)
            normal("meadow_slime", SLIME, 5, 11, 1, t(0.85, 0.8, 0.0, 1.0), SpawnHints.sized(2)),
            normal("wandering_dead", ZOMBIE, 6, 13, 1, t(1.0, 1.0, 1.0, 0.95), SpawnHints.helmet(LEATHER_HELMET)),
            normal("highway_bandit", PILLAGER, 8, 15, 1, t(0.9, 1.1, 0.8, 1.0), SpawnHints.holding(CROSSBOW)),
            elite("bandit_chief", VINDICATOR, 10, 15, 1, t(1.0, 1.0, 1.0, 1.0), SpawnHints.holding(IRON_AXE)),
            // ---- Field 2: 月影の森
            normal("forest_spider", SPIDER, 10, 17, 2, t(0.9, 1.0, 0.6, 1.05), NONE),
            normal("vine_creeper", CREEPER, 11, 18, 2, t(0.8, 2.5, 0.5, 1.0), NONE),
            normal("forest_witch", WITCH, 12, 20, 2, t(0.95, 1.1, 0.6, 1.0), NONE),
            elite("elder_druid", EVOKER, 15, 20, 2, t(0.9, 1.1, 0.8, 1.0), NONE),
            // ---- Field 3: 鉄鉱の鉱山
            normal("sand_husk", HUSK, 15, 22, 3, t(1.1, 1.0, 1.0, 0.95), NONE),
            normal("giant_scarab", CAVE_SPIDER, 16, 23, 3, t(0.8, 0.9, 1.2, 1.1), NONE),
            normal("desert_skeleton", SKELETON, 17, 25, 3, t(0.85, 1.1, 0.8, 1.0),
                    SpawnHints.equipped(GOLDEN_HELMET, BOW)),
            elite("tomb_warden", WITHER_SKELETON, 20, 25, 3, t(1.0, 1.0, 1.2, 1.0), SpawnHints.holding(GOLDEN_SWORD)),
            // ---- Field 4: 聖なる滝
            normal("frozen_dead", ZOMBIE, 20, 27, 4, t(1.1, 1.0, 1.1, 0.9), SpawnHints.helmet(IRON_HELMET)),
            normal("snowfield_archer", STRAY, 21, 28, 4, t(0.85, 1.1, 0.8, 1.0),
                    SpawnHints.equipped(CHAINMAIL_HELMET, BOW)),
            normal("frost_spider", SPIDER, 22, 30, 4, t(0.9, 1.0, 0.8, 1.1), NONE),
            elite("snowfield_yeti", RAVAGER, 25, 30, 4, t(1.2, 1.1, 1.3, 0.9), NONE),
            // ---- Field 5: 古代遺跡
            normal("lava_slime", MAGMA_CUBE, 30, 37, 5, t(0.9, 0.9, 1.5, 1.0), SpawnHints.sized(2)),
            normal("crater_blaze", BLAZE, 31, 38, 5, t(0.8, 1.1, 0.8, 1.0), NONE),
            normal("eruption_creeper", CREEPER, 32, 40, 5, t(0.8, 2.5, 0.6, 1.05), NONE),
            elite("dragonkin_berserker", PIGLIN_BRUTE, 35, 40, 5, t(1.0, 1.2, 1.2, 1.0), SpawnHints.holding(GOLDEN_AXE)),
            // ---- Field 6: 天翔の峰
            normal("cloud_phantom", PHANTOM, 40, 47, 6, t(0.75, 1.0, 0.5, 1.0), NONE),
            normal("storm_spirit", BLAZE, 41, 48, 6, t(0.85, 1.1, 0.8, 1.0), NONE),
            normal("sky_archer", SKELETON, 42, 50, 6, t(0.9, 1.1, 1.0, 1.0), SpawnHints.equipped(GOLDEN_HELMET, BOW)),
            elite("celestial_paladin", VINDICATOR, 45, 50, 6, t(1.1, 1.0, 1.4, 1.0), SpawnHints.holding(DIAMOND_SWORD)),
            // ---- Field 7: 終焉の森 (Lv50〜)
            normal("void_wanderer", ENDERMAN, 50, 50, 7, t(1.0, 1.1, 0.8, 1.0), NONE),
            normal("knight_of_the_end", WITHER_SKELETON, 50, 50, 7, t(1.15, 1.0, 1.3, 1.0),
                    SpawnHints.holding(NETHERITE_SWORD)),
            normal("doom_cultist", WITCH, 50, 50, 7, t(0.9, 1.2, 0.8, 1.0), NONE),
            elite("herald_of_ruin", EVOKER, 50, 50, 7, t(1.0, 1.2, 1.1, 1.0), NONE),
            // ---- Field 8: 豊穣の農場 (Lv10-20)
            normal("crop_slime", SLIME, 10, 16, 8, t(0.9, 0.8, 0.0, 1.0), SpawnHints.sized(2)),
            normal("scarecrow_dead", ZOMBIE, 11, 18, 8, t(1.0, 1.0, 0.9, 0.95), SpawnHints.helmet(CARVED_PUMPKIN)),
            normal("harvest_raider", PILLAGER, 13, 20, 8, t(0.9, 1.1, 0.8, 1.0), SpawnHints.holding(CROSSBOW)),
            elite("rampaging_bull", RAVAGER, 15, 20, 8, t(1.1, 1.0, 1.1, 0.95), NONE),
            // ---- Field 9: 湖畔のほとり (Lv15-25)
            normal("marsh_slime", SLIME, 15, 21, 9, t(0.9, 0.85, 0.0, 1.0), SpawnHints.sized(2)),
            normal("lake_drowned", DROWNED, 16, 23, 9, t(1.0, 1.0, 1.0, 1.0), SpawnHints.equipped(LEATHER_HELMET, TRIDENT)),
            normal("reed_spider", SPIDER, 17, 25, 9, t(0.9, 1.0, 0.7, 1.1), NONE),
            elite("lake_witch", WITCH, 20, 25, 9, t(1.0, 1.1, 0.9, 1.0), NONE),
            // ---- Field 10: 風の港 (Lv20-30)
            normal("dock_drowned", DROWNED, 20, 27, 10, t(1.05, 1.0, 1.0, 1.0), SpawnHints.equipped(LEATHER_HELMET, TRIDENT)),
            normal("pirate_cutthroat", VINDICATOR, 21, 28, 10, t(0.95, 1.05, 0.9, 1.05), SpawnHints.holding(IRON_SWORD)),
            normal("pirate_gunner", PILLAGER, 22, 30, 10, t(0.9, 1.1, 0.8, 1.0), SpawnHints.holding(CROSSBOW)),
            elite("pirate_captain", VINDICATOR, 25, 30, 10, t(1.1, 1.1, 1.2, 1.0), SpawnHints.holding(DIAMOND_SWORD)),
            // ---- d1 失われた地下都市 (area 1)
            normal("d1_bandit", VINDICATOR, 6, 10, 1, t(1.0, 1.0, 1.0, 1.0), SpawnHints.holding(IRON_AXE)),
            normal("d1_crawler", SILVERFISH, 6, 10, 1, t(0.7, 0.8, 0.5, 1.1), NONE),
            elite("d1_canal_drowned", DROWNED, 6, 10, 1, t(1.0, 1.0, 1.0, 1.0),
                    SpawnHints.equipped(CHAINMAIL_HELMET, TRIDENT)),
            // ---- d2 古代樹の迷宮 (area 2)
            normal("d2_root_spider", CAVE_SPIDER, 12, 16, 2, t(0.8, 0.9, 0.8, 1.1), NONE),
            normal("d2_spore_creeper", CREEPER, 12, 16, 2, t(0.8, 2.5, 0.5, 1.0), NONE),
            elite("d2_grove_priestess", WITCH, 12, 16, 2, t(0.9, 1.1, 0.8, 1.0), NONE),
            // ---- d3 砂塵の神殿 (area 3)
            normal("d3_temple_mummy", HUSK, 19, 23, 3, t(1.1, 1.0, 1.0, 0.95), NONE),
            normal("d3_scarab_swarm", SILVERFISH, 19, 23, 3, t(0.7, 0.8, 0.8, 1.15), NONE),
            elite("d3_sun_priest", EVOKER, 19, 23, 3, t(0.9, 1.1, 0.8, 1.0), NONE),
            // ---- d4 凍てつく地下牢 (area 4)
            normal("d4_frozen_prisoner", ZOMBIE, 24, 28, 4, t(1.1, 1.0, 1.0, 0.9), SpawnHints.helmet(CHAINMAIL_HELMET)),
            normal("d4_ice_crawler", SILVERFISH, 24, 28, 4, t(0.7, 0.8, 0.8, 1.15), NONE),
            elite("d4_jailer", VINDICATOR, 24, 28, 4, t(1.1, 1.0, 1.2, 1.0), SpawnHints.holding(IRON_AXE)),
            // ---- d5 氷結の城 (area 4)
            normal("d5_castle_archer", STRAY, 27, 31, 4, t(0.85, 1.1, 0.9, 1.0), SpawnHints.equipped(IRON_HELMET, BOW)),
            normal("d5_ice_knight", STRAY, 27, 31, 4, t(1.1, 1.0, 1.2, 1.0),
                    SpawnHints.equipped(IRON_HELMET, IRON_SWORD)),
            elite("d5_queens_handmaiden", EVOKER, 27, 31, 4, t(1.0, 1.1, 0.9, 1.0), NONE),
            // ---- d6 灼熱の火山洞窟 (area 5)
            normal("d6_cinder_cube", MAGMA_CUBE, 34, 38, 5, t(0.9, 0.9, 1.5, 1.0), SpawnHints.sized(2)),
            normal("d6_flame_spirit", BLAZE, 34, 38, 5, t(0.85, 1.1, 0.8, 1.0), NONE),
            elite("d6_dragon_guard", PIGLIN_BRUTE, 34, 38, 5, t(1.1, 1.2, 1.2, 1.0), SpawnHints.holding(GOLDEN_AXE)),
            // ---- d7 天空の古代遺跡 (area 6)
            normal("d7_ruin_sentry", SKELETON, 41, 45, 6, t(0.9, 1.1, 1.0, 1.0), SpawnHints.equipped(GOLDEN_HELMET, BOW)),
            normal("d7_ancient_drone", PHANTOM, 41, 45, 6, t(0.75, 1.0, 0.8, 1.0), NONE),
            elite("d7_ancient_guardian", RAVAGER, 41, 45, 6, t(1.2, 1.1, 1.4, 0.9), NONE),
            // ---- d8 終焉の塔 (area 7)
            normal("d8_void_acolyte", ENDERMAN, 46, 50, 7, t(1.0, 1.1, 0.9, 1.0), NONE),
            normal("d8_doom_guard", WITHER_SKELETON, 46, 50, 7, t(1.15, 1.0, 1.3, 1.0),
                    SpawnHints.holding(NETHERITE_SWORD)),
            elite("d8_doom_sorcerer", EVOKER, 46, 50, 7, t(1.0, 1.2, 1.0, 1.0), NONE),
            elite("d8_tower_behemoth", RAVAGER, 46, 50, 7, t(1.15, 1.1, 1.4, 0.9), NONE),
            // ---- Boss summons
            minion("b1", ZOMBIE, 6, 10, 1, t(1.0, 1.0, 1.0, 1.0), SpawnHints.helmet(LEATHER_HELMET)),
            minion("b2", SLIME, 12, 16, 2, t(1.0, 0.9, 0.0, 1.0), SpawnHints.sized(2)),
            minion("b3", HUSK, 19, 23, 3, t(1.0, 1.0, 1.0, 1.0), NONE),
            minion("b4", ZOMBIE, 24, 28, 4, t(1.0, 1.0, 1.0, 1.0), SpawnHints.helmet(CHAINMAIL_HELMET)),
            minion("b5", STRAY, 27, 31, 4, t(1.0, 1.0, 1.0, 1.0), SpawnHints.equipped(IRON_HELMET, IRON_SWORD)),
            minion("b6", MAGMA_CUBE, 34, 38, 5, t(1.0, 0.9, 1.0, 1.0), SpawnHints.sized(2)),
            minion("b7", SILVERFISH, 41, 45, 6, t(0.9, 0.9, 1.0, 1.15), NONE),
            minion("b8", WITHER_SKELETON, 46, 50, 7, t(1.0, 1.0, 1.0, 1.0), SpawnHints.holding(STONE_SWORD)),
            minion(Areas.FINAL_BOSS_ID, ENDERMAN, 48, 50, 7, t(1.0, 1.0, 1.0, 1.0), NONE));

    private static final Map<String, MonsterTemplate> BY_ID = index(ALL);

    private static final List<MonsterTable> TABLES = List.of(
            table(Areas.ARCELIA, 0.05,
                    List.of(entry("meadow_slime", 40, -1), entry("wandering_dead", 35, 0), entry("highway_bandit", 25, 1)),
                    entry("bandit_chief", 1, 1)),
            table(Areas.ELDER_FOREST, 0.05,
                    List.of(entry("forest_spider", 40, 0), entry("vine_creeper", 25, 0), entry("forest_witch", 35, 1)),
                    entry("elder_druid", 1, 1)),
            table(Areas.SANDRIA_DESERT, 0.05,
                    List.of(entry("sand_husk", 40, 0), entry("giant_scarab", 35, 0), entry("desert_skeleton", 25, 1)),
                    entry("tomb_warden", 1, 1)),
            table(Areas.NORDIA_SNOWFIELD, 0.05,
                    List.of(entry("frozen_dead", 40, 0), entry("snowfield_archer", 30, 0), entry("frost_spider", 30, 1)),
                    entry("snowfield_yeti", 1, 1)),
            table(Areas.DRAGONIA_VOLCANO, 0.05,
                    List.of(entry("lava_slime", 40, 0), entry("crater_blaze", 35, 0), entry("eruption_creeper", 25, 1)),
                    entry("dragonkin_berserker", 1, 1)),
            table(Areas.CELESTIA, 0.05,
                    List.of(entry("cloud_phantom", 30, 0), entry("storm_spirit", 35, 0), entry("sky_archer", 35, 1)),
                    entry("celestial_paladin", 1, 1)),
            table(Areas.TOWER_OF_THE_END, 0.06,
                    List.of(entry("void_wanderer", 35, 0), entry("knight_of_the_end", 35, 0), entry("doom_cultist", 30, 1)),
                    entry("herald_of_ruin", 1, 1)),
            table(Areas.FERTILE_FARM, 0.05,
                    List.of(entry("crop_slime", 40, 0), entry("scarecrow_dead", 35, 0), entry("harvest_raider", 25, 1)),
                    entry("rampaging_bull", 1, 1)),
            table(Areas.LAKESIDE, 0.05,
                    List.of(entry("marsh_slime", 35, 0), entry("lake_drowned", 35, 0), entry("reed_spider", 30, 1)),
                    entry("lake_witch", 1, 1)),
            table(Areas.WIND_HARBOUR, 0.05,
                    List.of(entry("dock_drowned", 35, 0), entry("pirate_cutthroat", 35, 0), entry("pirate_gunner", 30, 1)),
                    entry("pirate_captain", 1, 1)));

    private Monsters() {
    }

    public static List<MonsterTemplate> all() {
        return ALL;
    }

    public static Optional<MonsterTemplate> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** @throws IllegalArgumentException if there is no such template */
    public static MonsterTemplate require(String id) {
        return byId(id).orElseThrow(() -> new IllegalArgumentException("Unknown monster template " + id));
    }

    /** Id of the template a boss summons: {@code <bossId>_minion}. */
    public static String minionId(String bossId) {
        return bossId + "_minion";
    }

    /** The wave templates of a dungeon ({@code <dungeonId>_<name>}), in catalog order. */
    public static List<MonsterTemplate> dungeonMonsters(String dungeonId) {
        String prefix = dungeonId + "_";
        return ALL.stream().filter(template -> template.id().startsWith(prefix)).toList();
    }

    public static List<MonsterTable> tables() {
        return TABLES;
    }

    public static Optional<MonsterTable> table(String tableId) {
        for (MonsterTable table : TABLES) {
            if (table.id().equals(tableId)) {
                return Optional.of(table);
            }
        }
        return Optional.empty();
    }

    /** The table that populates {@code area}. */
    public static MonsterTable tableFor(AreaDefinition area) {
        return table(area.monsterTableId())
                .orElseThrow(() -> new IllegalArgumentException("No monster table " + area.monsterTableId()));
    }

    private static Tuning t(double health, double damage, double armor, double speed) {
        return new Tuning(health, damage, armor, speed);
    }

    private static MonsterTemplate normal(String id, String body, int minLevel, int maxLevel, int areaId, Tuning tuning,
                                          SpawnHints hints) {
        return template(id, body, MonsterRole.NORMAL, NORMAL, minLevel, maxLevel, areaId, tuning, hints, id);
    }

    private static MonsterTemplate elite(String id, String body, int minLevel, int maxLevel, int areaId, Tuning tuning,
                                         SpawnHints hints) {
        return template(id, body, MonsterRole.ELITE, ELITE, minLevel, maxLevel, areaId, tuning, hints, id);
    }

    private static MonsterTemplate minion(String bossId, String body, int minLevel, int maxLevel, int areaId,
                                          Tuning tuning, SpawnHints hints) {
        return template(minionId(bossId), body, MonsterRole.MINION, MINION, minLevel, maxLevel, areaId, tuning, hints,
                DropTables.NONE_ID);
    }

    private static MonsterTemplate template(String id, String body, MonsterRole role, Standard standard, int minLevel,
                                            int maxLevel, int areaId, Tuning tuning, SpawnHints hints,
                                            String dropTableId) {
        return new MonsterTemplate(id, body, role, LevelRange.of(minLevel, maxLevel),
                standard.health() * tuning.health(), standard.healthPerLevel() * tuning.health(),
                standard.damage() * tuning.damage(), standard.damagePerLevel() * tuning.damage(),
                standard.armor() * tuning.armor(), standard.armorPerLevel() * tuning.armor(),
                tuning.speed(), standard.exp() * tuning.health(), standard.goldMin(), standard.goldMax(),
                dropTableId, Set.of(areaId), hints);
    }

    private static MonsterTable.Entry entry(String templateId, int weight, int levelOffset) {
        return new MonsterTable.Entry(require(templateId), weight, levelOffset);
    }

    private static MonsterTable table(AreaDefinition area, double eliteChance, List<MonsterTable.Entry> entries,
                                      MonsterTable.Entry elite) {
        return new MonsterTable(area.monsterTableId(), entries, eliteChance, List.of(elite));
    }

    private static Map<String, MonsterTemplate> index(List<MonsterTemplate> templates) {
        Map<String, MonsterTemplate> byId = new HashMap<>();
        for (MonsterTemplate template : templates) {
            if (byId.put(template.id(), template) != null) {
                throw new IllegalStateException("Duplicate monster template id " + template.id());
            }
        }
        return Map.copyOf(byId);
    }

    /** All template ids, for diagnostics (e.g. an admin spawn command's suggestions). */
    public static List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (MonsterTemplate template : ALL) {
            ids.add(template.id());
        }
        return List.copyOf(ids);
    }
}
