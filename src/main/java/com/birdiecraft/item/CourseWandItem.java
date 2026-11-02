package com.birdiecraft.item;

import com.birdiecraft.block.GreenBlock;
import com.birdiecraft.course.Course;
import com.birdiecraft.course.GolfCourseData;
import com.birdiecraft.course.Hole;
import com.birdiecraft.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

import java.util.List;

/**
 * Course building tool.
 * <ul>
 *   <li>Right click a tee marker, then a golf cup: adds a hole to the course named after the wand
 *   (rename it in an anvil).</li>
 *   <li>Sneak + right click a tee marker or cup of an existing hole: cycle its par.</li>
 *   <li>Right click a green: cycle slope direction; sneak + right click: cycle steepness.</li>
 *   <li>Right click air: show the course info.</li>
 * </ul>
 */
public class CourseWandItem extends Item {
    public CourseWandItem(Properties properties) {
        super(properties);
    }

    public static String courseName(ItemStack stack) {
        return stack.hasCustomHoverName() ? stack.getHoverName().getString() : "My Course";
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (player == null) return InteractionResult.PASS;

        if (state.getBlock() instanceof GreenBlock) {
            if (!level.isClientSide) {
                BlockState next = player.isShiftKeyDown()
                        ? state.cycle(GreenBlock.STEEP)
                        : state.setValue(GreenBlock.SLOPE, state.getValue(GreenBlock.SLOPE).next());
                level.setBlock(pos, next, 3);
                player.displayClientMessage(Component.translatable("msg.birdiecraft.wand_slope",
                        next.getValue(GreenBlock.SLOPE).getSerializedName().toUpperCase(java.util.Locale.ROOT),
                        next.getValue(GreenBlock.STEEP)), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        boolean isTee = state.is(ModBlocks.TEE_MARKER.get());
        boolean isCup = state.is(ModBlocks.GOLF_CUP.get());
        if (!isTee && !isCup) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        GolfCourseData data = GolfCourseData.get(sp.getServer());
        String name = courseName(stack);
        String id = Course.sanitizeId(name);
        Course course = data.get(id);

        if (player.isShiftKeyDown() && course != null) {
            for (int i = 0; i < course.holes.size(); i++) {
                Hole h = course.holes.get(i);
                if (h.tee.equals(pos) || h.cup.equals(pos)) {
                    h.par = h.par >= 5 ? 3 : h.par + 1;
                    data.setDirty();
                    sp.displayClientMessage(Component.translatable("msg.birdiecraft.wand_par", i + 1, h.par), true);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        CompoundTag tag = stack.getOrCreateTag();
        if (isTee) {
            tag.put("PendingTee", NbtUtils.writeBlockPos(pos));
            sp.displayClientMessage(Component.translatable("msg.birdiecraft.wand_tee").withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.SUCCESS;
        }
        if (!tag.contains("PendingTee")) {
            sp.displayClientMessage(Component.translatable("msg.birdiecraft.wand_need_tee").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        BlockPos tee = NbtUtils.readBlockPos(tag.getCompound("PendingTee"));
        tag.remove("PendingTee");
        if (course == null) {
            course = new Course(id, name, level.dimension());
        }
        Hole hole = new Hole(tee, pos, 4);
        hole.par = Hole.autoPar(hole.lengthBlocks());
        course.holes.add(hole);
        data.put(course);
        sp.sendSystemMessage(Component.translatable("msg.birdiecraft.wand_hole_added", course.holes.size(), name, hole.par, hole.yards())
                .withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            String id = Course.sanitizeId(courseName(stack));
            Course course = GolfCourseData.get(sp.getServer()).get(id);
            if (course == null) {
                sp.displayClientMessage(Component.translatable("msg.birdiecraft.wand_no_course", courseName(stack)), true);
            } else {
                sp.displayClientMessage(Component.translatable("msg.birdiecraft.wand_info", course.displayName, course.holes.size(), course.totalPar()), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.birdiecraft.wand.1", courseName(stack)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.birdiecraft.wand.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.birdiecraft.wand.3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.birdiecraft.wand.4").withStyle(ChatFormatting.GRAY));
    }
}
