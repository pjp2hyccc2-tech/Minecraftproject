package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.CombatEvent;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.DamageRoll;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.ProjectileSpec;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.job.SkillTheme;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.DoubleSupplier;

/** Launches the projectiles of a projectile hit frame (each projectile rolls its own critical hit). */
public final class CombatProjectiles {
    private CombatProjectiles() {
    }

    public static void launch(ServerPlayer player, CombatStats stats, CombatEvent.Hit hit, DamageType type,
                              List<SkillEffect.Afflict> afflictions, DoubleSupplier random) {
        launch(player, stats, hit, type, afflictions, random, 1.0, List.of(), List.of(), null);
    }

    /**
     * Launches a skill frame's projectiles, carrying the skill's on-impact extras.
     *
     * @param effectScale strength of the afflictions (skill level)
     * @param theme       look of the impact ({@code null}: plain weapon bolt)
     */
    public static void launch(ServerPlayer player, CombatStats stats, CombatEvent.Hit hit, DamageType type,
                              List<SkillEffect.Afflict> afflictions, DoubleSupplier random, double effectScale,
                              List<SkillEffect.Siphon> siphons, List<SkillEffect.Execute> executes, SkillTheme theme) {
        ProjectileSpec spec = hit.frame().projectile();
        if (spec == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 target = spec.fromAbove() ? lookTarget(player, ProjectileSpec.FROM_ABOVE_RANGE) : null;
        for (double yawOffset : spec.yawOffsets()) {
            DamageRoll roll = CombatHits.roll(player, stats, hit.weapon(), hit.frame(), hit.damageScale(), type,
                    random.getAsDouble());
            ProjectilePayload payload = new ProjectilePayload(spec, roll, type, hit.frame().knockback(),
                    hit.action().kind(), afflictions, effectScale, siphons, executes, theme);
            Projectile projectile = "arrow".equals(spec.kind())
                    ? SkillArrow.create(level, player, payload)
                    : MagicProjectile.create(level, player, payload);
            if (target != null) {
                double sideways = Math.toRadians(player.getYRot() + 90.0F);
                double offset = yawOffset / 10.0;
                projectile.setPos(target.x + Math.cos(sideways) * offset, target.y + ProjectileSpec.FROM_ABOVE_HEIGHT,
                        target.z + Math.sin(sideways) * offset);
                projectile.setDeltaMovement(0.0, -spec.speed(), 0.0);
            } else {
                projectile.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
                projectile.shootFromRotation(player, player.getXRot(), player.getYRot() + (float) yawOffset, 0.0F,
                        (float) (spec.speed() * stats.projectileSpeedFactor()), 0.0F);
            }
            level.addFreshEntity(projectile);
        }
    }

    /** Where the player looks, clipped by blocks and by {@code range}. */
    static Vec3 lookTarget(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        HitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }
}
