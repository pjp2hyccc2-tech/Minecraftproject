package com.eternalrealm.client.entity.model;

import com.eternalrealm.entity.ERMonster;
import com.eternalrealm.entity.EnemyAnim;
import net.minecraft.client.model.geom.ModelPart;

/** Model of an enemy rig; the variant selects parts, rest pose and animation set. */
public class EnemyModel<T extends ERMonster> extends RigModel<T> {
    private static final int IDLE = EnemyAnim.IDLE.ordinal();
    private static final int WALK = EnemyAnim.WALK.ordinal();

    private final float walkSpeed;

    /** @param walkSpeed walk cycle speed for {@code animateWalk}, divided by the variant scale */
    public EnemyModel(ModelPart root, RigTables tables, float walkSpeed) {
        super(root, tables);
        this.walkSpeed = walkSpeed;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        int index = prepare(entity.getVariant());
        if (entity.deathAnimation.isStarted()) {
            animate(entity.deathAnimation, animation(index, EnemyAnim.DEATH.ordinal()), ageInTicks);
            return;
        }
        lookAt(netHeadYaw, headPitch, 1.0F);
        animateWalk(animation(index, WALK), limbSwing, limbSwingAmount, this.walkSpeed / scaleOf(index), 2.0F);
        animate(entity.idleAnimation, animation(index, IDLE), ageInTicks);
        animate(entity.spawnAnimation, animation(index, EnemyAnim.SPAWN.ordinal()), ageInTicks);
        animate(entity.attack1Animation, animation(index, EnemyAnim.ATTACK1.ordinal()), ageInTicks);
        animate(entity.attack2Animation, animation(index, EnemyAnim.ATTACK2.ordinal()), ageInTicks);
        animate(entity.castAnimation, animation(index, EnemyAnim.CAST.ordinal()), ageInTicks);
        animate(entity.hurtAnimation, animation(index, EnemyAnim.HURT.ordinal()), ageInTicks);
    }
}
