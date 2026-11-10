package com.eternalrealm.world.map;

import com.eternalrealm.world.field.FieldLayout;

/**
 * The overlay of the world map's field tab: over a box around one field it is clear inside the field, a dark veil
 * outside it, and a bright rim (the field's colour) along its border, so the field stands out and its edge is
 * unmistakable. Pure; painted off the render thread.
 */
public final class FieldMaskPainter {
    /** Blocks of the neighbouring fields shown around the field's bounding box. */
    public static final int MARGIN = 200;
    /** Colour of the veil over everything that is not the field (ARGB). */
    public static final int VEIL = 0xA8080810;

    private FieldMaskPainter() {
    }

    /** World box {@code {minX, minZ, maxX, maxZ}} the mask of {@code field} covers. */
    public static int[] box(int field) {
        int[] b = FieldLayout.regionBounds(field);
        return new int[]{b[0] - MARGIN, b[1] - MARGIN, b[2] + 16 + MARGIN, b[3] + 16 + MARGIN};
    }

    /**
     * ARGB pixels, {@code size} × {@code size}, row-major from the box's north-west corner; {@code rim} is the
     * colour of the border line (alpha ignored).
     */
    public static int[] paint(int field, int size, int rim) {
        if (size < 8 || size > 2048) {
            throw new IllegalArgumentException("size " + size);
        }
        int[] box = box(field);
        double stepX = (box[2] - box[0]) / (double) size;
        double stepZ = (box[3] - box[1]) / (double) size;
        boolean[] inside = new boolean[size * size];
        for (int y = 0; y < size; y++) {
            double z = box[1] + (y + 0.5) * stepZ;
            for (int x = 0; x < size; x++) {
                double wx = box[0] + (x + 0.5) * stepX;
                inside[y * size + x] = FieldLayout.inBounds(wx, z) && FieldLayout.areaAt(wx, z) == field;
            }
        }
        int rgb = rim & 0xFFFFFF;
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                if (!inside[i]) {
                    pixels[i] = VEIL;
                    continue;
                }
                int edge = edgeDistance(inside, size, x, y);
                pixels[i] = switch (edge) {
                    case 1 -> 0xFF000000 | rgb;
                    case 2 -> 0xC0000000 | rgb;
                    case 3 -> 0x50000000 | rgb;
                    default -> 0;
                };
            }
        }
        return pixels;
    }

    /** 1..3 = Chebyshev distance (pixels) to the nearest pixel outside the field, 0 = farther than that. */
    static int edgeDistance(boolean[] inside, int size, int x, int y) {
        for (int r = 1; r <= 3; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= size || ny >= size || !inside[ny * size + nx]) {
                        return r;
                    }
                }
            }
        }
        return 0;
    }
}
