package jp.satsujinki.test;

import jp.satsujinki.Satsujinki;
import jp.satsujinki.block.entity.GuillotineBlockEntity;
import jp.satsujinki.block.entity.MixerBlockEntity;
import jp.satsujinki.block.entity.PressBlockEntity;
import jp.satsujinki.block.entity.SawBlockEntity;
import jp.satsujinki.entity.ContestantEntity;
import jp.satsujinki.entity.CorpseEntity;
import jp.satsujinki.entity.GibEntity;
import jp.satsujinki.game.ContestantType;
import jp.satsujinki.game.Structures;
import jp.satsujinki.registry.ModBlocks;
import jp.satsujinki.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side checks run by CI (runGameTestServer): every trap must actually execute a contestant,
 * spawn remains, and nothing may throw on a dedicated server.
 */
@GameTestHolder(Satsujinki.MODID)
@PrefixGameTestTemplate(false)
public final class SatsujinkiGameTests {
    private SatsujinkiGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void contestantsLiveAndTalk(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ContestantEntity[] all = new ContestantEntity[ContestantType.values().length];
        int i = 0;
        for (ContestantType t : ContestantType.values()) {
            all[i] = ContestantEntity.spawnAt(level, Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2 + i, 1, 3))), t);
            i++;
        }
        h.runAfterDelay(120, () -> {
            for (ContestantEntity c : all) {
                if (c == null || !c.isAlive()) h.fail("a contestant died by itself");
                else if (c.getCustomName() == null) h.fail("contestant without a name");
            }
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void mixerGrindsContestant(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos core = h.absolutePos(new BlockPos(6, 1, 6));
        Structures.build(level, Structures.mixer(core), null);
        if (!(level.getBlockEntity(core) instanceof MixerBlockEntity be)) {
            h.fail("mixer block entity missing");
            return;
        }
        ContestantEntity c = ContestantEntity.spawnAt(level, Vec3.atBottomCenterOf(core.above(2)), ContestantType.SALARYMAN);
        be.setRunning(true, null);
        h.succeedWhen(() -> {
            if (c != null && c.isAlive()) h.fail("contestant survived the mixer");
            if (be.getKills() < 1) h.fail("mixer kill not counted");
            if (be.blood <= 0.0F) h.fail("no blood soup");
            if (level.getEntitiesOfClass(GibEntity.class, new AABB(core).inflate(10.0D)).isEmpty()) h.fail("no gore chunks");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void pressCrushesContestant(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(6, 1, 6));
        Structures.build(level, Structures.press(floor), null);
        if (!(level.getBlockEntity(floor.above(5)) instanceof PressBlockEntity be)) {
            h.fail("press block entity missing");
            return;
        }
        ContestantEntity c = ContestantEntity.spawnAt(level, Vec3.atBottomCenterOf(floor), ContestantType.MACHO);
        h.runAfterDelay(5, () -> be.trigger(null));
        h.succeedWhen(() -> {
            if (c != null && c.isAlive()) h.fail("contestant survived the press");
            if (be.getKills() < 1) h.fail("press kill not counted");
            if (level.getEntitiesOfClass(CorpseEntity.class, new AABB(floor).inflate(4.0D)).isEmpty()) h.fail("no flattened corpse");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void sawCutsContestant(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos sawPos = h.absolutePos(new BlockPos(6, 1, 6));
        level.setBlock(sawPos, ModBlocks.SAW.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(sawPos) instanceof SawBlockEntity be)) {
            h.fail("saw block entity missing");
            return;
        }
        be.setRunning(true, null);
        ContestantEntity c = ContestantEntity.spawnAt(level, Vec3.atBottomCenterOf(sawPos.above()), ContestantType.OTAKU);
        h.succeedWhen(() -> {
            if (c != null && c.isAlive()) h.fail("contestant survived the saw");
            if (be.getKills() < 1) h.fail("saw kill not counted");
            if (level.getEntitiesOfClass(CorpseEntity.class, new AABB(sawPos).inflate(6.0D)).size() < 2) h.fail("not cut in two");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void guillotineBeheadsContestant(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos gPos = h.absolutePos(new BlockPos(6, 1, 6));
        level.setBlock(gPos, ModBlocks.GUILLOTINE.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(gPos) instanceof GuillotineBlockEntity be)) {
            h.fail("guillotine block entity missing");
            return;
        }
        ContestantEntity c = ContestantEntity.spawnAt(level, Vec3.atBottomCenterOf(gPos), ContestantType.PRESIDENT);
        h.runAfterDelay(30, () -> {
            if (c == null || !c.isLocked()) h.fail("contestant was not locked into the lunette");
            be.trigger(null);
        });
        h.succeedWhen(() -> {
            if (c != null && c.isAlive()) h.fail("contestant survived the guillotine");
            if (be.getKills() < 1) h.fail("guillotine kill not counted");
            boolean head = false;
            for (GibEntity g : level.getEntitiesOfClass(GibEntity.class, new AABB(gPos).inflate(8.0D))) {
                if (g.getKind() == GibEntity.KIND_HEAD) head = true;
            }
            if (!head) h.fail("no flying head");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void spikesHurt(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos p = h.absolutePos(new BlockPos(4, 1, 4));
        level.setBlock(p, ModBlocks.SPIKE_BED.get().defaultBlockState(), 3);
        ContestantEntity c = ContestantEntity.spawnAt(level, Vec3.atBottomCenterOf(p).add(0.0D, 1.5D, 0.0D), ContestantType.GYARU);
        h.succeedWhen(() -> {
            if (c == null) h.fail("spawn failed");
            else if (c.isAlive() && c.getHealth() >= c.getMaxHealth()) h.fail("spikes did no damage");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public static void kitsBuild(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos base = h.absolutePos(new BlockPos(6, 1, 6));
        Structures.build(level, Structures.mixer(base), null);
        if (!level.getBlockState(base.offset(2, 1, 0)).is(ModBlocks.MIXER_GLASS.get())) h.fail("mixer glass missing");
        if (!level.getBlockState(base).is(ModBlocks.MIXER_CORE.get())) h.fail("mixer core missing");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void entitiesConstruct(GameTestHelper h) {
        if (ModEntities.CORPSE.get().create(h.getLevel()) == null) h.fail("corpse");
        if (ModEntities.GIB.get().create(h.getLevel()) == null) h.fail("gib");
        h.succeed();
    }
}
