package com.hitori.client;

import com.hitori.Hitori;
import com.hitori.block.Props;
import com.hitori.client.sound.PositionLoopSound;
import com.hitori.registry.ModBlocks;
import com.hitori.registry.ModSounds;
import com.hitori.world.ApartmentBuilder;
import com.hitori.world.Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The apartment's own sounds on the client: positional loops of everything that runs or hums in 203 — the CRT's
 * static and line whine at the TV (the game's most important sound), the old fridge, the wall clock, the taps, the
 * bath filling, the washing machine — the rain on the nearest windows, the murmur of a TV behind 204's door, water
 * dripping off the eaves and a light rain falling outside. Loops follow the listener (the camera, so a fixed camera
 * hears what is around it), ease in and out, and only exist near it.
 */
@Mod.EventBusSubscriber(modid = Hitori.MODID, value = Dist.CLIENT)
public final class WorldAmbience {
    private WorldAmbience() {}

    private record Source(BlockPos pos, RegistryObject<SoundEvent> sound, float volume) {}

    private static final Map<Long, PositionLoopSound> LOOPS = new HashMap<>();
    private static final Map<Long, Integer> FADING = new HashMap<>();
    private static int tick;
    private static int nextMurmur = 100;

    /** 203's windows (the rain loops sit on the nearest ones). */
    static final BlockPos[] WINDOWS = {new BlockPos(23, 71, 0), new BlockPos(29, 72, 0), new BlockPos(31, 72, 0), new BlockPos(32, 72, 0),
            new BlockPos(24, 71, 13), new BlockPos(25, 71, 13), new BlockPos(26, 71, 13), new BlockPos(30, 71, 13), new BlockPos(31, 71, 13)};
    /** Behind 204's door: somebody watching TV at night. */
    static final BlockPos MURMUR = new BlockPos(37, 71, 9);

    static Vec3 listener(Minecraft mc) {
        return mc.gameRenderer.getMainCamera().isInitialized() ? mc.gameRenderer.getMainCamera().getPosition() : mc.player.getEyePosition();
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
        Vec3 ear = listener(mc);
        if (tick % 10 == 0) update(level, ear);
        if (tick % 3 == 0) drips(level, ear, level.random);
        rain(level, ear, level.random);
        if (--nextMurmur <= 0) {
            nextMurmur = 120 + level.random.nextInt(160);
            if (MURMUR.distToCenterSqr(ear) < 18 * 18) {
                mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.FIXTURE_TV_VOICE.get(), SoundSource.AMBIENT, 0.22f,
                        0.9f + level.random.nextFloat() * 0.1f, level.random, MURMUR.getX() + 0.5, MURMUR.getY() + 0.5, MURMUR.getZ() + 0.5));
            }
        }
    }

    private static void update(ClientLevel level, Vec3 ear) {
        List<Source> active = new ArrayList<>();
        fixture(level, ear, active, "tv", 24, s -> s.is(ModBlocks.CRT_TV.get()) && s.getValue(Props.ON), ModSounds.AMB_TV_STATIC, 1.0f);
        fixture(level, ear, active, "fridge", 14, s -> s.is(ModBlocks.FRIDGE_OLD.get()) && s.getValue(Props.ON), ModSounds.FIXTURE_FRIDGE, 0.55f);
        fixture(level, ear, active, "wall_clock", 12, s -> s.is(ModBlocks.WALL_CLOCK.get()), ModSounds.FIXTURE_CLOCK, 0.5f);
        fixture(level, ear, active, "kitchen_sink", 16, s -> s.hasProperty(Props.RUNNING) && s.getValue(Props.RUNNING), ModSounds.FIXTURE_TAP, 0.8f);
        fixture(level, ear, active, "washbasin", 16, s -> s.hasProperty(Props.RUNNING) && s.getValue(Props.RUNNING), ModSounds.FIXTURE_TAP, 0.8f);
        fixture(level, ear, active, "bath_tap", 18, s -> s.hasProperty(Props.RUNNING) && s.getValue(Props.RUNNING), ModSounds.FIXTURE_TAP_BATH, 1.0f);
        fixture(level, ear, active, "washing_machine", 20, s -> s.hasProperty(Props.RUNNING) && s.getValue(Props.RUNNING), ModSounds.FIXTURE_WASHING_MACHINE, 1.0f);
        // rain on the three nearest windows
        List<BlockPos> wins = new ArrayList<>();
        for (BlockPos p : WINDOWS) if (p.distToCenterSqr(ear) < 12 * 12) wins.add(p);
        wins.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(ear)));
        for (int i = 0; i < Math.min(3, wins.size()); i++) active.add(new Source(wins.get(i), ModSounds.FIXTURE_RAIN_WINDOW, i == 0 ? 0.7f : 0.45f));

        Map<Long, Float> targets = new HashMap<>();
        for (Source s : active) {
            long key = s.pos.asLong() * 31 + s.sound.getId().hashCode();
            targets.put(key, s.volume);
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
            if (!targets.containsKey(e.getKey())) {
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

    private static void fixture(ClientLevel level, Vec3 ear, List<Source> out, String marker, double range,
                                java.util.function.Predicate<BlockState> on, RegistryObject<SoundEvent> sound, float volume) {
        if (!Layout.has(marker)) return;
        BlockPos p = Layout.marker(marker).pos();
        if (p.distToCenterSqr(ear) > range * range || !level.isLoaded(p)) return;
        if (on.test(level.getBlockState(p))) out.add(new Source(p, sound, volume));
    }

    /** Water running off the roof: drips at the eaves near the listener. */
    private static void drips(ClientLevel level, Vec3 ear, RandomSource r) {
        for (int i = 0; i < 4; i++) {
            int side = r.nextInt(2);
            double x = ApartmentBuilder.ROOF_X1 + r.nextDouble() * (ApartmentBuilder.ROOF_X2 - ApartmentBuilder.ROOF_X1 + 1);
            double z = side == 0 ? ApartmentBuilder.ROOF_Z1 - 0.05 : ApartmentBuilder.ROOF_Z2 + 1.05;
            if ((x - ear.x) * (x - ear.x) + (z - ear.z) * (z - ear.z) > 24 * 24) continue;
            level.addParticle(ParticleTypes.FALLING_WATER, x, 73.9, z, 0, 0, 0);
        }
    }

    /** Raindrops splashing where the sky is open (the vanilla rain is off; the shader pack draws it on the windows). */
    private static void rain(ClientLevel level, Vec3 ear, RandomSource r) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL) return;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 10; i++) {
            double x = ear.x + (r.nextDouble() - 0.5) * 24, z = ear.z + (r.nextDouble() - 0.5) * 24;
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            if (top > ear.y + 10) continue;
            double y = Math.max(top, ear.y - 4) + r.nextDouble() * 8;
            p.set(x, y, z);
            if (!level.canSeeSky(p)) continue;
            level.addParticle(ParticleTypes.RAIN, x, top + 0.02, z, 0, 0, 0);
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
