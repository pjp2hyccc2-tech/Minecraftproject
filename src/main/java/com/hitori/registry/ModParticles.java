package com.hitori.registry;

import com.hitori.Hitori;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Particle types (owner: game; textures and JSONs: art). docs/DESIGN.md §7 "Particles". */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Hitori.MODID);

    /** Grains of rice spilling (filling the plush, the knife wound). */
    public static final RegistryObject<SimpleParticleType> RICE_GRAIN = PARTICLES.register("rice_grain", () -> new SimpleParticleType(false));
    /** Tufts of stuffing pulled out of the plush. */
    public static final RegistryObject<SimpleParticleType> STUFFING = PARTICLES.register("stuffing", () -> new SimpleParticleType(false));
    /** Water dripping from the doll, the tap. */
    public static final RegistryObject<SimpleParticleType> WATER_DRIP = PARTICLES.register("water_drip", () -> new SimpleParticleType(false));
    /** Salt water spat, salt poured. */
    public static final RegistryObject<SimpleParticleType> SALT_SPRAY = PARTICLES.register("salt_spray", () -> new SimpleParticleType(false));
    /** Embers rising from the burning doll. */
    public static final RegistryObject<SimpleParticleType> EMBER = PARTICLES.register("ember", () -> new SimpleParticleType(false));
    /** Smoke of the burning doll (the lighter). */
    public static final RegistryObject<SimpleParticleType> SMOKE = PARTICLES.register("smoke", () -> new SimpleParticleType(false));
    /** Pale motes in the CRT's static light. */
    public static final RegistryObject<SimpleParticleType> TV_STATIC_MOTE = PARTICLES.register("tv_static_mote", () -> new SimpleParticleType(true));

    private ModParticles() {}
}
