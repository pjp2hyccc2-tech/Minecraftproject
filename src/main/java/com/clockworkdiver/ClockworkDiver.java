package com.clockworkdiver;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ClockworkDiver.MODID)
public class ClockworkDiver {
    public static final String MODID = "clockworkdiver";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ClockworkDiver() {
        LOGGER.info("Clockwork Diver loading");
    }
}
