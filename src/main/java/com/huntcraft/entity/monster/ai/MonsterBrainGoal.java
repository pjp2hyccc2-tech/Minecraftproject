package com.huntcraft.entity.monster.ai;

import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Combat brain shared by all hunt monsters: first-sight roar, attack selection (weighted, by range/angle/
 * cooldown/state), pauses between attacks, approach and repositioning. Monster-specific behaviour
 * (flight, area changes, eating...) plugs in through {@link HuntMonster#brainTick} and {@link HuntMonster#combatMove}.
 */
public class MonsterBrainGoal extends Goal {
    private final HuntMonster m;
    private int gapTicks;

    public MonsterBrainGoal(HuntMonster monster) {
        this.m = monster;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = m.getTarget();
        return t != null && t.isAlive() && !m.isCarcass();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = m.getTarget();
        if (t == null || !t.isAlive() || m.isCarcass()) return false;
        double range = 64.0 * Math.max(1.0, m.getMonsterScale());
        return m.distanceToSqr(t) < range * range;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        gapTicks = 8;
    }

    @Override
    public void stop() {
        m.setRunning(false);
        m.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity t = m.getTarget();
        if (t == null || m.isLocked()) return;
        if (!m.isActing()) m.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (m.isActing()) return;

        // roar when first engaging (large monsters)
        if (m.profile().large && m.isHostile() && !m.hasRoared()) {
            m.setHasRoared(true);
            if (m.animIndex("roar") != 255) {
                m.turnTowards(t.position(), 360);
                m.playAction("roar", 44);
                m.doRoar(16.0F);
                gapTicks = 6;
                return;
            }
        }

        if (m.brainTick(t)) return;

        double dist = Math.sqrt(m.distanceToSqr(t)) - m.getBbWidth() * 0.5;
        float angle = m.angleTo(t.position());

        if (gapTicks > 0) {
            gapTicks--;
            m.combatMove(t, dist, angle, true);
            return;
        }

        List<MonsterAttack> usable = new ArrayList<>();
        int total = 0;
        for (MonsterAttack a : m.getAttacks()) {
            if (a.canUse(m, t, dist, angle)) {
                int w = a.getWeight(m, t);
                if (w > 0) {
                    usable.add(a);
                    total += w;
                }
            }
        }
        if (!usable.isEmpty() && total > 0) {
            int r = m.getRandom().nextInt(total);
            for (MonsterAttack a : usable) {
                r -= a.getWeight(m, t);
                if (r < 0) {
                    m.setRunning(false);
                    m.startAttack(a, t);
                    int gap = m.getAttackGap();
                    gapTicks = gap + m.getRandom().nextInt(Math.max(1, gap / 2));
                    return;
                }
            }
        }
        m.combatMove(t, dist, angle, false);
    }
}
