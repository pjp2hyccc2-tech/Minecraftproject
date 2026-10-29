package com.huntcraft.registry;

import com.huntcraft.HuntCraft;
import com.huntcraft.entity.misc.TrapEntity;
import com.huntcraft.item.BbqSpitItem;
import com.huntcraft.item.FarcasterItem;
import com.huntcraft.item.GuildPassItem;
import com.huntcraft.item.HCConsumableItem;
import com.huntcraft.item.HunterNotesItem;
import com.huntcraft.item.MaterialItem;
import com.huntcraft.item.ThrowableToolItem;
import com.huntcraft.item.TrapItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Core items shared by every module: materials (monster + gathering) and consumables.
 * Weapons / armor / spawn eggs are registered by their own modules.
 */
public final class HCItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HuntCraft.MODID);

    /** Every material registered here (for creative tab / data generation). */
    public static final List<RegistryObject<Item>> MATERIALS = new ArrayList<>();
    /** Every consumable / tool registered here. */
    public static final List<RegistryObject<Item>> CONSUMABLES = new ArrayList<>();

    private HCItems() {
    }

    public static RegistryObject<Item> mat(String id, int rarity) {
        RegistryObject<Item> ro = ITEMS.register(id, () -> new MaterialItem(new Item.Properties(), rarity));
        MATERIALS.add(ro);
        return ro;
    }

    public static RegistryObject<Item> tool(String id, Supplier<Item> sup) {
        RegistryObject<Item> ro = ITEMS.register(id, sup);
        CONSUMABLES.add(ro);
        return ro;
    }

    /** Material set of a large monster: low rank + high rank (plus) versions. */
    public static final class MonsterMats {
        public final String prefix;
        public final RegistryObject<Item> scale, shell, special, tail, claw, gem;
        public final RegistryObject<Item> scalePlus, shellPlus, specialPlus, clawPlus, gemPlus;

        private MonsterMats(String prefix, String scaleN, String shellN, String specialN, String clawN, String gemN, int baseRarity) {
            this.prefix = prefix;
            int r = baseRarity;
            this.scale = mat(prefix + "_" + scaleN, r);
            this.shell = mat(prefix + "_" + shellN, r);
            this.special = mat(prefix + "_" + specialN, r + 1);
            this.tail = mat(prefix + "_tail", r + 1);
            this.claw = mat(prefix + "_" + clawN, r);
            this.gem = mat(prefix + "_" + gemN, r + 2);
            this.scalePlus = mat(prefix + "_" + scaleN + "_plus", r + 3);
            this.shellPlus = mat(prefix + "_" + shellN + "_plus", r + 3);
            this.specialPlus = mat(prefix + "_" + specialN + "_plus", r + 4);
            this.clawPlus = mat(prefix + "_" + clawN + "_plus", r + 3);
            this.gemPlus = mat(prefix + "_" + gemN + "_plus", Math.min(8, r + 5));
        }
    }

    // ---------------------------------------------------------------- large monster materials
    public static final MonsterMats IGNIVALD = new MonsterMats("ignivald", "scale", "shell", "webbing", "claw", "ruby", 3);
    public static final MonsterMats FROSTGARM = new MonsterMats("frostgarm", "fur", "hide", "fang", "claw", "frostgem", 3);
    public static final MonsterMats VOLTRIX = new MonsterMats("voltrix", "scale", "shell", "electrode", "fang", "thunderorb", 3);
    public static final MonsterMats GRAVADON = new MonsterMats("gravadon", "plate", "shell", "skull", "tusk", "earthgem", 3);
    public static final MonsterMats AZRAGOS = new MonsterMats("azragos", "scale", "shell", "horn", "claw", "orb", 4);

    // ---------------------------------------------------------------- small monster materials
    public static final RegistryObject<Item> JAGRAPTOR_HIDE = mat("jagraptor_hide", 1);
    public static final RegistryObject<Item> JAGRAPTOR_FANG = mat("jagraptor_fang", 2);
    public static final RegistryObject<Item> BLIZRAPTOR_HIDE = mat("blizraptor_hide", 2);
    public static final RegistryObject<Item> BLIZRAPTOR_CREST = mat("blizraptor_crest", 3);
    public static final RegistryObject<Item> BOARFANG_HIDE = mat("boarfang_hide", 1);
    public static final RegistryObject<Item> BOARFANG_TUSK = mat("boarfang_tusk", 2);
    public static final RegistryObject<Item> GRASICA_ANTLER = mat("grasica_antler", 1);
    public static final RegistryObject<Item> GAMENOS_SHELL = mat("gamenos_shell", 2);
    public static final RegistryObject<Item> BUZZLING_STINGER = mat("buzzling_stinger", 2);
    public static final RegistryObject<Item> BUZZLING_WING = mat("buzzling_wing", 1);
    public static final RegistryObject<Item> YUKIMAMMO_FUR = mat("yukimammo_fur", 2);
    public static final RegistryObject<Item> YUKIMAMMO_TUSK = mat("yukimammo_tusk", 3);
    public static final RegistryObject<Item> HEATGECKO_SCALE = mat("heatgecko_scale", 2);
    public static final RegistryObject<Item> GANSEKI_SHELL = mat("ganseki_shell", 2);
    public static final RegistryObject<Item> GANSEKI_CLAW = mat("ganseki_claw", 2);
    public static final RegistryObject<Item> MOFU_COIN = mat("mofu_coin", 3);

    // ---------------------------------------------------------------- generic monster parts
    public static final RegistryObject<Item> MONSTER_FLUID = mat("monster_fluid", 2);
    public static final RegistryObject<Item> FLAME_SAC = mat("flame_sac", 3);
    public static final RegistryObject<Item> FROST_SAC = mat("frost_sac", 3);
    public static final RegistryObject<Item> THUNDER_SAC = mat("thunder_sac", 3);
    public static final RegistryObject<Item> AQUA_SAC = mat("aqua_sac", 3);
    public static final RegistryObject<Item> POISON_SAC = mat("poison_sac", 3);
    public static final RegistryObject<Item> MONSTER_BONE_S = mat("monster_bone_s", 1);
    public static final RegistryObject<Item> MONSTER_BONE_M = mat("monster_bone_m", 2);
    public static final RegistryObject<Item> MONSTER_BONE_L = mat("monster_bone_l", 4);
    public static final RegistryObject<Item> RAW_MEAT = mat("raw_meat", 1);

    // ---------------------------------------------------------------- gathering materials
    public static final RegistryObject<Item> HERB = mat("herb", 1);
    public static final RegistryObject<Item> BLUE_MUSHROOM = mat("blue_mushroom", 1);
    public static final RegistryObject<Item> HONEY = mat("honey", 1);
    public static final RegistryObject<Item> ANTIDOTE_HERB = mat("antidote_herb", 1);
    public static final RegistryObject<Item> MIGHT_SEED = mat("might_seed", 2);
    public static final RegistryObject<Item> ADAMANT_SEED = mat("adamant_seed", 2);
    public static final RegistryObject<Item> STICKY_GRASS = mat("sticky_grass", 1);
    public static final RegistryObject<Item> SPIDER_WEB = mat("spider_web", 1);
    public static final RegistryObject<Item> FLASHBUG = mat("flashbug", 1);
    public static final RegistryObject<Item> THUNDERBUG = mat("thunderbug", 1);
    public static final RegistryObject<Item> BITTERBUG = mat("bitterbug", 1);
    public static final RegistryObject<Item> FIRE_HERB = mat("fire_herb", 1);
    public static final RegistryObject<Item> SLEEP_HERB = mat("sleep_herb", 1);
    public static final RegistryObject<Item> NUMB_MUSHROOM = mat("numb_mushroom", 1);
    public static final RegistryObject<Item> BLAST_NUT = mat("blast_nut", 2);
    public static final RegistryObject<Item> ICE_CRYSTAL = mat("ice_crystal", 1);
    public static final RegistryObject<Item> HOT_PEPPER = mat("hot_pepper", 1);
    public static final RegistryObject<Item> EARTH_CRYSTAL = mat("earth_crystal", 1);
    public static final RegistryObject<Item> AZURITE_ORE = mat("azurite_ore", 2);
    public static final RegistryObject<Item> CRIMSONITE_ORE = mat("crimsonite_ore", 4);
    public static final RegistryObject<Item> NOVACRYSTAL = mat("novacrystal", 5);
    public static final RegistryObject<Item> LIGHTCRYSTAL = mat("lightcrystal", 5);
    public static final RegistryObject<Item> ARMOR_SPHERE = mat("armor_sphere", 2);
    public static final RegistryObject<Item> ARMOR_SPHERE_PLUS = mat("armor_sphere_plus", 4);
    public static final RegistryObject<Item> HARD_ARMOR_SPHERE = mat("hard_armor_sphere", 6);

    // ---------------------------------------------------------------- consumables & tools
    public static final RegistryObject<Item> POTION = tool("potion", () -> HCConsumableItem.potion(8.0F));
    public static final RegistryObject<Item> MEGA_POTION = tool("mega_potion", () -> HCConsumableItem.potion(16.0F));
    public static final RegistryObject<Item> MAX_POTION = tool("max_potion", HCConsumableItem::maxPotion);
    public static final RegistryObject<Item> FIRST_AID_MED = tool("first_aid_med", () -> HCConsumableItem.potion(6.0F));
    public static final RegistryObject<Item> ANTIDOTE = tool("antidote", HCConsumableItem::antidote);
    public static final RegistryObject<Item> ENERGY_DRINK = tool("energy_drink", HCConsumableItem::energyDrink);
    public static final RegistryObject<Item> HOT_DRINK = tool("hot_drink", () -> HCConsumableItem.drink(HCConsumableItem.DrinkKind.HOT));
    public static final RegistryObject<Item> COOL_DRINK = tool("cool_drink", () -> HCConsumableItem.drink(HCConsumableItem.DrinkKind.COOL));
    public static final RegistryObject<Item> DEMONDRUG = tool("demondrug", () -> HCConsumableItem.drink(HCConsumableItem.DrinkKind.DEMON));
    public static final RegistryObject<Item> ARMORSKIN = tool("armorskin", () -> HCConsumableItem.drink(HCConsumableItem.DrinkKind.ARMOR));
    public static final RegistryObject<Item> RATION = tool("ration", () -> HCConsumableItem.food(25, 0F));
    public static final RegistryObject<Item> WELL_DONE_STEAK = tool("well_done_steak", () -> HCConsumableItem.food(50, 4F));
    public static final RegistryObject<Item> RARE_STEAK = tool("rare_steak", () -> HCConsumableItem.food(25, 0F));
    public static final RegistryObject<Item> BURNT_MEAT = tool("burnt_meat", () -> HCConsumableItem.food(10, 0F));
    public static final RegistryObject<Item> BBQ_SPIT = tool("bbq_spit", () -> new BbqSpitItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PAINTBALL = tool("paintball", () -> new ThrowableToolItem(new Item.Properties().stacksTo(16), ThrowableToolItem.Kind.PAINT));
    public static final RegistryObject<Item> FLASH_BOMB = tool("flash_bomb", () -> new ThrowableToolItem(new Item.Properties().stacksTo(8), ThrowableToolItem.Kind.FLASH));
    public static final RegistryObject<Item> DUNG_BOMB = tool("dung_bomb", () -> new ThrowableToolItem(new Item.Properties().stacksTo(8), ThrowableToolItem.Kind.DUNG));
    public static final RegistryObject<Item> TRANQ_BOMB = tool("tranq_bomb", () -> new ThrowableToolItem(new Item.Properties().stacksTo(8), ThrowableToolItem.Kind.TRANQ));
    public static final RegistryObject<Item> PITFALL_TRAP = tool("pitfall_trap", () -> new TrapItem(new Item.Properties().stacksTo(2), TrapEntity.TrapKind.PITFALL));
    public static final RegistryObject<Item> SHOCK_TRAP = tool("shock_trap", () -> new TrapItem(new Item.Properties().stacksTo(2), TrapEntity.TrapKind.SHOCK));
    public static final RegistryObject<Item> FARCASTER = tool("farcaster", () -> new FarcasterItem(new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> GUILD_PASS = tool("guild_pass", () -> new GuildPassItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HUNTER_NOTES = tool("hunter_notes", () -> new HunterNotesItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BARREL_BOMB = tool("barrel_bomb", () -> new BlockItem(HCBlocks.BARREL_BOMB.get(), new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> BARREL_BOMB_PLUS = tool("barrel_bomb_plus", () -> new BlockItem(HCBlocks.BARREL_BOMB_PLUS.get(), new Item.Properties().stacksTo(2)));
}
