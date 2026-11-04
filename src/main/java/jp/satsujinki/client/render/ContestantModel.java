package jp.satsujinki.client.render;

import jp.satsujinki.entity.ContestantEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Player-shaped model with emotional poses: flailing when grabbed, begging, hands-up panic, guard stance. */
public class ContestantModel extends PlayerModel<ContestantEntity> {
    public ContestantModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(ContestantEntity e, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        int st = e.getState();
        this.crouching = st == ContestantEntity.ST_COWER;
        super.setupAnim(e, limbSwing, limbSwingAmount, age, netHeadYaw, headPitch);
        float fear = e.getFear();
        switch (st) {
            case ContestantEntity.ST_GRABBED, ContestantEntity.ST_THROWN -> {
                this.rightArm.xRot = -2.6F + Mth.sin(age * 1.3F) * 0.7F;
                this.rightArm.zRot = 0.3F + Mth.cos(age * 1.1F) * 0.35F;
                this.leftArm.xRot = -2.6F + Mth.cos(age * 1.2F) * 0.7F;
                this.leftArm.zRot = -0.3F - Mth.sin(age * 1.4F) * 0.35F;
                this.rightLeg.xRot = Mth.sin(age * 1.6F) * 0.9F;
                this.leftLeg.xRot = -Mth.sin(age * 1.6F) * 0.9F;
                this.head.xRot = -0.35F + Mth.sin(age * 2.1F) * 0.25F;
            }
            case ContestantEntity.ST_COWER -> {
                float bob = Mth.sin(age * 0.55F) * 0.18F;
                this.rightArm.xRot = -1.45F + bob;
                this.rightArm.yRot = -0.5F;
                this.rightArm.zRot = 0.0F;
                this.leftArm.xRot = -1.45F + bob;
                this.leftArm.yRot = 0.5F;
                this.leftArm.zRot = 0.0F;
                this.head.xRot = 0.25F + bob * 0.5F;
            }
            case ContestantEntity.ST_LOCKED -> {
                this.rightArm.xRot = 0.55F;
                this.rightArm.zRot = 0.2F + Mth.sin(age * 0.9F) * 0.08F;
                this.leftArm.xRot = 0.55F;
                this.leftArm.zRot = -0.2F - Mth.sin(age * 0.9F) * 0.08F;
                this.head.xRot = 0.45F + Mth.sin(age * 0.7F) * 0.06F;
                this.head.yRot = Mth.sin(age * 0.33F) * 0.35F;
                this.rightLeg.xRot = Mth.sin(age * 0.8F) * 0.12F;
                this.leftLeg.xRot = -Mth.sin(age * 0.8F) * 0.12F;
            }
            case ContestantEntity.ST_PANIC -> {
                float k = Math.min(1.0F, fear);
                this.rightArm.xRot = this.rightArm.xRot * 0.3F - 2.5F * k + Mth.sin(age * 0.9F) * 0.35F;
                this.leftArm.xRot = this.leftArm.xRot * 0.3F - 2.5F * k + Mth.cos(age * 0.9F) * 0.35F;
                this.rightArm.zRot = 0.35F;
                this.leftArm.zRot = -0.35F;
            }
            case ContestantEntity.ST_FIGHT -> {
                if (this.attackTime <= 0.0F) {
                    this.rightArm.xRot = -1.15F + Mth.sin(age * 0.4F) * 0.08F;
                    this.rightArm.yRot = -0.35F;
                    this.leftArm.xRot = -1.35F + Mth.cos(age * 0.4F) * 0.08F;
                    this.leftArm.yRot = 0.45F;
                }
            }
            case ContestantEntity.ST_ANXIOUS -> {
                this.rightArm.zRot = 0.08F;
                this.leftArm.zRot = -0.08F;
                this.rightArm.xRot = this.rightArm.xRot * 0.5F - 0.3F;
                this.leftArm.xRot = this.leftArm.xRot * 0.5F - 0.3F;
            }
            default -> {
            }
        }
        if (fear > 0.4F && st != ContestantEntity.ST_GRABBED) {
            float tr = (fear - 0.4F) * 0.09F;
            this.head.yRot += Mth.sin(age * 4.7F) * tr;
            this.head.zRot += Mth.cos(age * 5.3F) * tr;
            this.rightArm.zRot += Mth.sin(age * 6.1F) * tr;
            this.leftArm.zRot -= Mth.sin(age * 5.7F) * tr;
        }
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
    }
}
