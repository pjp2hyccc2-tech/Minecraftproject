package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.entity.BossAnimation;
import com.eternalrealm.entity.BossKind;
import com.eternalrealm.entity.ERBoss;
import com.eternalrealm.entity.EREntityTypes;
import com.eternalrealm.entity.ERMonster;
import com.eternalrealm.entity.EnemyAnim;
import com.eternalrealm.entity.EnemyRig;
import com.eternalrealm.entity.EnemyVariant;
import com.eternalrealm.entity.HumanoidEnemy;
import com.eternalrealm.entity.RigData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game tests of the enemy and boss entities: every enemy variant and every boss spawns with the right look and
 * hitbox, plays every animation, fights (hits land at the impact frame), and dies with a death animation followed by
 * removal - all without exceptions on a real server.
 *
 * <p>Arena: 9x5x9 with a stone floor; entities stand at relative y=2 (see CombatGameTests).</p>
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EntityGameTests {
    private static final String ARENA = "arena";

    private EntityGameTests() {
    }

    // ------------------------------------------------------------------------------------------------ enemies

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void humanoidVariantsSpawnAnimateAndDie(GameTestHelper helper) {
        enemyVariants(helper, EnemyRig.HUMANOID);
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void beastVariantsSpawnAnimateAndDie(GameTestHelper helper) {
        enemyVariants(helper, EnemyRig.BEAST);
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void golemVariantsSpawnAnimateAndDie(GameTestHelper helper) {
        enemyVariants(helper, EnemyRig.GOLEM);
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void flyerVariantsSpawnAnimateAndDie(GameTestHelper helper) {
        enemyVariants(helper, EnemyRig.FLYER);
    }

    private static void enemyVariants(GameTestHelper helper, EnemyRig rig) {
        List<String> variants = ERMonster.variantsOf(rig);
        check(!variants.isEmpty(), "no variants for " + rig);
        for (EnemyVariant behaviour : EnemyVariant.values()) {
            if (behaviour.rig() == rig) {
                check(variants.contains(behaviour.id()), "variant " + behaviour.id() + " has no model look");
            }
        }
        for (String id : variants) {
            check(EnemyVariant.of(rig, id).id().equals(id), "variant " + id + " has no behaviour entry");
        }
        int columns = rig == EnemyRig.GOLEM ? 3 : 4;
        int spacing = rig == EnemyRig.GOLEM ? 3 : 2;
        List<ERMonster> spawned = new ArrayList<>();
        int maxDeath = 0;
        for (int i = 0; i < variants.size(); i++) {
            String id = variants.get(i);
            BlockPos pos = new BlockPos(1 + (i % columns) * spacing, 2, 1 + (i / columns) * spacing);
            ERMonster mob = helper.spawn(EREntityTypes.enemy(rig).get(), pos);
            mob.setVariant(id);
            check(id.equals(mob.getVariant()), "variant not applied: " + id);
            float expected = mob.getType().getHeight() * RigData.scale(rig.id(), id);
            check(Math.abs(mob.getBbHeight() - expected) < 0.01F,
                    id + ": hitbox height " + mob.getBbHeight() + " != " + expected);
            check(!mob.getName().getString().isEmpty(), id + ": no name");
            maxDeath = Math.max(maxDeath, mob.deathDurationTicks());
            spawned.add(mob);
        }
        int killAt = 40;
        helper.runAfterDelay(killAt - 10, () -> {
            for (ERMonster mob : spawned) {
                check(mob.isAlive(), mob.getVariant() + " died on its own");
                mob.playAnimation(EnemyAnim.ATTACK1);
                mob.playAnimation(EnemyAnim.ATTACK2);
                mob.playAnimation(EnemyAnim.CAST);
                mob.hurt(mob.damageSources().generic(), 1.0F);
            }
        });
        helper.runAfterDelay(killAt, () -> {
            for (ERMonster mob : spawned) {
                mob.kill();
                check(mob.isDeadOrDying(), mob.getVariant() + " survived kill()");
            }
        });
        helper.runAfterDelay(killAt + 3, () -> {
            for (ERMonster mob : spawned) {
                check(!mob.isRemoved(), mob.getVariant() + " was removed before its death animation played");
            }
        });
        helper.runAfterDelay(killAt + maxDeath + 5, () -> {
            for (ERMonster mob : spawned) {
                check(mob.isRemoved(), mob.getVariant() + " still present " + mob.deathTicks() + " ticks after dying");
            }
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void meleeEnemyHitsAtTheImpactFrame(GameTestHelper helper) {
        HumanoidEnemy bandit = helper.spawn(EREntityTypes.HUMANOID_ENEMY.get(), new BlockPos(4, 2, 3));
        bandit.setVariant("bandit");
        IronGolem target = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 5));
        target.setNoAi(true);
        float before = target.getHealth();
        bandit.setTarget(target);
        helper.succeedWhen(() -> {
            if (!(target.getHealth() < before)) {
                throw new GameTestAssertException("the bandit has not hit yet (acting=" + bandit.isActing() + ")");
            }
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void rangedEnemyFiresBolts(GameTestHelper helper) {
        HumanoidEnemy mage = helper.spawn(EREntityTypes.HUMANOID_ENEMY.get(), new BlockPos(4, 2, 1));
        mage.setVariant("frost_mage");
        check(mage.isRangedAttacker(), "frost mages are ranged");
        IronGolem target = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 8));
        target.setNoAi(true);
        float before = target.getHealth();
        mage.setTarget(target);
        helper.succeedWhen(() -> {
            if (!(target.getHealth() < before)) {
                throw new GameTestAssertException("no bolt has hit yet (action=" + mage.currentAction() + ")");
            }
        });
    }

    @GameTest(template = ARENA)
    public static void enemyVariantSurvivesSaving(GameTestHelper helper) {
        ERMonster golem = helper.spawn(EREntityTypes.GOLEM_ENEMY.get(), new BlockPos(4, 2, 4));
        golem.setVariant("lava_golem");
        CompoundTag tag = golem.saveWithoutId(new CompoundTag());
        ERMonster copy = EREntityTypes.GOLEM_ENEMY.get().create(helper.getLevel());
        check(copy != null, "could not create a golem");
        copy.load(tag);
        check("lava_golem".equals(copy.getVariant()), "variant lost: " + copy.getVariant());
        check(Math.abs(copy.getBbWidth() - golem.getBbWidth()) < 1.0E-4F, "hitbox not restored");
        check(copy.fireImmune(), "lava golems are fire immune");
        boolean rejected = false;
        try {
            copy.setVariant("no_such_variant");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "unknown variants must be rejected");
        golem.discard();
        helper.succeed();
    }

    // -------------------------------------------------------------------------------------------------- bosses

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void bossB1PlaysEverythingAndDies(GameTestHelper helper) {
        bossLifecycle(helper, BossKind.B1);
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void bossB2PlaysEverythingAndDies(GameTestHelper helper) {
        bossLifecycle(helper, BossKind.B2);
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void bossB3PlaysEverythingAndDies(GameTestHelper helper) {
        bossLifecycle(helper, BossKind.B3);
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void bossB4PlaysEverythingAndDies(GameTestHelper helper) {
        bossLifecycle(helper, BossKind.B4);
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void bossB5PlaysEverythingAndDies(GameTestHelper helper) {
        bossLifecycle(helper, BossKind.B5);
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void bossB6PlaysEverythingAndDies(GameTestHelper helper) {
        bossLifecycle(helper, BossKind.B6);
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void bossB7PlaysEverythingAndDies(GameTestHelper helper) {
        bossLifecycle(helper, BossKind.B7);
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void bossB8PlaysEverythingAndDies(GameTestHelper helper) {
        bossLifecycle(helper, BossKind.B8);
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void finalBossPlaysEverythingAndDies(GameTestHelper helper) {
        bossLifecycle(helper, BossKind.FINAL);
    }

    private static void bossLifecycle(GameTestHelper helper, BossKind kind) {
        ERBoss boss = helper.spawn(EREntityTypes.bossType(kind).get(), new BlockPos(4, 2, 4));
        check(kind.id().equals(boss.bossId()), "boss id " + boss.bossId());
        check(EREntityTypes.boss(kind.id()).get() == boss.getType(), "boss(id) lookup");
        check(Math.abs(boss.getBbWidth() - kind.width()) < 1.0E-4F && Math.abs(boss.getBbHeight() - kind.height()) < 1.0E-4F,
                "hitbox " + boss.getBbWidth() + "x" + boss.getBbHeight());
        check(boss.getBbHeight() >= 1.5F * 1.8F - 0.01F, kind.id() + " should be at least 1.5x player size");
        check(boss.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "knockback resistance");
        check(boss.isInvulnerableTo(boss.damageSources().inWall()) && boss.isInvulnerableTo(boss.damageSources().drown())
                && boss.isInvulnerableTo(boss.damageSources().fall()), "suffocation/drowning/fall immunity");
        check(!boss.causeFallDamage(30.0F, 1.0F, boss.damageSources().fall()), "no fall damage");
        check(!boss.removeWhenFarAway(1000.0) && boss.requiresCustomPersistence(), "bosses never despawn");
        boss.setAiFrozen(true);
        check(boss.isAiFrozen(), "AI freeze");
        BossAnimation[] animations = BossAnimation.values();
        int step = 12;
        for (int i = 0; i < animations.length; i++) {
            BossAnimation animation = animations[i];
            int index = i;
            helper.runAfterDelay(5L + (long) i * step, () -> {
                check(boss.isAlive(), kind.id() + " died during " + animation.id());
                boss.playAnimation(animation);
                check(boss.playingAnimation().isPresent() == animation.isAction(),
                        animation.id() + ": playing=" + boss.playingAnimation());
                check(boss.durationTicks(animation) > 0, animation.id() + " has no length");
                boss.setTelegraphing(index % 2 == 0);
                boss.setShielded(index % 3 == 0);
                boss.setEnraged(index >= animations.length / 2);
                boss.setPhase(index % 4);
                check(boss.isTelegraphing() == (index % 2 == 0) && boss.isShielded() == (index % 3 == 0),
                        "visual states not stored");
                check(boss.getPhase() == index % 4, "phase not stored");
            });
        }
        int killAt = 10 + animations.length * step;
        helper.runAfterDelay(killAt - 2, () -> {
            boss.setAiFrozen(false);
            boss.setTelegraphing(false);
            boss.setShielded(false);
            check(boss.lookKey().startsWith(kind.id()), "look key " + boss.lookKey());
        });
        helper.runAfterDelay(killAt, () -> {
            boss.kill();
            check(boss.isDeadOrDying(), kind.id() + " survived kill()");
        });
        helper.runAfterDelay(killAt + 20, () -> check(!boss.isRemoved(), kind.id() + " was removed before its death animation"));
        helper.runAfterDelay(killAt + boss.deathDurationTicks() + 6, () -> {
            check(boss.isRemoved(), kind.id() + " still present " + boss.deathTicks() + " ticks after dying");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void bossChasesAndSlamsItsTarget(GameTestHelper helper) {
        ERBoss boss = helper.spawn(EREntityTypes.BOSS_B1.get(), new BlockPos(4, 2, 2));
        IronGolem target = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 6));
        target.setNoAi(true);
        float before = target.getHealth();
        boss.setTarget(target);
        helper.succeedWhen(() -> {
            if (!(target.getHealth() < before)) {
                throw new GameTestAssertException("no slam has landed yet (playing=" + boss.playingAnimation() + ")");
            }
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
