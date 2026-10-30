package com.nihonisshu.tabi.block;

import com.nihonisshu.tabi.dialogue.DialogueManager;
import com.nihonisshu.tabi.game.ShopManager;
import com.nihonisshu.tabi.game.TicketOffice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Interactive furniture. action examples:
 * "shop:vending"  -> opens a shop
 * "ticket"        -> opens the ticket machine of the nearest station
 * "talk:dad"      -> starts a dialogue with a virtual speaker (the black telephone)
 * "warp"          -> elevator / lift: destination listed in meta.json "warps" (nearest entry to this block)
 */
public class ActionBlock extends FacingBlock {
    private final String action;

    public ActionBlock(Properties props, @Nullable Map<Direction, VoxelShape> shapes, String action) {
        super(props, shapes, false);
        this.action = action;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (action.isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (action.startsWith("shop:")) {
                ShopManager.open(sp, action.substring(5));
            } else if (action.equals("ticket")) {
                TicketOffice.open(sp, pos);
            } else if (action.startsWith("talk:")) {
                DialogueManager.talkVirtual(sp, action.substring(5));
            } else if (action.equals("warp")) {
                com.nihonisshu.tabi.game.Warps.use(sp, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
