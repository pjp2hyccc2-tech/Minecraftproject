package com.huntcraft.gear.weapon;

import com.huntcraft.core.HunterDamage;
import com.huntcraft.core.SafeZones;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterPart;
import com.huntcraft.entity.monster.PartDef;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Target selection for weapon special moves. Like {@link HunterDamage#hitCone} / {@link HunterDamage#hitArea}, but it
 * never hits villagers, pets or anything but hostiles inside safe zones, and it tells the weapon which monster part
 * is struck (hammer KO bonus on heads, shield bash...). Damage still goes through {@link HunterDamage#hit}.
 */
public final class WeaponHits {
    private WeaponHits() {
    }

    /** A struck entity: the monster part (or the entity itself) plus the part definition when it is a monster. */
    public record Target(Entity struck, LivingEntity owner, @Nullable PartDef part) {
        public boolean isKoZone() {
            return part != null && part.koZone;
        }
    }

    public static boolean canHit(Player p, LivingEntity e) {
        if (e == p || !e.isAlive() || e instanceof Player || e instanceof ArmorStand || e.isInvulnerable()) return false;
        if (e instanceof HuntMonster m) return !m.isCarcass();
        if (e instanceof Npc) return false;
        if (e instanceof TamableAnimal t && t.isTame()) return false;
        if (e instanceof AbstractHorse h && h.isTamed()) return false;
        return !SafeZones.isInSafeZone(p.level(), p.position()) || e instanceof Enemy;
    }

    private static boolean inCone(Vec3 eye, Vec3 point, Vec3 flatLook, double cos) {
        Vec3 to = point.subtract(eye);
        Vec3 tf = new Vec3(to.x, 0, to.z);
        if (tf.lengthSqr() < 1.0E-4) return true;
        return tf.normalize().dot(flatLook) >= cos;
    }

    public static Vec3 flatLook(Player p) {
        Vec3 look = p.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-6) {
            float yaw = p.getYRot() * ((float) Math.PI / 180F);
            return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        return flat.normalize();
    }

    /** Targets in front of the player within `range` (from the eyes) and a horizontal cone (full angle, degrees). */
    public static List<Target> coneTargets(Player p, double range, float coneDeg) {
        Vec3 eye = p.getEyePosition();
        Vec3 flat = flatLook(p);
        boolean full = coneDeg >= 359.0F;
        double cos = Math.cos(Math.toRadians(Math.min(coneDeg, 360.0F) / 2.0));
        double r2 = range * range;
        List<Target> out = new ArrayList<>();
        AABB box = p.getBoundingBox().inflate(range + 4.0);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> canHit(p, e))) {
            Entity struck = null;
            PartDef def = null;
            double best = Double.MAX_VALUE;
            double dBody = HuntMonster.boxDistSqr(e.getBoundingBox(), eye);
            if (dBody <= r2 && (full || dBody < 2.25 || inCone(eye, e.getBoundingBox().getCenter(), flat, cos))) {
                struck = e;
                best = dBody;
            }
            if (e instanceof HuntMonster m) {
                for (MonsterPart part : m.getMonsterParts()) {
                    double d = HuntMonster.boxDistSqr(part.getBoundingBox(), eye);
                    if (d > r2 || d >= best) continue;
                    if (full || d < 1.0 || inCone(eye, part.getBoundingBox().getCenter(), flat, cos)) {
                        struck = part;
                        best = d;
                        def = part.def;
                    }
                }
                if (struck == e) def = m.profile().part("body");
            }
            if (struck != null) out.add(new Target(struck, e, def));
        }
        return out;
    }

    /** Targets within a sphere; for monsters the part closest to the center is struck. */
    public static List<Target> areaTargets(Player p, Vec3 center, double radius) {
        double r2 = radius * radius;
        List<Target> out = new ArrayList<>();
        AABB box = new AABB(center, center).inflate(radius + 3.0);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> canHit(p, e))) {
            Entity struck = e;
            PartDef def = null;
            double best = HuntMonster.boxDistSqr(e.getBoundingBox(), center);
            if (e instanceof HuntMonster m) {
                def = m.profile().part("body");
                for (MonsterPart part : m.getMonsterParts()) {
                    double d = HuntMonster.boxDistSqr(part.getBoundingBox(), center);
                    if (d < best) {
                        best = d;
                        struck = part;
                        def = part.def;
                    }
                }
            }
            if (best <= r2) out.add(new Target(struck, e, def));
        }
        return out;
    }

    /** Hits every target of the cone with the given motion value. Returns the number of hits. */
    public static int cone(Player p, double range, float coneDeg, float mv, @Nullable BiConsumer<Target, HunterDamage.Context> cfg) {
        return hitAll(p, coneTargets(p, range, coneDeg), mv, cfg);
    }

    public static int area(Player p, Vec3 center, double radius, float mv, @Nullable BiConsumer<Target, HunterDamage.Context> cfg) {
        return hitAll(p, areaTargets(p, center, radius), mv, cfg);
    }

    public static int hitAll(Player p, List<Target> targets, float mv, @Nullable BiConsumer<Target, HunterDamage.Context> cfg) {
        int n = 0;
        for (Target t : targets) {
            if (!t.owner().isAlive()) continue;
            boolean ok = HunterDamage.hit(p, t.struck(), mv, ctx -> {
                if (cfg != null) cfg.accept(t, ctx);
            });
            if (ok) n++;
        }
        return n;
    }

    /** A point in front of the player at the given distance (feet height + up). */
    public static Vec3 front(Player p, double dist, double up) {
        Vec3 f = flatLook(p);
        return p.position().add(f.x * dist, up, f.z * dist);
    }
}
