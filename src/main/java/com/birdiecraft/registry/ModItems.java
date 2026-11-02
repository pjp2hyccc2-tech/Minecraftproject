package com.birdiecraft.registry;

import com.birdiecraft.BirdieCraft;
import com.birdiecraft.item.CourseWandItem;
import com.birdiecraft.item.GolfBagItem;
import com.birdiecraft.item.GolfClubItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BirdieCraft.MODID);

    public static final RegistryObject<Item> GOLF_CLUB = ITEMS.register("golf_club", () -> new GolfClubItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GOLF_BAG = ITEMS.register("golf_bag", () -> new GolfBagItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COURSE_WAND = ITEMS.register("course_wand", () -> new CourseWandItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GOLF_BALL = ITEMS.register("golf_ball", () -> new Item(new Item.Properties().stacksTo(16)));

    private ModItems() {
    }
}
