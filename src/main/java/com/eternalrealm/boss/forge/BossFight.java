package com.eternalrealm.boss.forge;

import com.eternalrealm.boss.AbilityKind;
import com.eternalrealm.boss.BossAbility;
import com.eternalrealm.boss.BossBrain;
import com.eternalrealm.boss.BossCommand;
import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.BossPosition;
import com.eternalrealm.boss.BossRules;
import com.eternalrealm.boss.BossShape;
import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.boss.Point;
import com.eternalrealm.boss.TargetSnapshot;
import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.forge.ERParticles;
import com.eternalrealm.combat.forge.Telegraphs;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.monster.forge.MonsterFactory;
import com.eternalrealm.server.ServerContext;
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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
 * (job threat multipliers), telegraphs are drawn on the ground, abilities hit the players inside their shapes through
 * the normal defense pipeline, volleys fly as particle bolts, adds are summoned and a boss bar shows the fight.
 */
public final class BossFight {
    public static final String TAG = "eternalrealm_boss";
    static final String FIGHT = "er_fight";
    private static final double ARENA_RANGE = 48.0;
    private static final SplittableRandom RANDOM = new SplittableRandom();

    /** A charging ability area; {@code markerId} is its ground marker on the clients ({@link Telegraphs}). */
    private record Telegraph(BossAbility ability, BossShape shape, long until, int markerId) {
    }

    private static final class Bolt {
        Vec3 position;
        final Vec3 step;
        double travelled;
        final double damage;
        final boolean guardable;

        Bolt(Vec3 position, Vec3 step, double damage, boolean guardable) {
            this.position = position;
            this.step = step;
            this.damage = damage;
            this.guardable = guardable;
        }
    }

    private final UUID id = UUID.randomUUID();
    private final ServerLevel level;
    private final Mob body;
    private final BossBrain brain;
    private final ServerBossEvent bar;
    private final Set<UUID> participants;
    private final Map<UUID, Double> threat = new HashMap<>();
    private final List<Telegraph> telegraphs = new ArrayList<>();
    private final List<Bolt> bolts = new ArrayList<>();
    private final Set<UUID> shieldAdds = new HashSet<>();
    private final Consumer<BossFight> onDefeated;
    private final double maxHealth;
    private double health;
    private boolean shielded;
    private boolean executing;
    private boolean defeated;
    private boolean defeatPending;
    private long now;

    private BossFight(ServerLevel level, Mob body, BossBrain brain, Set<UUID> participants,
                      Consumer<BossFight> onDefeated) {
        this.level = level;
        this.body = body;
        this.brain = brain;
        this.participants = Set.copyOf(participants);
        this.onDefeated = onDefeated;
        this.maxHealth = brain.maxHealth();
        this.health = maxHealth;
        this.bar = new ServerBossEvent(body.getDisplayName(), BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.NOTCHED_10);
        this.bar.setDarkenScreen(true);
    }

    /** Spawns the boss at {@code spawn} and starts the fight. Empty if no body could be built. */
    public static Optional<BossFight> start(ServerLevel level, BossDefinition definition, Difficulty difficulty,
                                            BlockPos spawn, Set<UUID> participants, Consumer<BossFight> onDefeated) {
        BossBrain brain = new BossBrain(definition, Math.max(1, participants.size()), difficulty, RANDOM);
        Optional<Mob> body = BossBodies.create(level, definition, brain);
        if (body.isEmpty()) {
            return Optional.empty();
        }
        BossFight fight = new BossFight(level, body.get(), brain, participants, onDefeated);
        body.get().getPersistentData().putUUID(FIGHT, fight.id);
        body.get().moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 180.0F, 0.0F);
        level.addFreshEntity(body.get());
        for (UUID member : participants) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (player != null) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(
                        difficulty.translationKey()).withStyle(ChatFormatting.GRAY)));
                player.connection.send(new ClientboundSetTitleTextPacket(body.get().getDisplayName().copy()
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
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

    public boolean isDefeated() {
        return defeated || defeatPending;
    }

    /** The body is being killed by the fight itself (its own damage must go through). */
    public boolean isKilling() {
        return defeated;
    }

    public boolean isOver() {
        return defeated || !body.isAlive() || body.isRemoved();
    }

    /** Whether hits on the body are ignored now (phase transition, shield). */
    public boolean isInvulnerable() {
        return shielded || brain.isInvulnerable(now);
    }

    /** Damage of a vanilla melee hit of the body (abilities bring their own damage). */
    public OptionalDouble meleeDamage(LivingEntity victim) {
        return executing ? OptionalDouble.empty() : OptionalDouble.of(brain.meleeDamage(now));
    }

    /** A hit on the body: taken from the fight's health pool, the attacker gains threat. */
    public void absorb(Entity attacker, double amount) {
        if (defeated || !(amount > 0.0)) {
            return;
        }
        health = Math.max(0.0, health - amount);
        if (attacker instanceof ServerPlayer player && participants.contains(player.getUUID())) {
            threat.merge(player.getUUID(), amount * threatMultiplier(player), Double::sum);
        }
        bar.setProgress((float) (health / maxHealth));
        if (health <= 0.0) {
            defeatPending = true; // finished on the next tick, outside the damage event
        }
    }

    private static double threatMultiplier(ServerPlayer player) {
        JobType job = ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(CharacterSnapshot::jobId).map(JobType::fromStored).orElse(JobType.NONE);
        return job.isChosen() ? Jobs.get(job).threatMultiplier() : 1.0;
    }

    /** One of the summoned adds died (or vanished). */
    public void onAddGone(UUID entityId) {
        if (shieldAdds.remove(entityId)) {
            brain.onAddKilled();
        }
    }

    // ------------------------------------------------------------------ tick

    public void tick(long tick) {
        now = tick;
        if (defeated) {
            return;
        }
        if (defeatPending) {
            defeat();
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
        com.eternalrealm.entity.ERBoss boss = animated();
        if (brain.currentAbility().isPresent()) {
            body.getNavigation().stop();
        } else {
            if (boss != null) {
                boss.setAiFrozen(false);
            }
            if (tick % 20 == 0) {
                topThreat(targets).ifPresent(body::setTarget);
            }
        }
        drawTelegraphs();
        moveBolts(targets);
    }

    private List<ServerPlayer> targets() {
        List<ServerPlayer> targets = new ArrayList<>();
        for (UUID member : participants) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (player != null && player.isAlive() && !player.isSpectator() && player.level() == level
                    && player.distanceToSqr(body) < ARENA_RANGE * ARENA_RANGE) {
                targets.add(player);
            }
        }
        return targets;
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

    // ------------------------------------------------------------------ animated body

    private com.eternalrealm.entity.ERBoss animated() {
        return body instanceof com.eternalrealm.entity.ERBoss boss ? boss : null;
    }

    private static com.eternalrealm.entity.BossAnimation animationFor(AbilityKind kind) {
        if (kind instanceof AbilityKind.CircleAroundBoss || kind instanceof AbilityKind.Ring) {
            return com.eternalrealm.entity.BossAnimation.MELEE_SLAM;
        }
        if (kind instanceof AbilityKind.Cone) {
            return com.eternalrealm.entity.BossAnimation.MELEE_SWEEP;
        }
        if (kind instanceof AbilityKind.Line) {
            return com.eternalrealm.entity.BossAnimation.CHARGE;
        }
        if (kind instanceof AbilityKind.ArenaWide || kind instanceof AbilityKind.Buff) {
            return com.eternalrealm.entity.BossAnimation.ROAR;
        }
        if (kind instanceof AbilityKind.Summon) {
            return com.eternalrealm.entity.BossAnimation.SUMMON;
        }
        return com.eternalrealm.entity.BossAnimation.CAST;
    }

    // ------------------------------------------------------------------ commands

    private void handle(BossCommand command, List<ServerPlayer> targets) {
        com.eternalrealm.entity.ERBoss boss = animated();
        if (command instanceof BossCommand.PhaseChanged phase) {
            bar.setColor(phase.phaseIndex() >= 2 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PINK);
            if (boss != null) {
                boss.setPhase(phase.phaseIndex());
                boss.playAnimation(com.eternalrealm.entity.BossAnimation.PHASE_CHANGE);
            }
            tell(targets, Component.translatable("message.eternalrealm.boss.phase_changed", body.getDisplayName(),
                    phase.phaseIndex() + 1).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            sound(com.eternalrealm.sound.ERSounds.BOSS_PHASE_CHANGE.get(), 1.0F, 1.0F);
            burst(ERParticles.RUNE.get(), body.position().add(0, 1.5, 0), 40, 2.0);
            int phaseNumber = phase.phaseIndex() + 1;
            ServerContext.current().ifPresent(context -> {
                if ("final".equals(bossId())) {
                    context.game().story().trigger(new com.eternalrealm.story.StoryTrigger.FinalPhase(
                            Math.max(1, Math.min(4, phaseNumber))), null, targets);
                } else if (phaseNumber >= 2) {
                    context.game().story().catalog().bossPhaseBark(bossId(), phaseNumber).ifPresent(line ->
                            context.game().story().bark(targets, line));
                }
            });
        } else if (command instanceof BossCommand.AbilityStarted started) {
            tell(targets, Component.translatable("message.eternalrealm.boss.ability", body.getDisplayName(),
                    Component.translatable(started.ability().translationKey())).withStyle(ChatFormatting.RED), true);
            sound(com.eternalrealm.sound.ERSounds.BOSS_TELEGRAPH.get(), 1.0F, 1.0F);
            if (boss != null) {
                boss.setAiFrozen(true);
                topThreat(targets).ifPresent(target -> boss.faceTowards(target.getX(), target.getZ()));
                boss.playAnimation(animationFor(started.ability().kind()));
            }
        } else if (command instanceof BossCommand.Telegraph telegraph) {
            int marker = Telegraphs.show(level, BossTelegraphs.marker(telegraph.ability(), telegraph.shape(),
                    telegraph.durationTicks(), body.getY()));
            telegraphs.add(new Telegraph(telegraph.ability(), telegraph.shape(), now + telegraph.durationTicks(), marker));
            if (boss != null) {
                boss.setTelegraphing(true);
            }
        } else if (command instanceof BossCommand.Cancelled cancelled) {
            telegraphs.removeIf(t -> {
                if (!t.ability().equals(cancelled.ability())) {
                    return false;
                }
                Telegraphs.cancel(level, t.markerId());
                return true;
            });
            if (boss != null) {
                boss.setTelegraphing(false);
            }
        } else if (command instanceof BossCommand.Execute execute) {
            if (boss != null) {
                boss.setTelegraphing(false);
            }
            execute(execute, targets);
        } else if (command instanceof BossCommand.Volley volley) {
            Vec3 origin = new Vec3(volley.origin().x(), volley.origin().y() + body.getEyeHeight(), volley.origin().z());
            for (double yaw : volley.yawDegrees()) {
                double radians = Math.toRadians(yaw);
                Vec3 step = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians)).scale(volley.speed());
                bolts.add(new Bolt(origin, step, volley.damage(), volley.ability().kind().guardable()));
            }
            sound(SoundEvents.BLAZE_SHOOT, 1.0F, 0.8F);
        } else if (command instanceof BossCommand.Summon summon) {
            summon(summon);
        } else if (command instanceof BossCommand.Teleport teleport) {
            burst(ParticleTypes.REVERSE_PORTAL, body.position().add(0, 1, 0), 40, 1.0);
            body.teleportTo(teleport.destination().x(), teleport.destination().y(), teleport.destination().z());
            burst(ParticleTypes.PORTAL, body.position().add(0, 1, 0), 60, 1.0);
            sound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.7F);
        } else if (command instanceof BossCommand.ShieldChanged shield) {
            shielded = shield.on();
            if (boss != null) {
                boss.setShielded(shield.on());
            } else {
                body.setGlowingTag(shield.on());
            }
            tell(targets, Component.translatable(shield.on() ? "message.eternalrealm.boss.shield_up"
                    : "message.eternalrealm.boss.shield_down", body.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
            sound(shield.on() ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.0F);
        } else if (command instanceof BossCommand.Enraged) {
            bar.setColor(BossEvent.BossBarColor.RED);
            if (boss != null) {
                boss.setEnraged(true);
            }
            tell(targets, Component.translatable("message.eternalrealm.boss.enraged", body.getDisplayName())
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
            sound(com.eternalrealm.sound.ERSounds.BOSS_ROAR.get(), 1.2F, 1.0F);
            burst(ParticleTypes.ANGRY_VILLAGER, body.position().add(0, 2, 0), 20, 1.5);
        } else if (command instanceof BossCommand.Heal heal) {
            health = Math.min(maxHealth, health + maxHealth * heal.fraction());
            bar.setProgress((float) (health / maxHealth));
            burst(ERParticles.HEAL.get(), body.position().add(0, 1.5, 0), 30, 1.5);
            sound(SoundEvents.ZOMBIE_VILLAGER_CURE, 0.8F, 1.2F);
        } else if (command instanceof BossCommand.Buffed) {
            burst(ERParticles.EMBER.get(), body.position().add(0, 1.5, 0), 30, 1.2);
            sound(SoundEvents.BLAZE_AMBIENT, 1.0F, 0.6F);
        }
    }

    private void execute(BossCommand.Execute execute, List<ServerPlayer> targets) {
        BossShape shape = execute.shape();
        telegraphs.removeIf(t -> {
            if (!t.ability().equals(execute.ability()) || !t.shape().equals(shape)) {
                return false;
            }
            Telegraphs.fire(level, t.markerId());
            return true;
        });
        List<ServerPlayer> hit = new ArrayList<>();
        for (ServerPlayer player : targets) {
            if (Math.abs(player.getY() - body.getY()) <= BossRules.VERTICAL_TOLERANCE + 2.0
                    && shape.contains(player.getX(), player.getY(), player.getZ())) {
                hit.add(player);
            }
        }
        double damage = execute.damage();
        AbilityKind kind = execute.ability().kind();
        if (kind.splitsDamage() && !hit.isEmpty()) {
            damage /= hit.size();
        }
        executing = true;
        try {
            for (ServerPlayer player : hit) {
                player.hurt(level.damageSources().mobAttack(body), (float) damage);
            }
        } finally {
            executing = false;
        }
        drawShape(shape, ParticleTypes.EXPLOSION, 6);
        drawShape(shape, ERParticles.EMBER.get(), 2);
        sound(SoundEvents.GENERIC_EXPLODE, 0.9F, 1.1F);
    }

    private void summon(BossCommand.Summon summon) {
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
            burst(ParticleTypes.SOUL, mob.position().add(0, 0.5, 0), 12, 0.5);
            if (summon.shieldAdds()) {
                shieldAdds.add(mob.getUUID());
            }
        }
        sound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 0.9F);
    }

    // ------------------------------------------------------------------ visuals

    private void drawTelegraphs() {
        Iterator<Telegraph> iterator = telegraphs.iterator();
        while (iterator.hasNext()) {
            Telegraph telegraph = iterator.next();
            if (now > telegraph.until() + 5) {
                Telegraphs.cancel(level, telegraph.markerId());
                iterator.remove();
            }
        }
    }

    /** Particles on the outline of a shape at the floor of the arena. */
    private void drawShape(BossShape shape, ParticleOptions particle, int density) {
        double y = body.getY() + 0.15;
        if (shape instanceof BossShape.Circle circle) {
            ring(particle, circle.center().x(), y, circle.center().z(), circle.radius(), density);
        } else if (shape instanceof BossShape.Ring ring) {
            ring(particle, ring.center().x(), y, ring.center().z(), ring.inner(), density);
            ring(particle, ring.center().x(), y, ring.center().z(), ring.outer(), density);
        } else if (shape instanceof BossShape.Cone cone) {
            double half = Math.toRadians(cone.halfAngleDegrees());
            double yaw = Math.toRadians(cone.yawDegrees());
            for (double a = -half; a <= half; a += Math.max(0.05, 1.0 / (cone.range() * density))) {
                double x = cone.origin().x() - Math.sin(yaw + a) * cone.range();
                double z = cone.origin().z() + Math.cos(yaw + a) * cone.range();
                level.sendParticles(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            for (double d = 0.5; d <= cone.range(); d += 1.0 / density) {
                for (double edge : new double[] {-half, half}) {
                    level.sendParticles(particle, cone.origin().x() - Math.sin(yaw + edge) * d, y,
                            cone.origin().z() + Math.cos(yaw + edge) * d, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        } else if (shape instanceof BossShape.Line line) {
            double dx = line.end().x() - line.origin().x();
            double dz = line.end().z() - line.origin().z();
            double length = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
            double nx = -dz / length * line.width() * 0.5;
            double nz = dx / length * line.width() * 0.5;
            for (double t = 0.0; t <= 1.0; t += 1.0 / (length * density)) {
                double x = line.origin().x() + dx * t;
                double z = line.origin().z() + dz * t;
                level.sendParticles(particle, x + nx, y, z + nz, 1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(particle, x - nx, y, z - nz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        } else if (shape instanceof BossShape.Arena arena) {
            for (int i = 0; i < 24 * density; i++) {
                double angle = RANDOM.nextDouble() * Math.PI * 2.0;
                double radius = RANDOM.nextDouble() * 14.0;
                level.sendParticles(particle, arena.center().x() + Math.cos(angle) * radius, y,
                        arena.center().z() + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private void ring(ParticleOptions particle, double x, double y, double z, double radius, int density) {
        int points = Math.max(8, (int) (radius * 6.0 * density));
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            level.sendParticles(particle, x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius, 1,
                    0.0, 0.0, 0.0, 0.0);
        }
    }

    private void moveBolts(List<ServerPlayer> targets) {
        Iterator<Bolt> iterator = bolts.iterator();
        while (iterator.hasNext()) {
            Bolt bolt = iterator.next();
            bolt.position = bolt.position.add(bolt.step);
            bolt.travelled += bolt.step.length();
            level.sendParticles(ERParticles.SHADOW.get(), bolt.position.x, bolt.position.y, bolt.position.z, 2,
                    0.05, 0.05, 0.05, 0.0);
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
                    struck.hurt(level.damageSources().mobAttack(body), (float) bolt.damage);
                } finally {
                    executing = false;
                }
                iterator.remove();
            } else if (bolt.travelled > BossRules.VOLLEY_RANGE
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

    // ------------------------------------------------------------------ end

    private void defeat() {
        defeated = true;
        brain.tick(now, 0.0, new BossPosition(body.getX(), body.getY(), body.getZ(), body.getYRot()), List.of());
        telegraphs.forEach(t -> Telegraphs.cancel(level, t.markerId()));
        telegraphs.clear();
        bolts.clear();
        bar.setProgress(0.0F);
        body.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
        if (body.isAlive()) {
            body.kill();
        }
        burst(ERParticles.HOLY.get(), body.position().add(0, 1.5, 0), 80, 2.0);
        sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        end();
        onDefeated.accept(this);
    }

    /** Removes the bar (the fight is over, either way). */
    public void end() {
        bar.removeAllPlayers();
        bar.setVisible(false);
    }
}
