package com.eternalrealm.client;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.network.forge.ClientboundHandlers;
import com.eternalrealm.ui.hud.CharacterHudOverlay;
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
        ClientboundHandlers.setCharacterSnapshotHandler(ClientCharacterState::apply);
        ClientboundHandlers.setCombatStatsHandler(ClientCombatState::onStats);
        ClientboundHandlers.setCombatStatusHandler(ClientCombatState::onStatus);
        ClientboundHandlers.setDamageNumberHandler(ClientCombatState::onDamageNumber);
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
            }
        });
        ClientboundHandlers.setPlayerActionHandler(CombatAnimations::onRemoteAction);
        ClientboundHandlers.setTelegraphHandler(com.eternalrealm.client.telegraph.ClientTelegraphs::accept);
        com.eternalrealm.block.forge.LightShaftBlockEntity.clientLoaded =
                com.eternalrealm.client.block.ClientLightShafts::onLoaded;
        com.eternalrealm.item.forge.EquipmentStacks.setViewerLevel(() -> ClientCharacterState.snapshot()
                .map(com.eternalrealm.character.CharacterSnapshot::level).orElse(1));
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
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        DualWield.addLayers(event);
    }

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "character_hud", new CharacterHudOverlay());
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "combat_hud", new CombatHudOverlay());
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "party_hud", new com.eternalrealm.ui.hud.PartyHudOverlay());
        event.registerAbove(VanillaGuiOverlay.CHAT_PANEL.id(), "dialogue_box", new com.eternalrealm.ui.dialogue.DialogueOverlay());
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "danger_warning",
                new com.eternalrealm.client.telegraph.DangerOverlay());
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ClientKeyMappings.OPEN_CHARACTER);
        event.register(ClientKeyMappings.HEAVY_ATTACK);
        event.register(ClientKeyMappings.DODGE);
        event.register(ClientKeyMappings.WEAPON_SKILL);
        event.register(ClientKeyMappings.JOB_SKILL_1);
        event.register(ClientKeyMappings.JOB_SKILL_2);
        event.register(ClientKeyMappings.JOB_SKILL_3);
        event.register(ClientKeyMappings.JOB_SKILL_4);
        event.register(ClientKeyMappings.DIALOGUE_NEXT);
    }
}
