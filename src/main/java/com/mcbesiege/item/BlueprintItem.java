package com.mcbesiege.item;

import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.block.MachinePartBlockEntity;
import com.mcbesiege.machine.Assembler;
import com.mcbesiege.machine.Blueprint;
import com.mcbesiege.part.PartType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Machine save/load: right-click a starting block to save the machine, right-click the ground to build a saved machine. */
public class BlueprintItem extends Item {
    public BlueprintItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof MachinePartBlock mb && mb.type == PartType.STARTING_BLOCK && (player == null || !player.isShiftKeyDown())) {
            Assembler.Result r = Assembler.capture(level, pos, 4000);
            if (r.blueprint == null) {
                if (player != null) player.displayClientMessage(Component.translatable("msg.mcbesiege.err." + r.error), true);
                return InteractionResult.SUCCESS;
            }
            r.blueprint.name = stack.hasCustomHoverName() ? stack.getHoverName().getString() : "";
            stack.getOrCreateTag().put("bp", r.blueprint.save());
            stack.getOrCreateTag().putInt("count", r.blueprint.entries.size());
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
            if (player != null) player.displayClientMessage(Component.translatable("msg.mcbesiege.bp_saved", r.blueprint.entries.size()), true);
            return InteractionResult.SUCCESS;
        }
        if (!stack.hasTag() || !stack.getTag().contains("bp")) return InteractionResult.PASS;
        Blueprint bp = Blueprint.load(stack.getTag().getCompound("bp"));
        Direction look = player != null ? player.getDirection() : Direction.NORTH;
        Rotation rot = rotationBetween(bp.forward, look);
        BlockPos origin = pos.relative(ctx.getClickedFace());
        // lowest part must sit on the clicked surface
        int minY = Integer.MAX_VALUE;
        for (Blueprint.Entry e : bp.entries) minY = Math.min(minY, e.rel.getY());
        origin = origin.below(minY == Integer.MAX_VALUE ? 0 : minY);
        boolean creative = player == null || player.getAbilities().instabuild;
        Map<Item, Integer> need = new HashMap<>();
        for (Blueprint.Entry e : bp.entries) need.merge(e.state.getBlock().asItem(), 1, Integer::sum);
        if (!creative && player != null) {
            for (Map.Entry<Item, Integer> n : need.entrySet()) {
                if (player.getInventory().countItem(n.getKey()) < n.getValue()) {
                    player.displayClientMessage(Component.translatable("msg.mcbesiege.bp_missing", n.getValue(), new ItemStack(n.getKey()).getHoverName()), true);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        for (Blueprint.Entry e : bp.entries) {
            BlockPos p = origin.offset(e.rel.rotate(rot));
            if (!level.getBlockState(p).canBeReplaced()) {
                if (player != null) player.displayClientMessage(Component.translatable("msg.mcbesiege.bp_blocked"), true);
                return InteractionResult.SUCCESS;
            }
        }
        if (!creative && player != null) {
            for (Map.Entry<Item, Integer> n : need.entrySet()) removeItems(player, n.getKey(), n.getValue());
        }
        for (Blueprint.Entry e : bp.entries) {
            BlockPos p = origin.offset(e.rel.rotate(rot));
            level.setBlock(p, e.state.rotate(rot), Block.UPDATE_ALL);
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof MachinePartBlockEntity pbe) {
                CompoundTag d = e.data.copy();
                if (rot != Rotation.NONE) d.remove("con");
                pbe.loadData(d);
            }
        }
        level.playSound(null, origin, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
        if (player instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("msg.mcbesiege.bp_built", bp.entries.size()), true);
        return InteractionResult.SUCCESS;
    }

    private static void removeItems(Player p, Item item, int count) {
        for (int i = 0; i < p.getInventory().getContainerSize() && count > 0; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) {
                int take = Math.min(count, s.getCount());
                s.shrink(take);
                count -= take;
            }
        }
    }

    private static Rotation rotationBetween(Direction from, Direction to) {
        if (!from.getAxis().isHorizontal() || !to.getAxis().isHorizontal()) return Rotation.NONE;
        int a = from.get2DDataValue(), b = to.get2DDataValue();
        return switch (Math.floorMod(b - a, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("bp");
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (stack.hasTag() && stack.getTag().contains("bp")) {
            tooltip.add(Component.translatable("tooltip.mcbesiege.blueprint_filled", stack.getTag().getInt("count")));
        } else {
            tooltip.add(Component.translatable("tooltip.mcbesiege.blueprint"));
        }
    }
}
