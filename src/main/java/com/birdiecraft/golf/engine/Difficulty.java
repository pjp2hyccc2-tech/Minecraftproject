package com.birdiecraft.golf.engine;

/** CPU skill levels. Sigmas are standard deviations of the execution errors. */
public enum Difficulty {
    //        pow   aim  dev   perfect mishit puttPow puttAim think
    BEGINNER(0.115, 5.2, 0.95, 0.04, 0.26, 0.170, 4.0, 50),
    AMATEUR( 0.075, 3.4, 0.70, 0.08, 0.13, 0.110, 2.6, 40),
    PRO(     0.048, 2.2, 0.50, 0.20, 0.05, 0.070, 1.7, 30),
    LEGEND(  0.032, 1.4, 0.36, 0.35, 0.02, 0.050, 1.2, 25);

    public static final Difficulty[] VALUES = values();

    public final double powerSigma;
    public final double aimSigma;
    public final double devSigma;
    public final double perfectChance;
    public final double mishitChance;
    public final double puttPowerSigma;
    public final double puttAimSigma;
    public final int thinkTicks;

    Difficulty(double powerSigma, double aimSigma, double devSigma, double perfectChance, double mishitChance,
               double puttPowerSigma, double puttAimSigma, int thinkTicks) {
        this.powerSigma = powerSigma;
        this.aimSigma = aimSigma;
        this.devSigma = devSigma;
        this.perfectChance = perfectChance;
        this.mishitChance = mishitChance;
        this.puttPowerSigma = puttPowerSigma;
        this.puttAimSigma = puttAimSigma;
        this.thinkTicks = thinkTicks;
    }

    public static Difficulty byId(int id) {
        if (id < 0 || id >= VALUES.length) return AMATEUR;
        return VALUES[id];
    }

    public Difficulty next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public String translationKey() {
        return "difficulty.birdiecraft." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
