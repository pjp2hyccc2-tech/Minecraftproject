package jp.satsujinki.block.entity;

import jp.satsujinki.block.GuillotineBlock;
import jp.satsujinki.entity.ContestantEntity;
import jp.satsujinki.game.GoreHandler;
import jp.satsujinki.game.KillStyle;
import jp.satsujinki.registry.ModBlockEntities;
import jp.satsujinki.registry.ModDamageTypes;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** ギロチン: locks a contestant in the lunette; triggering drops the blade. */
public class GuillotineBlockEntity extends TrapBlockEntity {
    public static final int IDLE = 0, DROP = 1, HOLD = 2, RAISE = 3;
    public static final int DROP_T = 5, HOLD_T = 26, RAISE_T = 50;
    public static final float BLADE_TRAVEL = 1.15F;

    public int phase = IDLE;
    public int phaseTicks;
    public boolean bloody;
    @Nullable private UUID lockedId;
    // client
    public int clientTicks;

    public GuillotineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GUILLOTINE.get(), pos, state);
    }

    public Direction facing() {
        BlockState st = getBlockState();
        return st.hasProperty(GuillotineBlock.FACING) ? st.getValue(GuillotineBlock.FACING) : Direction.NORTH;
    }

    @Override
    public void trigger(@Nullable Player by) {
        if (this.phase != IDLE || !(this.level instanceof ServerLevel sl)) return;
        if (by != null) this.activator = by.getUUID();
        sl.playSound(null, this.worldPosition, ModSounds.GUILLOTINE_DROP.get(), SoundSource.BLOCKS, 1.6F, 1.0F);
        setPhase(DROP);
    }

    private void setPhase(int p) {
        this.phase = p;
        this.phaseTicks = 0;
        sync();
    }

    private AABB slot() {
        BlockPos p = this.worldPosition;
        return new AABB(p.getX() + 0.1D, p.getY(), p.getZ() + 0.1D, p.getX() + 0.9D, p.getY() + 2.0D, p.getZ() + 0.9D);
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = this.worldPosition;
        return new AABB(p.getX() - 1.0D, p.getY(), p.getZ() - 1.0D, p.getX() + 2.0D, p.getY() + 3.5D, p.getZ() + 2.0D);
    }

    @Override
    public void serverTick() {
        if (!(this.level instanceof ServerLevel sl)) return;
        if (this.lockedId != null) {
            Entity e = sl.getEntity(this.lockedId);
            if (!(e instanceof ContestantEntity c) || !c.isAlive() || !c.isLockedAt(this.worldPosition)) this.lockedId = null;
        }
        if (this.phase == IDLE && this.lockedId == null && sl.getGameTime() % 4L == 0L) {
            for (ContestantEntity c : sl.getEntitiesOfClass(ContestantEntity.class, slot(), e -> e.isAlive() && !e.isHeld() && !e.isLocked())) {
                c.lockInto(this.worldPosition, facing().toYRot());
                this.lockedId = c.getUUID();
                sl.playSound(null, this.worldPosition, ModSounds.GUILLOTINE_LOCK.get(), SoundSource.BLOCKS, 1.2F, 1.0F);
                break;
            }
        }
        if (this.phase == IDLE) return;
        this.phaseTicks++;
        switch (this.phase) {
            case DROP -> {
                if (this.phaseTicks >= DROP_T) {
                    chop(sl);
                    setPhase(HOLD);
                }
            }
            case HOLD -> {
                if (this.phaseTicks >= HOLD_T) setPhase(RAISE);
            }
            default -> {
                if (this.phaseTicks >= RAISE_T) setPhase(IDLE);
            }
        }
    }

    private void chop(ServerLevel sl) {
        sl.playSound(null, this.worldPosition, ModSounds.GUILLOTINE_CHOP.get(), SoundSource.BLOCKS, 2.0F, 0.95F + sl.random.nextFloat() * 0.1F);
        List<LivingEntity> victims = sl.getEntitiesOfClass(LivingEntity.class, slot(), ContestantEntity::isTrapVictim);
        Player credit = creditPlayer();
        for (LivingEntity e : victims) {
            if (e instanceof ContestantEntity c && c.isHeld()) continue;
            if (e instanceof ContestantEntity c) c.unlock();
            GoreHandler.kill(e, KillStyle.GUILLOTINE, credit, ModDamageTypes.GUILLOTINE, Double.NaN, false, facing());
            this.kills++;
            this.bloody = true;
        }
        this.lockedId = null;
    }

    /** Client: how far the blade has dropped (blocks). */
    public float bladeDrop(float partial) {
        float t = this.clientTicks + partial;
        return switch (this.phase) {
            case DROP -> {
                float k = Math.min(1.0F, t / DROP_T);
                yield BLADE_TRAVEL * k * k;
            }
            case HOLD -> BLADE_TRAVEL;
            case RAISE -> {
                float k = Math.min(1.0F, t / RAISE_T);
                yield BLADE_TRAVEL * (1.0F - k * k * (3.0F - 2.0F * k));
            }
            default -> 0.0F;
        };
    }

    @Override
    public void clientTick() {
        this.clientTicks++;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Phase", this.phase);
        tag.putInt("PhaseTicks", this.phaseTicks);
        tag.putBoolean("Bloody", this.bloody);
    }

    @Override
    public void load(CompoundTag tag) {
        int old = this.phase;
        super.load(tag);
        this.phase = tag.getInt("Phase");
        this.phaseTicks = tag.getInt("PhaseTicks");
        this.bloody = tag.getBoolean("Bloody");
        if (old != this.phase) this.clientTicks = this.phaseTicks;
    }
}
