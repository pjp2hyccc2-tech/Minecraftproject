package com.birdiecraft.golf.engine;

/**
 * Shared rules of the 3-click swing gauge. Gauge coordinate: 0 = centre of the impact zone,
 * 1 = full power. The marker rises from 0 to the chosen power, then comes back down through
 * the impact zone around 0.
 */
public final class GaugeMath {
    /** |deviation| at or below this counts as a perfect impact. */
    public static final double PERFECT_WINDOW = 0.35;
    /** Clamp for deviation values (beyond the zone = big mishit). */
    public static final double MAX_DEVIATION = 3.0;
    /** Gauge position under which the swing fires automatically as a miss. */
    public static final double MISS_POSITION = -0.26;
    private static final double BASE_ZONE_HALF = 0.06;

    private GaugeMath() {
    }

    /** Half width of the impact zone in gauge units. */
    public static double zoneHalfWidth(Club club, ShotMode mode, Lie lie, PlayStyle style) {
        double w = BASE_ZONE_HALF * style.control * lie.zoneFactor * club.family.zoneFactor;
        if (!club.isPutter()) w *= mode.zoneFactor;
        return Math.max(0.018, Math.min(0.12, w));
    }

    /** Rising marker speed, gauge units per second. */
    public static double riseSpeed(Club club, ShotMode mode) {
        if (club.isPutter()) return 0.70;
        return 0.95 * mode.gaugeSpeedFactor;
    }

    /** Returning marker speed, gauge units per second. */
    public static double returnSpeed(Club club, ShotMode mode) {
        if (club.isPutter()) return 0.62;
        return 0.80 * mode.gaugeSpeedFactor;
    }

    public static double deviationAt(double gaugePos, double zoneHalf) {
        double d = gaugePos / zoneHalf;
        return Math.max(-MAX_DEVIATION, Math.min(MAX_DEVIATION, d));
    }

    /** Gauge power needed to send the ball the given distance (blocks), ignoring wind and slope. */
    public static double powerForDistance(Club club, ShotMode mode, Lie lie, PlayStyle style, int puttRange, double distBlocks) {
        if (club.isPutter()) {
            double range = GolfUnits.toBlocks(ShotCalculator.PUTT_RANGES_YARDS[Math.max(0, Math.min(puttRange, ShotCalculator.PUTT_RANGES_YARDS.length - 1))]);
            return distBlocks / range;
        }
        double max = ShotCalculator.maxCarryBlocks(club, mode, style) * lie.midPower(club);
        return max <= 0 ? 0 : distBlocks / max;
    }
}
