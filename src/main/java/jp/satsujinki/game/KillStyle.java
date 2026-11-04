package jp.satsujinki.game;

import jp.satsujinki.registry.ModDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

import java.util.Locale;

/** How a contestant died. Drives points, style gauge gain, corpse type and HUD stamp. */
public enum KillStyle {
    GENERIC(60, 0.30F, 0xFFC8C8C8),
    THROW(100, 0.40F, 0xFFFFB050),
    SPIKES(110, 0.45F, 0xFFD8D8E8),
    SAW(140, 0.52F, 0xFFFF7A30),
    MIXER(170, 0.62F, 0xFFFF2828),
    PRESS(190, 0.66F, 0xFF9AB8FF),
    GUILLOTINE(220, 0.72F, 0xFFFFD84A);

    public final int basePoints;
    public final float styleGain;
    public final int color;

    KillStyle(int basePoints, float styleGain, int color) {
        this.basePoints = basePoints;
        this.styleGain = styleGain;
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String langKey() {
        return "satsujinki.kill." + id();
    }

    public static KillStyle byId(int i) {
        KillStyle[] v = values();
        return i >= 0 && i < v.length ? v[i] : GENERIC;
    }

    public static KillStyle fromSource(DamageSource src, boolean recentlyThrown) {
        if (src.is(ModDamageTypes.MIXER)) return MIXER;
        if (src.is(ModDamageTypes.PRESS)) return PRESS;
        if (src.is(ModDamageTypes.SAW)) return SAW;
        if (src.is(ModDamageTypes.GUILLOTINE)) return GUILLOTINE;
        if (src.is(ModDamageTypes.SPIKES)) return SPIKES;
        if (recentlyThrown && (src.is(DamageTypes.FALL) || src.is(DamageTypes.FLY_INTO_WALL) || src.is(DamageTypes.IN_WALL))) return THROW;
        return GENERIC;
    }
}
