package com.eternalrealm;

import com.eternalrealm.bootstrap.ServerBootstrap;
import com.eternalrealm.character.forge.CharacterCommands;
import com.eternalrealm.character.forge.InterimKillExperience;
import com.eternalrealm.combat.forge.CombatEvents;
import com.eternalrealm.combat.forge.ERCreativeTabs;
import com.eternalrealm.combat.forge.EREntities;
import com.eternalrealm.combat.forge.ERItems;
import com.eternalrealm.combat.forge.ERMobEffects;
import com.eternalrealm.entity.EREntityTypes;
import com.eternalrealm.job.forge.JobActions;
import com.eternalrealm.social.forge.SocialCommands;
import com.eternalrealm.social.forge.SocialUiActions;
import com.eternalrealm.npc.forge.Npcs;
import com.eternalrealm.ranking.forge.RankingCommands;
import com.eternalrealm.config.ERClientConfig;
import com.eternalrealm.config.ERServerConfig;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.player.forge.PlayerConnectionEvents;
import com.eternalrealm.world.forge.AstoriaWorldgen;
import com.eternalrealm.world.forge.LandmarkBuilder;
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
        com.eternalrealm.item.forge.ERGearItems.register(modBus);
        com.eternalrealm.item.forge.GearEvents.install();
        com.eternalrealm.item.forge.Bags.install();
        ERMobEffects.register(modBus);
        com.eternalrealm.combat.forge.ERParticles.register(modBus);
        EREntities.register(modBus);
        EREntityTypes.register(modBus);
        com.eternalrealm.npc.forge.NpcEntities.register(modBus);
        com.eternalrealm.block.forge.ERBlocks.register(modBus);
        com.eternalrealm.block.forge.ERBannerPatterns.register(modBus);
        com.eternalrealm.sound.ERSounds.register(modBus);
        ERCreativeTabs.register(modBus);
        JobActions.register();
        RankingCommands.register();
        SocialUiActions.register();
        com.eternalrealm.quest.forge.QuestActions.register();
        com.eternalrealm.npc.forge.NpcServices.register();
        com.eternalrealm.story.forge.StoryActions.register();
        com.eternalrealm.world.forge.WaystoneService.register();
        com.eternalrealm.item.forge.SmithService.register();
        com.eternalrealm.item.forge.BagService.register();
        com.eternalrealm.revival.forge.RevivalService.register();
        com.eternalrealm.dungeon.forge.DungeonResults.register();
        AstoriaWorldgen.register(modBus);

        modBus.addListener(ServerBootstrap::onConfigReloading);
        forgeBus.addListener(ServerBootstrap::onServerAboutToStart);
        forgeBus.addListener((net.minecraftforge.event.server.ServerStartedEvent event) -> {
            if (com.eternalrealm.config.ERServerConfig.KEEP_INVENTORY.get()) {
                event.getServer().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)
                        .set(true, event.getServer());
            }
        });
        forgeBus.addListener(com.eternalrealm.player.forge.Vitals::onServerStarted);
        forgeBus.addListener(com.eternalrealm.player.forge.Vitals::onPlayerTick);
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
        forgeBus.addListener(com.eternalrealm.world.forge.capital.CapitalBuilder::onServerTick);
        forgeBus.addListener(com.eternalrealm.world.forge.CapitalCommands::onRegisterCommands);
        forgeBus.addListener((net.minecraftforge.event.server.ServerStoppedEvent event) ->
                com.eternalrealm.world.forge.capital.CapitalBuilder.onServerStopped());
        com.eternalrealm.world.forge.capital.CapitalBuilder.onCompleted(
                com.eternalrealm.world.forge.RealmSetup::onCapitalCompleted);
        forgeBus.addListener(com.eternalrealm.world.forge.CapitalProtection::onBreak);
        forgeBus.addListener(com.eternalrealm.world.forge.CapitalProtection::onPlace);
        forgeBus.addListener(com.eternalrealm.world.forge.CapitalProtection::onBucket);
        forgeBus.addListener(CombatEvents::onLivingAttack);
        forgeBus.addListener(CombatEvents::onLivingHurt);
        forgeBus.addListener(com.eternalrealm.item.forge.GearEvents::onEquipmentChange);
        forgeBus.addListener(EventPriority.HIGH, com.eternalrealm.monster.forge.MonsterEvents::onLivingHurt);
        forgeBus.addListener(com.eternalrealm.monster.forge.MonsterEvents::onLivingDeath);
        forgeBus.addListener(com.eternalrealm.monster.forge.MonsterEvents::onLivingDrops);
        forgeBus.addListener(com.eternalrealm.monster.forge.MonsterEvents::onExperienceDrop);
        forgeBus.addListener(com.eternalrealm.monster.forge.MonsterEvents::onLivingTick);
        forgeBus.addListener(com.eternalrealm.monster.forge.MonsterEvents::onMobGriefing);
        forgeBus.addListener(com.eternalrealm.monster.forge.MonsterEvents::onEntityJoin);
        forgeBus.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,
                com.eternalrealm.monster.forge.VanillaSpawnBlocker::onFinalizeSpawn);
        forgeBus.addListener(com.eternalrealm.quest.forge.QuestActions::onItemPickup);
        forgeBus.addListener(EventPriority.HIGH, com.eternalrealm.dungeon.forge.DungeonEvents::onLivingHurt);
        forgeBus.addListener(com.eternalrealm.dungeon.forge.DungeonEvents::onLivingAttack);
        forgeBus.addListener(com.eternalrealm.boss.forge.BossProps::onEntityJoin);
        forgeBus.addListener(com.eternalrealm.dungeon.forge.DungeonEvents::onLivingDeath);
        forgeBus.addListener(com.eternalrealm.dungeon.forge.DungeonEvents::onLivingDamage);
        forgeBus.addListener(com.eternalrealm.dungeon.forge.DungeonEvents::onLivingDrops);
        forgeBus.addListener(com.eternalrealm.dungeon.forge.DungeonEvents::onExperienceDrop);
        forgeBus.addListener(com.eternalrealm.dungeon.forge.DungeonEvents::onRespawn);
        forgeBus.addListener(com.eternalrealm.dungeon.forge.DungeonEvents::onLogin);
        forgeBus.addListener(com.eternalrealm.dungeon.forge.DungeonEvents::onRegisterCommands);
        forgeBus.addListener(com.eternalrealm.economy.forge.MarketCommands::onRegisterCommands);
        forgeBus.addListener(com.eternalrealm.economy.forge.MarketCommands::onLogin);
        forgeBus.addListener(com.eternalrealm.item.forge.GearEvents::onLivingHurt);
        forgeBus.addListener(CombatEvents::onKnockBack);
        forgeBus.addListener(EventPriority.LOWEST, CombatEvents::onLivingDeath);
        forgeBus.addListener(CombatEvents::onRespawn);
        forgeBus.addListener(CombatEvents::onClone);
        forgeBus.addListener(CombatEvents::onChangedDimension);
        forgeBus.addListener(LandmarkBuilder::onServerTick);
        forgeBus.addListener(com.eternalrealm.world.forge.RealmSetup::onServerStarted);
        forgeBus.addListener(com.eternalrealm.world.forge.RealmSetup::onServerTick);
        forgeBus.addListener(com.eternalrealm.world.forge.RealmSetup::onLogin);
        forgeBus.addListener(EventPriority.HIGH, com.eternalrealm.world.forge.RealmSetup::onRespawn);

        LOGGER.info("{} {} loaded (network protocol {})", ModInfo.DISPLAY_NAME,
                context.getContainer().getModInfo().getVersion(), ERNetwork.PROTOCOL_VERSION);
    }
}
