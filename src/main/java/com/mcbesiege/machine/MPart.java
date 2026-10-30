package com.mcbesiege.machine;

import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.part.Orient;
import com.mcbesiege.part.PartConfig;
import com.mcbesiege.part.PartType;
import com.mcbesiege.physics.Collider;
import com.mcbesiege.physics.Joint;
import com.mcbesiege.physics.Quat;
import com.mcbesiege.physics.RigidBody;
import com.mcbesiege.physics.V3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** Runtime state of one machine part during simulation. */
public final class MPart {
    /** One rigid piece of a part: the whole part, or the base / head half of a joint part. */
    public static final class Sub {
        public final MPart part;
        public final boolean head;
        public Collider collider;
        public MBody body;
        /** Vector from collider center to the block cell center, in body frame (constant). */
        public V3 colliderToCell = V3.ZERO;
        public BlockState renderState;

        Sub(MPart part, boolean head) {
            this.part = part;
            this.head = head;
        }

        public RigidBody rb() { return body == null ? null : body.rb; }

        /** Cell center offset from the body's COM (body frame), used for rendering. */
        public V3 renderOffset() { return collider.localPos.add(colliderToCell); }

        public V3 worldCellCenter() { return body.rb.localToWorld(renderOffset()); }

        public boolean attached() { return part.alive && !part.debris; }
    }

    public final int index;
    public final PartType type;
    public final BlockState state;
    public final BlockPos rel;
    public final Direction facing;
    public final boolean twist;
    public final PartConfig cfg;
    public final CompoundTag data;
    public final Quat orient;
    public final V3 facingVec;

    public final Sub base;
    public final Sub head;
    public Joint joint;

    public boolean alive = true;
    public boolean debris;
    public boolean jointBroken;
    public boolean charred;
    public double health;
    public double pendingDamage;
    public int flash;
    public boolean burning;
    public int burnTicks;
    public int debrisAge;

    // behaviour state
    public boolean toggled;
    public final boolean[] prevKey = new boolean[4];
    public double value;
    public double value2;
    public int cooldown;
    public int timer;
    public int ammo;
    public double fuel;
    public int fuse = -1;
    public boolean active;
    public boolean used;
    public double autoSign = 1;
    public Joint grab;
    public int grabCooldown;
    public double startY;

    MPart(int index, Blueprint.Entry e) {
        this.index = index;
        this.state = e.state;
        this.type = ((MachinePartBlock) e.state.getBlock()).type;
        this.rel = e.rel;
        this.facing = e.state.getValue(MachinePartBlock.FACING);
        this.twist = e.state.getValue(MachinePartBlock.TWIST);
        this.data = e.data.copy();
        this.cfg = PartConfig.of(type, data.getCompound("cfg"));
        this.orient = Orient.quat(type, facing, twist);
        this.facingVec = Orient.dir(facing);
        this.health = type.hp;
        this.base = new Sub(this, false);
        this.head = type.isJoint() ? new Sub(this, true) : null;
        if (type.isJoint()) {
            base.renderState = state.setValue(MachinePartBlock.SIM_PART, MachinePartBlock.SimPart.BASE);
            head.renderState = state.setValue(MachinePartBlock.SIM_PART, MachinePartBlock.SimPart.HEAD);
        } else {
            base.renderState = state;
        }
        this.ammo = Math.round(cfg.slider("ammo"));
        this.fuel = cfg.slider("fuel") * 20.0;
    }

    /** Twist axis (hinge pin) in world space at assembly. */
    public V3 twistAxis() { return orient.rotate(V3.X); }

    /** World axis of this part's joint: the facing for axial/slider parts, the hinge pin for perpendicular hinges.
     *  A steering hinge's pin is canonical Z, which is vertical whenever it is mounted on a side face (Besiege car steering). */
    public V3 jointAxis() {
        if (type.joint != PartType.JointKind.PERP) return facingVec;
        return type == PartType.STEERING_HINGE ? orient.rotate(V3.Z) : twistAxis();
    }

    public boolean isExplosive() { return type.explosive > 0; }

    public Sub subForFace(Direction face) {
        if (head != null && face == facing) return head;
        return base;
    }

    public boolean keyDown(Machine m, String action) {
        return m.isDown(cfg.keys(action));
    }

    /** Rising edge detection for the n-th action. */
    public boolean pressed(Machine m, String action, int slot) {
        boolean d = keyDown(m, action);
        boolean was = prevKey[slot];
        prevKey[slot] = d;
        return d && !was;
    }
}
