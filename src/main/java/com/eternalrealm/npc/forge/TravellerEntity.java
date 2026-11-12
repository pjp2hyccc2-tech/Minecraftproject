package com.eternalrealm.npc.forge;

import com.eternalrealm.monster.forge.MonsterTags;
import com.eternalrealm.npc.Travellers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * A travelling adventurer (docs/modules/travellers.md) in the world: the body of one of
 * {@link Travellers.Traveller}, an NPC body (so it looks, walks, idles and gestures as the town's people do) that
 * fights. In the field it roams its
 * hunting ground and fights the monsters there (ordinary attacks in its rhythm, its two signature skills by turns,
 * {@link TravellerSkills}); in town it stays near its spot at the item shop (and now and then buys something) or in
 * the inn's lobby. It turns to whoever talks to it. Like every NPC it cannot be hurt or pushed.
 *
 * <p>It is never saved ({@link TravellerDirector} brings it whenever the player comes near where it is).</p>
 */
public class TravellerEntity extends NpcEntity {
    private static final double CHASE_SPEED = 0.65;
    private static final double WALK_SPEED = 0.45;
    /** How far it looks for monsters (blocks). */
    private static final double SIGHT = 16.0;

    private Travellers.Traveller who = Travellers.Traveller.BOLD;
    private Travellers.Place place = Travellers.Place.FIELD;
    private Vec3 anchor = Vec3.ZERO;
    private double roam = 8.0;
    @Nullable
    private LivingEntity enemy;
    private int nextLook;
    private int nextBasic;
    private int nextSkill = 60;
    private int skillTurn;
    private int nextWard = 100;
    private int nextErrand = 300;
    @Nullable
    private Player listener;
    private int listening;

    public TravellerEntity(EntityType<? extends TravellerEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return NpcEntity.createAttributes().add(Attributes.FOLLOW_RANGE, 32.0);
    }

    /** Who it is, where it is and the ground it keeps to (server side). */
    public void setup(Travellers.Traveller traveller, Travellers.Place at, Vec3 center, double radius) {
        this.who = traveller;
        this.place = at;
        this.anchor = center;
        this.roam = radius;
        this.enemy = null;
        this.skillTurn = getRandom().nextInt(2);
        setAppearance(traveller.appearance());
        setCustomName(Component.translatable(traveller.nameKey()));
    }

    public Travellers.Traveller who() {
        return this.who;
    }

    public Travellers.Place place() {
        return this.place;
    }

    public Vec3 anchor() {
        return this.anchor;
    }

    @Nullable
    public LivingEntity enemy() {
        return this.enemy;
    }

    /** Fights {@code target} (tests; in the field it finds its own). */
    public void fight(LivingEntity target) {
        this.enemy = target;
    }

    /** Somebody talks to it: it stops and turns to them for a few seconds (plays its talk gesture). */
    void listenTo(Player player) {
        this.listener = player;
        this.listening = 80;
        getNavigation().stop();
        startTalking(player);
    }

    // ------------------------------------------------------------------------------------------------ goals

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ListenGoal(this));
        this.goalSelector.addGoal(2, new FightGoal(this));
        this.goalSelector.addGoal(5, new RoamGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.03F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (this.listening > 0 && --this.listening == 0) {
            this.listener = null;
        }
        if (this.enemy != null && !alive(this.enemy)) {
            this.enemy = null;
        }
        if (this.who == Travellers.Traveller.SELE && this.tickCount >= this.nextWard) {
            this.nextWard = this.tickCount + 40;
            if (TravellerSkills.ward(this)) {
                this.nextWard = this.tickCount + Travellers.Skill.BUBBLE_WARD.cooldownTicks();
            }
        }
        if (this.place == Travellers.Place.SHOP && this.tickCount >= this.nextErrand && this.listener == null) {
            this.nextErrand = this.tickCount + 400 + getRandom().nextInt(300);
            buySomething();
        }
        if (this.place == Travellers.Place.INN && this.who == Travellers.Traveller.SELE && this.tickCount % 30 == 0) {
            TravellerSkills.notes(this); // she plays at the inn
        }
    }

    private static boolean alive(LivingEntity entity) {
        return entity.isAlive() && !entity.isRemoved();
    }

    /** At the shop: a word with the shopkeeper, the clink of coins. */
    private void buySomething() {
        List<NpcEntity> shopkeepers = level().getEntitiesOfClass(NpcEntity.class, getBoundingBox().inflate(8.0),
                npc -> npc != this && Npcs.roleOf(npc).filter(role -> role == NpcRole.ITEM_SHOP).isPresent());
        if (shopkeepers.isEmpty()) {
            return;
        }
        NpcEntity shopkeeper = shopkeepers.get(0);
        getLookControl().setLookAt(shopkeeper, 30.0F, 30.0F);
        level().broadcastEntityEvent(this, NpcEntity.TALK_EVENT);
        level().broadcastEntityEvent(shopkeeper, NpcEntity.TALK_EVENT);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.35F,
                1.4F + getRandom().nextFloat() * 0.3F);
        TravellerSkills.coins(this, shopkeeper);
    }

    /** The nearest monster on its hunting ground it can see (a monster going for a player close by first). */
    @Nullable
    private LivingEntity lookForEnemy() {
        if (this.place != Travellers.Place.FIELD || !(level() instanceof ServerLevel server)) {
            return null;
        }
        Mob best = null;
        double bestScore = Double.MAX_VALUE;
        AABB around = getBoundingBox().inflate(SIGHT);
        for (Mob mob : server.getEntitiesOfClass(Mob.class, around, mob -> MonsterTags.isMonster(mob) && alive(mob))) {
            if (mob.position().distanceTo(this.anchor) > this.roam + 14.0 || !hasLineOfSight(mob)) {
                continue;
            }
            double score = mob.distanceToSqr(this);
            if (mob.getTarget() instanceof Player) {
                score *= 0.25; // helping a player comes first
            }
            if (score < bestScore) {
                bestScore = score;
                best = mob;
            }
        }
        return best;
    }

    private void fightTick() {
        LivingEntity target = this.enemy;
        if (target == null) {
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = distanceTo(target);
        double range = this.who.range();
        if (distance > range) {
            if (this.tickCount % 10 == 0 || getNavigation().isDone()) {
                getNavigation().moveTo(target, CHASE_SPEED);
            }
        } else {
            getNavigation().stop();
            if (this.who.style() != Travellers.Style.KNIGHT && distance < range * 0.4 && this.tickCount % 20 == 0) {
                Vec3 away = position().subtract(target.position()).normalize().scale(4.0).add(position());
                getNavigation().moveTo(away.x, away.y, away.z, CHASE_SPEED); // a step back to keep its distance
            }
        }
        if (distance > range + 1.0 || !hasLineOfSight(target)) {
            return;
        }
        if (this.tickCount >= this.nextSkill) {
            List<Travellers.Skill> skills = this.who.skills().stream().filter(skill -> !skill.heals()).toList();
            Travellers.Skill skill = skills.get(this.skillTurn++ % skills.size());
            TravellerSkills.cast(this, skill, target);
            this.nextSkill = this.tickCount + skill.cooldownTicks() + getRandom().nextInt(60);
            this.nextBasic = this.tickCount + 30;
        } else if (this.tickCount >= this.nextBasic) {
            TravellerSkills.basic(this, target);
            this.nextBasic = this.tickCount + this.who.basicTicks() + getRandom().nextInt(10);
        }
    }

    // ------------------------------------------------------------------------------------------- interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            TravellerDirector.talk(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ------------------------------------------------------------------------------------------------ goals

    /** Turns to whoever talks to it and stops (not while fighting). */
    private static final class ListenGoal extends Goal {
        private final TravellerEntity traveller;

        ListenGoal(TravellerEntity traveller) {
            this.traveller = traveller;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.traveller.listener != null && this.traveller.enemy == null;
        }

        @Override
        public void start() {
            this.traveller.getNavigation().stop();
        }

        @Override
        public void tick() {
            Player player = this.traveller.listener;
            if (player != null) {
                this.traveller.getLookControl().setLookAt(player, 30.0F, 30.0F);
            }
        }
    }

    /** In the field: finds a monster on its ground and fights it until it falls or gets away. */
    private static final class FightGoal extends Goal {
        private final TravellerEntity traveller;

        FightGoal(TravellerEntity traveller) {
            this.traveller = traveller;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.traveller.enemy != null && alive(this.traveller.enemy)) {
                return true;
            }
            if (this.traveller.tickCount < this.traveller.nextLook) {
                return false;
            }
            this.traveller.nextLook = this.traveller.tickCount + 10;
            this.traveller.enemy = this.traveller.lookForEnemy();
            return this.traveller.enemy != null;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.traveller.enemy;
            return target != null && alive(target) && target.distanceTo(this.traveller) < SIGHT * 1.5;
        }

        @Override
        public void tick() {
            this.traveller.fightTick();
        }

        @Override
        public void stop() {
            this.traveller.enemy = null;
            this.traveller.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /** Between fights: a stroll about its hunting ground, or a step about its spot in town. */
    private static final class RoamGoal extends Goal {
        private final TravellerEntity traveller;

        RoamGoal(TravellerEntity traveller) {
            this.traveller = traveller;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            TravellerEntity w = this.traveller;
            if (w.enemy != null || w.listener != null || w.isTalking()) {
                return false;
            }
            boolean field = w.place == Travellers.Place.FIELD;
            double away = w.position().distanceTo(w.anchor);
            if (away > (field ? w.roam : 2.5)) {
                return true; // back to its ground
            }
            return w.getRandom().nextInt(field ? 90 : 400) == 0;
        }

        @Override
        public void start() {
            TravellerEntity w = this.traveller;
            boolean field = w.place == Travellers.Place.FIELD;
            double radius = field ? w.roam * 0.8 : 1.5;
            double angle = w.getRandom().nextDouble() * Math.PI * 2.0;
            double distance = w.getRandom().nextDouble() * radius;
            Vec3 target = w.anchor.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
            w.getNavigation().moveTo(target.x, target.y, target.z, WALK_SPEED);
        }

        @Override
        public boolean canContinueToUse() {
            return !this.traveller.getNavigation().isDone() && this.traveller.enemy == null
                    && this.traveller.listener == null;
        }
    }
}
