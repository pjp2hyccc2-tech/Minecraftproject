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
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.SkillBinding;
import com.eternalrealm.job.ComposedStats;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.PassiveTotals;
import com.eternalrealm.job.SkillTheme;
import com.eternalrealm.item.WeaponArt;
import com.eternalrealm.item.WeaponArts;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.job.SkillTree;
import com.eternalrealm.job.forge.PlayerStats;
import com.eternalrealm.job.forge.SkillEffectsExecutor;
import com.eternalrealm.job.forge.SkillZones;
import com.eternalrealm.combat.DefenseEvent;
import com.eternalrealm.combat.GuardResult;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.HitGeometry;
import com.eternalrealm.combat.Knockback;
import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.RequestOutcome;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.network.TokenBucketRateLimiter;
import com.eternalrealm.network.forge.CombatStatsPacket;
import com.eternalrealm.network.forge.CombatStatusPacket;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.PlayerActionPacket;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.SyncQueue;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
    /** A player counts as in combat this long after dealing or taking damage. */
    private static final int OUT_OF_COMBAT_TICKS = 100;
    /** Upper bound of damage reflected by thorns in one hit. */
    private static final double MAX_REFLECT = 500.0;
    /** Vanilla ignores most hits while {@code invulnerableTime} is above this (half of its 20-tick hurt cooldown). */
    private static final int HURT_COOLDOWN_THRESHOLD = 10;

    private final MinecraftServer server;
    private final PlayerDataService playerData;
    private final SyncQueue statsDirty;
    private final TokenBucketRateLimiter<UUID> limiter;
    private final CombatService service = new CombatService();
    private final Map<UUID, NetState> net = new HashMap<>();
    /** Learned skill levels and passive totals per online player (rebuilt whenever the character changes). */
    private final Map<UUID, PlayerSkills> skills = new HashMap<>();
    private CombatTuning tuning;

    /**
     * What the server needs of a player's skill tree while hits land: the passive totals (learned passives plus the
     * perks of the worn named equipment) and the learned skill levels.
     */
    private record PlayerSkills(PassiveTotals passives, Map<String, Integer> levels) {
        static final PlayerSkills NONE = new PlayerSkills(PassiveTotals.NONE, Map.of());

        PlayerSkills withGear(PassiveTotals gear) {
            return gear == PassiveTotals.NONE ? this : new PlayerSkills(passives.plus(gear), levels);
        }
    }

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
        long lastCombatTick = Long.MIN_VALUE / 4;
        double lastSentStamina = -1.0;
        final boolean[] lastSentCooling = new boolean[Combatant.SKILL_SLOTS];
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

    /** Equipment changed: the player's stats are re-derived on the next tick. */
    public void markStatsDirty(UUID playerId) {
        statsDirty.mark(playerId);
    }

    /** The combat clock. */
    public long now() {
        return server.getTickCount();
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        applyChangedStats();
        SkillZones.tick(server);
        WeaponSkillFx.tickScheduled();
        ArtFx.tickScheduled();
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
                    handleEvent(player, combatant.stats(), event);
                }
                WeaponSkillFx.tick(player, combatant, now);
                ArtFx.tick(player, combatant, now);
                resyncStatus(player, combatant, now);
            } catch (RuntimeException e) {
                // One broken fighter must not stop combat for everyone else.
                LOG.error("Combat tick failed for {}; cancelling their action", player.getGameProfile().getName(), e);
                combatant.cancelAction();
            }
        }
    }

    /** Whether the player dealt or took damage in the last {@link #OUT_OF_COMBAT_TICKS} ticks. */
    public boolean inCombat(ServerPlayer player) {
        long now = now();
        NetState state = net(player.getUUID());
        long lastCombat = Math.max(state.lastCombatTick, player.getLastHurtMobTimestamp() == 0 ? Long.MIN_VALUE / 4
                : now - (player.tickCount - player.getLastHurtMobTimestamp()));
        return now - lastCombat < OUT_OF_COMBAT_TICKS || player.hurtTime > 0;
    }

    /** Taking damage (any source) puts the player in combat for {@link #OUT_OF_COMBAT_TICKS}. */
    public void markInCombat(ServerPlayer player) {
        net(player.getUUID()).lastCombatTick = now();
    }

    private void applyChangedStats() {
        for (UUID playerId : statsDirty.drain()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            Optional<CharacterSnapshot> snapshot = playerData.snapshot(playerId);
            if (player == null || snapshot.isEmpty()) {
                continue;
            }
            try {
                CharacterSnapshot character = snapshot.get();
                JobType job = JobType.fromStored(character.jobId());
                skills.put(playerId, skillsOf(job, character).withGear(PlayerStats.perks(player)));
                ComposedStats composed = PlayerStats.compose(player, character);
                CombatStats stats = CombatStats.derive(composed.total(), character.level(), tuning,
                        composed.modifiers());
                Combatant combatant = service.register(playerId, stats, now());
                SkillBinding[] bindings = SkillTree.bindings(job, character.level(), character.skillInvestments(),
                        character.skillLoadout());
                for (int slot = 1; slot < Combatant.SKILL_SLOTS; slot++) {
                    combatant.setJobSkill(slot, bindings[slot]);
                }
                combatant.setAllowedWeapons(Jobs.get(job).weapons());
                StatEffects.apply(player, stats);
                ERNetwork.sendToPlayer(player, new CombatStatsPacket(stats));
                // the character screen shows what the gear adds (flat and percentage stats): keep it current
                ERNetwork.sendToPlayer(player, new com.eternalrealm.network.forge.CharacterSnapshotPacket(character,
                        composed.bonus(), composed.percents()));
                sendStatus(player, combatant, false, RequestOutcome.IGNORED, DefenseEvent.NONE);
            } catch (RuntimeException e) {
                LOG.error("Could not apply combat stats for {}", player.getGameProfile().getName(), e);
            }
        }
    }

    private static PlayerSkills skillsOf(JobType job, CharacterSnapshot character) {
        if (!job.isChosen()) {
            return PlayerSkills.NONE;
        }
        Map<String, Integer> levels = new HashMap<>();
        for (SkillDefinition skill : Jobs.get(job).skills()) {
            int level = SkillTree.levelOf(skill, character.level(), character.skillInvestments());
            if (level > 0) {
                levels.put(skill.id(), level);
            }
        }
        return new PlayerSkills(SkillTree.passives(job, character.level(), character.skillInvestments()),
                Map.copyOf(levels));
    }

    /** The player's passive skill totals (none before the character loaded). */
    public PassiveTotals passives(ServerPlayer player) {
        return skills.getOrDefault(player.getUUID(), PlayerSkills.NONE).passives();
    }

    /** Learned level of {@code skillId} (1 when unknown, e.g. a scripted call before the character loaded). */
    public int levelOf(UUID playerId, String skillId) {
        return Math.max(1, skills.getOrDefault(playerId, PlayerSkills.NONE).levels().getOrDefault(skillId, 1));
    }

    /**
     * How much the held piece's rarity raises the weapon art's damage and effects ({@code ItemRarity#power}, owner's
     * request of 2026-11-11): 1 for a plain common piece and for an art that is not the held weapon's.
     */
    public static double artPower(ServerPlayer player, WeaponArt art) {
        ItemStack held = player.getMainHandItem();
        WeaponProfile profile = WeaponItem.profileOf(held);
        if (profile == null || !profile.skill().id().equals(art.id())) {
            return 1.0;
        }
        return com.eternalrealm.item.forge.EquipmentStacks.rarity(held)
                .map(com.eternalrealm.item.ItemRarity::power).orElse(1.0);
    }

    /** The character's level, if their data is loaded. */
    static java.util.OptionalInt characterLevel(ServerPlayer player) {
        return ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(snapshot -> java.util.OptionalInt.of(snapshot.level())).orElse(java.util.OptionalInt.empty());
    }

    /**
     * The held weapon asks for a higher level than the character's (or is bound to someone else): it cannot attack,
     * guard or use its art. A player without character data (not loaded yet, a fake player) is not stopped.
     *
     * @return the level it needs, or 0 when it may be used
     */
    public static int refusedLevel(ServerPlayer player) {
        java.util.OptionalInt level = characterLevel(player);
        if (level.isEmpty()) {
            return 0;
        }
        return com.eternalrealm.item.forge.EquipmentStacks.read(player.getMainHandItem())
                .filter(com.eternalrealm.item.EquipmentData::isWeapon)
                .filter(data -> !data.canBeUsedBy(level.getAsInt(), player.getUUID()))
                .map(com.eternalrealm.item.EquipmentData::requiredLevel).orElse(0);
    }

    private static void tellLevel(ServerPlayer player, int level) {
        // (not "net.minecraft...": the field "net" would shadow the package)
        player.displayClientMessage(Component.translatable("message.eternalrealm.equipment.weapon_level", level,
                player.getMainHandItem().getHoverName()).withStyle(ChatFormatting.RED), true);
    }

    private static int skillLevel(ServerPlayer player, SkillDefinition skill) {
        int level = ServerContext.current().map(context -> context.combat().levelOf(player.getUUID(), skill.id()))
                .orElse(1);
        return Math.min(level, skill.maxLevel());
    }

    /** A skill effect gives stamina back; the client learns it from the status sent right away. */
    public void restoreStamina(ServerPlayer player, double amount) {
        service.get(player.getUUID()).ifPresent(combatant -> {
            combatant.addStamina(amount);
            sendStatus(player, combatant, false, RequestOutcome.IGNORED, DefenseEvent.NONE);
        });
    }

    /** A skill effect shortens the running job skill cooldowns; the client learns it from the status sent now. */
    public void cutCooldowns(ServerPlayer player, int ticks) {
        service.get(player.getUUID()).ifPresent(combatant -> {
            combatant.reduceJobSkillCooldowns(now(), ticks);
            sendStatus(player, combatant, false, RequestOutcome.IGNORED, DefenseEvent.NONE);
        });
    }

    /**
     * Hit frames become damage (melee volume or projectiles) with the damage type of the weapon or of the job skill;
     * job skills also run their effects for the frame at their learned level (afflictions, executes and siphons go
     * to what the frame damaged).
     */
    public static void handleEvent(ServerPlayer player, CombatStats stats, CombatEvent event) {
        String actionId = event instanceof CombatEvent.Hit hit ? hit.action().id()
                : event instanceof CombatEvent.ActionStarted start ? start.action().id() : null;
        if (actionId == null) {
            process(player, stats, event);
        } else {
            // a skill locked on to an enemy starts and hits facing it, and its look-placed areas land on it (D-LOCKON)
            SkillLocks.during(player, actionId, player.server.getTickCount(), () -> process(player, stats, event));
        }
    }

    private static void process(ServerPlayer player, CombatStats stats, CombatEvent event) {
        if (event instanceof CombatEvent.Hit original) {
            SkillDefinition skill = Jobs.skillOfAction(original.action().id()).orElse(null);
            // a weapon art: its effects run like a job skill's, as strong as the held piece's rarity makes them
            WeaponArt art = skill == null ? WeaponArts.byId(original.action().id()).orElse(null) : null;
            double artPower = art == null ? 1.0 : artPower(player, art);
            DamageType type = skill != null ? skill.damageType() : original.weapon().type().damageType();
            // magic and bow attacks push nothing (D-RANGEDKB); the spellblade's sword blows keep their push
            boolean swordBlow = skill != null && skill.job() == JobType.SPELLBLADE && !original.frame().isProjectile();
            HitFrame frame = original.frame().withKnockback(
                    Knockback.of(original.frame().knockback(), type, swordBlow));
            CombatEvent.Hit hit = artPower == 1.0 && frame == original.frame() ? original
                    : new CombatEvent.Hit(original.weapon(), original.action(), original.frameIndex(), frame,
                    original.damageScale() * artPower, original.tick());
            List<SkillEffect> effects = skill != null ? skill.effectsAt(hit.frameIndex())
                    : art != null ? art.effectsAt(hit.frameIndex()) : List.of();
            SkillTheme theme = skill != null ? skill.theme() : art != null ? art.theme() : null;
            int level = skill != null ? skillLevel(player, skill) : 1;
            double scale = skill != null ? skill.effectScale(level) : artPower;
            List<SkillEffect.Afflict> afflictions = SkillEffectsExecutor.afflictions(effects);
            List<SkillEffect.Execute> executes = SkillEffectsExecutor.only(effects, SkillEffect.Execute.class);
            List<CombatHits.HitTarget> targets = List.of();
            List<SkillEffect> rest = effects;
            if (hit.frame().damaging()) {
                if (hit.frame().isProjectile()) {
                    CombatProjectiles.launch(player, stats, hit, type, afflictions, player.getRandom()::nextDouble,
                            scale, SkillEffectsExecutor.only(effects, SkillEffect.Siphon.class), executes, theme);
                    // siphons of projectiles heal on impact
                    rest = effects.stream().filter(effect -> !(effect instanceof SkillEffect.Siphon)).toList();
                } else {
                    targets = CombatHits.execute(player, stats, hit, type, player.getRandom()::nextDouble, executes,
                            theme);
                    Afflictions.apply(player, targets, afflictions, scale, player.position());
                }
            }
            if (skill == null && art == null && WeaponSkillFx.isWeaponSkill(hit.action())) {
                WeaponSkillFx.frame(player, hit);
            }
            if (art != null && ArtFx.isGenerated(hit.action())) {
                ArtFx.frame(player, hit); // the composed look of a named or tier weapon's art
            }
            if (!rest.isEmpty()) {
                if (art != null) {
                    SkillEffectsExecutor.applyArt(player, stats, art, hit.weapon(), hit.damageScale(), artPower, rest,
                            targets);
                } else {
                    SkillEffectsExecutor.apply(player, stats, skill, level, hit.weapon(), hit.damageScale(), rest,
                            targets);
                }
            }
        } else if (event instanceof CombatEvent.ActionStarted start) {
            CombatEffects.actionStarted(player, start);
            WeaponSkillFx.started(player, start.weapon(), start.action());
            ArtFx.started(player, start.weapon(), start.action());
            ERNetwork.sendToTracking(player, new PlayerActionPacket(player.getId(), start.action().id(),
                    start.comboStep(), start.action().timing(stats.actionSpeedFactor()).duration(), false));
            Jobs.skillOfAction(start.action().id()).ifPresent(skill ->
                    SkillEffectsExecutor.apply(player, stats, skill, skillLevel(player, skill), start.weapon(),
                            skill.damageScale(skillLevel(player, skill)), skill.effectsAt(SkillEffect.ON_START),
                            List.of()));
            WeaponArts.byId(start.action().id()).ifPresent(art -> {
                double power = artPower(player, art);
                SkillEffectsExecutor.applyArt(player, stats, art, start.weapon(), power, power,
                        art.effectsAt(SkillEffect.ON_START), List.of());
            });
        } else if (event instanceof CombatEvent.ChargeStarted charge) {
            ERNetwork.sendToTracking(player, new PlayerActionPacket(player.getId(),
                    charge.weapon().heavy().strike().id(), -1, charge.weapon().heavy().maxChargeTicks(), true));
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
        handleRequest(player, request, sequence, -1);
    }

    /**
     * A validated request; a skill aimed by holding its key names the enemy it is locked on ({@code targetId}, -1 for
     * none), which {@link SkillLocks} checks before keeping the skill on it.
     */
    public void handleRequest(ServerPlayer player, CombatRequest request, int sequence, int targetId) {
        Combatant combatant = service.get(player.getUUID()).orElse(null);
        RequestOutcome outcome;
        int refused = request == CombatRequest.DODGE ? 0 : refusedLevel(player);
        if (combatant == null || !player.isAlive() || player.isSpectator()
                || com.eternalrealm.revival.forge.RevivalService.isDown(player.getUUID())) {
            outcome = RequestOutcome.REJECTED_NOT_ALLOWED;
        } else if (refused > 0) {
            outcome = RequestOutcome.REJECTED_NOT_ALLOWED; // the client refuses first and says why
            tellLevel(player, refused);
        } else {
            syncEquipment(player, combatant);
            boolean restricted = player.isPassenger() || player.isFallFlying();
            boolean usingOtherItem = player.isUsingItem() && !isUsingWeapon(player);
            outcome = combatant.submit(request, now(), WeaponItem.profileOf(player.getMainHandItem()), restricted,
                    usingOtherItem);
            if (request.isSkill() && (outcome == RequestOutcome.ACCEPTED || outcome == RequestOutcome.BUFFERED)) {
                ActionDefinition action = skillAction(combatant, request.skillSlot());
                if (action != null && targetId >= 0) {
                    // a buffered skill starts at the current action's cancel point: the lock waits for it
                    SkillLocks.lock(player, targetId, action, action.durationTicks() + 40, now());
                } else if (action != null) {
                    SkillLocks.clear(player, action.id()); // only this skill's own earlier lock
                }
            }
        }
        net(player.getUUID()).lastSequence = sequence;
        sendStatus(player, combatant, true, outcome, DefenseEvent.NONE);
    }

    /** The action a skill request uses: the weapon's skill (slot 0) or the job skill in the slot. */
    private static ActionDefinition skillAction(Combatant combatant, int slot) {
        if (slot == 0) {
            return combatant.weapon() == null ? null : combatant.weapon().skill();
        }
        com.eternalrealm.combat.SkillBinding binding = combatant.jobSkill(slot);
        return binding == null ? null : binding.action();
    }

    /**
     * Right click with a weapon on the server: may the guard start? Only the server starts guards (D-035); the client
     * shows the guard when vanilla syncs the "using item" state back to it.
     */
    public boolean startGuard(ServerPlayer player, WeaponProfile weapon) {
        Combatant combatant = service.get(player.getUUID()).orElse(null);
        if (combatant == null || !player.isAlive()) {
            return false;
        }
        int refused = refusedLevel(player);
        if (refused > 0) {
            tellLevel(player, refused);
            return false;
        }
        return combatant.startGuard(now(), weapon);
    }

    // ------------------------------------------------------------------ defense

    /**
     * LivingAttackEvent: @return true when the attack is completely avoided (dodge or parry).
     *
     * <p>For players this event fires before vanilla's creative-invulnerability and hurt-cooldown checks; hits vanilla
     * would ignore anyway must not trigger parry rewards, so they are left to vanilla.</p>
     */
    public boolean avoid(ServerPlayer player, DamageSource source) {
        Combatant combatant = service.get(player.getUUID()).orElse(null);
        if (combatant == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || player.getAbilities().invulnerable || player.invulnerableTime > HURT_COOLDOWN_THRESHOLD) {
            return false;
        }
        syncEquipment(player, combatant);
        boolean evadable = source.getEntity() != null || source.getDirectEntity() != null
                || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_PROJECTILE);
        AvoidOutcome outcome = combatant.checkAvoid(now(), evadable, isGuardable(player, combatant, source));
        if (outcome == AvoidOutcome.NONE && evadable && source.getEntity() != null && source.getEntity() != player) {
            double evasion = Math.min(PassiveTotals.MAX_EVASION + 0.25, passives(player).evasion()
                    + ERMobEffects.magnitude(player, ERMobEffects.EVASION.get()));
            if (evasion > 0.0 && player.getRandom().nextDouble() < evasion) {
                outcome = AvoidOutcome.DODGED;
            }
        }
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
        markInCombat(player);
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
        result *= ERMobEffects.defenseMultiplier(player);
        PassiveTotals passive = passives(player);
        if (passive.lastStandReduction() > 0.0 && player.getHealth() < player.getMaxHealth() * CombatHits.LAST_STAND_HEALTH) {
            result *= 1.0 - passive.lastStandReduction();
        }
        reflect(player, source, result, passive);
        return (float) result;
    }

    /** Thorns (passives and the thorns buff) send part of the damage back to a living attacker. */
    private static void reflect(ServerPlayer player, DamageSource source, double taken, PassiveTotals passive) {
        double thorns = passive.thorns() + ERMobEffects.magnitude(player, ERMobEffects.THORNS.get());
        if (thorns <= 0.0 || taken <= 0.0 || !(source.getEntity() instanceof LivingEntity attacker)
                || attacker == player || !attacker.isAlive() || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypes.THORNS)
                || attacker.distanceToSqr(player) > 64.0) {
            return;
        }
        float amount = (float) Math.min(MAX_REFLECT, taken * Math.min(1.5, thorns));
        if (amount > 0.0F) {
            attacker.hurt(player.damageSources().thorns(player), amount);
        }
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

    /**
     * Leaving the End creates a new player entity without dying. Re-apply the max health bonus and keep the health the
     * player had (vanilla may have clamped it to the default maximum before the bonus was back).
     */
    public void onCloned(ServerPlayer player, float previousHealth) {
        service.get(player.getUUID()).ifPresent(combatant -> {
            StatEffects.apply(player, combatant.stats());
            player.setHealth(Math.min(previousHealth, player.getMaxHealth()));
        });
    }

    public void onDimensionChanged(ServerPlayer player) {
        service.get(player.getUUID()).ifPresent(Combatant::cancelAction);
    }

    public void onPlayerLeft(UUID playerId) {
        WeaponSkillFx.forget(playerId);
        ArtFx.forget(playerId);
        service.remove(playerId, now());
        net.remove(playerId);
        skills.remove(playerId);
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
        int[] cooldowns = new int[Combatant.SKILL_SLOTS];
        int[] totals = new int[Combatant.SKILL_SLOTS];
        for (int slot = 0; slot < Combatant.SKILL_SLOTS; slot++) {
            cooldowns[slot] = combatant == null ? 0 : combatant.skillCooldownRemaining(slot, now);
            totals[slot] = combatant == null ? 0 : combatant.skillCooldownTotal(slot);
            state.lastSentCooling[slot] = cooldowns[slot] > 0;
        }
        ERNetwork.sendToPlayer(player, new CombatStatusPacket(state.lastSequence, answersRequest, outcome,
                (float) stamina, cooldowns, totals, event));
        state.lastSentTick = now;
        state.lastSentStamina = stamina;
    }

    private void resyncStatus(ServerPlayer player, Combatant combatant, long now) {
        NetState state = net(player.getUUID());
        if (now - state.lastSentTick < STATUS_RESYNC_TICKS) {
            return;
        }
        boolean changed = Math.abs(combatant.stamina() - state.lastSentStamina) > 1.0e-6;
        for (int slot = 0; slot < Combatant.SKILL_SLOTS && !changed; slot++) {
            changed = (combatant.skillCooldownRemaining(slot, now) > 0) != state.lastSentCooling[slot];
        }
        if (changed) {
            sendStatus(player, combatant, false, RequestOutcome.IGNORED, DefenseEvent.NONE);
        }
    }
}
