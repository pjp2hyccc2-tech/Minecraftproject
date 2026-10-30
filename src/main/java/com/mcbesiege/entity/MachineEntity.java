package com.mcbesiege.entity;

import com.mcbesiege.MCBesiege;
import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.machine.MPart;
import com.mcbesiege.machine.Machine;
import com.mcbesiege.machine.MachineManager;
import com.mcbesiege.net.Net;
import com.mcbesiege.part.PartType;
import com.mcbesiege.physics.RigidBody;
import com.mcbesiege.physics.V3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The simulating machine. Server: owns the {@link Machine}; client: holds a {@link MachineView} for rendering. */
public class MachineEntity extends Entity implements IEntityAdditionalSpawnData {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(MachineEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public Machine machine;
    public final MachineView view = new MachineView();
    private CompoundTag pendingLoad;
    private int lastSentVersion = -1;
    private AABB cullBox;

    public MachineEntity(EntityType<? extends MachineEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public void setMachine(Machine m) {
        this.machine = m;
        if (m.owner != null) entityData.set(OWNER, Optional.of(m.owner));
    }

    public UUID owner() { return entityData.get(OWNER).orElse(null); }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER, Optional.empty());
    }

    @Override
    public void tick() {
        if (level().isClientSide) {
            view.clientTick();
            return;
        }
        if (machine == null) {
            if (pendingLoad != null) {
                try {
                    Machine m = Machine.load(this, (ServerLevel) level(), pendingLoad);
                    setMachine(m);
                    m.addToWorld();
                } catch (Exception ex) {
                    MCBesiege.LOGGER.error("[mcbesiege] could not restore machine, resetting it", ex);
                    discard();
                }
                pendingLoad = null;
            } else {
                discard();
            }
            return;
        }
        if (!machine.inWorld) machine.addToWorld();
        if (getY() < level().getMinBuildHeight() - 64) MachineManager.reset(this, true);
    }

    /** Called by the level physics after each step: sends structure (if changed) and body poses to watching players. */
    public void syncAfterStep() {
        if (machine == null) return;
        if (machine.needStructureSync || lastSentVersion != machine.structureVersion) {
            machine.needStructureSync = false;
            lastSentVersion = machine.structureVersion;
            CompoundTag t = machine.structureTag();
            t.putInt("id", getId());
            Net.toTracking(this, "mstruct", t);
        }
        int n = machine.bodies.size();
        double[] poses = new double[n * 7];
        for (int i = 0; i < n; i++) {
            RigidBody rb = machine.bodies.get(i).rb;
            poses[i * 7] = rb.pos.x;
            poses[i * 7 + 1] = rb.pos.y;
            poses[i * 7 + 2] = rb.pos.z;
            poses[i * 7 + 3] = rb.rot.w;
            poses[i * 7 + 4] = rb.rot.x;
            poses[i * 7 + 5] = rb.rot.y;
            poses[i * 7 + 6] = rb.rot.z;
        }
        Net.sendState(this, machine.structureVersion, poses, machine.partFlags());
        for (Entity p : getPassengers()) positionRider(p);
    }

    // ------------------------------------------------------------------------------------------------ riding

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < seatCount();
    }

    private int seatCount() {
        if (machine != null) return machine.seats().size();
        int n = 0;
        for (MachineView.SubView s : view.subs) if (isSeat(s)) n++;
        return n;
    }

    private static boolean isSeat(MachineView.SubView s) {
        return s.state.getBlock() instanceof MachinePartBlock b && b.type == PartType.SEAT;
    }

    /** World position of the k-th seat (top surface center). */
    public V3 seatPos(int k, float pt) {
        if (machine != null) {
            List<MPart> seats = machine.seats();
            if (k < 0 || k >= seats.size()) return null;
            MPart.Sub s = seats.get(k).base;
            if (s.body == null) return null;
            return s.body.rb.localToWorld(s.renderOffset());
        }
        int i = 0;
        double[] pose = new double[7];
        for (MachineView.SubView s : view.subs) {
            if (!isSeat(s)) continue;
            if (i++ == k) {
                if (!view.pose(s.body, pt, pose)) return null;
                double[] w = MachineView.transform(pose, s.ox, s.oy, s.oz);
                return new V3(w[0], w[1], w[2]);
            }
        }
        return null;
    }

    @Override
    public void positionRider(Entity passenger, MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        int k = getPassengers().indexOf(passenger);
        V3 p = seatPos(k, 1f);
        if (p == null) {
            move.accept(passenger, getX(), getY() + 1, getZ());
            return;
        }
        move.accept(passenger, p.x, p.y + passenger.getMyRidingOffset() + 0.15, p.z);
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.5;
    }

    // ------------------------------------------------------------------------------------------------ misc

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean ignoreExplosion() { return true; }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 320 * 320; }

    public void setCullBox(AABB box) { this.cullBox = box; }

    @Override
    public AABB getBoundingBoxForCulling() {
        return cullBox != null ? cullBox : super.getBoundingBoxForCulling();
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide && machine != null) machine.addToWorld();
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!level().isClientSide && machine != null) machine.removeFromWorld();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("machine")) pendingLoad = tag.getCompound("machine");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (machine != null) {
            try {
                tag.put("machine", machine.save());
            } catch (Exception ex) {
                MCBesiege.LOGGER.error("[mcbesiege] machine save failed", ex);
            }
        } else if (pendingLoad != null) {
            tag.put("machine", pendingLoad);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buf) {
        buf.writeNbt(machine != null ? machine.structureTag() : new CompoundTag());
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        view.applyStructure(buf.readNbt());
    }

    public List<Entity> riders() { return new ArrayList<>(getPassengers()); }
}
