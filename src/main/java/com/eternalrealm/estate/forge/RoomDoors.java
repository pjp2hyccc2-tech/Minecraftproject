package com.eternalrealm.estate.forge;

import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstateResult;
import com.eternalrealm.estate.EstateStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * What a room door does (docs/modules/estates.md): in the capital, a player who rents a room hears the key turn and
 * steps into their own room (whichever door they use: every door of the inn opens onto one's own room); a player
 * without a room is sent to the innkeeper. Inside an estate the door is the way out. Server thread.
 */
public final class RoomDoors {
    public static final String NO_ROOM = "message.eternalrealm.estate.door.no_room";
    public static final String KEY_TURNS = "message.eternalrealm.estate.door.key";
    /** Using the door from inside an estate (the room's own exit): replaced by the exit menu of the inn. */
    private static BiConsumer<ServerPlayer, BlockPos> exit = (player, pos) -> EstateService.leave(player);

    private RoomDoors() {
    }

    /** What using the door from inside an estate does (the inn opens a little menu: back, or to the capital). */
    public static void setExit(BiConsumer<ServerPlayer, BlockPos> handler) {
        exit = handler;
    }

    /** A player used the room door whose lower half is at {@code pos}. */
    public static void use(ServerPlayer player, BlockPos pos) {
        if (EstateLevels.isEstateLevel(player.level())) {
            exit.accept(player, pos);
            return;
        }
        Optional<EstateKey> room = EstateService.estate(EstateKey.ROOM, player.getUUID());
        if (room.isEmpty()) {
            player.level().playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable(NO_ROOM).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        EstateStatus status = EstateService.status(room.get());
        if (!status.enterable()) {
            player.displayClientMessage(Component.translatable(EstateResult.BUILDING.messageKey(), status.percent())
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        player.level().playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.BLOCKS, 1.0F, 1.3F);
        player.level().playSound(null, pos, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.8F, 0.9F);
        if (EstateService.enter(player, room.get()).ok()) {
            player.displayClientMessage(Component.translatable(KEY_TURNS).withStyle(ChatFormatting.GRAY), true);
        }
    }
}
