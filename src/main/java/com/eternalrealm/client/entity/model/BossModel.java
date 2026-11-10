package com.eternalrealm.client.entity.model;

import com.eternalrealm.entity.BossAnimation;
import com.eternalrealm.entity.ERBoss;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.AnimationState;
import org.joml.Vector3f;

/**
 * Model of a boss rig; the boss's look key (boss id or form look) selects parts and animation set. A defeated boss
 * only plays (and holds) its death animation.
 *
 * <p>Layers: the walk cycle (its speed matched to the drawn size so planted feet stay on the ground), the idle
 * (softened while an action plays), the telegraph roar (blended in, and blended out under the action that follows)
 * and the actions.</p>
 */
public class BossModel extends RigModel<ERBoss> {
    private static final Vector3f CACHE = new Vector3f();
    private final float walkSpeed;

    /** @param walkSpeed walk cycle speed at scale 1 ({@code RigData.walkSpeed}); divided by the drawn scale */
    public BossModel(ModelPart root, RigTables tables, float walkSpeed) {
        super(root, tables);
        this.walkSpeed = walkSpeed;
    }

    @Override
    public void setupAnim(ERBoss boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        int index = prepare(boss.lookKey());
        if (boss.isDeadOrDying() || boss.isDefeatPlaying()) {
            animate(boss.animationState(BossAnimation.DEATH), animation(index, BossAnimation.DEATH.ordinal()), ageInTicks);
            return;
        }
        float partialTick = ageInTicks - boss.tickCount;
        lookAt(netHeadYaw, headPitch, 0.6F);
        animateWalk(animation(index, BossAnimation.WALK.ordinal()), limbSwing, limbSwingAmount,
                this.walkSpeed / Math.max(0.1F, boss.modelScale()), 2.0F);
        animateWeighted(boss.idleAnimation, animation(index, BossAnimation.IDLE.ordinal()), ageInTicks,
                boss.idleWeight(partialTick));
        for (BossAnimation action : BossAnimation.values()) {
            if (action == BossAnimation.ROAR) {
                animateWeighted(boss.animationState(action), animation(index, action.ordinal()), ageInTicks,
                        boss.roarWeight(partialTick));
            } else if (action.isAction()) {
                animate(boss.animationState(action), animation(index, action.ordinal()), ageInTicks);
            }
        }
    }

    /** Like {@code animate}, with every channel scaled by {@code weight} (0 skips the animation). */
    private void animateWeighted(AnimationState state, AnimationDefinition definition, float ageInTicks, float weight) {
        state.updateTime(ageInTicks, 1.0F);
        if (weight > 0.001F) {
            state.ifStarted(started -> KeyframeAnimations.animate(this, definition, started.getAccumulatedTime(),
                    Math.min(1.0F, weight), CACHE));
        }
    }
}
