package com.hitori.world;

import com.hitori.Hitori;
import com.hitori.block.Props;
import com.hitori.registry.ModBlocks;
import com.hitori.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The apartment's own life on the server: the bath filling while its tap runs (empty → half → full in ~20 s),
 * flickering lights (the 霊障 ones and the dying lamps outside: the corridor lamp in front of 202, the park's lamp),
 * the hanging lamp swinging, the intercom giving up ringing, the doll's wet marks drying, and putting the lamps of
 * freshly loaded chunks into the state of their switch.
 */
@Mod.EventBusSubscriber(modid = Hitori.MODID)
public final class WorldTicker {
    private WorldTicker() {}

    /** Ticks of running water for half a tub / a full tub. */
    public static final int HALF_TICKS = 200, FULL_TICKS = 400;

    private static final Map<BlockPos, Long> FLICKERS = new HashMap<>();
    private static final Map<BlockPos, Long> WET = new HashMap<>();
    private static final Set<ChunkPos> SYNC = new HashSet<>();
    private static long swingUntil, ringUntil;
    private static int fill;

    /** Lamps outside that never work properly. */
    public static final BlockPos[] DYING = {new BlockPos(Site.UNIT_X0[1] + 4, 72, -2), new BlockPos(54, Site.GROUND + 5, 26)};

    static void clear() {
        FLICKERS.clear();
        WET.clear();
        swingUntil = 0;
        ringUntil = 0;
        fill = 0;
    }

    static void flicker(ServerLevel level, BlockPos light, int ticks) {
        FLICKERS.merge(light.immutable(), level.getGameTime() + ticks, Math::max);
    }

    static boolean flickering(BlockPos p) {
        return FLICKERS.containsKey(p);
    }

    static void swing(ServerLevel level, int ticks) {
        swingUntil = Math.max(swingUntil, level.getGameTime() + ticks);
    }

    static void ringFor(ServerLevel level, int ticks) {
        ringUntil = level.getGameTime() + ticks;
    }

    static void wetMark(ServerLevel level, BlockPos pos, int ticks) {
        WET.put(pos.immutable(), level.getGameTime() + ticks);
    }

    static void startFilling(ServerLevel level) {
        fill = switch (Fixtures.bathWater(level)) {
            case 2 -> FULL_TICKS;
            case 1 -> HALF_TICKS;
            default -> 0;
        };
    }

    static void syncFill(int water) {
        fill = water >= 2 ? FULL_TICKS : water == 1 ? HALF_TICKS : 0;
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        clear();
        SYNC.clear();
    }

    @SubscribeEvent
    public static void chunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel sl && sl.dimension() == Layout.DIMENSION) {
            synchronized (SYNC) {
                SYNC.add(event.getChunk().getPos());
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Layout.DIMENSION) return;
        long now = level.getGameTime();
        try {
            syncChunks(level);
            bath(level);
            flickers(level, now);
            if (now % 10 == 0) swinging(level, now);
            if (ringUntil != 0 && now > ringUntil) {
                ringUntil = 0;
                Fixtures.setBool(level, Layout.marker("intercom").pos(), Props.RINGING, false);
            }
            if (now % 20 == 0) drying(level, now);
        } catch (Exception e) {
            Hitori.LOGGER.error("[hitori] world ticker", e);
        }
    }

    // ------------------------------------------------------------------------------------------------ the bath
    private static void bath(ServerLevel level) {
        BlockPos tap = Layout.marker("bath_tap").pos();
        if (!level.isLoaded(tap) || !Fixtures.getBool(level, tap, Props.RUNNING)) return;
        BlockPos tub = Layout.marker("bathtub").pos();
        BlockState s = level.getBlockState(tub);
        if (!s.hasProperty(Props.WATER) || s.getValue(Props.LID)) return;
        if (fill < FULL_TICKS) fill++;
        int water = fill >= FULL_TICKS ? 2 : fill >= HALF_TICKS ? 1 : 0;
        if (water > s.getValue(Props.WATER)) com.hitori.block.BathtubBlock.setBoth(level, tub, water, false);
    }

    // ------------------------------------------------------------------------------------------------ circuits
    private static void syncChunks(ServerLevel level) {
        List<ChunkPos> todo;
        synchronized (SYNC) {
            if (SYNC.isEmpty()) return;
            todo = new ArrayList<>(SYNC);
            SYNC.clear();
        }
        for (ChunkPos cp : todo) {
            if (!level.hasChunk(cp.x, cp.z)) continue;
            for (BlockPos p : Fixtures.allLamps()) {
                if ((p.getX() >> 4) != cp.x || (p.getZ() >> 4) != cp.z) continue;
                BlockState s = level.getBlockState(p);
                if (!s.hasProperty(Props.LIT)) continue;
                boolean lit = Fixtures.shouldBeLit(level, p);
                if (s.getValue(Props.LIT) != lit) level.setBlock(p, s.setValue(Props.LIT, lit), 3);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ flicker
    private static void flickers(ServerLevel level, long now) {
        if (now % 2 != 0) return;
        Set<BlockPos> active = new HashSet<>();
        for (BlockPos p : DYING) active.add(p);
        Iterator<Map.Entry<BlockPos, Long>> it = FLICKERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> e = it.next();
            if (e.getValue() < now) {
                it.remove();
                restore(level, e.getKey());
            } else active.add(e.getKey());
        }
        for (BlockPos p : active) {
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            if (!s.hasProperty(Props.LIT)) continue;
            boolean temporary = FLICKERS.containsKey(p);
            boolean lit = flickerState(p, now, temporary);
            if (s.getValue(Props.LIT) != lit) {
                level.setBlock(p, s.setValue(Props.LIT, lit), 3);
                if (temporary && !lit && level.random.nextInt(5) == 0) {
                    level.playSound(null, p, ModSounds.FIXTURE_SWITCH.get(), SoundSource.BLOCKS, 0.25f, 1.8f);
                }
            }
        }
    }

    /** Deterministic stutter of a dying lamp: long stretches on, then bursts of dropouts; hard = a 霊障 flicker. */
    static boolean flickerState(BlockPos p, long now, boolean hard) {
        long window = now / 40;
        int salt = (int) (p.asLong() * 31);
        double episode = Site.rand((int) window, salt, 91);
        boolean stutter = hard || episode < 0.3;
        if (!stutter) return true;
        double r = Site.rand((int) (now / 2), salt, 92);
        return r > (hard ? 0.55 : 0.3);
    }

    private static void restore(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p)) return;
        BlockState s = level.getBlockState(p);
        if (!s.hasProperty(Props.LIT)) return;
        boolean lit = Fixtures.shouldBeLit(level, p);
        if (s.getValue(Props.LIT) != lit) level.setBlock(p, s.setValue(Props.LIT, lit), 3);
    }

    // ------------------------------------------------------------------------------------------------ the lamp, the marks
    /** The hanging lamp swings: its tilted model flips side every half second, slower toward the end. */
    private static void swinging(ServerLevel level, long now) {
        BlockPos p = Layout.marker("hanging_lamp").pos();
        if (!level.isLoaded(p)) return;
        BlockState s = level.getBlockState(p);
        if (!s.is(ModBlocks.HANGING_LAMP.get())) return;
        if (swingUntil > now) {
            long left = swingUntil - now;
            int period = left < 60 ? 20 : 10;
            if (now % period != 0) return;
            Direction f = s.getValue(HorizontalDirectionalBlock.FACING) == Direction.NORTH ? Direction.SOUTH : Direction.NORTH;
            level.setBlock(p, s.setValue(Props.SWING, true).setValue(HorizontalDirectionalBlock.FACING, f), 3);
            if (level.random.nextInt(3) == 0) level.playSound(null, p, ModSounds.FIXTURE_LAMP_SWING.get(), SoundSource.BLOCKS, 0.5f, 0.9f + level.random.nextFloat() * 0.2f);
        } else if (s.getValue(Props.SWING) || s.getValue(HorizontalDirectionalBlock.FACING) != Direction.NORTH) {
            swingUntil = 0;
            level.setBlock(p, s.setValue(Props.SWING, false).setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
        }
    }

    private static void drying(ServerLevel level, long now) {
        Iterator<Map.Entry<BlockPos, Long>> it = WET.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> e = it.next();
            if (e.getValue() > now) continue;
            it.remove();
            BlockPos p = e.getKey();
            if (level.isLoaded(p) && level.getBlockState(p).is(ModBlocks.PUDDLE.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
