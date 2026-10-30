package com.nihonisshu.tabi.entity;

import com.nihonisshu.tabi.data.CarDesign;
import com.nihonisshu.tabi.data.Route;
import com.nihonisshu.tabi.game.TicketOffice;
import com.nihonisshu.tabi.network.ClientHooksProxy;
import com.nihonisshu.tabi.registry.TabiEntities;
import com.nihonisshu.tabi.registry.TabiSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * A whole train (all cars) or a bus, moving along a {@link Route}. Players right click it to take a seat.
 * Position along the line is the synced float S (train centre); clients compute poses locally for smooth motion.
 */
public class VehicleEntity extends Entity {
    public static final byte STOPPED = 0, DEPARTING = 1, RUNNING = 2;

    private static final EntityDataAccessor<String> ROUTE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> S = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DIR = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> STATION = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> SEATS = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private int timer;
    private boolean announcedDeparture;
    // client side interpolation
    public float prevS, curS;
    private boolean clientInit;
    private byte lastState = -1;
    public Object clientSound;

    public VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static VehicleEntity spawnAt(ServerLevel level, Route route, Route.Station station) {
        VehicleEntity v = TabiEntities.VEHICLE.get().create(level);
        if (v == null) return null;
        v.entityData.set(ROUTE, route.id);
        v.entityData.set(S, (float) station.s);
        int dir = station.index == route.stations.size() - 1 ? -1 : 1;
        v.entityData.set(DIR, (byte) dir);
        v.entityData.set(STATE, STOPPED);
        v.entityData.set(STATION, station.index);
        Vec3 p = route.pos(station.s);
        v.setPos(p.x, p.y, p.z);
        level.addFreshEntity(v);
        return v;
    }

    // ------------------------------------------------------------------ accessors

    public Route route() {
        return Route.get(entityData.get(ROUTE));
    }

    public CarDesign design() {
        Route r = route();
        return r == null ? null : CarDesign.get(r.design);
    }

    public String routeId() { return entityData.get(ROUTE); }
    public double getS() { return entityData.get(S); }
    public float getSpeed() { return entityData.get(SPEED); }
    public int getDir() { return entityData.get(DIR); }
    public byte getState() { return entityData.get(STATE); }
    public int getStationIndex() { return entityData.get(STATION); }
    public boolean doorsOpen() { return getState() == STOPPED; }

    public Route.Station currentStation() {
        Route r = route();
        int i = getStationIndex();
        if (r == null || i < 0 || i >= r.stations.size()) return null;
        return r.stations.get(i);
    }

    public int carCount() {
        Route r = route();
        return r == null ? 1 : Math.max(1, r.cars);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ROUTE, "");
        entityData.define(S, 0f);
        entityData.define(SPEED, 0f);
        entityData.define(DIR, (byte) 1);
        entityData.define(STATE, STOPPED);
        entityData.define(STATION, 0);
        entityData.define(SEATS, new CompoundTag());
    }

    // ------------------------------------------------------------------ geometry

    public static final class CarPose {
        public Vec3 center;
        public double yaw, pitch;
    }

    /** pose of car i at train position s (train centre). Car 0 sits on the +s side. */
    public CarPose carPose(double s, int i) {
        Route r = route();
        CarDesign d = design();
        CarPose p = new CarPose();
        if (r == null || d == null) {
            p.center = position();
            return p;
        }
        int n = carCount();
        int dir = getDir();
        double cs = s + ((n - 1) / 2.0 - i) * r.carSpacing;
        double half = d.length * 0.35;
        Vec3 front = r.pos(cs + dir * half);
        Vec3 back = r.pos(cs - dir * half);
        p.center = front.add(back).scale(0.5);
        double fx = front.x - back.x, fy = front.y - back.y, fz = front.z - back.z;
        double hz = Math.sqrt(fx * fx + fz * fz);
        p.yaw = Math.atan2(fx, fz);
        p.pitch = Math.atan2(fy, Math.max(hz, 1e-6));
        return p;
    }

    public static Vec3 toWorld(CarPose p, double lx, double ly, double lz) {
        double phi = -p.pitch;
        double cy = Math.cos(phi), sy = Math.sin(phi);
        double y1 = ly * cy - lz * sy, z1 = ly * sy + lz * cy;
        double ct = Math.cos(p.yaw), st = Math.sin(p.yaw);
        double x2 = lx * ct + z1 * st, z2 = -lx * st + z1 * ct;
        return p.center.add(x2, y1, z2);
    }

    private double sForGeometry() {
        return level().isClientSide && clientInit ? curS : getS();
    }

    public int seatCount() {
        CarDesign d = design();
        return d == null ? 0 : d.seats.size() * carCount();
    }

    public Vec3 seatPos(int seat, double s) {
        CarDesign d = design();
        if (d == null || d.seats.isEmpty()) return position();
        int per = d.seats.size();
        int car = Math.min(carCount() - 1, seat / per);
        Vec3 l = d.seats.get(seat % per);
        return toWorld(carPose(s, car), l.x, l.y, l.z);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        Route r = route();
        if (r == null) {
            if (!level().isClientSide && tickCount > 40) discard();
            return;
        }
        if (level().isClientSide) {
            clientTick(r);
        } else {
            serverTick(r);
        }
    }

    private void clientTick(Route r) {
        float s = entityData.get(S);
        if (!clientInit) {
            prevS = curS = s;
            clientInit = true;
        } else {
            prevS = curS;
            curS = s;
        }
        Vec3 p = r.pos(curS);
        setPos(p.x, p.y, p.z);
        byte st = getState();
        if (lastState != -1 && st != lastState) {
            ClientHooksProxy.vehicleStateChanged(this, lastState, st);
        }
        lastState = st;
        ClientHooksProxy.vehicleTick(this);
    }

    private void serverTick(Route r) {
        double s = getS();
        double v = getSpeed();
        int dir = getDir();
        byte state = getState();
        boolean hasPlayer = getPassengers().stream().anyMatch(e -> e instanceof Player);
        Route.Station here = currentStation();
        switch (state) {
            case STOPPED -> {
                timer++;
                boolean terminalWait = here != null && here.terminal;
                int dwell = terminalWait ? (hasPlayer ? 200 : Integer.MAX_VALUE) : 300;
                if (hasPlayer && dwell != Integer.MAX_VALUE && timer == dwell - 120 && !announcedDeparture) {
                    announcedDeparture = true;
                    Route.Station next = r.nextStation(s, dir);
                    announce("まもなく発車します。" + (next != null ? "次は " + next.name + " に止まります。" : ""));
                    level().playSound(null, getX(), getY(), getZ(), TabiSounds.MELODY.get(), SoundSource.NEUTRAL, 1.6f, 1f);
                }
                if (timer >= dwell) {
                    entityData.set(STATE, DEPARTING);
                    timer = 0;
                    level().playSound(null, getX(), getY(), getZ(), TabiSounds.DOOR_CHIME.get(), SoundSource.NEUTRAL, 1.0f, 1f);
                }
                if (!hasPlayer && tickCount % 100 == 0 && level().getNearestPlayer(this, 192) == null) {
                    discard();
                }
            }
            case DEPARTING -> {
                timer++;
                if (timer >= 40) {
                    entityData.set(STATE, RUNNING);
                    timer = 0;
                    announcedDeparture = false;
                }
            }
            case RUNNING -> {
                Route.Station next = r.nextStation(s, dir);
                double dist = next == null ? Math.max(0, (dir > 0 ? r.length - s : s)) : (next.s - s) * dir;
                double accel = r.type.equals("bus") ? 0.006 : 0.004;
                double decel = r.type.equals("bus") ? 0.008 : 0.006;
                v = Math.min(r.maxSpeed, v + accel);
                v = Math.min(v, Math.sqrt(2 * decel * Math.max(0, dist)));
                if (v < 0.03) v = 0.03;
                if (dist <= v) {
                    s = next != null ? next.s : (dir > 0 ? r.length : 0);
                    v = 0;
                    entityData.set(STATE, STOPPED);
                    timer = 0;
                    if (next != null) {
                        entityData.set(STATION, next.index);
                        announce(next.name + "、" + next.name + "です。" + (next.terminal ? "終点です。ご乗車ありがとうございました。" : ""));
                        if (next.terminal) dir = -dir;
                    }
                } else {
                    s += dir * v;
                    if (timer++ % 600 == 30 && hasPlayer && next != null) {
                        announce("次は " + next.name + "（" + next.en + "）です。");
                    }
                }
            }
            default -> entityData.set(STATE, STOPPED);
        }
        entityData.set(S, (float) s);
        entityData.set(SPEED, (float) v);
        entityData.set(DIR, (byte) dir);
        Vec3 p = r.pos(s);
        setPos(p.x, p.y, p.z);
    }

    private void announce(String text) {
        for (Entity e : getPassengers()) {
            if (e instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal("【車内放送】" + text).withStyle(net.minecraft.ChatFormatting.AQUA), false);
            }
        }
    }

    // ------------------------------------------------------------------ riding

    @Override
    public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isPassenger()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (getState() != STOPPED) {
            player.displayClientMessage(Component.literal("ドアが閉まっています"), true);
            return InteractionResult.CONSUME;
        }
        int seat = nearestFreeSeat(position().add(vec));
        if (seat < 0) {
            player.displayClientMessage(Component.literal("満席です"), true);
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer sp && !TicketOffice.canBoard(sp, this)) {
            return InteractionResult.CONSUME;
        }
        CompoundTag seats = entityData.get(SEATS).copy();
        seats.putInt(player.getStringUUID(), seat);
        entityData.set(SEATS, seats);
        player.startRiding(this, true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return interactAt(player, player.getEyePosition().subtract(position()), hand);
    }

    private int nearestFreeSeat(Vec3 hit) {
        CompoundTag taken = entityData.get(SEATS);
        int best = -1;
        double bd = Double.MAX_VALUE;
        double s = getS();
        for (int i = 0; i < seatCount(); i++) {
            boolean used = false;
            for (String k : taken.getAllKeys()) if (taken.getInt(k) == i) used = true;
            if (used) continue;
            double d = seatPos(i, s).distanceToSqr(hit);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    public int seatOf(Entity passenger) {
        CompoundTag t = entityData.get(SEATS);
        String k = passenger.getStringUUID();
        return t.contains(k) ? t.getInt(k) : 0;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < Math.max(1, seatCount());
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        if (!hasPassenger(passenger)) return;
        Vec3 p = seatPos(seatOf(passenger), sForGeometry());
        callback.accept(passenger, p.x, p.y + passenger.getMyRidingOffset(), p.z);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide) {
            CompoundTag seats = entityData.get(SEATS).copy();
            seats.remove(passenger.getStringUUID());
            entityData.set(SEATS, seats);
            if (passenger instanceof ServerPlayer sp) {
                TicketOffice.onAlight(sp, this);
            }
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Route.Station st = currentStation();
        if (getState() == STOPPED && st != null) return st.exit;
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    // ------------------------------------------------------------------ misc entity plumbing

    @Override
    protected AABB makeBoundingBox() {
        Route r = Route.get(entityData == null ? "" : entityData.get(ROUTE));
        CarDesign d = r == null ? null : CarDesign.get(r.design);
        if (r == null || d == null) return super.makeBoundingBox();
        double minX = 1e9, minY = 1e9, minZ = 1e9, maxX = -1e9, maxY = -1e9, maxZ = -1e9;
        double s = getS();
        for (int i = 0; i < Math.max(1, r.cars); i++) {
            CarPose p = carPose(s, i);
            for (int c = 0; c < 4; c++) {
                double lx = (c & 1) == 0 ? -d.width / 2 : d.width / 2;
                double lz = (c & 2) == 0 ? -d.length / 2 : d.length / 2;
                Vec3 w = toWorld(p, lx, 0, lz);
                minX = Math.min(minX, w.x);
                maxX = Math.max(maxX, w.x);
                minZ = Math.min(minZ, w.z);
                maxZ = Math.max(maxZ, w.z);
                minY = Math.min(minY, w.y);
                maxY = Math.max(maxY, w.y + d.height);
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(4);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 320 * 320;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        // position is computed locally from the synced line position
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(ROUTE, tag.getString("route"));
        entityData.set(S, tag.getFloat("s"));
        entityData.set(DIR, tag.getByte("dir") == 0 ? 1 : tag.getByte("dir"));
        entityData.set(STATE, tag.getByte("state"));
        entityData.set(STATION, tag.getInt("station"));
        timer = tag.getInt("timer");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("route", routeId());
        tag.putFloat("s", (float) getS());
        tag.putByte("dir", (byte) getDir());
        tag.putByte("state", getState());
        tag.putInt("station", getStationIndex());
        tag.putInt("timer", timer);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
