package com.eternalrealm.testkit.bots.forge;

import com.eternalrealm.combat.CombatRequest;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.forge.WeaponItem;
import com.eternalrealm.item.forge.QuickUse;
import com.eternalrealm.job.SkillTree;
import com.eternalrealm.monster.forge.MonsterTags;
import com.eternalrealm.revival.forge.RevivalService;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.testkit.bots.BotPlans;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * How a test bot fights: it keeps its enemy in view, closes in to its weapon's reach (bows and staves keep their
 * distance), and asks the combat system for what a player's keys would: light attacks in a rhythm, now and then a
 * charged heavy attack, the weapon's skill and the job skills in its slots when they are ready, a dodge when hard
 * pressed, and a potion when its health runs low. Everything is checked by the server as a client's request would be.
 */
final class BotCombat {
    /** Health share under which a bot drinks. */
    private static final float DRINK_BELOW = 0.45F;
    /** A target farther than this is given up. */
    private static final double GIVE_UP = 32.0;

    private final TestBot bot;
    private final BotMover mover;
    private final RandomSource random;
    private LivingEntity target;
    private int sequence;
    private int nextAttack;
    private int nextWeaponSkill;
    private final int[] nextJobSkill = new int[SkillTree.LOADOUT_SLOTS + 1];
    private int nextDodge;
    private int nextPotion;
    private int heavyRelease = -1;
    private Vec3 chasing;
    private int strafe;

    BotCombat(TestBot bot, BotMover mover, RandomSource random) {
        this.bot = bot;
        this.mover = mover;
        this.random = random;
    }

    void engage(LivingEntity enemy) {
        if (enemy != target) {
            target = enemy;
            chasing = null;
        }
    }

    void disengage() {
        if (heavyRelease >= 0) {
            request(CombatRequest.HEAVY_RELEASE, -1);
            heavyRelease = -1;
        }
        target = null;
        chasing = null;
    }

    boolean fighting() {
        if (target != null && !valid(target)) {
            disengage();
        }
        return target != null;
    }

    LivingEntity target() {
        return fighting() ? target : null;
    }

    private boolean valid(LivingEntity enemy) {
        return enemy.isAlive() && !enemy.isRemoved() && enemy.level() == bot.level()
                && enemy.distanceToSqr(bot) < GIVE_UP * GIVE_UP && !RevivalService.isDown(enemy);
    }

    /** The nearest monster within {@code radius} of {@code around} that {@code also} accepts and the bot can see. */
    static Optional<Mob> nearestMonster(TestBot bot, Vec3 around, double radius, Predicate<Mob> also) {
        ServerLevel level = bot.serverLevel();
        Mob best = null;
        double bestDistance = radius * radius;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(around, around).inflate(radius),
                mob -> MonsterTags.isMonster(mob) && mob.isAlive() && also.test(mob))) {
            double distance = mob.distanceToSqr(around);
            if (distance <= bestDistance && bot.hasLineOfSight(mob)) {
                best = mob;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private WeaponProfile weapon() {
        return WeaponItem.profileOf(bot.getMainHandItem());
    }

    private boolean ranged() {
        WeaponProfile weapon = weapon();
        return weapon != null && BotPlans.isRanged(weapon.type());
    }

    private double engageDistance() {
        WeaponProfile weapon = weapon();
        return BotPlans.engageDistance(weapon == null ? null : weapon.type(), BotPlans.meleeReach(weapon));
    }

    /** Before the bot moves: close in on the enemy, or stand where it is to fight. */
    void beforeMove() {
        if (!fighting()) {
            return;
        }
        double distance = bot.distanceTo(target);
        double engage = engageDistance();
        if (distance > engage) {
            Vec3 at = target.position();
            if (chasing == null || chasing.distanceToSqr(at) > 2.25 || !mover.moving()) {
                mover.goTo(at, engage * 0.85, true);
                chasing = at;
            }
        } else if (mover.moving()) {
            mover.stop();
            chasing = null;
        }
    }

    /** After the mover set the inputs: face the enemy, keep the distance, attack, drink, dodge. */
    void afterMove(int level) {
        if (bot.getHealth() < bot.getMaxHealth() * DRINK_BELOW) {
            drink(level);
        }
        if (!fighting()) {
            return;
        }
        double distance = bot.distanceTo(target);
        double engage = engageDistance();
        if (distance > engage + 0.75) {
            return; // still closing in: the mover steers
        }
        face(target);
        if (ranged() && distance < engage * 0.45) {
            bot.zza = -0.9F; // too close for a bow or a staff: back off while shooting
        } else if (!ranged() && distance < engage) {
            if (bot.tickCount % 40 == 0) {
                strafe = random.nextInt(3) - 1; // a step aside now and then, as players circle their enemy
            }
            bot.xxa = strafe * 0.35F;
        }
        if (!bot.hasLineOfSight(target)) {
            return;
        }
        if (bot.getHealth() < bot.getMaxHealth() * 0.5F && distance < 3.0 && bot.tickCount >= nextDodge
                && random.nextFloat() < 0.04F) {
            request(CombatRequest.DODGE, -1);
            nextDodge = bot.tickCount + 60;
            return;
        }
        attack();
    }

    private void attack() {
        int now = bot.tickCount;
        if (heavyRelease >= 0) {
            if (now >= heavyRelease) {
                request(CombatRequest.HEAVY_RELEASE, -1);
                heavyRelease = -1;
                nextAttack = now + 10;
            }
            return;
        }
        if (now < nextAttack) {
            return;
        }
        int id = target.getId();
        if (now >= nextWeaponSkill && random.nextFloat() < 0.5F) {
            request(CombatRequest.SKILL, id);
            nextWeaponSkill = now + 80 + random.nextInt(80);
            nextAttack = now + 16;
            return;
        }
        int start = 1 + random.nextInt(SkillTree.LOADOUT_SLOTS);
        for (int i = 0; i < SkillTree.LOADOUT_SLOTS; i++) {
            int slot = 1 + (start - 1 + i) % SkillTree.LOADOUT_SLOTS;
            if (now < nextJobSkill[slot] || random.nextFloat() >= 0.3F) {
                continue;
            }
            boolean ultimate = slot == SkillTree.ULTIMATE_SLOT;
            if (ultimate && target.getHealth() < target.getMaxHealth() * 0.5F) {
                continue; // the ultimate is kept for a fresh, strong enemy
            }
            request(CombatRequest.jobSkill(slot), id);
            nextJobSkill[slot] = now + (ultimate ? 600 : 60) + random.nextInt(100);
            nextAttack = now + 16;
            return;
        }
        if (random.nextFloat() < 0.08F) {
            request(CombatRequest.HEAVY_START, -1);
            heavyRelease = now + 12 + random.nextInt(14);
            return;
        }
        request(CombatRequest.LIGHT, -1);
        nextAttack = now + 6 + random.nextInt(5);
    }

    private void drink(int level) {
        if (bot.tickCount < nextPotion) {
            return;
        }
        int slot = BotBag.bestPotionSlot(bot, level);
        nextPotion = bot.tickCount + (slot >= 0 && QuickUse.useSlot(bot, slot) ? 100 : 40);
    }

    /** Looks at the middle of {@code entity}, as a player aims. */
    void face(Entity entity) {
        Vec3 eye = bot.getEyePosition();
        Vec3 aim = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
        double dx = aim.x - eye.x;
        double dy = aim.y - eye.y;
        double dz = aim.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYRot(yaw);
        bot.setYHeadRot(yaw);
        bot.setYBodyRot(yaw);
        bot.setXRot(pitch);
    }

    private void request(CombatRequest request, int targetId) {
        ServerContext.current().ifPresent(context -> context.combat().handleRequest(bot, request, ++sequence,
                targetId));
    }
}
