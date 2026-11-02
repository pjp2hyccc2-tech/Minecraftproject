package com.clockworkdiver.item;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Chrono Spectacles (時視の眼鏡): at the repair bench they reveal the movement inside a clock and the
 * faulty parts; inside a clock they make enemies and repair targets glow.
 */
public class SpectaclesItem extends ArmorItem {
    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return 0;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return 1;
        }

        @Override
        public int getEnchantmentValue() {
            return 15;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_GOLD;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(ModItems.BRASS_COIN.get());
        }

        @Override
        public String getName() {
            return ClockworkDiver.MODID + ":chrono_spectacles";
        }

        @Override
        public float getToughness() {
            return 0.0f;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0f;
        }
    };

    public SpectaclesItem(Properties properties) {
        super(MATERIAL, ArmorItem.Type.HELMET, properties);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return ClockworkDiver.MODID + ":textures/models/armor/chrono_spectacles_layer_1.png";
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.clockworkdiver.spectacles.0").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.clockworkdiver.spectacles.1").withStyle(ChatFormatting.GRAY));
    }
}
