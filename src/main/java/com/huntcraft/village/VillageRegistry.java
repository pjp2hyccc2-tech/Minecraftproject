package com.huntcraft.village;

import com.huntcraft.HuntCraft;
import com.huntcraft.village.block.ItemBoxBlock;
import com.huntcraft.village.block.PaperLanternBlock;
import com.huntcraft.village.block.QuestBoardBlock;
import com.huntcraft.village.companion.MofuBomb;
import com.huntcraft.village.companion.MofuEntity;
import com.huntcraft.village.menu.ItemBoxMenu;
import com.huntcraft.village.npc.TrainingDummy;
import com.huntcraft.village.npc.VillageNpc;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Blocks, items, entities, menus and sounds of the village module. */
public final class VillageRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HuntCraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HuntCraft.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HuntCraft.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, HuntCraft.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HuntCraft.MODID);

    private VillageRegistry() {
    }

    // ------------------------------------------------------------------ blocks
    public static final RegistryObject<Block> ITEM_BOX = BLOCKS.register("item_box",
            () -> new ItemBoxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> QUEST_BOARD = BLOCKS.register("village_quest_board",
            () -> new QuestBoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD)));
    public static final RegistryObject<Block> PAPER_LANTERN = BLOCKS.register("paper_lantern",
            () -> new PaperLanternBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.5F).sound(SoundType.WOOL)
                    .lightLevel(s -> 15).noOcclusion()));
    public static final RegistryObject<Block> PAPER_LANTERN_WHITE = BLOCKS.register("paper_lantern_white",
            () -> new PaperLanternBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.5F).sound(SoundType.WOOL)
                    .lightLevel(s -> 15).noOcclusion()));

    public static final RegistryObject<Item> ITEM_BOX_ITEM = ITEMS.register("item_box", () -> new BlockItem(ITEM_BOX.get(), new Item.Properties()));
    public static final RegistryObject<Item> QUEST_BOARD_ITEM = ITEMS.register("village_quest_board", () -> new BlockItem(QUEST_BOARD.get(), new Item.Properties()));
    public static final RegistryObject<Item> PAPER_LANTERN_ITEM = ITEMS.register("paper_lantern", () -> new BlockItem(PAPER_LANTERN.get(), new Item.Properties()));
    public static final RegistryObject<Item> PAPER_LANTERN_WHITE_ITEM = ITEMS.register("paper_lantern_white", () -> new BlockItem(PAPER_LANTERN_WHITE.get(), new Item.Properties()));

    // ------------------------------------------------------------------ entities
    public static final RegistryObject<EntityType<VillageNpc>> HUMAN_NPC = ENTITIES.register("village_npc",
            () -> EntityType.Builder.<VillageNpc>of(VillageNpc::new, MobCategory.MISC).sized(0.6F, 1.8F)
                    .clientTrackingRange(10).build("village_npc"));
    public static final RegistryObject<EntityType<VillageNpc>> MOFU_NPC = ENTITIES.register("mofu_npc",
            () -> EntityType.Builder.<VillageNpc>of(VillageNpc::new, MobCategory.MISC).sized(0.6F, 1.0F)
                    .clientTrackingRange(10).build("mofu_npc"));
    public static final RegistryObject<EntityType<MofuEntity>> MOFU = ENTITIES.register("mofu",
            () -> EntityType.Builder.<MofuEntity>of(MofuEntity::new, MobCategory.MISC).sized(0.6F, 0.95F)
                    .clientTrackingRange(10).build("mofu"));
    public static final RegistryObject<EntityType<MofuBomb>> MOFU_BOMB = ENTITIES.register("mofu_bomb",
            () -> EntityType.Builder.<MofuBomb>of(MofuBomb::new, MobCategory.MISC).sized(0.35F, 0.35F)
                    .clientTrackingRange(6).updateInterval(5).build("mofu_bomb"));
    public static final RegistryObject<EntityType<TrainingDummy>> TRAINING_DUMMY = ENTITIES.register("training_dummy",
            () -> EntityType.Builder.<TrainingDummy>of(TrainingDummy::new, MobCategory.MISC).sized(0.9F, 2.1F)
                    .clientTrackingRange(10).build("training_dummy"));

    // ------------------------------------------------------------------ menus
    public static final RegistryObject<MenuType<ItemBoxMenu>> ITEM_BOX_MENU = MENUS.register("item_box",
            () -> IForgeMenuType.create((windowId, inv, data) -> new ItemBoxMenu(windowId, inv)));

    // ------------------------------------------------------------------ sounds (see src/main/sounds/village.json)
    public static final RegistryObject<SoundEvent> MOFU_MEOW = sound("village.mofu_meow");
    public static final RegistryObject<SoundEvent> MOFU_HORN = sound("village.mofu_horn");
    public static final RegistryObject<SoundEvent> MOFU_BOMB_SOUND = sound("village.mofu_bomb");
    public static final RegistryObject<SoundEvent> MOFU_ATTACK = sound("village.mofu_attack");
    public static final RegistryObject<SoundEvent> MOFU_FAINT = sound("village.mofu_faint");
    public static final RegistryObject<SoundEvent> MEAL = sound("village.meal");
    public static final RegistryObject<SoundEvent> NPC_GREET = sound("village.npc_greet");
    public static final RegistryObject<SoundEvent> ANVIL_TAP = sound("village.anvil_tap");
    public static final RegistryObject<SoundEvent> BOX_OPEN = sound("village.box_open");
    public static final RegistryObject<SoundEvent> UI_PAGE = sound("village.ui_page");
    public static final RegistryObject<SoundEvent> DUMMY_HIT = sound("village.dummy_hit");

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HuntCraft.id(name)));
    }
}
