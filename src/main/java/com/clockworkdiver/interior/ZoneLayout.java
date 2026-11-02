package com.clockworkdiver.interior;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Where things are inside a freshly built clock chamber (absolute positions). */
public final class ZoneLayout {
    public Vec3 spawn = Vec3.ZERO;
    public float spawnYaw = 180.0f;
    /** Rust clusters or tooth sockets. */
    public final List<BlockPos> targets = new ArrayList<>();
    public final List<BlockPos> vents = new ArrayList<>();
    /** Mechanisms that start running once the part is repaired. */
    public final List<BlockPos> mechanisms = new ArrayList<>();
    public final List<Vec3> enemySpawns = new ArrayList<>();
    public BlockPos center = BlockPos.ZERO;
    public BlockPos crank;
    /** Escape wheel, mainspring or balance wheel driven by the repair. */
    public BlockPos mainMechanism;
    public int screwHeight;
    public int screwMaxHeight;
    public AABB bounds = new AABB(BlockPos.ZERO);
}
