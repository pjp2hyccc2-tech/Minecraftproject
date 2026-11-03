package com.hitori.registry;

import com.hitori.Hitori;
import com.hitori.item.GameItem;
import com.hitori.item.GameItem.Kind;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The game's own items (docs/DESIGN.md §7 Items): the ritual's things, the cameras, the laptop and the phone, the
 * lighter, the printout (owner: game). {@code phone_light} is the phone with its flashlight on (§9: the shader reads
 * the held item id).
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Hitori.MODID);
    public static final List<RegistryObject<Item>> GAME_ITEMS = new ArrayList<>();

    public static final RegistryObject<Item> PLUSH = game("plush", Kind.PLUSH, single());
    public static final RegistryObject<Item> PLUSH_OPEN = game("plush_open", Kind.PLUSH_OPEN, single());
    public static final RegistryObject<Item> PLUSH_RICE = game("plush_rice", Kind.PLUSH_RICE, single());
    public static final RegistryObject<Item> RITUAL_DOLL = game("ritual_doll", Kind.RITUAL_DOLL, single().rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Item> SCISSORS = game("scissors", Kind.SCISSORS, single());
    public static final RegistryObject<Item> RICE_SCOOP = game("rice_scoop", Kind.RICE_SCOOP, single());
    public static final RegistryObject<Item> RED_THREAD = game("red_thread", Kind.RED_THREAD, single());
    public static final RegistryObject<Item> NAIL_CLIPPER = game("nail_clipper", Kind.NAIL_CLIPPER, single());
    public static final RegistryObject<Item> KNIFE = game("knife", Kind.KNIFE, single());
    public static final RegistryObject<Item> SALT = game("salt", Kind.SALT, single());
    public static final RegistryObject<Item> CUP_WATER = game("cup_water", Kind.CUP_WATER, single());
    public static final RegistryObject<Item> SALT_WATER = game("salt_water", Kind.SALT_WATER, single());
    public static final RegistryObject<Item> FIXED_CAMERA = game("fixed_camera", Kind.FIXED_CAMERA, new Item.Properties().stacksTo(4));
    public static final RegistryObject<Item> LAPTOP = game("laptop", Kind.LAPTOP, single());
    public static final RegistryObject<Item> PHONE = game("phone", Kind.PHONE, single());
    public static final RegistryObject<Item> PHONE_LIGHT = game("phone_light", Kind.PHONE_LIGHT, single());
    public static final RegistryObject<Item> LIGHTER = game("lighter", Kind.LIGHTER, single());
    public static final RegistryObject<Item> PRINTOUT = game("printout", Kind.PRINTOUT, single());

    private ModItems() {}

    private static RegistryObject<Item> game(String name, Kind kind, Item.Properties props) {
        RegistryObject<Item> ro = ITEMS.register(name, () -> new GameItem(kind, props));
        GAME_ITEMS.add(ro);
        return ro;
    }

    private static Item.Properties single() {
        return new Item.Properties().stacksTo(1);
    }

    public static Item of(Kind kind) {
        return switch (kind) {
            case PLUSH -> PLUSH.get();
            case PLUSH_OPEN -> PLUSH_OPEN.get();
            case PLUSH_RICE -> PLUSH_RICE.get();
            case RITUAL_DOLL -> RITUAL_DOLL.get();
            case SCISSORS -> SCISSORS.get();
            case RICE_SCOOP -> RICE_SCOOP.get();
            case RED_THREAD -> RED_THREAD.get();
            case NAIL_CLIPPER -> NAIL_CLIPPER.get();
            case KNIFE -> KNIFE.get();
            case SALT -> SALT.get();
            case CUP_WATER -> CUP_WATER.get();
            case SALT_WATER -> SALT_WATER.get();
            case FIXED_CAMERA -> FIXED_CAMERA.get();
            case LAPTOP -> LAPTOP.get();
            case PHONE -> PHONE.get();
            case PHONE_LIGHT -> PHONE_LIGHT.get();
            case LIGHTER -> LIGHTER.get();
            case PRINTOUT -> PRINTOUT.get();
        };
    }
}
