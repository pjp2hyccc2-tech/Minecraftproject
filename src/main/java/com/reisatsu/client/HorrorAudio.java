package com.reisatsu.client;

import com.reisatsu.client.sound.EntityLoopSound;
import com.reisatsu.client.sound.LoopSound;
import com.reisatsu.entity.Chef;
import com.reisatsu.entity.Manager;
import com.reisatsu.entity.Spirit;
import com.reisatsu.game.GameData;
import com.reisatsu.game.GameManager;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * The sound direction of the night (docs/DESIGN.md §8): a bed per room (the dining hall's compressors and clock, the
 * kitchen's hum, the tiled restrooms, the back corridor's ballast, the walk-in fridge, the wet road outside), music
 * layers by fear and danger (dread, tension, danger, the chase), the fragile safe theme by the medium's car, the store
 * music warped at closing time, the exorcism; the heartbeat at the real rate (loud while you hold your breath),
 * frightened breathing, whispers at one ear, and the pressure hum near a spirit. Real dynamic range: quiet first.
 */
public final class HorrorAudio {
    private static final RandomSource RAND = RandomSource.create();
    private static LoopSound road, dining, kitchen, restroom, back, fridge, silence, closingBed;
    private static LoopSound dread, tension, danger, chase, safe, closingMusic, store, storeWrong, chant;
    private static final Map<Integer, EntityLoopSound> NEAR = new HashMap<>();
    private static final Map<Integer, EntityLoopSound> WORK = new HashMap<>();
    private static float heartbeatIn = 40;
    private static int breathIn = 60;
    private static int whisperIn = 600;
    private static float lastDanger;
    private static int storeAge = -1;
    private static boolean exorcismMusic;

    private HorrorAudio() {}

    private static LoopSound ensure(LoopSound loop, SoundEvent event, float ease, SoundSource source) {
        Minecraft mc = Minecraft.getInstance();
        if (loop != null && mc.getSoundManager().isActive(loop)) return loop;
        LoopSound l = new LoopSound(event, source, ease);
        mc.getSoundManager().play(l);
        return l;
    }

    public static void stopAll() {
        for (LoopSound l : new LoopSound[]{road, dining, kitchen, restroom, back, fridge, silence, closingBed, dread, tension, danger, chase, safe,
                closingMusic, store, storeWrong, chant}) {
            if (l != null) l.finish();
        }
        road = dining = kitchen = restroom = back = fridge = silence = closingBed = null;
        dread = tension = danger = chase = safe = closingMusic = store = storeWrong = chant = null;
        for (EntityLoopSound s : NEAR.values()) Minecraft.getInstance().getSoundManager().stop(s);
        NEAR.clear();
        for (EntityLoopSound s : WORK.values()) Minecraft.getInstance().getSoundManager().stop(s);
        WORK.clear();
        exorcismMusic = false;
        storeAge = -1;
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || !ClientState.inGame()) {
            if (dread != null || road != null || !NEAR.isEmpty()) stopAll();
            return;
        }
        if (mc.isPaused() || mc.screen instanceof com.reisatsu.client.gui.EndingScreen) return;
        float fear = ClientState.fearShown;
        float dng = ClientState.dangerShown;
        boolean intro = (ClientState.introTicks >= 0 && ClientState.introTicks < com.reisatsu.game.Story.INTRO_TEXT) || ClientState.black();
        boolean caught = ClientState.flag(GameManager.F_CAUGHT) || ClientState.jumpscareTicks >= 0 || ClientState.caughtTicks >= 0;
        boolean hunted = ClientState.flag(GameManager.F_HUNTED);
        boolean closing = ClientState.phase == GameData.PHASE_CLOSING;
        boolean exorcism = ClientState.exorcismStage >= 0 || ClientState.phase == GameData.PHASE_EXORCISM;
        boolean dawn = ClientState.dawnTicks >= 0;
        boolean holding = ClientState.flag(GameManager.F_BREATH);
        Layout.Room room = Layout.roomAt(mc.player.blockPosition());
        String key = room == null ? "outside" : room.key();
        float hush = caught ? 0.0f : intro ? 0.0f : 1.0f;
        if (dawn) hush *= Math.max(0.0f, 1.0f - ClientState.dawnTicks / 120.0f);

        // ---- beds
        road = ensure(road, ModSounds.AMB_NIGHT_ROAD.get(), 0.03f, SoundSource.AMBIENT);
        dining = ensure(dining, ModSounds.AMB_DINING.get(), 0.03f, SoundSource.AMBIENT);
        kitchen = ensure(kitchen, ModSounds.AMB_KITCHEN.get(), 0.03f, SoundSource.AMBIENT);
        restroom = ensure(restroom, ModSounds.AMB_RESTROOM.get(), 0.03f, SoundSource.AMBIENT);
        back = ensure(back, ModSounds.AMB_BACK.get(), 0.03f, SoundSource.AMBIENT);
        fridge = ensure(fridge, ModSounds.AMB_FRIDGE.get(), 0.05f, SoundSource.AMBIENT);
        silence = ensure(silence, ModSounds.AMB_SILENCE.get(), 0.05f, SoundSource.AMBIENT);
        closingBed = ensure(closingBed, ModSounds.AMB_CLOSING.get(), 0.02f, SoundSource.AMBIENT);
        boolean introOutside = ClientState.introTicks >= 0;
        road.target = (key.equals("outside") ? 0.65f : key.equals("vestibule") ? 0.25f : 0.06f) * (introOutside ? 1.0f : hush);
        dining.target = (key.equals("dining") || key.equals("waiting") || key.equals("vestibule") ? 0.5f : 0.0f) * hush;
        kitchen.target = key.equals("kitchen") ? 0.55f * hush : 0.0f;
        restroom.target = (key.equals("womens") || key.equals("mens") || key.equals("cavity")) ? 0.5f * hush : 0.0f;
        back.target = (key.equals("corridor") || key.equals("staff") || key.equals("office") || key.equals("storage")) ? 0.5f * hush : 0.0f;
        fridge.target = key.equals("fridge") ? 0.7f * hush : 0.0f;
        closingBed.target = ClientState.flag(GameManager.F_CLOSING) && !exorcism ? 0.4f * hush : 0.0f;
        // a sudden spike of danger: everything holds its breath for a moment
        if (dng - lastDanger > 0.3f) ClientState.stingerTicks = Math.max(ClientState.stingerTicks, 0);
        lastDanger = dng;
        silence.target = (dng > 0.35f && dng < 0.75f && !hunted) || ClientState.screamTicks >= 0 ? 0.5f : 0.0f;

        // ---- music
        dread = ensure(dread, ModSounds.MUSIC_DREAD.get(), 0.01f, SoundSource.MUSIC);
        tension = ensure(tension, ModSounds.MUSIC_TENSION.get(), 0.02f, SoundSource.MUSIC);
        danger = ensure(danger, ModSounds.MUSIC_DANGER.get(), 0.05f, SoundSource.MUSIC);
        chase = ensure(chase, ModSounds.MUSIC_CHASE.get(), hunted ? 0.12f : 0.02f, SoundSource.MUSIC);
        safe = ensure(safe, ModSounds.MUSIC_SAFE.get(), 0.01f, SoundSource.MUSIC);
        closingMusic = ensure(closingMusic, ModSounds.MUSIC_CLOSING.get(), 0.02f, SoundSource.MUSIC);
        float chaseV = hunted ? 0.95f : 0.0f;
        boolean nearMedium = ClientState.flag(GameManager.F_NEAR_MEDIUM) && key.equals("outside");
        float musicHush = caught || intro || exorcism ? 0.0f : 1.0f;
        dread.target = (nearMedium ? 0.08f : 0.3f) * (1.0f - 0.7f * chaseV) * musicHush;
        tension.target = fear > 0.35f ? (fear - 0.35f) / 0.65f * 0.6f * (1.0f - chaseV) * musicHush : 0.0f;
        danger.target = dng > 0.2f ? Mth.clamp((dng - 0.2f) * 1.3f, 0.0f, 0.85f) * (1.0f - 0.8f * chaseV) * musicHush : 0.0f;
        chase.target = chaseV * musicHush;
        safe.target = nearMedium && dng < 0.1f && !closing ? 0.45f * musicHush : 0.0f;
        closingMusic.target = closing && !hunted ? 0.5f * musicHush : 0.0f;
        // the store speakers: the cheerful loop for a moment, then it goes wrong
        boolean storeOn = ClientState.flag(GameManager.F_STORE_MUSIC) && !exorcism;
        if (storeOn) storeAge++;
        else storeAge = -1;
        store = ensure(store, ModSounds.MUSIC_STORE.get(), 0.05f, SoundSource.RECORDS);
        storeWrong = ensure(storeWrong, ModSounds.MUSIC_STORE_WRONG.get(), 0.03f, SoundSource.RECORDS);
        float inside = key.equals("outside") ? 0.25f : 1.0f;
        store.target = storeOn && storeAge < 70 && !closing ? 0.4f * inside * hush : 0.0f;
        storeWrong.target = storeOn && (storeAge >= 50 || closing) ? (closing ? 0.32f : 0.42f) * inside * hush : 0.0f;
        // the exorcism: the ritual music once, the chant under it
        chant = ensure(chant, ModSounds.MEDIUM_CHANT.get(), 0.03f, SoundSource.NEUTRAL);
        chant.target = ClientState.exorcismStage >= 1 && !dawn ? 0.45f : 0.0f;
        if (ClientState.exorcismStage >= 1 && !exorcismMusic) {
            exorcismMusic = true;
            ClientPacketHandler.ui(ModSounds.MUSIC_EXORCISM.get(), 0.8f, 1.0f);
        }
        if (caught || intro || exorcism) {
            for (EntityLoopSound s : WORK.values()) mc.getSoundManager().stop(s);
            WORK.clear();
            return;
        }
        tickWork(mc);

        // ---- the body
        heartbeatIn -= 1.0f;
        if (heartbeatIn <= 0) {
            if (fear >= 0.3f || holding) {
                float k = Mth.clamp((fear - 0.3f) / 0.7f, 0.0f, 1.0f);
                float vol = Mth.lerp(k, 0.15f, 0.9f) + dng * 0.2f + (holding ? 0.45f : 0.0f);
                ClientPacketHandler.ui(ModSounds.PLAYER_HEARTBEAT.get(), Math.min(1.3f, vol), 1.0f + dng * 0.05f);
                heartbeatIn += 1200.0f / ClientState.bpm();
            } else {
                heartbeatIn = 20;
            }
        }
        if (--breathIn <= 0) {
            if (!holding && fear >= 0.7f) ClientPacketHandler.ui(ModSounds.PLAYER_BREATH_FEAR.get(), 0.3f + fear * 0.3f, 1.0f);
            breathIn = 50 + RAND.nextInt(40);
        }
        if (--whisperIn <= 0) {
            if (fear >= 0.75f) {
                double side = RAND.nextBoolean() ? 0.7 : -0.7;
                float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
                ClientPacketHandler.near(ModSounds.SPIRIT_WHISPER.get(), -Mth.cos(yaw) * side, 0.0, -Mth.sin(yaw) * side, 0.5f,
                        0.9f + RAND.nextFloat() * 0.2f);
            }
            whisperIn = 500 + RAND.nextInt(700);
        }
        tickNear(mc);
    }

    /**
     * The seamless work loops (docs §8): the chef's chopping — started on a cleaver hit (his model's hits fall on
     * {@code tickCount % 10 == 7}; the 4 s loop is 80 ticks, so it stays in step) — and the manager's typing at his desk.
     * They keep running silently while the work pauses, so the rhythm never slips.
     */
    private static void tickWork(Minecraft mc) {
        for (Entity e : mc.level.entitiesForRendering()) {
            boolean chef = e instanceof Chef, manager = e instanceof Manager;
            if (!chef && !manager) continue;
            if (e.distanceTo(mc.player) > 26) continue;
            EntityLoopSound s = WORK.get(e.getId());
            if (s != null && mc.getSoundManager().isActive(s)) continue;
            java.util.function.Predicate<Entity> on = chef
                    ? en -> !en.isRemoved() && ((Chef) en).pose() == Chef.CHOP && !((Chef) en).flag(Spirit.F_BOUND)
                    : en -> !en.isRemoved() && ((Manager) en).pose() == Manager.SIT;
            if (!on.test(e)) continue;
            if (chef && e.tickCount % 10 != 7) continue;
            s = new EntityLoopSound(chef ? ModSounds.CHEF_CHOP.get() : ModSounds.MANAGER_TYPING.get(), e, chef ? 0.9f : 0.45f, on, true);
            mc.getSoundManager().play(s);
            WORK.put(e.getId(), s);
        }
        WORK.entrySet().removeIf(en -> {
            if (en.getValue().entity().isRemoved() || en.getValue().entity().distanceTo(mc.player) > 34) {
                mc.getSoundManager().stop(en.getValue());
                return true;
            }
            return false;
        });
    }

    /** The pressure hum near a spirit (heard through walls, even when nothing is seen). */
    private static void tickNear(Minecraft mc) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Spirit sp) || sp.flag(Spirit.F_EXORCISM)) continue;
            if (e.distanceTo(mc.player) > 14) continue;
            EntityLoopSound s = NEAR.get(e.getId());
            if (s == null || !mc.getSoundManager().isActive(s)) {
                s = new EntityLoopSound(ModSounds.SPIRIT_NEAR.get(), e, 0.55f, ent -> !ent.isRemoved() && !((Spirit) ent).flag(Spirit.F_BOUND));
                mc.getSoundManager().play(s);
                NEAR.put(e.getId(), s);
            }
        }
        NEAR.entrySet().removeIf(en -> {
            if (en.getValue().entity().isRemoved() || en.getValue().entity().distanceTo(mc.player) > 20) {
                mc.getSoundManager().stop(en.getValue());
                return true;
            }
            return false;
        });
    }
}
