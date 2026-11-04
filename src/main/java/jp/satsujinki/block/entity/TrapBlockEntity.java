package jp.satsujinki.block.entity;

import jp.satsujinki.game.TrapRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/** Shared state of trap machines: owner / last activator (who gets the points), running flag, kill count. */
public abstract class TrapBlockEntity extends BlockEntity {
    @Nullable protected UUID owner;
    @Nullable protected UUID activator;
    protected boolean running;
    protected int kills;

    protected TrapBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract void serverTick();

    public abstract void clientTick();

    /** Right click, host remote. Continuous traps toggle, one-shot traps fire. */
    public abstract void trigger(@Nullable Player by);

    public boolean isContinuous() {
        return false;
    }

    public void onRedstone(boolean powered) {
        if (isContinuous()) setRunning(powered, null);
        else if (powered) trigger(null);
    }

    public boolean isRunning() {
        return this.running;
    }

    public int getKills() {
        return this.kills;
    }

    public void setRunning(boolean run, @Nullable Player by) {
        if (by != null) this.activator = by.getUUID();
        if (run == this.running) return;
        this.running = run;
        onRunningChanged();
        if (this.level != null && !this.level.isClientSide) TrapRegistry.set(this.level, this.worldPosition, run);
        sync();
    }

    protected void onRunningChanged() {
    }

    public void setOwner(UUID id) {
        this.owner = id;
        setChanged();
    }

    /** The player who should be credited for kills by this trap. */
    @Nullable
    public Player creditPlayer() {
        if (this.level == null) return null;
        if (this.activator != null) {
            Player p = this.level.getPlayerByUUID(this.activator);
            if (p != null) return p;
        }
        if (this.owner != null) {
            Player p = this.level.getPlayerByUUID(this.owner);
            if (p != null) return p;
        }
        return this.level.getNearestPlayer(this.worldPosition.getX() + 0.5D, this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D, 48.0D, false);
    }

    protected void sync() {
        setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.owner != null) tag.putUUID("Owner", this.owner);
        if (this.activator != null) tag.putUUID("Activator", this.activator);
        tag.putBoolean("Running", this.running);
        tag.putInt("Kills", this.kills);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.activator = tag.hasUUID("Activator") ? tag.getUUID("Activator") : null;
        this.running = tag.getBoolean("Running");
        this.kills = tag.getInt("Kills");
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

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide && this.running) TrapRegistry.set(this.level, this.worldPosition, true);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide) TrapRegistry.set(this.level, this.worldPosition, false);
    }
}
