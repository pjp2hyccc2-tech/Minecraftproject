package com.mcbesiege.item;

import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.block.MachinePartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Locale;

/** Two-point connectors: brace (rigid strut), contractible spring and rope/winch. Click two part faces. */
public class ConnectorItem extends Item {
    public enum Kind { BRACE, SPRING, ROPE }

    public final Kind kind;

    public ConnectorItem(Kind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    public String kindId() { return kind.name().toLowerCase(Locale.ROOT); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (!(level.getBlockState(pos).getBlock() instanceof MachinePartBlock)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        CompoundTag tag = stack.getOrCreateTag();
        Direction face = ctx.getClickedFace();
        if (!tag.contains("first")) {
            tag.putLong("first", pos.asLong());
            tag.putInt("face", face.get3DDataValue());
            if (player != null) player.displayClientMessage(Component.translatable("msg.mcbesiege.connector_first"), true);
            return InteractionResult.SUCCESS;
        }
        BlockPos first = BlockPos.of(tag.getLong("first"));
        Direction fa = Direction.from3DDataValue(tag.getInt("face"));
        tag.remove("first");
        tag.remove("face");
        if (first.equals(pos)) return InteractionResult.SUCCESS;
        if (first.distSqr(pos) > 16 * 16) {
            if (player != null) player.displayClientMessage(Component.translatable("msg.mcbesiege.connector_far"), true);
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(first);
        if (!(be instanceof MachinePartBlockEntity pbe)) return InteractionResult.SUCCESS;
        CompoundTag c = new CompoundTag();
        c.putString("kind", kindId());
        c.putInt("dx", pos.getX() - first.getX());
        c.putInt("dy", pos.getY() - first.getY());
        c.putInt("dz", pos.getZ() - first.getZ());
        c.putInt("fa", fa.get3DDataValue());
        c.putInt("fb", face.get3DDataValue());
        CompoundTag cfg = new CompoundTag();
        if (kind == Kind.SPRING) {
            cfg.putIntArray("keys", new int[]{com.mcbesiege.part.Keys.L});
            cfg.putFloat("strength", 1.0f);
        } else if (kind == Kind.ROPE) {
            cfg.putIntArray("wind", new int[]{com.mcbesiege.part.Keys.O});
            cfg.putIntArray("unwind", new int[]{com.mcbesiege.part.Keys.P});
            cfg.putFloat("speed", 1.0f);
        }
        c.put("cfg", cfg);
        pbe.addConnector(c);
        level.playSound(null, pos, kind == Kind.ROPE ? SoundEvents.LEASH_KNOT_PLACE : SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1f, 1f);
        if (player != null) player.displayClientMessage(Component.translatable("msg.mcbesiege.connector_done", Component.translatable(getDescriptionId())), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.hasTag()) {
            stack.getOrCreateTag().remove("first");
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("msg.mcbesiege.connector_cancel"), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("first");
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.mcbesiege.connector"));
    }
}
