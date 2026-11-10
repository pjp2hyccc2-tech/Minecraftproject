package com.eternalrealm.block.forge;

import com.eternalrealm.world.town.TowerClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * A tower clock dial of radius {@code Size} blocks ({@code {Size:2.5f}}), drawn by the client in front of the block's
 * {@code facing} side with hands showing the world's time. At the top of each hour the bell tolls the hour (once per
 * tower: neighbouring dials within 48 blocks share one bell).
 */
public class ClockFaceBlockEntity extends SyncedBlockEntity {
    private static final int STRIKE_INTERVAL = 44;
    private static final List<long[]> RECENT_CHIMES = new ArrayList<>();

    private float size = 2.5F;

    private long lastHourIndex = Long.MIN_VALUE;
    private int loadedTicks;
    private int strikesLeft;
    private int strikeCooldown;

    public ClockFaceBlockEntity(BlockPos pos, BlockState state) {
        super(ERBlocks.CLOCK_FACE_ENTITY.get(), pos, state);
    }

    public float size() {
        return size;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        size = clampedFloat(tag, "Size", size, 0.75F, 6.0F);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("Size", size);
    }

    /** Client tick: toll the hours. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, ClockFaceBlockEntity clock) {
        clock.loadedTicks++;
        long dayTime = level.getDayTime();
        long hourIndex = Math.floorDiv(dayTime, TowerClock.TICKS_PER_HOUR);
        if (clock.lastHourIndex == Long.MIN_VALUE) {
            clock.lastHourIndex = hourIndex;
        } else if (hourIndex != clock.lastHourIndex) {
            boolean ticking = hourIndex == clock.lastHourIndex + 1;   // not after /time set or sleeping
            clock.lastHourIndex = hourIndex;
            if (ticking && clock.loadedTicks > 40 && claimChime(pos, hourIndex)) {
                clock.strikesLeft = TowerClock.strikes(TowerClock.hour(dayTime));
                clock.strikeCooldown = 10;
            }
        }
        if (clock.strikesLeft > 0 && --clock.strikeCooldown <= 0) {
            clock.strikesLeft--;
            clock.strikeCooldown = STRIKE_INTERVAL;
            Direction facing = state.getValue(FacadeMechanismBlock.FACING);
            double x = pos.getX() + 0.5 + facing.getStepX() * 0.6;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.6;
            float volume = 3.0F + clock.size;
            level.playLocalSound(x, y, z, SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, volume, 0.5F, false);
            level.playLocalSound(x, y, z, SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, volume * 0.4F, 0.6F, false);
        }
    }

    private static synchronized boolean claimChime(BlockPos pos, long hourIndex) {
        RECENT_CHIMES.removeIf(chime -> chime[2] < hourIndex - 1 || chime[2] > hourIndex + 1);
        for (long[] chime : RECENT_CHIMES) {
            if (chime[2] == hourIndex && Math.abs(chime[0] - pos.getX()) < 48 && Math.abs(chime[1] - pos.getZ()) < 48) {
                return false;
            }
        }
        RECENT_CHIMES.add(new long[]{pos.getX(), pos.getZ(), hourIndex});
        return true;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(size + 1.0);
    }
}
