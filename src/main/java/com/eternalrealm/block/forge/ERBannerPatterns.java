package com.eternalrealm.block.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The kingdom crest as a banner pattern: banners store it as {@code {Pattern:"er_crest",Color:4}} (gold on any base).
 * Textures: {@code textures/entity/banner/er_crest.png} and {@code textures/entity/shield/er_crest.png}.
 */
public final class ERBannerPatterns {
    public static final DeferredRegister<BannerPattern> PATTERNS =
            DeferredRegister.create(Registries.BANNER_PATTERN, ModInfo.MOD_ID);
    public static final RegistryObject<BannerPattern> CREST = PATTERNS.register("er_crest", () -> new BannerPattern("er_crest"));

    private ERBannerPatterns() {
    }

    public static void register(IEventBus modBus) {
        PATTERNS.register(modBus);
    }
}
