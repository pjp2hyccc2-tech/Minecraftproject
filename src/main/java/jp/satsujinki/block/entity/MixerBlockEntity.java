package jp.satsujinki.block.entity;

import jp.satsujinki.block.MixerGlassBlock;
import jp.satsujinki.entity.ContestantEntity;
import jp.satsujinki.game.GoreHandler;
import jp.satsujinki.game.KillStyle;
import jp.satsujinki.network.GoreFxPacket;
import jp.satsujinki.registry.ModBlockEntities;
import jp.satsujinki.registry.ModDamageTypes;
import jp.satsujinki.registry.ModParticles;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Giant mixer: a vortex pulls victims onto the blades; every kill raises the blood soup and smears the glass. */
public class MixerBlockEntity extends TrapBlockEntity {
    public static final int GRIND_TICKS = 18;
    /** 0..1 height of the blood soup. */
    public float blood;
    private final Map<Integer, Integer> grind = new HashMap<>();

    // client animation
    public float angle;
    public float prevAngle;
    public float spin;
    private boolean loopStarted;

    public MixerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MIXER.get(), pos, state);
    }

    @Override
    public boolean isContinuous() {
        return true;
    }

    @Override
    public void trigger(@Nullable Player by) {
        setRunning(!this.running, by);
    }

    @Override
    protected void onRunningChanged() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.playSound(null, this.worldPosition, this.running ? ModSounds.MIXER_START.get() : ModSounds.MIXER_STOP.get(),
                    SoundSource.BLOCKS, 1.6F, 1.0F);
        }
    }

    public AABB interior() {
        BlockPos p = this.worldPosition;
        return new AABB(p.getX() - 1.0D, p.getY() + 1.0D, p.getZ() - 1.0D, p.getX() + 2.0D, p.getY() + 4.5D, p.getZ() + 2.0D);
    }

    /** The column of air above the jar where thrown victims get sucked in. */
    public AABB funnel() {
        BlockPos p = this.worldPosition;
        return new AABB(p.getX() - 2.0D, p.getY() + 3.5D, p.getZ() - 2.0D, p.getX() + 3.0D, p.getY() + 9.0D, p.getZ() + 3.0D);
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = this.worldPosition;
        return new AABB(p.getX() - 2.0D, p.getY(), p.getZ() - 2.0D, p.getX() + 3.0D, p.getY() + 5.0D, p.getZ() + 3.0D);
    }

    @Override
    public void serverTick() {
        if (!(this.level instanceof ServerLevel sl)) return;
        if (this.running) {
            List<LivingEntity> victims = sl.getEntitiesOfClass(LivingEntity.class, interior(), ContestantEntity::isTrapVictim);
            Set<Integer> seen = new HashSet<>();
            double cx = this.worldPosition.getX() + 0.5D, cz = this.worldPosition.getZ() + 0.5D;
            for (LivingEntity e : victims) {
                if (e instanceof ContestantEntity c && c.isHeld()) continue;
                seen.add(e.getId());
                double dx = cx - e.getX(), dz = cz - e.getZ();
                Vec3 v = e.getDeltaMovement();
                e.setDeltaMovement(v.x * 0.5D + dx * 0.12D - dz * 0.2D, Math.min(v.y, 0.0D) - 0.06D, v.z * 0.5D + dz * 0.12D + dx * 0.2D);
                e.hasImpulse = true;
                e.fallDistance = 0.0F;
                if (e.getY() < this.worldPosition.getY() + 2.3D) {
                    int g = this.grind.merge(e.getId(), 1, Integer::sum);
                    if (g == 1 && e instanceof ContestantEntity c) c.screamNow();
                    if (g % 3 == 0) GoreHandler.fx(sl, e.getX(), e.getY() + 0.5D, e.getZ(), GoreFxPacket.MIXER_GRIND, 1.0F);
                    if (g % 7 == 1) sl.playSound(null, this.worldPosition, ModSounds.MIXER_GRIND.get(), SoundSource.BLOCKS, 1.3F, 0.85F + sl.random.nextFloat() * 0.3F);
                    if (g >= GRIND_TICKS) {
                        this.grind.remove(e.getId());
                        GoreHandler.kill(e, KillStyle.MIXER, creditPlayer(), ModDamageTypes.MIXER);
                        onKill(sl);
                    }
                }
            }
            this.grind.keySet().retainAll(seen);
            // Suction funnel above the rim: anything thrown over (or left standing on) the jar is dragged to the
            // middle and drops onto the blades, so tossing victims in from a distance actually works.
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, funnel(), ContestantEntity::isTrapVictim)) {
                if (e instanceof ContestantEntity c && c.isHeld()) continue;
                if (e.getY() < this.worldPosition.getY() + 3.9D) continue;
                double dx = cx - e.getX(), dz = cz - e.getZ();
                Vec3 v = e.getDeltaMovement();
                e.setDeltaMovement(v.x * 0.55D + dx * 0.2D, Math.min(v.y, 0.15D) - 0.03D, v.z * 0.55D + dz * 0.2D);
                e.hasImpulse = true;
                e.fallDistance = 0.0F;
            }
        } else {
            this.grind.clear();
            if (this.blood > 0.0F && sl.getGameTime() % 20L == 0L) {
                this.blood = Math.max(0.0F, this.blood - 0.003F);
                if (sl.getGameTime() % 400L == 0L) sync();
            }
        }
    }

    private void onKill(ServerLevel sl) {
        this.kills++;
        this.blood = Math.min(1.0F, this.blood + 0.16F);
        RandomSource r = sl.random;
        for (int i = 0; i < 10; i++) {
            int dx = r.nextInt(5) - 2, dz = r.nextInt(5) - 2;
            if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
            BlockPos gp = this.worldPosition.offset(dx, 1 + r.nextInt(3), dz);
            BlockState gs = sl.getBlockState(gp);
            if (gs.getBlock() instanceof MixerGlassBlock) {
                int b = gs.getValue(MixerGlassBlock.BLOOD);
                if (b < 3) sl.setBlock(gp, gs.setValue(MixerGlassBlock.BLOOD, b + 1), 3);
            }
        }
        sync();
    }

    @Override
    public void clientTick() {
        if (this.level == null) return;
        this.prevAngle = this.angle;
        this.spin += ((this.running ? 1.0F : 0.0F) - this.spin) * (this.running ? 0.05F : 0.025F);
        this.angle += this.spin * 52.0F;
        if (this.angle > 360.0F) {
            this.angle -= 360.0F;
            this.prevAngle -= 360.0F;
        }
        if (this.running && !this.loopStarted) {
            this.loopStarted = true;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> jp.satsujinki.client.ClientHooks.playTrapLoop(this, ModSounds.MIXER_LOOP.get()));
        }
        if (!this.running) this.loopStarted = false;
        RandomSource r = this.level.random;
        if (this.spin > 0.3F && this.blood > 0.05F && r.nextInt(2) == 0) {
            double a = r.nextDouble() * Math.PI * 2.0D;
            double rad = 0.4D + r.nextDouble() * 1.0D;
            double sx = this.worldPosition.getX() + 0.5D + Math.cos(a) * rad;
            double sz = this.worldPosition.getZ() + 0.5D + Math.sin(a) * rad;
            double sy = this.worldPosition.getY() + 1.1D + this.blood * 2.6D;
            this.level.addParticle(ModParticles.BLOOD_DROP.get(), sx, sy, sz, -Math.sin(a) * 0.15D, 0.15D + r.nextDouble() * 0.15D, Math.cos(a) * 0.15D);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("Blood", this.blood);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.blood = tag.getFloat("Blood");
    }
}
