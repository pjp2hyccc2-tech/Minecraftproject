package com.hitori.game;

import com.hitori.entity.OniDoll;
import com.hitori.registry.ModEntities;
import com.hitori.world.Layout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The doll's presence in the level: spawned into the tub at step 12, kept in the game's data (it is never saved
 * with the chunk) so save and quit finds it where it was, doing what it was doing.
 */
public final class Doll {
    /** Everything of the game happens inside this box (the building, the street, the corridor). */
    public static final AABB AREA = new AABB(-24, Layout.GROUND_Y - 12, -24, 72, Layout.FEET_Y + 24, 40);
    @Nullable private static OniDoll cache;
    private static long cacheTick = Long.MIN_VALUE;

    private Doll() {}

    @Nullable
    public static OniDoll get(ServerLevel level) {
        long now = level.getGameTime();
        if (now != cacheTick || (cache != null && (!cache.isAlive() || cache.level() != level))) {
            List<OniDoll> l = level.getEntitiesOfClass(OniDoll.class, AREA, Entity::isAlive);
            cache = l.isEmpty() ? null : l.get(0);
            cacheTick = now;
        }
        return cache;
    }

    public static void invalidate() {
        cacheTick = Long.MIN_VALUE;
    }

    @Nullable
    public static OniDoll spawnAt(ServerLevel level, GameData d, Vec3 at, float yaw) {
        removeAll(level);
        OniDoll e = ModEntities.ONI_DOLL.get().create(level);
        if (e == null) return null;
        e.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        e.setYHeadRot(yaw);
        e.yBodyRot = yaw;
        level.addFreshEntity(e);
        d.dollAlive = true;
        d.setDirty();
        invalidate();
        return e;
    }

    /** Step 12: into the full tub, face down. */
    @Nullable
    public static OniDoll spawnInTub(ServerLevel level, GameData d) {
        OniDoll e = spawnAt(level, d, OniDoll.tubSpot(0), Layout.marker("bathtub").yaw());
        if (e != null) e.dormantInTub();
        save(level, d);
        return e;
    }

    public static void removeAll(ServerLevel level) {
        for (OniDoll e : level.getEntitiesOfClass(OniDoll.class, AREA, Entity::isAlive)) e.discard();
        invalidate();
    }

    /** It is in your hands (or ash): nothing to respawn. */
    public static void forget(GameData d) {
        d.dollAlive = false;
        d.dollState = "";
        d.setDirty();
        invalidate();
    }

    public static void tick(ServerLevel level, GameData d) {
        if (d.playTicks % 40 == 0) save(level, d);
    }

    public static void save(ServerLevel level, GameData d) {
        OniDoll e = get(level);
        if (e == null || !d.dollAlive) return;
        d.dollX = e.getX();
        d.dollY = e.getY();
        d.dollZ = e.getZ();
        d.dollYaw = e.getYRot();
        d.dollState = e.state().name();
        d.dollSuspicion = e.suspicion;
        d.dollTubTicks = e.tubTicks;
        d.setDirty();
    }

    /** After a restart: the doll where it was (a search goes on from there). */
    public static void ensure(ServerLevel level, GameData d) {
        if (!d.dollAlive || get(level) != null || d.phase == GameData.PHASE_ENDING) return;
        if (d.dollState.equals("BURN")) {
            d.dollAlive = false;
            return;
        }
        OniDoll e = ModEntities.ONI_DOLL.get().create(level);
        if (e == null) return;
        e.moveTo(d.dollX, d.dollY, d.dollZ, d.dollYaw, 0.0f);
        level.addFreshEntity(e);
        invalidate();
        e.suspicion = d.dollSuspicion * 0.5f;
        switch (d.dollState) {
            case "DORMANT", "" -> e.dormantInTub();
            case "TUB" -> {
                e.wake();
                e.tubTicks = Math.max(20 * 20, d.dollTubTicks);
            }
            case "WAIT" -> e.startWaiting();
            case "HUNT" -> e.startHunting();
            case "LIMP" -> {
                e.setState(OniDoll.State.LIMP);
                e.setAnimPose(OniDoll.LIMP);
            }
            default -> {
                e.setKnife(true);
                e.setState(OniDoll.State.SEARCH);
            }
        }
    }

    public static void resume(ServerLevel level, GameData d) {
        ensure(level, d);
    }
}
