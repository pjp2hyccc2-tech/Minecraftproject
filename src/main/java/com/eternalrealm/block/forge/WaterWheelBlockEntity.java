package com.eternalrealm.block.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A mill's water wheel of radius {@code Radius} and width {@code Width} blocks ({@code {Radius:3.0f,Width:2.0f}}),
 * turning about the horizontal axis through the block centre along {@code facing}, in front of that side (from 0.15
 * to 0.15 + Width blocks out). The client draws rims, spokes and paddles, and splashes where they meet the water.
 */
public class WaterWheelBlockEntity extends SyncedBlockEntity {
    /** Speed of the paddles at the rim, blocks per second. */
    public static final double RIM_SPEED = 2.0;

    private float radius = 3.0F;
    private float width = 2.0F;

    /** Client: depth of the water surface below the axle (blocks), or NaN if no water under the wheel. */
    private double waterDepth = Double.NaN;
    private int scanCooldown;

    public WaterWheelBlockEntity(BlockPos pos, BlockState state) {
        super(ERBlocks.WATER_WHEEL_ENTITY.get(), pos, state);
    }

    public float radius() {
        return radius;
    }

    public float width() {
        return width;
    }

    /** Rotation in radians at a game time (ticks); the same for every player. */
    public double angle(double gameTicks) {
        return gameTicks / 20.0 * RIM_SPEED / radius + (worldPosition.asLong() & 255) * 0.1;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        radius = clampedFloat(tag, "Radius", radius, 1.5F, 8.0F);
        width = clampedFloat(tag, "Width", width, 0.5F, 4.0F);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("Radius", radius);
        tag.putFloat("Width", width);
    }

    /** Client tick: water dripping off the rising paddles, splashes where they dip in, the sound of the race. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, WaterWheelBlockEntity wheel) {
        if (--wheel.scanCooldown <= 0) {
            wheel.scanCooldown = 100;
            wheel.waterDepth = wheel.scanWater(level, state);
        }
        if (Double.isNaN(wheel.waterDepth)) {
            return;
        }
        RandomSource random = level.getRandom();
        Direction facing = state.getValue(FacadeMechanismBlock.FACING);
        double fx = facing.getStepX();
        double fz = facing.getStepZ();
        double sx = -fz;                                // across the wheel (horizontal, in its plane)
        double sz = fx;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double r = wheel.radius;
        double depth = Math.min(wheel.waterDepth, r * 0.95);
        double half = Math.sqrt(Math.max(0.0, r * r - depth * depth));     // where the rim cuts the water surface
        for (int i = 0; i < 2; i++) {
            double t = 0.5 + 0.15 + random.nextDouble() * wheel.width;
            double across = (random.nextDouble() * 2.0 - 1.0) * half;
            level.addParticle(ParticleTypes.SPLASH, cx + fx * t + sx * across, cy - depth + 0.05,
                    cz + fz * t + sz * across, 0.0, 0.05, 0.0);
        }
        if (random.nextFloat() < 0.6F) {
            // the rising side: drops falling off the paddles above the water
            double a = random.nextDouble() * Math.PI * 0.8 - Math.PI * 0.4;
            double across = Math.cos(a) * r;
            double up = Math.sin(a) * r;
            if (up > -depth) {
                double t = 0.5 + 0.15 + random.nextDouble() * wheel.width;
                level.addParticle(ParticleTypes.FALLING_WATER, cx + fx * t + sx * across, cy + up,
                        cz + fz * t + sz * across, 0.0, 0.0, 0.0);
            }
        }
        if (random.nextInt(70) == 0) {
            level.playLocalSound(cx + fx * (0.65 + wheel.width / 2.0), cy - depth, cz + fz * (0.65 + wheel.width / 2.0),
                    SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.7F, 0.8F + random.nextFloat() * 0.3F, false);
        }
    }

    private double scanWater(Level level, BlockState state) {
        Direction facing = state.getValue(FacadeMechanismBlock.FACING);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int out = Math.max(1, Math.round(0.65F + width / 2.0F));
        for (int down = 0; down <= Math.ceil(radius) + 1; down++) {
            cursor.set(worldPosition.getX() + facing.getStepX() * out, worldPosition.getY() - down,
                    worldPosition.getZ() + facing.getStepZ() * out);
            var fluid = level.getFluidState(cursor);
            if (!fluid.isEmpty()) {
                double surface = cursor.getY() + fluid.getHeight(level, cursor);
                return Math.max(0.0, worldPosition.getY() + 0.5 - surface);
            }
        }
        return Double.NaN;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(radius + width + 1.0);
    }
}
