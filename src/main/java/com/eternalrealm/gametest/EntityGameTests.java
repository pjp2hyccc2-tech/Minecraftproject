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
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game tests of the enemy and boss entities: every enemy variant and every boss spawns with the right look and
 * hitbox, plays every animation, fights (hits land at the impact frame), and dies with a death animation followed by
 * removal - all without exceptions on a real server.
 *
 * <p>Arena: 9x5x9 with a stone floor; entities stand at relative y=2 (see CombatGameTests). The line-ups of all the
 * enemy looks of a rig and the giants use
 * {@code boss_arena} (41x15x41: a closed stone hall with a 5x5 pillar in the middle and four smaller ones; made by
 * tools/gen-gametest-structures.py), where they also stand at relative y=2 (the template's floor layer is relative
 * y=1). Note: {@code GameTestHelper.relativePos} mirrors x and z (it turns positions by 180 degrees), unlike
 * {@code relativeVec}.</p>
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EntityGameTests {
    private static final String ARENA = "arena";
    private static final String BOSS_ARENA = "boss_arena";

    private EntityGameTests() {
    }

    // ------------------------------------------------------------------------------------------------ enemies

    @GameTest(template = BOSS_ARENA, timeoutTicks = 300)
    public static void humanoidVariantsSpawnAnimateAndDie(GameTestHelper helper) {
        enemyVariants(helper, EnemyRig.HUMANOID);
    }

    @GameTest(template = BOSS_ARENA, timeoutTicks = 300)
    public static void beastVariantsSpawnAnimateAndDie(GameTestHelper helper) {
        enemyVariants(helper, EnemyRig.BEAST);
    }

    @GameTest(template = BOSS_ARENA, timeoutTicks = 300)
    public static void golemVariantsSpawnAnimateAndDie(GameTestHelper helper) {
        enemyVariants(helper, EnemyRig.GOLEM);
    }

    @GameTest(template = BOSS_ARENA, timeoutTicks = 300)
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
        List<BlockPos> spots = variantSpots(rig == EnemyRig.GOLEM ? 4 : 3);
        check(spots.size() >= variants.size(), rig + ": " + variants.size() + " variants, " + spots.size() + " spots");
        List<ERMonster> spawned = new ArrayList<>();
        int maxDeath = 0;
        for (int i = 0; i < variants.size(); i++) {
            String id = variants.get(i);
            BlockPos pos = spots.get(i);
            ERMonster mob = helper.spawn(EREntityTypes.enemy(rig), pos);
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

    /**
     * A grid of places in {@code boss_arena} for the variant line-ups, {@code spacing} blocks apart, clear of the walls
     * and the pillars (the middle one at 18..22, the small ones at 10..11 and 29..30 on both axes).
     */
    private static List<BlockPos> variantSpots(int spacing) {
        List<BlockPos> spots = new ArrayList<>();
        for (int x = 3; x <= 37; x += spacing) {
            for (int z = 3; z <= 37; z += spacing) {
                if (nearPillar(x, z)) {
                    continue;
                }
                spots.add(new BlockPos(x, 2, z));
            }
        }
        return spots;
    }

    private static boolean nearPillar(int x, int z) {
        if (x >= 15 && x <= 25 && z >= 15 && z <= 25) {
            return true;
        }
        for (int cx : new int[]{10, 29}) {
            for (int cz : new int[]{10, 29}) {
                if (x >= cx - 3 && x <= cx + 4 && z >= cz - 3 && z <= cz + 4) {
                    return true;
                }
            }
        }
        return false;
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
        ERMonster ghoul = EREntityTypes.createEnemy(helper.getLevel(), "ghoul").orElse(null);
        check(ghoul instanceof HumanoidEnemy && "ghoul".equals(ghoul.getVariant()), "createEnemy(ghoul)");
        check(EREntityTypes.createEnemy(helper.getLevel(), "scorpion").map(ERMonster::rig).orElse(null) == EnemyRig.BEAST,
                "createEnemy(scorpion) is a beast");
        check(EREntityTypes.createEnemy(helper.getLevel(), "no_such_variant").isEmpty(), "unknown ids give no entity");
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

    // ------------------------------------------------------------------------------------------ the giants

    @GameTest(template = BOSS_ARENA, timeoutTicks = 900)
    public static void theColossusWalksRoundThePillarsToItsTarget(GameTestHelper helper) {
        giantReachesItsTarget(helper, BossKind.B3);
    }

    @GameTest(template = BOSS_ARENA, timeoutTicks = 900)
    public static void theDragonWalksRoundThePillarsToItsTarget(GameTestHelper helper) {
        giantReachesItsTarget(helper, BossKind.B6);
    }

    @GameTest(template = BOSS_ARENA, timeoutTicks = 900)
    public static void theTreantWalksRoundThePillarsToItsTarget(GameTestHelper helper) {
        giantReachesItsTarget(helper, BossKind.B2);
    }

    @GameTest(template = BOSS_ARENA, timeoutTicks = 900)
    public static void theKingOfTheEndWalksRoundThePillarsToItsTarget(GameTestHelper helper) {
        giantReachesItsTarget(helper, BossKind.FINAL);
    }

    /**
     * A giant crosses the arena to a target behind the middle pillar and slams it: it finds a way round (a path or
     * straight steering), never sinks into a wall or a pillar on the way, and its slam lands from its own reach.
     */
    private static void giantReachesItsTarget(GameTestHelper helper, BossKind kind) {
        ERBoss boss = helper.spawn(EREntityTypes.bossType(kind).get(), new BlockPos(6, 2, 6));
        IronGolem target = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(34, 2, 34));
        target.setNoAi(true);
        float before = target.getHealth();
        boss.setTarget(target);
        check(boss.meleeReach() >= boss.getBbWidth() * 0.5 + 1.0, kind.id() + ": reach " + boss.meleeReach());
        check(boss.maxUpStep() >= 1.0F, kind.id() + " cannot step over a block");
        int[] inside = {0};
        helper.onEachTick(() -> {
            check(boss.isAlive(), kind.id() + " died");
            AABB box = boss.getBoundingBox().deflate(0.1);
            if (helper.getLevel().noCollision(boss, box)) {
                inside[0] = 0;
            } else if (++inside[0] == 1 || inside[0] >= 10) {
                String where = kind.id() + " overlaps" + collisions(helper, boss, box) + " at "
                        + helper.relativeVec(boss.position()) + ", box " + helper.relativeVec(new Vec3(box.minX, box.minY,
                        box.minZ)) + " .. " + helper.relativeVec(new Vec3(box.maxX, box.maxY, box.maxZ));
                LogUtils.getLogger().warn("[GameTest] {} (tick {} of the overlap)", where, inside[0]);
                // a momentary touch is the collision code's business; a body that stays in a block is stuck
                check(inside[0] < 10, where);
            }
            if (boss.getTarget() != target) {
                boss.setTarget(target);
            }
        });
        helper.succeedWhen(() -> {
            if (!(target.getHealth() < before)) {
                throw new GameTestAssertException(kind.id() + " has not reached its target yet (at "
                        + helper.relativeVec(boss.position()) + ", playing " + boss.playingAnimation() + ")");
            }
        });
    }

    /** The blocks (relative positions) and colliding entities inside {@code box}, for failure messages. */
    private static String collisions(GameTestHelper helper, ERBoss boss, AABB box) {
        StringBuilder found = new StringBuilder();
        ServerLevel level = helper.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty() && shape.bounds().move(pos).intersects(box) && found.length() < 400) {
                found.append(' ').append(helper.relativePos(pos.immutable()).toShortString()).append('=')
                        .append(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            }
        }
        int entities = level.getEntityCollisions(boss, box).size();
        if (entities > 0) {
            found.append(" and ").append(entities).append(" entities");
        }
        // blocks falling nearby (gravel or sand from elsewhere landing in the arena)
        for (net.minecraft.world.entity.item.FallingBlockEntity falling : level.getEntitiesOfClass(
                net.minecraft.world.entity.item.FallingBlockEntity.class, box.inflate(32.0))) {
            if (found.length() < 700) {
                found.append(" falling ").append(BuiltInRegistries.BLOCK.getKey(falling.getBlockState().getBlock()))
                        .append('@').append(helper.relativeVec(falling.position()));
            }
        }
        return found.length() == 0 ? " nothing found" : found.toString();
    }

    /** Later forms are a little bigger, model and hitbox alike, without sinking into the floor or the walls. */
    @GameTest(template = BOSS_ARENA, timeoutTicks = 100)
    public static void bossesGrowWithTheirForms(GameTestHelper helper) {
        ERBoss boss = helper.spawn(EREntityTypes.bossType(BossKind.B7).get(), new BlockPos(8, 2, 20));
        boss.setAiFrozen(true);
        float width = boss.getBbWidth();
        float height = boss.getBbHeight();
        float scale = boss.modelScale();
        check(Math.abs(width - BossKind.B7.width()) < 1.0E-4F, "first form hitbox " + width);
        boss.setPhase(3);
        float growth = 1.0F + 3.0F * (float) com.eternalrealm.boss.BossRigData.FORM_GROWTH;
        check(Math.abs(boss.getBbWidth() - width * growth) < 1.0E-3F
                        && Math.abs(boss.getBbHeight() - height * growth) < 1.0E-3F,
                "the last form's hitbox " + boss.getBbWidth() + "x" + boss.getBbHeight());
        check(boss.modelScale() > scale * 1.1F, "the last form's model " + boss.modelScale() + " vs " + scale);
        helper.runAfterDelay(10, () -> {
            check(helper.getLevel().noCollision(boss, boss.getBoundingBox().deflate(0.1)), "the grown boss is in a block");
            helper.succeed();
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
