package com.huntcraft.gear.weapon;

import com.huntcraft.core.WeaponClass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * ランス Lance: hold right click to guard (strongest guard); left click while guarding = counter poke (stronger right
 * after blocking an attack). Sneak + right click = charge dash that hits repeatedly and ends with a finishing thrust.
 */
public class LanceItem extends HunterWeaponItem {
    public static final int DASH_TICKS = 36;

    public LanceItem(WeaponLine line, Properties props) {
        super(WeaponClass.LANCE, line, props);
    }

    @Override
    public float guardPower() {
        return 0.85F;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        WeaponState s = WeaponState.get(p);
        if (p.isShiftKeyDown()) {
            if (s.dashTicks > 0) return InteractionResultHolder.fail(stack);
            if (!useStamina(p, 20.0F)) {
                if (!level.isClientSide) p.displayClientMessage(Component.translatable("message.huntcraft.weapon.no_stamina").withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(stack);
            }
            s.dashTicks = DASH_TICKS;
            s.markDirty();
            if (!level.isClientSide) playSound(p, WeaponSounds.DASH.get(), 1.0F, 1.0F);
            p.getCooldowns().addCooldown(this, DASH_TICKS + 16);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        beginUse(p, hand, WeaponState.MODE_GUARD);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void guardAttack(ServerPlayer p, ItemStack stack) {
        WeaponState s = WeaponState.get(p);
        if (s.counterCooldown > 0) return;
        s.counterCooldown = 12;
        boolean counter = p.level().getGameTime() - s.lastGuardHit <= 30;
        float mv = counter ? 0.55F : 0.36F;
        special(p, () -> WeaponHits.cone(p, 4.4, 36F, mv, (t, ctx) -> ctx.sharpnessCost = counter ? 2 : 1));
        playSound(p, counter ? WeaponSounds.COUNTER.get() : WeaponSounds.SWING_LIGHT.get(), 1.0F, counter ? 1.0F : 0.8F);
        WeaponFx.send(p, counter ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, WeaponHits.front(p, 2.6, 1.3), counter ? 16 : 6, 0.2, 0.2, 0.2, 0.25);
        if (counter) s.lastGuardHit = -1000;
    }

    @Override
    public void serverTick(ServerPlayer p, ItemStack stack, WeaponState s) {
        if (s.counterCooldown > 0) s.counterCooldown--;
        if (s.dashTicks > 0) {
            s.dashTicks--;
            boolean tired = !p.getAbilities().instabuild && com.huntcraft.data.HunterData.get(p).stamina <= 0.5F;
            if (!p.getAbilities().instabuild) com.huntcraft.data.HunterData.get(p).consumeStamina(0.6F);
            if (s.dashTicks % 4 == 0 && s.dashTicks > 0) {
                special(p, () -> WeaponHits.cone(p, 2.8, 80F, 0.16F, null));
                WeaponFx.send(p, ParticleTypes.CLOUD, p.position().add(0, 0.1, 0), 3, 0.3, 0.02, 0.3, 0.02);
                if (s.dashTicks % 8 == 0) playSound(p, WeaponSounds.DASH.get(), 0.6F, 1.1F);
            }
            if (tired && s.dashTicks > 2) s.dashTicks = 0;
            if (s.dashTicks == 0) finisher(p);
        }
    }

    private void finisher(ServerPlayer p) {
        special(p, () -> WeaponHits.cone(p, 4.0, 50F, 0.60F, (t, ctx) -> ctx.sharpnessCost = 2));
        playSound(p, WeaponSounds.COUNTER.get(), 1.0F, 0.8F);
        WeaponFx.send(p, ParticleTypes.ENCHANTED_HIT, WeaponHits.front(p, 2.4, 1.2), 14, 0.3, 0.3, 0.3, 0.25);
        WeaponFx.sweep(p, 2.0, 2);
    }

    @Override
    public void onUnequip(ServerPlayer p, WeaponState s) {
        s.dashTicks = 0;
    }

    @Override
    protected void appendClassInfo(ItemStack stack, List<Component> tooltip) {
        super.appendClassInfo(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.huntcraft.weapon.counter").withStyle(ChatFormatting.GRAY));
    }
}
