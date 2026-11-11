package com.eternalrealm.client.guildhouse;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.guild.house.forge.TrainingDummy;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws the training dummy in one of its three looks (straw, armoured, painted target). No name tag. */
public class TrainingDummyRenderer extends LivingEntityRenderer<TrainingDummy, TrainingDummyModel> {
    private static final ResourceLocation[] TEXTURES = {
            new ResourceLocation(ModInfo.MOD_ID, "textures/entity/training_dummy_straw.png"),
            new ResourceLocation(ModInfo.MOD_ID, "textures/entity/training_dummy_armoured.png"),
            new ResourceLocation(ModInfo.MOD_ID, "textures/entity/training_dummy_target.png"),
    };

    public TrainingDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new TrainingDummyModel(context.bakeLayer(TrainingDummyModel.LAYER)), 0.45F);
    }

    @Override
    public ResourceLocation getTextureLocation(TrainingDummy dummy) {
        return TEXTURES[Math.max(0, Math.min(TEXTURES.length - 1, dummy.look()))];
    }

    @Override
    protected boolean shouldShowName(TrainingDummy dummy) {
        return false;
    }

    @Override
    protected float getFlipDegrees(TrainingDummy dummy) {
        return 0.0F;
    }
}
