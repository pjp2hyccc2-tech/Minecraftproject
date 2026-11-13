package com.eternalrealm.world.map;

import java.util.ArrayList;
import java.util.List;

/**
 * The minimap's texture as a window that scrolls over the world without being repainted (the owner, 2026-11-13: the
 * minimap lighter while walking). The texture of {@code size}×{@code size} texels wraps around: the world block (x, z)
 * always sits at texel ({@code x mod size}, {@code z mod size}), so when the window moves only the strips that come
 * into view have to be painted, and the texture is drawn with texture coordinates x / size, z / size (repeating).
 * Pure: rectangles are {@code int[]{x0, z0, x1, z1}} (end exclusive) in blocks, or {@code int[]{u, v, width, height}}
 * in texels.
 */
public final class ScrollingWindow {
    private ScrollingWindow() {
    }

    /**
     * The world strips that come into view when the window's corner moves from (oldX, oldZ) to (newX, newZ), or null
     * when the window moved too far and everything has to be painted.
     */
    public static List<int[]> entering(int oldX, int oldZ, int newX, int newZ, int size) {
        int dx = newX - oldX;
        int dz = newZ - oldZ;
        if (Math.abs(dx) >= size || Math.abs(dz) >= size) {
            return null;
        }
        List<int[]> strips = new ArrayList<>(2);
        if (dx > 0) {
            strips.add(new int[]{oldX + size, newZ, newX + size, newZ + size});
        } else if (dx < 0) {
            strips.add(new int[]{newX, newZ, oldX, newZ + size});
        }
        if (dz > 0) {
            strips.add(new int[]{newX, oldZ + size, newX + size, newZ + size});
        } else if (dz < 0) {
            strips.add(new int[]{newX, newZ, newX + size, oldZ});
        }
        return strips;
    }

    /** The part of a world rectangle inside the window with corner (x, z), or null if none. */
    public static int[] clip(int[] rect, int x, int z, int size) {
        int x0 = Math.max(rect[0], x);
        int z0 = Math.max(rect[1], z);
        int x1 = Math.min(rect[2], x + size);
        int z1 = Math.min(rect[3], z + size);
        return x0 < x1 && z0 < z1 ? new int[]{x0, z0, x1, z1} : null;
    }

    /** The texel rectangles of a world rectangle no wider and no deeper than the texture (up to four, wrapped). */
    public static List<int[]> texels(int[] rect, int size) {
        int width = rect[2] - rect[0];
        int depth = rect[3] - rect[1];
        if (width <= 0 || depth <= 0 || width > size || depth > size) {
            throw new IllegalArgumentException("rectangle " + width + " x " + depth + " for a texture of " + size);
        }
        int[][] us = split(Math.floorMod(rect[0], size), width, size);
        int[][] vs = split(Math.floorMod(rect[1], size), depth, size);
        List<int[]> out = new ArrayList<>(us.length * vs.length);
        for (int[] v : vs) {
            for (int[] u : us) {
                out.add(new int[]{u[0], v[0], u[1], v[1]});
            }
        }
        return out;
    }

    /** {start, length} pieces of a run of {@code length} texels starting at {@code start}, wrapped at {@code size}. */
    private static int[][] split(int start, int length, int size) {
        if (start + length <= size) {
            return new int[][]{{start, length}};
        }
        return new int[][]{{start, size - start}, {0, start + length - size}};
    }
}
