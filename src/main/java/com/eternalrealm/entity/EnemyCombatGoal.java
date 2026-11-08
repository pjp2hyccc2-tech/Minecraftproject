package com.eternalrealm.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Fights the current target: melee variants chase and swing when in reach; ranged variants keep 6-14 blocks away,
 * back off when the target closes in, and cast when they can see it. Flyers aim for a spot above the target. Strong
 * monsters ({@link ERMonster#specialMoveCount()} > 0) open with a pause, then use a telegraphed special move whenever
 * one is ready and the target is at a fitting distance ({@link ERMonster#trySpecialMove}).
 */
final class EnemyCombatGoal extends Goal {
    private static final double RANGED_MIN = 6.0;
    private static final double RANGED_MAX = 14.0;
    private static final double RANGED_FIRE = 18.0;

    private final ERMonster mob;
    private int cooldown;
    private int repath;

    EnemyCombatGoal(ERMonster mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && this.mob.canAttack(target);
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
        this.cooldown = 10;
        this.repath = 0;
        this.mob.delaySpecialMoves(com.eternalrealm.monster.SpecialMoves.OPENING_DELAY_TICKS
                + this.mob.getRandom().nextInt(40));
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        if (this.mob.isActing()) {
            this.mob.getNavigation().stop();
            return;
        }
        boolean sees = this.mob.getSensing().hasLineOfSight(target);
        if (sees && this.mob.trySpecialMove(target)) {
            this.mob.getNavigation().stop();
            return;
        }
        if (this.mob.isRangedAttacker()) {
            tickRanged(target, sees);
        } else {
            tickMelee(target, sees);
        }
    }

    private void tickMelee(LivingEntity target, boolean sees) {
        if (sees && this.mob.inMeleeReach(target, 1.0)) {
            this.mob.getNavigation().stop();
            if (this.cooldown <= 0 && this.mob.startMeleeAttack(target)) {
                this.cooldown = 18 + this.mob.getRandom().nextInt(14);
            }
        } else if (--this.repath <= 0) {
            this.repath = 6 + this.mob.getRandom().nextInt(6);
            moveTowards(target, 1.0);
        }
    }

    private void tickRanged(LivingEntity target, boolean sees) {
        double distance = Math.sqrt(this.mob.distanceToSqr(target));
        if (!sees || distance > RANGED_MAX) {
            if (--this.repath <= 0) {
                this.repath = 10;
                moveTowards(target, 1.0);
            }
        } else if (distance < RANGED_MIN) {
            if (--this.repath <= 0) {
                this.repath = 10;
                Vec3 away = this.mob.position().subtract(target.position());
                away = new Vec3(away.x, 0.0, away.z);
                if (away.lengthSqr() < 1.0E-4) {
                    away = new Vec3(1.0, 0.0, 0.0);
                }
                Vec3 to = this.mob.position().add(away.normalize().scale(6.0));
                this.mob.getNavigation().moveTo(to.x, to.y + (this.mob.variant().floats() ? 1.5 : 0.0), to.z, 1.15);
            }
        } else {
            this.mob.getNavigation().stop();
        }
        if (sees && this.cooldown <= 0 && distance < RANGED_FIRE && this.mob.startRangedAttack(target)) {
            this.cooldown = 40 + this.mob.getRandom().nextInt(30);
        }
    }

    private void moveTowards(LivingEntity target, double speed) {
        if (this.mob.variant().floats()) {
            this.mob.getNavigation().moveTo(target.getX(), target.getY() + 1.5, target.getZ(), speed);
        } else {
            this.mob.getNavigation().moveTo(target, speed);
        }
    }
}
