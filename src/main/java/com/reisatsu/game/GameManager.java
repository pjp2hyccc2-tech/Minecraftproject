package com.reisatsu.game;

import com.reisatsu.Reisatsu;
import com.reisatsu.ReisatsuConfig;
import com.reisatsu.debug.Greybox;
import com.reisatsu.entity.Medium;
import com.reisatsu.entity.Spirit;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.item.GameItem;
import com.reisatsu.network.C2S;
import com.reisatsu.network.ModNetwork;
import com.reisatsu.network.S2C;
import com.reisatsu.registry.ModItems;
import com.reisatsu.registry.ModParticles;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Circuit;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The week at HIDAMARI on the server: starting and leaving (your own inventory, position and mode are kept and given
 * back), the player's nerves and body (fear, danger, footsteps, breath, the finder's night vision), being caught,
 * the medium, notes, syncing. The nights live in {@link Nights}, the story beats in {@link Story}, the camera in
 * {@link Camera}, the poltergeist and what you witness of it in {@link Director}, the spirits' presence in {@link Spirits}.
 */
public final class GameManager {
    // S2C.Event types
    public static final int EV_INTRO = 1, EV_JUMPSCARE = 2, EV_CAUGHT = 3, EV_WAKE = 4, EV_AREA = 5, EV_SUBTITLE = 6, EV_HINT = 7,
            EV_CLOSING = 8, EV_STINGER = 9, EV_ENDING = 10, EV_LEFT = 11, EV_DAWN = 12, EV_FADE = 13, EV_SHAKE = 14, EV_OBJECTIVE = 15,
            EV_CAPTURED = 16, EV_LOCKER = 17, EV_SCREAM = 18, EV_NOTICED = 19, EV_GLITCH = 20, EV_EXORCISM = 21, EV_SUBMIT = 22,
            EV_FLICKER = 23, EV_THOUGHT = 24, EV_CREDITS = 25, EV_NIGHT = 26, EV_BLACKOUT = 27, EV_WITNESS = 28, EV_MANIFEST = 29;
    // S2C.State flags
    public static final int F_INGAME = 1, F_INTRO = 2, F_CAUGHT = 4, F_ENDING = 8, F_HIDING = 16, F_BREATH = 32, F_HUNTED = 64,
            F_CLOSING = 128, F_STORE_MUSIC = 256, F_LOCKED = 512, F_NEAR_MEDIUM = 1024, F_OUTSIDE = 2048, F_EXORCISM = 4096,
            F_PEACEFUL = 8192, F_FINDER = 16384, F_HINT = 32768, F_SKIPPABLE = 65536;

    public static final int CAUGHT_TICKS = 70;
    private static final Map<UUID, PlayerState> STATES = new HashMap<>();
    /** Players that are not in the level's own list (the self test's fake player). */
    private static final java.util.List<ServerPlayer> EXTRA = new java.util.ArrayList<>();
    private static final Map<UUID, Float> DANGER = new HashMap<>();
    public static boolean debugPeaceful;

    private GameManager() {}

    // ================================================================================================ access
    /** The restaurant's level: the HIDAMARI dimension, or (until the world exists) the overworld greybox. */
    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        ServerLevel l = server.getLevel(Layout.DIMENSION);
        if (l != null) return l;
        return Greybox.fallback(server) ? server.overworld() : null;
    }

    /** Every player of the night in this level (including a test player). */
    public static java.util.List<ServerPlayer> players(ServerLevel level) {
        if (EXTRA.isEmpty()) return level.players();
        java.util.List<ServerPlayer> all = new java.util.ArrayList<>(level.players());
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
        if (level.getServer() != null) return level.getServer().getLevel(Layout.DIMENSION) == null && level.dimension() == Level.OVERWORLD
                && Greybox.fallback(level.getServer());
        return false;
    }

    public static PlayerState state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), id -> {
            PlayerState ps = new PlayerState();
            ps.load(p.getPersistentData().getCompound("reisatsu"));
            ps.lastX = p.getX();
            ps.lastZ = p.getZ();
            return ps;
        });
    }

    public static boolean inGame(@Nullable Player p) {
        return p != null && !p.isSpectator() && isGameLevel(p.level()) && (p instanceof FakePlayer || p.getPersistentData().contains("reisatsu_return")
                || (p.level().getServer() != null && GameData.get(p.level().getServer()).started));
    }

    /** May the spirits notice and take this player now? */
    public static boolean huntable(@Nullable ServerPlayer p) {
        if (p == null || !inGame(p) || p.isCreative() || debugPeaceful) return false;
        PlayerState ps = state(p);
        if (ps.caught() || ps.endingTicks >= 0 || ps.introTicks >= 0 || ps.locked) return false;
        GameData data = GameData.get(p.server);
        return data.phase == GameData.PHASE_INVESTIGATE || data.phase == GameData.PHASE_CLOSING || data.phase == GameData.PHASE_RETURN;
    }

    /** Are the spirits awake (the game running in this level)? */
    public static boolean running(ServerLevel level) {
        if (!isGameLevel(level)) return false;
        GameData data = GameData.get(level.getServer());
        return data.started && (data.phase == GameData.PHASE_INVESTIGATE || data.phase == GameData.PHASE_CLOSING
                || data.phase == GameData.PHASE_RETURN);
    }

    public static int reishou(Player p) {
        return p.level().getServer() == null ? 0 : GameData.get(p.level().getServer()).reishou;
    }

    public static void reportDanger(ServerPlayer p, float v) {
        DANGER.merge(p.getUUID(), v, Math::max);
    }

    /** Fear 0..1. */
    public static void addFear(ServerPlayer p, float amount) {
        PlayerState ps = state(p);
        ps.fear = Mth.clamp(ps.fear + amount, 0.0f, 1.0f);
    }

    public static void event(ServerPlayer p, int type, int arg, String text) {
        if (p instanceof FakePlayer) return;
        ModNetwork.send(p, new S2C.Event(type, arg, text));
    }

    public static void subtitle(ServerPlayer p, String key, int ticks) {
        event(p, EV_SUBTITLE, ticks, key);
    }

    public static void send(ServerPlayer p, Object msg) {
        if (p instanceof FakePlayer) return;
        ModNetwork.send(p, msg);
    }

    /** A sound only this player hears, at a position (director tricks). */
    public static void privateSound(ServerPlayer p, SoundEvent sound, Vec3 at, float volume, float pitch) {
        send(p, new S2C.Sound(sound.getLocation().toString(), at.x, at.y, at.z, volume, pitch));
    }

    public static void worldSound(Level level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
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

    public static boolean consume(Player p, Item item) {
        for (ItemStack s : p.getInventory().items) {
            if (s.is(item)) {
                s.shrink(1);
                return true;
            }
        }
        ItemStack off = p.getOffhandItem();
        if (off.is(item)) {
            off.shrink(1);
            return true;
        }
        return false;
    }

    public static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    /** The player's camera (raised or not), main hand first. */
    public static ItemStack camera(Player p) {
        ItemStack main = p.getMainHandItem();
        if (GameItem.kindOf(main) != null && GameItem.kindOf(main).camera()) return main;
        ItemStack off = p.getOffhandItem();
        if (GameItem.kindOf(off) != null && GameItem.kindOf(off).camera()) return off;
        for (ItemStack s : p.getInventory().items) {
            GameItem.Kind k = GameItem.kindOf(s);
            if (k != null && k.camera()) return s;
        }
        return ItemStack.EMPTY;
    }

    public static boolean finderUp(Player p) {
        return GameItem.is(p.getMainHandItem(), GameItem.Kind.CAMERA_FINDER);
    }

    public static boolean flashlightOn(Player p) {
        return GameItem.is(p.getMainHandItem(), GameItem.Kind.FLASHLIGHT_ON) || GameItem.is(p.getOffhandItem(), GameItem.Kind.FLASHLIGHT_ON);
    }

    /** Replaces the item in a hand keeping its NBT (camera raised / lowered, flashlight on / off). */
    public static void swap(Player p, InteractionHand hand, Item to) {
        ItemStack old = p.getItemInHand(hand);
        ItemStack fresh = new ItemStack(to, Math.max(1, old.getCount()));
        if (old.hasTag() && old.getTag() != null) fresh.setTag(old.getTag().copy());
        p.setItemInHand(hand, fresh);
    }

    // ================================================================================================ start / leave
    public static void onLogin(ServerPlayer p) {
        GameData data = GameData.get(p.server);
        PlayerState ps = state(p);
        if (!data.started && data.ending == GameData.ENDING_NONE) {
            if (ReisatsuConfig.autoStart() && level(p.server) != null && !p.getPersistentData().getBoolean("reisatsu_done")) startGame(p);
            return;
        }
        if (data.started && !p.getPersistentData().contains("reisatsu_return")) {
            // somebody else started the night: join them by the car
            ServerLevel level = level(p.server);
            if (level == null) return;
            saveReturn(p);
            p.getInventory().clearContent();
            giveStartItems(p);
            if (!p.isCreative()) p.setGameMode(GameType.ADVENTURE);
            Layout.Marker w = Layout.marker("wake");
            teleport(p, level, w.center(), w.yaw(), 0.0f);
        }
        if (data.phase == GameData.PHASE_INTRO) {
            Story.finishIntro(p, data);
        } else if (Nights.needsResume(data)) {
            // save and quit: the current night starts again (photos, お札 and notes kept)
            ServerLevel level = level(p.server);
            if (level != null) Nights.start(level, data, data.night, true);
        }
        if (data.phase == GameData.PHASE_ENDING && data.ending != GameData.ENDING_NONE) {
            // the night ended while they were away: show them the end
            ps.endingTicks = 0;
            ps.locked = true;
            event(p, EV_ENDING, data.ending, String.valueOf(data.ofudaUsed));
        }
        syncState(p, ps, data);
        Camera.syncAlbum(p);
    }

    public static void onLogout(ServerPlayer p) {
        savePlayer(p);
        FlashlightLight.remove(p);
        Hiding.exit(p, false);
        STATES.remove(p.getUUID());
    }

    public static void savePlayer(ServerPlayer p) {
        PlayerState ps = STATES.get(p.getUUID());
        if (ps != null) p.getPersistentData().put("reisatsu", ps.save());
    }

    public static void startGame(ServerPlayer p) {
        MinecraftServer server = p.server;
        ServerLevel level = level(server);
        if (level == null) {
            Reisatsu.LOGGER.error("[reisatsu] the restaurant dimension is missing");
            return;
        }
        GameData data = GameData.get(server);
        boolean replay = data.started || data.ending != GameData.ENDING_NONE || data.playTicks > 0;
        boolean introSeen = data.introSeen;
        data.resetProgress();
        data.introSeen = introSeen;
        data.started = true;
        data.phase = GameData.PHASE_INTRO;
        data.setDirty();
        Greybox.ensure(level);
        Fixtures.ensureBuilt(level);
        Nights.reset();
        Director.reset();
        if (replay) {
            Fixtures.resetAll(level);
            Spirits.removeAll(level);
        }
        Story.resetWorld(level);
        if (!p.getPersistentData().contains("reisatsu_return")) saveReturn(p);
        p.getPersistentData().remove("reisatsu_done");
        p.getInventory().clearContent();
        if (!p.isCreative()) p.setGameMode(GameType.ADVENTURE);
        PlayerState ps = new PlayerState();
        STATES.put(p.getUUID(), ps);
        Layout.Marker spawn = Layout.marker("spawn");
        Vec3 med = Layout.marker("medium").center();
        float faceMedium = (float) (Mth.atan2(med.z - spawn.center().z, med.x - spawn.center().x) * Mth.RAD_TO_DEG) - 90.0f;
        teleport(p, level, spawn.center(), faceMedium, 6.0f);
        ps.lastX = p.getX();
        ps.lastZ = p.getZ();
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        Spirits.ensure(level, data);
        Story.startIntro(level, p, ps, data);
        syncState(p, ps, data);
        Camera.syncAlbum(p);
        Reisatsu.LOGGER.info("[reisatsu] {} arrives at HIDAMARI (replay={})", p.getGameProfile().getName(), replay);
    }

    static void giveStartItems(ServerPlayer p) {
        ItemStack cam = new ItemStack(ModItems.REISHA_CAMERA.get());
        GameItem.setFilm(cam, GameItem.START_FILM);
        p.getInventory().setItem(0, cam);
        p.getInventory().setItem(1, new ItemStack(ModItems.FLASHLIGHT.get()));
        p.getInventory().setItem(2, new ItemStack(ModItems.OFUDA.get(), 3));
        p.getInventory().setItem(3, new ItemStack(ModItems.SHOP_KEY.get()));
        p.getInventory().selected = 0;
    }

    public static void teleport(ServerPlayer p, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        p.stopRiding();
        if (p instanceof FakePlayer) p.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
        else p.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch);
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
        p.getPersistentData().put("reisatsu_return", tag);
    }

    /** Back where they came from, with their own things. */
    public static void leaveGame(ServerPlayer p) {
        FlashlightLight.remove(p);
        Hiding.exit(p, false);
        Director.clearFor(p);
        CompoundTag tag = p.getPersistentData().getCompound("reisatsu_return");
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
        p.stopRiding();
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
        p.getPersistentData().remove("reisatsu_return");
        p.getPersistentData().remove("reisatsu");
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
            if (running(level)) data.playTicks++;
            if (level.getGameTime() % 20 == 0) Spirits.ensure(level, data);
            Nights.tick(level, data);
            Story.tick(level, data);
            if (running(level)) Director.tick(level, data);
            FlashlightLight.tick(level);
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
        if (ps.shutterCooldown > 0) ps.shutterCooldown--;
        if (ps.hintIn > 0 && --ps.hintIn == 0) event(p, EV_HINT, 0, "");
        if (ps.endingTicks >= 0) {
            ps.endingTicks++;
            holdStill(p, ps);
            syncTimer(p, ps, data);
            return;
        }
        if (ps.caught()) {
            tickCaught(level, p, ps, data);
            syncTimer(p, ps, data);
            return;
        }
        Story.tickPlayer(level, p, ps, data);
        if (ps.introTicks >= 0 || ps.locked) {
            holdStill(p, ps);
            syncTimer(p, ps, data);
            return;
        }
        tickRoom(level, p, ps, data);
        tickBody(level, p, ps);
        tickLights(level, p, ps);
        tickNerves(level, p, ps, data);
        Hiding.tick(level, p, ps);
        syncTimer(p, ps, data);
    }

    private static void holdStill(ServerPlayer p, PlayerState ps) {
        if (ps.lockPos != null && p.position().distanceToSqr(ps.lockPos) > 0.04) {
            p.teleportTo(ps.lockPos.x, ps.lockPos.y, ps.lockPos.z);
        }
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static void tickRoom(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        Layout.Room r = Layout.roomAt(p.blockPosition());
        String key = r == null ? "outside" : r.key();
        if (!key.equals(ps.room)) {
            String old = ps.room;
            ps.room = key;
            if (r != null && ps.roomsSeen.add(key)) event(p, EV_AREA, 0, key);
            Story.onEnterRoom(level, p, ps, data, old, key);
            Director.onEnterRoom(level, p, key);
        }
        ps.outsideTicks = r == null ? ps.outsideTicks + 1 : 0;
    }

    /** Footsteps by surface and speed (§3.4), landings, the held breath, heavy breathing. */
    private static void tickBody(ServerLevel level, ServerPlayer p, PlayerState ps) {
        double dx = p.getX() - ps.lastX, dz = p.getZ() - ps.lastZ;
        double moved = Math.sqrt(dx * dx + dz * dz);
        ps.lastX = p.getX();
        ps.lastZ = p.getZ();
        if (p.onGround() && moved > 0.01 && moved < 1.5 && !ps.hiding()) {
            ps.stepAccum += moved;
            double stride = p.isSprinting() ? 1.7 : 1.35;
            if (ps.stepAccum >= stride) {
                ps.stepAccum = 0;
                step(level, p);
            }
        }
        if (!p.onGround()) {
            if (ps.airTicks++ == 0) ps.airTopY = p.getY();
            ps.airTopY = Math.max(ps.airTopY, p.getY());
        } else {
            if (ps.airTicks > 5 && ps.airTopY - p.getY() > 0.4) {
                NoiseSystem.emit(level, p.position(), p.isShiftKeyDown() ? 4.0f : 8.0f, "landing", p);
            }
            ps.airTicks = 0;
        }
        // held breath (§3.5): a gauge that drains over 10 s, tighter at high fear; a gasp after more than 4 s
        if (ps.breathHolding) {
            ps.breathTicks++;
            ps.breathGauge -= 1.0f / (200.0f * (1.0f - 0.4f * ps.fear));
            if (ps.breathGauge <= 0.0f) releaseBreath(level, p, ps);
        } else {
            ps.breathGauge = Math.min(1.0f, ps.breathGauge + 0.008f);
        }
        if (ps.fear >= 0.85f && !ps.breathHolding && --ps.heavyBreathIn <= 0) {
            NoiseSystem.emit(level, p.getEyePosition(), 4.0f, "breath", p);
            ps.heavyBreathIn = 30;
        }
    }

    private static void step(ServerLevel level, ServerPlayer p) {
        BlockPos feet = p.blockPosition();
        BlockState at = level.getBlockState(feet);
        BlockState below = level.getBlockState(feet.below());
        String surface = surface(at);
        if (surface == null) surface = surface(below);
        if (surface == null) surface = "tile";
        boolean sneak = p.isShiftKeyDown();
        float r;
        String cause = "step";
        if (p.isSprinting()) {
            r = 14.0f;
            cause = "sprint";
        } else {
            r = switch (surface) {
                case "glass" -> 10.0f;
                case "paper" -> 8.0f;
                case "kitchen" -> 7.0f;
                case "soft" -> 3.0f;
                case "vinyl" -> 5.0f;
                default -> 6.0f;
            };
            if (surface.equals("glass")) cause = "glass";
            if (sneak) r = surface.equals("glass") ? 3.0f : 0.0f;
        }
        if (r > 0) NoiseSystem.emit(level, p.position(), r, cause, p);
    }

    @Nullable
    private static String surface(BlockState s) {
        if (s.isAir()) return null;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(s.getBlock());
        if (id == null) return null;
        String path = id.getPath();
        if (path.equals("broken_plate")) return "glass";
        if (path.equals("menu_scattered")) return "paper";
        if (path.equals("entrance_mat") || path.equals("kids_mat") || path.contains("carpet") || path.contains("wool")) return "soft";
        if (path.equals("kitchen_floor")) return "kitchen";
        if (path.equals("dining_floor")) return "vinyl";
        if (path.equals("restroom_floor") || path.equals("restroom_floor_dark") || path.equals("vestibule_floor") || path.contains("tile")) return "tile";
        if (path.equals("asphalt") || path.equals("parking_line") || path.equals("road_line") || path.equals("sidewalk") || path.equals("curb")) return "wet";
        return path.equals("puddle") ? "wet" : null;
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

    private static void releaseBreath(ServerLevel level, ServerPlayer p, PlayerState ps) {
        boolean gasp = ps.breathTicks > 80 || ps.breathGauge <= 0.0f;
        ps.breathHolding = false;
        ps.breathTicks = 0;
        if (gasp) {
            level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.PLAYER_GASP.get(), SoundSource.PLAYERS, 0.9f, 0.95f + level.random.nextFloat() * 0.1f);
            NoiseSystem.emit(level, p.getEyePosition(), 9.0f, "gasp", p);
        }
    }

    private static void tickLights(ServerLevel level, ServerPlayer p, PlayerState ps) {
        boolean finder = finderUp(p);
        if (finder) {
            MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
            if (nv == null || nv.getDuration() < 300) p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 420, 0, true, false, false));
        } else if (ps.finderUp && p.hasEffect(MobEffects.NIGHT_VISION)) {
            p.removeEffect(MobEffects.NIGHT_VISION);
        }
        ps.finderUp = finder;
        // the flashlight stutters when something is close to taking you
        if (ps.flickerTicks > 0) {
            if (--ps.flickerTicks == 0) {
                for (InteractionHand h : InteractionHand.values()) {
                    if (GameItem.is(p.getItemInHand(h), GameItem.Kind.FLASHLIGHT) && p.getItemInHand(h).getOrCreateTag().getBoolean("flicker")) {
                        p.getItemInHand(h).getOrCreateTag().remove("flicker");
                        swap(p, h, ModItems.FLASHLIGHT_ON.get());
                    }
                }
            }
        } else if (flashlightOn(p) && ps.danger > 0.55f && level.random.nextFloat() < (ps.danger - 0.5f) * 0.05f) {
            for (InteractionHand h : InteractionHand.values()) {
                if (GameItem.is(p.getItemInHand(h), GameItem.Kind.FLASHLIGHT_ON)) {
                    swap(p, h, ModItems.FLASHLIGHT.get());
                    p.getItemInHand(h).getOrCreateTag().putBoolean("flicker", true);
                    ps.flickerTicks = 2 + level.random.nextInt(4);
                    level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.ITEM_FLASHLIGHT_FLICKER.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
                    break;
                }
            }
        }
    }

    private static void tickNerves(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        float aware = 0.0f;
        boolean hunted = false;
        double nearest = 99.0;
        for (Spirit s : Spirits.all(level)) {
            aware = Math.max(aware, s.awarenessOf(p));
            if (s.hunting() && s.huntTarget() == p) hunted = true;
            if (s.mode() != Spirit.Mode.BOUND && s.mode() != Spirit.Mode.EXORCISM) nearest = Math.min(nearest, s.distanceTo(p));
        }
        float threat = Math.max(DANGER.getOrDefault(p.getUUID(), 0.0f), hunted ? 1.0f : aware);
        ps.aware = hunted ? 1.0f : aware;
        ps.danger += (threat - ps.danger) * (threat > ps.danger ? 0.3f : 0.025f);
        int light = level.getBrightness(LightLayer.BLOCK, BlockPos.containing(p.getEyePosition()));
        boolean lit = light >= 7 || flashlightOn(p);
        float d = 0.0f;
        d += lit ? -0.0005f : 0.0006f;
        if (nearest < 10.0) d += (float) (0.0016 * (1.0 - nearest / 10.0));
        d += ps.danger * 0.012f;
        float floor = 0.08f + 0.06f * data.reishou;
        Medium m = Spirits.medium(level);
        if (m != null && m.distanceTo(p) < 10.0 && ps.danger < 0.1f) d -= 0.004f;
        if (ps.fear > floor || d > 0) ps.fear = Mth.clamp(ps.fear + d, floor * 0.5f, 1.0f);
        if (ps.forceTicks > 0) {
            ps.forceTicks--;
            if (ps.forceFear >= 0) ps.fear = ps.forceFear;
            if (ps.forceDanger >= 0) ps.danger = ps.forceDanger;
        }
    }

    // ================================================================================================ noticed / caught
    public static void onNoticed(ServerPlayer p, Spirit s) {
        addFear(p, 0.25f);
        event(p, EV_NOTICED, s.kind().ordinal(), "");
        Director.onNoticed(p.serverLevel(), p, s);
    }

    /** A spirit touched you: its face, black, then you wake by the car — or, with no お札 left, you stay. */
    public static void caught(ServerPlayer p, Spirit s) {
        PlayerState ps = state(p);
        if (ps.caught() || !huntable(p)) return;
        ps.caughtTicks = 0;
        ps.caughtBy = s.kind();
        ps.caughtPos = p.position();
        ps.breathHolding = false;
        Hiding.exit(p, false);
        FlashlightLight.remove(p);
        lowerCamera(p);
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, CAUGHT_TICKS + 30, 0, true, false, false));
        event(p, EV_JUMPSCARE, s.kind().ordinal(), "");
        s.afterCatch();
        for (Spirit o : Spirits.all(p.serverLevel())) {
            if (o != s && o.hunting() && o.huntTarget() == p) o.afterCatch();
            if (o != s) o.setAwareness(p, 0.0f);
        }
        Reisatsu.LOGGER.info("[reisatsu] {} was caught by the {}", p.getGameProfile().getName(), s.kind().id);
    }

    /** Debug / self test: be caught by a kind of spirit without one being near. */
    public static void caughtBy(ServerPlayer p, SpiritKind kind) {
        PlayerState ps = state(p);
        if (ps.caught()) return;
        ps.caughtTicks = 0;
        ps.caughtBy = kind;
        ps.caughtPos = p.position();
        Hiding.exit(p, false);
        lowerCamera(p);
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, CAUGHT_TICKS + 30, 0, true, false, false));
        event(p, EV_JUMPSCARE, kind.ordinal(), "");
    }

    private static void tickCaught(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        ps.caughtTicks++;
        if (ps.caughtPos != null) {
            p.teleportTo(ps.caughtPos.x, ps.caughtPos.y, ps.caughtPos.z);
            p.setDeltaMovement(Vec3.ZERO);
        }
        if (ps.caughtTicks == 13) event(p, EV_CAUGHT, ps.caughtBy == null ? 0 : ps.caughtBy.ordinal(), "");
        if (ps.caughtTicks < CAUGHT_TICKS) return;
        if (!consume(p, ModItems.OFUDA.get())) {
            ps.caughtTicks = -1;
            Story.startBadEnding(level, p, ps, data);
            return;
        }
        data.ofudaUsed++;
        data.reishou = Math.min(3, data.reishou + 1);
        data.setDirty();
        // you come to by the car, the medium in front of you
        Layout.Marker w = Layout.marker("wake");
        Medium medium = Spirits.medium(level);
        Vec3 face = medium != null ? medium.position() : Layout.marker("medium").center();
        float yaw = (float) (Mth.atan2(face.z - w.center().z, face.x - w.center().x) * Mth.RAD_TO_DEG) - 90.0f;
        teleport(p, level, w.center(), yaw, 4.0f);
        ps.resetAfterWake();
        p.removeEffect(MobEffects.DARKNESS);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false, false));
        level.sendParticles(ModParticles.PAPER_ASH.get(), p.getX(), p.getY() + 1.2, p.getZ(), 24, 0.4, 0.5, 0.4, 0.02);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ITEM_OFUDA_BURN.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        event(p, EV_WAKE, count(p, ModItems.OFUDA.get()), "");
        if (medium != null) medium.talk(60);
        Story.say(p, "medium", 70, "dialogue.reisatsu.wake." + Math.min(3, data.ofudaUsed));
        savePlayer(p);
    }

    public static void lowerCamera(ServerPlayer p) {
        for (InteractionHand h : InteractionHand.values()) {
            if (GameItem.is(p.getItemInHand(h), GameItem.Kind.CAMERA_FINDER)) swap(p, h, ModItems.REISHA_CAMERA.get());
        }
        p.removeEffect(MobEffects.NIGHT_VISION);
    }

    // ================================================================================================ items
    public static boolean useItem(ServerPlayer p, InteractionHand hand, GameItem.Kind kind) {
        PlayerState ps = state(p);
        ServerLevel level = p.serverLevel();
        if (ps.caught() || ps.endingTicks >= 0) return false;
        switch (kind) {
            case CAMERA -> {
                if (hand != InteractionHand.MAIN_HAND || ps.hiding() || ps.introTicks >= 0 || ps.locked) return false;
                swap(p, hand, ModItems.REISHA_CAMERA_FINDER.get());
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 420, 0, true, false, false));
                ps.finderUp = true;
                return true;
            }
            case CAMERA_FINDER -> {
                swap(p, hand, ModItems.REISHA_CAMERA.get());
                p.removeEffect(MobEffects.NIGHT_VISION);
                ps.finderUp = false;
                return true;
            }
            case FLASHLIGHT -> {
                swap(p, hand, ModItems.FLASHLIGHT_ON.get());
                level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.ITEM_FLASHLIGHT_ON.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
                NoiseSystem.emit(level, p.getEyePosition(), 3.0f, "flashlight", p);
                return true;
            }
            case FLASHLIGHT_ON -> {
                swap(p, hand, ModItems.FLASHLIGHT.get());
                level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.ITEM_FLASHLIGHT_OFF.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
                NoiseSystem.emit(level, p.getEyePosition(), 3.0f, "flashlight", p);
                return true;
            }
            case FILM_PACK -> {
                ItemStack cam = camera(p);
                if (cam.isEmpty()) return false;
                GameItem.setFilm(cam, Math.min(30, GameItem.film(cam) + GameItem.FILM_PER_PACK));
                p.getItemInHand(hand).shrink(1);
                level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.CAMERA_LOAD.get(), SoundSource.PLAYERS, 0.7f, 1.0f);
                event(p, EV_THOUGHT, 0, "thought.reisatsu.film_loaded");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ================================================================================================ client actions
    public static void action(ServerPlayer p, int type, int arg) {
        PlayerState ps = state(p);
        switch (type) {
            case C2S.INTRO_SKIP -> Story.skipIntro(p, ps, GameData.get(p.server));
            case C2S.ENDING_DONE -> Story.endingDone(p);
            case C2S.CREDITS_DONE -> Story.endingDone(p);
            case C2S.SHADERS -> ps.shaders = arg != 0;
            case C2S.BREATH -> holdBreath(p, arg != 0);
            case C2S.UNHIDE -> Hiding.exit(p, true);
            case C2S.BREAKER -> Breaker.toggle(p, arg >> 1, (arg & 1) != 0);
            case C2S.DIALOGUE_SKIP -> Story.skipDialogue(p);
            default -> {
            }
        }
    }

    // ================================================================================================ the medium, notes
    public static void talkToMedium(ServerPlayer p, Medium m) {
        Story.talk(p, m);
    }

    public static void openNote(ServerPlayer p, String id) {
        GameData data = GameData.get(p.server);
        if (data.notes.add(id)) {
            data.setDirty();
            Camera.syncAlbum(p);
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ITEM_PAPER.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
        send(p, new S2C.Note(id, String.join(",", data.witnessed)));
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
        if (ps.introTicks >= 0) flags |= F_INTRO;
        if (ps.caught()) flags |= F_CAUGHT;
        if (ps.endingTicks >= 0 || data.phase == GameData.PHASE_ENDING) flags |= F_ENDING;
        if (ps.hiding()) flags |= F_HIDING;
        if (ps.breathHolding) flags |= F_BREATH;
        if (ps.aware >= 1.0f) flags |= F_HUNTED;
        ServerLevel level = p.serverLevel();
        boolean here = isGameLevel(level);
        if (data.phase == GameData.PHASE_CLOSING || (data.happenedTonight("closing") && data.phase <= GameData.PHASE_RETURN)) flags |= F_CLOSING;
        if (here && Fixtures.deviceOn(level, "store_music") && data.phase <= GameData.PHASE_RETURN) flags |= F_STORE_MUSIC;
        if (ps.locked) flags |= F_LOCKED;
        if (Nights.skippable()) flags |= F_SKIPPABLE;
        if (data.phase == GameData.PHASE_EXORCISM) flags |= F_EXORCISM;
        if (debugPeaceful) flags |= F_PEACEFUL;
        if (ps.finderUp) flags |= F_FINDER;
        Medium m = here ? Spirits.medium(level) : null;
        if (m != null && m.distanceTo(p) < 12.0) flags |= F_NEAR_MEDIUM;
        if ("outside".equals(ps.room)) flags |= F_OUTSIDE;
        int circuits = 0;
        if (here) {
            for (Circuit c : Circuit.values()) if (Fixtures.circuitOn(level, c)) circuits |= 1 << c.ordinal();
        }
        ModNetwork.send(p, new S2C.State(ps.fear, ps.danger, ps.aware, flags, GameItem.film(camera(p)), count(p, ModItems.OFUDA.get()),
                data.captured, data.submitted, data.phase, data.reishou, ps.breathGauge, circuits, data.night, data.nightStage, data.signs.size()));
    }

    public static void onServerStopping(MinecraftServer server) {
        EXTRA.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Hiding.exit(p, false);
            savePlayer(p);
        }
        ServerLevel level = level(server);
        if (level != null) FlashlightLight.removeAll(level);
        STATES.clear();
        DANGER.clear();
        NoiseSystem.clear();
        Director.reset();
        Story.reset();
        Nights.reset();
    }
}
