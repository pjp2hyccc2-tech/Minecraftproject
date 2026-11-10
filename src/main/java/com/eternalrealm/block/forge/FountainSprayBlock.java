package com.eternalrealm.block.forge;

import com.eternalrealm.combat.forge.ERParticles;
import com.eternalrealm.world.town.WaterJet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * A fountain nozzle: put it in the air block at the jet's mouth. {@code facing=up} shoots a column of water
 * ({@code power} 1..3: about 1.5 / 3 / 5 blocks) that breaks into falling drops; a horizontal facing is a spout arcing
 * out of a lion's mouth or a statue's jar (about 1.5 / 2.5 / 4 blocks out). See {@link WaterJet}. The client draws
 * the water and its spray and splashes.
 */
public class FountainSprayBlock extends InvisibleEmitterBlock<FountainSprayBlock.Entity> {
    public static final IntegerProperty POWER = IntegerProperty.create("power", 1, 3);
    public static final DirectionProperty FACING = DirectionProperty.create("facing",
            direction -> direction != Direction.DOWN);

    public FountainSprayBlock(Properties properties) {
        super(properties, () -> ERBlocks.FOUNTAIN_SPRAY_ENTITY.get(), FountainSprayBlock::clientTick);
        registerDefaultState(stateDefinition.any().setValue(POWER, 2).setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return defaultBlockState().setValue(FACING, face == Direction.DOWN ? Direction.UP : face);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    /** Where the nozzle's water comes out, in block coordinates. */
    public static double[] nozzle(Direction facing) {
        if (facing == Direction.UP) {
            return new double[]{0.5, 0.02, 0.5};
        }
        return new double[]{0.5 - facing.getStepX() * 0.45, 0.5, 0.5 - facing.getStepZ() * 0.45};
    }

    private static void clientTick(Level level, BlockPos pos, BlockState state, Entity entity) {
        RandomSource random = level.getRandom();
        if (--entity.scanCooldown <= 0) {
            entity.scanCooldown = 100 + random.nextInt(20);
            entity.landingDrop = landingDrop(level, pos, state);
            Player near = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 48.0, false);
            entity.playerNear = near != null;
        }
        if (!entity.playerNear) {
            return;
        }
        int power = state.getValue(POWER);
        Direction facing = state.getValue(FACING);
        double[] n = nozzle(facing);
        double x = pos.getX() + n[0];
        double y = pos.getY() + n[1];
        double z = pos.getZ() + n[2];
        if (facing == Direction.UP) {
            double apex = WaterJet.apex(power);
            int drops = 1 + power;
            for (int i = 0; i < drops; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double speed = (0.025 + random.nextDouble() * 0.035) * Math.sqrt(power);
                double h = apex * (0.82 + random.nextDouble() * 0.18);
                level.addParticle(ERParticles.SPRAY.get(), x + Math.cos(angle) * 0.06, y + h, z + Math.sin(angle) * 0.06,
                        Math.cos(angle) * speed, random.nextDouble() * 0.04, Math.sin(angle) * speed);
            }
            if (random.nextFloat() < 0.3F) {
                // mist at the base where the column leaves the water
                level.addParticle(ParticleTypes.SPLASH, x + (random.nextDouble() - 0.5) * 0.3, y + 0.05,
                        z + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.0, 0.0);
            }
        } else {
            double vf = WaterJet.spoutForward(power);
            double vu = WaterJet.spoutUp(power);
            for (int i = 0; i < 2; i++) {
                double jitter = 0.94 + random.nextDouble() * 0.12;
                double side = (random.nextDouble() - 0.5) * 0.01;
                double fx = facing.getStepX();
                double fz = facing.getStepZ();
                level.addParticle(ERParticles.SPRAY.get(), x, y, z, (fx * vf + -fz * side) * jitter, vu * jitter,
                        (fz * vf + fx * side) * jitter);
            }
        }
        if (random.nextInt(90) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.5F + power * 0.15F,
                    1.1F + random.nextFloat() * 0.3F, false);
        }
    }

    /** How far below the nozzle the water lands (fluid surface or solid top), searched up to 8 blocks down. */
    private static double landingDrop(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        int power = state.getValue(POWER);
        double[] n = nozzle(facing);
        double x = pos.getX() + n[0];
        double y = pos.getY() + n[1];
        double z = pos.getZ() + n[2];
        if (facing != Direction.UP) {
            x += facing.getStepX() * WaterJet.reach(power);
            z += facing.getStepZ() * WaterJet.reach(power);
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int top = (int) Math.floor(y) - (facing == Direction.UP ? 1 : 0);
        for (int by = top; by >= top - 8; by--) {
            cursor.set(Math.floor(x), by, Math.floor(z));
            FluidState fluid = level.getFluidState(cursor);
            if (!fluid.isEmpty()) {
                return Math.max(0.0, y - (by + fluid.getHeight(level, cursor)));
            }
            BlockState below = level.getBlockState(cursor);
            if (!below.getCollisionShape(level, cursor).isEmpty()) {
                return Math.max(0.0, y - (by + below.getCollisionShape(level, cursor).max(Direction.Axis.Y)));
            }
        }
        return 8.0;
    }

    /** Client data: where the water lands, whether anyone is near enough to see the spray. */
    public static class Entity extends BlockEntity {
        private double landingDrop = 1.0;
        private int scanCooldown;
        private boolean playerNear;

        public Entity(BlockPos pos, BlockState state) {
            super(ERBlocks.FOUNTAIN_SPRAY_ENTITY.get(), pos, state);
        }

        /** Blocks the water falls below the nozzle before it lands (client side; 1 until first scanned). */
        public double landingDrop() {
            return landingDrop;
        }

        @Override
        public AABB getRenderBoundingBox() {
            return new AABB(worldPosition).inflate(5.0, 6.0, 5.0);
        }
    }
}
