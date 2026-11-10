package com.eternalrealm.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Default boss AI: chase the target and slam it when in reach. Inactive while the AI is frozen.
 *
 * <p>Bosses fight in open arenas and their footprint is several blocks wide, which vanilla path finding handles
 * badly, so a boss steers instead: it heads straight for its target and feels ahead with its own hitbox; when a
 * pillar or a wall is in the way it swings out to the nearest free heading on one side and keeps to that side until
 * it is past (no dithering in front of an obstacle). A boss that still makes no headway switches sides.</p>
 */
final class BossMeleeGoal extends Goal {
    /** Headings tried around the direct one, in degrees, nearest first. */
    private static final double[] SWING = {0.0, 20.0, 40.0, 60.0, 80.0, 100.0, 125.0, 150.0};
    /** Ticks a chosen side is kept after an obstacle was seen. */
    private static final int COMMIT_TICKS = 16;
    /** Ticks between progress checks, and the least distance (squared) to cover in them. */
    private static final int PROGRESS_TICKS = 20;
    private static final double MIN_PROGRESS_SQR = 0.6 * 0.6;

    private final ERBoss boss;
    private int cooldown;
    /** +1: swing out to the left of the target line, -1: to the right. */
    private int side = 1;
    private int committed;
    private Vec3 lastPosition;
    private int progressTimer;

    BossMeleeGoal(ERBoss boss) {
        this.boss = boss;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.boss.getTarget();
        return !this.boss.isAiFrozen() && target != null && target.isAlive() && this.boss.canAttack(target);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.cooldown = 20;
        this.committed = 0;
        this.lastPosition = null;
        this.boss.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.boss.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.boss.getTarget();
        if (target == null) {
            return;
        }
        this.boss.getLookControl().setLookAt(target, 20.0F, 20.0F);
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        if (this.boss.playingAnimation().isPresent()) {
            this.lastPosition = null;
            return;
        }
        if (this.boss.inMeleeReach(target, 1.0)) {
            this.lastPosition = null;
            if (this.cooldown <= 0 && this.boss.startSlam(target)) {
                this.cooldown = this.boss.durationTicks(BossAnimation.MELEE_SLAM) + 20 + this.boss.getRandom().nextInt(20);
            }
            return;
        }
        Vec3 heading = heading(target);
        if (heading != null) {
            double lead = 2.0 + this.boss.getBbWidth() * 0.5;
            this.boss.getMoveControl().setWantedPosition(this.boss.getX() + heading.x * lead, target.getY(),
                    this.boss.getZ() + heading.z * lead, 1.0);
        }
        checkProgress();
    }

    /** Horizontal unit heading towards the target that the body can walk, or null if every heading is blocked. */
    private Vec3 heading(LivingEntity target) {
        double dx = target.getX() - this.boss.getX();
        double dz = target.getZ() - this.boss.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-3) {
            return null;
        }
        double probe = Math.min(distance, 1.5 + this.boss.getBbWidth() * 0.5);
        if (this.committed > 0) {
            this.committed--;
        }
        for (double swing : SWING) {
            int[] sides = swing == 0.0 ? new int[]{1} : this.committed > 0 ? new int[]{this.side}
                    : new int[]{this.side, -this.side};
            for (int s : sides) {
                double angle = Math.toRadians(swing * s);
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                Vec3 heading = new Vec3((dx * cos - dz * sin) / distance, 0.0, (dx * sin + dz * cos) / distance);
                if (free(heading, probe)) {
                    if (swing > 0.0) {
                        this.side = s;
                        this.committed = COMMIT_TICKS;
                    }
                    return heading;
                }
            }
        }
        return null;
    }

    /** Whether the body fits along {@code heading} for {@code probe} blocks (steps it can climb do not count). */
    private boolean free(Vec3 heading, double probe) {
        AABB box = this.boss.getBoundingBox().deflate(0.05).move(0.0, this.boss.maxUpStep() + 0.05, 0.0);
        for (double f : new double[]{0.5, 1.0}) {
            if (!this.boss.level().noCollision(this.boss, box.move(heading.x * probe * f, 0.0, heading.z * probe * f))) {
                return false;
            }
        }
        return true;
    }

    /** No headway for a while (caught on a corner): try the other side. */
    private void checkProgress() {
        if (++this.progressTimer < PROGRESS_TICKS) {
            return;
        }
        this.progressTimer = 0;
        Vec3 here = this.boss.position();
        Vec3 before = this.lastPosition;
        this.lastPosition = here;
        if (before != null && here.distanceToSqr(before) < MIN_PROGRESS_SQR) {
            this.side = -this.side;
            this.committed = COMMIT_TICKS * 2;
        }
    }
}
