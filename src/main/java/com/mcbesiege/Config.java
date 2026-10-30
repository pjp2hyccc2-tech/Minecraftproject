package com.mcbesiege;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server/common settings (config/mcbesiege-common.toml). */
public final class Config {
    private Config() {}

    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue GRAVITY;
    private static final ForgeConfigSpec.IntValue SUBSTEPS;
    private static final ForgeConfigSpec.IntValue ITERATIONS;
    private static final ForgeConfigSpec.IntValue MAX_PARTS;
    private static final ForgeConfigSpec.BooleanValue BREAK_BLOCKS;
    private static final ForgeConfigSpec.BooleanValue EXPLOSIONS_BREAK;
    private static final ForgeConfigSpec.BooleanValue FIRE_GRIEFING;
    private static final ForgeConfigSpec.BooleanValue BLOCK_DROPS;
    private static final ForgeConfigSpec.DoubleValue BLOCK_TOUGHNESS;
    private static final ForgeConfigSpec.DoubleValue IMPACT_DAMAGE;
    private static final ForgeConfigSpec.DoubleValue JOINT_STRENGTH;
    private static final ForgeConfigSpec.DoubleValue WATER_DENSITY;
    private static final ForgeConfigSpec.IntValue MAX_DEBRIS;
    private static final ForgeConfigSpec.BooleanValue OWNER_IMMUNE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("physics");
        GRAVITY = b.comment("Gravity for machines in blocks/s^2 (Minecraft entities use ~32, real life 9.8)").defineInRange("gravity", 20.0, 1.0, 60.0);
        SUBSTEPS = b.comment("Physics substeps per tick (higher = stiffer & more stable, slower)").defineInRange("substeps", 8, 2, 20);
        ITERATIONS = b.comment("Solver iterations per substep").defineInRange("iterations", 4, 1, 20);
        JOINT_STRENGTH = b.comment("Multiplier for joint break forces and servo strength").defineInRange("jointStrength", 1.0, 0.1, 100.0);
        WATER_DENSITY = b.comment("Water density (wood 0.3 floats, iron 1.0 sinks)").defineInRange("waterDensity", 0.8, 0.0, 5.0);
        b.pop();
        b.push("machines");
        MAX_PARTS = b.comment("Maximum number of parts in one machine").defineInRange("maxParts", 600, 8, 4000);
        IMPACT_DAMAGE = b.comment("Impact damage multiplier for machine parts").defineInRange("impactDamage", 0.05, 0.0, 10.0);
        MAX_DEBRIS = b.comment("Maximum loose debris pieces kept per machine").defineInRange("maxDebris", 64, 0, 1000);
        OWNER_IMMUNE = b.comment("Your own machine does not hurt you").define("ownerImmune", true);
        b.pop();
        b.push("destruction");
        BREAK_BLOCKS = b.comment("Fast machines, cannonballs and drills break world blocks").define("machinesBreakBlocks", true);
        EXPLOSIONS_BREAK = b.comment("Bombs / barrels / rockets break world blocks").define("explosionsBreakBlocks", true);
        FIRE_GRIEFING = b.comment("Flamethrowers set world blocks on fire").define("fireGriefing", true);
        BLOCK_DROPS = b.comment("Blocks destroyed by machines drop items").define("blockDrops", false);
        BLOCK_TOUGHNESS = b.comment("Multiplier for the energy needed to smash a block").defineInRange("blockToughness", 1.0, 0.05, 100.0);
        b.pop();
        SPEC = b.build();
    }

    private static boolean loaded() { return SPEC.isLoaded(); }

    public static double gravity() { return loaded() ? GRAVITY.get() : 20.0; }

    public static int substeps() { return loaded() ? SUBSTEPS.get() : 8; }

    public static int iterations() { return loaded() ? ITERATIONS.get() : 4; }

    public static int maxParts() { return loaded() ? MAX_PARTS.get() : 600; }

    public static boolean machinesBreakBlocks() { return !loaded() || BREAK_BLOCKS.get(); }

    public static boolean explosionsBreakBlocks() { return !loaded() || EXPLOSIONS_BREAK.get(); }

    public static boolean fireGriefing() { return !loaded() || FIRE_GRIEFING.get(); }

    public static boolean blockDrops() { return loaded() && BLOCK_DROPS.get(); }

    public static double blockToughness() { return loaded() ? BLOCK_TOUGHNESS.get() : 1.0; }

    public static double impactDamage() { return loaded() ? IMPACT_DAMAGE.get() : 0.05; }

    public static double jointStrength() { return loaded() ? JOINT_STRENGTH.get() : 1.0; }

    public static double waterDensity() { return loaded() ? WATER_DENSITY.get() : 0.8; }

    public static int maxDebris() { return loaded() ? MAX_DEBRIS.get() : 64; }

    public static boolean ownerImmune() { return !loaded() || OWNER_IMMUNE.get(); }
}
