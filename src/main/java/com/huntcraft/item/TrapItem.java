package com.huntcraft.item;

import com.huntcraft.entity.misc.TrapEntity;
import com.huntcraft.registry.HCSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Places a pitfall / shock trap on the ground. */
public class TrapItem extends Item {
    private final TrapEntity.TrapKind kind;

    public TrapItem(Properties props, TrapEntity.TrapKind kind) {
        super(props);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        Vec3 pos = Vec3.atBottomCenterOf(ctx.getClickedPos().above());
        if (TrapEntity.isTrapNear(level, pos, 6)) {
            if (player != null && !level.isClientSide) player.displayClientMessage(Component.translatable("message.huntcraft.trap_near"), true);
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            TrapEntity trap = TrapEntity.create(level, kind, pos.x, pos.y, pos.z);
            level.addFreshEntity(trap);
            level.playSound(null, pos.x, pos.y, pos.z, HCSounds.TRAP_SET.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            if (player == null || !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
