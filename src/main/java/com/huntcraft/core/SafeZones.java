package com.huntcraft.core;

import com.huntcraft.HuntCraft;
import com.huntcraft.entity.monster.HuntMonster;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monster-free areas: the whole hunter village dimension plus registered zones (base camps, gathering hall).
 * Hostile mobs cannot spawn, enter or target players inside a safe zone.
 */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SafeZones {
    public record Zone(ResourceKey<Level> dimension, Vec3 center, double radius) {
        public boolean contains(Vec3 p) {
            double dx = p.x - center.x, dz = p.z - center.z;
            return dx * dx + dz * dz <= radius * radius && p.y > center.y - 24 && p.y < center.y + 40;
        }
    }

    private static final Map<String, Zone> ZONES = new ConcurrentHashMap<>();

    private SafeZones() {
    }

    /** Registers (or replaces) a circular safe zone. */
    public static void register(String id, ResourceKey<Level> dimension, Vec3 center, double radius) {
        ZONES.put(id, new Zone(dimension, center, radius));
    }

    public static void unregister(String id) {
        ZONES.remove(id);
    }

    public static void clear() {
        ZONES.clear();
    }

    public static boolean isInSafeZone(Level level, Vec3 pos) {
        if (level == null) return false;
        if (HCDimensions.isVillage(level)) return true;
        ResourceKey<Level> dim = level.dimension();
        for (Zone z : ZONES.values()) {
            if (z.dimension == dim && z.contains(pos)) return true;
        }
        return false;
    }

    private static Zone nearest(Level level, Vec3 pos) {
        Zone best = null;
        double bd = Double.MAX_VALUE;
        for (Zone z : ZONES.values()) {
            if (z.dimension != level.dimension()) continue;
            double d = z.center.distanceToSqr(pos);
            if (d < bd) {
                bd = d;
                best = z;
            }
        }
        return best;
    }

    public static boolean isHostile(Entity e) {
        if (e instanceof HuntMonster hm) return hm.isHostile() || hm.profile().large;
        return e instanceof Enemy;
    }

    /** Pushes a hostile entity out of the zone it stands in (or removes it in the village). */
    public static void pushOut(Entity e) {
        Level level = e.level();
        if (HCDimensions.isVillage(level)) {
            e.discard();
            return;
        }
        if (e instanceof Mob mob) mob.setTarget(null);
        Zone z = nearest(level, e.position());
        if (z == null) return;
        Vec3 d = e.position().subtract(z.center);
        d = new Vec3(d.x, 0, d.z);
        if (d.lengthSqr() < 0.01) d = new Vec3(1, 0, 0);
        d = d.normalize();
        double depth = z.radius - Math.sqrt(Math.pow(e.getX() - z.center.x, 2) + Math.pow(e.getZ() - z.center.z, 2));
        if (depth > z.radius * 0.6 && e.tickCount % 100 == 0) {
            // deep inside for a while: teleport out
            Vec3 out = z.center.add(d.scale(z.radius + 3));
            e.teleportTo(out.x, e.getY(), out.z);
        } else {
            e.setDeltaMovement(d.x * 0.5, e.getDeltaMovement().y, d.z * 0.5);
            e.hasImpulse = true;
        }
        if (e instanceof Mob mob) {
            Vec3 away = z.center.add(d.scale(z.radius + 8));
            mob.getNavigation().moveTo(away.x, e.getY(), away.z, 1.2);
        }
    }

    // ------------------------------------------------------------------ enforcement
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity e = event.getEntity();
        if (!isHostile(e)) return;
        if (HCDimensions.isVillage(event.getLevel())) {
            event.setCanceled(true);
            return;
        }
        if (!event.loadedFromDisk() && isInSafeZone(event.getLevel(), e.position())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (isHostile(event.getEntity()) && isInSafeZone(event.getEntity().level(), event.getEntity().position())) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() != null && isHostile(event.getEntity())
                && isInSafeZone(event.getNewTarget().level(), event.getNewTarget().position())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        Entity e = event.getEntity();
        if (e.level().isClientSide || e.tickCount % 10 != 0 || e instanceof HuntMonster) return; // monsters handle themselves
        if (isHostile(e) && isInSafeZone(e.level(), e.position())) pushOut(e);
    }
}
