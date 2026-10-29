package com.huntcraft.gear.weapon;

import com.huntcraft.core.WeaponClass;
import com.huntcraft.gear.weapon.action.CombatActions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * ハンマー Hammer: blunt damage that builds KO on heads. Hold right click to charge 3 levels (you can walk while
 * charging); release: Lv1 upswing, Lv2 side smash, Lv3 spinning big bang ending in a ground slam with heavy KO.
 */
public class HammerItem extends HunterWeaponItem {
    public HammerItem(WeaponLine line, Properties props) {
        super(WeaponClass.HAMMER, line, props);
    }

    @Override
    protected int[] chargeThresholds() {
        return new int[]{12, 26, 40};
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (CombatActions.isBusy(player)) return InteractionResultHolder.fail(stack);
        beginUse(player, hand, WeaponState.MODE_CHARGE);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    protected void onChargeRelease(Level level, Player p, ItemStack stack, int used, int lv) {
        if (lv <= 0 || level.isClientSide) return;
        CombatActions.start((ServerPlayer) p, "hm_charge" + lv);
    }

    @Override
    protected void appendClassInfo(ItemStack stack, List<Component> tooltip) {
        super.appendClassInfo(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.huntcraft.weapon.ko").withStyle(ChatFormatting.GRAY));
    }
}
