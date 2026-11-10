package com.eternalrealm.gametest;

import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.Bosses;
import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.boss.FieldBosses;
import com.eternalrealm.boss.forge.BossFight;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.entity.BossAnimation;
import com.eternalrealm.entity.BossKind;
import com.eternalrealm.entity.ERBoss;
import com.eternalrealm.entity.EREntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The 25 field bosses (boss.FieldBosses: the ten calamities and the fifteen wanderers). Each in a real fight against
 * fake players (they cannot be hurt, everything else runs for real): it spawns its own animated body, goes through every
 * form with that form's look, collapses when its health is gone and leaves nothing behind. And all their bodies on their
 * own: every animation, every form's look, and a clean death.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FieldBossGameTests {
    private static final String ARENA = "arena";
    private static final String BOSS_ARENA = "boss_arena";
    private static final int TIMEOUT = 1800;

    private FieldBossGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static void fight(GameTestHelper helper, String bossId) {
        ServerLevel level = helper.getLevel();
        BossDefinition definition = Bosses.byId(bossId)
                .orElseThrow(() -> new GameTestAssertException("no boss " + bossId));
        check(FieldBosses.all().contains(definition), bossId + " is not a field boss");
        BlockPos spawn = helper.absolutePos(new BlockPos(4, 2, 4));
        int number = FieldBosses.all().indexOf(definition);
        List<ServerPlayer> fighters = List.of(fake(helper, "erfb" + number + "a", 1.5, 1.5),
                fake(helper, "erfb" + number + "b", 7.5, 6.5), fake(helper, "erfb" + number + "c", 4.5, 8.0));
        Set<UUID> ids = new HashSet<>();
        fighters.forEach(player -> ids.add(player.getUUID()));
        AtomicBoolean killed = new AtomicBoolean();
        AtomicBoolean defeated = new AtomicBoolean();
        BossFight fight = BossFight.start(level, definition, Difficulty.NORMAL, spawn, Vec3.atBottomCenterOf(spawn),
                6.0, ids, done -> killed.set(true), done -> defeated.set(true))
                .orElseThrow(() -> new GameTestAssertException("no body for " + bossId));
        fight.overrideTargets(() -> fighters);
        check(fight.body() instanceof ERBoss, bossId + " has no animated body: " + fight.body());
        ERBoss body = (ERBoss) fight.body();
        check(bossId.equals(body.bossId()), "body of " + body.bossId() + " for " + bossId);
        check(EREntityTypes.boss(bossId).get() == body.getType(), bossId + " has the wrong entity type");
        check(body.getBbHeight() >= 1.5F * 1.8F - 0.01F, bossId + " is smaller than 1.5x a player");
        check(body.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, bossId + " can be knocked back");
        check(!body.removeWhenFarAway(1000.0) && body.requiresCustomPersistence(), bossId + " could despawn");
        int forms = definition.phases().size();
        AtomicInteger ticks = new AtomicInteger();
        Set<Integer> seen = new HashSet<>();
        Map<Integer, String> looks = new HashMap<>();
        AtomicBoolean enraged = new AtomicBoolean();
        helper.onEachTick(() -> {
            int t = ticks.incrementAndGet();
            fight.tick(level.getGameTime());
            if (!fight.isDefeated()) {
                seen.add(fight.brain().phase());
                looks.put(body.getPhase(), body.lookKey());
                enraged.set(enraged.get() || fight.brain().isEnraged());
            }
            if (t % 45 == 0 && !fight.isInvulnerable() && !fight.isDefeated()) {
                fight.absorb(null, fight.brain().maxHealth() * 0.075);
            }
        });
        helper.succeedWhen(() -> {
            check(defeated.get() && killed.get(), "waiting for the defeat of " + bossId + " (form "
                    + (fight.brain().phase() + 1) + "/" + forms + ", tick " + ticks.get() + ")");
            Set<Integer> every = new HashSet<>();
            for (int form = 0; form < forms; form++) {
                every.add(form);
                String look = looks.get(form);
                String expected = form == 0 ? bossId : bossId + "_p" + form;
                check(expected.equals(look), bossId + " form " + (form + 1) + " looked like " + look);
            }
            check(seen.equals(every), bossId + " played forms " + seen + " of " + forms);
            check(enraged.get() == definition.phases().get(forms - 1).enrages(),
                    bossId + " enraged " + enraged.get());
            check(fight.body().isRemoved(), bossId + "'s body is still there");
            check(fight.propCount() == 0, bossId + " left props behind");
            List<Entity> leftovers = level.getEntities((Entity) null, new AABB(spawn).inflate(48.0),
                    entity -> !entity.isRemoved() && (entity.getTags().contains(com.eternalrealm.boss.forge.BossProps.TAG)
                            || entity.getPersistentData().hasUUID("er_fight")));
            check(leftovers.isEmpty(), bossId + " left in the arena: " + leftovers);
        });
    }

    private static ServerPlayer fake(GameTestHelper helper, String name, double x, double z) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        Vec3 position = helper.absoluteVec(new Vec3(x, 2.0, z));
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        return player;
    }

    /**
     * Every field boss body on its own (no fight): its hitbox and immunities, every animation of its rig with a length,
     * each form's state stored, and a death that plays its animation before the body goes.
     */
    @GameTest(template = BOSS_ARENA, timeoutTicks = 600)
    public static void everyFieldBossBodyAnimatesAndDiesCleanly(GameTestHelper helper) {
        List<ERBoss> bodies = new ArrayList<>();
        int index = 0;
        for (BossDefinition definition : FieldBosses.all()) {
            BossKind kind = BossKind.byId(definition.id())
                    .orElseThrow(() -> new GameTestAssertException("no body kind for " + definition.id()));
            // a grid of bodies 8 blocks apart on the big arena's floor (they never touch)
            ERBoss boss = helper.spawn(EREntityTypes.bossType(kind).get(),
                    new BlockPos(4 + 8 * (index % 5), 2, 4 + 8 * (index / 5)));
            index++;
            check(kind.id().equals(boss.bossId()), "boss id " + boss.bossId());
            check(Math.abs(boss.getBbWidth() - kind.width()) < 1.0E-4F
                    && Math.abs(boss.getBbHeight() - kind.height()) < 1.0E-4F, kind.id() + " hitbox "
                    + boss.getBbWidth() + "x" + boss.getBbHeight());
            check(boss.isInvulnerableTo(boss.damageSources().inWall()) && boss.isInvulnerableTo(boss.damageSources().drown())
                    && boss.isInvulnerableTo(boss.damageSources().fall()), kind.id() + " can suffocate, drown or fall");
            boss.setAiFrozen(true);
            bodies.add(boss);
        }
        BossAnimation[] animations = BossAnimation.values();
        int step = 10;
        for (int i = 0; i < animations.length; i++) {
            BossAnimation animation = animations[i];
            int number = i;
            helper.runAfterDelay(5L + (long) i * step, () -> {
                for (ERBoss boss : bodies) {
                    check(boss.isAlive(), boss.bossId() + " died during " + animation.id());
                    boss.playAnimation(animation);
                    check(boss.playingAnimation().isPresent() == animation.isAction(),
                            boss.bossId() + " " + animation.id() + ": playing=" + boss.playingAnimation());
                    check(boss.durationTicks(animation) > 0, boss.bossId() + " " + animation.id() + " has no length");
                    int forms = Bosses.byId(boss.bossId()).map(definition -> definition.phases().size()).orElse(1);
                    boss.setPhase(number % forms);
                    check(boss.getPhase() == number % forms, boss.bossId() + " phase not stored");
                    String expected = number % forms == 0 ? boss.bossId() : boss.bossId() + "_p" + number % forms;
                    check(expected.equals(boss.lookKey()), boss.bossId() + " form " + (number % forms + 1)
                            + " looks like " + boss.lookKey());
                }
            });
        }
        int killAt = 10 + animations.length * step;
        helper.runAfterDelay(killAt, () -> {
            for (ERBoss boss : bodies) {
                boss.setAiFrozen(false);
                boss.kill();
                check(boss.isDeadOrDying(), boss.bossId() + " survived kill()");
            }
        });
        helper.runAfterDelay(killAt + 10, () -> {
            for (ERBoss boss : bodies) {
                check(!boss.isRemoved() || boss.deathDurationTicks() <= 10,
                        boss.bossId() + " was removed before its death animation");
            }
        });
        int longest = 0;
        for (ERBoss boss : bodies) {
            longest = Math.max(longest, boss.deathDurationTicks());
        }
        helper.runAfterDelay(killAt + longest + 6, () -> {
            for (ERBoss boss : bodies) {
                check(boss.isRemoved(), boss.bossId() + " still present " + boss.deathTicks() + " ticks after dying");
            }
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ the calamities

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void calamityOfTheMeadowFightsAndFalls(GameTestHelper helper) {
        fight(helper, "c_meadow");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void calamityOfTheMoonForestFightsAndFalls(GameTestHelper helper) {
        fight(helper, "c_moon_forest");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void calamityOfTheIronMineFightsAndFalls(GameTestHelper helper) {
        fight(helper, "c_iron_mine");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void calamityOfTheSacredFallsFightsAndFalls(GameTestHelper helper) {
        fight(helper, "c_sacred_falls");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void calamityOfTheAncientRuinsFightsAndFalls(GameTestHelper helper) {
        fight(helper, "c_ancient_ruins");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void calamityOfTheSoaringPeaksFightsAndFalls(GameTestHelper helper) {
        fight(helper, "c_summit");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void calamityOfTheEndForestFightsAndFalls(GameTestHelper helper) {
        fight(helper, "c_end_forest");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void calamityOfTheFarmFightsAndFalls(GameTestHelper helper) {
        fight(helper, "c_farm");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void calamityOfTheLakesideFightsAndFalls(GameTestHelper helper) {
        fight(helper, "c_lakeside");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void calamityOfTheHarbourFightsAndFalls(GameTestHelper helper) {
        fight(helper, "c_harbour");
    }

    // ------------------------------------------------------------------ the wanderers

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingBrambleKnightFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_bramble_knight");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingMoonmothWitchFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_moonmoth_witch");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingSlagTitanFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_slag_titan");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingCascadeWyvernFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_cascade_wyvern");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingMistHermitFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_mist_hermit");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingWalkingGlyphFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_walking_glyph");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingAshHeraldFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_ash_herald");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingThunderRocFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_thunder_roc");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingGlacierBearerFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_glacier_bearer");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingHollowWalkerFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_hollow_walker");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingGravebloomFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_gravebloom");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingScarecrowKingFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_scarecrow_king");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingReedSerpentFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_reed_serpent");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingSunkenCaptainFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_sunken_captain");
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wanderingTideSingerFightsAndFalls(GameTestHelper helper) {
        fight(helper, "w_tide_singer");
    }
}
