package com.huntcraft.monster.large.a;

import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Small helpers shared by Frostgarm and Voltrix. */
final class LargeAUtil {
    private LargeAUtil() {
    }

    /** Snaps a position onto the ground (searches a few blocks up and down); returns the input when nothing is found. */
    static Vec3 ground(Level level, double x, double y, double z) {
        BlockPos.MutableBlockPos p = BlockPos.containing(x, y + 3, z).mutable();
        for (int k = 0; k < 10; k++) {
            BlockPos below = p.below();
            boolean solidBelow = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
            boolean freeHere = level.getBlockState(p).getCollisionShape(level, p).isEmpty();
            if (solidBelow && freeHere) return new Vec3(x, p.getY(), z);
            p.move(0, -1, 0);
        }
        return new Vec3(x, y, z);
    }

    /** Burst of block-break particles (shattering ice etc.). */
    static void blockBurst(HuntMonster m, Vec3 pos, BlockState state, int count, double spread) {
        if (m.level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.x, pos.y, pos.z, count, spread, spread * 0.6, spread, 0.15);
        }
    }

    /** Harmless lightning bolt (visual + sound only). */
    static void visualBolt(HuntMonster m, Vec3 pos) {
        if (m.level() instanceof ServerLevel sl) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
            if (bolt != null) {
                bolt.moveTo(pos.x, pos.y, pos.z);
                bolt.setVisualOnly(true);
                sl.addFreshEntity(bolt);
            }
        }
    }

    /** Weight rule for {@link Weighted}. */
    @FunctionalInterface
    interface WeightRule {
        int weight(HuntMonster m, LivingEntity target, int base);
    }

    /**
     * Wraps an attack and changes its selection weight from the target's position (e.g. use the paw on the
     * side the hunter stands on, prefer the tail whip against hunters behind). Everything else is delegated.
     */
    static final class Weighted extends MonsterAttack {
        private final MonsterAttack inner;
        private final WeightRule rule;

        Weighted(MonsterAttack inner, WeightRule rule) {
            super(inner.name, inner.duration);
            this.inner = inner;
            this.rule = rule;
            this.anim = inner.anim;
            this.cooldown = inner.cooldown;
            this.minRange = inner.minRange;
            this.maxRange = inner.maxRange;
            this.maxAngle = inner.maxAngle;
            this.weight = inner.weight;
            this.enragedOnly = inner.enragedOnly;
            this.groundOnly = inner.groundOnly;
            this.airOnly = inner.airOnly;
            this.allowedWhenTired = inner.allowedWhenTired;
        }

        @Override
        public int getWeight(HuntMonster monster, LivingEntity target) {
            return Math.max(0, rule.weight(monster, target, weight));
        }

        @Override
        public void start(HuntMonster monster, @Nullable LivingEntity target) {
            inner.start(monster, target);
        }

        @Override
        public void tick(HuntMonster monster, @Nullable LivingEntity target, int t) {
            inner.tick(monster, target, t);
        }

        @Override
        public void end(HuntMonster monster) {
            inner.end(monster);
        }
    }

    /** Full weight when the target is on the given side (-1 left / +1 right) of the facing, a fifth otherwise. */
    static WeightRule side(int side) {
        return (m, t, base) -> {
            float a = m.angleTo(t.position());
            boolean ok = side < 0 ? a < 15 : a > -15;
            return ok ? base : Math.max(1, base / 5);
        };
    }

    /** Boosted weight when the target is behind / beside the monster. */
    static WeightRule rear(float minAngle, int boost) {
        return (m, t, base) -> Math.abs(m.angleTo(t.position())) >= minAngle ? base * boost : Math.max(1, base / 3);
    }
}
