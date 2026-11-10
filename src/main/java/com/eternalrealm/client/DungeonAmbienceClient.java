package com.eternalrealm.client;

import com.eternalrealm.client.map.ClientMapState;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.DungeonLayout;
import com.eternalrealm.dungeon.decor.DungeonAmbience;
import com.eternalrealm.world.map.MapView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Plays a dungeon's ambience ({@link DungeonAmbience}) around the local player while they are inside the instance
 * the map data says they are in: drifting motes, and now and then a quiet sound. Client-side only, so it costs the
 * server and the network nothing; it follows the particle setting.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DungeonAmbienceClient {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<String, Optional<ParticleOptions>> PARTICLES = new HashMap<>();
    private static final double RADIUS = 10.0;
    private static float owed;
    private static long nextSound = -1;

    private DungeonAmbienceClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || minecraft.isPaused()) {
            return;
        }
        Optional<MapView.Dungeon> view = ClientMapState.view().dungeon();
        if (view.isEmpty() || !inside(view.get(), player)) {
            nextSound = -1;
            return;
        }
        Optional<DungeonAmbience> ambience = DungeonAmbience.themeOf(view.get().id()).map(DungeonAmbience::of);
        if (ambience.isEmpty()) {
            return;
        }
        motes(level, player, ambience.get(), minecraft.options.particles().get());
        sounds(level, player, ambience.get());
    }

    private static boolean inside(MapView.Dungeon dungeon, LocalPlayer player) {
        double x = player.getX() - dungeon.originX();
        double y = player.getY() - dungeon.originY();
        double z = player.getZ() - dungeon.originZ();
        return x >= 0 && z >= 0 && x < DungeonLayout.FOOTPRINT && z < DungeonLayout.FOOTPRINT && y > -8
                && y < DungeonLayout.MAX_HEIGHT + 8;
    }

    private static void motes(ClientLevel level, LocalPlayer player, DungeonAmbience ambience, ParticleStatus status) {
        if (status == ParticleStatus.MINIMAL || ambience.motes().isEmpty()) {
            return;
        }
        owed += ambience.motesPerSecond() / 20.0F * (status == ParticleStatus.DECREASED ? 0.5F : 1.0F);
        int total = 0;
        for (DungeonAmbience.Mote mote : ambience.motes()) {
            total += mote.weight();
        }
        while (owed >= 1.0F) {
            owed -= 1.0F;
            int pick = RANDOM.nextInt(total);
            DungeonAmbience.Mote chosen = ambience.motes().get(0);
            for (DungeonAmbience.Mote mote : ambience.motes()) {
                pick -= mote.weight();
                if (pick < 0) {
                    chosen = mote;
                    break;
                }
            }
            Optional<ParticleOptions> particle = particle(chosen.particle());
            if (particle.isEmpty()) {
                continue;
            }
            double x = player.getX() + (RANDOM.nextDouble() * 2 - 1) * RADIUS;
            double y = player.getY() + RANDOM.nextDouble() * 5.0 - 0.5;
            double z = player.getZ() + (RANDOM.nextDouble() * 2 - 1) * RADIUS;
            if (!level.getBlockState(net.minecraft.core.BlockPos.containing(x, y, z)).isAir()) {
                continue;
            }
            double drift = chosen.drift() * 0.02;
            level.addParticle(particle.get(), x, y, z, (RANDOM.nextDouble() - 0.5) * 0.01, drift,
                    (RANDOM.nextDouble() - 0.5) * 0.01);
        }
    }

    private static void sounds(ClientLevel level, LocalPlayer player, DungeonAmbience ambience) {
        long now = level.getGameTime();
        if (ambience.sounds().isEmpty()) {
            return;
        }
        if (nextSound < 0) {
            nextSound = now + ambience.soundEvery() / 2 + RANDOM.nextInt(Math.max(1, ambience.soundEvery()));
            return;
        }
        if (now < nextSound) {
            return;
        }
        nextSound = now + ambience.soundEvery() / 2 + RANDOM.nextInt(Math.max(1, ambience.soundEvery()));
        String id = ambience.sounds().get(RANDOM.nextInt(ambience.sounds().size()));
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.tryParse(id));
        if (sound == null) {
            return;
        }
        double x = player.getX() + (RANDOM.nextDouble() * 2 - 1) * 8.0;
        double y = player.getY() + RANDOM.nextDouble() * 3.0;
        double z = player.getZ() + (RANDOM.nextDouble() * 2 - 1) * 8.0;
        level.playLocalSound(x, y, z, sound, SoundSource.AMBIENT, ambience.volume(),
                0.8F + RANDOM.nextFloat() * 0.4F, false);
    }

    /** The particle of a {@link DungeonAmbience.Mote} id (cached; empty if the game does not know it). */
    static Optional<ParticleOptions> particle(String id) {
        return PARTICLES.computeIfAbsent(id, key -> {
            int slash = key.indexOf('/');
            if (slash > 0) {
                Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(key.substring(slash + 1)));
                return Optional.of(new BlockParticleOption(ParticleTypes.FALLING_DUST, block.defaultBlockState()));
            }
            ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.tryParse(key));
            return type instanceof SimpleParticleType simple ? Optional.of(simple) : Optional.empty();
        });
    }
}
