package com.nihonisshu.tabi;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TabiMod.MODID)
public class TabiMod {
    public static final String MODID = "tabi";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TabiMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        LOGGER.info("日本一周旅 loading");
    }
}
