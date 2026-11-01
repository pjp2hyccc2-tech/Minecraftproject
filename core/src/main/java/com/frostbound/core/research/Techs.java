package com.frostbound.core.research;

import static com.frostbound.core.resources.ResourceType.IRON;
import static com.frostbound.core.resources.ResourceType.STEAM_CORE;
import static com.frostbound.core.resources.ResourceType.WOOD;

import com.frostbound.core.city.Modifiers;
import com.frostbound.core.resources.Cost;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The technology tree: eight categories, tiers 1-3 (provisional costs). */
public final class Techs {
    public static final String[] CATEGORIES = {
        "generator", "heating", "mining", "food", "medical", "construction", "steam", "exploration"
    };
    private static final Map<String, Tech> BY_ID = new LinkedHashMap<>();

    static {
        // Generator
        add("gen_efficiency", "generator", 1, List.of(), List.of(), Cost.of(WOOD, 20), m -> m.coalBurnMult *= 0.9);
        add("gen_range", "generator", 2, List.of("gen_efficiency"), List.of(), Cost.of(IRON, 20), m -> m.rangeBonus += 8);
        add("gen_overdrive", "generator", 2, List.of("gen_efficiency"), List.of(), Cost.of(IRON, 25),
                m -> m.overloadStressMult *= 0.6);
        add("gen_level4", "generator", 3, List.of("gen_range"), List.of(), Cost.of(IRON, 60, STEAM_CORE, 2),
                m -> m.maxGeneratorLevel = 4);
        // Heating
        add("insulation", "heating", 1, List.of(), List.of(), Cost.of(WOOD, 30), m -> m.housingInsulation += 3);
        add("heated_houses", "heating", 2, List.of("insulation"), List.of("heated_house"), Cost.of(IRON, 20), m -> {
        });
        add("heat_boost", "heating", 3, List.of("heated_houses"), List.of(), Cost.of(IRON, 40, STEAM_CORE, 1), m -> {
            m.heatBoostBonus += 6;
            m.heatCapacityBonus += 0.1;
        });
        // Mining
        add("coal_efficiency", "mining", 1, List.of(), List.of(), Cost.of(WOOD, 20), m -> m.coalPitMult *= 1.2);
        add("deep_mining", "mining", 2, List.of("coal_efficiency"), List.of("mine"), Cost.of(IRON, 15), m -> {
        });
        add("core_extraction", "mining", 3, List.of("deep_mining"), List.of("core_extractor"), Cost.of(IRON, 50), m -> {
        });
        // Food
        add("hunting_gear", "food", 1, List.of(), List.of(), Cost.of(WOOD, 20), m -> m.huntingMult *= 1.25);
        add("greenhouse", "food", 2, List.of("hunting_gear"), List.of("greenhouse"), Cost.of(IRON, 20), m -> {
        });
        add("food_processing", "food", 2, List.of("hunting_gear"), List.of("food_processor"), Cost.of(IRON, 15), m -> {
        });
        // Medical
        add("clinic", "medical", 1, List.of(), List.of("clinic"), Cost.of(WOOD, 25), m -> {
        });
        add("hospital", "medical", 2, List.of("clinic"), List.of("hospital"), Cost.of(IRON, 25), m -> {
        });
        add("advanced_medicine", "medical", 3, List.of("hospital"), List.of("advanced_lab"), Cost.of(IRON, 40, STEAM_CORE, 1),
                m -> m.healingMult *= 1.25);
        // Construction
        add("wooden_houses", "construction", 1, List.of(), List.of("wooden_house"), Cost.of(WOOD, 25), m -> {
        });
        add("apartments", "construction", 2, List.of("wooden_houses"), List.of("apartment"), Cost.of(IRON, 20), m -> {
        });
        add("faster_building", "construction", 2, List.of("wooden_houses"), List.of(), Cost.of(IRON, 20),
                m -> m.buildSpeedMult *= 1.3);
        // Steam engines
        add("research_methods", "steam", 1, List.of(), List.of(), Cost.of(WOOD, 20), m -> m.researchMult *= 1.2);
        add("machine_factory", "steam", 2, List.of("research_methods"), List.of("machine_factory"), Cost.of(IRON, 30), m -> {
        });
        add("steam_engine_factory", "steam", 3, List.of("machine_factory"), List.of("steam_engine_factory"),
                Cost.of(IRON, 60, STEAM_CORE, 2), m -> {
                });
        // Exploration
        add("scouting_gear", "exploration", 1, List.of(), List.of(), Cost.of(WOOD, 20), m -> m.expeditionSpeedMult *= 1.25);
        add("cold_suits", "exploration", 2, List.of("scouting_gear"), List.of(), Cost.of(IRON, 20),
                m -> m.expeditionRiskMult *= 0.5);
        add("long_range", "exploration", 3, List.of("cold_suits"), List.of(), Cost.of(IRON, 30), m -> m.longRange = true);
    }

    private Techs() {
    }

    private static void add(String id, String category, int tier, List<String> requires, List<String> unlocks, Cost cost,
            Consumer<Modifiers> effect) {
        int points = tier == 1 ? 60 : tier == 2 ? 150 : 300;
        BY_ID.put(id, new Tech(id, category, tier, points, cost, requires, unlocks, effect));
    }

    public static Tech get(String id) {
        return BY_ID.get(id);
    }

    public static List<Tech> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public static List<Tech> inCategory(String category) {
        List<Tech> out = new ArrayList<>();
        for (Tech t : BY_ID.values()) {
            if (t.category.equals(category)) {
                out.add(t);
            }
        }
        return out;
    }
}
