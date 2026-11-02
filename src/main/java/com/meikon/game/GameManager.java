package com.meikon.game;

import com.meikon.Meikon;
import com.meikon.MeikonConfig;
import com.meikon.block.CandleStandBlock;
import com.meikon.block.SuzuDaiBlock;
import com.meikon.block.ToriiGateBlock;
import com.meikon.entity.Hanayome;
import com.meikon.entity.Ichimatsu;
import com.meikon.entity.Kageboshi;
import com.meikon.entity.Maboroshi;
import com.meikon.item.LanternItem;
import com.meikon.item.NoteItem;
import com.meikon.network.ModNetwork;
import com.meikon.network.S2C;
import com.meikon.registry.ModBlocks;
import com.meikon.registry.ModDimensions;
import com.meikon.registry.ModItems;
import com.meikon.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-side game logic of 冥婚. */
public final class GameManager {
    public static boolean debugPeaceful;
    /**
     * Trailer capture (client {@code -Dmeikon.trailer}): the world holds still - no player logic, no director, no lantern
     * light blocks - and the camera, the actors and the lights are driven by the capture script.
     */
    public static volatile boolean cinematic;
    public static final int CAPTURE_TICKS = 36;
    public static final int EVENT_JUMPSCARE = 1, EVENT_DEATH = 2, EVENT_OFUDA = 3, EVENT_INTRO = 4, EVENT_TITLE = 5,
            EVENT_ENDING = 6, EVENT_SPOTTED = 7, EVENT_STINGER = 8, EVENT_SHADOW = 9, EVENT_SCREEN = 10, EVENT_LEFT = 11,
            EVENT_SHAKE = 12, EVENT_BELL = 13, EVENT_FLICKER = 14, EVENT_RITUAL = 15;
    public static final int SCREEN_ENVELOPE = 0, SCREEN_SAKAZUKI = 1;

    @Nullable private static Mansion mansion;
    private static long mansionSeed = Long.MIN_VALUE;
    private static final Map<UUID, PlayerState> STATES = new HashMap<>();
    private static final Set<Long> SALT = new HashSet<>();
    private static final RandomSource RAND = RandomSource.create();
    private static int phase = 1;
    private static int lastPhase = 1;

    private GameManager() {}

    // ================================================================== access
    @Nullable
    public static Mansion mansion() {
        return mansion;
    }

    @Nullable
    public static PlayerState state(Player p) {
        return STATES.get(p.getUUID());
    }

    public static PlayerState stateOrCreate(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new PlayerState());
    }

    public static int phase() {
        return phase;
    }

    public static int phaseFor(int bells) {
        if (bells >= 7) return 5;
        if (bells >= 6) return 4;
        if (bells >= 4) return 3;
        if (bells >= 2) return 2;
        return 1;
    }

    @Nullable
    public static ServerLevel kakuriyo(MinecraftServer server) {
        return server.getLevel(ModDimensions.KAKURIYO);
    }

    public static boolean inGame(ServerPlayer p) {
        return ModDimensions.isKakuriyo(p.level()) && GameData.get(p.server).active && STATES.containsKey(p.getUUID());
    }

    private static void ensureMansion(GameData data) {
        if (data.built && (mansion == null || mansionSeed != data.seed)) {
            mansion = MansionBuilder.record(data.seed);
            mansionSeed = data.seed;
        }
    }

    // ================================================================== tuning by phase
    public static double brideSpeedFactor() {
        return switch (phase) {
            case 1 -> 1.0;
            case 2 -> 1.03;
            case 3 -> 1.07;
            case 4 -> 1.10;
            default -> 1.05;
        } * MeikonConfig.brideSpeed();
    }

    public static double hearingFactor() {
        return 1.0 + 0.1 * (phase - 1);
    }

    public static boolean relentless() {
        return phase >= 4;
    }

    public static boolean dollsAwake() {
        return phase >= 2;
    }

    public static double dollSpeedFactor() {
        return 1.0 + 0.08 * Math.max(0, phase - 2);
    }

    public static double sightRange(ServerPlayer p, PlayerState ps) {
        double r;
        if (ps.lanternLit) r = 26.0;
        else if (p.level().getMaxLocalRawBrightness(p.blockPosition().above()) >= 8) r = 18.0;
        else r = 9.0;
        if (p.isCrouching()) r *= 0.65;
        return r * (1.0 + 0.07 * (phase - 1));
    }

    // ================================================================== player lists
    public static List<ServerPlayer> huntable(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        GameData data = GameData.get(level.getServer());
        if (!data.active) return out;
        for (ServerPlayer p : level.players()) {
            PlayerState ps = STATES.get(p.getUUID());
            if (ps == null || ps.captured || ps.ending || !p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            out.add(p);
        }
        return out;
    }

    @Nullable
    public static ServerPlayer nearestHuntable(ServerLevel level, Vec3 from, double max) {
        ServerPlayer best = null;
        double bd = max * max;
        for (ServerPlayer p : huntable(level)) {
            double d = p.distanceToSqr(from);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    public static boolean lanternLit(ServerPlayer p) {
        PlayerState ps = state(p);
        return ps != null && ps.lanternLit;
    }

    public static boolean holdsLitLantern(Player p) {
        return p.getMainHandItem().is(ModItems.CHOCHIN_LIT.get()) || p.getOffhandItem().is(ModItems.CHOCHIN_LIT.get());
    }

    /** Is any player (any mode except spectator) looking at this entity? */
    public static boolean anyoneSees(ServerLevel level, Entity e) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            if (looksAt(p, e, 0.5, 48.0)) return true;
        }
        return false;
    }

    public static boolean looksAt(ServerPlayer p, Entity e, double minDot, double maxDist) {
        Vec3 to = e.position().add(0, e.getBbHeight() * 0.6, 0).subtract(p.getEyePosition());
        double dist = to.length();
        if (dist > maxDist || dist < 1.0e-3) return dist < 1.0e-3;
        double dot = p.getViewVector(1.0f).dot(to.scale(1.0 / dist));
        return dot >= minDot && p.hasLineOfSight(e);
    }

    // ================================================================== safe room, salt, closets, waypoints
    public static boolean nearSafeRoom(Vec3 pos, double margin) {
        return mansion != null && mansion.safeBox != null && mansion.safeBox.inflate(margin).contains(pos);
    }

    public static void saltPlaced(ServerLevel level, BlockPos pos) {
        SALT.add(pos.asLong());
    }

    /** Active salt within the radius of this position (the bride will not cross it). */
    public static boolean nearSalt(ServerLevel level, Vec3 pos, double radius) {
        Iterator<Long> it = SALT.iterator();
        boolean found = false;
        while (it.hasNext()) {
            BlockPos p = BlockPos.of(it.next());
            if (!level.getBlockState(p).is(ModBlocks.SALT_PILE.get())) {
                it.remove();
                continue;
            }
            if (Vec3.atBottomCenterOf(p).distanceToSqr(pos) < radius * radius) found = true;
        }
        return found;
    }

    @Nullable
    public static Closet closet(int index) {
        if (mansion == null || index < 0 || index >= mansion.closets.size()) return null;
        return mansion.closets.get(index);
    }

    @Nullable
    public static ServerPlayer playerInCloset(ServerLevel level, int index) {
        for (ServerPlayer p : level.players()) {
            PlayerState ps = state(p);
            if (ps != null && ps.closet == index) return p;
        }
        return null;
    }

    @Nullable
    public static BlockPos nextWaypoint(ServerLevel level, Hanayome bride) {
        if (mansion == null || mansion.waypoints.isEmpty()) return null;
        List<BlockPos> wps = mansion.waypoints;
        ServerPlayer target = nearestHuntable(level, bride.position(), 300);
        if (target != null) {
            PlayerState ps = state(target);
            boolean menace = ps != null && Director.wantsMenace(ps, level.getGameTime());
            if (menace && RAND.nextFloat() < 0.65f) {
                List<BlockPos> near = new ArrayList<>();
                for (BlockPos w : wps) {
                    double d = w.distSqr(target.blockPosition());
                    if (d > 7 * 7 && d < 18 * 18) near.add(w);
                }
                if (!near.isEmpty()) return near.get(RAND.nextInt(near.size()));
            }
        }
        for (int i = 0; i < 8; i++) {
            BlockPos w = wps.get(RAND.nextInt(wps.size()));
            if (w.distSqr(bride.blockPosition()) > 10 * 10) return w;
        }
        return wps.get(RAND.nextInt(wps.size()));
    }

    public static BlockPos farWaypoint(ServerLevel level, BlockPos from) {
        if (mansion == null || mansion.waypoints.isEmpty()) return from;
        List<ServerPlayer> players = huntable(level);
        BlockPos best = from;
        double bestScore = -1;
        for (BlockPos w : mansion.waypoints) {
            double score = Double.MAX_VALUE;
            for (ServerPlayer p : players) score = Math.min(score, w.distSqr(p.blockPosition()));
            if (players.isEmpty()) score = w.distSqr(from);
            score += RAND.nextDouble() * 40;
            if (score > bestScore) {
                bestScore = score;
                best = w;
            }
        }
        return best;
    }

    // ================================================================== fear helpers
    public static void addFear(ServerPlayer p, float amount) {
        PlayerState ps = state(p);
        if (ps == null) return;
        float mul = amount > 0 && hasItem(p, ModItems.OMAMORI.get()) ? 0.7f : 1.0f;
        ps.fear = Mth.clamp(ps.fear + amount * mul * (float) MeikonConfig.fearRate(), 0.0f, 100.0f);
    }

    public static boolean hasItem(Player p, Item item) {
        for (ItemStack s : p.getInventory().items) if (s.is(item)) return true;
        return p.getOffhandItem().is(item);
    }

    private static boolean consume(Player p, Item item) {
        for (ItemStack s : p.getInventory().items) {
            if (s.is(item)) {
                s.shrink(1);
                return true;
            }
        }
        if (p.getOffhandItem().is(item)) {
            p.getOffhandItem().shrink(1);
            return true;
        }
        return false;
    }

    public static void extinguish(ServerPlayer p) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            if (s.is(ModItems.CHOCHIN_LIT.get())) p.setItemInHand(h, LanternItem.toggled(s, false));
        }
        for (int i = 0; i < p.getInventory().items.size(); i++) {
            ItemStack s = p.getInventory().items.get(i);
            if (s.is(ModItems.CHOCHIN_LIT.get())) p.getInventory().items.set(i, LanternItem.toggled(s, false));
        }
        p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), ModSounds.GAME_LANTERN_OFF.get(), SoundSource.PLAYERS, 0.9f, 0.7f);
    }

    // ================================================================== packets helpers
    public static void event(ServerPlayer p, int type, int arg, String text) {
        ModNetwork.send(p, new S2C.Event(type, arg, text));
    }

    public static void sound(ServerPlayer p, SoundEvent sound, Vec3 at, float volume, float pitch) {
        ResourceLocation id = ForgeRegistries.SOUND_EVENTS.getKey(sound);
        if (id != null) ModNetwork.send(p, new S2C.Sound(id.toString(), at.x, at.y, at.z, volume, pitch));
    }

    private static void allInGame(MinecraftServer server, java.util.function.Consumer<ServerPlayer> action) {
        ServerLevel level = kakuriyo(server);
        if (level == null) return;
        for (ServerPlayer p : level.players()) {
            if (STATES.containsKey(p.getUUID())) action.accept(p);
        }
    }

    // ================================================================== start / leave / reset
    public static void openEnvelope(ServerPlayer p) {
        if (inGame(p)) {
            openNote(p, Notes.INVITATION);
            return;
        }
        event(p, EVENT_SCREEN, SCREEN_ENVELOPE, p.getGameProfile().getName());
    }

    public static void startGame(ServerPlayer p) {
        MinecraftServer server = p.server;
        GameData data = GameData.get(server);
        ServerLevel level = kakuriyo(server);
        if (level == null) {
            p.displayClientMessage(Component.translatable("message.meikon.no_dimension"), false);
            return;
        }
        if (!data.active || !data.built) {
            Long fixed = Long.getLong("meikon.mansionSeed");
            long seed = fixed != null ? fixed : RAND.nextLong() ^ System.nanoTime();
            newMansion(level, data, seed);
        }
        ensureMansion(data);
        if (!ModDimensions.isKakuriyo(p.level())) saveReturn(p);
        p.getInventory().clearContent();
        ItemStack lantern = new ItemStack(ModItems.CHOCHIN.get());
        LanternItem.setOil(lantern, LanternItem.MAX_OIL * 6 / 10);
        p.getInventory().add(lantern);
        if (!p.isCreative()) p.setGameMode(GameType.ADVENTURE);
        PlayerState ps = new PlayerState();
        STATES.put(p.getUUID(), ps);
        toSpawn(p);
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        event(p, EVENT_INTRO, 0, p.getGameProfile().getName());
        syncState(p, ps, data);
        Meikon.LOGGER.info("[meikon] {} entered the mansion (seed {})", p.getGameProfile().getName(), data.seed);
    }

    /** Builds a fresh mansion and resets progress. */
    public static void newMansion(ServerLevel level, GameData data, long seed) {
        data.resetProgress(seed);
        long t = System.currentTimeMillis();
        for (int cx = -3; cx <= 2; cx++) {
            for (int cz = -3; cz <= 2; cz++) level.setChunkForced(cx, cz, true);
        }
        mansion = MansionBuilder.build(level, seed);
        mansionSeed = seed;
        data.built = true;
        data.active = true;
        data.setDirty();
        phase = 1;
        lastPhase = 1;
        SALT.clear();
        NoiseSystem.clear();
        LanternLight.forgetAll();
        Meikon.LOGGER.info("[meikon] mansion built in {} ms ({} rooms, {} closets, {} bells on pedestals)",
                System.currentTimeMillis() - t, mansion.rooms.size(), mansion.closets.size(), mansion.bellSpots.size());
    }

    public static void toSpawn(ServerPlayer p) {
        ServerLevel level = kakuriyo(p.server);
        if (level == null || mansion == null) return;
        BlockPos s = mansion.spawn;
        p.stopRiding();
        p.teleportTo(level, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, mansion.spawnYaw, 5.0f);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    private static void saveReturn(ServerPlayer p) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", p.level().dimension().location().toString());
        tag.putDouble("X", p.getX());
        tag.putDouble("Y", p.getY());
        tag.putDouble("Z", p.getZ());
        tag.putFloat("Yaw", p.getYRot());
        tag.putInt("Mode", p.gameMode.getGameModeForPlayer().getId());
        tag.put("Inv", p.getInventory().save(new ListTag()));
        tag.putInt("XpLevel", p.experienceLevel);
        tag.putFloat("XpProgress", p.experienceProgress);
        p.getPersistentData().put("meikon_return", tag);
    }

    /** Sends the player back to where they opened the envelope (with their own inventory). */
    public static void leaveGame(ServerPlayer p, boolean goodEnding) {
        LanternLight.remove(p);
        STATES.remove(p.getUUID());
        CompoundTag tag = p.getPersistentData().getCompound("meikon_return");
        MinecraftServer server = p.server;
        ServerLevel target = server.overworld();
        double x, y, z;
        float yaw = p.getYRot();
        if (tag.contains("Dim")) {
            ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new ResourceLocation(tag.getString("Dim")));
            ServerLevel l = server.getLevel(key);
            if (l != null && l != kakuriyo(server)) target = l;
            x = tag.getDouble("X");
            y = tag.getDouble("Y");
            z = tag.getDouble("Z");
            yaw = tag.getFloat("Yaw");
        } else {
            BlockPos sp = target.getSharedSpawnPos();
            x = sp.getX() + 0.5;
            y = sp.getY();
            z = sp.getZ() + 0.5;
        }
        p.removeAllEffects();
        p.teleportTo(target, x, y, z, yaw, 0.0f);
        p.getInventory().clearContent();
        if (tag.contains("Inv")) p.getInventory().load(tag.getList("Inv", Tag.TAG_COMPOUND));
        if (tag.contains("Mode")) p.setGameMode(GameType.byId(tag.getInt("Mode")));
        else if (p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) p.setGameMode(GameType.SURVIVAL);
        if (tag.contains("XpLevel")) {
            p.setExperienceLevels(tag.getInt("XpLevel"));
            p.experienceProgress = tag.getFloat("XpProgress");
        }
        p.getPersistentData().remove("meikon_return");
        if (goodEnding) {
            for (int i = 0; i < p.getInventory().items.size(); i++) {
                if (p.getInventory().items.get(i).is(ModItems.RED_ENVELOPE.get())) p.getInventory().items.set(i, ItemStack.EMPTY);
            }
            p.getInventory().add(new ItemStack(ModItems.ASH.get()));
            if (server.isSingleplayer()) server.overworld().setDayTime((server.overworld().getDayTime() / 24000L + 1) * 24000L - 600L);
        }
        event(p, EVENT_LEFT, goodEnding ? 1 : 0, "");
    }

    public static void endGame(MinecraftServer server) {
        GameData data = GameData.get(server);
        data.active = false;
        data.endings++;
        data.setDirty();
    }

    // ================================================================== ticking
    public static void serverTick(MinecraftServer server) {
        ServerLevel level = kakuriyo(server);
        if (level == null) return;
        GameData data = GameData.get(server);
        ensureMansion(data);
        if (cinematic) return;
        phase = phaseFor(data.bells);
        if (phase != lastPhase) {
            int old = lastPhase;
            lastPhase = phase;
            if (data.active) onPhaseChanged(level, old, phase);
        }
        // forget players who are gone
        STATES.keySet().removeIf(id -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            return p == null || p.level() != level;
        });
        for (ServerPlayer p : level.players()) {
            PlayerState ps = STATES.get(p.getUUID());
            if (ps == null) {
                if (data.active && !p.isSpectator()) {
                    ps = stateOrCreate(p);
                } else {
                    continue;
                }
            }
            try {
                tickPlayer(level, p, ps, data);
            } catch (Exception e) {
                Meikon.LOGGER.error("[meikon] player tick failed", e);
            }
        }
        LanternLight.tick(level);
        if (data.active && level.getGameTime() % 20 == 0) Director.tickLevel(level, data);
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        long now = level.getGameTime();
        // ---------------------------------------------------------- capture in progress
        if (ps.captured) {
            ps.captureTicks++;
            if (ps.capturePos != null) {
                p.teleportTo(ps.capturePos.x, ps.capturePos.y, ps.capturePos.z);
                p.setDeltaMovement(Vec3.ZERO);
            }
            if (ps.captureTicks >= CAPTURE_TICKS) finishCapture(level, p, ps, data);
            syncTimer(p, ps, data);
            return;
        }
        if (ps.ending) {
            ps.endingTicks++;
            if (ps.endingTicks > 20 * 120) {
                boolean good = ps.endingGood;
                leaveGame(p, good);
            }
            return;
        }
        // keep the body going: no hunger, no health loss
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(5.0f);
        if (p.getHealth() < p.getMaxHealth()) p.setHealth(p.getMaxHealth());

        // ---------------------------------------------------------- where is the player
        BlockPos feet = p.blockPosition();
        int c = mansion == null ? -1 : mansion.closetAt(feet);
        if (c >= 0) {
            Closet cl = mansion.closets.get(c);
            BlockState door = level.getBlockState(cl.front());
            if (!(door.getBlock() instanceof DoorBlock) || door.getValue(DoorBlock.OPEN)) c = -1;
        }
        ps.closet = c;
        ps.hiddenTicks = c >= 0 ? ps.hiddenTicks + 1 : 0;
        ps.inSafeRoom = nearSafeRoom(p.position(), 0.0);
        if (!ps.everLeftSafeRoom && !ps.inSafeRoom && mansion != null && p.position().distanceToSqr(Vec3.atCenterOf(mansion.spawn)) > 4) {
            ps.everLeftSafeRoom = true;
            event(p, EVENT_TITLE, phase, "");
            sound(p, ModSounds.BRIDE_SCREECH.get(), p.position().add(0, 2, -30), 0.35f, 0.6f);
        }
        ps.lanternLit = holdsLitLantern(p);

        // ---------------------------------------------------------- stamina and footstep noise
        double moved = ps.lastPos == null ? 0 : Math.hypot(p.getX() - ps.lastPos.x, p.getZ() - ps.lastPos.z);
        ps.lastPos = p.position();
        boolean sprinting = p.isSprinting() && moved > 0.15;
        if (ps.exhausted > 0) {
            ps.exhausted--;
            if (p.isSprinting()) p.setSprinting(false);
        }
        if (sprinting && !p.isCreative()) {
            ps.stamina -= 0.75f;
            if (++ps.stepNoise >= 8) {
                ps.stepNoise = 0;
                float r = 12.0f;
                if (data.bells > 0 && hasItem(p, ModItems.SUZU.get())) {
                    r += 5.0f;
                    level.playSound(null, p.getX(), p.getY() + 0.8, p.getZ(), ModSounds.GAME_BELL_CHIME.get(), SoundSource.PLAYERS, 0.35f, 1.3f);
                }
                NoiseSystem.emit(level, p.position(), r, NoiseSystem.Source.SPRINT);
            }
            if (ps.stamina <= 0.0f) {
                ps.stamina = 0.0f;
                ps.exhausted = 70;
                p.setSprinting(false);
                level.playSound(null, p.getX(), p.getY() + 1.5, p.getZ(), ModSounds.PLAYER_BREATH_EXHAUSTED.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
                NoiseSystem.emit(level, p.position(), 9.0f, NoiseSystem.Source.VOICE);
            }
        } else {
            ps.stamina = Math.min(100.0f, ps.stamina + (moved < 0.02 ? 0.55f : 0.33f));
            if (moved > 0.05 && !p.isCrouching() && ++ps.stepNoise >= 16) {
                ps.stepNoise = 0;
                NoiseSystem.emit(level, p.position(), 3.5f, NoiseSystem.Source.STEP);
            }
        }

        // ---------------------------------------------------------- holding breath in a closet
        if (ps.closet >= 0 && p.isShiftKeyDown()) {
            ps.holdingBreath = true;
            ps.breath -= 0.85f;
            if (ps.breath <= 0.0f) {
                ps.breath = 0.0f;
                ps.holdingBreath = false;
                level.playSound(null, p.getX(), p.getY() + 1.5, p.getZ(), ModSounds.PLAYER_GASP.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                NoiseSystem.emit(level, p.position(), 8.0f, NoiseSystem.Source.VOICE);
                addFear(p, 10.0f);
            }
        } else {
            if (ps.holdingBreath && ps.breath < 40.0f) {
                level.playSound(null, p.getX(), p.getY() + 1.5, p.getZ(), ModSounds.PLAYER_GASP.get(), SoundSource.PLAYERS, 0.5f, 1.1f);
            }
            ps.holdingBreath = false;
            ps.breath = Math.min(100.0f, ps.breath + 1.1f);
        }

        // ---------------------------------------------------------- fear
        float perSecond = 0.0f;
        int light = ps.lanternLit ? 12 : level.getMaxLocalRawBrightness(feet.above());
        if (ps.inSafeRoom) perSecond -= 4.0f;
        else if (light >= 7) perSecond -= 0.8f;
        else if (light >= 3) perSecond += 0.5f;
        else perSecond += 1.5f;
        if (ps.closet >= 0) perSecond += 0.3f;
        Hanayome bride = nearestBride(level, p.position());
        float dangerTarget = 0.0f;
        if (bride != null && !ps.inSafeRoom) {
            double d = bride.distanceTo(p);
            if (d < 14.0) perSecond += (float) (14.0 - d) * 0.2f;
            boolean hunting = bride.state() == Hanayome.State.CHASE && p.equals(bride.target());
            if (hunting) {
                perSecond += 5.0f;
                dangerTarget = 1.0f;
                ps.lastChase = (int) (now % Integer.MAX_VALUE);
            } else if (d < 8.0 && (bride.state() == Hanayome.State.SEARCH || bride.state() == Hanayome.State.CHECK_CLOSET
                    || bride.state() == Hanayome.State.INVESTIGATE)) {
                dangerTarget = 0.55f;
            } else if (d < 6.0) {
                dangerTarget = 0.35f;
            }
            if (d < 24.0 && looksAt(p, bride, 0.8, 24.0)) perSecond += 5.0f;
        }
        if (perSecond > 0) addFear(p, perSecond / 20.0f);
        else ps.fear = Math.max(0.0f, ps.fear + perSecond / 20.0f);
        ps.danger += (dangerTarget - ps.danger) * 0.08f;
        if (ps.seenByBride > 0) ps.seenByBride--;

        // panic: your breathing gives you away
        if (ps.fear >= 95.0f && ++ps.panicNoise >= 50) {
            ps.panicNoise = 0;
            NoiseSystem.emit(level, p.position(), 6.0f, NoiseSystem.Source.VOICE);
        }
        if (bride != null && bride.distanceTo(p) < 3.0 && bride.state() == Hanayome.State.CHASE) {
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false, false));
        }
        // the ritual
        if (now - ps.atGate > 5) ps.atGate = Long.MIN_VALUE / 2;
        if (ps.forceTicks > 0) {
            ps.forceTicks--;
            ps.fear = ps.forceFear;
            ps.danger = ps.forceDanger;
        }

        syncTimer(p, ps, data);
        if (now % 20 == 0) Director.tickPlayer(level, p, ps, data);
    }

    private static void syncTimer(ServerPlayer p, PlayerState ps, GameData data) {
        if (++ps.syncTimer >= 4) {
            ps.syncTimer = 0;
            syncState(p, ps, data);
        }
    }

    public static void syncState(ServerPlayer p, PlayerState ps, GameData data) {
        int flags = 1;
        if (ps.closet >= 0) flags |= 2;
        if (ps.holdingBreath) flags |= 4;
        if (ps.exhausted > 0) flags |= 8;
        if (ps.captured) flags |= 16;
        if (ps.inSafeRoom) flags |= 32;
        if (canStartRitual(p)) flags |= 64;
        if (data.kuraUnlocked) flags |= 128;
        if (!ps.everLeftSafeRoom) flags |= 256;
        ModNetwork.send(p, new S2C.State(ps.fear, ps.stamina, ps.breath, ps.danger, flags, data.bells, phase, data.deaths));
    }

    @Nullable
    public static Hanayome nearestBride(ServerLevel level, Vec3 pos) {
        Hanayome best = null;
        double bd = 80 * 80;
        for (Hanayome h : level.getEntitiesOfClass(Hanayome.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(80))) {
            double d = h.distanceToSqr(pos);
            if (d < bd) {
                bd = d;
                best = h;
            }
        }
        return best;
    }

    @Nullable
    public static Hanayome anyBride(ServerLevel level) {
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Hanayome h && h.isAlive()) return h;
        }
        return null;
    }

    // ================================================================== phases
    private static void onPhaseChanged(ServerLevel level, int old, int now) {
        Meikon.LOGGER.info("[meikon] phase {} -> {}", old, now);
        if (mansion == null) return;
        for (BlockPos c : mansion.clocks) {
            level.playSound(null, c, ModSounds.AMBIENT_CLOCK_CHIME.get(), SoundSource.AMBIENT, 4.0f, 1.0f);
        }
        allInGame(level.getServer(), p -> {
            event(p, EVENT_TITLE, now, "");
            sound(p, ModSounds.AMBIENT_TEMPLE_BELL.get(), p.position().add(0, 6, 0), 0.9f, 0.9f);
        });
        if (now >= 3) {
            for (BlockPos a : mansion.andons) {
                BlockState s = level.getBlockState(a);
                if (s.is(ModBlocks.ANDON.get()) && RAND.nextFloat() < 0.6f) {
                    level.setBlock(a, s.setValue(com.meikon.block.AndonBlock.LIT, false), 3);
                }
            }
        }
        if (now >= 5) {
            for (BlockPos g : mansion.gateBlocks) {
                BlockState s = level.getBlockState(g);
                if (s.is(ModBlocks.TORII_GATE.get())) level.setBlock(g, s.setValue(ToriiGateBlock.ACTIVE, true), 3);
            }
            level.playSound(null, mansion.toriiCenter, ModSounds.GAME_GATE_OPEN.get(), SoundSource.AMBIENT, 4.0f, 1.0f);
            Hanayome bride = anyBride(level);
            ServerPlayer p = huntable(level).isEmpty() ? null : huntable(level).get(0);
            if (bride != null && p != null) {
                BlockPos far = null;
                for (BlockPos w : mansion.waypoints) {
                    double d = w.distSqr(p.blockPosition());
                    if (d > 18 * 18 && d < 30 * 30) {
                        far = w;
                        break;
                    }
                }
                if (far != null && !anyoneSees(level, bride)) bride.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
                bride.force(Hanayome.State.INVESTIGATE, p.position());
            }
        }
    }

    // ================================================================== bells
    public static void bellTaken(ServerPlayer p, BlockPos pos) {
        GameData data = GameData.get(p.server);
        if (!inGame(p)) {
            p.getInventory().add(new ItemStack(ModItems.SUZU.get()));
            return;
        }
        if (!data.takenBells.add(pos.asLong())) return;
        grantBell(p, data);
    }

    private static void grantBell(ServerPlayer p, GameData data) {
        data.bells = Math.min(7, data.bells + 1);
        data.setDirty();
        p.getInventory().add(new ItemStack(ModItems.SUZU.get()));
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY() + 1, p.getZ(), ModSounds.GAME_BELL_CHIME.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
        NoiseSystem.emit(level, p.position(), 14.0f, NoiseSystem.Source.BELL);
        event(p, EVENT_BELL, data.bells, "");
        p.displayClientMessage(Component.translatable("message.meikon.bell", data.bells), true);
        Meikon.LOGGER.info("[meikon] bell {} / 7", data.bells);
    }

    // ================================================================== puzzles and interactions
    public static boolean tryUnlockKura(ServerPlayer p, BlockPos pos) {
        GameData data = GameData.get(p.server);
        if (data.kuraUnlocked) return true;
        if (consume(p, ModItems.KURA_KEY.get())) {
            data.kuraUnlocked = true;
            data.setDirty();
            p.level().playSound(null, pos, ModSounds.GAME_UNLOCK.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            NoiseSystem.emit(p.serverLevel(), pos, 10.0f, NoiseSystem.Source.DOOR);
            p.displayClientMessage(Component.translatable("message.meikon.unlocked"), true);
            return true;
        }
        return false;
    }

    public static void candleLit(ServerPlayer p, BlockPos pos, int colour) {
        if (mansion == null || !inGame(p)) return;
        GameData data = GameData.get(p.server);
        int idx = -1;
        for (int i = 0; i < 3; i++) if (pos.equals(mansion.candles[i])) idx = i;
        if (idx < 0 || data.butsumaSolved) return;
        ServerLevel level = p.serverLevel();
        int expected = mansion.candleOrder[Math.min(2, data.candleStep)];
        if (colour == expected) {
            data.candleStep++;
            data.setDirty();
            if (data.candleStep >= 3) {
                data.butsumaSolved = true;
                if (mansion.butsumaBell != null) {
                    level.setBlock(mansion.butsumaBell, ModBlocks.SUZU_DAI.get().defaultBlockState().setValue(SuzuDaiBlock.HAS_BELL, true), 3);
                }
                level.playSound(null, pos, ModSounds.AMBIENT_TEMPLE_BELL.get(), SoundSource.BLOCKS, 1.5f, 1.2f);
                p.displayClientMessage(Component.translatable("message.meikon.butsuma_solved"), true);
            }
        } else {
            data.candleStep = 0;
            data.setDirty();
            for (BlockPos c : mansion.candles) {
                if (c == null) continue;
                BlockState s = level.getBlockState(c);
                if (s.is(ModBlocks.CANDLE_STAND.get())) level.setBlock(c, s.setValue(CandleStandBlock.LIT, false), 3);
            }
            level.playSound(null, pos, ModSounds.GAME_PUZZLE_FAIL.get(), SoundSource.BLOCKS, 1.4f, 1.0f);
            NoiseSystem.emit(level, Vec3.atCenterOf(pos), 24.0f, NoiseSystem.Source.VOICE);
            addFear(p, 15.0f);
            event(p, EVENT_SHAKE, 12, "");
            p.displayClientMessage(Component.translatable("message.meikon.butsuma_fail"), true);
        }
    }

    public static void dollHeadPlaced(ServerPlayer p, BlockPos pos) {
        if (mansion == null || !inGame(p)) return;
        GameData data = GameData.get(p.server);
        if (!pos.equals(mansion.headlessDoll) || data.dollSolved) return;
        data.dollSolved = true;
        data.setDirty();
        ServerLevel level = p.serverLevel();
        if (mansion.dollBell != null) {
            level.setBlock(mansion.dollBell, ModBlocks.SUZU_DAI.get().defaultBlockState().setValue(SuzuDaiBlock.HAS_BELL, true), 3);
        }
        level.playSound(null, pos, ModSounds.DOLL_HEAD_TURN.get(), SoundSource.BLOCKS, 1.4f, 0.8f);
        for (Ichimatsu doll : level.getEntitiesOfClass(Ichimatsu.class, p.getBoundingBox().inflate(12))) {
            doll.getLookControl().setLookAt(p, 90, 90);
        }
        event(p, EVENT_STINGER, 0, "");
        addFear(p, 12.0f);
        p.displayClientMessage(Component.translatable("message.meikon.doll_solved"), true);
    }

    public static void interact(ServerPlayer p, BlockPos pos, String action) {
        switch (action) {
            case "well" -> useWell(p, pos);
            case "sakazuki" -> {
                if (inGame(p)) event(p, EVENT_SCREEN, SCREEN_SAKAZUKI, "");
                else p.displayClientMessage(Component.translatable("message.meikon.sakazuki_idle"), true);
            }
            default -> {
            }
        }
    }

    private static void useWell(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        level.playSound(null, pos, ModSounds.GAME_WELL.get(), SoundSource.BLOCKS, 1.0f, 0.9f + RAND.nextFloat() * 0.2f);
        if (!inGame(p)) return;
        GameData data = GameData.get(p.server);
        if (data.wellSolved) {
            p.displayClientMessage(Component.translatable("message.meikon.well_empty"), true);
            return;
        }
        data.wellPulls++;
        data.setDirty();
        NoiseSystem.emit(level, Vec3.atCenterOf(pos), 12.0f, NoiseSystem.Source.OTHER);
        if (data.wellPulls < 3) {
            p.displayClientMessage(Component.translatable("message.meikon.well_" + data.wellPulls), true);
            addFear(p, 4.0f);
            return;
        }
        data.wellSolved = true;
        data.setDirty();
        event(p, EVENT_JUMPSCARE, 2, "");
        addFear(p, 22.0f);
        grantBell(p, data);
        p.displayClientMessage(Component.translatable("message.meikon.well_done"), false);
        Director.spawnPeek(level, p);
    }

    public static void tansuSearched(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        level.playSound(null, pos, ModSounds.GAME_DRAWER.get(), SoundSource.BLOCKS, 1.0f, 0.9f + RAND.nextFloat() * 0.2f);
        NoiseSystem.emit(level, Vec3.atCenterOf(pos), 8.0f, NoiseSystem.Source.DRAWER);
        if (mansion == null || !inGame(p)) return;
        GameData data = GameData.get(p.server);
        if (!data.searched.add(pos.asLong())) {
            p.displayClientMessage(Component.translatable("message.meikon.empty"), true);
            return;
        }
        data.setDirty();
        String loot = mansion.tansuLoot.getOrDefault(pos.asLong(), "");
        if (loot.isEmpty()) {
            p.displayClientMessage(Component.translatable("message.meikon.empty"), true);
            return;
        }
        if (loot.equals("scare") || loot.equals("hair")) {
            sound(p, ModSounds.AMBIENT_WHISPER.get(), p.getEyePosition().add(p.getViewVector(1.0f).scale(-0.6)), 1.0f, 0.9f);
            addFear(p, 9.0f);
            event(p, EVENT_SHAKE, 6, "");
            p.displayClientMessage(Component.translatable(loot.equals("hair") ? "message.meikon.found_hair" : "message.meikon.found_scare"), true);
            return;
        }
        List<Component> names = new ArrayList<>();
        for (String id : loot.split(",")) {
            Item item = ForgeRegistries.ITEMS.getValue(Meikon.id(id));
            if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
            ItemStack stack = new ItemStack(item);
            if (!p.getInventory().add(stack)) p.drop(stack, false);
            names.add(Component.translatable(item.getDescriptionId()));
        }
        if (!names.isEmpty()) {
            Component joined = names.get(0);
            for (int i = 1; i < names.size(); i++) joined = Component.empty().append(joined).append("、").append(names.get(i));
            p.displayClientMessage(Component.translatable("message.meikon.found", joined), true);
        }
    }

    public static void letterTaken(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        int note = mansion == null ? Notes.MAID_2 : mansion.letters.getOrDefault(pos.asLong(), Notes.MAID_2);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, pos, ModSounds.GAME_PAPER.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
        ItemStack stack = NoteItem.create(ModItems.NOTE.get(), note);
        if (!p.getInventory().add(stack)) p.drop(stack, false);
        GameData data = GameData.get(p.server);
        data.foundNotes.add(note);
        data.lettersTaken.add(pos.asLong());
        data.setDirty();
        openNote(p, note);
    }

    public static void openNote(ServerPlayer p, int id) {
        String extra = p.getGameProfile().getName();
        if (id == Notes.CANDLES && mansion != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                if (i > 0) sb.append(',');
                sb.append(mansion.candleOrder[i]);
            }
            extra = sb.toString();
        }
        ModNetwork.send(p, new S2C.Note(id, extra));
        p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), ModSounds.GAME_PAPER.get(), SoundSource.PLAYERS, 0.6f, 1.1f);
    }

    public static void saltThrown(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (Hanayome bride : level.getEntitiesOfClass(Hanayome.class, p.getBoundingBox().inflate(6))) {
            Vec3 to = bride.position().subtract(p.position());
            if (to.horizontalDistance() < 5.5 && p.getViewVector(1.0f).dot(to.normalize()) > 0.4) {
                bride.repelled();
                PlayerState ps = state(p);
                if (ps != null) ps.fear = Math.max(0.0f, ps.fear - 10.0f);
                p.displayClientMessage(Component.translatable("message.meikon.salt_hit"), true);
            }
        }
        for (Kageboshi k : level.getEntitiesOfClass(Kageboshi.class, p.getBoundingBox().inflate(6))) k.dissolve(level);
    }

    // ================================================================== creatures
    public static void onBrideSpotted(ServerPlayer p, Hanayome bride) {
        addFear(p, 18.0f);
        event(p, EVENT_SPOTTED, 0, "");
    }

    @Nullable
    public static ServerPlayer dollObserver(ServerLevel level, Ichimatsu doll) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            double d = p.distanceTo(doll);
            if (d > 32) continue;
            boolean visible = d < 4.0 || level.getMaxLocalRawBrightness(doll.blockPosition()) >= 5 || (holdsLitLantern(p) && d < 13);
            if (!visible) continue;
            if (looksAt(p, doll, 0.55, 32.0)) return p;
        }
        return null;
    }

    public static void dollAttack(ServerPlayer p, Ichimatsu doll) {
        PlayerState ps = state(p);
        if (ps == null || ps.captured || ps.inSafeRoom) return;
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY() + 1, p.getZ(), ModSounds.DOLL_ATTACK.get(), SoundSource.HOSTILE, 1.6f, 1.0f);
        event(p, EVENT_JUMPSCARE, 1, "");
        addFear(p, 35.0f);
        if (ps.lanternLit) extinguish(p);
        NoiseSystem.emit(level, p.position(), 30.0f, NoiseSystem.Source.VOICE);
    }

    public static boolean lanternBurns(ServerPlayer p, Entity shadow) {
        PlayerState ps = state(p);
        return ps != null && ps.lanternLit && p.distanceTo(shadow) < 7.5 && looksAt(p, shadow, 0.6, 8.0);
    }

    public static void shadowTouch(ServerPlayer p, Kageboshi shadow) {
        PlayerState ps = state(p);
        if (ps == null || ps.inSafeRoom) return;
        if (ps.lanternLit) extinguish(p);
        addFear(p, 20.0f);
        event(p, EVENT_SHADOW, 0, "");
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, false));
    }

    public static void peekSeen(ServerPlayer p, Maboroshi m) {
        addFear(p, 16.0f);
        event(p, EVENT_STINGER, 1, "");
    }

    // ================================================================== capture & fake death
    public static void beginCapture(ServerPlayer p, Hanayome bride) {
        PlayerState ps = state(p);
        if (ps == null || ps.captured || ps.ending) return;
        if (consume(p, ModItems.OFUDA.get())) {
            p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), ModSounds.GAME_OFUDA_BURN.get(), SoundSource.PLAYERS, 1.4f, 1.0f);
            event(p, EVENT_OFUDA, 0, "");
            bride.repelled();
            addFear(p, 25.0f);
            p.displayClientMessage(Component.translatable("message.meikon.ofuda"), true);
            return;
        }
        ps.captured = true;
        ps.captureTicks = 0;
        ps.capturePos = p.position();
        LanternLight.remove(p);
        Vec3 eye = bride.getEyePosition().add(0, 0.25, 0);
        Vec3 d = eye.subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0 / Math.PI));
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, pitch);
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, false));
        event(p, EVENT_JUMPSCARE, 0, "");
    }

    private static void finishCapture(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        data.deaths++;
        data.setDirty();
        event(p, EVENT_DEATH, data.deaths, "");
        extinguish(p);
        ps.resetAfterDeath();
        toSpawn(p);
        p.removeAllEffects();
        NoiseSystem.clear();
        for (Kageboshi k : level.getEntitiesOfClass(Kageboshi.class, p.getBoundingBox().inflate(64))) k.discard();
        Hanayome bride = anyBride(level);
        if (bride != null) {
            BlockPos far = farWaypoint(level, p.blockPosition());
            bride.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
            bride.force(Hanayome.State.WANDER, null);
        }
        Meikon.LOGGER.info("[meikon] {} was taken (deaths {})", p.getGameProfile().getName(), data.deaths);
    }

    /** Used by the death handler: something killed the player inside the mansion. */
    public static void fakeDeath(ServerPlayer p) {
        PlayerState ps = stateOrCreate(p);
        ServerLevel level = p.serverLevel();
        ps.captured = true;
        ps.captureTicks = CAPTURE_TICKS - 1;
        finishCapture(level, p, ps, GameData.get(p.server));
        ps.captured = false;
    }

    // ================================================================== the ritual and the endings
    public static void insideGate(ServerPlayer p, boolean active) {
        PlayerState ps = state(p);
        if (ps != null) ps.atGate = p.level().getGameTime();
    }

    public static boolean canStartRitual(ServerPlayer p) {
        PlayerState ps = state(p);
        if (ps == null || ps.captured || mansion == null || !GameData.get(p.server).active) return false;
        if (GameData.get(p.server).bells < 7) return false;
        boolean atGate = p.level().getGameTime() - ps.atGate <= 5
                || p.position().distanceToSqr(Vec3.atBottomCenterOf(mansion.toriiCenter)) < 2.5 * 2.5;
        return atGate;
    }

    public static void ritualStarted(ServerPlayer p) {
        PlayerState ps = state(p);
        if (ps == null) return;
        ps.ritualTicks = 0;
        event(p, EVENT_RITUAL, 1, "");
        ServerLevel level = p.serverLevel();
        Hanayome bride = anyBride(level);
        if (bride != null) bride.force(Hanayome.State.INVESTIGATE, p.position());
        NoiseSystem.emit(level, p.position(), 60.0f, NoiseSystem.Source.BELL);
    }

    public static void ritualTick(ServerPlayer p, int t) {
        if (t % 10 == 0) {
            ServerLevel level = p.serverLevel();
            level.playSound(null, p.getX(), p.getY() + 1.2, p.getZ(), ModSounds.GAME_BELL_CHIME.get(), SoundSource.PLAYERS, 1.6f,
                    0.8f + t / 160.0f);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 8, 0.6, 0.6, 0.6, 0.02);
        }
    }

    public static void ritualInterrupted(ServerPlayer p) {
        event(p, EVENT_RITUAL, 0, "");
        p.displayClientMessage(Component.translatable("message.meikon.ritual_broken"), true);
    }

    public static void ritualComplete(ServerPlayer p) {
        PlayerState ps = state(p);
        if (ps == null || ps.ending) return;
        ps.ending = true;
        ps.endingGood = true;
        ps.endingTicks = 0;
        ServerLevel level = p.serverLevel();
        Hanayome bride = anyBride(level);
        if (bride != null) bride.discard();
        for (Ichimatsu d : level.getEntitiesOfClass(Ichimatsu.class, p.getBoundingBox().inflate(80))) d.discard();
        for (Kageboshi k : level.getEntitiesOfClass(Kageboshi.class, p.getBoundingBox().inflate(80))) k.discard();
        endGame(p.server);
        event(p, EVENT_ENDING, 1, p.getGameProfile().getName());
        Meikon.LOGGER.info("[meikon] good ending for {}", p.getGameProfile().getName());
    }

    public static void sakazukiAccepted(ServerPlayer p) {
        PlayerState ps = state(p);
        if (ps == null || ps.ending || !inGame(p)) return;
        ps.ending = true;
        ps.endingGood = false;
        ps.endingTicks = 0;
        endGame(p.server);
        event(p, EVENT_ENDING, 0, p.getGameProfile().getName());
        Meikon.LOGGER.info("[meikon] bad ending for {}", p.getGameProfile().getName());
    }

    public static void endingDone(ServerPlayer p) {
        PlayerState ps = state(p);
        if (ps != null && ps.ending) {
            leaveGame(p, ps.endingGood);
        }
    }

    // ================================================================== login / logout
    public static void onLogin(ServerPlayer p) {
        CompoundTag pd = p.getPersistentData();
        if (MeikonConfig.giveEnvelope() && !pd.getBoolean("meikon_envelope")) {
            pd.putBoolean("meikon_envelope", true);
            p.getInventory().add(new ItemStack(ModItems.RED_ENVELOPE.get()));
            p.displayClientMessage(Component.translatable("message.meikon.envelope_found"), false);
        }
        if (ModDimensions.isKakuriyo(p.level())) {
            GameData data = GameData.get(p.server);
            if (!data.active) {
                leaveGame(p, false);
            } else {
                ensureMansion(data);
                PlayerState ps = stateOrCreate(p);
                ps.everLeftSafeRoom = true;
                syncState(p, ps, data);
            }
        }
    }

    public static void onLogout(ServerPlayer p) {
        LanternLight.remove(p);
        STATES.remove(p.getUUID());
    }

    public static void onServerStopping() {
        LanternLight.forgetAll();
        STATES.clear();
        mansion = null;
        mansionSeed = Long.MIN_VALUE;
        SALT.clear();
        NoiseSystem.clear();
    }
}
