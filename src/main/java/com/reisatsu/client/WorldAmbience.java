package com.reisatsu.client;

import com.reisatsu.Reisatsu;
import com.reisatsu.block.Props;
import com.reisatsu.client.sound.PositionLoopSound;
import com.reisatsu.registry.ModBlocks;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.ExteriorBuilder;
import com.reisatsu.world.Layout;
import com.reisatsu.world.RestaurantBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The fixtures' own sounds on the client: positional loops of everything that runs, hums or buzzes (running taps,
 * the fryer, gas, the dishwasher, the staff TV's static, the office monitors, the ringing phone, the vending
 * machines, the pole sign's transformer, flickering tubes, gutters after the rain) plus drips from the eaves and the
 * water running off the roof. Loops ease in and out and only exist near the listener.
 */
@Mod.EventBusSubscriber(modid = Reisatsu.MODID, value = Dist.CLIENT)
public final class WorldAmbience {
    private WorldAmbience() {}

    private record Source(BlockPos pos, RegistryObject<SoundEvent> sound, float volume, float range) {}

    private static final Map<Long, PositionLoopSound> LOOPS = new HashMap<>();
    private static final Map<Long, Float> TARGETS = new HashMap<>();
    private static final Map<Long, Integer> FADING = new HashMap<>();
    private static List<BlockPos> fixtures;
    private static int tick;

    /** Gutters: downpipes at the building's corners and either side of the entrance canopy. */
    static final BlockPos[] GUTTERS = {new BlockPos(-1, 65, -1), new BlockPos(41, 65, -1), new BlockPos(-1, 65, 31), new BlockPos(41, 65, 31),
            new BlockPos(7, 65, 31)};
    static final BlockPos[] VENDING = {new BlockPos(7, 65, 31), new BlockPos(8, 65, 31)};

    static List<BlockPos> fixtures() {
        if (fixtures == null) {
            List<BlockPos> l = new ArrayList<>();
            for (String k : new String[]{"sink_w", "sink_m", "sink_kitchen", "fryer", "gas_range", "dishwasher", "tv", "phone", "monitors",
                    "hand_dryer_w", "hand_dryer_m", "drink_bar"}) {
                for (Layout.Marker m : Layout.markers(k)) l.add(m.pos());
            }
            l.addAll(RestaurantBuilder.BROKEN_LIGHTS);
            l.addAll(RestaurantBuilder.CEILING_LIGHTS);
            fixtures = l;
        }
        return fixtures;
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || level.dimension() != Layout.DIMENSION) {
            stopAll();
            return;
        }
        if (mc.isPaused()) return;
        tick++;
        Vec3 ear = mc.player.getEyePosition();
        if (tick % 10 == 0) update(level, ear);
        if (tick % 4 == 0) drips(level, ear, level.random);
    }

    private static void update(ClientLevel level, Vec3 ear) {
        List<Source> active = new ArrayList<>();
        for (BlockPos p : fixtures()) {
            if (p.distToCenterSqr(ear) > 30 * 30 || !level.isLoaded(p)) continue;
            Source s = sourceOf(level, p, level.getBlockState(p));
            if (s != null) active.add(s);
        }
        for (BlockPos p : VENDING) if (p.distToCenterSqr(ear) < 24 * 24) active.add(new Source(p, ModSounds.AMB_VENDING, 0.6f, 12));
        BlockPos sign = new BlockPos(ExteriorBuilder.SIGN_POLE_X, ExteriorBuilder.SIGN_Y0 + 1, ExteriorBuilder.SIGN_Z_FRONT);
        if (sign.distToCenterSqr(ear) < 40 * 40 && level.isLoaded(sign)) {
            BlockState s = level.getBlockState(sign);
            if (s.hasProperty(Props.LIT) && s.getValue(Props.LIT)) active.add(new Source(sign, ModSounds.AMB_SIGN_BUZZ, 0.8f, 24));
        }
        boolean outside = Layout.roomAt(BlockPos.containing(ear)) == null;
        for (BlockPos p : GUTTERS) if (p.distToCenterSqr(ear) < 20 * 20) active.add(new Source(p, ModSounds.AMB_GUTTER, outside ? 0.7f : 0.25f, 16));

        TARGETS.clear();
        for (Source s : active) {
            long key = s.pos.asLong() * 31 + s.sound.getId().hashCode();
            TARGETS.put(key, s.volume);
            PositionLoopSound loop = LOOPS.get(key);
            if (loop == null || loop.isStopped()) {
                if (!s.sound.isPresent()) continue;
                loop = new PositionLoopSound(s.sound.get(), SoundSource.BLOCKS, s.pos.getX() + 0.5, s.pos.getY() + 0.5, s.pos.getZ() + 0.5);
                LOOPS.put(key, loop);
                Minecraft.getInstance().getSoundManager().play(loop);
            }
            loop.target = s.volume;
        }
        Iterator<Map.Entry<Long, PositionLoopSound>> it = LOOPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, PositionLoopSound> e = it.next();
            PositionLoopSound loop = e.getValue();
            if (!TARGETS.containsKey(e.getKey())) {
                loop.target = 0.0f;
                int f = FADING.merge(e.getKey(), 10, Integer::sum);
                if (f > 80 || loop.isStopped()) {
                    loop.finish();
                    it.remove();
                    FADING.remove(e.getKey());
                }
            } else {
                FADING.remove(e.getKey());
            }
        }
    }

    /** The loop a fixture plays in its current state, or null. */
    static Source sourceOf(ClientLevel level, BlockPos p, BlockState s) {
        if (s.is(ModBlocks.RESTROOM_SINK.get()) || s.is(ModBlocks.KITCHEN_SINK.get())) {
            return s.getValue(Props.RUNNING) ? new Source(p, ModSounds.FIXTURE_FAUCET_LOOP, 0.8f, 12) : null;
        }
        if (s.is(ModBlocks.FRYER.get())) return s.getValue(Props.ON) ? new Source(p, ModSounds.FIXTURE_FRYER_LOOP, 0.7f, 12) : null;
        if (s.is(ModBlocks.GAS_RANGE.get())) return s.getValue(Props.LIT) ? new Source(p, ModSounds.FIXTURE_GAS_LOOP, 0.6f, 12) : null;
        if (s.is(ModBlocks.DISHWASHER.get())) return s.getValue(Props.RUNNING) ? new Source(p, ModSounds.FIXTURE_DISHWASHER, 0.8f, 14) : null;
        if (s.is(ModBlocks.CRT_TV.get())) return s.getValue(Props.ON) ? new Source(p, ModSounds.FIXTURE_TV_STATIC, 0.6f, 12) : null;
        if (s.is(ModBlocks.SECURITY_MONITORS.get())) return s.getValue(Props.ON) ? new Source(p, ModSounds.FIXTURE_MONITOR_STATIC, 0.25f, 8) : null;
        if (s.is(ModBlocks.BUSINESS_PHONE.get())) return s.getValue(Props.RINGING) ? new Source(p, ModSounds.FIXTURE_PHONE_RING, 1.0f, 24) : null;
        if (s.is(ModBlocks.HAND_DRYER.get())) return s.getValue(Props.RUNNING) ? new Source(p, ModSounds.FIXTURE_HAND_DRYER, 1.0f, 16) : null;
        if (s.is(ModBlocks.CEILING_LIGHT.get())) {
            boolean lit = s.getValue(Props.LIT), broken = s.getValue(Props.BROKEN);
            if (broken && (lit || RestaurantBuilder.BROKEN_LIGHTS.contains(p))) return new Source(p, ModSounds.AMB_FLUORESCENT, 0.7f, 10);
            return lit ? new Source(p, ModSounds.AMB_FLUORESCENT, 0.12f, 10) : null;
        }
        return null;
    }

    /** Water still running off the roof after the rain: drips at the eaves near the listener, now and then a drip sound. */
    private static void drips(ClientLevel level, Vec3 ear, RandomSource r) {
        for (int i = 0; i < 3; i++) {
            int side = r.nextInt(4);
            double t = r.nextDouble();
            double x = side < 2 ? -1.0 + t * 43.0 : (side == 2 ? -1.0 : 42.0);
            double z = side < 2 ? (side == 0 ? -1.0 : 32.0) : -1.0 + t * 33.0;
            if ((x - ear.x) * (x - ear.x) + (z - ear.z) * (z - ear.z) > 28 * 28) continue;
            level.addParticle(ParticleTypes.FALLING_WATER, x, 70.0, z, 0, 0, 0);
            if (r.nextInt(14) == 0) {
                Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(ModSounds.AMB_DRIP.get(), SoundSource.AMBIENT,
                        0.35f + r.nextFloat() * 0.25f, 0.9f + r.nextFloat() * 0.2f, r, x, 65.2, z));
            }
        }
    }

    private static void stopAll() {
        if (LOOPS.isEmpty()) return;
        for (PositionLoopSound l : LOOPS.values()) l.finish();
        LOOPS.clear();
        FADING.clear();
        tick = 0;
    }
}
