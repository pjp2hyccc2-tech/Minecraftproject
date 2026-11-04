package jp.satsujinki;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Satsujinki.MODID)
public class Satsujinki {
    public static final String MODID = "satsujinki";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Satsujinki() {
        LOGGER.info("Satsujinki skeleton loaded");
    }
}
