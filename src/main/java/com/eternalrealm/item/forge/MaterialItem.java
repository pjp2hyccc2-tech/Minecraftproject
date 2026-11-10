package com.eternalrealm.item.forge;

import com.eternalrealm.item.ItemPrices;
import com.eternalrealm.item.Materials;
import com.eternalrealm.world.Areas;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** A crafting material dropped by monsters (or an enhancement stone / reroll crystal). */
public final class MaterialItem extends Item {
    private final String id;

    public MaterialItem(String id, Properties properties) {
        super(properties);
        this.id = id;
    }

    public String materialId() {
        return id;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        java.util.Optional<Materials.FieldMaterial> field = Materials.fieldMaterial(id);
        OptionalInt area = Materials.areaOf(id);
        if (field.isPresent() && Areas.exists(field.get().field())) {
            ChatFormatting color = switch (field.get().grade()) {
                case COMMON -> ChatFormatting.GRAY;
                case UNCOMMON -> ChatFormatting.GREEN;
                case ELITE -> ChatFormatting.AQUA;
                case BOSS -> ChatFormatting.LIGHT_PURPLE;
            };
            tooltip.add(Component.translatable("tooltip.eternalrealm.material.grade."
                            + field.get().grade().name().toLowerCase(java.util.Locale.ROOT),
                    Component.translatable(Areas.require(field.get().field()).translationKey())).withStyle(color));
        } else if (area.isPresent() && Areas.exists(area.getAsInt())) {
            tooltip.add(Component.translatable("tooltip.eternalrealm.material.area",
                    Component.translatable(Areas.require(area.getAsInt()).translationKey())).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.eternalrealm.material").withStyle(ChatFormatting.GRAY));
        }
        OptionalLong sell = ItemPrices.materialSellValue(id);
        if (sell.isPresent()) {
            tooltip.add(Component.translatable("tooltip.eternalrealm.sell_value", sell.getAsLong())
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
