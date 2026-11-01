package com.frostbound.core.city;

import com.frostbound.core.population.Profession;
import com.frostbound.core.resources.Cost;
import com.frostbound.core.resources.ResourceType;

/**
 * Static definition of a building type. Values are provisional balance numbers; per-level values grow by
 * 30% (workers, output) or 25% (housing, beds) for each level above 1.
 */
public final class BuildingType {
    public final String id;
    public final BuildingCategory category;
    public final int width;
    public final int depth;
    public final int height;
    public final Cost cost;
    public final int buildHours;
    public final int builders;
    public final int maxWorkers;
    public final Profession job;
    public final int housing;
    public final double insulation;
    public final double heatDemand;
    public final ResourceType output;
    public final double outputRate;
    public final ResourceType output2;
    public final double output2Rate;
    public final ResourceType input;
    public final double inputRate;
    public final double research;
    public final int beds;
    public final int storage;
    public final double hopeEffect;
    public final double discontentEffect;
    public final int socialCapacity;
    public final boolean outdoor;
    public final String unlockTech;
    public final String unlockLaw;
    public final boolean buildable;
    public final int maxLevel;

    private BuildingType(Builder b) {
        id = b.id;
        category = b.category;
        width = b.width;
        depth = b.depth;
        height = b.height;
        cost = b.cost;
        buildHours = b.buildHours;
        builders = b.builders;
        maxWorkers = b.maxWorkers;
        job = b.job;
        housing = b.housing;
        insulation = b.insulation;
        heatDemand = b.heatDemand;
        output = b.output;
        outputRate = b.outputRate;
        output2 = b.output2;
        output2Rate = b.output2Rate;
        input = b.input;
        inputRate = b.inputRate;
        research = b.research;
        beds = b.beds;
        storage = b.storage;
        hopeEffect = b.hopeEffect;
        discontentEffect = b.discontentEffect;
        socialCapacity = b.socialCapacity;
        outdoor = b.outdoor;
        unlockTech = b.unlockTech;
        unlockLaw = b.unlockLaw;
        buildable = b.buildable;
        maxLevel = b.maxLevel;
    }

    public String key() {
        return "frostbound.building." + id;
    }

    public static double levelMult(int level) {
        return 1.0 + 0.3 * (Math.max(1, level) - 1);
    }

    public int maxWorkers(int level) {
        return (int) Math.round(maxWorkers * levelMult(level));
    }

    public int housing(int level) {
        return (int) Math.round(housing * (1.0 + 0.25 * (Math.max(1, level) - 1)));
    }

    public int beds(int level) {
        return (int) Math.round(beds * (1.0 + 0.25 * (Math.max(1, level) - 1)));
    }

    public double insulation(int level) {
        return insulation + 3.0 * (Math.max(1, level) - 1);
    }

    /** Cost to go from {@code level} to {@code level + 1}. */
    public Cost upgradeCost(int level) {
        return cost.scaled(0.6 + 0.2 * level);
    }

    public int upgradeHours(int level) {
        return Math.max(2, (int) Math.round(buildHours * (0.5 + 0.25 * level)));
    }

    public boolean employs() {
        return maxWorkers > 0;
    }

    static Builder of(String id, BuildingCategory category, int width, int depth, int height) {
        return new Builder(id, category, width, depth, height);
    }

    static final class Builder {
        private final String id;
        private final BuildingCategory category;
        private final int width;
        private final int depth;
        private final int height;
        private Cost cost = Cost.NONE;
        private int buildHours = 4;
        private int builders = 3;
        private int maxWorkers;
        private Profession job = Profession.WORKER;
        private int housing;
        private double insulation = 5;
        private double heatDemand = 8;
        private ResourceType output;
        private double outputRate;
        private ResourceType output2;
        private double output2Rate;
        private ResourceType input;
        private double inputRate;
        private double research;
        private int beds;
        private int storage;
        private double hopeEffect;
        private double discontentEffect;
        private int socialCapacity;
        private boolean outdoor;
        private String unlockTech;
        private String unlockLaw;
        private boolean buildable = true;
        private int maxLevel = 3;

        private Builder(String id, BuildingCategory category, int width, int depth, int height) {
            this.id = id;
            this.category = category;
            this.width = width;
            this.depth = depth;
            this.height = height;
        }

        Builder cost(Object... pairs) {
            cost = Cost.of(pairs);
            return this;
        }

        Builder build(int hours, int builders) {
            this.buildHours = hours;
            this.builders = builders;
            return this;
        }

        Builder workers(int max, Profession job) {
            this.maxWorkers = max;
            this.job = job;
            return this;
        }

        Builder housing(int capacity) {
            this.housing = capacity;
            return this;
        }

        Builder heat(double insulation, double demand) {
            this.insulation = insulation;
            this.heatDemand = demand;
            return this;
        }

        Builder output(ResourceType t, double perWorkerHour) {
            this.output = t;
            this.outputRate = perWorkerHour;
            return this;
        }

        Builder output2(ResourceType t, double perWorkerHour) {
            this.output2 = t;
            this.output2Rate = perWorkerHour;
            return this;
        }

        Builder input(ResourceType t, double perWorkerHour) {
            this.input = t;
            this.inputRate = perWorkerHour;
            return this;
        }

        Builder research(double perWorkerHour) {
            this.research = perWorkerHour;
            return this;
        }

        Builder beds(int beds) {
            this.beds = beds;
            return this;
        }

        Builder storage(int perLevel) {
            this.storage = perLevel;
            return this;
        }

        Builder social(double hope, double discontent, int capacity) {
            this.hopeEffect = hope;
            this.discontentEffect = discontent;
            this.socialCapacity = capacity;
            return this;
        }

        Builder outdoor() {
            this.outdoor = true;
            return this;
        }

        Builder tech(String techId) {
            this.unlockTech = techId;
            return this;
        }

        Builder law(String lawId) {
            this.unlockLaw = lawId;
            return this;
        }

        Builder special() {
            this.buildable = false;
            this.maxLevel = 1;
            return this;
        }

        BuildingType done() {
            return new BuildingType(this);
        }
    }
}
