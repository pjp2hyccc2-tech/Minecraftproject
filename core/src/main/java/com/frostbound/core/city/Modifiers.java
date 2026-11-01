package com.frostbound.core.city;

import java.util.HashSet;
import java.util.Set;

/**
 * City-wide modifiers, rebuilt from research, laws and special buildings whenever one of them changes
 * (and every hour). Rules read these instead of checking techs and laws directly.
 */
public final class Modifiers {
    // Generator and heat
    public double coalBurnMult = 1.0;
    public double heatCapacityBonus = 0.0;
    public double heatBoostBonus = 0.0;
    public double rangeBonus = 0.0;
    public double overloadStressMult = 1.0;
    public double housingInsulation = 0.0;
    public int maxGeneratorLevel = 3;

    // Production
    public double resourceMult = 1.0;
    public double foodMult = 1.0;
    public double coalPitMult = 1.0;
    public double huntingMult = 1.0;
    public double buildSpeedMult = 1.0;
    public double researchMult = 1.0;
    public double lawProductionMult = 1.0;

    // Health
    public double healingMult = 1.0;
    public double illnessMult = 1.0;
    public double medicineUseMult = 1.0;

    // Society
    public int shiftStartHour = 8;
    public int shiftEndHour = 18;
    public boolean childLabor;
    public boolean rationing;
    public boolean medicalPriority;
    public boolean curfew;
    public boolean freeMedicine;
    public boolean entertainment;
    public boolean strictControl;
    public boolean selfGovernance;
    public double hopeTarget;
    public double discontentTarget;
    public double discontentRateMult = 1.0;
    public double fatigueMult = 1.0;
    public double foodUseMult = 1.0;
    public double satietyGainMult = 1.0;

    // Expeditions
    public double expeditionSpeedMult = 1.0;
    public double expeditionRiskMult = 1.0;
    public boolean longRange;

    /** Building type ids that may be built. */
    public final Set<String> unlocked = new HashSet<>();
    /** Completed research ids (copied for quick checks). */
    public final Set<String> techs = new HashSet<>();
}
