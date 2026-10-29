package com.huntcraft.gear.weapon;

import com.huntcraft.HuntCraft;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.HuntMonster;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Server side gameplay events of the weapon system. */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WeaponEvents {
    private WeaponEvents() {
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        WeaponState s = WeaponState.get(p);
        ItemStack main = p.getMainHandItem();
        Item held = main.getItem();
        if (held != s.lastHeld) {
            if (s.lastHeld instanceof HunterWeaponItem old) old.onUnequip(p, s);
            s.resetWeaponState();
            if (s.sharpenLeft > 0 && s.sharpenSlot != Sharpening.OFFHAND && s.sharpenSlot != p.getInventory().selected
                    && Sharpening.stackAt(p, s.sharpenSlot).isEmpty()) {
                Sharpening.cancel(p, s);
            }
            s.lastHeld = held;
        }
        runScheduled(p, s);
        Sharpening.tick(p, s);
        if (held instanceof HunterWeaponItem w) {
            try {
                w.serverTick(p, main, s);
            } catch (Exception e) {
                HuntCraft.LOGGER.error("Weapon tick failed", e);
            }
        }
        DualBladesItem.updateSpeed(p, s, held instanceof DualBladesItem);
        HornSongs.tick(p, s);
        if (s.syncCooldown > 0) s.syncCooldown--;
        if (s.dirty && s.syncCooldown == 0) {
            s.dirty = false;
            s.syncCooldown = 2;
            WeaponNet.sync(p, s);
        }
    }

    private static void runScheduled(ServerPlayer p, WeaponState s) {
        if (s.scheduled.isEmpty()) return;
        long now = p.level().getGameTime();
        List<WeaponState.Scheduled> due = new ArrayList<>();
        Iterator<WeaponState.Scheduled> it = s.scheduled.iterator();
        while (it.hasNext()) {
            WeaponState.Scheduled sc = it.next();
            if (sc.due() <= now) {
                due.add(sc);
                it.remove();
            }
        }
        for (WeaponState.Scheduled sc : due) {
            try {
                sc.action().accept(p);
            } catch (Exception e) {
                HuntCraft.LOGGER.error("Weapon action failed", e);
            }
        }
    }

    /** Foresight dodge detection (hits cancelled by i-frames) and guard detection for the lance counter. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        boolean fromMonster = event.getSource().getEntity() instanceof HuntMonster;
        if (event.isCanceled()) {
            if (fromMonster || event.getSource().getDirectEntity() != null) LongSwordItem.markDodged(p);
            return;
        }
        if (fromMonster && HunterData.get(p).guardPower > 0) {
            WeaponState s = WeaponState.get(p);
            s.lastGuardHit = p.level().getGameTime();
            WeaponFx.send(p, net.minecraft.core.particles.ParticleTypes.CRIT, WeaponHits.front(p, 0.8, 1.2), 8, 0.3, 0.3, 0.3, 0.3);
        }
    }

    /** Defense Up song. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            float m = HornSongs.defenseMultiplier(p);
            if (m < 1.0F) event.setAmount(event.getAmount() * m);
        }
    }

    /** No attacking while sharpening. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (!p.level().isClientSide && Sharpening.isSharpening(p)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        Player p = event.getEntity();
        if (!p.level().isClientSide && Sharpening.isSharpening(p)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            WeaponState s = WeaponState.get(p);
            s.resetWeaponState();
            s.archdemon = false;
            s.demonGauge = 0;
            s.spirit = 0;
            s.spiritLevel = 0;
            Sharpening.cancel(p, s);
            DualBladesItem.updateSpeed(p, s, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            DualBladesItem.updateSpeed(p, WeaponState.get(p), false);
            WeaponState.removeServer(p);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) WeaponState.get(p).markDirty();
    }
}
