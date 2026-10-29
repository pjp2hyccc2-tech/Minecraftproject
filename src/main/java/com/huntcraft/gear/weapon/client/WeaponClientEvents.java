package com.huntcraft.gear.weapon.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.gear.weapon.HunterWeaponItem;
import com.huntcraft.gear.weapon.LanceItem;
import com.huntcraft.gear.weapon.SwordShieldItem;
import com.huntcraft.gear.weapon.WeaponHits;
import com.huntcraft.gear.weapon.WeaponNet;
import com.huntcraft.gear.weapon.WeaponState;
import com.huntcraft.network.HCNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client input of the weapon system (forge bus): air swings, guard attacks, sharpen key, lance dash, movement. */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WeaponClientEvents {
    private WeaponClientEvents() {
    }

    /** The attack key with a hunter weapon never mines or swings vanilla style: it starts a combat action. */
    @SubscribeEvent
    public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        WeaponState s = WeaponState.get(p);
        if (s.sharpenLeft > 0 && (event.isAttack() || event.isUseItem())) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (!event.isAttack()) return;
        ItemStack main = p.getMainHandItem();
        if (!(main.getItem() instanceof HunterWeaponItem)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        p.resetAttackStrengthTicker();
        CompoundTag t = new CompoundTag();
        t.putBoolean("sneak", p.isShiftKeyDown());
        HCNetwork.sendToServer(WeaponNet.SWING, t);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        WeaponState s = WeaponState.get(p);
        if (event.phase == TickEvent.Phase.START) {
            // vanilla ignores attack clicks while an item is in use: lance / sword & shield attack out of guard
            if (mc.screen == null && p.isUsingItem() && s.useMode == WeaponState.MODE_GUARD
                    && (p.getUseItem().getItem() instanceof LanceItem || p.getUseItem().getItem() instanceof SwordShieldItem)) {
                while (mc.options.keyAttack.consumeClick()) {
                    p.swing(InteractionHand.MAIN_HAND);
                    HCNetwork.sendToServer(WeaponNet.GUARD_ATTACK, new CompoundTag());
                }
            }
            return;
        }
        while (WeaponClient.SHARPEN.consumeClick()) {
            if (mc.screen == null) HCNetwork.sendToServer(WeaponNet.SHARPEN, new CompoundTag());
        }
        // lance charge dash: the client drives player movement
        if (s.dashTicks > 0) {
            s.dashTicks--;
            if (p.getMainHandItem().getItem() instanceof LanceItem && !p.isPassenger()) {
                Vec3 f = WeaponHits.flatLook(p);
                double speed = s.dashTicks > 4 ? 0.42 : 0.2;
                p.setDeltaMovement(f.x * speed, p.getDeltaMovement().y, f.z * speed);
                p.setSprinting(false);
            } else {
                s.dashTicks = 0;
            }
        }
        // local countdowns between server syncs (smooth HUD timers)
        for (int i = 0; i < s.buffTicks.length; i++) if (s.buffTicks[i] > 0) s.buffTicks[i]--;
        if (s.sharpenLeft > 0) s.sharpenLeft--;
        if (s.spiritLevelTicks > 0) s.spiritLevelTicks--;
        if (s.foresightTicks > 0) s.foresightTicks--;
    }

    /** Using an item slows the player to 20%; some weapons allow moving faster while charging / guarding. */
    @SubscribeEvent
    public static void movementInput(MovementInputUpdateEvent event) {
        Player p = event.getEntity();
        if (com.huntcraft.client.player.CombatClient.lockMovement(p)) {
            Input in = event.getInput();
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.jumping = false;
            in.up = in.down = in.left = in.right = false;
            return;
        }
        if (!p.isUsingItem()) return;
        if (p.getUsedItemHand() == InteractionHand.OFF_HAND && p.getMainHandItem().getItem() instanceof SwordShieldItem) {
            // sword & shield: walk while using an off-hand item
            Input in = event.getInput();
            in.forwardImpulse *= 2.5F;
            in.leftImpulse *= 2.5F;
            return;
        }
        if (!(p.getUseItem().getItem() instanceof HunterWeaponItem w)) return;
        WeaponState s = WeaponState.get(p);
        float mult = switch (w.weaponClass()) {
            case HAMMER -> 3.0F;
            case BOW, SWORD_AND_SHIELD -> 2.5F;
            case LANCE -> 1.6F;
            case GREAT_SWORD -> s.useMode == WeaponState.MODE_GUARD ? 1.5F : 1.0F;
            default -> 1.0F;
        };
        if (mult != 1.0F) {
            Input in = event.getInput();
            in.forwardImpulse *= mult;
            in.leftImpulse *= mult;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        WeaponState.clearClient();
    }
}
