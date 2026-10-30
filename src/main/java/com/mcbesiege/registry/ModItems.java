package com.mcbesiege.registry;

import com.mcbesiege.MCBesiege;
import com.mcbesiege.item.BlueprintItem;
import com.mcbesiege.item.ConnectorItem;
import com.mcbesiege.item.WrenchItem;
import com.mcbesiege.part.PartType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MCBesiege.MODID);

    public static final Map<PartType, RegistryObject<Item>> PARTS = new EnumMap<>(PartType.class);
    public static final List<RegistryObject<Item>> FORTRESS = new ArrayList<>();

    static {
        for (PartType t : PartType.values()) {
            RegistryObject<Block> b = ModBlocks.PARTS.get(t);
            PARTS.put(t, ITEMS.register(t.id(), () -> new BlockItem(b.get(), new Item.Properties())));
        }
        fortress(ModBlocks.CASTLE_STONE, "castle_stone");
        fortress(ModBlocks.CASTLE_BRICKS, "castle_bricks");
        fortress(ModBlocks.CASTLE_BRICK_STAIRS, "castle_brick_stairs");
        fortress(ModBlocks.CASTLE_BRICK_SLAB, "castle_brick_slab");
        fortress(ModBlocks.TIMBER_FRAME, "timber_frame");
        fortress(ModBlocks.TIMBER_FRAME_PLAIN, "timber_frame_plain");
        fortress(ModBlocks.THATCH, "thatch");
        fortress(ModBlocks.THATCH_STAIRS, "thatch_stairs");
        fortress(ModBlocks.THATCH_SLAB, "thatch_slab");
        fortress(ModBlocks.CASTLE_GATE, "castle_gate");
        fortress(ModBlocks.BANNER_BLUE, "banner_blue");
        fortress(ModBlocks.BANNER_RED, "banner_red");
        fortress(ModBlocks.SIEGE_TARGET, "siege_target");
    }

    public static final RegistryObject<Item> MISSION_TABLE = ITEMS.register("mission_table",
            () -> new BlockItem(ModBlocks.MISSION_TABLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> WRENCH = ITEMS.register("wrench", () -> new WrenchItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BRACE_TOOL = ITEMS.register("brace_tool",
            () -> new ConnectorItem(ConnectorItem.Kind.BRACE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SPRING_TOOL = ITEMS.register("spring_tool",
            () -> new ConnectorItem(ConnectorItem.Kind.SPRING, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ROPE_TOOL = ITEMS.register("rope_tool",
            () -> new ConnectorItem(ConnectorItem.Kind.ROPE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BLUEPRINT = ITEMS.register("blueprint", () -> new BlueprintItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CANNONBALL = ITEMS.register("cannonball", () -> new Item(new Item.Properties().stacksTo(64)));

    private static void fortress(RegistryObject<Block> b, String id) {
        FORTRESS.add(ITEMS.register(id, () -> new BlockItem(b.get(), new Item.Properties())));
    }

    public static Item part(PartType t) { return PARTS.get(t).get(); }
}
