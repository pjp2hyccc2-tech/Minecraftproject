package com.sounan.registry;

import com.sounan.Sounan;
import com.sounan.block.AndonBlock;
import com.sounan.block.CandleStandBlock;
import com.sounan.block.FacingBlock;
import com.sounan.block.HeadlessDollBlock;
import com.sounan.block.InteractBlock;
import com.sounan.block.KakejikuBlock;
import com.sounan.block.KuraDoorBlock;
import com.sounan.block.LetterBlock;
import com.sounan.block.SounanDoorBlock;
import com.sounan.block.SaltPileBlock;
import com.sounan.block.SuzuDaiBlock;
import com.sounan.block.TansuBlock;
import com.sounan.block.TatamiBlock;
import com.sounan.block.ThinFacingBlock;
import com.sounan.block.ToriiGateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Sounan.MODID);

    // ---------------------------------------------------------------- building blocks
    public static final RegistryObject<Block> TATAMI = BLOCKS.register("tatami",
            () -> new TatamiBlock(props(MapColor.COLOR_YELLOW, SoundType.WOOL, 1.0f)));
    public static final RegistryObject<Block> OLD_PLANKS = BLOCKS.register("old_planks", () -> new Block(wood(MapColor.COLOR_BROWN)));
    public static final RegistryObject<Block> ENGAWA = BLOCKS.register("engawa", () -> new Block(wood(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> ROTTEN_PLANKS = BLOCKS.register("rotten_planks", () -> new Block(wood(MapColor.TERRACOTTA_BROWN)));
    public static final RegistryObject<Block> DARK_BEAM = BLOCKS.register("dark_beam",
            () -> new RotatedPillarBlock(wood(MapColor.COLOR_BLACK)));
    public static final RegistryObject<Block> PLASTER_WALL = BLOCKS.register("plaster_wall",
            () -> new Block(props(MapColor.TERRACOTTA_WHITE, SoundType.STONE, 1.5f)));
    public static final RegistryObject<Block> PLASTER_WALL_STAINED = BLOCKS.register("plaster_wall_stained",
            () -> new Block(props(MapColor.TERRACOTTA_WHITE, SoundType.STONE, 1.5f)));
    public static final RegistryObject<Block> WAINSCOT = BLOCKS.register("wainscot", () -> new Block(wood(MapColor.COLOR_BROWN)));
    public static final RegistryObject<Block> KURA_WALL = BLOCKS.register("kura_wall",
            () -> new Block(props(MapColor.SNOW, SoundType.STONE, 2.0f)));
    public static final RegistryObject<Block> SHOJI = BLOCKS.register("shoji", () -> new Block(paper()));
    public static final RegistryObject<Block> SHOJI_TORN = BLOCKS.register("shoji_torn", () -> new Block(paper().noOcclusion()));
    public static final RegistryObject<Block> SHOJI_BLOODY = BLOCKS.register("shoji_bloody", () -> new Block(paper()));
    public static final RegistryObject<Block> FUSUMA = BLOCKS.register("fusuma", () -> new Block(paper()));
    public static final RegistryObject<Block> RANMA = BLOCKS.register("ranma", () -> new Block(wood(MapColor.COLOR_BROWN).noOcclusion()));
    public static final RegistryObject<Block> VERMILION = BLOCKS.register("vermilion",
            () -> new RotatedPillarBlock(wood(MapColor.COLOR_RED)));
    public static final RegistryObject<Block> BLACK_LACQUER = BLOCKS.register("black_lacquer", () -> new Block(wood(MapColor.COLOR_BLACK)));

    // ---------------------------------------------------------------- doors
    public static final RegistryObject<Block> FUSUMA_DOOR = BLOCKS.register("fusuma_door",
            () -> new SounanDoorBlock(door(), BlockSetType.OAK, ModSounds.GAME_FUSUMA, 10.0f));
    public static final RegistryObject<Block> SHOJI_DOOR = BLOCKS.register("shoji_door",
            () -> new SounanDoorBlock(door(), BlockSetType.OAK, ModSounds.GAME_FUSUMA, 10.0f));
    /** The safe room's door: the bride cannot open it. */
    public static final RegistryObject<Block> SEALED_DOOR = BLOCKS.register("sealed_door",
            () -> new SounanDoorBlock(door(), BlockSetType.IRON, ModSounds.GAME_FUSUMA, 4.0f));
    public static final RegistryObject<Block> OSHIIRE_DOOR = BLOCKS.register("oshiire_door",
            () -> new SounanDoorBlock(door(), BlockSetType.OAK, ModSounds.GAME_CLOSET, 5.0f));
    public static final RegistryObject<Block> KURA_DOOR = BLOCKS.register("kura_door",
            () -> new KuraDoorBlock(door().strength(-1.0f, 3600000.0f), ModSounds.GAME_UNLOCK));

    // ---------------------------------------------------------------- shrine
    public static final RegistryObject<Block> TORII_GATE = BLOCKS.register("torii_gate",
            () -> new ToriiGateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).noCollission().noOcclusion()
                    .strength(-1.0f, 3600000.0f).noLootTable().pushReaction(PushReaction.BLOCK)
                    .lightLevel(s -> s.getValue(ToriiGateBlock.ACTIVE) ? 13 : 2)));
    public static final RegistryObject<Block> SHIMENAWA = BLOCKS.register("shimenawa",
            () -> new ThinFacingBlock(decor(SoundType.GRASS), 0, 6, 7, 16, 16, 9));

    // ---------------------------------------------------------------- decals & soft furnishings
    public static final RegistryObject<Block> BLOOD_STAIN = BLOCKS.register("blood_stain",
            () -> new ThinFacingBlock(decor(SoundType.SLIME_BLOCK), 0, 0, 0, 16, 0.5, 16));
    public static final RegistryObject<Block> HAIR_STRANDS = BLOCKS.register("hair_strands",
            () -> new ThinFacingBlock(decor(SoundType.WOOL), 0, 0, 0, 16, 0.5, 16));
    public static final RegistryObject<Block> FOOTPRINTS = BLOCKS.register("footprints",
            () -> new ThinFacingBlock(decor(SoundType.SLIME_BLOCK), 0, 0, 0, 16, 0.5, 16));
    public static final RegistryObject<Block> FUTON = BLOCKS.register("futon",
            () -> new FacingBlock(props(MapColor.WOOL, SoundType.WOOL, 0.5f).noOcclusion(), 0, 0, 0, 16, 3, 16));
    public static final RegistryObject<Block> KAKEJIKU = BLOCKS.register("kakejiku",
            () -> new KakejikuBlock(decor(SoundType.WOOL)));
    public static final RegistryObject<Block> LETTER = BLOCKS.register("letter",
            () -> new LetterBlock(decor(SoundType.WOOL)));

    // ---------------------------------------------------------------- furniture
    public static final RegistryObject<Block> BUTSUDAN = BLOCKS.register("butsudan",
            () -> new FacingBlock(wood(MapColor.COLOR_BLACK).noOcclusion(), 1, 0, 4, 15, 16, 16));
    public static final RegistryObject<Block> CANDLE_STAND = BLOCKS.register("candle_stand",
            () -> new CandleStandBlock(props(MapColor.GOLD, SoundType.METAL, 0.8f).noOcclusion()
                    .lightLevel(s -> s.getValue(CandleStandBlock.LIT) ? 9 : 0)));
    public static final RegistryObject<Block> ANDON = BLOCKS.register("andon",
            () -> new AndonBlock(props(MapColor.SAND, SoundType.BAMBOO_WOOD, 0.8f).noOcclusion()
                    .lightLevel(s -> s.getValue(AndonBlock.LIT) ? 11 : 0)));
    public static final RegistryObject<Block> TANSU = BLOCKS.register("tansu", () -> new TansuBlock(wood(MapColor.COLOR_BROWN).noOcclusion()));
    public static final RegistryObject<Block> KAMADO = BLOCKS.register("kamado",
            () -> new FacingBlock(props(MapColor.TERRACOTTA_ORANGE, SoundType.STONE, 2.0f)));
    public static final RegistryObject<Block> WELL = BLOCKS.register("well",
            () -> new InteractBlock(props(MapColor.STONE, SoundType.STONE, 2.0f), "well", 0, 0, 0, 16, 16, 16));
    public static final RegistryObject<Block> SUZU_DAI = BLOCKS.register("suzu_dai",
            () -> new SuzuDaiBlock(wood(MapColor.COLOR_RED).noOcclusion().lightLevel(s -> s.getValue(SuzuDaiBlock.HAS_BELL) ? 4 : 0)));
    public static final RegistryObject<Block> SALT_PILE = BLOCKS.register("salt_pile",
            () -> new SaltPileBlock(props(MapColor.SNOW, SoundType.SAND, 0.2f).noOcclusion().noCollission().noLootTable()));
    public static final RegistryObject<Block> HINA_DAN = BLOCKS.register("hina_dan",
            () -> new Block(props(MapColor.COLOR_RED, SoundType.WOOL, 1.0f)));
    public static final RegistryObject<Block> HEADLESS_DOLL = BLOCKS.register("headless_doll",
            () -> new HeadlessDollBlock(props(MapColor.COLOR_RED, SoundType.WOOL, 0.5f).noOcclusion()));
    public static final RegistryObject<Block> SAKAZUKI_TABLE = BLOCKS.register("sakazuki_table",
            () -> new InteractBlock(wood(MapColor.COLOR_RED).noOcclusion(), "sakazuki", 2, 0, 2, 14, 6, 14));
    public static final RegistryObject<Block> OLD_CLOCK = BLOCKS.register("old_clock",
            () -> new FacingBlock(wood(MapColor.COLOR_BROWN).noOcclusion(), 3, 0, 11, 13, 16, 16));
    public static final RegistryObject<Block> STONE_LANTERN = BLOCKS.register("stone_lantern",
            () -> new FacingBlock(props(MapColor.STONE, SoundType.STONE, 2.0f).noOcclusion(), 3, 0, 3, 13, 16, 13));

    private ModBlocks() {}

    private static BlockBehaviour.Properties props(MapColor color, SoundType sound, float strength) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(strength, 6.0f).sound(sound);
    }

    private static BlockBehaviour.Properties wood(MapColor color) {
        return props(color, SoundType.WOOD, 2.0f);
    }

    private static BlockBehaviour.Properties paper() {
        return props(MapColor.TERRACOTTA_WHITE, SoundType.BAMBOO_WOOD, 1.0f);
    }

    private static BlockBehaviour.Properties door() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 6.0f).sound(SoundType.BAMBOO_WOOD)
                .noOcclusion().pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties decor(SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.2f).sound(sound).noCollission().noOcclusion()
                .pushReaction(PushReaction.DESTROY);
    }
}
