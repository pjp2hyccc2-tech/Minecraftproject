package com.eternalrealm.client;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.network.forge.ClientboundHandlers;
import com.eternalrealm.ui.hud.CombatHudOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import com.eternalrealm.combat.forge.EREntities;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only mod-bus wiring. {@code value = Dist.CLIENT} guarantees this class (and everything it references) is never
 * loaded on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Extensible-enum values must be created on the main thread before the first model is posed.
        event.enqueueWork(WeaponAnimations::init);
        event.enqueueWork(com.eternalrealm.ui.settings.SettingsAccess::registerConfigScreen);
        ClientboundHandlers.setCharacterSnapshotHandler(ClientCharacterState::apply);
        ClientboundHandlers.setCombatStatsHandler(ClientCombatState::onStats);
        ClientboundHandlers.setCombatStatusHandler(ClientCombatState::onStatus);
        ClientboundHandlers.setDamageNumberHandler(ClientCombatState::onDamageNumber);
        ClientboundHandlers.setPickupFeedHandler(com.eternalrealm.ui.hud.PickupFeedOverlay::onPacket);
        ClientScreens.setBagScreen(com.eternalrealm.ui.screen.BagScreen::new);
        ClientScreens.setEquipmentScreen(com.eternalrealm.ui.screen.EquipmentScreen::new);
        ClientboundHandlers.setUiDataHandler((channel, json) -> {
            ClientUiState.accept(channel, json);
            if ("open".equals(channel)) {
                ClientUiState.get("open").ifPresent(com.eternalrealm.ui.screen.JournalScreen::openRequested);
            } else if ("music".equals(channel)) {
                ClientUiState.get("music").ifPresent(data -> com.eternalrealm.client.sound.MusicDirector.setContext(
                        ClientUiState.stringOr(data, "context", "none")));
            } else if (com.eternalrealm.story.forge.StoryDirector.CHANNEL.equals(channel)) {
                ClientUiState.get(channel).ifPresent(com.eternalrealm.ui.dialogue.ClientDialogue::accept);
            } else if ("jingle".equals(channel)) {
                ClientUiState.get("jingle").ifPresent(data -> com.eternalrealm.client.sound.MusicDirector.playJingle(
                        ClientUiState.stringOr(data, "id", "")));
            } else if (com.eternalrealm.client.boss.BossScreenEffects.CHANNEL.equals(channel)) {
                ClientUiState.get(channel).ifPresent(com.eternalrealm.client.boss.BossScreenEffects::accept);
            } else if (com.eternalrealm.item.forge.SmithService.CHANNEL.equals(channel)) {
                ClientUiState.get(channel).ifPresent(com.eternalrealm.ui.screen.SmithScreen::open);
            } else if (com.eternalrealm.item.forge.SmithService.RESULT_CHANNEL.equals(channel)) {
                ClientUiState.get(channel).ifPresent(com.eternalrealm.ui.screen.SmithScreen::onResult);
            } else if (com.eternalrealm.world.field.Waystones.UI_CHANNEL.equals(channel)) {
                ClientUiState.get(channel).ifPresent(com.eternalrealm.ui.map.WorldMapScreen::openTravel);
            } else if (com.eternalrealm.world.field.Waystones.ARRIVE_CHANNEL.equals(channel)) {
                com.eternalrealm.ui.map.TravelFlashOverlay.start();
            } else {
                ClientChannels.dispatch(channel);
            }
        });
        com.eternalrealm.client.revival.ClientRevival.init();
        com.eternalrealm.client.boss.BossHudOverlay.init();
        com.eternalrealm.ui.screen.DungeonResultScreen.registerChannels();
        ClientTrinkets.register();
        com.eternalrealm.client.map.ThreatClient.init();
        ClientboundHandlers.setPlayerActionHandler(CombatAnimations::onRemoteAction);
        ClientboundHandlers.setTelegraphHandler(com.eternalrealm.client.telegraph.ClientTelegraphs::accept);
        ClientboundHandlers.setHeroFxHandler(HeroFx::accept);
        ClientboundHandlers.setSkillFxHandler(SkillFx::accept);
        com.eternalrealm.block.forge.LightShaftBlockEntity.clientLoaded =
                com.eternalrealm.client.block.ClientLightShafts::onLoaded;
        com.eternalrealm.item.forge.EquipmentStacks.setViewerLevel(() -> ClientCharacterState.snapshot()
                .map(com.eternalrealm.character.CharacterSnapshot::level).orElse(1));
        com.eternalrealm.item.forge.EquipmentStacks.setViewerJob(() -> ClientCharacterState.snapshot()
                .map(snapshot -> com.eternalrealm.job.JobType.fromStored(snapshot.jobId()))
                .orElse(com.eternalrealm.job.JobType.NONE));
    }

    @SubscribeEvent
    public static void onRegisterParticles(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        ERParticleProviders.register(event);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EREntities.MAGIC_PROJECTILE.get(),
                context -> new ThrownItemRenderer<>(context, 1.25F, true));
        event.registerEntityRenderer(EREntities.SKILL_ARROW.get(), SkillArrowRenderer::new);
        event.registerBlockEntityRenderer(com.eternalrealm.block.forge.ERBlocks.LIGHT_SHAFT_ENTITY.get(),
                com.eternalrealm.client.block.LightShaftRenderer::new);
        event.registerBlockEntityRenderer(com.eternalrealm.block.forge.ERBlocks.FLAG_ENTITY.get(),
                com.eternalrealm.client.block.FlagRenderer::new);
        event.registerBlockEntityRenderer(com.eternalrealm.block.forge.ERBlocks.CLOCK_FACE_ENTITY.get(),
                com.eternalrealm.client.block.ClockFaceRenderer::new);
        event.registerBlockEntityRenderer(com.eternalrealm.block.forge.ERBlocks.WINDMILL_SAILS_ENTITY.get(),
                com.eternalrealm.client.block.WindmillRenderer::new);
        event.registerBlockEntityRenderer(com.eternalrealm.block.forge.ERBlocks.WATER_WHEEL_ENTITY.get(),
                com.eternalrealm.client.block.WaterWheelRenderer::new);
        event.registerBlockEntityRenderer(com.eternalrealm.block.forge.ERBlocks.FOUNTAIN_SPRAY_ENTITY.get(),
                com.eternalrealm.client.block.FountainRenderer::new);
        event.registerBlockEntityRenderer(com.eternalrealm.block.forge.ERBlocks.WAYSTONE_ENTITY.get(),
                com.eternalrealm.client.block.WaystoneRenderer::new);
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        DualWield.addLayers(event);
        com.eternalrealm.client.monster.MenaceSwirlLayer.addLayers(event);
    }

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "player_frame", new com.eternalrealm.ui.hud.PlayerFrameOverlay());
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "quick_slots", new com.eternalrealm.ui.hud.QuickSlotsOverlay());
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "hud_icons", new com.eternalrealm.ui.hud.HudIconsOverlay());
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "pickup_feed", new com.eternalrealm.ui.hud.PickupFeedOverlay());
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "combat_hud", new CombatHudOverlay());
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "party_hud", new com.eternalrealm.ui.hud.PartyHudOverlay());
        event.registerAbove(VanillaGuiOverlay.CHAT_PANEL.id(), "dialogue_box", new com.eternalrealm.ui.dialogue.DialogueOverlay());
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "danger_warning",
                new com.eternalrealm.client.telegraph.DangerOverlay());
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "boss_flash",
                new com.eternalrealm.client.boss.BossScreenEffects());
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "waystone_flash",
                new com.eternalrealm.ui.map.TravelFlashOverlay());
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ClientKeyMappings.OPEN_CHARACTER);
        event.register(ClientKeyMappings.OPEN_EQUIPMENT);
        event.register(ClientKeyMappings.HEAVY_ATTACK);
        event.register(ClientKeyMappings.DODGE);
        event.register(ClientKeyMappings.WEAPON_SKILL);
        event.register(ClientKeyMappings.JOB_SKILL_1);
        event.register(ClientKeyMappings.JOB_SKILL_2);
        event.register(ClientKeyMappings.JOB_SKILL_3);
        event.register(ClientKeyMappings.JOB_SKILL_4);
        event.register(ClientKeyMappings.JOB_SKILL_5);
        event.register(ClientKeyMappings.JOB_SKILL_6);
        event.register(ClientKeyMappings.DIALOGUE_NEXT);
    }
}
