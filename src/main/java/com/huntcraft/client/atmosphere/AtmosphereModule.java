package com.huntcraft.client.atmosphere;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HCDimensions;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * In-mod atmosphere (works without any shader loader): per-dimension sky / fog / lightmap tint via
 * {@link HCDimensionEffects}, ambient particles, roar / lightning screen effects ({@link AtmosphereEvents}),
 * and the bundled shader pack installer ({@link ShaderPackInstaller}).
 */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AtmosphereModule {
    /** The four HuntCraft worlds, each with its own look. */
    public enum Kind {
        VILLAGE, HILLS, FROST, EMBER
    }

    private AtmosphereModule() {
    }

    @Nullable
    public static Kind kindOf(@Nullable Level level) {
        if (level == null) return null;
        if (level.dimension() == HCDimensions.HUNTER_VILLAGE) return Kind.VILLAGE;
        if (level.dimension() == HCDimensions.VERDANT_HILLS) return Kind.HILLS;
        if (level.dimension() == HCDimensions.FROSTPEAK_RANGE) return Kind.FROST;
        if (level.dimension() == HCDimensions.EMBER_CALDERA) return Kind.EMBER;
        return null;
    }

    @SubscribeEvent
    public static void dimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        // keys match the "effects" field of data/huntcraft/dimension_type/*.json
        event.register(HuntCraft.id("hunter_village"), new HCDimensionEffects(Kind.VILLAGE, 192.0F,
                new Vector3f(1.00F, 0.94F, 0.90F), new Vector3f(1.04F, 0.99F, 0.94F)));
        event.register(HuntCraft.id("verdant_hills"), new HCDimensionEffects(Kind.HILLS, 200.0F,
                new Vector3f(0.92F, 0.98F, 1.00F), new Vector3f(1.00F, 1.01F, 0.97F)));
        event.register(HuntCraft.id("frostpeak_range"), new HCDimensionEffects(Kind.FROST, 170.0F,
                new Vector3f(0.86F, 0.92F, 1.00F), new Vector3f(0.93F, 0.98F, 1.08F)));
        event.register(HuntCraft.id("ember_caldera"), new HCDimensionEffects(Kind.EMBER, 150.0F,
                new Vector3f(1.00F, 0.62F, 0.38F), new Vector3f(1.08F, 0.94F, 0.84F)));
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("hc_atmosphere", AtmosphereEvents::renderOverlay);
    }
}
