package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.CombatEvent;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.HitShape;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.item.ArtChoreography;
import com.eternalrealm.item.ArtLook;
import com.eternalrealm.item.GeneratedArts;
import com.eternalrealm.sound.ERSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The composer of the generated weapon arts' effects ({@link GeneratedArts}): every art has an {@link ArtLook}
 * (particles, colours, a primary shape for its hits, an accent shape for its wind-up and impacts, a wind-up style),
 * and this draws it fitted to the art's own hit geometry: arcs, circles, lines, the ground ahead or the launch of a
 * projectile. A readable wind-up before the first hit, a shape and a sound on every hit, a burst on every target, a
 * fading after-image, and the art's ground areas ({@code fx_<family>}). Every hit also draws its strike's own trail
 * ({@link ArtChoreography}: the crescent of a slash, a falling arc, a thrust streak, a ring, a quake...) and the last
 * one lands with a shockwave, so arts of one weapon read differently. For everyone around; purely visual.
 */
public final class ArtFx {
    private static final Map<UUID, Window> WINDOWS = new HashMap<>();
    private static final List<Scheduled> QUEUE = new ArrayList<>();
    private static final Map<String, ParticleOptions> PARTICLES = new HashMap<>();
    private static final int DARK = 0x16121C;

    private record Window(ArtLook look, WeaponType type, long until) {
    }

    private record Scheduled(ServerLevel level, long due, Runnable effect) {
    }

    /** Where a hit lands: the wielder's position and facing and the hit's shape. */
    private record Geometry(Vec3 origin, Vec3 forward, Vec3 look, Vec3 center, double reach, double halfAngle,
                            HitShape.Kind kind, boolean ahead) {
    }

    private ArtFx() {
    }

    public static boolean isGenerated(ActionDefinition action) {
        return GeneratedArts.look(action.id()).isPresent();
    }

    // ------------------------------------------------------------------ hooks of the combat driver

    static void started(ServerPlayer player, WeaponProfile weapon, ActionDefinition action) {
        ArtLook look = GeneratedArts.look(action.id()).orElse(null);
        if (look == null) {
            WINDOWS.remove(player.getUUID());
            return;
        }
        ServerLevel level = player.serverLevel();
        WINDOWS.put(player.getUUID(), new Window(look, weapon.type(), level.getGameTime() + action.durationTicks() + 60L));
        // everyone else hears the wind-up from here; the wielder's own client plays it at once (chargePitch)
        level.playSound(player, player.getX(), player.getY(), player.getZ(),
                ERSounds.weaponSkillCharge(weapon.type()).get(), SoundSource.PLAYERS, 1.0F, pitch(look, 0.9F));
        Vec3 hand = hand(player);
        burst(level, particle(look.accentParticle(), look.accentRgb()), hand, 8, 0.2, 0.02);
        burst(level, ParticleTypes.END_ROD, hand, 4, 0.1, 0.02);
    }

    static void tick(ServerPlayer player, Combatant combatant, long now) {
        ActionDefinition action = combatant.currentAction().orElse(null);
        if (action == null) {
            return;
        }
        ArtLook look = GeneratedArts.look(action.id()).orElse(null);
        if (look == null || action.frames().isEmpty()) {
            return;
        }
        int age = (int) (now - combatant.actionStartTick());
        int windUp = action.frames().get(0).offsetTicks();
        if (age >= 0 && age < windUp) {
            windUp(player, look, age, windUp);
        }
    }

    static void frame(ServerPlayer player, CombatEvent.Hit hit) {
        ArtLook look = GeneratedArts.look(hit.action().id()).orElse(null);
        if (look == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        HitFrame frame = hit.frame();
        int index = Math.max(0, hit.frameIndex());
        boolean last = index >= hit.action().frames().size() - 1;
        if (frame.isProjectile()) {
            launch(player, look, last);
            ArtChoreography.forAction(hit.action().id()).ifPresent(choreo -> strike(player, look,
                    choreo.strike(index).trail(), geometry(player, HitFrame.NO_SHAPE), last, choreo.heavy()));
            return;
        }
        Geometry g = geometry(player, frame.shape());
        ArtChoreography.forAction(hit.action().id()).ifPresent(choreo ->
                strike(player, look, choreo.strike(index).trail(), g, last, choreo.heavy()));
        draw(level, look, look.primary(), g, last ? 1.25 : 1.0, level.getRandom(), false);
        draw(level, look, look.accent(), g, 0.6, level.getRandom(), true);
        sound(level, g.center(), hitSound(look.primary()), last ? 1.3F : 1.0F, pitch(look, 1.0F + 0.05F * index));
        if (last) {
            level.sendParticles(ParticleTypes.FLASH, g.center().x, g.center().y + 0.8, g.center().z, 1, 0.0, 0.0,
                    0.0, 0.0);
        }
        // the after-image: the same shape fading
        schedule(level, 1, () -> draw(level, look, look.primary(), g, 0.85, level.getRandom(), false, true));
        schedule(level, 3, () -> draw(level, look, look.primary(), g, 0.7, level.getRandom(), false, true));
    }

    static void impact(ServerPlayer attacker, LivingEntity target, boolean critical) {
        Window window = WINDOWS.get(attacker.getUUID());
        ServerLevel level = attacker.serverLevel();
        if (window == null || level.getGameTime() > window.until()) {
            return;
        }
        ArtLook look = window.look();
        Vec3 at = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ());
        level.playSound(null, at.x, at.y, at.z, ERSounds.WEAPON_SKILL_IMPACT.get(), SoundSource.PLAYERS, 1.0F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        burst(level, particle(look.particle(), look.rgb()), at, 12, 0.35, 0.12);
        burst(level, particle(look.accentParticle(), look.accentRgb()), at, 8, 0.3, 0.08);
        burst(level, dust(look.rgb(), 1.5F), at, 10, 0.4, 0.0);
        burst(level, ParticleTypes.ENCHANTED_HIT, at, 6, 0.3, 0.3);
        if (critical) {
            level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            burst(level, ParticleTypes.CRIT, at, 12, 0.4, 0.4);
        }
    }

    /** The flight of an arrow shot by an art: a streak in the art's colours. */
    static void arrowTrail(ServerLevel level, Entity arrow, ServerPlayer owner) {
        Window window = WINDOWS.get(owner.getUUID());
        if (window == null) {
            return;
        }
        Vec3 motion = arrow.getDeltaMovement();
        for (int i = 0; i < 3; i++) {
            Vec3 p = arrow.position().subtract(motion.scale(i / 3.0));
            level.sendParticles(dust(window.look().rgb(), 1.2F), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        level.sendParticles(particle(window.look().particle(), window.look().rgb()), arrow.getX(), arrow.getY(),
                arrow.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
    }

    /** Whether the owner's weapon art is out (its arrows get a trail). */
    static boolean hasWindow(ServerPlayer owner) {
        Window window = WINDOWS.get(owner.getUUID());
        return window != null && owner.serverLevel().getGameTime() <= window.until();
    }

    /** A ground area of a generated art appears: its outline. @return whether {@code look} is one of these */
    public static boolean zoneCreated(ServerLevel level, String lookId, Vec3 at, double radius) {
        ArtLook look = GeneratedArts.zoneLook(lookId).orElse(null);
        if (look == null) {
            return false;
        }
        ring(level, dust(look.rgb(), 1.4F), at.x, at.y + 0.15, at.z, radius, 36, 0.0);
        ring(level, particle(look.accentParticle(), look.accentRgb()), at.x, at.y + 0.3, at.z, radius * 0.7, 14, 0.0);
        return true;
    }

    /** A pulse of a generated art's ground area: its shape drawn on the ground. */
    public static boolean zonePulse(ServerLevel level, String lookId, Vec3 at, double radius, int pulse) {
        ArtLook look = GeneratedArts.zoneLook(lookId).orElse(null);
        if (look == null) {
            return false;
        }
        Geometry g = new Geometry(at, new Vec3(Math.cos(pulse), 0.0, Math.sin(pulse)), new Vec3(0.0, 0.0, 1.0), at,
                radius, 180.0, HitShape.Kind.CIRCLE, true);
        draw(level, look, look.primary(), g, 0.8, level.getRandom(), false);
        ring(level, dust(look.rgb(), 1.2F), at.x, at.y + 0.1, at.z, radius, 24, 0.0);
        sound(level, at, ERSounds.WEAPON_SKILL_IMPACT.get(), 0.6F, pitch(look, 0.8F));
        return true;
    }

    static void tickScheduled() {
        if (QUEUE.isEmpty()) {
            return;
        }
        List<Scheduled> due = new ArrayList<>();
        for (Iterator<Scheduled> it = QUEUE.iterator(); it.hasNext(); ) {
            Scheduled scheduled = it.next();
            if (scheduled.level().getGameTime() >= scheduled.due()) {
                due.add(scheduled);
                it.remove();
            }
        }
        for (Scheduled scheduled : due) {
            scheduled.effect().run();
        }
    }

    static void forget(UUID playerId) {
        WINDOWS.remove(playerId);
    }

    // ------------------------------------------------------------------ the strike's own trail

    /**
     * The trail of one strike in particles, in the art's colours: a dense crescent along a slash's path with slash
     * marks, a streak and a shock ring for a thrust, a full ring for a spin, rings and debris for a quake, a circle for
     * a cast, a ring at the bow. The last strike adds a shockwave (bigger for heavy arts).
     */
    private static void strike(ServerPlayer player, ArtLook look, ArtChoreography.Trail trail, Geometry g,
                               boolean last, boolean heavy) {
        ServerLevel level = player.serverLevel();
        ParticleOptions ink = dust(look.rgb(), last ? 2.0F : 1.6F);
        ParticleOptions light = dust(look.accentRgb(), 1.2F);
        ParticleOptions slash = ERParticles.SLASH.get();
        Vec3 fwd = forward(player);
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        if (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT) {
            right = right.scale(-1.0);
        }
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 feet = player.position();
        Vec3 chest = feet.add(0.0, 1.15, 0.0);
        Vec3 lookDir = player.getLookAngle();
        double r = Math.max(1.6, g.reach() * 0.8);
        switch (trail) {
            case ARC_RIGHT -> arc(level, ink, light, slash, chest.add(fwd.scale(0.15)), right.add(0.0, 0.18, 0.0)
                    .normalize(), fwd, r, 155.0, 25.0);
            case ARC_LEFT -> arc(level, ink, light, slash, chest.add(fwd.scale(0.15)), right.add(0.0, -0.18, 0.0)
                    .normalize(), fwd, r, 25.0, 155.0);
            case SWEEP -> {
                arc(level, ink, light, slash, feet.add(0.0, 0.85, 0.0), right, fwd, g.reach() * 0.95, 205.0, -25.0);
                arc(level, light, ink, slash, feet.add(0.0, 0.95, 0.0), right, fwd, g.reach() * 0.6, 205.0, -25.0);
            }
            case RISE -> arc(level, ink, light, slash, feet.add(fwd.scale(0.2)).add(0.0, 1.0, 0.0), up, fwd, r,
                    155.0, -15.0);
            case FALL -> {
                arc(level, ink, light, slash, feet.add(fwd.scale(0.2)).add(0.0, 1.1, 0.0), up, fwd, r, -15.0, 160.0);
                debris(level, player, feet.add(fwd.scale(Math.min(2.6, r))), look, 1.6);
            }
            case DIAG_RIGHT -> arc(level, ink, light, slash, chest.add(fwd.scale(0.2)),
                    up.scale(0.75).subtract(right.scale(0.65)).normalize(), fwd, r, 0.0, 180.0);
            case DIAG_LEFT -> arc(level, ink, light, slash, chest.add(fwd.scale(0.2)),
                    up.scale(0.75).add(right.scale(0.65)).normalize(), fwd, r, 0.0, 180.0);
            case CROSS -> {
                arc(level, ink, light, slash, chest.add(fwd.scale(0.3)),
                        up.scale(0.75).subtract(right.scale(0.65)).normalize(), fwd, r * 0.85, 0.0, 180.0);
                arc(level, light, ink, slash, chest.add(fwd.scale(0.3)),
                        up.scale(0.75).add(right.scale(0.65)).normalize(), fwd, r * 0.85, 0.0, 180.0);
            }
            case STREAK -> {
                Vec3 from = player.getEyePosition().add(0.0, -0.35, 0.0).add(lookDir.scale(0.6));
                double length = g.reach() * 1.05;
                for (double t = 0.0; t <= length; t += 0.25) {
                    Vec3 at = from.add(lookDir.scale(t));
                    level.sendParticles(t > length - 1.0 ? light : ink, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
                }
                Vec3 tip = from.add(lookDir.scale(length));
                ringAcross(level, light, tip, lookDir, 0.9, 18);
                level.sendParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 10, 0.2, 0.2, 0.2, 0.4);
            }
            case RING -> {
                ring(level, ink, feet.x, feet.y + 1.0, feet.z, r, 48, 0.0);
                ring(level, light, feet.x, feet.y + 0.55, feet.z, r * 0.75, 28, 0.0);
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI * 2.0 * i / 8.0;
                    level.sendParticles(slash, feet.x + Math.cos(angle) * r, feet.y + 1.0, feet.z + Math.sin(angle) * r,
                            1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            case QUAKE -> debris(level, player, feet.add(fwd.scale(1.6)), look, heavy ? 3.2 : 2.4);
            case FAN -> {
                for (int ray = -2; ray <= 2; ray++) {
                    Vec3 d = rotate(new Vec3(lookDir.x, 0.0, lookDir.z).normalize(), Math.toRadians(ray * 20.0));
                    Vec3 from = player.getEyePosition().add(0.0, -0.3, 0.0);
                    for (double t = 0.7; t <= 4.5; t += 0.4) {
                        Vec3 at = from.add(d.scale(t));
                        level.sendParticles(ray == 0 ? light : ink, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
            case SIGIL_AHEAD, BEAM -> {
                Vec3 center = player.getEyePosition().add(lookDir.scale(1.6)).add(0.0, -0.1, 0.0);
                ringAcross(level, ink, center, lookDir, 1.0, 28);
                ringAcross(level, light, center, lookDir, 0.65, 18);
                level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 8, 0.2, 0.2, 0.2, 0.05);
                if (trail == ArtChoreography.Trail.BEAM) {
                    for (double t = 0.0; t <= 10.0; t += 0.3) {
                        Vec3 at = center.add(lookDir.scale(t));
                        level.sendParticles(t % 0.9 < 0.3 ? light : ink, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
                    }
                }
            }
            case SIGIL_SKY -> {
                Vec3 target = player.getEyePosition().add(lookDir.scale(10.0));
                double y = Math.max(target.y, feet.y) + 6.0;
                ring(level, ink, target.x, y, target.z, 2.2, 40, 0.0);
                ring(level, light, target.x, y, target.z, 1.5, 28, 0.0);
                level.sendParticles(ParticleTypes.END_ROD, target.x, y, target.z, 14, 1.2, 0.1, 1.2, 0.02);
            }
            case MUZZLE, MUZZLE_HEAVY -> {
                Vec3 bow = player.getEyePosition().add(0.0, -0.12, 0.0).add(lookDir.scale(1.0));
                boolean big = trail == ArtChoreography.Trail.MUZZLE_HEAVY;
                ringAcross(level, ink, bow, lookDir, big ? 1.2 : 0.8, big ? 24 : 16);
                if (big) {
                    schedule(level, 2, () -> ringAcross(level, light, bow.add(lookDir.scale(0.8)), lookDir, 1.6, 26));
                    level.sendParticles(ParticleTypes.EXPLOSION, bow.x, bow.y, bow.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
        if (last) {
            double waveReach = heavy ? 4.5 : 3.0;
            for (int step = 1; step <= 3; step++) {
                double radius = waveReach * step / 3.0;
                ParticleOptions wave = step % 2 == 1 ? light : ink;
                schedule(level, step, () -> ring(level, wave, feet.x, feet.y + 0.2, feet.z, radius,
                        16 + (int) (radius * 6), 0.0));
            }
            level.sendParticles(ParticleTypes.FLASH, chest.x, chest.y, chest.z, 1, 0.0, 0.0, 0.0, 0.0);
            if (heavy) {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, feet.x, feet.y + 0.5, feet.z, 1, 0.0, 0.0, 0.0,
                        0.0);
            }
        }
    }

    /** Points along an arc in the plane of {@code a} (0°) and {@code b} (90°), slash marks along it. */
    private static void arc(ServerLevel level, ParticleOptions ink, ParticleOptions light, ParticleOptions slash,
                            Vec3 center, Vec3 a, Vec3 b, double radius, double from, double to) {
        int points = Math.max(16, (int) (Math.abs(to - from) / 6.0));
        for (int i = 0; i <= points; i++) {
            double u = i / (double) points;
            double angle = Math.toRadians(from + (to - from) * u);
            Vec3 p = center.add(a.scale(Math.cos(angle) * radius)).add(b.scale(Math.sin(angle) * radius));
            level.sendParticles(u > 0.75 ? light : ink, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 5 == 2) {
                level.sendParticles(slash, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        double end = Math.toRadians(to);
        Vec3 tip = center.add(a.scale(Math.cos(end) * radius)).add(b.scale(Math.sin(end) * radius));
        level.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 6, 0.1, 0.1, 0.1, 0.06);
    }

    /** A ring of points across a direction (around a thrust's tip, at a bow, a cast circle). */
    private static void ringAcross(ServerLevel level, ParticleOptions particle, Vec3 center, Vec3 dir, double radius,
                                   int count) {
        Vec3[] basis = basis(dir);
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            Vec3 p = center.add(basis[0].scale(Math.cos(angle) * radius)).add(basis[1].scale(Math.sin(angle) * radius));
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Where something heavy lands: rings, the ground's own crumbs thrown up, cracks of light. */
    private static void debris(ServerLevel level, ServerPlayer player, Vec3 at, ArtLook look, double radius) {
        net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(at.x, player.getY() - 0.5, at.z);
        net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(below);
        double y = player.getY() + 0.1;
        ring(level, dust(look.rgb(), 1.8F), at.x, y, at.z, radius * 0.5, 24, 0.0);
        ring(level, dust(look.accentRgb(), 1.3F), at.x, y + 0.05, at.z, radius, 36, 0.0);
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, y + 0.2, at.z, 30,
                    radius * 0.4, 0.2, radius * 0.4, 0.25);
        }
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0 + 0.2;
            for (double t = 0.4; t <= radius; t += 0.35) {
                level.sendParticles(dust(look.rgb(), 1.0F), at.x + Math.cos(angle) * t, y, at.z + Math.sin(angle) * t, 1,
                        0.0, 0.0, 0.0, 0.0);
            }
        }
        for (int h = 0; h < 10; h++) {
            level.sendParticles(h % 2 == 0 ? ParticleTypes.END_ROD : dust(look.rgb(), 1.6F), at.x, y + h * 0.4, at.z, 1,
                    0.05, 0.0, 0.05, 0.0);
        }
    }

    // ------------------------------------------------------------------ wind-up and launch

    private static void windUp(ServerPlayer player, ArtLook look, int age, int windUp) {
        ServerLevel level = player.serverLevel();
        double progress = (age + 1.0) / Math.max(1, windUp);
        Vec3 feet = player.position();
        Vec3 fwd = forward(player);
        ParticleOptions main = particle(look.particle(), look.rgb());
        ParticleOptions accent = particle(look.accentParticle(), look.accentRgb());
        switch (look.windup()) {
            case GATHER -> {
                double radius = Math.max(0.4, 2.6 * (1.0 - progress));
                for (int k = 0; k < 3; k++) {
                    double angle = age * 0.9 + k * Math.PI * 2.0 / 3.0;
                    level.sendParticles(k == 0 ? main : dust(look.rgb(), 1.1F), feet.x + Math.cos(angle) * radius,
                            feet.y + 0.9 + 0.15 * k, feet.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            case GLOW -> burst(level, age % 2 == 0 ? accent : dust(look.accentRgb(), (float) (0.8 + progress)),
                    hand(player), 1 + (int) (progress * 4), 0.15, 0.01);
            case GROUND_SIGIL -> {
                int points = 24;
                int drawn = (int) Math.ceil(points * progress);
                for (int i = 0; i < drawn; i++) {
                    double angle = Math.PI * 2.0 * i / points;
                    level.sendParticles(dust(look.rgb(), 0.9F), feet.x + Math.cos(angle) * 1.6, feet.y + 0.1,
                            feet.z + Math.sin(angle) * 1.6, 1, 0.0, 0.0, 0.0, 0.0);
                }
                if (age % 3 == 0) {
                    burst(level, accent, feet.add(0.0, 0.2, 0.0), 2, 0.6, 0.01);
                }
            }
            case RISING -> {
                for (int k = 0; k < 3; k++) {
                    double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
                    double r = 0.5 + level.getRandom().nextDouble() * 1.3;
                    level.sendParticles(k == 0 ? main : dust(look.rgb(), 1.2F), feet.x + Math.cos(angle) * r,
                            feet.y + 0.1, feet.z + Math.sin(angle) * r, 0, 0.0, 1.0, 0.0, 0.1);
                }
            }
            case SPIN_UP -> {
                double spin = age * 0.7;
                for (int k = 0; k < 6; k++) {
                    double angle = spin + k * Math.PI / 3.0;
                    level.sendParticles(k % 2 == 0 ? main : dust(look.rgb(), 1.0F), feet.x + Math.cos(angle) * 1.5,
                            feet.y + 0.6 + 0.3 * progress, feet.z + Math.sin(angle) * 1.5, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            case AIR_SIGIL -> {
                Vec3 dir = player.getLookAngle();
                Vec3 center = player.getEyePosition().add(dir.scale(1.1));
                Vec3[] basis = basis(dir);
                int points = 16;
                int drawn = (int) Math.ceil(points * progress);
                for (int i = 0; i < drawn; i++) {
                    double angle = age * 0.2 + Math.PI * 2.0 * i / points;
                    Vec3 p = center.add(basis[0].scale(Math.cos(angle) * 0.9)).add(basis[1].scale(Math.sin(angle) * 0.9));
                    level.sendParticles(dust(look.rgb(), 0.9F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
                burst(level, accent, center, 1, 0.3, 0.01);
            }
        }
        if (age == 0) {
            // a faint preview of where the art reaches
            burst(level, main, feet.add(fwd.scale(1.5)).add(0.0, 0.5, 0.0), 3, 0.4, 0.01);
        }
    }

    private static void launch(ServerPlayer player, ArtLook look, boolean last) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = player.getLookAngle();
        Vec3 muzzle = player.getEyePosition().add(0.0, -0.15, 0.0).add(dir.scale(1.2));
        Vec3[] basis = basis(dir);
        sound(level, muzzle, ERSounds.WEAPON_SKILL_SIGIL_LAUNCH.get(), last ? 1.2F : 0.9F, pitch(look, 1.1F));
        for (int k = 0; k < 16; k++) {
            double angle = k * Math.PI / 8.0;
            Vec3 out = basis[0].scale(Math.cos(angle)).add(basis[1].scale(Math.sin(angle)));
            Vec3 p = muzzle.add(out.scale(0.8));
            level.sendParticles(k % 2 == 0 ? dust(look.rgb(), 1.3F) : particle(look.accentParticle(), look.accentRgb()),
                    p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        burst(level, particle(look.particle(), look.rgb()), muzzle, 6, 0.2, 0.05);
        if (last) {
            level.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // ------------------------------------------------------------------ the shapes

    private static Geometry geometry(ServerPlayer player, HitShape shape) {
        Vec3 origin = player.position();
        Vec3 fwd = forward(player);
        Vec3 look = player.getLookAngle();
        boolean ahead = shape.offsetForward() > 0.0;
        Vec3 center = ahead ? origin.add(fwd.scale(shape.offsetForward())) : origin;
        if (shape.kind() == HitShape.Kind.LINE) {
            center = player.getEyePosition().add(0.0, -0.3, 0.0).add(look.scale(shape.reach() * 0.5));
        } else if (shape.kind() == HitShape.Kind.ARC && !ahead) {
            center = origin.add(fwd.scale(shape.reach() * 0.6));
        }
        return new Geometry(origin, fwd, look, center, shape.reach(), shape.halfAngleDegrees(), shape.kind(), ahead);
    }

    private static void draw(ServerLevel level, ArtLook look, ArtLook.Shape shape, Geometry g, double scale,
                             RandomSource random, boolean accent) {
        draw(level, look, shape, g, scale, random, accent, false);
    }

    private static void draw(ServerLevel level, ArtLook look, ArtLook.Shape shape, Geometry g, double scale,
                             RandomSource random, boolean accent, boolean fading) {
        int rgb = accent ? look.accentRgb() : look.rgb();
        ParticleOptions main = fading ? fade(rgb, 1.2F) : particle(accent ? look.accentParticle() : look.particle(), rgb);
        ParticleOptions tint = fading ? fade(rgb, 1.0F) : dust(rgb, (float) (1.1 * scale));
        double r = Math.max(1.0, g.reach() * (accent ? 0.6 : 1.0));
        Vec3 c = g.center();
        double y = g.origin().y + 1.0;
        int n = (int) Math.round((accent ? 10 : 22) * scale);
        switch (shape) {
            case RING -> {
                ring(level, tint, c.x, y, c.z, g.kind() == HitShape.Kind.CIRCLE ? r : r * 0.6, n * 2, 0.0);
                ring(level, main, c.x, y + 0.2, c.z, (g.kind() == HitShape.Kind.CIRCLE ? r : r * 0.6) * 0.8, n, 0.0);
            }
            case BURST -> {
                for (int k = 0; k < 10; k++) {
                    double angle = k * Math.PI / 5.0;
                    level.sendParticles(k % 2 == 0 ? main : tint, c.x, y, c.z, 0, Math.cos(angle), 0.15,
                            Math.sin(angle), 0.35 * scale);
                }
                burst(level, main, new Vec3(c.x, y, c.z), n, r * 0.25, 0.05);
            }
            case CRESCENT -> arcPoints(level, main, tint, g, r, 75.0, n * 2, 0.9, scale);
            case ARC -> {
                arcPoints(level, tint, tint, g, r * 0.95, g.halfAngle(), n * 2, 0.3, scale);
                arcPoints(level, main, main, g, r * 0.75, g.halfAngle(), n, 0.0, scale);
            }
            case LINE -> line(level, main, tint, g, n * 2, 0.0, 0.0);
            case HELIX -> line(level, main, tint, g, n * 3, 0.45, 2.6);
            case CHAIN -> line(level, main, tint, g, n * 2, 0.25, 6.0);
            case SPIKES -> {
                List<Vec3> spots = spots(g, r, 8 + (int) (4 * scale), random);
                for (Vec3 spot : spots) {
                    for (int h = 0; h < 5; h++) {
                        level.sendParticles(h < 3 ? main : tint, spot.x, g.origin().y + 0.1 + h * 0.35, spot.z, 1,
                                0.04, 0.0, 0.04, 0.0);
                    }
                }
            }
            case PILLAR -> {
                for (int h = 0; h < 14; h++) {
                    double py = g.origin().y + h * 0.35;
                    level.sendParticles(h % 2 == 0 ? main : tint, c.x, py, c.z, 2, 0.25, 0.05, 0.25, 0.0);
                }
                ring(level, tint, c.x, g.origin().y + 0.1, c.z, Math.min(2.5, r * 0.5), 18, 0.0);
            }
            case RAIN -> {
                for (int k = 0; k < n; k++) {
                    Vec3 spot = inside(random, c, r);
                    level.sendParticles(k % 2 == 0 ? main : tint, spot.x, g.origin().y + 4.0, spot.z, 0, 0.0, -1.0,
                            0.0, 0.6);
                }
            }
            case WAVE -> {
                for (int step = 0; step < 3; step++) {
                    double rr = r * (step + 1) / 3.0;
                    int count = 10 + step * 6;
                    ParticleOptions ink = step % 2 == 0 ? main : tint;
                    schedule(level, step, () -> {
                        if (g.kind() == HitShape.Kind.CIRCLE) {
                            ring(level, ink, g.origin().x, g.origin().y + 0.15, g.origin().z, rr, count, 0.0);
                        } else {
                            Geometry front = new Geometry(g.origin(), g.forward(), g.look(), g.origin(), rr, 70.0,
                                    HitShape.Kind.ARC, false);
                            arcPoints(level, ink, tint, front, rr, 70.0, count, -0.85, 1.0);
                        }
                    });
                }
            }
            case FAN -> {
                double half = Math.max(30.0, Math.min(80.0, g.halfAngle()));
                for (int ray = 0; ray < 5; ray++) {
                    double angle = Math.toRadians(-half + 2.0 * half * ray / 4.0);
                    Vec3 dir = rotate(g.forward(), angle);
                    for (double t = 0.8; t <= r; t += 0.5) {
                        Vec3 p = g.origin().add(dir.scale(t));
                        level.sendParticles(ray % 2 == 0 ? main : tint, p.x, y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
            case SPIRAL -> {
                for (int i = 0; i < n * 2; i++) {
                    double t = i / (double) (n * 2);
                    double angle = t * Math.PI * 5.0;
                    double rr = 0.4 + t * r;
                    level.sendParticles(i % 3 == 0 ? main : tint, c.x + Math.cos(angle) * rr, y + t * 1.2,
                            c.z + Math.sin(angle) * rr, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            case VORTEX -> {
                for (int i = 0; i < n * 2; i++) {
                    double angle = i * 0.6;
                    double rr = r * (1.0 - i / (double) (n * 2)) + 0.3;
                    double px = c.x + Math.cos(angle) * rr;
                    double pz = c.z + Math.sin(angle) * rr;
                    level.sendParticles(i % 2 == 0 ? main : tint, px, g.origin().y + 0.3 + i * 0.04, pz, 0,
                            c.x - px, 0.2, c.z - pz, 0.12);
                }
            }
            case STAR -> star(level, main, tint, c, Math.min(r, 3.5), g.origin().y + 0.15, random.nextDouble());
            case SIGIL -> {
                ring(level, tint, c.x, g.origin().y + 0.12, c.z, Math.min(r, 3.2), 32, 0.0);
                ring(level, tint, c.x, g.origin().y + 0.12, c.z, Math.min(r, 3.2) * 0.6, 20, 0.0);
                star(level, main, main, c, Math.min(r, 3.2) * 0.6, g.origin().y + 0.12, 0.0);
            }
            case CROSS -> {
                for (int arm = 0; arm < 4; arm++) {
                    Vec3 dir = rotate(g.forward(), Math.PI / 4.0 + arm * Math.PI / 2.0);
                    for (double t = 0.0; t <= r * 0.8; t += 0.3) {
                        Vec3 p = c.add(dir.scale(t));
                        level.sendParticles(arm % 2 == 0 ? main : tint, p.x, y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
            case PETALS -> {
                for (int petal = 0; petal < 6; petal++) {
                    double base = petal * Math.PI / 3.0;
                    for (int i = 0; i <= 8; i++) {
                        double t = i / 8.0;
                        double rr = Math.sin(Math.PI * t) * r * 0.6;
                        double angle = base + (t - 0.5) * 0.8;
                        level.sendParticles(i % 2 == 0 ? main : tint, c.x + Math.cos(angle) * rr, y - 0.4,
                                c.z + Math.sin(angle) * rr, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
            case ORBS -> {
                for (int orb = 0; orb < 4; orb++) {
                    double angle = orb * Math.PI / 2.0 + random.nextDouble() * 0.5;
                    Vec3 at = c.add(Math.cos(angle) * r * 0.5, 0.6 + orb * 0.15, Math.sin(angle) * r * 0.5);
                    burst(level, main, at, 5, 0.18, 0.0);
                    burst(level, tint, at, 4, 0.12, 0.0);
                }
            }
            case DOME -> {
                for (int i = 0; i < n * 2; i++) {
                    double theta = random.nextDouble() * Math.PI * 2.0;
                    double phi = random.nextDouble() * Math.PI / 2.0;
                    double rr = Math.min(r, 3.5);
                    level.sendParticles(i % 2 == 0 ? main : tint, c.x + Math.cos(theta) * Math.cos(phi) * rr,
                            g.origin().y + Math.sin(phi) * rr, c.z + Math.sin(theta) * Math.cos(phi) * rr, 1, 0.0,
                            0.0, 0.0, 0.0);
                }
            }
            case COMET -> {
                Vec3 start = g.origin().add(0.0, 1.0, 0.0);
                for (double t = 0.5; t <= r; t += 0.35) {
                    Vec3 p = start.add(g.look().scale(t));
                    double width = 0.05 + 0.25 * t / r;
                    level.sendParticles(t > r - 1.0 ? main : tint, p.x, p.y, p.z, 2, width, width, width, 0.0);
                }
                Vec3 head = start.add(g.look().scale(r));
                burst(level, main, head, 10, 0.35, 0.08);
            }
            case SCATTER -> {
                for (int k = 0; k < n * 2; k++) {
                    Vec3 spot = inside(random, c, r);
                    level.sendParticles(k % 2 == 0 ? main : tint, spot.x, y - 0.5 + random.nextDouble(), spot.z, 1,
                            0.0, 0.0, 0.0, 0.0);
                }
            }
            case GEYSER -> {
                for (Vec3 spot : spots(g, r, 4, random)) {
                    level.sendParticles(main, spot.x, g.origin().y + 0.2, spot.z, 0, 0.0, 1.0, 0.0, 0.7);
                    level.sendParticles(tint, spot.x, g.origin().y + 0.2, spot.z, 6, 0.15, 0.6, 0.15, 0.05);
                }
            }
            case CAGE -> {
                double rr = Math.min(r, 3.0);
                for (int bar = 0; bar < 10; bar++) {
                    double angle = bar * Math.PI / 5.0;
                    for (int h = 0; h < 6; h++) {
                        level.sendParticles(h % 2 == 0 ? main : tint, c.x + Math.cos(angle) * rr,
                                g.origin().y + 0.2 + h * 0.4, c.z + Math.sin(angle) * rr, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
                ring(level, tint, c.x, g.origin().y + 2.6, c.z, rr, 20, 0.0);
            }
        }
    }

    private static void arcPoints(ServerLevel level, ParticleOptions main, ParticleOptions tint, Geometry g, double r,
                                  double halfAngle, int count, double tilt, double scale) {
        double half = Math.toRadians(Math.max(20.0, Math.min(150.0, halfAngle)));
        for (int i = 0; i < count; i++) {
            double u = i / (double) Math.max(1, count - 1);
            double angle = -half + 2.0 * half * u;
            Vec3 dir = rotate(g.forward(), angle);
            Vec3 p = g.origin().add(dir.scale(r)).add(0.0, 1.0 + (0.5 - u) * tilt, 0.0);
            level.sendParticles(i % 3 == 0 ? main : tint, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void line(ServerLevel level, ParticleOptions main, ParticleOptions tint, Geometry g, int count,
                             double radius, double turns) {
        Vec3 start = g.origin().add(0.0, 1.3, 0.0);
        Vec3[] basis = basis(g.look());
        double length = g.reach();
        for (int i = 0; i < count; i++) {
            double t = 0.6 + (length - 0.6) * i / (double) Math.max(1, count - 1);
            Vec3 p = start.add(g.look().scale(t));
            if (radius > 0.0) {
                double angle = t * turns;
                p = p.add(basis[0].scale(Math.cos(angle) * radius)).add(basis[1].scale(Math.sin(angle) * radius));
            }
            level.sendParticles(i % 3 == 0 ? main : tint, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void star(ServerLevel level, ParticleOptions main, ParticleOptions tint, Vec3 c, double radius,
                             double y, double spin) {
        Vec3[] points = new Vec3[5];
        for (int k = 0; k < 5; k++) {
            double angle = spin + Math.PI / 2.0 + k * Math.PI * 2.0 / 5.0;
            points[k] = new Vec3(c.x + Math.cos(angle) * radius, y, c.z + Math.sin(angle) * radius);
        }
        for (int e = 0; e < 5; e++) {
            Vec3 from = points[(e * 2) % 5];
            Vec3 to = points[(e * 2 + 2) % 5];
            for (int i = 0; i <= 7; i++) {
                Vec3 p = from.add(to.subtract(from).scale(i / 7.0));
                level.sendParticles(i % 2 == 0 ? main : tint, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Spots spread over the hit area: around a circle, along a line, across an arc. */
    private static List<Vec3> spots(Geometry g, double r, int count, RandomSource random) {
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (g.kind() == HitShape.Kind.LINE) {
                double t = 0.8 + (r - 0.8) * (i + random.nextDouble() * 0.5) / count;
                out.add(g.origin().add(g.forward().scale(t)).add((random.nextDouble() - 0.5) * 0.8, 0.0,
                        (random.nextDouble() - 0.5) * 0.8));
            } else if (g.kind() == HitShape.Kind.ARC && !g.ahead()) {
                double angle = Math.toRadians((random.nextDouble() * 2.0 - 1.0) * Math.min(90.0, g.halfAngle()));
                out.add(g.origin().add(rotate(g.forward(), angle).scale(1.0 + random.nextDouble() * (r - 1.0))));
            } else {
                out.add(inside(random, g.center(), r));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ particles, sounds, helpers

    /** A particle of an {@link ArtLook} spec (cached); dust of {@code fallbackRgb} for anything unknown. */
    static ParticleOptions particle(String spec, int fallbackRgb) {
        ParticleOptions cached = PARTICLES.get(spec);
        if (cached != null) {
            return cached;
        }
        ParticleOptions made = resolve(spec);
        if (made == null) {
            made = dust(fallbackRgb, 1.3F);
        }
        PARTICLES.put(spec, made);
        return made;
    }

    private static ParticleOptions resolve(String spec) {
        int colon = spec.indexOf(':');
        String kind = spec.substring(0, colon);
        String rest = spec.substring(colon + 1);
        switch (kind) {
            case "dust" -> {
                return dust(Integer.parseInt(rest, 16), 1.3F);
            }
            case "v" -> {
                ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(ResourceLocation.tryParse("minecraft:"
                        + rest));
                return type instanceof SimpleParticleType simple ? simple : null;
            }
            case "item" -> {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(rest));
                return item == null ? null : new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(item));
            }
            case "block" -> {
                Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(rest));
                return block == null ? null : new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState());
            }
            case "er" -> {
                return switch (rest) {
                    case "slash" -> ERParticles.SLASH.get();
                    case "rune" -> ERParticles.RUNE.get();
                    case "spark" -> ERParticles.SPARK.get();
                    case "ember" -> ERParticles.EMBER.get();
                    case "frost" -> ERParticles.FROST.get();
                    case "holy" -> ERParticles.HOLY.get();
                    case "shadow" -> ERParticles.SHADOW.get();
                    case "heal" -> ERParticles.HEAL.get();
                    default -> null;
                };
            }
            default -> {
                return null;
            }
        }
    }

    /** The sound of a hit: by what the art draws. */
    private static SoundEvent hitSound(ArtLook.Shape shape) {
        return switch (shape) {
            case RING, VORTEX, SPIRAL -> ERSounds.WEAPON_SKILL_WHIRL.get();
            case SPIKES, WAVE, GEYSER, DOME -> ERSounds.WEAPON_SKILL_QUAKE.get();
            case CRESCENT, ARC, SCATTER, CROSS, FAN -> ERSounds.WEAPON_SKILL_DANCE.get();
            case LINE, HELIX, CHAIN, COMET -> ERSounds.WEAPON_SKILL_DRILL.get();
            default -> ERSounds.WEAPON_SKILL_SIGIL_BURST.get();
        };
    }

    /** Every art sounds a little different: its pitch follows its colour. */
    /** The pitch of an art's wind-up sound (the acting client predicts it). */
    public static float chargePitch(String artId) {
        return GeneratedArts.look(artId).map(look -> pitch(look, 0.9F)).orElse(1.0F);
    }

    private static float pitch(ArtLook look, float base) {
        float shift = ((look.rgb() >> 4) & 0x7) / 7.0F - 0.5F;
        return Math.max(0.5F, Math.min(2.0F, base + shift * 0.2F));
    }

    private static void schedule(ServerLevel level, int delay, Runnable effect) {
        if (delay <= 0) {
            effect.run();
            return;
        }
        if (QUEUE.size() < 4096) {
            QUEUE.add(new Scheduled(level, level.getGameTime() + delay, effect));
        }
    }

    private static Vec3 forward(ServerPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    private static Vec3 rotate(Vec3 dir, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(dir.x * cos - dir.z * sin, 0.0, dir.x * sin + dir.z * cos);
    }

    private static Vec3[] basis(Vec3 dir) {
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
        Vec3 up = Math.abs(d.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 u = d.cross(up).normalize();
        return new Vec3[] {u, u.cross(d).normalize()};
    }

    private static Vec3 hand(ServerPlayer player) {
        Vec3 fwd = forward(player);
        return player.position().add(0.0, 1.2, 0.0).add(fwd.scale(0.4)).add(-fwd.z * 0.35, 0.0, fwd.x * 0.35);
    }

    private static Vec3 inside(RandomSource random, Vec3 center, double radius) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt(random.nextDouble()) * radius;
        return new Vec3(center.x + Math.cos(angle) * r, center.y, center.z + Math.sin(angle) * r);
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void burst(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread,
                              double speed) {
        level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    private static void ring(ServerLevel level, ParticleOptions particle, double x, double y, double z, double radius,
                             int count, double speed) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            level.sendParticles(particle, x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius, 1, 0.0, 0.0,
                    0.0, speed);
        }
    }

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(color(rgb), Math.max(0.2F, Math.min(4.0F, size)));
    }

    private static DustColorTransitionOptions fade(int rgb, float size) {
        return new DustColorTransitionOptions(color(rgb), color(DARK), Math.max(0.2F, Math.min(4.0F, size)));
    }

    private static Vector3f color(int rgb) {
        return new Vector3f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
    }
}
