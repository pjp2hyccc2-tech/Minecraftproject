package com.meikon.client;

import com.meikon.MeikonConfig;
import com.meikon.client.gui.EndingScreen;
import com.meikon.client.gui.EnvelopeScreen;
import com.meikon.client.gui.NoteScreen;
import com.meikon.client.gui.SakazukiScreen;
import com.meikon.game.ClientView;
import com.meikon.game.GameManager;
import com.meikon.network.S2C;
import com.meikon.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;

/** Client side of the server messages. Only ever loaded on the physical client. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void state(S2C.State s) {
        ClientState.fear = s.fear();
        ClientState.stamina = s.stamina();
        ClientState.breath = s.breath();
        ClientState.danger = s.danger();
        int f = s.flags();
        ClientState.inGame = (f & 1) != 0;
        ClientState.hidden = (f & 2) != 0;
        ClientState.holding = (f & 4) != 0;
        ClientState.exhausted = (f & 8) != 0;
        ClientState.captured = (f & 16) != 0;
        ClientState.inSafe = (f & 32) != 0;
        ClientState.ritualReady = (f & 64) != 0;
        ClientState.prologue = (f & 256) != 0;
        ClientState.bells = s.bells();
        ClientState.phase = s.phase();
        ClientState.deaths = s.deaths();
        ClientView.inGame = ClientState.inGame;
        ClientView.phase = s.phase();
        ClientView.bells = s.bells();
        ClientView.kuraUnlocked = (f & 128) != 0;
        ClientView.ritualReady = ClientState.ritualReady;
    }

    public static void event(S2C.Event e) {
        Minecraft mc = Minecraft.getInstance();
        boolean calm = MeikonConfig.flashReduction();
        switch (e.type()) {
            case GameManager.EVENT_JUMPSCARE -> {
                ClientState.jumpscareKind = e.arg();
                ClientState.jumpscareTicks = 0;
                ClientState.shake(calm ? 1.0f : 3.0f, 26);
                ui(e.arg() == 2 ? ModSounds.GAME_STINGER.get() : ModSounds.GAME_JUMPSCARE.get(), 1.0f, 1.0f);
            }
            case GameManager.EVENT_DEATH -> {
                ClientState.deathTicks = 0;
                ClientState.deathCount = e.arg();
                ClientState.jumpscareTicks = -1;
                ui(ModSounds.GAME_DEATH.get(), 1.0f, 1.0f);
            }
            case GameManager.EVENT_OFUDA -> {
                ClientState.ofudaTicks = 0;
                ClientState.shake(1.5f, 15);
            }
            case GameManager.EVENT_INTRO -> {
                ClientState.introTicks = 0;
                ClientState.introName = e.text();
                ClientState.inGame = true;
                ui(ModSounds.MUSIC_TITLE.get(), 1.0f, 1.0f);
            }
            case GameManager.EVENT_TITLE -> {
                ClientState.titleTicks = 0;
                ClientState.titlePhase = e.arg();
                ui(ModSounds.GAME_PHASE.get(), 1.0f, 1.0f);
            }
            case GameManager.EVENT_ENDING -> mc.setScreen(new EndingScreen(e.arg() == 1, e.text()));
            case GameManager.EVENT_SPOTTED -> {
                ClientState.spottedTicks = 0;
                ClientState.shake(1.6f, 18);
            }
            case GameManager.EVENT_STINGER -> {
                ClientState.stingerTicks = 0;
                ClientState.shake(1.2f, 14);
                ui(ModSounds.GAME_STINGER.get(), 1.0f, 1.0f);
            }
            case GameManager.EVENT_SHADOW -> {
                ClientState.shadowTicks = 0;
                ClientState.shake(0.8f, 10);
            }
            case GameManager.EVENT_SCREEN -> {
                if (e.arg() == GameManager.SCREEN_ENVELOPE) mc.setScreen(new EnvelopeScreen(e.text()));
                else mc.setScreen(new SakazukiScreen());
            }
            case GameManager.EVENT_LEFT -> {
                ClientState.reset();
                HorrorAudio.stopAll();
                if (mc.screen instanceof EndingScreen) mc.setScreen(null);
            }
            case GameManager.EVENT_SHAKE -> ClientState.shake(1.0f, Math.max(4, e.arg()));
            case GameManager.EVENT_BELL -> {
                ClientState.bellFlash = 60;
                ClientState.bells = e.arg();
                ui(ModSounds.GAME_BELL_GET.get(), 1.0f, 1.0f);
            }
            case GameManager.EVENT_FLICKER -> ClientState.flickerTicks = 0;
            case GameManager.EVENT_RITUAL -> {
                ClientState.ritualActive = e.arg() == 1;
                ClientState.ritualTicks = 0;
                if (ClientState.ritualActive) ui(ModSounds.GAME_RITUAL.get(), 1.0f, 1.0f);
            }
            default -> {
            }
        }
    }

    public static void sound(S2C.Sound s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(s.id()));
        if (ev == null) return;
        mc.level.playLocalSound(s.x(), s.y(), s.z(), ev, SoundSource.AMBIENT, s.volume(), s.pitch(), false);
    }

    public static void note(S2C.Note n) {
        ClientState.NOTE_EXTRA.put(n.id(), n.extra());
        Minecraft.getInstance().setScreen(new NoteScreen(n.id(), n.extra()));
    }

    public static void ui(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
