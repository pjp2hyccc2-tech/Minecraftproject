package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.core.ModInfo;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** Item registrations: one weapon item per weapon type (tiers and rarities are stored on the stack). */
public final class ERItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModInfo.MOD_ID);

    public static final RegistryObject<WeaponItem> IRON_LONGSWORD = weapon("iron_longsword", WeaponProfiles.ONE_HANDED_SWORD);
    public static final RegistryObject<WeaponItem> IRON_GREATSWORD = weapon("iron_greatsword", WeaponProfiles.TWO_HANDED_SWORD);
    public static final RegistryObject<WeaponItem> IRON_TWIN_BLADES = weapon("iron_twin_blades", WeaponProfiles.DUAL_BLADES);
    public static final RegistryObject<WeaponItem> IRON_SPEAR = weapon("iron_spear", WeaponProfiles.SPEAR);
    public static final RegistryObject<WeaponItem> STAFF = weapon("staff", WeaponProfiles.STAFF);
    public static final RegistryObject<WeaponItem> LONGBOW = weapon("longbow", WeaponProfiles.BOW);

    private static final List<RegistryObject<WeaponItem>> WEAPONS =
            List.of(IRON_LONGSWORD, IRON_GREATSWORD, IRON_TWIN_BLADES, IRON_SPEAR, STAFF, LONGBOW);

    private ERItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    public static List<RegistryObject<WeaponItem>> weapons() {
        return WEAPONS;
    }

    /** The weapon item of a weapon type. */
    public static WeaponItem weaponFor(WeaponType type) {
        for (RegistryObject<WeaponItem> weapon : WEAPONS) {
            if (weapon.get().profile().type() == type) {
                return weapon.get();
            }
        }
        throw new IllegalStateException("No weapon item for " + type);
    }

    private static RegistryObject<WeaponItem> weapon(String name, WeaponProfile profile) {
        return ITEMS.register(name, () -> new WeaponItem(profile, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    }
}
