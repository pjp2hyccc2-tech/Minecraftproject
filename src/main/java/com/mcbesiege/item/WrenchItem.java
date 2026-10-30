package com.mcbesiege.item;

import com.mcbesiege.block.MachinePartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;

/** Key mapper / tuning tool: right-click a part to edit keys and sliders, sneak-right-click to rotate it, use in air for the block palette. */
public class WrenchItem extends Item {
    public WrenchItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof MachinePartBlock mb)) return InteractionResult.PASS;
        Player player = ctx.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                BlockState ns;
                if (mb.type.twistable && !st.getValue(MachinePartBlock.TWIST)) ns = st.setValue(MachinePartBlock.TWIST, true);
                else {
                    Direction f = st.getValue(MachinePartBlock.FACING);
                    Direction next = Direction.from3DDataValue((f.get3DDataValue() + 1) % 6);
                    ns = st.setValue(MachinePartBlock.FACING, next).setValue(MachinePartBlock.TWIST, false);
                }
                level.setBlock(pos, ns, 3);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mcbesiege.client.ClientHooks.openPartConfig(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.mcbesiege.client.ClientHooks::openBuilder);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.mcbesiege.wrench"));
    }
}
