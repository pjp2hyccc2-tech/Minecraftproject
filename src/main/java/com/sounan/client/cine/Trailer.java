package com.sounan.client.cine;

import com.sounan.Sounan;
import com.sounan.client.ClientState;
import com.sounan.client.ShaderCompat;
import com.sounan.registry.ModDimensions;
import com.sounan.registry.ModItems;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.lang.reflect.Field;
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
 * CI only ({@code -Dsounan.trailer=true}, Oculus + SounanShaders, Xvfb + software GL): films the trailer footage.
 * <p>
 * Each shot is set up while the game runs (teleport, set dressing, actors), then the game is paused (an empty pause
 * screen: no server ticks, no client ticks) and every frame is posed by hand from a virtual clock: camera, actors
 * (client-side puppets), fear/danger (the shader's rain/thunder uniforms), the shader clock and the partial tick. So the
 * footage is smooth at any real frame rate. Frames go to {@code run/trailer/<NN>_<shot>/00000.png}; with
 * {@code -Dsounan.trailer.preview=true} only three key frames per shot go to {@code run/trailer_preview/}.
 */
@Mod.EventBusSubscriber(modid = Sounan.MODID, value = Dist.CLIENT)
public final class Trailer {
    static final boolean ENABLED = Boolean.getBoolean("sounan.trailer");
    private static final boolean PREVIEW = Boolean.getBoolean("sounan.trailer.preview");
    private static final Set<String> ONLY = parseOnly(System.getProperty("sounan.trailer.only", ""));
    public static final int FPS = 30;
    private static final int WARMUP = 8;
    private static final int KEY_REPEAT = 3;

    private enum Phase { IDLE, TITLE, WORLD, PREP, SETUP, CAPTURE, DRAIN, DONE }

    private static Phase phase = Phase.IDLE;
    private static int wait;
    private static int ticks;
    private static int prepStep;
    private static final List<TrailerShots.Shot> SHOTS = new ArrayList<>();
    private static int shotIndex = -1;
    private static TrailerShots.Shot shot;
    private static int frame;
    private static int captured;
    private static long shotStartNs;
    private static boolean posed;
    private static boolean captureThisFrame;
    private static final TrailerShots.Frame F = new TrailerShots.Frame();
    private static final List<String> LOG = new ArrayList<>();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sounan-trailer-writer");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger PENDING = new AtomicInteger();
    private static int nextPuppetId = -2_000_000;
    private static float fov = 70.0f, roll, shakeYaw, shakePitch;
    private static ItemInHandRenderer handRenderer;
    private static boolean handLookedUp;

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
        Minecraft.getInstance().options.pauseOnLostFocus = false;
        log("title screen (preview=" + PREVIEW + ", only=" + ONLY + ")");
    }

    /** Client tick (END): everything that happens while the game runs. */
    public static void tick(Minecraft mc) {
        if (!ENABLED || phase == Phase.IDLE || phase == Phase.DONE) return;
        if (++ticks > 20 * 60 * 170) {
            log("global timeout");
            finish(mc);
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
                wait = 60;
            }
            case PREP -> prep(mc);
            case SETUP -> {
                // set-up done: pause and film
                logLight(mc, "end of set-up");
                if (PREVIEW) saveLive(mc);
                mc.setScreen(new HoldScreen());
                phase = Phase.CAPTURE;
                frame = 0;
                captured = 0;
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
                cmd(mc, "gamerule doMobSpawning false");
                cmd(mc, "gamerule sendCommandFeedback false");
                // the new world starts the game by itself (autoStart); make sure, and let the intro run out
                if (mc.level == null || !ModDimensions.isMountain(mc.level)) cmd(mc, "sounan start");
                wait = 420;
            }
            case 1 -> {
                cmd(mc, "gamemode creative");
                cmd(mc, "sounan debug peaceful true");
                cmd(mc, "sounan debug cinematic true");
                cmd(mc, "effect give @s minecraft:invisibility infinite 0 true");
                cmd(mc, "item replace entity @s weapon.offhand with air");
                mc.options.particles().set(ParticleStatus.DECREASED);
                mc.options.bobView().set(false);
                mc.options.framerateLimit().set(260);
                mc.options.hideGui = false;
                mc.options.renderDebug = false;
                wait = 60;
            }
            case 2 -> {
                LocalPlayer p = mc.player;
                if (p != null) {
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                }
                log("in " + (mc.level == null ? "?" : mc.level.dimension().location()) + ", shaders " + ShaderCompat.active()
                        + ", iris clock " + IrisClock.describe());
                SHOTS.clear();
                SHOTS.addAll(TrailerShots.all());
                SHOTS.removeIf(s -> !ONLY.isEmpty() && !ONLY.contains(s.name));
                log(SHOTS.size() + " shots, " + String.format(Locale.ROOT, "%.1f", SHOTS.stream().mapToDouble(s -> s.duration).sum()) + " s");
                shotIndex = -1;
                nextShot(mc);
            }
            default -> {
            }
        }
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
            shot.prepare();
            F.reset();
            shot.frame(0.0f, F);
            Vec3 c = F.cam;
            cmd(mc, String.format(Locale.ROOT, "execute in sounan:kaerazu run tp @s %.3f %.3f %.3f %.2f %.2f", c.x, c.y - 1.62, c.z, F.yaw, F.pitch));
            cmd(mc, "item replace entity @s weapon.mainhand with " + heldId(F.held));
            // the server keeps the shot's opening fear / danger, so the set-up ends on the values the film starts with
            cmd(mc, String.format(Locale.ROOT, "sounan debug fear %d %.2f 2000", Math.round(clamp01(F.fear) * 100.0f), clamp01(F.danger)));
            cmd(mc, F.held == TrailerShots.CAMERA ? "effect give @s minecraft:night_vision infinite 0 true" : "effect clear @s minecraft:night_vision");
            shot.setup(mc);
            LocalPlayer lp = mc.player;
            if (lp != null) {
                lp.getAbilities().flying = true;
                lp.onUpdateAbilities();
            }
            // pose the new actors at once, so nothing pops in on the first filmed frame
            F.reset();
            shot.frame(0.0f, F);
            for (Puppet pp : shot.puppets) pp.apply(PartialTick.current(mc), 0.0f);
            log("set-up " + label() + " (" + shot.duration + " s, " + shot.puppets.size() + " actors)");
        } catch (Exception e) {
            log("set-up failed " + label() + ": " + e);
            Sounan.LOGGER.error("[sounan] trailer set-up failed", e);
        }
        phase = Phase.SETUP;
        wait = shot.setupTicks;
    }

    static String heldId(int held) {
        return switch (held) {
            case TrailerShots.FLASHLIGHT_ON -> "sounan:flashlight_on";
            case TrailerShots.FLASHLIGHT_OFF -> "sounan:flashlight";
            case TrailerShots.CAMERA -> "sounan:phone_camera";
            case TrailerShots.FLARE -> "sounan:flare_lit";
            case TrailerShots.PHONE_LIGHT -> "sounan:phone_light";
            case TrailerShots.FLASHLIGHT_LOW -> "sounan:flashlight_low";
            default -> "air";
        };
    }

    static ItemStack heldStack(int held) {
        return switch (held) {
            case TrailerShots.FLASHLIGHT_ON -> new ItemStack(ModItems.FLASHLIGHT_ON.get());
            case TrailerShots.FLASHLIGHT_OFF -> new ItemStack(ModItems.FLASHLIGHT.get());
            case TrailerShots.CAMERA -> new ItemStack(ModItems.PHONE_CAMERA.get());
            case TrailerShots.FLARE -> new ItemStack(ModItems.FLARE_LIT.get());
            case TrailerShots.PHONE_LIGHT -> new ItemStack(ModItems.PHONE_LIGHT.get());
            case TrailerShots.FLASHLIGHT_LOW -> new ItemStack(ModItems.FLASHLIGHT_LOW.get());
            default -> ItemStack.EMPTY;
        };
    }

    private static String label() {
        return String.format(Locale.ROOT, "%02d_%s", shotIndex, shot == null ? "?" : shot.name);
    }

    // =========================================================================================== per frame
    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (!ENABLED || phase != Phase.CAPTURE) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            posed = false;
            if (!mc.isPaused() || mc.player == null || mc.level == null) return;
            float t;
            int frames = frameCount();
            if (PREVIEW) {
                int k = Math.max(0, frame - WARMUP) / KEY_REPEAT;
                k = Math.min(k, 2);
                t = frame < WARMUP ? 0.0f : keyTime(k);
                captureThisFrame = frame >= WARMUP && (frame - WARMUP) % KEY_REPEAT == KEY_REPEAT - 1;
            } else {
                t = frame < WARMUP ? 0.0f : (frame - WARMUP) / (float) FPS;
                captureThisFrame = frame >= WARMUP;
            }
            if (frame >= WARMUP + frames) return;
            pose(mc, t);
            posed = true;
        } else if (posed) {
            if (frame == WARMUP) logLight(mc, "first frame");
            if (captureThisFrame) save(mc);
            frame++;
            if (frame >= WARMUP + frameCount()) endShot(mc);
        }
    }

    private static int frameCount() {
        return PREVIEW ? 3 * KEY_REPEAT : Math.round(shot.duration * FPS);
    }

    private static float keyTime(int k) {
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
        double x = F.cam.x, y = F.cam.y - 1.62, z = F.cam.z;
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
        ClientState.fear = ClientState.fearShown = ClientState.fearPrev = fear * 100.0f;
        ClientState.danger = ClientState.dangerShown = ClientState.dangerPrev = danger;
        mc.gameRenderer.setRenderHand(F.hand);
        ItemStack want = heldStack(F.held);
        int slot = p.getInventory().selected;
        if (!ItemStack.isSameItem(p.getInventory().getItem(slot), want) || p.getInventory().getItem(slot).isEmpty() != want.isEmpty()) {
            p.getInventory().setItem(slot, want);
        }
        ItemInHandRenderer hands = hands(mc);
        if (hands != null) hands.tick();
        for (Puppet pp : shot.puppets) pp.apply(partial, ageTicks);
        if (shot.irisClock) IrisClock.set(shotIndex * 97.0f + t);
        mc.getToasts().clear();
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
                    Sounan.LOGGER.error("[sounan] trailer frame write failed", e);
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

    /** Diagnostics: what the shader pack is told about the light at the eye (its indoor / outdoor switch). */
    private static void logLight(Minecraft mc, String when) {
        if (mc.player == null || mc.level == null) return;
        net.minecraft.core.BlockPos eye = net.minecraft.core.BlockPos.containing(mc.player.getEyePosition());
        StringBuilder fx = new StringBuilder();
        for (var e : mc.player.getActiveEffects()) fx.append(e.getEffect().getDescriptionId()).append(' ');
        log(String.format(Locale.ROOT, "  %s: eye %s sky %d block %d raw sky %d, rain %.2f thunder %.2f, creative %s flying %s, effects [%s] %s",
                when, eye.toShortString(), mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, eye),
                mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, eye),
                mc.level.getChunkSource().getLightEngine().getLayerListener(net.minecraft.world.level.LightLayer.SKY).getLightValue(eye),
                mc.level.getRainLevel(1.0f), mc.level.getThunderLevel(1.0f), mc.player.isCreative(), mc.player.getAbilities().flying,
                fx.toString().trim(), IrisEye.describe()));
    }

    /** Iris / Oculus: the smoothed eye brightness the pack sees (reflection; best effort). */
    static final class IrisEye {
        static String describe() {
            try {
                Class<?> c = Class.forName("net.irisshaders.iris.uniforms.CommonUniforms");
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("getEyeBrightness") && m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        return "iris eyeBrightness " + m.invoke(null);
                    }
                }
                return "iris eyeBrightness: no method";
            } catch (Throwable t) {
                return "iris eyeBrightness: " + t.getClass().getSimpleName();
            }
        }
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

    // =========================================================================================== helpers
    static void cmd(Minecraft mc, String command) {
        if (mc.player != null) mc.player.connection.sendCommand(command);
    }

    /** Runs a job on the integrated server's thread, in the mountain level. */
    static void server(Consumer<ServerLevel> job) {
        MinecraftServer s = Minecraft.getInstance().getSingleplayerServer();
        if (s == null) return;
        s.execute(() -> {
            ServerLevel l = s.getLevel(ModDimensions.KAERAZU);
            if (l != null) {
                try {
                    job.accept(l);
                } catch (Exception e) {
                    Sounan.LOGGER.error("[sounan] trailer set dressing failed", e);
                }
            }
        });
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
        e.setPos(0.5, 65.0, 0.5);
        mc.level.putNonPlayerEntity(id, e);
        return new Puppet(e, id);
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
            String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "SounanTrailer", "");
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            LevelSettings settings = new LevelSettings("SounanTrailer", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules,
                    WorldDataConfiguration.DEFAULT);
            WorldOptions options = new WorldOptions(20261102L, false, false);
            mc.createWorldOpenFlows().createFreshLevel(folder, settings, options,
                    ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            log("world creation requested");
        } catch (Exception e) {
            log("could not create world: " + e);
            Sounan.LOGGER.error("[sounan] could not create world", e);
        }
    }

    static void log(String s) {
        LOG.add(s);
        Sounan.LOGGER.info("[sounan] trailer: {}", s);
        try {
            Files.writeString(Path.of("sounan_trailer.txt"), String.join("\n", LOG) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void finish(Minecraft mc) {
        phase = Phase.DONE;
        log("finished");
        mc.stop();
    }

    // =========================================================================================== pieces
    /** An empty pause screen: the world stands still and is drawn without any overlay. */
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
        }
    }

    /** A client-side actor posed every frame (no AI, no physics, invisible to the server). */
    public static final class Puppet {
        final Entity e;
        final int id;
        double x, y, z;
        float bodyYaw, headYaw, headPitch;
        float walkPos, walkSpeed;
        int ageOffset;

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

    /** The frozen partial tick of a paused game (found by probing Minecraft's float fields once). */
    static final class PartialTick {
        private static Field field;
        private static java.lang.reflect.Method getter;
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
                } catch (Exception e) {
                    log("partial tick probe failed: " + e);
                }
                log("pause partial tick: getter " + (getter != null) + ", field " + (field == null ? "not found" : field.getName()));
            }
            if (field != null) {
                try {
                    field.setFloat(mc, value);
                } catch (Exception ignored) {
                }
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
                // Iris adds the time since lastStartTime when the frame begins: make that one virtual frame, so
                // frameTimeCounter lands on the virtual clock and frameTime-smoothed values (eye brightness) move
                // at film speed
                long frameNs = 1_000_000_000L / FPS;
                counter.setFloat(timer, (seconds - 1.0f / FPS) % 3600.0f);
                if (lastStart != null) {
                    if (lastStart.getType() == OptionalLong.class) lastStart.set(timer, OptionalLong.of(System.nanoTime() - frameNs));
                    else if (lastStart.getType() == long.class) lastStart.setLong(timer, System.nanoTime() - frameNs);
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
}
