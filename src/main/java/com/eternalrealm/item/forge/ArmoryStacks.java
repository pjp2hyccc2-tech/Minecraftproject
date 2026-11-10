package com.eternalrealm.item.forge;

import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.item.Design;
import com.eternalrealm.item.EquipmentGenerator;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.WeaponDesign;
import com.eternalrealm.server.ServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.random.RandomGenerator;

/**
 * Item stacks of the named designs for the reward code (dungeons, lords, calamities, monster drops) and the hands of
 * the monsters that carry one. The rarity is rolled with the receiving player's luck, at least {@code floor}.
 */
public final class ArmoryStacks {
    private ArmoryStacks() {
    }

    /** A freshly rolled piece of {@code design} for {@code player} (rarity: their luck, at least {@code floor}). */
    public static ItemStack reward(ServerPlayer player, Design design, ItemRarity floor, RandomGenerator random) {
        ItemRarity rarity = EquipmentGenerator.rollRarity(luck(player), random);
        if (rarity.rank() < floor.rank()) {
            rarity = floor;
        }
        return EquipmentStacks.create(EquipmentGenerator.roll(design, rarity, random));
    }

    /** "名品を手に入れた: …" in gold, a boss weapon also to everyone on the server. */
    public static void announce(ServerPlayer player, ItemStack stack, Design design) {
        player.sendSystemMessage(Component.translatable("message.eternalrealm.armory.found", stack.getDisplayName())
                .withStyle(ChatFormatting.GOLD));
        if (design instanceof WeaponDesign weapon && weapon.isBossWeapon() && player.getServer() != null) {
            player.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
                    "message.eternalrealm.armory.boss_weapon_broadcast", player.getDisplayName(),
                    stack.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
    }

    /** Puts a plain piece of {@code design} in the monster's main hand (never dropped by vanilla). */
    public static void arm(Mob mob, WeaponDesign design) {
        mob.setItemSlot(EquipmentSlot.MAINHAND, EquipmentStacks.ofDesign(design));
        mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    /** The player's loot bonus (LUK); 0 before their fighter exists. */
    public static double luck(ServerPlayer player) {
        return ServerContext.current().flatMap(context -> context.combat().service().get(player.getUUID()))
                .map(Combatant::stats).map(CombatStats::lootBonus).orElse(0.0);
    }
}
