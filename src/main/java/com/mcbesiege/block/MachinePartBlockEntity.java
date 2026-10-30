package com.mcbesiege.block;

import com.mcbesiege.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Stores the part's key-mapper settings and the two-point connectors (brace / spring / rope) that start here. */
public class MachinePartBlockEntity extends BlockEntity {
    private CompoundTag config = new CompoundTag();
    private ListTag connectors = new ListTag();

    public MachinePartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_PART.get(), pos, state);
    }

    public CompoundTag getConfig() { return config; }

    public void setConfig(CompoundTag tag) {
        this.config = tag == null ? new CompoundTag() : tag;
        sync();
    }

    public ListTag getConnectors() { return connectors; }

    public void addConnector(CompoundTag c) {
        // replace an existing connector to the same target
        for (int i = 0; i < connectors.size(); i++) {
            CompoundTag o = connectors.getCompound(i);
            if (o.getInt("dx") == c.getInt("dx") && o.getInt("dy") == c.getInt("dy") && o.getInt("dz") == c.getInt("dz")) {
                connectors.remove(i);
                break;
            }
        }
        connectors.add(c);
        sync();
    }

    public void setConnectors(ListTag list) {
        this.connectors = list == null ? new ListTag() : list;
        sync();
    }

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        config = tag.getCompound("cfg");
        connectors = tag.getList("con", Tag.TAG_COMPOUND);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("cfg", config.copy());
        tag.put("con", connectors.copy());
    }

    /** Data written into blueprints (config + connectors). */
    public CompoundTag saveData() {
        CompoundTag t = new CompoundTag();
        t.put("cfg", config.copy());
        t.put("con", connectors.copy());
        return t;
    }

    public void loadData(CompoundTag t) {
        config = t.getCompound("cfg").copy();
        connectors = t.getList("con", Tag.TAG_COMPOUND).copy();
        sync();
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        if (connectors.isEmpty()) return new AABB(worldPosition);
        return new AABB(worldPosition).inflate(16);
    }
}
