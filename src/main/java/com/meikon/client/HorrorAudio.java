package com.meikon.client;

import com.meikon.client.sound.EntityLoopSound;
import com.meikon.client.sound.LoopSound;
import com.meikon.entity.Hanayome;
import com.meikon.registry.ModDimensions;
import com.meikon.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * The client-side sound direction: drone beds, wind in the courtyard, the heartbeat that quickens with fear,
 * frightened breathing, ear ringing, rare koto phrases in the calm, chase music, the wedding music of the
 * fourth phase, and the bride's humming following her through the house.
 */
public final class HorrorAudio {
    private static final RandomSource RAND = RandomSource.create();
    private static LoopSound drone, droneTense, wind, chase, gagaku;
    private static final Map<Integer, EntityLoopSound> HUMS = new HashMap<>();
    private static int heartbeatIn = 40;
    private static int breathIn = 60;
    private static int tinnitusIn = 300;
    private static int kotoIn = 600;

    private HorrorAudio() {}

    private static LoopSound ensure(LoopSound loop, SoundEvent event, float ease) {
        Minecraft mc = Minecraft.getInstance();
        if (loop != null && mc.getSoundManager().isActive(loop)) return loop;
        LoopSound l = new LoopSound(event, SoundSource.AMBIENT, ease);
        mc.getSoundManager().play(l);
        return l;
    }

    public static void stopAll() {
        for (LoopSound l : new LoopSound[]{drone, droneTense, wind, chase, gagaku}) {
            if (l != null) l.finish();
        }
        drone = droneTense = wind = chase = gagaku = null;
        for (EntityLoopSound s : HUMS.values()) Minecraft.getInstance().getSoundManager().stop(s);
        HUMS.clear();
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || !ModDimensions.isKakuriyo(mc.level)) {
            if (drone != null || !HUMS.isEmpty()) stopAll();
            return;
        }
        if (mc.isPaused()) return;
        float fear = ClientState.fearShown / 100.0f;
        float danger = ClientState.dangerShown;
        int phase = ClientState.phase;
        boolean cinematic = ClientState.cinematic();

        drone = ensure(drone, ModSounds.AMBIENT_DRONE.get(), 0.02f);
        droneTense = ensure(droneTense, ModSounds.AMBIENT_DRONE_TENSE.get(), 0.02f);
        wind = ensure(wind, ModSounds.AMBIENT_WIND.get(), 0.03f);
        chase = ensure(chase, ModSounds.GAME_CHASE.get(), danger > 0.6f ? 0.08f : 0.025f);
        gagaku = ensure(gagaku, ModSounds.MUSIC_GAGAKU.get(), 0.01f);
        float duck = 1.0f - 0.6f * danger;
        boolean tense = phase >= 3 || fear > 0.7f;
        drone.target = cinematic ? 0.15f : (tense ? 0.12f : 0.55f) * duck;
        droneTense.target = cinematic ? 0.1f : (tense ? 0.6f : 0.1f) * duck;
        boolean sky = mc.level.canSeeSky(mc.player.blockPosition().above());
        wind.target = (sky ? 0.55f : 0.12f) * (cinematic ? 0.3f : 1.0f);
        chase.target = danger > 0.62f ? Mth.clamp((danger - 0.55f) * 2.2f, 0.0f, 1.0f) : 0.0f;
        gagaku.target = phase == 4 && !cinematic ? 0.38f * (1.0f - danger) : 0.0f;

        // heartbeat
        if (--heartbeatIn <= 0) {
            float f = ClientState.fearShown;
            if (f >= 22.0f && !ClientState.inSafe) {
                float k = (f - 22.0f) / 78.0f;
                float vol = Mth.lerp(k, 0.25f, 1.0f) + danger * 0.3f;
                ClientPacketHandler.ui(ModSounds.PLAYER_HEARTBEAT.get(), Math.min(1.2f, vol), 1.0f + danger * 0.08f);
                heartbeatIn = Math.round(Mth.lerp(Math.min(1.0f, k + danger * 0.4f), 26.0f, 7.0f));
            } else {
                heartbeatIn = 20;
            }
        }
        // frightened breathing
        if (--breathIn <= 0) {
            if (ClientState.fearShown >= 70.0f && !ClientState.holding && !ClientState.inSafe) {
                ClientPacketHandler.ui(ModSounds.PLAYER_BREATH_FEAR.get(), 0.45f + fear * 0.25f, 1.0f);
            }
            breathIn = 50 + RAND.nextInt(30);
        }
        // ringing in the ears
        if (--tinnitusIn <= 0) {
            if (ClientState.fearShown >= 92.0f) ClientPacketHandler.ui(ModSounds.AMBIENT_TINNITUS.get(), 0.35f, 1.0f);
            tinnitusIn = 200 + RAND.nextInt(260);
        }
        // a few notes of koto in the calm
        if (--kotoIn <= 0) {
            if (ClientState.fearShown < 40.0f && danger < 0.1f && phase <= 3 && !cinematic) {
                ClientPacketHandler.ui(ModSounds.AMBIENT_KOTO.get(), 0.45f, 1.0f);
            }
            kotoIn = 900 + RAND.nextInt(900);
        }

        // the bride's humming
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Hanayome h)) continue;
            if (h.distanceTo(mc.player) > 56) continue;
            EntityLoopSound s = HUMS.get(h.getId());
            if (s == null || !mc.getSoundManager().isActive(s)) {
                s = new EntityLoopSound(ModSounds.BRIDE_HUM.get(), h, 1.0f, ent -> ent instanceof Hanayome b
                        && (b.clientState() == Hanayome.State.WANDER || b.clientState() == Hanayome.State.IDLE));
                mc.getSoundManager().play(s);
                HUMS.put(h.getId(), s);
            }
        }
        HUMS.entrySet().removeIf(en -> {
            if (en.getValue().entity().isRemoved() || en.getValue().entity().distanceTo(mc.player) > 64) {
                mc.getSoundManager().stop(en.getValue());
                return true;
            }
            return false;
        });
    }
}
