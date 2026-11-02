package com.meikon.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The lantern's light: an invisible light block that follows a player holding a lit lantern, so the light
 * works with or without a shader pack (and lights the bride's eyes to you — and you to her).
 */
public final class LanternLight {
    public static final int LEVEL = 12;
    private static final Map<UUID, BlockPos> LIGHTS = new HashMap<>();
    private static final Map<UUID, Integer> SUPPRESS = new HashMap<>();

    private LanternLight() {}

    public static void tick(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            UUID id = p.getUUID();
            PlayerState ps = GameManager.state(p);
            int sup = SUPPRESS.getOrDefault(id, 0);
            if (sup > 0) SUPPRESS.put(id, sup - 1);
            boolean want = GameManager.holdsLitLantern(p) && sup <= 0 && !p.isSpectator()
                    && (ps == null || (ps.closet < 0 && !ps.captured));
            BlockPos target = want ? spot(level, p) : null;
            BlockPos old = LIGHTS.get(id);
            if (Objects.equals(old, target)) continue;
            if (old != null) clear(level, old);
            if (target != null) {
                level.setBlock(target, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LEVEL), 3);
                LIGHTS.put(id, target);
            } else {
                LIGHTS.remove(id);
            }
        }
        LIGHTS.entrySet().removeIf(e -> {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || p.level() != level) {
                clear(level, e.getValue());
                return true;
            }
            return false;
        });
    }

    private static BlockPos spot(ServerLevel level, ServerPlayer p) {
        BlockPos head = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
        if (level.getBlockState(head).isAir() || level.getBlockState(head).is(Blocks.LIGHT)) return head;
        BlockPos feet = p.blockPosition();
        if (level.getBlockState(feet).isAir() || level.getBlockState(feet).is(Blocks.LIGHT)) return feet;
        return null;
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.LIGHT)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    public static void suppress(ServerPlayer p, int ticks) {
        SUPPRESS.put(p.getUUID(), ticks);
    }

    public static void remove(ServerPlayer p) {
        BlockPos old = LIGHTS.remove(p.getUUID());
        if (old != null && p.level() instanceof ServerLevel sl) clear(sl, old);
    }

    public static void removeAll(ServerLevel level) {
        for (BlockPos p : LIGHTS.values()) clear(level, p);
        LIGHTS.clear();
    }

    public static void forgetAll() {
        LIGHTS.clear();
        SUPPRESS.clear();
    }
}
