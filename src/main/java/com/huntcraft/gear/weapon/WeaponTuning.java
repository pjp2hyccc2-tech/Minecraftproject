package com.huntcraft.gear.weapon;

import com.huntcraft.core.WeaponClass;
import com.huntcraft.registry.HCItems;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/** Balance numbers per weapon class (one place to tune). */
public final class WeaponTuning {
    private WeaponTuning() {
    }

    /** True raw multiplier of the class (true raw is similar across classes, like the original games). */
    public static float attackMult(WeaponClass c) {
        return switch (c) {
            case GREAT_SWORD, HAMMER -> 1.0F;
            case LONG_SWORD, HUNTING_HORN -> 0.95F;
            case SWORD_AND_SHIELD, BOW -> 0.85F;
            case DUAL_BLADES -> 0.8F;
            case LANCE -> 0.9F;
        };
    }

    /** Attacks per second (vanilla attack speed attribute). */
    public static float attackSpeed(WeaponClass c) {
        return switch (c) {
            case GREAT_SWORD -> 0.7F;
            case LONG_SWORD -> 1.4F;
            case SWORD_AND_SHIELD -> 2.2F;
            case DUAL_BLADES -> 2.6F;
            case HAMMER -> 0.95F;
            case LANCE -> 1.25F;
            case HUNTING_HORN -> 0.9F;
            case BOW -> 4.0F;
        };
    }

    /** Average left-click motion value, used for the displayed vanilla attack damage attribute. */
    public static float displayMV(WeaponClass c) {
        float[] mv = combo(c);
        float sum = 0;
        for (float v : mv) sum += v;
        return sum / mv.length;
    }

    /** Extra entity reach in blocks. */
    public static double reachBonus(WeaponClass c) {
        return switch (c) {
            case GREAT_SWORD, LONG_SWORD -> 0.75;
            case LANCE -> 1.25;
            case HAMMER, HUNTING_HORN -> 0.25;
            default -> 0.0;
        };
    }

    /** Horizontal cone (degrees) of left clicks that hit air / blocks. */
    public static float swingCone(WeaponClass c) {
        return switch (c) {
            case GREAT_SWORD, DUAL_BLADES -> 120F;
            case LANCE -> 40F;
            case HAMMER, SWORD_AND_SHIELD -> 90F;
            case BOW -> 60F;
            default -> 100F;
        };
    }

    /** Left click combo motion values. */
    public static float[] combo(WeaponClass c) {
        return switch (c) {
            case GREAT_SWORD -> new float[]{0.48F, 0.36F, 0.52F};
            case LONG_SWORD -> new float[]{0.26F, 0.22F, 0.24F, 0.20F};
            case SWORD_AND_SHIELD -> new float[]{0.14F, 0.14F, 0.17F, 0.20F};
            case DUAL_BLADES -> new float[]{0.12F, 0.12F, 0.16F, 0.10F, 0.10F, 0.18F};
            case HAMMER -> new float[]{0.36F, 0.42F, 0.56F};
            case LANCE -> new float[]{0.24F, 0.24F, 0.30F};
            case HUNTING_HORN -> new float[]{0.33F, 0.30F, 0.38F};
            case BOW -> new float[]{0.08F};
        };
    }

    /** Ticks without attacking after which the combo restarts. */
    public static int comboReset(WeaponClass c) {
        return c == WeaponClass.GREAT_SWORD || c == WeaponClass.HAMMER ? 45 : 30;
    }

    /** Small monster material used by every upgrade of the class (class flavour). */
    public static RegistryObject<Item> classMaterial(WeaponClass c) {
        return switch (c) {
            case GREAT_SWORD -> HCItems.MONSTER_BONE_M;
            case LONG_SWORD -> HCItems.JAGRAPTOR_FANG;
            case SWORD_AND_SHIELD -> HCItems.BOARFANG_TUSK;
            case DUAL_BLADES -> HCItems.HEATGECKO_SCALE;
            case HAMMER -> HCItems.GANSEKI_SHELL;
            case LANCE -> HCItems.GAMENOS_SHELL;
            case HUNTING_HORN -> HCItems.BUZZLING_WING;
            case BOW -> HCItems.SPIDER_WEB;
        };
    }
}
