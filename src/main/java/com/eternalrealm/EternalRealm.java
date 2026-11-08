package com.eternalrealm;

import com.eternalrealm.bootstrap.ServerBootstrap;
import com.eternalrealm.character.forge.CharacterCommands;
import com.eternalrealm.character.forge.InterimKillExperience;
import com.eternalrealm.combat.forge.CombatEvents;
import com.eternalrealm.combat.forge.ERCreativeTabs;
import com.eternalrealm.combat.forge.EREntities;
import com.eternalrealm.combat.forge.ERItems;
import com.eternalrealm.combat.forge.ERMobEffects;
import com.eternalrealm.job.forge.JobActions;
import com.eternalrealm.social.forge.SocialCommands;
import com.eternalrealm.npc.forge.Npcs;
import com.eternalrealm.ranking.forge.RankingCommands;
import com.eternalrealm.config.ERClientConfig;
import com.eternalrealm.config.ERServerConfig;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.player.forge.PlayerConnectionEvents;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Mod entry point. Only wiring lives here: every system is its own module (see docs/ARCHITECTURE.md).
 * Client-only wiring is in {@code com.eternalrealm.client} and is loaded by Forge on the client only.
 */
@Mod(ModInfo.MOD_ID)
public final class EternalRealm {
    private static final Logger LOGGER = LogUtils.getLogger();

    public EternalRealm(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        IEventBus forgeBus = MinecraftForge.EVENT_BUS;

        context.registerConfig(ModConfig.Type.SERVER, ERServerConfig.SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, ERClientConfig.SPEC);

        ERNetwork.register();
        ERItems.register(modBus);
        ERMobEffects.register(modBus);
        EREntities.register(modBus);
        ERCreativeTabs.register(modBus);
        JobActions.register();
        RankingCommands.register();

        modBus.addListener(ServerBootstrap::onConfigReloading);
        forgeBus.addListener(ServerBootstrap::onServerAboutToStart);
        forgeBus.addListener(ServerBootstrap::onServerStopping);
        forgeBus.addListener(ServerBootstrap::onServerStopped);
        forgeBus.addListener(ServerBootstrap::onServerTick);
        forgeBus.addListener(PlayerConnectionEvents::onPlayerLoggedIn);
        forgeBus.addListener(PlayerConnectionEvents::onPlayerLoggedOut);
        forgeBus.addListener(EventPriority.LOWEST, InterimKillExperience::onLivingDeath);
        forgeBus.addListener(CharacterCommands::onRegisterCommands);
        forgeBus.addListener(JobActions::onRegisterCommands);
        forgeBus.addListener(SocialCommands::onRegisterCommands);
        forgeBus.addListener(RankingCommands::onRegisterCommands);
        forgeBus.addListener(SocialCommands::onNameFormat);
        forgeBus.addListener(Npcs::onEntityInteract);
        forgeBus.addListener(CombatEvents::onLivingAttack);
        forgeBus.addListener(CombatEvents::onLivingHurt);
        forgeBus.addListener(CombatEvents::onKnockBack);
        forgeBus.addListener(EventPriority.LOWEST, CombatEvents::onLivingDeath);
        forgeBus.addListener(CombatEvents::onRespawn);
        forgeBus.addListener(CombatEvents::onClone);
        forgeBus.addListener(CombatEvents::onChangedDimension);

        LOGGER.info("{} {} loaded (network protocol {})", ModInfo.DISPLAY_NAME,
                context.getContainer().getModInfo().getVersion(), ERNetwork.PROTOCOL_VERSION);
    }
}
