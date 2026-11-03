package com.reisatsu.entity;

import com.reisatsu.game.GameManager;
import com.reisatsu.game.NoiseSystem;
import com.reisatsu.game.Sight;
import com.reisatsu.game.Stealth;
import com.reisatsu.registry.ModParticles;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A spirit of HIDAMARI (docs/DESIGN.md §3.3, §5). Every spirit keeps an awareness 0..1 of each player, fed by sight
 * (view cone, line of sight, light), hearing (the {@link NoiseSystem}) and, for some, breathing; when it reaches 1 the
 * spirit NOTICEs you and HUNTs. Break its line of sight for 5 s and it loses you and goes back to its routine — wary
 * for 30 s (photos of it fail meanwhile). A valid photo BINDs it: it is pulled into the lens and gone.
 * <p>
 * Spirits are ghosts: they glide over the floor at feet level ({@link Layout#FEET_Y}) without physics, pass through
 * doors and furniture on paths computed by the vanilla path finder (walls still matter), and are never saved: the
 * game spawns them from its own progress data ({@code game/Spirits}).
 */
public abstract class Spirit extends PathfinderMob implements Animated {
    private static final EntityDataAccessor<Integer> DATA_POSE = SynchedEntityData.defineId(Spirit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(Spirit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_GLITCH = SynchedEntityData.defineId(Spirit.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AWARE = SynchedEntityData.defineId(Spirit.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_FADE = SynchedEntityData.defineId(Spirit.class, EntityDataSerializers.FLOAT);

    public static final int F_HUNTING = 1, F_PHOTOGENIC = 2, F_BOUND = 4, F_EXORCISM = 8, F_SHOWN = 16, F_NOTICE = 32, F_ALERT = 64;

    public enum Mode { ROUTINE, INVESTIGATE, NOTICE, HUNT, RETURN, BOUND, EXORCISM, FROZEN }

    protected Mode mode = Mode.ROUTINE;
    protected int modeTicks;
    private int poseStart;
    private final Map<UUID, Float> awareness = new HashMap<>();
    private final Map<UUID, Integer> lastPerceived = new HashMap<>();
    /** Players this spirit saw climbing into a locker (they can be found there). */
    private final Set<UUID> sawHide = new HashSet<>();
    @Nullable protected UUID huntId;
    protected int lostTicks;
    /** After a hunt: wary, photos of it fail. */
    public int waryTicks;
    @Nullable protected Vec3 investigatePos;
    @Nullable protected Vec3 lookAtUntil;
    protected int lookAtTicks;
    private long lastNoise = NoiseSystem.lastId();
    private int spikeTicks;
    /** Debug: frozen in a pose (screenshots), {@code shown}: forced visible to the naked eye. */
    public boolean debugShown;

    // ---- path following
    @Nullable private List<Vec3> path;
    private int pathIndex;
    @Nullable private BlockPos pathGoal;
    private int pathAge;

    // ---- bound into a photo / exorcism
    @Nullable private Vec3 boundTo;
    private int exorcismSlot = -1;
    private float fadeTarget = 1.0f;
    private float fadeSpeed = 0.05f;

    protected Spirit(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
        setNoGravity(true);
        noPhysics = true;
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public abstract SpiritKind kind();

    // ================================================================================================ synced data
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_POSE, 0);
        entityData.define(DATA_FLAGS, 0);
        entityData.define(DATA_GLITCH, 0.0f);
        entityData.define(DATA_AWARE, 0.0f);
        entityData.define(DATA_FADE, 1.0f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_POSE.equals(key)) poseStart = tickCount;
    }

    @Override
    public int pose() {
        return entityData.get(DATA_POSE);
    }

    @Override
    public float poseTime(float partialTick) {
        return tickCount - poseStart + partialTick;
    }

    @Override
    public float glitch(float partialTick) {
        return entityData.get(DATA_GLITCH);
    }

    public int flags() {
        return entityData.get(DATA_FLAGS);
    }

    public boolean flag(int f) {
        return (flags() & f) != 0;
    }

    /** Highest awareness of any player (client: the HUD eye, the head turning toward you). */
    public float awareShown() {
        return entityData.get(DATA_AWARE);
    }

    /** 0..1 visibility multiplier (fading in at the exorcism, out when bound). */
    public float fade() {
        return entityData.get(DATA_FADE);
    }

    public int poseTicks() {
        return tickCount - poseStart;
    }

    public void setAnimPose(int pose) {
        if (entityData.get(DATA_POSE) != pose) {
            entityData.set(DATA_POSE, pose);
            poseStart = tickCount;
        }
    }

    public Mode mode() {
        return mode;
    }

    protected void setMode(Mode m) {
        if (mode != m) {
            mode = m;
            modeTicks = 0;
            clearPath();
        }
    }

    // ================================================================================================ per-spirit tuning
    /** Half angle of the view cone (degrees); 0 = blind right now. */
    public float coneHalfAngle() {
        return 60.0f;
    }

    /** Multiplier on the sight range (6 dark / 14 lit). */
    public float rangeMul() {
        return 1.0f;
    }

    /** Extra downward tilt of the view (serving, plating: the cone points at the table). */
    public float lookDownTilt() {
        return 0.0f;
    }

    public float hearingMul() {
        return 1.0f;
    }

    /** Hearing for a noise at this position (the chef hears the kitchen best). */
    public float hearingMulAt(Vec3 pos) {
        return hearingMul();
    }

    /** A hard sight range that ignores light and sneaking (0 = the usual 6 / 14). */
    public float fixedRange() {
        return 0.0f;
    }

    /** Hears a player's breathing within this many blocks (0 = never). */
    public float breathRange() {
        return 0.0f;
    }

    /** Other eyes than its own (the manager's security cameras): awareness per tick. */
    protected float extraSight(ServerLevel level, ServerPlayer p) {
        return 0.0f;
    }

    /** Walks over to look when it hears something. */
    protected boolean investigates() {
        return true;
    }

    /** Is the spirit in a state its photo can be taken in (§5 photo windows)? Awareness and wariness are checked apart. */
    public abstract boolean photogenic();

    /** Max angle between its face and the camera for the face to count (§3.1: 110°). */
    public float faceAngle() {
        return 110.0f;
    }

    /** Hunting speed in blocks per tick (your sprint is ~0.28). */
    protected double huntSpeed() {
        return 0.24;
    }

    protected double walkSpeed() {
        return 0.075;
    }

    /** May it chase this player where they are now (the chef stays in the kitchen)? */
    protected boolean mayHunt(ServerPlayer p) {
        return true;
    }

    protected abstract int poseWalk();

    protected abstract int poseStand();

    protected abstract int poseNotice();

    protected abstract int poseHunt();

    /** The head/look pose while searching (falls back to standing). */
    protected int poseLook() {
        return poseStand();
    }

    /** Where it goes back to after a hunt or an investigation. */
    protected abstract Vec3 home();

    /** The per-tick routine (patrols, cycles). */
    protected abstract void routine(ServerLevel level);

    /** Called once when its routine is interrupted (a noise, a notice), to tidy up (taps, devices...). */
    protected void interrupted() {
    }

    /** Called when it gives up a hunt or finishes investigating. */
    protected void resumeRoutine() {
    }

    /** Ambient sounds and particles, every tick. */
    protected void ambient(ServerLevel level) {
    }

    /** The NOTICE sting: the universal one plus the spirit's own. */
    protected void onNotice(ServerPlayer p) {
        play(ModSounds.SPIRIT_NOTICE.get(), 1.2f, 0.95f + random.nextFloat() * 0.1f);
    }

    /** NOTICE lasts this many ticks before the hunt. */
    protected int noticeTicks() {
        return 22;
    }

    /** Called when the hunt ends (caught = it touched the player). */
    protected void onHuntEnd(boolean caught) {
    }

    /** Reaction when awareness of a player becomes full. Default: NOTICE, then HUNT. */
    protected void onAwarenessFull(ServerPlayer p) {
        startNotice(p);
    }

    /** The client's naked-eye alpha for this spirit right now (the finder always shows 1). */
    public float eyeAlpha() {
        if (debugShown || flag(F_SHOWN)) return 1.0f;
        return kind().eyeAlpha;
    }

    // ================================================================================================ awareness
    public float awarenessOf(Player p) {
        return awareness.getOrDefault(p.getUUID(), 0.0f);
    }

    public float maxAwareness() {
        float m = 0;
        for (float f : awareness.values()) m = Math.max(m, f);
        return m;
    }

    /** §3.1 rule 5: has it noticed you (or anyone, or is it still wary after a hunt)? */
    public boolean hasNoticed(Player p) {
        return awarenessOf(p) >= 1.0f || mode == Mode.NOTICE || mode == Mode.HUNT || waryTicks > 0;
    }

    public void setAwareness(Player p, float v) {
        awareness.put(p.getUUID(), Mth.clamp(v, 0.0f, 1.0f));
        lastPerceived.put(p.getUUID(), tickCount);
    }

    /** Raises the awareness of a player; full awareness triggers the spirit's reaction. */
    public void raise(ServerPlayer p, float amount) {
        if (amount <= 0 || mode == Mode.BOUND || mode == Mode.EXORCISM || mode == Mode.FROZEN) return;
        if (!GameManager.huntable(p)) return;
        float before = awarenessOf(p);
        float now = Math.min(1.0f, before + amount);
        awareness.put(p.getUUID(), now);
        lastPerceived.put(p.getUUID(), tickCount);
        GameManager.addFear(p, amount * 0.35f);
        if (now >= 1.0f && before < 1.0f && mode != Mode.NOTICE && mode != Mode.HUNT) {
            if (mode == Mode.ROUTINE || mode == Mode.INVESTIGATE) interrupted();
            onAwarenessFull(p);
        }
    }

    public boolean sawHide(Player p) {
        return sawHide.contains(p.getUUID());
    }

    public void markSawHide(Player p, boolean saw) {
        if (saw) sawHide.add(p.getUUID());
        else sawHide.remove(p.getUUID());
    }

    /** Eye position the spirit looks from. */
    public Vec3 sightOrigin() {
        return getEyePosition();
    }

    /** Direction it looks in (head yaw, head pitch + the per-pose downward tilt). */
    public Vec3 sightDir() {
        return Vec3.directionFromRotation(Mth.clamp(getXRot() + lookDownTilt(), -89.0f, 89.0f), getYHeadRot());
    }

    private void perceive(ServerLevel level) {
        boolean seeing = coneHalfAngle() > 0.0f;
        for (ServerPlayer p : level.players()) {
            if (!GameManager.huntable(p)) continue;
            if (distanceToSqr(p) > 34 * 34) continue;
            float add = seeing ? Stealth.sightRate(this, p) : 0.0f;
            add += extraSight(level, p);
            float br = breathRange();
            if (br > 0 && !GameManager.state(p).breathHeld() && distanceTo(p) < br) add += 0.012f;
            if (add > 0) {
                raise(p, add);
                if (mode == Mode.ROUTINE && awarenessOf(p) > 0.45f) lookAt(p.getEyePosition(), 10);
            }
        }
        for (NoiseSystem.Noise n : NoiseSystem.since(lastNoise)) hear(level, n);
        lastNoise = NoiseSystem.lastId();
        // forget slowly: -0.1/s after 2 s without perceiving
        awareness.replaceAll((id, v) -> tickCount - lastPerceived.getOrDefault(id, 0) > 40 ? Math.max(0.0f, v - 0.005f) : v);
        awareness.values().removeIf(v -> v <= 0.0f);
    }

    /** A noise reached it: awareness of whoever made it, and it turns (and maybe walks) toward the sound. */
    protected void hear(ServerLevel level, NoiseSystem.Noise n) {
        double hearing = hearingMulAt(n.pos());
        if (hearing <= 0.0) return;
        double r = n.radius() * hearing;
        double d = n.pos().distanceTo(position().add(0.0, 1.0, 0.0));
        if (d > r || r <= 0.0) return;
        float strength = (float) (1.0 - d / r);
        ServerPlayer src = n.player(level);
        if (src != null && GameManager.huntable(src)) raise(src, (0.12f + 0.38f * strength) * n.weight());
        if (mode == Mode.ROUTINE || mode == Mode.INVESTIGATE) {
            if (investigates() && strength > 0.12f && (src != null || n.weight() >= 1.0f)) startInvestigate(n.pos());
            else lookAt(n.pos(), 50);
        }
    }

    // ================================================================================================ states
    public void startInvestigate(Vec3 at) {
        if (mode == Mode.ROUTINE) interrupted();
        investigatePos = at;
        setMode(Mode.INVESTIGATE);
        setAnimPose(poseWalk());
    }

    public void startNotice(ServerPlayer p) {
        huntId = p.getUUID();
        setMode(Mode.NOTICE);
        setAnimPose(poseNotice());
        setAwareness(p, 1.0f);
        faceTowards(p.getEyePosition());
        onNotice(p);
        GameManager.onNoticed(p, this);
    }

    public void startHunt(ServerPlayer p) {
        huntId = p.getUUID();
        lostTicks = 0;
        setMode(Mode.HUNT);
        setAnimPose(poseHunt());
    }

    /** Ends a hunt: back to the routine, wary for 30 s. */
    public void loseTrack() {
        huntId = null;
        waryTicks = 20 * 30;
        for (UUID id : awareness.keySet()) awareness.put(id, 0.5f);
        setMode(Mode.RETURN);
        setAnimPose(poseWalk());
        onHuntEnd(false);
    }

    /** After it caught somebody: forget, go home. */
    public void afterCatch() {
        huntId = null;
        awareness.clear();
        waryTicks = 20 * 10;
        setMode(Mode.RETURN);
        setAnimPose(poseWalk());
        onHuntEnd(true);
    }

    /** Puts it straight back into its routine at home (debug, reset after the player was caught by another). */
    public void resetToRoutine() {
        huntId = null;
        awareness.clear();
        sawHide.clear();
        waryTicks = 0;
        investigatePos = null;
        Vec3 h = home();
        moveTo(h.x, h.y, h.z, getYRot(), 0.0f);
        setMode(Mode.ROUTINE);
        setAnimPose(poseStand());
        resumeRoutine();
    }

    @Nullable
    public ServerPlayer huntTarget() {
        if (huntId == null || !(level() instanceof ServerLevel sl)) return null;
        Player p = sl.getPlayerByUUID(huntId);
        return p instanceof ServerPlayer sp && sp.isAlive() && sp.level() == level() ? sp : null;
    }

    public boolean hunting() {
        return mode == Mode.HUNT || mode == Mode.NOTICE;
    }

    /** Bound into a photo: pulled toward the camera, then gone. */
    public void bind(Vec3 camera) {
        interrupted();
        boundTo = camera;
        huntId = null;
        setMode(Mode.BOUND);
        setAnimPose(poseNotice());
        fadeTarget = 0.0f;
        fadeSpeed = 1.0f / 26.0f;
        play(ModSounds.SPIRIT_BOUND.get(), 1.3f, 1.0f);
    }

    /** Stands at the exorcism table (slot = index in the exorcism_spirits markers), fading in. */
    public void exorcism(int slot) {
        interrupted();
        exorcismSlot = slot;
        huntId = null;
        awareness.clear();
        setMode(Mode.EXORCISM);
        setAnimPose(poseStand());
        entityData.set(DATA_FADE, 0.0f);
        fadeTarget = 1.0f;
        fadeSpeed = 1.0f / 40.0f;
    }

    /** Fades away into light (its photo has burnt). */
    public void release() {
        fadeTarget = 0.0f;
        fadeSpeed = 1.0f / 50.0f;
    }

    public void freeze(int pose) {
        setMode(Mode.FROZEN);
        setAnimPose(pose);
        clearPath();
    }

    public void unfreeze() {
        if (mode == Mode.FROZEN) {
            setMode(Mode.ROUTINE);
            resumeRoutine();
        }
    }

    // ================================================================================================ ticking
    @Override
    public void tick() {
        noPhysics = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        super.tick();
        setOnGround(true);
        fallDistance = 0;
        if (level().isClientSide) return;
        ServerLevel level = (ServerLevel) level();
        modeTicks++;
        if (waryTicks > 0) waryTicks--;
        if (lookAtTicks > 0 && --lookAtTicks == 0) lookAtUntil = null;
        tickFade();
        switch (mode) {
            case BOUND -> {
                tickBound(level);
                syncFlags();
                return;
            }
            case EXORCISM -> {
                if (fade() <= 0.0f && fadeTarget <= 0.0f) {
                    discard();
                    return;
                }
                if (fadeTarget <= 0.0f && random.nextInt(2) == 0) {
                    level.sendParticles(ModParticles.SPIRIT_MOTE.get(), getX(), getY() + getBbHeight() * random.nextFloat(), getZ(),
                            2, 0.25, 0.3, 0.25, 0.02);
                }
                syncFlags();
                return;
            }
            case FROZEN -> {
                syncFlags();
                return;
            }
            default -> {
            }
        }
        if (!GameManager.running(level)) {
            syncFlags();
            return;
        }
        perceive(level);
        switch (mode) {
            case ROUTINE -> routine(level);
            case INVESTIGATE -> tickInvestigate(level);
            case NOTICE -> tickNotice(level);
            case HUNT -> tickHunt(level);
            case RETURN -> tickReturn(level);
            default -> {
            }
        }
        if (lookAtUntil != null && (mode == Mode.ROUTINE || mode == Mode.INVESTIGATE)) lookHead(lookAtUntil);
        ambient(level);
        syncFlags();
    }

    private void tickFade() {
        float f = fade();
        if (Math.abs(f - fadeTarget) > 0.001f) {
            float n = f < fadeTarget ? Math.min(fadeTarget, f + fadeSpeed) : Math.max(fadeTarget, f - fadeSpeed);
            entityData.set(DATA_FADE, n);
        }
    }

    private void tickInvestigate(ServerLevel level) {
        if (investigatePos == null || modeTicks > 20 * 25) {
            setMode(Mode.RETURN);
            return;
        }
        double d = horizontalDistance(investigatePos);
        if (d > 1.6 && modeTicks < 20 * 18) {
            setAnimPose(poseWalk());
            walkTo(investigatePos, walkSpeed() * 1.25);
        } else {
            // look left and right where the sound was
            setAnimPose(poseLook());
            float base = getYRot();
            float sweep = Mth.sin(modeTicks * 0.09f) * 70.0f;
            setYHeadRot(base + sweep);
            if (modeTicks > 20 * 25 || (d <= 1.6 && poseTicks() > 70)) {
                investigatePos = null;
                setMode(Mode.RETURN);
            }
        }
    }

    private void tickNotice(ServerLevel level) {
        ServerPlayer p = huntTarget();
        if (p == null || !GameManager.huntable(p)) {
            loseTrack();
            return;
        }
        faceTowards(p.getEyePosition());
        GameManager.reportDanger(p, 1.0f);
        if (modeTicks >= noticeTicks()) {
            if (mayHunt(p)) startHunt(p);
            else loseTrack();
        }
    }

    private void tickHunt(ServerLevel level) {
        ServerPlayer p = huntTarget();
        if (p == null || !GameManager.huntable(p)) {
            loseTrack();
            return;
        }
        GameManager.reportDanger(p, 1.0f);
        if (!mayHunt(p)) {
            loseTrack();
            return;
        }
        boolean hidden = GameManager.state(p).hiding();
        boolean sees = (!hidden || sawHide(p)) && Sight.clear(level, getEyePosition(), p.getEyePosition());
        if (hidden && sawHide(p)) sees = true;
        lostTicks = sees ? 0 : lostTicks + 1;
        if (lostTicks > 100) {
            loseTrack();
            return;
        }
        Vec3 goal = hidden && GameManager.state(p).hiddenAt() != null ? Vec3.atBottomCenterOf(GameManager.state(p).hiddenAt()) : p.position();
        walkTo(goal, huntSpeed() * com.reisatsu.ReisatsuConfig.spiritSpeed());
        lookHead(p.getEyePosition());
        if (horizontalDistance(p.position()) < 0.95 + getBbWidth() * 0.5 && Math.abs(p.getY() - getY()) < 2.2) {
            GameManager.caught(p, this);
        }
    }

    private void tickReturn(ServerLevel level) {
        Vec3 h = home();
        setAnimPose(poseWalk());
        if (walkTo(h, walkSpeed()) || modeTicks > 20 * 40) {
            if (modeTicks > 20 * 40) moveTo(h.x, h.y, h.z, getYRot(), 0.0f);
            setMode(Mode.ROUTINE);
            setAnimPose(poseStand());
            resumeRoutine();
        }
    }

    private void tickBound(ServerLevel level) {
        if (boundTo != null) {
            Vec3 to = boundTo.subtract(position().add(0, getBbHeight() * 0.5, 0));
            double d = to.length();
            if (d > 0.05) {
                Vec3 step = to.scale(Math.min(1.0, (0.04 + modeTicks * 0.012) / d));
                setPos(getX() + step.x, getY() + step.y * 0.6, getZ() + step.z);
            }
        }
        if (modeTicks % 2 == 0) {
            level.sendParticles(ModParticles.SPIRIT_MOTE.get(), getX(), getY() + getBbHeight() * 0.6, getZ(), 3, 0.2, 0.4, 0.2, 0.05);
        }
        if (modeTicks > 28) discard();
    }

    private void syncFlags() {
        int f = 0;
        if (mode == Mode.HUNT) f |= F_HUNTING;
        if (mode == Mode.NOTICE) f |= F_NOTICE | F_HUNTING;
        if (mode == Mode.BOUND) f |= F_BOUND;
        if (mode == Mode.EXORCISM) f |= F_EXORCISM;
        if (debugShown) f |= F_SHOWN;
        if (mode == Mode.INVESTIGATE || waryTicks > 0) f |= F_ALERT;
        float aware = maxAwareness();
        boolean ready = photogenic() && aware < 1.0f && waryTicks == 0 && mode != Mode.HUNT && mode != Mode.NOTICE
                && mode != Mode.BOUND && mode != Mode.EXORCISM;
        if (ready) f |= F_PHOTOGENIC;
        if (entityData.get(DATA_FLAGS) != f) entityData.set(DATA_FLAGS, f);
        float shown = Math.round(aware * 20.0f) / 20.0f;
        if (mode == Mode.HUNT || mode == Mode.NOTICE) shown = 1.0f;
        if (entityData.get(DATA_AWARE) != shown) entityData.set(DATA_AWARE, shown);
        // the stutter: low in the routine, strong when it has noticed you; brief spikes
        float g = switch (mode) {
            case NOTICE, BOUND -> 1.0f;
            case HUNT -> 0.7f;
            case INVESTIGATE -> 0.3f;
            case EXORCISM -> 0.0f;
            default -> 0.08f + aware * 0.3f;
        };
        if (spikeTicks > 0) {
            spikeTicks--;
            g = Math.max(g, 0.85f);
        } else if (mode != Mode.EXORCISM && random.nextInt(260) == 0) {
            spikeTicks = 2 + random.nextInt(4);
        }
        if (Math.abs(entityData.get(DATA_GLITCH) - g) > 0.02f) entityData.set(DATA_GLITCH, g);
    }

    // ================================================================================================ movement
    @Override
    protected BodyRotationControl createBodyControl() {
        // the body faces where it is going (yRot), the head turns on its own — too far, sometimes
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                Spirit.this.yBodyRot = Spirit.this.getYRot();
            }
        };
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GhostNavigation(this, level);
    }

    /** Feet level the spirit glides at. */
    protected double floorY() {
        return Layout.FEET_Y;
    }

    public double horizontalDistance(Vec3 to) {
        double dx = to.x - getX(), dz = to.z - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    protected void clearPath() {
        path = null;
        pathGoal = null;
        pathIndex = 0;
    }

    /**
     * Walks toward a goal along a path through the restaurant (doors are no obstacle to a ghost). Returns true once it
     * is there. Falls back to a straight line when no path is found.
     */
    protected boolean walkTo(Vec3 goal, double speed) {
        BlockPos gb = BlockPos.containing(goal.x, floorY(), goal.z);
        if (path == null || pathGoal == null || pathGoal.distSqr(gb) > 2.5 || ++pathAge > 40) {
            computePath(gb);
        }
        if (horizontalDistance(goal) < 0.3) return true;
        if (path == null || pathIndex >= path.size()) return glide(goal, speed);
        Vec3 node = pathIndex == path.size() - 1 ? goal : path.get(pathIndex);
        if (glide(node, speed)) pathIndex++;
        return horizontalDistance(goal) < 0.3;
    }

    private void computePath(BlockPos goal) {
        pathGoal = goal;
        pathAge = 0;
        pathIndex = 0;
        path = null;
        try {
            Path p = getNavigation().createPath(goal, 0);
            if (p == null || p.getNodeCount() == 0) return;
            List<Vec3> pts = new ArrayList<>();
            for (int i = 0; i < p.getNodeCount(); i++) {
                BlockPos n = p.getNodePos(i);
                pts.add(new Vec3(n.getX() + 0.5, floorY(), n.getZ() + 0.5));
            }
            // skip the node we are standing in
            if (!pts.isEmpty() && horizontalDistance(pts.get(0)) < 0.75) pts.remove(0);
            path = pts;
        } catch (Exception ignored) {
            path = null;
        }
    }

    /** One step in a straight line toward a point (feet level), turning the body. Returns true on arrival. */
    protected boolean glide(Vec3 to, double speed) {
        double dx = to.x - getX(), dz = to.z - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        double y = floorY();
        if (d < 1e-4) {
            setPos(getX(), y, getZ());
            return true;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        turnBody(yaw, 22.0f);
        if (d <= speed) {
            setPos(to.x, y, to.z);
            return true;
        }
        setPos(getX() + dx / d * speed, y, getZ() + dz / d * speed);
        return false;
    }

    protected void turnBody(float yaw, float maxStep) {
        float cur = getYRot();
        float diff = Mth.wrapDegrees(yaw - cur);
        float n = cur + Mth.clamp(diff, -maxStep, maxStep);
        setYRot(n);
        yBodyRot = n;
        if (lookAtUntil == null) setYHeadRot(n);
    }

    /** Turns body and head at once toward a point. */
    public void faceTowards(Vec3 at) {
        double dx = at.x - getX(), dz = at.z - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        setYRot(yaw);
        yRotO = yaw;
        yBodyRot = yaw;
        yBodyRotO = yaw;
        setYHeadRot(yaw);
        yHeadRotO = yaw;
        lookHead(at);
    }

    public void faceYaw(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        setYHeadRot(yaw);
    }

    /** Turns only the head (and the pitch) toward a point. */
    protected void lookHead(Vec3 at) {
        Vec3 eye = getEyePosition();
        double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        setYHeadRot(yaw);
        setXRot(Mth.clamp(pitch, -60.0f, 60.0f));
    }

    /** Looks toward a point for a while (a sound, a player at the edge of its view). */
    public void lookAt(Vec3 at, int ticks) {
        lookAtUntil = at;
        lookAtTicks = ticks;
    }

    // ================================================================================================ helpers
    protected void play(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getEyeY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    protected void playAt(Vec3 at, SoundEvent sound, float volume, float pitch) {
        level().playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** The nearest huntable player in range, or null. */
    @Nullable
    protected ServerPlayer nearestPlayer(ServerLevel level, double range) {
        ServerPlayer best = null;
        double bd = range * range;
        for (ServerPlayer p : level.players()) {
            if (!GameManager.huntable(p)) continue;
            double d = distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    public int exorcismSlot() {
        return exorcismSlot;
    }

    // ================================================================================================ vanilla overrides
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isInWall() {
        return false;
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    @Nullable
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1.2);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }
}
