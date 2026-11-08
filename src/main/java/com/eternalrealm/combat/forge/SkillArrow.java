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

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !isRemoved()
                && (payload == null || tickCount > payload.spec().lifetimeTicks())) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity) || alreadyHit.contains(entity.getId())) {
            return false;
        }
        if (level().isClientSide) {
            return true;
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
