package com.hitori.game;

import com.hitori.Hitori;
import com.hitori.HitoriConfig;
import com.hitori.debug.Greybox;
import com.hitori.entity.OniDoll;
import com.hitori.item.GameItem;
import com.hitori.network.C2S;
import com.hitori.network.ModNetwork;
import com.hitori.network.S2C;
import com.hitori.registry.ModItems;
import com.hitori.registry.ModSounds;
import com.hitori.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The night in room 203 on the server: starting and leaving (your own inventory, position and mode are kept and
 * given back), the player's body (footsteps, breath, the salt water in the mouth) and nerves (fear, danger), being
 * found, syncing. The procedure lives in {@link Ritual}, the clock in {@link Clock}, the cameras in {@link Cameras},
 * hiding in {@link Hiding}, the doll's presence in {@link Doll}, the 霊障 in {@link Director}, the phone in
 * {@link Phone}, the endings in {@link Endings}.
 */
public final class GameManager {
    // S2C.Event types
    public static final int EV_SUBTITLE = 1, EV_STEP = 2, EV_THREE = 3, EV_EYES = 4, EV_JUMPSCARE = 5, EV_ENDING = 6, EV_LEFT = 7,
            EV_FADE = 8, EV_SHAKE = 9, EV_STINGER = 10, EV_FEED = 11, EV_HIDE = 12, EV_MESSAGE = 13, EV_CALL = 14, EV_MAP = 15,
            EV_THOUGHT = 16, EV_HINT = 17, EV_RECORD = 18, EV_NEW_NIGHT = 19, EV_MORNING = 20, EV_INTRO = 21, EV_PRINTOUT = 22,
            EV_NAMING = 23, EV_APP = 24, EV_SCARE = 25, EV_CAUGHT = 26, EV_RULE = 27, EV_GLITCH = 28, EV_TITLE = 29;
    // S2C.State flags
    public static final int F_INGAME = 1, F_LOCKED = 2, F_HIDING = 4, F_CLOSET = 8, F_PEEK = 16, F_BREATH = 32, F_EYES = 64, F_APP = 128,
            F_TV = 256, F_SILENT = 512, F_LIGHT = 1024, F_RINGING = 2048, F_CAUGHT = 4096, F_ENDING = 8192, F_PEACEFUL = 16384,
            F_BUSY = 32768, F_GREY = 65536, F_SOTO = 1 << 17, F_DOLL_NEAR = 1 << 18, F_DOLL_SEEN = 1 << 19, F_HAS_WATER = 1 << 20;

    private static final Map<UUID, PlayerState> STATES = new HashMap<>();
    /** Players that are not in the level's own list (the self test's fake player). */
    private static final List<ServerPlayer> EXTRA = new ArrayList<>();
    private static final Map<UUID, Float> DANGER = new HashMap<>();
    public static boolean debugPeaceful;

    private GameManager() {}

    // ================================================================================================ access
    /** The apartment's level: the shirakaba dimension, or (until the world exists) the overworld greybox. */
    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        ServerLevel l = server.getLevel(Layout.DIMENSION);
        if (l != null) return l;
        return Greybox.fallback(server) ? server.overworld() : null;
    }

    public static List<ServerPlayer> players(ServerLevel level) {
        if (EXTRA.isEmpty()) return level.players();
        List<ServerPlayer> all = new ArrayList<>(level.players());
        for (ServerPlayer p : EXTRA) if (p.level() == level && !all.contains(p)) all.add(p);
        return all;
    }

    @Nullable
    public static ServerPlayer player(ServerLevel level, @Nullable UUID id) {
        if (id == null) return null;
        Player p = level.getPlayerByUUID(id);
        if (p instanceof ServerPlayer sp) return sp;
        for (ServerPlayer e : EXTRA) if (e.getUUID().equals(id)) return e;
        return null;
    }

    public static void addTestPlayer(ServerPlayer p) {
        if (!EXTRA.contains(p)) EXTRA.add(p);
    }

    public static void removeTestPlayer(ServerPlayer p) {
        EXTRA.remove(p);
    }

    public static boolean isGameLevel(@Nullable Level level) {
        if (level == null) return false;
        if (level.dimension() == Layout.DIMENSION) return true;
        if (level.getServer() != null) return level.dimension() == Level.OVERWORLD && Greybox.fallback(level.getServer());
        return false;
    }

    public static PlayerState state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), id -> {
            PlayerState ps = new PlayerState();
            ps.load(p.getPersistentData().getCompound("hitori"));
            ps.lastX = p.getX();
            ps.lastZ = p.getZ();
            return ps;
        });
    }

    public static boolean inGame(@Nullable Player p) {
        return p != null && !p.isSpectator() && isGameLevel(p.level()) && (p instanceof FakePlayer || p.getPersistentData().contains("hitori_return")
                || (p.level().getServer() != null && GameData.get(p.level().getServer()).started));
    }

    /** May the doll find / take this player now? */
    public static boolean huntable(@Nullable ServerPlayer p) {
        if (p == null || !inGame(p) || p.isCreative() || debugPeaceful) return false;
        PlayerState ps = state(p);
        return !ps.caught() && ps.endingTicks < 0 && !ps.locked && ps.sotoTicks < 0;
    }

    /** Is the night running (the doll awake possible)? */
    public static boolean running(ServerLevel level) {
        if (!isGameLevel(level)) return false;
        GameData data = GameData.get(level.getServer());
        return data.started && data.ritualRunning();
    }

    public static void reportDanger(ServerPlayer p, float v) {
        DANGER.merge(p.getUUID(), v, Math::max);
    }

    public static void addFear(ServerPlayer p, float amount) {
        PlayerState ps = state(p);
        ps.fear = Mth.clamp(ps.fear + amount, 0.0f, 1.0f);
    }

    public static void event(ServerPlayer p, int type, int arg, String text) {
        if (p instanceof FakePlayer) return;
        ModNetwork.send(p, new S2C.Event(type, arg, text));
    }

    public static void thought(ServerPlayer p, String key) {
        event(p, EV_THOUGHT, 0, key);
    }

    public static void send(ServerPlayer p, Object msg) {
        if (p instanceof FakePlayer) return;
        ModNetwork.send(p, msg);
    }

    /** A subtitle line (lang key with arguments). */
    public static void say(ServerPlayer p, String speaker, String key, int ticks, String... args) {
        send(p, new S2C.Say(speaker, key, Arrays.asList(args), ticks));
    }

    /** A sound only this player hears, at a position (director tricks). */
    public static void privateSound(ServerPlayer p, SoundEvent sound, Vec3 at, float volume, float pitch) {
        send(p, new S2C.Sound(sound.getLocation().toString(), at.x, at.y, at.z, volume, pitch));
    }

    public static void worldSound(Level level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    public static void playerSound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getEyeY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ================================================================================================ inventory
    public static boolean has(Player p, Item item) {
        return count(p, item) > 0;
    }

    public static int count(Player p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        if (p.getOffhandItem().is(item)) n += p.getOffhandItem().getCount();
        return n;
    }

    /** The first stack of this item (main hand, off hand, then the inventory), or EMPTY. */
    public static ItemStack find(Player p, Item item) {
        if (p.getMainHandItem().is(item)) return p.getMainHandItem();
        if (p.getOffhandItem().is(item)) return p.getOffhandItem();
        for (ItemStack s : p.getInventory().items) if (s.is(item)) return s;
        return ItemStack.EMPTY;
    }

    public static boolean holding(Player p, Item item) {
        return p.getMainHandItem().is(item) || p.getOffhandItem().is(item);
    }

    public static boolean consume(Player p, Item item) {
        ItemStack s = find(p, item);
        if (s.isEmpty()) return false;
        s.shrink(1);
        return true;
    }

    public static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    /** Replaces a stack keeping its NBT (the plush through the ritual, the phone's light). */
    public static ItemStack transform(Player p, ItemStack old, Item to) {
        ItemStack fresh = new ItemStack(to, Math.max(1, old.getCount()));
        if (old.hasTag() && old.getTag() != null) fresh.setTag(old.getTag().copy());
        for (InteractionHand h : InteractionHand.values()) {
            if (p.getItemInHand(h) == old) {
                p.setItemInHand(h, fresh);
                return fresh;
            }
        }
        var inv = p.getInventory().items;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.get(i) == old) {
                inv.set(i, fresh);
                return fresh;
            }
        }
        give((ServerPlayer) p, fresh);
        return fresh;
    }

    public static boolean phoneLit(Player p) {
        return p.getMainHandItem().is(ModItems.PHONE_LIGHT.get()) || p.getOffhandItem().is(ModItems.PHONE_LIGHT.get());
    }

    // ================================================================================================ start / leave
    public static void onLogin(ServerPlayer p) {
        GameData data = GameData.get(p.server);
        PlayerState ps = state(p);
        if (!data.started && data.ending == GameData.ENDING_NONE) {
            if (HitoriConfig.autoStart() && level(p.server) != null && !p.getPersistentData().getBoolean("hitori_done")) startGame(p);
            return;
        }
        ServerLevel level = level(p.server);
        if (data.started && !p.getPersistentData().contains("hitori_return") && level != null) {
            // somebody else started the night: in with them (a rule: alone)
            saveReturn(p);
            p.getInventory().clearContent();
            if (!p.isCreative()) p.setGameMode(GameType.ADVENTURE);
            Layout.Marker w = Layout.marker("spawn");
            teleport(p, level, w.center(), w.yaw(), 0.0f);
            if (data.ritualRunning() && data.breakRule("alone")) event(p, EV_RULE, 1, "alone");
        }
        if (level != null && data.started) {
            // save and quit: everything resumes where it was; back in the closet if you were in it
            Hiding.resume(p, ps);
            Doll.resume(level, data);
            Cameras.ensure(level, data);
            event(p, EV_INTRO, -1, "");
        }
        if (data.phase == GameData.PHASE_ENDING && data.ending != GameData.ENDING_NONE) Endings.showAgain(p, data);
        syncState(p, ps, data);
    }

    public static void onLogout(ServerPlayer p) {
        savePlayer(p);
        PlayerState ps = STATES.get(p.getUUID());
        if (ps != null) ps.appCamera = 0;
        STATES.remove(p.getUUID());
    }

    public static void savePlayer(ServerPlayer p) {
        PlayerState ps = STATES.get(p.getUUID());
        if (ps != null) p.getPersistentData().put("hitori", ps.save());
    }

    public static void startGame(ServerPlayer p) {
        MinecraftServer server = p.server;
        ServerLevel level = level(server);
        if (level == null) {
            Hitori.LOGGER.error("[hitori] the apartment dimension is missing");
            return;
        }
        GameData data = GameData.get(server);
        boolean introSeen = data.introSeen;
        data.resetProgress();
        data.introSeen = introSeen;
        data.started = true;
        data.phase = GameData.PHASE_PREP;
        data.setDirty();
        Apt.ensureBuilt(level);
        Apt.resetAll(level);
        Doll.removeAll(level);
        Cameras.removeAll(level);
        Director.reset();
        Endings.reset();
        Phone.reset();
        Ritual.resetWorld(level);
        NoiseSystem.clear();
        if (!p.getPersistentData().contains("hitori_return")) saveReturn(p);
        p.getPersistentData().remove("hitori_done");
        p.getPersistentData().remove("hitori");
        p.getInventory().clearContent();
        if (!p.isCreative()) p.setGameMode(GameType.ADVENTURE);
        PlayerState ps = new PlayerState();
        STATES.put(p.getUUID(), ps);
        Layout.Marker spawn = Layout.marker("spawn");
        teleport(p, level, spawn.center(), spawn.yaw(), 12.0f);
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        // your phone is in your pocket
        p.getInventory().setItem(8, new ItemStack(ModItems.PHONE.get()));
        p.getInventory().selected = 0;
        event(p, EV_NEW_NIGHT, 0, "");
        event(p, EV_INTRO, introSeen ? 1 : 0, "");
        data.introSeen = true;
        syncState(p, ps, data);
        Hitori.LOGGER.info("[hitori] {} begins the night in room 203", p.getGameProfile().getName());
    }

    public static void teleport(ServerPlayer p, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        p.stopRiding();
        if (p instanceof FakePlayer) p.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
        else p.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch);
        p.setYHeadRot(yaw);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        PlayerState ps = state(p);
        ps.lastX = pos.x;
        ps.lastZ = pos.z;
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
        tag.putFloat("Health", p.getHealth());
        tag.putInt("Food", p.getFoodData().getFoodLevel());
        p.getPersistentData().put("hitori_return", tag);
    }

    /** Back where they came from, with their own things. */
    public static void leaveGame(ServerPlayer p) {
        Hiding.exit(p, false);
        Director.clearFor(p);
        CompoundTag tag = p.getPersistentData().getCompound("hitori_return");
        MinecraftServer server = p.server;
        ServerLevel target = server.overworld();
        double x, y, z;
        float yaw = p.getYRot();
        if (tag.contains("Dim")) {
            ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new ResourceLocation(tag.getString("Dim")));
            ServerLevel l = server.getLevel(key);
            if (l != null && l.dimension() != Layout.DIMENSION) target = l;
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
        teleport(p, target, new Vec3(x, y, z), yaw, 0.0f);
        p.getInventory().clearContent();
        if (tag.contains("Inv")) p.getInventory().load(tag.getList("Inv", Tag.TAG_COMPOUND));
        if (tag.contains("Mode")) p.setGameMode(GameType.byId(tag.getInt("Mode")));
        else if (p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) p.setGameMode(GameType.SURVIVAL);
        if (tag.contains("XpLevel")) {
            p.setExperienceLevels(tag.getInt("XpLevel"));
            p.experienceProgress = tag.getFloat("XpProgress");
        }
        if (tag.contains("Health")) p.setHealth(Math.max(1.0f, tag.getFloat("Health")));
        if (tag.contains("Food")) p.getFoodData().setFoodLevel(tag.getInt("Food"));
        p.getPersistentData().remove("hitori_return");
        p.getPersistentData().remove("hitori");
        STATES.remove(p.getUUID());
        event(p, EV_LEFT, GameData.get(server).ending, "");
    }

    // ================================================================================================ tick
    public static void serverTick(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null) {
            DANGER.clear();
            return;
        }
        GameData data = GameData.get(server);
        boolean anyone = false;
        for (ServerPlayer p : players(level)) {
            if (!inGame(p)) continue;
            anyone = true;
            tickPlayer(level, p, state(p), data);
        }
        if (data.started && anyone) {
            data.playTicks++;
            if (data.phase != GameData.PHASE_ENDING) Clock.tick(data);
            Apt.tick(level);
            Ritual.tick(level, data);
            if (level.getGameTime() % 20 == 0) {
                Cameras.ensure(level, data);
                Doll.ensure(level, data);
            }
            Doll.tick(level, data);
            Director.tick(level, data);
            Phone.tick(level, data);
            Endings.tick(level, data);
            if (data.playTicks % 100 == 0) data.setDirty();
        }
        DANGER.clear();
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        if (p.isSpectator()) return;
        if (p.tickCount % 100 == 0) {
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(5.0f);
            if (p.getHealth() < p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        }
        if (++ps.saveTimer >= 200) {
            ps.saveTimer = 0;
            savePlayer(p);
        }
        if (ps.hintIn > 0 && --ps.hintIn == 0 && data.phase == GameData.PHASE_PREP) event(p, EV_HINT, 0, "");
        if (ps.endingTicks >= 0) {
            ps.endingTicks++;
            holdStill(p, ps);
            syncTimer(p, ps, data);
            return;
        }
        if (ps.caught()) {
            ps.caughtTicks++;
            holdStill(p, ps);
            syncTimer(p, ps, data);
            return;
        }
        Ritual.tickPlayer(level, p, ps, data);
        if (ps.locked || ps.busy()) holdStill(p, ps);
        tickRoom(level, p, ps, data);
        tickBody(level, p, ps, data);
        tickNerves(level, p, ps, data);
        Hiding.tick(level, p, ps, data);
        Cameras.tickPlayer(level, p, ps, data);
        syncTimer(p, ps, data);
    }

    public static void holdStill(ServerPlayer p, PlayerState ps) {
        if (ps.lockPos == null) ps.lockPos = p.position();
        if (p.position().distanceToSqr(ps.lockPos) > 0.04) setPos(p, ps.lockPos.x, ps.lockPos.y, ps.lockPos.z);
        p.setDeltaMovement(Vec3.ZERO);
    }

    /** Moves a player within the level (the self test's fake player has no connection to teleport through). */
    public static void setPos(ServerPlayer p, double x, double y, double z) {
        if (p instanceof FakePlayer) p.moveTo(x, y, z, p.getYRot(), p.getXRot());
        else p.teleportTo(x, y, z);
    }

    public static void lock(ServerPlayer p, boolean on) {
        PlayerState ps = state(p);
        ps.locked = on;
        ps.lockPos = on ? p.position() : null;
    }

    private static void tickRoom(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        Layout.Room r = Layout.roomAt(p.blockPosition());
        String key = r == null ? "outside" : r.key();
        if (!key.equals(ps.room)) {
            String old = ps.room;
            ps.room = key;
            Director.onEnterRoom(level, p, old, key);
        }
    }

    /** Footsteps by surface and speed (§3.3), landings, the held breath and the gasp, frightened breathing, the mouth. */
    private static void tickBody(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        double dx = p.getX() - ps.lastX, dz = p.getZ() - ps.lastZ;
        double moved = Math.sqrt(dx * dx + dz * dz);
        ps.lastX = p.getX();
        ps.lastZ = p.getZ();
        if (p.onGround() && moved > 0.01 && moved < 1.5 && !ps.hiding()) {
            ps.stepAccum += moved;
            double stride = p.isSprinting() ? 1.7 : 1.3;
            if (ps.stepAccum >= stride) {
                ps.stepAccum = 0;
                step(level, p);
            }
        }
        if (!p.onGround()) {
            if (ps.airTicks++ == 0) ps.airTopY = p.getY();
            ps.airTopY = Math.max(ps.airTopY, p.getY());
        } else {
            if (ps.airTicks > 5 && ps.airTopY - p.getY() > 0.4) NoiseSystem.emit(level, p.position(), p.isShiftKeyDown() ? 4.0f : 8.0f, "landing", p);
            ps.airTicks = 0;
        }
        // the held breath (§3, V): a gauge that empties faster the more afraid you are; let it run out and you gasp
        if (ps.breathHolding) {
            ps.breathTicks++;
            ps.breathGauge -= 1.0f / (240.0f * (1.0f - 0.45f * ps.fear));
            if (ps.breathGauge <= 0.0f) releaseBreath(level, p, ps);
        } else {
            ps.breathGauge = Math.min(1.0f, ps.breathGauge + 0.006f);
        }
        // the salt water: swallowed little by little, faster while you hold your breath
        if (ps.mouth > 0.0f) {
            float drain = 1.0f / (20.0f * 60.0f * (float) HitoriConfig.mouthMinutes());
            if (ps.breathHolding) drain *= 3.0f;
            ps.mouth = Math.max(0.0f, ps.mouth - drain);
            if (ps.mouth <= 0.0f) {
                playerSound(p, ModSounds.PLAYER_SWALLOW.get(), 0.4f, 1.0f);
                thought(p, "thought.hitori.mouth_empty");
            }
        }
        // frightened breathing: heard (4; 2 through salt water)
        if (ps.fear >= 0.8f && !ps.breathHolding && --ps.heavyBreathIn <= 0) {
            NoiseSystem.emit(level, p.getEyePosition(), ps.mouth > 0.0f ? 2.0f : 4.0f, "breath", p);
            ps.heavyBreathIn = 30;
        }
    }

    private static void step(ServerLevel level, ServerPlayer p) {
        BlockPos feet = p.blockPosition();
        String surface = surface(level.getBlockState(feet));
        if (surface == null) surface = surface(level.getBlockState(feet.below()));
        if (surface == null) surface = "floor";
        boolean sneak = p.isShiftKeyDown();
        float r;
        String cause = "step";
        if (p.isSprinting()) {
            r = 12.0f;
            cause = "sprint";
        } else {
            r = switch (surface) {
                case "tatami" -> 3.0f;
                case "tile", "hard", "wet" -> 6.0f;
                default -> 5.0f;
            };
            if (sneak) r = surface.equals("tile") || surface.equals("wet") ? 1.0f : 0.0f;
        }
        if (r > 0) NoiseSystem.emit(level, p.position(), r, cause, p);
    }

    @Nullable
    private static String surface(BlockState s) {
        if (s.isAir()) return null;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(s.getBlock());
        if (id == null) return null;
        String path = id.getPath();
        if (path.startsWith("tatami") || path.equals("zabuton") || path.equals("oshiire_floor") || path.contains("carpet") || path.contains("wool")
                || path.equals("futon_folded")) return "tatami";
        if (path.equals("old_flooring") || path.equals("vinyl_floor") || path.contains("planks")) return "floor";
        if (path.equals("genkan_tile") || path.contains("tile")) return "tile";
        if (path.equals("puddle") || path.equals("asphalt_wet")) return "wet";
        if (path.contains("concrete") || path.contains("stone")) return "hard";
        return null;
    }

    public static void holdBreath(ServerPlayer p, boolean hold) {
        PlayerState ps = state(p);
        if (hold && !ps.breathHolding && ps.breathGauge > 0.15f && inGame(p)) {
            ps.breathHolding = true;
            ps.breathTicks = 0;
        } else if (!hold && ps.breathHolding) {
            releaseBreath(p.serverLevel(), p, ps);
        }
    }

    /** Let go of the breath: quietly, unless it ran low — then a gasp, and the salt water is gone. */
    private static void releaseBreath(ServerLevel level, ServerPlayer p, PlayerState ps) {
        boolean gasp = ps.breathGauge <= 0.22f;
        ps.breathHolding = false;
        ps.breathTicks = 0;
        if (gasp) {
            level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.PLAYER_GASP.get(), SoundSource.PLAYERS, 0.9f, 0.95f + level.random.nextFloat() * 0.1f);
            NoiseSystem.emit(level, p.getEyePosition(), 8.0f, "gasp", p);
            loseWater(p, ps, "gasp");
        }
    }

    /** Fills the mouth from the cup (§3: "refill only from the cup"). */
    public static boolean sip(ServerPlayer p, ItemStack cup) {
        PlayerState ps = state(p);
        int sips = GameItem.sips(cup);
        if (sips <= 0) {
            thought(p, "thought.hitori.cup_empty");
            return false;
        }
        if (ps.mouth >= 0.95f) {
            thought(p, "thought.hitori.mouth_full");
            return false;
        }
        GameItem.setSips(cup, sips - 1);
        ps.mouth = 1.0f;
        playerSound(p, ModSounds.ITEM_CUP.get(), 0.5f, 1.0f);
        NoiseSystem.emit(p.serverLevel(), p.getEyePosition(), ps.hiding() ? 3.0f : 5.0f, "cup", p);
        return true;
    }

    /** The salt water is gone (a gasp, speaking, spitting). While hiding that breaks a rule (§1). */
    public static void loseWater(ServerPlayer p, PlayerState ps, String why) {
        if (ps.mouth <= 0.0f) return;
        ps.mouth = 0.0f;
        GameData data = GameData.get(p.server);
        if (ps.hiding() && data.ritualRunning() && !why.equals("spit")) {
            if (!ps.lostWaterThisHide) {
                ps.lostWaterThisHide = true;
                if (data.breakRule("water_" + data.broken.size())) event(p, EV_RULE, 2, "water");
            }
            thought(p, "thought.hitori.water_lost");
        }
    }

    private static void tickNerves(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        float threat = DANGER.getOrDefault(p.getUUID(), 0.0f);
        ps.danger += (threat - ps.danger) * (threat > ps.danger ? 0.3f : 0.03f);
        int light = level.getBrightness(LightLayer.BLOCK, BlockPos.containing(p.getEyePosition()));
        boolean lit = light >= 7 || phoneLit(p);
        float d = 0.0f;
        if (data.phase == GameData.PHASE_PREP) d += lit ? -0.0008f : 0.0003f;
        else if (!lit) d += 0.0005f;
        if (ps.hiding()) d += 0.0004f;
        OniDoll doll = Doll.get(level);
        if (doll != null && data.ritualRunning()) {
            double dist = doll.distanceTo(p);
            if (dist < 8.0) d += (float) (0.0022 * (1.0 - dist / 8.0));
            if (doll.seenByEyes || (ps.inApp() && doll.seenByCamera)) ps.sawDollAt = data.playTicks;
            if (data.phase == GameData.PHASE_HIDING && data.playTicks - ps.sawDollAt > 20 * 60) d += 0.0003f;
        }
        d += ps.danger * 0.006f;
        float floor = switch (data.phase) {
            case GameData.PHASE_RITUAL -> 0.25f;
            case GameData.PHASE_HIDING -> 0.35f;
            case GameData.PHASE_FINISH -> 0.45f;
            default -> 0.06f;
        };
        ps.fear = Mth.clamp(ps.fear + d, floor, 1.0f);
        if (ps.forceTicks > 0) {
            ps.forceTicks--;
            if (ps.forceFear >= 0) ps.fear = ps.forceFear;
            if (ps.forceDanger >= 0) ps.danger = ps.forceDanger;
        }
    }

    // ================================================================================================ found
    /** The doll found you: its face, black, the 鬼 ending (or, at a debug / test, nothing more). */
    public static void caughtByDoll(ServerPlayer p, @Nullable OniDoll doll) {
        PlayerState ps = state(p);
        if (ps.caught() || ps.endingTicks >= 0) return;
        ps.caughtTicks = 0;
        ps.lockPos = p.position();
        ps.breathHolding = false;
        ps.appCamera = 0;
        event(p, EV_APP, 0, "");
        event(p, EV_JUMPSCARE, 0, "");
        Hitori.LOGGER.info("[hitori] {} was found by the doll", p.getGameProfile().getName());
        Endings.begin(p.serverLevel(), p, GameData.ENDING_ONI);
    }

    // ================================================================================================ items
    public static boolean useItem(ServerPlayer p, InteractionHand hand, GameItem.Kind kind) {
        PlayerState ps = state(p);
        if (ps.caught() || ps.endingTicks >= 0 || ps.locked || ps.busy()) return false;
        return Ritual.useItem(p, hand, kind);
    }

    // ================================================================================================ client actions
    public static void action(ServerPlayer p, int type, int arg) {
        PlayerState ps = state(p);
        switch (type) {
            case C2S.BREATH -> holdBreath(p, arg != 0);
            case C2S.ACTION -> Ritual.actionKey(p);
            case C2S.APP -> Cameras.app(p, ps, arg & 0xFF, arg >> 8);
            case C2S.PEEK -> Hiding.peek(p, arg != 0);
            case C2S.UNHIDE -> Hiding.exit(p, true);
            case C2S.PHONE -> Phone.action(p, arg);
            case C2S.ENDING_DONE -> Endings.done(p);
            case C2S.SHADERS -> ps.shaders = arg != 0;
            case C2S.SKIP -> Endings.skip(p);
            case C2S.PRINTOUT -> Ritual.readPrintout(p);
            default -> {
            }
        }
    }

    // ================================================================================================ sync
    private static void syncTimer(ServerPlayer p, PlayerState ps, GameData data) {
        if (++ps.syncTimer >= 4) {
            ps.syncTimer = 0;
            syncState(p, ps, data);
        }
    }

    public static void syncState(ServerPlayer p, PlayerState ps, GameData data) {
        if (p instanceof FakePlayer) return;
        int flags = 0;
        if (inGame(p)) flags |= F_INGAME;
        if (ps.locked) flags |= F_LOCKED;
        if (ps.hiding()) flags |= F_HIDING;
        if (ps.inCloset()) flags |= F_CLOSET;
        if (ps.peeking) flags |= F_PEEK;
        if (ps.breathHolding) flags |= F_BREATH;
        if (ps.eyesClosed) flags |= F_EYES;
        if (ps.inApp()) flags |= F_APP;
        ServerLevel level = p.serverLevel();
        boolean here = isGameLevel(level);
        if (here && Apt.tvOn(level)) flags |= F_TV;
        if (data.phoneSilent) flags |= F_SILENT;
        if (phoneLit(p)) flags |= F_LIGHT;
        if (ps.ringTicks >= 0) flags |= F_RINGING;
        if (ps.caught()) flags |= F_CAUGHT;
        if (ps.endingTicks >= 0 || data.phase == GameData.PHASE_ENDING) flags |= F_ENDING;
        if (debugPeaceful) flags |= F_PEACEFUL;
        if (ps.busy()) flags |= F_BUSY;
        if (here && Apt.grey(level)) flags |= F_GREY;
        if (ps.sotoTicks >= 0) flags |= F_SOTO;
        if (here) {
            OniDoll doll = Doll.get(level);
            if (doll != null && doll.distanceTo(p) < 6.0) flags |= F_DOLL_NEAR;
            if (doll != null && data.playTicks - ps.sawDollAt < 10) flags |= F_DOLL_SEEN;
        }
        if (has(p, ModItems.SALT_WATER.get())) flags |= F_HAS_WATER;
        String action = ps.action == null ? "" : ps.action;
        float progress = ps.action == null || ps.actionLength <= 0 ? 0.0f : ps.actionTicks / (float) ps.actionLength;
        ModNetwork.send(p, new S2C.State(ps.fear, ps.danger, ps.mouth, ps.breathGauge, flags, data.phase, data.steps, Step.hudLine(data),
                (float) data.clock, data.broken.size(), action, progress, data.dollName, data.cameras.size()));
    }

    public static void onServerStopping(MinecraftServer server) {
        EXTRA.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) savePlayer(p);
        ServerLevel level = level(server);
        if (level != null) Doll.save(level, GameData.get(server));
        STATES.clear();
        DANGER.clear();
        NoiseSystem.clear();
        Director.reset();
        Endings.reset();
        Phone.reset();
    }
}
