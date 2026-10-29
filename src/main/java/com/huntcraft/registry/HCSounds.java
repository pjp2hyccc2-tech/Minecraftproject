package com.huntcraft.registry;

import com.huntcraft.HuntCraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Core sound events. sounds.json is assembled at build time from src/main/sounds/*.json
 * (each module may add its own fragment file).
 */
public final class HCSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HuntCraft.MODID);

    private HCSounds() {
    }

    public static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HuntCraft.id(name)));
    }

    // monsters (generic, usable by any monster)
    public static final RegistryObject<SoundEvent> ROAR_LARGE = register("monster.roar_large");
    public static final RegistryObject<SoundEvent> ROAR_DRAGON = register("monster.roar_dragon");
    public static final RegistryObject<SoundEvent> ROAR_BEAST = register("monster.roar_beast");
    public static final RegistryObject<SoundEvent> GROWL = register("monster.growl");
    public static final RegistryObject<SoundEvent> SMALL_CALL = register("monster.small_call");
    public static final RegistryObject<SoundEvent> STOMP = register("monster.stomp");
    public static final RegistryObject<SoundEvent> TAIL_SWING = register("monster.tail_swing");
    public static final RegistryObject<SoundEvent> BITE = register("monster.bite");
    public static final RegistryObject<SoundEvent> WING_FLAP = register("monster.wing_flap");
    public static final RegistryObject<SoundEvent> FIRE_BREATH = register("monster.fire_breath");
    public static final RegistryObject<SoundEvent> ICE_BREATH = register("monster.ice_breath");
    public static final RegistryObject<SoundEvent> THUNDER_CRACK = register("monster.thunder_crack");
    public static final RegistryObject<SoundEvent> WATER_JET = register("monster.water_jet");
    public static final RegistryObject<SoundEvent> DRAGON_BEAM = register("monster.dragon_beam");
    public static final RegistryObject<SoundEvent> EXPLOSION_SMALL = register("monster.explosion_small");
    public static final RegistryObject<SoundEvent> HURT_LARGE = register("monster.hurt_large");
    public static final RegistryObject<SoundEvent> DEATH_LARGE = register("monster.death_large");
    public static final RegistryObject<SoundEvent> HURT_SMALL = register("monster.hurt_small");
    public static final RegistryObject<SoundEvent> DEATH_SMALL = register("monster.death_small");
    public static final RegistryObject<SoundEvent> ENRAGE = register("monster.enrage");
    public static final RegistryObject<SoundEvent> TIRED = register("monster.tired");
    // hunting systems
    public static final RegistryObject<SoundEvent> PART_BREAK = register("hunt.part_break");
    public static final RegistryObject<SoundEvent> TAIL_CUT = register("hunt.tail_cut");
    public static final RegistryObject<SoundEvent> CARVE = register("hunt.carve");
    public static final RegistryObject<SoundEvent> CAPTURE = register("hunt.capture");
    public static final RegistryObject<SoundEvent> TRAP_SET = register("hunt.trap_set");
    public static final RegistryObject<SoundEvent> TRAP_TRIGGER = register("hunt.trap_trigger");
    public static final RegistryObject<SoundEvent> FLASH = register("hunt.flash");
    public static final RegistryObject<SoundEvent> PAINT = register("hunt.paint");
    public static final RegistryObject<SoundEvent> DRINK = register("hunt.drink");
    public static final RegistryObject<SoundEvent> SHARPEN = register("hunt.sharpen");
    public static final RegistryObject<SoundEvent> BOUNCE = register("hunt.bounce");
    public static final RegistryObject<SoundEvent> HIT_HEAVY = register("hunt.hit_heavy");
    public static final RegistryObject<SoundEvent> HIT_WEAK = register("hunt.hit_weak");
    public static final RegistryObject<SoundEvent> CHARGE = register("hunt.charge");
    public static final RegistryObject<SoundEvent> CHARGE_MAX = register("hunt.charge_max");
    public static final RegistryObject<SoundEvent> DODGE = register("hunt.dodge");
    public static final RegistryObject<SoundEvent> ZENNY = register("hunt.zenny");
    public static final RegistryObject<SoundEvent> KO = register("hunt.ko");
    // jingles (original synthesized music)
    public static final RegistryObject<SoundEvent> QUEST_ACCEPT = register("jingle.quest_accept");
    public static final RegistryObject<SoundEvent> QUEST_START = register("jingle.quest_start");
    public static final RegistryObject<SoundEvent> QUEST_CLEAR = register("jingle.quest_clear");
    public static final RegistryObject<SoundEvent> QUEST_FAIL = register("jingle.quest_fail");
    public static final RegistryObject<SoundEvent> MEAT_ROAST = register("jingle.meat_roast");
    public static final RegistryObject<SoundEvent> MEAT_DONE = register("jingle.meat_done");
    public static final RegistryObject<SoundEvent> LEVEL_UP = register("jingle.level_up");
}
