package com.eternalrealm.boss.forge;

import com.eternalrealm.boss.AbilityKind;
import com.eternalrealm.boss.AbilityLook;
import com.eternalrealm.boss.BossShape;
import com.eternalrealm.boss.DefeatSequence;
import com.eternalrealm.boss.Point;
import com.eternalrealm.combat.forge.ERParticles;
import com.eternalrealm.entity.ERBoss;
import com.eternalrealm.entity.RigData;
import com.eternalrealm.sound.ERSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * What players see and hear of boss attacks besides the ground markers: themed particles, props
 * ({@link BossProps}), visual-only lightning, beams, shockwaves and the transformation and collapse effects. Purely
 * cosmetic: who is hit is decided by the shapes alone. Server side; particles reach players within 32 blocks, the big
 * ones (pillars of light) everyone in the fight.
 */
final class BossVfx {
    /** Runs {@code action} {@code delay} ticks later (owned by the fight's scheduler). */
    @FunctionalInterface
    interface Scheduler {
        void later(int delay, String owner, Runnable action);
    }

    /** Colours and particles of one boss. */
    private record Theme(ParticleOptions main, ParticleOptions accent, int rgb, BlockState debris) {
    }

    private static final int MAX_SPOTS = 16;
    private static final int MAX_LIGHTNING = 3;

    private final ServerLevel level;
    private final Mob body;
    private final BossProps props;
    private final Scheduler scheduler;
    private final Theme theme;
    private final SplittableRandom random = new SplittableRandom();
    /** Sounds, shakes and lightning already played this tick (a wave of many shapes plays them once). */
    private final java.util.Set<SoundEvent> soundsThisTick = new java.util.HashSet<>();
    private long effectTick = -1;
    private boolean shookThisTick;
    private int lightningThisTick;

    BossVfx(ServerLevel level, Mob body, String bossId, BossProps props, Scheduler scheduler) {
        this.level = level;
        this.body = body;
        this.props = props;
        this.scheduler = scheduler;
        this.theme = theme(bossId);
    }

    private static Theme theme(String bossId) {
        return switch (bossId) {
            case "b1" -> new Theme(ParticleTypes.SMOKE, ParticleTypes.CRIT, 0xE8B048, Blocks.COBBLESTONE.defaultBlockState());
            case "b2" -> new Theme(ParticleTypes.HAPPY_VILLAGER, ParticleTypes.SPORE_BLOSSOM_AIR, 0x9BE05A,
                    Blocks.MOSS_BLOCK.defaultBlockState());
            case "b3" -> new Theme(ParticleTypes.WHITE_ASH, new BlockParticleOption(ParticleTypes.BLOCK,
                    Blocks.SAND.defaultBlockState()), 0xF0C060, Blocks.SANDSTONE.defaultBlockState());
            case "b4" -> new Theme(ParticleTypes.SNOWFLAKE, ERParticles.FROST.get(), 0x7FD8FF,
                    Blocks.PACKED_ICE.defaultBlockState());
            case "b5" -> new Theme(ParticleTypes.SNOWFLAKE, ERParticles.FROST.get(), 0x9FF0FF,
                    Blocks.BLUE_ICE.defaultBlockState());
            case "b6" -> new Theme(ParticleTypes.FLAME, ParticleTypes.LAVA, 0xFF7A20, Blocks.MAGMA_BLOCK.defaultBlockState());
            case "b7" -> new Theme(ParticleTypes.ELECTRIC_SPARK, ParticleTypes.END_ROD, 0x5AE6FF,
                    Blocks.IRON_BLOCK.defaultBlockState());
            case "b8" -> new Theme(ParticleTypes.SCULK_SOUL, ERParticles.SHADOW.get(), 0x4AE8D8,
                    Blocks.OBSIDIAN.defaultBlockState());
            default -> new Theme(ParticleTypes.REVERSE_PORTAL, ERParticles.SHADOW.get(), 0xC060FF,
                    Blocks.CRYING_OBSIDIAN.defaultBlockState());
        };
    }

    /** Theme colour of the boss (0xRRGGBB). */
    int rgb() {
        return theme.rgb();
    }

    // ------------------------------------------------------------------ attacks

    /** A marker appeared: props that wait for the hit (meteors, orbs, bombs) and a rune circle for magic. */
    void telegraphed(String owner, AbilityLook look, AbilityKind hitKind, BossShape shape, long now, long fireTick) {
        switch (look.effect()) {
            case METEOR -> {
                for (Vec3 spot : centers(shape)) {
                    props.meteor(owner, BossProps.state(look.block()), spot.x, spot.y, spot.z, size(shape, 1.6F), now,
                            fireTick);
                }
            }
            case ORB -> {
                for (Vec3 spot : centers(shape)) {
                    props.orb(owner, BossProps.state(look.block()), spot.x, spot.y, spot.z, 0.9F, now, fireTick);
                }
            }
            case BOMB -> {
                for (Vec3 spot : centers(shape)) {
                    props.bomb(owner, BossProps.state(look.block()), spot.x, spot.y, spot.z, 0.7F, now, fireTick);
                }
            }
            default -> {
            }
        }
        if (look.styleFor(hitKind) == com.eternalrealm.combat.Telegraph.Style.MAGIC
                && !(shape instanceof BossShape.Arena || shape instanceof BossShape.Outside)) {
            for (Vec3 point : outline(shape, 1.2)) {
                level.sendParticles(ERParticles.RUNE.get(), point.x, point.y + 0.15, point.z, 1, 0.05, 0.02, 0.05, 0.0);
            }
        }
    }

    /** A green safe circle appeared: a pillar of soft light marks it until the hit. */
    void safeSpot(String owner, BossShape shape, int ticks) {
        for (int delay = 0; delay < ticks; delay += 5) {
            scheduler.later(delay, owner, () -> {
                for (Vec3 spot : centers(shape)) {
                    for (int i = 0; i < 6; i++) {
                        level.sendParticles(ParticleTypes.END_ROD, spot.x, spot.y + 0.3 + i * 0.6, spot.z, 1, 0.15, 0.1,
                                0.15, 0.01);
                    }
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.x, spot.y + 0.2, spot.z, 4, 1.2, 0.05, 1.2, 0.0);
                }
            });
        }
    }

    /** The hit landed on {@code shape}. */
    void hit(String owner, AbilityLook look, AbilityKind hitKind, BossShape shape, List<ServerPlayer> audience) {
        if (hitKind instanceof AbilityKind.Knockback knockback) {
            if (knockback.pulls()) {
                pull(shape.anchor(), knockback.radius());
            } else {
                shockwave(owner, shape.anchor(), knockback.radius(), audience);
            }
            if (look.effect() != AbilityLook.Effect.SHOCKWAVE) {
                return;
            }
        }
        List<Vec3> spots = spots(shape);
        switch (look.effect()) {
            case SLASH -> {
                for (Vec3 spot : spots) {
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, spot.x, spot.y + 0.8, spot.z, 1, 0.2, 0.1, 0.2, 0.0);
                    level.sendParticles(ParticleTypes.CRIT, spot.x, spot.y + 0.6, spot.z, 3, 0.4, 0.2, 0.4, 0.1);
                }
                sound(shape.anchor(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.7F);
                sound(shape.anchor(), ERSounds.SKILL_HEAVY_SLASH.get(), 1.0F, 0.8F);
            }
            case SLAM -> {
                BlockParticleOption floor = new BlockParticleOption(ParticleTypes.BLOCK, floorBlock(shape.anchor()));
                for (Vec3 spot : spots) {
                    level.sendParticles(floor, spot.x, spot.y + 0.1, spot.z, 8, 0.6, 0.1, 0.6, 0.15);
                    level.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.2, spot.z, 2, 0.5, 0.05, 0.5, 0.02);
                }
                explosionAt(shape.anchor(), 1);
                // the floor breaks where the body's blow lands: cracks and chunks as big as the boss
                impact(owner, landing(shape), floorBlock(shape.anchor()));
                sound(shape.anchor(), heavySound(), 1.4F + bodySize() * 0.2F, 0.8F);
                shake(near(audience, shape.anchor(), 16.0 + bodySize() * 4.0), 0.5F + bodySize() * 0.15F, 8);
            }
            case EXPLOSION, BOMB -> {
                for (Vec3 center : centers(shape)) {
                    level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.6, center.z, 2, 0.5, 0.3, 0.5, 0.0);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 0.4, center.z, 6, 0.6, 0.3, 0.6, 0.02);
                    level.sendParticles(theme.main(), center.x, center.y + 0.5, center.z, 10, 0.8, 0.4, 0.8, 0.05);
                }
                sound(shape.anchor(), SoundEvents.GENERIC_EXPLODE, 1.0F, 1.1F);
            }
            case SPIKES -> {
                BlockState state = BossProps.state(look.block());
                BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, state);
                long now = level.getGameTime();
                float width = shape instanceof BossShape.Square square ? (float) Math.min(2.4, square.halfSize() * 1.1)
                        : 1.0F;
                for (Vec3 spot : spots) {
                    float height = shape instanceof BossShape.Square ? 2.2F + (float) random.nextDouble() * 0.8F
                            : 1.3F + (float) random.nextDouble() * 1.0F;
                    props.spike(owner, state, spot.x, spot.y, spot.z, width, height, now, 12, true);
                    level.sendParticles(debris, spot.x, spot.y + 0.3, spot.z, 6, 0.4, 0.2, 0.4, 0.1);
                }
                sound(shape.anchor(), state.getSoundType().getBreakSound(), 1.4F, 0.7F);
                sound(shape.anchor(), heavySound(), 0.8F, 1.2F);
            }
            case METEOR -> {
                for (Vec3 center : centers(shape)) {
                    level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, 3, 0.8, 0.3, 0.8, 0.0);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BossProps.state(look.block())),
                            center.x, center.y + 0.5, center.z, 20, 1.0, 0.4, 1.0, 0.2);
                    level.sendParticles(theme.accent(), center.x, center.y + 0.5, center.z, 12, 1.0, 0.5, 1.0, 0.05);
                }
                sound(shape.anchor(), SoundEvents.GENERIC_EXPLODE, 1.3F, 0.8F);
                shake(near(audience, shape.anchor(), 10.0), 0.5F, 6);
            }
            case ORB -> {
                for (Vec3 center : centers(shape)) {
                    level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1.2, center.z, 1, 0.0, 0.0, 0.0, 0.0);
                    burstOutward(theme.accent(), center.add(0, 1.0, 0), 16, 0.35);
                }
                sound(shape.anchor(), SoundEvents.GLASS_BREAK, 1.2F, 0.6F);
                sound(shape.anchor(), SoundEvents.GENERIC_EXPLODE, 0.7F, 1.4F);
            }
            case BEAM -> {
                beam(shape, look);
                sound(shape.anchor(), beamSound(look), 1.3F, 0.9F);
            }
            case LIGHTNING -> {
                newTick();
                for (Vec3 center : centers(shape)) {
                    if (lightningThisTick++ < MAX_LIGHTNING) {
                        lightning(center);
                    }
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 0.5, center.z, 12, 0.8, 0.4,
                            0.8, 0.2);
                }
                sound(shape.anchor(), ERSounds.SKILL_THUNDER.get(), 1.2F, 1.0F);
            }
            case FIRE -> {
                for (Vec3 spot : spots) {
                    level.sendParticles(ParticleTypes.FLAME, spot.x, spot.y + 0.3, spot.z, 6, 0.6, 0.4, 0.6, 0.03);
                    level.sendParticles(ParticleTypes.LAVA, spot.x, spot.y + 0.3, spot.z, 1, 0.4, 0.1, 0.4, 0.0);
                }
                sound(shape.anchor(), ERSounds.SKILL_FIRE_EXPLOSION.get(), 1.3F, 0.8F);
            }
            case FROST -> {
                for (Vec3 spot : spots) {
                    level.sendParticles(ParticleTypes.SNOWFLAKE, spot.x, spot.y + 0.4, spot.z, 6, 0.6, 0.4, 0.6, 0.05);
                    level.sendParticles(ERParticles.FROST.get(), spot.x, spot.y + 0.4, spot.z, 3, 0.5, 0.3, 0.5, 0.02);
                }
                sound(shape.anchor(), ERSounds.SKILL_ICE_SHATTER.get(), 1.3F, 0.8F);
            }
            case VOID -> {
                for (Vec3 spot : spots) {
                    for (int i = 0; i < 4; i++) {
                        level.sendParticles(ParticleTypes.SCULK_SOUL, spot.x, spot.y + 0.2 + i * 0.7, spot.z, 1, 0.2, 0.1,
                                0.2, 0.02);
                    }
                    level.sendParticles(ERParticles.SHADOW.get(), spot.x, spot.y + 0.5, spot.z, 4, 0.5, 0.5, 0.5, 0.02);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, spot.x, spot.y + 0.3, spot.z, 6, 0.6, 0.2, 0.6, 0.05);
                }
                sound(shape.anchor(), ERSounds.SKILL_SHADOW_WHOOSH.get(), 1.4F, 0.6F);
            }
            case HOLY -> {
                for (Vec3 center : centers(shape)) {
                    column(ParticleTypes.END_ROD, center, 10.0);
                    level.sendParticles(ERParticles.HOLY.get(), center.x, center.y + 0.5, center.z, 16, 1.0, 0.5, 1.0, 0.02);
                }
                sound(shape.anchor(), ERSounds.SKILL_HOLY_CHIME.get(), 1.3F, 0.8F);
            }
            case SAND -> {
                BlockParticleOption sand = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
                for (Vec3 spot : spots) {
                    level.sendParticles(sand, spot.x, spot.y + 0.4, spot.z, 10, 0.7, 0.4, 0.7, 0.15);
                    level.sendParticles(ParticleTypes.WHITE_ASH, spot.x, spot.y + 0.8, spot.z, 8, 0.9, 0.6, 0.9, 0.02);
                }
                sound(shape.anchor(), SoundEvents.SAND_BREAK, 1.4F, 0.6F);
            }
            case SHOCKWAVE -> {
                if (!(hitKind instanceof AbilityKind.Knockback)) {
                    shockwave(owner, shape.anchor(), 8.0, audience);
                }
            }
            case SUMMON, AURA -> {
                for (Vec3 center : centers(shape)) {
                    level.sendParticles(theme.main(), center.x, center.y + 0.5, center.z, 12, 0.6, 0.6, 0.6, 0.03);
                }
            }
        }
    }

    /** A lasting hazard: a little activity on its area every few ticks. */
    void hazardTick(AbilityLook look, BossShape shape) {
        ParticleOptions particle = switch (look.style()) {
            case FIRE -> ParticleTypes.FLAME;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case DARK, MAGIC -> ERParticles.SHADOW.get();
            default -> theme.main();
        };
        for (Vec3 spot : spots(shape)) {
            level.sendParticles(particle, spot.x, spot.y + 0.2, spot.z, 2, 0.6, 0.1, 0.6, 0.01);
        }
    }

    /** Trail of a charge from {@code from} to {@code to}. */
    void dashTrail(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        for (double d = 0.0; d <= length; d += 0.8) {
            Vec3 point = from.add(delta.scale(length < 1.0E-6 ? 0.0 : d / length));
            level.sendParticles(ParticleTypes.CLOUD, point.x, point.y + 0.3, point.z, 1, 0.3, 0.1, 0.3, 0.01);
            level.sendParticles(theme.main(), point.x, point.y + 1.0, point.z, 1, 0.4, 0.4, 0.4, 0.01);
        }
    }

    /** Players being pulled: particles rush from the edge to the centre. */
    void pull(Point center, double radius) {
        int points = (int) Math.max(16, radius * 4);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            double x = center.x() + Math.cos(angle) * radius;
            double z = center.z() + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, center.y() + 0.6, z, 0, -Math.cos(angle), 0.0,
                    -Math.sin(angle), radius * 0.08);
            level.sendParticles(ParticleTypes.CLOUD, x, center.y() + 0.3, z, 0, -Math.cos(angle), 0.0, -Math.sin(angle),
                    radius * 0.05);
        }
        sound(center, SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.5F);
    }

    /** A ring of dust and light that rushes outward from {@code center} to {@code radius} over a few ticks. */
    void shockwave(String owner, Point center, double radius, List<ServerPlayer> audience) {
        int steps = 6;
        for (int step = 1; step <= steps; step++) {
            double r = radius * step / steps;
            scheduler.later(step - 1, owner, () -> {
                int points = (int) Math.max(12, r * 5);
                for (int i = 0; i < points; i++) {
                    double angle = Math.PI * 2.0 * i / points;
                    double x = center.x() + Math.cos(angle) * r;
                    double z = center.z() + Math.sin(angle) * r;
                    level.sendParticles(ParticleTypes.CLOUD, x, center.y() + 0.2, z, 0, Math.cos(angle), 0.05,
                            Math.sin(angle), 0.25);
                    if (i % 2 == 0) {
                        level.sendParticles(theme.accent(), x, center.y() + 0.5, z, 1, 0.1, 0.2, 0.1, 0.01);
                    }
                }
            });
        }
        sound(center, heavySound(), 1.6F, 0.6F);
        sound(center, SoundEvents.GENERIC_EXPLODE, 0.8F, 0.7F);
        shake(near(audience, center, radius + 8.0), 0.9F, 12);
    }

    // ------------------------------------------------------------------ transformation & defeat

    /** The boss changes into its next form. */
    void transformation(int phaseIndex, List<ServerPlayer> audience) {
        Vec3 center = body.position();
        float size = bodySize();
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + body.getBbHeight() * 0.5, center.z,
                1 + (int) size, size * 0.5, body.getBbHeight() * 0.25, size * 0.5, 0);
        burstOutward(theme.main(), center.add(0, body.getBbHeight() * 0.5, 0), (int) (60 * size), 0.5 * size);
        burstOutward(ERParticles.RUNE.get(), center.add(0, body.getBbHeight() * 0.5, 0), (int) (30 * size),
                0.3 * size);
        for (int delay = 0; delay < 30; delay += 3) {
            scheduler.later(delay, "transform", () -> {
                Vec3 at = body.position();
                for (int i = 0; i < (int) (12 * size); i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    double r = body.getBbWidth() * 0.7 + random.nextDouble() * 1.5 * size;
                    level.sendParticles(theme.main(), at.x + Math.cos(angle) * r, at.y + 0.1, at.z + Math.sin(angle) * r,
                            0, 0.0, 1.0, 0.0, 0.15 + 0.05 * phaseIndex);
                }
                level.sendParticles(theme.accent(), at.x, at.y + body.getBbHeight() * 0.6, at.z, 6,
                        body.getBbWidth() * 0.6, body.getBbHeight() * 0.4, body.getBbWidth() * 0.6, 0.05);
            });
        }
        BossFx.send(audience, 1.4F, 30, theme.rgb(), 0.35F, 14);
        Point at = new Point(center.x, center.y, center.z);
        sound(at, ERSounds.BOSS_FORM_STING.get(), 2.5F, 1.0F);
        sound(at, ERSounds.BOSS_PHASE_CHANGE.get(), 2.0F, 1.0F);
        sound(at, ERSounds.BOSS_ROAR.get(), 2.0F, 0.85F);
    }

    /** One beat of the collapse of a defeated boss. */
    void defeatBeat(DefeatSequence.Beat beat, List<ServerPlayer> audience) {
        Vec3 center = body.position().add(0, body.getBbHeight() * 0.5, 0);
        Point ground = new Point(body.getX(), body.getY(), body.getZ());
        float intensity = (float) beat.intensity();
        switch (beat.kind()) {
            case COLLAPSE -> {
                double spread = Math.max(0.6, body.getBbWidth() * 0.3);
                level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 3, spread, spread, spread,
                        0.0);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, (int) (20 * bodySize()),
                        spread * 1.3, body.getBbHeight() * 0.25, spread * 1.3, 0.03);
                sound(ground, deathSound(), 2.0F, 0.8F);
                sound(ground, ERSounds.BOSS_ROAR.get(), 2.0F, 0.6F);
                sound(ground, ERSounds.BOSS_COLLAPSE.get(), 3.0F, 1.0F);
                BossFx.shake(audience, 1.8F * intensity, 26);
            }
            case FLASH -> {
                level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
                burstOutward(theme.main(), center, (int) (30 * intensity), 0.4);
                sound(ground, ERSounds.SKILL_HOLY_CHIME.get(), 1.4F, 0.6F + 0.6F * intensity);
                BossFx.shake(audience, 0.4F * intensity, 6);
            }
            case BURST -> {
                level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 2, 0.8, 0.8, 0.8, 0.0);
                burstOutward(ParticleTypes.END_ROD, center, (int) (24 * intensity), 0.35);
                burstOutward(theme.accent(), center, (int) (16 * intensity), 0.3);
                sound(ground, SoundEvents.GENERIC_EXPLODE, 0.8F * intensity, 1.3F);
            }
            case SHOCKWAVE -> {
                // the body hits the floor: dust all along where it lies, then the wave
                fallenDust(ground);
                shockwave("defeat", ground, 10.0 + 6.0 * intensity + body.getBbWidth(), audience);
            }
            case PILLAR -> {
                for (int delay = 0; delay < 24; delay += 2) {
                    scheduler.later(delay, "defeat", () -> {
                        for (ServerPlayer player : audience) {
                            for (double y = 0.0; y < 26.0; y += 0.9) {
                                level.sendParticles(player, ParticleTypes.END_ROD, true, ground.x(), ground.y() + y,
                                        ground.z(), 1, 0.25, 0.2, 0.25, 0.01);
                            }
                            level.sendParticles(player, ERParticles.HOLY.get(), true, ground.x(), ground.y() + 1.0,
                                    ground.z(), 6, 1.2, 1.0, 1.2, 0.02);
                        }
                    });
                }
                sound(ground, SoundEvents.BEACON_ACTIVATE, 2.0F, 0.8F);
                sound(ground, ERSounds.SKILL_HOLY_CHIME.get(), 2.0F, 1.2F);
            }
            case DISSOLVE -> {
                burstOutward(ERParticles.HOLY.get(), center, 30, 0.2);
                sound(ground, SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.6F);
            }
            case VANISH -> {
                level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 2, 0.2, 0.2, 0.2, 0);
                burstOutward(ParticleTypes.END_ROD, center, 80, 0.5);
                burstOutward(ERParticles.HOLY.get(), center, 50, 0.4);
                burstOutward(theme.main(), center, 40, 0.4);
                sound(ground, SoundEvents.TOTEM_USE, 1.6F, 1.0F);
                sound(ground, SoundEvents.GENERIC_EXPLODE, 1.2F, 0.6F);
                BossFx.send(audience, 1.0F, 16, 0xFFFFFF, 0.8F, 26);
            }
        }
    }

    /** Light flickering on the dissolving body (every few ticks of the end of the collapse). */
    void dissolving() {
        level.sendParticles(ParticleTypes.END_ROD, body.getRandomX(0.8), body.getRandomY(), body.getRandomZ(0.8), 3, 0.1,
                0.1, 0.1, 0.03);
        level.sendParticles(theme.main(), body.getRandomX(0.8), body.getRandomY(), body.getRandomZ(0.8), 2, 0.1, 0.1, 0.1,
                0.02);
    }

    // ------------------------------------------------------------------ helpers

    private void beam(BossShape shape, AbilityLook look) {
        if (!(shape instanceof BossShape.Line line)) {
            for (Vec3 spot : spots(shape)) {
                level.sendParticles(ParticleTypes.END_ROD, spot.x, spot.y + 1.0, spot.z, 2, 0.3, 0.3, 0.3, 0.02);
            }
            return;
        }
        DustParticleOptions dust = dust(look);
        ParticleOptions core = switch (look.style()) {
            case FIRE -> ParticleTypes.FLAME;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case DARK -> ParticleTypes.REVERSE_PORTAL;
            default -> ParticleTypes.END_ROD;
        };
        double dx = line.end().x() - line.origin().x();
        double dz = line.end().z() - line.origin().z();
        double length = Math.max(1.0E-3, Math.sqrt(dx * dx + dz * dz));
        for (double d = 0.0; d <= length; d += 0.45) {
            double x = line.origin().x() + dx * d / length;
            double z = line.origin().z() + dz * d / length;
            level.sendParticles(dust, x, line.origin().y() + 1.0, z, 2, line.width() * 0.15, 0.15, line.width() * 0.15,
                    0.0);
            if (((int) (d / 0.45)) % 3 == 0) {
                level.sendParticles(core, x, line.origin().y() + 1.0, z, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
    }

    private static DustParticleOptions dust(AbilityLook look) {
        int rgb = switch (look.style()) {
            case FIRE -> 0xFF7A14;
            case FROST -> 0x8EE0FF;
            case HOLY -> 0xFFE070;
            case DARK -> 0x9A3AE0;
            case MAGIC -> 0xC070FF;
            case SAFE -> 0x60FF80;
            case ALLY -> 0x7FD8FF;
            case DANGER -> 0xFF4030;
        };
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F), 1.6F);
    }

    private SoundEvent beamSound(AbilityLook look) {
        return switch (look.style()) {
            case FIRE -> ERSounds.SKILL_FIRE_CAST.get();
            case FROST -> ERSounds.SKILL_ICE_CAST.get();
            case DARK -> ERSounds.SKILL_SHADOW_WHOOSH.get();
            default -> ERSounds.SKILL_MAGIC_BOLT.get();
        };
    }

    private void lightning(Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at.x, at.y, at.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    private void column(ParticleOptions particle, Vec3 at, double height) {
        for (double y = 0.0; y < height; y += 0.7) {
            level.sendParticles(particle, at.x, at.y + y, at.z, 1, 0.15, 0.1, 0.15, 0.01);
        }
    }

    /** Particles flying out of {@code center} in every direction. */
    private void burstOutward(ParticleOptions particle, Vec3 center, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double yaw = random.nextDouble() * Math.PI * 2.0;
            double pitch = (random.nextDouble() - 0.3) * Math.PI * 0.6;
            level.sendParticles(particle, center.x, center.y, center.z, 0, Math.cos(yaw) * Math.cos(pitch),
                    Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch), speed);
        }
    }

    /** Size of the body against a 3-block-wide boss (1 at least): scales debris, dust and bursts. */
    private float bodySize() {
        return Math.max(1.0F, body.getBbWidth() / 3.0F);
    }

    /**
     * Where the body's blow lands for a slam on {@code shape}: in front of the boss at the reach of its weapon when the
     * shape is centred on the boss (slams, novas), else the shape's centre.
     */
    private Point landing(BossShape shape) {
        Point anchor = shape.anchor();
        if (!(body instanceof ERBoss boss) || anchor.horizontalDistanceSquared(body.getX(), body.getZ()) > 1.0) {
            return anchor;
        }
        double reach = Math.max(body.getBbWidth() * 0.5 + 0.5, boss.meleeReach() * 0.85);
        double yaw = Math.toRadians(body.getYRot());
        return new Point(body.getX() - Math.sin(yaw) * reach, anchor.y(), body.getZ() + Math.cos(yaw) * reach);
    }

    /** Cracks radiating from {@code at}, floor chunks thrown up and dust, scaled to the body. */
    private void impact(String owner, Point at, BlockState floor) {
        float size = bodySize();
        long now = level.getGameTime();
        int cracks = 4 + (int) (2 * size);
        double start = random.nextDouble() * Math.PI * 2.0;
        BlockState dark = Blocks.BLACKSTONE.defaultBlockState();
        for (int i = 0; i < cracks; i++) {
            double angle = start + Math.PI * 2.0 * i / cracks + (random.nextDouble() - 0.5) * 0.5;
            float length = (float) (1.2 + random.nextDouble() * 1.6) * size;
            props.crack(owner, dark, at.x(), at.y(), at.z(), length, 0.16F + 0.06F * size, angle, now,
                    40 + random.nextInt(20));
        }
        int chunks = 2 + (int) (2 * size);
        for (int i = 0; i < chunks; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = 0.4 + random.nextDouble() * 1.4 * size;
            float chunk = (float) (0.25 + random.nextDouble() * 0.25) * size;
            props.debris(owner, floor, at.x() + Math.cos(angle) * r, at.y(), at.z() + Math.sin(angle) * r, chunk, angle,
                    now, 30 + random.nextInt(20));
        }
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, floor);
        level.sendParticles(dust, at.x(), at.y() + 0.3, at.z(), (int) (24 * size), 0.8 * size, 0.3, 0.8 * size, 0.3);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x(), at.y() + 0.2, at.z(), (int) (3 * size),
                0.6 * size, 0.1, 0.6 * size, 0.01);
    }

    /** Dust and debris along the fallen body, from its feet to where its head lies at the end of the death animation. */
    private void fallenDust(Point ground) {
        if (!(body instanceof ERBoss boss)) {
            return;
        }
        float[] head = RigData.fallen(boss.kind().rig().id(), boss.lookKey());
        float scale = boss.modelScale();
        double yaw = Math.toRadians(body.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double toX = (forwardZ * head[0] + forwardX * head[1]) * scale;
        double toZ = (-forwardX * head[0] + forwardZ * head[1]) * scale;
        double length = Math.sqrt(toX * toX + toZ * toZ);
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, floorBlock(ground));
        int steps = Math.max(2, (int) (length * 1.5));
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps;
            double x = ground.x() + toX * f;
            double z = ground.z() + toZ * f;
            level.sendParticles(dust, x, ground.y() + 0.2, z, 6, 0.5 * bodySize(), 0.1, 0.5 * bodySize(), 0.2);
            level.sendParticles(ParticleTypes.CLOUD, x, ground.y() + 0.3, z, 2, 0.6, 0.1, 0.6, 0.04);
        }
        impact("defeat", new Point(ground.x() + toX, ground.y(), ground.z() + toZ), floorBlock(ground));
    }

    private void explosionAt(Point at, int count) {
        level.sendParticles(ParticleTypes.EXPLOSION, at.x(), at.y() + 0.5, at.z(), count, 0.3, 0.2, 0.3, 0.0);
    }

    private BlockState floorBlock(Point at) {
        BlockState below = level.getBlockState(BlockPos.containing(at.x(), at.y() - 0.5, at.z()));
        return below.isAir() ? theme.debris() : below;
    }

    private SoundEvent heavySound() {
        return body instanceof ERBoss boss ? boss.kind().sounds()[4] : SoundEvents.GENERIC_EXPLODE;
    }

    private SoundEvent deathSound() {
        return body instanceof ERBoss boss ? boss.kind().sounds()[2] : SoundEvents.WITHER_DEATH;
    }

    private void sound(Point at, SoundEvent sound, float volume, float pitch) {
        newTick();
        if (sound != null && soundsThisTick.add(sound)) {
            level.playSound(null, at.x(), at.y(), at.z(), sound, SoundSource.HOSTILE, volume, pitch);
        }
    }

    private void newTick() {
        long tick = level.getGameTime();
        if (tick != effectTick) {
            effectTick = tick;
            soundsThisTick.clear();
            shookThisTick = false;
            lightningThisTick = 0;
        }
    }

    /** Camera shake for the players near a hit, once per tick. */
    private void shake(List<ServerPlayer> players, float intensity, int ticks) {
        newTick();
        if (!shookThisTick && !players.isEmpty()) {
            shookThisTick = true;
            BossFx.shake(players, intensity, ticks);
        }
    }

    private static List<ServerPlayer> near(List<ServerPlayer> players, Point at, double range) {
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer player : players) {
            double dx = player.getX() - at.x();
            double dz = player.getZ() - at.z();
            if (dx * dx + dz * dz <= range * range) {
                near.add(player);
            }
        }
        return near;
    }

    private static float size(BossShape shape, float fallback) {
        if (shape instanceof BossShape.Circle circle) {
            return (float) Math.max(0.8, Math.min(2.6, circle.radius() * 0.55));
        }
        return fallback;
    }

    // ------------------------------------------------------------------ sampling shapes

    /** Centres of the areas of a shape (one for most shapes). */
    private static List<Vec3> centers(BossShape shape) {
        Point anchor = shape.anchor();
        if (shape instanceof BossShape.Line line) {
            return List.of(new Vec3((line.origin().x() + line.end().x()) / 2.0, anchor.y(),
                    (line.origin().z() + line.end().z()) / 2.0));
        }
        return List.of(new Vec3(anchor.x(), anchor.y(), anchor.z()));
    }

    /** Up to {@link #MAX_SPOTS} points spread over the area of a shape (for particles and props). */
    private List<Vec3> spots(BossShape shape) {
        List<Vec3> spots = new ArrayList<>();
        Point anchor = shape.anchor();
        if (shape instanceof BossShape.Circle circle) {
            spots.add(new Vec3(anchor.x(), anchor.y(), anchor.z()));
            int around = circle.radius() < 2.5 ? 3 : 6;
            for (int i = 0; i < around; i++) {
                double angle = Math.PI * 2.0 * i / around + random.nextDouble() * 0.4;
                double r = circle.radius() * 0.6;
                spots.add(new Vec3(anchor.x() + Math.cos(angle) * r, anchor.y(), anchor.z() + Math.sin(angle) * r));
            }
        } else if (shape instanceof BossShape.Square) {
            spots.add(new Vec3(anchor.x(), anchor.y(), anchor.z()));
        } else if (shape instanceof BossShape.Line line) {
            double dx = line.end().x() - line.origin().x();
            double dz = line.end().z() - line.origin().z();
            double length = Math.sqrt(dx * dx + dz * dz);
            int count = (int) Math.min(MAX_SPOTS, Math.max(2, length / 2.2));
            for (int i = 0; i < count; i++) {
                double t = (i + 0.5) / count;
                spots.add(new Vec3(line.origin().x() + dx * t, anchor.y(), line.origin().z() + dz * t));
            }
        } else if (shape instanceof BossShape.Cross cross) {
            double radians = Math.toRadians(cross.yawDegrees());
            double fx = -Math.sin(radians);
            double fz = Math.cos(radians);
            int perArm = (int) Math.max(1, Math.min(4, cross.armLength() / 3.5));
            spots.add(new Vec3(anchor.x(), anchor.y(), anchor.z()));
            for (int i = 1; i <= perArm; i++) {
                double d = cross.armLength() * i / (perArm + 0.5);
                spots.add(new Vec3(anchor.x() + fx * d, anchor.y(), anchor.z() + fz * d));
                spots.add(new Vec3(anchor.x() - fx * d, anchor.y(), anchor.z() - fz * d));
                spots.add(new Vec3(anchor.x() + fz * d, anchor.y(), anchor.z() - fx * d));
                spots.add(new Vec3(anchor.x() - fz * d, anchor.y(), anchor.z() + fx * d));
            }
        } else if (shape instanceof BossShape.Cone cone) {
            double half = Math.toRadians(cone.halfAngleDegrees());
            double yaw = Math.toRadians(cone.yawDegrees());
            for (int ray = -1; ray <= 1; ray++) {
                for (double d = cone.range() * 0.3; d <= cone.range(); d += cone.range() * 0.35) {
                    double angle = yaw + half * 0.7 * ray;
                    spots.add(new Vec3(anchor.x() - Math.sin(angle) * d, anchor.y(), anchor.z() + Math.cos(angle) * d));
                }
            }
        } else if (shape instanceof BossShape.Ring ring) {
            double r = (ring.inner() + ring.outer()) / 2.0;
            int count = (int) Math.min(MAX_SPOTS, Math.max(6, r * 1.2));
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0 * i / count;
                spots.add(new Vec3(anchor.x() + Math.cos(angle) * r, anchor.y(), anchor.z() + Math.sin(angle) * r));
            }
        } else {
            for (int i = 0; i < MAX_SPOTS; i++) { // whole arena, outside of safe zones
                double angle = random.nextDouble() * Math.PI * 2.0;
                double r = 2.0 + random.nextDouble() * 14.0;
                double x = anchor.x() + Math.cos(angle) * r;
                double z = anchor.z() + Math.sin(angle) * r;
                if (shape.contains(x, anchor.y(), z)) {
                    spots.add(new Vec3(x, anchor.y(), z));
                }
            }
        }
        return spots.size() > MAX_SPOTS ? spots.subList(0, MAX_SPOTS) : spots;
    }

    /** Points on the outline of a shape, about {@code step} blocks apart. */
    private static List<Vec3> outline(BossShape shape, double step) {
        List<Vec3> points = new ArrayList<>();
        Point anchor = shape.anchor();
        if (shape instanceof BossShape.Circle circle) {
            int count = (int) Math.max(8, Math.PI * 2.0 * circle.radius() / step);
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0 * i / count;
                points.add(new Vec3(anchor.x() + Math.cos(angle) * circle.radius(), anchor.y(),
                        anchor.z() + Math.sin(angle) * circle.radius()));
            }
        } else if (shape instanceof BossShape.Ring ring) {
            points.addAll(outline(new BossShape.Circle(anchor, Math.max(0.1, ring.inner())), step));
            points.addAll(outline(new BossShape.Circle(anchor, ring.outer()), step));
        } else {
            for (Vec3 center : centers(shape)) {
                points.add(center);
            }
        }
        return points.size() > 96 ? points.subList(0, 96) : points;
    }
}
