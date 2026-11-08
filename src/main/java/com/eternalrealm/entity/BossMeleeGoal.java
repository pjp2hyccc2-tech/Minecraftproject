package com.eternalrealm.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Default boss AI: chase the target and slam it when in reach. Inactive while the AI is frozen. */
final class BossMeleeGoal extends Goal {
    private final ERBoss boss;
    private int cooldown;
    private int repath;

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
            return;
        }
        if (this.boss.inMeleeReach(target, 1.0)) {
            this.boss.getNavigation().stop();
            if (this.cooldown <= 0 && this.boss.startSlam(target)) {
                this.cooldown = this.boss.durationTicks(BossAnimation.MELEE_SLAM) + 20 + this.boss.getRandom().nextInt(20);
            }
        } else if (--this.repath <= 0) {
            this.repath = 8 + this.boss.getRandom().nextInt(6);
            this.boss.getNavigation().moveTo(target, 1.0);
        }
    }
}
