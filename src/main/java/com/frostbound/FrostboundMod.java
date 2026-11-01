package com.frostbound;

import com.frostbound.core.CoreInfo;
import com.frostbound.selftest.BootSelfTest;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * FROSTBOUND: THE LAST HEARTH.
 *
 * <p>BUILD 0 skeleton: verifies the build pipeline only. No gameplay is registered yet.
 */
@Mod(CoreInfo.MOD_ID)
public final class FrostboundMod {
    public static final String MOD_ID = CoreInfo.MOD_ID;
    public static final Logger LOGGER = LogUtils.getLogger();

    public FrostboundMod() {
        LOGGER.info("[FROSTBOUND] mod constructed (BUILD 0 skeleton, no gameplay yet)");
        if (BootSelfTest.isEnabled()) {
            MinecraftForge.EVENT_BUS.addListener(BootSelfTest::onServerStarted);
            LOGGER.info("[FROSTBOUND] boot self-test enabled");
        }
    }
}
