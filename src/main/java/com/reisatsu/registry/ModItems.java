package com.reisatsu.registry;

import com.reisatsu.Reisatsu;
import com.reisatsu.item.GameItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/** The game's own items: camera, film, photos, flashlight, お札, keys (owner: game). */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Reisatsu.MODID);
    public static final List<RegistryObject<Item>> GAME_ITEMS = new ArrayList<>();

    public static final RegistryObject<Item> REISHA_CAMERA = game("reisha_camera", GameItem.Kind.CAMERA, single().rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Item> REISHA_CAMERA_FINDER = game("reisha_camera_finder", GameItem.Kind.CAMERA_FINDER, single().rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Item> FILM_PACK = game("film_pack", GameItem.Kind.FILM_PACK, new Item.Properties().stacksTo(8));
    public static final RegistryObject<Item> EVIDENCE_PHOTO = game("evidence_photo", GameItem.Kind.PHOTO, single().rarity(Rarity.RARE));
    public static final RegistryObject<Item> FLASHLIGHT = game("flashlight", GameItem.Kind.FLASHLIGHT, single());
    public static final RegistryObject<Item> FLASHLIGHT_ON = game("flashlight_on", GameItem.Kind.FLASHLIGHT_ON, single());
    public static final RegistryObject<Item> OFUDA = game("ofuda", GameItem.Kind.OFUDA, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Item> SHOP_KEY = game("shop_key", GameItem.Kind.SHOP_KEY, single());
    public static final RegistryObject<Item> OFFICE_KEY = game("office_key", GameItem.Kind.OFFICE_KEY, single());

    private ModItems() {}

    private static RegistryObject<Item> game(String name, GameItem.Kind kind, Item.Properties props) {
        RegistryObject<Item> ro = ITEMS.register(name, () -> new GameItem(kind, props));
        GAME_ITEMS.add(ro);
        return ro;
    }

    private static Item.Properties single() {
        return new Item.Properties().stacksTo(1);
    }
}
