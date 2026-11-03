package com.reisatsu.registry;

import com.reisatsu.Reisatsu;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Particle types (owner: game; textures and JSONs: art). */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Reisatsu.MODID);

    private ModParticles() {}
}
