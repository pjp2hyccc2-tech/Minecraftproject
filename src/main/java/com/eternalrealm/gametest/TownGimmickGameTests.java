package com.eternalrealm.gametest;

import com.eternalrealm.block.forge.BuntingBlock;
import com.eternalrealm.block.forge.BuntingBlockEntity;
import com.eternalrealm.block.forge.SecretDoorBlock;
import com.eternalrealm.block.forge.SecretDoorBlockEntity;
import com.eternalrealm.block.forge.ShopSignBlock;
import com.eternalrealm.block.forge.TownGimmickBlocks;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.town.BuntingLine;
import com.eternalrealm.world.town.BuntingStyle;
import com.eternalrealm.world.town.SecretDoorMotion;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The canal town gimmicks in a real server: a secret door opens from either half, lets people through, waits while
 * someone stands in it and slides shut again; its halves never part; bunting data written by the capital build loads,
 * clamps and saves unchanged; and every block state the capital build may write for these blocks (and the floating
 * lantern) exists.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TownGimmickGameTests {
    private static final String EMPTY = "empty";
    /**
     * Their own batch: tests that place players far from their structure (the market's 60 blocks) depend on the
     * default batches' grid layout, which extra tests there would shift.
     */
    private static final String BATCH = "town_gimmicks";
    private static final BlockPos LOWER = new BlockPos(1, 1, 1);
    private static final BlockPos UPPER = LOWER.above();
    /** Ticks from a click until the door is shut again with nobody about (plus a little slack). */
    private static final int ROUND_TRIP = SecretDoorMotion.MOVE_TICKS + SecretDoorMotion.STAY_OPEN_TICKS
            + SecretDoorMotion.MOVE_TICKS + 2 * SecretDoorMotion.WATCH_TICKS + 4;

    private TownGimmickGameTests() {
    }

    // ------------------------------------------------------------------------------------------ secret door

    @GameTest(template = EMPTY, timeoutTicks = 400, batch = BATCH)
    public static void secretDoorOpensLetsPeopleThroughAndCloses(GameTestHelper helper) {
        buildDoor(helper, SecretDoorBlock.Look.BOOKSHELF);
        check(isWall(helper, LOWER) && isWall(helper, UPPER), "a closed door is a solid wall");
        use(helper, LOWER);
        check(isOpen(helper, LOWER) && isOpen(helper, UPPER), "both halves open at once");
        check(passable(helper, LOWER) && passable(helper, UPPER), "an open door has no collision");
        check(anim(helper, LOWER) == SecretDoorMotion.OPENING && anim(helper, UPPER) == SecretDoorMotion.OPENING,
                "both halves slide");
        helper.runAfterDelay(SecretDoorMotion.MOVE_TICKS + 2L * SecretDoorMotion.WATCH_TICKS + 1L, () -> {
            check(isOpen(helper, LOWER) && isOpen(helper, UPPER), "still open after the slide");
            check(anim(helper, LOWER) == SecretDoorMotion.IDLE, "the slide is over");
        });
        helper.runAfterDelay(SecretDoorMotion.MOVE_TICKS + SecretDoorMotion.STAY_OPEN_TICKS - 10L, () ->
                check(isOpen(helper, LOWER), "it stays open for a while"));
        helper.runAfterDelay(ROUND_TRIP, () -> {
            check(!isOpen(helper, LOWER) && !isOpen(helper, UPPER), "it closed again by itself");
            check(isWall(helper, LOWER) && isWall(helper, UPPER), "and is a solid wall again");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 600, batch = BATCH)
    public static void secretDoorWaitsWhileSomeoneStandsInIt(GameTestHelper helper) {
        buildDoor(helper, SecretDoorBlock.Look.STONE_BRICKS);
        use(helper, UPPER);
        check(isOpen(helper, LOWER) && isOpen(helper, UPPER), "the upper half opens the door too");
        ArmorStand visitor = helper.spawn(EntityType.ARMOR_STAND, new Vec3(1.5, 1.0, 1.5));
        visitor.setNoGravity(true);
        helper.runAfterDelay(ROUND_TRIP + 40L, () -> {
            check(isOpen(helper, LOWER) && isOpen(helper, UPPER), "it does not close on someone in the doorway");
            visitor.discard();
        });
        helper.runAfterDelay(ROUND_TRIP + 40L + SecretDoorMotion.RETRY_TICKS + SecretDoorMotion.MOVE_TICKS
                + 2L * SecretDoorMotion.WATCH_TICKS + 4L, () -> {
            check(!isOpen(helper, LOWER) && !isOpen(helper, UPPER), "it closes once the doorway is free");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 300, batch = BATCH)
    public static void secretDoorPlacedOpenClosesByItself(GameTestHelper helper) {
        buildDoor(helper, SecretDoorBlock.Look.MOSSY_STONE_BRICKS);
        BlockState lower = helper.getLevel().getBlockState(helper.absolutePos(LOWER));
        BlockState upper = helper.getLevel().getBlockState(helper.absolutePos(UPPER));
        helper.setBlock(LOWER, Blocks.AIR);                       // takes the upper half with it
        helper.setBlock(LOWER, lower.setValue(SecretDoorBlock.OPEN, true));
        helper.setBlock(UPPER, upper.setValue(SecretDoorBlock.OPEN, true));
        check(isOpen(helper, LOWER) && isOpen(helper, UPPER), "placed standing open");
        helper.runAfterDelay(SecretDoorMotion.STAY_OPEN_TICKS + SecretDoorMotion.MOVE_TICKS
                + 2L * SecretDoorMotion.WATCH_TICKS + 4L, () -> {
            check(!isOpen(helper, LOWER) && !isOpen(helper, UPPER), "a door left open closes by itself");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void secretDoorHalvesNeverPart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildDoor(helper, SecretDoorBlock.Look.CASTLE_BRICKS);
        level.destroyBlock(helper.absolutePos(UPPER), false);
        check(level.getBlockState(helper.absolutePos(LOWER)).isAir(), "removing the upper half removes the lower");
        buildDoor(helper, SecretDoorBlock.Look.WHITE_PLASTER);
        level.destroyBlock(helper.absolutePos(LOWER), false);
        check(level.getBlockState(helper.absolutePos(UPPER)).isAir(), "removing the lower half removes the upper");
        buildDoor(helper, SecretDoorBlock.Look.SPRUCE_PLANKS);
        BlockState upper = level.getBlockState(helper.absolutePos(UPPER));
        check(upper.getValue(SecretDoorBlock.LOOK) == SecretDoorBlock.Look.SPRUCE_PLANKS
                && upper.getValue(SecretDoorBlock.HALF) == DoubleBlockHalf.UPPER, "the halves agree");
        check(level.getBlockState(helper.absolutePos(LOWER)).isSolidRender(level, helper.absolutePos(LOWER)),
                "a closed door hides the passage like a wall");
        helper.succeed();
    }

    private static void buildDoor(GameTestHelper helper, SecretDoorBlock.Look look) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        for (int y = 1; y <= 2; y++) {
            helper.setBlock(new BlockPos(0, y, 1), look.block());
            helper.setBlock(new BlockPos(2, y, 1), look.block());
        }
        BlockState lower = TownGimmickBlocks.SECRET_DOOR.get().defaultBlockState()
                .setValue(SecretDoorBlock.FACING, Direction.SOUTH).setValue(SecretDoorBlock.LOOK, look)
                .setValue(SecretDoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(SecretDoorBlock.OPEN, false);
        helper.setBlock(LOWER, lower);
        helper.setBlock(UPPER, lower.setValue(SecretDoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void use(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(relative);
        BlockState state = level.getBlockState(at);
        InteractionResult result = state.use(level, FakePlayerFactory.getMinecraft(level), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.SOUTH, at, false));
        check(result.consumesAction(), "using the door does something: " + result);
    }

    private static boolean isOpen(GameTestHelper helper, BlockPos relative) {
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(relative));
        return state.getBlock() instanceof SecretDoorBlock && state.getValue(SecretDoorBlock.OPEN);
    }

    private static boolean passable(GameTestHelper helper, BlockPos relative) {
        BlockPos at = helper.absolutePos(relative);
        return helper.getLevel().getBlockState(at).getCollisionShape(helper.getLevel(), at).isEmpty();
    }

    private static boolean isWall(GameTestHelper helper, BlockPos relative) {
        BlockPos at = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(at);
        return state.getBlock() instanceof SecretDoorBlock
                && Block.isShapeFullBlock(state.getCollisionShape(helper.getLevel(), at));
    }

    private static int anim(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(relative)) instanceof SecretDoorBlockEntity door
                ? door.anim() : -1;
    }

    // ------------------------------------------------------------------------------------------ bunting

    @GameTest(template = EMPTY, batch = BATCH)
    public static void buntingDataRoundTrips(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, TownGimmickBlocks.BUNTING.get().defaultBlockState()
                .setValue(BuntingBlock.FACING, Direction.EAST));
        BuntingBlockEntity bunting = bunting(helper, relative);
        load(bunting, "{Tx:12,Ty:-2,Tz:5,Sag:1.5f,Style:\"lanterns\",Colors:[I;1,14,4],Wind:0.5f}");
        check(bunting.targetX() == 12 && bunting.targetY() == -2 && bunting.targetZ() == 5, "target");
        check(Math.abs(bunting.sag() - 1.5F) < 1e-6F && Math.abs(bunting.wind() - 0.5F) < 1e-6F, "sag and wind");
        check(bunting.style() == BuntingStyle.LANTERNS, "style");
        check(bunting.color(0) == 1 && bunting.color(1) == 14 && bunting.color(2) == 4 && bunting.color(3) == 1,
                "colours go round in turn");
        BuntingLine line = bunting.line();
        check(line != null && bunting.layout() != null && bunting.layout().count() > 5, "a string with lanterns");
        AABB box = bunting.getRenderBoundingBox();
        BlockPos anchor = helper.absolutePos(relative);
        check(box.contains(Vec3.atCenterOf(anchor)) && box.contains(Vec3.atCenterOf(anchor.offset(12, -2, 5))),
                "the render bounds hold both ends");
        check(box.minY < anchor.getY() - 2 - 1.5, "and the sag below them");

        // what it saves loads back the same
        CompoundTag saved = bunting.saveWithoutMetadata();
        helper.setBlock(relative.above(), TownGimmickBlocks.BUNTING.get().defaultBlockState());
        BuntingBlockEntity copy = bunting(helper, relative.above());
        copy.load(saved);
        check(copy.targetX() == 12 && copy.targetY() == -2 && copy.targetZ() == 5 && copy.style() == BuntingStyle.LANTERNS
                && copy.color(1) == 14 && Math.abs(copy.sag() - 1.5F) < 1e-6F, "saved data loads back unchanged");

        // nonsense is clamped or dropped, never fatal
        load(copy, "{Tx:99,Ty:-50,Tz:0,Sag:40.0f,Style:\"fireworks\",Colors:[I;16,-1,3],Wind:9.0f}");
        check(copy.targetX() == BuntingLine.MAX_REACH && copy.targetY() == -BuntingLine.MAX_REACH, "reach is clamped");
        check(copy.sag() <= BuntingLine.MAX_SAG && copy.wind() <= 3.0F, "sag and wind are clamped");
        check(copy.style() == BuntingStyle.PENNANT && copy.color(0) == 3 && copy.color(1) == 3,
                "unknown styles fall back to pennants, bad colours are dropped");
        load(copy, "{Tx:0,Ty:0,Tz:0}");
        check(copy.line() == null, "no target is a bare hook");
        helper.succeed();
    }

    private static BuntingBlockEntity bunting(GameTestHelper helper, BlockPos relative) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(relative)) instanceof BuntingBlockEntity bunting) {
            return bunting;
        }
        throw new GameTestAssertException("no bunting block entity at " + relative);
    }

    private static void load(BuntingBlockEntity bunting, String snbt) {
        try {
            CompoundTag tag = bunting.saveWithoutMetadata();
            tag.merge(TagParser.parseTag(snbt));
            bunting.load(tag);
        } catch (Exception e) {
            throw new GameTestAssertException("bad test data " + snbt + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------------------------ capital states

    @GameTest(template = EMPTY, batch = BATCH)
    public static void everyCapitalStateOfTheCanalGimmicksExists(GameTestHelper helper) {
        HolderLookup<Block> blocks = helper.getLevel().holderLookup(Registries.BLOCK);
        List<String> problems = new ArrayList<>();
        String[] facings = {"north", "south", "east", "west"};
        for (String facing : facings) {
            parse(blocks, "eternalrealm:bunting[facing=" + facing + "]", problems);
            for (String half : new String[]{"lower", "upper"}) {
                for (String open : new String[]{"false", "true"}) {
                    for (SecretDoorBlock.Look look : SecretDoorBlock.Look.values()) {
                        parse(blocks, "eternalrealm:secret_door[facing=" + facing + ",half=" + half + ",look="
                                + look.getSerializedName() + ",open=" + open + "]", problems);
                    }
                }
            }
            for (String attach : new String[]{"wall", "ceiling"}) {
                for (ShopSignBlock.Icon icon : ShopSignBlock.Icon.values()) {
                    parse(blocks, "eternalrealm:shop_sign[attach=" + attach + ",facing=" + facing + ",icon="
                            + icon.getSerializedName() + "]", problems);
                }
            }
        }
        parse(blocks, "eternalrealm:floating_lantern", problems);
        // the capital build's schema lists exactly these looks and icons (tools/capital/blocks.py)
        check(SecretDoorBlock.Look.values().length == 7, "seven looks");
        check(ShopSignBlock.Icon.values().length == 16, "sixteen icons");
        BlockState sign = TownGimmickBlocks.SHOP_SIGN.get().defaultBlockState();
        BlockState anchor = TownGimmickBlocks.BUNTING.get().defaultBlockState();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockState lantern = TownGimmickBlocks.FLOATING_LANTERN.get().defaultBlockState();
        if (!sign.getCollisionShape(helper.getLevel(), origin).isEmpty()
                || !anchor.getCollisionShape(helper.getLevel(), origin).isEmpty()
                || !lantern.getCollisionShape(helper.getLevel(), origin).isEmpty()) {
            problems.add("signs, bunting hooks and floating lanterns must not block streets and canals");
        }
        if (lantern.getLightEmission() != 9) {
            problems.add("a floating lantern gives light 9, not " + lantern.getLightEmission());
        }
        if (!problems.isEmpty()) {
            throw new GameTestAssertException(problems.size() + " problems: " + String.join("; ", problems));
        }
        helper.succeed();
    }

    private static void parse(HolderLookup<Block> blocks, String text, List<String> problems) {
        try {
            BlockStateParser.parseForBlock(blocks, text, false);
        } catch (Exception e) {
            problems.add(text + ": " + e.getMessage());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
