package com.eternalrealm.combat.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.AvoidOutcome;
import com.eternalrealm.combat.CombatEvent;
import com.eternalrealm.combat.CombatRequest;
import com.eternalrealm.combat.CombatService;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.CombatTuning;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.DamageCalculator;
import com.eternalrealm.combat.DefenseEvent;
import com.eternalrealm.combat.GuardResult;
import com.eternalrealm.combat.HitGeometry;
import com.eternalrealm.combat.RequestOutcome;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.network.TokenBucketRateLimiter;
import com.eternalrealm.network.forge.CombatStatsPacket;
import com.eternalrealm.network.forge.CombatStatusPacket;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.SyncQueue;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Server side of the combat system for one logical server (owned by the ServerContext; server thread only).
 *
 * <p>Every tick: apply changed character stats → keep each fighter's weapon and guard in sync with what the player
 * actually holds/uses → advance the state machines → turn due hit frames into damage → send status updates.
 * Requests from clients and incoming damage are handled as they happen, with the server tick as the clock.</p>
 */
public final class CombatDriver {
    private static final Logger LOG = LogUtils.getLogger();
    /** Resend the status this often while stamina/cooldowns change on their own, to correct client drift. */
    private static final int STATUS_RESYNC_TICKS = 20;
    private static final double PARRY_KNOCKBACK = 0.8;
    private static final double PARRY_STUN_RANGE = 6.0;
    private static final int PARRY_SLOW_TICKS = 40;
    private static final int PARRY_SLOW_AMPLIFIER = 3;
    private static final float GUARD_KNOCKBACK_FACTOR = 0.5F;

    private final MinecraftServer server;
    private final PlayerDataService playerData;
    private final SyncQueue statsDirty;
    private final TokenBucketRateLimiter<UUID> limiter;
    private final CombatService service = new CombatService();
    private final Map<UUID, NetState> net = new HashMap<>();
    private CombatTuning tuning;

    /**
     * @param statsDirty players whose character changed; marked by the player data listener, drained every tick
     */
    public CombatDriver(MinecraftServer server, PlayerDataService playerData, SyncQueue statsDirty,
                        TokenBucketRateLimiter<UUID> limiter, CombatTuning tuning) {
        this.server = Objects.requireNonNull(server, "server");
        this.playerData = Objects.requireNonNull(playerData, "playerData");
        this.statsDirty = Objects.requireNonNull(statsDirty, "statsDirty");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        this.tuning = Objects.requireNonNull(tuning, "tuning");
    }

    private static final class NetState {
        int lastSequence;
        long lastSentTick = Long.MIN_VALUE / 4;
        double lastSentStamina = -1.0;
        int lastSentCooldown = -1;
    }

    public CombatService service() {
        return service;
    }

    public TokenBucketRateLimiter<UUID> limiter() {
        return limiter;
    }

    public CombatTuning tuning() {
        return tuning;
    }

    /** Config reload: every fighter's stats are re-derived on the next tick. */
    public void setTuning(CombatTuning newTuning) {
        tuning = Objects.requireNonNull(newTuning, "tuning");
        for (UUID playerId : service.all().keySet()) {
            statsDirty.mark(playerId);
        }
    }

    /** The combat clock. */
    public long now() {
        return server.getTickCount();
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        applyChangedStats();
        long now = now();
        for (UUID playerId : new ArrayList<>(service.all().keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            Combatant combatant = service.get(playerId).orElse(null);
            if (player == null || combatant == null) {
                continue;
            }
            try {
                syncEquipment(player, combatant);
                for (CombatEvent event : combatant.tick(now)) {
                    handleEvent(player, combatant, event);
                }
                resyncStatus(player, combatant, now);
            } catch (RuntimeException e) {
                // One broken fighter must not stop combat for everyone else.
                LOG.error("Combat tick failed for {}; cancelling their action", player.getGameProfile().getName(), e);
                combatant.cancelAction();
            }
        }
    }

    private void applyChangedStats() {
        for (UUID playerId : statsDirty.drain()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            Optional<CharacterSnapshot> snapshot = playerData.snapshot(playerId);
            if (player == null || snapshot.isEmpty()) {
                continue;
            }
            try {
                CombatStats stats = CombatStats.derive(snapshot.get().totalStats(), snapshot.get().level(), tuning);
                Combatant combatant = service.register(playerId, stats);
                StatEffects.apply(player, stats);
                ERNetwork.sendToPlayer(player, new CombatStatsPacket(stats));
                sendStatus(player, combatant, false, RequestOutcome.IGNORED, DefenseEvent.NONE);
            } catch (RuntimeException e) {
                LOG.error("Could not apply combat stats for {}", player.getGameProfile().getName(), e);
            }
        }
    }

    private void handleEvent(ServerPlayer player, Combatant combatant, CombatEvent event) {
        if (event instanceof CombatEvent.Hit hit) {
            CombatHits.execute(player, combatant.stats(), hit, player.getRandom()::nextDouble);
        } else if (event instanceof CombatEvent.ActionStarted start) {
            CombatEffects.actionStarted(player, start);
        }
    }

    /** Weapon switches cancel actions; the guard follows vanilla's "using item" state in both directions. */
    private static void syncEquipment(ServerPlayer player, Combatant combatant) {
        combatant.updateWeapon(WeaponItem.profileOf(player.getMainHandItem()));
        boolean usingWeapon = isUsingWeapon(player);
        if (combatant.isGuarding() && !usingWeapon) {
            combatant.stopGuard();
        } else if (!combatant.isGuarding() && usingWeapon) {
            player.stopUsingItem(); // e.g. guard broken or dodged while right click is still held
        }
    }

    private static boolean isUsingWeapon(ServerPlayer player) {
        return player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && player.getUseItem().getItem() instanceof WeaponItem;
    }

    // ------------------------------------------------------------------ requests

    /** A validated request from the network layer (sender authenticated, rate limited, data loaded). */
    public void handleRequest(ServerPlayer player, CombatRequest request, int sequence) {
        Combatant combatant = service.get(player.getUUID()).orElse(null);
        RequestOutcome outcome;
        if (combatant == null || !player.isAlive() || player.isSpectator()) {
            outcome = RequestOutcome.REJECTED_NOT_ALLOWED;
        } else {
            syncEquipment(player, combatant);
            boolean restricted = player.isPassenger() || player.isFallFlying();
            outcome = combatant.submit(request, now(), WeaponItem.profileOf(player.getMainHandItem()), restricted);
        }
        net(player.getUUID()).lastSequence = sequence;
        sendStatus(player, combatant, true, outcome, DefenseEvent.NONE);
    }

    /**
     * Right click with a weapon on the server: may the guard start? A refusal is reported to the client, which may
     * have predicted the guard (it then drops the pose instead of showing a guard that does not protect).
     */
    public boolean startGuard(ServerPlayer player, WeaponProfile weapon) {
        Combatant combatant = service.get(player.getUUID()).orElse(null);
        if (combatant == null || !player.isAlive()) {
            return false;
        }
        boolean started = combatant.startGuard(now(), weapon);
        if (!started) {
            sendStatus(player, combatant, false, RequestOutcome.IGNORED, DefenseEvent.GUARD_REFUSED);
        }
        return started;
    }

    // ------------------------------------------------------------------ defense

    /** LivingAttackEvent: @return true when the attack is completely avoided (dodge or parry). */
    public boolean avoid(ServerPlayer player, DamageSource source) {
        Combatant combatant = service.get(player.getUUID()).orElse(null);
        if (combatant == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        syncEquipment(player, combatant);
        boolean evadable = source.getEntity() != null || source.getDirectEntity() != null
                || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_PROJECTILE);
        AvoidOutcome outcome = combatant.checkAvoid(now(), evadable, isGuardable(player, combatant, source));
        switch (outcome) {
            case DODGED -> {
                CombatEffects.dodged(player);
                sendStatus(player, combatant, false, RequestOutcome.IGNORED, DefenseEvent.DODGED);
                return true;
            }
            case PARRIED -> {
                stunAttacker(player, source);
                CombatEffects.parried(player);
                sendStatus(player, combatant, false, RequestOutcome.IGNORED, DefenseEvent.PARRIED);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** LivingHurtEvent: guard, then the VIT damage reduction for damage that has an attacker. */
    public float defend(ServerPlayer player, DamageSource source, float amount) {
        Combatant combatant = service.get(player.getUUID()).orElse(null);
        if (combatant == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return amount;
        }
        syncEquipment(player, combatant);
        GuardResult guard = combatant.applyGuard(now(), isGuardable(player, combatant, source), amount);
        double result = guard.amount();
        if (guard.outcome() == GuardResult.Outcome.GUARDED) {
            CombatEffects.guarded(player);
            sendStatus(player, combatant, false, RequestOutcome.IGNORED, DefenseEvent.GUARDED);
        } else if (guard.outcome() == GuardResult.Outcome.GUARD_BROKEN) {
            player.stopUsingItem();
            CombatEffects.guardBroken(player);
            sendStatus(player, combatant, false, RequestOutcome.IGNORED, DefenseEvent.GUARD_BROKEN);
        }
        if (source.getEntity() != null) {
            result = DamageCalculator.reduce(result, combatant.stats().damageReduction());
        }
        return (float) result;
    }

    /** Guarding players take reduced knockback. */
    public float knockbackFactor(ServerPlayer player) {
        return service.get(player.getUUID()).filter(Combatant::isGuarding).isPresent() ? GUARD_KNOCKBACK_FACTOR : 1.0F;
    }

    /**
     * Guardable: guarding, the damage has a direct source (attacker or projectile), it is not shield-bypassing or an
     * explosion, and it comes from inside the weapon's guard arc.
     */
    private static boolean isGuardable(ServerPlayer player, Combatant combatant, DamageSource source) {
        if (!combatant.isGuarding() || combatant.weapon() == null || source.getDirectEntity() == null
                || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return false;
        }
        double angle = HitGeometry.horizontalAngle(player.getYRot(), from.x - player.getX(), from.z - player.getZ());
        return angle <= combatant.weapon().guard().arcHalfAngleDegrees();
    }

    /** A parried melee attacker is pushed back and slowed (not the shooter of a parried projectile). */
    private static void stunAttacker(ServerPlayer player, DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker == null || attacker != source.getDirectEntity() || attacker == player
                || !(attacker instanceof LivingEntity living) || !living.isAlive()
                || living.distanceToSqr(player) > PARRY_STUN_RANGE * PARRY_STUN_RANGE) {
            return;
        }
        living.knockback(PARRY_KNOCKBACK, player.getX() - living.getX(), player.getZ() - living.getZ());
        living.hurtMarked = true;
        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PARRY_SLOW_TICKS, PARRY_SLOW_AMPLIFIER),
                player);
    }

    // ------------------------------------------------------------------ lifecycle

    public void onDeath(ServerPlayer player) {
        service.get(player.getUUID()).ifPresent(Combatant::reset);
    }

    /** The respawned player is a new entity: re-apply the max health bonus and fill it up after a death. */
    public void onRespawn(ServerPlayer player, boolean endConquered) {
        service.get(player.getUUID()).ifPresent(combatant -> {
            combatant.reset();
            StatEffects.apply(player, combatant.stats());
            if (!endConquered) {
                player.setHealth(player.getMaxHealth());
            }
            sendStatus(player, combatant, false, RequestOutcome.IGNORED, DefenseEvent.NONE);
        });
    }

    public void onDimensionChanged(ServerPlayer player) {
        service.get(player.getUUID()).ifPresent(Combatant::cancelAction);
    }

    public void onPlayerLeft(UUID playerId) {
        service.remove(playerId);
        net.remove(playerId);
        limiter.forget(playerId);
        statsDirty.forget(playerId);
    }

    // ------------------------------------------------------------------ status

    private NetState net(UUID playerId) {
        return net.computeIfAbsent(playerId, id -> new NetState());
    }

    private void sendStatus(ServerPlayer player, Combatant combatant, boolean answersRequest, RequestOutcome outcome,
                            DefenseEvent event) {
        NetState state = net(player.getUUID());
        long now = now();
        double stamina = combatant == null ? 0.0 : combatant.stamina();
        int cooldown = combatant == null ? 0 : combatant.skillCooldownRemaining(now);
        int cooldownTotal = combatant == null ? 0 : combatant.skillCooldownTotal();
        ERNetwork.sendToPlayer(player, new CombatStatusPacket(state.lastSequence, answersRequest, outcome,
                (float) stamina, cooldown, cooldownTotal, event));
        state.lastSentTick = now;
        state.lastSentStamina = stamina;
        state.lastSentCooldown = cooldown;
    }

    private void resyncStatus(ServerPlayer player, Combatant combatant, long now) {
        NetState state = net(player.getUUID());
        if (now - state.lastSentTick < STATUS_RESYNC_TICKS) {
            return;
        }
        int cooldown = combatant.skillCooldownRemaining(now);
        boolean changed = Math.abs(combatant.stamina() - state.lastSentStamina) > 1.0e-6
                || (cooldown > 0) != (state.lastSentCooldown > 0);
        if (changed) {
            sendStatus(player, combatant, false, RequestOutcome.IGNORED, DefenseEvent.NONE);
        }
    }
}
