package com.hitori.client;

import com.hitori.HitoriConfig;
import com.hitori.client.gui.EndingScreen;
import com.hitori.client.gui.NamingScreen;
import com.hitori.client.gui.PrintoutScreen;
import com.hitori.game.GameManager;
import com.hitori.network.S2C;
import com.hitori.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.registries.ForgeRegistries;

/** Client side of the server messages. Only ever loaded on the physical client. */
public final class ClientPacketHandler {
    private static final RandomSource RAND = RandomSource.create();
    private static boolean ownBlindness;

    private ClientPacketHandler() {}

    public static void state(S2C.State s) {
        int oldHud = ClientState.hud;
        ClientState.fear = s.fear();
        ClientState.danger = s.danger();
        ClientState.mouth = s.mouth();
        ClientState.breath = s.breath();
        ClientState.flags = s.flags();
        ClientState.phase = s.phase();
        ClientState.steps = s.steps();
        ClientState.hud = s.hud();
        ClientState.clock = s.clock();
        ClientState.broken = s.broken();
        ClientState.action = s.action();
        ClientState.actionProgress = s.progress();
        ClientState.dollName = s.dollName();
        ClientState.cameras = s.cameras();
        if (oldHud != s.hud() && ClientState.stepFlash < 0) ClientState.stepFlash = 0;
        if (!ClientState.flag(GameManager.F_HIDING) && ClientState.hideKind > 0 && ClientState.clientTicks % 20 == 0) ClientState.hideKind = 0;
        // the eyes: the shader's blindness channel follows the server's word
        if (!ClientState.flag(GameManager.F_EYES) && ClientState.eyesTicks >= 0 && ClientState.eyesTicks > 40) eyes(false);
    }

    public static void event(S2C.Event e) {
        Minecraft mc = Minecraft.getInstance();
        boolean calm = HitoriConfig.flashReduction();
        switch (e.type()) {
            case GameManager.EV_SUBTITLE -> Dialogue.subtitle(e.text(), Math.max(40, e.arg()));
            case GameManager.EV_STEP -> {
                ClientState.stepFlash = 0;
                ClientState.stepFlashIndex = e.arg();
                ui(ModSounds.GAME_STEP_DONE.get(), 0.5f, 1.0f);
            }
            case GameManager.EV_THREE -> {
                ClientState.threeTicks = 0;
                ui(ModSounds.GAME_THREE_AM.get(), 1.0f, 1.0f);
                ClientState.shake(calm ? 0.2f : 0.8f, 30);
            }
            case GameManager.EV_EYES -> eyes(e.arg() == 1);
            case GameManager.EV_JUMPSCARE -> {
                CameraApp.forceClose();
                Peek.reset();
                ClientState.jumpscareKind = e.arg();
                ClientState.jumpscareTicks = 0;
                ClientState.caughtTicks = -1;
                ClientState.shake(calm ? 1.0f : 5.0f, 24);
                if (HitoriConfig.jumpscares()) ui(ModSounds.GAME_JUMPSCARE.get(), 1.0f, 0.95f + RAND.nextFloat() * 0.1f);
                else ui(ModSounds.GAME_STINGER.get(), 1.0f, 1.0f);
            }
            case GameManager.EV_CAUGHT -> {
                ClientState.jumpscareTicks = -1;
                ClientState.caughtTicks = 0;
            }
            case GameManager.EV_ENDING -> {
                PhoneUi.reset();
                ClientState.jumpscareTicks = -1;
                CameraApp.forceClose();
                Peek.reset();
                Dialogue.clear();
                eyes(false);
                mc.setScreen(new EndingScreen(e.arg(), e.text()));
            }
            case GameManager.EV_LEFT -> {
                ClientState.reset();
                Dialogue.clear();
                HorrorAudio.stopAll();
                CameraApp.reset();
                Peek.reset();
                PhoneUi.reset();
                eyes(false);
                if (mc.screen instanceof EndingScreen) mc.setScreen(null);
            }
            case GameManager.EV_FADE -> {
                ClientState.fadeTicks = 0;
                ClientState.fadeLength = Math.max(10, e.arg());
            }
            case GameManager.EV_SHAKE -> ClientState.shake((calm ? 0.3f : 1.0f) * e.arg() / 10.0f, 30);
            case GameManager.EV_STINGER -> {
                ClientState.stingerTicks = 0;
                ui(ModSounds.GAME_STINGER.get(), e.arg() == 1 ? 0.6f : 0.9f, 1.0f);
                ClientState.shake(calm ? 0.2f : 0.8f, 12);
            }
            case GameManager.EV_FEED -> CameraApp.glitch(e.arg());
            case GameManager.EV_HIDE -> {
                ClientState.hideKind = e.arg();
                if (e.arg() == 0) Peek.reset();
            }
            case GameManager.EV_MESSAGE -> PhoneUi.message(e.text(), e.arg() == 1);
            case GameManager.EV_CALL -> PhoneUi.call(e.arg(), e.text());
            case GameManager.EV_MAP -> ClientState.mapTicks = 0;
            case GameManager.EV_THOUGHT -> thought(e.text());
            case GameManager.EV_HINT -> {
                ClientState.hintTicks = 0;
                ClientState.hintKind = e.arg();
            }
            case GameManager.EV_RECORD -> FrameCapture.tag(e.arg(), e.text());
            case GameManager.EV_NEW_NIGHT -> {
                FrameCapture.clear();
                Dialogue.clear();
                PhoneUi.reset();
                CameraApp.reset();
                Peek.reset();
                eyes(false);
                ClientState.newNight();
                if (mc.screen instanceof EndingScreen) mc.setScreen(null);
            }
            case GameManager.EV_MORNING -> {
                PhoneUi.reset();
                ClientState.morningTicks = 0;
                ClientState.morningKind = e.arg();
                if (e.arg() == 0) ui(ModSounds.GAME_MORNING.get(), 1.0f, 1.0f);
            }
            case GameManager.EV_INTRO -> {
                if (e.arg() < 0) {
                    ClientState.introTicks = -1;
                } else {
                    ClientState.introTicks = 0;
                    ClientState.introShort = e.arg() == 1;
                    ui(ModSounds.GAME_TITLE_HIT.get(), 0.6f, 0.8f);
                }
            }
            case GameManager.EV_PRINTOUT -> {
                if (mc.screen == null) mc.setScreen(new PrintoutScreen());
            }
            case GameManager.EV_NAMING -> mc.setScreen(new NamingScreen(e.text()));
            case GameManager.EV_APP -> {
                if (e.arg() == 0) CameraApp.forceClose();
            }
            case GameManager.EV_SCARE -> {
                ClientState.scareKind = e.arg();
                ClientState.scareTicks = 0;
                ClientState.shake(calm ? 0.6f : 3.0f, 20);
                if (e.arg() == 2 && HitoriConfig.jumpscares()) ui(ModSounds.GAME_JUMPSCARE.get(), 0.9f, 1.1f);
                else ui(ModSounds.GAME_STINGER.get(), 1.0f, 0.9f);
                if (e.arg() >= 2) CameraApp.glitch(0);
            }
            case GameManager.EV_RULE -> {
                ClientState.ruleTicks = 0;
                ClientState.ruleKey = e.text();
                ui(ModSounds.GAME_RISER.get(), 0.35f, 0.8f);
            }
            case GameManager.EV_GLITCH -> CameraApp.glitch(0);
            default -> {
            }
        }
    }

    /** Eyes closed while counting: the screen goes black; with a shader pack, the blindness channel. */
    private static boolean ownDarkness;

    /**
     * Caught: the shader's {@code darknessFactor} channel (a hidden darkness effect on you) - with a shader pack only,
     * and never while a last feed is being captured (vanilla darkens its lightmap by the player's darkness).
     */
    static void syncDarkness(Minecraft mc) {
        if (mc.player == null) return;
        boolean want = ClientState.inGame() && ClientState.caughtTicks >= 0 && !FrameCapture.capturing() && ShaderCompat.active();
        if (want && !ownDarkness) {
            mc.player.forceAddEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 60, 0, false, false, false), null);
            ownDarkness = true;
        } else if (!want && ownDarkness) {
            mc.player.removeEffectNoUpdate(MobEffects.DARKNESS);
            ownDarkness = false;
        }
    }

    static void eyes(boolean closed) {
        Minecraft mc = Minecraft.getInstance();
        ClientState.eyesTicks = closed ? 0 : -1;
        if (mc.player == null) return;
        if (closed) {
            mc.player.forceAddEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 30, 0, false, false, false), null);
            ownBlindness = true;
        } else if (ownBlindness) {
            mc.player.removeEffectNoUpdate(MobEffects.BLINDNESS);
            ownBlindness = false;
        }
    }

    /** A thought line: "key|arg|arg" (an argument starting with @ is itself a lang key). */
    public static void thought(String text) {
        ClientState.thought = text;
        ClientState.thoughtTicks = 0;
    }

    public static String format(String text) {
        String[] p = text.split("\\|");
        Object[] args = new Object[p.length - 1];
        for (int i = 1; i < p.length; i++) args[i - 1] = p[i].startsWith("@") ? I18n.get(p[i].substring(1)) : p[i];
        return I18n.get(p[0], args);
    }

    public static void say(S2C.Say s) {
        Dialogue.line(s.speaker(), s.key(), s.args(), s.ticks());
    }

    public static void sound(S2C.Sound s) {
        Minecraft mc = Minecraft.getInstance();
        SoundEvent ev = event(s.id());
        mc.getSoundManager().play(new SimpleSoundInstance(ev, SoundSource.AMBIENT, s.volume(), s.pitch(), RAND, s.x(), s.y(), s.z()));
    }

    public static void capture(S2C.Capture c) {
        FrameCapture.start(c);
    }

    public static SoundEvent event(String id) {
        ResourceLocation rl = new ResourceLocation(id);
        SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(rl);
        return ev != null ? ev : SoundEvent.createVariableRangeEvent(rl);
    }

    public static void ui(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** A sound placed relative to the listener (whispers at one ear). */
    public static void near(SoundEvent sound, double dx, double dy, double dz, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.AMBIENT, volume, pitch, RAND, false, 0,
                SoundInstance.Attenuation.LINEAR, mc.player.getX() + dx, mc.player.getEyeY() + dy, mc.player.getZ() + dz, false));
    }
}
