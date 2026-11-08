package com.eternalrealm.gametest;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.CombatEvent;
import com.eternalrealm.combat.CombatRequest;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.RequestOutcome;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.forge.CombatEvents;
import com.eternalrealm.combat.forge.CombatHits;
import com.eternalrealm.combat.forge.ERItems;
import com.eternalrealm.combat.forge.StatEffects;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.server.ServerContext;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * In-game integration tests of the combat system's Forge glue: hit detection on real entities, damage and knockback
 * application, suppression of the vanilla attack, the defense hooks (dodge, parry, guard) and the max health
 * modifier. The pure rules are covered by the unit tests; these prove they are wired to Minecraft correctly.
 *
 * <p>Arena: 9x5x9 with a stone floor as its bottom layer. GameTest positions are relative to the structure block, which
 * sits one block below the structure, so the floor is at relative y=1 and entities stand at y=2 (spawning them at y=1
 * puts them inside the floor, where line of sight correctly blocks every hit). The attacker is a Forge fake player at
 * the centre facing south (+Z).</p>
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatGameTests {
    private static final String ARENA = "arena";
    private static final WeaponProfile SWORD = WeaponProfiles.ONE_HANDED_SWORD;
    /** No stat scaling, no critical hits, no damage reduction. */
    private static final CombatStats PLAIN = new CombatStats(1.0, 0.0, 0.0, 1.0, 0.0, 1.5, 100.0, 1.0);
    private static final float EPS = 1.0e-3F;

    private CombatGameTests() {
    }

    @GameTest(template = ARENA)
    public static void hitFrameDamagesOnlyTargetsInFront(GameTestHelper helper) {
        IronGolem front = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 6)));
        IronGolem behind = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 2)));
        FakePlayer attacker = fighter(helper, "er_gt_hitter", SWORD);
        float frontBefore = front.getHealth();
        float behindBefore = behind.getHealth();

        int damaged = swing(attacker, firstHit(SWORD));

        check(damaged == 1, "expected exactly one target, got " + damaged);
        // 6 base attack x 1.0 attack multiplier x 1.0 frame multiplier; iron golems have no armor
        check(Math.abs(frontBefore - front.getHealth() - 6.0F) < EPS,
                "front golem lost " + (frontBefore - front.getHealth()) + " instead of 6");
        check(behind.getHealth() == behindBefore, "the golem behind the attacker was hit");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void wallsBlockHits(GameTestHelper helper) {
        IronGolem target = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 7)));
        FakePlayer attacker = fighter(helper, "er_gt_wall", SWORD);
        for (int x = 3; x <= 5; x++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 5), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        float before = target.getHealth();
        check(swing(attacker, firstHit(SWORD)) == 0, "hit through a wall");
        check(target.getHealth() == before, "the golem behind the wall took damage");

        for (int x = 3; x <= 5; x++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 5), net.minecraft.world.level.block.Blocks.AIR);
            }
        }
        check(swing(attacker, firstHit(SWORD)) == 1, "control: no hit without the wall");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void comboHitsIgnoreVanillaHurtImmunity(GameTestHelper helper) {
        IronGolem target = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 6)));
        FakePlayer attacker = fighter(helper, "er_gt_combo", SWORD);
        float before = target.getHealth();
        swing(attacker, firstHit(SWORD));
        swing(attacker, firstHit(SWORD)); // same tick: vanilla would ignore it
        check(Math.abs(before - target.getHealth() - 12.0F) < EPS,
                "two hits should deal 12, dealt " + (before - target.getHealth()));
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void knockbackUsesTheActionValue(GameTestHelper helper) {
        Pig pig = still(helper.spawn(EntityType.PIG, new BlockPos(4, 2, 6)));
        FakePlayer attacker = fighter(helper, "er_gt_knockback", SWORD);
        pig.setDeltaMovement(Vec3.ZERO);
        float before = pig.getHealth();
        String stages = diagnose(helper, attacker, pig, firstHit(SWORD));
        int damaged = swing(attacker, firstHit(SWORD));
        Vec3 motion = pig.getDeltaMovement();
        String state = " [damaged=" + damaged + ", health " + before + "->" + pig.getHealth() + ", motion=" + motion
                + ", pig=" + pig.getBoundingBox() + ", attackerEye=" + attacker.getEyePosition() + ", " + stages + "]";
        check(damaged == 1, "a small mob in front must be hit" + state);
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        double expected = SWORD.combo().get(0).frames().get(0).knockback(); // 0.25 instead of vanilla's 0.4
        check(Math.abs(horizontal - expected) < 0.01, "knockback " + horizontal + " != " + expected + state);
        check(motion.z > 0.0, "the pig should be pushed away from the attacker (south)");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void vanillaAttackWithAWeaponDoesNothing(GameTestHelper helper) {
        IronGolem target = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 6)));
        FakePlayer attacker = fighter(helper, "er_gt_vanilla", SWORD);
        float before = target.getHealth();
        attacker.attack(target);
        check(target.getHealth() == before, "the vanilla attack path must not damage with an ETERNAL REALM weapon");

        attacker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        attacker.attack(target);
        check(target.getHealth() < before, "control: a bare-handed vanilla attack should still work");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void dodgeAvoidsAttacksButNotTheEnvironment(GameTestHelper helper) {
        ServerContext context = requireContext();
        Zombie zombie = still(helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 6)));
        FakePlayer defender = fighter(helper, "er_gt_dodger", SWORD);
        Combatant combatant = context.combat().service().register(defender.getUUID(), PLAIN, context.combat().now());
        try {
            RequestOutcome outcome = combatant.submit(CombatRequest.DODGE, context.combat().now(), SWORD, false, false);
            check(outcome == RequestOutcome.ACCEPTED, "dodge was " + outcome);

            LivingAttackEvent attack = new LivingAttackEvent(defender, zombie.damageSources().mobAttack(zombie), 4.0F);
            CombatEvents.onLivingAttack(attack);
            check(attack.isCanceled(), "an attack during the dodge must be avoided");

            LivingAttackEvent fall = new LivingAttackEvent(defender, defender.damageSources().fall(), 4.0F);
            CombatEvents.onLivingAttack(fall);
            check(!fall.isCanceled(), "fall damage is not dodged");
        } finally {
            context.combat().onPlayerLeft(defender.getUUID());
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void parryThenGuardFromTheFrontOnly(GameTestHelper helper) {
        ServerContext context = requireContext();
        Zombie front = still(helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 6)));
        Zombie behind = still(helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 2)));
        FakePlayer defender = fighter(helper, "er_gt_guard", SWORD);
        UUID id = defender.getUUID();
        Combatant combatant = context.combat().service().register(id, PLAIN, context.combat().now());
        defender.startUsingItem(InteractionHand.MAIN_HAND);
        check(context.combat().startGuard(defender, SWORD), "guard should start from idle");

        LivingAttackEvent fromBehind = new LivingAttackEvent(defender, behind.damageSources().mobAttack(behind), 4.0F);
        CombatEvents.onLivingAttack(fromBehind);
        check(!fromBehind.isCanceled(), "attacks from behind cannot be parried");

        LivingAttackEvent fromFront = new LivingAttackEvent(defender, front.damageSources().mobAttack(front), 4.0F);
        CombatEvents.onLivingAttack(fromFront);
        check(fromFront.isCanceled(), "an attack right after raising the guard must be parried");
        check(front.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the parried attacker should be slowed");

        helper.runAfterDelay(10, () -> {
            try {
                DamageSource source = front.damageSources().mobAttack(front);
                LivingAttackEvent late = new LivingAttackEvent(defender, source, 10.0F);
                CombatEvents.onLivingAttack(late);
                check(!late.isCanceled(), "after the parry window the attack is only guarded");
                double staminaBefore = combatant.stamina();
                LivingHurtEvent hurt = new LivingHurtEvent(defender, source, 10.0F);
                CombatEvents.onLivingHurt(hurt);
                check(Math.abs(hurt.getAmount() - 4.0F) < EPS, "60% guard should leave 4 of 10, left " + hurt.getAmount());
                check(Math.abs(staminaBefore - combatant.stamina() - 18.0) < 1.0e-6,
                        "blocking 6 damage should cost 18 stamina, cost " + (staminaBefore - combatant.stamina()));

                LivingHurtEvent unguarded = new LivingHurtEvent(defender, behind.damageSources().mobAttack(behind), 10.0F);
                CombatEvents.onLivingHurt(unguarded);
                check(unguarded.getAmount() == 10.0F, "damage from behind is not guarded");
            } finally {
                defender.stopUsingItem();
                context.combat().onPlayerLeft(id);
            }
            helper.succeed();
        });
    }

    /** A raised vanilla shield (or food, a bow...) blocks every combat action, so they cannot be combined. */
    @GameTest(template = ARENA)
    public static void noActionsWhileUsingAnotherItem(GameTestHelper helper) {
        ServerContext context = requireContext();
        FakePlayer player = fighter(helper, "er_gt_shield", SWORD);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(net.minecraft.world.item.Items.SHIELD));
        Combatant combatant = context.combat().service().register(player.getUUID(), PLAIN, context.combat().now());
        try {
            player.startUsingItem(InteractionHand.OFF_HAND);
            check(player.isUsingItem(), "setup: the shield should be raised");
            context.combat().handleRequest(player, CombatRequest.LIGHT, 1);
            check(combatant.currentAction().isEmpty(), "an attack was accepted behind a raised shield");
            player.stopUsingItem();
            context.combat().handleRequest(player, CombatRequest.LIGHT, 2);
            check(combatant.currentAction().isPresent(), "control: the attack works once the shield is lowered");
        } finally {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            context.combat().onPlayerLeft(player.getUUID());
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void vitBonusSetsMaxHealthWithoutStacking(GameTestHelper helper) {
        FakePlayer player = fighter(helper, "er_gt_health", SWORD);
        double base = 20.0;
        StatEffects.apply(player, withHealthBonus(8.0));
        check(Math.abs(player.getMaxHealth() - (base + 8.0)) < EPS, "max health " + player.getMaxHealth());
        StatEffects.apply(player, withHealthBonus(8.0));
        check(Math.abs(player.getMaxHealth() - (base + 8.0)) < EPS, "the bonus must not stack");
        player.setHealth(player.getMaxHealth());
        StatEffects.apply(player, withHealthBonus(2.0));
        check(Math.abs(player.getMaxHealth() - (base + 2.0)) < EPS, "lowered bonus not applied");
        check(player.getHealth() <= player.getMaxHealth() + EPS, "health must be clamped to the new maximum");
        StatEffects.apply(player, withHealthBonus(0.0));
        check(Math.abs(player.getMaxHealth() - base) < EPS, "zero bonus must remove the modifier");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** Which stage of hit detection accepts/rejects {@code target} (for failure messages). */
    private static String diagnose(GameTestHelper helper, FakePlayer attacker, net.minecraft.world.entity.LivingEntity target,
                                   CombatEvent.Hit hit) {
        Vec3 eye = attacker.getEyePosition();
        net.minecraft.world.phys.AABB box = target.getBoundingBox();
        boolean valid = CombatHits.isValidTarget(attacker, target);
        boolean shape = com.eternalrealm.combat.HitGeometry.contains(hit.frame().shape(),
                new com.eternalrealm.combat.AttackOrigin(eye.x, eye.y, eye.z, attacker.getYRot(), attacker.getXRot()),
                new com.eternalrealm.combat.Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        StringBuilder rays = new StringBuilder();
        for (Vec3 point : new Vec3[]{box.getCenter(), new Vec3(box.getCenter().x, box.maxY, box.minZ)}) {
            net.minecraft.world.phys.BlockHitResult result = helper.getLevel().clip(new net.minecraft.world.level.ClipContext(
                    eye, point, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, attacker));
            rays.append(result.getType()).append('@').append(result.getBlockPos()).append('=')
                    .append(helper.getLevel().getBlockState(result.getBlockPos())).append(' ');
        }
        int inQuery = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                attacker.getBoundingBox().inflate(hit.frame().shape().reach() + 2.0), entity -> entity == target).size();
        return "valid=" + valid + " shape=" + shape + " query=" + inQuery + " yaw=" + attacker.getYRot()
                + " pitch=" + attacker.getXRot() + " rays=" + rays.toString().trim();
    }

    private static CombatStats withHealthBonus(double bonus) {
        return new CombatStats(1.0, bonus, 0.0, 1.0, 0.0, 1.5, 100.0, 1.0);
    }

    /** Executes a melee hit frame with neutral stats and no critical hit; @return targets damaged */
    static int swing(FakePlayer attacker, CombatEvent.Hit hit) {
        return CombatHits.execute(attacker, PLAIN, hit, com.eternalrealm.combat.DamageType.PHYSICAL, () -> 0.99).size();
    }

    private static CombatEvent.Hit firstHit(WeaponProfile weapon) {
        ActionDefinition step = weapon.combo().get(0);
        return new CombatEvent.Hit(weapon, step, 0, step.frames().get(0), 1.0, 0L);
    }

    /** A fake player at the arena centre, standing on the floor, facing south, holding {@code weapon}. */
    private static FakePlayer fighter(GameTestHelper helper, String name, WeaponProfile weapon) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        Vec3 position = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        player.setYHeadRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(itemFor(weapon)));
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static net.minecraft.world.item.Item itemFor(WeaponProfile weapon) {
        return ERItems.weapons().stream().map(net.minecraftforge.registries.RegistryObject::get)
                .filter(item -> item.profile() == weapon).findFirst()
                .orElseThrow(() -> new GameTestAssertException("no item for " + weapon.id()));
    }

    private static <T extends Mob> T still(T mob) {
        mob.setNoAi(true);
        return mob;
    }

    private static ServerContext requireContext() {
        return ServerContext.current().orElseThrow(() -> new GameTestAssertException("ServerContext is not installed"));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
