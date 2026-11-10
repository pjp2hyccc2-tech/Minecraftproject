package com.eternalrealm.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Default boss AI: chase the target and slam it when in reach. Inactive while the AI is frozen.
 *
 * <p>A giant whose footprint (several blocks wide) finds no path walks straight at the target instead, and when it
 * makes no headway (a pillar in the way) it steps round the obstacle: a detour to one side, then the other side the
 * next time, before it heads for the target again.</p>
 */
final class BossMeleeGoal extends Goal {
    /** Ticks between progress checks, and the least distance (squared) to cover in them. */
    private static final int PROGRESS_TICKS = 10;
    private static final double MIN_PROGRESS_SQR = 0.3 * 0.3;
    private static final int DETOUR_TICKS = 30;

    private final ERBoss boss;
    private int cooldown;
    private int repath;
    /** No path was found: walk straight at the target. */
    private boolean steer;
    private Vec3 lastPosition;
    private int progressTimer;
    private Vec3 detour;
    private int detourTicks;
    private boolean detourLeft;

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
        this.repath = 0;
        this.lastPosition = null;
        this.detourTicks = 0;
    }

    @Override
    public void stop() {
        this.boss.getNavigation().stop();
        this.steer = false;
        this.detourTicks = 0;
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
            this.boss.getNavigation().stop();
            this.steer = false;
            this.detourTicks = 0;
            this.lastPosition = null;
            if (this.cooldown <= 0 && this.boss.startSlam(target)) {
                this.cooldown = this.boss.durationTicks(BossAnimation.MELEE_SLAM) + 20 + this.boss.getRandom().nextInt(20);
            }
            return;
        }
        if (this.detourTicks <= 0 && --this.repath <= 0) {
            this.repath = 8 + this.boss.getRandom().nextInt(6);
            this.steer = !this.boss.getNavigation().moveTo(target, 1.0);
            if (this.steer) {
                this.boss.getNavigation().stop();
            }
        }
        if (this.detourTicks > 0) {
            this.detourTicks--;
            this.boss.getMoveControl().setWantedPosition(this.detour.x, this.detour.y, this.detour.z, 1.0);
        } else if (this.steer) {
            this.boss.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
        }
        checkProgress(target);
    }

    /** No headway for a while (stuck on a pillar or a corner): step round it. */
    private void checkProgress(LivingEntity target) {
        if (++this.progressTimer < PROGRESS_TICKS) {
            return;
        }
        this.progressTimer = 0;
        Vec3 here = this.boss.position();
        Vec3 before = this.lastPosition;
        this.lastPosition = here;
        if (before == null || here.distanceToSqr(before) >= MIN_PROGRESS_SQR) {
            return;
        }
        Vec3 toTarget = new Vec3(target.getX() - here.x, 0.0, target.getZ() - here.z);
        if (toTarget.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 ahead = toTarget.normalize();
        Vec3 side = new Vec3(-ahead.z, 0.0, ahead.x).scale(this.detourLeft ? 1.0 : -1.0);
        double step = this.boss.getBbWidth() + 3.0;
        this.detour = here.add(side.scale(step)).add(ahead.scale(step * 0.5));
        this.detourTicks = DETOUR_TICKS;
        this.detourLeft = !this.detourLeft;
        this.boss.getNavigation().stop();
    }
}
