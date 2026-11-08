package com.eternalrealm.combat.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's particles (textures and frame lists from tools/art: {@code particles/<name>.json}). Their look and motion
 * live in the client provider ({@code client/ERParticleProviders}).
 */
public final class ERParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ModInfo.MOD_ID);

    /** Rising sparks of fire skills. */
    public static final RegistryObject<SimpleParticleType> EMBER = register("ember");
    /** Ice crystals of frost skills. */
    public static final RegistryObject<SimpleParticleType> FROST = register("frost");
    /** Green crosses of healing. */
    public static final RegistryObject<SimpleParticleType> HEAL = register("heal");
    /** Golden light of holy skills. */
    public static final RegistryObject<SimpleParticleType> HOLY = register("holy");
    /** Arcane runes of mages and spellblades. */
    public static final RegistryObject<SimpleParticleType> RUNE = register("rune");
    /** Dark wisps of assassins and the abyss. */
    public static final RegistryObject<SimpleParticleType> SHADOW = register("shadow");
    /** A blade arc on melee hits. */
    public static final RegistryObject<SimpleParticleType> SLASH = register("slash");
    /** Electric sparks (critical hits, thunder). */
    public static final RegistryObject<SimpleParticleType> SPARK = register("spark");
    /** Dust drifting in sunbeams (capital interiors). */
    public static final RegistryObject<SimpleParticleType> MOTE = register("mote");

    private ERParticles() {
    }

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }

    private static RegistryObject<SimpleParticleType> register(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }
}
