package com.reisatsu.client;

import com.reisatsu.ReisatsuConfig;
import com.reisatsu.client.gui.BreakerScreen;
import com.reisatsu.client.gui.EndingScreen;
import com.reisatsu.client.gui.NoteScreen;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.game.GameManager;
import com.reisatsu.network.S2C;
import com.reisatsu.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;

/** Client side of the server messages. Only ever loaded on the physical client. */
public final class ClientPacketHandler {
    private static final RandomSource RAND = RandomSource.create();

    private ClientPacketHandler() {}

    public static void state(S2C.State s) {
        int oldPhase = ClientState.phase;
        ClientState.fear = s.fear();
        ClientState.danger = s.danger();
        ClientState.aware = s.aware();
        ClientState.flags = s.flags();
        ClientState.film = s.film();
        ClientState.ofuda = s.ofuda();
        ClientState.captured = s.captured();
        ClientState.submitted = s.submitted();
        ClientState.phase = s.phase();
        ClientState.reishou = s.reishou();
        ClientState.breath = s.breath();
        ClientState.circuits = s.closingMask();
        int oldStage = ClientState.nightStage, oldNight = ClientState.night;
        ClientState.night = s.night();
        ClientState.nightStage = s.nightStage();
        ClientState.signs = s.signs();
        if (oldPhase != s.phase() || oldStage != s.nightStage() || oldNight != s.night()) ClientState.objectiveTicks = 0;
        if (!ClientState.flag(GameManager.F_HIDING) && ClientState.lockerFacing >= 0 && ClientState.clientTicks % 20 == 0) ClientState.lockerFacing = -1;
    }

    public static void event(S2C.Event e) {
        Minecraft mc = Minecraft.getInstance();
        boolean calm = ReisatsuConfig.flashReduction();
        switch (e.type()) {
            case GameManager.EV_INTRO -> {
                if (e.arg() < 0) {
                    ClientState.introTicks = -1;
                    Dialogue.clear();
                } else {
                    ClientState.introTicks = 0;
                    ClientState.introSkippable = e.arg() == 1;
                    ClientState.introName = e.text();
                }
            }
            case GameManager.EV_JUMPSCARE -> {
                ClientState.jumpscareKind = e.arg();
                ClientState.jumpscareTicks = 0;
                ClientState.caughtTicks = -1;
                ClientState.shake(calm ? 1.0f : 5.0f, 24);
                Finder.reset();
                if (ReisatsuConfig.jumpscares()) ui(ModSounds.GAME_JUMPSCARE.get(), 1.0f, 0.95f + RAND.nextFloat() * 0.1f);
                else ui(ModSounds.GAME_STINGER.get(), 1.0f, 1.0f);
            }
            case GameManager.EV_CAUGHT -> {
                ClientState.jumpscareTicks = -1;
                ClientState.caughtTicks = 0;
                ui(ModSounds.GAME_CAUGHT.get(), 1.0f, 1.0f);
            }
            case GameManager.EV_WAKE -> {
                ClientState.caughtTicks = -1;
                ClientState.wakeTicks = 0;
                ClientState.wakeOfuda = e.arg();
                ui(ModSounds.GAME_WAKE.get(), 0.9f, 1.0f);
            }
            case GameManager.EV_AREA -> {
                ClientState.areaKey = e.text();
                ClientState.areaTicks = 0;
            }
            case GameManager.EV_SUBTITLE -> Dialogue.subtitle(e.text(), Math.max(40, e.arg()));
            case GameManager.EV_THOUGHT -> {
                ClientState.thought = e.text();
                ClientState.thoughtTicks = 0;
            }
            case GameManager.EV_HINT -> ClientState.hintTicks = 0;
            case GameManager.EV_CLOSING -> {
                ClientState.closingTicks = 0;
                ui(ModSounds.GAME_CLOSING.get(), 1.0f, 1.0f);
                ClientState.shake(calm ? 0.3f : 1.2f, 30);
            }
            case GameManager.EV_STINGER -> {
                ClientState.stingerTicks = 0;
                ui(ModSounds.GAME_STINGER.get(), 0.9f, 1.0f);
                ClientState.shake(calm ? 0.2f : 0.8f, 12);
            }
            case GameManager.EV_NOTICED -> {
                ClientState.noticedTicks = 0;
                ClientState.noticedKind = e.arg();
                ClientState.stingerTicks = 0;
                ClientState.shake(calm ? 0.3f : 1.4f, 14);
                if (!calm) ClientState.glitchTicks = 0;
            }
            case GameManager.EV_ENDING -> {
                ClientState.jumpscareTicks = -1;
                ClientState.caughtTicks = -1;
                Finder.reset();
                Dialogue.clear();
                mc.setScreen(new EndingScreen(e.arg(), e.text()));
            }
            case GameManager.EV_LEFT -> {
                ClientState.reset();
                Dialogue.clear();
                HorrorAudio.stopAll();
                Finder.reset();
                if (mc.screen instanceof EndingScreen) mc.setScreen(null);
            }
            case GameManager.EV_DAWN -> {
                ClientState.dawnTicks = 0;
                ui(ModSounds.GAME_DAWN.get(), 1.0f, 1.0f);
            }
            case GameManager.EV_FADE -> {
                ClientState.fadeTicks = 0;
                ClientState.fadeLength = Math.max(10, e.arg());
            }
            case GameManager.EV_SHAKE -> ClientState.shake((calm ? 0.3f : 1.0f) * e.arg() / 10.0f, 30);
            case GameManager.EV_OBJECTIVE -> ClientState.objectiveTicks = 0;
            case GameManager.EV_CAPTURED -> {
            }
            case GameManager.EV_LOCKER -> {
                ClientState.lockerFacing = e.arg() - 1;
                Finder.reset();
            }
            case GameManager.EV_SCREAM -> {
                ClientState.screamTicks = 0;
                ClientState.shake(calm ? 0.6f : 3.0f, 40);
                if (!calm) ClientState.glitchTicks = 0;
            }
            case GameManager.EV_GLITCH -> {
                if (!calm) ClientState.glitchTicks = 0;
            }
            case GameManager.EV_EXORCISM -> {
                ClientState.exorcismStage = e.arg();
                if (e.arg() <= 1) ClientState.exorcismTicks = 0;
                if (e.arg() == 2) {
                    ClientState.releasedSpirit = e.text();
                    ClientState.releaseTicks = 0;
                    ui(ModSounds.GAME_RELEASE.get(), 0.9f, 1.0f);
                }
            }
            case GameManager.EV_NIGHT -> {
                if (e.arg() > 0) {
                    // a new night: black, its title, the lot fading in; whatever cinematic was left is over
                    ClientState.nightCard = e.arg();
                    ClientState.nightCardTicks = 0;
                    ClientState.blackoutTicks = -1;
                    ClientState.exorcismStage = ClientState.exorcismTicks = ClientState.dawnTicks = ClientState.releaseTicks = -1;
                    ClientState.closingTicks = ClientState.fadeTicks = ClientState.wakeTicks = ClientState.screamTicks = -1;
                    ClientState.thoughtTicks = ClientState.witnessTicks = ClientState.submitTicks = -1;
                    ClientState.introTicks = -1;
                    Dialogue.clear();
                    PhotoView.dismiss();
                    Finder.reset();
                    ui(ModSounds.GAME_TITLE_HIT.get(), 0.55f, 0.8f);
                } else {
                    // her lines are over (or skipped)
                    Dialogue.clear();
                    if (ClientState.nightCardTicks >= 0) ClientState.nightCardTicks = Math.max(ClientState.nightCardTicks, ClientState.NIGHT_CARD_END - 20);
                }
            }
            case GameManager.EV_BLACKOUT -> {
                ClientState.blackoutTicks = 0;
                ClientState.blackoutFade = Math.max(5, e.arg());
            }
            case GameManager.EV_WITNESS -> {
                ClientState.witnessKey = "witness.reisatsu." + e.text();
                ClientState.witnessTicks = 0;
                ClientState.objectiveTicks = 0;
                ui(ModSounds.ITEM_PAPER.get(), 0.35f, 1.3f);
            }
            case GameManager.EV_MANIFEST -> {
                ClientState.manifestTicks = 0;
                ClientState.objectiveTicks = 0;
                ui(ModSounds.GAME_RISER.get(), 0.45f, 1.0f);
            }
            case GameManager.EV_SUBMIT -> {
                ClientState.submitKind = e.arg();
                ClientState.submitTicks = 0;
                ui(ModSounds.PHOTO_NAME.get(), 0.6f, 0.9f);
            }
            default -> {
            }
        }
    }

    public static void photoResult(S2C.PhotoResult r) {
        PhotoCapture.onResult(r.id(), r.result(), SpiritKind.byIndex(r.spirit()), r.stars(), r.ghost());
    }

    public static void album(S2C.Album a) {
        ClientState.PHOTOS.clear();
        ClientState.PHOTOS.addAll(a.photos());
        ClientState.NOTES.clear();
        ClientState.NOTES.addAll(a.notes());
    }

    public static void note(S2C.Note n) {
        if (!ClientState.NOTES.contains(n.id())) ClientState.NOTES.add(n.id());
        Minecraft.getInstance().setScreen(new NoteScreen(n.id(), n.extra()));
    }

    public static void dialogue(S2C.Dialogue d) {
        Dialogue.play(d.speaker(), d.lines(), d.ticks());
    }

    public static void breaker(S2C.Breaker b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BreakerScreen bs) bs.update(b.on());
        else mc.setScreen(new BreakerScreen(b.on()));
    }

    public static void sound(S2C.Sound s) {
        Minecraft mc = Minecraft.getInstance();
        SoundEvent ev = event(s.id());
        mc.getSoundManager().play(new SimpleSoundInstance(ev, SoundSource.AMBIENT, s.volume(), s.pitch(), RAND, s.x(), s.y(), s.z()));
    }

    public static SoundEvent event(String id) {
        ResourceLocation rl = new ResourceLocation(id);
        SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(rl);
        return ev != null ? ev : SoundEvent.createVariableRangeEvent(rl);
    }

    public static void ui(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** A sound placed relative to the listener (whispers at one ear, things behind you). */
    public static void near(SoundEvent sound, double dx, double dy, double dz, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.AMBIENT, volume, pitch, RAND, false, 0,
                SoundInstance.Attenuation.LINEAR, mc.player.getX() + dx, mc.player.getEyeY() + dy, mc.player.getZ() + dz, false));
    }
}
