package com.huntcraft.monster.small;

import com.huntcraft.entity.monster.SmallMonster;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared behaviour of the HuntCraft small monsters:
 * <ul>
 *   <li>"death" pose while dying (small monsters do not leave a carcass) and a slightly longer death time</li>
 *   <li>idle actions (grazing, looking around...) played as action animations while calm</li>
 *   <li>short flinches, run flag driven by the actual ground speed (walk / run loops always match the motion)</li>
 *   <li>an extra synced "special pose" (shell, burrow...) rendered as a state animation</li>
 * </ul>
 */
public abstract class SmallHuntMonster extends SmallMonster {
    private static final EntityDataAccessor<Integer> DATA_SPECIAL = SynchedEntityData.defineId(SmallHuntMonster.class, EntityDataSerializers.INT);

    /** One idle action: animation name, duration (ticks) and selection weight. */
    public record IdleAction(String anim, int ticks, int weight) {
    }

    protected int idleCooldown;
    protected int idleActionTicks;
    protected int lastHurtTick;

    protected SmallHuntMonster(EntityType<? extends SmallHuntMonster> type, Level level) {
        super(type, level);
        this.idleCooldown = 60 + this.getRandom().nextInt(160);
        this.lastHurtTick = -100000;
    }

    // ------------------------------------------------------------------ synced special pose
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SPECIAL, 0);
    }

    public int getSpecialPose() {
        return this.entityData.get(DATA_SPECIAL);
    }

    public void setSpecialPose(int pose) {
        if (getSpecialPose() != pose) this.entityData.set(DATA_SPECIAL, pose);
    }

    /** Extra looping state pose ("shell", "burrow"...) or null. Evaluated on the client. */
    @Nullable
    protected String specialStatePose() {
        return null;
    }

    @Override
    @Nullable
    protected String computeStateAnim() {
        if (isDeadOrDying()) return "death";
        String s = super.computeStateAnim();
        return s != null ? s : specialStatePose();
    }

    // ------------------------------------------------------------------ tuning hooks
    /** Idle actions played while calm (no target, not fleeing). */
    protected IdleAction[] idleActions() {
        return new IdleAction[0];
    }

    /** Duration of the flinch action (small monsters recover quickly). */
    protected int flinchTicks() {
        return 14;
    }

    /** Ground speed (blocks/tick) above which the run loop is used instead of walk. */
    protected double runThreshold() {
        return 0.15;
    }

    /** Ticks before the death animation ends and the body disappears. */
    protected int deathDuration() {
        return 30;
    }

    protected boolean wantsIdleActions() {
        return getTarget() == null && getLastHurtByMob() == null && !isRunning() && !isFlying();
    }

    /** Called when an idle action starts (play a sound, spawn particles...). */
    protected void onIdleAction(String anim) {
    }

    /** Per-species server logic (called every tick while alive). */
    protected void smallTick() {
    }

    // ------------------------------------------------------------------ overrides
    @Override
    protected void onFlinch() {
        if (animIndex("flinch") != 255) playAction("flinch", flinchTicks());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && idleActionTicks > 0 && getCurrentAttack() == null && !isInvulnerableTo(source)) {
            idleActionTicks = 0;
            stopAction();
        }
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide) {
            lastHurtTick = this.tickCount;
            idleCooldown = Math.max(idleCooldown, 120);
        }
        return r;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            stopAction();
            idleActionTicks = 0;
            if (isFlying()) setFlying(false);
            this.getNavigation().stop();
        }
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= deathDuration() && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    @Override
    protected final void customServerTick() {
        if (isDeadOrDying()) return;
        if (idleActionTicks > 0) idleActionTicks--;
        if (!isFlying()) {
            double dx = getX() - this.xo, dz = getZ() - this.zo;
            setRunning(Math.sqrt(dx * dx + dz * dz) > runThreshold() && !isLocked());
        }
        smallTick();
        tickIdleActions();
    }

    private void tickIdleActions() {
        if (idleCooldown > 0) {
            idleCooldown--;
            return;
        }
        if (!canAct() || !wantsIdleActions()) {
            idleCooldown = 20;
            return;
        }
        IdleAction[] acts = idleActions();
        if (acts.length == 0) {
            idleCooldown = 400;
            return;
        }
        int total = 0;
        for (IdleAction a : acts) total += Math.max(0, a.weight());
        if (total > 0) {
            int r = getRandom().nextInt(total);
            for (IdleAction a : acts) {
                r -= Math.max(0, a.weight());
                if (r < 0) {
                    this.getNavigation().stop();
                    playAction(a.anim(), a.ticks());
                    idleActionTicks = a.ticks();
                    onIdleAction(a.anim());
                    break;
                }
            }
        }
        idleCooldown = 120 + getRandom().nextInt(260);
    }

    // ------------------------------------------------------------------ helpers
    /** Y of the first solid block surface below (or at) the given position, scanning down `depth` blocks. */
    public double groundBelow(Vec3 pos, int depth) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(pos.x), Mth.floor(pos.y), Mth.floor(pos.z));
        for (int i = 0; i < depth; i++) {
            if (!level().getBlockState(p).getCollisionShape(level(), p).isEmpty()) return p.getY() + 1.0;
            p.move(0, -1, 0);
        }
        return pos.y - depth;
    }

    /** Ticks since this monster was last damaged. */
    public int ticksSinceHurt() {
        return this.tickCount - lastHurtTick;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HCSmallPose", getSpecialPose());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSpecialPose(tag.getInt("HCSmallPose"));
    }

    /**
     * Retaliation target goal for passive monsters: alerts the herd, and gives up once the monster has not
     * been hurt for `calmTicks`.
     */
    public static class RetaliateGoal extends HurtByTargetGoal {
        private final SmallHuntMonster monster;
        private final int calmTicks;

        public RetaliateGoal(SmallHuntMonster monster, int calmTicks) {
            super(monster);
            this.monster = monster;
            this.calmTicks = calmTicks;
            this.setAlertOthers();
        }

        @Override
        public boolean canContinueToUse() {
            return monster.ticksSinceHurt() < calmTicks && super.canContinueToUse();
        }
    }

    /** Distance-keeping helper: walks to a point on a circle around the target (used while waiting). */
    protected void circleTarget(LivingEntity target, double radius, double speed, int dir) {
        Vec3 rel = position().subtract(target.position());
        rel = new Vec3(rel.x, 0, rel.z);
        if (rel.lengthSqr() < 1.0E-4) rel = new Vec3(1, 0, 0);
        double ang = Math.atan2(rel.z, rel.x) + dir * 0.6;
        Vec3 goal = target.position().add(Math.cos(ang) * radius, 0, Math.sin(ang) * radius);
        this.getNavigation().moveTo(goal.x, goal.y, goal.z, speed);
    }
}
