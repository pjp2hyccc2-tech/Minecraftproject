package com.eternalrealm.combat.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.HashSet;
import java.util.Set;

/**
 * Arrow shot by bow attacks and ranger skills: vanilla arrow physics and look, the mod's damage. It cannot be picked
 * up, disappears when it hits a block and is never saved.
 */
public final class SkillArrow extends AbstractArrow {
    private ProjectilePayload payload;
    private int pierceLeft;
    private final Set<Integer> alreadyHit = new HashSet<>();
    /** Enemy a locked skill's arrow curves into (D-LOCKON), or -1. */
    private int homingTarget = -1;

    public SkillArrow(EntityType<? extends SkillArrow> type, Level level) {
        super(type, level);
    }

    private SkillArrow(Level level, ServerPlayer shooter) {
        super(EREntities.SKILL_ARROW.get(), shooter, level);
    }

    static SkillArrow create(ServerLevel level, ServerPlayer owner, ProjectilePayload payload) {
        SkillArrow arrow = new SkillArrow(level, owner);
        arrow.payload = payload;
        arrow.pierceLeft = payload.spec().pierce();
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(0.0);
        arrow.setCritArrow(payload.roll().critical());
        return arrow;
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    /** Makes the arrow fly straight at {@code target} and curve into it (a skill locked on to an enemy). */
    void homeOn(net.minecraft.world.entity.LivingEntity target) {
        homingTarget = target.getId();
        setNoGravity(true);
    }

    @Override
    public void tick() {
        if (!level().isClientSide && !inGround) {
            homingTarget = Homing.steer(this, homingTarget);
        }
        super.tick();
        if (!level().isClientSide && !isRemoved()
                && (payload == null || tickCount > payload.spec().lifetimeTicks())) {
            discard();
            return;
        }
        // the bow's weapon skill (a skill shot without a job skill's theme) leaves a bright streak while it flies
        if (!level().isClientSide && !isRemoved() && !inGround && payload != null
                && payload.kind() == com.eternalrealm.combat.ActionKind.SKILL && payload.theme() == null
                && level() instanceof ServerLevel serverLevel && getOwner() instanceof ServerPlayer owner) {
            WeaponSkillFx.arrowTrail(serverLevel, this, owner);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity) || alreadyHit.contains(entity.getId())) {
            return false;
        }
        if (level().isClientSide) {
            return !(entity instanceof net.minecraft.world.entity.player.Player); // the server decides; never stop on a player
        }
        return entity instanceof LivingEntity living && getOwner() instanceof ServerPlayer owner
                && CombatHits.isValidTarget(owner, living);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide || isRemoved()) {
            return;
        }
        if (payload == null || !(getOwner() instanceof ServerPlayer owner)
                || !(result.getEntity() instanceof LivingEntity target)) {
            discard();
            return;
        }
        alreadyHit.add(target.getId());
        CombatHits.projectileHit(owner, target, payload);
        if (pierceLeft-- <= 0) {
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide) {
            discard();
        }
    }
}
