package com.meikon.block;

import com.meikon.game.GameManager;
import com.meikon.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

import java.util.function.Supplier;

/** The storehouse (蔵) door: locked until the player brings the key. Mobs can never open it. */
public class KuraDoorBlock extends MeikonDoorBlock {
    public KuraDoorBlock(Properties properties, Supplier<SoundEvent> sound) {
        super(properties, BlockSetType.IRON, sound, 12.0f);
    }

    @Override
    protected boolean canPlayerOpen(BlockState state, Level level, BlockPos pos, Player player) {
        if (player.isCreative()) return true;
        if (level.isClientSide) {
            return player.getMainHandItem().is(ModItems.KURA_KEY.get()) || player.getOffhandItem().is(ModItems.KURA_KEY.get())
                    || com.meikon.game.ClientView.kuraUnlocked;
        }
        if (player instanceof ServerPlayer sp) {
            if (GameManager.tryUnlockKura(sp, pos)) return true;
            sp.displayClientMessage(Component.translatable("message.meikon.locked"), true);
        }
        return false;
    }
}
