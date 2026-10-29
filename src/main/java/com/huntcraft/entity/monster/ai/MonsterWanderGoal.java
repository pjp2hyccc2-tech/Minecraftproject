package com.huntcraft.entity.monster.ai;

import com.huntcraft.core.SafeZones;
import com.huntcraft.entity.monster.HuntMonster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Idle roaming around the monster's home position (area). Avoids safe zones. */
public class MonsterWanderGoal extends Goal {
    private final HuntMonster m;
    private final int radius;
    private Vec3 dest;
    private int timeout;
    private int cooldown;

    public MonsterWanderGoal(HuntMonster monster, int radius) {
        this.m = monster;
        this.radius = radius;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (m.getTarget() != null || m.isLocked() || m.isActing() || m.isCarcass()) return false;
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        return m.getRandom().nextInt(m.profile().large ? 60 : 40) == 0 && pickDestination();
    }

    private boolean pickDestination() {
        BlockPos home = m.getHomePos() != null ? m.getHomePos() : m.blockPosition();
        for (int i = 0; i < 8; i++) {
            int x = home.getX() + m.getRandom().nextInt(radius * 2 + 1) - radius;
            int z = home.getZ() + m.getRandom().nextInt(radius * 2 + 1) - radius;
            int y = m.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Vec3 v = new Vec3(x + 0.5, y, z + 0.5);
            if (Math.abs(y - m.getY()) > 12) continue;
            if (SafeZones.isInSafeZone(m.level(), v)) continue;
            dest = v;
            timeout = 300;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return dest != null && timeout > 0 && m.getTarget() == null && !m.isLocked() && !m.isActing()
                && m.position().distanceToSqr(dest) > 4.0;
    }

    @Override
    public void tick() {
        timeout--;
        if (dest == null) return;
        if (m.isFlying()) {
            m.flyTowards(dest.add(0, 8, 0), 0.35);
        } else if (m.profile().large || m.getBbWidth() > 1.6F) {
            m.getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, m.getWalkSpeedModifier() * 0.7);
        } else if (m.getNavigation().isDone()) {
            m.getNavigation().moveTo(dest.x, dest.y, dest.z, m.getWalkSpeedModifier());
        }
    }

    @Override
    public void stop() {
        dest = null;
        cooldown = 40;
        m.getNavigation().stop();
    }
}
