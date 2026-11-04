package jp.satsujinki.item;

import jp.satsujinki.block.entity.TrapBlockEntity;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** 主催者リモコン: sneak + right-click traps to link them (max 16), right-click in the air to fire them all. */
public class HostRemoteItem extends Item {
    private static final int MAX_LINKS = 16;

    public HostRemoteItem(Properties props) {
        super(props);
    }

    private static ListTag links(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new ListTag() : tag.getList("Links", Tag.TAG_LONG);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player p = ctx.getPlayer();
        if (!(level.getBlockEntity(pos) instanceof TrapBlockEntity)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        ListTag list = links(stack);
        long key = pos.asLong();
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof LongTag lt && lt.getAsLong() == key) {
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) {
            if (list.size() >= MAX_LINKS) {
                if (p != null) p.displayClientMessage(Component.translatable("satsujinki.remote.full").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            list.add(LongTag.valueOf(key));
        }
        stack.getOrCreateTag().put("Links", list);
        if (p != null) {
            p.displayClientMessage(Component.translatable(removed ? "satsujinki.remote.unlinked" : "satsujinki.remote.linked", list.size())
                    .withStyle(removed ? ChatFormatting.GRAY : ChatFormatting.RED), true);
        }
        level.playSound(null, pos, ModSounds.HOST_REMOTE.get(), SoundSource.PLAYERS, 0.7F, removed ? 0.7F : 1.3F);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        ListTag list = links(stack);
        int fired = 0;
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof LongTag lt)) continue;
            BlockPos pos = BlockPos.of(lt.getAsLong());
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof TrapBlockEntity trap) {
                trap.trigger(player);
                fired++;
            }
        }
        player.displayClientMessage(Component.translatable(fired > 0 ? "satsujinki.remote.fired" : "satsujinki.remote.none", fired)
                .withStyle(ChatFormatting.DARK_RED), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.HOST_REMOTE.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.satsujinki.host_remote.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.satsujinki.host_remote.desc2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.satsujinki.host_remote.links", links(stack).size()).withStyle(ChatFormatting.RED));
    }
}
