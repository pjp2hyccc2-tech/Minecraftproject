package com.huntcraft.item;

import com.huntcraft.core.HunterSkills;
import com.huntcraft.core.SkillType;
import com.huntcraft.data.HunterData;
import com.huntcraft.registry.HCEffects;
import com.huntcraft.registry.HCSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

/** Potions, drinks and food. Each instance is configured by a factory method. */
public class HCConsumableItem extends Item {
    public enum DrinkKind { HOT, COOL, DEMON, ARMOR }

    private final UseAnim anim;
    private final int useTicks;
    private final BiConsumer<Player, Level> effect;

    public HCConsumableItem(Properties props, UseAnim anim, int useTicks, BiConsumer<Player, Level> effect) {
        super(props);
        this.anim = anim;
        this.useTicks = useTicks;
        this.effect = effect;
    }

    // ------------------------------------------------------------------ factories
    public static HCConsumableItem potion(float heal) {
        return new HCConsumableItem(new Properties().stacksTo(10), UseAnim.DRINK, 28, (p, l) -> {
            float h = heal * (1.0F + 0.25F * HunterSkills.level(p, SkillType.RECOVERY_UP));
            p.heal(h);
            particles(p, true);
        });
    }

    public static HCConsumableItem maxPotion() {
        return new HCConsumableItem(new Properties().stacksTo(2), UseAnim.DRINK, 32, (p, l) -> {
            p.setHealth(p.getMaxHealth());
            p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 120, 1));
            HunterData d = HunterData.get(p);
            d.restoreMaxStamina(150);
            particles(p, true);
        });
    }

    public static HCConsumableItem antidote() {
        return new HCConsumableItem(new Properties().stacksTo(10), UseAnim.DRINK, 20, (p, l) -> {
            p.removeEffect(HCEffects.POISON.get());
            p.removeEffect(MobEffects.POISON);
            particles(p, false);
        });
    }

    public static HCConsumableItem energyDrink() {
        return new HCConsumableItem(new Properties().stacksTo(10), UseAnim.DRINK, 20, (p, l) -> {
            p.removeEffect(HCEffects.SLEEP.get());
            p.removeEffect(HCEffects.PARALYSIS.get());
            HunterData d = HunterData.get(p);
            d.stamina = d.maxStamina;
            d.exhausted = false;
            d.markDirty();
            particles(p, false);
        });
    }

    public static HCConsumableItem drink(DrinkKind kind) {
        return new HCConsumableItem(new Properties().stacksTo(10), UseAnim.DRINK, 24, (p, l) -> {
            switch (kind) {
                case HOT -> p.addEffect(new MobEffectInstance(HCEffects.HOT_DRINK.get(), 20 * 60 * 8, 0));
                case COOL -> p.addEffect(new MobEffectInstance(HCEffects.COOL_DRINK.get(), 20 * 60 * 8, 0));
                case DEMON -> p.addEffect(new MobEffectInstance(HCEffects.DEMONDRUG.get(), 20 * 60 * 10, 0));
                case ARMOR -> p.addEffect(new MobEffectInstance(HCEffects.ARMORSKIN.get(), 20 * 60 * 10, 0));
            }
            particles(p, false);
        });
    }

    /** Food restoring max stamina (and optionally health). */
    public static HCConsumableItem food(int stamina, float heal) {
        return new HCConsumableItem(new Properties().stacksTo(10), UseAnim.EAT, 32, (p, l) -> {
            HunterData.get(p).restoreMaxStamina(stamina);
            if (heal > 0) p.heal(heal);
            p.getFoodData().eat(Math.max(1, stamina / 10), 0.6F);
        });
    }

    private static void particles(Player p, boolean heal) {
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles(heal ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.EFFECT, p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.4, 0.5, 0.4, 0.05);
        }
    }

    // ------------------------------------------------------------------ item behaviour
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player p) {
            if (!level.isClientSide) {
                effect.accept(p, level);
                if (anim == UseAnim.DRINK) level.playSound(null, p.getX(), p.getY(), p.getZ(), HCSounds.DRINK.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            if (!p.getAbilities().instabuild) stack.shrink(1);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return useTicks;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return anim;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
