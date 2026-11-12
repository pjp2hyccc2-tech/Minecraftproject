package com.eternalrealm.client;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.ui.hud.DamageNumberRenderer;
import com.eternalrealm.ui.screen.JournalScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only game-bus handlers: key input, combat input, world rendering and clearing state when leaving a
 * world/server.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            // Before vanilla processes key presses this tick, so weapon clicks never reach the vanilla attack.
            ClientCombatController.onTickStart();
            CameraShake.tick();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (ClientKeyMappings.OPEN_CHARACTER.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                JournalScreen.open();
            }
        }
        com.eternalrealm.ui.dialogue.ClientDialogue.tick();
        com.eternalrealm.client.telegraph.ClientTelegraphs.tick();
        SkillFx.tick();
        com.eternalrealm.client.boss.BossBodyEffects.tick();
        com.eternalrealm.client.block.ClientLightShafts.tick();
        BotSkins.tick(minecraft);
        KeyConflicts.resolveOnce();
    }

    /**
     * No vanilla attack or block breaking while holding a weapon (e.g. holding the button on a block), and no off-hand
     * use when right click belongs to the guard (otherwise a refused guard would fall through to e.g. an off-hand
     * shield). Sneaking + right click still uses the off-hand item.
     */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        boolean cancel = (event.isAttack() && ClientCombatController.holdsWeapon())
                || (event.isUseItem() && event.getHand() == InteractionHand.OFF_HAND
                && ClientCombatController.blocksOffHandUse());
        if (cancel) {
            event.setCanceled(true);
            event.setSwingHand(false);
        } else if (event.isAttack() && attackThroughPlayer(Minecraft.getInstance())) {
            event.setCanceled(true); // the swing still plays
        }
    }

    /**
     * A left click aimed at another player goes through them to the creature behind (players never hurt each other
     * outside PvP matches, so in a crowd the click must not be wasted on an ally). PvP arenas do not exist yet; when
     * they do, opponents must not be let through here.
     *
     * @return whether the click was aimed at another player (and was handled here)
     */
    private static boolean attackThroughPlayer(Minecraft minecraft) {
        net.minecraft.client.player.LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null
                || !(minecraft.hitResult instanceof net.minecraft.world.phys.EntityHitResult aimed)
                || !(aimed.getEntity() instanceof net.minecraft.world.entity.player.Player other) || other == player) {
            return false;
        }
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0F);
        double reach = player.getEntityReach();
        net.minecraft.world.phys.Vec3 end = eye.add(look.scale(reach));
        net.minecraft.world.phys.AABB sweep = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        net.minecraft.world.phys.EntityHitResult behind = net.minecraft.world.entity.projectile.ProjectileUtil
                .getEntityHitResult(player, eye, end, sweep, entity -> !entity.isSpectator() && entity.isPickable()
                        && !(entity instanceof net.minecraft.world.entity.player.Player), reach * reach);
        if (behind == null) {
            return true;
        }
        net.minecraft.world.phys.BlockHitResult wall = player.level().clip(new net.minecraft.world.level.ClipContext(
                eye, behind.getLocation(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (wall.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            minecraft.gameMode.attack(player, behind.getEntity());
        }
        return true;
    }

    @SubscribeEvent
    public static void onCameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        CameraShake.apply(event);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        com.eternalrealm.client.telegraph.TelegraphRenderer.render(event);
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            ArtTrails.render(event);
            HeroFx.render(event);
            SkillFx.render(event);
            DamageNumberRenderer.render(event);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
        DualWield.onRenderHand(event);
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        DualWield.onRenderPlayerPre(event);
    }

    /** A relic's tooltip has the gold frame (金枠) as its border. */
    @SubscribeEvent
    public static void onTooltipColor(net.minecraftforge.client.event.RenderTooltipEvent.Color event) {
        if (com.eternalrealm.item.forge.EquipmentStacks.isGoldFrame(event.getItemStack())) {
            event.setBorderStart(com.eternalrealm.ui.GoldFrame.borderTop());
            event.setBorderEnd(com.eternalrealm.ui.GoldFrame.borderBottom());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Never show one world's/server's character in the next one.
        ClientCharacterState.clear();
        ClientCombatState.clear();
        ClientUiState.clear();
        ClientTrinkets.clear();
        CombatAnimations.clear();
        CameraShake.clear();
        SkillFx.clear();
        com.eternalrealm.client.boss.BossBodyEffects.clear();
        com.eternalrealm.ui.dialogue.ClientDialogue.clear();
        com.eternalrealm.client.telegraph.ClientTelegraphs.clear();
        com.eternalrealm.client.block.ClientLightShafts.clear();
    }
}
