package com.reisatsu.registry;

import com.reisatsu.Reisatsu;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Particle types (owner: game; textures and JSONs: art). docs/DESIGN.md §7 "Particles". */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Reisatsu.MODID);

    /** A flying laminated menu, tumbling. */
    public static final RegistryObject<SimpleParticleType> MENU_FLUTTER = PARTICLES.register("menu_flutter", () -> new SimpleParticleType(false));
    /** Tap water. */
    public static final RegistryObject<SimpleParticleType> WATER_DROP = PARTICLES.register("water_drop", () -> new SimpleParticleType(false));
    /** A footprint flat on the ground that fades (x speed = yaw in degrees). */
    public static final RegistryObject<SimpleParticleType> WET_PRINT = PARTICLES.register("wet_print", () -> new SimpleParticleType(true));
    /** The chef's embers. */
    public static final RegistryObject<SimpleParticleType> EMBER = PARTICLES.register("ember", () -> new SimpleParticleType(false));
    /** Pale motes when a spirit is bound / at the exorcism. */
    public static final RegistryObject<SimpleParticleType> SPIRIT_MOTE = PARTICLES.register("spirit_mote", () -> new SimpleParticleType(true));
    /** Your breath in the walk-in fridge. */
    public static final RegistryObject<SimpleParticleType> FROST_BREATH = PARTICLES.register("frost_breath", () -> new SimpleParticleType(false));
    /** Burning photos / お札. */
    public static final RegistryObject<SimpleParticleType> PAPER_ASH = PARTICLES.register("paper_ash", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
