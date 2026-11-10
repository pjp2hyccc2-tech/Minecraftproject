package com.eternalrealm.player.forge;

import com.eternalrealm.config.ERServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/**
 * Health and hunger rules of the game: there is no hunger and no natural regeneration.
 *
 * <ul>
 *     <li>The food bar is hidden (client) and kept full here every tick, so sprinting is always allowed and nobody
 *     starves. Vanilla food therefore has no use.</li>
 *     <li>Health comes back from potions (VIT makes them stronger), healing skills, revival and respawning only. The
 *     {@code naturalRegeneration} game rule is switched off when the server starts (config
 *     {@code survival.naturalRegeneration}); it also stops the peaceful-difficulty heal. The old out-of-combat VIT
 *     regeneration is gone.</li>
 * </ul>
 */
public final class Vitals {
    /** Vanilla's full food bar. */
    public static final int FULL_FOOD = 20;
    /** Vanilla's starting saturation; only matters if an admin turns natural regeneration back on. */
    public static final float SATURATION = 5.0F;

    private Vitals() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        applyRegenerationRule(event.getServer());
    }

    /** Sets the game rule from the config (also used by GameTests, which run on their own server). */
    public static void applyRegenerationRule(MinecraftServer server) {
        server.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION)
                .set(ERServerConfig.NATURAL_REGENERATION.get(), server);
    }

    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide()) {
            keepFed(event.player);
        }
    }

    /** Refills food and saturation; the client's copy follows through vanilla's health packet. */
    public static void keepFed(Player player) {
        FoodData food = player.getFoodData();
        if (food.getFoodLevel() < FULL_FOOD) {
            food.setFoodLevel(FULL_FOOD);
        }
        if (food.getSaturationLevel() < SATURATION) {
            food.setSaturation(SATURATION);
        }
    }
}
