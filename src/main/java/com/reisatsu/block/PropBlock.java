package com.reisatsu.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * A facing prop of the restaurant, configured by a {@link Builder}: its box (pixels, for the block facing north),
 * extra state properties (booleans default to false, integers to their minimum), whether it collides, whether it
 * blocks sight ({@code ClipContext.Block.VISUAL}), and optional behaviour hooks for right-click, entities inside
 * its cell, client particles and scheduled ticks.
 */
public class PropBlock extends FacingBlock {
    /** Right-click behaviour (both sides; return the result to give vanilla). */
    public interface Use {
        InteractionResult use(PropBlock block, BlockState state, Level level, BlockPos pos, Player player);
    }

    /** Called every tick for every entity whose box overlaps the block cell (doors, decals, chairs). */
    public interface Inside {
        void inside(PropBlock block, BlockState state, Level level, BlockPos pos, Entity entity);
    }

    /** Client-side display tick (particles). */
    public interface Animate {
        void animate(BlockState state, Level level, BlockPos pos, RandomSource random);
    }

    /** Scheduled tick (server). */
    public interface Tick {
        void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, RandomSource random);
    }

    private static final ThreadLocal<Builder> PENDING = new ThreadLocal<>();

    public final String id;
    private final boolean collide;
    private final boolean seeThrough;
    private final Use use;
    private final Inside inside;
    private final Animate animate;
    private final Tick tick;
    private final VoxelShape[] collisionShapes = new VoxelShape[4];
    private final boolean customCollision;

    public static Builder builder(String id, Properties properties) {
        return new Builder(id, properties);
    }

    protected PropBlock(Builder b) {
        super(b.properties, b.box[0], b.box[1], b.box[2], b.box[3], b.box[4], b.box[5]);
        this.id = b.id;
        this.collide = b.collide;
        this.seeThrough = b.seeThrough;
        this.use = b.use;
        this.inside = b.inside;
        this.animate = b.animate;
        this.tick = b.tick;
        this.customCollision = b.collisionBox != null;
        if (customCollision) {
            double[] c = b.collisionBox;
            for (Direction d : Direction.Plane.HORIZONTAL) collisionShapes[d.get2DDataValue()] = rotate(c[0], c[1], c[2], c[3], c[4], c[5], d);
        }
        BlockState s = defaultBlockState();
        for (Property<?> p : b.extra) s = defaultValue(s, p);
        registerDefaultState(s);
    }

    private static BlockState defaultValue(BlockState s, Property<?> p) {
        if (p instanceof BooleanProperty bp) return s.setValue(bp, false);
        if (p instanceof IntegerProperty ip) return s.setValue(ip, ip.getPossibleValues().stream().min(Integer::compare).orElse(0));
        return s;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        Builder b = PENDING.get();
        if (b != null && !b.extra.isEmpty()) builder.add(b.extra.toArray(new Property<?>[0]));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (!collide) return Shapes.empty();
        if (customCollision) return collisionShapes[state.getValue(FACING).get2DDataValue()];
        return super.getCollisionShape(state, level, pos, ctx);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // glass, mirrors and decals never hide anybody; partitions, counters, shelves do (their box)
        if (seeThrough) return Shapes.empty();
        return getShape(state, level, pos, ctx);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (use == null) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        return use.use(this, state, level, pos, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (inside != null) inside.inside(this, state, level, pos, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (animate != null) animate.animate(state, level, pos, random);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, RandomSource random) {
        if (tick != null) tick.tick(state, level, pos, random);
    }

    public static final class Builder {
        final String id;
        final Properties properties;
        double[] box = {0, 0, 0, 16, 16, 16};
        double[] collisionBox;
        final List<Property<?>> extra = new ArrayList<>();
        boolean collide = true;
        boolean seeThrough;
        Use use;
        Inside inside;
        Animate animate;
        Tick tick;

        Builder(String id, Properties properties) {
            this.id = id;
            this.properties = properties;
        }

        /** Outline box in pixels for the block facing north (front toward -z). */
        public Builder box(double x1, double y1, double z1, double x2, double y2, double z2) {
            box = new double[]{x1, y1, z1, x2, y2, z2};
            return this;
        }

        /** A collision box different from the outline (pixels, facing north). */
        public Builder collision(double x1, double y1, double z1, double x2, double y2, double z2) {
            collisionBox = new double[]{x1, y1, z1, x2, y2, z2};
            return this;
        }

        public Builder props(Property<?>... ps) {
            extra.addAll(List.of(ps));
            return this;
        }

        public Builder noCollision() {
            collide = false;
            return this;
        }

        /** Does not block sight (glass, decals, small things on tables). */
        public Builder seeThrough() {
            seeThrough = true;
            return this;
        }

        public Builder use(Use u) {
            use = u;
            return this;
        }

        public Builder inside(Inside i) {
            inside = i;
            return this;
        }

        public Builder animate(Animate a) {
            animate = a;
            return this;
        }

        public Builder tick(Tick t) {
            tick = t;
            return this;
        }

        public PropBlock build() {
            return build(PropBlock::new);
        }

        public <T extends PropBlock> T build(java.util.function.Function<Builder, T> ctor) {
            PENDING.set(this);
            try {
                return ctor.apply(this);
            } finally {
                PENDING.remove();
            }
        }
    }
}
