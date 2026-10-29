package com.huntcraft.gear.armor;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HCEvents;
import com.huntcraft.core.HuntHooks;
import com.huntcraft.core.HunterSkills;
import com.huntcraft.core.Rank;
import com.huntcraft.core.SkillType;
import com.huntcraft.data.HunterData;
import com.huntcraft.registry.HCEffects;
import com.huntcraft.registry.HCItems;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Attribute-based skills (health / defense boost, heat & cold immunity) and charm drops from mining. */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArmorEvents {
    private static final UUID HEALTH_UUID = UUID.fromString("5d3b1a52-8c7e-4f0b-9b2a-0c1e7f4a6b01");
    private static final UUID DEFENSE_UUID = UUID.fromString("5d3b1a52-8c7e-4f0b-9b2a-0c1e7f4a6b02");

    private ArmorEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player p = event.player;
        if (p.tickCount % 5 != 0) return;
        applySkillAttributes(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        ArmorSkills.invalidate(event.getEntity());
        applySkillAttributes(event.getEntity());
    }

    /** HEALTH_BOOST (+4 max HP per level), DEFENSE_BOOST (+1 armor per level), HEAT_COLD_RES (drink effects). */
    public static void applySkillAttributes(Player p) {
        int hb = HunterSkills.level(p, SkillType.HEALTH_BOOST);
        setModifier(p, Attributes.MAX_HEALTH, HEALTH_UUID, "HuntCraft health boost", hb * 4.0);
        int db = HunterSkills.level(p, SkillType.DEFENSE_BOOST);
        setModifier(p, Attributes.ARMOR, DEFENSE_UUID, "HuntCraft defense boost", db * 1.0);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        if (HunterSkills.level(p, SkillType.HEAT_COLD_RES) > 0) {
            refreshHidden(p, HCEffects.HOT_DRINK.get());
            refreshHidden(p, HCEffects.COOL_DRINK.get());
        }
    }

    private static void setModifier(Player p, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        if (amount <= 0) {
            if (cur != null) inst.removeModifier(id);
            return;
        }
        if (cur != null && Math.abs(cur.getAmount() - amount) < 1.0E-6) return;
        if (cur != null) inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
    }

    private static void refreshHidden(Player p, MobEffect effect) {
        MobEffectInstance cur = p.getEffect(effect);
        if (cur == null || cur.getDuration() < 40) {
            p.addEffect(new MobEffectInstance(effect, 100, 0, true, false, false));
        }
    }

    // ------------------------------------------------------------------ charms from mining
    public static boolean isOre(ItemStack stack) {
        Item i = stack.getItem();
        return i == HCItems.EARTH_CRYSTAL.get() || i == HCItems.AZURITE_ORE.get() || i == HCItems.CRIMSONITE_ORE.get()
                || i == HCItems.NOVACRYSTAL.get() || i == HCItems.LIGHTCRYSTAL.get() || i == HCItems.ICE_CRYSTAL.get();
    }

    @SubscribeEvent
    public static void onGather(HCEvents.GatherEvent event) {
        Player p = event.getPlayer();
        if (p == null || p.level().isClientSide || event.getStack().isEmpty() || !isOre(event.getStack())) return;
        Rank rank = HuntHooks.questRank.rankOf(p);
        int hr = HunterData.get(p).hunterRank;
        float chance = rank == Rank.HIGH ? 0.06F : (hr >= 8 ? 0.05F : 0.04F);
        if (p.getRandom().nextFloat() >= chance) return;
        ItemStack charm = CharmItem.roll(ArmorModule.CHARM.get(), p.getRandom(), rank, hr);
        Component name = charm.getHoverName();
        if (!p.getInventory().add(charm)) p.drop(charm, false);
        p.displayClientMessage(Component.translatable("message.huntcraft.charm_found", name), false);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.1F);
    }
}
