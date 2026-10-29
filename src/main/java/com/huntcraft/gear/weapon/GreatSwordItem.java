package com.huntcraft.gear.weapon;

import com.huntcraft.core.WeaponClass;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 大剣 Great Sword: slow, heavy swings. Hold right click to charge (3 levels, Focus speeds it up, overcharging past
 * level 3 drops back to level 2), release for a wide slam (MV x0.75 / x1.0 / x1.35). Sneak + right click guards.
 */
public class GreatSwordItem extends HunterWeaponItem {
    public static final float SLAM_MV = 1.10F;
    public static final float[] CHARGE_MULT = {0.55F, 0.75F, 1.0F, 1.35F};
    /** Ticks past level 3 before the charge is over-held. */
    public static final int OVERCHARGE = 24;

    public GreatSwordItem(WeaponLine line, Properties props) {
        super(WeaponClass.GREAT_SWORD, line, props);
    }

    @Override
    public float guardPower() {
        return 0.6F;
    }

    @Override
    protected int[] chargeThresholds() {
        return new int[]{14, 28, 42};
    }

    /** True when the charge has been held too long (shown in the HUD, release hits like level 2). */
    public boolean isOvercharged(Player p, int used) {
        return used >= chargeTicksFor(p, 3) + OVERCHARGE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        beginUse(player, hand, player.isShiftKeyDown() ? WeaponState.MODE_GUARD : WeaponState.MODE_CHARGE);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    protected void onChargeRelease(Level level, Player p, ItemStack stack, int used, int chargeLevel) {
        if (used < 3) return;
        int lv = isOvercharged(p, used) ? 2 : chargeLevel;
        p.swing(InteractionHand.MAIN_HAND);
        if (level.isClientSide) return;
        float mv = SLAM_MV * CHARGE_MULT[lv];
        double range = 4.0 + 0.2 * lv;
        float cone = 110F + 10F * lv;
        special(p, () -> WeaponHits.cone(p, range, cone, mv, (t, ctx) -> {
            ctx.sharpnessCost = 2;
            ctx.elementMult = 1.0F + 0.15F * lv;
            ctx.statusMult = 1.0F + 0.15F * lv;
        }));
        playSound(p, WeaponSounds.SWING_HEAVY.get(), 1.0F, 0.7F);
        playSound(p, WeaponSounds.SLAM.get(), 0.4F + 0.2F * lv, 1.3F - 0.1F * lv);
        WeaponFx.sweep(p, 2.2, 3 + lv);
        WeaponFx.slam(p, WeaponHits.front(p, 2.4, 0.1), 0.5F + 0.5F * lv);
        WeaponFx.element(p, getElement(stack), WeaponHits.front(p, 2.4, 1.0), 6 + 4 * lv);
    }

    @Override
    protected void appendClassInfo(ItemStack stack, List<Component> tooltip) {
        super.appendClassInfo(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.huntcraft.weapon.charge_mv",
                Math.round(SLAM_MV * CHARGE_MULT[1] * 100), Math.round(SLAM_MV * CHARGE_MULT[2] * 100), Math.round(SLAM_MV * CHARGE_MULT[3] * 100))
                .withStyle(ChatFormatting.GRAY));
    }
}
