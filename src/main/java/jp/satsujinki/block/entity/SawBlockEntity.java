package jp.satsujinki.block.entity;

import jp.satsujinki.entity.ContestantEntity;
import jp.satsujinki.game.GoreHandler;
import jp.satsujinki.game.KillStyle;
import jp.satsujinki.network.GoreFxPacket;
import jp.satsujinki.registry.ModBlockEntities;
import jp.satsujinki.registry.ModDamageTypes;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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

/** 回転ノコギリ: anything standing on the blade gets stuck, shredded and cut in two. */
public class SawBlockEntity extends TrapBlockEntity {
    public static final int CUT_TICKS = 14;
    private final Map<Integer, Integer> cut = new HashMap<>();
    // client
    public float angle;
    public float prevAngle;
    public float spin;
    private boolean loopStarted;

    public SawBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAW.get(), pos, state);
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
    public AABB getRenderBoundingBox() {
        BlockPos p = this.worldPosition;
        return new AABB(p.getX() - 0.5D, p.getY(), p.getZ() - 0.5D, p.getX() + 1.5D, p.getY() + 2.0D, p.getZ() + 1.5D);
    }

    @Override
    public void serverTick() {
        if (!(this.level instanceof ServerLevel sl)) return;
        if (!this.running) {
            this.cut.clear();
            return;
        }
        BlockPos p = this.worldPosition;
        AABB zone = new AABB(p.getX() + 0.05D, p.getY() + 0.9D, p.getZ() + 0.05D, p.getX() + 0.95D, p.getY() + 1.7D, p.getZ() + 0.95D);
        List<LivingEntity> victims = sl.getEntitiesOfClass(LivingEntity.class, zone, ContestantEntity::isTrapVictim);
        Set<Integer> seen = new HashSet<>();
        for (LivingEntity e : victims) {
            if (e instanceof ContestantEntity c && c.isHeld()) continue;
            seen.add(e.getId());
            int g = this.cut.merge(e.getId(), 1, Integer::sum);
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * 0.1D + (p.getX() + 0.5D - e.getX()) * 0.2D, Math.max(v.y, 0.0D) * 0.2D, v.z * 0.1D + (p.getZ() + 0.5D - e.getZ()) * 0.2D);
            e.hasImpulse = true;
            if (g == 1) {
                if (e instanceof ContestantEntity c) c.screamNow();
                sl.playSound(null, p, ModSounds.SAW_CUT.get(), SoundSource.BLOCKS, 1.6F, 0.9F + sl.random.nextFloat() * 0.2F);
            }
            if (g % 2 == 0) GoreHandler.fx(sl, e.getX(), p.getY() + 1.05D, e.getZ(), GoreFxPacket.SAW_CUT, 1.0F);
            if (g >= CUT_TICKS) {
                this.cut.remove(e.getId());
                GoreHandler.kill(e, KillStyle.SAW, creditPlayer(), ModDamageTypes.SAW);
                this.kills++;
                sync();
            }
        }
        this.cut.keySet().retainAll(seen);
    }

    @Override
    public void clientTick() {
        if (this.level == null) return;
        this.prevAngle = this.angle;
        this.spin += ((this.running ? 1.0F : 0.0F) - this.spin) * (this.running ? 0.08F : 0.03F);
        this.angle += this.spin * 61.0F;
        if (this.angle > 360.0F) {
            this.angle -= 360.0F;
            this.prevAngle -= 360.0F;
        }
        if (this.running && !this.loopStarted) {
            this.loopStarted = true;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> jp.satsujinki.client.ClientHooks.playTrapLoop(this, ModSounds.SAW_LOOP.get()));
        }
        if (!this.running) this.loopStarted = false;
        RandomSource r = this.level.random;
        if (this.spin > 0.5F && r.nextInt(6) == 0) {
            BlockPos p = this.worldPosition;
            this.level.addParticle(ParticleTypes.SMALL_FLAME, p.getX() + 0.5D, p.getY() + 1.02D, p.getZ() + 0.5D,
                    (r.nextDouble() - 0.5D) * 0.1D, 0.05D, (r.nextDouble() - 0.5D) * 0.1D);
        }
    }
}
