package com.eternalrealm.block.forge;

import com.eternalrealm.world.town.SecretDoorMotion;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Motion of one half of a {@link SecretDoorBlock}: what it is doing ({@code Anim}: 0 idle, 1 opening, 2 closing, see
 * {@link SecretDoorMotion}) and the game time that started ({@code Start}). Both halves carry the same values; the
 * client draws each half's sliding panel from them.
 */
public class SecretDoorBlockEntity extends SyncedBlockEntity {
    private int anim = SecretDoorMotion.IDLE;
    private long start = -1000L;

    /** Client: the way the panel slides, +1 to the right as seen from the front, -1 to the left, 0 = not chosen yet. */
    public int clientSide;

    public SecretDoorBlockEntity(BlockPos pos, BlockState state) {
        super(TownGimmickBlocks.SECRET_DOOR_ENTITY.get(), pos, state);
    }

    public int anim() {
        return anim;
    }

    public long start() {
        return start;
    }

    /** Sets the motion and sends it to the players nearby. */
    void setAnim(int anim, long start) {
        this.anim = anim;
        this.start = start;
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Anim", Tag.TAG_ANY_NUMERIC)) {
            int value = tag.getByte("Anim");
            anim = value == SecretDoorMotion.OPENING || value == SecretDoorMotion.CLOSING ? value : SecretDoorMotion.IDLE;
        }
        if (tag.contains("Start", Tag.TAG_ANY_NUMERIC)) {
            start = tag.getLong("Start");
        }
        clientSide = 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putByte("Anim", (byte) anim);
        tag.putLong("Start", start);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.1, 0.1, 1.1);
    }
}
