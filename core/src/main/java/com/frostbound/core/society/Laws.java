package com.frostbound.core.society;

import com.frostbound.core.resources.Cost;
import com.frostbound.core.resources.ResourceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The ten laws from the specification (display names live in the language files). */
public final class Laws {
    private static final Map<String, Law> BY_ID = new LinkedHashMap<>();

    public static final Law EXTENDED_SHIFT = add(new Law("extended_shift", List.of(), List.of(), -2, 5, Cost.NONE, false, m -> {
        m.shiftEndHour = 22;
        m.fatigueMult *= 1.5;
        m.discontentTarget += 8;
    }));
    public static final Law EMERGENCY_SHIFT = add(new Law("emergency_shift", List.of("extended_shift"), List.of(), -3, 12,
            Cost.NONE, true, m -> {
            }));
    public static final Law FOOD_RATIONING = add(new Law("food_rationing", List.of(), List.of(), -3, 4, Cost.NONE, false, m -> {
        m.rationing = true;
        m.foodUseMult *= 0.7;
        m.satietyGainMult *= 0.75;
        m.hopeTarget -= 4;
        m.discontentTarget += 4;
    }));
    public static final Law MEDICAL_PRIORITY = add(new Law("medical_priority", List.of(), List.of(), 3, 0, Cost.NONE, false, m -> {
        m.medicalPriority = true;
        m.healingMult *= 1.25;
        m.hopeTarget += 2;
    }));
    public static final Law CURFEW = add(new Law("curfew", List.of(), List.of(), -3, 2, Cost.NONE, false, m -> {
        m.curfew = true;
        m.discontentRateMult *= 0.8;
        m.hopeTarget -= 3;
    }));
    public static final Law CHILD_LABOR = add(new Law("child_labor", List.of(), List.of(), -8, 6, Cost.NONE, false, m -> {
        m.childLabor = true;
        m.hopeTarget -= 6;
        m.discontentTarget += 4;
    }));
    public static final Law FREE_MEDICINE = add(new Law("free_medicine", List.of(), List.of(), 5, -2, Cost.NONE, false, m -> {
        m.freeMedicine = true;
        m.medicineUseMult *= 1.3;
        m.healingMult *= 1.15;
        m.hopeTarget += 5;
    }));
    public static final Law ENTERTAINMENT = add(new Law("entertainment", List.of(), List.of(), 3, -5,
            Cost.of(ResourceType.WOOD, 5), false, m -> {
        m.entertainment = true;
        m.discontentTarget -= 6;
    }));
    public static final Law STRICT_CONTROL = add(new Law("strict_control", List.of("curfew"), List.of("self_governance"), -8, -6,
            Cost.NONE, false, m -> {
        m.strictControl = true;
        m.discontentRateMult *= 0.5;
        m.hopeTarget -= 8;
        m.lawProductionMult *= 1.05;
    }));
    public static final Law SELF_GOVERNANCE = add(new Law("self_governance", List.of(), List.of("strict_control"), 8, -4,
            Cost.NONE, false, m -> {
        m.selfGovernance = true;
        m.hopeTarget += 8;
        m.discontentTarget -= 4;
        m.lawProductionMult *= 0.95;
    }));

    private Laws() {
    }

    private static Law add(Law law) {
        BY_ID.put(law.id, law);
        return law;
    }

    public static Law get(String id) {
        return BY_ID.get(id);
    }

    public static List<Law> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }
}
