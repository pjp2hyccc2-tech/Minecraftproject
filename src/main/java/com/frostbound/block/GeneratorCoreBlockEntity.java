package com.frostbound.block;

import com.frostbound.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Mirrors the generator state for rendering (the authoritative state lives in the city simulation).
 * The core sits at the boiler's front; the chimney top is {@link #CHIMNEY_TOP} blocks above it.
 */
public class GeneratorCoreBlockEntity extends BlockEntity {
    public static final int CHIMNEY_TOP = 40;

    private int genLevel;
    private boolean running;
    private boolean overload;

    public GeneratorCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.GENERATOR_CORE_BE.get(), pos, state);
    }

    public int genLevel() {
        return genLevel;
    }

    public boolean running() {
        return running;
    }

    public boolean overload() {
        return overload;
    }

    /** Server side: update and push to clients when something changed. */
    public void setState(int level, boolean running, boolean overload) {
        if (level == genLevel && running == this.running && overload == this.overload) {
            return;
        }
        this.genLevel = level;
        this.running = running;
        this.overload = overload;
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("genLevel", genLevel);
        tag.putBoolean("running", running);
        tag.putBoolean("overload", overload);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        genLevel = tag.getInt("genLevel");
        running = tag.getBoolean("running");
        overload = tag.getBoolean("overload");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(8, 2, 8).expandTowards(0, CHIMNEY_TOP + 2, 0);
    }

    /** Client: smoke from the chimney and steam from the vents. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, GeneratorCoreBlockEntity be) {
        if (!be.running || be.genLevel <= 0) {
            return;
        }
        RandomSource r = level.random;
        int plumes = be.genLevel + (be.overload ? 2 : 0);
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() - 2.5;
        double top = pos.getY() + CHIMNEY_TOP;
        if (r.nextInt(4) < Math.min(4, plumes)) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, cx + r.nextGaussian() * 0.6, top,
                    cz + r.nextGaussian() * 0.6, 0.0, 0.07 + 0.02 * be.genLevel, 0.0);
        }
        if (r.nextInt(40) < plumes) {
            double side = r.nextBoolean() ? -5.5 : 6.5;
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + side, pos.getY() + 14, pos.getZ() - 5.5, 0, 0.12, 0);
        }
    }
}
