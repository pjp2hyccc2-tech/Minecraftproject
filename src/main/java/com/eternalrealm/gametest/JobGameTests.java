package com.eternalrealm.gametest;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.CombatEvent;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.DamageRoll;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.ProjectileSpec;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.forge.CombatDriver;
import com.eternalrealm.combat.forge.CombatHits;
import com.eternalrealm.combat.forge.CombatProjectiles;
import com.eternalrealm.combat.forge.ERItems;
import com.eternalrealm.combat.forge.ERMobEffects;
import com.eternalrealm.combat.forge.ProjectilePayload;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.forge.JobActions;
import com.eternalrealm.job.forge.SkillEffectsExecutor;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.ServerContext;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 3 glue: job skills through the combat driver (damage + afflictions), projectiles (staff bolts, arrows,
 * explosions), effect buffs, healing, cooperative PvP rules and job selection through the player data service.
 * Same arena and conventions as {@link CombatGameTests} (fighter at the centre facing south, targets at y=2).
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JobGameTests {
    private static final String ARENA = "arena";
    private static final CombatStats PLAIN = new CombatStats(1.0, 0.0, 0.0, 1.0, 0.0, 1.5, 100.0, 1.0);
    private static final float EPS = 1.0e-3F;

    private JobGameTests() {
    }

    @GameTest(template = ARENA)
    public static void knightBashDamagesAndSlows(GameTestHelper helper) {
        IronGolem target = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 6)));
        FakePlayer knight = fighter(helper, "er_gt_bash", WeaponProfiles.ONE_HANDED_SWORD);
        SkillDefinition bash = Jobs.skill("knight_shield_bash").orElseThrow();
        float before = target.getHealth();
        CombatDriver.handleEvent(knight, PLAIN, skillHit(bash, WeaponProfiles.ONE_HANDED_SWORD, 0));
        float dealt = before - target.getHealth();
        check(Math.abs(dealt - 6.0F * 1.6F) < EPS, "shield bash should deal 9.6, dealt " + dealt);
        check(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "shield bash should slow the target");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void attackUpIncreasesDamage(GameTestHelper helper) {
        IronGolem target = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 6)));
        FakePlayer attacker = fighter(helper, "er_gt_attack_up", WeaponProfiles.ONE_HANDED_SWORD);
        attacker.addEffect(new MobEffectInstance(ERMobEffects.ATTACK_UP.get(), 200, ERMobEffects.amplifierFor(0.2)));
        float before = target.getHealth();
        CombatGameTests.swing(attacker, firstHit(WeaponProfiles.ONE_HANDED_SWORD));
        float dealt = before - target.getHealth();
        check(Math.abs(dealt - 7.2F) < EPS, "attack up 20% should make 6 into 7.2, dealt " + dealt);
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void staffBoltHitsTheTargetInFront(GameTestHelper helper) {
        IronGolem target = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 7)));
        FakePlayer mage = fighter(helper, "er_gt_staff", WeaponProfiles.STAFF);
        float before = target.getHealth();
        CombatProjectiles.launch(mage, PLAIN, firstHit(WeaponProfiles.STAFF), DamageType.MAGIC, List.of(), () -> 0.99);
        helper.succeedWhen(() -> {
            float dealt = before - target.getHealth();
            check(dealt > 0.0F, "waiting for the bolt");
            check(Math.abs(dealt - 6.0F) < EPS, "a staff bolt should deal 6, dealt " + dealt);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void bowArrowHitsTheTargetInFront(GameTestHelper helper) {
        IronGolem target = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 7)));
        FakePlayer ranger = fighter(helper, "er_gt_bow", WeaponProfiles.BOW);
        float before = target.getHealth();
        CombatProjectiles.launch(ranger, PLAIN, firstHit(WeaponProfiles.BOW), DamageType.RANGED, List.of(), () -> 0.99);
        helper.succeedWhen(() -> {
            float dealt = before - target.getHealth();
            check(dealt > 0.0F, "waiting for the arrow");
            check(Math.abs(dealt - 6.0F) < EPS, "an arrow should deal 6, dealt " + dealt);
        });
    }

    @GameTest(template = ARENA)
    public static void explosionsHitEverythingAround(GameTestHelper helper) {
        IronGolem left = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 6)));
        IronGolem right = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(6, 2, 6)));
        IronGolem far = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 1)));
        FakePlayer mage = fighter(helper, "er_gt_boom", WeaponProfiles.STAFF);
        ProjectilePayload payload = new ProjectilePayload(ProjectileSpec.magic("fireball", 1.5, 30).withExplosion(3.0),
                new DamageRoll(10.0, false), DamageType.MAGIC, 0.5, ActionKind.SKILL, List.of());
        float leftBefore = left.getHealth();
        float rightBefore = right.getHealth();
        float farBefore = far.getHealth();
        Vec3 center = helper.absoluteVec(new Vec3(4.5, 2.5, 6.5));
        CombatHits.explosion(mage, helper.getLevel(), center, payload);
        check(Math.abs(leftBefore - left.getHealth() - 10.0F) < EPS, "left golem should take 10");
        check(Math.abs(rightBefore - right.getHealth() - 10.0F) < EPS, "right golem should take 10");
        check(far.getHealth() == farBefore, "the golem outside the radius was hit");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void healRestoresTheCasterAndBuffsApply(GameTestHelper helper) {
        FakePlayer priest = fighter(helper, "er_gt_heal", WeaponProfiles.STAFF);
        priest.setHealth(5.0F);
        SkillDefinition heal = Jobs.skill("priest_heal").orElseThrow();
        SkillEffectsExecutor.apply(priest, PLAIN, heal.effectsAt(0), List.of());
        // 20% of 20 max health + 4 = 8 with a healing multiplier of 1
        check(Math.abs(priest.getHealth() - 13.0F) < EPS, "heal should restore 8, health " + priest.getHealth());
        SkillDefinition blessing = Jobs.skill("priest_blessing").orElseThrow();
        SkillEffectsExecutor.apply(priest, PLAIN, blessing.effectsAt(0), List.of());
        check(Math.abs(ERMobEffects.attackMultiplier(priest) - 1.2) < 1.0e-6, "blessing should give attack up 20%");
        check(priest.hasEffect(MobEffects.REGENERATION), "blessing should give regeneration");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void playersNeverHurtEachOtherOutsideArenas(GameTestHelper helper) {
        FakePlayer first = fighter(helper, "er_gt_pvp_a", WeaponProfiles.ONE_HANDED_SWORD);
        FakePlayer second = FakePlayerFactory.get(helper.getLevel(), profile("er_gt_pvp_b"));
        check(!CombatHits.isValidTarget(first, second), "players must not be valid targets outside PvP matches");
        helper.succeed();
    }

    /** Stage 0: wait for the character; choose a job (free, starter weapon); stage 1: done. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void firstJobChoiceGivesTheStarterWeapon(GameTestHelper helper) {
        ServerContext context = ServerContext.current()
                .orElseThrow(() -> new GameTestAssertException("ServerContext is not installed"));
        PlayerDataService data = context.playerData();
        FakePlayer player = fighter(helper, "er_gt_job_" + UUID.randomUUID().toString().substring(0, 8),
                WeaponProfiles.ONE_HANDED_SWORD);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        UUID id = player.getUUID();
        data.onPlayerJoin(id, "gametest_job");
        AtomicBoolean done = new AtomicBoolean();
        helper.succeedWhen(() -> {
            if (done.get()) {
                return;
            }
            check(data.isActive(id), "waiting for the character to load");
            boolean changed = JobActions.chooseJob(player, context, JobType.MAGE);
            CharacterSnapshot snapshot = data.snapshot(id).orElseThrow();
            net.minecraft.world.item.Item staffItem = com.eternalrealm.item.forge.ERGearItems.equipment(
                    com.eternalrealm.item.EquipmentBase.weapon(com.eternalrealm.combat.WeaponType.STAFF,
                            com.eternalrealm.item.ItemTier.IRON));
            boolean hasStaff = player.getInventory().hasAnyMatching(stack -> stack.is(staffItem)
                    && com.eternalrealm.item.forge.EquipmentStacks.read(stack).isPresent());
            data.onPlayerLeave(id);
            done.set(true);
            if (!changed || !"mage".equals(snapshot.jobId()) || !hasStaff) {
                throw new GameTestAssertException("job choice failed: changed=" + changed + ", job="
                        + snapshot.jobId() + ", staff=" + hasStaff);
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private static CombatEvent.Hit firstHit(WeaponProfile weapon) {
        ActionDefinition step = weapon.combo().get(0);
        return new CombatEvent.Hit(weapon, step, 0, step.frames().get(0), 1.0, 0L);
    }

    private static CombatEvent.Hit skillHit(SkillDefinition skill, WeaponProfile weapon, int frame) {
        return new CombatEvent.Hit(weapon, skill.action(), frame, skill.action().frames().get(frame), 1.0, 0L);
    }

    private static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
    }

    private static FakePlayer fighter(GameTestHelper helper, String name, WeaponProfile weapon) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), profile(name));
        Vec3 position = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        player.setYHeadRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ERItems.weaponFor(weapon.type())));
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static <T extends Mob> T still(T mob) {
        mob.setNoAi(true);
        return mob;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
