package com.huntcraft.client;

import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client-only transient state: camera shake, damage numbers, dodge roll animations. */
@OnlyIn(Dist.CLIENT)
public final class ClientState {
    private ClientState() {
    }

    // ---- camera shake
    public static float shakeIntensity;
    public static int shakeTicks;
    public static int shakeTotal;

    public static void addShake(float intensity, int ticks) {
        if (intensity * ticks > shakeIntensity * shakeTicks) {
            shakeIntensity = intensity;
            shakeTicks = ticks;
            shakeTotal = ticks;
        }
    }

    // ---- damage numbers
    public static final class DamageNumber {
        public Vec3 pos;
        public final String text;
        public final int color;
        public final boolean crit;
        public int age;

        DamageNumber(Vec3 pos, String text, int color, boolean crit) {
            this.pos = pos;
            this.text = text;
            this.color = color;
            this.crit = crit;
        }
    }

    public static final List<DamageNumber> DAMAGE_NUMBERS = new ArrayList<>();

    public static void addDamageNumber(Vec3 pos, float amount, int color, boolean crit) {
        String text = amount >= 10 ? String.valueOf(Math.round(amount)) : String.format("%.1f", amount);
        Vec3 jitter = pos.add((Math.random() - 0.5) * 0.6, (Math.random() - 0.5) * 0.3, (Math.random() - 0.5) * 0.6);
        DAMAGE_NUMBERS.add(new DamageNumber(jitter, text, color, crit));
        if (DAMAGE_NUMBERS.size() > 60) DAMAGE_NUMBERS.remove(0);
    }

    // ---- dodge rolls (entity id -> start game time, direction)
    public record Dodge(long start, int dir) {
    }

    public static final Map<Integer, Dodge> DODGES = new HashMap<>();
    public static int localDodgeCooldown;

    // ---- HUD helpers
    public static float displayedHealth = -1;

    public static void tick(long gameTime) {
        if (shakeTicks > 0) shakeTicks--;
        DAMAGE_NUMBERS.removeIf(d -> ++d.age > 32);
        DODGES.entrySet().removeIf(e -> gameTime - e.getValue().start() > 12);
        if (localDodgeCooldown > 0) localDodgeCooldown--;
    }
}
