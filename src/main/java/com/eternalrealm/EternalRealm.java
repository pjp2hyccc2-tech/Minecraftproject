package com.eternalrealm;

import com.eternalrealm.core.ModInfo;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Mod entry point. Only wiring lives here: every system is its own module (see docs/ARCHITECTURE.md).
 */
@Mod(ModInfo.MOD_ID)
public final class EternalRealm {
    private static final Logger LOGGER = LogUtils.getLogger();

    public EternalRealm(FMLJavaModLoadingContext context) {
        LOGGER.info("{} {} loaded", ModInfo.DISPLAY_NAME, context.getContainer().getModInfo().getVersion());
    }
}
