package jp.satsujinki.registry;

import jp.satsujinki.Satsujinki;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Satsujinki.MODID);

    public static final RegistryObject<SoundEvent> MIXER_START = reg("mixer.start");
    public static final RegistryObject<SoundEvent> MIXER_LOOP = reg("mixer.loop");
    public static final RegistryObject<SoundEvent> MIXER_GRIND = reg("mixer.grind");
    public static final RegistryObject<SoundEvent> MIXER_STOP = reg("mixer.stop");
    public static final RegistryObject<SoundEvent> PRESS_CHARGE = reg("press.charge");
    public static final RegistryObject<SoundEvent> PRESS_SLAM = reg("press.slam");
    public static final RegistryObject<SoundEvent> PRESS_RETRACT = reg("press.retract");
    public static final RegistryObject<SoundEvent> SAW_LOOP = reg("saw.loop");
    public static final RegistryObject<SoundEvent> SAW_CUT = reg("saw.cut");
    public static final RegistryObject<SoundEvent> GUILLOTINE_DROP = reg("guillotine.drop");
    public static final RegistryObject<SoundEvent> GUILLOTINE_CHOP = reg("guillotine.chop");
    public static final RegistryObject<SoundEvent> GUILLOTINE_LOCK = reg("guillotine.lock");
    public static final RegistryObject<SoundEvent> GORE_SPLAT = reg("gore.splat");
    public static final RegistryObject<SoundEvent> GORE_CRUNCH = reg("gore.crunch");
    public static final RegistryObject<SoundEvent> GORE_SQUELCH = reg("gore.squelch");
    public static final RegistryObject<SoundEvent> GORE_SPRAY = reg("gore.spray");
    public static final RegistryObject<SoundEvent> GORE_GIB_LAND = reg("gore.gib_land");
    public static final RegistryObject<SoundEvent> SPIKE_IMPALE = reg("gore.impale");
    public static final RegistryObject<SoundEvent> SCREAM_MALE = reg("npc.scream_male");
    public static final RegistryObject<SoundEvent> SCREAM_FEMALE = reg("npc.scream_female");
    public static final RegistryObject<SoundEvent> PANIC_MALE = reg("npc.panic_male");
    public static final RegistryObject<SoundEvent> PANIC_FEMALE = reg("npc.panic_female");
    public static final RegistryObject<SoundEvent> BEG_MALE = reg("npc.beg_male");
    public static final RegistryObject<SoundEvent> BEG_FEMALE = reg("npc.beg_female");
    public static final RegistryObject<SoundEvent> HURT_MALE = reg("npc.hurt_male");
    public static final RegistryObject<SoundEvent> HURT_FEMALE = reg("npc.hurt_female");
    public static final RegistryObject<SoundEvent> DEATH_MALE = reg("npc.death_male");
    public static final RegistryObject<SoundEvent> DEATH_FEMALE = reg("npc.death_female");
    public static final RegistryObject<SoundEvent> FIGHT = reg("npc.fight");
    public static final RegistryObject<SoundEvent> HOST_GRAB = reg("host.grab");
    public static final RegistryObject<SoundEvent> HOST_THROW = reg("host.throw");
    public static final RegistryObject<SoundEvent> HOST_REMOTE = reg("host.remote");
    public static final RegistryObject<SoundEvent> UI_KILL = reg("ui.kill");
    public static final RegistryObject<SoundEvent> UI_COMBO = reg("ui.combo");
    public static final RegistryObject<SoundEvent> UI_RANKUP = reg("ui.rankup");
    public static final RegistryObject<SoundEvent> UI_ANNOUNCE = reg("ui.announce");
    public static final RegistryObject<SoundEvent> UI_COUNTDOWN = reg("ui.countdown");
    public static final RegistryObject<SoundEvent> UI_START = reg("ui.start");
    public static final RegistryObject<SoundEvent> UI_GAMEOVER = reg("ui.gameover");
    public static final RegistryObject<SoundEvent> UI_BUY = reg("ui.buy");
    public static final RegistryObject<SoundEvent> UI_R18 = reg("ui.r18");
    public static final RegistryObject<SoundEvent> MOP_CLEAN = reg("mop.clean");
    public static final RegistryObject<SoundEvent> SPAWN_POOF = reg("npc.spawn");
    public static final RegistryObject<SoundEvent> MUSIC_DEATHGAME = reg("music.deathgame");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Satsujinki.id(name)));
    }
}
