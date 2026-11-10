package com.eternalrealm.world.town;

/**
 * The shape of a waving flag, computed fresh every frame from a travelling-wave model (no simulation state besides
 * the wave phase): waves run from the pole to the fly end, growing as they go, with diagonal wave fronts and a second
 * harmonic for flutter. Each row keeps its cloth length (the fly end pulls in as the cloth curls) and the whole flag
 * droops towards the pole in a calm and streams out flat in a gale.
 *
 * <p>Flag space: {@code x} out from the pole along the wind, {@code y} up (0 = top of the hoist), {@code z} sideways.
 * The grid has {@code cols + 1} columns (0 = at the pole) and {@code rows + 1} rows (0 = top edge).
 */
public final class FlagCloth {
    /** Outline of the cloth. */
    public enum Shape {
        /** A plain rectangle. */
        RECTANGLE,
        /** A long streamer narrowing to a point at the fly end. */
        PENNANT
    }

    private FlagCloth() {
    }

    /** Wavelength of the main wave in blocks. */
    public static double wavelength(double width, double wind) {
        return (0.9 + 0.45 * width) * (1.15 - 0.25 * Math.min(wind, 1.0));
    }

    /** How fast the wave phase advances, radians per second. */
    public static double angularSpeed(double width, double wind) {
        return (2.4 + 3.8 * wind) * Math.sqrt(2.2 / wavelength(width, wind));
    }

    /** Height of the cloth at fraction {@code u} (0 at the pole .. 1 at the fly end) of its length. */
    public static double heightAt(Shape shape, double height, double u) {
        return shape == Shape.PENNANT ? height * (1.0 - 0.9 * u) : height;
    }

    /**
     * Writes the grid positions as xyz triples, row-major: index {@code ((row * (cols + 1)) + col) * 3}.
     *
     * @param phase wave phase in radians (accumulate {@link #angularSpeed} over time)
     * @param wind  wind strength (see {@link TownWind})
     * @param out   at least {@code (cols + 1) * (rows + 1) * 3} doubles
     */
    public static void compute(double width, double height, int cols, int rows, Shape shape, double phase,
                               double wind, double[] out) {
        if (cols < 1 || rows < 1) {
            throw new IllegalArgumentException("cols and rows must be positive");
        }
        if (out.length < (cols + 1) * (rows + 1) * 3) {
            throw new IllegalArgumentException("output array too small");
        }
        double lambda = wavelength(width, wind);
        double k = Math.PI * 2.0 / lambda;
        double amplitude = width * (0.085 + 0.03 * Math.min(wind, 1.2));
        double seg = width / cols;
        double calm = Math.max(0.0, 1.0 - Math.min(wind, 1.0));
        double droop = Math.pow(calm, 1.3) * 1.1;           // radians of tilt reached at the fly end
        for (int row = 0; row <= rows; row++) {
            double v = (double) row / rows;
            double x = 0.0;
            double y = 0.0;
            double zPrev = 0.0;
            for (int col = 0; col <= cols; col++) {
                double u = (double) col / cols;
                double rest = u * width;
                double amp = amplitude * Math.pow(u, 0.85) * (0.3 + 0.7 * u);
                double z = amp * (Math.sin(k * rest - phase + 0.55 * v)
                        + 0.3 * Math.sin(2.3 * k * rest - 1.7 * phase + 1.3 * v + 0.7));
                if (col > 0) {
                    double dz = z - zPrev;
                    double step = Math.sqrt(Math.max(seg * seg * 0.25, seg * seg - dz * dz));
                    double tilt = droop * Math.pow(u, 1.2);
                    x += step * Math.cos(tilt);
                    y -= step * Math.sin(tilt);
                }
                zPrev = z;
                double flutter = amp * 0.12 * u * Math.sin(1.3 * k * rest - 1.2 * phase + 2.0 * v);
                int i = ((row * (cols + 1)) + col) * 3;
                out[i] = x;
                out[i + 1] = y - v * heightAt(shape, height, u) + flutter;
                out[i + 2] = z;
            }
        }
    }
}
