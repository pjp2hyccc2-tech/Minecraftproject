package com.huntcraft.item;

import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * BBQ spit: hold right-click with raw meat in the inventory while the roasting tune plays,
 * release in the perfect window (~4.6s - 5.3s) for a well-done steak.
 */
public class BbqSpitItem extends Item {
    public static final int PERFECT_FROM = 92;
    public static final int PERFECT_TO = 106;

    public BbqSpitItem(Properties props) {
        super(props);
    }

    private static int findMeat(Player p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(HCItems.RAW_MEAT.get())) return i;
        }
        return -1;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (findMeat(player) < 0) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.huntcraft.need_meat"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), HCSounds.MEAT_ROAST.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int used = getUseDuration(stack) - remaining;
        if (level.isClientSide && used % 3 == 0) {
            Vec3 look = entity.getLookAngle();
            Vec3 p = entity.position().add(look.x * 0.8, 0.9, look.z * 0.8);
            level.addParticle(used > PERFECT_TO ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, p.x, p.y, p.z, 0, 0.03, 0);
            if (used > 60) level.addParticle(ParticleTypes.SMOKE, p.x, p.y + 0.3, p.z, 0, 0.05, 0);
        }
        if (used > 160 && entity instanceof Player player) player.releaseUsingItem();
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player) || level.isClientSide) return;
        int used = getUseDuration(stack) - timeLeft;
        int slot = findMeat(player);
        if (slot < 0) return;
        if (used < 25) return; // cancelled
        player.getInventory().getItem(slot).shrink(1);
        Item result;
        String msg;
        if (used < PERFECT_FROM) {
            result = HCItems.RARE_STEAK.get();
            msg = "message.huntcraft.meat_rare";
        } else if (used <= PERFECT_TO) {
            result = HCItems.WELL_DONE_STEAK.get();
            msg = "message.huntcraft.meat_done";
            level.playSound(null, player.getX(), player.getY(), player.getZ(), HCSounds.MEAT_DONE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            result = HCItems.BURNT_MEAT.get();
            msg = "message.huntcraft.meat_burnt";
        }
        player.displayClientMessage(Component.translatable(msg).withStyle(used >= PERFECT_FROM && used <= PERFECT_TO ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
        ItemStack out = new ItemStack(result);
        if (!player.getInventory().add(out)) player.drop(out, false);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.huntcraft.bbq_spit.desc").withStyle(ChatFormatting.GRAY));
    }
}
