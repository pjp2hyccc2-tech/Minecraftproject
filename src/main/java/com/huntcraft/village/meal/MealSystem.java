package com.huntcraft.village.meal;

import com.huntcraft.data.HunterData;
import com.huntcraft.village.VillageRegistry;
import com.huntcraft.village.companion.MofuData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Meal buffs: stored as {@code village.meal = {id, until(gameTime), guts}}; active for 30 minutes or until the hunter
 * returns from a quest. Attribute modifiers are transient and re-applied every second (self-healing after relog/respawn).
 */
public final class MealSystem {
    public static final long DURATION = 20L * 60L * 30L;
    private static final UUID HP_ID = UUID.fromString("7a1e2c33-5b1d-4f2e-9a61-6c0de1f00a01");
    private static final UUID DEF_ID = UUID.fromString("7a1e2c33-5b1d-4f2e-9a61-6c0de1f00a02");
    private static final UUID SPEED_ID = UUID.fromString("7a1e2c33-5b1d-4f2e-9a61-6c0de1f00a03");

    private MealSystem() {
    }

    @Nullable
    public static Meals.Meal active(Player p) {
        CompoundTag v = MofuData.village(p);
        if (!v.contains("meal", Tag.TAG_COMPOUND)) return null;
        CompoundTag m = v.getCompound("meal");
        if (p.level() != null && p.level().getGameTime() > m.getLong("until")) return null;
        return Meals.get(m.getString("id"));
    }

    public static long remainingTicks(Player p) {
        CompoundTag v = MofuData.village(p);
        if (!v.contains("meal", Tag.TAG_COMPOUND) || p.level() == null) return 0;
        return Math.max(0, v.getCompound("meal").getLong("until") - p.level().getGameTime());
    }

    public static boolean has(Player p, Meals.Skill s) {
        Meals.Meal m = active(p);
        return m != null && m.skills().contains(s);
    }

    /** Installed into HuntHooks.addAttackMultiplier. */
    public static double attackMultiplier(Player p) {
        Meals.Meal m = active(p);
        if (m == null) return 1.0;
        double k = 1.0 + m.atkPct() / 100.0;
        if (m.skills().contains(Meals.Skill.POWER)) k *= 1.05;
        return k;
    }

    /** Installed into HuntHooks.addExtraCarveProvider. */
    public static int extraCarves(Player p) {
        return has(p, Meals.Skill.CARVE) ? 1 : 0;
    }

    public static void eat(ServerPlayer p, String id) {
        Meals.Meal m = Meals.get(id);
        if (m == null) return;
        HunterData d = HunterData.get(p);
        if (d.hunterRank < m.requiredHR()) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_hr", m.requiredHR()), true);
            return;
        }
        if (!d.spendZenny(m.price())) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_zenny"), true);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putLong("until", p.level().getGameTime() + DURATION);
        tag.putBoolean("guts", false);
        MofuData.village(p).put("meal", tag);
        if (m.stamina() > 0) {
            d.maxStamina = Math.min(HunterData.STAMINA_CAP, Math.max(d.maxStamina, HunterData.BASE_MAX_STAMINA + m.stamina()));
        }
        d.stamina = d.maxStamina;
        d.exhausted = false;
        refresh(p, m);
        p.setHealth(p.getMaxHealth());
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), VillageRegistry.MEAL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.6F, 1.0F);
        p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.5, 0.6, 0.5, 0.05);
        p.displayClientMessage(Component.translatable("message.huntcraft.meal_eaten", m.displayName()).withStyle(ChatFormatting.GOLD), false);
        d.markDirty();
    }

    public static void clear(ServerPlayer p) {
        CompoundTag v = MofuData.village(p);
        if (v.contains("meal")) {
            v.remove("meal");
            HunterData.get(p).markDirty();
        }
        refresh(p, null);
    }

    public static void tick(ServerPlayer p) {
        Meals.Meal m = active(p);
        if (m != null && m.skills().contains(Meals.Skill.STAMINA)) {
            HunterData d = HunterData.get(p);
            if (d.stamina < d.maxStamina && d.staminaDelay <= 0 && !d.exhausted) d.stamina = Math.min(d.maxStamina, d.stamina + 0.35F);
        }
        if (p.tickCount % 20 != 0) return;
        refresh(p, m);
        if (m != null && m.skills().contains(Meals.Skill.REGEN) && p.tickCount % 60 == 0 && p.getHealth() < p.getMaxHealth()) p.heal(1.0F);
        CompoundTag v = MofuData.village(p);
        if (m == null && v.contains("meal")) {
            v.remove("meal");
            p.displayClientMessage(Component.translatable("message.huntcraft.meal_expired").withStyle(ChatFormatting.GRAY), false);
            HunterData.get(p).markDirty();
        }
    }

    private static void refresh(Player p, @Nullable Meals.Meal m) {
        setMod(p, Attributes.MAX_HEALTH, HP_ID, "huntcraft meal health", m == null ? 0 : m.hp(), AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.ARMOR, DEF_ID, "huntcraft meal defense", m == null ? 0 : m.def(), AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.MOVEMENT_SPEED, SPEED_ID, "huntcraft meal speed",
                m != null && m.skills().contains(Meals.Skill.SWIFT) ? 0.08 : 0, AttributeModifier.Operation.MULTIPLY_BASE);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    private static void setMod(Player p, Attribute attr, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        if (amount == 0) {
            if (cur != null) inst.removeModifier(id);
            return;
        }
        if (cur != null && cur.getAmount() == amount) return;
        if (cur != null) inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    /** Guts ("ネコの根性"): once per meal, a lethal hit leaves 1 HP. */
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (event.getAmount() < p.getHealth() || p.getHealth() <= 1.0F) return;
        if (!has(p, Meals.Skill.GUTS)) return;
        CompoundTag meal = MofuData.village(p).getCompound("meal");
        if (meal.getBoolean("guts")) return;
        meal.putBoolean("guts", true);
        event.setAmount(p.getHealth() - 1.0F);
        p.displayClientMessage(Component.translatable("message.huntcraft.meal_guts").withStyle(ChatFormatting.GOLD), true);
        p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.6, 0.4, 0.3);
        HunterData.get(p).markDirty();
    }
}
