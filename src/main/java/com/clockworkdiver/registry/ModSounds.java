package com.clockworkdiver.registry;

import com.clockworkdiver.ClockworkDiver;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ClockworkDiver.MODID);

    public static final RegistryObject<SoundEvent> TICK = reg("tick");
    public static final RegistryObject<SoundEvent> TOCK = reg("tock");
    public static final RegistryObject<SoundEvent> PERFECT = reg("perfect");
    public static final RegistryObject<SoundEvent> STEAM_BURST = reg("steam_burst");
    public static final RegistryObject<SoundEvent> SHRINK = reg("shrink");
    public static final RegistryObject<SoundEvent> GROW = reg("grow");
    public static final RegistryObject<SoundEvent> DISCOVER = reg("discover");
    public static final RegistryObject<SoundEvent> REPAIRED = reg("repaired");
    public static final RegistryObject<SoundEvent> RATCHET = reg("ratchet");
    public static final RegistryObject<SoundEvent> CLUNK = reg("clunk");
    public static final RegistryObject<SoundEvent> SHOP_BELL = reg("shop_bell");
    public static final RegistryObject<SoundEvent> GLASSES = reg("glasses");
    public static final RegistryObject<SoundEvent> MITE = reg("mite");
    public static final RegistryObject<SoundEvent> GREMLIN = reg("gremlin");
    public static final RegistryObject<SoundEvent> MOTH = reg("moth");
    public static final RegistryObject<SoundEvent> BOSS_ROAR = reg("boss_roar");
    public static final RegistryObject<SoundEvent> BOSS_PULSE = reg("boss_pulse");
    public static final RegistryObject<SoundEvent> FANFARE = reg("fanfare");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ClockworkDiver.id(name)));
    }
}
