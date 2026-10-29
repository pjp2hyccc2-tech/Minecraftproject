package com.huntcraft.monster.small.client;

import com.huntcraft.client.model.HcModel;
import com.huntcraft.client.model.HcModelLoader;
import com.huntcraft.monster.small.NussuttoMofuEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Thief Mofu model: the regular hcmodel animations, plus a "carry" pose while it holds stolen loot - both arms thrown
 * up in a victory V with the loot balanced on its head (drawn at the "loot" bone by {@link MofuRenderer}).
 */
@OnlyIn(Dist.CLIENT)
public class MofuModel extends HcModel<NussuttoMofuEntity> {
    private static final float ARM_UP = (float) Math.toRadians(-170.0);
    private static final float ARM_SPLAY = (float) Math.toRadians(32.0);

    public MofuModel(ModelPart root, HcModelLoader.HcModelData data) {
        super(root, data);
    }

    @Override
    public void setupAnim(NussuttoMofuEntity e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (!e.hasLoot() || e.isBurrowing() || e.getStateAnimName() != null) return;
        float bob = Mth.sin(ageInTicks * 0.55F) * 0.06F;
        carry(bone("arm_l"), 1, bob);
        carry(bone("arm_r"), -1, -bob);
    }

    private static void carry(ModelPart arm, int side, float bob) {
        if (arm == null) return;
        arm.xRot = ARM_UP + bob;
        arm.yRot = 0.0F;
        arm.zRot = side * ARM_SPLAY;
    }
}
