package com.eternalrealm.guild.house.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The guild houses' blocks and entities: the gates' portal surface and the guild board (art:
 * tools/art/guild_portal.py), the roof tiles the house designs are covered with and the training dummy (art:
 * tools/art/guild_house_blocks.py).
 */
public final class GuildHouseBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ModInfo.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModInfo.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ModInfo.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ModInfo.MOD_ID);
    /** Every roof block (full, stairs, slab of each family), for the creative tab and the tests. */
    public static final List<RegistryObject<Block>> ROOFS = new ArrayList<>();

    public static final RegistryObject<Block> PORTAL = BLOCKS.register("guild_portal", () -> new GuildPortalBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3600000.0F).noCollission()
                    .noOcclusion().noLootTable().noParticlesOnBreak().lightLevel(state -> 11)
                    .sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));
    public static final RegistryObject<Block> BOARD = BLOCKS.register("guild_board", () -> new GuildBoardBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(-1.0F, 3600000.0F).noOcclusion()
                    .noLootTable().sound(SoundType.WOOD).pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Item> BOARD_ITEM = ITEMS.register("guild_board",
            () -> new BlockItem(BOARD.get(), new Item.Properties()));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<GuildBoardBlockEntity>> BOARD_ENTITY =
            BLOCK_ENTITIES.register("guild_board",
                    () -> BlockEntityType.Builder.of(GuildBoardBlockEntity::new, BOARD.get()).build(null));

    /** The training dummy: hit it to see your damage; it never falls and gives no ranking credit. */
    public static final RegistryObject<EntityType<TrainingDummy>> DUMMY = ENTITIES.register("training_dummy",
            () -> EntityType.Builder.<TrainingDummy>of(TrainingDummy::new, MobCategory.MISC).sized(0.7F, 1.95F)
                    .clientTrackingRange(10).fireImmune().build("training_dummy"));

    static {
        // roof tiles: weathered wooden shingles, blue-grey slate, Japanese kawara, straw thatch, copper verdigris, gold
        roof("shingle_roof_tiles", "shingle_roof_tile", MapColor.COLOR_BROWN, SoundType.WOOD);
        roof("slate_roof_tiles", "slate_roof_tile", MapColor.COLOR_GRAY, SoundType.DEEPSLATE_TILES);
        roof("kawara_roof_tiles", "kawara_roof_tile", MapColor.COLOR_BLACK, SoundType.DEEPSLATE_TILES);
        roof("verdigris_roof_tiles", "verdigris_roof_tile", MapColor.WARPED_STEM, SoundType.COPPER);
        roof("gilded_roof_tiles", "gilded_roof_tile", MapColor.GOLD, SoundType.METAL);
        roof("thatch_roof", "thatch_roof", MapColor.COLOR_YELLOW, SoundType.GRASS);
    }

    private GuildHouseBlocks() {
    }

    private static void roof(String full, String stem, MapColor color, SoundType sound) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(color).strength(1.5F, 6.0F)
                .sound(sound);
        RegistryObject<Block> base = BLOCKS.register(full, () -> new Block(properties));
        RegistryObject<Block> stairs = BLOCKS.register(stem + "_stairs",
                () -> new StairBlock(() -> base.get().defaultBlockState(), properties));
        RegistryObject<Block> slab = BLOCKS.register(stem + "_slab", () -> new SlabBlock(properties));
        for (RegistryObject<Block> block : List.of(base, stairs, slab)) {
            ROOFS.add(block);
            ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
        }
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(GuildHouseBlocks::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(DUMMY.get(), LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).build());
    }
}
