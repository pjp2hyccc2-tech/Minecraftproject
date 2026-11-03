package com.reisatsu.client;

import com.reisatsu.Reisatsu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/** Client registrations of the restaurant (block entity renderers, block colours). Owner: world. */
@Mod.EventBusSubscriber(modid = Reisatsu.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WorldClientSetup {
    private WorldClientSetup() {}
}
