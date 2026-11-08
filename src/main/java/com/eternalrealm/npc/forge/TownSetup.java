package com.eternalrealm.npc.forge;

import com.eternalrealm.quest.NpcDefinition;
import com.eternalrealm.story.Speaker;
import com.eternalrealm.story.StoryNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Places the town's NPCs: the ten service NPCs and the story characters in a ring around the capital's centre, and the
 * quest giver of every other area at the inner edge of that area's ring (east of the centre). Used by the world layer
 * with the capital's own spots, and by {@code /eternalrealm setup town} on servers without the アストリア dimension.
 */
public final class TownSetup {
    private static final NpcRole[] SERVICES = {NpcRole.QUEST_BOARD, NpcRole.ITEM_SHOP, NpcRole.BLACKSMITH,
            NpcRole.JOB_MASTER, NpcRole.AUCTION, NpcRole.MAILBOX, NpcRole.GUILD_REGISTRAR, NpcRole.GATEKEEPER,
            NpcRole.ARENA_MASTER, NpcRole.HALL_OF_FAME};
    private static final List<StoryNpc> CHARACTERS = List.of(StoryNpc.LILIA, StoryNpc.GUSTAV, StoryNpc.NOX,
            StoryNpc.LASZLO, StoryNpc.WOLFRAM);
    private static final double SERVICE_RADIUS = 9.0;
    private static final double CHARACTER_RADIUS = 15.0;
    private static final int RING_WIDTH = 300;

    private TownSetup() {
    }

    /** @return how many NPCs stand now */
    public static int placeAround(ServerLevel level, BlockPos center) {
        int placed = 0;
        for (int i = 0; i < SERVICES.length; i++) {
            NpcRole role = SERVICES[i];
            BlockPos pos = onRing(level, center, SERVICE_RADIUS, i, SERVICES.length);
            placed += Npcs.ensure(level, pos, role, facing(center, pos)).map(npc -> {
                StoryNpc.byId(role.id()).map(StoryNpc::speaker).map(Speaker::key).ifPresent(key ->
                        npc.setCustomName(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GOLD)));
                return 1;
            }).orElse(0);
        }
        for (int i = 0; i < CHARACTERS.size(); i++) {
            StoryNpc character = CHARACTERS.get(i);
            BlockPos pos = onRing(level, center, CHARACTER_RADIUS, i * 2 + 1, CHARACTERS.size() * 2);
            placed += StoryNpcs.ensure(level, pos, character.id(), Component.translatable(character.speaker().key()),
                    facing(center, pos)).isPresent() ? 1 : 0;
        }
        for (NpcDefinition giver : com.eternalrealm.quest.Npcs.all()) {
            double distance = giver.areaId() <= 1 ? CHARACTER_RADIUS + 4.0 : (giver.areaId() - 1) * RING_WIDTH + 24.0;
            BlockPos column = giver.areaId() <= 1 ? onRing(level, center, distance, 0, 1)
                    : surface(level, center.getX() + (int) distance, center.getZ());
            placed += StoryNpcs.ensure(level, column, giver, facing(center, column)).isPresent() ? 1 : 0;
        }
        return placed;
    }

    private static BlockPos onRing(ServerLevel level, BlockPos center, double radius, int index, int count) {
        double angle = Math.PI * 2.0 * index / count;
        return surface(level, center.getX() + (int) Math.round(Math.cos(angle) * radius),
                center.getZ() + (int) Math.round(Math.sin(angle) * radius));
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4); // load the chunk so the NPC can be placed
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Yaw looking from {@code pos} towards {@code center}. */
    private static float facing(BlockPos center, BlockPos pos) {
        double dx = center.getX() - pos.getX();
        double dz = center.getZ() - pos.getZ();
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }
}
