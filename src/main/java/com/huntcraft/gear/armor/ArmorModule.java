package com.huntcraft.gear.armor;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HuntHooks;
import com.huntcraft.core.HunterSkills;
import com.huntcraft.core.SmithingRegistry;
import com.huntcraft.registry.HCCreativeTabs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Module entry point: armor sets, skills, charms, item art. Called from the HuntCraft constructor. */
public final class ArmorModule {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HuntCraft.MODID);

    public static final RegistryObject<Item> CHARM = ITEMS.register("charm", () -> new CharmItem(new Item.Properties().stacksTo(1)));

    private ArmorModule() {
    }

    public static void init(IEventBus modBus) {
        ArmorSets.registerItems(ITEMS);
        ITEMS.register(modBus);
        modBus.addListener(ArmorModule::buildTabs);
        registerRecipes();
        HunterSkills.setProvider(ArmorSkills::compute);
        HuntHooks.addStarterKit(ArmorModule::giveStarterSet);
    }

    private static void buildTabs(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(HCCreativeTabs.ARMOR.getKey())) return;
        for (ArmorSet set : ArmorSets.ALL) {
            for (int i = 0; i < 4; i++) event.accept(set.item(i).get());
        }
        for (int tier = 1; tier <= CharmItem.MAX_TIER; tier++) event.accept(CharmItem.unrolled(CHARM.get(), tier));
    }

    /** Recipe id of the forge recipe of a piece. */
    public static String forgeRecipeId(ArmorSet set, int piece) {
        return "armor/" + set.itemId(piece);
    }

    /** Recipe id of the trade-in upgrade recipe of a piece (only for series with {@link ArmorSet#upgradeFrom}). */
    public static String upgradeRecipeId(ArmorSet set, int piece) {
        return "armor/" + set.itemId(piece) + "/upgrade";
    }

    /**
     * Blacksmith recipes (category ARMOR, group = set id): every piece can be forged from materials, and pieces of
     * series with a predecessor can also be upgraded from the predecessor's piece of the same slot for about half the
     * materials. Level-ups (Lv2-8) come from {@link HCArmorItem#getUpgradeCost} (armor spheres + the series' materials).
     */
    private static void registerRecipes() {
        for (ArmorSet set : ArmorSets.ALL) {
            for (int i = 0; i < 4; i++) {
                ArmorSet.Piece p = set.piece(i);
                SmithingRegistry.register(new SmithingRegistry.Recipe(forgeRecipeId(set, i), SmithingRegistry.Category.ARMOR, set.id,
                        set.item(i), null, p.forgeCost(), p.forgeZenny(), set.requiredHR));
                if (set.upgradeFrom != null) {
                    SmithingRegistry.register(new SmithingRegistry.Recipe(upgradeRecipeId(set, i), SmithingRegistry.Category.ARMOR, set.id,
                            set.item(i), set.upgradeFrom.item(i), p.upgradeCost(), p.upgradeZenny(), set.requiredHR));
                }
            }
        }
    }

    /** The starter hunter set: equipped into empty armor slots, otherwise put in the inventory. */
    private static void giveStarterSet(ServerPlayer p) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            ItemStack st = new ItemStack(ArmorSets.HUNTER.item(i).get());
            if (p.getItemBySlot(slots[i]).isEmpty()) p.setItemSlot(slots[i], st);
            else if (!p.getInventory().add(st)) p.drop(st, false);
        }
    }
}
