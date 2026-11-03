package com.hitori.client.cine;

import com.hitori.Hitori;
import com.hitori.client.CameraApp;
import com.hitori.client.ClientState;
import com.hitori.client.ShaderCompat;
import com.hitori.entity.FixedCamera;
import com.hitori.game.Cameras;
import com.hitori.game.GameData;
import com.hitori.world.ApartmentBuilder;
import com.hitori.world.Layout;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * CI only ({@code -Dhitori.trailer=true}, Oculus + HitoriShaders, Xvfb + software GL): films the trailer footage.
 * Ported from the SOUNAN rig.
 * <p>
 * Each shot is set up while the game runs (teleport, set dressing on the server, actors on the client), then the game
 * is paused (an empty pause screen: no server ticks, no level ticks) and every frame is posed by hand from a virtual
 * clock: the view (the player, or one of the fixed cameras for the camera app's feeds), the actors (client-side
 * puppets), fear / danger (the pack's rain / thunder uniforms; one game tick per filmed frame so Iris refreshes its
 * per-tick uniforms), the eyes (blindness), the camera app's IR (night vision on the feed's camera), the shader clock
 * and the partial tick. While a shot is filmed the client ticks follow the film clock (20 per film second: animated
 * textures, particles), and timed changes to the world (a light going out, the fusuma sliding) are made on the client
 * at once (mirrored on the server) and given a few unfilmed frames to settle. So the footage is smooth at any real
 * frame rate. Frames go to {@code run/trailer/<NN_shot>/00000.png}; with {@code -Dhitori.trailer.preview=true} only
 * three key frames per shot go to {@code run/trailer_preview/}. Log: {@code run/hitori_trailer.txt}.
 */
@Mod.EventBusSubscriber(modid = Hitori.MODID, value = Dist.CLIENT)
public final class Trailer {
    static final boolean ENABLED = Boolean.getBoolean("hitori.trailer");
    private static final boolean PREVIEW = Boolean.getBoolean("hitori.trailer.preview");
    private static final Set<String> ONLY = parseOnly(System.getProperty("hitori.trailer.only", ""));
    public static final int FPS = 30;
    private static final int WARMUP = 8;
    private static final int KEY_REPEAT = 3;
    /** Unfilmed frames after a timed change to the world (the chunk rebuild, the light engine). */
    private static final int SETTLE = 4;
    private static final long WALL_LIMIT_NS = (long) (3.3 * 3600.0 * 1e9);

    private enum Phase { IDLE, TITLE, WORLD, PREP, SETUP, CAPTURE, DRAIN, DONE }

    private static Phase phase = Phase.IDLE;
    private static int wait;
    private static int ticks;
    private static int prepStep;
    private static int prepTries;
    private static long startNs;
    private static final List<TrailerShots.Shot> SHOTS = new ArrayList<>();
    private static int shotIndex = -1;
    private static TrailerShots.Shot shot;
    private static int frame;
    private static int captured;
    private static int settle;
    private static long shotStartNs;
    private static boolean posed;
    private static boolean captureThisFrame;
    private static float frameT;
    private static int filmTicksDone;
    private static final TrailerShots.Frame F = new TrailerShots.Frame();
    private static final List<String> LOG = new ArrayList<>();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hitori-trailer-writer");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger PENDING = new AtomicInteger();
    private static int nextPuppetId = -2_000_000;
    private static float fov = 70.0f, roll, shakeYaw, shakePitch;
    private static ItemInHandRenderer handRenderer;
    private static boolean handLookedUp;
    private static final List<Runnable> UNDO = new ArrayList<>();
    private static final RandomSource RAND = RandomSource.create(20261102L);

    private Trailer() {}

    public static boolean enabled() {
        return ENABLED;
    }

    /** True while frames are being posed and filmed (other camera / overlay code stands aside). */
    public static boolean active() {
        return ENABLED && phase == Phase.CAPTURE;
    }

    // =========================================================================================== start-up
    public static void onTitle() {
        if (!ENABLED || phase != Phase.IDLE) return;
        phase = Phase.TITLE;
        wait = 60;
        startNs = System.nanoTime();
        Minecraft.getInstance().options.pauseOnLostFocus = false;
        log("title screen (preview=" + PREVIEW + ", only=" + ONLY + ")");
    }

    /** Client tick (END): everything that happens while the game runs; while filming, the film's own ticks. */
    public static void tick(Minecraft mc) {
        if (!ENABLED || phase == Phase.IDLE || phase == Phase.DONE) return;
        if (System.nanoTime() - startNs > WALL_LIMIT_NS || ++ticks > 20 * 60 * 600) {
            log("global timeout");
            finish(mc);
            return;
        }
        if (phase == Phase.CAPTURE) {
            // one client tick of the film (the clock is held to the film): particles move, drips fall
            filmTick(mc);
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (phase) {
            case TITLE -> {
                if (mc.getOverlay() != null) return;
                createWorld(mc);
                phase = Phase.WORLD;
            }
            case WORLD -> {
                if (mc.level == null || mc.player == null || mc.screen != null) return;
                phase = Phase.PREP;
                prepStep = 0;
                wait = 80;
            }
            case PREP -> prep(mc);
            case SETUP -> {
                // set-up done: pause and film
                shot.beforeCapture(mc);
                logLight(mc, "end of set-up");
                if (PREVIEW) saveLive(mc);
                mc.setScreen(new HoldScreen());
                phase = Phase.CAPTURE;
                frame = 0;
                captured = 0;
                settle = 0;
                filmTicksDone = 0;
                posed = false;
                shotStartNs = System.nanoTime();
            }
            case DRAIN -> {
                if (PENDING.get() > 0) return;
                finish(mc);
            }
            default -> {
            }
        }
    }

    private static void prep(Minecraft mc) {
        switch (prepStep++) {
            case 0 -> {
                // the new world starts the night by itself (autoStart) and brings the player into 203
                if (mc.level == null || mc.level.dimension() != Layout.DIMENSION) {
                    prepTries++;
                    if (prepTries == 6) cmd(mc, "hitori start");
                    if (prepTries < 20) {
                        prepStep = 0;
                        wait = 40;
                        return;
                    }
                    log("never got into the apartment's dimension");
                }
                cmd(mc, "gamerule sendCommandFeedback false");
                cmd(mc, "gamerule doMobSpawning false");
                cmd(mc, "gamerule doDaylightCycle false");
                cmd(mc, "gamerule doWeatherCycle false");
                wait = 120;
            }
            case 1 -> {
                cmd(mc, "gamemode creative");
                cmd(mc, "hitori debug peaceful true");
                cmd(mc, "hitori debug director true");
                cmd(mc, "effect give @s minecraft:invisibility infinite 0 true");
                cmd(mc, "item replace entity @s weapon.offhand with air");
                mc.options.particles().set(ParticleStatus.ALL);
                mc.options.bobView().set(false);
                mc.options.framerateLimit().set(260);
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                mc.options.hideGui = false;
                mc.options.renderDebug = false;
                ClientState.introTicks = -1;
                cameras();
                wait = 60;
            }
            case 2 -> {
                LocalPlayer p = mc.player;
                if (p != null) {
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                }
                log("in " + (mc.level == null ? "?" : mc.level.dimension().location()) + ", shaders " + ShaderCompat.active()
                        + ", iris clock " + IrisClock.describe() + ", gui scale " + mc.getWindow().getGuiScale()
                        + ", window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
                        + ", cameras " + CameraApp.cameras(mc).size());
                SHOTS.clear();
                SHOTS.addAll(TrailerShots.all());
                SHOTS.removeIf(s -> !ONLY.isEmpty() && !ONLY.contains(s.name) && !ONLY.contains(s.name.substring(3)));
                log(SHOTS.size() + " shots, " + String.format(Locale.ROOT, "%.1f", SHOTS.stream().mapToDouble(s -> s.duration).sum()) + " s");
                shotIndex = -1;
                nextShot(mc);
            }
            default -> {
            }
        }
    }

    /** The four fixed cameras of the night (CAM2 stands on the hall floor at its north end, looking down the hall). */
    private static void cameras() {
        server(l -> {
            GameData data = GameData.get(l.getServer());
            Cameras.removeAll(l);
            data.cameras.clear();
            for (TrailerShots.CamSpot c : TrailerShots.CAMERAS) {
                float pitch = c.pitch();
                if (Float.isNaN(pitch)) pitch = Cameras.suggestedPitch(l, c.feet(), c.facing());
                data.cameras.add(new GameData.CamSpot(c.number(), c.feet().x, c.feet().y, c.feet().z, c.facing().toYRot(), pitch, false));
            }
            data.setDirty();
            Cameras.ensure(l, data);
            Hitori.LOGGER.info("[hitori] trailer: {} cameras", data.cameras.size());
        });
    }

    private static void nextShot(Minecraft mc) {
        shotIndex++;
        if (shotIndex >= SHOTS.size()) {
            log("all shots done; writing " + PENDING.get() + " frames");
            phase = Phase.DRAIN;
            return;
        }
        shot = SHOTS.get(shotIndex);
        try {
            shot.reset();
            shot.prepare();
            F.reset();
            shot.frame(0.0f, F);
            Vec3 c = shot.setupAt != null ? shot.setupAt : F.cam;
            cmd(mc, String.format(Locale.ROOT, "execute in %s run tp @s %.3f %.3f %.3f %.2f %.2f", Layout.DIMENSION.location(),
                    c.x, c.y - 1.62, c.z, F.yaw, F.pitch));
            cmd(mc, "item replace entity @s weapon.mainhand with " + F.main.id);
            cmd(mc, "item replace entity @s weapon.offhand with " + F.off.id);
            // the server keeps the shot's opening fear / danger, so the set-up ends on the values the film starts with
            cmd(mc, String.format(Locale.ROOT, "hitori debug fear %.2f %.2f 4000", clamp01(F.fear), clamp01(F.danger)));
            TrailerShots.Shot dressed = shot;
            server(dressed::dress);
            shot.setup(mc);
            LocalPlayer lp = mc.player;
            if (lp != null) {
                lp.getAbilities().flying = true;
                lp.onUpdateAbilities();
                lp.removeEffectNoUpdate(MobEffects.BLINDNESS);
            }
            // the view settles where the film starts (Iris eases eye brightness with a 3 s half-life)
            setView(mc, F, true);
            // pose the new actors at once, so nothing pops in on the first filmed frame
            F.reset();
            shot.frame(0.0f, F);
            for (Puppet pp : shot.puppets) pp.apply(PartialTick.current(mc), 0.0f);
            log("set-up " + label() + " (" + shot.duration + " s, " + shot.puppets.size() + " actors, view " + (F.view == 0 ? "eyes" : "CAM" + F.view) + ")");
        } catch (Exception e) {
            log("set-up failed " + label() + ": " + e);
            Hitori.LOGGER.error("[hitori] trailer set-up failed", e);
        }
        phase = Phase.SETUP;
        wait = shot.setupTicks;
    }

    private static String label() {
        return shot == null ? "?" : shot.name;
    }

    // =========================================================================================== per frame
    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (!ENABLED || phase != Phase.CAPTURE) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            posed = false;
            if (!mc.isPaused() || mc.player == null || mc.level == null) return;
            int frames = frameCount();
            if (frame >= WARMUP + frames) return;
            float t = frameTime(frame);
            if (PREVIEW) {
                captureThisFrame = frame >= WARMUP && (frame - WARMUP) % KEY_REPEAT == KEY_REPEAT - 1;
            } else {
                captureThisFrame = frame >= WARMUP;
            }
            // timed changes to the world: made now, then a few frames for the chunks and the light to follow
            for (TrailerShots.Event ev : shot.events) {
                if (!ev.fired && t >= ev.time) {
                    ev.fired = true;
                    try {
                        ev.client.run();
                        if (ev.server != null) server(ev.server);
                        log(String.format(Locale.ROOT, "  %s at %.2f s: %s", label(), t, ev.label));
                    } catch (Exception e) {
                        log("  event failed " + ev.label + ": " + e);
                    }
                    settle = SETTLE;
                }
            }
            if (settle > 0) captureThisFrame = false;
            frameT = t;
            pose(mc, t);
            posed = true;
        } else if (posed) {
            if (frame == WARMUP && settle == 0) logLight(mc, "first frame");
            if (settle > 0) {
                settle--;
            } else {
                if (captureThisFrame) save(mc);
                frame++;
            }
            if (frame >= WARMUP + frameCount()) {
                endShot(mc);
                return;
            }
            // the client ticks the next frame needs (the film's 20 per second; none while settling)
            int target = settle > 0 ? filmTicksDone : (int) Math.floor(frameTime(frame) * 20.0f);
            int n = Math.max(0, Math.min(10, target - filmTicksDone));
            filmTicksDone += n;
            ClientClock.next(n);
        }
    }

    private static float frameTime(int f) {
        if (f < WARMUP) return 0.0f;
        if (PREVIEW) {
            int k = Math.min(2, (f - WARMUP) / KEY_REPEAT);
            return keyTime(k);
        }
        return (f - WARMUP) / (float) FPS;
    }

    private static int frameCount() {
        return PREVIEW ? 3 * KEY_REPEAT : Math.round(shot.duration * FPS);
    }

    private static float keyTime(int k) {
        if (shot.keys != null) return Math.min(shot.keys[k], shot.duration - 1.0f / FPS);
        return switch (k) {
            case 0 -> 0.0f;
            case 1 -> shot.duration * 0.5f;
            default -> shot.duration - 1.0f / FPS;
        };
    }

    private static void pose(Minecraft mc, float t) {
        F.reset();
        shot.frame(t, F);
        LocalPlayer p = mc.player;
        // the partial tick is frozen while paused: choose it so that age = tickCount + partial is exact
        float ageTicks = t * 20.0f;
        float partial = PartialTick.set(mc, ageTicks - (float) Math.floor(ageTicks));
        setView(mc, F, false);
        double x = F.cam.x, y = F.cam.y - 1.62, z = F.cam.z;
        if (F.view != 0) {
            // looking through a camera: the body (invisible, empty-handed) waits where it waited during the set-up
            Vec3 s = shot.setupAt != null ? shot.setupAt : F.cam;
            x = s.x;
            y = s.y - 1.62;
            z = s.z;
        }
        p.setPos(x, y, z);
        p.xo = x;
        p.yo = y;
        p.zo = z;
        p.xOld = x;
        p.yOld = y;
        p.zOld = z;
        p.setYRot(F.yaw);
        p.yRotO = F.yaw;
        p.setXRot(F.pitch);
        p.xRotO = F.pitch;
        p.yHeadRot = F.yaw;
        p.yHeadRotO = F.yaw;
        p.yBodyRot = F.yaw;
        p.yBodyRotO = F.yaw;
        p.xBob = p.xBobO = F.pitch + F.handSwayPitch;
        p.yBob = p.yBobO = F.yaw + F.handSwayYaw;
        fov = F.fov;
        roll = F.roll;
        shakeYaw = F.shakeYaw;
        shakePitch = F.shakePitch;
        float fear = clamp01(F.fear), danger = clamp01(F.danger);
        mc.level.setRainLevel(fear);
        mc.level.setThunderLevel(danger);
        // Iris refreshes its per-tick uniforms (rainStrength = fear, thunderStrength = danger) only when the level's game
        // time moves on, and a paused game's never does: one tick per filmed frame keeps them on the film's values
        mc.level.setGameTime(mc.level.getGameTime() + 1L);
        ClientState.fear = ClientState.fearShown = ClientState.fearPrev = fear;
        ClientState.danger = ClientState.dangerShown = ClientState.dangerPrev = danger;
        ClientState.clientTicks = (int) Math.floor(ageTicks) + 1000;
        if (F.clock >= 0) ClientState.clock = (float) F.clock;
        mc.gameRenderer.setRenderHand(F.hand);
        hold(p, InteractionHand.MAIN_HAND, F.main.stack());
        hold(p, InteractionHand.OFF_HAND, F.off.stack());
        ItemInHandRenderer hands = hands(mc);
        if (hands != null) hands.tick();
        // the eyes: shut = blindness on the view (the pack's eyelids), opening = its last second running out
        eyes(p, F.eyes);
        for (Puppet pp : shot.puppets) pp.apply(partial, ageTicks);
        if (shot.irisClock) IrisClock.set(shot.clockBase + t);
        mc.getToasts().clear();
    }

    /** The view of a frame: your own eyes, or the fixed camera CAM n (the camera app's feed, night vision for its IR). */
    private static void setView(Minecraft mc, TrailerShots.Frame f, boolean setup) {
        Entity view = mc.player;
        for (FixedCamera c : CameraApp.cameras(mc)) {
            boolean hide = (f.hide & (1 << c.number())) != 0;
            if (c.isInvisible() != hide) c.setInvisible(hide);
            if (f.view == c.number()) {
                view = c;
                if (f.camTicks >= 0) c.tickCount = f.camTicks;
                c.place(f.cam, f.yaw, f.pitch);
                if (!c.hasEffect(MobEffects.NIGHT_VISION)) {
                    c.forceAddEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1_000_000, 0, false, false, false), null);
                }
            } else if (f.camTicks >= 0 && f.posedCam == c.number()) {
                // a camera filmed from outside: its REC LED blinks on the film's clock
                c.tickCount = f.camTicks;
            }
            if (f.view != c.number() && c.hasEffect(MobEffects.NIGHT_VISION)) c.removeEffectNoUpdate(MobEffects.NIGHT_VISION);
        }
        if (view != null && mc.getCameraEntity() != view) mc.setCameraEntity(view);
    }

    private static void hold(LocalPlayer p, InteractionHand hand, ItemStack want) {
        ItemStack have = p.getItemInHand(hand);
        if (!ItemStack.isSameItem(have, want) || have.isEmpty() != want.isEmpty()) p.setItemInHand(hand, want);
    }

    private static void eyes(LocalPlayer p, float shut) {
        if (shut <= 0.001f) {
            if (p.hasEffect(MobEffects.BLINDNESS)) p.removeEffectNoUpdate(MobEffects.BLINDNESS);
            return;
        }
        // Iris: blindness = remaining duration / 20 ticks
        int duration = shut >= 0.999f ? 20 * 60 : Math.max(1, Math.round(shut * 20.0f));
        p.removeEffectNoUpdate(MobEffects.BLINDNESS);
        p.forceAddEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0, false, false, false), null);
    }

    /** One client tick of the film: particles, drips off the eaves, rain splashes, the shot's own particles. */
    private static void filmTick(Minecraft mc) {
        if (mc.level == null || shot == null) return;
        try {
            Ambience.tick(mc.level, F.cam, RAND);
            shot.particles(mc.level, RAND);
            mc.particleEngine.tick();
        } catch (Exception e) {
            log("film tick failed: " + e);
        }
    }

    private static void save(Minecraft mc) {
        try {
            NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            Path out;
            if (PREVIEW) {
                int k = Math.min(2, (frame - WARMUP) / KEY_REPEAT);
                out = mc.gameDirectory.toPath().resolve("trailer_preview").resolve(label() + "_" + k + ".png");
            } else {
                out = mc.gameDirectory.toPath().resolve("trailer").resolve(label()).resolve(String.format(Locale.ROOT, "%05d.png", captured));
            }
            captured++;
            Files.createDirectories(out.getParent());
            PENDING.incrementAndGet();
            WRITER.execute(() -> {
                try {
                    img.writeToFile(out);
                } catch (IOException e) {
                    Hitori.LOGGER.error("[hitori] trailer frame write failed", e);
                } finally {
                    img.close();
                    PENDING.decrementAndGet();
                }
            });
            while (PENDING.get() > 24) Thread.onSpinWait();
        } catch (Exception e) {
            log("frame capture failed: " + e);
        }
    }

    private static void endShot(Minecraft mc) {
        long ms = (System.nanoTime() - shotStartNs) / 1_000_000L;
        log(String.format(Locale.ROOT, "filmed %s: %d frames in %.1f s (%.0f ms/frame)", label(), captured, ms / 1000.0, ms / (double) Math.max(1, frame)));
        for (Puppet pp : shot.puppets) pp.remove();
        shot.puppets.clear();
        for (int i = UNDO.size() - 1; i >= 0; i--) {
            try {
                UNDO.get(i).run();
            } catch (Exception ignored) {
            }
        }
        UNDO.clear();
        for (FixedCamera c : CameraApp.cameras(mc)) {
            c.setInvisible(false);
            c.removeEffectNoUpdate(MobEffects.NIGHT_VISION);
        }
        if (mc.player != null) {
            mc.player.removeEffectNoUpdate(MobEffects.BLINDNESS);
            if (mc.getCameraEntity() != mc.player) mc.setCameraEntity(mc.player);
        }
        mc.gameRenderer.setRenderHand(true);
        mc.setScreen(null);
        nextShot(mc);
    }

    /** Diagnostics: the last frame drawn while the game still ran (before the pause), next to the filmed ones. */
    private static void saveLive(Minecraft mc) {
        try {
            NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            Path out = mc.gameDirectory.toPath().resolve("trailer_preview").resolve(label() + "_live.png");
            Files.createDirectories(out.getParent());
            img.writeToFile(out);
            img.close();
        } catch (Exception e) {
            log("live frame failed: " + e);
        }
    }

    /** Diagnostics: what the shader pack is told about the light at the eye and the channels. */
    private static void logLight(Minecraft mc, String when) {
        if (mc.player == null || mc.level == null) return;
        Entity v = mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity();
        BlockPos eye = BlockPos.containing(v.getEyePosition());
        StringBuilder fx = new StringBuilder();
        for (var e : mc.player.getActiveEffects()) fx.append(e.getEffect().getDescriptionId()).append(' ');
        log(String.format(Locale.ROOT, "  %s: view %s eye %s sky %d block %d, rain %.2f thunder %.2f, effects [%s] %s",
                when, v.getClass().getSimpleName(), eye.toShortString(), mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, eye),
                mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, eye),
                mc.level.getRainLevel(1.0f), mc.level.getThunderLevel(1.0f), fx.toString().trim(), IrisUniforms.describe()));
    }

    // =========================================================================================== view
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event) {
        if (active() && event.usedConfiguredFov()) event.setFOV(fov);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        if (!active()) return;
        event.setYaw(event.getYaw() + shakeYaw);
        event.setPitch(event.getPitch() + shakePitch);
        event.setRoll(roll);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void gui(RenderGuiEvent.Pre event) {
        if (ENABLED && (phase == Phase.CAPTURE || phase == Phase.SETUP)) event.setCanceled(true);
    }

    /** The main hand drawn by the film when it lifts the knife (vanilla's first-person transform, then the raise). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void hand(RenderHandEvent event) {
        if (!active() || event.getHand() != InteractionHand.MAIN_HAND || !F.customHand) return;
        Minecraft mc = Minecraft.getInstance();
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || mc.player == null) return;
        event.setCanceled(true);
        PoseStack ps = event.getPoseStack();
        HumanoidArm arm = mc.player.getMainArm();
        boolean right = arm == HumanoidArm.RIGHT;
        int side = right ? 1 : -1;
        float r = TrailerShots.easeSine(F.raise);
        ps.pushPose();
        ps.translate(side * 0.56f, -0.52f + event.getEquipProgress() * -0.6f, -0.72f);
        // the walk: the hand sways with the steps
        ps.translate(side * 0.012f * F.handBobX, 0.018f * F.handBob, 0.0f);
        // the raise: up and back, the point tipping down toward what is in front of you
        ps.translate(side * -0.10f * r, 0.30f * r, 0.12f * r);
        ps.mulPose(Axis.XP.rotationDegrees(-38.0f * r));
        ps.mulPose(Axis.ZP.rotationDegrees(side * 12.0f * r));
        hands(mc).renderItem(mc.player, stack, right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !right, ps, event.getMultiBufferSource(), event.getPackedLight());
        ps.popPose();
    }

    // =========================================================================================== helpers
    static void cmd(Minecraft mc, String command) {
        if (mc.player != null) mc.player.connection.sendCommand(command);
    }

    /** Runs a job on the integrated server's thread, in the apartment's level. */
    static void server(Consumer<ServerLevel> job) {
        MinecraftServer s = Minecraft.getInstance().getSingleplayerServer();
        if (s == null) return;
        s.execute(() -> {
            ServerLevel l = s.getLevel(Layout.DIMENSION);
            if (l != null) {
                try {
                    job.accept(l);
                } catch (Exception e) {
                    Hitori.LOGGER.error("[hitori] trailer set dressing failed", e);
                }
            }
        });
    }

    /** A block changed on the client only (the server never hears of it); put back when the shot ends. */
    static void clientBlock(BlockPos pos, BlockState state) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockState old = level.getBlockState(pos);
        if (old == state) return;
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE | Block.UPDATE_KNOWN_SHAPE);
        UNDO.add(() -> {
            ClientLevel l = Minecraft.getInstance().level;
            if (l != null) l.setBlock(pos, old, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE | Block.UPDATE_KNOWN_SHAPE);
        });
    }

    /** A block changed on the client at once, the server told the same (it sends it on after the pause). */
    static void clientMirror(BlockPos pos, BlockState state) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.getBlockState(pos) == state) return;
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE | Block.UPDATE_KNOWN_SHAPE);
    }

    static <T extends Entity> Puppet puppet(EntityType<T> type) {
        Minecraft mc = Minecraft.getInstance();
        T e = type.create(mc.level);
        if (e == null || mc.level == null) throw new IllegalStateException("cannot create " + type);
        e.setNoGravity(true);
        e.noPhysics = true;
        e.setSilent(true);
        int id = nextPuppetId--;
        e.setId(id);
        e.setPos(25.5, Layout.FEET_Y, 10.5);
        mc.level.putNonPlayerEntity(id, e);
        return new Puppet(e, id);
    }

    /**
     * A set-dressing prop: a block or item display (client-only), its transformation given as a translation (the
     * display's origin is the entity position), a rotation (degrees about y, then x, then z) and a uniform scale.
     */
    static Puppet display(BlockState block, ItemStack item, String itemContext, Vec3 at, float yRot, float xRot, float zRot,
                          float scale, Vec3 translate) {
        return display(block, item, itemContext, at, yRot, xRot, zRot, new Vec3(scale, scale, scale), translate);
    }

    static Puppet display(BlockState block, ItemStack item, String itemContext, Vec3 at, float yRot, float xRot, float zRot,
                          Vec3 scale, Vec3 translate) {
        Minecraft mc = Minecraft.getInstance();
        EntityType<?> type = block != null ? EntityType.BLOCK_DISPLAY : EntityType.ITEM_DISPLAY;
        Entity e = type.create(mc.level);
        if (e == null || mc.level == null) throw new IllegalStateException("cannot create a display");
        CompoundTag tag = new CompoundTag();
        if (block != null) {
            tag.put("block_state", NbtUtils.writeBlockState(block));
        } else {
            tag.put("item", item.save(new CompoundTag()));
            tag.putString("item_display", itemContext);
        }
        org.joml.Quaternionf q = new org.joml.Quaternionf().rotateY((float) Math.toRadians(yRot)).rotateX((float) Math.toRadians(xRot))
                .rotateZ((float) Math.toRadians(zRot));
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", floats(q.x, q.y, q.z, q.w));
        tr.put("right_rotation", floats(0.0f, 0.0f, 0.0f, 1.0f));
        tr.put("translation", floats((float) translate.x, (float) translate.y, (float) translate.z));
        tr.put("scale", floats((float) scale.x, (float) scale.y, (float) scale.z));
        tag.put("transformation", tr);
        tag.putFloat("view_range", 4.0f);
        e.load(tag);
        e.setNoGravity(true);
        int id = nextPuppetId--;
        e.setId(id);
        e.setPos(at.x, at.y, at.z);
        mc.level.putNonPlayerEntity(id, e);
        e.tick();
        Puppet p = new Puppet(e, id);
        p.at(at);
        p.prop = true;
        return p;
    }

    private static ListTag floats(float... v) {
        ListTag l = new ListTag();
        for (float f : v) l.add(FloatTag.valueOf(f));
        return l;
    }

    private static ItemInHandRenderer hands(Minecraft mc) {
        if (!handLookedUp) {
            handLookedUp = true;
            try {
                for (Field f : mc.gameRenderer.getClass().getDeclaredFields()) {
                    if (f.getType() == ItemInHandRenderer.class) {
                        f.setAccessible(true);
                        handRenderer = (ItemInHandRenderer) f.get(mc.gameRenderer);
                        break;
                    }
                }
            } catch (Exception e) {
                log("no item-in-hand renderer: " + e);
            }
            if (handRenderer == null) handRenderer = mc.getEntityRenderDispatcher().getItemInHandRenderer();
            log("item-in-hand renderer " + (handRenderer != null));
        }
        return handRenderer;
    }

    static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static Set<String> parseOnly(String s) {
        Set<String> out = new HashSet<>();
        for (String p : s.split(",")) {
            if (!p.isBlank() && !p.trim().equals("all")) out.add(p.trim());
        }
        return out;
    }

    private static void createWorld(Minecraft mc) {
        try {
            String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "HitoriTrailer", "");
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            LevelSettings settings = new LevelSettings("HitoriTrailer", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules,
                    WorldDataConfiguration.DEFAULT);
            WorldOptions options = new WorldOptions(20261102L, false, false);
            mc.createWorldOpenFlows().createFreshLevel(folder, settings, options,
                    ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            log("world creation requested");
        } catch (Exception e) {
            log("could not create world: " + e);
            Hitori.LOGGER.error("[hitori] could not create world", e);
        }
    }

    static void log(String s) {
        LOG.add(s);
        Hitori.LOGGER.info("[hitori] trailer: {}", s);
        try {
            Files.writeString(Path.of("hitori_trailer.txt"), String.join("\n", LOG) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void finish(Minecraft mc) {
        phase = Phase.DONE;
        log("finished");
        mc.stop();
    }

    // =========================================================================================== pieces
    /** An empty pause screen: the world stands still and is drawn without the HUD; the shot may draw over it. */
    static final class HoldScreen extends Screen {
        HoldScreen() {
            super(Component.empty());
        }

        @Override
        public boolean isPauseScreen() {
            return true;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
            if (!active() || shot == null || !posed) return;
            try {
                shot.overlay(g, minecraft, g.guiWidth(), g.guiHeight(), frameT, F);
            } catch (Exception e) {
                log("overlay failed: " + e);
            }
        }
    }

    /** Projects a world point to GUI coordinates with the frame's view (yaw, pitch, roll, fov); null behind the eye. */
    static float[] project(Minecraft mc, TrailerShots.Frame f, Vec3 p, int guiW, int guiH) {
        double yaw = Math.toRadians(f.yaw + f.shakeYaw), pitch = Math.toRadians(f.pitch + f.shakePitch);
        Vec3 fw = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        Vec3 up = right.cross(fw);
        Vec3 d = p.subtract(f.cam);
        double z = d.dot(fw);
        if (z < 0.01) return null;
        double tan = Math.tan(Math.toRadians(f.fov) / 2.0);
        double aspect = guiW / (double) guiH;
        double nx = d.dot(right) / z / (tan * aspect), ny = d.dot(up) / z / tan;
        double rr = Math.toRadians(-f.roll);
        double ax = nx * aspect, ay = ny;
        double rx = ax * Math.cos(rr) - ay * Math.sin(rr), ry = ax * Math.sin(rr) + ay * Math.cos(rr);
        nx = rx / aspect;
        ny = ry;
        return new float[]{(float) ((nx + 1.0) * 0.5 * guiW), (float) ((1.0 - ny) * 0.5 * guiH)};
    }

    /** A flat quad in GUI coordinates (corners in order), one colour. */
    static void quad(GuiGraphics g, float[] a, float[] b, float[] c, float[] d, int argb) {
        if (a == null || b == null || c == null || d == null) return;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        float al = (argb >>> 24) / 255.0f, r = (argb >> 16 & 255) / 255.0f, gr = (argb >> 8 & 255) / 255.0f, bl = (argb & 255) / 255.0f;
        vc.vertex(m, a[0], a[1], 0.0f).color(r, gr, bl, al).endVertex();
        vc.vertex(m, b[0], b[1], 0.0f).color(r, gr, bl, al).endVertex();
        vc.vertex(m, c[0], c[1], 0.0f).color(r, gr, bl, al).endVertex();
        vc.vertex(m, d[0], d[1], 0.0f).color(r, gr, bl, al).endVertex();
        g.flush();
    }

    /** A client-side actor posed every frame (no AI, no physics, invisible to the server). */
    public static final class Puppet {
        final Entity e;
        final int id;
        double x, y, z;
        float bodyYaw, headYaw, headPitch;
        float walkPos, walkSpeed;
        int ageOffset;
        boolean prop;

        Puppet(Entity e, int id) {
            this.e = e;
            this.id = id;
        }

        public Puppet at(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Puppet at(Vec3 v) {
            return at(v.x, v.y, v.z);
        }

        public Puppet look(float bodyYaw, float headYaw, float headPitch) {
            this.bodyYaw = bodyYaw;
            this.headYaw = headYaw;
            this.headPitch = headPitch;
            return this;
        }

        public Puppet walk(float position, float speed) {
            this.walkPos = position;
            this.walkSpeed = speed;
            return this;
        }

        public Entity entity() {
            return e;
        }

        void apply(float partial, float ageTicks) {
            e.setPos(x, y, z);
            e.xo = x;
            e.yo = y;
            e.zo = z;
            e.xOld = x;
            e.yOld = y;
            e.zOld = z;
            if (prop) return;
            e.setYRot(bodyYaw);
            e.yRotO = bodyYaw;
            e.setXRot(headPitch);
            e.xRotO = headPitch;
            e.tickCount = (int) Math.floor(ageTicks) + ageOffset;
            if (e instanceof LivingEntity le) {
                le.yBodyRot = bodyYaw;
                le.yBodyRotO = bodyYaw;
                le.yHeadRot = headYaw;
                le.yHeadRotO = headYaw;
                // walk animation: position(partial) == walkPos and speed(partial) == walkSpeed
                WalkAnimationState w = le.walkAnimation;
                float s = walkSpeed;
                float current = w.position(1.0f);
                float target = walkPos + s * (1.0f - partial);
                w.update(target - current - 2.0f * s, 1.0f);
                w.update(s, 1.0f);
                w.update(s, 1.0f);
            }
        }

        void remove() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) mc.level.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }
    }

    /** The rain the night is made of, on the film's ticks (the game's own ambience stands still while paused). */
    static final class Ambience {
        static void tick(ClientLevel level, Vec3 ear, RandomSource r) {
            if (level.dimension() != Layout.DIMENSION) return;
            // water running off the roof: drips at the eaves near the camera
            for (int i = 0; i < 3; i++) {
                int sideOf = r.nextInt(2);
                double x = ApartmentBuilder.ROOF_X1 + r.nextDouble() * (ApartmentBuilder.ROOF_X2 - ApartmentBuilder.ROOF_X1 + 1);
                double z = sideOf == 0 ? ApartmentBuilder.ROOF_Z1 - 0.05 : ApartmentBuilder.ROOF_Z2 + 1.05;
                if ((x - ear.x) * (x - ear.x) + (z - ear.z) * (z - ear.z) > 20 * 20) continue;
                level.addParticle(ParticleTypes.FALLING_WATER, x, 73.9, z, 0, 0, 0);
            }
            // raindrops splashing where the sky is open
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int i = 0; i < 12; i++) {
                double x = ear.x + (r.nextDouble() - 0.5) * 24, z = ear.z + (r.nextDouble() - 0.5) * 24;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
                if (top > ear.y + 10) continue;
                p.set(x, top + 0.5, z);
                if (!level.canSeeSky(p)) continue;
                level.addParticle(ParticleTypes.RAIN, x, top + 0.02, z, 0, 0, 0);
            }
        }
    }

    /** The client's tick clock, held to the film while it is paused (reflection: Minecraft#timer). */
    static final class ClientClock {
        private static Object timer;
        private static Field lastMs, partial;
        private static boolean probed;

        /** The next frame runs exactly this many client ticks. */
        static void next(int ticks) {
            probe();
            if (timer == null) return;
            try {
                lastMs.setLong(timer, Util.getMillis());
                partial.setFloat(timer, ticks);
            } catch (Exception e) {
                timer = null;
            }
        }

        private static void probe() {
            if (probed) return;
            probed = true;
            try {
                Minecraft mc = Minecraft.getInstance();
                for (Field f : Minecraft.class.getDeclaredFields()) {
                    if (f.getType() == net.minecraft.client.Timer.class) {
                        f.setAccessible(true);
                        timer = f.get(mc);
                    }
                }
                Class<?> tc = net.minecraft.client.Timer.class;
                for (Field f : tc.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (f.getType() == long.class) {
                        f.setAccessible(true);
                        lastMs = f;
                    }
                }
                try {
                    partial = tc.getDeclaredField("partialTick");
                } catch (NoSuchFieldException e) {
                    for (Field f : tc.getDeclaredFields()) {
                        if (f.getType() == float.class && !java.lang.reflect.Modifier.isFinal(f.getModifiers())) {
                            partial = f;
                            break;
                        }
                    }
                }
                if (partial != null) partial.setAccessible(true);
                if (lastMs == null || partial == null) timer = null;
            } catch (Exception e) {
                timer = null;
            }
            log("client clock: " + (timer == null ? "not found" : "timer " + lastMs.getName() + " / " + partial.getName()));
        }
    }

    /** The frozen partial tick of a paused game (found by probing Minecraft's float fields once). */
    static final class PartialTick {
        private static Field field, pauseField;
        private static Method getter;
        private static boolean probed;

        /** What the renderer uses this frame: Minecraft#getPartialTick (pause-aware) when it exists. */
        static float current(Minecraft mc) {
            try {
                if (getter != null) return (Float) getter.invoke(mc);
            } catch (Exception ignored) {
            }
            return mc.getFrameTime();
        }

        static float set(Minecraft mc, float value) {
            if (!probed) {
                probed = true;
                try {
                    getter = Minecraft.class.getMethod("getPartialTick");
                } catch (Exception e) {
                    getter = null;
                }
                try {
                    for (Field f : Minecraft.class.getDeclaredFields()) {
                        if (f.getType() != float.class || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        f.setAccessible(true);
                        float old = f.getFloat(mc);
                        f.setFloat(mc, 0.4375f);
                        boolean hit = current(mc) == 0.4375f;
                        f.setFloat(mc, old);
                        if (hit) {
                            field = f;
                            break;
                        }
                    }
                    try {
                        pauseField = Minecraft.class.getDeclaredField("pausePartialTick");
                        pauseField.setAccessible(true);
                    } catch (NoSuchFieldException ignored) {
                        pauseField = null;
                    }
                } catch (Exception e) {
                    log("partial tick probe failed: " + e);
                }
                log("pause partial tick: getter " + (getter != null) + ", field " + (field == null ? "not found" : field.getName())
                        + ", pause field " + (pauseField == null ? "not found" : pauseField.getName()));
            }
            try {
                if (field != null) field.setFloat(mc, value);
                if (pauseField != null && pauseField != field) pauseField.setFloat(mc, value);
            } catch (Exception ignored) {
            }
            return current(mc);
        }
    }

    /** Iris / Oculus frameTimeCounter driven by the virtual clock (reflection; optional). */
    static final class IrisClock {
        private static Object timer;
        private static Field counter, lastStart;
        private static boolean looked;
        private static String status = "not looked up";

        static String describe() {
            lookup();
            return status;
        }

        static void set(float seconds) {
            lookup();
            if (timer == null) return;
            try {
                // Iris adds the time since lastStartTime when the frame begins. The frame start is cleared here: the frame
                // adds nothing, frameTimeCounter stays on the film clock, and the smoothed values (eye brightness) hold
                // what they settled on during the set-up (a negative frame time once poisoned them in the SOUNAN rig)
                counter.setFloat(timer, seconds % 3600.0f);
                if (lastStart != null) {
                    if (lastStart.getType() == OptionalLong.class) lastStart.set(timer, OptionalLong.empty());
                    else if (lastStart.getType() == long.class) lastStart.setLong(timer, 0L);
                }
            } catch (Exception e) {
                status = "failed: " + e;
                timer = null;
            }
        }

        private static void lookup() {
            if (looked) return;
            looked = true;
            for (String cn : new String[]{"net.irisshaders.iris.uniforms.SystemTimeUniforms", "net.coderbot.iris.uniforms.SystemTimeUniforms"}) {
                try {
                    Class<?> c = Class.forName(cn);
                    Field tf = c.getDeclaredField("TIMER");
                    tf.setAccessible(true);
                    Object t = tf.get(null);
                    Field fc = t.getClass().getDeclaredField("frameTimeCounter");
                    fc.setAccessible(true);
                    Field ls = null;
                    try {
                        ls = t.getClass().getDeclaredField("lastStartTime");
                        ls.setAccessible(true);
                    } catch (NoSuchFieldException ignored) {
                    }
                    timer = t;
                    counter = fc;
                    lastStart = ls;
                    status = "ok (" + cn + ", lastStartTime " + (ls == null ? "-" : ls.getType().getSimpleName()) + ")";
                    return;
                } catch (Throwable ignored) {
                }
            }
            status = "not found";
        }
    }

    /** Iris / Oculus: what the pack is told (eye brightness, blindness, night vision); reflection, best effort. */
    static final class IrisUniforms {
        static String describe() {
            StringBuilder sb = new StringBuilder();
            for (String cn : new String[]{"net.irisshaders.iris.uniforms.CommonUniforms", "net.coderbot.iris.uniforms.CommonUniforms"}) {
                try {
                    Class<?> c = Class.forName(cn);
                    for (String m : new String[]{"getEyeBrightness", "getBlindness", "getNightVision"}) {
                        try {
                            Method mm = c.getDeclaredMethod(m);
                            mm.setAccessible(true);
                            sb.append(m.substring(3)).append(' ').append(mm.invoke(null)).append("; ");
                        } catch (Throwable t) {
                            sb.append(m.substring(3)).append(" ?; ");
                        }
                    }
                    return "iris " + sb;
                } catch (Throwable ignored) {
                }
            }
            return "iris: no uniforms class";
        }
    }
}
