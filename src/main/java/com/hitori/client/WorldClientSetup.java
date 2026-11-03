package com.hitori.client;

import com.hitori.Hitori;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/** Client registrations of the apartment (block entity renderers, block colours). Owner: world. */
@Mod.EventBusSubscriber(modid = Hitori.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WorldClientSetup {
    private WorldClientSetup() {}
}
