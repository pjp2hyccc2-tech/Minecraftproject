package com.eternalrealm.client;

import com.eternalrealm.client.block.ShaderCompat;
import com.eternalrealm.combat.forge.MagicProjectile;
import com.eternalrealm.combat.forge.SkillArrow;
import com.eternalrealm.config.ERClientConfig;
import com.eternalrealm.job.SkillTheme;
import com.eternalrealm.job.fx.Brush;
import com.eternalrealm.job.fx.Fields;
import com.eternalrealm.job.fx.Light;
import com.eternalrealm.job.fx.Painter;
import com.eternalrealm.job.fx.Pose;
import com.eternalrealm.job.fx.ShowState;
import com.eternalrealm.job.fx.SkillShow;
import com.eternalrealm.job.fx.SkillShows;
import com.eternalrealm.job.fx.Trails;
import com.eternalrealm.job.fx.V3;
import com.eternalrealm.network.forge.SkillFxPacket;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The light of the job skills and ultimates (owner, 2026-11-12: 「この四人のスキルのように全部のアクティブスキルと必殺技の
 * 視覚的効果、エフェクトを作り直して。高クオリティで派手に仕上げて」), drawn by every client for every player in sight with
 * additive light after the particles, the way the legendary travellers' moves are ({@link HeroFx}):
 * <ul>
 *     <li>a skill's show ({@link SkillShows}: its wind-up, every cut, thrust, circle, rain and blast on its hit
 *     frames, its blessings and marks, an ultimate's grand finale) starts when the player's action starts (the local
 *     player's predicted action, another player's {@code PlayerActionPacket}), runs on the action's own timeline
 *     (faster with attack speed, held during the local hit stop) and fades out after it;</li>
 *     <li>a skill's spells and arrows carry their element's light while they fly ({@link Trails});</li>
 *     <li>a ground area glows for its whole life and an exploding spell bursts where it lands ({@link Fields},
 *     {@link SkillFxPacket}).</li>
 * </ul>
 * Big moments shake the caster's view (and, more gently, the view of whoever stands close). Purely visual: the
 * server's particles still draw every skill, and they alone show with a shader pack (where this is skipped) or with
 * {@code skillEffects = false}. Client thread only.
 */
public final class SkillFx {
    private static final Logger LOG = LogUtils.getLogger();
    /** Players and areas farther than this are not drawn. */
    private static final double RANGE = 96.0;
    private static final int MAX_PLAYING = 64;
    /** Quads all skill light of one frame may use; past it, farther shows wait for a lighter frame. */
    private static final int FRAME_BUDGET = 48_000;
    /** How long a projectile's wake lingers after the projectile is gone (ticks). */
    private static final int WAKE_TICKS = 6;
    private static final List<Playing> PLAYING = new ArrayList<>();
    private static final Map<Integer, Long> STARTED = new HashMap<>();
    private static final Map<Integer, Wake> WAKES = new HashMap<>();
    private static boolean disabled;

    private SkillFx() {
    }

    /** Additive position-colour quads: light. */
    private static final class Types extends RenderType {
        static final RenderType LIGHT = RenderType.create("eternalrealm_skill_light", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 1 << 18, false, false, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                      boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    /** One show being drawn: a player's skill, or an area or a blast where it landed. */
    private static final class Playing {
        final SkillShow show;
        final ShowState state;
        /** The caster ({@code -1}: an area or a blast, standing where it landed). */
        final int entityId;
        final String actionId;
        /** The client combat tick the show began. */
        final long start;
        /** Show ticks per client tick (an action sped up by attack speed plays its light faster). */
        final double pace;
        /** Ticks the local player's hit stop has held the action back. */
        double held;
        final boolean[] shaken;

        Playing(SkillShow show, ShowState state, int entityId, String actionId, long start, double pace) {
            this.show = show;
            this.state = state;
            this.entityId = entityId;
            this.actionId = actionId;
            this.start = start;
            this.pace = pace;
            this.shaken = new boolean[show.shakes().size()];
        }
    }

    /** Where a skill's projectile has been (newest first). */
    private static final class Wake {
        final SkillTheme theme;
        final boolean arrow;
        final long seed;
        final ArrayDeque<V3> points = new ArrayDeque<>();
        V3 previous;
        V3 current;
        int goneFor = -1;

        Wake(SkillTheme theme, boolean arrow, long seed) {
            this.theme = theme;
            this.arrow = arrow;
            this.seed = seed;
        }
    }

    private static boolean enabled() {
        return !disabled && ERClientConfig.SKILL_EFFECTS.get() && !ShaderCompat.shaderPackInUse();
    }

    // ------------------------------------------------------------------------------------------------ input

    /** An area or a blast landed ({@link SkillFxPacket}). */
    public static void accept(SkillFxPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !enabled()) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        if (camera.distanceToSqr(packet.x(), packet.y(), packet.z()) > RANGE * RANGE) {
            return;
        }
        SkillShow show;
        V3 feet;
        if (packet.kind() == SkillFxPacket.ZONE) {
            show = Fields.zone(packet.themeValue(), packet.radius(), packet.delay(), packet.pulses(),
                    packet.interval(), packet.heals(), packet.harms(), packet.seed());
            feet = new V3(packet.x(), packet.y(), packet.z());
        } else {
            show = Fields.blast(packet.themeValue(), packet.radius(), packet.seed());
            feet = new V3(packet.x(), packet.y() - 1.0, packet.z()); // the burst's heart one block above "the feet"
        }
        Pose pose = Fields.at(feet, packet.yaw());
        add(new Playing(show, new ShowState(show, pose, feet), -1, null, ClientCombatState.tick(), 1.0));
    }

    private static void add(Playing playing) {
        if (PLAYING.size() >= MAX_PLAYING) {
            PLAYING.remove(0);
        }
        PLAYING.add(playing);
    }

    /** Every client tick: starts the shows of skills that began and follows the skills' projectiles. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            PLAYING.clear();
            STARTED.clear();
            WAKES.clear();
            return;
        }
        if (!enabled()) {
            return;
        }
        try {
            startShows(minecraft);
            followProjectiles(minecraft);
        } catch (RuntimeException e) {
            disabled = true;
            LOG.error("Skill light disabled after an error", e);
        }
    }

    private static void startShows(Minecraft minecraft) {
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (player.distanceToSqr(camera) > RANGE * RANGE) {
                continue;
            }
            Optional<CombatAnimations.Anim> anim = CombatAnimations.of(player);
            if (anim.isEmpty() || anim.get().actionId() == null) {
                continue;
            }
            Long last = STARTED.get(player.getId());
            if (last != null && last == anim.get().start()) {
                continue;
            }
            Optional<SkillShow> show = SkillShows.forAction(anim.get().actionId());
            if (show.isEmpty()) {
                continue;
            }
            STARTED.put(player.getId(), anim.get().start());
            double pace = Mth.clamp(show.get().duration() / (double) Math.max(1, anim.get().duration()), 0.5, 2.0);
            Pose pose = pose(player, 1.0F);
            ShowState state = new ShowState(show.get(), pose, aim(minecraft, player, pose));
            add(new Playing(show.get(), state, player.getId(), anim.get().actionId(), anim.get().start(), pace));
        }
        if (STARTED.size() > 256) {
            STARTED.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);
        }
    }

    /** The caster's pose: where it stands, faces and looks, and which hand holds the weapon. */
    private static Pose pose(LivingEntity entity, float partial) {
        Vec3 feet = entity.getPosition(partial);
        float yaw = Mth.lerp(partial, entity.yRotO, entity.getYRot());
        float pitch = entity.getViewXRot(partial);
        int side = entity.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        return Pose.of(new V3(feet.x, feet.y, feet.z), yaw, pitch, entity.getEyeHeight(), side);
    }

    /**
     * Where a skill aims: the creature the caster looks at (a locked skill has already turned the caster to its
     * target), else the ground where the look meets it (at most 24 blocks away).
     */
    private static V3 aim(Minecraft minecraft, LivingEntity caster, Pose pose) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getViewVector(1.0F).scale(24.0));
        HitResult block = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        Vec3 reach = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : caster.level().getEntities(caster, new AABB(eye, reach).inflate(1.0),
                e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator())) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(0.3).clip(eye, reach);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < bestDistance) {
                bestDistance = hit.get().distanceToSqr(eye);
                best = entity;
            }
        }
        Vec3 at = best != null ? best.position() : reach;
        if (best == null) {
            HitResult down = caster.level().clip(new ClipContext(at.add(0.0, 0.5, 0.0), at.add(0.0, -8.0, 0.0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (down.getType() != HitResult.Type.MISS) {
                at = down.getLocation();
            }
        }
        V3 v = new V3(at.x, at.y, at.z);
        return v.finite() ? v : pose.feet();
    }

    private static void followProjectiles(Minecraft minecraft) {
        for (Wake wake : WAKES.values()) {
            wake.goneFor = wake.goneFor < 0 ? 0 : wake.goneFor; // not seen yet this tick
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            int theme;
            boolean arrow;
            if (entity instanceof MagicProjectile spell) {
                theme = spell.skillTheme();
                arrow = false;
            } else if (entity instanceof SkillArrow shot) {
                theme = shot.skillTheme();
                arrow = true;
            } else {
                continue;
            }
            if (theme < 0 || theme >= SkillTheme.values().length || entity.isRemoved()) {
                continue;
            }
            Wake wake = WAKES.computeIfAbsent(entity.getId(), id -> new Wake(SkillTheme.values()[theme], arrow,
                    entity.getUUID().getLeastSignificantBits()));
            V3 now = new V3(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
            wake.previous = wake.current == null ? now : wake.current;
            wake.current = now;
            wake.points.addFirst(now);
            while (wake.points.size() > Trails.LENGTH) {
                wake.points.removeLast();
            }
            wake.goneFor = -1;
        }
        Iterator<Wake> it = WAKES.values().iterator();
        while (it.hasNext()) {
            Wake wake = it.next();
            if (wake.goneFor >= 0 && ++wake.goneFor > WAKE_TICKS) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ drawing

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !enabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || (PLAYING.isEmpty() && WAKES.isEmpty())) {
            return;
        }
        try {
            draw(event, minecraft);
        } catch (RuntimeException e) {
            disabled = true;
            LOG.error("Skill light disabled after a rendering error", e);
        }
    }

    private static void draw(RenderLevelStageEvent event, Minecraft minecraft) {
        Vec3 cam = event.getCamera().getPosition();
        V3 camera = new V3(cam.x, cam.y, cam.z);
        float partial = event.getPartialTick();
        long tick = ClientCombatState.tick();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Sink sink = new Sink(buffers.getBuffer(Types.LIGHT), event.getPoseStack().last().pose(), cam);
        int used = 0;
        // wakes first (cheap), then the shows: the player's own, then the nearest
        for (Map.Entry<Integer, Wake> entry : WAKES.entrySet()) {
            Wake wake = entry.getValue();
            if (wake.points.isEmpty()) {
                continue;
            }
            V3[] path = wake.points.toArray(new V3[0]);
            double alpha = 1.0;
            if (wake.goneFor < 0) {
                path[0] = wake.previous.lerp(wake.current, partial);
            } else {
                alpha = 1.0 - (wake.goneFor + partial) / (WAKE_TICKS + 1.0);
            }
            if (path[0].distance(camera) > RANGE) {
                continue;
            }
            Brush brush = new Brush(sink, camera);
            Trails.draw(brush, wake.theme, wake.arrow, path, tick + partial, wake.seed, alpha);
            used += brush.quads();
        }
        PLAYING.sort((a, b) -> Double.compare(priority(minecraft, a, camera), priority(minecraft, b, camera)));
        Iterator<Playing> it = PLAYING.iterator();
        while (it.hasNext()) {
            Playing p = it.next();
            double t = showTime(minecraft, p, tick, partial);
            if (t > p.show.length() || t < -2.0) {
                it.remove();
                continue;
            }
            LivingEntity caster = p.entityId >= 0 && minecraft.level.getEntity(p.entityId) instanceof LivingEntity living
                    ? living : null;
            if (p.entityId >= 0 && caster != null && caster.isAlive()) {
                p.state.update(t, pose(caster, partial));
            }
            shake(minecraft, p, t, camera);
            if (used >= FRAME_BUDGET || (caster != null && caster.isInvisible() && caster != minecraft.player)) {
                continue;
            }
            Brush brush = new Brush(sink, camera);
            boolean ownEyes = caster == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
            used += Painter.paint(p.state, Math.max(0.0, t), brush, ownEyes);
        }
        buffers.endBatch(Types.LIGHT);
    }

    /** Lower first: the player's own show, then by distance. */
    private static double priority(Minecraft minecraft, Playing p, V3 camera) {
        if (minecraft.player != null && p.entityId == minecraft.player.getId()) {
            return -1.0;
        }
        return p.state.origin().feet().distance(camera);
    }

    /** The show's own time now: the action's progress while it lasts (held by a hit stop), then on at its pace. */
    private static double showTime(Minecraft minecraft, Playing p, long tick, float partial) {
        double raw = tick - p.start + partial;
        if (p.entityId >= 0 && minecraft.level.getEntity(p.entityId) instanceof LivingEntity living) {
            Optional<CombatAnimations.Anim> anim = CombatAnimations.of(living);
            if (anim.isPresent() && anim.get().start() == p.start && Objects.equals(anim.get().actionId(), p.actionId)) {
                p.held = Math.max(0.0, raw - anim.get().elapsed(tick, partial));
            }
        }
        return (raw - p.held) * p.pace;
    }

    /** The show's shakes as their moments come: the caster's own view, and gently the views close by. */
    private static void shake(Minecraft minecraft, Playing p, double t, V3 camera) {
        List<SkillShow.Shake> shakes = p.show.shakes();
        for (int i = 0; i < shakes.size(); i++) {
            SkillShow.Shake s = shakes.get(i);
            if (p.shaken[i] || t < s.time()) {
                continue;
            }
            p.shaken[i] = true;
            if (t > s.time() + 4.0) {
                continue; // long past (the show was only just seen)
            }
            double k;
            if (minecraft.player != null && p.entityId == minecraft.player.getId()) {
                k = 1.0;
            } else {
                double distance = p.state.origin().feet().distance(camera);
                k = (p.show.ultimate() || p.entityId < 0 ? 0.6 : 0.3) * Mth.clamp(1.0 - distance / 24.0, 0.0, 1.0);
            }
            if (k > 0.05) {
                CameraShake.add((float) (s.intensity() * k), s.ticks());
                if (s.kick() > 0.0F) {
                    CameraShake.kick((float) (s.kick() * k), Math.max(4, s.ticks() - 2));
                }
            }
        }
    }

    /** Forgets everything (leaving a world). */
    public static void clear() {
        PLAYING.clear();
        STARTED.clear();
        WAKES.clear();
    }

    /** Writes the engine's light into the buffer, relative to the camera. */
    private static final class Sink implements Light {
        private final VertexConsumer out;
        private final Matrix4f matrix;
        private final double cx;
        private final double cy;
        private final double cz;

        Sink(VertexConsumer out, Matrix4f matrix, Vec3 camera) {
            this.out = out;
            this.matrix = matrix;
            this.cx = camera.x;
            this.cy = camera.y;
            this.cz = camera.z;
        }

        @Override
        public void vertex(double x, double y, double z, int argb) {
            out.vertex(matrix, (float) (x - cx), (float) (y - cy), (float) (z - cz))
                    .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, argb >>> 24).endVertex();
        }
    }
}
