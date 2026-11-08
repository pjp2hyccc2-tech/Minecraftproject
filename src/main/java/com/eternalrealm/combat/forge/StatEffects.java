package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.CombatStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * Applies stat-derived values that live in vanilla attributes: the max health bonus (VIT and level).
 *
 * <p>The modifier is permanent (saved with the vanilla player data) so that vanilla restores the player's health
 * correctly on login, before the character data has loaded; it is overwritten with the current value every time the
 * character becomes ready or changes, so a stale value never survives (D-031).</p>
 */
public final class StatEffects {
    static final UUID MAX_HEALTH_MODIFIER_ID = UUID.fromString("6f6b0b8e-9b2e-4c55-9c62-6a1d2f4b3e01");
    private static final String MAX_HEALTH_MODIFIER_NAME = "eternalrealm.max_health";
    static final UUID MOVE_SPEED_MODIFIER_ID = UUID.fromString("2d8f1c44-7b39-4f3e-a0f1-5c2e9b7d4a12");
    private static final String MOVE_SPEED_MODIFIER_NAME = "eternalrealm.move_speed";

    private StatEffects() {
    }

    /** @return whether the max health changed */
    public static boolean apply(ServerPlayer player, CombatStats stats) {
        applyMoveSpeed(player, stats.moveSpeedBonus());
        return applyMaxHealth(player, stats);
    }

    /** Job/equipment movement speed bonus as a multiplicative modifier (not saved: re-applied when stats load). */
    private static void applyMoveSpeed(ServerPlayer player, double bonus) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier current = speed.getModifier(MOVE_SPEED_MODIFIER_ID);
        if (current != null && current.getAmount() == bonus) {
            return;
        }
        if (current != null) {
            speed.removeModifier(MOVE_SPEED_MODIFIER_ID);
        }
        if (bonus != 0.0) {
            speed.addTransientModifier(new AttributeModifier(MOVE_SPEED_MODIFIER_ID, MOVE_SPEED_MODIFIER_NAME, bonus,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private static boolean applyMaxHealth(ServerPlayer player, CombatStats stats) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return false;
        }
        double bonus = stats.maxHealthBonus();
        AttributeModifier current = maxHealth.getModifier(MAX_HEALTH_MODIFIER_ID);
        if (current != null && current.getAmount() == bonus
                && current.getOperation() == AttributeModifier.Operation.ADDITION) {
            return false;
        }
        if (current != null) {
            maxHealth.removeModifier(MAX_HEALTH_MODIFIER_ID);
        }
        if (bonus > 0.0) {
            maxHealth.addPermanentModifier(new AttributeModifier(MAX_HEALTH_MODIFIER_ID, MAX_HEALTH_MODIFIER_NAME,
                    bonus, AttributeModifier.Operation.ADDITION));
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        return true;
    }

    /** Current bonus on the player (0 when absent). */
    public static double currentBonus(ServerPlayer player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return 0.0;
        }
        AttributeModifier current = maxHealth.getModifier(MAX_HEALTH_MODIFIER_ID);
        return current == null ? 0.0 : current.getAmount();
    }
}
