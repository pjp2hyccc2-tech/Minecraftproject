package com.eternalrealm.item.forge;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.item.Armory;
import com.eternalrealm.item.Design;
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
 *
 * <p>A named design ({@link Armory}) or a relic is also written as its id ({@value #DESIGN_TAG}) and its model
 * number ({@code CustomModelData}), so every client draws the design's own icon and model and the combat code finds
 * the design's profile without decoding the data every tick.</p>
 *
 * <p>Other modules (the market, the trading board) filter stacks with {@link #isGoldFrame}, {@link #requiredLevel}
 * and {@link #rarity}.</p>
 */
public final class EquipmentStacks {
    public static final String TAG = "ERGear";
    public static final String DESIGN_TAG = "ERDesign";
    public static final String MODEL_TAG = "CustomModelData";

    private static volatile Map<Item, EquipmentBase> bases;
    /** Level of the player looking at a tooltip; the client installs the real value. */
    private static volatile IntSupplier viewerLevel = () -> Integer.MAX_VALUE;
    /** Job of the player looking at a tooltip; the client installs the real value. */
    private static volatile java.util.function.Supplier<com.eternalrealm.job.JobType> viewerJob =
            () -> com.eternalrealm.job.JobType.NONE;

    private EquipmentStacks() {
    }

    public static void setViewerLevel(IntSupplier supplier) {
        viewerLevel = supplier;
    }

    public static int viewerLevel() {
        return viewerLevel.getAsInt();
    }

    public static void setViewerJob(java.util.function.Supplier<com.eternalrealm.job.JobType> supplier) {
        viewerJob = supplier;
    }

    public static com.eternalrealm.job.JobType viewerJob() {
        com.eternalrealm.job.JobType job = viewerJob.get();
        return job == null ? com.eternalrealm.job.JobType.NONE : job;
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
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG, EquipmentCodec.encode(data));
        if (data.design() != null) {
            tag.putString(DESIGN_TAG, data.design().id());
            tag.putInt(MODEL_TAG, data.design().model());
        } else if (tag.contains(DESIGN_TAG)) {
            tag.remove(DESIGN_TAG);
            if (tag.getInt(MODEL_TAG) >= Design.WEAPON_MODELS) {
                tag.remove(MODEL_TAG);
            }
        }
    }

    /** A new stack of the data's base carrying the data (a unique id is assigned if it has none). */
    public static ItemStack create(EquipmentData data) {
        ItemStack stack = new ItemStack(ERGearItems.equipment(data.base()));
        write(stack, data.ensureUniqueId(UUID::randomUUID));
        return stack;
    }

    /**
     * A plain common piece of a design (creative menu, smithy previews, monsters' hands); a relic always comes as
     * legendary, its affixes rolled from the design's model number (the same piece every time).
     */
    public static ItemStack ofDesign(Design design) {
        ItemStack stack = new ItemStack(ERGearItems.equipment(design.base()));
        if (design.isRelic()) {
            write(stack, com.eternalrealm.item.EquipmentGenerator.roll(design, ItemRarity.LEGENDARY,
                    new java.util.SplittableRandom(design.model())));
        } else {
            write(stack, EquipmentData.of(design, ItemRarity.COMMON, List.of()));
        }
        return stack;
    }

    // ------------------------------------------------------------------ for other modules (market, trading board)

    /**
     * Whether the stack is a relic (金枠の遺宝): its frame is gold everywhere. Fast (reads the design tag, no
     * decoding); false for every other stack.
     */
    public static boolean isGoldFrame(ItemStack stack) {
        return designOf(stack).map(Design::isRelic).orElse(false);
    }

    /** Character level needed to equip the stack, if it is equipment (rarity and relic rules included). */
    public static java.util.OptionalInt requiredLevel(ItemStack stack) {
        return read(stack).map(data -> java.util.OptionalInt.of(data.requiredLevel()))
                .orElse(java.util.OptionalInt.empty());
    }

    /** The stack's rarity, if it is equipment (relics are legendary). */
    public static Optional<ItemRarity> rarity(ItemStack stack) {
        return read(stack).map(EquipmentData::rarity);
    }

    /**
     * The design a stack is made as, from its design tag (fast: no decoding), if the tag names a design of the stack's
     * own base; empty for plain pieces and every other item.
     */
    public static Optional<Design> designOf(ItemStack stack) {
        CompoundTag tag = stack.isEmpty() ? null : stack.getTag();
        if (tag == null || !tag.contains(DESIGN_TAG, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        EquipmentBase base = bases().get(stack.getItem());
        return base == null ? Optional.empty()
                : Armory.design(tag.getString(DESIGN_TAG)).filter(design -> design.base().equals(base));
    }

    // ------------------------------------------------------------------ text

    /** The rarity-coloured name ("+3 鉄の片手剣"); empty if the stack is not equipment. */
    public static Optional<Component> displayName(ItemStack stack) {
        return read(stack).map(data -> component(EquipmentTooltip.name(data)));
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        read(stack).ifPresent(data -> {
            for (TooltipLine line : EquipmentTooltip.lines(data, viewerLevel(), viewerJob())) {
                tooltip.add(component(line));
            }
        });
    }

    /** Epic and legendary pieces, boss weapons, relics, and the tiers that always shine, render with the glint. */
    public static boolean isFoil(ItemStack stack) {
        return read(stack).map(data -> data.tier().glint() || data.rarity().rank() >= ItemRarity.EPIC.rank()
                || data.isRelic()
                || data.weaponDesign().map(com.eternalrealm.item.WeaponDesign::isBossWeapon).orElse(false))
                .orElse(false);
    }

    /** A pure tooltip line as a Minecraft component in the line's colour. */
    public static MutableComponent component(TooltipLine line) {
        Object[] args = new Object[line.args().size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = argument(line.args().get(i));
        }
        int color = line.color();
        return Component.translatable(line.translationKey(), args).withStyle(style -> style.withColor(color));
    }

    private static Object argument(TooltipLine.Arg arg) {
        if (arg instanceof TooltipLine.Key key) {
            return Component.translatable(key.translationKey());
        }
        if (arg instanceof TooltipLine.Text text) {
            return text.text();
        }
        if (arg instanceof TooltipLine.Nested nested) {
            Object[] args = new Object[nested.args().size()];
            for (int i = 0; i < args.length; i++) {
                args[i] = argument(nested.args().get(i));
            }
            return Component.translatable(nested.translationKey(), args);
        }
        if (arg instanceof TooltipLine.Join join) {
            MutableComponent joined = Component.empty();
            for (int i = 0; i < join.parts().size(); i++) {
                if (i > 0) {
                    joined.append(Component.translatable(join.separatorKey()));
                }
                Object part = argument(join.parts().get(i));
                joined.append(part instanceof Component component ? component : Component.literal(part.toString()));
            }
            return joined;
        }
        return "";
    }

    private static Map<Item, EquipmentBase> bases() {
        Map<Item, EquipmentBase> map = bases;
        if (map == null) {
            map = new IdentityHashMap<>();
            for (EquipmentBase base : EquipmentBase.everything()) {
                map.put(ERGearItems.equipment(base), base);
            }
            bases = map;
        }
        return map;
    }
}
