package com.eternalrealm.client.entity;

import com.eternalrealm.core.ModInfo;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Texture locations {@code textures/entity/<rig>/<variant>.png} and {@code ..._glow.png}, cached. */
public final class EntityTextures {
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    public static final ResourceLocation BOSS_SHIELD =
            new ResourceLocation(ModInfo.MOD_ID, "textures/entity/fx/boss_shield.png");
    /** Flowing wisps of the aura of later boss forms (scrolled by the energy swirl render type, tiles). */
    public static final ResourceLocation BOSS_AURA =
            new ResourceLocation(ModInfo.MOD_ID, "textures/entity/fx/boss_aura.png");

    private EntityTextures() {
    }

    public static ResourceLocation body(String rig, String variant) {
        return CACHE.computeIfAbsent(rig + "/" + variant,
                key -> new ResourceLocation(ModInfo.MOD_ID, "textures/entity/" + key + ".png"));
    }

    public static ResourceLocation glow(String rig, String variant) {
        return CACHE.computeIfAbsent(rig + "/" + variant + "_glow",
                key -> new ResourceLocation(ModInfo.MOD_ID, "textures/entity/" + key + ".png"));
    }
}
