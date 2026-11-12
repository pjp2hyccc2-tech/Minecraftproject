package com.eternalrealm.client;

import com.eternalrealm.client.block.ShaderCompat;
import com.eternalrealm.network.forge.HeroFxPacket;
import com.eternalrealm.npc.HeroBodies;
import com.eternalrealm.npc.Travellers;
import com.eternalrealm.npc.forge.TravellerEntity;
import com.eternalrealm.npc.forge.TravellerMoves;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The light of the legendary travellers' moves (docs/modules/travellers.md §2; owner, 2026-11-12: 「スキルや必殺とか
 * も演出を入れて4人各々特別な物をつくって格好よく、綺麗につくって」), drawn by every client that sees them with additive
 * light quads after the particles, in each one's colours:
 * <ul>
 *     <li>Rosalind: a star from the staff, a spray of stardust, a turning circle in the sky raining stars, the
 *     constellation that binds, and the canopy of a thousand nights: a sky-wide circle of rings, stars and runes from
 *     which the stars fall in four showers;</li>
 *     <li>Siegwald: steel crescents, the oath's shockwave and the shield sigil that holds while he guards, the
 *     whirlwind's rings, and the dragon's stroke: fire spiralling up the raised blade, the head of the dragon he
 *     turned roaring in flame above him, one vertical cut and a wall of fire running down the line;</li>
 *     <li>Kaylen: cyan cuts, the after-image of the flash step and its cross, six phantom blades falling, and the
 *     infinite blades: cuts appear everywhere in the air and hang there while he sheathes the sword, then all burst
 *     at once;</li>
 *     <li>Mariel: bolts and lances of light, the sanctuary's golden circle, pillars under the enemies, and the prayer
 *     of the kingdom reborn: a rose window behind her, wings of light, a radiant wave and falling feathers.</li>
 * </ul>
 * Big moves shake the view of the players close by. Purely visual; the server's particles draw the same moves for a
 * client with a shader pack (where this is skipped). Client thread only.
 */
public final class HeroFx {
    private static final Logger LOG = LogUtils.getLogger();
    private static final double RANGE = 96.0;
    private static final int MAX = 64;
    private static final List<Performance> ACTIVE = new ArrayList<>();
    private static boolean disabled;

    private HeroFx() {
    }

    /** Additive position-colour quads: light. */
    private static final class Types extends RenderType {
        static final RenderType LIGHT = RenderType.create("eternalrealm_hero_light", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 1 << 17, false, true, CompositeState.builder()
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

    /** One move being drawn. */
    private static final class Performance {
        final int entityId;
        final int action;
        final Vec3 origin;
        final Vec3 aim;
        final Vec3 forward;
        final long start;
        final int seed;
        final int length;
        final List<Vec3> trail = new ArrayList<>();
        boolean shaken;
        boolean shakenLast;

        Performance(int entityId, int action, Vec3 origin, Vec3 aim, Vec3 forward, long start, int seed, int length) {
            this.entityId = entityId;
            this.action = action;
            this.origin = origin;
            this.aim = aim;
            this.forward = forward;
            this.start = start;
            this.seed = seed;
            this.length = length;
        }
    }

    // ------------------------------------------------------------------------------------------------ input

    /** A traveller starts a move ({@link HeroFxPacket}): its body plays it, the light is drawn. */
    public static void accept(HeroFxPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(packet.entityId());
        if (!(entity instanceof TravellerEntity hero)) {
            return;
        }
        int length;
        if (packet.action() == TravellerMoves.JOIN) {
            hero.startAction(HeroBodies.JOIN);
            length = 30;
        } else if (packet.action() == TravellerMoves.STAND_UP) {
            length = 16;
        } else if (packet.action() < Travellers.Move.values().length) {
            Travellers.Move move = Travellers.Move.values()[packet.action()];
            hero.startAction(move.slot());
            length = move.lengthTicks() + 24 + (move == Travellers.Move.MAR_SANCTUARY ? 120 : 0);
        } else {
            return;
        }
        Vec3 aim = new Vec3(packet.x(), packet.y(), packet.z());
        Vec3 forward = flat(aim.subtract(hero.position()));
        if (forward.lengthSqr() < 1.0E-4) {
            forward = Vec3.directionFromRotation(0.0F, hero.getYRot());
            forward = flat(forward);
        }
        if (ACTIVE.size() >= MAX) {
            ACTIVE.remove(0);
        }
        ACTIVE.add(new Performance(hero.getId(), packet.action(), hero.position(), aim, forward.normalize(),
                minecraft.level.getGameTime(), packet.seed(), length));
    }

    public static void clear() {
        ACTIVE.clear();
    }

    // ------------------------------------------------------------------------------------------------ drawing

    public static void render(RenderLevelStageEvent event) {
        if (disabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE.clear();
            return;
        }
        try {
            draw(event, minecraft);
        } catch (RuntimeException e) {
            disabled = true;
            LOG.error("The travellers' light is turned off after a rendering error", e);
        }
    }

    private static void draw(RenderLevelStageEvent event, Minecraft minecraft) {
        long now = minecraft.level.getGameTime();
        float partial = event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        boolean shaders = ShaderCompat.shaderPackInUse();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Light light = null;
        Iterator<Performance> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Performance p = it.next();
            float t = now - p.start + partial;
            if (t > p.length) {
                it.remove();
                continue;
            }
            Entity entity = minecraft.level.getEntity(p.entityId);
            TravellerEntity hero = entity instanceof TravellerEntity h ? h : null;
            shake(minecraft, p, hero, t);
            if (shaders || p.origin.distanceToSqr(camera) > RANGE * RANGE) {
                continue;
            }
            if (light == null) {
                light = new Light(buffers.getBuffer(Types.LIGHT), event.getPoseStack().last().pose(), camera);
            }
            perform(light, p, hero, t, partial);
        }
        if (!shaders) {
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity instanceof TravellerEntity hero && hero.isGuarding()
                        && hero.distanceToSqr(camera) < RANGE * RANGE) {
                    if (light == null) {
                        light = new Light(buffers.getBuffer(Types.LIGHT), event.getPoseStack().last().pose(), camera);
                    }
                    oathAura(light, hero, now + partial);
                }
            }
        }
        if (light != null) {
            buffers.endBatch(Types.LIGHT);
        }
    }

    /** The big moves shake the view of the players near them as their blows land. */
    private static void shake(Minecraft minecraft, Performance p, TravellerEntity hero, float t) {
        if (minecraft.player == null || p.action >= Travellers.Move.values().length) {
            return;
        }
        Travellers.Move move = Travellers.Move.values()[p.action];
        if (move.kind() != Travellers.Kind.ULTIMATE && move != Travellers.Move.SIEG_OATH) {
            return;
        }
        double distance = minecraft.player.position().distanceTo(p.origin);
        if (distance > 40.0) {
            return;
        }
        float near = (float) Math.max(0.25, 1.0 - distance / 40.0);
        int[] hits = move.hitTicks();
        if (!p.shaken && hits.length > 0 && t >= hits[0]) {
            p.shaken = true;
            CameraShake.add((move == Travellers.Move.SIEG_OATH ? 0.6F : 1.2F) * near, 10);
            if (move == Travellers.Move.SIEG_DRAGON_STROKE) {
                CameraShake.kick(2.0F * near, 8);
            }
        }
        if (!p.shakenLast && hits.length > 1 && t >= hits[hits.length - 1]) {
            p.shakenLast = true;
            CameraShake.add(2.0F * near, 16);
            CameraShake.kick(1.2F * near, 8);
        }
    }

    private static void perform(Light g, Performance p, TravellerEntity hero, float t, float partial) {
        if (p.action == TravellerMoves.JOIN) {
            join(g, p, hero, t);
            return;
        }
        if (p.action == TravellerMoves.STAND_UP) {
            standUp(g, p, t);
            return;
        }
        Travellers.Move move = Travellers.Move.values()[p.action];
        Vec3 body = hero != null ? hero.getPosition(partial) : p.origin;
        Vec3 hand = body.add(0.0, 1.35, 0.0).add(p.forward.scale(0.5));
        int[] hits = move.hitTicks();
        switch (move) {
            case ROS_STAR_SHOT -> bolt(g, p, hand.add(0.0, 1.1, 0.0), t, hits[0], ROS, 1);
            case ROS_STARDUST -> bolt(g, p, hand.add(0.0, 1.0, 0.0), t, hits[0], ROS, 3);
            case ROS_STARFALL -> starfall(g, p, t, hits);
            case ROS_STAR_BIND -> starBind(g, p, hand, t, hits[0]);
            case ROS_THOUSAND_NIGHTS -> thousandNights(g, p, body, t, hits);
            case SIEG_CLEAVE -> crescent(g, body, p.forward, t, hits[0], SIEG, 2.9, 0.0, -75.0, 75.0, false);
            case SIEG_RISING -> {
                crescent(g, body, p.forward, t, hits[0], SIEG, 3.0, 50.0, -60.0, 70.0, false);
                crescent(g, body, p.forward, t, hits[1], SIEG, 3.2, 90.0, 80.0, -40.0, true);
            }
            case SIEG_OATH -> oath(g, p, body, t, hits[0]);
            case SIEG_WHIRLWIND -> {
                whirl(g, body, p.forward, t, hits[0], SIEG, 3.6, 0.9);
                whirl(g, body, p.forward, t, hits[1], SIEG, 4.0, 1.2);
            }
            case SIEG_DRAGON_STROKE -> dragonStroke(g, p, body, t, hits[0]);
            case KAY_SLASH -> crescent(g, body, p.forward, t, hits[0], KAY, 2.4, 0.0, 70.0, -70.0, false);
            case KAY_CROSS -> {
                crescent(g, body, p.forward, t, hits[0], KAY, 2.5, 45.0, 70.0, -70.0, false);
                thrust(g, body, p.forward, t, hits[1], KAY);
            }
            case KAY_FLASH_STEP -> flashStep(g, p, hero, body, t, hits);
            case KAY_PHANTOM_ARRAY -> phantomArray(g, p, t, hits);
            case KAY_INFINITE_BLADES -> infiniteBlades(g, p, body, t, hits);
            case MAR_HOLY_BOLT -> bolt(g, p, hand.add(0.0, 1.2, 0.0), t, hits[0], MAR, 1);
            case MAR_LIGHT_LANCE -> lance(g, p, t, hits[0]);
            case MAR_SANCTUARY -> sanctuary(g, p, t, hits[0]);
            case MAR_PURIFICATION -> purification(g, p, t, hits[0]);
            case MAR_KINGDOM_REBORN -> kingdomReborn(g, p, body, t, hits[0]);
        }
    }

    // ------------------------------------------------------------------------------------------------ colours

    /** core (white-hot), glow, accent, deep */
    private record Colours(int core, int glow, int accent, int deep) {
    }

    private static final Colours ROS = new Colours(0xFFF6DC, 0xFFCF6A, 0xB78CFF, 0x5A46FF);
    private static final Colours SIEG = new Colours(0xF0F6FF, 0xFFA040, 0xFF5A1E, 0x8CC8FF);
    private static final Colours KAY = new Colours(0xEAFFFF, 0x5AF0FF, 0xB07CFF, 0x2A9CFF);
    private static final Colours MAR = new Colours(0xFFFFF2, 0xFFE08A, 0xA8E0FF, 0xFFC860);

    private static float fade(float t, float from, float to) {
        if (t < from || t > to) {
            return 0.0F;
        }
        float k = (t - from) / Math.max(0.001F, to - from);
        return 1.0F - k * k;
    }

    private static float rise(float t, float from, float to) {
        return Mth.clamp((t - from) / Math.max(0.001F, to - from), 0.0F, 1.0F);
    }

    // ============================================================================================ Rosalind

    /** A star (or a spray of stars) from the staff to the enemy, a burst where it lands. */
    private static void bolt(Light g, Performance p, Vec3 from, float t, int hit, Colours c, int count) {
        float windup = rise(t, 0.0F, hit);
        if (t < hit) {
            g.sparkle(from, 0.25 + 0.35 * windup, c.glow, 0.5F + 0.5F * windup, t * 0.2);
            g.billboard(from, 0.4 * windup, c.core, 0.6F * windup);
            return;
        }
        float travel = rise(t, hit, hit + 4.0F);
        Random random = new Random(p.seed);
        for (int i = 0; i < count; i++) {
            Vec3 to = p.aim.add(0.0, 1.0, 0.0);
            if (count > 1) {
                to = to.add((random.nextDouble() - 0.5) * 3.0, random.nextDouble() * 0.8, (random.nextDouble() - 0.5) * 3.0);
            }
            Vec3 bend = new Vec3(0.0, 1.2 + i * 0.6, 0.0);
            Vec3 head = curve(from, to, bend, travel);
            Vec3 tail = curve(from, to, bend, Math.max(0.0F, travel - 0.35F));
            if (travel < 1.0F) {
                g.ribbon(tail, head, 0.05, 0.3, c.glow, 0.0F, 0.9F);
                g.ribbon(tail, head, 0.02, 0.12, c.core, 0.0F, 1.0F);
                g.sparkle(head, 0.55, c.core, 1.0F, t * 0.3 + i);
            } else {
                float a = fade(t, hit + 4.0F, hit + 14.0F);
                g.sparkle(to, 1.2 * (1.0F - a * 0.5F), c.glow, a, t * 0.1);
                g.billboard(to, 1.4 * (1.2F - a), c.accent, a * 0.6F);
                Vec3[] basis = Light.basis(new Vec3(0.0, 1.0, 0.0));
                g.ring(to.add(0.0, -0.9, 0.0), basis[0], basis[1], 0.6 + 1.6 * (1.0F - a), 0.12, c.glow, a);
            }
        }
    }

    private static Vec3 curve(Vec3 from, Vec3 to, Vec3 bend, double k) {
        Vec3 straight = from.add(to.subtract(from).scale(k));
        return straight.add(bend.scale(4.0 * k * (1.0 - k)));
    }

    /** A circle turns in the sky over the enemy; stars fall from it in four waves. */
    private static void starfall(Light g, Performance p, float t, int[] hits) {
        Vec3 sky = p.aim.add(0.0, 9.0, 0.0);
        float show = rise(t, 2.0F, 12.0F) * (1.0F - rise(t, hits[hits.length - 1] + 6.0F, hits[hits.length - 1] + 14.0F));
        Vec3 x = new Vec3(1.0, 0.0, 0.0);
        Vec3 z = new Vec3(0.0, 0.0, 1.0);
        if (show > 0.01F) {
            double r = 3.4 * (0.4 + 0.6 * show);
            g.ring(sky, x, z, r, 0.16, ROS.glow, show);
            g.ring(sky, x, z, r * 0.72, 0.1, ROS.accent, show * 0.9F);
            g.star(sky, x, z, r * 0.72, 5, t * 0.05, 0.08, ROS.core, show);
            g.star(sky, x, z, r, 8, -t * 0.03, 0.05, ROS.accent, show * 0.6F);
            runes(g, sky, x, z, r * 1.12, 16, t * 0.02, ROS.glow, show * 0.8F, p.seed);
        }
        for (int w = 0; w < hits.length; w++) {
            meteors(g, p, sky, t, hits[w], 4, 4.5, ROS, p.seed + w * 17, 0.8);
        }
    }

    /** {@code count} shooting stars from the sky to the ground around the aim, landing at {@code hit}. */
    private static void meteors(Light g, Performance p, Vec3 sky, float t, int hit, int count, double radius,
                                Colours c, int seed, double size) {
        if (t < hit - 4.0F || t > hit + 12.0F) {
            return;
        }
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(random.nextDouble()) * radius;
            Vec3 ground = p.aim.add(Math.cos(a) * d, 0.1, Math.sin(a) * d);
            Vec3 top = sky.add((random.nextDouble() - 0.5) * 5.0, 2.0, (random.nextDouble() - 0.5) * 5.0);
            float fall = rise(t, hit - 4.0F + i * 0.4F, hit + i * 0.4F);
            if (fall > 0.0F && fall < 1.0F) {
                Vec3 head = top.add(ground.subtract(top).scale(fall * fall));
                Vec3 tail = top.add(ground.subtract(top).scale(Math.max(0.0, fall * fall - 0.3)));
                g.ribbon(tail, head, 0.04 * size, 0.45 * size, c.glow, 0.0F, 1.0F);
                g.ribbon(tail, head, 0.02 * size, 0.18 * size, c.core, 0.0F, 1.0F);
                g.sparkle(head, 0.7 * size, c.core, 1.0F, t * 0.4);
            }
            float a2 = fade(t, hit + i * 0.4F, hit + i * 0.4F + 10.0F);
            if (a2 > 0.0F) {
                Vec3[] basis = Light.basis(new Vec3(0.0, 1.0, 0.0));
                double grow = 1.0 - a2;
                g.ring(ground, basis[0], basis[1], (0.3 + 1.7 * grow) * size, 0.14 * size, c.glow, a2);
                g.billboard(ground.add(0.0, 0.5, 0.0), 1.6 * size * a2, c.core, a2 * 0.8F);
                g.sparkle(ground.add(0.0, 0.8, 0.0), 1.2 * size, c.accent, a2, t * 0.2);
            }
        }
    }

    /** The staff draws a circle; then stars light over the enemies and lines of light bind them. */
    private static void starBind(Light g, Performance p, Vec3 hand, float t, int hit) {
        if (t < hit) {
            Vec3[] basis = Light.basis(p.forward);
            Vec3 center = hand.add(p.forward.scale(1.2)).add(0.0, 0.6, 0.0);
            double sweep = rise(t, 2.0F, hit);
            g.band(center, basis[0], basis[1], 0.9, 0.12, 90.0, 90.0 + 360.0 * sweep, ROS.glow, ROS.core, 1.0F, 1.0F);
            g.star(center, basis[0], basis[1], 0.62, 5, t * 0.1, 0.05, ROS.accent, (float) sweep);
            return;
        }
        float a = fade(t, hit, hit + 22.0F);
        Random random = new Random(p.seed);
        Vec3[] stars = new Vec3[7];
        for (int i = 0; i < stars.length; i++) {
            double ang = i * Math.PI * 2.0 / stars.length + random.nextDouble() * 0.5;
            double d = 1.2 + random.nextDouble() * 3.8;
            stars[i] = p.aim.add(Math.cos(ang) * d, 0.6 + random.nextDouble() * 1.4, Math.sin(ang) * d);
        }
        float draw = rise(t, hit, hit + 5.0F);
        for (int i = 0; i < stars.length; i++) {
            Vec3 s = stars[i];
            Vec3 next = stars[(i + (i % 2 == 0 ? 2 : 3)) % stars.length];
            Vec3 end = s.add(next.subtract(s).scale(draw));
            g.ribbon(s, end, 0.08, 0.08, ROS.accent, a, a);
            g.ribbon(s, end, 0.03, 0.03, ROS.core, a, a);
            g.sparkle(s, 0.7, ROS.core, a, t * 0.2 + i);
        }
        Vec3[] flat = Light.basis(new Vec3(0.0, 1.0, 0.0));
        g.ring(p.aim.add(0.0, 0.1, 0.0), flat[0], flat[1], 5.2, 0.12, ROS.deep, a * 0.8F);
        g.star(p.aim.add(0.0, 0.1, 0.0), flat[0], flat[1], 5.2, 7, t * 0.02, 0.06, ROS.accent, a * 0.7F);
    }

    /** The canopy of a thousand nights. */
    private static void thousandNights(Light g, Performance p, Vec3 body, float t, int[] hits) {
        Vec3 x = new Vec3(1.0, 0.0, 0.0);
        Vec3 z = new Vec3(0.0, 0.0, 1.0);
        // light under her as she rises
        float column = fade(t, 0.0F, 24.0F);
        if (column > 0.0F) {
            g.ribbon(body, body.add(0.0, 9.0, 0.0), 1.6 * column, 0.2, ROS.accent, column * 0.7F, 0.0F);
            g.ribbon(body, body.add(0.0, 7.0, 0.0), 0.5 * column, 0.05, ROS.core, column, 0.0F);
            g.ring(body.add(0.0, 0.05, 0.0), x, z, 1.4 + (1.0F - column), 0.1, ROS.glow, column);
        }
        Vec3 sky = p.aim.add(0.0, 14.0, 0.0);
        float last = hits[hits.length - 1];
        float show = rise(t, 14.0F, 34.0F) * (1.0F - rise(t, last + 4.0F, last + 16.0F));
        if (show > 0.01F) {
            for (int i = 0; i < 3; i++) {
                double r = (6.0 + i * 2.6) * (0.3 + 0.7 * show);
                g.ring(sky, x, z, r, 0.22 - i * 0.04, i == 1 ? ROS.accent : ROS.glow, show);
            }
            g.star(sky, x, z, 6.0 * show, 8, t * 0.03, 0.12, ROS.core, show);
            g.star(sky, x, z, 8.6 * show, 5, -t * 0.02, 0.1, ROS.accent, show * 0.8F);
            g.star(sky, x, z, 11.2 * show, 12, t * 0.01, 0.07, ROS.glow, show * 0.6F);
            runes(g, sky, x, z, 12.2 * show, 32, -t * 0.015, ROS.glow, show * 0.9F, p.seed);
            Random random = new Random(p.seed * 31L);
            for (int i = 0; i < 48; i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double d = Math.sqrt(random.nextDouble()) * 10.5;
                float appear = rise(t, 16.0F + i * 0.5F, 20.0F + i * 0.5F) * show;
                float twinkle = 0.6F + 0.4F * Mth.sin(t * 0.4F + i);
                Vec3 star = sky.add(Math.cos(a) * d, -0.3 + random.nextDouble() * 0.6, Math.sin(a) * d);
                g.sparkle(star, 0.5 + random.nextDouble() * 0.6, i % 3 == 0 ? ROS.accent : ROS.core,
                        appear * twinkle, t * 0.05 + i);
            }
            g.billboard(sky, 4.0 * show, ROS.deep, show * 0.35F);
        }
        for (int w = 0; w < hits.length; w++) {
            meteors(g, p, sky, t, hits[w], 12, 10.0, ROS, p.seed + w * 101, 1.3);
        }
        float wave = fade(t, last, last + 16.0F);
        if (wave > 0.0F) {
            Vec3 ground = p.aim.add(0.0, 0.1, 0.0);
            double grow = 1.0 - wave;
            g.ring(ground, x, z, 1.0 + 11.0 * grow, 0.5 * wave + 0.1, ROS.glow, wave);
            g.ring(ground, x, z, 0.5 + 8.0 * grow, 0.2, ROS.core, wave);
            g.ribbon(ground, ground.add(0.0, 16.0, 0.0), 3.0 * wave, 0.4, ROS.core, wave, 0.0F);
            g.billboard(ground.add(0.0, 1.5, 0.0), 8.0 * wave, ROS.accent, wave * 0.6F);
        }
    }

    /** Small strokes around a circle (rune marks): deterministic per move. */
    private static void runes(Light g, Vec3 center, Vec3 a, Vec3 b, double radius, int count, double spin, int rgb,
                              float alpha, int seed) {
        Random random = new Random(seed * 7L + count);
        for (int i = 0; i < count; i++) {
            double ang = spin + i * Math.PI * 2.0 / count;
            Vec3 at = center.add(a.scale(Math.cos(ang) * radius)).add(b.scale(Math.sin(ang) * radius));
            Vec3 tangent = a.scale(-Math.sin(ang)).add(b.scale(Math.cos(ang)));
            Vec3 radial = a.scale(Math.cos(ang)).add(b.scale(Math.sin(ang)));
            Vec3 normal = a.cross(b).normalize();
            int kind = random.nextInt(4);
            double s = 0.22;
            switch (kind) {
                case 0 -> g.flat(at.subtract(radial.scale(s)), at.add(radial.scale(s)), normal, 0.05, rgb, alpha, alpha);
                case 1 -> {
                    g.flat(at.subtract(tangent.scale(s)), at.add(tangent.scale(s)), normal, 0.05, rgb, alpha, alpha);
                    g.flat(at, at.add(radial.scale(s)), normal, 0.05, rgb, alpha, alpha);
                }
                case 2 -> {
                    g.flat(at.subtract(radial.scale(s)).subtract(tangent.scale(s)), at.add(radial.scale(s)), normal,
                            0.05, rgb, alpha, alpha);
                    g.flat(at.subtract(radial.scale(s)).add(tangent.scale(s)), at.add(radial.scale(s)), normal, 0.05,
                            rgb, alpha, alpha);
                }
                default -> g.flat(at.subtract(tangent.scale(s)), at.add(tangent.scale(s)), normal, 0.07, rgb, alpha,
                        alpha);
            }
        }
    }

    // ============================================================================================ Siegwald

    /**
     * A crescent of steel light at blow {@code hit}: in the plane through {@code forward}, tilted by {@code tilt}
     * degrees (0 horizontal, 90 vertical), swept from {@code from} to {@code to} degrees around the forward line.
     */
    private static void crescent(Light g, Vec3 body, Vec3 forward, float t, int hit, Colours c, double radius,
                                 double tilt, double from, double to, boolean slam) {
        float sweep = rise(t, hit - 3.0F, hit + 1.0F);
        float alpha = t < hit + 1.0F ? 1.0F : fade(t, hit + 1.0F, hit + 8.0F);
        if (sweep <= 0.0F || alpha <= 0.0F) {
            return;
        }
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        double tr = Math.toRadians(tilt);
        Vec3 b = side.scale(Math.cos(tr)).add(0.0, Math.sin(tr), 0.0);
        Vec3 center = body.add(0.0, 1.1, 0.0);
        g.band(center, forward, b, radius, 0.55, from, to, c.glow, c.core, sweep, alpha);
        g.band(center, forward, b, radius + 0.25, 0.18, from, to, c.accent, c.core, sweep, alpha * 0.7F);
        if (slam && t >= hit) {
            float crack = fade(t, hit, hit + 14.0F);
            Vec3 ground = body.add(forward.scale(2.2)).add(0.0, 0.05, 0.0);
            Vec3 up = new Vec3(0.0, 1.0, 0.0);
            Random random = new Random(hit * 13L);
            for (int i = 0; i < 6; i++) {
                double a = (random.nextDouble() - 0.5) * 2.4;
                Vec3 dir = Light.turn(forward, a);
                g.flat(ground, ground.add(dir.scale(1.0 + random.nextDouble() * 2.2)), up, 0.12, c.glow, crack, 0.0F);
            }
            g.billboard(ground.add(0.0, 0.3, 0.0), 2.2 * crack, c.core, crack);
        }
    }

    /** Full turns of light around him (the whirlwind). */
    private static void whirl(Light g, Vec3 body, Vec3 forward, float t, int hit, Colours c, double radius,
                              double height) {
        float sweep = rise(t, hit - 4.0F, hit + 1.0F);
        float alpha = t < hit + 1.0F ? 1.0F : fade(t, hit + 1.0F, hit + 9.0F);
        if (sweep <= 0.0F || alpha <= 0.0F) {
            return;
        }
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 center = body.add(0.0, height, 0.0);
        g.band(center, forward, side, radius, 0.7, -10.0, -370.0, c.glow, c.core, sweep, alpha);
        g.band(center.add(0.0, 0.3, 0.0), forward, side, radius * 0.8, 0.25, 0.0, -360.0, c.deep, c.core, sweep,
                alpha * 0.7F);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3.0 + t * 0.3;
            Vec3 base = body.add(Math.cos(a) * radius * 0.9, 0.2, Math.sin(a) * radius * 0.9);
            g.ribbon(base, base.add(0.0, 1.6, 0.0), 0.2, 0.02, c.deep, alpha * 0.6F, 0.0F);
        }
    }

    /** The oath: the shockwave as the sword goes into the ground. */
    private static void oath(Light g, Performance p, Vec3 body, float t, int hit) {
        Vec3 x = new Vec3(1.0, 0.0, 0.0);
        Vec3 z = new Vec3(0.0, 0.0, 1.0);
        if (t < hit) {
            float charge = rise(t, 0.0F, hit);
            g.ribbon(body.add(0.0, 0.2, 0.0), body.add(0.0, 3.5, 0.0), 0.6 * charge, 0.1, SIEG.deep, charge * 0.6F,
                    0.0F);
            return;
        }
        float wave = fade(t, hit, hit + 14.0F);
        double grow = 1.0 - wave;
        Vec3 ground = body.add(0.0, 0.08, 0.0);
        g.ring(ground, x, z, 1.0 + 9.0 * grow, 0.6 * wave + 0.1, SIEG.deep, wave);
        g.ring(ground, x, z, 0.6 + 7.0 * grow, 0.2, SIEG.core, wave);
        for (int i = 0; i < 4; i++) {
            g.ring(ground.add(0.0, 0.5 + i * 0.6, 0.0), x, z, (2.2 - i * 0.4) * (0.6 + 0.4 * grow), 0.08, SIEG.deep,
                    wave * 0.8F);
        }
        g.billboard(body.add(0.0, 1.0, 0.0).add(p.forward.scale(0.8)), 3.0 * wave, SIEG.core, wave);
        g.ribbon(ground, ground.add(0.0, 6.0, 0.0), 1.2 * wave, 0.1, SIEG.deep, wave, 0.0F);
    }

    /** While he guards: a shield sigil turns under him and rings of light stand around him. */
    private static void oathAura(Light g, TravellerEntity hero, float time) {
        Vec3 feet = hero.position().add(0.0, 0.06, 0.0);
        Vec3 x = new Vec3(1.0, 0.0, 0.0);
        Vec3 z = new Vec3(0.0, 0.0, 1.0);
        float pulse = 0.55F + 0.2F * Mth.sin(time * 0.15F);
        g.ring(feet, x, z, 1.9, 0.1, SIEG.deep, pulse);
        g.ring(feet, x, z, 1.4, 0.06, SIEG.core, pulse * 0.8F);
        g.star(feet, x, z, 1.9, 6, time * 0.03, 0.06, SIEG.deep, pulse);
        g.star(feet, x, z, 1.4, 3, -time * 0.05, 0.05, SIEG.core, pulse * 0.7F);
        for (int i = 0; i < 3; i++) {
            double h = 0.6 + i * 0.7 + 0.1 * Mth.sin(time * 0.1F + i);
            g.ring(feet.add(0.0, h, 0.0), x, z, 1.3 - i * 0.25, 0.04, SIEG.deep, pulse * 0.5F);
        }
    }

    /** The dragon's stroke. */
    private static void dragonStroke(Light g, Performance p, Vec3 body, float t, int hit) {
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 side = new Vec3(-p.forward.z, 0.0, p.forward.x);
        // the fire gathers along the raised blade
        float charge = rise(t, 4.0F, 30.0F) * (t < hit ? 1.0F : fade(t, hit, hit + 6.0F));
        if (charge > 0.0F) {
            Vec3 axis = body.add(0.0, 2.1, 0.0);
            for (int k = 0; k < 3; k++) {
                for (int i = 0; i < 10; i++) {
                    double h0 = i * 0.24;
                    double h1 = (i + 1) * 0.24;
                    double a0 = t * 0.35 + k * 2.1 + h0 * 2.4;
                    double a1 = t * 0.35 + k * 2.1 + h1 * 2.4;
                    double r = 0.45 - h0 * 0.08;
                    Vec3 q0 = axis.add(Math.cos(a0) * r, h0, Math.sin(a0) * r);
                    Vec3 q1 = axis.add(Math.cos(a1) * r, h1, Math.sin(a1) * r);
                    g.ribbon(q0, q1, 0.12, 0.1, k == 0 ? SIEG.accent : SIEG.glow, charge, charge);
                }
            }
            g.billboard(axis.add(0.0, 1.6, 0.0), 1.4 * charge, SIEG.glow, charge * 0.8F);
            Random embers = new Random(p.seed);
            for (int i = 0; i < 16; i++) {
                double life = ((t * 0.05 + embers.nextDouble()) % 1.0);
                Vec3 e = body.add((embers.nextDouble() - 0.5) * 2.4, 0.5 + life * 4.0, (embers.nextDouble() - 0.5) * 2.4);
                g.billboard(e, 0.12, SIEG.glow, charge * (float) (1.0 - life));
            }
        }
        // the dragon he turned, in fire, above and behind him
        float dragon = rise(t, 10.0F, 24.0F) * (1.0F - rise(t, hit + 2.0F, hit + 16.0F));
        if (dragon > 0.0F) {
            double split = t > hit ? (t - hit) * 0.25 : 0.0;
            float roar = t > 26.0F && t < hit ? 1.0F : 0.0F;
            dragonHead(g, body.add(0.0, 4.6, 0.0).subtract(p.forward.scale(1.5)), p.forward, side, dragon, roar, split);
        }
        // the cut, and the fire running down the line
        float cut = t < hit ? 0.0F : fade(t, hit, hit + 10.0F);
        if (cut > 0.0F) {
            Vec3 center = body.add(0.0, 1.6, 0.0).add(p.forward.scale(1.0));
            g.band(center, up.scale(-1.0), p.forward, 3.6, 1.0, -30.0, 120.0, SIEG.glow, SIEG.core, 1.0F, cut);
            g.billboard(center, 5.0 * cut, SIEG.core, cut * 0.9F);
        }
        float run = rise(t, hit, hit + 8.0F);
        float burn = t < hit ? 0.0F : fade(t, hit + 4.0F, hit + 26.0F);
        if (t >= hit && burn > 0.0F) {
            double length = Travellers.Move.SIEG_DRAGON_STROKE.radius();
            Vec3 start = body.add(0.0, 0.05, 0.0);
            Vec3 front = start.add(p.forward.scale(length * run));
            if (run < 1.0F) {
                g.ribbon(front, front.add(0.0, 3.2, 0.0), 2.6, 0.6, SIEG.glow, 1.0F, 0.2F);
                g.ribbon(front, front.add(0.0, 2.4, 0.0), 1.2, 0.2, SIEG.core, 1.0F, 0.0F);
                g.flat(front.subtract(side.scale(1.6)), front.add(side.scale(1.6)), p.forward, 0.4, SIEG.accent, 1.0F,
                        1.0F);
            }
            g.flat(start, front, up, 2.6, SIEG.accent, burn * 0.7F, burn * 0.7F);
            g.flat(start, front, up, 0.8, SIEG.glow, burn, burn);
            Random flames = new Random(p.seed + 3L);
            for (int i = 0; i < 24; i++) {
                double along = flames.nextDouble() * length * run;
                double aside = (flames.nextDouble() - 0.5) * 2.4;
                double life = ((t * 0.08 + flames.nextDouble()) % 1.0);
                Vec3 base = start.add(p.forward.scale(along)).add(side.scale(aside));
                g.ribbon(base, base.add(0.0, 0.4 + life * 1.6, 0.0), 0.3 * (1.0 - life), 0.02, i % 2 == 0 ? SIEG.glow
                        : SIEG.accent, burn, 0.0F);
            }
        }
    }

    /** The dragon's head drawn in lines of fire (side profile facing the enemy); its jaw opens to roar. */
    private static void dragonHead(Light g, Vec3 at, Vec3 forward, Vec3 side, float alpha, float roar, double split) {
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        // profile points (forward, up) in blocks
        double jaw = 0.5 + 0.6 * roar;
        double[][] top = {{-2.2, 0.2}, {-1.4, 0.9}, {-0.4, 1.2}, {0.6, 1.0}, {1.6, 0.6}, {2.4, 0.3}, {2.8, 0.0}};
        double[][] bottom = {{2.8, 0.0}, {2.2, -0.2}, {1.0, -0.3}, {0.0, -0.5}};
        double[][] lower = {{2.5, -0.3 - jaw}, {1.4, -0.5 - jaw * 0.7}, {0.2, -0.7 - jaw * 0.3}, {-0.8, -0.9}};
        double[][] horn = {{-1.2, 1.0}, {-2.0, 2.0}, {-3.0, 2.6}, {-3.6, 2.4}};
        double[][] horn2 = {{-0.6, 1.1}, {-1.2, 2.2}, {-2.0, 3.0}};
        double[][] neck = {{-2.2, 0.2}, {-2.8, -0.8}, {-3.0, -2.0}};
        for (int s = -1; s <= 1; s += 2) {
            Vec3 off = side.scale(s * (0.6 + split));
            float a = alpha * (split > 0.0 ? (float) Math.max(0.0, 1.0 - split * 0.4) : 1.0F);
            polyline(g, at.add(off), forward, up, top, 0.14, SIEG.glow, a);
            polyline(g, at.add(off), forward, up, bottom, 0.12, SIEG.glow, a);
            polyline(g, at.add(off), forward, up, lower, 0.12, SIEG.accent, a);
            polyline(g, at.add(off), forward, up, horn, 0.12, SIEG.accent, a);
            polyline(g, at.add(off), forward, up, horn2, 0.1, SIEG.glow, a * 0.8F);
            polyline(g, at.add(off), forward, up, neck, 0.16, SIEG.accent, a * 0.8F);
            Vec3 eye = at.add(off).add(forward.scale(0.9)).add(0.0, 0.55, 0.0);
            g.billboard(eye, 0.35, SIEG.core, a);
            for (int i = 0; i < 5; i++) {
                Vec3 tooth = at.add(off).add(forward.scale(2.4 - i * 0.45)).add(0.0, -0.05, 0.0);
                g.ribbon(tooth, tooth.add(0.0, -0.25, 0.0), 0.06, 0.0, SIEG.core, a, a);
            }
        }
        if (roar > 0.0F) {
            Vec3 mouth = at.add(forward.scale(3.0)).add(0.0, -0.4, 0.0);
            g.ribbon(mouth, mouth.add(forward.scale(2.0)), 0.6, 1.4, SIEG.accent, alpha * 0.8F, 0.0F);
            g.billboard(mouth, 1.2, SIEG.glow, alpha);
        }
    }

    private static void polyline(Light g, Vec3 origin, Vec3 forward, Vec3 up, double[][] pts, double width, int rgb,
                                 float alpha) {
        for (int i = 0; i + 1 < pts.length; i++) {
            Vec3 a = origin.add(forward.scale(pts[i][0])).add(up.scale(pts[i][1]));
            Vec3 b = origin.add(forward.scale(pts[i + 1][0])).add(up.scale(pts[i + 1][1]));
            g.ribbon(a, b, width, width, rgb, alpha, alpha);
            g.ribbon(a, b, width * 0.35, width * 0.35, 0xFFFFE0, alpha, alpha);
        }
    }

    // ============================================================================================== Kaylen

    private static void thrust(Light g, Vec3 body, Vec3 forward, float t, int hit, Colours c) {
        float a = t < hit ? rise(t, hit - 2.0F, hit) : fade(t, hit, hit + 7.0F);
        if (a <= 0.0F) {
            return;
        }
        Vec3 from = body.add(0.0, 1.3, 0.0);
        Vec3 to = from.add(forward.scale(4.2));
        g.ribbon(from, to, 0.5, 0.0, c.glow, a, a);
        g.ribbon(from, to, 0.18, 0.0, c.core, a, a);
        Vec3[] basis = Light.basis(forward);
        g.ring(to.subtract(forward.scale(0.6)), basis[0], basis[1], 0.4 + (1.0F - a) * 0.8, 0.07, c.accent, a);
    }

    private static void flashStep(Light g, Performance p, TravellerEntity hero, Vec3 body, float t, int[] hits) {
        if (t >= hits[0] && hero != null) {
            float a = fade(t, hits[0], hits[0] + 14.0F);
            Vec3 from = p.origin.add(0.0, 1.0, 0.0);
            Vec3 to = body.add(0.0, 1.0, 0.0);
            g.ribbon(from, to, 1.2 * a, 0.3 * a, KAY.deep, a * 0.5F, a);
            g.ribbon(from, to, 0.3 * a, 0.1, KAY.core, a * 0.6F, a);
            for (int i = 1; i <= 3; i++) {
                Vec3 ghost = from.add(to.subtract(from).scale(i / 4.0));
                g.ribbon(ghost.add(0.0, -1.0, 0.0), ghost.add(0.0, 0.9, 0.0), 0.6, 0.6, KAY.glow, a * 0.25F,
                        a * 0.25F);
            }
        }
        if (hits.length > 1) {
            Vec3 at = p.aim.add(0.0, 1.0, 0.0);
            float cut = t < hits[1] ? rise(t, hits[1] - 2.0F, hits[1]) : fade(t, hits[1], hits[1] + 10.0F);
            if (cut > 0.0F) {
                Vec3 side = new Vec3(-p.forward.z, 0.0, p.forward.x);
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 a = at.add(side.scale(-1.3)).add(0.0, 1.3 * s, 0.0);
                    Vec3 b = at.add(side.scale(1.3)).add(0.0, -1.3 * s, 0.0);
                    g.ribbon(a, b, 0.05, 0.45, KAY.glow, cut, cut);
                    g.ribbon(a, b, 0.02, 0.15, KAY.core, cut, cut);
                }
                g.sparkle(at, 1.8 * cut, KAY.core, cut, t * 0.2);
            }
        }
    }

    private static void phantomArray(Light g, Performance p, float t, int[] hits) {
        Random random = new Random(p.seed);
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        for (int i = 0; i < hits.length; i++) {
            double a = i * Math.PI * 2.0 / hits.length;
            Vec3 high = p.aim.add(Math.cos(a) * 2.6, 6.5, Math.sin(a) * 2.6);
            double ga = random.nextDouble() * Math.PI * 2.0;
            double gd = random.nextDouble() * 2.5;
            Vec3 ground = p.aim.add(Math.cos(ga) * gd, 0.0, Math.sin(ga) * gd);
            float appear = rise(t, 4.0F + i, 9.0F + i);
            float fall = rise(t, hits[i] - 3.0F, hits[i]);
            float gone = fade(t, hits[i], hits[i] + 10.0F);
            if (t < hits[i]) {
                Vec3 tip = high.add(ground.subtract(high).scale(fall * fall));
                blade(g, tip, tip.subtract(ground.subtract(high).normalize().scale(-2.4)), appear);
            } else if (gone > 0.0F) {
                blade(g, ground.add(0.0, 0.2, 0.0), ground.add(0.0, 2.6, 0.0), gone);
                Vec3[] basis = Light.basis(up);
                g.ring(ground.add(0.0, 0.05, 0.0), basis[0], basis[1], 0.4 + 1.4 * (1.0F - gone), 0.1, KAY.glow, gone);
                g.star(ground.add(0.0, 0.06, 0.0), basis[0], basis[1], 0.9, 6, t * 0.1, 0.05, KAY.accent, gone);
            }
        }
    }

    /** A sword of light: the blade from tip to hilt, a guard across. */
    private static void blade(Light g, Vec3 tip, Vec3 hilt, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        g.ribbon(tip, hilt, 0.02, 0.26, KAY.glow, alpha, alpha);
        g.ribbon(tip, hilt, 0.01, 0.1, KAY.core, alpha, alpha);
        Vec3 dir = hilt.subtract(tip).normalize();
        Vec3 across = dir.cross(new Vec3(0.0, 1.0, 0.0));
        if (across.lengthSqr() < 1.0E-4) {
            across = new Vec3(1.0, 0.0, 0.0);
        }
        across = across.normalize().scale(0.35);
        g.ribbon(hilt.subtract(across), hilt.add(across), 0.08, 0.08, KAY.accent, alpha, alpha);
        g.ribbon(hilt, hilt.add(dir.scale(0.5)), 0.08, 0.06, KAY.accent, alpha, alpha);
    }

    /** Cuts appear everywhere and hang in the air; at the click of the sheath they all burst. */
    private static void infiniteBlades(Light g, Performance p, Vec3 body, float t, int[] hits) {
        int last = hits[hits.length - 1];
        Vec3 x = new Vec3(1.0, 0.0, 0.0);
        Vec3 z = new Vec3(0.0, 0.0, 1.0);
        float domain = rise(t, 6.0F, 12.0F) * (1.0F - rise(t, last + 4.0F, last + 10.0F));
        if (domain > 0.0F) {
            Vec3 ground = p.aim.add(0.0, 0.06, 0.0);
            g.ring(ground, x, z, 10.0, 0.08, KAY.glow, domain * 0.8F);
            g.ring(ground, x, z, 9.4, 0.04, KAY.accent, domain * 0.6F);
            runes(g, ground, x, z, 9.7, 40, t * 0.01, KAY.glow, domain * 0.6F, p.seed);
        }
        Random random = new Random(p.seed);
        for (int i = 0; i < 70; i++) {
            float appear = 8.0F + random.nextFloat() * (hits[hits.length - 2] - 6.0F);
            double a = random.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(random.nextDouble()) * 9.0;
            Vec3 center = p.aim.add(Math.cos(a) * d, 0.4 + random.nextDouble() * 2.8, Math.sin(a) * d);
            Vec3 dir = new Vec3(random.nextDouble() - 0.5, (random.nextDouble() - 0.5) * 1.4, random.nextDouble() - 0.5)
                    .normalize().scale(1.0 + random.nextDouble() * 1.4);
            if (t < appear) {
                continue;
            }
            float alpha;
            if (t < appear + 3.0F) {
                alpha = 1.0F; // the cut, bright
            } else if (t < last) {
                alpha = 0.32F + 0.06F * Mth.sin(t * 0.5F + i); // hanging in the air
            } else {
                alpha = fade(t, last, last + 9.0F) * 1.4F; // the burst
            }
            double grow = t < last ? 1.0 : 1.0 + (t - last) * 0.25;
            Vec3 p0 = center.subtract(dir.scale(grow));
            Vec3 p1 = center.add(dir.scale(grow));
            g.ribbon(p0, p1, 0.0, 0.16, i % 3 == 0 ? KAY.accent : KAY.glow, alpha, alpha);
            g.ribbon(p0, p1, 0.0, 0.05, KAY.core, alpha, alpha);
        }
        for (int i = 0; i + 1 < hits.length; i++) {
            float streak = fade(t, hits[i] - 1.0F, hits[i] + 5.0F);
            if (streak > 0.0F) {
                g.billboard(body.add(0.0, 1.0, 0.0), 1.6 * streak, KAY.glow, streak * 0.7F);
            }
        }
        float burst = fade(t, last, last + 14.0F);
        if (burst > 0.0F) {
            Vec3 ground = p.aim.add(0.0, 0.1, 0.0);
            double grow = 1.0 - burst;
            g.ring(ground, x, z, 2.0 + 10.0 * grow, 0.4 * burst + 0.1, KAY.glow, burst);
            g.ring(ground, x, z, 1.0 + 8.0 * grow, 0.15, KAY.accent, burst);
            g.billboard(ground.add(0.0, 1.4, 0.0), 7.0 * burst, KAY.core, burst * 0.6F);
        }
    }

    // ============================================================================================== Mariel

    private static void lance(Light g, Performance p, float t, int hit) {
        Vec3 ground = p.aim.add(0.0, 0.05, 0.0);
        Vec3 top = ground.add(0.0, 8.0, 0.0);
        Vec3 x = new Vec3(1.0, 0.0, 0.0);
        Vec3 z = new Vec3(0.0, 0.0, 1.0);
        if (t < hit) {
            float charge = rise(t, 2.0F, hit);
            g.ring(top, x, z, 1.2 * charge, 0.1, MAR.glow, charge);
            g.star(top, x, z, 1.2 * charge, 6, t * 0.1, 0.05, MAR.accent, charge);
            g.ring(ground, x, z, 1.4 * charge, 0.06, MAR.glow, charge * 0.6F);
            return;
        }
        float a = fade(t, hit, hit + 10.0F);
        g.ribbon(top, ground, 0.1, 0.9 * a + 0.1, MAR.glow, a, a);
        g.ribbon(top, ground, 0.05, 0.35 * a, MAR.core, a, a);
        g.ring(ground, x, z, 0.5 + 2.0 * (1.0F - a), 0.14, MAR.glow, a);
        Vec3 at = ground.add(0.0, 1.0, 0.0);
        g.ribbon(at.add(-1.4 * a, 0.0, 0.0), at.add(1.4 * a, 0.0, 0.0), 0.12, 0.12, MAR.core, a, a);
        g.ribbon(at.add(0.0, -1.4 * a, 0.0), at.add(0.0, 1.4 * a, 0.0), 0.12, 0.12, MAR.core, a, a);
    }

    /** The golden circle under her: two rings, a hexagram, runes; light rises from it while it lasts. */
    private static void sanctuary(Light g, Performance p, float t, int hit) {
        Vec3 ground = p.origin.add(0.0, 0.06, 0.0);
        Vec3 x = new Vec3(1.0, 0.0, 0.0);
        Vec3 z = new Vec3(0.0, 0.0, 1.0);
        double radius = Travellers.Move.MAR_SANCTUARY.radius();
        float open = rise(t, hit - 4.0F, hit + 4.0F);
        float close = 1.0F - rise(t, hit + 112.0F, hit + 124.0F);
        float a = open * close;
        if (a <= 0.0F) {
            return;
        }
        g.ring(ground, x, z, radius * open, 0.16, MAR.glow, a);
        g.ring(ground, x, z, radius * 0.92 * open, 0.06, MAR.core, a * 0.8F);
        g.ring(ground, x, z, radius * 0.55 * open, 0.08, MAR.accent, a * 0.8F);
        g.star(ground, x, z, radius * 0.92 * open, 6, t * 0.01, 0.07, MAR.glow, a * 0.9F);
        g.star(ground, x, z, radius * 0.55 * open, 6, -t * 0.02, 0.06, MAR.core, a * 0.8F);
        runes(g, ground, x, z, radius * 0.75 * open, 24, -t * 0.008, MAR.glow, a * 0.7F, p.seed);
        Random random = new Random(p.seed);
        for (int i = 0; i < 20; i++) {
            double ang = random.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(random.nextDouble()) * radius * 0.9;
            double life = ((t * 0.02 + random.nextDouble()) % 1.0);
            Vec3 mote = ground.add(Math.cos(ang) * d, life * 3.0, Math.sin(ang) * d);
            g.sparkle(mote, 0.25, i % 2 == 0 ? MAR.glow : MAR.accent, a * (float) (1.0 - life), t * 0.1 + i);
        }
        g.ribbon(ground, ground.add(0.0, 2.2, 0.0), radius * 1.6 * open, radius * 1.2, MAR.glow, a * 0.12F, 0.0F);
    }

    /** Pillars of light under the enemies near the aim. */
    private static void purification(Light g, Performance p, float t, int hit) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        List<Vec3> spots = new ArrayList<>();
        if (t >= hit - 6.0F) {
            for (Entity e : minecraft.level.getEntities((Entity) null, new AABB(p.aim, p.aim).inflate(8.0, 4.0, 8.0),
                    e -> e instanceof Enemy && e instanceof Mob && e.isAlive())) {
                if (spots.size() >= 5) {
                    break;
                }
                spots.add(e.position());
            }
        }
        if (spots.isEmpty()) {
            spots.add(p.aim);
        }
        if (p.trail.isEmpty() && t >= hit) {
            p.trail.addAll(spots); // where they stood when the pillars rose
        }
        Vec3 x = new Vec3(1.0, 0.0, 0.0);
        Vec3 z = new Vec3(0.0, 0.0, 1.0);
        for (Vec3 at : t >= hit && !p.trail.isEmpty() ? p.trail : spots) {
            Vec3 ground = at.add(0.0, 0.05, 0.0);
            if (t < hit) {
                float charge = rise(t, hit - 6.0F, hit);
                g.ring(ground, x, z, 1.1, 0.08, MAR.glow, charge);
                g.star(ground, x, z, 1.1, 6, t * 0.1, 0.05, MAR.core, charge);
                continue;
            }
            float a = fade(t, hit, hit + 16.0F);
            float grow = rise(t, hit, hit + 3.0F);
            g.ribbon(ground, ground.add(0.0, 7.0 * grow, 0.0), 1.4, 1.0, MAR.glow, a, 0.0F);
            g.ribbon(ground, ground.add(0.0, 6.0 * grow, 0.0), 0.5, 0.3, MAR.core, a, 0.0F);
            g.ring(ground, x, z, 1.0 + (1.0F - a), 0.12, MAR.glow, a);
            for (int i = 0; i < 4; i++) {
                g.ring(ground.add(0.0, 1.0 + i * 1.3 + (t - hit) * 0.1, 0.0), x, z, 0.8 - i * 0.1, 0.05, MAR.accent,
                        a * 0.7F);
            }
        }
    }

    /** The prayer of the kingdom reborn. */
    private static void kingdomReborn(Light g, Performance p, Vec3 body, float t, int hit) {
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 side = new Vec3(-p.forward.z, 0.0, p.forward.x);
        Vec3 x = new Vec3(1.0, 0.0, 0.0);
        Vec3 z = new Vec3(0.0, 0.0, 1.0);
        float end = 1.0F - rise(t, hit + 16.0F, hit + 26.0F);
        // the rose window behind her
        float window = rise(t, 8.0F, 26.0F) * end;
        if (window > 0.0F) {
            Vec3 center = body.add(0.0, 2.4, 0.0).subtract(p.forward.scale(1.2));
            double r = 2.8 * window;
            g.ring(center, side, up, r, 0.12, MAR.glow, window);
            g.ring(center, side, up, r * 0.78, 0.06, MAR.core, window);
            g.ring(center, side, up, r * 0.42, 0.08, MAR.accent, window);
            g.star(center, side, up, r * 0.78, 12, t * 0.01, 0.05, MAR.glow, window * 0.8F);
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6.0 + t * 0.01;
                Vec3 inner = center.add(side.scale(Math.cos(a) * r * 0.42)).add(up.scale(Math.sin(a) * r * 0.42));
                Vec3 outer = center.add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
                g.ribbon(inner, outer, 0.05, 0.05, MAR.glow, window * 0.7F, window * 0.7F);
            }
            g.billboard(center, 2.4 * window, MAR.accent, window * 0.35F);
        }
        // the wings of light
        float wings = rise(t, 14.0F, 32.0F) * end;
        if (wings > 0.0F) {
            Vec3 root = body.add(0.0, 1.9, 0.0).subtract(p.forward.scale(0.35));
            float flap = 0.08F * Mth.sin(t * 0.25F);
            for (int s = -1; s <= 1; s += 2) {
                for (int i = 0; i < 9; i++) {
                    double k = i / 8.0;
                    double angle = Math.toRadians(-20.0 + (100.0 + 30.0 * flap) * k * wings);
                    Vec3 dir = side.scale(s * Math.cos(angle)).add(up.scale(Math.sin(angle) * 0.9 + 0.25))
                            .subtract(p.forward.scale(0.3)).normalize();
                    double length = (1.6 + 1.6 * Math.sin(Math.PI * Math.min(1.0, k * 0.9 + 0.1))) * wings;
                    Vec3 tip = root.add(dir.scale(length));
                    g.ribbon(root.add(dir.scale(0.3)), tip, 0.18, 0.42, MAR.glow, wings * 0.85F, wings * 0.5F);
                    g.ribbon(root.add(dir.scale(0.3)), tip, 0.06, 0.12, MAR.core, wings, wings * 0.6F);
                }
            }
            g.billboard(root, 1.6 * wings, MAR.core, wings * 0.5F);
        }
        // the radiant wave and the feathers
        float wave = t < hit ? 0.0F : fade(t, hit, hit + 22.0F);
        if (wave > 0.0F) {
            Vec3 ground = body.add(0.0, 0.08, 0.0);
            double grow = 1.0 - wave;
            g.ring(ground, x, z, 1.0 + 15.0 * grow, 0.6 * wave + 0.1, MAR.glow, wave);
            g.ring(ground, x, z, 0.6 + 12.0 * grow, 0.2, MAR.core, wave);
            g.ribbon(ground, ground.add(0.0, 20.0, 0.0), 3.0 * wave, 0.6, MAR.core, wave, 0.0F);
            g.billboard(body.add(0.0, 2.0, 0.0), 10.0 * wave, MAR.glow, wave * 0.5F);
        }
        if (t >= hit - 10.0F) {
            Random random = new Random(p.seed);
            float feathers = end;
            for (int i = 0; i < 30; i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double d = random.nextDouble() * 9.0;
                double drop = ((t - hit + 10.0) * 0.06 + random.nextDouble() * 4.0) % 6.0;
                Vec3 f = body.add(Math.cos(a) * d + Math.sin(t * 0.1 + i) * 0.3, 7.0 - drop, Math.sin(a) * d);
                Vec3 tilt = side.scale(0.25 * Math.cos(t * 0.15 + i)).add(0.0, 0.2, 0.0);
                g.ribbon(f.subtract(tilt), f.add(tilt), 0.04, 0.16, MAR.glow, feathers * 0.9F, feathers * 0.9F);
            }
        }
    }

    // ============================================================================================ gestures

    private static void join(Light g, Performance p, TravellerEntity hero, float t) {
        if (hero == null) {
            return;
        }
        Vec3 body = hero.position();
        Vec3 x = new Vec3(1.0, 0.0, 0.0);
        Vec3 z = new Vec3(0.0, 0.0, 1.0);
        float a = fade(t, 0.0F, 30.0F);
        Colours c = switch (hero.who()) {
            case ROSALIND -> ROS;
            case SIEGWALD -> SIEG;
            case KAYLEN -> KAY;
            case MARIEL -> MAR;
        };
        g.ring(body.add(0.0, 0.06, 0.0), x, z, 0.8 + 1.4 * (1.0F - a), 0.12, c.glow, a);
        g.star(body.add(0.0, 0.07, 0.0), x, z, 1.2, hero.who() == Travellers.Traveller.MARIEL ? 6 : 5, t * 0.05,
                0.06, c.accent, a);
        for (int i = 0; i < 10; i++) {
            double angle = i * Math.PI / 5.0 + t * 0.15;
            double h = (t * 0.08 + i * 0.1) % 2.4;
            Vec3 s = body.add(Math.cos(angle) * 0.9, h, Math.sin(angle) * 0.9);
            g.sparkle(s, 0.35, i % 2 == 0 ? c.core : c.glow, a, t * 0.2 + i);
        }
    }

    private static void standUp(Light g, Performance p, float t) {
        float a = fade(t, 0.0F, 16.0F);
        Vec3 ground = p.origin.add(0.0, 0.06, 0.0);
        Vec3 x = new Vec3(1.0, 0.0, 0.0);
        Vec3 z = new Vec3(0.0, 0.0, 1.0);
        g.ring(ground, x, z, 0.6 + 1.6 * (1.0F - a), 0.12, 0xFFF0C0, a);
        g.ribbon(ground, ground.add(0.0, 3.0, 0.0), 1.0 * a, 0.1, 0xFFF0C0, a * 0.7F, 0.0F);
    }

    private static Vec3 flat(Vec3 v) {
        return new Vec3(v.x, 0.0, v.z);
    }

    // ============================================================================================ quads of light

    /** Writes quads of light, relative to the camera. */
    private static final class Light {
        private final VertexConsumer out;
        private final Matrix4f matrix;
        private final Vec3 camera;

        Light(VertexConsumer out, Matrix4f matrix, Vec3 camera) {
            this.out = out;
            this.matrix = matrix;
            this.camera = camera;
        }

        static Vec3[] basis(Vec3 dir) {
            Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
            Vec3 up = Math.abs(d.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            Vec3 u = d.cross(up).normalize();
            return new Vec3[]{u, u.cross(d).normalize()};
        }

        static Vec3 turn(Vec3 dir, double angle) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            return new Vec3(dir.x * cos - dir.z * sin, dir.y, dir.x * sin + dir.z * cos);
        }

        /**
         * A crescent in the plane of {@code a} (0°) and {@code b} (90°) around {@code center}, from {@code from}
         * towards {@code to} degrees as far as {@code sweep} (0..1): brightest at its head, thinning to its tail,
         * a white core.
         */
        void band(Vec3 center, Vec3 a, Vec3 b, double radius, double width, double from, double to, int glow, int core,
                  float sweep, float alpha) {
            if (alpha <= 0.01F || sweep <= 0.0F) {
                return;
            }
            double head = from + (to - from) * sweep;
            int steps = Math.max(4, (int) Math.ceil(Math.abs(head - from) / 6.0));
            for (int i = 0; i < steps; i++) {
                double u0 = i / (double) steps;
                double u1 = (i + 1) / (double) steps;
                double t0 = Math.toRadians(from + (head - from) * u0);
                double t1 = Math.toRadians(from + (head - from) * u1);
                float al0 = alpha * (float) (0.12 + 0.88 * u0 * u0);
                float al1 = alpha * (float) (0.12 + 0.88 * u1 * u1);
                double w0 = width * (0.25 + 0.75 * Math.sin(Math.PI * Math.min(1.0, u0 * 1.15)));
                double w1 = width * (0.25 + 0.75 * Math.sin(Math.PI * Math.min(1.0, u1 * 1.15)));
                arcQuad(center, a, b, radius, w0, w1, t0, t1, glow, al0, al1);
                arcQuad(center, a, b, radius, w0 * 0.3, w1 * 0.3, t0, t1, core, al0, al1);
            }
        }

        void ring(Vec3 center, Vec3 a, Vec3 b, double radius, double width, int rgb, float alpha) {
            if (alpha <= 0.01F || radius <= 0.0) {
                return;
            }
            int steps = Math.max(16, (int) Math.min(96, radius * 10));
            for (int i = 0; i < steps; i++) {
                double t0 = Math.PI * 2.0 * i / steps;
                double t1 = Math.PI * 2.0 * (i + 1) / steps;
                arcQuad(center, a, b, radius, width, width, t0, t1, rgb, alpha, alpha);
            }
        }

        /** A star polygon of {@code points} points in the plane of {@code a} and {@code b}. */
        void star(Vec3 center, Vec3 a, Vec3 b, double radius, int points, double spin, double width, int rgb,
                  float alpha) {
            if (alpha <= 0.01F || radius <= 0.0) {
                return;
            }
            Vec3 normal = a.cross(b).normalize();
            int step = points <= 4 ? 1 : (points == 6 ? 2 : (points % 2 == 0 ? points / 2 - 1 : 2));
            for (int i = 0; i < points; i++) {
                double t0 = spin + Math.PI * 2.0 * i / points;
                double t1 = spin + Math.PI * 2.0 * (i + step) / points;
                Vec3 p0 = point(center, a, b, radius, t0);
                Vec3 p1 = point(center, a, b, radius, t1);
                Vec3 across = p1.subtract(p0).cross(normal).normalize().scale(width / 2.0);
                quad(p0.add(across), p0.subtract(across), p1.subtract(across), p1.add(across), rgb, alpha, alpha);
            }
        }

        /** A ribbon from {@code a} to {@code b} that always faces the camera. */
        void ribbon(Vec3 a, Vec3 b, double widthA, double widthB, int rgb, float alphaA, float alphaB) {
            Vec3 along = b.subtract(a);
            if (along.lengthSqr() < 1.0E-4 || (alphaA <= 0.01F && alphaB <= 0.01F)) {
                return;
            }
            Vec3 view = a.add(b).scale(0.5).subtract(camera);
            Vec3 across = along.cross(view);
            if (across.lengthSqr() < 1.0E-6) {
                across = along.cross(new Vec3(0.0, 1.0, 0.0));
            }
            if (across.lengthSqr() < 1.0E-6) {
                return;
            }
            across = across.normalize();
            Vec3 sa = across.scale(widthA / 2.0);
            Vec3 sb = across.scale(widthB / 2.0);
            quad(a.add(sa), a.subtract(sa), b.subtract(sb), b.add(sb), rgb, alphaA, alphaB);
        }

        /** A strip lying in the plane across {@code normal} (on the ground: normal up). */
        void flat(Vec3 a, Vec3 b, Vec3 normal, double width, int rgb, float alphaA, float alphaB) {
            Vec3 along = b.subtract(a);
            if (along.lengthSqr() < 1.0E-4) {
                return;
            }
            Vec3 across = along.cross(normal);
            if (across.lengthSqr() < 1.0E-6) {
                return;
            }
            across = across.normalize().scale(width / 2.0);
            quad(a.add(across), a.subtract(across), b.subtract(across), b.add(across), rgb, alphaA, alphaB);
        }

        /** A square of light facing the camera. */
        void billboard(Vec3 at, double size, int rgb, float alpha) {
            if (alpha <= 0.01F || size <= 0.0) {
                return;
            }
            Vec3[] basis = basis(at.subtract(camera));
            Vec3 u = basis[0].scale(size / 2.0);
            Vec3 v = basis[1].scale(size / 2.0);
            quad(at.add(u).add(v), at.subtract(u).add(v), at.subtract(u).subtract(v), at.add(u).subtract(v), rgb,
                    alpha, alpha);
        }

        /** A four-pointed star of light facing the camera (long thin rays, a bright centre). */
        void sparkle(Vec3 at, double size, int rgb, float alpha, double spin) {
            if (alpha <= 0.01F || size <= 0.0) {
                return;
            }
            Vec3[] basis = basis(at.subtract(camera));
            for (int i = 0; i < 2; i++) {
                double a = spin * 0.2 + i * Math.PI / 2.0;
                Vec3 ray = basis[0].scale(Math.cos(a) * size).add(basis[1].scale(Math.sin(a) * size));
                Vec3 across = basis[0].scale(-Math.sin(a) * size * 0.08).add(basis[1].scale(Math.cos(a) * size * 0.08));
                quad(at.subtract(ray), at.subtract(across), at.add(ray), at.add(across), rgb, alpha, alpha);
            }
            billboard(at, size * 0.35, 0xFFFFFF, alpha);
        }

        private void arcQuad(Vec3 center, Vec3 a, Vec3 b, double radius, double w0, double w1, double t0, double t1,
                             int rgb, float alpha0, float alpha1) {
            Vec3 in0 = point(center, a, b, Math.max(0.0, radius - w0 / 2.0), t0);
            Vec3 out0 = point(center, a, b, radius + w0 / 2.0, t0);
            Vec3 in1 = point(center, a, b, Math.max(0.0, radius - w1 / 2.0), t1);
            Vec3 out1 = point(center, a, b, radius + w1 / 2.0, t1);
            quad(in0, out0, out1, in1, rgb, alpha0, alpha1);
        }

        private static Vec3 point(Vec3 center, Vec3 a, Vec3 b, double radius, double angle) {
            return center.add(a.scale(Math.cos(angle) * radius)).add(b.scale(Math.sin(angle) * radius));
        }

        private void quad(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int rgb, float alpha01, float alpha23) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            int a0 = Math.round(255.0F * Mth.clamp(alpha01, 0.0F, 1.0F));
            int a1 = Math.round(255.0F * Mth.clamp(alpha23, 0.0F, 1.0F));
            vertex(p0, r, g, b, a0);
            vertex(p1, r, g, b, a0);
            vertex(p2, r, g, b, a1);
            vertex(p3, r, g, b, a1);
        }

        private void vertex(Vec3 p, int r, int g, int b, int a) {
            out.vertex(matrix, (float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z))
                    .color(r, g, b, a).endVertex();
        }
    }
}
