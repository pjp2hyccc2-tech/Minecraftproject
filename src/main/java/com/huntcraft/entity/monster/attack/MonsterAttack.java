package com.huntcraft.entity.monster.attack;

import com.huntcraft.entity.monster.HuntMonster;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * One monster move. Subclass directly for fully custom logic, or use {@link TimelineAttack} for
 * declarative timed events (recommended).
 */
public abstract class MonsterAttack {
    public final String name;
    /** Animation name (in the monster's hcmodel JSON) played while this attack runs. */
    public String anim;
    public int duration;
    public int cooldown = 60;
    public float minRange = 0, maxRange = 6;
    /** Target must be within this angle (degrees, 0..180) of the monster's facing. 180 = anywhere. */
    public float maxAngle = 50;
    public int weight = 10;
    public boolean enragedOnly = false;
    public boolean groundOnly = true;
    public boolean airOnly = false;
    /** Usable while tired? Breath attacks typically "fizzle" when tired. */
    public boolean allowedWhenTired = true;
    public long lastUsedTick = -100000;

    protected MonsterAttack(String name, int duration) {
        this.name = name;
        this.anim = name;
        this.duration = duration;
    }

    public boolean canUse(HuntMonster monster, LivingEntity target, double distance, float angleDeg) {
        if (monster.tickCount - lastUsedTick < cooldown) return false;
        if (enragedOnly && !monster.isEnraged()) return false;
        if (groundOnly && monster.isFlying()) return false;
        if (airOnly && !monster.isFlying()) return false;
        if (!allowedWhenTired && monster.isTired()) return false;
        float scale = monster.getMonsterScale();
        if (distance < minRange * scale || distance > maxRange * scale) return false;
        return Math.abs(angleDeg) <= maxAngle;
    }

    /** Selection weight (can depend on state). */
    public int getWeight(HuntMonster monster, LivingEntity target) {
        return weight;
    }

    public void start(HuntMonster monster, @Nullable LivingEntity target) {
    }

    /** @param t ticks since start, 0..duration-1 */
    public abstract void tick(HuntMonster monster, @Nullable LivingEntity target, int t);

    public void end(HuntMonster monster) {
    }
}
