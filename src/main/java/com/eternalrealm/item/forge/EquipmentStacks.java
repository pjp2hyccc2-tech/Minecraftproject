package com.eternalrealm.item.forge;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.item.EquipmentBase;
import com.eternalrealm.item.EquipmentCodec;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.EquipmentTooltip;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.TooltipLine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Equipment data on item stacks. A stack of an equipment item stores its {@link EquipmentData} (rarity, affixes,
 * enhancement, unique id, binding) as the {@link EquipmentCodec} string in the tag {@value #TAG}; a stack without the
 * tag (creative menu, /give, the starter weapon of older versions) is a plain common piece of its base.
 */
public final class EquipmentStacks {
    public static final String TAG = "ERGear";

    private static volatile Map<Item, EquipmentBase> bases;
    /** Level of the player looking at a tooltip; the client installs the real value. */
    private static volatile IntSupplier viewerLevel = () -> Integer.MAX_VALUE;

    private EquipmentStacks() {
    }

    public static void setViewerLevel(IntSupplier supplier) {
        viewerLevel = supplier;
    }

    public static int viewerLevel() {
        return viewerLevel.getAsInt();
    }

    /** The base of an equipment item; empty for every other item. */
    public static Optional<EquipmentBase> baseOf(Item item) {
        return Optional.ofNullable(bases().get(item));
    }

    public static boolean isEquipment(ItemStack stack) {
        return !stack.isEmpty() && bases().containsKey(stack.getItem());
    }

    /** The stack's equipment data; empty if the stack is not equipment. Unreadable data counts as a plain piece. */
    public static Optional<EquipmentData> read(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        EquipmentBase base = bases().get(stack.getItem());
        if (base == null) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG, Tag.TAG_STRING)) {
            try {
                EquipmentData data = EquipmentCodec.decode(tag.getString(TAG));
                if (data.base().equals(base)) {
                    return Optional.of(data);
                }
            } catch (CorruptDataException | IncompatibleDataException | RuntimeException e) {
                // fall through: a damaged tag must never make the item unusable
            }
        }
        return Optional.of(plain(base));
    }

    public static EquipmentData plain(EquipmentBase base) {
        return EquipmentData.of(base, ItemRarity.COMMON, List.of());
    }

    public static void write(ItemStack stack, EquipmentData data) {
        stack.getOrCreateTag().putString(TAG, EquipmentCodec.encode(data));
    }

    /** A new stack of the data's base carrying the data (a unique id is assigned if it has none). */
    public static ItemStack create(EquipmentData data) {
        ItemStack stack = new ItemStack(ERGearItems.equipment(data.base()));
        write(stack, data.ensureUniqueId(UUID::randomUUID));
        return stack;
    }

    // ------------------------------------------------------------------ text

    /** The rarity-coloured name ("+3 鉄の片手剣"); empty if the stack is not equipment. */
    public static Optional<Component> displayName(ItemStack stack) {
        return read(stack).map(data -> component(EquipmentTooltip.name(data)));
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        read(stack).ifPresent(data -> {
            int level = viewerLevel();
            for (TooltipLine line : level == Integer.MAX_VALUE ? EquipmentTooltip.lines(data)
                    : EquipmentTooltip.lines(data, level)) {
                tooltip.add(component(line));
            }
        });
    }

    /** Epic and legendary pieces, and the tiers that always shine, render with the glint. */
    public static boolean isFoil(ItemStack stack) {
        return read(stack).map(data -> data.tier().glint() || data.rarity().rank() >= ItemRarity.EPIC.rank())
                .orElse(false);
    }

    /** A pure tooltip line as a Minecraft component in the line's colour. */
    public static MutableComponent component(TooltipLine line) {
        Object[] args = new Object[line.args().size()];
        for (int i = 0; i < args.length; i++) {
            TooltipLine.Arg arg = line.args().get(i);
            if (arg instanceof TooltipLine.Key key) {
                args[i] = Component.translatable(key.translationKey());
            } else if (arg instanceof TooltipLine.Text text) {
                args[i] = text.text();
            } else {
                args[i] = "";
            }
        }
        int color = line.color();
        return Component.translatable(line.translationKey(), args).withStyle(style -> style.withColor(color));
    }

    private static Map<Item, EquipmentBase> bases() {
        Map<Item, EquipmentBase> map = bases;
        if (map == null) {
            map = new IdentityHashMap<>();
            for (EquipmentBase base : EquipmentBase.all()) {
                map.put(ERGearItems.equipment(base), base);
            }
            bases = map;
        }
        return map;
    }
}
