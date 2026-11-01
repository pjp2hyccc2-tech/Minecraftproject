package com.frostbound.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Sky of the frozen wastes: normal sun and moon, a cold desaturated fog colour. Clouds sit above the highest
 * management camera position (about y 200) so the zoomed-out city view never looks down onto them.
 */
public final class FrozenEffects extends DimensionSpecialEffects {
    public FrozenEffects() {
        super(250.0f, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        double b = brightness * 0.92 + 0.05;
        return new Vec3(color.x * b * 0.92 + 0.02, color.y * b * 0.96 + 0.03, color.z * b + 0.05);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }
}
