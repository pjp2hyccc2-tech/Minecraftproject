package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.DungeonDefinition;
import com.eternalrealm.dungeon.DungeonLayout;
import com.eternalrealm.dungeon.DungeonLayoutGenerator;
import com.eternalrealm.dungeon.DungeonTheme;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.dungeon.decor.DecorBlock;
import com.eternalrealm.dungeon.decor.DecorBlocks;
import com.eternalrealm.dungeon.decor.DungeonAmbience;
import com.eternalrealm.dungeon.decor.DungeonDecor;
import com.eternalrealm.dungeon.forge.decor.DecorBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The dungeon decoration against the real game: every block state the planner writes exists, and what the pure planner
 * believes about it (collision, light it gives, light it stops, fluid) is what the game says, so the pure tests of
 * walkable paths and light levels (DungeonDecorTest) hold in game; sign data parses; the ambience's particles and
 * sounds exist.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DecorGameTests {
    private static final String EMPTY = "empty";

    private DecorGameTests() {
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void decorationBlocksAreWhatThePlannerThinks(GameTestHelper helper) {
        Set<String> states = new TreeSet<>();
        Set<String> data = new TreeSet<>();
        List<DungeonLayout> layouts = new ArrayList<>();
        for (DungeonDefinition dungeon : Dungeons.all()) {
            layouts.add(DungeonLayoutGenerator.generate(dungeon, 1L));
            layouts.add(DungeonLayoutGenerator.generate(dungeon, 2L));
        }
        layouts.add(DungeonLayoutGenerator.raidArena(Dungeons.FINAL_RAID));
        for (DungeonLayout layout : layouts) {
            for (DecorBlock block : DungeonDecor.plan(layout).blocks()) {
                if (!block.isRole()) {
                    states.add(block.state());
                }
                if (block.nbt() != null) {
                    data.add(block.nbt());
                }
            }
        }
        List<String> problems = new ArrayList<>();
        for (String text : states) {
            BlockState state = DecorBuilder.state(text);
            if (state == null) {
                problems.add("unknown " + text);
                continue;
            }
            if (state.isAir() != text.equals(DecorBlocks.AIR)) {
                problems.add(text + " is air");
            }
            int light = state.getLightEmission();
            if (light != DecorBlocks.light(text)) {
                problems.add(text + " gives light " + light + ", the planner thinks " + DecorBlocks.light(text));
            }
            VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            boolean shapeOk = switch (DecorBlocks.shape(text)) {
                case NONE -> shape.isEmpty();
                case THIN -> !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 0.25;
                case PARTIAL -> !shape.isEmpty() && !Block.isShapeFullBlock(shape);
                case FULL -> Block.isShapeFullBlock(shape);
            };
            if (!shapeOk) {
                problems.add(text + " collides as " + (shape.isEmpty() ? "nothing" : shape.bounds())
                        + ", the planner thinks " + DecorBlocks.shape(text));
            }
            boolean opaque = state.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) >= 15;
            if (opaque != DecorBlocks.opaque(text)) {
                problems.add(text + (opaque ? " stops" : " lets through") + " light, the planner thinks otherwise");
            }
            if (state.getFluidState().isEmpty() == DecorBlocks.fluid(text)) {
                problems.add(text + ": fluid " + !state.getFluidState().isEmpty());
            }
        }
        for (String snbt : data) {
            try {
                TagParser.parseTag(snbt);
            } catch (Exception e) {
                problems.add("bad block entity data " + snbt + ": " + e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new GameTestAssertException(problems.size() + " problems: " + String.join("; ", problems));
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theAmbienceParticlesAndSoundsExist(GameTestHelper helper) {
        List<String> missing = new ArrayList<>();
        for (DungeonTheme theme : DungeonTheme.values()) {
            DungeonAmbience ambience = DungeonAmbience.of(theme);
            for (DungeonAmbience.Mote mote : ambience.motes()) {
                String id = mote.particle();
                int slash = id.indexOf('/');
                if (slash > 0) {
                    if (!BuiltInRegistries.BLOCK.containsKey(new ResourceLocation(id.substring(slash + 1)))
                            || !BuiltInRegistries.PARTICLE_TYPE.containsKey(new ResourceLocation(id.substring(0, slash)))) {
                        missing.add(theme.id() + " " + id);
                    }
                } else if (!(BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(id)) instanceof SimpleParticleType)) {
                    missing.add(theme.id() + " " + id + " (not a simple particle)");
                }
            }
            for (String sound : ambience.sounds()) {
                if (!BuiltInRegistries.SOUND_EVENT.containsKey(new ResourceLocation(sound))) {
                    missing.add(theme.id() + " " + sound);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new GameTestAssertException("missing: " + missing);
        }
        helper.succeed();
    }
}
