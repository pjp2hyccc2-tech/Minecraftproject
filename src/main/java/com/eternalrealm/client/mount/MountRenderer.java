package com.eternalrealm.client.mount;

import com.eternalrealm.client.entity.EntityTextures;
import com.eternalrealm.client.entity.layer.RigGlowLayer;
import com.eternalrealm.client.entity.model.RigTables;
import com.eternalrealm.mount.MountBodies;
import com.eternalrealm.mount.MountKind;
import com.eternalrealm.mount.forge.MountEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Renderer of a mount: the texture of its coat ({@code textures/entity/<rig>/<coat>.png}: each coat is a variant of
 * the model), the model scale, and the emissive layer (eyes and gems, the griffin's and the bike's lanterns, the bike's
 * wheel rims and crystals, which brighten with speed; the rare coats' shining manes, specks and feathers). Mounts show
 * no name tag and never take the death pose.
 */
public class MountRenderer extends MobRenderer<MountEntity, MountModel> {
    private final MountKind kind;
    private final RigTables tables;

    public MountRenderer(EntityRendererProvider.Context context, MountKind kind) {
        super(context, new MountModel(context.bakeLayer(MountClient.layer(kind)), MountClient.tables(kind), kind),
                shadow(kind));
        this.kind = kind;
        this.tables = MountClient.tables(kind);
        addLayer(new RigGlowLayer<>(this, this::glowOf, this::glowColor));
    }

    /** The model variant of the mount's coat (the first one for a coat the model does not know). */
    private String variantOf(MountEntity mount) {
        return this.tables.variants()[this.tables.indexOf(mount.look().coat())];
    }

    private ResourceLocation glowOf(MountEntity mount) {
        int index = this.tables.indexOf(mount.look().coat());
        return this.tables.glows()[index] ? EntityTextures.glow(this.tables.rig(), this.tables.variants()[index]) : null;
    }

    private static float shadow(MountKind kind) {
        return MountBodies.width(kind) * 0.6F;
    }

    private float[] glowColor(MountEntity mount, float ageInTicks) {
        float pulse = 0.85F + 0.15F * Mth.sin(ageInTicks * 0.12F);
        if (this.kind == MountKind.BIKE) {
            pulse = Math.min(1.0F, pulse + mount.speed() * 0.6F);
        }
        return new float[] {pulse, pulse, pulse};
    }

    @Override
    public ResourceLocation getTextureLocation(MountEntity mount) {
        return EntityTextures.body(this.tables.rig(), variantOf(mount));
    }

    @Override
    protected void scale(MountEntity mount, PoseStack poseStack, float partialTick) {
        float scale = MountBodies.scale(this.kind);
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected boolean shouldShowName(MountEntity mount) {
        return false;
    }

    @Override
    protected float getFlipDegrees(MountEntity mount) {
        return 0.0F;
    }
}
