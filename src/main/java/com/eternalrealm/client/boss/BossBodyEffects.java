package com.eternalrealm.client.boss;

import com.eternalrealm.client.CameraShake;
import com.eternalrealm.entity.BossAnimation;
import com.eternalrealm.entity.ERBoss;
import com.eternalrealm.entity.RigData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What the body of a giant does to the world around it, timed on its animations (client thread only): every foot
 * that lands in the walk cycle kicks up dust from the block under it with a heavy step sound and, for the big ones,
 * a light camera shake for players close by; slams, charges and transformations shake the camera on their impact
 * frame, the stronger the bigger the boss.
 */
public final class BossBodyEffects {
    /** Bosses closer than this to the player are followed. */
    private static final double RANGE = 64.0;
    private static final BossAnimation[] HEAVY = {BossAnimation.MELEE_SLAM, BossAnimation.CHARGE,
            BossAnimation.PHASE_CHANGE, BossAnimation.SUMMON};
    private static final Map<ERBoss, Float> WALK_TIME = new WeakHashMap<>();

    private BossBodyEffects() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || minecraft.isPaused()) {
            return;
        }
        for (ERBoss boss : level.getEntitiesOfClass(ERBoss.class, player.getBoundingBox().inflate(RANGE))) {
            if (!boss.isAlive() || boss.isDefeatPlaying()) {
                WALK_TIME.remove(boss);
                continue;
            }
            footsteps(level, player, boss);
            impacts(player, boss);
        }
    }

    /** Clears the per-boss state (leaving a world). */
    public static void clear() {
        WALK_TIME.clear();
    }

    private static void footsteps(ClientLevel level, LocalPlayer player, ERBoss boss) {
        String rig = boss.kind().rig().id();
        String look = boss.lookKey();
        float[] steps = RigData.footsteps(rig, look);
        float length = RigData.length(rig, look, BossAnimation.WALK.ordinal());
        float scale = boss.modelScale();
        float speed = RigData.walkSpeed(rig) / Math.max(0.1F, scale);
        if (steps.length < 3 || length <= 0.0F || speed <= 0.0F) {
            return;
        }
        // the same clock as the model's walk cycle (HierarchicalModel.animateWalk)
        float now = (boss.walkAnimation.position() * 50.0F * speed / 1000.0F) % length;
        Float before = WALK_TIME.put(boss, now);
        float weight = Math.min(1.0F, boss.walkAnimation.speed() * 2.0F);
        if (before == null || weight < 0.3F || now == before) {
            return;
        }
        for (int i = 0; i + 2 < steps.length; i += 3) {
            float at = steps[i];
            boolean crossed = before <= now ? before < at && at <= now : at > before || at <= now;
            if (crossed) {
                step(level, player, boss, steps[i + 1] * scale / 16.0F, steps[i + 2] * scale / 16.0F, weight);
            }
        }
    }

    /** A foot lands at (left, back) blocks from the boss's centre. */
    private static void step(ClientLevel level, LocalPlayer player, ERBoss boss, float left, float back, float weight) {
        double yaw = Math.toRadians(boss.yBodyRot);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double x = boss.getX() + forwardZ * left - forwardX * back;
        double z = boss.getZ() - forwardX * left - forwardZ * back;
        double y = boss.getY();
        float size = Math.max(1.0F, boss.getBbWidth() / 2.5F);
        BlockState floor = level.getBlockState(BlockPos.containing(x, y - 0.5, z));
        if (floor.getRenderShape() != RenderShape.INVISIBLE) {
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, floor);
            for (int i = 0; i < (int) (6 * size * weight); i++) {
                double angle = boss.getRandom().nextDouble() * Math.PI * 2.0;
                double r = boss.getRandom().nextDouble() * 0.5 * size;
                level.addParticle(dust, x + Math.cos(angle) * r, y + 0.1, z + Math.sin(angle) * r,
                        Math.cos(angle) * 0.15, 0.12, Math.sin(angle) * 0.15);
            }
            level.playLocalSound(x, y, z, floor.getSoundType().getStepSound(), SoundSource.HOSTILE,
                    0.5F + 0.2F * size, 0.6F, false);
        }
        for (int i = 0; i < (int) (2 * size * weight); i++) {
            level.addParticle(ParticleTypes.POOF, x + (boss.getRandom().nextDouble() - 0.5) * size, y + 0.1,
                    z + (boss.getRandom().nextDouble() - 0.5) * size, 0.0, 0.02, 0.0);
        }
        level.playLocalSound(x, y, z, thud(boss), SoundSource.HOSTILE, 0.35F + 0.15F * size * weight,
                Mth.clamp(1.15F - 0.08F * boss.getBbWidth(), 0.45F, 1.0F), false);
        // only giants make the ground shake under the player's feet
        float giant = (boss.getBbWidth() - 2.8F) / 3.0F;
        if (giant > 0.0F) {
            double reach = 8.0 + 3.0 * boss.getBbWidth();
            double distance = Math.sqrt(player.distanceToSqr(x, y, z));
            if (distance < reach) {
                CameraShake.add(Math.min(0.6F, 0.35F * giant * weight) * (float) (1.0 - distance / reach), 5);
            }
        }
    }

    private static SoundEvent thud(ERBoss boss) {
        return switch (boss.kind().rig()) {
            case KING -> SoundEvents.IRON_GOLEM_STEP;
            case QUEEN -> SoundEvents.ARMOR_EQUIP_IRON;
            case TREANT -> SoundEvents.WOOD_STEP;
            case COLOSSUS -> SoundEvents.RAVAGER_STEP;
            case DRAGON -> SoundEvents.RAVAGER_STEP;
        };
    }

    /** Slams, charges and transformations shake the camera on their impact frame. */
    private static void impacts(LocalPlayer player, ERBoss boss) {
        for (BossAnimation action : HEAVY) {
            int ticks = boss.actionTicks(action);
            if (ticks < 0 || ticks != boss.impactTicks(action)) {
                continue;
            }
            float size = boss.getBbWidth() / 3.0F;
            double reach = 10.0 + 4.0 * boss.getBbWidth();
            double distance = Math.sqrt(player.distanceToSqr(boss));
            if (distance < reach) {
                float base = action == BossAnimation.PHASE_CHANGE ? 0.9F : 0.5F;
                CameraShake.add(Math.min(1.6F, base * size) * (float) (1.0 - distance / reach), 8);
            }
        }
    }
}
