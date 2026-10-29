package com.huntcraft.gear.weapon;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.CoreEvents;
import com.huntcraft.core.HuntHooks;
import com.huntcraft.core.ShopRegistry;
import com.huntcraft.core.WeaponClass;
import com.huntcraft.registry.HCCreativeTabs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Module entry point: 8 weapon types x 7 weapon lines (iron, bone + one per large monster), whetstone, bow coatings,
 * bow arrows, sharpness, weapon HUD (client package). Called from the HuntCraft constructor.
 */
public final class WeaponsModule {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HuntCraft.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HuntCraft.MODID);

    /** weapon class -> line -> item. */
    public static final Map<WeaponClass, Map<WeaponLine, RegistryObject<Item>>> WEAPONS = new EnumMap<>(WeaponClass.class);
    public static final List<RegistryObject<Item>> ALL_WEAPONS = new ArrayList<>();

    public static final RegistryObject<Item> WHETSTONE = ITEMS.register("whetstone", () -> new WhetstoneItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COATING_POWER = ITEMS.register("coating_power", () -> new CoatingItem(new Item.Properties().stacksTo(64), HunterBowItem.COAT_POWER));
    public static final RegistryObject<Item> COATING_POISON = ITEMS.register("coating_poison", () -> new CoatingItem(new Item.Properties().stacksTo(64), HunterBowItem.COAT_POISON));
    public static final RegistryObject<Item> COATING_PARA = ITEMS.register("coating_para", () -> new CoatingItem(new Item.Properties().stacksTo(64), HunterBowItem.COAT_PARA));

    public static final RegistryObject<EntityType<HunterArrow>> HUNTER_ARROW = ENTITIES.register("hunter_arrow",
            () -> EntityType.Builder.<HunterArrow>of(HunterArrow::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(4).updateInterval(20).build("hunter_arrow"));

    static {
        for (WeaponClass c : WeaponClass.values()) {
            Map<WeaponLine, RegistryObject<Item>> byLine = new EnumMap<>(WeaponLine.class);
            for (WeaponLine l : WeaponLine.values()) {
                RegistryObject<Item> ro = ITEMS.register(itemId(c, l), () -> create(c, l));
                byLine.put(l, ro);
                ALL_WEAPONS.add(ro);
            }
            WEAPONS.put(c, byLine);
        }
    }

    private WeaponsModule() {
    }

    public static String itemId(WeaponClass c, WeaponLine l) {
        return l.id + "_" + c.name().toLowerCase(Locale.ROOT);
    }

    private static Item create(WeaponClass c, WeaponLine l) {
        Item.Properties p = new Item.Properties();
        return switch (c) {
            case GREAT_SWORD -> new GreatSwordItem(l, p);
            case LONG_SWORD -> new LongSwordItem(l, p);
            case SWORD_AND_SHIELD -> new SwordShieldItem(l, p);
            case DUAL_BLADES -> new DualBladesItem(l, p);
            case HAMMER -> new HammerItem(l, p);
            case LANCE -> new LanceItem(l, p);
            case HUNTING_HORN -> new HuntingHornItem(l, p);
            case BOW -> new HunterBowItem(l, p);
        };
    }

    public static RegistryObject<Item> weapon(WeaponClass c, WeaponLine l) {
        return WEAPONS.get(c).get(l);
    }

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        WeaponSounds.SOUNDS.register(modBus);
        modBus.addListener(WeaponsModule::tabs);

        WeaponRecipes.registerAll();
        WeaponNet.registerServerActions();
        HuntHooks.addAttackMultiplier(HornSongs::attackMultiplier);
        HuntHooks.addStarterKit(p -> {
            CoreEvents.give(p, new ItemStack(WHETSTONE.get()));
            CoreEvents.give(p, new ItemStack(weapon(WeaponClass.SWORD_AND_SHIELD, WeaponLine.IRON).get()));
            CoreEvents.give(p, new ItemStack(weapon(WeaponClass.GREAT_SWORD, WeaponLine.IRON).get()));
        });
        ShopRegistry.add("whetstone", WHETSTONE, 100, 1, "tool");
        ShopRegistry.add("coating_power", COATING_POWER, 60, 2, "tool");
        ShopRegistry.add("coating_poison", COATING_POISON, 45, 1, "tool");
        ShopRegistry.add("coating_para", COATING_PARA, 55, 2, "tool");
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(HCCreativeTabs.WEAPONS.getKey())) return;
        for (WeaponClass c : WeaponClass.values()) {
            for (WeaponLine l : WeaponLine.values()) event.accept(weapon(c, l));
        }
        event.accept(WHETSTONE);
        event.accept(COATING_POWER);
        event.accept(COATING_POISON);
        event.accept(COATING_PARA);
    }
}
