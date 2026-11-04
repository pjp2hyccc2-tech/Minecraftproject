package jp.satsujinki.registry;

import jp.satsujinki.Satsujinki;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Satsujinki.MODID);

    public static final RegistryObject<SimpleParticleType> BLOOD_DROP = PARTICLES.register("blood_drop", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> BLOOD_SPLAT = PARTICLES.register("blood_splat", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> BLOOD_MIST = PARTICLES.register("blood_mist", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> GORE_BIT = PARTICLES.register("gore_bit", () -> new SimpleParticleType(true));

    private ModParticles() {
    }
}
