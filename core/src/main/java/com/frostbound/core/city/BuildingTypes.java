package com.frostbound.core.city;

import static com.frostbound.core.city.BuildingCategory.HOUSING;
import static com.frostbound.core.city.BuildingCategory.INDUSTRY;
import static com.frostbound.core.city.BuildingCategory.MEDICAL;
import static com.frostbound.core.city.BuildingCategory.RESOURCE;
import static com.frostbound.core.city.BuildingCategory.SOCIAL;
import static com.frostbound.core.city.BuildingCategory.SPECIAL;
import static com.frostbound.core.population.Profession.ENGINEER;
import static com.frostbound.core.population.Profession.MEDIC;
import static com.frostbound.core.population.Profession.TECHNICIAN;
import static com.frostbound.core.population.Profession.WORKER;
import static com.frostbound.core.resources.ResourceType.COAL;
import static com.frostbound.core.resources.ResourceType.FOOD;
import static com.frostbound.core.resources.ResourceType.IRON;
import static com.frostbound.core.resources.ResourceType.MEDICINE;
import static com.frostbound.core.resources.ResourceType.STEAM_CORE;
import static com.frostbound.core.resources.ResourceType.WOOD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All building types (provisional balance values). */
public final class BuildingTypes {
    private static final Map<String, BuildingType> BY_ID = new LinkedHashMap<>();

    public static final BuildingType GENERATOR = add(BuildingType.of("generator", SPECIAL, 13, 13, 40)
            .heat(30, 0).special().done());

    // Housing
    public static final BuildingType TENT = add(BuildingType.of("tent", HOUSING, 3, 3, 3)
            .cost(WOOD, 10).build(2, 2).housing(6).heat(3, 4).done());
    public static final BuildingType SHELTER = add(BuildingType.of("shelter", HOUSING, 5, 5, 4)
            .cost(WOOD, 25).build(4, 3).housing(10).heat(6, 6).done());
    public static final BuildingType WOODEN_HOUSE = add(BuildingType.of("wooden_house", HOUSING, 5, 7, 5)
            .cost(WOOD, 40, IRON, 5).build(6, 4).housing(10).heat(10, 7).tech("wooden_houses").done());
    public static final BuildingType APARTMENT = add(BuildingType.of("apartment", HOUSING, 7, 7, 8)
            .cost(WOOD, 60, IRON, 25).build(12, 6).housing(24).heat(12, 14).tech("apartments").done());
    public static final BuildingType HEATED_HOUSE = add(BuildingType.of("heated_house", HOUSING, 5, 7, 5)
            .cost(WOOD, 40, IRON, 20, STEAM_CORE, 1).build(10, 4).housing(10).heat(20, 6).tech("heated_houses").done());

    // Resources
    public static final BuildingType COAL_PIT = add(BuildingType.of("coal_pit", RESOURCE, 7, 7, 5)
            .cost(WOOD, 20).build(4, 4).workers(10, WORKER).output(COAL, 1.0).heat(5, 8).done());
    public static final BuildingType SAWMILL = add(BuildingType.of("sawmill", RESOURCE, 7, 5, 5)
            .cost(WOOD, 20).build(4, 4).workers(10, WORKER).output(WOOD, 0.8).heat(5, 8).done());
    public static final BuildingType STEELWORKS = add(BuildingType.of("steelworks", RESOURCE, 7, 7, 7)
            .cost(WOOD, 30).build(6, 5).workers(10, TECHNICIAN).output(IRON, 0.5).input(COAL, 0.2).heat(6, 10).done());
    public static final BuildingType MINE = add(BuildingType.of("mine", RESOURCE, 7, 7, 6)
            .cost(WOOD, 40, IRON, 20).build(8, 5).workers(15, WORKER).output(COAL, 0.6).output2(IRON, 0.3)
            .heat(4, 8).tech("deep_mining").done());
    public static final BuildingType WAREHOUSE = add(BuildingType.of("warehouse", RESOURCE, 7, 7, 6)
            .cost(WOOD, 30).build(4, 4).storage(400).heat(0, 0).done());
    public static final BuildingType CORE_EXTRACTOR = add(BuildingType.of("core_extractor", RESOURCE, 7, 7, 8)
            .cost(WOOD, 50, IRON, 40).build(12, 6).workers(10, TECHNICIAN).output(STEAM_CORE, 0.02)
            .heat(6, 10).tech("core_extraction").done());

    // Food
    public static final BuildingType GREENHOUSE = add(BuildingType.of("greenhouse", BuildingCategory.FOOD, 7, 7, 5)
            .cost(WOOD, 30, IRON, 15).build(6, 4).workers(10, WORKER).output(FOOD, 0.6).heat(8, 12).tech("greenhouse").done());
    public static final BuildingType FOOD_PROCESSOR = add(BuildingType.of("food_processor", BuildingCategory.FOOD, 7, 5, 5)
            .cost(WOOD, 25, IRON, 10).build(6, 4).workers(8, WORKER).output(FOOD, 0.5).input(WOOD, 0.1)
            .heat(6, 8).tech("food_processing").done());
    public static final BuildingType HUNTERS_LODGE = add(BuildingType.of("hunters_lodge", BuildingCategory.FOOD, 5, 5, 4)
            .cost(WOOD, 15).build(3, 3).workers(8, WORKER).output(FOOD, 1.0).outdoor().heat(4, 6).done());
    public static final BuildingType FISHERY = add(BuildingType.of("fishery", BuildingCategory.FOOD, 5, 5, 4)
            .cost(WOOD, 20).build(3, 3).workers(6, WORKER).output(FOOD, 0.7).outdoor().heat(4, 6).done());

    // Medical
    public static final BuildingType MEDICAL_TENT = add(BuildingType.of("medical_tent", MEDICAL, 5, 5, 4)
            .cost(WOOD, 15).build(2, 2).workers(2, MEDIC).beds(8).heat(4, 6).done());
    public static final BuildingType CLINIC = add(BuildingType.of("clinic", MEDICAL, 7, 5, 5)
            .cost(WOOD, 30, IRON, 10).build(6, 4).workers(4, MEDIC).beds(16).heat(10, 8).tech("clinic").done());
    public static final BuildingType HOSPITAL = add(BuildingType.of("hospital", MEDICAL, 9, 7, 7)
            .cost(WOOD, 50, IRON, 30).build(12, 6).workers(8, MEDIC).beds(30).heat(12, 14).tech("hospital").done());
    public static final BuildingType ADVANCED_LAB = add(BuildingType.of("advanced_lab", MEDICAL, 7, 7, 7)
            .cost(WOOD, 40, IRON, 30, STEAM_CORE, 1).build(12, 5).workers(6, MEDIC).beds(5).output(MEDICINE, 0.15)
            .heat(12, 12).tech("advanced_medicine").done());

    // Industry
    public static final BuildingType WORKSHOP = add(BuildingType.of("workshop", INDUSTRY, 7, 5, 5)
            .cost(WOOD, 25).build(4, 4).workers(6, TECHNICIAN).output(MEDICINE, 0.12).input(WOOD, 0.2).heat(6, 8).done());
    public static final BuildingType MACHINE_FACTORY = add(BuildingType.of("machine_factory", INDUSTRY, 9, 7, 7)
            .cost(WOOD, 40, IRON, 40).build(10, 5).workers(8, TECHNICIAN).input(COAL, 0.3).heat(8, 12)
            .tech("machine_factory").done());
    public static final BuildingType STEAM_ENGINE_FACTORY = add(BuildingType.of("steam_engine_factory", INDUSTRY, 9, 7, 8)
            .cost(WOOD, 40, IRON, 50, STEAM_CORE, 2).build(14, 6).workers(8, ENGINEER).input(IRON, 0.1).heat(8, 12)
            .tech("steam_engine_factory").done());
    public static final BuildingType RESEARCH_LAB = add(BuildingType.of("research_lab", INDUSTRY, 7, 7, 6)
            .cost(WOOD, 30, IRON, 10).build(6, 4).workers(5, ENGINEER).research(1.0).heat(10, 10).done());

    // Social
    public static final BuildingType MEETING_HALL = add(BuildingType.of("meeting_hall", SOCIAL, 9, 7, 6)
            .cost(WOOD, 40).build(6, 4).workers(2, WORKER).social(6, 0, 60).heat(8, 10).done());
    public static final BuildingType TAVERN = add(BuildingType.of("tavern", SOCIAL, 7, 7, 5)
            .cost(WOOD, 30).build(5, 4).workers(3, WORKER).social(0, -8, 50).input(FOOD, 0.05).heat(8, 10)
            .law("entertainment").done());
    public static final BuildingType SCHOOL = add(BuildingType.of("school", SOCIAL, 7, 7, 5)
            .cost(WOOD, 35, IRON, 5).build(6, 4).workers(2, WORKER).social(4, 0, 40).heat(10, 10).done());
    public static final BuildingType CHAPEL = add(BuildingType.of("chapel", SOCIAL, 7, 9, 9)
            .cost(WOOD, 40, IRON, 10).build(8, 4).workers(2, WORKER).social(5, -2, 60).heat(8, 10).done());
    public static final BuildingType GUARD_POST = add(BuildingType.of("guard_post", SOCIAL, 5, 5, 5)
            .cost(WOOD, 25, IRON, 10).build(4, 3).workers(4, WORKER).social(0, -6, 60).heat(6, 6)
            .law("curfew").done());

    private BuildingTypes() {
    }

    private static BuildingType add(BuildingType t) {
        BY_ID.put(t.id, t);
        return t;
    }

    public static BuildingType get(String id) {
        return BY_ID.get(id);
    }

    public static List<BuildingType> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public static List<BuildingType> buildable() {
        List<BuildingType> out = new ArrayList<>();
        for (BuildingType t : BY_ID.values()) {
            if (t.buildable) {
                out.add(t);
            }
        }
        return out;
    }

    /** Types that need no research and no law. */
    public static boolean initiallyUnlocked(BuildingType t) {
        return t.buildable && t.unlockTech == null && t.unlockLaw == null;
    }
}
