package com.huntcraft.client.atmosphere;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Sky / fog / lightmap look of one HuntCraft dimension, without shaders:
 * tinted fog colour, custom cloud height, tinted lightmap (warm village, cold peaks, hot caldera),
 * and a permanent ember glow on the caldera horizon.
 */
@OnlyIn(Dist.CLIENT)
public final class HCDimensionEffects extends DimensionSpecialEffects {
    private final AtmosphereModule.Kind kind;
    private final Vector3f fogTint;
    private final Vector3f lightmapTint;
    private final float[] sunriseColor = new float[4];

    public HCDimensionEffects(AtmosphereModule.Kind kind, float cloudLevel, Vector3f fogTint, Vector3f lightmapTint) {
        super(cloudLevel, true, SkyType.NORMAL, false, false);
        this.kind = kind;
        this.fogTint = fogTint;
        this.lightmapTint = lightmapTint;
    }

    public AtmosphereModule.Kind kind() {
        return kind;
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        Vec3 base = fogColor.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
        return base.multiply(fogTint.x, fogTint.y, fogTint.z);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Nullable
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        if (kind == AtmosphereModule.Kind.EMBER) {
            // the caldera horizon always glows like a sunset
            float f = Mth.cos(timeOfDay * Mth.TWO_PI) * 0.5F + 0.5F;
            sunriseColor[0] = 0.80F;
            sunriseColor[1] = 0.30F;
            sunriseColor[2] = 0.10F;
            sunriseColor[3] = 0.18F + 0.10F * f;
            return sunriseColor;
        }
        return super.getSunriseColor(timeOfDay, partialTicks);
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        colors.mul(lightmapTint);
        float blockLight = pixelX / 15.0F;
        switch (kind) {
            case EMBER -> {
                // hot block light, a faint ember glow even in the dark
                colors.add(blockLight * blockLight * 0.08F, blockLight * blockLight * 0.02F, 0.0F);
                colors.add(0.035F, 0.012F, 0.0F);
            }
            case FROST -> {
                // moonlit blue shadows
                float dark = 1.0F - Mth.clamp(Math.max(blockLight, pixelY / 15.0F), 0.0F, 1.0F);
                colors.add(0.0F, dark * 0.015F, dark * 0.05F);
            }
            case VILLAGE -> colors.add(blockLight * 0.03F, blockLight * 0.015F, 0.0F);
            case HILLS -> colors.add(0.0F, 0.006F, 0.0F);
        }
        colors.x = Mth.clamp(colors.x, 0.0F, 1.0F);
        colors.y = Mth.clamp(colors.y, 0.0F, 1.0F);
        colors.z = Mth.clamp(colors.z, 0.0F, 1.0F);
    }
}
