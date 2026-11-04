package jp.satsujinki.item;

import jp.satsujinki.block.BloodSplatterBlock;
import jp.satsujinki.block.MixerGlassBlock;
import jp.satsujinki.block.SpikeBedBlock;
import jp.satsujinki.block.entity.MixerBlockEntity;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/** モップ: cleans blood within 4 blocks so the stage is ready for the next show. */
public class MopItem extends Item {
    public MopItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        int cleaned = clean(level, ctx.getClickedPos(), 4);
        Player p = ctx.getPlayer();
        level.playSound(null, ctx.getClickedPos(), ModSounds.MOP_CLEAN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (p != null) {
            p.displayClientMessage(Component.translatable("satsujinki.mop.cleaned", cleaned).withStyle(ChatFormatting.AQUA), true);
            if (cleaned > 0) ctx.getItemInHand().hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(ctx.getHand()));
        }
        return InteractionResult.CONSUME;
    }

    public static int clean(ServerLevel level, BlockPos center, int r) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 3, r))) {
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof BloodSplatterBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.SPLASH, p.getX() + 0.5D, p.getY() + 0.1D, p.getZ() + 0.5D, 4, 0.3D, 0.05D, 0.3D, 0.1D);
                n++;
            } else if (st.getBlock() instanceof MixerGlassBlock && st.getValue(MixerGlassBlock.BLOOD) > 0) {
                level.setBlock(p, st.setValue(MixerGlassBlock.BLOOD, 0), 3);
                n++;
            } else if (st.getBlock() instanceof SpikeBedBlock && st.getValue(SpikeBedBlock.BLOOD) > 0) {
                level.setBlock(p, st.setValue(SpikeBedBlock.BLOOD, 0), 3);
                n++;
            } else if (level.getBlockEntity(p) instanceof MixerBlockEntity m && m.blood > 0.0F) {
                m.blood = 0.0F;
                m.setChanged();
                level.sendBlockUpdated(p, st, st, 3);
                n++;
            }
        }
        return n;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.satsujinki.mop.desc").withStyle(ChatFormatting.GRAY));
    }
}
