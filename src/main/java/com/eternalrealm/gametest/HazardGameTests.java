package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.forge.DungeonHazards;
import com.eternalrealm.dungeon.hazard.HazardField;
import com.eternalrealm.dungeon.hazard.HazardKind;
import com.eternalrealm.dungeon.hazard.HazardSite;
import com.eternalrealm.entity.EREntityTypes;
import com.eternalrealm.entity.ERMonster;
import com.eternalrealm.entity.GolemEnemy;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.monster.SpecialMove;
import com.eternalrealm.monster.forge.MonsterFactory;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.world.forge.MapSync;
import com.eternalrealm.world.map.MapView;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * In-game tests of the field telegraphs: a dungeon trap shows its marker before it hits and only hurts who stands
 * inside it; an elite's special move winds up with its marker and then hits only inside it; the monster factory gives
 * special moves by the rule; the trap damage type is loaded; the map data of a player can be built.
 *
 * <p>Arena: 9x5x9 with a stone floor; entities stand at relative y=2 (see CombatGameTests). Iron golems with no AI
 * are the victims (fake players cannot be hurt).</p>
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HazardGameTests {
    private static final String ARENA = "arena";

    private HazardGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static IronGolem still(GameTestHelper helper, BlockPos pos) {
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, pos);
        golem.setNoAi(true);
        return golem;
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aTrapWarnsBeforeItHitsAndSparesWhoIsOutside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0)); // the floor of the arena: feet at origin y + 1
        HazardKind kind = HazardKind.SAND_GEYSER;
        HazardSite geyser = new HazardSite(0, kind, new BlockBox(0, 1, 0, 8, 3, 8), 4.5, 4.5, 0.0, kind.a(), 0.0, 0);
        HazardField field = new HazardField(List.of(geyser), origin.getX(), origin.getY(), origin.getZ(), 1L);
        DungeonHazards hazards = new DungeonHazards(level, field, 1.0);
        IronGolem inside = still(helper, new BlockPos(4, 2, 4));
        IronGolem outside = still(helper, new BlockPos(8, 2, 8));
        float full = inside.getMaxHealth();
        long[] warnedAt = {-1};
        helper.onEachTick(() -> {
            hazards.tick(level.getGameTime(), List.of(inside, outside));
            if (warnedAt[0] < 0 && field.pending(0) != null) {
                warnedAt[0] = level.getGameTime();
            }
        });
        helper.runAfterDelay(kind.warnTicks() / 2, () -> {
            check(warnedAt[0] >= 0, "the trap has not shown its marker");
            check(field.pending(0) != null, "the marker must still be up");
            check(inside.getHealth() == full, "hit before the marker was full");
        });
        helper.runAfterDelay(kind.warnTicks() + 12, () -> {
            check(inside.getHealth() < full, "the golem on the geyser was not hit");
            check(outside.getHealth() == outside.getMaxHealth(), "the golem outside the marker was hit");
            hazards.stop();
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void theTrapDamageTypeIsLoaded(GameTestHelper helper) {
        DamageSource source = DungeonHazards.trapDamage(helper.getLevel());
        check(source.is(DungeonHazards.TRAP_DAMAGE), "data/eternalrealm/damage_type/trap.json is not loaded: "
                + source.getMsgId());
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void anEliteSlamTelegraphsThenHitsOnlyInsideItsMarker(GameTestHelper helper) {
        GolemEnemy golem = helper.spawn(EREntityTypes.GOLEM_ENEMY.get(), new BlockPos(4, 2, 1));
        golem.setVariant("ice_golem");
        golem.setSpecialMoves(2);
        golem.setNoAi(true); // stays where it is; its actions still play
        IronGolem target = still(helper, new BlockPos(4, 2, 4));
        IronGolem bystander = still(helper, new BlockPos(4, 2, 8));
        int start = 45; // after the spawn animation
        int windup = SpecialMove.SLAM.windupTicks();
        helper.runAfterDelay(start, () -> {
            check(golem.specialMoves().contains(SpecialMove.SLAM), "golems slam: " + golem.specialMoves());
            check(golem.startSpecialMove(SpecialMove.SLAM, target), "the slam did not start (acting="
                    + golem.isActing() + ")");
            check(golem.currentSpecialTelegraph() != null, "no marker");
            check(golem.currentSpecialTelegraph().contains(target.getX(), target.getY(), target.getZ()),
                    "the target should stand in the marker");
            check(!golem.currentSpecialTelegraph().contains(bystander.getX(), bystander.getY(), bystander.getZ()),
                    "the bystander should stand outside the marker");
        });
        helper.runAfterDelay(start + windup - 4, () -> {
            check(golem.currentSpecialMove() == SpecialMove.SLAM, "the wind-up ended early");
            check(target.getHealth() == target.getMaxHealth(), "hit during the wind-up");
        });
        helper.runAfterDelay(start + windup + 6, () -> {
            check(target.getHealth() < target.getMaxHealth(), "the slam did not hit the target in its marker");
            check(bystander.getHealth() == bystander.getMaxHealth(), "the slam hit outside its marker");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void specialMovesFollowTheRule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        check(moves(level, "tomb_warden", 18) == 2, "elites use two moves");
        check(moves(level, "sand_husk", 15) == 0, "ordinary monsters use none");
        check(moves(level, "knight_of_the_end", 44) == 1, "monsters of level 30+ use one");
        check(moves(level, "b8_minion", 50) == 0, "boss summons use none");
        ERMonster plain = EREntityTypes.createEnemy(level, "bandit").orElseThrow();
        check(plain.specialMoves().isEmpty(), "a plain body has no moves");
        IronGolem target = still(helper, new BlockPos(4, 2, 4));
        check(!plain.trySpecialMove(target), "a plain body must not use special moves");
        plain.discard();
        helper.succeed();
    }

    private static int moves(ServerLevel level, String templateId, int monsterLevel) {
        Mob mob = MonsterFactory.create(level, new MonsterSpawn(Monsters.require(templateId), monsterLevel), true)
                .orElseThrow(() -> new GameTestAssertException("no body for " + templateId));
        int count = mob instanceof ERMonster monster ? monster.specialMoveCount() : -1;
        mob.discard();
        return count;
    }

    @GameTest(template = ARENA)
    public static void mapDataCanBeBuiltForAPlayer(GameTestHelper helper) {
        ServerContext context = ServerContext.current().orElseThrow(() -> new GameTestAssertException("no context"));
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "er_gt_map"));
        MapView view = MapSync.build(context, player);
        check(view.isUnlocked(1), "the first area is always open");
        check(view.party().isEmpty(), "no party");
        check(view.dungeon().isEmpty(), "not in a dungeon");
        MapView back = MapView.fromJson(view.toJson());
        check(back.realm().equals(view.realm()), "realm changed in transit: " + back.realm());
        check(back.unlockedAreas().equals(view.unlockedAreas()), "areas changed in transit");
        check(Optional.empty().equals(back.dungeon()), "dungeon appeared in transit");
        helper.succeed();
    }
}
