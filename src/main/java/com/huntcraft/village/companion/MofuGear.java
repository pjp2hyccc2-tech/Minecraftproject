package com.huntcraft.village.companion;

import com.huntcraft.core.Element;
import com.huntcraft.core.SmithingRegistry;
import com.huntcraft.core.SmithingRegistry.Cost;
import com.huntcraft.registry.HCItems;
import com.huntcraft.village.VillageRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Mofu companion equipment: 8 material tiers x 3 slots (weapon / helm / armor). Every piece is a real item forged at
 * the blacksmith (SmithingRegistry category COMPANION) and equipped in the companion screen. Equipped pieces are
 * stored in the companion record by tier index ("gw", "gh", "ga"; -1 = nothing) and drawn on the mofu with the
 * gear model ({@code mofu_gear}) using the tier texture.
 */
public final class MofuGear {
    public enum Slot {
        WEAPON("weapon", "gw"), HELM("helm", "gh"), ARMOR("armor", "ga");

        public final String id;
        public final String key;

        Slot(String id, String key) {
            this.id = id;
            this.key = key;
        }

        public Component displayName() {
            return Component.translatable("mofu.huntcraft.slot." + id);
        }
    }

    /**
     * @param attack  weapon attack bonus
     * @param elemVal weapon element value (bonus damage scaled by the target's weakness stars)
     * @param helmDef helm defense
     * @param armorDef armor defense
     * @param armorHp armor health bonus
     */
    public enum Tier {
        BAMBOO("bamboo", 1, 1, Element.NONE, 2.0F, 0, 1, 2, 4, 0x9BC46A),
        BONE("bone", 2, 1, Element.NONE, 4.0F, 0, 2, 3, 6, 0xE6DCC4),
        AZURITE("azurite", 3, 2, Element.NONE, 6.0F, 0, 3, 5, 8, 0x5A8FD8),
        IGNIVALD("ignivald", 4, 3, Element.FIRE, 7.5F, 6, 4, 6, 10, 0xE05A20),
        FROSTGARM("frostgarm", 4, 3, Element.ICE, 7.5F, 6, 4, 6, 10, 0xA3C4E0),
        VOLTRIX("voltrix", 5, 4, Element.THUNDER, 8.0F, 7, 4, 7, 11, 0xF2D74A),
        GRAVADON("gravadon", 5, 4, Element.WATER, 10.0F, 4, 5, 8, 14, 0x938677),
        AZRAGOS("azragos", 6, 6, Element.DRAGON, 10.0F, 9, 6, 9, 14, 0xB04CFF);

        public final String id;
        public final int rarity;
        public final int requiredHR;
        public final Element element;
        public final float attack;
        public final float elemVal;
        public final int helmDef;
        public final int armorDef;
        public final int armorHp;
        public final int color;

        Tier(String id, int rarity, int requiredHR, Element element, float attack, float elemVal, int helmDef, int armorDef, int armorHp, int color) {
            this.id = id;
            this.rarity = rarity;
            this.requiredHR = requiredHR;
            this.element = element;
            this.attack = attack;
            this.elemVal = elemVal;
            this.helmDef = helmDef;
            this.armorDef = armorDef;
            this.armorHp = armorHp;
            this.color = color;
        }

        public Component displayName() {
            return Component.translatable("mofu.huntcraft.tier." + id);
        }

        public static Tier byIndex(int i) {
            Tier[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }
    }

    /** Registered gear items, by slot then tier. Filled by {@link #registerItems()}. */
    private static final Map<Slot, List<RegistryObject<Item>>> ITEMS = new EnumMap<>(Slot.class);

    private MofuGear() {
    }

    public static String itemId(Slot slot, Tier tier) {
        return "mofu_" + slot.id + "_" + tier.id;
    }

    /** Called from VillageRegistry's static init (DeferredRegister). */
    public static void registerItems() {
        if (!ITEMS.isEmpty()) return;
        for (Slot s : Slot.values()) {
            List<RegistryObject<Item>> l = new ArrayList<>();
            for (Tier t : Tier.values()) {
                l.add(VillageRegistry.ITEMS.register(itemId(s, t), () -> new MofuGearItem(new Item.Properties().stacksTo(1), s, t)));
            }
            ITEMS.put(s, l);
        }
    }

    public static RegistryObject<Item> item(Slot slot, Tier tier) {
        return ITEMS.get(slot).get(tier.ordinal());
    }

    public static List<RegistryObject<Item>> all() {
        List<RegistryObject<Item>> out = new ArrayList<>();
        for (Slot s : Slot.values()) out.addAll(ITEMS.getOrDefault(s, List.of()));
        return out;
    }

    @Nullable
    public static MofuGearItem gearOf(ItemStack st) {
        return st.getItem() instanceof MofuGearItem g ? g : null;
    }

    /** Equipped tier index of a record slot (-1 = none). */
    public static int equipped(net.minecraft.nbt.CompoundTag rec, Slot slot) {
        return rec.contains(slot.key) ? rec.getInt(slot.key) : -1;
    }

    @Nullable
    public static Tier equippedTier(net.minecraft.nbt.CompoundTag rec, Slot slot) {
        int i = equipped(rec, slot);
        return i < 0 ? null : Tier.byIndex(i);
    }

    // ------------------------------------------------------------------ smithing recipes
    /** Registers one COMPANION recipe per gear piece (forge only). Called from VillageModule.init. */
    public static void registerRecipes() {
        for (Slot s : Slot.values()) {
            for (Tier t : Tier.values()) {
                SmithingRegistry.register(new SmithingRegistry.Recipe(itemId(s, t), SmithingRegistry.Category.COMPANION,
                        "mofu_" + s.id, item(s, t), null, costs(s, t), zenny(s, t), t.requiredHR));
            }
        }
    }

    public static int zenny(Slot s, Tier t) {
        int base = switch (t) {
            case BAMBOO -> 80;
            case BONE -> 200;
            case AZURITE -> 450;
            case IGNIVALD, FROSTGARM -> 900;
            case VOLTRIX, GRAVADON -> 1100;
            case AZRAGOS -> 1800;
        };
        return s == Slot.WEAPON ? base + base / 4 : base;
    }

    private static Supplier<Item> vanilla(Item i) {
        return () -> i;
    }

    public static List<Cost> costs(Slot s, Tier t) {
        List<Cost> out = new ArrayList<>();
        switch (t) {
            case BAMBOO -> {
                out.add(Cost.of(vanilla(Items.BAMBOO), s == Slot.WEAPON ? 6 : 4));
                out.add(Cost.of(HCItems.HERB, 2));
            }
            case BONE -> {
                out.add(Cost.of(HCItems.MONSTER_BONE_S, s == Slot.WEAPON ? 4 : 3));
                out.add(Cost.of(s == Slot.ARMOR ? HCItems.BOARFANG_HIDE : HCItems.JAGRAPTOR_HIDE, 2));
            }
            case AZURITE -> {
                out.add(Cost.of(HCItems.AZURITE_ORE, s == Slot.WEAPON ? 3 : 2));
                out.add(Cost.of(HCItems.EARTH_CRYSTAL, 2));
                out.add(Cost.of(HCItems.MONSTER_BONE_M, 1));
            }
            default -> {
                HCItems.MonsterMats m = mats(t);
                if (m != null) {
                    switch (s) {
                        case WEAPON -> {
                            out.add(Cost.of(m.claw, 2));
                            out.add(Cost.of(m.scale, 1));
                        }
                        case HELM -> {
                            out.add(Cost.of(m.scale, 2));
                            out.add(Cost.of(m.shell, 1));
                        }
                        case ARMOR -> {
                            out.add(Cost.of(m.shell, 2));
                            out.add(Cost.of(m.scale, 1));
                        }
                    }
                    if (t == Tier.AZRAGOS) out.add(Cost.of(HCItems.MONSTER_BONE_L, 1));
                }
                out.add(Cost.of(HCItems.MOFU_COIN, t == Tier.AZRAGOS ? 2 : 1));
            }
        }
        return out;
    }

    @Nullable
    private static HCItems.MonsterMats mats(Tier t) {
        return switch (t) {
            case IGNIVALD -> HCItems.IGNIVALD;
            case FROSTGARM -> HCItems.FROSTGARM;
            case VOLTRIX -> HCItems.VOLTRIX;
            case GRAVADON -> HCItems.GRAVADON;
            case AZRAGOS -> HCItems.AZRAGOS;
            default -> null;
        };
    }

    /** Registry name of an item (for records / debugging). */
    public static String key(Item item) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
        return rl == null ? "" : rl.toString().toLowerCase(Locale.ROOT);
    }
}
