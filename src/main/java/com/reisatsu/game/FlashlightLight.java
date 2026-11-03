package com.reisatsu.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The flashlight without a shader pack: invisible light blocks where the beam hits and a dim one at the player. With a
 * shader pack on that client the pack draws the beam ({@code item.properties}) and nothing is placed.
 */
public final class FlashlightLight {
    private record Lights(BlockPos spot, BlockPos self) {}

    private static final Map<UUID, Lights> LIGHTS = new HashMap<>();

    private FlashlightLight() {}

    public static void tick(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.tickCount % 2 != 0) continue;
            PlayerState ps = GameManager.state(p);
            boolean beam = GameManager.flashlightOn(p) && !ps.shaders && !ps.caught() && GameManager.inGame(p);
            BlockPos spot = null, self = null;
            int spotLevel = 0, selfLevel = 0;
            if (beam) {
                double range = 28.0;
                Vec3 eye = p.getEyePosition();
                Vec3 end = eye.add(p.getViewVector(1.0f).scale(range));
                BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
                Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
                double dist = at.distanceTo(eye);
                spot = BlockPos.containing(at.subtract(p.getViewVector(1.0f).scale(0.6)));
                spotLevel = (int) Math.round(14 - dist * 0.2);
                spotLevel = Math.max(0, Math.min(15, spotLevel));
                self = BlockPos.containing(eye);
                selfLevel = 6;
            }
            Lights want = new Lights(spotLevel > 0 ? spot : null, selfLevel > 0 ? self : null);
            Lights old = LIGHTS.get(p.getUUID());
            if (old != null && Objects.equals(old.spot, want.spot) && Objects.equals(old.self, want.self)) continue;
            if (old != null) {
                if (old.spot != null && !old.spot.equals(want.spot) && !old.spot.equals(want.self)) clear(level, old.spot);
                if (old.self != null && !old.self.equals(want.self) && !old.self.equals(want.spot)) clear(level, old.self);
            }
            boolean placedSpot = want.spot != null && place(level, want.spot, spotLevel);
            boolean placedSelf = want.self != null && !want.self.equals(want.spot) && place(level, want.self, selfLevel);
            Lights now = new Lights(placedSpot ? want.spot : null, placedSelf ? want.self : null);
            if (now.spot == null && now.self == null) LIGHTS.remove(p.getUUID());
            else LIGHTS.put(p.getUUID(), now);
        }
        LIGHTS.entrySet().removeIf(e -> {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || p.level() != level) {
                if (e.getValue().spot != null) clear(level, e.getValue().spot);
                if (e.getValue().self != null) clear(level, e.getValue().self);
                return true;
            }
            return false;
        });
    }

    private static boolean place(ServerLevel level, BlockPos pos, int lightLevel) {
        BlockState cur = level.getBlockState(pos);
        if (!cur.isAir() && !cur.is(Blocks.LIGHT)) return false;
        BlockState want = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lightLevel);
        if (!cur.equals(want)) level.setBlock(pos, want, 2);
        return true;
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.LIGHT)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }

    public static void remove(ServerPlayer p) {
        Lights old = LIGHTS.remove(p.getUUID());
        if (old != null && p.level() instanceof ServerLevel sl) {
            if (old.spot != null) clear(sl, old.spot);
            if (old.self != null) clear(sl, old.self);
        }
    }

    public static void removeAll(ServerLevel level) {
        for (Lights l : LIGHTS.values()) {
            if (l.spot != null) clear(level, l.spot);
            if (l.self != null) clear(level, l.self);
        }
        LIGHTS.clear();
    }
}
