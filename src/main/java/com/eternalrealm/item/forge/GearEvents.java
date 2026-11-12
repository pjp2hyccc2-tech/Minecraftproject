package com.eternalrealm.item.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.forge.WeaponBonus;
import com.eternalrealm.item.ConsumableEffect;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.EquipmentSlot;
import com.eternalrealm.item.Gear;
import com.eternalrealm.item.GearBonus;
import com.eternalrealm.job.forge.PlayerStats;
import com.eternalrealm.server.ServerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Connects equipment to the character: the gear reader sums the worn pieces (armor, trinkets and the held weapon's
 * affixes) into the composed stats, the weapon bonus reader adds the held weapon's bonus attack per hit, and every equipment change
 * re-derives the player's stats on the next tick.
 */
public final class GearEvents {
    private GearEvents() {
    }

    /** Installs the readers (mod construction). */
    public static void install() {
        PlayerStats.setGearReader(GearEvents::readGear);
        PlayerStats.setPerksReader(GearEvents::readPerks);
        WeaponBonus.setReader(GearEvents::weaponBonus);
    }

    /** A held weapon the player's job cannot use gives nothing (it cannot be used at all). */
    static PlayerStats.Gear readGear(ServerPlayer player) {
        List<EquipmentData> worn = worn(player);
        if (worn.isEmpty()) {
            return PlayerStats.Gear.NONE;
        }
        GearBonus bonus = Gear.sum(worn, level(player), player.getUUID());
        return new PlayerStats.Gear(bonus.stats(), bonus.percents(), bonus.modifiers());
    }

    /** Perks of the held named weapon (and its element) and of the worn set bonuses. */
    static com.eternalrealm.job.PassiveTotals readPerks(ServerPlayer player) {
        List<EquipmentData> worn = worn(player);
        return worn.isEmpty() ? com.eternalrealm.job.PassiveTotals.NONE
                : Gear.perks(worn, level(player), player.getUUID());
    }

    private static List<EquipmentData> worn(ServerPlayer player) {
        List<EquipmentData> worn = new ArrayList<>(8);
        com.eternalrealm.job.JobType job = job(player);
        EquipmentStacks.read(player.getMainHandItem()).filter(EquipmentData::isWeapon)
                .filter(data -> com.eternalrealm.job.Jobs.canUse(job, data.weaponType())).ifPresent(worn::add);
        for (EquipmentSlot slot : EquipmentSlot.armor()) {
            ItemStack stack = player.getItemBySlot(GearArmorItem.vanillaSlot(slot));
            EquipmentStacks.read(stack).filter(data -> data.slot() == slot).ifPresent(worn::add);
        }
        worn.addAll(TrinketSlots.worn(player));
        return worn;
    }

    static double weaponBonus(ServerPlayer player, ItemStack stack) {
        Optional<EquipmentData> held = EquipmentStacks.read(stack);
        return held.map(data -> Gear.weaponBonusAttack(data, level(player), player.getUUID())).orElse(0.0);
    }

    private static com.eternalrealm.job.JobType job(ServerPlayer player) {
        return ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(snapshot -> com.eternalrealm.job.JobType.fromStored(snapshot.jobId()))
                .orElse(com.eternalrealm.job.JobType.NONE);
    }

    private static int level(ServerPlayer player) {
        return ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(CharacterSnapshot::level).orElse(1);
    }

    /** Server: any change of a hand or armor slot re-derives the stats. */
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (event.getSlot().getType() == net.minecraft.world.entity.EquipmentSlot.Type.ARMOR) {
                OutfitStyles.onEquipped(player, event.getTo());
            }
            ServerContext.current().ifPresent(context -> context.combat().markStatsDirty(player.getUUID()));
        }
    }

    /** Taking damage interrupts a return-scroll channel. */
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !entity.isUsingItem() || event.getAmount() <= 0.0F) {
            return;
        }
        if (entity.getUseItem().getItem() instanceof ConsumableItem item
                && item.consumable().effect() instanceof ConsumableEffect.ReturnHome) {
            ConsumableItem.cancelChannel(entity);
        }
    }
}
