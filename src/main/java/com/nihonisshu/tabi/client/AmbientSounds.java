package com.nihonisshu.tabi.client;

import com.nihonisshu.tabi.registry.TabiSounds;
import com.nihonisshu.tabi.registry.TabiWorldgen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;

/** Summer soundscape: cicadas by day, higurashi at dusk, frogs over the paddies at night, waves on the coast. */
public final class AmbientSounds {
    private AmbientSounds() {}

    private static final RandomSource RAND = RandomSource.create();
    private static int cooldown = 60;
    private static int waveCooldown = 40;

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (mc.level.dimension() != TabiWorldgen.JAPAN) return;
        BlockPos p = mc.player.blockPosition();
        long t = Math.floorMod(mc.level.getDayTime(), 24000L);
        float hour = ((t / 1000f) + 6f) % 24f;
        boolean outdoors = mc.level.getBrightness(LightLayer.SKY, p) >= 10;
        boolean raining = mc.level.isRaining();
        String biome = mc.level.getBiome(p).unwrapKey().map(k -> k.location().getPath()).orElse("");
        boolean countryside = biome.equals("satoyama");

        if (--waveCooldown <= 0) {
            waveCooldown = 90 + RAND.nextInt(60);
            if (biome.equals("seto_sea") || nearWater(mc, p)) {
                play(mc, TabiSounds.WAVES.get(), 0.55f, 0.9f + RAND.nextFloat() * 0.2f, 12);
            }
        }
        if (--cooldown > 0) return;
        cooldown = 160 + RAND.nextInt(240);
        if (!outdoors || raining) return;
        if (countryside || biome.equals("kobe") && hour > 9 && hour < 16) {
            if (hour >= 8.5f && hour < 16.5f) play(mc, TabiSounds.CICADA.get(), countryside ? 0.5f : 0.25f, 0.95f + RAND.nextFloat() * 0.1f, 10);
            else if (hour >= 16.5f && hour < 19.2f && countryside) play(mc, TabiSounds.HIGURASHI.get(), 0.45f, 0.95f + RAND.nextFloat() * 0.1f, 14);
            else if ((hour >= 19.5f || hour < 3.5f) && countryside && mc.player.getX() < 1500) play(mc, TabiSounds.FROGS.get(), 0.5f, 0.9f + RAND.nextFloat() * 0.2f, 8);
        }
    }

    private static boolean nearWater(Minecraft mc, BlockPos p) {
        for (int i = 0; i < 6; i++) {
            BlockPos q = p.offset(RAND.nextInt(25) - 12, -2 - RAND.nextInt(4), RAND.nextInt(25) - 12);
            if (mc.level.getFluidState(q).isSource() && q.getY() <= 62 && mc.level.getBrightness(LightLayer.SKY, q.above()) > 10 && mc.player.getY() < 75) {
                return mc.level.getBiome(q).unwrapKey().map(k -> k.location().getPath().equals("seto_sea")).orElse(false);
            }
        }
        return false;
    }

    private static void play(Minecraft mc, SoundEvent ev, float vol, float pitch, int spread) {
        double x = mc.player.getX() + (RAND.nextDouble() - 0.5) * 2 * spread;
        double y = mc.player.getY() + 2 + RAND.nextDouble() * 3;
        double z = mc.player.getZ() + (RAND.nextDouble() - 0.5) * 2 * spread;
        mc.getSoundManager().play(new SimpleSoundInstance(ev, SoundSource.AMBIENT, vol, pitch, RAND, x, y, z));
    }
}
