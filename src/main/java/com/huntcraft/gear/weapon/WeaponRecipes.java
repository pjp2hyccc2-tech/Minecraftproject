package com.huntcraft.gear.weapon;

import com.huntcraft.core.SmithingRegistry;
import com.huntcraft.core.SmithingRegistry.Cost;
import com.huntcraft.core.WeaponClass;
import com.huntcraft.registry.HCItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Weapon trees for the blacksmith: forge the iron weapon, upgrade it to bone, upgrade bone to any monster line.
 * Level-up costs (levels 2..8) are provided through {@link HunterWeaponItem#getUpgradeCost}.
 */
public final class WeaponRecipes {
    private static final Supplier<Item> IRON_INGOT = () -> Items.IRON_INGOT;

    private WeaponRecipes() {
    }

    public static String recipeId(WeaponClass c, WeaponLine l) {
        return "weapon_" + c.name().toLowerCase(Locale.ROOT) + "_" + l.id;
    }

    public static void registerAll() {
        for (WeaponClass c : WeaponClass.values()) {
            for (WeaponLine l : WeaponLine.values()) {
                RegistryObject<Item> result = WeaponsModule.weapon(c, l);
                Supplier<? extends Item> from = switch (l) {
                    case IRON -> null;
                    case BONE -> WeaponsModule.weapon(c, WeaponLine.IRON);
                    default -> WeaponsModule.weapon(c, WeaponLine.BONE);
                };
                SmithingRegistry.register(new SmithingRegistry.Recipe(recipeId(c, l), SmithingRegistry.Category.WEAPON, c.name(),
                        result, from, forgeCost(c, l), forgeZenny(l), l.forgeHR));
            }
        }
    }

    public static List<Cost> forgeCost(WeaponClass c, WeaponLine l) {
        List<Cost> out = new ArrayList<>();
        RegistryObject<Item> cm = WeaponTuning.classMaterial(c);
        HCItems.MonsterMats m = l.mats();
        switch (l) {
            case IRON -> {
                out.add(Cost.of(IRON_INGOT, c == WeaponClass.DUAL_BLADES || c == WeaponClass.SWORD_AND_SHIELD ? 4 : 6));
                out.add(Cost.of(HCItems.EARTH_CRYSTAL, 2));
            }
            case BONE -> {
                out.add(Cost.of(HCItems.MONSTER_BONE_S, 5));
                out.add(Cost.of(HCItems.MONSTER_BONE_M, 2));
                out.add(Cost.of(cm, 2));
            }
            default -> {
                if (m != null) {
                    out.add(Cost.of(m.scale, 3));
                    out.add(Cost.of(m.shell, 2));
                    out.add(Cost.of(c == WeaponClass.BOW || c == WeaponClass.HUNTING_HORN ? m.special : m.claw, 1));
                    out.add(Cost.of(l.sac(), l == WeaponLine.AZRAGOS ? 3 : 1));
                    if (l == WeaponLine.AZRAGOS) out.add(Cost.of(HCItems.MONSTER_BONE_L, 1));
                }
            }
        }
        return out;
    }

    public static int forgeZenny(WeaponLine l) {
        return switch (l) {
            case IRON -> 400;
            case BONE -> 800;
            case AZRAGOS -> 4000;
            case GRAVADON, VOLTRIX -> 2400;
            default -> 2000;
        };
    }

    public static List<Cost> levelCost(WeaponClass c, WeaponLine l, int next) {
        List<Cost> out = new ArrayList<>();
        boolean high = next >= 6;
        RegistryObject<Item> cm = WeaponTuning.classMaterial(c);
        HCItems.MonsterMats m = l.mats();
        switch (l) {
            case IRON -> {
                if (!high) {
                    out.add(Cost.of(IRON_INGOT, next + 1));
                    out.add(Cost.of(HCItems.EARTH_CRYSTAL, next - 1));
                    if (next >= 4) out.add(Cost.of(HCItems.AZURITE_ORE, next - 3));
                } else {
                    out.add(Cost.of(HCItems.CRIMSONITE_ORE, next - 4));
                    if (next >= 7) out.add(Cost.of(HCItems.NOVACRYSTAL, next - 6));
                    out.add(Cost.of(HCItems.MONSTER_BONE_L, 1));
                }
            }
            case BONE -> {
                if (!high) {
                    out.add(Cost.of(HCItems.MONSTER_BONE_S, 3));
                    if (next >= 3) out.add(Cost.of(HCItems.MONSTER_BONE_M, next - 2));
                    if (next >= 4) out.add(Cost.of(next >= 5 ? HCItems.BOARFANG_TUSK : HCItems.JAGRAPTOR_FANG, 2));
                } else {
                    out.add(Cost.of(HCItems.MONSTER_BONE_L, next - 4));
                    if (next >= 7) out.add(Cost.of(HCItems.LIGHTCRYSTAL, next - 6));
                    out.add(Cost.of(HCItems.CRIMSONITE_ORE, 1));
                }
            }
            default -> {
                if (m == null) break;
                switch (next) {
                    case 2 -> {
                        out.add(Cost.of(m.scale, 2));
                        out.add(Cost.of(m.shell, 1));
                        out.add(Cost.of(HCItems.MONSTER_BONE_M, 1));
                    }
                    case 3 -> {
                        out.add(Cost.of(m.scale, 2));
                        out.add(Cost.of(m.claw, 2));
                        out.add(Cost.of(l.sac(), 1));
                    }
                    case 4 -> {
                        out.add(Cost.of(m.shell, 2));
                        out.add(Cost.of(m.special, 1));
                        out.add(Cost.of(HCItems.AZURITE_ORE, 2));
                    }
                    case 5 -> {
                        out.add(Cost.of(m.tail, 1));
                        out.add(Cost.of(m.special, 1));
                        out.add(Cost.of(m.gem, 1));
                    }
                    case 6 -> {
                        out.add(Cost.of(m.scalePlus, 3));
                        out.add(Cost.of(m.shellPlus, 1));
                        out.add(Cost.of(HCItems.NOVACRYSTAL, 1));
                    }
                    case 7 -> {
                        out.add(Cost.of(m.shellPlus, 2));
                        out.add(Cost.of(m.clawPlus, 2));
                        out.add(Cost.of(m.specialPlus, 1));
                    }
                    default -> {
                        out.add(Cost.of(m.specialPlus, 2));
                        out.add(Cost.of(m.gemPlus, 1));
                        out.add(Cost.of(HCItems.LIGHTCRYSTAL, 2));
                    }
                }
            }
        }
        if (!high && next >= 3) out.add(Cost.of(cm, next >= 5 ? 2 : 1));
        if (high && l.isMonsterLine() && next == 8) out.add(Cost.of(HCItems.MONSTER_BONE_L, 2));
        return out;
    }

    public static int levelZenny(WeaponLine l, int next) {
        boolean high = next >= 6;
        return switch (l) {
            case IRON -> high ? 1500 * (next - 4) : 150 * next;
            case BONE -> high ? 1800 * (next - 4) : 200 * next;
            case AZRAGOS -> high ? 4500 * (next - 4) : 600 * next;
            default -> high ? 3000 * (next - 4) : 400 * next;
        };
    }
}
