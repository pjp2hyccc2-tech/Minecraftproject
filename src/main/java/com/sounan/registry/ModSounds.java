package com.sounan.registry;

import com.sounan.Sounan;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every sound event of the mod (files and variants are defined in assets/meikon/sounds.json). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Sounan.MODID);

    public static final RegistryObject<SoundEvent> AMBIENT_DRONE = reg("ambient.drone");
    public static final RegistryObject<SoundEvent> AMBIENT_DRONE_TENSE = reg("ambient.drone_tense");
    public static final RegistryObject<SoundEvent> AMBIENT_WIND = reg("ambient.wind");
    public static final RegistryObject<SoundEvent> AMBIENT_KOTO = reg("ambient.koto");
    public static final RegistryObject<SoundEvent> AMBIENT_CREAK = reg("ambient.creak");
    public static final RegistryObject<SoundEvent> AMBIENT_FOOTSTEPS_ABOVE = reg("ambient.footsteps_above");
    public static final RegistryObject<SoundEvent> AMBIENT_KNOCK = reg("ambient.knock");
    public static final RegistryObject<SoundEvent> AMBIENT_WHISPER = reg("ambient.whisper");
    public static final RegistryObject<SoundEvent> AMBIENT_DOOR_SLAM = reg("ambient.door_slam");
    public static final RegistryObject<SoundEvent> AMBIENT_MUSIC_BOX = reg("ambient.music_box");
    public static final RegistryObject<SoundEvent> AMBIENT_CRYING = reg("ambient.crying");
    public static final RegistryObject<SoundEvent> AMBIENT_CLOCK_CHIME = reg("ambient.clock_chime");
    public static final RegistryObject<SoundEvent> AMBIENT_TEMPLE_BELL = reg("ambient.temple_bell");
    public static final RegistryObject<SoundEvent> AMBIENT_DRIP = reg("ambient.drip");
    public static final RegistryObject<SoundEvent> AMBIENT_SCRATCH = reg("ambient.scratch");
    public static final RegistryObject<SoundEvent> AMBIENT_THUD = reg("ambient.thud");
    public static final RegistryObject<SoundEvent> AMBIENT_TINNITUS = reg("ambient.tinnitus");
    public static final RegistryObject<SoundEvent> BRIDE_HUM = reg("bride.hum");
    public static final RegistryObject<SoundEvent> BRIDE_STEP = reg("bride.step");
    public static final RegistryObject<SoundEvent> BRIDE_SCREECH = reg("bride.screech");
    public static final RegistryObject<SoundEvent> BRIDE_BREATH = reg("bride.breath");
    public static final RegistryObject<SoundEvent> BRIDE_RUSTLE = reg("bride.rustle");
    public static final RegistryObject<SoundEvent> BRIDE_LAUGH = reg("bride.laugh");
    public static final RegistryObject<SoundEvent> BRIDE_DOOR = reg("bride.door");
    public static final RegistryObject<SoundEvent> DOLL_MOVE = reg("doll.move");
    public static final RegistryObject<SoundEvent> DOLL_HEAD_TURN = reg("doll.head_turn");
    public static final RegistryObject<SoundEvent> DOLL_ATTACK = reg("doll.attack");
    public static final RegistryObject<SoundEvent> SHADOW_WHOOSH = reg("shadow.whoosh");
    public static final RegistryObject<SoundEvent> SHADOW_DRONE = reg("shadow.drone");
    public static final RegistryObject<SoundEvent> PLAYER_HEARTBEAT = reg("player.heartbeat");
    public static final RegistryObject<SoundEvent> PLAYER_BREATH_FEAR = reg("player.breath_fear");
    public static final RegistryObject<SoundEvent> PLAYER_GASP = reg("player.gasp");
    public static final RegistryObject<SoundEvent> PLAYER_BREATH_EXHAUSTED = reg("player.breath_exhausted");
    public static final RegistryObject<SoundEvent> GAME_BELL_CHIME = reg("game.bell_chime");
    public static final RegistryObject<SoundEvent> GAME_BELL_GET = reg("game.bell_get");
    public static final RegistryObject<SoundEvent> GAME_JUMPSCARE = reg("game.jumpscare");
    public static final RegistryObject<SoundEvent> GAME_STINGER = reg("game.stinger");
    public static final RegistryObject<SoundEvent> GAME_CHASE = reg("game.chase");
    public static final RegistryObject<SoundEvent> GAME_PHASE = reg("game.phase");
    public static final RegistryObject<SoundEvent> GAME_DEATH = reg("game.death");
    public static final RegistryObject<SoundEvent> GAME_FUSUMA = reg("game.fusuma");
    public static final RegistryObject<SoundEvent> GAME_CLOSET = reg("game.closet");
    public static final RegistryObject<SoundEvent> GAME_LANTERN_ON = reg("game.lantern_on");
    public static final RegistryObject<SoundEvent> GAME_LANTERN_OFF = reg("game.lantern_off");
    public static final RegistryObject<SoundEvent> GAME_PAPER = reg("game.paper");
    public static final RegistryObject<SoundEvent> GAME_OFUDA_BURN = reg("game.ofuda_burn");
    public static final RegistryObject<SoundEvent> GAME_SALT = reg("game.salt");
    public static final RegistryObject<SoundEvent> GAME_MARBLE = reg("game.marble");
    public static final RegistryObject<SoundEvent> GAME_DRAWER = reg("game.drawer");
    public static final RegistryObject<SoundEvent> GAME_UNLOCK = reg("game.unlock");
    public static final RegistryObject<SoundEvent> GAME_CANDLE = reg("game.candle");
    public static final RegistryObject<SoundEvent> GAME_PUZZLE_FAIL = reg("game.puzzle_fail");
    public static final RegistryObject<SoundEvent> GAME_WELL = reg("game.well");
    public static final RegistryObject<SoundEvent> GAME_GATE_OPEN = reg("game.gate_open");
    public static final RegistryObject<SoundEvent> GAME_RITUAL = reg("game.ritual");
    public static final RegistryObject<SoundEvent> MUSIC_TITLE = reg("music.title");
    public static final RegistryObject<SoundEvent> MUSIC_GAGAKU = reg("music.gagaku");
    public static final RegistryObject<SoundEvent> MUSIC_ENDING = reg("music.ending");
    public static final RegistryObject<SoundEvent> MUSIC_BAD_ENDING = reg("music.bad_ending");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Sounan.id(name)));
    }
}
