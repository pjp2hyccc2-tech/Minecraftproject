package com.nihonisshu.tabi.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nihonisshu.tabi.block.CrossingSignalBlock;
import com.nihonisshu.tabi.data.TabiData;
import com.nihonisshu.tabi.entity.VehicleEntity;
import com.nihonisshu.tabi.registry.TabiBlocks;
import com.nihonisshu.tabi.registry.TabiSounds;
import com.nihonisshu.tabi.registry.TabiWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Level crossings (踏切): while a train runs nearby the lamps flash alternately, the bell rings ("カンカン")
 * and the barrier bars come down across the road. meta.json "crossings":
 * [{"route":"kawagiri","s":1780,"pos":[x,y,z],"signals":[[x,y,z],...],"bars":[[x,y,z,"east"],...],"raised":[[x,y,z],...]}]
 */
public final class CrossingManager {
    private CrossingManager() {}

    private record Crossing(String route, double s, BlockPos pos, List<BlockPos> signals, List<BlockPos> bars, List<String> barFacing) {}

    private static List<Crossing> list;
    private static final Map<BlockPos, Boolean> ACTIVE = new HashMap<>();

    private static List<Crossing> all() {
        if (list == null) {
            list = new ArrayList<>();
            JsonArray arr = TabiData.meta().getAsJsonArray("crossings");
            if (arr != null) {
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    JsonArray p = o.getAsJsonArray("pos");
                    List<BlockPos> sig = new ArrayList<>(), bars = new ArrayList<>();
                    List<String> fac = new ArrayList<>();
                    for (JsonElement s : o.getAsJsonArray("signals")) sig.add(pos(s.getAsJsonArray()));
                    for (JsonElement b : o.getAsJsonArray("bars")) {
                        JsonArray a = b.getAsJsonArray();
                        bars.add(pos(a));
                        fac.add(a.size() > 3 ? a.get(3).getAsString() : "north");
                    }
                    list.add(new Crossing(TabiData.str(o, "route", ""), o.get("s").getAsDouble(), pos(p), sig, bars, fac));
                }
            }
        }
        return list;
    }

    private static BlockPos pos(JsonArray a) {
        return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0) return;
        ServerLevel level = server.getLevel(TabiWorldgen.JAPAN);
        if (level == null || all().isEmpty()) return;
        for (Crossing c : all()) {
            if (!level.isLoaded(c.pos)) continue;
            boolean active = false;
            for (VehicleEntity v : level.getEntitiesOfClass(VehicleEntity.class, new AABB(c.pos).inflate(140, 40, 140),
                    v -> v.routeId().equals(c.route))) {
                double d = Math.abs(v.getS() - c.s);
                if (d < 85 && v.getState() != VehicleEntity.STOPPED) active = true;
                if (d < 30) active = true;
            }
            boolean was = ACTIVE.getOrDefault(c.pos, false);
            int phase = (server.getTickCount() / 10) % 2;
            for (BlockPos sp : c.signals) {
                BlockState st = level.getBlockState(sp);
                if (st.getBlock() instanceof CrossingSignalBlock) {
                    int want = active ? 1 + phase : 0;
                    if (st.getValue(CrossingSignalBlock.FLASH) != want) level.setBlock(sp, st.setValue(CrossingSignalBlock.FLASH, want), 3);
                }
            }
            if (active != was) {
                for (int i = 0; i < c.bars.size(); i++) {
                    BlockPos bp = c.bars.get(i);
                    if (active) {
                        if (level.getBlockState(bp).isAir()) {
                            String f = c.barFacing.get(i);
                            BlockState bar = TabiBlocks.CROSSING_BAR.get().defaultBlockState();
                            try {
                                bar = bar.setValue(com.nihonisshu.tabi.block.FacingBlock.FACING, net.minecraft.core.Direction.byName(f));
                            } catch (Exception ignored) {
                            }
                            level.setBlock(bp, bar, 3);
                        }
                    } else if (level.getBlockState(bp).is(TabiBlocks.CROSSING_BAR.get())) {
                        level.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                ACTIVE.put(c.pos, active);
            }
            if (active && server.getTickCount() % 10 == 0) {
                level.playSound(null, c.pos, TabiSounds.CROSSING_BELL.get(), SoundSource.BLOCKS, 1.6f, 1f);
            }
        }
    }
}
