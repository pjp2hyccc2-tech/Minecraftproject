package com.birdiecraft.registry;

import com.birdiecraft.BirdieCraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, BirdieCraft.MODID);

    public static final RegistryObject<SoundEvent> HIT_DRIVER = register("hit_driver");
    public static final RegistryObject<SoundEvent> HIT_IRON = register("hit_iron");
    public static final RegistryObject<SoundEvent> HIT_PUTT = register("hit_putt");
    public static final RegistryObject<SoundEvent> BALL_BOUNCE = register("ball_bounce");
    public static final RegistryObject<SoundEvent> CUP_IN = register("cup_in");
    public static final RegistryObject<SoundEvent> GAUGE_TICK = register("gauge_tick");
    public static final RegistryObject<SoundEvent> PERFECT = register("perfect");
    public static final RegistryObject<SoundEvent> FANFARE_SMALL = register("fanfare_small");
    public static final RegistryObject<SoundEvent> FANFARE_BIG = register("fanfare_big");
    public static final RegistryObject<SoundEvent> APPLAUSE = register("applause");
    public static final RegistryObject<SoundEvent> GROAN = register("groan");
    public static final RegistryObject<SoundEvent> WHOOSH = register("whoosh");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(BirdieCraft.id(name)));
    }

    private ModSounds() {
    }
}
