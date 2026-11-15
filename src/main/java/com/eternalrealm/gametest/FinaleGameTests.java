package com.eternalrealm.gametest;

import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.forge.Afflictions;
import com.eternalrealm.combat.forge.CombatEvents;
import com.eternalrealm.combat.forge.CombatHits;
import com.eternalrealm.combat.forge.ERItems;
import com.eternalrealm.combat.forge.ERMobEffects;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.job.SkillTheme;
import com.eternalrealm.job.forge.BoneKing;
import com.eternalrealm.job.forge.FinaleMechanics;
import com.eternalrealm.server.ServerContext;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
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
import java.util.List;
import java.util.UUID;

/**
 * The finales' (秘奥義) own mechanics on a real server: the guardian's ward, the hexblade's hex, the necromancer's bone
 * king and the inquisitor's sentences. Same arena and conventions as {@link SkillTreeGameTests}.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FinaleGameTests {
    private static final String ARENA = "arena";
    private static final CombatStats PLAIN = new CombatStats(1.0, 0.0, 0.0, 1.0, 0.0, 1.5, 100.0, 1.0);
    private static final float EPS = 1.0e-3F;

    private FinaleGameTests() {
    }

    /** 金剛不壊: while the ward holds no blow, fall or fire hurts its bearer; the void still does. */
    @GameTest(template = ARENA)
    public static void theAegisWardsOffEverythingButTheVoid(GameTestHelper helper) {
        ServerContext context = requireContext();
        Zombie zombie = still(helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 6)));
        FakePlayer guardian = fighter(helper, "er_gt_aegis", WeaponProfiles.ONE_HANDED_SWORD);
        context.combat().service().register(guardian.getUUID(), PLAIN, context.combat().now());
        try {
            guardian.addEffect(new MobEffectInstance(ERMobEffects.DIVINE_AEGIS.get(), 200));
            check(ERMobEffects.isWarded(guardian), "the ward should hold");
            for (DamageSource source : List.of(zombie.damageSources().mobAttack(zombie),
                    guardian.damageSources().fall(), guardian.damageSources().inFire())) {
                LivingAttackEvent attack = new LivingAttackEvent(guardian, source, 6.0F);
                CombatEvents.onLivingAttack(attack);
                check(attack.isCanceled(), source.getMsgId() + " got through the ward");
            }
            LivingAttackEvent fall = new LivingAttackEvent(guardian, guardian.damageSources().fellOutOfWorld(), 6.0F);
            CombatEvents.onLivingAttack(fall);
            check(!fall.isCanceled(), "the void is never warded off");
        } finally {
            context.combat().onPlayerLeft(guardian.getUUID());
        }
        helper.succeed();
    }

    /** 呪詛 (丑の刻参り): it hurts every second from the strength stored on its victim. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void aHexHurtsEverySecond(GameTestHelper helper) {
        IronGolem golem = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 6)));
        FakePlayer hexblade = fighter(helper, "er_gt_hex", WeaponProfiles.ONE_HANDED_SWORD);
        Afflictions.apply(hexblade, golem, 10.0F, SkillEffect.AfflictionKind.CURSE, 60, 0.25, 1.0,
                hexblade.position());
        check(golem.hasEffect(ERMobEffects.HEXED.get()), "the golem should be hexed");
        float before = golem.getHealth();
        helper.runAfterDelay(45, () -> {
            float lost = before - golem.getHealth();
            // 2.5 per second (25 % of the 10 damage hit), ticking every second
            check(lost >= 2.5F - EPS, "the hex should have dealt at least 2.5 by now, dealt " + lost);
            helper.succeed();
        });
    }

    /** 神判: the nearest foe is bound in chains, the next branded, the third withered. */
    @GameTest(template = ARENA)
    public static void theVerdictSentencesEachFoeInTurn(GameTestHelper helper) {
        FakePlayer inquisitor = fighter(helper, "er_gt_verdict", WeaponProfiles.STAFF);
        IronGolem near = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 6)));
        IronGolem middle = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 8)));
        IronGolem far = still(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(1, 2, 10)));
        FinaleMechanics.verdicts(inquisitor, List.of(new CombatHits.HitTarget(far, 9.0F),
                new CombatHits.HitTarget(near, 9.0F), new CombatHits.HitTarget(middle, 9.0F)), 1, 1.0);
        check(ERMobEffects.isStunned(near), "the nearest foe should be bound in chains (stunned)");
        check(middle.hasEffect(ERMobEffects.VULNERABLE.get()), "the next foe should be branded (vulnerable)");
        check(far.hasEffect(ERMobEffects.WEAKENED.get()), "the third foe should be withered (weakened)");
        check(!near.hasEffect(ERMobEffects.VULNERABLE.get()), "a foe gets its own sentence only");
        helper.succeed();
    }

    /** 骸王顕現: the king rises ahead of its summoner, nothing hurts it, and it crumbles away after its time. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void theBoneKingRisesAndCrumbles(GameTestHelper helper) {
        FakePlayer necromancer = fighter(helper, "er_gt_bone_king", WeaponProfiles.STAFF);
        BoneKing.summon(necromancer, PLAIN, WeaponProfiles.STAFF, DamageType.MAGIC, SkillTheme.SHADOW, 1.0,
                new SkillEffect.Summon(1, "bone_king", 40, 2, 2.5));
        List<BoneKing> kings = helper.getLevel().getEntitiesOfClass(BoneKing.class,
                necromancer.getBoundingBox().inflate(8.0));
        check(kings.size() == 1, "one king should rise, found " + kings.size());
        BoneKing king = kings.get(0);
        check(necromancer.getUUID().equals(king.getOwnerUUID()), "the king serves its summoner");
        check(!king.hurt(necromancer.damageSources().playerAttack(necromancer), 100.0F), "the king was hurt");
        check(!king.isPickable() && !king.isPushable(), "the king is neither picked nor pushed");
        // a second summon: the first king crumbles, a new one rises
        BoneKing.summon(necromancer, PLAIN, WeaponProfiles.STAFF, DamageType.MAGIC, SkillTheme.SHADOW, 1.0,
                new SkillEffect.Summon(1, "bone_king", 40, 2, 2.5));
        helper.runAfterDelay(120, () -> {
            check(helper.getLevel().getEntitiesOfClass(BoneKing.class, necromancer.getBoundingBox().inflate(16.0))
                    .isEmpty(), "every king should have crumbled away by now");
            helper.succeed();
        });
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
