package com.eternalrealm.client.block;

import com.eternalrealm.block.forge.LightShaftBlock;
import com.eternalrealm.block.forge.LightShaftBlockEntity;
import com.eternalrealm.combat.forge.ERParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Dust drifting in the sunbeams near the player (also with a shader pack: the dust is ours either way). */
public final class ClientLightShafts {
    private static final Set<LightShaftBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final RandomSource RANDOM = RandomSource.create();

    private ClientLightShafts() {
    }

    public static void onLoaded(LightShaftBlockEntity beam) {
        LOADED.add(beam);
    }

    /** Client tick. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || LOADED.isEmpty() || minecraft.isPaused()) {
            return;
        }
        LOADED.removeIf(beam -> beam.isRemoved() || beam.getLevel() != minecraft.level);
        float daylight = LightShaftRenderer.daylight(minecraft.level, 0.0F);
        if (daylight < 0.1F) {
            return;
        }
        Vec3 eye = minecraft.player.getEyePosition();
        for (LightShaftBlockEntity beam : LOADED) {
            if (beam.getBlockPos().distToCenterSqr(eye) > 40.0 * 40.0
                    || minecraft.level.getBrightness(LightLayer.SKY, beam.getBlockPos()) < 8) {
                continue;
            }
            float chance = 0.06F * beam.width() * beam.height() * beam.length() / 30.0F * daylight;
            while (chance > 0.0F) {
                if (RANDOM.nextFloat() < chance) {
                    spawn(minecraft, beam);
                }
                chance -= 1.0F;
            }
        }
    }

    private static void spawn(Minecraft minecraft, LightShaftBlockEntity beam) {
        Direction facing = beam.getBlockState().getValue(LightShaftBlock.FACING);
        Vec3 forward = new Vec3(facing.getStepX(), 0.0, facing.getStepZ());
        Vec3 side = new Vec3(-facing.getStepZ(), 0.0, facing.getStepX());
        double pitch = Math.toRadians(beam.pitch());
        Vec3 direction = forward.scale(Math.cos(pitch)).add(0.0, -Math.sin(pitch), 0.0);
        double t = RANDOM.nextDouble() * beam.length() * 0.9;
        Vec3 point = Vec3.atCenterOf(beam.getBlockPos()).add(direction.scale(t))
                .add(side.scale((RANDOM.nextDouble() - 0.5) * beam.width()))
                .add(0.0, (RANDOM.nextDouble() - 0.5) * beam.height(), 0.0);
        minecraft.level.addParticle(ERParticles.MOTE.get(), point.x, point.y, point.z,
                (RANDOM.nextDouble() - 0.5) * 0.004, (RANDOM.nextDouble() - 0.3) * 0.003,
                (RANDOM.nextDouble() - 0.5) * 0.004);
    }

    public static void clear() {
        LOADED.clear();
    }
}
