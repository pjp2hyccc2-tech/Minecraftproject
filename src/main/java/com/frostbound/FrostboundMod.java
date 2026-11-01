package com.frostbound;

import com.frostbound.config.FrostboundConfig;
import com.frostbound.core.CoreInfo;
import com.frostbound.network.Net;
import com.frostbound.registry.ModRegistries;
import com.frostbound.selftest.BootSelfTest;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * FROSTBOUND: THE LAST HEARTH.
 *
 * <p>The simulation lives in the pure-Java {@code com.frostbound.core} module; this layer wires it to
 * Minecraft: registries, the frozen dimension, persistence, networking, NPCs and the client UI.
 */
@Mod(CoreInfo.MOD_ID)
public final class FrostboundMod {
    public static final String MOD_ID = CoreInfo.MOD_ID;
    public static final Logger LOGGER = LogUtils.getLogger();

    public FrostboundMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistries.register(modBus);
        modBus.addListener(ModRegistries::onEntityAttributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FrostboundConfig.SPEC);
        Net.init();
        if (BootSelfTest.isEnabled()) {
            MinecraftForge.EVENT_BUS.register(BootSelfTest.class);
            LOGGER.info("[FROSTBOUND] server self-test enabled (phase {})", BootSelfTest.phase());
        }
        LOGGER.info("[FROSTBOUND] mod constructed");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
