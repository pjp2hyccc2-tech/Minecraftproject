package com.eternalrealm.quest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.monster.DropTables;
import com.eternalrealm.monster.Materials;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.world.Areas;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.eternalrealm.monster.Materials.DESERT_CRYSTAL;
import static com.eternalrealm.monster.Materials.DRAKE_SCALE;
import static com.eternalrealm.monster.Materials.ENHANCEMENT_STONE;
import static com.eternalrealm.monster.Materials.FOREST_SPORE;
import static com.eternalrealm.monster.Materials.FROST_SHARD;
import static com.eternalrealm.monster.Materials.MAGMA_CORE;
import static com.eternalrealm.monster.Materials.REROLL_CRYSTAL;
import static com.eternalrealm.monster.Materials.SCARAB_SHELL;
import static com.eternalrealm.monster.Materials.SKY_FEATHER;
import static com.eternalrealm.monster.Materials.SLIME_GEL;
import static com.eternalrealm.monster.Materials.STAR_FRAGMENT;
import static com.eternalrealm.monster.Materials.TREANT_BARK;
import static com.eternalrealm.monster.Materials.VOID_ESSENCE;
import static com.eternalrealm.monster.Materials.YETI_FUR;

/**
 * The quests of the initial season (docs/modules/world.md).
 *
 * <p><b>Main story</b>: per area "arrive → hunt → clear the dungeon → defeat its boss" (area 4 has both snow dungeons),
 * ending with 終焉の王. Each main quest requires the previous one; the minimum levels follow the area's recommended
 * range. Defeating a boss for the story also unlocks the next area on the server if nobody did it before.
 * <b>Side quests</b>: one per area 1-6. <b>Dailies</b>: four, spread over the level range.</p>
 *
 * <p>Rewards: {@code EXP = 100 × level^1.5 × f} (a fraction {@code f} of the EXP the default curve needs at the quest's
 * minimum level: arrive 0.2, hunt 0.4, dungeon 0.6, boss 0.8, final 1.0; side 0.3-0.4,
 * daily 0.2-0.25), {@code gold = max(10, 20 × level × f)},
 * plus area materials and upgrade materials.</p>
 */
public final class Quests {
    private static final String BREAD = "minecraft:bread";
    private static final String COOKED_BEEF = "minecraft:cooked_beef";
    private static final String GOLDEN_APPLE = "minecraft:golden_apple";
    private static final String ENCHANTED_GOLDEN_APPLE = "minecraft:enchanted_golden_apple";

    private static final QuestCatalog CATALOG = new QuestCatalog(List.of(
            // ===== Area 1: Kingdom of Arcelia (Lv1-8)
            main("m1_arrival", 1, 1, null, List.of(talk(Npcs.KNIGHT_CAPTAIN)),
                    reward(1, 0.2, item(BREAD, 4))),
            main("m1_hunt", 1, 1, "m1_arrival",
                    List.of(Objective.KillMonsters.of("meadow_slime", 6), Objective.KillMonsters.of("wandering_dead", 6)),
                    reward(1, 0.4, item(SLIME_GEL, 3))),
            main("m1_dungeon", 1, 5, "m1_hunt", List.of(new Objective.ClearDungeon("d1", 1)),
                    reward(5, 0.6, item(ENHANCEMENT_STONE, 2))),
            main("m1_boss", 1, 6, "m1_dungeon", List.of(new Objective.DefeatBoss("b1")), bossReward(6)),
            // ===== Area 2: Elder Forest (Lv8-15)
            main("m2_arrival", 2, 7, "m1_boss", arrival(2, Npcs.FOREST_SAGE), reward(7, 0.2, item(BREAD, 4))),
            main("m2_hunt", 2, 8, "m2_arrival",
                    List.of(Objective.KillMonsters.of("forest_spider", 8), Objective.KillMonsters.of("forest_witch", 4)),
                    reward(8, 0.4, item(TREANT_BARK, 3))),
            main("m2_dungeon", 2, 11, "m2_hunt", List.of(new Objective.ClearDungeon("d2", 1)),
                    reward(11, 0.6, item(ENHANCEMENT_STONE, 2))),
            main("m2_boss", 2, 13, "m2_dungeon", List.of(new Objective.DefeatBoss("b2")), bossReward(13)),
            // ===== Area 3: Sandria Desert (Lv15-22)
            main("m3_arrival", 3, 14, "m2_boss", arrival(3, Npcs.CARAVAN_MASTER),
                    reward(14, 0.2, item(COOKED_BEEF, 4))),
            main("m3_hunt", 3, 15, "m3_arrival",
                    List.of(Objective.KillMonsters.of("sand_husk", 8), Objective.KillMonsters.of("giant_scarab", 6)),
                    reward(15, 0.4, item(SCARAB_SHELL, 3))),
            main("m3_dungeon", 3, 18, "m3_hunt", List.of(new Objective.ClearDungeon("d3", 1)),
                    reward(18, 0.6, item(ENHANCEMENT_STONE, 2))),
            main("m3_boss", 3, 20, "m3_dungeon", List.of(new Objective.DefeatBoss("b3")), bossReward(20)),
            // ===== Area 4: Nordia Snowfield (Lv22-30), two dungeons
            main("m4_arrival", 4, 21, "m3_boss", arrival(4, Npcs.CHIEFTAIN), reward(21, 0.2, item(COOKED_BEEF, 4))),
            main("m4_hunt", 4, 22, "m4_arrival",
                    List.of(Objective.KillMonsters.of("frozen_dead", 8), Objective.KillMonsters.of("snowfield_archer", 6)),
                    reward(22, 0.4, item(FROST_SHARD, 3))),
            main("m4_dungeon1", 4, 24, "m4_hunt", List.of(new Objective.ClearDungeon("d4", 1)),
                    reward(24, 0.6, item(ENHANCEMENT_STONE, 2))),
            main("m4_boss1", 4, 25, "m4_dungeon1", List.of(new Objective.DefeatBoss("b4")), bossReward(25)),
            main("m4_dungeon2", 4, 27, "m4_boss1", List.of(new Objective.ClearDungeon("d5", 1)),
                    reward(27, 0.6, item(ENHANCEMENT_STONE, 2))),
            main("m4_boss2", 4, 28, "m4_dungeon2", List.of(new Objective.DefeatBoss("b5")), bossReward(28)),
            // ===== Area 5: Dragonia Volcano (Lv30-37)
            main("m5_arrival", 5, 29, "m4_boss2", arrival(5, Npcs.BLACKSMITH), reward(29, 0.2, item(COOKED_BEEF, 4))),
            main("m5_hunt", 5, 30, "m5_arrival",
                    List.of(Objective.KillMonsters.of("lava_slime", 8), Objective.KillMonsters.of("crater_blaze", 6)),
                    reward(30, 0.4, item(MAGMA_CORE, 3))),
            main("m5_dungeon", 5, 33, "m5_hunt", List.of(new Objective.ClearDungeon("d6", 1)),
                    reward(33, 0.6, item(ENHANCEMENT_STONE, 2))),
            main("m5_boss", 5, 35, "m5_dungeon", List.of(new Objective.DefeatBoss("b6")), bossReward(35)),
            // ===== Area 6: Celestia Sky Continent (Lv37-44)
            main("m6_arrival", 6, 37, "m5_boss", arrival(6, Npcs.SKY_ORACLE), reward(36, 0.2, item(COOKED_BEEF, 4))),
            main("m6_hunt", 6, 37, "m6_arrival",
                    List.of(Objective.KillMonsters.of("cloud_phantom", 8), Objective.KillMonsters.of("sky_archer", 6)),
                    reward(37, 0.4, item(SKY_FEATHER, 3))),
            main("m6_dungeon", 6, 40, "m6_hunt", List.of(new Objective.ClearDungeon("d7", 1)),
                    reward(40, 0.6, item(ENHANCEMENT_STONE, 2))),
            main("m6_boss", 6, 42, "m6_dungeon", List.of(new Objective.DefeatBoss("b7")), bossReward(42)),
            // ===== Area 7: Tower of the End (Lv44-50)
            main("m7_arrival", 7, 47, "m6_boss", arrival(7, Npcs.TOWER_WATCHER), reward(43, 0.2, item(COOKED_BEEF, 4))),
            main("m7_hunt", 7, 47, "m7_arrival",
                    List.of(Objective.KillMonsters.of("void_wanderer", 8),
                            Objective.KillMonsters.of("knight_of_the_end", 6)),
                    reward(44, 0.4, item(VOID_ESSENCE, 3))),
            main("m7_dungeon", 7, 47, "m7_hunt", List.of(new Objective.ClearDungeon("d8", 1)),
                    reward(46, 0.6, item(ENHANCEMENT_STONE, 2))),
            main("m7_boss", 7, 47, "m7_dungeon", List.of(new Objective.DefeatBoss("b8")), bossReward(47)),
            main("m7_final", 7, 48, "m7_boss", List.of(new Objective.DefeatBoss(Areas.FINAL_BOSS_ID)),
                    reward(48, 1.0, item(REROLL_CRYSTAL, 3), item(ENCHANTED_GOLDEN_APPLE, 1))),
            // ===== Side quests
            side("s1_slime_gel", 1, 2, "m1_arrival", List.of(new Objective.CollectItems(SLIME_GEL, 8)),
                    reward(2, 0.3, item(ENHANCEMENT_STONE, 1))),
            side("s2_forest_spores", 2, 9, "m2_arrival",
                    List.of(new Objective.CollectItems(FOREST_SPORE, 6), Objective.KillMonsters.of("vine_creeper", 4)),
                    reward(9, 0.3, item(ENHANCEMENT_STONE, 1))),
            side("s3_tomb_warden", 3, 18, "m3_arrival",
                    List.of(Objective.KillMonsters.of("tomb_warden", 1), new Objective.CollectItems(DESERT_CRYSTAL, 4)),
                    reward(18, 0.4, item(ENHANCEMENT_STONE, 2))),
            side("s4_yeti_hunt", 4, 25, "m4_arrival",
                    List.of(Objective.KillMonsters.of("snowfield_yeti", 2), new Objective.CollectItems(YETI_FUR, 4)),
                    reward(25, 0.4, item(ENHANCEMENT_STONE, 2))),
            side("s5_drake_scales", 5, 32, "m5_arrival",
                    List.of(Objective.KillMonsters.of("dragonkin_berserker", 1), new Objective.CollectItems(DRAKE_SCALE, 4)),
                    reward(32, 0.4, item(ENHANCEMENT_STONE, 2), item(REROLL_CRYSTAL, 1))),
            side("s6_star_fragments", 6, 39, "m6_arrival",
                    List.of(new Objective.CollectItems(STAR_FRAGMENT, 6), Objective.KillMonsters.of("celestial_paladin", 1)),
                    reward(39, 0.4, item(ENHANCEMENT_STONE, 2), item(REROLL_CRYSTAL, 1))),
            // ===== Dailies
            daily("daily_plains_patrol", 1, 3, "m1_arrival", List.of(Objective.KillMonsters.anyIn(1, 15)),
                    reward(3, 0.2, item(ENHANCEMENT_STONE, 1))),
            daily("daily_desert_supplies", 3, 16, "m3_arrival", List.of(new Objective.CollectItems(SCARAB_SHELL, 8)),
                    reward(16, 0.2, item(ENHANCEMENT_STONE, 1))),
            daily("daily_volcano_expedition", 5, 31, "m5_arrival", List.of(new Objective.ClearDungeon("d6", 1)),
                    reward(31, 0.25, item(ENHANCEMENT_STONE, 2))),
            daily("daily_tower_vigil", 7, 45, "m7_arrival", List.of(Objective.KillMonsters.anyIn(7, 25)),
                    reward(45, 0.25, item(ENHANCEMENT_STONE, 2), item(REROLL_CRYSTAL, 1)))));

    private Quests() {
    }

    public static QuestCatalog catalog() {
        return CATALOG;
    }

    /**
     * Cross-module consistency of a catalog: every monster, dungeon, boss and NPC an objective names exists, monsters
     * filtered by area live there, every item a CollectItems objective asks for is dropped by some monster, and every
     * mod item in a reward is a known material. Run by the tests and usable at startup for data-driven catalogs.
     *
     * @return human-readable problems; empty if consistent
     */
    public static List<String> referenceProblems(QuestCatalog catalog) {
        List<String> problems = new ArrayList<>();
        for (QuestDefinition quest : catalog.all()) {
            String where = quest.id() + ": ";
            for (Objective objective : quest.objectives()) {
                if (objective instanceof Objective.KillMonsters kill && !kill.anyMonster()) {
                    Optional<MonsterTemplate> template = Monsters.byId(kill.target());
                    if (template.isEmpty()) {
                        problems.add(where + "unknown monster " + kill.target());
                    } else if (kill.areaId() != 0 && !template.get().livesIn(kill.areaId())) {
                        problems.add(where + kill.target() + " does not live in area " + kill.areaId());
                    }
                } else if (objective instanceof Objective.CollectItems collect
                        && !DropTables.droppableItemIds().contains(collect.itemId())) {
                    problems.add(where + "no monster drops " + collect.itemId());
                } else if (objective instanceof Objective.ClearDungeon clear && Areas.dungeon(clear.dungeonId()).isEmpty()) {
                    problems.add(where + "unknown dungeon " + clear.dungeonId());
                } else if (objective instanceof Objective.DefeatBoss boss && !Areas.isBossId(boss.bossId())) {
                    problems.add(where + "unknown boss " + boss.bossId());
                } else if (objective instanceof Objective.TalkTo talk && Npcs.byId(talk.npcId()).isEmpty()) {
                    problems.add(where + "unknown NPC " + talk.npcId());
                }
            }
            for (ItemReward item : quest.reward().items()) {
                if (item.itemId().startsWith(ModInfo.MOD_ID + ":") && !Materials.isKnown(item.itemId())) {
                    problems.add(where + "unknown mod item " + item.itemId());
                }
            }
        }
        return problems;
    }

    private static QuestDefinition main(String id, int areaId, int minLevel, String previous, List<Objective> objectives,
                                        QuestReward reward) {
        return quest(id, QuestKind.MAIN, areaId, minLevel, previous, objectives, reward);
    }

    private static QuestDefinition side(String id, int areaId, int minLevel, String requires, List<Objective> objectives,
                                        QuestReward reward) {
        return quest(id, QuestKind.SIDE, areaId, minLevel, requires, objectives, reward);
    }

    private static QuestDefinition daily(String id, int areaId, int minLevel, String requires,
                                         List<Objective> objectives, QuestReward reward) {
        return quest(id, QuestKind.DAILY, areaId, minLevel, requires, objectives, reward);
    }

    private static QuestDefinition quest(String id, QuestKind kind, int areaId, int minLevel, String requires,
                                         List<Objective> objectives, QuestReward reward) {
        List<String> required = requires == null ? List.of() : List.of(requires);
        return new QuestDefinition(id, kind, areaId, new QuestPrerequisites(required, minLevel, areaId), objectives,
                reward);
    }

    /** Arriving in a new area: enter it and report to its guide. */
    private static List<Objective> arrival(int areaId, NpcDefinition guide) {
        return List.of(new Objective.ReachArea(areaId), talk(guide));
    }

    private static Objective talk(NpcDefinition npc) {
        return new Objective.TalkTo(npc.id());
    }

    private static QuestReward bossReward(int level) {
        return reward(level, 0.8, item(ENHANCEMENT_STONE, 3), item(REROLL_CRYSTAL, 1), item(GOLDEN_APPLE, 1));
    }

    private static QuestReward reward(int level, double fraction, ItemReward... items) {
        long exp = Math.round(100.0 * StrictMath.pow(level, 1.5) * fraction);
        long gold = Math.max(10L, Math.round(20.0 * level * fraction));
        return new QuestReward(exp, gold, List.of(items));
    }

    private static ItemReward item(String itemId, int count) {
        return new ItemReward(itemId, count);
    }
}
