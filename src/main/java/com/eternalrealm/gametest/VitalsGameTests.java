package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.forge.ERGearItems;
import com.eternalrealm.player.forge.Vitals;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Health and hunger in a real server: no natural regeneration (the game rule is off and a well-fed, hurt player does
 * not heal from food), the food bar is kept full, and a health potion heals and recovers quickly out of combat.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VitalsGameTests {
    private static final String ARENA = "arena";

    private VitalsGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        Vec3 position = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        player.removeAllEffects();
        return player;
    }

    @GameTest(template = ARENA)
    public static void healthDoesNotComeBackByItselfAndFoodStaysFull(GameTestHelper helper) {
        check(!helper.getLevel().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION),
                "the naturalRegeneration game rule is still on");
        FakePlayer player = player(helper, "er_gt_vitals_food");
        player.setHealth(6.0F);
        player.getFoodData().setFoodLevel(3);
        player.getFoodData().setSaturation(0.0F);
        Vitals.keepFed(player);
        check(player.getFoodData().getFoodLevel() == Vitals.FULL_FOOD, "food not refilled: " + player.getFoodData().getFoodLevel());
        check(player.getFoodData().getSaturationLevel() >= Vitals.SATURATION, "saturation not refilled");
        // with full food vanilla would heal every 10 ticks; 30 seconds of food ticks must not heal at all
        for (int i = 0; i < 600; i++) {
            player.getFoodData().tick(player);
            Vitals.keepFed(player);
        }
        check(player.getHealth() == 6.0F, "health came back by itself: " + player.getHealth());
        check(player.getFoodData().getFoodLevel() == Vitals.FULL_FOOD, "got hungry");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void aPotionHealsAndRecoversQuicklyOutOfCombat(GameTestHelper helper) {
        FakePlayer player = player(helper, "er_gt_vitals_potion");
        Item small = ERGearItems.consumable(Consumable.HEALTH_POTION_SMALL);
        Item large = ERGearItems.consumable(Consumable.HEALTH_POTION_LARGE);
        player.getCooldowns().removeCooldown(small);
        player.getCooldowns().removeCooldown(large);
        ItemStack potions = new ItemStack(small, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, potions);
        player.setHealth(4.0F);
        float max = player.getMaxHealth();
        potions.getItem().finishUsingItem(potions, helper.getLevel(), player);
        // 15% of max health + 4 (no VIT bonus: this player has no character)
        float expected = Math.min(max, 4.0F + 0.15F * max + 4.0F);
        check(Math.abs(player.getHealth() - expected) < 0.01F, "healed to " + player.getHealth() + ", expected " + expected);
        check(potions.getCount() == 2, "the potion was not used up: " + potions.getCount());
        check(player.getCooldowns().isOnCooldown(small) && player.getCooldowns().isOnCooldown(large),
                "the potions do not share their cooldown");
        for (int i = 0; i < Consumable.CALM_HEAL_COOLDOWN_TICKS; i++) {
            player.getCooldowns().tick();
        }
        check(!player.getCooldowns().isOnCooldown(small), "out of combat the cooldown should end after "
                + Consumable.CALM_HEAL_COOLDOWN_TICKS + " ticks");
        helper.succeed();
    }
}
