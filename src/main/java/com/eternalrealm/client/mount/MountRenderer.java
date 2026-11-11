package com.eternalrealm.client.mount;

import com.eternalrealm.client.entity.EntityTextures;
import com.eternalrealm.client.entity.layer.RigGlowLayer;
import com.eternalrealm.client.entity.model.RigTables;
import com.eternalrealm.mount.MountBodies;
import com.eternalrealm.mount.MountFamily;
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
 * wheel rims and crystals, which brighten with speed; the rare coats' shining manes, specks and feathers; the breeds'
 * fire, light, crystals, lightning and stars). The glow lives: fire flickers and roars up in a surge, the halohorn's
 * light breathes, the storm's lightning flashes now and then, the void's glow pulses. Mounts show no name tag and
 * never take the death pose.
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
        if (this.kind.family() == MountFamily.BIKE) {
            pulse = Math.min(1.0F, pulse + mount.speed() * 0.6F);
        }
        switch (this.kind) {
            case HORSE_BLAZE -> {
                float flicker = 0.5F * Mth.sin(ageInTicks * 0.9F) + 0.3F * Mth.sin(ageInTicks * 2.3F + 1.7F)
                        + 0.2F * Mth.sin(ageInTicks * 5.1F + 0.4F);
                pulse = mount.surging() ? 1.0F : 0.84F + 0.12F * flicker;
            }
            case HORSE_HALO -> pulse = 0.78F + 0.22F * Mth.sin(ageInTicks * 0.06F);
            case GRIFFIN_STORM -> {
                float charge = Mth.sin(ageInTicks * 0.37F) * Mth.sin(ageInTicks * 0.113F);
                pulse = charge > 0.93F ? 1.0F : 0.7F + 0.1F * Mth.sin(ageInTicks * 0.2F);
            }
            case GRIFFIN_VOID -> pulse = 0.7F + 0.3F * Mth.sin(ageInTicks * 0.09F);
            case GRIFFIN_FROST -> pulse = 0.82F + 0.12F * Mth.sin(ageInTicks * 0.05F);
            default -> {
                // the steady pulse
            }
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
