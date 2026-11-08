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

    private StatEffects() {
    }

    /** @return whether the max health changed */
    public static boolean apply(ServerPlayer player, CombatStats stats) {
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
