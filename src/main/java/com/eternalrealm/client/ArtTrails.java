package com.eternalrealm.client;

import com.eternalrealm.client.block.ShaderCompat;
import com.eternalrealm.combat.WeaponSkillLooks;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.config.ERClientConfig;
import com.eternalrealm.item.ArtChoreography;
import com.eternalrealm.item.ArtLook;
import com.eternalrealm.item.ArtTimeline;
import com.eternalrealm.item.GeneratedArts;
import com.eternalrealm.item.WeaponArt;
import com.eternalrealm.item.WeaponArts;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * The glowing trails of the Z-key weapon skills (owner, 2026-11-12: 「もう少し各々派手に」): every strike of an art's
 * choreography ({@link ArtChoreography}) draws its own light in the art's colours as the blade moves: crescents that
 * sweep with the cut, falling and rising arcs, thrust streaks, rings around a spin, a shockwave and a pillar where a
 * slam lands, turning magic circles for staves, rings at the bow; the wind-up gathers light its own way (spirals,
 * a glow, circles on the ground or in the air, rising light, a spinning ring). Drawn for every player in sight with
 * additive blending, after the particles; the server's particles draw the same strikes for anyone with a shader pack.
 * A heavy art's last strike shakes the wielder's own view. Client thread only.
 */
public final class ArtTrails {
    private static final Logger LOG = LogUtils.getLogger();
    private static final double RANGE = 64.0;
    /** Ticks a strike's light lingers after its hit (the last one a little longer). */
    private static final float LIFE = 7.0F;
    private static boolean disabled;
    private static String shaken = "";

    private ArtTrails() {
    }

    /** Additive position-colour quads: light. */
    private static final class Types extends RenderType {
        static final RenderType GLOW = RenderType.create("eternalrealm_art_trail", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 1 << 16, false, true, CompositeState.builder()
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

    /** The colours of an art: its core, its glow, its accent (always bright enough to show when added). */
    private record Palette(int core, int glow, int accent, ArtLook.Windup windup) {
    }

    public static void render(RenderLevelStageEvent event) {
        if (disabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || !ERClientConfig.WEAPON_ANIMATIONS.get() || ShaderCompat.shaderPackInUse()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        try {
            draw(event, minecraft);
        } catch (RuntimeException e) {
            disabled = true;
            LOG.error("Weapon skill trails disabled after a rendering error", e);
        }
    }

    private static void draw(RenderLevelStageEvent event, Minecraft minecraft) {
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        long tick = ClientCombatState.tick();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Glow glow = null;
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (player.isInvisible() || player.distanceToSqr(camera) > RANGE * RANGE) {
                continue;
            }
            Optional<CombatAnimations.Anim> anim = CombatAnimations.of(player);
            if (anim.isEmpty() || anim.get().motion() != CombatAnimations.Motion.ART) {
                continue;
            }
            Optional<ArtPoses.Play> play = ArtPoses.play(anim.get());
            if (play.isEmpty()) {
                continue;
            }
            if (glow == null) {
                glow = new Glow(buffers.getBuffer(Types.GLOW), event.getPoseStack().last().pose(), camera);
            }
            boolean ownView = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
            performance(glow, player, anim.get(), play.get(), anim.get().elapsed(tick, partial), partial, ownView);
        }
        if (glow != null) {
            buffers.endBatch(Types.GLOW);
        }
    }

    // ------------------------------------------------------------------ one performance

    private static void performance(Glow glow, AbstractClientPlayer player, CombatAnimations.Anim anim,
                                    ArtPoses.Play play, float elapsed, float partial, boolean ownView) {
        Palette palette = palette(anim.actionId(), anim.weapon());
        Frame f = new Frame(player, partial);
        int[] hits = play.hits();
        float first = ArtTimeline.strikeStart(hits, 0);
        if (elapsed < first) {
            windup(glow, f, palette, elapsed, elapsed / Math.max(1.0F, first), ownView);
        }
        ArtChoreography.Choreo choreo = play.choreo();
        for (int k = 0; k < hits.length; k++) {
            boolean last = k == hits.length - 1;
            float start = ArtTimeline.strikeStart(hits, k);
            float end = ArtTimeline.strikeEnd(hits, k);
            float life = last ? LIFE + 3.0F : LIFE;
            if (elapsed < start || elapsed > end + life) {
                continue;
            }
            float sweep = ArtPoses.easeOut((elapsed - start) / Math.max(0.5F, end - start));
            float fade = elapsed <= end ? 1.0F : 1.0F - (elapsed - end) / life;
            float alpha = (float) Math.pow(Math.max(0.0F, fade), 1.5);
            float scale = last ? (choreo.heavy() ? 1.3F : 1.15F) : 1.0F;
            ArtChoreography.Trail trail = choreo.strike(k).trail();
            strike(glow, f, palette, trail, sweep, alpha, scale, Math.max(1.6, play.reach(k)), elapsed - hits[k],
                    last);
            if (last && choreo.heavy() && elapsed >= hits[k] && player == Minecraft.getInstance().player) {
                String key = anim.actionId() + "@" + anim.start();
                if (!key.equals(shaken)) {
                    shaken = key;
                    CameraShake.add(1.4F, 9);
                    CameraShake.kick(1.6F, 6);
                }
            }
        }
    }

    /** Where a player stands and faces this frame. */
    private static final class Frame {
        final Vec3 feet;
        final Vec3 eye;
        final Vec3 fwd;
        final Vec3 right;
        final Vec3 up = new Vec3(0.0, 1.0, 0.0);
        final Vec3 look;
        final int side;

        Frame(AbstractClientPlayer player, float partial) {
            feet = player.getPosition(partial);
            eye = player.getEyePosition(partial);
            double yaw = Math.toRadians(Mth.lerp(partial, player.yRotO, player.getYRot()));
            fwd = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
            right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
            look = player.getViewVector(partial);
            side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        }

        Vec3 at(double forward, double height, double toRight) {
            return feet.add(fwd.scale(forward)).add(0.0, height, 0.0).add(right.scale(toRight * side));
        }

        Vec3 hand() {
            return at(0.5, 1.3, 0.38);
        }
    }

    // ------------------------------------------------------------------ the strikes

    private static void strike(Glow g, Frame f, Palette p, ArtChoreography.Trail trail, float sweep, float alpha,
                               float scale, double reach, float sinceHit, boolean last) {
        double r = reach * 0.78 * scale;
        int dir = f.side;
        switch (trail) {
            case ARC_RIGHT, ARC_LEFT -> {
                int way = (trail == ArtChoreography.Trail.ARC_RIGHT ? 1 : -1) * dir;
                Vec3 a = f.right.add(0.0, 0.18 * way, 0.0).normalize();
                double from = way > 0 ? 155.0 : 25.0;
                double to = way > 0 ? 25.0 : 155.0;
                g.band(f.at(0.15, 1.15, 0.0), a, f.fwd, r, 0.55 * scale, from, to, sweep, alpha, p);
            }
            case SWEEP -> {
                int way = dir;
                double from = way > 0 ? 205.0 : -25.0;
                double to = way > 0 ? -25.0 : 205.0;
                g.band(f.at(0.0, 0.85, 0.0), f.right, f.fwd, reach * 0.95 * scale, 0.8 * scale, from, to, sweep, alpha, p);
                g.band(f.at(0.0, 0.95, 0.0), f.right, f.fwd, reach * 0.6 * scale, 0.35 * scale, from, to, sweep,
                        alpha * 0.6F, p);
            }
            case RISE -> g.band(f.at(0.2, 1.0, 0.2), f.up, f.fwd, r, 0.5 * scale, 155.0, -15.0, sweep, alpha, p);
            case FALL -> {
                g.band(f.at(0.2, 1.1, 0.1), f.up, f.fwd, r, 0.55 * scale, -15.0, 160.0, sweep, alpha, p);
                if (sinceHit >= 0.0F) {
                    quake(g, f, p, f.feet.add(f.fwd.scale(Math.min(2.6, r))), sinceHit, alpha, 0.55 * scale, last);
                }
            }
            case DIAG_RIGHT, DIAG_LEFT -> {
                int way = (trail == ArtChoreography.Trail.DIAG_RIGHT ? 1 : -1) * dir;
                Vec3 a = f.up.scale(0.75).subtract(f.right.scale(0.65 * way)).normalize();
                g.band(f.at(0.2, 1.1, 0.0), a, f.fwd, r, 0.55 * scale, 0.0, 180.0, sweep, alpha, p);
            }
            case STREAK -> streak(g, f, p, reach * 1.05 * scale, 0.42 * scale, sweep, alpha, sinceHit);
            case RING -> {
                int way = dir;
                double head = 90.0 + way * 360.0;
                g.band(f.at(0.0, 1.0, 0.0), f.right, f.fwd, r, 0.6 * scale, 90.0, head, sweep, alpha, p);
                g.band(f.at(0.0, 0.55, 0.0), f.right, f.fwd, r * 0.75, 0.3 * scale, 90.0 + way * 40.0, head + way * 40.0,
                        sweep, alpha * 0.55F, p);
            }
            case QUAKE -> {
                if (sinceHit < 0.0F) {
                    g.band(f.at(0.2, 1.2, 0.0), f.up, f.fwd, Math.min(r, 2.6), 0.55 * scale, -20.0, 165.0, sweep,
                            alpha, p);
                } else {
                    quake(g, f, p, f.feet.add(f.fwd.scale(1.6)), sinceHit, alpha, scale, last);
                }
            }
            case CROSS -> {
                Vec3 a1 = f.up.scale(0.75).subtract(f.right.scale(0.65)).normalize();
                Vec3 a2 = f.up.scale(0.75).add(f.right.scale(0.65)).normalize();
                g.band(f.at(0.3, 1.1, 0.0), a1, f.fwd, r * 0.85, 0.45 * scale, 0.0, 180.0, sweep, alpha, p);
                g.band(f.at(0.3, 1.1, 0.0), a2, f.fwd, r * 0.85, 0.45 * scale, 0.0, 180.0, sweep, alpha, p);
            }
            case FAN -> {
                for (int i = -2; i <= 2; i++) {
                    Vec3 d = turn(f.look, Math.toRadians(i * 20.0));
                    Vec3 from = f.eye.add(0.0, -0.3, 0.0).add(d.scale(0.7));
                    g.ribbon(from, from.add(d.scale(reach * 1.4 * sweep)), 0.22 * scale, 0.04, p.glow, alpha * 0.9F,
                            alpha * 0.2F);
                }
            }
            case SIGIL_AHEAD -> sigil(g, f, p, sweep, alpha, scale, sinceHit, false);
            case SIGIL_SKY -> sky(g, f, p, sweep, alpha, scale, reach, sinceHit);
            case BEAM -> {
                sigil(g, f, p, sweep, alpha, scale, sinceHit, true);
                if (sinceHit >= -1.0F) {
                    Vec3 from = f.eye.add(f.look.scale(1.6)).add(0.0, -0.1, 0.0);
                    Vec3 to = from.add(f.look.scale(Math.max(8.0, reach) * Math.min(1.0F, (sinceHit + 1.0F) / 2.0F)));
                    g.ribbon(from, to, 0.7 * scale, 0.35 * scale, p.glow, alpha, alpha * 0.4F);
                    g.ribbon(from, to, 0.25 * scale, 0.12 * scale, 0xFFFFFF, alpha, alpha * 0.5F);
                }
            }
            case MUZZLE, MUZZLE_HEAVY -> {
                boolean heavy = trail == ArtChoreography.Trail.MUZZLE_HEAVY;
                Vec3 bow = f.eye.add(0.0, -0.12, 0.0).add(f.look.scale(1.0));
                Vec3[] basis = basis(f.look);
                if (sinceHit >= 0.0F) {
                    float grow = Mth.clamp(sinceHit / 5.0F, 0.0F, 1.0F);
                    g.ring(bow, basis[0], basis[1], 0.25 + (heavy ? 1.4 : 0.9) * grow, 0.14, p.glow, alpha);
                    if (heavy) {
                        g.ring(bow.add(f.look.scale(0.8)), basis[0], basis[1], 0.2 + 1.0 * grow, 0.1, p.accent,
                                alpha * 0.8F);
                    }
                    Vec3 to = bow.add(f.look.scale((heavy ? 8.0 : 5.0) * Math.min(1.0F, sinceHit / 2.0F + 0.3F)));
                    g.ribbon(bow, to, heavy ? 0.3 : 0.18, 0.04, p.core, alpha, alpha * 0.1F);
                } else {
                    // the arrow gathering light on the string
                    g.ring(bow, basis[0], basis[1], 0.5 * (1.0 + sinceHit / 3.0), 0.08, p.accent, 0.6F);
                }
            }
        }
    }

    /** A thrust: a streak growing along the look, a shock ring at its tip once it lands. */
    private static void streak(Glow g, Frame f, Palette p, double length, double width, float sweep, float alpha,
                               float sinceHit) {
        Vec3 from = f.eye.add(0.0, -0.35, 0.0).add(f.look.scale(0.6));
        Vec3 tip = from.add(f.look.scale(length * sweep));
        g.ribbon(from, tip, width, 0.05, p.glow, alpha * 0.85F, alpha);
        g.ribbon(from, tip, width * 0.35, 0.02, 0xFFFFFF, alpha * 0.7F, alpha);
        if (sinceHit >= 0.0F) {
            Vec3[] basis = basis(f.look);
            float grow = Mth.clamp(sinceHit / 4.0F, 0.0F, 1.0F);
            g.ring(tip, basis[0], basis[1], 0.2 + 0.9 * grow, 0.12, p.accent, alpha);
            g.ring(tip.subtract(f.look.scale(0.6)), basis[0], basis[1], 0.15 + 0.6 * grow, 0.08, p.glow, alpha * 0.7F);
        }
    }

    /** Where something heavy lands: rings racing over the ground, cracks, a pillar of light. */
    private static void quake(Glow g, Frame f, Palette p, Vec3 at, float sinceHit, float alpha, double scale,
                              boolean last) {
        Vec3 ground = new Vec3(at.x, f.feet.y + 0.06, at.z);
        float fast = Mth.clamp(sinceHit / 6.0F, 0.0F, 1.0F);
        float slow = Mth.clamp(sinceHit / 10.0F, 0.0F, 1.0F);
        double reach = (last ? 4.2 : 3.2) * scale;
        g.ring(ground, f.right, f.fwd, 0.4 + reach * fast, 0.35 * scale, p.glow, alpha);
        g.ring(ground.add(0.0, 0.02, 0.0), f.right, f.fwd, 0.3 + reach * 0.6 * slow, 0.2 * scale, p.accent,
                alpha * 0.8F);
        for (int i = 0; i < 8; i++) {
            Vec3 d = turn(f.fwd, Math.PI * 2.0 * i / 8.0 + 0.2);
            Vec3 a = ground.add(d.scale(0.4));
            g.flat(a, a.add(d.scale(0.6 + reach * 0.55 * fast)), f.up, 0.12, p.core, alpha, alpha * 0.1F);
        }
        float pillar = Mth.clamp(1.0F - sinceHit / 5.0F, 0.0F, 1.0F);
        if (pillar > 0.0F) {
            g.ribbon(ground, ground.add(0.0, 4.0 * scale, 0.0), 1.1 * scale, 0.2, p.glow, pillar * alpha, 0.0F);
            g.ribbon(ground, ground.add(0.0, 3.0 * scale, 0.0), 0.4 * scale, 0.08, 0xFFFFFF, pillar * alpha, 0.0F);
        }
    }

    /** A turning magic circle in front of the staff (and its flash when it fires). */
    private static void sigil(Glow g, Frame f, Palette p, float sweep, float alpha, float scale, float sinceHit,
                              boolean wide) {
        Vec3 center = f.eye.add(f.look.scale(1.6)).add(0.0, -0.1, 0.0);
        Vec3[] basis = basis(f.look);
        double size = (wide ? 1.15 : 0.95) * scale * (0.35 + 0.65 * Math.min(1.0F, sweep * 1.4F));
        double spin = (sinceHit + 10.0) * 0.18;
        g.ring(center, basis[0], basis[1], size, 0.1, p.glow, alpha);
        g.ring(center, basis[0], basis[1], size * 0.66, 0.07, p.accent, alpha);
        g.star(center, basis[0], basis[1], size * 0.66, 6, spin, 0.06, p.core, alpha);
        g.star(center, basis[0], basis[1], size * 0.95, 5, -spin * 0.6, 0.04, p.accent, alpha * 0.7F);
        if (sinceHit >= 0.0F && sinceHit < 3.0F) {
            g.billboard(center, 0.9 * scale * (1.0F - sinceHit / 3.0F), 0xFFFFFF, alpha);
        }
    }

    /** A magic circle in the sky over the aimed ground, light falling from it. */
    private static void sky(Glow g, Frame f, Palette p, float sweep, float alpha, float scale, double reach,
                            float sinceHit) {
        Vec3 target = f.eye.add(f.look.scale(Math.min(12.0, Math.max(6.0, reach * 3.0))));
        Vec3 center = new Vec3(target.x, Math.max(target.y, f.feet.y) + 6.0, target.z);
        double size = 2.2 * scale * Math.min(1.0F, 0.3F + sweep);
        double spin = (sinceHit + 10.0) * 0.12;
        g.ring(center, f.right, f.fwd, size, 0.2, p.glow, alpha);
        g.ring(center, f.right, f.fwd, size * 0.7, 0.12, p.accent, alpha);
        g.star(center, f.right, f.fwd, size * 0.7, 5, spin, 0.1, p.core, alpha);
        if (sinceHit >= 0.0F) {
            Vec3 ground = new Vec3(center.x, f.feet.y, center.z);
            g.ribbon(center, ground, 1.0 * scale, 0.6 * scale, p.glow, alpha, alpha * 0.6F);
            g.ribbon(center, ground, 0.35 * scale, 0.2 * scale, 0xFFFFFF, alpha, alpha * 0.5F);
        }
        // the look of the wielder's own bow or staff: a ring at the hand
        Vec3 hand = f.eye.add(f.look.scale(1.0)).add(0.0, -0.1, 0.0);
        Vec3[] basis = basis(f.look);
        g.ring(hand, basis[0], basis[1], 0.4 + 0.3 * sweep, 0.08, p.accent, alpha * 0.8F);
    }

    // ------------------------------------------------------------------ the wind-up

    private static void windup(Glow g, Frame f, Palette p, float elapsed, float w, boolean ownView) {
        float alpha = 0.35F + 0.55F * w;
        switch (p.windup()) {
            case GATHER -> {
                if (ownView) {
                    break;
                }
                Vec3 hand = f.hand();
                for (int arm = 0; arm < 3; arm++) {
                    for (int i = 0; i < 5; i++) {
                        double t = (i + 1) / 5.0;
                        double radius = (1.0 - w) * 1.6 * t + 0.15;
                        double angle = elapsed * 0.5 + arm * Math.PI * 2.0 / 3.0 + t * 2.0;
                        Vec3 spot = hand.add(f.right.scale(Math.cos(angle) * radius)).add(0.0,
                                Math.sin(angle) * radius * 0.6, 0.0).add(f.fwd.scale(Math.sin(angle) * radius * 0.5));
                        g.billboard(spot, 0.08 + 0.06 * t, i % 2 == 0 ? p.glow : p.accent, alpha * (float) t);
                    }
                }
            }
            case GLOW -> {
                if (!ownView) {
                    float pulse = 0.8F + 0.2F * Mth.sin(elapsed * 1.3F);
                    g.billboard(f.hand(), (0.25 + 0.45 * w) * pulse, p.glow, alpha);
                    g.billboard(f.hand(), (0.1 + 0.2 * w) * pulse, 0xFFFFFF, alpha);
                }
            }
            case GROUND_SIGIL -> {
                Vec3 ground = f.feet.add(0.0, 0.05, 0.0);
                g.band(ground, f.right, f.fwd, 1.7, 0.14, 90.0, 90.0 + 360.0 * w, 1.0F, alpha, p);
                if (w > 0.4F) {
                    g.ring(ground, f.right, f.fwd, 1.15, 0.08, p.accent, alpha * (w - 0.4F) / 0.6F);
                    g.star(ground, f.right, f.fwd, 1.15, 5, elapsed * 0.05, 0.06, p.core, alpha * (w - 0.4F) / 0.6F);
                }
            }
            case RISING -> {
                for (int i = 0; i < 6; i++) {
                    double angle = Math.PI * 2.0 * i / 6.0 + elapsed * 0.08;
                    Vec3 base = f.feet.add(f.right.scale(Math.cos(angle) * 1.3)).add(f.fwd.scale(Math.sin(angle) * 1.3));
                    g.ribbon(base, base.add(0.0, 0.4 + 2.0 * w, 0.0), 0.14, 0.02, i % 2 == 0 ? p.glow : p.accent,
                            alpha * 0.8F, 0.0F);
                }
            }
            case SPIN_UP -> {
                double head = 90.0 + elapsed * 34.0;
                g.band(f.at(0.0, 0.7 + 0.4 * w, 0.0), f.right, f.fwd, 1.5, 0.22, head - 220.0, head, 1.0F, alpha, p);
            }
            case AIR_SIGIL -> {
                Vec3 center = f.eye.add(f.look.scale(1.3)).add(0.0, -0.1, 0.0);
                Vec3[] basis = basis(f.look);
                g.band(center, basis[0], basis[1], 0.9, 0.08, 90.0, 90.0 + 360.0 * w, 1.0F, alpha, p);
                if (w > 0.5F) {
                    g.star(center, basis[0], basis[1], 0.6, 6, elapsed * 0.1, 0.05, p.accent, alpha * (w - 0.5F) * 2.0F);
                }
            }
        }
    }

    // ------------------------------------------------------------------ colours

    private static Palette palette(String actionId, WeaponType weapon) {
        Optional<ArtLook> look = GeneratedArts.look(actionId);
        if (look.isPresent()) {
            return palette(look.get().rgb(), look.get().accentRgb(), look.get().windup());
        }
        Optional<WeaponArt> boss = WeaponArts.byId(actionId);
        WeaponType type = boss.map(WeaponArt::type).orElse(weapon);
        int rgb = boss.map(art -> WeaponSkillLooks.rgb(type, art.theme())).orElse(WeaponSkillLooks.of(type).rgb());
        int accent = WeaponSkillLooks.of(type).accentRgb();
        ArtLook.Windup windup = switch (type) {
            case STAFF -> ArtLook.Windup.AIR_SIGIL;
            case TWO_HANDED_SWORD -> ArtLook.Windup.RISING;
            case SPEAR, BOW -> ArtLook.Windup.GATHER;
            case DUAL_BLADES -> ArtLook.Windup.SPIN_UP;
            default -> ArtLook.Windup.GLOW;
        };
        return palette(rgb, accent, windup);
    }

    /** Light is added to the scene: dark colours would vanish, so they are lifted towards the accent. */
    private static Palette palette(int rgb, int accent, ArtLook.Windup windup) {
        int glow = brightest(rgb) < 120 ? mix(rgb, brightest(accent) < 120 ? 0xE8E0FF : accent, 0.55F) : rgb;
        int light = brightest(accent) < 120 ? mix(accent, 0xFFFFFF, 0.6F) : accent;
        return new Palette(mix(glow, 0xFFFFFF, 0.55F), glow, light, windup);
    }

    private static int brightest(int rgb) {
        return Math.max((rgb >> 16) & 0xFF, Math.max((rgb >> 8) & 0xFF, rgb & 0xFF));
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ geometry

    private static Vec3 turn(Vec3 dir, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(dir.x * cos - dir.z * sin, dir.y, dir.x * sin + dir.z * cos);
    }

    private static Vec3[] basis(Vec3 dir) {
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
        Vec3 up = Math.abs(d.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 u = d.cross(up).normalize();
        return new Vec3[] {u, u.cross(d).normalize()};
    }

    /** Writes quads of light, relative to the camera. */
    private static final class Glow {
        private final VertexConsumer out;
        private final Matrix4f matrix;
        private final Vec3 camera;

        Glow(VertexConsumer out, Matrix4f matrix, Vec3 camera) {
            this.out = out;
            this.matrix = matrix;
            this.camera = camera;
        }

        /**
         * A crescent in the plane of {@code a} (0°) and {@code b} (90°) around {@code center}, drawn from
         * {@code from} towards {@code to} as far as {@code sweep} (0..1) has come: brightest at its head, fading and
         * thinning towards its tail, with a white core.
         */
        void band(Vec3 center, Vec3 a, Vec3 b, double radius, double width, double from, double to, float sweep,
                  float alpha, Palette p) {
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
                arcQuad(center, a, b, radius, w0, w1, t0, t1, p.glow, al0, al1);
                arcQuad(center, a, b, radius, w0 * 0.3, w1 * 0.3, t0, t1, p.core, al0, al1);
            }
        }

        void ring(Vec3 center, Vec3 a, Vec3 b, double radius, double width, int rgb, float alpha) {
            if (alpha <= 0.01F) {
                return;
            }
            int steps = Math.max(16, (int) (radius * 14));
            for (int i = 0; i < steps; i++) {
                double t0 = Math.PI * 2.0 * i / steps;
                double t1 = Math.PI * 2.0 * (i + 1) / steps;
                arcQuad(center, a, b, radius, width, width, t0, t1, rgb, alpha, alpha);
            }
        }

        /** A star polygon of {@code points} points in the plane of {@code a} and {@code b}. */
        void star(Vec3 center, Vec3 a, Vec3 b, double radius, int points, double spin, double width, int rgb,
                  float alpha) {
            if (alpha <= 0.01F) {
                return;
            }
            Vec3 normal = a.cross(b).normalize();
            int step = 2;
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
            across = across.normalize();
            Vec3 sa = across.scale(widthA / 2.0);
            Vec3 sb = across.scale(widthB / 2.0);
            quad(a.add(sa), a.subtract(sa), b.subtract(sb), b.add(sb), rgb, alphaA, alphaB);
        }

        /** A strip lying in the plane across {@code normal} (cracks on the ground). */
        void flat(Vec3 a, Vec3 b, Vec3 normal, double width, int rgb, float alphaA, float alphaB) {
            Vec3 along = b.subtract(a);
            if (along.lengthSqr() < 1.0E-4) {
                return;
            }
            Vec3 across = along.cross(normal).normalize().scale(width / 2.0);
            quad(a.add(across), a.subtract(across), b.subtract(across.scale(0.3)), b.add(across.scale(0.3)), rgb,
                    alphaA, alphaB);
        }

        /** A square of light facing the camera. */
        void billboard(Vec3 at, double size, int rgb, float alpha) {
            if (alpha <= 0.01F || size <= 0.0) {
                return;
            }
            Vec3 view = at.subtract(camera).normalize();
            Vec3[] basis = basis(view);
            Vec3 u = basis[0].scale(size / 2.0);
            Vec3 v = basis[1].scale(size / 2.0);
            quad(at.add(u).add(v), at.subtract(u).add(v), at.subtract(u).subtract(v), at.add(u).subtract(v), rgb,
                    alpha, alpha);
        }

        private void arcQuad(Vec3 center, Vec3 a, Vec3 b, double radius, double w0, double w1, double t0, double t1,
                             int rgb, float alpha0, float alpha1) {
            Vec3 in0 = point(center, a, b, radius - w0 / 2.0, t0);
            Vec3 out0 = point(center, a, b, radius + w0 / 2.0, t0);
            Vec3 in1 = point(center, a, b, radius - w1 / 2.0, t1);
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
