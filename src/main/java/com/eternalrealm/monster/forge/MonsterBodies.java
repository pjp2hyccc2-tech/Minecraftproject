package com.eternalrealm.monster.forge;

import java.util.Map;
import java.util.Optional;

/**
 * Which monster templates wear an animated body of the entity module ({@code eternalrealm:<rig>_enemy} variants)
 * instead of their vanilla base type. Templates not listed (slimes, creepers, spiders, archers, ...) keep the vanilla
 * body, whose behaviour (exploding, climbing, shooting) the template relies on.
 */
public final class MonsterBodies {
    private static final Map<String, String> VARIANTS = Map.ofEntries(
            Map.entry("wandering_dead", "ghoul"),
            Map.entry("highway_bandit", "crossbowman"),
            Map.entry("bandit_chief", "bandit"),
            Map.entry("forest_witch", "dryad"),
            Map.entry("elder_druid", "treant"),
            Map.entry("sand_husk", "mummy"),
            Map.entry("giant_scarab", "scorpion"),
            Map.entry("tomb_warden", "sand_golem"),
            Map.entry("snowfield_archer", "frost_mage"),
            Map.entry("snowfield_yeti", "ice_golem"),
            Map.entry("crater_blaze", "fire_imp"),
            Map.entry("cloud_phantom", "harpy"),
            Map.entry("storm_spirit", "storm_spirit"),
            Map.entry("void_wanderer", "shade"),
            Map.entry("knight_of_the_end", "dark_knight"),
            Map.entry("doom_cultist", "cultist"),
            Map.entry("herald_of_ruin", "void_mage"),
            Map.entry("d1_bandit", "bandit"),
            Map.entry("d1_crawler", "cave_rat"),
            Map.entry("d2_grove_priestess", "dryad"),
            Map.entry("d3_temple_mummy", "mummy"),
            Map.entry("d3_scarab_swarm", "scorpion"),
            Map.entry("d3_sun_priest", "cultist"),
            Map.entry("d4_frozen_prisoner", "prisoner"),
            Map.entry("d4_ice_crawler", "frost_hound"),
            Map.entry("d4_jailer", "jailer"),
            Map.entry("d5_ice_knight", "ice_knight"),
            Map.entry("d5_queens_handmaiden", "frost_mage"),
            Map.entry("d6_flame_spirit", "fire_spirit"),
            Map.entry("d6_dragon_guard", "lava_golem"),
            Map.entry("d7_ancient_drone", "sky_sentinel"),
            Map.entry("d7_ancient_guardian", "ruin_construct"),
            Map.entry("d8_void_acolyte", "cultist"),
            Map.entry("d8_doom_guard", "dark_knight"),
            Map.entry("d8_doom_sorcerer", "void_mage"),
            Map.entry("d8_tower_behemoth", "abyss_hound"),
            Map.entry("b1_minion", "bandit"),
            Map.entry("b2_minion", "dryad"),
            Map.entry("b3_minion", "mummy"),
            Map.entry("b4_minion", "jailer"),
            Map.entry("b5_minion", "ice_wraith"),
            Map.entry("b6_minion", "salamander"),
            Map.entry("b7_minion", "sky_sentinel"),
            Map.entry("b8_minion", "shade"),
            Map.entry("final_minion", "dark_knight"));

    private MonsterBodies() {
    }

    public static Optional<String> variantFor(String templateId) {
        return Optional.ofNullable(VARIANTS.get(templateId));
    }

    public static Map<String, String> all() {
        return VARIANTS;
    }
}
