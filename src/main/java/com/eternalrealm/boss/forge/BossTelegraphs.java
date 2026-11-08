package com.eternalrealm.boss.forge;

import com.eternalrealm.boss.AbilityKind;
import com.eternalrealm.boss.BossAbility;
import com.eternalrealm.boss.BossShape;
import com.eternalrealm.combat.Telegraph;

/** Ground markers ({@link Telegraph}) for boss ability areas. */
final class BossTelegraphs {
    /** Radius drawn for attacks that hit the whole arena. */
    static final double ARENA_MARKER_RADIUS = 22.0;

    private BossTelegraphs() {
    }

    static Telegraph marker(BossAbility ability, BossShape shape, int durationTicks, double bossY) {
        Telegraph.Style style = style(ability);
        int ticks = Math.max(1, Math.min(Telegraph.MAX_TICKS, durationTicks));
        if (shape instanceof BossShape.Circle circle) {
            return Telegraph.circle(style, circle.center().x(), circle.center().y(), circle.center().z(),
                    Math.min(Telegraph.MAX_SIZE, circle.radius()), ticks);
        }
        if (shape instanceof BossShape.Ring ring) {
            return Telegraph.ring(style, ring.center().x(), ring.center().y(), ring.center().z(),
                    Math.min(Telegraph.MAX_SIZE, ring.inner()), Math.min(Telegraph.MAX_SIZE, ring.outer()), ticks);
        }
        if (shape instanceof BossShape.Cone cone) {
            return Telegraph.cone(style, cone.origin().x(), cone.origin().y(), cone.origin().z(), cone.yawDegrees(),
                    Math.min(Telegraph.MAX_SIZE, cone.range()), Math.min(180.0, cone.halfAngleDegrees()), ticks);
        }
        if (shape instanceof BossShape.Line line) {
            return Telegraph.line(style, line.origin().x(), line.origin().y(), line.origin().z(), line.end().x(),
                    line.end().z(), Math.min(Telegraph.MAX_SIZE, line.width()), ticks);
        }
        BossShape.Arena arena = (BossShape.Arena) shape;
        return Telegraph.circle(Telegraph.Style.DARK, arena.center().x(), bossY, arena.center().z(),
                ARENA_MARKER_RADIUS, ticks);
    }

    /** Magic-looking kinds get the violet marker, the rest the red danger marker. */
    private static Telegraph.Style style(BossAbility ability) {
        if (ability.kind() instanceof AbilityKind.Soak) {
            return Telegraph.Style.HOLY; // gold: stack here and share the damage
        }
        String id = ability.id();
        if (id.contains("fire") || id.contains("flame") || id.contains("meteor") || id.contains("magma")
                || id.contains("inferno") || id.contains("breath")) {
            return Telegraph.Style.FIRE;
        }
        if (id.contains("frost") || id.contains("ice") || id.contains("blizzard") || id.contains("freeze")) {
            return Telegraph.Style.FROST;
        }
        if (id.contains("holy") || id.contains("light") || id.contains("judg")) {
            return Telegraph.Style.HOLY;
        }
        if (id.contains("void") || id.contains("dark") || id.contains("shadow") || id.contains("end")) {
            return Telegraph.Style.DARK;
        }
        if (ability.kind() instanceof AbilityKind.ProjectileVolley || id.contains("arcane") || id.contains("magic")
                || id.contains("rune") || id.contains("spell")) {
            return Telegraph.Style.MAGIC;
        }
        return Telegraph.Style.DANGER;
    }
}
