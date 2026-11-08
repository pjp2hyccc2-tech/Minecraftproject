package com.eternalrealm.client;

import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.CombatEvent;
import com.eternalrealm.combat.CombatRequest;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.HitShape;
import com.eternalrealm.combat.RequestOutcome;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.forge.WeaponItem;
import com.eternalrealm.network.forge.CombatActionPacket;
import com.eternalrealm.network.forge.ERNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
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
 * The server answers every request; a rejection of the latest request restores the state saved just before it.</p>
 *
 * <p>The guard is not predicted (D-035): right click sends vanilla's use packet, the server decides, and the local
 * fighter follows the "using item" state vanilla syncs back.</p>
 */
public final class ClientCombatController {
    /** Horizontal dodge impulse in blocks/tick: about 2.6 blocks on the ground, a short dash in the air. */
    private static final double DODGE_SPEED_GROUND = 1.2;
    private static final double DODGE_SPEED_AIR = 0.35;

    private static boolean heavyWasDown;
    /** Hotbar slot we last told the server about; vanilla only sends slot changes later in the tick. */
    private static int syncedSlot = -1;

    private ClientCombatController() {
    }

    public static void onTickStart() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            heavyWasDown = false;
            syncedSlot = -1;
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
            syncGuard(minecraft, player, local, weapon, now);
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
        int heavyPresses = 0;
        while (ClientKeyMappings.HEAVY_ATTACK.consumeClick()) {
            heavyPresses++;
        }
        if (heavyDown && !heavyWasDown && canAct) {
            request(minecraft, player, local, CombatRequest.HEAVY_START, now, weapon);
        } else if (!heavyDown && heavyWasDown && local != null) {
            request(minecraft, player, local, CombatRequest.HEAVY_RELEASE, now, weapon);
        } else if (!heavyDown && !heavyWasDown && heavyPresses > 0 && canAct) {
            // Pressed and released between two ticks: a tap is a minimum-charge heavy attack.
            if (request(minecraft, player, local, CombatRequest.HEAVY_START, now, weapon)) {
                request(minecraft, player, local, CombatRequest.HEAVY_RELEASE, now, weapon);
            }
        }
        heavyWasDown = heavyDown;

        while (ClientKeyMappings.DODGE.consumeClick()) {
            if (canAct) {
                request(minecraft, player, local, CombatRequest.DODGE, now, weapon);
            }
        }
        while (ClientKeyMappings.WEAPON_SKILL.consumeClick()) {
            if (canAct) {
                request(minecraft, player, local, CombatRequest.SKILL, now, weapon);
            }
        }
        for (int slot = 1; slot < Combatant.SKILL_SLOTS; slot++) {
            while (ClientKeyMappings.jobSkill(slot).consumeClick()) {
                if (canAct) {
                    request(minecraft, player, local, CombatRequest.jobSkill(slot), now, weapon);
                }
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

    /**
     * Keeps the local guard equal to vanilla's "using the weapon" state (which the server controls), and ends the guard
     * right away when the use key was released this tick, so an attack clicked in the same tick is not rejected as
     * "guarding" (vanilla would only send the release later in the tick, after our attack request).
     */
    private static void syncGuard(Minecraft minecraft, LocalPlayer player, Combatant local, WeaponProfile weapon,
                                  long now) {
        boolean usingWeapon = isUsingWeapon(player);
        if (usingWeapon && !minecraft.options.keyUse.isDown() && minecraft.gameMode != null) {
            minecraft.gameMode.releaseUsingItem(player);
            usingWeapon = false;
        }
        if (local.isGuarding() && !usingWeapon) {
            local.stopGuard();
        } else if (!local.isGuarding() && usingWeapon) {
            local.startGuard(now, weapon);
        }
    }

    /** @return whether the request was sent (the prediction accepted or buffered it) */
    private static boolean request(Minecraft minecraft, LocalPlayer player, Combatant local, CombatRequest request,
                                   long now, WeaponProfile weapon) {
        boolean restricted = player.isPassenger() || player.isFallFlying();
        boolean usingOtherItem = player.isUsingItem() && !isUsingWeapon(player);
        boolean guarding = isUsingWeapon(player);
        Combatant.Snapshot before = local.snapshot();
        RequestOutcome outcome = local.submit(request, now, weapon, restricted, usingOtherItem);
        if (outcome != RequestOutcome.ACCEPTED && outcome != RequestOutcome.BUFFERED) {
            if (request != CombatRequest.LIGHT && request != CombatRequest.HEAVY_RELEASE) {
                outcome.messageKey().ifPresent(key -> player.displayClientMessage(Component.translatable(key), true));
            }
            return false;
        }
        syncSelectedSlot(minecraft, player);
        if (request == CombatRequest.DODGE && guarding && minecraft.gameMode != null) {
            // The dodge ends the guard: tell the server (vanilla release packet) before the dodge request arrives.
            minecraft.gameMode.releaseUsingItem(player);
        }
        int sequence = ClientCombatState.nextSequence();
        ClientCombatState.rememberPrediction(sequence, before);
        ERNetwork.sendToServer(new CombatActionPacket(request, sequence));
        return true;
    }

    /**
     * Vanilla sends a changed hotbar slot only later in the tick; without this the server would judge a request sent
     * right after scrolling to a weapon with the previously held item.
     */
    private static void syncSelectedSlot(Minecraft minecraft, LocalPlayer player) {
        int selected = player.getInventory().selected;
        ClientPacketListener connection = minecraft.getConnection();
        if (selected != syncedSlot && connection != null) {
            connection.send(new ServerboundSetCarriedItemPacket(selected));
            syncedSlot = selected;
        }
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
                Jobs.skillOfAction(start.action().id()).ifPresent(skill -> {
                    for (SkillEffect effect : skill.effectsAt(SkillEffect.ON_START)) {
                        if (effect instanceof SkillEffect.Dash dash) {
                            applyDash(player, dash);
                        }
                    }
                });
            }
        }
    }

    /**
     * Skill dashes along the facing. On the ground friction leaves about 2.2× the initial speed as distance; in the
     * air there is far less friction, so the impulse is much smaller.
     */
    private static void applyDash(LocalPlayer player, SkillEffect.Dash dash) {
        double yaw = Math.toRadians(player.getYRot());
        double direction = dash.backwards() ? -1.0 : 1.0;
        double x = -Math.sin(yaw) * direction;
        double z = Math.cos(yaw) * direction;
        double speed = player.onGround() ? dash.distance() / 2.2 : dash.distance() / 9.0;
        speed = Math.min(speed, 4.0);
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(x * speed, player.onGround() ? Math.max(motion.y, 0.1) : motion.y, z * speed);
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
        double distance = ClientCombatState.stats() == null ? 1.0 : ClientCombatState.stats().dodgeDistanceFactor();
        double speed = (player.onGround() ? DODGE_SPEED_GROUND : DODGE_SPEED_AIR) * distance;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(x * speed, motion.y, z * speed);
    }

    private static boolean isUsingWeapon(Player player) {
        return player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && player.getUseItem().getItem() instanceof WeaponItem;
    }

    /** True while the player holds an ETERNAL REALM weapon (vanilla attack/mining input is suppressed). */
    public static boolean holdsWeapon() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && WeaponItem.profileOf(player.getMainHandItem()) != null;
    }

    /** Right click with a weapon uses the guard; the off-hand item is only used while sneaking. */
    public static boolean blocksOffHandUse() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && !player.isSecondaryUseActive()
                && WeaponItem.profileOf(player.getMainHandItem()) != null;
    }
}
