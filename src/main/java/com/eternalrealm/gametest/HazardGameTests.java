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
import com.eternalrealm.monster.forge.VanillaSpecialMoves;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
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

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void anEliteSlamTelegraphsThenHitsOnlyInsideItsMarker(GameTestHelper helper) {
        GolemEnemy golem = helper.spawn(EREntityTypes.GOLEM_ENEMY.get(), new BlockPos(4, 2, 1));
        golem.setVariant("ice_golem");
        golem.setSpecialMoves(2);
        golem.setNoAi(true); // stays where it is; its actions still play
        IronGolem target = still(helper, new BlockPos(4, 2, 4));
        IronGolem bystander = still(helper, new BlockPos(4, 2, 8));
        int windup = SpecialMove.SLAM.windupTicks();
        // start as soon as the spawn animation (and anything else it is playing) is over, not at a fixed tick
        long[] startedAt = {-1L};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (startedAt[0] < 0) {
                if (tick < 40 || golem.isActing()) {
                    check(tick < 200, "the golem never became free to act (action=" + golem.currentAction() + ")");
                    return;
                }
                check(golem.specialMoves().contains(SpecialMove.SLAM), "golems slam: " + golem.specialMoves());
                check(golem.startSpecialMove(SpecialMove.SLAM, target), "the slam did not start (acting="
                        + golem.isActing() + ", action=" + golem.currentAction() + ")");
                check(golem.currentSpecialTelegraph() != null, "no marker");
                check(golem.currentSpecialTelegraph().contains(target.getX(), target.getY(), target.getZ()),
                        "the target should stand in the marker");
                check(!golem.currentSpecialTelegraph().contains(bystander.getX(), bystander.getY(), bystander.getZ()),
                        "the bystander should stand outside the marker");
                startedAt[0] = tick;
                return;
            }
            long t = tick - startedAt[0];
            // who hurt whom: the batch's other tests run next door (a giant's slam can reach into this arena), so
            // only the golem's own blows count
            if (t == windup - 4) {
                check(golem.currentSpecialMove() == SpecialMove.SLAM, "the wind-up ended early");
                check(target.getLastHurtByMob() != golem, "hit during the wind-up");
            } else if (t == windup + 6) {
                check(target.getLastHurtByMob() == golem, "the slam did not hit the target in its marker");
                check(bystander.getLastHurtByMob() != golem, "the slam hit outside its marker");
                helper.succeed();
            }
        });
    }

    /**
     * The lords' line-up (the owner, 2026-11-11): two lords of every field out at a time, and a change of the line-up
     * keeps two out and brings the lord that was resting.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void twoLordsOfAFieldAreOutAndEveryChangeBringsTheRestingOne(GameTestHelper helper) {
        ServerLevel astoria = helper.getLevel().getServer().getLevel(
                com.eternalrealm.world.forge.AstoriaDimension.LEVEL);
        check(astoria != null, "the field dimension is not loaded");
        helper.runAfterDelay(5, () -> {
            java.util.Map<Integer, java.util.List<String>> before = new java.util.HashMap<>();
            for (int field = 1; field <= com.eternalrealm.world.field.FieldLayout.FIELD_COUNT; field++) {
                java.util.List<String> out = com.eternalrealm.world.forge.FieldThreatService.outForTest(field);
                check(out.size() == com.eternalrealm.world.field.LordRoster.PER_FIELD,
                        "field " + field + " has " + out + " out");
                before.put(field, out);
            }
            com.eternalrealm.world.forge.FieldThreatService.rotateForTest(astoria);
            for (int field = 1; field <= com.eternalrealm.world.field.FieldLayout.FIELD_COUNT; field++) {
                java.util.List<String> out = com.eternalrealm.world.forge.FieldThreatService.outForTest(field);
                check(out.size() == com.eternalrealm.world.field.LordRoster.PER_FIELD,
                        "after the change field " + field + " has " + out + " out");
                for (com.eternalrealm.world.field.FieldThreats.Lord lord
                        : com.eternalrealm.world.field.FieldThreats.lordsOf(field)) {
                    if (!before.get(field).contains(lord.id())) {
                        check(out.contains(lord.id()), "the resting lord " + lord.id() + " did not come out: " + out);
                    }
                }
            }
            helper.succeed();
        });
    }

    /**
     * A field lord fights like a small boss: the three moves of its body and three arts of a lord; a blow that takes
     * it to 70% calls two monsters of its field, one to 50% makes it roar and rage (faster), and every turn comes once.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aLordCallsForHelpAndFliesIntoARage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        Mob lord = com.eternalrealm.world.forge.FieldThreatService.spawnLordForTest(level, "garm", at).orElse(null);
        check(lord != null, "no body for the lord garm");
        int moves = com.eternalrealm.monster.SpecialMoves.LORD_MOVES;
        check(lord instanceof ERMonster monster && monster.specialMoveCount() == moves
                        && monster.specialMoves().size() == moves
                        && monster.specialMoves().containsAll(com.eternalrealm.monster.SpecialMoves.lordArts(
                                monster.rig().id(), monster.getVariant())),
                "a lord uses all three moves of its body and three arts of a lord: "
                        + (lord instanceof ERMonster body ? body.specialMoves() : "not a monster"));
        net.minecraft.world.phys.AABB around = lord.getBoundingBox().inflate(10.0);
        // only the lord's own helpers count: the batch's other tests have monsters next door
        java.util.function.IntSupplier helpers = () -> level.getEntitiesOfClass(Mob.class, around,
                mob -> "garm".equals(mob.getPersistentData().getString(
                        com.eternalrealm.world.forge.FieldThreatService.HELPER_OF))).size();
        check(helpers.getAsInt() == 0, "helpers before any call");
        helper.runAfterDelay(5, () -> {
            lord.setHealth(lord.getMaxHealth() * 0.69F);
            lord.invulnerableTime = 0;
            lord.hurt(lord.damageSources().generic(), 1.0F);
        });
        helper.runAfterDelay(12, () -> {
            int called = helpers.getAsInt();
            check(called == com.eternalrealm.world.field.LordTurns.HELPERS, "the call brought " + called + " monsters");
            check(!com.eternalrealm.world.forge.FieldThreatService.enragedForTest("garm"), "raged too early");
            lord.setHealth(lord.getMaxHealth() * 0.6F);
            lord.invulnerableTime = 0;
            lord.hurt(lord.damageSources().generic(), 1.0F);
        });
        helper.runAfterDelay(20, () -> {
            int called = helpers.getAsInt();
            check(called == com.eternalrealm.world.field.LordTurns.HELPERS, "a turn came twice: " + called);
            lord.setHealth(lord.getMaxHealth() * 0.49F);
            lord.invulnerableTime = 0;
            lord.hurt(lord.damageSources().generic(), 1.0F);
        });
        helper.runAfterDelay(28, () -> {
            check(com.eternalrealm.world.forge.FieldThreatService.enragedForTest("garm"), "no rage at half health");
            check(lord.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED), "the rage makes it faster");
            for (Mob mob : level.getEntitiesOfClass(Mob.class, around, mob -> mob == lord || "garm".equals(
                    mob.getPersistentData().getString(com.eternalrealm.world.forge.FieldThreatService.HELPER_OF)))) {
                mob.discard();
            }
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
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        Mob paladin = MonsterFactory.spawn(level, new MonsterSpawn(Monsters.require("celestial_paladin"), 40),
                at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F).orElseThrow();
        check(VanillaSpecialMoves.hasGoal(paladin), "a vanilla-bodied elite gets the special move goal");
        paladin.discard();
        Mob slime = MonsterFactory.spawn(level, new MonsterSpawn(Monsters.require("meadow_slime"), 3),
                at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F).orElseThrow();
        check(!VanillaSpecialMoves.hasGoal(slime), "an ordinary vanilla-bodied monster gets none");
        slime.discard();
        ERMonster plain = EREntityTypes.createEnemy(level, "bandit").orElseThrow();
        check(plain.specialMoves().isEmpty(), "a plain body has no moves");
        IronGolem target = still(helper, new BlockPos(4, 2, 4));
        check(!plain.trySpecialMove(target), "a plain body must not use special moves");
        plain.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aVanillaBodiedEliteTelegraphsItsMoveToo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        IronGolem target = still(helper, new BlockPos(4, 2, 6));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        target.setHealth(1000.0F);
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 2));
        Mob paladin = MonsterFactory.spawn(level, new MonsterSpawn(Monsters.require("celestial_paladin"), 40),
                at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F).orElseThrow();
        paladin.setTarget(target);
        long[] startedAt = {-1};
        float[] healthAtStart = {0.0F};
        int[] windup = {0};
        helper.onEachTick(() -> {
            long now = level.getGameTime();
            if (startedAt[0] < 0) {
                com.eternalrealm.combat.Telegraph marker = VanillaSpecialMoves.windingUp(paladin);
                if (marker != null) {
                    startedAt[0] = now;
                    healthAtStart[0] = target.getHealth();
                    windup[0] = marker.durationTicks();
                }
                return;
            }
            long since = now - startedAt[0];
            if (since == windup[0] - 2) {
                check(target.getHealth() == healthAtStart[0], "hit during the wind-up");
            } else if (since == windup[0] + 4) {
                check(target.getHealth() < healthAtStart[0], "the special move did not hit the target in front");
                paladin.discard();
                helper.succeed();
            }
        });
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
