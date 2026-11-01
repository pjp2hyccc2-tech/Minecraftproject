package com.frostbound.core.heat;

import com.frostbound.core.city.Building;
import com.frostbound.core.city.BuildingCategory;
import com.frostbound.core.city.BuildingTypes;
import com.frostbound.core.city.Modifiers;
import java.util.List;

/**
 * Shares the generator's heat output among the heated buildings in range.
 *
 * <p>f = min(1, C / sum of demand) is the city-wide supply ratio (computed separately for medical
 * buildings first when the medical priority law is active). A building gets f x eta(distance) x max
 * boost; its indoor temperature approaches outside + insulation + boost with a 30-step time constant.
 */
public final class HeatNetwork {
    public static final double TAU_STEPS = 30.0;
    public static final double COMFORT = 10.0;
    private static final double K = 1.0 - Math.exp(-1.0 / TAU_STEPS);
    public static final int GRID_HALF = 128;
    public static final int GRID_CELL = 4;
    public static final int GRID_SIZE = GRID_HALF * 2 / GRID_CELL;

    private double supplyRatio = 1.0;
    private double otherRatio = 1.0;
    private double totalDemand;
    private double capacity;
    private double range;
    private double boost;

    public static double eta(double distance, double range) {
        if (range <= 0 || distance > range) {
            return 0;
        }
        double core = 0.6 * range;
        if (distance <= core) {
            return 1.0;
        }
        return 1.0 - 0.5 * (distance - core) / (range - core);
    }

    public void step(List<Building> buildings, double gx, double gz, Generator gen, Modifiers m, double outside) {
        capacity = gen.capacity(m);
        range = gen.range(m);
        boost = gen.maxBoost(m);
        double demandMed = 0;
        double demandOther = 0;
        for (Building b : buildings) {
            double d = Math.hypot(b.centerX() - gx, b.centerZ() - gz);
            b.eta = eta(d, range);
            if (!b.active() || b.type().heatDemand <= 0 || b.eta <= 0 || b.type() == BuildingTypes.GENERATOR) {
                continue;
            }
            if (m.medicalPriority && b.type().category == BuildingCategory.MEDICAL) {
                demandMed += b.type().heatDemand;
            } else {
                demandOther += b.type().heatDemand;
            }
        }
        double fMed = demandMed > 0 ? Math.min(1.0, capacity / demandMed) : 1.0;
        double left = Math.max(0, capacity - demandMed * fMed);
        double fOther = demandOther > 0 ? Math.min(1.0, left / demandOther) : (capacity > 0 ? 1.0 : 0.0);
        if (capacity <= 0) {
            fMed = 0;
            fOther = 0;
        }
        otherRatio = fOther;
        totalDemand = demandMed + demandOther;
        supplyRatio = totalDemand > 0 ? Math.min(1.0, capacity / totalDemand) : (capacity > 0 ? 1.0 : 0.0);
        for (Building b : buildings) {
            if (b.type() == BuildingTypes.GENERATOR) {
                b.heatBoost = gen.running() ? 40 : 0;
                b.temperature += (outside + b.type().insulation + b.heatBoost - b.temperature) * K;
                continue;
            }
            boolean med = m.medicalPriority && b.type().category == BuildingCategory.MEDICAL;
            double f = med ? fMed : fOther;
            b.heatBoost = b.active() && b.type().heatDemand > 0 ? f * b.eta * boost : 0;
            double insulation = 0;
            if (b.active()) {
                insulation = b.type().insulation(b.level);
                if (b.type().category == BuildingCategory.HOUSING) {
                    insulation += m.housingInsulation;
                }
            }
            double eq = outside + insulation + b.heatBoost;
            b.temperature += (eq - b.temperature) * K;
        }
    }

    /** Open-air temperature at a distance from the generator (people without shelter). */
    public double openAir(double distance, double outside) {
        return outside + 0.5 * otherRatio * eta(distance, range) * boost;
    }

    /** Predicted indoor temperature of a standard wooden house at a distance (for the overlay). */
    public double predictedHouse(double distance, double outside, Modifiers m) {
        double e = eta(distance, range);
        return outside + 10.0 + m.housingInsulation + otherRatio * e * boost;
    }

    /**
     * Heat overlay grid around the generator: {@value #GRID_SIZE} x {@value #GRID_SIZE} cells of
     * {@value #GRID_CELL} blocks. 0 = out of range, otherwise predicted house temperature + 100 (1-255).
     */
    public byte[] grid(double gx, double gz, double outside, Modifiers m) {
        byte[] g = new byte[GRID_SIZE * GRID_SIZE];
        for (int iz = 0; iz < GRID_SIZE; iz++) {
            for (int ix = 0; ix < GRID_SIZE; ix++) {
                double cx = gx - GRID_HALF + ix * GRID_CELL + GRID_CELL / 2.0;
                double cz = gz - GRID_HALF + iz * GRID_CELL + GRID_CELL / 2.0;
                double d = Math.hypot(cx - gx, cz - gz);
                if (eta(d, range) <= 0) {
                    continue;
                }
                long v = Math.round(predictedHouse(d, outside, m)) + 100;
                g[iz * GRID_SIZE + ix] = (byte) Math.max(1, Math.min(255, v));
            }
        }
        return g;
    }

    public double supplyRatio() {
        return supplyRatio;
    }

    public double totalDemand() {
        return totalDemand;
    }

    public double capacity() {
        return capacity;
    }

    public double range() {
        return range;
    }

    public double boost() {
        return boost;
    }
}
