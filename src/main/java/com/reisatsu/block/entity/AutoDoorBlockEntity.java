package com.reisatsu.block.entity;

import com.reisatsu.block.AutoDoorBlock;
import com.reisatsu.block.Props;
import com.reisatsu.registry.ModBlockEntities;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.FixtureEvents;
import com.reisatsu.world.Fixtures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The bottom block of an automatic door panel. Client: the slide animation (0 shut .. 1 open, ≈0.7 s). Server, on
 * the left panel of a pair (the master): the sensor — any living thing within ~2 blocks in front of or behind the
 * doors opens them while they are enabled (key switch + DOOR circuit) and not locked — plus forced openings by the
 * game ({@link Fixtures#openAutoDoor}), the motor and the chime on every opening, and closing once the sensor is clear.
 */
public class AutoDoorBlockEntity extends BlockEntity {
    public static final int SLIDE_TICKS = 14;
    private static final int CLOSE_DELAY = 30;

    public float progress, prevProgress;
    private long holdUntil;
    private int clearTicks;
    private int scan;

    public AutoDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTO_DOOR.get(), pos, state);
    }

    public boolean isMaster() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof AutoDoorBlock && s.getValue(Props.SIDE) == Props.Side.LEFT;
    }

    /** Bottom block of the other panel of the pair. */
    public BlockPos partner() {
        BlockState s = getBlockState();
        Direction f = s.getValue(AutoDoorBlock.FACING);
        return s.getValue(Props.SIDE) == Props.Side.LEFT ? worldPosition.relative(f.getCounterClockWise()) : worldPosition.relative(f.getClockWise());
    }

    public boolean isOpen() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof AutoDoorBlock && s.getValue(Props.OPEN);
    }

    public void clientTick() {
        prevProgress = progress;
        float target = isOpen() ? 1.0f : 0.0f;
        float step = 1.0f / SLIDE_TICKS;
        if (progress < target) progress = Math.min(target, progress + step);
        else if (progress > target) progress = Math.max(target, progress - step);
    }

    /** The game opens the pair now and keeps it open for at least holdTicks (ignored while locked). */
    public void forceOpen(int holdTicks) {
        if (level == null) return;
        holdUntil = Math.max(holdUntil, level.getGameTime() + Math.max(1, holdTicks));
        if (!isOpen() && level instanceof ServerLevel sl && !Fixtures.autoDoorsLocked(sl)) open(sl, null);
    }

    /** Closes immediately (reset / lock). */
    public void forceClose(boolean sound) {
        holdUntil = 0;
        clearTicks = 0;
        if (isOpen() && level instanceof ServerLevel sl) close(sl, sound);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl) || !isMaster()) return;
        boolean locked = Fixtures.autoDoorsLocked(sl);
        if (locked) {
            if (isOpen()) close(sl, true);
            holdUntil = 0;
            return;
        }
        boolean held = holdUntil > sl.getGameTime();
        Entity seen = null;
        if (Fixtures.autoDoorsEnabled(sl) && (++scan % 2 == 0 || isOpen())) {
            seen = sensed(sl);
        }
        if (held || seen != null) {
            clearTicks = 0;
            if (!isOpen()) open(sl, seen);
        } else if (isOpen()) {
            if (++clearTicks >= CLOSE_DELAY) close(sl, true);
        }
    }

    @Nullable
    private Entity sensed(ServerLevel sl) {
        AABB box = sensorBox();
        List<LivingEntity> list = sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator());
        return list.isEmpty() ? null : list.get(0);
    }

    /** Both panels, 2.5 blocks to each side of the door line, 3 high. */
    public AABB sensorBox() {
        BlockPos other = partner();
        Direction f = getBlockState().getValue(AutoDoorBlock.FACING);
        AABB a = new AABB(worldPosition).minmax(new AABB(other)).expandTowards(0, 2, 0);
        double dx = f.getAxis() == Direction.Axis.X ? 2.5 : 0;
        double dz = f.getAxis() == Direction.Axis.Z ? 2.5 : 0;
        return a.inflate(dx, 0, dz);
    }

    private Vec3 centre() {
        return Vec3.atCenterOf(worldPosition).add(Vec3.atCenterOf(partner())).scale(0.5).add(0, 0.5, 0);
    }

    private void open(ServerLevel sl, @Nullable Entity cause) {
        AutoDoorBlock.setPanelOpen(sl, worldPosition, true);
        AutoDoorBlock.setPanelOpen(sl, partner(), true);
        Vec3 c = centre();
        sl.playSound(null, c.x, c.y, c.z, ModSounds.DOOR_AUTO_OPEN.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
        sl.playSound(null, c.x, c.y + 1.2, c.z, ModSounds.DOOR_CHIME.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        FixtureEvents.LISTENER.noise(sl, c, 16.0f, "auto_door", cause);
        FixtureEvents.LISTENER.autoDoorOpened(sl, Fixtures.autoDoorName(worldPosition), cause);
    }

    private void close(ServerLevel sl, boolean sound) {
        AutoDoorBlock.setPanelOpen(sl, worldPosition, false);
        AutoDoorBlock.setPanelOpen(sl, partner(), false);
        clearTicks = 0;
        if (sound) {
            Vec3 c = centre();
            sl.playSound(null, c.x, c.y, c.z, ModSounds.DOOR_AUTO_CLOSE.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5, 0, 1.5).expandTowards(0, 3, 0);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
