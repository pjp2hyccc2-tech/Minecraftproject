package com.reisatsu.world;

import com.reisatsu.Reisatsu;
import com.reisatsu.block.LightBlock;
import com.reisatsu.block.Props;
import com.reisatsu.registry.ModBlocks;
import com.reisatsu.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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
 * The restaurant's own life on the server: broken tubes flickering, temporary flickers, the blinking yellow
 * traffic light, the pole sign's failing row, devices that switch themselves off (drink bar, hand dryers, locker
 * doors), drying wet footprints, hand prints on the mirror, and putting lights of freshly loaded chunks into the
 * state of their circuit.
 */
@Mod.EventBusSubscriber(modid = Reisatsu.MODID)
public final class WorldTicker {
    private WorldTicker() {}

    private record Timer(BlockPos pos, BooleanProperty prop, long at) {}

    private static final Map<BlockPos, Long> FLICKERS = new HashMap<>();
    private static final List<Timer> TIMERS = new ArrayList<>();
    private static final Map<BlockPos, Long> FOOTPRINTS = new HashMap<>();
    private static final Set<ChunkPos> SYNC = new HashSet<>();
    private static long handPrintsUntil;

    static final BlockPos[] TRAFFIC_LIGHTS = {new BlockPos(-28, 70, 49), new BlockPos(-32, 70, 59)};

    static void clear() {
        FLICKERS.clear();
        TIMERS.clear();
        FOOTPRINTS.clear();
        handPrintsUntil = 0;
    }

    static void flicker(ServerLevel level, BlockPos light, int ticks) {
        FLICKERS.merge(light.immutable(), level.getGameTime() + ticks, Math::max);
    }

    static void timer(ServerLevel level, BlockPos pos, BooleanProperty prop, int ticks) {
        TIMERS.removeIf(t -> t.pos.equals(pos) && t.prop == prop);
        TIMERS.add(new Timer(pos.immutable(), prop, level.getGameTime() + ticks));
    }

    static void footprint(ServerLevel level, BlockPos pos, int ticks) {
        FOOTPRINTS.put(pos.immutable(), level.getGameTime() + ticks);
    }

    static void handPrints(ServerLevel level, int ticks) {
        handPrintsUntil = level.getGameTime() + ticks;
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
            flickers(level, now);
            timers(level, now);
            if (now % 20 == 0) footprints(level, now);
            if (now % 15 == 0) blink(level, now);
            signFlicker(level, now);
            if (handPrintsUntil > now && now % 6 == 0) mirrorDrips(level);
        } catch (Exception e) {
            Reisatsu.LOGGER.error("[reisatsu] world ticker", e);
        }
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
            for (BlockPos p : allLights()) {
                if ((p.getX() >> 4) != cp.x || (p.getZ() >> 4) != cp.z) continue;
                BlockState s = level.getBlockState(p);
                if (!s.hasProperty(Props.LIT)) continue;
                boolean lit = Fixtures.shouldBeLit(level, p);
                if (s.getValue(Props.LIT) != lit) level.setBlock(p, s.setValue(Props.LIT, lit), 3);
            }
        }
    }

    private static List<BlockPos> all;

    static List<BlockPos> allLights() {
        if (all == null) {
            List<BlockPos> l = new ArrayList<>(RestaurantBuilder.allLights());
            l.addAll(ExteriorBuilder.signLights());
            l.addAll(RestaurantBuilder.NIGHT_LIGHTS);
            all = l;
        }
        return all;
    }

    // ------------------------------------------------------------------------------------------------ flicker
    /** Broken tubes and temporary flickers: mostly on, with dropouts and stutters. */
    private static void flickers(ServerLevel level, long now) {
        if (now % 2 != 0) return;
        Set<BlockPos> active = new HashSet<>(RestaurantBuilder.BROKEN_LIGHTS);
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
            if (!(s.getBlock() instanceof LightBlock) && !s.hasProperty(Props.LIT)) continue;
            if (!Fixtures.shouldBeLit(level, p)) {
                if (s.getValue(Props.LIT)) level.setBlock(p, s.setValue(Props.LIT, false), 3);
                continue;
            }
            boolean temporary = FLICKERS.containsKey(p);
            boolean lit = flickerState(p, now, temporary);
            if (s.getValue(Props.LIT) != lit) level.setBlock(p, s.setValue(Props.LIT, lit), 3);
        }
    }

    /** Deterministic stutter pattern of a tube: long stretches on, then a burst of dropouts. */
    static boolean flickerState(BlockPos p, long now, boolean hard) {
        long window = now / 40;
        int salt = (int) (p.asLong() * 31);
        double episode = Site.rand((int) window, salt, 91);
        boolean stutter = hard || episode < 0.35;
        if (!stutter) return true;
        double r = Site.rand((int) (now / 2), salt, 92);
        return r > (hard ? 0.5 : 0.3);
    }

    private static void restore(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p)) return;
        BlockState s = level.getBlockState(p);
        if (!s.hasProperty(Props.LIT)) return;
        boolean lit = Fixtures.shouldBeLit(level, p);
        if (s.getValue(Props.LIT) != lit) level.setBlock(p, s.setValue(Props.LIT, lit), 3);
    }

    // ------------------------------------------------------------------------------------------------ timers
    private static void timers(ServerLevel level, long now) {
        Iterator<Timer> it = TIMERS.iterator();
        List<Timer> due = new ArrayList<>();
        while (it.hasNext()) {
            Timer t = it.next();
            if (t.at <= now) {
                due.add(t);
                it.remove();
            }
        }
        for (Timer t : due) Fixtures.setBool(level, t.pos, t.prop, false);
    }

    private static void footprints(ServerLevel level, long now) {
        Iterator<Map.Entry<BlockPos, Long>> it = FOOTPRINTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> e = it.next();
            if (e.getValue() > now) continue;
            it.remove();
            BlockPos p = e.getKey();
            if (level.isLoaded(p) && level.getBlockState(p).is(ModBlocks.PUDDLE.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------------------------------------ outside
    /** The yellow signal blinks (on 15 ticks, off 15 ticks). */
    private static void blink(ServerLevel level, long now) {
        boolean lit = (now / 15) % 2 == 0;
        for (BlockPos p : TRAFFIC_LIGHTS) {
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.hasProperty(Props.LIT) && s.getValue(Props.LIT) != lit) level.setBlock(p, s.setValue(Props.LIT, lit), 2);
        }
    }

    /** The bottom row of the pole sign fails now and then: a few seconds of stutter, a buzz. */
    private static void signFlicker(ServerLevel level, long now) {
        if (now % 2 != 0) return;
        if (!Fixtures.data(level).on(Circuit.SIGN)) return;
        long window = now / 160;
        boolean episode = Site.rand((int) window, 7, 93) < 0.45;
        long phase = now % 160;
        boolean off = episode && phase < 50 && Site.rand((int) (now / 2), 11, 94) < 0.45;
        for (BlockPos p : ExteriorBuilder.signTiles()) {
            if (ExteriorBuilder.tileOf(p) < 12 && ExteriorBuilder.tileOf(p) != 5) continue;
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            if (!s.hasProperty(Props.LIT)) continue;
            boolean lit = !off && !Fixtures.data(level).burst.contains(p);
            if (s.getValue(Props.LIT) != lit) level.setBlock(p, s.setValue(Props.LIT, lit), 2);
        }
        if (episode && phase == 0) {
            BlockPos p = new BlockPos(ExteriorBuilder.SIGN_POLE_X, ExteriorBuilder.SIGN_Y0, ExteriorBuilder.SIGN_Z_FRONT);
            if (level.isLoaded(p)) level.playSound(null, p, ModSounds.FIXTURE_LIGHT_FLICKER.get(), SoundSource.BLOCKS, 1.4f, 0.7f);
        }
    }

    private static void mirrorDrips(ServerLevel level) {
        for (Layout.Marker m : Layout.markers("mirror")) {
            BlockPos p = m.pos();
            if (!level.isLoaded(p)) continue;
            double x = p.getX() + 0.2 + level.random.nextDouble() * 0.6, y = p.getY() + 0.2 + level.random.nextDouble() * 0.7;
            level.sendParticles(ParticleTypes.DRIPPING_WATER, x, y, p.getZ() + 1.03, 1, 0.0, 0.0, 0.0, 0.0);
            if (level.random.nextInt(3) == 0) level.sendParticles(ParticleTypes.FALLING_WATER, x, y - 0.1, p.getZ() + 1.03, 1, 0, 0, 0, 0);
        }
    }
}
