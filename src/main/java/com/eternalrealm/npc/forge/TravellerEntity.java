package com.eternalrealm.npc.forge;

import com.eternalrealm.boss.forge.BossFight;
import com.eternalrealm.monster.MonsterRole;
import com.eternalrealm.monster.forge.MonsterTags;
import com.eternalrealm.npc.HeroBodies;
import com.eternalrealm.npc.Travellers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One of the four legendary travellers in the world (docs/modules/travellers.md): their hero body (synced: who, and
 * whether they are fighting, fallen or guarding), and what they do.
 *
 * <p>On their own round they roam their hunting ground and fight its monsters, or keep to their spot at the item shop
 * or in the inn's lobby. In the player's party they follow the player (at a place of their own around them, stepping
 * to the player's side when left behind or across worlds), fight every monster that comes near the player, each in
 * their role, with their moves ({@link Travellers.Move}: two ordinary attacks, two skills and the ultimate, chosen by
 * {@link Travellers#choose}), each move played by the body and drawn with light ({@link TravellerMoves}); their
 * kills are the player's.</p>
 *
 * <p>Players cannot hurt them; monsters and bosses can, through their toughness. A traveller whose health runs out
 * falls (戦闘不能) and stands up again after {@link Travellers#DOWN_TICKS} (or at once in the sister's light). They are
 * never saved: {@link TravellerDirector} brings them whenever they are needed.</p>
 */
public class TravellerEntity extends NpcEntity {
    private static final EntityDataAccessor<Byte> DATA_HERO =
            SynchedEntityData.defineId(TravellerEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_STATE =
            SynchedEntityData.defineId(TravellerEntity.class, EntityDataSerializers.BYTE);
    /** State bits: fighting (the ready stance), fallen, in the player's party, guarding (the knight's oath). */
    public static final int FIGHTING = 1;
    public static final int DOWN = 2;
    public static final int COMPANION = 4;
    public static final int GUARDING = 8;
    private static final double WALK_SPEED = 0.45;
    private static final double FOLLOW_SPEED = 0.36;
    /** How far a traveller on its round looks for monsters (blocks). */
    private static final double SIGHT = 16.0;
    private static final int LOOK_GAP = 10;

    // --------------------------------------------------------------------- server
    private Travellers.Place place = Travellers.Place.FIELD;
    private Vec3 anchor = Vec3.ZERO;
    private double roam = 8.0;
    @Nullable
    private UUID owner;
    private int slot;
    @Nullable
    private LivingEntity enemy;
    @Nullable
    private TravellerMoves.Run run;
    private final Map<Travellers.Move, Integer> readyAt = new EnumMap<>(Travellers.Move.class);
    private boolean secondAttack;
    private int nextAttack;
    private int nextLook;
    private int nextErrand = 300;
    private int downUntil;
    private int guardUntil;
    private int fightingUntil;
    @Nullable
    private Player listener;
    private int listening;
    private Vec3 lastPos = Vec3.ZERO;
    private int stuck;

    // --------------------------------------------------------------------- client
    /** The move being played (the animation of its slot). */
    public final AnimationState actionAnimation = new AnimationState();
    private int actionSlot = -1;
    private int actionStart;
    private float actionLength;
    private float actionW;
    private float actionWO;
    private float downW;
    private float downWO;
    private float readyW;
    private float readyWO;

    public TravellerEntity(EntityType<? extends TravellerEntity> type, Level level) {
        super(type, level);
        setInvulnerable(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return NpcEntity.createAttributes().add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.MAX_HEALTH, 1000.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_HERO, (byte) 0);
        this.entityData.define(DATA_STATE, (byte) 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_HERO.equals(key)) {
            refreshDimensions();
        }
    }

    // -------------------------------------------------------------------------------------------- identity

    /** Who it is, where it is and the ground it keeps to (server side). */
    public void setup(Travellers.Traveller traveller, Travellers.Place at, Vec3 center, double radius) {
        this.entityData.set(DATA_HERO, (byte) traveller.ordinal());
        this.place = at;
        this.anchor = center;
        this.roam = radius;
        this.enemy = null;
        this.run = null;
        setCustomName(Component.translatable(traveller.nameKey()));
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(traveller.maxHealth());
        }
        setHealth((float) traveller.maxHealth());
        refreshDimensions();
    }

    public Travellers.Traveller who() {
        Travellers.Traveller[] all = Travellers.Traveller.values();
        return all[Math.floorMod(this.entityData.get(DATA_HERO), all.length)];
    }

    /** The hero body ({@link HeroBodies}). */
    public String heroAppearance() {
        return who().appearance();
    }

    @Override
    protected float bodyScale() {
        return HeroBodies.scale(heroAppearance());
    }

    @Override
    protected float talkSeconds() {
        return HeroBodies.length(heroAppearance(), HeroBodies.TALK);
    }

    public Travellers.Place place() {
        return this.place;
    }

    public Vec3 anchor() {
        return this.anchor;
    }

    private boolean state(int bit) {
        return (this.entityData.get(DATA_STATE) & bit) != 0;
    }

    private void setState(int bit, boolean on) {
        byte old = this.entityData.get(DATA_STATE);
        byte now = (byte) (on ? old | bit : old & ~bit);
        if (now != old) {
            this.entityData.set(DATA_STATE, now);
        }
    }

    public boolean isDown() {
        return state(DOWN);
    }

    public boolean isFighting() {
        return state(FIGHTING);
    }

    public boolean isCompanion() {
        return state(COMPANION);
    }

    public boolean isGuarding() {
        return state(GUARDING);
    }

    // ------------------------------------------------------------------------------------------- the party

    /** Joins the party of {@code player} at place {@code index} around them (server side). */
    public void becomeCompanion(ServerPlayer player, int index) {
        this.owner = player.getUUID();
        this.slot = index;
        this.place = Travellers.Place.FIELD;
        this.enemy = null;
        setState(COMPANION, true);
    }

    @Nullable
    public UUID ownerId() {
        return this.owner;
    }

    /** The player whose party it is in, if they are online. */
    @Nullable
    public ServerPlayer owner() {
        if (this.owner == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        return server.getServer().getPlayerList().getPlayer(this.owner);
    }

    public int formationSlot() {
        return this.slot;
    }

    @Nullable
    public LivingEntity enemy() {
        return this.enemy;
    }

    /** Fights {@code target} (tests and taunts; otherwise it finds its own). */
    public void fight(LivingEntity target) {
        this.enemy = target;
    }

    /** Somebody talks to it: it stops and turns to them for a few seconds (plays its talk gesture). */
    void listenTo(Player player) {
        this.listener = player;
        this.listening = 100;
        getNavigation().stop();
        startTalking(player);
    }

    // ---------------------------------------------------------------------------------------------- moves

    /** Whether the cooldown of {@code move} is over. */
    public boolean ready(Travellers.Move move) {
        return this.tickCount >= this.readyAt.getOrDefault(move, 0);
    }

    /** Lets every move be used at once (tests, a fresh arrival). */
    public void refreshMoves() {
        this.readyAt.clear();
    }

    @Nullable
    public TravellerMoves.Run running() {
        return this.run;
    }

    /** Starts {@code move} on {@code target} (null for the sanctuary): the body plays it, the blows land on time. */
    public void perform(Travellers.Move move, @Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel) || isDown()) {
            return;
        }
        this.readyAt.put(move, this.tickCount + move.cooldownTicks());
        if (move.kind() == Travellers.Kind.ATTACK) {
            this.secondAttack = !this.secondAttack;
        }
        getNavigation().stop();
        Vec3 aim = target != null ? target.position() : position();
        if (target != null) {
            face(target.position());
        }
        this.run = new TravellerMoves.Run(move, target, aim, position(), this.tickCount, move.hitTicks(),
                getRandom().nextInt());
        this.fightingUntil = this.tickCount + 100;
        TravellerMoves.begin(this, this.run);
    }

    /** The gesture of joining a party (and of a won fight). */
    public void cheer() {
        if (level() instanceof ServerLevel && !isDown() && this.run == null) {
            TravellerMoves.cheer(this);
        }
    }

    void face(Vec3 at) {
        double dx = at.x - getX();
        double dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        this.yRotO = yaw;
    }

    /** The oath: every enemy around turns on him and his guard holds for {@code ticks}. */
    void guard(int ticks) {
        this.guardUntil = this.tickCount + ticks;
        setState(GUARDING, true);
    }

    // ------------------------------------------------------------------------------------------- falling

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return livingHurt(source, amount);
        }
        if (isDown()) {
            return false;
        }
        Entity attacker = source.getEntity();
        double blow;
        if (MonsterTags.isMonster(attacker)) {
            blow = MonsterTags.damage(attacker) * (0.9 + getRandom().nextDouble() * 0.2);
        } else if (attacker != null && attacker.getTags().contains(BossFight.TAG)) {
            blow = amount;
        } else {
            return false; // players, falls, fire, the void of the world: none of it touches a legend
        }
        double taken = Travellers.damageTaken(who(), blow, isGuarding());
        this.fightingUntil = this.tickCount + 100;
        if (attacker instanceof LivingEntity living && this.enemy == null && living.isAlive()) {
            this.enemy = living;
        }
        if (getHealth() - taken < 1.0) {
            fall();
            return true;
        }
        return livingHurt(source, (float) taken);
    }

    /** Health runs out (or a test knocks it down): it falls (戦闘不能) where it stands. */
    public void fall() {
        setHealth(1.0F);
        this.run = null;
        this.enemy = null;
        this.downUntil = this.tickCount + Travellers.DOWN_TICKS;
        getNavigation().stop();
        setState(DOWN, true);
        setState(FIGHTING, false);
        TravellerDirector.say(this, Travellers.lineKey(who(), "down"));
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_HURT, SoundSource.NEUTRAL, 0.8F, 0.8F);
    }

    /** Stands up again ({@code full}: raised by the sister's light, at full health). */
    public void standUp(boolean full) {
        if (!isDown()) {
            if (full) {
                setHealth(getMaxHealth());
            }
            return;
        }
        setState(DOWN, false);
        setHealth((float) (getMaxHealth() * (full ? 1.0 : Travellers.STAND_UP_HEALTH)));
        TravellerMoves.standUp(this, full);
        TravellerDirector.say(this, Travellers.lineKey(who(), "up"));
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !isDown() && super.canBeSeenAsEnemy();
    }

    // ---------------------------------------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (this.listening > 0 && --this.listening == 0) {
            this.listener = null;
        }
        if (isDown()) {
            if (this.tickCount >= this.downUntil) {
                standUp(false);
            }
            return;
        }
        if (this.enemy != null && (!alive(this.enemy) || this.enemy.level() != level())) {
            this.enemy = null;
        }
        if (isGuarding() && this.tickCount >= this.guardUntil) {
            setState(GUARDING, false);
        }
        if (this.run != null) {
            TravellerMoves.Run current = this.run;
            int age = this.tickCount - current.start();
            if (current.target() != null && current.target().isAlive()) {
                face(current.target().position());
            }
            while (current.nextHit() < current.hits().length && age >= current.hits()[current.nextHit()]) {
                TravellerMoves.hit(this, current, current.nextHit());
                current.advance();
            }
            if (age >= current.move().lengthTicks()) {
                this.run = null;
                this.nextAttack = this.tickCount + who().attackGap() + getRandom().nextInt(6);
            }
        } else if (who() == Travellers.Traveller.MARIEL && this.tickCount % 10 == 0) {
            TravellerMoves.tend(this); // she heals and raises whoever needs it, in a fight or not
        }
        boolean fighting = this.run != null || this.enemy != null || this.tickCount < this.fightingUntil;
        setState(FIGHTING, fighting);
        if (!fighting && this.tickCount % 20 == 0 && getHealth() < getMaxHealth()) {
            heal((float) (getMaxHealth() * Travellers.REST_REGEN));
        }
        if (!isCompanion()) {
            roundTick();
        }
    }

    private void roundTick() {
        if (this.place == Travellers.Place.SHOP && this.tickCount >= this.nextErrand && this.listener == null) {
            this.nextErrand = this.tickCount + 400 + getRandom().nextInt(300);
            buySomething();
        }
    }

    static boolean alive(LivingEntity entity) {
        return entity.isAlive() && !entity.isRemoved() && !(entity instanceof TravellerEntity t && t.isDown());
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
        TravellerMoves.coins(this, shopkeeper);
    }

    // -------------------------------------------------------------------------------------------- enemies

    /** Monsters and bosses (never players, never NPCs). */
    static boolean isFoe(Entity entity) {
        return entity instanceof Mob mob && mob.isAlive()
                && (MonsterTags.isMonster(mob) || mob.getTags().contains(BossFight.TAG));
    }

    static boolean isBig(LivingEntity entity) {
        return entity.getTags().contains(BossFight.TAG) || MonsterTags.template(entity)
                .map(template -> template.role() == MonsterRole.ELITE).orElse(false);
    }

    /** The enemy to fight now: near the player for a companion, on its hunting ground otherwise. */
    @Nullable
    private LivingEntity lookForEnemy() {
        if (!(level() instanceof ServerLevel server)) {
            return null;
        }
        ServerPlayer player = owner();
        if (isCompanion()) {
            if (player == null || player.level() != level() || player.distanceTo(this) > Travellers.LEASH) {
                return null;
            }
        } else if (this.place != Travellers.Place.FIELD) {
            return null;
        }
        Vec3 center = isCompanion() ? player.position() : position();
        double radius = isCompanion() ? Travellers.GUARD_RADIUS : SIGHT;
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Mob mob : server.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(radius, 6.0, radius),
                TravellerEntity::isFoe)) {
            if (!isCompanion() && mob.position().distanceTo(this.anchor) > this.roam + 14.0) {
                continue;
            }
            if (!hasLineOfSight(mob) && (player == null || !player.hasLineOfSight(mob))) {
                continue;
            }
            double score = mob.position().distanceToSqr(center);
            LivingEntity target = mob.getTarget();
            if (target instanceof Player) {
                score *= 0.35; // whatever goes for the player first
            } else if (target instanceof TravellerEntity && target != this) {
                score *= who().role() == Travellers.Role.TANK ? 0.3 : 0.7;
            }
            if (player != null && player.getLastHurtMob() == mob) {
                score *= 0.5;
            }
            if (score < bestScore) {
                bestScore = score;
                best = mob;
            }
        }
        return best;
    }

    /** What it sees when choosing a move. */
    Travellers.Situation situation(LivingEntity target) {
        double distance = distanceTo(target);
        int near = 0;
        int others = 0;
        AABB around = target.getBoundingBox().inflate(5.0, 3.0, 5.0);
        for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(12.0, 4.0, 12.0),
                TravellerEntity::isFoe)) {
            if (mob.getBoundingBox().intersects(around)) {
                near++;
            }
            if (mob.getTarget() != null && mob.getTarget() != this) {
                others++;
            }
        }
        double worst = 1.0;
        int hurt = 0;
        boolean down = false;
        for (LivingEntity ally : allies()) {
            double share = ally.getHealth() / Math.max(1.0F, ally.getMaxHealth());
            if (ally instanceof TravellerEntity t && t.isDown()) {
                down = true;
                continue;
            }
            if (ally instanceof ServerPlayer p && com.eternalrealm.revival.forge.RevivalService.isDown(p)) {
                down = true;
                continue;
            }
            worst = Math.min(worst, share);
            if (share < 0.7) {
                hurt++;
            }
        }
        return new Travellers.Situation(distance, near, isBig(target), worst, hurt, down, others);
    }

    /** The party it fights for: the player and the other companions (itself included); itself alone otherwise. */
    List<LivingEntity> allies() {
        List<LivingEntity> out = new ArrayList<>();
        ServerPlayer player = owner();
        if (isCompanion() && player != null && player.level() == level() && player.isAlive()
                && !player.isSpectator()) {
            out.add(player);
        }
        if (isCompanion()) {
            out.addAll(level().getEntitiesOfClass(TravellerEntity.class, getBoundingBox().inflate(32.0),
                    t -> t.isCompanion() && this.owner != null && this.owner.equals(t.owner)));
        } else {
            out.add(this);
        }
        return out;
    }

    private void fightTick() {
        LivingEntity target = this.enemy;
        if (target == null || this.run != null) {
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = distanceTo(target);
        Travellers.Traveller me = who();
        boolean melee = me.role() == Travellers.Role.TANK || me.role() == Travellers.Role.BLADE;
        double range = me.range() + target.getBbWidth() * 0.5;
        ServerPlayer player = owner();
        if (distance > range) {
            if (this.tickCount % 8 == 0 || getNavigation().isDone()) {
                getNavigation().moveTo(target, me.speed());
            }
        } else if (!melee && distance < range * 0.45) {
            if (this.tickCount % 20 == 0) {
                Vec3 away = position().subtract(target.position()).normalize().scale(4.0).add(position());
                if (player != null && away.distanceTo(player.position()) > 10.0) {
                    away = player.position();
                }
                getNavigation().moveTo(away.x, away.y, away.z, me.speed());
            }
        } else {
            getNavigation().stop();
        }
        if (this.tickCount < this.nextAttack || !hasLineOfSight(target)) {
            return;
        }
        Travellers.choose(me, situation(target), this::ready, this.secondAttack)
                .ifPresent(move -> perform(move, move.heals() ? null : target));
    }

    // ------------------------------------------------------------------------------------------- following

    /** Where it should be: its place around the player. */
    Vec3 formationSpot(ServerPlayer player) {
        double[] offset = Travellers.formation(this.slot);
        double yaw = Math.toRadians(player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        // side to the player's right (+), back behind them (+)
        double x = player.getX() - cos * offset[0] + sin * offset[1];
        double z = player.getZ() - sin * offset[0] - cos * offset[1];
        return new Vec3(x, player.getY(), z);
    }

    /** Steps to the player's side (left behind, stuck, or the player flew off). @return whether it could */
    boolean catchUp(ServerPlayer player) {
        if (!(level() instanceof ServerLevel server)) {
            return false;
        }
        Optional<Vec3> spot = TravellerDirector.standable(server, formationSpot(player))
                .or(() -> TravellerDirector.standable(server, player.position()));
        if (spot.isEmpty()) {
            return false;
        }
        TravellerMoves.trip(this, false);
        Vec3 at = spot.get();
        getNavigation().stop();
        moveTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
        TravellerMoves.trip(this, true);
        this.stuck = 0;
        return true;
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

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ListenGoal(this));
        this.goalSelector.addGoal(2, new FightGoal(this));
        this.goalSelector.addGoal(3, new FollowGoal(this));
        this.goalSelector.addGoal(5, new RoamGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.03F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** Turns to whoever talks to it and stops (not while fighting). */
    private static final class ListenGoal extends Goal {
        private final TravellerEntity traveller;

        ListenGoal(TravellerEntity traveller) {
            this.traveller = traveller;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.traveller.listener != null && this.traveller.enemy == null && this.traveller.run == null
                    && !this.traveller.isDown();
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

    /** Finds an enemy and fights it until it falls, gets away, or (in a party) the player moves on. */
    private static final class FightGoal extends Goal {
        private final TravellerEntity traveller;

        FightGoal(TravellerEntity traveller) {
            this.traveller = traveller;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            TravellerEntity t = this.traveller;
            if (t.isDown()) {
                return false;
            }
            if (t.enemy != null && alive(t.enemy)) {
                return true;
            }
            if (t.tickCount < t.nextLook) {
                return false;
            }
            t.nextLook = t.tickCount + LOOK_GAP;
            t.enemy = t.lookForEnemy();
            return t.enemy != null;
        }

        @Override
        public boolean canContinueToUse() {
            TravellerEntity t = this.traveller;
            LivingEntity target = t.enemy;
            if (t.isDown() || target == null || !alive(target) || target.level() != t.level()) {
                return false;
            }
            ServerPlayer player = t.owner();
            if (t.isCompanion()) {
                return player != null && player.level() == t.level() && player.distanceTo(t) < Travellers.LEASH
                        && target.distanceTo(player) < Travellers.GUARD_RADIUS + 8.0;
            }
            return target.distanceTo(t) < SIGHT * 1.5;
        }

        @Override
        public void tick() {
            this.traveller.fightTick();
        }

        @Override
        public void stop() {
            if (this.traveller.run == null) {
                this.traveller.enemy = null;
            }
            this.traveller.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /** In the party, between fights: keeps to its place around the player; steps over when left behind. */
    private static final class FollowGoal extends Goal {
        private final TravellerEntity traveller;

        FollowGoal(TravellerEntity traveller) {
            this.traveller = traveller;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            TravellerEntity t = this.traveller;
            return t.isCompanion() && !t.isDown() && t.enemy == null && t.run == null && t.listener == null
                    && t.owner() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            TravellerEntity t = this.traveller;
            ServerPlayer player = t.owner();
            if (player == null || player.level() != t.level()) {
                return;
            }
            double distance = t.distanceTo(player);
            if (distance > Travellers.CATCH_UP || t.stuck > 60) {
                t.catchUp(player);
                return;
            }
            Vec3 spot = t.formationSpot(player);
            double away = t.position().distanceTo(spot);
            if (away > 1.6) {
                if (t.tickCount % 5 == 0 || t.getNavigation().isDone()) {
                    double speed = distance > 7.0 ? t.who().speed() * 1.15 : FOLLOW_SPEED;
                    t.getNavigation().moveTo(spot.x, spot.y, spot.z, speed);
                }
                t.stuck = t.position().distanceToSqr(t.lastPos) < 0.0025 && away > 3.0 ? t.stuck + 1 : 0;
            } else {
                t.getNavigation().stop();
                t.stuck = 0;
                if (t.tickCount % 40 == 0) {
                    t.getLookControl().setLookAt(player, 20.0F, 20.0F);
                }
            }
            t.lastPos = t.position();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /** On its round, between fights: a stroll about its hunting ground, or a step about its spot in town. */
    private static final class RoamGoal extends Goal {
        private final TravellerEntity traveller;

        RoamGoal(TravellerEntity traveller) {
            this.traveller = traveller;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            TravellerEntity w = this.traveller;
            if (w.isCompanion() || w.isDown() || w.enemy != null || w.listener != null || w.isTalking()
                    || w.run != null) {
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
            if (field && w.level() instanceof ServerLevel level) {
                BlockPos column = BlockPos.containing(target);
                if (level.hasChunkAt(column)) {
                    target = new Vec3(target.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            column.getX(), column.getZ()), target.z);
                }
            }
            w.getNavigation().moveTo(target.x, target.y, target.z, WALK_SPEED);
        }

        @Override
        public boolean canContinueToUse() {
            TravellerEntity w = this.traveller;
            return !w.getNavigation().isDone() && w.enemy == null && w.listener == null && w.run == null
                    && !w.isCompanion();
        }
    }

    // ---------------------------------------------------------------------------------------------- client

    /** Client: a move (or the joining gesture) starts: the body plays the animation of {@code slot}. */
    public void startAction(int slotIndex) {
        this.actionSlot = slotIndex;
        this.actionStart = this.tickCount;
        this.actionLength = HeroBodies.length(heroAppearance(), slotIndex);
        this.actionAnimation.start(this.tickCount);
    }

    /** Client: the slot of the move being played, or -1. */
    public int actionSlot() {
        return this.actionAnimation.isStarted() ? this.actionSlot : -1;
    }

    private void clientTick() {
        if (!this.idleAnimation.isStarted()) {
            this.idleAnimation.start(this.tickCount - Math.floorMod(getId() * 37, 120));
        }
        boolean acting = false;
        if (this.actionAnimation.isStarted()) {
            float elapsed = (this.tickCount - this.actionStart) / 20.0F;
            if (elapsed >= this.actionLength) {
                this.actionAnimation.stop();
            } else {
                acting = true;
            }
        }
        this.actionWO = this.actionW;
        this.actionW = acting ? Math.min(1.0F, this.actionW + 0.5F) : Math.max(0.0F, this.actionW - 0.25F);
        this.downWO = this.downW;
        this.downW = isDown() ? Math.min(1.0F, this.downW + 0.12F) : Math.max(0.0F, this.downW - 0.12F);
        this.readyWO = this.readyW;
        this.readyW = isFighting() ? Math.min(1.0F, this.readyW + 0.1F) : Math.max(0.0F, this.readyW - 0.05F);
    }

    /** Client: weights (0..1) of the move, the fallen pose and the fighting stance at {@code ageInTicks}. */
    public float actionWeight(float ageInTicks) {
        return lerp(ageInTicks, this.actionWO, this.actionW);
    }

    public float downWeight(float ageInTicks) {
        return lerp(ageInTicks, this.downWO, this.downW);
    }

    public float readyWeight(float ageInTicks) {
        return lerp(ageInTicks, this.readyWO, this.readyW);
    }

    private float lerp(float ageInTicks, float old, float now) {
        return Mth.lerp(Mth.clamp(ageInTicks - this.tickCount, 0.0F, 1.0F), old, now);
    }
}
