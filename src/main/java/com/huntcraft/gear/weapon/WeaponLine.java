package com.huntcraft.gear.weapon;

import com.huntcraft.core.Element;
import com.huntcraft.core.WeaponClass;
import com.huntcraft.registry.HCItems;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * A weapon tree line. Every weapon class has one item per line: the base line (iron -> bone) and one line per large
 * monster. Each item can be levelled 1..8 at the smith (levels 6-8 need high rank materials and HR 8).
 */
public enum WeaponLine {
    //        id          element         elem  +/lv aff rar atk   +/lv  HR shot             notes (indices into HornSongs.NOTE_RGB)
    IRON("iron", Element.NONE, 0, 0, 0, 1, 14.0F, 2.0F, 1, 0, new int[]{0, 2, 3},
            new int[]{40, 40, 50, 60, 0, 0, 0}, new int[]{40, 40, 50, 60, 30, 0, 0}),
    BONE("bone", Element.NONE, 0, 0, 0, 2, 16.0F, 2.2F, 1, 1, new int[]{1, 2, 4},
            new int[]{30, 40, 50, 50, 20, 0, 0}, new int[]{30, 40, 50, 50, 40, 10, 0}),
    IGNIVALD("ignivald", Element.FIRE, 220, 25, 0, 3, 20.0F, 2.6F, 1, 0, new int[]{0, 2, 7},
            new int[]{25, 30, 40, 50, 40, 15, 0}, new int[]{25, 30, 40, 50, 40, 40, 5}),
    FROSTGARM("frostgarm", Element.ICE, 240, 25, 10, 3, 19.0F, 2.5F, 2, 2, new int[]{0, 6, 3},
            new int[]{20, 30, 40, 40, 50, 20, 0}, new int[]{20, 30, 40, 40, 50, 40, 10}),
    VOLTRIX("voltrix", Element.THUNDER, 280, 28, 15, 4, 18.5F, 2.4F, 2, 0, new int[]{1, 4, 6},
            new int[]{25, 30, 40, 50, 50, 10, 0}, new int[]{25, 30, 40, 50, 50, 30, 10}),
    GRAVADON("gravadon", Element.WATER, 180, 20, -10, 4, 22.0F, 2.8F, 3, 1, new int[]{0, 3, 5},
            new int[]{30, 40, 50, 60, 30, 0, 0}, new int[]{30, 40, 50, 60, 40, 20, 0}),
    AZRAGOS("azragos", Element.DRAGON, 300, 30, -15, 5, 23.0F, 3.0F, 4, 2, new int[]{1, 2, 6},
            new int[]{20, 25, 30, 40, 50, 25, 0}, new int[]{20, 25, 30, 40, 50, 40, 15});

    public static final int SHOT_RAPID = 0;
    public static final int SHOT_SPREAD = 1;
    public static final int SHOT_PIERCE = 2;

    public final String id;
    public final Element element;
    public final float elementBase;
    public final float elementGrowth;
    public final int affinity;
    public final int rarity;
    public final float attackBase;
    public final float attackGrowth;
    public final int forgeHR;
    /** Bow shot type (SHOT_*). */
    public final int shot;
    /** Hunting horn note colours (3 indices into {@link HornSongs#NOTE_RGB}). */
    public final int[] notes;
    /** Sharpness segment lengths RED..PURPLE without Handicraft, and the maximum reachable profile. */
    public final int[] sharpBase;
    public final int[] sharpMax;

    WeaponLine(String id, Element element, float elementBase, float elementGrowth, int affinity, int rarity, float attackBase,
               float attackGrowth, int forgeHR, int shot, int[] notes, int[] sharpBase, int[] sharpMax) {
        this.id = id;
        this.element = element;
        this.elementBase = elementBase;
        this.elementGrowth = elementGrowth;
        this.affinity = affinity;
        this.rarity = rarity;
        this.attackBase = attackBase;
        this.attackGrowth = attackGrowth;
        this.forgeHR = forgeHR;
        this.shot = shot;
        this.notes = notes;
        this.sharpBase = sharpBase;
        this.sharpMax = sharpMax;
    }

    public boolean isMonsterLine() {
        return this != IRON && this != BONE;
    }

    /** Element / status of a weapon of this line (bone weapons of some classes carry a status). */
    public Element elementFor(WeaponClass c) {
        if (this == BONE) {
            return switch (c) {
                case SWORD_AND_SHIELD -> Element.PARALYSIS;
                case DUAL_BLADES -> Element.POISON;
                case HUNTING_HORN -> Element.SLEEP;
                default -> Element.NONE;
            };
        }
        return element;
    }

    public float elementValue(WeaponClass c, int level) {
        Element e = elementFor(c);
        if (e == Element.NONE) return 0;
        if (this == BONE) return 140 + 15 * (level - 1);
        float v = elementBase + elementGrowth * (level - 1);
        // fast weapons hit often: slightly lower element per hit, slow weapons slightly higher
        if (c == WeaponClass.DUAL_BLADES) v *= 0.8F;
        else if (c == WeaponClass.GREAT_SWORD || c == WeaponClass.HAMMER) v *= 1.15F;
        else if (c == WeaponClass.BOW) v *= 0.9F;
        return Math.round(v / 10.0F) * 10.0F;
    }

    /** Monster materials of this line (null for iron / bone). */
    @Nullable
    public HCItems.MonsterMats mats() {
        return switch (this) {
            case IGNIVALD -> HCItems.IGNIVALD;
            case FROSTGARM -> HCItems.FROSTGARM;
            case VOLTRIX -> HCItems.VOLTRIX;
            case GRAVADON -> HCItems.GRAVADON;
            case AZRAGOS -> HCItems.AZRAGOS;
            default -> null;
        };
    }

    /** Elemental sac used in upgrades. */
    public RegistryObject<Item> sac() {
        return switch (this) {
            case IGNIVALD -> HCItems.FLAME_SAC;
            case FROSTGARM -> HCItems.FROST_SAC;
            case VOLTRIX -> HCItems.THUNDER_SAC;
            case GRAVADON -> HCItems.AQUA_SAC;
            default -> HCItems.MONSTER_FLUID;
        };
    }
}
