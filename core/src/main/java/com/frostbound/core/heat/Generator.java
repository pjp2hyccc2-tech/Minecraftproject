package com.frostbound.core.heat;

import com.frostbound.core.Difficulty;
import com.frostbound.core.city.Modifiers;
import com.frostbound.core.resources.ResourceLedger;
import com.frostbound.core.resources.ResourceType;
import com.frostbound.core.sim.SimClock;
import com.frostbound.core.util.Tree;
import java.util.Map;

/**
 * The giant steam generator: operating level 0-4, an internal coal buffer refilled from the stockpile,
 * overload with accumulating stress, and breakdown/repair. Provisional balance values.
 */
public final class Generator {
    public static final int[] BURN_PER_DAY = {0, 60, 90, 135, 200};
    public static final int[] CAPACITY = {0, 120, 200, 300, 420};
    public static final int[] BOOST = {0, 25, 35, 45, 55};
    public static final int[] RANGE = {0, 40, 48, 56, 64};
    public static final long BUFFER_MAX = 100 * ResourceLedger.MILLI;

    public enum Change {
        NONE,
        FUEL_OUT,
        FUEL_RESTORED,
        BROKE,
        REPAIRED
    }

    private int level = 1;
    private boolean overload;
    private double stress;
    private boolean broken;
    private boolean repairing;
    private long repairStepsLeft;
    private long buffer = BUFFER_MAX / 2;
    private boolean fuelOut;

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(0, Math.min(4, level));
        if (this.level == 0) {
            overload = false;
        }
    }

    public boolean overload() {
        return overload;
    }

    public void setOverload(boolean overload) {
        this.overload = overload && level > 0;
    }

    public double stress() {
        return stress;
    }

    public void addStress(double delta) {
        stress = Math.max(0, Math.min(100, stress + delta));
    }

    public boolean broken() {
        return broken;
    }

    public boolean repairing() {
        return repairing;
    }

    public long repairStepsLeft() {
        return repairStepsLeft;
    }

    public boolean fuelOut() {
        return fuelOut;
    }

    public long bufferMilli() {
        return buffer;
    }

    public boolean running() {
        return level > 0 && !broken && !fuelOut;
    }

    public void breakDown() {
        broken = true;
        overload = false;
        repairing = false;
    }

    public void startRepair(long steps) {
        if (broken) {
            repairing = true;
            repairStepsLeft = Math.max(1, steps);
        }
    }

    public double burnPerDay(Modifiers m, Difficulty d) {
        if (level == 0 || broken) {
            return 0;
        }
        return BURN_PER_DAY[level] * (overload ? 1.5 : 1.0) * d.coalBurn * m.coalBurnMult;
    }

    public double capacity(Modifiers m) {
        return running() ? CAPACITY[level] * (overload ? 1.4 : 1.0) * (1.0 + m.heatCapacityBonus) : 0;
    }

    public double maxBoost(Modifiers m) {
        return running() ? BOOST[level] + (overload ? 10 : 0) + m.heatBoostBonus : 0;
    }

    public double range(Modifiers m) {
        return running() ? nominalRange(m) : 0;
    }

    /** Range the generator would have at its current level if it were running. */
    public double nominalRange(Modifiers m) {
        return level == 0 ? 0 : RANGE[level] + (overload ? 16 : 0) + m.rangeBonus;
    }

    /** One simulation step. */
    public Change step(ResourceLedger ledger, Modifiers m, Difficulty d) {
        if (broken) {
            if (repairing && --repairStepsLeft <= 0) {
                broken = false;
                repairing = false;
                stress = 20;
                return Change.REPAIRED;
            }
            return Change.NONE;
        }
        Change change = Change.NONE;
        if (level > 0) {
            // Burn straight from the stockpile, falling back on the small hopper buffer; refill the hopper gently
            // so the coal consumption stays smooth (no large hourly spikes).
            long need = Math.round(burnPerDay(m, d) * ResourceLedger.MILLI / SimClock.STEPS_PER_DAY);
            long missing = need - ledger.take(ResourceType.COAL, need);
            if (missing > 0) {
                long fromBuffer = Math.min(buffer, missing);
                buffer -= fromBuffer;
                missing -= fromBuffer;
            }
            if (missing <= 0) {
                if (buffer < BUFFER_MAX) {
                    buffer += ledger.take(ResourceType.COAL, Math.min(BUFFER_MAX - buffer, Math.max(1, need / 2)));
                }
                if (fuelOut) {
                    fuelOut = false;
                    change = Change.FUEL_RESTORED;
                }
            } else {
                buffer = 0;
                if (!fuelOut) {
                    fuelOut = true;
                    overload = false;
                    change = Change.FUEL_OUT;
                }
            }
        } else if (fuelOut) {
            fuelOut = false;
        }
        if (overload && running()) {
            stress += 100.0 / (2.0 * SimClock.STEPS_PER_DAY) * m.overloadStressMult;
            if (stress >= 100) {
                stress = 100;
                breakDown();
                return Change.BROKE;
            }
        } else {
            stress = Math.max(0, stress - 100.0 / SimClock.STEPS_PER_DAY);
        }
        return change;
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        m.put("level", level);
        m.put("overload", overload);
        m.put("stress", stress);
        m.put("broken", broken);
        m.put("repairing", repairing);
        m.put("repairLeft", repairStepsLeft);
        m.put("buffer", buffer);
        m.put("fuelOut", fuelOut);
        return m;
    }

    public void load(Map<String, Object> m) {
        level = Tree.i(m, "level", 1, 0, 4);
        overload = Tree.b(m, "overload", false);
        stress = Tree.d(m, "stress", 0, 0, 100);
        broken = Tree.b(m, "broken", false);
        repairing = Tree.b(m, "repairing", false);
        repairStepsLeft = Tree.l(m, "repairLeft", 0L, 0L, 1_000_000L);
        buffer = Tree.l(m, "buffer", 0L, 0L, BUFFER_MAX);
        fuelOut = Tree.b(m, "fuelOut", false);
        if (level == 0) {
            overload = false;
        }
    }
}
