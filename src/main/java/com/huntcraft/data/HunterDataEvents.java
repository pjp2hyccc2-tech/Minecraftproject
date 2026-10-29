package com.huntcraft.data;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HCDimensions;
import com.huntcraft.core.HunterSkills;
import com.huntcraft.core.SkillType;
import com.huntcraft.registry.HCEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Capability attach / clone / sync and the stamina simulation. */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HunterDataEvents {
    private HunterDataEvents() {
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            HCCapabilities.Provider provider = new HCCapabilities.Provider();
            event.addCapability(HCCapabilities.HUNTER_ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        HunterData oldData = HunterData.get(original);
        HunterData newData = HunterData.get(event.getEntity());
        newData.copyFrom(oldData);
        if (event.isWasDeath()) {
            newData.stamina = newData.maxStamina;
        }
        newData.markDirty();
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) HunterData.get(sp).syncTo(sp);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) HunterData.get(sp).syncTo(sp);
    }

    @SubscribeEvent
    public static void changeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) HunterData.get(sp).syncTo(sp);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        HunterData d = HunterData.get(player);
        if (d.iframes > 0) d.iframes--;
        if (d.dodgeTicks > 0) d.dodgeTicks--;
        if (d.dodgeCooldown > 0) d.dodgeCooldown--;
        if (d.guardTicksLeft > 0) {
            d.guardTicksLeft--;
        } else {
            d.guardPower = 0;
        }
        if (d.sharpenTicks > 0) d.sharpenTicks--;

        // ---- stamina
        float before = d.stamina;
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.0025;
        if (player.isCreative() || player.isSpectator()) {
            d.stamina = d.maxStamina;
            d.exhausted = false;
        } else if (player.isSprinting() && moving) {
            float cost = 0.30F * (1.0F - 0.1F * HunterSkills.level(player, SkillType.CONSTITUTION));
            d.stamina = Math.max(0, d.stamina - cost);
            d.staminaDelay = 12;
            if (d.stamina <= 0) {
                d.exhausted = true;
                d.exhaustTicks = 50;
            }
        } else if (d.staminaDelay > 0) {
            d.staminaDelay--;
        } else if (d.stamina < d.maxStamina) {
            float regen = 0.9F * (1.0F + 0.25F * HunterSkills.level(player, SkillType.STAMINA_SURGE));
            if (player.hasEffect(HCEffects.ICEBLIGHT.get())) regen *= 0.5F;
            if (player.hasEffect(HCEffects.WATERBLIGHT.get())) regen *= 0.5F;
            if (d.exhausted) regen *= 0.6F;
            d.stamina = Math.min(d.maxStamina, d.stamina + regen);
        }
        if (d.exhausted) {
            player.setSprinting(false);
            if (--d.exhaustTicks <= 0 && d.stamina > d.maxStamina * 0.25F) d.exhausted = false;
            if (player.tickCount % 20 == 0) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false));
        }
        // max stamina slowly decays while on a hunt (restored by meat / rations)
        if (HCDimensions.isQuestMap(player.level()) && player.tickCount % 400 == 0 && d.maxStamina > HunterData.STAMINA_FLOOR) {
            d.maxStamina = Math.max(HunterData.STAMINA_FLOOR, d.maxStamina - 1.0F);
            d.stamina = Math.min(d.stamina, d.maxStamina);
            d.markDirty();
        }
        if (d.consumeDirty()) {
            d.syncTo(player);
        } else if ((Math.abs(d.lastSentStamina - d.stamina) > 0.01F || d.lastSentExhausted != d.exhausted) && player.tickCount % 3 == 0) {
            d.syncStamina(player);
        }
    }
}
