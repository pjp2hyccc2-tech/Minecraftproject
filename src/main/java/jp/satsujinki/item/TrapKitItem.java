package jp.satsujinki.item;

import jp.satsujinki.game.Structures;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/** Builds a whole giant mixer, spike press or execution arena with one click. */
public class TrapKitItem extends Item {
    public enum Kind { MIXER, PRESS, ARENA }

    private final Kind kind;

    public TrapKitItem(Kind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player p = ctx.getPlayer();
        BlockPos base = ctx.getClickedFace() == Direction.UP ? ctx.getClickedPos() : ctx.getClickedPos().relative(ctx.getClickedFace()).below();
        Direction facing = p != null ? p.getDirection().getOpposite() : Direction.NORTH;
        Map<BlockPos, BlockState> plan = switch (this.kind) {
            case MIXER -> Structures.mixer(base);
            case PRESS -> Structures.press(base);
            case ARENA -> Structures.arena(base.above(), facing);
        };
        if (!Structures.canPlace(level, plan)) {
            if (p != null) p.displayClientMessage(Component.translatable("satsujinki.kit.blocked").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        Structures.build(level, plan, p);
        level.playSound(null, base, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.playSound(null, base, ModSounds.UI_BUY.get(), SoundSource.BLOCKS, 1.0F, 0.8F);
        if (p != null) {
            p.displayClientMessage(Component.translatable("satsujinki.kit.built." + this.kind.name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(ChatFormatting.DARK_RED), true);
            if (!p.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String k = this.kind.name().toLowerCase(java.util.Locale.ROOT);
        tooltip.add(Component.translatable("item.satsujinki." + k + "_kit.desc").withStyle(ChatFormatting.GRAY));
    }
}
