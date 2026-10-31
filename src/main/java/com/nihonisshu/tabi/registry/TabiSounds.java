package com.nihonisshu.tabi.registry;

import com.nihonisshu.tabi.TabiMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class TabiSounds {
    private TabiSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TabiMod.MODID);

    public static final RegistryObject<SoundEvent> MELODY = reg("melody");
    public static final RegistryObject<SoundEvent> DOOR_CHIME = reg("door_chime");
    public static final RegistryObject<SoundEvent> TRAIN_RUN = reg("train_run");
    public static final RegistryObject<SoundEvent> TRAIN_JOINT = reg("train_joint");
    public static final RegistryObject<SoundEvent> BUS_RUN = reg("bus_run");
    public static final RegistryObject<SoundEvent> BUS_BELL = reg("bus_bell");
    public static final RegistryObject<SoundEvent> EVENING_CHIME = reg("evening_chime");
    public static final RegistryObject<SoundEvent> CICADA = reg("cicada");
    public static final RegistryObject<SoundEvent> HIGURASHI = reg("higurashi");
    public static final RegistryObject<SoundEvent> FROGS = reg("frogs");
    public static final RegistryObject<SoundEvent> WAVES = reg("waves");
    public static final RegistryObject<SoundEvent> TALK = reg("talk");
    public static final RegistryObject<SoundEvent> COIN = reg("coin");
    public static final RegistryObject<SoundEvent> TICKET_GATE = reg("ticket_gate");
    public static final RegistryObject<SoundEvent> MELODY_OKAWA = reg("melody_okawa");
    public static final RegistryObject<SoundEvent> MELODY_KIRIGAYA = reg("melody_kirigaya");
    public static final RegistryObject<SoundEvent> MELODY_SHIOMI = reg("melody_shiomi");
    public static final RegistryObject<SoundEvent> MELODY_KOBE = reg("melody_kobe");
    public static final RegistryObject<SoundEvent> APPROACH = reg("approach");
    public static final RegistryObject<SoundEvent> CAR_CHIME = reg("car_chime");
    public static final RegistryObject<SoundEvent> GUIDE_CHIME = reg("guide_chime");
    public static final RegistryObject<SoundEvent> DOOR_OPEN = reg("door_open");
    public static final RegistryObject<SoundEvent> DOOR_CLOSE = reg("door_close");
    public static final RegistryObject<SoundEvent> HORN = reg("horn");
    public static final RegistryObject<SoundEvent> BRAKE = reg("brake");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(TabiMod.MODID, name)));
    }
}
