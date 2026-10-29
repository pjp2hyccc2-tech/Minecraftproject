package com.huntcraft.entity.monster.attack;

import com.huntcraft.core.Element;
import com.huntcraft.entity.misc.MonsterProjectile;

/** Parameters for a {@link MonsterProjectile}. Fluent builder; immutable after creation is not enforced. */
public final class ProjectileSpec {
    public MonsterProjectile.Kind kind = MonsterProjectile.Kind.FIREBALL;
    public float speed = 1.2F;
    public float damage = 10F;
    public Element element = Element.NONE;
    /** Radius of the impact explosion (0 = direct hit only). Explosions never break blocks. */
    public float explosionRadius = 0F;
    public float gravity = 0F;
    public int count = 1;
    /** Horizontal spread between projectiles in degrees when count > 1. */
    public float spreadDeg = 15F;
    public int lifetime = 80;
    public float size = 1.0F;
    /** Leaves a lingering hazard (fire pool, ice field...) on impact for this many ticks (0 = none). */
    public int lingerTicks = 0;
    public boolean homing = false;

    public static ProjectileSpec of(MonsterProjectile.Kind kind, float damage, Element element) {
        ProjectileSpec s = new ProjectileSpec();
        s.kind = kind;
        s.damage = damage;
        s.element = element;
        return s;
    }

    public ProjectileSpec speed(float v) { this.speed = v; return this; }
    public ProjectileSpec explode(float r) { this.explosionRadius = r; return this; }
    public ProjectileSpec gravity(float g) { this.gravity = g; return this; }
    public ProjectileSpec count(int n, float spread) { this.count = n; this.spreadDeg = spread; return this; }
    public ProjectileSpec lifetime(int t) { this.lifetime = t; return this; }
    public ProjectileSpec size(float s) { this.size = s; return this; }
    public ProjectileSpec linger(int ticks) { this.lingerTicks = ticks; return this; }
    public ProjectileSpec homing() { this.homing = true; return this; }
}
