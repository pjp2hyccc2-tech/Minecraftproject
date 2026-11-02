package com.clockworkdiver.block;

import com.clockworkdiver.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Parameters of one animated mechanism. Synced to clients; animated purely client side from game time. */
public class MechanismBlockEntity extends BlockEntity {
    public static final int GEAR = 0;
    public static final int ESCAPE_WHEEL = 1;
    public static final int BALANCE = 2;
    public static final int PENDULUM = 3;
    public static final int SPRING = 4;
    public static final int CLOCK_FACE = 5;

    public static final int AXIS_Y = 0;
    public static final int AXIS_X = 1;
    public static final int AXIS_Z = 2;

    public static final int COLOR_BRASS = 0;
    public static final int COLOR_STEEL = 1;
    public static final int COLOR_COPPER = 2;
    public static final int COLOR_RUST = 3;
    public static final int COLOR_GOLD = 4;
    public static final int COLOR_DARK = 5;

    public int kind = GEAR;
    public float radius = 3.0f;
    public int teeth = 24;
    public float thickness = 0.5f;
    public int axis = AXIS_Y;
    /** Degrees turned per beat (gears) or swing amplitude in degrees (pendulum, balance). */
    public float speed = 6.0f;
    public float phase = 0.0f;
    public int color = COLOR_BRASS;
    public boolean running = true;
    /** Teeth missing from an escape wheel. */
    public int missing = 0;
    /** Mainspring winding 0..1. */
    public float progress = 1.0f;
    public int period = 20;
    /** Tick style motion (jump on each beat) instead of continuous rotation. */
    public boolean stepped = true;
    /** Faces the negative direction of its axis (clock faces). */
    public boolean flip = false;
    public float offsetX;
    public float offsetY;
    public float offsetZ;

    public MechanismBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECHANISM.get(), pos, state);
    }

    public MechanismBlockEntity gear(float radius, int teeth, float thickness, int axis, float speed, int color) {
        this.kind = GEAR;
        this.radius = radius;
        this.teeth = teeth;
        this.thickness = thickness;
        this.axis = axis;
        this.speed = speed;
        this.color = color;
        return this;
    }

    public MechanismBlockEntity kind(int kind) {
        this.kind = kind;
        return this;
    }

    public MechanismBlockEntity running(boolean running) {
        this.running = running;
        return this;
    }

    public MechanismBlockEntity period(int period) {
        this.period = Math.max(1, period);
        return this;
    }

    public MechanismBlockEntity offset(float x, float y, float z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        return this;
    }

    public MechanismBlockEntity flip(boolean flip) {
        this.flip = flip;
        return this;
    }

    public MechanismBlockEntity stepped(boolean stepped) {
        this.stepped = stepped;
        return this;
    }

    public MechanismBlockEntity phase(float phase) {
        this.phase = phase;
        return this;
    }

    /** Saves and sends the new parameters to every client tracking the chunk. */
    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Kind", kind);
        tag.putFloat("Radius", radius);
        tag.putInt("Teeth", teeth);
        tag.putFloat("Thickness", thickness);
        tag.putInt("Axis", axis);
        tag.putFloat("Speed", speed);
        tag.putFloat("Phase", phase);
        tag.putInt("Color", color);
        tag.putBoolean("Running", running);
        tag.putInt("Missing", missing);
        tag.putFloat("Progress", progress);
        tag.putInt("Period", period);
        tag.putBoolean("Stepped", stepped);
        tag.putBoolean("Flip", flip);
        tag.putFloat("OffX", offsetX);
        tag.putFloat("OffY", offsetY);
        tag.putFloat("OffZ", offsetZ);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        kind = tag.getInt("Kind");
        radius = tag.contains("Radius") ? tag.getFloat("Radius") : 3.0f;
        teeth = tag.contains("Teeth") ? tag.getInt("Teeth") : 24;
        thickness = tag.contains("Thickness") ? tag.getFloat("Thickness") : 0.5f;
        axis = tag.getInt("Axis");
        speed = tag.getFloat("Speed");
        phase = tag.getFloat("Phase");
        color = tag.getInt("Color");
        running = !tag.contains("Running") || tag.getBoolean("Running");
        missing = tag.getInt("Missing");
        progress = tag.contains("Progress") ? tag.getFloat("Progress") : 1.0f;
        period = Math.max(1, tag.contains("Period") ? tag.getInt("Period") : 20);
        stepped = !tag.contains("Stepped") || tag.getBoolean("Stepped");
        flip = tag.getBoolean("Flip");
        offsetX = tag.getFloat("OffX");
        offsetY = tag.getFloat("OffY");
        offsetZ = tag.getFloat("OffZ");
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
        double r = radius + 2.0 + Math.max(Math.abs(offsetX), Math.max(Math.abs(offsetY), Math.abs(offsetZ)));
        if (kind == PENDULUM) r += radius * 2.0;
        return new AABB(worldPosition).inflate(r);
    }
}
