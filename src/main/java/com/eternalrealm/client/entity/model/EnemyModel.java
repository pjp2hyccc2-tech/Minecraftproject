package com.eternalrealm.client.entity.model;

import com.eternalrealm.entity.ERMonster;
import com.eternalrealm.entity.EnemyAnim;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Model of an enemy rig; the variant selects parts, rest pose and animation set. A humanoid rig holding an item
 * (a named weapon of the armory) hides its built-in weapon, and {@link #translateToHand} gives the grip of its right
 * hand to the held item layer.
 */
public class EnemyModel<T extends ERMonster> extends RigModel<T> {
    private static final int IDLE = EnemyAnim.IDLE.ordinal();
    private static final int WALK = EnemyAnim.WALK.ordinal();
    /** Built-in weapons of the humanoid rig's right hand. */
    private static final String[] HAND_WEAPONS = {"dagger", "blade", "greatblade", "club", "staff", "crossbow"};
    /** Parts from the root to the right forearm (the hand's grip is 5 px down the forearm). */
    private static final String[] HAND_CHAIN = {"base", "hips", "chest", "right_arm", "right_forearm"};

    private final float walkSpeed;
    private final ModelPart[] handWeapons;
    private final ModelPart[] handChain;

    /** @param walkSpeed walk cycle speed for {@code animateWalk}, divided by the variant scale */
    public EnemyModel(ModelPart root, RigTables tables, float walkSpeed) {
        super(root, tables);
        this.walkSpeed = walkSpeed;
        this.handWeapons = new ModelPart[HAND_WEAPONS.length];
        for (int i = 0; i < HAND_WEAPONS.length; i++) {
            this.handWeapons[i] = findOrNull(root, HAND_WEAPONS[i]);
        }
        ModelPart[] chain = new ModelPart[HAND_CHAIN.length];
        for (int i = 0; i < HAND_CHAIN.length; i++) {
            chain[i] = findOrNull(root, HAND_CHAIN[i]);
            if (chain[i] == null) {
                chain = null;
                break;
            }
        }
        this.handChain = chain;
    }

    /** Whether the rig has a right hand that can hold an item. */
    public boolean hasHand() {
        return this.handChain != null;
    }

    /** Moves {@code pose} to the grip of the right hand (after {@link #setupAnim}); false without a hand. */
    public boolean translateToHand(PoseStack pose) {
        if (this.handChain == null) {
            return false;
        }
        root().translateAndRotate(pose);
        for (ModelPart part : this.handChain) {
            part.translateAndRotate(pose);
        }
        pose.translate(0.0F, 5.0F / 16.0F, 0.0F);
        return true;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        int index = prepare(entity.getVariant());
        if (this.handChain != null && !entity.getMainHandItem().isEmpty()) {
            for (ModelPart weapon : this.handWeapons) {
                if (weapon != null) {
                    weapon.visible = false;
                }
            }
        }
        if (entity.deathAnimation.isStarted()) {
            animate(entity.deathAnimation, animation(index, EnemyAnim.DEATH.ordinal()), ageInTicks);
            return;
        }
        lookAt(netHeadYaw, headPitch, 1.0F);
        animateWalk(animation(index, WALK), limbSwing, limbSwingAmount, this.walkSpeed / scaleOf(index), 2.0F);
        animate(entity.idleAnimation, animation(index, IDLE), ageInTicks);
        animate(entity.spawnAnimation, animation(index, EnemyAnim.SPAWN.ordinal()), ageInTicks);
        // special moves wind up in slow motion (ERMonster#animationSpeed)
        animate(entity.attack1Animation, animation(index, EnemyAnim.ATTACK1.ordinal()), ageInTicks,
                entity.animationSpeed(EnemyAnim.ATTACK1));
        animate(entity.attack2Animation, animation(index, EnemyAnim.ATTACK2.ordinal()), ageInTicks,
                entity.animationSpeed(EnemyAnim.ATTACK2));
        animate(entity.castAnimation, animation(index, EnemyAnim.CAST.ordinal()), ageInTicks,
                entity.animationSpeed(EnemyAnim.CAST));
        animate(entity.hurtAnimation, animation(index, EnemyAnim.HURT.ordinal()), ageInTicks);
    }
}
