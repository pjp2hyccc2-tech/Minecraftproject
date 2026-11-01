package com.blockcity;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(BlockCity.MODID)
public final class BlockCity {
    public static final String MODID = "blockcity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlockCity() {
        LOGGER.info("BLOCK CITY bootstrap");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
