package com.eternalrealm.item.forge;

import com.eternalrealm.combat.forge.ERItems;
import com.eternalrealm.combat.forge.WeaponItem;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.EquipmentBase;
import com.eternalrealm.item.Materials;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registers the 84 equipment items of the item module (the four Phase 2 iron weapons keep their {@link ERItems}
 * registrations; 14 of them are the trinket bases), the crafting materials, the consumables and the ETERNAL REALM
 * creative tab (every named design and relic included).
 */
public final class ERGearItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModInfo.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModInfo.MOD_ID);

    private static final Map<EquipmentBase, Supplier<? extends Item>> EQUIPMENT = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<MaterialItem>> MATERIALS = new LinkedHashMap<>();
    private static final Map<Consumable, RegistryObject<ConsumableItem>> CONSUMABLES = new EnumMap<>(Consumable.class);

    static {
        for (EquipmentBase base : EquipmentBase.everything()) {
            Supplier<? extends Item> existing = ERItems.byPath(base.itemPath()).orElse(null);
            if (existing != null) {
                EQUIPMENT.put(base, existing);
            } else if (base.isTrinket()) {
                EQUIPMENT.put(base, ITEMS.register(base.itemPath(),
                        () -> new TrinketItem(base, new Item.Properties().stacksTo(1))));
            } else if (base.isWeapon()) {
                EQUIPMENT.put(base, ITEMS.register(base.itemPath(), () -> new WeaponItem(
                        com.eternalrealm.item.TierProfiles.of(base.tier(), base.weaponType()),
                        new Item.Properties().stacksTo(1))));
            } else {
                EQUIPMENT.put(base, ITEMS.register(base.itemPath(),
                        () -> new GearArmorItem(base, new Item.Properties().stacksTo(1))));
            }
        }
        for (String id : Materials.all()) {
            MATERIALS.put(id, ITEMS.register(path(id), () -> new MaterialItem(id, new Item.Properties().stacksTo(64))));
        }
        for (Consumable consumable : Consumable.values()) {
            CONSUMABLES.put(consumable, ITEMS.register(path(consumable.itemId()),
                    () -> new ConsumableItem(consumable, new Item.Properties().stacksTo(consumable.maxStack()))));
        }
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register(ModInfo.MOD_ID, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.eternalrealm"))
            .icon(() -> new ItemStack(ERItems.IRON_LONGSWORD.get()))
            .displayItems((parameters, output) -> {
                for (EquipmentBase base : EquipmentBase.all()) {
                    ItemStack stack = new ItemStack(equipment(base));
                    EquipmentStacks.write(stack, EquipmentStacks.plain(base));
                    output.accept(stack);
                }
                for (com.eternalrealm.item.Design design : com.eternalrealm.item.Armory.designs()) {
                    output.accept(EquipmentStacks.ofDesign(design));
                }
                for (com.eternalrealm.item.Design relic : com.eternalrealm.item.Relics.designs()) {
                    output.accept(EquipmentStacks.ofDesign(relic));
                }
                for (RegistryObject<MaterialItem> material : MATERIALS.values()) {
                    output.accept(material.get());
                }
                for (RegistryObject<ConsumableItem> consumable : CONSUMABLES.values()) {
                    output.accept(consumable.get());
                }
            })
            .build());

    private ERGearItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    /** The item of an equipment base. */
    public static Item equipment(EquipmentBase base) {
        Supplier<? extends Item> item = EQUIPMENT.get(base);
        if (item == null) {
            throw new IllegalArgumentException("Unknown equipment base " + base);
        }
        return item.get();
    }

    /** The item of a material id ({@code eternalrealm:slime_gel}); {@code null} if unknown. */
    public static Item material(String id) {
        RegistryObject<MaterialItem> item = MATERIALS.get(id);
        return item == null ? null : item.get();
    }

    public static ConsumableItem consumable(Consumable consumable) {
        return CONSUMABLES.get(consumable).get();
    }

    public static Map<String, RegistryObject<MaterialItem>> materials() {
        return Collections.unmodifiableMap(MATERIALS);
    }

    private static String path(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }
}
