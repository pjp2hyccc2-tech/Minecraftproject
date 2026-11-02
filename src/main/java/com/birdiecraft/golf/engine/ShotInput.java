package com.birdiecraft.golf.engine;

/**
 * Everything the golfer decided for one swing.
 *
 * @param club       club used
 * @param mode       normal / power / approach (ignored for putts)
 * @param yawDeg     aim direction (Minecraft yaw)
 * @param power      gauge power, 0..1
 * @param deviation  impact deviation in zone units: 0 = centre, |1| = edge of the impact zone,
 *                   positive = early click (push/slice), negative = late click (pull/hook)
 * @param spinX      side spin input -2..2 (negative curves left, positive curves right)
 * @param spinY      top/back spin input -2..2 (negative = backspin, positive = topspin)
 * @param puttRange  index into {@link ShotCalculator#PUTT_RANGES_YARDS}
 */
public record ShotInput(Club club, ShotMode mode, double yawDeg, double power, double deviation,
                        int spinX, int spinY, int puttRange) {

    public ShotInput {
        power = clamp(power, 0.0, 1.0);
        deviation = clamp(deviation, -GaugeMath.MAX_DEVIATION, GaugeMath.MAX_DEVIATION);
        spinX = (int) clamp(spinX, -2, 2);
        spinY = (int) clamp(spinY, -2, 2);
        puttRange = (int) clamp(puttRange, 0, ShotCalculator.PUTT_RANGES_YARDS.length - 1);
        if (club.isPutter()) mode = ShotMode.NORMAL;
    }

    public boolean isPerfect() {
        return Math.abs(deviation) <= GaugeMath.PERFECT_WINDOW;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
