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
                } else if (event instanceof CombatEvent.Hit hit && hit.action().kind() == ActionKind.SKILL) {
                    Jobs.skillOfAction(hit.action().id()).ifPresent(skill -> move(player,
                            skill.effectsAt(hit.frameIndex())));
                }
            }
        }
        announceForbiddenWeapon(player, local, weapon);
    }

    /** Weapon type last announced as unusable for the job (so the hint shows once per weapon switch). */
    private static com.eternalrealm.combat.WeaponType announcedWeapon;

    /** Switching to a weapon the job cannot use shows why it will not work (attacks would be refused anyway). */
    private static void announceForbiddenWeapon(LocalPlayer player, Combatant local, WeaponProfile weapon) {
        if (local == null || weapon == null || local.allowsWeapon(weapon)) {
            announcedWeapon = null;
            return;
        }
        if (announcedWeapon != weapon.type()) {
            announcedWeapon = weapon.type();
            player.displayClientMessage(jobWeaponMessage(weapon), true);
        }
    }

    /** "魔剣士は弓を使えません（使える武器: 片手剣・両手剣）". */
    private static Component jobWeaponMessage(WeaponProfile weapon) {
        com.eternalrealm.job.JobType job = ClientCharacterState.snapshot()
                .map(snapshot -> com.eternalrealm.job.JobType.fromStored(snapshot.jobId()))
                .orElse(com.eternalrealm.job.JobType.NONE);
        net.minecraft.network.chat.MutableComponent usable = Component.empty();
        boolean first = true;
        for (com.eternalrealm.combat.WeaponType type : com.eternalrealm.combat.WeaponType.values()) {
            if (Jobs.canUse(job, type)) {
                if (!first) {
                    usable.append(Component.translatable("tooltip.eternalrealm.equipment.job_separator"));
                }
                usable.append(Component.translatable(type.translationKey()));
                first = false;
            }
        }
        return Component.translatable("message.eternalrealm.combat.job_weapon_detail",
                Component.translatable(job.translationKey()), Component.translatable(weapon.type().translationKey()),
                usable).withStyle(net.minecraft.ChatFormatting.RED);
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
            if (outcome == RequestOutcome.REJECTED_JOB_WEAPON) {
                player.displayClientMessage(jobWeaponMessage(weapon), true);
            } else if (request != CombatRequest.LIGHT && request != CombatRequest.HEAVY_RELEASE) {
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
                player.playSound(com.eternalrealm.sound.ERSounds.COMBAT_DODGE.get(), 0.7F, 1.0F);
            }
            case LIGHT -> {
                player.swing(InteractionHand.MAIN_HAND);
                player.playSound(com.eternalrealm.sound.ERSounds.weaponSwing(start.weapon().type()).get(), 0.6F,
                        1.0F + 0.06F * Math.max(0, start.comboStep()));
            }
            case HEAVY -> {
                player.swing(InteractionHand.MAIN_HAND);
                player.playSound(com.eternalrealm.sound.ERSounds.SKILL_HEAVY_SLASH.get(), 0.8F, 1.0F);
            }
            case SKILL -> {
                player.swing(InteractionHand.MAIN_HAND);
                player.playSound(com.eternalrealm.sound.ERSounds.SKILL_SLASH.get(), 1.0F, 1.0F);
                Jobs.skillOfAction(start.action().id()).ifPresent(skill ->
                        move(player, skill.effectsAt(SkillEffect.ON_START)));
            }
        }
    }

    /** The movement effects of a skill moment (dashes and leaps; movement is the client's). */
    private static void move(LocalPlayer player, java.util.List<SkillEffect> effects) {
        for (SkillEffect effect : effects) {
            if (effect instanceof SkillEffect.Dash dash) {
                applyDash(player, dash);
            } else if (effect instanceof SkillEffect.Leap leap) {
                applyLeap(player, leap);
            }
        }
    }

    /** A jump of about {@code height} blocks carrying the player {@code distance} blocks forward. */
    private static void applyLeap(LocalPlayer player, SkillEffect.Leap leap) {
        double yaw = Math.toRadians(player.getYRot());
        double up = Math.min(1.6, Math.sqrt(2.0 * 0.08 * leap.height()) + 0.05);
        double airTicks = Math.max(8.0, up / 0.08 * 2.0);
        double forward = Math.min(1.2, leap.distance() / airTicks * 1.6);
        player.setDeltaMovement(-Math.sin(yaw) * forward, up, Math.cos(yaw) * forward);
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
