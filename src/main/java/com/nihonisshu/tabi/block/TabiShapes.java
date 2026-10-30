package com.nihonisshu.tabi.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/** Helpers to build voxel shapes from 16-pixel boxes and rotate them by facing. */
public final class TabiShapes {
    private TabiShapes() {}

    /** boxes: flat array of {x1,y1,z1,x2,y2,z2,...} in pixels, defined for facing NORTH. */
    public static VoxelShape of(double... b) {
        VoxelShape s = net.minecraft.world.phys.shapes.Shapes.empty();
        for (int i = 0; i + 5 < b.length; i += 6) {
            s = net.minecraft.world.phys.shapes.Shapes.joinUnoptimized(s,
                    Block.box(b[i], b[i + 1], b[i + 2], b[i + 3], b[i + 4], b[i + 5]), BooleanOp.OR);
        }
        return s.optimize();
    }

    /** Rotate a NORTH-defined box list to each horizontal facing. */
    public static Map<Direction, VoxelShape> horizontal(double... north) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            double[] r = new double[north.length];
            for (int i = 0; i + 5 < north.length; i += 6) {
                double x1 = north[i], y1 = north[i + 1], z1 = north[i + 2];
                double x2 = north[i + 3], y2 = north[i + 4], z2 = north[i + 5];
                double[] a = rot(d, x1, z1), c = rot(d, x2, z2);
                r[i] = Math.min(a[0], c[0]);
                r[i + 1] = y1;
                r[i + 2] = Math.min(a[1], c[1]);
                r[i + 3] = Math.max(a[0], c[0]);
                r[i + 4] = y2;
                r[i + 5] = Math.max(a[1], c[1]);
            }
            map.put(d, of(r));
        }
        return map;
    }

    private static double[] rot(Direction d, double x, double z) {
        return switch (d) {
            case SOUTH -> new double[]{16 - x, 16 - z};
            case WEST -> new double[]{z, 16 - x};
            case EAST -> new double[]{16 - z, x};
            default -> new double[]{x, z};
        };
    }

    public static VoxelShape full() {
        return net.minecraft.world.phys.shapes.Shapes.block();
    }
}
