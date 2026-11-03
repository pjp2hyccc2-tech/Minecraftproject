package com.reisatsu.game;

import com.reisatsu.entity.Manager;
import com.reisatsu.entity.Medium;
import com.reisatsu.entity.MediumCar;
import com.reisatsu.entity.Spirit;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.entity.Visitor;
import com.reisatsu.registry.ModEntities;
import com.reisatsu.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Who is in the restaurant tonight. Spirits, the medium and her car are never saved; this keeps them in the level
 * according to the progress (a captured spirit never comes back; the visitor comes and goes on his own schedule).
 */
public final class Spirits {
    /** Everything of the game happens inside this box (the lot, the road and the curve included). */
    public static final AABB AREA = new AABB(-40, Layout.FEET_Y - 12, -30, 120, Layout.FEET_Y + 30, 90);
    private static List<Spirit> cache = List.of();
    private static long cacheTick = Long.MIN_VALUE;
    private static ServerLevel cacheLevel;
    /** Self test: the spirits are placed by hand. */
    public static boolean paused;

    private Spirits() {}

    public static List<Spirit> all(ServerLevel level) {
        long now = level.getGameTime();
        if (now != cacheTick || level != cacheLevel) {
            cache = level.getEntitiesOfClass(Spirit.class, AREA, Entity::isAlive);
            cacheTick = now;
            cacheLevel = level;
        }
        return cache;
    }

    public static void invalidate() {
        cacheTick = Long.MIN_VALUE;
    }

    @Nullable
    public static Spirit get(ServerLevel level, SpiritKind kind) {
        for (Spirit s : all(level)) if (s.kind() == kind && s.mode() != Spirit.Mode.EXORCISM) return s;
        return null;
    }

    @Nullable
    public static Medium medium(ServerLevel level) {
        List<Medium> l = level.getEntitiesOfClass(Medium.class, AREA, Entity::isAlive);
        return l.isEmpty() ? null : l.get(0);
    }

    @Nullable
    public static MediumCar car(ServerLevel level) {
        List<MediumCar> l = level.getEntitiesOfClass(MediumCar.class, AREA, Entity::isAlive);
        return l.isEmpty() ? null : l.get(0);
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.isPositionEntityTicking(pos);
    }

    /** Spawns whoever should be here and is not (every second), removes duplicates. */
    public static void ensure(ServerLevel level, GameData data) {
        if (!data.started) return;
        // the medium and her car are always by the lot
        Layout.Marker mm = Layout.marker("medium");
        List<Medium> media = level.getEntitiesOfClass(Medium.class, AREA, Entity::isAlive);
        for (int i = 1; i < media.size(); i++) media.get(i).discard();
        if (media.isEmpty() && loaded(level, mm.pos())) {
            Medium m = ModEntities.MEDIUM.get().create(level);
            if (m != null) {
                Vec3 c = mm.center();
                m.moveTo(c.x, c.y, c.z, mm.yaw(), 0.0f);
                m.faceYaw(mm.yaw());
                level.addFreshEntity(m);
            }
        }
        Layout.Marker cm = Layout.marker("medium_car");
        List<MediumCar> cars = level.getEntitiesOfClass(MediumCar.class, AREA, Entity::isAlive);
        for (int i = 1; i < cars.size(); i++) cars.get(i).discard();
        if (cars.isEmpty() && loaded(level, cm.pos())) {
            MediumCar car = ModEntities.MEDIUM_CAR.get().create(level);
            if (car != null) {
                Vec3 c = cm.center();
                car.moveTo(c.x, c.y, c.z, cm.yaw(), 0.0f);
                level.addFreshEntity(car);
            }
        }
        boolean night = data.phase == GameData.PHASE_INTRO || data.phase == GameData.PHASE_INVESTIGATE
                || data.phase == GameData.PHASE_CLOSING || data.phase == GameData.PHASE_RETURN;
        if (!night || paused) return; // the exorcism and the endings place their own spirits
        for (SpiritKind kind : SpiritKind.ALL) {
            List<Spirit> mine = new ArrayList<>();
            for (Spirit s : all(level)) if (s.kind() == kind && s.mode() != Spirit.Mode.EXORCISM && s.mode() != Spirit.Mode.BOUND) mine.add(s);
            boolean keep = night && !data.isCaptured(kind);
            for (int i = keep ? 1 : 0; i < mine.size(); i++) mine.get(i).discard();
            // the visitor is not resident: he arrives on his own schedule (Visitor.schedule)
            if (keep && kind != SpiritKind.VISITOR && mine.isEmpty()) spawnAtHome(level, kind);
        }
        if (night && data.phase != GameData.PHASE_INTRO) Visitor.schedule(level, data);
        invalidate();
    }

    @Nullable
    public static Spirit spawnAtHome(ServerLevel level, SpiritKind kind) {
        Spirit s = ModEntities.type(kind).create(level);
        if (s == null) return null;
        Vec3 home = homeOf(kind);
        if (!loaded(level, BlockPos.containing(home))) return null;
        s.moveTo(home.x, home.y, home.z, homeYaw(kind), 0.0f);
        s.faceYaw(homeYaw(kind));
        level.addFreshEntity(s);
        s.resetToRoutine();
        invalidate();
        return s;
    }

    /** Spawns a spirit at a position (debug, the exorcism, the bad ending). */
    @Nullable
    public static Spirit spawnAt(ServerLevel level, SpiritKind kind, Vec3 at, float yaw) {
        Spirit s = ModEntities.type(kind).create(level);
        if (s == null) return null;
        s.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        s.faceYaw(yaw);
        level.addFreshEntity(s);
        invalidate();
        return s;
    }

    public static Vec3 homeOf(SpiritKind kind) {
        return switch (kind) {
            case VISITOR -> Layout.marker("visitor_spawn").center();
            case WAITRESS -> Layout.marker("waitress_station").center();
            case CHEF -> Layout.marker("chef_board").center();
            case CHILD -> Layout.marker("child_count").center();
            case MIRROR_WOMAN -> Layout.marker("mirror_woman").center();
            case MANAGER -> Manager.deskSpot();
        };
    }

    public static float homeYaw(SpiritKind kind) {
        return switch (kind) {
            case VISITOR -> Layout.marker("visitor_spawn").yaw();
            case WAITRESS -> Layout.marker("waitress_station").yaw();
            case CHEF -> Layout.marker("chef_board").yaw();
            case CHILD -> Layout.marker("child_count").yaw();
            case MIRROR_WOMAN -> Layout.marker("mirror_woman").yaw();
            case MANAGER -> Layout.marker("manager_desk").yaw();
        };
    }

    public static void removeAll(ServerLevel level) {
        for (Spirit s : level.getEntitiesOfClass(Spirit.class, AREA)) s.discard();
        for (Medium m : level.getEntitiesOfClass(Medium.class, AREA)) m.discard();
        for (MediumCar c : level.getEntitiesOfClass(MediumCar.class, AREA)) c.discard();
        invalidate();
    }
}
