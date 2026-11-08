package com.eternalrealm.client;

import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.CombatEvent;
import com.eternalrealm.combat.CombatRequest;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.HitShape;
import com.eternalrealm.combat.RequestOutcome;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.forge.WeaponItem;
import com.eternalrealm.network.forge.CombatActionPacket;
import com.eternalrealm.network.forge.ERNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Turns input into combat requests (client thread, at the start of every client tick, before vanilla handles keys).
 *
 * <p>While an ETERNAL REALM weapon is in the main hand, left clicks are taken away from vanilla (no vanilla attack,
 * no block breaking) and become light attacks. Each request is first run through the local {@link Combatant}
 * (prediction): only requests the rules allow are sent, and the swing, sounds and dodge movement start immediately.
 * The server answers every request; the answer corrects stamina/cooldowns and undoes rejected predictions.</p>
 */
public final class ClientCombatController {
    /** Horizontal dodge impulse in blocks/tick: about 2.6 blocks on the ground, a short dash in the air. */
    private static final double DODGE_SPEED_GROUND = 1.2;
    private static final double DODGE_SPEED_AIR = 0.35;

    private static boolean heavyWasDown;

    private ClientCombatController() {
    }

    public static void onTickStart() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            heavyWasDown = false;
            return;
        }
        if (minecraft.isPaused()) {
            return; // the integrated server is paused too: keep both combat clocks in step
        }
        long now = ClientCombatState.advanceTick();
        Combatant local = ClientCombatState.combatant();
        WeaponProfile weapon = WeaponItem.profileOf(player.getMainHandItem());
        boolean inGame = minecraft.screen == null && minecraft.getOverlay() == null;
        boolean canAct = local != null && weapon != null && inGame && player.isAlive() && !player.isSpectator();

        if (local != null) {
            local.updateWeapon(weapon);
            boolean usingWeapon = isUsingWeapon(player);
            if (local.isGuarding() && !usingWeapon) {
                local.stopGuard();
            } else if (!local.isGuarding() && usingWeapon) {
                local.startGuard(now, weapon); // the server started a guard we did not predict: follow it
            }
        }

        if (weapon != null) {
            // Taken from vanilla so it never attacks or mines with a weapon.
            while (minecraft.options.keyAttack.consumeClick()) {
                if (canAct) {
                    request(minecraft, player, local, CombatRequest.LIGHT, now, weapon);
                }
            }
        }

        boolean heavyDown = inGame && ClientKeyMappings.HEAVY_ATTACK.isDown();
        while (ClientKeyMappings.HEAVY_ATTACK.consumeClick()) {
            // edges are taken from isDown(); clicks are only drained
        }
        if (heavyDown && !heavyWasDown && canAct) {
            request(minecraft, player, local, CombatRequest.HEAVY_START, now, weapon);
        } else if (!heavyDown && heavyWasDown && local != null) {
            request(minecraft, player, local, CombatRequest.HEAVY_RELEASE, now, weapon);
        }
        heavyWasDown = heavyDown;

        while (ClientKeyMappings.DODGE.consumeClick()) {
            if (canAct) {
                boolean wasGuarding = isUsingWeapon(player);
                if (request(minecraft, player, local, CombatRequest.DODGE, now, weapon) && wasGuarding
                        && minecraft.gameMode != null) {
                    // Tell the server the guard ended (vanilla release packet) right before the dodge arrives.
                    minecraft.gameMode.releaseUsingItem(player);
                }
            }
        }
        while (ClientKeyMappings.WEAPON_SKILL.consumeClick()) {
            if (canAct) {
                request(minecraft, player, local, CombatRequest.SKILL, now, weapon);
            }
        }

        if (local != null) {
            for (CombatEvent event : local.tick(now)) {
                if (event instanceof CombatEvent.ActionStarted start) {
                    onActionStarted(player, start);
                }
            }
        }
    }

    /** @return whether the request was sent (the prediction accepted or buffered it) */
    private static boolean request(Minecraft minecraft, LocalPlayer player, Combatant local, CombatRequest request,
                                   long now, WeaponProfile weapon) {
        boolean restricted = player.isPassenger() || player.isFallFlying();
        RequestOutcome outcome = local.submit(request, now, weapon, restricted);
        if (outcome == RequestOutcome.ACCEPTED || outcome == RequestOutcome.BUFFERED) {
            ERNetwork.sendToServer(new CombatActionPacket(request, ClientCombatState.nextSequence()));
            return true;
        }
        if (request != CombatRequest.LIGHT && request != CombatRequest.HEAVY_RELEASE) {
            outcome.messageKey().ifPresent(key -> player.displayClientMessage(Component.translatable(key), true));
        }
        return false;
    }

    /** Predicted start of an action: swing, sound and (for dodges) the movement impulse, all without waiting. */
    private static void onActionStarted(LocalPlayer player, CombatEvent.ActionStarted start) {
        ActionKind kind = start.action().kind();
        switch (kind) {
            case DODGE -> {
                applyDodgeImpulse(player);
                player.playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.5F, 1.6F);
            }
            case LIGHT -> {
                player.swing(InteractionHand.MAIN_HAND);
                boolean thrust = start.action().frames().get(0).shape().kind() == HitShape.Kind.LINE;
                player.playSound(thrust ? SoundEvents.TRIDENT_THROW : SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F,
                        1.0F + 0.08F * Math.max(0, start.comboStep()));
            }
            case HEAVY -> {
                player.swing(InteractionHand.MAIN_HAND);
                player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 0.7F);
            }
            case SKILL -> {
                player.swing(InteractionHand.MAIN_HAND);
                player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.55F);
            }
        }
    }

    /** Dodge towards the movement input (relative to the facing), backwards without input. */
    private static void applyDodgeImpulse(LocalPlayer player) {
        double strafe = player.input.leftImpulse;
        double forward = player.input.forwardImpulse;
        if (Math.abs(strafe) < 1.0e-3 && Math.abs(forward) < 1.0e-3) {
            forward = -1.0;
        }
        double length = Math.sqrt(strafe * strafe + forward * forward);
        strafe /= length;
        forward /= length;
        double yaw = Math.toRadians(player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double x = strafe * cos - forward * sin;
        double z = forward * cos + strafe * sin;
        double speed = player.onGround() ? DODGE_SPEED_GROUND : DODGE_SPEED_AIR;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(x * speed, motion.y, z * speed);
    }

    private static boolean isUsingWeapon(Player player) {
        return player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && player.getUseItem().getItem() instanceof WeaponItem;
    }

    /** The server refused our guard: stop the vanilla item use we predicted (sends the vanilla release packet). */
    static void dropPredictedGuard() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null && minecraft.gameMode != null && isUsingWeapon(player)) {
            minecraft.gameMode.releaseUsingItem(player);
        }
    }

    /** True while the player holds an ETERNAL REALM weapon (vanilla attack/mining input is suppressed). */
    public static boolean holdsWeapon() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && WeaponItem.profileOf(player.getMainHandItem()) != null;
    }

    /** Client side of {@code GuardGate}: the predicted state decides like the server will. */
    public static boolean tryStartGuard(Player player, WeaponProfile weapon) {
        Combatant local = ClientCombatState.combatant();
        return local != null && local.startGuard(ClientCombatState.tick(), weapon);
    }
}
