package jp.satsujinki.game;

import jp.satsujinki.SatsujinkiConfig;
import jp.satsujinki.block.BloodSplatterBlock;
import jp.satsujinki.block.SpikeBedBlock;
import jp.satsujinki.entity.ContestantEntity;
import jp.satsujinki.entity.CorpseEntity;
import jp.satsujinki.entity.GibEntity;
import jp.satsujinki.item.HostHandItem;
import jp.satsujinki.network.GoreFxPacket;
import jp.satsujinki.network.ModNetwork;
import jp.satsujinki.registry.ModBlocks;
import jp.satsujinki.registry.ModDamageTypes;
import jp.satsujinki.registry.ModEntities;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

/** Server-side gore: kill effects, corpses, flying chunks, blood decals. */
public final class GoreHandler {
    private GoreHandler() {
    }

    // ------------------------------------------------------------------ client fx

    public static void fx(ServerLevel level, double x, double y, double z, int kind, float intensity) {
        fx(level, x, y, z, kind, intensity, 0.0F, 0.0F);
    }

    public static void fx(ServerLevel level, double x, double y, double z, int kind, float intensity, float dirX, float dirZ) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(x, y, z, 80.0D, level.dimension())),
                new GoreFxPacket(x, y, z, kind, intensity, dirX, dirZ));
    }

    public static void bleed(LivingEntity e, float amount) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        fx(sl, e.getX(), e.getY() + e.getBbHeight() * 0.6D, e.getZ(), GoreFxPacket.BLEED, Math.min(4.0F, amount / 3.0F + 0.5F));
        if (sl.random.nextFloat() < 0.35F) splatter(sl, e.position(), 1, 0.7D);
    }

    // ------------------------------------------------------------------ kills

    public static void kill(LivingEntity victim, KillStyle style, @Nullable Player credit, ResourceKey<DamageType> type) {
        kill(victim, style, credit, type, Double.NaN, false, null);
    }

    /**
     * Executes a victim with full gore. floorY/spikes are used by the press, facing by the guillotine.
     */
    public static void kill(LivingEntity victim, KillStyle style, @Nullable Player credit, ResourceKey<DamageType> type,
                            double floorY, boolean spikes, @Nullable Direction facing) {
        if (!(victim.level() instanceof ServerLevel level) || !victim.isAlive()) return;
        if (victim instanceof ContestantEntity c) {
            c.goreHandled = true;
            c.forcedStyle = style;
            HostHandItem.forgetEntity(c);
        }
        spawnRemains(level, victim, style, floorY, spikes, facing);
        DamageSource src = ModDamageTypes.source(level, type, credit);
        victim.invulnerableTime = 0;
        victim.hurt(src, 100000.0F);
        if (victim.isAlive()) {
            victim.setHealth(0.0F);
            victim.die(src);
        }
        if (!(victim instanceof ContestantEntity) && !(victim instanceof Player)) victim.discard();
    }

    /** Spawns the body/chunks/blood for a death. Also used for non-trap deaths (sword, fall, ...). */
    public static void spawnRemains(ServerLevel level, LivingEntity v, KillStyle style, double floorY, boolean spikes, @Nullable Direction facing) {
        RandomSource r = level.random;
        int skin = v instanceof ContestantEntity c ? c.getSkin() : -1;
        boolean human = skin >= 0;
        double x = v.getX(), y = v.getY(), z = v.getZ();
        double cy = y + v.getBbHeight() * 0.5D;
        float yaw = v.getYRot();
        int maxGibs = SatsujinkiConfig.maxGibs();
        switch (style) {
            case MIXER -> {
                fx(level, x, cy, z, GoreFxPacket.DEATH_MIXER, 1.6F);
                gibs(level, x, y + 0.5D, z, skin, Math.min(maxGibs, 14), new Vec3(0.0D, 0.85D, 0.0D), 0.3D, human);
                sound(level, x, y, z, ModSounds.GORE_SQUELCH.get(), 1.8F, 0.75F);
                sound(level, x, y, z, ModSounds.GORE_CRUNCH.get(), 1.6F, 0.8F);
                splatter(level, new Vec3(x, y + 4.0D, z), 4, 2.6D);
            }
            case PRESS -> {
                double fy = Double.isNaN(floorY) ? y : floorY;
                fx(level, x, fy + 0.15D, z, GoreFxPacket.DEATH_PRESS, 1.6F);
                if (human) corpse(level, x, fy + 0.02D, z, yaw, skin, spikes ? CorpseEntity.STYLE_IMPALED : CorpseEntity.STYLE_FLAT, Vec3.ZERO);
                gibs(level, x, fy + 0.35D, z, skin, Math.min(maxGibs, 7), new Vec3(0.0D, 0.2D, 0.0D), 0.45D, false);
                splatter(level, new Vec3(x, fy + 0.5D, z), 12, 1.7D);
                sound(level, x, fy, z, ModSounds.GORE_CRUNCH.get(), 2.0F, 0.65F);
                sound(level, x, fy, z, ModSounds.GORE_SPLAT.get(), 1.8F, 0.8F);
                if (spikes) sound(level, x, fy, z, ModSounds.SPIKE_IMPALE.get(), 1.6F, 0.9F);
            }
            case SAW -> {
                fx(level, x, y + 0.9D, z, GoreFxPacket.DEATH_SAW, 1.3F);
                if (human) {
                    corpse(level, x, y + 0.85D, z, yaw, skin, CorpseEntity.STYLE_TOP_HALF,
                            new Vec3(r.nextGaussian() * 0.12D, 0.42D, r.nextGaussian() * 0.12D));
                    corpse(level, x, y + 0.05D, z, yaw + 180.0F, skin, CorpseEntity.STYLE_BOTTOM_HALF,
                            new Vec3(r.nextGaussian() * 0.08D, 0.15D, r.nextGaussian() * 0.08D));
                }
                gibs(level, x, y + 0.9D, z, skin, Math.min(maxGibs, 6), new Vec3(0.0D, 0.45D, 0.0D), 0.3D, false);
                splatter(level, v.position(), 7, 1.3D);
                sound(level, x, y, z, ModSounds.GORE_SQUELCH.get(), 1.5F, 1.0F);
            }
            case GUILLOTINE -> {
                Direction f = facing != null ? facing : Direction.NORTH;
                fx(level, x, y + 1.45D, z, GoreFxPacket.DEATH_GUILLOTINE, 1.3F, f.getStepX(), f.getStepZ());
                if (human) {
                    corpse(level, x - f.getStepX() * 0.3D, y + 0.2D, z - f.getStepZ() * 0.3D, f.toYRot(), skin, CorpseEntity.STYLE_HEADLESS, Vec3.ZERO);
                    GibEntity head = gib(level, x + f.getStepX() * 0.45D, y + 1.5D, z + f.getStepZ() * 0.45D, GibEntity.KIND_HEAD, skin);
                    head.setDeltaMovement(f.getStepX() * 0.3D + r.nextGaussian() * 0.04D, 0.32D, f.getStepZ() * 0.3D + r.nextGaussian() * 0.04D);
                    level.addFreshEntity(head);
                }
                gibs(level, x, y + 1.4D, z, skin, Math.min(maxGibs, 3), new Vec3(f.getStepX() * 0.2D, 0.3D, f.getStepZ() * 0.2D), 0.2D, false);
                splatter(level, v.position(), 6, 1.1D);
                sound(level, x, y, z, ModSounds.GORE_SPLAT.get(), 1.5F, 0.9F);
            }
            case SPIKES -> {
                fx(level, x, y + 0.4D, z, GoreFxPacket.DEATH_SPIKES, 1.0F);
                if (human) corpse(level, x, y + 0.02D, z, yaw, skin, CorpseEntity.STYLE_IMPALED, Vec3.ZERO);
                gibs(level, x, y + 0.5D, z, skin, Math.min(maxGibs, 3), new Vec3(0.0D, 0.25D, 0.0D), 0.25D, false);
                splatter(level, v.position(), 5, 1.0D);
                sound(level, x, y, z, ModSounds.SPIKE_IMPALE.get(), 1.4F, 0.9F);
            }
            default -> {
                fx(level, x, cy, z, GoreFxPacket.DEATH_GENERIC, 1.0F);
                if (human) corpse(level, x, y + 0.05D, z, yaw, skin, CorpseEntity.STYLE_LYING,
                        new Vec3(r.nextGaussian() * 0.05D, 0.12D, r.nextGaussian() * 0.05D));
                gibs(level, x, cy, z, skin, Math.min(maxGibs, 4), new Vec3(0.0D, 0.3D, 0.0D), 0.25D, false);
                splatter(level, v.position(), 5, 1.0D);
                sound(level, x, y, z, ModSounds.GORE_SPLAT.get(), 1.4F, 1.0F);
            }
        }
        bloodySpikes(level, BlockPos.containing(x, y + 0.2D, z));
        ContestantEntity.alertWitnesses(level, new Vec3(x, cy, z), v);
    }

    private static void sound(Level level, double x, double y, double z, SoundEvent s, float vol, float pitch) {
        level.playSound(null, x, y, z, s, SoundSource.HOSTILE, vol, pitch * (0.92F + level.random.nextFloat() * 0.16F));
    }

    private static void gibs(ServerLevel level, double x, double y, double z, int skin, int count, Vec3 base, double spread, boolean limbs) {
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            float k = r.nextFloat();
            int kind = k < 0.5F ? GibEntity.KIND_MEAT : k < 0.78F ? GibEntity.KIND_ORGAN : GibEntity.KIND_BONE;
            GibEntity g = gib(level, x + r.nextGaussian() * 0.15D, y + r.nextGaussian() * 0.1D, z + r.nextGaussian() * 0.15D, kind, skin);
            g.setDeltaMovement(base.x + r.nextGaussian() * spread, base.y + r.nextDouble() * spread * 1.5D, base.z + r.nextGaussian() * spread);
            level.addFreshEntity(g);
        }
        if (limbs && skin >= 0) {
            int[] kinds = {GibEntity.KIND_ARM, GibEntity.KIND_ARM, GibEntity.KIND_LEG, GibEntity.KIND_LEG, GibEntity.KIND_HEAD};
            for (int kind : kinds) {
                GibEntity g = gib(level, x + r.nextGaussian() * 0.2D, y + 0.3D, z + r.nextGaussian() * 0.2D, kind, skin);
                g.setDeltaMovement(base.x + r.nextGaussian() * spread * 0.8D, base.y * 1.1D + r.nextDouble() * 0.3D, base.z + r.nextGaussian() * spread * 0.8D);
                level.addFreshEntity(g);
            }
        }
    }

    private static GibEntity gib(ServerLevel level, double x, double y, double z, int kind, int skin) {
        GibEntity g = new GibEntity(ModEntities.GIB.get(), level);
        g.setup(kind, skin, 220 + level.random.nextInt(200));
        g.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        return g;
    }

    private static void corpse(ServerLevel level, double x, double y, double z, float yaw, int skin, int style, Vec3 vel) {
        CorpseEntity c = new CorpseEntity(ModEntities.CORPSE.get(), level);
        c.setup(skin, style, SatsujinkiConfig.corpseTicks());
        c.moveTo(x, y, z, yaw, 0.0F);
        c.setDeltaMovement(vel);
        level.addFreshEntity(c);
    }

    // ------------------------------------------------------------------ blood on the ground

    /** Puts blood splatter blocks on solid ground around a point. */
    public static void splatter(ServerLevel level, Vec3 center, int count, double radius) {
        if (!SatsujinkiConfig.bloodDecals()) return;
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            double px = center.x + r.nextGaussian() * radius;
            double pz = center.z + r.nextGaussian() * radius;
            BlockPos start = BlockPos.containing(px, center.y + 1.0D, pz);
            for (int dy = 0; dy < 6; dy++) {
                BlockPos at = start.below(dy);
                BlockState st = level.getBlockState(at);
                BlockPos below = at.below();
                boolean free = st.isAir() || st.is(ModBlocks.BLOOD_SPLATTER.get());
                if (free && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    if (st.isAir()) {
                        level.setBlock(at, ModBlocks.BLOOD_SPLATTER.get().defaultBlockState()
                                .setValue(BloodSplatterBlock.VARIANT, r.nextInt(4)), 3);
                    } else if (st.getValue(BloodSplatterBlock.DRIED)) {
                        level.setBlock(at, st.setValue(BloodSplatterBlock.DRIED, false), 3);
                    }
                    break;
                }
                if (!st.getCollisionShape(level, at).isEmpty()) break;
            }
        }
    }

    private static void bloodySpikes(ServerLevel level, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockState st = level.getBlockState(p);
                    if (st.getBlock() instanceof SpikeBedBlock) {
                        int b = st.getValue(SpikeBedBlock.BLOOD);
                        if (b < 2) level.setBlock(p, st.setValue(SpikeBedBlock.BLOOD, b + 1), 3);
                    }
                }
            }
        }
    }

    /** Who gets the points for a kill that has no attacker (spikes, falls). */
    @Nullable
    public static Player creditFor(LivingEntity victim) {
        Level level = victim.level();
        if (victim instanceof ContestantEntity c && c.wasThrownRecently() && c.getThrowerId() != null) {
            Player p = level.getPlayerByUUID(c.getThrowerId());
            if (p != null) return p;
        }
        return level.getNearestPlayer(victim.getX(), victim.getY(), victim.getZ(), 48.0D, false);
    }
}
