package com.eternalrealm.client;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.CombatRules;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.network.forge.PlayerActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What every visible player is doing right now, for arm and weapon animations: the local player from its predicted
 * fighter, other players from {@link PlayerActionPacket}s. Client thread only; times are client combat ticks.
 */
public final class CombatAnimations {
    /** Which motion an action looks like. */
    public enum Motion {
        SLASH_RIGHT,
        SLASH_LEFT,
        OVERHEAD,
        THRUST,
        SPIN,
        CAST,
        SHOOT,
        CHARGE,
        DODGE
    }

    /**
     * @param start    client tick the action started
     * @param duration length in ticks
     */
    public record Anim(Motion motion, WeaponType weapon, long start, int duration) {
        /** Progress in [0, 1]. */
        public float progress(long tick, float partialTick) {
            return Math.max(0.0F, Math.min(1.0F, (tick - start + partialTick) / Math.max(1, duration)));
        }
    }

    private static final Map<Integer, Anim> REMOTE = new HashMap<>();

    private CombatAnimations() {
    }

    public static void onRemoteAction(PlayerActionPacket packet) {
        WeaponProfile weapon = weaponOfAction(packet.actionId());
        WeaponType type = weapon == null ? WeaponType.ONE_HANDED_SWORD : weapon.type();
        Motion motion = packet.charging() ? Motion.CHARGE : motionOf(packet.actionId(), packet.comboStep(), type);
        REMOTE.put(packet.entityId(), new Anim(motion, type, ClientCombatState.tick(), packet.duration()));
        if (REMOTE.size() > 256) {
            REMOTE.values().removeIf(anim -> ClientCombatState.tick() - anim.start() > anim.duration() + 40);
        }
    }

    /** The animation of {@code entity} at the current tick, if it is acting. */
    public static Optional<Anim> of(LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = ClientCombatState.tick();
        if (entity == minecraft.player) {
            return local(now);
        }
        Anim anim = REMOTE.get(entity.getId());
        if (anim == null || now - anim.start() > anim.duration()) {
            return Optional.empty();
        }
        return Optional.of(anim);
    }

    private static Optional<Anim> local(long now) {
        Combatant fighter = ClientCombatState.combatant();
        if (fighter == null || fighter.weapon() == null) {
            return Optional.empty();
        }
        WeaponType type = fighter.weapon().type();
        long chargeStart = fighter.chargeStartTick();
        if (chargeStart >= 0) {
            return Optional.of(new Anim(Motion.CHARGE, type, chargeStart, fighter.weapon().heavy().maxChargeTicks()));
        }
        Optional<ActionDefinition> action = fighter.currentAction();
        if (action.isEmpty()) {
            return Optional.empty();
        }
        long start = fighter.actionStartTick();
        int duration = (int) Math.max(1L, fighter.actionEndTick() - start);
        return Optional.of(new Anim(motionOf(action.get().id(), fighter.currentComboStep(), type), type, start, duration));
    }

    /** Charge progress of the local player in [0, 1] (0 when not charging). */
    public static float localChargeRatio() {
        Combatant fighter = ClientCombatState.combatant();
        if (fighter == null || fighter.weapon() == null || fighter.chargeStartTick() < 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float) fighter.chargeTicks(ClientCombatState.tick())
                / fighter.weapon().heavy().maxChargeTicks());
    }

    static Motion motionOf(String actionId, int comboStep, WeaponType weapon) {
        if (CombatRules.DODGE.id().equals(actionId)) {
            return Motion.DODGE;
        }
        Optional<com.eternalrealm.item.WeaponArt> art = com.eternalrealm.item.WeaponArts.byId(actionId);
        if (art.isPresent()) {
            return switch (art.get().motion()) {
                case OVERHEAD -> Motion.OVERHEAD;
                case THRUST -> Motion.THRUST;
                case SPIN -> Motion.SPIN;
                case CAST -> Motion.CAST;
                case SHOOT -> Motion.SHOOT;
            };
        }
        if (weapon == WeaponType.BOW) {
            return Motion.SHOOT;
        }
        if (weapon == WeaponType.STAFF) {
            return actionId.equals("arcane_nova") ? Motion.SPIN : Motion.CAST;
        }
        if (Jobs.skill(actionId).isPresent()) {
            String id = actionId;
            if (id.contains("thousand") || id.contains("judgment") || id.contains("sanctuary")) {
                return Motion.SPIN;
            }
            if (id.contains("heal") || id.contains("blessing") || id.contains("war_cry") || id.contains("iron_wall")
                    || id.contains("smoke") || id.contains("flame_blade") || id.startsWith("mage_")
                    || id.contains("holy_light")) {
                return Motion.CAST;
            }
            if (id.contains("step") || id.contains("dimension") || id.contains("spiral")) {
                return Motion.THRUST;
            }
            return Motion.OVERHEAD;
        }
        if (actionId.equals("whirlwind") || actionId.equals("blade_dance")) {
            return Motion.SPIN;
        }
        if (weapon == WeaponType.SPEAR) {
            return Motion.THRUST;
        }
        if (actionId.endsWith("_heavy") || actionId.equals("ground_rend")) {
            return Motion.OVERHEAD;
        }
        if (comboStep >= 0) {
            WeaponProfile profile = WeaponProfiles.of(weapon);
            boolean finisher = comboStep == profile.combo().size() - 1 && profile.combo().size() > 2;
            if (finisher && weapon != WeaponType.DUAL_BLADES) {
                return Motion.OVERHEAD;
            }
            return comboStep % 2 == 0 ? Motion.SLASH_RIGHT : Motion.SLASH_LEFT;
        }
        return Motion.OVERHEAD;
    }

    private static WeaponProfile weaponOfAction(String actionId) {
        for (WeaponProfile profile : WeaponProfiles.all()) {
            if (profile.skill().id().equals(actionId) || profile.heavy().strike().id().equals(actionId)) {
                return profile;
            }
            for (ActionDefinition step : profile.combo()) {
                if (step.id().equals(actionId)) {
                    return profile;
                }
            }
        }
        Optional<com.eternalrealm.item.WeaponArt> art = com.eternalrealm.item.WeaponArts.byId(actionId);
        if (art.isPresent()) {
            return WeaponProfiles.of(art.get().type());
        }
        return Jobs.skill(actionId).map(skill -> Jobs.get(skill.job()).starterWeapon())
                .map(WeaponProfiles::of).orElse(null);
    }

    public static boolean isDodge(ActionKind kind) {
        return kind == ActionKind.DODGE;
    }

    public static void clear() {
        REMOTE.clear();
    }
}
