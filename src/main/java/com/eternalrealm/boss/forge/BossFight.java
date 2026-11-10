package com.eternalrealm.boss.forge;

import com.eternalrealm.boss.AbilityKind;
import com.eternalrealm.boss.AbilityLook;
import com.eternalrealm.boss.BossActions;
import com.eternalrealm.boss.BossAbility;
import com.eternalrealm.boss.BossBrain;
import com.eternalrealm.boss.BossCommand;
import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.BossMarkers;
import com.eternalrealm.boss.BossPosition;
import com.eternalrealm.boss.BossRules;
import com.eternalrealm.boss.BossShape;
import com.eternalrealm.boss.BossTexts;
import com.eternalrealm.boss.Contributions;
import com.eternalrealm.boss.DefeatSequence;
import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.boss.Point;
import com.eternalrealm.boss.TargetSnapshot;
import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.Telegraph;
import com.eternalrealm.combat.forge.ERParticles;
import com.eternalrealm.combat.forge.Telegraphs;
import com.eternalrealm.entity.BossAnimation;
import com.eternalrealm.entity.ERBoss;
import com.eternalrealm.entity.RigData;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.monster.forge.MonsterFactory;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One boss fight: the boss module's {@link BossBrain} drives a body in the arena. Health is the fight's own pool (boss
 * health exceeds vanilla's attribute cap): hits on the body are absorbed into it, threat is collected per player
 * (job threat multipliers). Every attack area is shown as a ground marker ({@link Telegraphs}) for exactly as long as
 * it charges and hits exactly the players inside it when it fires, through the normal defense pipeline with the
 * dodge/guard rules of its kind ({@link BossDamage}); safe zones are green markers, pushes and pulls move the
 * players, hazards linger, charging bosses dash, and {@link BossVfx} adds themed particles, props and lightning.
 *
 * <p>Later forms start with a transformation (new look, aura, roar, shockwave, camera shake, title, bark). When the
 * health pool is empty the boss does not just disappear: it freezes, becomes invulnerable and plays its
 * {@link DefeatSequence} (collapse, flashes, bursts, a shockwave, a pillar of light, dissolving), and only then is the
 * body removed and the defeat callback runs (rewards, story, music). The killing blow itself is reported at once
 * through {@code onKilled}, so time limits and records use the real time of the kill.</p>
 */
public final class BossFight {
    public static final String TAG = "eternalrealm_boss";
    /** The boss HUD of the participants (layered health frame, cast bar, merit ranking). */
    public static final String HUD_CHANNEL = "boss_hud";
    private static final int HUD_INTERVAL = 4;
    private static final int RANKING_ROWS = 5;
    static final String FIGHT = "er_fight";
    /** Half size of the arena floor when the caller does not know it (tests). */
    public static final double DEFAULT_ARENA_HALF_SIZE = 14.5;
    /** Players this close to the boss take part in the fight (at least; bigger arenas reach their corners). */
    private static final double ARENA_RANGE = 48.0;
    /** Height above the floor at which projectiles fly once they have come down from the boss's hand or mouth. */
    private static final double BOLT_CRUISE_HEIGHT = 1.1;
    private static final SplittableRandom RANDOM = new SplittableRandom();

    /** A charging attack area (or safe spot); {@code markerId} is its ground marker on the clients. */
    private record Marker(BossAbility ability, BossShape shape, long fireTick, int markerId, boolean safe) {
    }

    /** A lasting zone that hurts everyone inside every {@code interval} ticks until {@code until}. */
    private static final class HazardZone {
        final BossAbility ability;
        final BossShape shape;
        final long until;
        final int interval;
        final double damage;
        final int markerId;
        long nextPulse;

        HazardZone(BossAbility ability, BossShape shape, long until, int interval, long nextPulse, double damage,
                   int markerId) {
            this.ability = ability;
            this.shape = shape;
            this.until = until;
            this.interval = interval;
            this.nextPulse = nextPulse;
            this.damage = damage;
            this.markerId = markerId;
        }
    }

    /** A charge in progress: the body moves from {@code from} to {@code to} over {@link BossRules#DASH_TICKS}. */
    private record DashState(Vec3 from, Vec3 to, long start) {
    }

    private record Scheduled(long tick, String owner, Runnable action) {
    }

    /**
     * A projectile: it leaves the boss's hand or mouth (high up on a big boss), comes down along its path to
     * {@code cruiseY} (chest height over the floor) and flies on level from there.
     */
    private static final class Bolt {
        Vec3 position;
        final Vec3 step;
        final double cruiseY;
        final double range;
        double travelled;
        final double damage;
        final AbilityKind kind;
        final ParticleOptions particle;

        Bolt(Vec3 position, Vec3 step, double cruiseY, double range, double damage, AbilityKind kind,
             ParticleOptions particle) {
            this.position = position;
            this.step = step;
            this.cruiseY = cruiseY;
            this.range = range;
            this.damage = damage;
            this.kind = kind;
            this.particle = particle;
        }
    }

    private final UUID id = UUID.randomUUID();
    private final ServerLevel level;
    private final Mob body;
    private final BossBrain brain;
    private final ServerBossEvent bar;
    private final Set<UUID> participants;
    private final Map<UUID, Double> threat = new HashMap<>();
    private final List<Marker> markers = new ArrayList<>();
    private final List<HazardZone> hazards = new ArrayList<>();
    private final List<Bolt> bolts = new ArrayList<>();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final Set<UUID> shieldAdds = new HashSet<>();
    private final Set<UUID> adds = new HashSet<>();
    private final Map<String, AbilityLook> looks = new HashMap<>();
    private final Contributions contributions = new Contributions();
    private final BossProps props;
    private final BossVfx vfx;
    private final Consumer<BossFight> onKilled;
    private final Consumer<BossFight> onDefeated;
    private final double maxHealth;
    private final double arenaRadius;
    private final double arenaRange;
    private final double baseSpeed;
    private double health;
    private boolean shielded;
    private boolean executing;
    private boolean defeated;
    private boolean defeatPending;
    private boolean dying;
    private boolean ended;
    private long killTick = -1;
    private DefeatSequence sequence;
    private DashState dash;
    /** Tick at which the last charge of the current charge ability was aimed (-1: none yet). */
    private long chargeAimedAt = -1;
    private long now;
    private java.util.function.Supplier<List<ServerPlayer>> targetOverride;

    private BossFight(ServerLevel level, Mob body, BossBrain brain, Set<UUID> participants, double arenaHalfSize,
                      Consumer<BossFight> onKilled, Consumer<BossFight> onDefeated) {
        this.level = level;
        this.body = body;
        this.brain = brain;
        this.participants = Set.copyOf(participants);
        this.onKilled = onKilled;
        this.onDefeated = onDefeated;
        this.maxHealth = brain.maxHealth();
        this.health = maxHealth;
        this.arenaRadius = arenaHalfSize * 1.45;
        this.arenaRange = Math.max(ARENA_RANGE, arenaHalfSize * 1.5 + 8.0);
        AttributeInstance speed = body.getAttribute(Attributes.MOVEMENT_SPEED);
        this.baseSpeed = speed == null ? 0.0 : speed.getBaseValue() / Math.max(0.1, brain.movementSpeedMultiplier());
        this.props = new BossProps(level);
        this.vfx = new BossVfx(level, body, brain.definition().id(), props, this::later);
        this.bar = new ServerBossEvent(body.getDisplayName(), BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.NOTCHED_10);
        this.bar.setDarkenScreen(true);
        this.bar.setVisible(false); // the boss HUD replaces the vanilla bar
    }

    /** Spawns the boss at {@code spawn} in an arena of unknown size and starts the fight. */
    public static Optional<BossFight> start(ServerLevel level, BossDefinition definition, Difficulty difficulty,
                                            BlockPos spawn, Set<UUID> participants, Consumer<BossFight> onDefeated) {
        return start(level, definition, difficulty, spawn, Vec3.atBottomCenterOf(spawn), DEFAULT_ARENA_HALF_SIZE,
                participants, fight -> {
                }, onDefeated);
    }

    /**
     * Spawns the boss at {@code spawn} and starts the fight. Empty if no body could be built.
     *
     * @param arenaCenter   centre of the arena floor (feet height)
     * @param arenaHalfSize half the width of the (square) arena floor
     * @param onKilled      the health pool reached 0 (the killing blow); the collapse begins
     * @param onDefeated    the collapse is over and the body gone: rewards, story, music
     */
    public static Optional<BossFight> start(ServerLevel level, BossDefinition definition, Difficulty difficulty,
                                            BlockPos spawn, Vec3 arenaCenter, double arenaHalfSize,
                                            Set<UUID> participants, Consumer<BossFight> onKilled,
                                            Consumer<BossFight> onDefeated) {
        BossBrain brain = new BossBrain(definition, Math.max(1, participants.size()), difficulty, RANDOM);
        double half = Mth.clamp(arenaHalfSize, 4.0, 64.0);
        brain.setArena(new Point(arenaCenter.x, arenaCenter.y, arenaCenter.z), half);
        Optional<Mob> body = BossBodies.create(level, definition, brain);
        if (body.isEmpty()) {
            return Optional.empty();
        }
        BossFight fight = new BossFight(level, body.get(), brain, participants, half, onKilled, onDefeated);
        body.get().getPersistentData().putUUID(FIGHT, fight.id);
        body.get().moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 180.0F, 0.0F);
        level.addFreshEntity(body.get());
        for (UUID member : participants) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (player != null) {
                title(player, body.get().getDisplayName().copy().withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        Component.translatable(difficulty.translationKey()).withStyle(ChatFormatting.GRAY), 50);
            }
        }
        level.playSound(null, spawn, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, 0.9F);
        return Optional.of(fight);
    }

    public UUID id() {
        return id;
    }

    public Mob body() {
        return body;
    }

    public BossBrain brain() {
        return brain;
    }

    public String bossId() {
        return brain.definition().id();
    }

    public Set<UUID> participants() {
        return participants;
    }

    /** The boss lost: its health pool reached 0 (it may still be collapsing, see {@link #isDying()}). */
    public boolean isDefeated() {
        return defeated || defeatPending || dying;
    }

    /** The defeat sequence plays: the body is still there, frozen and invulnerable. */
    public boolean isDying() {
        return dying || defeatPending;
    }

    /** Tick of the killing blow (-1 before). */
    public long killTick() {
        return killTick;
    }

    /** The body is being removed by the fight itself (its own damage must go through). */
    public boolean isKilling() {
        return defeated;
    }

    public boolean isOver() {
        return defeated || !body.isAlive() || body.isRemoved();
    }

    /** Whether hits on the body are ignored now (phase transition, shield, collapse). */
    public boolean isInvulnerable() {
        return shielded || dying || brain.isInvulnerable(now);
    }

    /** Damage of a vanilla melee hit of the body (abilities bring their own damage). */
    public OptionalDouble meleeDamage(LivingEntity victim) {
        return executing || dying ? OptionalDouble.empty() : OptionalDouble.of(brain.meleeDamage(now));
    }

    /** A hit on the body: taken from the fight's health pool, the attacker gains threat. */
    public void absorb(Entity attacker, double amount) {
        if (defeated || dying || defeatPending || !(amount > 0.0)) {
            return;
        }
        double dealt = Math.min(health, amount);
        health = Math.max(0.0, health - amount);
        ServerPlayer hitter = attacker instanceof ServerPlayer player && participants.contains(player.getUUID())
                ? player : null;
        if (hitter != null) {
            threat.merge(hitter.getUUID(), amount * threatMultiplier(hitter), Double::sum);
            contributions.damage(hitter.getUUID(), hitter.getGameProfile().getName(), jobOf(hitter), dealt);
        }
        bar.setProgress((float) (health / maxHealth));
        if (health <= 0.0) {
            defeatPending = true; // the collapse starts on the next tick, outside the damage event
            killTick = now;
            if (hitter != null) {
                contributions.lastHit(hitter.getUUID());
            }
        }
    }

    private static double threatMultiplier(ServerPlayer player) {
        JobType job = ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(CharacterSnapshot::jobId).map(JobType::fromStored).orElse(JobType.NONE);
        return job.isChosen() ? Jobs.get(job).threatMultiplier() : 1.0;
    }

    private static String jobOf(ServerPlayer player) {
        return ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(CharacterSnapshot::jobId).map(JobType::fromStored).map(JobType::id).orElse("none");
    }

    /** Who did what so far (merit ranking, the hero of the fight). */
    public Contributions contributions() {
        return contributions;
    }

    /** A participant took {@code amount} damage during the fight (damage ranking, merit score). */
    public void recordTaken(ServerPlayer player, double amount) {
        if (participants.contains(player.getUUID()) && !isDefeated()) {
            contributions.taken(player.getUUID(), player.getGameProfile().getName(), jobOf(player), amount);
        }
    }

    /** A participant healed allies for {@code amount} during the fight. */
    public void recordHealing(ServerPlayer healer, double amount) {
        if (participants.contains(healer.getUUID()) && !isDefeated()) {
            contributions.healing(healer.getUUID(), healer.getGameProfile().getName(), jobOf(healer), amount);
        }
    }

    /** A participant fell (戦闘不能). */
    public void recordFall(ServerPlayer player) {
        if (participants.contains(player.getUUID()) && !isDefeated()) {
            contributions.fell(player.getUUID(), player.getGameProfile().getName(), jobOf(player));
        }
    }

    /** One of the summoned adds died (or vanished). */
    public void onAddGone(UUID entityId) {
        adds.remove(entityId);
        if (shieldAdds.remove(entityId)) {
            brain.onAddKilled();
        }
    }

    // ------------------------------------------------------------------ tick

    public void tick(long tick) {
        now = tick;
        runScheduled();
        props.tick(now);
        if (defeated || ended) {
            return;
        }
        if (now % HUD_INTERVAL == 0) {
            sendHud();
        }
        if (dying) {
            tickDefeat();
            return;
        }
        if (defeatPending) {
            beginDefeat();
            return;
        }
        if (!body.isAlive() || body.isRemoved()) {
            end();
            return;
        }
        List<ServerPlayer> targets = targets();
        syncBar(targets);
        List<TargetSnapshot> snapshots = new ArrayList<>(targets.size());
        for (ServerPlayer player : targets) {
            snapshots.add(new TargetSnapshot(player.getUUID(), player.getX(), player.getY(), player.getZ(),
                    1.0 + threat.getOrDefault(player.getUUID(), 0.0)));
        }
        BossPosition self = new BossPosition(body.getX(), body.getY(), body.getZ(), body.getYRot());
        for (BossCommand command : brain.tick(now, health / maxHealth, self, snapshots)) {
            handle(command, targets);
        }
        ERBoss boss = animated();
        if (brain.currentAbility().isPresent() || brain.isTransforming(now) || dash != null) {
            body.getNavigation().stop();
            if (boss != null) {
                boss.setAiFrozen(true);
            }
        } else {
            if (boss != null) {
                boss.setAiFrozen(false);
            }
            if (tick % 20 == 0) {
                topThreat(targets).ifPresent(body::setTarget);
            }
        }
        stepDash();
        expireMarkers();
        tickHazards(targets);
        moveBolts(targets);
    }

    /**
     * GameTests: fight these players instead of the participants (fake players are not in the player list and cannot
     * be hurt, but everything else runs for real).
     */
    public void overrideTargets(java.util.function.Supplier<List<ServerPlayer>> targets) {
        this.targetOverride = targets;
    }

    /** Props (block displays) of the fight alive right now. */
    public int propCount() {
        return props.count();
    }

    private List<ServerPlayer> targets() {
        if (targetOverride != null) {
            return new ArrayList<>(targetOverride.get());
        }
        List<ServerPlayer> targets = new ArrayList<>();
        for (UUID member : participants) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (player != null && player.isAlive() && !player.isSpectator() && player.level() == level
                    && !com.eternalrealm.revival.forge.RevivalService.isDown(member)
                    && player.distanceToSqr(body) < arenaRange * arenaRange) {
                targets.add(player);
            }
        }
        return targets;
    }

    /** Participants in this level, wherever they stand (screen effects, titles). */
    private List<ServerPlayer> audience() {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID member : participants) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (player != null && player.level() == level) {
                players.add(player);
            }
        }
        return players;
    }

    private Optional<ServerPlayer> topThreat(List<ServerPlayer> targets) {
        ServerPlayer best = null;
        double bestThreat = -1.0;
        for (ServerPlayer player : targets) {
            double value = threat.getOrDefault(player.getUUID(), 0.0);
            if (value > bestThreat) {
                best = player;
                bestThreat = value;
            }
        }
        return Optional.ofNullable(best);
    }

    private void syncBar(List<ServerPlayer> targets) {
        Set<ServerPlayer> shown = new HashSet<>(bar.getPlayers());
        for (ServerPlayer player : targets) {
            if (!shown.remove(player)) {
                bar.addPlayer(player);
            }
        }
        for (ServerPlayer gone : shown) {
            bar.removePlayer(gone);
        }
    }

    private void later(int delay, String owner, Runnable action) {
        scheduled.add(new Scheduled(now + Math.max(0, delay), owner, action));
    }

    private void runScheduled() {
        if (scheduled.isEmpty()) {
            return;
        }
        List<Scheduled> due = new ArrayList<>();
        Iterator<Scheduled> iterator = scheduled.iterator();
        while (iterator.hasNext()) {
            Scheduled entry = iterator.next();
            if (entry.tick() <= now) {
                due.add(entry);
                iterator.remove();
            }
        }
        for (Scheduled entry : due) {
            entry.action().run();
        }
    }

    // ------------------------------------------------------------------ animated body

    private ERBoss animated() {
        return body instanceof ERBoss boss ? boss : null;
    }

    private static BossAnimation animationFor(AbilityKind kind) {
        String action = BossActions.actionFor(kind);
        for (BossAnimation animation : BossAnimation.values()) {
            if (animation.id().equals(action)) {
                return animation;
            }
        }
        return BossAnimation.CAST;
    }

    /**
     * Plays the body's action for {@code ability} so that its impact frame lands on the hit {@code ticksToHit} from
     * now ({@link BossActions}); until then the body holds its telegraph roar. A cancelled ability never starts it.
     */
    private void startAction(ERBoss boss, BossAbility ability, int ticksToHit) {
        BossAnimation animation = animationFor(ability.kind());
        if (animation == BossAnimation.ROAR || BossActions.channelled(ability.kind())) {
            boss.playAnimation(animation);
            return;
        }
        int delay = Math.max(0, ticksToHit - boss.impactTicks(animation));
        if (delay == 0) {
            boss.playAnimation(animation);
        } else {
            later(delay, ability.id(), () -> {
                if (!dying && brain.currentAbility().map(ability::equals).orElse(false)) {
                    boss.playAnimation(animation);
                }
            });
        }
    }

    private AbilityLook look(BossAbility ability) {
        return looks.computeIfAbsent(ability.id(), key -> AbilityLook.of(ability));
    }

    // ------------------------------------------------------------------ commands

    private void handle(BossCommand command, List<ServerPlayer> targets) {
        ERBoss boss = animated();
        if (command instanceof BossCommand.PhaseChanged phase) {
            phaseChanged(phase, targets);
        } else if (command instanceof BossCommand.AbilityStarted started) {
            BossAbility ability = started.ability();
            chargeAimedAt = ability.kind() instanceof AbilityKind.Charge ? now : -1;
            Component hint = ability.kind() instanceof AbilityKind.SafeZones
                    ? Component.translatable(BossTexts.SAFE_ZONES).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    : Component.translatable("message.eternalrealm.boss.ability", body.getDisplayName(),
                    Component.translatable(ability.translationKey())).withStyle(ChatFormatting.RED);
            tell(targets, hint, true);
            sound(com.eternalrealm.sound.ERSounds.BOSS_TELEGRAPH.get(), 1.0F, 1.0F);
            if (boss != null) {
                boss.setAiFrozen(true);
                topThreat(targets).ifPresent(target -> boss.faceTowards(target.getX(), target.getZ()));
                if (!brain.isTransforming(now)) {
                    startAction(boss, ability, started.executeInTicks());
                }
            }
        } else if (command instanceof BossCommand.Telegraph telegraph) {
            telegraph(telegraph, targets);
        } else if (command instanceof BossCommand.SafeSpot spot) {
            int marker = Telegraphs.show(level, BossMarkers.safe(spot.shape(), spot.durationTicks()));
            markers.add(new Marker(spot.ability(), spot.shape(), now + spot.durationTicks(), marker, true));
            vfx.safeSpot(spot.ability().id(), spot.shape(), spot.durationTicks());
        } else if (command instanceof BossCommand.Cancelled cancelled) {
            String owner = cancelled.ability().id();
            markers.removeIf(marker -> {
                if (!marker.ability().equals(cancelled.ability())) {
                    return false;
                }
                Telegraphs.cancel(level, marker.markerId());
                return true;
            });
            props.remove(owner);
            scheduled.removeIf(entry -> entry.owner().equals(owner));
            if (boss != null) {
                boss.setTelegraphing(false);
            }
        } else if (command instanceof BossCommand.Execute execute) {
            execute(execute, targets);
            if (boss != null) {
                boss.setTelegraphing(markers.stream().anyMatch(marker -> !marker.safe()));
            }
        } else if (command instanceof BossCommand.Volley volley) {
            volley(volley);
        } else if (command instanceof BossCommand.Displace displace) {
            displace(displace, targets);
        } else if (command instanceof BossCommand.Hazard hazard) {
            AbilityLook look = look(hazard.ability());
            int marker = Telegraphs.show(level, BossMarkers.marker(look.style(), hazard.shape(), hazard.durationTicks(),
                    arenaRadius));
            hazards.add(new HazardZone(hazard.ability(), hazard.shape(), now + hazard.durationTicks(),
                    hazard.intervalTicks(), now + hazard.intervalTicks(), hazard.damage(), marker));
        } else if (command instanceof BossCommand.Dash charge) {
            if (dash != null) {
                body.setPos(dash.to().x, dash.to().y, dash.to().z);
            }
            dash = new DashState(body.position(), new Vec3(charge.to().x(), body.getY(), charge.to().z()), now);
            if (boss != null) {
                boss.faceTowardsNow(charge.to().x(), charge.to().z());
                if (boss.playingAnimation().filter(BossAnimation.CHARGE::equals).isEmpty()) {
                    boss.playAnimation(BossAnimation.CHARGE); // the wind-up was skipped: lunge now
                }
            }
            sound(SoundEvents.RAVAGER_ROAR, 1.2F, 1.3F);
            sound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5F, 0.6F);
        } else if (command instanceof BossCommand.Summon summon) {
            summon(summon);
        } else if (command instanceof BossCommand.Teleport teleport) {
            double size = Math.max(1.0, body.getBbWidth() * 0.5);
            burst(ParticleTypes.REVERSE_PORTAL, body.position().add(0, body.getBbHeight() * 0.4, 0), (int) (40 * size),
                    size);
            body.teleportTo(teleport.destination().x(), teleport.destination().y(), teleport.destination().z());
            burst(ParticleTypes.PORTAL, body.position().add(0, body.getBbHeight() * 0.4, 0), (int) (60 * size), size);
            sound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.7F);
        } else if (command instanceof BossCommand.ShieldChanged shield) {
            shielded = shield.on();
            if (boss != null) {
                boss.setShielded(shield.on());
            } else {
                body.setGlowingTag(shield.on());
            }
            if (!brain.isTransforming(now) || !shield.on()) {
                tell(targets, Component.translatable(shield.on() ? BossCommand.ShieldChanged.SHIELD_UP_KEY
                        : BossCommand.ShieldChanged.SHIELD_DOWN_KEY, body.getDisplayName())
                        .withStyle(ChatFormatting.AQUA), true);
            }
            sound(shield.on() ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.0F);
        } else if (command instanceof BossCommand.Enraged) {
            bar.setColor(BossEvent.BossBarColor.RED);
            if (boss != null) {
                boss.setEnraged(true);
            }
            tell(targets, Component.translatable(BossCommand.Enraged.MESSAGE_KEY, body.getDisplayName())
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
            sound(com.eternalrealm.sound.ERSounds.BOSS_ROAR.get(), 1.2F, 1.0F);
            burst(ParticleTypes.ANGRY_VILLAGER, body.position().add(0, body.getBbHeight() * 0.75, 0), 20,
                    Math.max(1.5, body.getBbWidth() * 0.5));
            BossFx.send(targets, 0.8F, 16, 0xFF2010, 0.25F, 12);
        } else if (command instanceof BossCommand.Heal heal) {
            health = Math.min(maxHealth, health + maxHealth * heal.fraction());
            bar.setProgress((float) (health / maxHealth));
            burst(ERParticles.HEAL.get(), body.position().add(0, body.getBbHeight() * 0.5, 0), 30,
                    Math.max(1.5, body.getBbWidth() * 0.5));
            sound(SoundEvents.ZOMBIE_VILLAGER_CURE, 0.8F, 1.2F);
        } else if (command instanceof BossCommand.Buffed) {
            burst(ERParticles.EMBER.get(), body.position().add(0, body.getBbHeight() * 0.5, 0), 30,
                    Math.max(1.2, body.getBbWidth() * 0.5));
            burst(ParticleTypes.ANGRY_VILLAGER, body.position().add(0, body.getBbHeight() * 0.75, 0), 8, 1.0);
            sound(SoundEvents.BLAZE_AMBIENT, 1.0F, 0.6F);
        }
    }

    private void phaseChanged(BossCommand.PhaseChanged phase, List<ServerPlayer> targets) {
        int index = phase.phaseIndex();
        bar.setColor(index == 0 ? BossEvent.BossBarColor.PURPLE
                : index == 1 ? BossEvent.BossBarColor.PINK : BossEvent.BossBarColor.RED);
        AttributeInstance speed = body.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && baseSpeed > 0.0) {
            speed.setBaseValue(baseSpeed * phase.phase().movementSpeedMultiplier());
        }
        ERBoss boss = animated();
        if (boss != null) {
            boss.setPhase(index);
        }
        if (index == 0) {
            return;
        }
        if (boss != null) {
            boss.setAiFrozen(true);
            // the full-body roar peaks (its impact frame) when the transformation's blast goes off
            int blast = brain.definition().transitionAbility(index).map(BossAbility::telegraphTicks).orElse(0);
            int delay = Math.max(0, blast - boss.impactTicks(BossAnimation.PHASE_CHANGE));
            if (delay == 0) {
                boss.playAnimation(BossAnimation.PHASE_CHANGE);
            } else {
                later(delay, "phase", () -> {
                    if (!dying) {
                        boss.playAnimation(BossAnimation.PHASE_CHANGE);
                    }
                });
            }
        }
        List<ServerPlayer> audience = audience();
        for (ServerPlayer player : audience) {
            title(player, Component.translatable(BossTexts.TRANSFORM_TITLE, body.getDisplayName())
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                    Component.translatable(BossTexts.TRANSFORM_SUBTITLE, index + 1).withStyle(ChatFormatting.GOLD), 30);
        }
        tell(targets, Component.translatable(BossCommand.PhaseChanged.MESSAGE_KEY, body.getDisplayName(), index + 1)
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        vfx.transformation(index, audience);
        int phaseNumber = index + 1;
        ServerContext.current().ifPresent(context -> {
            if ("final".equals(bossId())) {
                context.game().story().trigger(new com.eternalrealm.story.StoryTrigger.FinalPhase(
                        Math.max(1, Math.min(4, phaseNumber))), null, targets);
            } else {
                context.game().story().catalog().bossPhaseBark(bossId(), phaseNumber).ifPresent(line ->
                        context.game().story().bark(targets, line));
            }
        });
    }

    private void telegraph(BossCommand.Telegraph telegraph, List<ServerPlayer> targets) {
        BossAbility ability = telegraph.ability();
        AbilityLook look = look(ability);
        boolean damaging = ability.kind().damaging();
        Telegraph.Style style = damaging ? look.styleFor(telegraph.hitKind()) : Telegraph.Style.MAGIC;
        int marker = Telegraphs.show(level, BossMarkers.marker(style, telegraph.shape(), telegraph.durationTicks(),
                arenaRadius));
        long fireTick = now + telegraph.durationTicks();
        markers.add(new Marker(ability, telegraph.shape(), fireTick, marker, false));
        if (!damaging) {
            return;
        }
        ERBoss charger = animated();
        if (charger != null && ability.kind() instanceof AbilityKind.Charge && chargeAimedAt >= 0
                && now > chargeAimedAt && telegraph.shape() instanceof BossShape.Line strip) {
            // every further charge winds up again with its lunge on the dash (the first one starts with the ability)
            charger.faceTowards(strip.end().x(), strip.end().z());
            startAction(charger, ability, telegraph.durationTicks());
            chargeAimedAt = now;
        }
        vfx.telegraphed(ability.id(), look, telegraph.hitKind(), telegraph.shape(), now, fireTick);
        if (telegraph.hitKind() instanceof AbilityKind.Soak) {
            Point center = telegraph.shape().anchor();
            targets.stream().min(java.util.Comparator.comparingDouble(player -> player.distanceToSqr(center.x(),
                    center.y(), center.z()))).ifPresent(marked -> tell(targets, Component.translatable(BossTexts.STACK,
                    marked.getDisplayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true));
        }
        ERBoss boss = animated();
        if (boss != null) {
            boss.setTelegraphing(true);
        }
    }

    private void execute(BossCommand.Execute execute, List<ServerPlayer> targets) {
        BossShape shape = execute.shape();
        fireMarker(execute.ability(), shape);
        if (shape instanceof BossShape.Outside outside) {
            for (BossShape safe : outside.safe()) {
                fireMarker(execute.ability(), safe);
            }
        }
        List<ServerPlayer> hit = new ArrayList<>();
        for (ServerPlayer player : targets) {
            if (Math.abs(player.getY() - shape.anchor().y()) <= BossRules.VERTICAL_TOLERANCE + 2.0
                    && shape.contains(player.getX(), player.getY(), player.getZ())) {
                hit.add(player);
            }
        }
        double damage = execute.damage();
        if (execute.splitsDamage() && !hit.isEmpty()) {
            damage /= hit.size();
        }
        if (damage > 0.0) {
            executing = true;
            try {
                for (ServerPlayer player : hit) {
                    BossDamage.hit(level, body, player, execute.hitKind(), damage);
                }
            } finally {
                executing = false;
            }
        }
        vfx.hit(execute.abilityId(), look(execute.ability()), execute.hitKind(), shape, targets);
    }

    /** The marker of {@code shape} hits now: it flashes on the clients and goes. */
    private void fireMarker(BossAbility ability, BossShape shape) {
        Marker due = null;
        for (Marker marker : markers) {
            if (marker.ability().equals(ability) && marker.shape().equals(shape)
                    && (due == null || marker.fireTick() < due.fireTick())) {
                due = marker;
            }
        }
        if (due != null) {
            markers.remove(due);
            Telegraphs.fire(level, due.markerId());
        }
    }

    /** Markers whose hit never came (an effect without a hit, a lost command) fade out shortly after their time. */
    private void expireMarkers() {
        Iterator<Marker> iterator = markers.iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next();
            if (now > marker.fireTick() + 5) {
                Telegraphs.cancel(level, marker.markerId());
                iterator.remove();
            } else if (now == marker.fireTick() && !marker.ability().kind().damaging()) {
                Telegraphs.fire(level, marker.markerId()); // summon / teleport spots: the effect happens now
                iterator.remove();
            }
        }
    }

    private void volley(BossCommand.Volley volley) {
        AbilityLook look = look(volley.ability());
        ParticleOptions particle = switch (look.style()) {
            case FIRE -> ParticleTypes.FLAME;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case MAGIC -> ERParticles.RUNE.get();
            case DANGER -> ParticleTypes.CRIT;
            default -> ERParticles.SHADOW.get();
        };
        fireMarkersDue(volley.ability());
        // the projectiles leave the hand, gem, core or mouth of the body (as the cast animation shows at its release)
        Vec3 muzzle = muzzle();
        double floorY = volley.origin().y();
        double cruiseY = floorY + BOLT_CRUISE_HEIGHT;
        for (double yaw : volley.yawDegrees()) {
            double radians = Math.toRadians(yaw);
            Vec3 direction = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
            // start at the muzzle's height on this bolt's line (the fan stays the telegraphed one), no further out
            // than the front of the body, so nobody standing close is skipped
            double ahead = Mth.clamp((muzzle.x - volley.origin().x()) * direction.x
                    + (muzzle.z - volley.origin().z()) * direction.z, 0.0, body.getBbWidth() * 0.5);
            Vec3 start = new Vec3(volley.origin().x() + direction.x * ahead, muzzle.y,
                    volley.origin().z() + direction.z * ahead);
            // come down steeply to chest height, then fly level
            double drop = Math.max(0.0, start.y - cruiseY);
            double descent = Math.max(2.0, drop * 0.9);
            Vec3 step = direction.scale(volley.speed()).add(0.0, -drop / descent * volley.speed(), 0.0);
            bolts.add(new Bolt(start, step, cruiseY, Math.max(4.0, BossRules.VOLLEY_RANGE - ahead), volley.damage(),
                    volley.ability().kind(), particle));
        }
        sound(look.style() == Telegraph.Style.FIRE ? SoundEvents.BLAZE_SHOOT : SoundEvents.CROSSBOW_SHOOT, 1.0F, 0.8F);
    }

    /** World position of the body's muzzle (cast release point) for its current look and size. */
    private Vec3 muzzle() {
        ERBoss boss = animated();
        if (boss == null) {
            return new Vec3(body.getX(), body.getY() + body.getEyeHeight(), body.getZ());
        }
        float[] offset = RigData.muzzle(boss.kind().rig().id(), boss.lookKey());
        float scale = boss.modelScale();
        double yaw = Math.toRadians(body.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        // left of the facing direction (x: +left): forward rotated by 90 degrees
        double leftX = forwardZ;
        double leftZ = -forwardX;
        double left = offset[0] * scale;
        double up = offset[1] * scale;
        double forward = offset[2] * scale;
        return new Vec3(body.getX() + forwardX * forward + leftX * left, body.getY() + Math.max(0.5, up),
                body.getZ() + forwardZ * forward + leftZ * left);
    }

    /** Fires every marker of {@code ability} that is due now (a volley's fan, summon spots). */
    private void fireMarkersDue(BossAbility ability) {
        markers.removeIf(marker -> {
            if (!marker.ability().equals(ability) || marker.fireTick() > now) {
                return false;
            }
            Telegraphs.fire(level, marker.markerId());
            return true;
        });
    }

    /** Pushes players away from (or pulls them towards) the centre of a blast. */
    private void displace(BossCommand.Displace displace, List<ServerPlayer> targets) {
        Point center = displace.center();
        fireMarkersDue(displace.ability());
        for (ServerPlayer player : targets) {
            if (player.isCreative() || player.isSpectator()
                    || Math.abs(player.getY() - center.y()) > BossRules.VERTICAL_TOLERANCE + 1.0) {
                continue;
            }
            double dx = player.getX() - center.x();
            double dz = player.getZ() - center.z();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > displace.radius()) {
                continue;
            }
            double nx;
            double nz;
            if (distance < 0.3) {
                double angle = RANDOM.nextDouble() * Math.PI * 2.0;
                nx = Math.cos(angle);
                nz = Math.sin(angle);
            } else {
                nx = dx / distance;
                nz = dz / distance;
            }
            Vec3 velocity;
            if (displace.strength() >= 0.0) {
                velocity = new Vec3(nx * displace.strength(), 0.45, nz * displace.strength());
            } else {
                double pull = Math.min(-displace.strength(), Math.max(0.0, distance - 2.0) / 6.5);
                velocity = new Vec3(-nx * pull, 0.35, -nz * pull);
            }
            player.setDeltaMovement(velocity);
            player.hurtMarked = true;
        }
    }

    private void tickHazards(List<ServerPlayer> targets) {
        Iterator<HazardZone> iterator = hazards.iterator();
        while (iterator.hasNext()) {
            HazardZone hazard = iterator.next();
            if (now >= hazard.until) {
                Telegraphs.cancel(level, hazard.markerId);
                iterator.remove();
                continue;
            }
            if (now % 4 == 0) {
                vfx.hazardTick(look(hazard.ability), hazard.shape);
            }
            if (now >= hazard.nextPulse) {
                hazard.nextPulse += hazard.interval;
                for (ServerPlayer player : targets) {
                    if (hazard.shape.contains(player.getX(), player.getY(), player.getZ())) {
                        BossDamage.hazard(level, body, player, hazard.damage);
                    }
                }
            }
        }
    }

    /** Moves a charging boss along its strip, stopping at walls. */
    private void stepDash() {
        if (dash == null) {
            return;
        }
        double t = Math.min(1.0, (now - dash.start() + 1) / (double) BossRules.DASH_TICKS);
        Vec3 target = dash.from().add(dash.to().subtract(dash.from()).scale(t));
        Vec3 current = body.position();
        Vec3 move = target.subtract(current);
        if (level.noCollision(body, body.getBoundingBox().move(move))) {
            body.setPos(target.x, target.y, target.z);
            vfx.dashTrail(current, target);
        } else {
            t = 1.0; // stopped by a wall
        }
        body.setDeltaMovement(Vec3.ZERO);
        if (t >= 1.0) {
            dash = null;
            burst(ParticleTypes.CLOUD, body.position().add(0, 0.3, 0), 16, 0.8);
        }
    }

    private void summon(BossCommand.Summon summon) {
        fireMarkersDue(brain.currentAbility().orElse(null));
        MonsterTemplate template = Monsters.byId(summon.templateId()).orElse(null);
        for (Point position : summon.positions()) {
            Optional<Mob> add = template == null ? Optional.empty() : MonsterFactory.create(level,
                    new MonsterSpawn(template, Mth.clamp(brain.definition().level(), template.levels().min(),
                            template.levels().max())), false);
            if (add.isEmpty()) {
                if (summon.shieldAdds()) {
                    brain.onAddKilled();
                }
                continue;
            }
            Mob mob = add.get();
            mob.moveTo(position.x(), position.y() + 0.1, position.z(), RANDOM.nextFloat() * 360.0F, 0.0F);
            mob.getPersistentData().putUUID(FIGHT, id);
            level.addFreshEntity(mob);
            adds.add(mob.getUUID());
            burst(ParticleTypes.SOUL, mob.position().add(0, 0.5, 0), 12, 0.5);
            level.sendParticles(ERParticles.RUNE.get(), position.x(), position.y() + 0.1, position.z(), 10, 0.8, 0.05,
                    0.8, 0.0);
            if (summon.shieldAdds()) {
                shieldAdds.add(mob.getUUID());
            }
        }
        sound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 0.9F);
    }

    // ------------------------------------------------------------------ visuals

    private void moveBolts(List<ServerPlayer> targets) {
        Iterator<Bolt> iterator = bolts.iterator();
        while (iterator.hasNext()) {
            Bolt bolt = iterator.next();
            Vec3 next = bolt.position.add(bolt.step);
            bolt.position = next.y < bolt.cruiseY ? new Vec3(next.x, bolt.cruiseY, next.z) : next;
            bolt.travelled += Math.sqrt(bolt.step.x * bolt.step.x + bolt.step.z * bolt.step.z);
            level.sendParticles(bolt.particle, bolt.position.x, bolt.position.y, bolt.position.z, 2, 0.05, 0.05, 0.05,
                    0.0);
            ServerPlayer struck = null;
            for (ServerPlayer player : targets) {
                if (player.getBoundingBox().inflate(0.4).contains(bolt.position)) {
                    struck = player;
                    break;
                }
            }
            if (struck != null) {
                executing = true;
                try {
                    BossDamage.hit(level, body, struck, bolt.kind, bolt.damage);
                } finally {
                    executing = false;
                }
                iterator.remove();
            } else if (bolt.travelled > bolt.range
                    || !level.getBlockState(BlockPos.containing(bolt.position)).isAir()) {
                iterator.remove();
            }
        }
    }

    private void burst(ParticleOptions particle, Vec3 at, int count, double spread) {
        level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread * 0.5, spread, 0.05);
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        level.playSound(null, body.getX(), body.getY(), body.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    private static void tell(List<ServerPlayer> players, Component message, boolean actionBar) {
        for (ServerPlayer player : players) {
            if (actionBar) {
                player.displayClientMessage(message, true);
            } else {
                player.sendSystemMessage(message);
            }
        }
    }

    private static void title(ServerPlayer player, Component title, Component subtitle, int stay) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, stay, 16));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    // ------------------------------------------------------------------ HUD

    /** HP layers of the bar ("×N"): more for bosses with more health. */
    static int layers(double maxHealth) {
        return Mth.clamp((int) Math.round(Math.sqrt(maxHealth / 30.0)), 3, 25);
    }

    /** Sends the boss HUD (frame, cast bar, ranking) to every participant in the level. */
    private void sendHud() {
        List<ServerPlayer> audience = audience();
        if (audience.isEmpty()) {
            return;
        }
        JsonObject base = new JsonObject();
        base.addProperty("id", id.toString());
        base.addProperty("name", Component.Serializer.toJson(body.getDisplayName()));
        base.addProperty("boss", bossId());
        base.addProperty("level", brain.definition().level());
        base.addProperty("difficulty", brain.difficulty().name().toLowerCase(Locale.ROOT));
        base.addProperty("difficultyKey", brain.difficulty().translationKey());
        base.addProperty("hp", Math.round(health * 10.0) / 10.0);
        base.addProperty("max", Math.round(maxHealth * 10.0) / 10.0);
        base.addProperty("layers", layers(maxHealth));
        base.addProperty("phase", brain.phase());
        base.addProperty("phases", brain.definition().phases().size());
        base.addProperty("shield", shielded);
        base.addProperty("invulnerable", isInvulnerable());
        base.addProperty("dying", isDying());
        base.addProperty("enraged", brain.isEnraged());
        long enrageTick = brain.enrageTick();
        base.addProperty("enrage", enrageTick == Long.MAX_VALUE ? -1.0 : Math.max(0L, enrageTick - now) / 20.0);
        brain.cast().ifPresent(cast -> {
            long left = cast.firstHitTick() - now;
            if (left > 0 && !isDying()) {
                JsonObject castBar = new JsonObject();
                castBar.addProperty("key", cast.ability().translationKey());
                castBar.addProperty("total", Math.max(1L, cast.firstHitTick() - cast.startTick()) / 20.0);
                castBar.addProperty("left", left / 20.0);
                base.add("cast", castBar);
            }
        });
        List<Contributions.Line> ranking = contributions.byDamage();
        for (ServerPlayer player : audience) {
            JsonObject data = base.deepCopy();
            JsonArray rows = new JsonArray();
            int place = 0;
            for (Contributions.Line line : ranking) {
                place++;
                boolean me = line.id().equals(player.getUUID());
                if (place > RANKING_ROWS && !me) {
                    continue;
                }
                JsonObject row = new JsonObject();
                row.addProperty("pos", place);
                row.addProperty("name", line.name());
                row.addProperty("job", line.job());
                row.addProperty("damage", Math.round(line.damage()));
                row.addProperty("share", Math.round(line.share() * 1000.0) / 1000.0);
                if (me) {
                    row.addProperty("me", true);
                }
                rows.add(row);
            }
            data.add("rank", rows);
            UiData.send(player, HUD_CHANNEL, data);
        }
    }

    // ------------------------------------------------------------------ defeat

    /** The killing blow: everything stops, the boss freezes and starts to collapse. */
    private void beginDefeat() {
        defeatPending = false;
        dying = true;
        sequence = DefeatSequence.of(bossId());
        killTick = killTick < 0 ? now : killTick;
        brain.tick(now, 0.0, new BossPosition(body.getX(), body.getY(), body.getZ(), body.getYRot()), List.of());
        clearAttacks();
        bar.setProgress(0.0F);
        bar.setColor(BossEvent.BossBarColor.WHITE);
        ERBoss boss = animated();
        if (boss != null) {
            boss.startDefeat(sequence.lengthTicks(), sequence.dissolveTick());
        } else {
            body.setNoAi(true);
            body.setInvulnerable(true);
        }
        body.getNavigation().stop();
        for (UUID addId : List.copyOf(adds)) { // the boss's power fades: its adds go with it
            Entity add = level.getEntity(addId);
            if (add != null && add.isAlive()) {
                level.sendParticles(ParticleTypes.SOUL, add.getX(), add.getY() + 0.8, add.getZ(), 12, 0.3, 0.4, 0.3,
                        0.03);
                add.discard();
            }
        }
        adds.clear();
        shieldAdds.clear();
        onKilled.accept(this);
        tickDefeat();
    }

    private void tickDefeat() {
        List<ServerPlayer> audience = audience();
        if (!body.isAlive() || body.isRemoved()) {
            finishDefeat(audience);
            return;
        }
        int elapsed = (int) (now - (killTick < 0 ? now : killTick)) - 1;
        int t = Math.max(0, elapsed);
        body.setDeltaMovement(Vec3.ZERO);
        body.getNavigation().stop();
        for (DefeatSequence.Beat beat : sequence.at(t)) {
            vfx.defeatBeat(beat, audience);
        }
        if (t >= sequence.dissolveTick() && t % 2 == 0) {
            vfx.dissolving();
        }
        if (t >= sequence.lengthTicks()) {
            finishDefeat(audience);
        }
    }

    /** The collapse is over: the body is gone in the light, the defeat flow runs. */
    private void finishDefeat(List<ServerPlayer> audience) {
        dying = false;
        defeated = true;
        for (ServerPlayer player : audience) {
            title(player, Component.translatable(BossTexts.DEFEATED_TITLE).withStyle(ChatFormatting.GOLD,
                    ChatFormatting.BOLD), Component.translatable(BossTexts.DEFEATED_SUBTITLE, body.getDisplayName())
                    .withStyle(ChatFormatting.YELLOW), 50);
        }
        sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        if (!body.isRemoved()) {
            body.discard();
        }
        end();
        onDefeated.accept(this);
    }

    /** Removes every marker, hazard, bolt, charge and prop of the fight. */
    private void clearAttacks() {
        for (Marker marker : markers) {
            Telegraphs.cancel(level, marker.markerId());
        }
        markers.clear();
        for (HazardZone hazard : hazards) {
            Telegraphs.cancel(level, hazard.markerId);
        }
        hazards.clear();
        bolts.clear();
        dash = null;
        scheduled.removeIf(entry -> !entry.owner().equals("defeat"));
        props.clear();
    }

    /** Removes the bar and everything the fight shows (the fight is over, either way). */
    public void end() {
        if (ended) {
            return;
        }
        ended = true;
        JsonObject over = new JsonObject();
        over.addProperty("id", id.toString());
        over.addProperty("end", true);
        for (ServerPlayer player : audience()) {
            UiData.send(player, HUD_CHANNEL, over);
        }
        bar.removeAllPlayers();
        bar.setVisible(false);
        clearAttacks();
        scheduled.clear();
    }
}
