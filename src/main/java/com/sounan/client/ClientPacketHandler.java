package com.sounan.client;

import com.sounan.SounanConfig;
import com.sounan.client.gui.EndingScreen;
import com.sounan.client.gui.NoteScreen;
import com.sounan.game.GameManager;
import com.sounan.network.S2C;
import com.sounan.registry.ModSounds;
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
        ClientState.fear = s.fear();
        ClientState.danger = s.danger();
        ClientState.stamina = s.stamina();
        ClientState.flash = s.flash();
        ClientState.phone = s.phone();
        ClientState.flags = s.flags();
        ClientState.captures = s.captures();
        ClientState.area = s.area();
        ClientState.minutes = s.minutes();
        ClientState.days = s.days();
        ClientState.inGame = (s.flags() & GameManager.F_INGAME) != 0;
        ClientState.notesRead |= s.notes();
        if ((s.flags() & GameManager.F_MAP) != 0) ClientState.messagesArrived = true;
    }

    public static void event(S2C.Event e) {
        Minecraft mc = Minecraft.getInstance();
        boolean calm = SounanConfig.flashReduction();
        switch (e.type()) {
            case GameManager.EV_JUMPSCARE -> {
                ClientState.jumpscareKind = e.arg();
                ClientState.jumpscareTicks = 0;
                ClientState.shake(calm ? 1.0f : 4.0f, 30);
                ui(ModSounds.GAME_JUMPSCARE.get(), 1.0f, 0.95f + RAND.nextFloat() * 0.1f);
                ui(ModSounds.GAME_CAPTURE.get(), 0.9f, 1.0f);
            }
            case GameManager.EV_WAKE -> {
                ClientState.wakeTicks = 0;
                ClientState.jumpscareTicks = -1;
                ClientState.captures = e.arg();
                ui(ModSounds.GAME_WAKE.get(), 0.9f, 1.0f);
                ClientState.thought = "thought.sounan.wake_" + Math.min(4, Math.max(1, e.arg()));
                ClientState.thoughtTicks = -60;
            }
            case GameManager.EV_INTRO -> {
                ClientState.introTicks = 0;
                ClientState.introName = e.text();
                ClientState.TRAIL.clear();
                ClientState.messagesArrived = false;
            }
            case GameManager.EV_AREA -> {
                ClientState.areaTitle = e.arg();
                ClientState.areaTicks = 0;
                ui(ModSounds.GAME_AREA.get(), 0.7f, 1.0f);
            }
            case GameManager.EV_THOUGHT -> {
                ClientState.thought = e.text();
                ClientState.thoughtTicks = 0;
            }
            case GameManager.EV_SUBTITLE -> {
                ClientState.subtitle = e.text();
                ClientState.subtitleTicks = 0;
            }
            case GameManager.EV_LOOP -> {
                ClientState.loopTicks = 0;
                ui(ModSounds.GAME_LOOP.get(), 0.9f, 1.0f);
            }
            case GameManager.EV_CHECKPOINT -> {
                ClientState.checkpointTicks = 0;
                ui(ModSounds.GAME_CHECKPOINT.get(), 0.7f, 1.0f);
            }
            case GameManager.EV_SIGNAL -> {
                ClientState.signalTicks = 0;
                ClientState.messagesArrived = true;
                ClientState.unreadMessages = 9 + e.arg() * 6;
                ui(ModSounds.PHONE_SIGNAL.get(), 1.0f, 1.0f);
                ClientState.buzzTicks = 0;
                ClientState.buzzText = "phone.sounan.buzz.messages";
            }
            case GameManager.EV_CALL -> {
                ClientState.callKind = e.arg();
                ClientState.callTicks = 0;
                ClientState.callExtra = e.text();
            }
            case GameManager.EV_ENDING -> {
                ClientState.jumpscareTicks = -1;
                mc.setScreen(new EndingScreen(e.arg(), e.text()));
            }
            case GameManager.EV_BUZZ -> {
                ClientState.buzzTicks = 0;
                ClientState.buzzText = e.text();
                if (mc.player != null) {
                    mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.PHONE_VIBRATE.get(), SoundSource.PLAYERS, 0.9f, 1.0f,
                            RAND, mc.player.getX(), mc.player.getY() + 0.8, mc.player.getZ()));
                }
            }
            case GameManager.EV_FLASH -> ClientState.flashTicks = 0;
            case GameManager.EV_OMAMORI -> {
                ClientState.omamoriTicks = 0;
                ClientState.shake(calm ? 0.5f : 2.0f, 20);
            }
            case GameManager.EV_SHAKE -> ClientState.shake((calm ? 0.3f : 1.0f) * e.arg() / 10.0f, 30);
            case GameManager.EV_SILENCE -> ClientState.silenceTicks = 0;
            case GameManager.EV_FOG_LIFT -> {
                ClientState.fogLiftTicks = 0;
                ui(ModSounds.GAME_FOG_LIFT.get(), 1.0f, 1.0f);
            }
            case GameManager.EV_STINGER -> {
                ClientState.stingerTicks = 0;
                ui(ModSounds.GAME_STINGER.get(), 0.8f + e.arg() / 20.0f, 1.0f);
                ClientState.shake(calm ? 0.2f : 0.8f, 12);
            }
            case GameManager.EV_GLITCH -> {
                if (!calm) ClientState.glitchTicks = 0;
            }
            case GameManager.EV_LEFT -> {
                ClientState.reset();
                HorrorAudio.stopAll();
            }
            default -> {
            }
        }
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

    public static void note(S2C.Note n) {
        Minecraft.getInstance().setScreen(new NoteScreen(n.id(), n.extra()));
    }

    public static void ui(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** A sound placed relative to the listener (ear whispers, things behind you). */
    public static void near(SoundEvent sound, double dx, double dy, double dz, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.AMBIENT, volume, pitch, RAND, false, 0,
                SoundInstance.Attenuation.LINEAR, mc.player.getX() + dx, mc.player.getEyeY() + dy, mc.player.getZ() + dz, false));
    }
}
