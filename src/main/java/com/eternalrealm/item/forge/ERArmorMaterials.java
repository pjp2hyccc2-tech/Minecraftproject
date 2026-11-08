package com.eternalrealm.item.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.ItemTier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * One armor material per {@link ItemTier}. Defense, toughness and durability are zero on purpose: the defense of a
 * piece is an ETERNAL REALM stat (item module) applied by the combat system, so vanilla armor points would count it
 * twice, and gear does not wear out. The material name selects the worn texture of the tier
 * {@code eternalrealm:textures/models/armor/er_tier<n>_layer_<1|2>.png} (tools/art); job outfits replace it per stack
 * ({@link GearArmorItem#getArmorTexture}).
 */
public enum ERArmorMaterials implements ArmorMaterial {
    IRON(ItemTier.IRON, SoundEvents.ARMOR_EQUIP_CHAIN),
    STEEL(ItemTier.STEEL, SoundEvents.ARMOR_EQUIP_IRON),
    SANDSTONE(ItemTier.SANDSTONE, SoundEvents.ARMOR_EQUIP_GOLD),
    FROST(ItemTier.FROST, SoundEvents.ARMOR_EQUIP_DIAMOND),
    FLAME(ItemTier.FLAME, SoundEvents.ARMOR_EQUIP_NETHERITE),
    CELESTIAL(ItemTier.CELESTIAL, SoundEvents.ARMOR_EQUIP_GOLD),
    ABYSS(ItemTier.ABYSS, SoundEvents.ARMOR_EQUIP_NETHERITE);

    private final ItemTier tier;
    private final SoundEvent equipSound;

    ERArmorMaterials(ItemTier tier, SoundEvent equipSound) {
        this.tier = tier;
        this.equipSound = equipSound;
    }

    public static ERArmorMaterials of(ItemTier tier) {
        for (ERArmorMaterials material : values()) {
            if (material.tier == tier) {
                return material;
            }
        }
        throw new IllegalArgumentException("No armor material for " + tier);
    }

    public ItemTier tier() {
        return tier;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return 0;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return 0;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public SoundEvent getEquipSound() {
        return equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }

    @Override
    public String getName() {
        return ModInfo.MOD_ID + ":er_tier" + tier.number();
    }

    @Override
    public float getToughness() {
        return 0.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0F;
    }
}
