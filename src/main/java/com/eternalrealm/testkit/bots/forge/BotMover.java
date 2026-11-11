package com.eternalrealm.testkit.bots.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * How a test bot walks: it asks the vanilla path finder for a way (through a stand-in walker of a player's size that
 * is never added to the world), then steers along it with a player's own inputs: turning, walking or sprinting,
 * jumping up steps and out of water, opening doors on the way. Far goals are approached in legs of at most
 * {@value #LEG} blocks. A bot stuck for a while takes the way a player would (a quick trip: it is set down at its
 * goal) rather than running into a wall forever.
 */
final class BotMover {
    private static final int LEG = 28;
    private static final int REPLAN_TICKS = 40;
    private static final int STUCK_TICKS = 120;

    private final TestBot bot;
    private Zombie walker;
    private Vec3 goal;
    private double within;
    private Path path;
    private int planned = -REPLAN_TICKS;
    private Vec3 lastProgress;
    private int sinceProgress;
    private boolean sprint;

    BotMover(TestBot bot) {
        this.bot = bot;
    }

    /** Walk to {@code target}, done when within {@code within} blocks (horizontally). */
    void goTo(Vec3 target, double within, boolean sprint) {
        if (goal == null || goal.distanceToSqr(target) > 1.0) {
            path = null;
            planned = -REPLAN_TICKS;
            sinceProgress = 0;
            lastProgress = bot.position();
        }
        this.goal = target;
        this.within = within;
        this.sprint = sprint;
    }

    void stop() {
        goal = null;
        path = null;
    }

    boolean moving() {
        return goal != null;
    }

    boolean arrived() {
        return goal == null || horizontal(bot.position(), goal) <= within;
    }

    Vec3 goal() {
        return goal;
    }

    /** One tick: plan when needed, then set this tick's movement inputs. */
    void tick() {
        bot.zza = 0.0F;
        bot.xxa = 0.0F;
        bot.setJumping(false);
        if (goal == null) {
            bot.setSprinting(false);
            return;
        }
        if (arrived()) {
            stop();
            bot.setSprinting(false);
            return;
        }
        if (bot.tickCount - planned >= REPLAN_TICKS || path == null) {
            plan();
        }
        Vec3 next = nextStep();
        steer(next);
        progress();
    }

    private void plan() {
        planned = bot.tickCount;
        ServerLevel level = bot.serverLevel();
        if (walker == null || walker.level() != level) {
            walker = new Zombie(EntityType.ZOMBIE, level);
            if (walker.getAttribute(Attributes.FOLLOW_RANGE) != null) {
                walker.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(LEG + 8);
            }
            if (walker.getNavigation() instanceof GroundPathNavigation ground) {
                ground.setCanOpenDoors(true);
                ground.setCanPassDoors(true);
                ground.setCanFloat(true);
            }
        }
        walker.moveTo(bot.getX(), bot.getY(), bot.getZ(), bot.getYRot(), 0.0F);
        walker.setOnGround(true);
        Vec3 leg = goal;
        double distance = horizontal(bot.position(), goal);
        if (distance > LEG) {
            Vec3 dir = goal.subtract(bot.position()).multiply(1.0, 0.0, 1.0).normalize();
            Vec3 point = bot.position().add(dir.scale(LEG));
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(point.x),
                    Mth.floor(point.z));
            leg = new Vec3(point.x, ground, point.z);
        }
        path = walker.getNavigation().createPath(BlockPos.containing(leg), 1);
    }

    private Vec3 nextStep() {
        if (path == null || path.isDone()) {
            return goal;
        }
        Node node = path.getNextNode();
        Vec3 at = new Vec3(node.x + 0.5, node.y, node.z + 0.5);
        // nodes it stands on or has passed are done (the first one is where it started)
        while (horizontal(bot.position(), at) < 0.6 && Math.abs(bot.getY() - node.y) < 1.2) {
            path.advance();
            if (path.isDone()) {
                return goal;
            }
            node = path.getNextNode();
            at = new Vec3(node.x + 0.5, node.y, node.z + 0.5);
        }
        openDoor(node.asBlockPos());
        return at;
    }

    private void steer(Vec3 next) {
        double dx = next.x - bot.getX();
        double dz = next.z - bot.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float turned = Mth.approachDegrees(bot.getYRot(), yaw, 40.0F);
        bot.setYRot(turned);
        bot.setYHeadRot(turned);
        bot.setYBodyRot(turned);
        bot.zza = 1.0F;
        boolean far = horizontal(bot.position(), goal) > 6.0;
        bot.setSprinting(sprint && far && bot.getFoodData().getFoodLevel() > 6);
        boolean climb = next.y > bot.getY() + 0.6;
        if (climb || bot.horizontalCollision || bot.isInWater() || bot.isInLava()) {
            bot.setJumping(true);
        }
    }

    private void progress() {
        if (lastProgress == null || bot.position().distanceToSqr(lastProgress) > 1.0) {
            lastProgress = bot.position();
            sinceProgress = 0;
            return;
        }
        if (++sinceProgress >= STUCK_TICKS) {
            // a player would not walk into the same wall for six seconds: the bot takes a quick trip instead
            sinceProgress = 0;
            Vec3 at = standable(bot.serverLevel(), goal);
            bot.teleportTo(bot.serverLevel(), at.x, at.y, at.z, bot.getYRot(), 0.0F);
            bot.fallDistance = 0.0F;
            path = null;
        }
    }

    /**
     * Where a bot can be set down at {@code at}: there if a player fits (indoors, under a roof), otherwise on top of
     * the ground there. The place is loaded first.
     */
    static Vec3 standable(ServerLevel level, Vec3 at) {
        BlockPos feet = BlockPos.containing(at);
        level.getChunk(feet.getX() >> 4, feet.getZ() >> 4);
        if (fits(level, feet)) {
            return at;
        }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ());
        return new Vec3(at.x, ground, at.z);
    }

    private static boolean fits(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    private void openDoor(BlockPos pos) {
        ServerLevel level = bot.serverLevel();
        for (BlockPos at : new BlockPos[]{pos, pos.above()}) {
            BlockState state = level.getBlockState(at);
            if (DoorBlock.isWoodenDoor(state) && state.getBlock() instanceof DoorBlock door && !door.isOpen(state)) {
                door.setOpen(bot, level, state, at, true);
            }
        }
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
