package com.huntcraft.gear.armor;

import com.huntcraft.core.SkillType;
import com.huntcraft.core.SmithingRegistry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One armor series (helm / mail / coil / greaves). Holds defense, per-piece skills and elemental
 * resistances, set bonuses, forge costs and level-up materials. Instances live in {@link ArmorSets}.
 */
public final class ArmorSet {
    public static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
    public static final String[] PIECE_NAMES = {"helm", "mail", "coil", "greaves"};
    /** Resistance skills in display order: fire, water, thunder, ice, dragon. */
    public static final SkillType[] RES = {SkillType.FIRE_RES, SkillType.WATER_RES, SkillType.THUNDER_RES, SkillType.ICE_RES, SkillType.DRAGON_RES};
    public static final int MAX_LEVEL = 8;

    public final String id;
    public final int rarity;
    public final int color;
    public final HCArmorMaterial material;
    /** Extra armor points per level above 1 (per piece). */
    public final float armorPerLevel;
    /** Extra toughness per level above 1 (per piece). */
    public final float toughnessPerLevel;
    public final int zennyBase;
    public final int requiredHR;
    private final Piece[] pieces;
    private final List<Bonus> bonuses;
    @SuppressWarnings("unchecked")
    final RegistryObject<Item>[] items = new RegistryObject[4];

    /** Data of one piece. */
    public static final class Piece {
        final EnumMap<SkillType, Integer> skills = new EnumMap<>(SkillType.class);
        final int[] res = new int[5];
        final List<SmithingRegistry.Cost> forgeCost = new ArrayList<>();
        int forgeZenny = 100;
        Supplier<? extends Item> lowMat;
        Supplier<? extends Item> highMat;

        public Piece skill(SkillType t, int lv) {
            skills.merge(t, lv, Integer::sum);
            return this;
        }

        /** Resistances: fire, water, thunder, ice, dragon. */
        public Piece res(int fire, int water, int thunder, int ice, int dragon) {
            res[0] = fire;
            res[1] = water;
            res[2] = thunder;
            res[3] = ice;
            res[4] = dragon;
            return this;
        }

        public Piece cost(Supplier<? extends Item> item, int count) {
            forgeCost.add(SmithingRegistry.Cost.of(item, count));
            return this;
        }

        public Piece zenny(int z) {
            forgeZenny = z;
            return this;
        }

        /** Materials used by level-ups: low (levels 4-5) and high (levels 6-8). */
        public Piece upgrade(Supplier<? extends Item> low, Supplier<? extends Item> high) {
            lowMat = low;
            highMat = high;
            return this;
        }

        public Map<SkillType, Integer> skills() {
            return Collections.unmodifiableMap(skills);
        }

        public int[] resistances() {
            return res.clone();
        }
    }

    /** Extra skill granted when at least {@code pieces} pieces of the set are worn. */
    public record Bonus(int pieces, SkillType skill, int level) {
    }

    private ArmorSet(Builder b) {
        this.id = b.id;
        this.rarity = b.rarity;
        this.color = b.color;
        this.material = new HCArmorMaterial(b.id, b.defense, b.toughness, b.knockback, b.sound);
        this.armorPerLevel = b.armorPerLevel;
        this.toughnessPerLevel = b.toughnessPerLevel;
        this.zennyBase = b.zennyBase;
        this.requiredHR = b.requiredHR;
        this.pieces = b.pieces;
        this.bonuses = List.copyOf(b.bonuses);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public Piece piece(int index) {
        return pieces[index];
    }

    public Piece piece(ArmorItem.Type type) {
        return pieces[type.ordinal()];
    }

    public List<Bonus> bonuses() {
        return bonuses;
    }

    public String itemId(int index) {
        return id + "_" + PIECE_NAMES[index];
    }

    public RegistryObject<Item> item(int index) {
        return items[index];
    }

    /** Adds the set bonus skills for {@code count} worn pieces. */
    public void addBonus(Map<SkillType, Integer> out, int count) {
        for (Bonus b : bonuses) {
            if (count >= b.pieces()) out.merge(b.skill(), b.level(), Integer::sum);
        }
    }

    /** Level-up materials for the given piece (nextLevel 2..8). */
    public List<SmithingRegistry.Cost> upgradeCost(ArmorItem.Type type, int nextLevel) {
        Piece p = piece(type);
        List<SmithingRegistry.Cost> l = new ArrayList<>();
        switch (nextLevel) {
            case 2 -> l.add(SmithingRegistry.Cost.of(com.huntcraft.registry.HCItems.ARMOR_SPHERE, 1));
            case 3 -> l.add(SmithingRegistry.Cost.of(com.huntcraft.registry.HCItems.ARMOR_SPHERE, 2));
            case 4 -> {
                l.add(SmithingRegistry.Cost.of(com.huntcraft.registry.HCItems.ARMOR_SPHERE, 2));
                if (p.lowMat != null) l.add(SmithingRegistry.Cost.of(p.lowMat, 1));
            }
            case 5 -> {
                l.add(SmithingRegistry.Cost.of(com.huntcraft.registry.HCItems.ARMOR_SPHERE, 3));
                if (p.lowMat != null) l.add(SmithingRegistry.Cost.of(p.lowMat, 2));
            }
            case 6 -> {
                l.add(SmithingRegistry.Cost.of(com.huntcraft.registry.HCItems.ARMOR_SPHERE_PLUS, 1));
                if (p.highMat != null) l.add(SmithingRegistry.Cost.of(p.highMat, 1));
            }
            case 7 -> {
                l.add(SmithingRegistry.Cost.of(com.huntcraft.registry.HCItems.ARMOR_SPHERE_PLUS, 2));
                if (p.highMat != null) l.add(SmithingRegistry.Cost.of(p.highMat, 2));
            }
            default -> {
                l.add(SmithingRegistry.Cost.of(com.huntcraft.registry.HCItems.HARD_ARMOR_SPHERE, 1));
                if (p.highMat != null) l.add(SmithingRegistry.Cost.of(p.highMat, 3));
            }
        }
        return l;
    }

    public int upgradeZenny(int nextLevel) {
        return zennyBase * nextLevel;
    }

    // ------------------------------------------------------------------ builder
    public static final class Builder {
        private final String id;
        private int rarity = 1;
        private int color = 0xFFFFFF;
        private int[] defense = {1, 3, 2, 1};
        private float toughness;
        private float knockback;
        private SoundEvent sound = net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC;
        private float armorPerLevel = 0.2F;
        private float toughnessPerLevel;
        private int zennyBase = 100;
        private int requiredHR = 1;
        private final Piece[] pieces = {new Piece(), new Piece(), new Piece(), new Piece()};
        private final List<Bonus> bonuses = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder rarity(int r) {
            rarity = r;
            return this;
        }

        public Builder color(int c) {
            color = c;
            return this;
        }

        /** Armor points helm/mail/coil/greaves, toughness and knockback resistance per piece. */
        public Builder defense(int helm, int mail, int coil, int greaves, float tough, float kb) {
            defense = new int[]{helm, mail, coil, greaves};
            toughness = tough;
            knockback = kb;
            return this;
        }

        public Builder sound(SoundEvent s) {
            sound = s;
            return this;
        }

        public Builder perLevel(float armor, float tough) {
            armorPerLevel = armor;
            toughnessPerLevel = tough;
            return this;
        }

        public Builder zenny(int z) {
            zennyBase = z;
            return this;
        }

        public Builder hr(int hr) {
            requiredHR = hr;
            return this;
        }

        public Builder piece(int index, Consumer<Piece> c) {
            c.accept(pieces[index]);
            return this;
        }

        public Builder bonus(int count, SkillType skill, int level) {
            bonuses.add(new Bonus(count, skill, level));
            return this;
        }

        public ArmorSet build() {
            return new ArmorSet(this);
        }
    }
}
