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
 * Renderer of a mount: its texture ({@code textures/entity/<rig>/<rig>.png}), the model scale, and the emissive
 * layer (eyes and gems, the griffin's and the bike's lanterns, the bike's wheel rims and crystals, which brighten with
 * speed). Mounts show no name tag and never take the death pose.
 */
public class MountRenderer extends MobRenderer<MountEntity, MountModel> {
    private final MountKind kind;
    private final RigTables tables;
    private final ResourceLocation texture;
    private final ResourceLocation glow;

    public MountRenderer(EntityRendererProvider.Context context, MountKind kind) {
        super(context, new MountModel(context.bakeLayer(MountClient.layer(kind)), MountClient.tables(kind), kind),
                shadow(kind));
        this.kind = kind;
        this.tables = MountClient.tables(kind);
        String variant = this.tables.variants()[0];
        this.texture = EntityTextures.body(this.tables.rig(), variant);
        this.glow = this.tables.glows()[0] ? EntityTextures.glow(this.tables.rig(), variant) : null;
        addLayer(new RigGlowLayer<>(this, mount -> this.glow, this::glowColor));
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
        return this.texture;
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
