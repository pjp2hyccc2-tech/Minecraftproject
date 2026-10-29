package com.huntcraft.gear.weapon;

import com.huntcraft.HuntCraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Weapon sound events (definitions in src/main/sounds/weapons.json, built from vanilla sound files). */
public final class WeaponSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HuntCraft.MODID);

    private WeaponSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HuntCraft.id(name)));
    }

    public static final RegistryObject<SoundEvent> SWING_HEAVY = reg("weapon.swing_heavy");
    public static final RegistryObject<SoundEvent> SWING_LIGHT = reg("weapon.swing_light");
    public static final RegistryObject<SoundEvent> SLAM = reg("weapon.slam");
    public static final RegistryObject<SoundEvent> GUARD = reg("weapon.guard");
    public static final RegistryObject<SoundEvent> SPIRIT_UP = reg("weapon.spirit_up");
    public static final RegistryObject<SoundEvent> SPIRIT_SLASH = reg("weapon.spirit_slash");
    public static final RegistryObject<SoundEvent> FORESIGHT = reg("weapon.foresight");
    public static final RegistryObject<SoundEvent> DEMON_ON = reg("weapon.demon_on");
    public static final RegistryObject<SoundEvent> DEMON_OFF = reg("weapon.demon_off");
    public static final RegistryObject<SoundEvent> BLADE_DANCE = reg("weapon.blade_dance");
    public static final RegistryObject<SoundEvent> BASH = reg("weapon.bash");
    public static final RegistryObject<SoundEvent> COUNTER = reg("weapon.counter");
    public static final RegistryObject<SoundEvent> DASH = reg("weapon.dash");
    public static final RegistryObject<SoundEvent> HORN_NOTE1 = reg("weapon.horn_note1");
    public static final RegistryObject<SoundEvent> HORN_NOTE2 = reg("weapon.horn_note2");
    public static final RegistryObject<SoundEvent> HORN_NOTE3 = reg("weapon.horn_note3");
    public static final RegistryObject<SoundEvent> HORN_SONG = reg("weapon.horn_song");
    public static final RegistryObject<SoundEvent> BOW_SHOT = reg("weapon.bow_shot");
    public static final RegistryObject<SoundEvent> BOW_DRAW = reg("weapon.bow_draw");
    public static final RegistryObject<SoundEvent> COAT_SWITCH = reg("weapon.coat_switch");
    public static final RegistryObject<SoundEvent> SHARPEN_DONE = reg("weapon.sharpen_done");
    // combat animation sounds (src/main/sounds/combat.json)
    public static final RegistryObject<SoundEvent> SHEATHE = reg("combat.sheathe");
    public static final RegistryObject<SoundEvent> UNSHEATHE = reg("combat.unsheathe");
    public static final RegistryObject<SoundEvent> KICK = reg("combat.kick");
    public static final RegistryObject<SoundEvent> HIT_SLASH = reg("combat.hit_slash");
    public static final RegistryObject<SoundEvent> HIT_BLUNT = reg("combat.hit_blunt");
    public static final RegistryObject<SoundEvent> KNOCKDOWN = reg("combat.knockdown");
}
