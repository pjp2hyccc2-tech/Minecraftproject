package com.huntcraft.gear.armor;

import com.huntcraft.HuntCraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Armor material of a HuntCraft series. Durability is 0: hunter armor never breaks (it is levelled up at the
 * smith instead); enchanting is disabled (skills replace enchantments).
 */
public final class HCArmorMaterial implements ArmorMaterial {
    private final String name;
    private final int[] defense;
    private final float toughness;
    private final float knockback;
    private final SoundEvent sound;

    public HCArmorMaterial(String name, int[] defense, float toughness, float knockback, SoundEvent sound) {
        this.name = name;
        this.defense = defense.clone();
        this.toughness = toughness;
        this.knockback = knockback;
        this.sound = sound;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return 0;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return defense[type.ordinal()];
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public SoundEvent getEquipSound() {
        return sound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }

    @Override
    public String getName() {
        return HuntCraft.MODID + ":" + name;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockback;
    }
}
