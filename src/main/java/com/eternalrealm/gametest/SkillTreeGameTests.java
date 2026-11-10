package com.eternalrealm.gametest;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.SetLevelResult;
import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.CombatEvent;
import com.eternalrealm.combat.CombatRequest;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.RequestOutcome;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.forge.Afflictions;
import com.eternalrealm.combat.forge.CombatDriver;
import com.eternalrealm.combat.forge.CombatEvents;
import com.eternalrealm.combat.forge.ERItems;
import com.eternalrealm.combat.forge.ERMobEffects;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.job.SkillTree;
import com.eternalrealm.job.forge.JobActions;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.ServerContext;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The job skill trees on a real server: job weapon sets enforced by the server's combat state, the tree rules
 * (prerequisites, level gates, loadout, paid reset) through the player data service, and the new skill effects
 * (stun, poison, thorns, lifesteal). Same arena and conventions as {@link CombatGameTests}.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillTreeGameTests {
    private static final String ARENA = "arena";
    private static final CombatStats PLAIN = new CombatStats(1.0, 0.0, 0.0, 1.0, 0.0, 1.5, 100.0, 1.0);
    private static final float EPS = 1.0e-3F;

    private SkillTreeGameTests() {
    }

    /** A mage holding a sword: attacks, job skills and the guard are refused by the server; the staff works. */
    @GameTest(template = ARENA)
    public static void jobWeaponsAreEnforcedByTheServer(GameTestHelper helper) {
        ServerContext context = requireContext();
        FakePlayer mage = fighter(helper, "er_gt_job_weapon", WeaponProfiles.ONE_HANDED_SWORD);
        UUID id = mage.getUUID();
        Combatant combatant = context.combat().service().register(id, PLAIN, context.combat().now());
        try {
            combatant.setAllowedWeapons(Jobs.get(JobType.MAGE).weapons());
            combatant.setJobSkill(1, SkillTree.bindings(JobType.MAGE, 1, Map.of())[1]);
            long now = context.combat().now();
            RequestOutcome light = combatant.submit(CombatRequest.LIGHT, now, WeaponProfiles.ONE_HANDED_SWORD, false,
                    false);
            check(light == RequestOutcome.REJECTED_JOB_WEAPON, "a mage swinging a sword was " + light);
            RequestOutcome skill = combatant.submit(CombatRequest.jobSkill(1), now, WeaponProfiles.ONE_HANDED_SWORD,
                    false, false);
            check(skill == RequestOutcome.REJECTED_JOB_WEAPON, "a fireball cast with a sword was " + skill);
            mage.startUsingItem(InteractionHand.MAIN_HAND);
            check(!context.combat().startGuard(mage, WeaponProfiles.ONE_HANDED_SWORD), "a mage guarded with a sword");
            mage.stopUsingItem();

            context.combat().handleRequest(mage, CombatRequest.LIGHT, 1);
            check(combatant.currentAction().isEmpty(), "the server started an attack with a forbidden weapon");
            mage.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ERItems.weaponFor(WeaponProfiles.STAFF.type())));
            context.combat().handleRequest(mage, CombatRequest.LIGHT, 2);
            check(combatant.currentAction().isPresent(), "control: the staff attack should start");
        } finally {
            context.combat().onPlayerLeft(id);
        }
        helper.succeed();
    }

    /** Dodging is never refused because of the weapon: a player can always get out of danger. */
    @GameTest(template = ARENA)
    public static void dodgeWorksWithAForbiddenWeapon(GameTestHelper helper) {
        ServerContext context = requireContext();
        FakePlayer priest = fighter(helper, "er_gt_job_dodge", WeaponProfiles.BOW);
        UUID id = priest.getUUID();
        Combatant combatant = context.combat().service().register(id, PLAIN, context.combat().now());
        try {
            combatant.setAllowedWeapons(Jobs.get(JobType.PRIEST).weapons());
            RequestOutcome dodge = combatant.submit(CombatRequest.DODGE, context.combat().now(), WeaponProfiles.BOW,
                    false, false);
            check(dodge == RequestOutcome.ACCEPTED, "a dodge holding a forbidden bow was " + dodge);
        } finally {
            context.combat().onPlayerLeft(id);
        }
        helper.succeed();
    }

    /**
     * Stage 0: wait for the character; then as a level 10 knight: prerequisites (heavy slam needs shield bash Lv3),
     * level gates, the loadout rules and the paid reset at the job master's price.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void treeRulesAreCheckedByTheServer(GameTestHelper helper) {
        ServerContext context = requireContext();
        PlayerDataService data = context.playerData();
        FakePlayer player = fighter(helper, "er_gt_tree_" + UUID.randomUUID().toString().substring(0, 8),
                WeaponProfiles.ONE_HANDED_SWORD);
        UUID id = player.getUUID();
        data.onPlayerJoin(id, "gametest_tree");
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw failure.get(); // keeps failing (with the first message) until the test times out
            }
            if (done.get()) {
                return;
            }
            check(data.isActive(id), "waiting for the character to load");
            done.set(true);
            try {
                check(JobActions.chooseJob(player, context, JobType.KNIGHT), "choosing the knight failed");
                check(data.setLevel(id, 10) == SetLevelResult.SUCCESS, "could not set level 10");
                check(data.snapshot(id).orElseThrow().unspentSkillPoints() == 20, "a level 10 character has 20 points");

                expect(data.investSkill(id, "knight_heavy_slam"), SkillTree.InvestResult.PREREQUISITE, "heavy slam");
                expect(data.investSkill(id, "knight_shield_bash"), SkillTree.InvestResult.OK, "shield bash Lv2");
                expect(data.investSkill(id, "knight_shield_bash"), SkillTree.InvestResult.OK, "shield bash Lv3");
                expect(data.investSkill(id, "knight_heavy_slam"), SkillTree.InvestResult.OK, "heavy slam after Lv3");
                expect(data.investSkill(id, "knight_titan_strike"), SkillTree.InvestResult.LOCKED, "a tier 5 node");
                expect(data.investSkill(id, "mage_fireball"), SkillTree.InvestResult.WRONG_JOB, "another job's skill");

                expectSlot(data.setSkillSlot(id, SkillTree.ULTIMATE_SLOT, "knight_heavy_slam"),
                        SkillTree.LoadoutResult.NEEDS_ULTIMATE, "an active in the ultimate slot");
                expectSlot(data.setSkillSlot(id, 2, "knight_toughness"), SkillTree.LoadoutResult.PASSIVE, "a passive");
                expectSlot(data.setSkillSlot(id, 2, "knight_cleave"), SkillTree.LoadoutResult.NOT_LEARNED,
                        "an unlearned active");
                expectSlot(data.setSkillSlot(id, 3, "knight_heavy_slam"), SkillTree.LoadoutResult.OK, "heavy slam");
                CharacterSnapshot snapshot = data.snapshot(id).orElseThrow();
                check("knight_heavy_slam".equals(SkillTree.effectiveLoadout(JobType.KNIGHT, snapshot.level(),
                                snapshot.skillInvestments(), snapshot.skillLoadout()).get(2)),
                        "heavy slam should sit in slot 3: " + snapshot.skillLoadout());
                check(snapshot.unspentSkillPoints() == 17, "three points should be spent, left "
                        + snapshot.unspentSkillPoints());

                long cost = context.game().jobRules().skillResetCost(10);
                long gold = snapshot.gold();
                if (gold < cost) {
                    PlayerDataService.SkillReset poor = data.resetSkills(id, context.game().jobRules());
                    check(poor.result() == PlayerDataService.SkillResetResult.NOT_ENOUGH_GOLD && poor.cost() == cost,
                            "a reset without enough gold was " + poor);
                    data.grantGold(id, cost - gold);
                    gold = cost;
                }
                PlayerDataService.SkillReset paid = data.resetSkills(id, context.game().jobRules());
                check(paid.result() == PlayerDataService.SkillResetResult.OK && paid.cost() == cost,
                        "the paid reset was " + paid);
                CharacterSnapshot after = data.snapshot(id).orElseThrow();
                check(after.skillInvestments().isEmpty() && after.unspentSkillPoints() == 20
                                && after.gold() == gold - cost,
                        "after the reset: " + after.skillInvestments() + ", points " + after.unspentSkillPoints()
                                + ", gold " + after.gold());
                expect(data.investSkill(id, "knight_heavy_slam"), SkillTree.InvestResult.PREREQUISITE,
                        "heavy slam after the reset");
            } catch (GameTestAssertException e) {
                failure.set(e);
                throw e;
            } finally {
                data.onPlayerLeave(id);
            }
        });
    }

    /** A stunned mob cannot strike; poison keeps hurting for its duration. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void stunStopsAttacksAndPoisonHurtsOverTime(GameTestHelper helper) {
        Zombie zombie = still(helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 6)));
        IronGolem golem = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 6)));
        FakePlayer assassin = fighter(helper, "er_gt_afflict", WeaponProfiles.DUAL_BLADES);
        Afflictions.apply(assassin, zombie, 10.0F, SkillEffect.AfflictionKind.STUN, 40, 0.0, 1.0, assassin.position());
        check(ERMobEffects.isStunned(zombie), "the zombie should be stunned");
        LivingAttackEvent attack = new LivingAttackEvent(assassin, zombie.damageSources().mobAttack(zombie), 4.0F);
        CombatEvents.onLivingAttack(attack);
        check(attack.isCanceled(), "a stunned zombie must not hit");

        Afflictions.apply(assassin, golem, 10.0F, SkillEffect.AfflictionKind.POISON, 60, 0.25, 1.0, assassin.position());
        check(golem.hasEffect(ERMobEffects.POISONED.get()), "the golem should be poisoned");
        float before = golem.getHealth();
        helper.runAfterDelay(45, () -> {
            float lost = before - golem.getHealth();
            // 2.5 per second (25 % of the 10 damage hit), ticking every second
            check(lost >= 2.5F - EPS, "poison should have dealt at least 2.5 by now, dealt " + lost);
            helper.succeed();
        });
    }

    /** The thorns buff sends part of the damage a player takes back to the attacker. */
    @GameTest(template = ARENA)
    public static void thornsReflectDamage(GameTestHelper helper) {
        ServerContext context = requireContext();
        IronGolem attacker = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 6)));
        FakePlayer knight = fighter(helper, "er_gt_thorns", WeaponProfiles.ONE_HANDED_SWORD);
        context.combat().service().register(knight.getUUID(), PLAIN, context.combat().now());
        try {
            knight.addEffect(new MobEffectInstance(ERMobEffects.THORNS.get(), 200, ERMobEffects.amplifierFor(0.3)));
            float before = attacker.getHealth();
            float taken = context.combat().defend(knight, attacker.damageSources().mobAttack(attacker), 10.0F);
            float reflected = before - attacker.getHealth();
            check(Math.abs(taken - 10.0F) < EPS, "thorns do not reduce the damage taken, took " + taken);
            check(Math.abs(reflected - 3.0F) < 0.05F, "30% thorns should send 3 of 10 back, sent " + reflected);
        } finally {
            context.combat().onPlayerLeft(knight.getUUID());
        }
        helper.succeed();
    }

    /** The lifesteal buff heals the attacker by part of the damage its skill hit dealt. */
    @GameTest(template = ARENA)
    public static void lifestealHealsTheAttacker(GameTestHelper helper) {
        IronGolem target = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 6)));
        FakePlayer fighter = fighter(helper, "er_gt_lifesteal", WeaponProfiles.ONE_HANDED_SWORD);
        fighter.setHealth(5.0F);
        fighter.addEffect(new MobEffectInstance(ERMobEffects.LIFESTEAL.get(), 200, ERMobEffects.amplifierFor(0.25)));
        SkillDefinition bash = Jobs.skill("knight_shield_bash").orElseThrow();
        float before = target.getHealth();
        CombatDriver.handleEvent(fighter, PLAIN, skillHit(bash, WeaponProfiles.ONE_HANDED_SWORD, 0));
        float dealt = before - target.getHealth();
        check(dealt > 0.0F, "the shield bash should hit");
        float expected = 5.0F + dealt * 0.25F;
        check(Math.abs(fighter.getHealth() - expected) < 0.05F, "25% lifesteal of " + dealt + " should leave "
                + expected + " health, has " + fighter.getHealth());
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static CombatEvent.Hit skillHit(SkillDefinition skill, WeaponProfile weapon, int frame) {
        ActionDefinition action = skill.action();
        return new CombatEvent.Hit(weapon, action, frame, action.frames().get(frame), 1.0, 0L);
    }

    private static void expect(java.util.Optional<SkillTree.InvestResult> result, SkillTree.InvestResult wanted,
                               String what) {
        check(result.isPresent() && result.get() == wanted, what + ": expected " + wanted + ", was " + result);
    }

    private static void expectSlot(java.util.Optional<SkillTree.LoadoutResult> result, SkillTree.LoadoutResult wanted,
                                   String what) {
        check(result.isPresent() && result.get() == wanted, what + ": expected " + wanted + ", was " + result);
    }

    private static ServerContext requireContext() {
        return ServerContext.current().orElseThrow(() -> new GameTestAssertException("ServerContext is not installed"));
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
