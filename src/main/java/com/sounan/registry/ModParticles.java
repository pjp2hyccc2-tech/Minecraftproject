package com.sounan.registry;

import com.sounan.Sounan;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Sounan.MODID);

    /** The glow a headlamp makes in the fog (drawn far beyond the normal particle distance). */
    public static final RegistryObject<SimpleParticleType> LAMP_GLOW = PARTICLES.register("lamp_glow", () -> new SimpleParticleType(true));

    private ModParticles() {}
}
