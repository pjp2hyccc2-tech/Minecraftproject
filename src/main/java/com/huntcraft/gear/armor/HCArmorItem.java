package com.huntcraft.gear.armor;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.huntcraft.HuntCraft;
import com.huntcraft.core.Element;
import com.huntcraft.core.IUpgradeable;
import com.huntcraft.core.SkillType;
import com.huntcraft.core.SmithingRegistry;
import com.huntcraft.item.MaterialItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * HuntCraft armor piece: defense grows with the smith level (1..8, NBT "HCLevel"), carries skills and
 * elemental resistances (summed by {@link ArmorSkills}), uses a custom humanoid model per series.
 */
public class HCArmorItem extends ArmorItem implements IUpgradeable {
    /** Same UUIDs as vanilla armor so modifiers replace each other per slot. */
    private static final UUID[] SLOT_UUIDS = {
            UUID.fromString("2AD3F246-FEE1-4E67-B886-69FD380BB150"),
            UUID.fromString("9F3D476D-C118-4544-8365-64846904B48E"),
            UUID.fromString("D8499B04-0E66-4726-AB29-64469D734E0D"),
            UUID.fromString("845DB27C-C624-495F-8C9F-6020A9A58B6B")};

    public final ArmorSet set;
    private final ArmorSet.Piece piece;
    private final List<Multimap<Attribute, AttributeModifier>> byLevel = new ArrayList<>();

    public HCArmorItem(ArmorSet set, ArmorItem.Type type, Properties props) {
        super(set.material, type, props);
        this.set = set;
        this.piece = set.piece(type);
        for (int lv = 1; lv <= ArmorSet.MAX_LEVEL; lv++) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
            UUID uuid = SLOT_UUIDS[type.ordinal()];
            b.put(Attributes.ARMOR, new AttributeModifier(uuid, "Armor modifier", armorAt(lv), AttributeModifier.Operation.ADDITION));
            float tough = toughnessAt(lv);
            if (tough > 0) b.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(uuid, "Armor toughness", tough, AttributeModifier.Operation.ADDITION));
            if (set.material.getKnockbackResistance() > 0) {
                b.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(uuid, "Armor knockback resistance",
                        set.material.getKnockbackResistance(), AttributeModifier.Operation.ADDITION));
            }
            byLevel.add(b.build());
        }
    }

    public ArmorSet.Piece piece() {
        return piece;
    }

    public double armorAt(int level) {
        double v = set.material.getDefenseForType(getType()) + set.armorPerLevel * (level - 1);
        return Math.round(v * 100.0) / 100.0;
    }

    public float toughnessAt(int level) {
        float v = set.material.getToughness() + set.toughnessPerLevel * (level - 1);
        return Math.round(v * 100.0F) / 100.0F;
    }

    /** Adds this piece's skills and resistances. */
    public void addSkills(Map<SkillType, Integer> out) {
        piece.skills.forEach((k, v) -> out.merge(k, v, Integer::sum));
        for (int i = 0; i < 5; i++) {
            if (piece.res[i] != 0) out.merge(ArmorSet.RES[i], piece.res[i], Integer::sum);
        }
    }

    // ------------------------------------------------------------------ attributes / visuals
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != getType().getSlot()) return ImmutableMultimap.of();
        int lv = Math.max(1, Math.min(ArmorSet.MAX_LEVEL, getLevel(stack)));
        return byLevel.get(lv - 1);
    }

    @Override
    public @Nullable String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return HuntCraft.MODID + ":textures/models/armor/" + set.id + (slot == EquipmentSlot.LEGS ? "_layer_2" : "_layer_1") + ".png";
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                HumanoidModel<?> m = com.huntcraft.gear.armor.client.ArmorModels.get(set.id, slot == EquipmentSlot.LEGS);
                return m != null ? m : original;
            }
        });
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return false;
    }

    // ------------------------------------------------------------------ IUpgradeable
    @Override
    public int getMaxLevel(ItemStack stack) {
        return ArmorSet.MAX_LEVEL;
    }

    @Override
    public List<SmithingRegistry.Cost> getUpgradeCost(ItemStack stack, int nextLevel) {
        return set.upgradeCost(getType(), nextLevel);
    }

    @Override
    public int getUpgradeZenny(ItemStack stack, int nextLevel) {
        return set.upgradeZenny(nextLevel);
    }

    // ------------------------------------------------------------------ text
    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(MaterialItem.rarityColor(set.rarity))));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int lv = getLevel(stack);
        tooltip.add(Component.translatable("armorset.huntcraft." + set.id).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(set.color)))
                .append(Component.literal("  "))
                .append(Component.translatable("tooltip.huntcraft.rarity", set.rarity).withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.translatable("tooltip.huntcraft.armor.level", lv, ArmorSet.MAX_LEVEL).withStyle(ChatFormatting.GOLD)
                .append(Component.literal("  "))
                .append(Component.translatable("tooltip.huntcraft.armor.defense", defenseRating(lv)).withStyle(ChatFormatting.WHITE)));
        // resistances
        MutableComponent res = Component.translatable("tooltip.huntcraft.armor.res").withStyle(ChatFormatting.GRAY);
        Element[] els = {Element.FIRE, Element.WATER, Element.THUNDER, Element.ICE, Element.DRAGON};
        for (int i = 0; i < 5; i++) {
            int v = piece.res[i];
            res.append(Component.literal(" "));
            res.append(els[i].displayName());
            String num = v > 0 ? "+" + v : String.valueOf(v);
            res.append(Component.literal(num).withStyle(v > 0 ? ChatFormatting.AQUA : v < 0 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        }
        tooltip.add(res);
        // skills
        tooltip.add(Component.translatable("tooltip.huntcraft.armor.skills").withStyle(ChatFormatting.GRAY));
        piece.skills.forEach((k, v) -> tooltip.add(Component.literal("  ")
                .append(Component.translatable(k.key()).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" Lv" + v).withStyle(ChatFormatting.WHITE))));
        // set bonus
        if (!set.bonuses().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.huntcraft.armor.bonus", Component.translatable("armorset.huntcraft." + set.id + ".bonus"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            for (ArmorSet.Bonus b : set.bonuses()) {
                tooltip.add(Component.literal("  ")
                        .append(Component.translatable("tooltip.huntcraft.armor.pieces", b.pieces()).withStyle(ChatFormatting.DARK_PURPLE))
                        .append(Component.literal(" "))
                        .append(Component.translatable(b.skill().key()).withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" +" + b.level()).withStyle(ChatFormatting.WHITE)));
            }
        }
        // next upgrade
        if (lv < ArmorSet.MAX_LEVEL) {
            int next = lv + 1;
            MutableComponent up = Component.translatable("tooltip.huntcraft.armor.next", next).withStyle(ChatFormatting.DARK_GRAY);
            boolean first = true;
            for (SmithingRegistry.Cost c : getUpgradeCost(stack, next)) {
                up.append(Component.literal(first ? " " : ", ").withStyle(ChatFormatting.DARK_GRAY));
                up.append(Component.translatable(c.item().get().getDescriptionId()).withStyle(ChatFormatting.DARK_GRAY));
                up.append(Component.literal(" x" + c.count()).withStyle(ChatFormatting.DARK_GRAY));
                first = false;
            }
            up.append(Component.literal(" (" + getUpgradeZenny(stack, next) + "z)").withStyle(ChatFormatting.DARK_GRAY));
            if (getRequiredHR(stack, next) > 1) {
                up.append(Component.literal(" ").append(Component.translatable("tooltip.huntcraft.armor.hr", getRequiredHR(stack, next)))
                        .withStyle(ChatFormatting.DARK_RED));
            }
            tooltip.add(up);
        }
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** MH-style defense number shown in the tooltip (armor and toughness combined). */
    public int defenseRating(int level) {
        return (int) Math.round((armorAt(level) + toughnessAt(level) * 0.5) * 10.0);
    }
}
