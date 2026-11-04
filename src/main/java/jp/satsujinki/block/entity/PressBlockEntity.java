package jp.satsujinki.block.entity;

import jp.satsujinki.block.SpikeBedBlock;
import jp.satsujinki.entity.ContestantEntity;
import jp.satsujinki.game.GoreHandler;
import jp.satsujinki.game.KillStyle;
import jp.satsujinki.network.GoreFxPacket;
import jp.satsujinki.registry.ModBlockEntities;
import jp.satsujinki.registry.ModDamageTypes;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/** 剣山プレス: hiss, slam a 3x3 steel plate onto the floor (spikes!), hold, retract slowly to reveal the mess. */
public class PressBlockEntity extends TrapBlockEntity {
    public static final int IDLE = 0, CHARGE = 1, SLAM = 2, HOLD = 3, RETRACT = 4;
    public static final int CHARGE_T = 26, SLAM_T = 5, HOLD_T = 34, RETRACT_T = 70;
    public static final float PLATE_THICK = 0.25F;

    public int phase = IDLE;
    public int phaseTicks;
    public float travel;
    public boolean spikes;
    public boolean bloody;
    // client
    public int clientTicks;

    public PressBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRESS.get(), pos, state);
    }

    @Override
    public void trigger(@Nullable Player by) {
        if (this.phase != IDLE || this.level == null) return;
        if (by != null) this.activator = by.getUUID();
        computeTravel();
        setPhase(CHARGE);
    }

    private void computeTravel() {
        this.travel = 0.0F;
        this.spikes = false;
        for (int d = 1; d <= 12; d++) {
            BlockPos p = this.worldPosition.below(d);
            BlockState st = this.level.getBlockState(p);
            if (st.getBlock() instanceof SpikeBedBlock) {
                this.spikes = true;
                this.travel = (float) (this.worldPosition.getY() - PLATE_THICK - (p.getY() + 0.75D));
                break;
            }
            VoxelShape shape = st.getCollisionShape(this.level, p);
            if (!shape.isEmpty()) {
                this.travel = (float) (this.worldPosition.getY() - PLATE_THICK - (p.getY() + shape.max(Direction.Axis.Y)));
                break;
            }
            if (d == 12) this.travel = 12.0F - PLATE_THICK;
        }
        this.travel = Math.max(0.0F, this.travel);
    }

    private void setPhase(int p) {
        this.phase = p;
        this.phaseTicks = 0;
        sync();
    }

    public double floorTop() {
        return this.worldPosition.getY() - PLATE_THICK - this.travel;
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = this.worldPosition;
        return new AABB(p.getX() - 2.0D, p.getY() - 13.0D, p.getZ() - 2.0D, p.getX() + 3.0D, p.getY() + 1.0D, p.getZ() + 3.0D);
    }

    @Override
    public void serverTick() {
        if (this.phase == IDLE || !(this.level instanceof ServerLevel sl)) return;
        this.phaseTicks++;
        switch (this.phase) {
            case CHARGE -> {
                if (this.phaseTicks == 1) sl.playSound(null, this.worldPosition, ModSounds.PRESS_CHARGE.get(), SoundSource.BLOCKS, 1.8F, 1.0F);
                if (this.phaseTicks >= CHARGE_T) setPhase(SLAM);
            }
            case SLAM -> {
                if (this.phaseTicks >= SLAM_T) {
                    impact(sl);
                    setPhase(HOLD);
                }
            }
            case HOLD -> {
                if (this.phaseTicks >= HOLD_T) {
                    sl.playSound(null, this.worldPosition, ModSounds.PRESS_RETRACT.get(), SoundSource.BLOCKS, 1.4F, 1.0F);
                    setPhase(RETRACT);
                }
            }
            default -> {
                if (this.phaseTicks >= RETRACT_T) setPhase(IDLE);
            }
        }
    }

    private void impact(ServerLevel sl) {
        double floor = floorTop();
        BlockPos p = this.worldPosition;
        AABB zone = new AABB(p.getX() - 1.0D, floor - 0.6D, p.getZ() - 1.0D, p.getX() + 2.0D, p.getY(), p.getZ() + 2.0D);
        List<LivingEntity> victims = sl.getEntitiesOfClass(LivingEntity.class, zone, ContestantEntity::isTrapVictim);
        Player credit = creditPlayer();
        for (LivingEntity e : victims) {
            if (e instanceof ContestantEntity c && c.isHeld()) continue;
            GoreHandler.kill(e, KillStyle.PRESS, credit, ModDamageTypes.PRESS, floor, this.spikes, null);
            this.kills++;
        }
        if (!victims.isEmpty()) this.bloody = true;
        // push players out instead of killing the host
        for (Player pl : sl.getEntitiesOfClass(Player.class, zone)) {
            Vec3 away = pl.position().subtract(p.getX() + 0.5D, pl.getY(), p.getZ() + 0.5D).normalize().scale(0.9D);
            pl.push(away.x, 0.3D, away.z);
            pl.hurtMarked = true;
        }
        sl.playSound(null, p.getX() + 0.5D, floor, p.getZ() + 0.5D, ModSounds.PRESS_SLAM.get(), SoundSource.BLOCKS, 3.0F, 0.9F + sl.random.nextFloat() * 0.15F);
        GoreHandler.fx(sl, p.getX() + 0.5D, floor + 0.1D, p.getZ() + 0.5D, GoreFxPacket.PRESS_IMPACT, 1.0F + victims.size());
    }

    /** Client: plate offset below the head (blocks), interpolated. */
    public float plateOffset(float partial) {
        float t = this.clientTicks + partial;
        return switch (this.phase) {
            case CHARGE -> (float) Math.max(0.0D, Math.sin(t * 2.7F) * 0.02D);
            case SLAM -> {
                float k = Math.min(1.0F, t / SLAM_T);
                yield this.travel * k * k;
            }
            case HOLD -> this.travel;
            case RETRACT -> {
                float k = Math.min(1.0F, t / RETRACT_T);
                float e = k * k * (3.0F - 2.0F * k);
                yield this.travel * (1.0F - e);
            }
            default -> 0.0F;
        };
    }

    @Override
    public void clientTick() {
        if (this.level == null) return;
        this.clientTicks++;
        RandomSource r = this.level.random;
        if (this.phase == CHARGE && r.nextInt(2) == 0) {
            BlockPos p = this.worldPosition;
            this.level.addParticle(ParticleTypes.CLOUD, p.getX() + 0.5D + (r.nextDouble() - 0.5D) * 1.2D, p.getY() + 0.3D,
                    p.getZ() + 0.5D + (r.nextDouble() - 0.5D) * 1.2D, (r.nextDouble() - 0.5D) * 0.2D, -0.02D, (r.nextDouble() - 0.5D) * 0.2D);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Phase", this.phase);
        tag.putInt("PhaseTicks", this.phaseTicks);
        tag.putFloat("Travel", this.travel);
        tag.putBoolean("Spikes", this.spikes);
        tag.putBoolean("Bloody", this.bloody);
    }

    @Override
    public void load(CompoundTag tag) {
        int old = this.phase;
        super.load(tag);
        this.phase = tag.getInt("Phase");
        this.phaseTicks = tag.getInt("PhaseTicks");
        this.travel = tag.getFloat("Travel");
        this.spikes = tag.getBoolean("Spikes");
        this.bloody = tag.getBoolean("Bloody");
        if (old != this.phase) this.clientTicks = this.phaseTicks;
    }
}
