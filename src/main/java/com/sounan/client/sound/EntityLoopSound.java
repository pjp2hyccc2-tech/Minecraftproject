package com.sounan.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.function.Predicate;

/** A looping sound that follows an entity (the bride's humming); fades out while {@code active} is false. */
public class EntityLoopSound extends AbstractTickableSoundInstance {
    private final Entity entity;
    private final Predicate<Entity> active;
    private final float maxVolume;

    public EntityLoopSound(SoundEvent event, Entity entity, float maxVolume, Predicate<Entity> active) {
        super(event, SoundSource.HOSTILE, RandomSource.create());
        this.entity = entity;
        this.active = active;
        this.maxVolume = maxVolume;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.x = entity.getX();
        this.y = entity.getY() + 1.7;
        this.z = entity.getZ();
    }

    public Entity entity() {
        return entity;
    }

    @Override
    public void tick() {
        if (entity.isRemoved()) {
            stop();
            return;
        }
        x = entity.getX();
        y = entity.getY() + 1.7;
        z = entity.getZ();
        float target = active.test(entity) ? maxVolume : 0.0f;
        volume += (target - volume) * (target > volume ? 0.04f : 0.18f);
        if (volume < 0.001f) volume = 0.001f;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
