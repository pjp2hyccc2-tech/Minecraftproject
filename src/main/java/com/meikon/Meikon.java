package com.meikon;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Meikon.MODID)
public class Meikon {
    public static final String MODID = "meikon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Meikon() {
        LOGGER.info("MEIKON skeleton");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
