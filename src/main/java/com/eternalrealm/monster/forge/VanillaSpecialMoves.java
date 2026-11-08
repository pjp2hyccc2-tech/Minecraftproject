package com.eternalrealm.monster.forge;

import com.eternalrealm.combat.Telegraph;
import com.eternalrealm.combat.forge.TelegraphFx;
import com.eternalrealm.combat.forge.Telegraphs;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.entity.ERMonster;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.SpecialMove;
import com.eternalrealm.monster.SpecialMoves;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;
import java.util.List;

/**
 * Telegraphed special moves for monsters that wear a vanilla body (the animated bodies do it themselves,
 * {@link ERMonster}): the same rule ({@link SpecialMoves#moveCount}) and moves ({@link SpecialMoves#forVanillaBody}),
 * run by a goal attached whenever such a monster joins a level. The monster stops, the marker appears, and when it is
 * full the hit lands on whoever stands inside, with an arm swing. Server thread only.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VanillaSpecialMoves {
    /** The monster whose special move is landing right now, for {@link MonsterEvents#onLivingHurt}. */
    private static Entity hitting;
    private static double hitMultiplier = 1.0;

    private VanillaSpecialMoves() {
    }

    /** Damage multiplier of a special move landing right now from {@code attacker}, otherwise 1. */
    static double hitMultiplier(Entity attacker) {
        return attacker != null && attacker == hitting ? hitMultiplier : 1.0;
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || mob instanceof ERMonster
                || !MonsterTags.isMonster(mob)) {
            return;
        }
        MonsterTemplate template = MonsterTags.template(mob).orElse(null);
        if (template == null) {
            return;
        }
        int count = SpecialMoves.moveCount(template.role(), MonsterTags.level(mob));
        List<SpecialMove> moves = SpecialMoves.forVanillaBody(template.baseEntityType());
        moves = moves.subList(0, Math.min(count, moves.size()));
        if (!moves.isEmpty()) {
            mob.goalSelector.addGoal(1, new MoveGoal(mob, moves, SpecialMoves.vanillaStyle(template.baseEntityType()),
                    SpecialMoves.cooldownTicks(count)));
        }
    }

    /** Whether a mob carries the special move goal (tests). */
    public static boolean hasGoal(Mob mob) {
        return mob.goalSelector.getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getGoal() instanceof MoveGoal);
    }

    /** The marker of the special move a mob is winding up, or null (tests). */
    public static Telegraph windingUp(Mob mob) {
        for (net.minecraft.world.entity.ai.goal.WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof MoveGoal goal && goal.move != null && !goal.fired) {
                return goal.telegraph;
            }
        }
        return null;
    }

    private static boolean enabled() {
        try {
            return com.eternalrealm.config.ERServerConfig.SPECIAL_MOVES.get();
        } catch (IllegalStateException e) {
            return true; // config not loaded (tests)
        }
    }

    /** The current target of a goal-driven or brain-driven mob. */
    private static LivingEntity targetOf(Mob mob) {
        LivingEntity target = mob.getTarget();
        if (target == null && mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            target = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        }
        return target;
    }

    private static final class MoveGoal extends Goal {
        private static final int RECOVERY_TICKS = 8;
        private static final int DASH_TICKS = 4;
        private final Mob mob;
        private final List<SpecialMove> moves;
        private final Telegraph.Style style;
        private final int cooldown;
        private int readyAt;
        private int index;
        private SpecialMove move;
        private LivingEntity target;
        private Telegraph telegraph;
        private int marker = -1;
        private int ticks;
        private float yaw;
        private boolean fired;
        private Vec3 dashStep = Vec3.ZERO;
        private int dashTicks;

        MoveGoal(Mob mob, List<SpecialMove> moves, Telegraph.Style style, int cooldown) {
            this.mob = mob;
            this.moves = List.copyOf(moves);
            this.style = style;
            this.cooldown = cooldown;
            this.readyAt = mob.tickCount + SpecialMoves.OPENING_DELAY_TICKS + mob.getRandom().nextInt(40);
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (this.mob.tickCount < this.readyAt || !this.mob.isAlive() || !enabled()) {
                return false;
            }
            LivingEntity candidate = targetOf(this.mob);
            if (candidate == null || !candidate.isAlive() || !this.mob.getSensing().hasLineOfSight(candidate)) {
                return false;
            }
            double distance = Math.sqrt(Mth.square(candidate.getX() - this.mob.getX())
                    + Mth.square(candidate.getZ() - this.mob.getZ()));
            for (int i = 0; i < this.moves.size(); i++) {
                SpecialMove candidateMove = this.moves.get((this.index + i) % this.moves.size());
                if (candidateMove.inRange(distance)) {
                    this.index = (this.index + i + 1) % this.moves.size();
                    this.move = candidateMove;
                    this.target = candidate;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return this.move != null && this.mob.isAlive()
                    && (this.ticks < this.move.windupTicks() + RECOVERY_TICKS || this.dashTicks > 0);
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            if (!(this.mob.level() instanceof ServerLevel level) || this.move == null || this.target == null) {
                this.move = null; // nothing to do: canContinueToUse ends the goal
                return;
            }
            this.telegraph = this.move.telegraph(this.style, this.mob.getX(), groundY(this.mob), this.mob.getZ(),
                    this.target.getX(), groundY(this.target), this.target.getZ());
            this.marker = Telegraphs.show(level, this.telegraph);
            this.yaw = (float) Math.toDegrees(Math.atan2(-(this.target.getX() - this.mob.getX()),
                    this.target.getZ() - this.mob.getZ()));
            this.ticks = 0;
            this.fired = false;
            this.dashTicks = 0;
            this.mob.getNavigation().stop();
            TelegraphFx.warnSound(level, this.telegraph, 0.9F);
        }

        @Override
        public void tick() {
            if (!(this.mob.level() instanceof ServerLevel level) || this.telegraph == null) {
                return;
            }
            this.ticks++;
            if (!this.fired) {
                this.mob.getNavigation().stop();
                this.mob.setYRot(this.yaw);
                this.mob.yBodyRot = this.yaw;
                this.mob.yHeadRot = this.yaw;
                if (this.ticks % 3 == 0) {
                    level.sendParticles(TelegraphFx.secondary(this.style), this.mob.getX(),
                            this.mob.getY() + this.mob.getBbHeight() * 0.6, this.mob.getZ(), 2, this.mob.getBbWidth() * 0.4,
                            this.mob.getBbHeight() * 0.3, this.mob.getBbWidth() * 0.4, 0.02);
                }
                if (this.ticks >= this.move.windupTicks()) {
                    this.fired = true;
                    hit(level);
                }
            } else if (this.dashTicks > 0) {
                this.dashTicks--;
                this.mob.move(MoverType.SELF, this.dashStep);
            }
        }

        @Override
        public void stop() {
            if (!this.fired && this.marker >= 0 && this.mob.level() instanceof ServerLevel level) {
                Telegraphs.cancel(level, this.marker);
            }
            this.marker = -1;
            this.move = null;
            this.target = null;
            this.telegraph = null;
            this.dashTicks = 0;
            this.readyAt = this.mob.tickCount + this.cooldown + this.mob.getRandom().nextInt(40);
        }

        private void hit(ServerLevel level) {
            Telegraph marked = this.telegraph;
            Telegraphs.fire(level, this.marker);
            this.marker = -1;
            this.mob.swing(InteractionHand.MAIN_HAND);
            double reach = marked.boundingRadius() + 1.0;
            AABB area = new AABB(marked.x() - reach, marked.y() - 3.0, marked.z() - reach, marked.x() + reach,
                    marked.y() + 4.0, marked.z() + reach);
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area,
                    entity -> canHit(entity) && marked.contains(entity.getX(), entity.getY(), entity.getZ()));
            double base = MonsterTags.damage(this.mob);
            float damage = (float) (Math.max(1.0, base) * this.move.damageMultiplier());
            hitting = this.mob;
            hitMultiplier = this.move.damageMultiplier();
            try {
                for (LivingEntity victim : victims) {
                    victim.hurt(level.damageSources().mobAttack(this.mob), damage);
                }
            } finally {
                hitting = null;
                hitMultiplier = 1.0;
            }
            TelegraphFx.burst(level, marked);
            TelegraphFx.impactSound(level, marked, 1.0F);
            if (this.move.dashes()) {
                double toX;
                double toZ;
                if (this.move == SpecialMove.POUNCE) {
                    toX = marked.x();
                    toZ = marked.z();
                } else {
                    double radians = Math.toRadians(marked.yaw());
                    double length = Math.max(0.0, marked.a() - 1.0);
                    toX = marked.x() - Math.sin(radians) * length;
                    toZ = marked.z() + Math.cos(radians) * length;
                }
                this.dashStep = new Vec3((toX - this.mob.getX()) / DASH_TICKS, 0.0, (toZ - this.mob.getZ()) / DASH_TICKS);
                this.dashTicks = DASH_TICKS;
            }
        }

        private boolean canHit(LivingEntity entity) {
            if (entity == this.mob || !entity.isAlive() || entity instanceof ArmorStand || this.mob.isAlliedTo(entity)
                    || MonsterTags.isMonster(entity)) {
                return false;
            }
            if (entity instanceof Player player) {
                return !player.isSpectator() && !player.isCreative();
            }
            return entity == this.target || !(entity instanceof Enemy);
        }

        /** Feet height of the ground under an entity (fliers hover above it). */
        private static double groundY(Entity entity) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(entity.getX()),
                    Mth.floor(entity.getY() + 0.01), Mth.floor(entity.getZ()));
            for (int i = 0; i < 12; i++) {
                pos.move(0, -1, 0);
                if (!entity.level().getBlockState(pos).getCollisionShape(entity.level(), pos).isEmpty()) {
                    return pos.getY() + 1.0;
                }
            }
            return entity.getY();
        }
    }
}
