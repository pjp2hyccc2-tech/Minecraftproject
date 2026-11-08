package com.eternalrealm.client.entity.model;

import com.eternalrealm.entity.BossAnimation;
import com.eternalrealm.entity.ERBoss;
import net.minecraft.client.model.geom.ModelPart;

/** Model of a boss rig; the boss's look key (boss id, final boss phase) selects parts and animation set. */
public class BossModel extends RigModel<ERBoss> {
    private final float walkSpeed;

    public BossModel(ModelPart root, RigTables tables, float walkSpeed) {
        super(root, tables);
        this.walkSpeed = walkSpeed;
    }

    @Override
    public void setupAnim(ERBoss boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        int index = prepare(boss.lookKey());
        if (boss.isDeadOrDying()) {
            animate(boss.animationState(BossAnimation.DEATH), animation(index, BossAnimation.DEATH.ordinal()), ageInTicks);
            return;
        }
        lookAt(netHeadYaw, headPitch, 0.6F);
        animateWalk(animation(index, BossAnimation.WALK.ordinal()), limbSwing, limbSwingAmount,
                this.walkSpeed / scaleOf(index), 2.0F);
        animate(boss.idleAnimation, animation(index, BossAnimation.IDLE.ordinal()), ageInTicks);
        for (BossAnimation action : BossAnimation.values()) {
            if (action.isAction()) {
                animate(boss.animationState(action), animation(index, action.ordinal()), ageInTicks);
            }
        }
    }
}
