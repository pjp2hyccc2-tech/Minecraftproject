package com.sounan.game;

import com.sounan.Sounan;
import com.sounan.SounanConfig;
import com.sounan.block.HokoraBlock;
import com.sounan.block.KeyHookBlock;
import com.sounan.block.LockedGateBlock;
import com.sounan.block.LostBagBlock;
import com.sounan.block.RopeAnchorBlock;
import com.sounan.block.TunnelLampBlock;
import com.sounan.item.GameItem;
import com.sounan.network.ModNetwork;
import com.sounan.network.S2C;
import com.sounan.registry.ModBlocks;
import com.sounan.registry.ModDimensions;
import com.sounan.registry.ModItems;
import com.sounan.registry.ModSounds;
import com.sounan.world.Pois;
import com.sounan.world.PoiLayout;
import com.sounan.world.TerrainData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The night on 帰らず山: progress, the player's body and nerves, gates, captures, endings. Server side. */
public final class GameManager {
    public static final int FLARE_TICKS = 20 * 40;
    public static final int CAPTURE_TICKS = 44;
    public static final int MAX_CAPTURES = 5;
    /** Phone clock: 2:13 AM when you wake up; one phone minute per 30 seconds of play. */
    public static final int START_MINUTES = 2 * 60 + 13;

    // S2C.Event types
    public static final int EV_JUMPSCARE = 1, EV_WAKE = 2, EV_INTRO = 3, EV_AREA = 4, EV_THOUGHT = 5, EV_LOOP = 6, EV_CHECKPOINT = 7,
            EV_SIGNAL = 8, EV_CALL = 9, EV_ENDING = 10, EV_BUZZ = 11, EV_FLASH = 12, EV_OMAMORI = 13, EV_SHAKE = 14, EV_SILENCE = 15,
            EV_FOG_LIFT = 16, EV_STINGER = 17, EV_LEFT = 18, EV_SUBTITLE = 19, EV_GLITCH = 20;
    // capture kinds (= jumpscare images)
    public static final int KIND_NOZOKI = 0, KIND_YOBIGOE = 1, KIND_USHIRO = 2, KIND_KOYANUSHI = 3, KIND_OYAMASAMA = 4;
    // S2C.State flags
    public static final int F_INGAME = 1, F_SIGNAL = 2, F_MAP = 4, F_PHONE_DEAD = 8, F_CAPTURED = 16, F_EXHAUSTED = 32, F_OFFERED = 64,
            F_BAG = 128, F_CALLED = 256, F_CLIMAX = 512, F_ENDING = 1024, F_KEY = 2048, F_FLARE = 4096;

    private static final Map<UUID, PlayerState> STATES = new HashMap<>();
    /** Highest threat reported this tick per player (from the monsters / director). */
    private static final Map<UUID, Float> THREAT = new HashMap<>();
    public static boolean debugPeaceful;
    public static volatile boolean cinematic;

    private GameManager() {}

    // ------------------------------------------------------------------------------------------------ access
    @Nullable
    public static ServerLevel mountain(MinecraftServer server) {
        return server.getLevel(ModDimensions.KAERAZU);
    }

    public static PlayerState state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), id -> {
            PlayerState ps = new PlayerState();
            ps.load(p.getPersistentData().getCompound("sounan"));
            return ps;
        });
    }

    public static boolean inGame(Player p) {
        return p != null && ModDimensions.isMountain(p.level()) && !p.isSpectator();
    }

    /** Players monsters may hunt (not creative/spectator, not already being taken, not in an ending). */
    public static boolean huntable(ServerPlayer p) {
        if (!inGame(p) || p.isCreative() || debugPeaceful || cinematic) return false;
        PlayerState ps = state(p);
        return !ps.captured && ps.endingTicks < 0 && ps.introTicks < 0;
    }

    public static void reportThreat(ServerPlayer p, float threat) {
        THREAT.merge(p.getUUID(), threat, Math::max);
    }

    public static void addFear(ServerPlayer p, float amount) {
        PlayerState ps = state(p);
        float scaled = amount > 0 ? (float) (amount * SounanConfig.fearRate()) : amount;
        ps.fear = Mth.clamp(ps.fear + scaled, 0.0f, 100.0f);
    }

    public static void event(ServerPlayer p, int type, int arg, String text) {
        ModNetwork.send(p, new S2C.Event(type, arg, text));
    }

    public static void thought(ServerPlayer p, String key) {
        event(p, EV_THOUGHT, 0, key);
    }

    /** A sound only this player hears, at a position (director tricks). */
    public static void privateSound(ServerPlayer p, SoundEvent sound, Vec3 at, float volume, float pitch) {
        ModNetwork.send(p, new S2C.Sound(sound.getLocation().toString(), at.x, at.y, at.z, volume, pitch));
    }

    public static void worldSound(Level level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    public static boolean has(Player p, Item item) {
        for (ItemStack s : p.getInventory().items) if (s.is(item)) return true;
        return p.getOffhandItem().is(item);
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

    @Nullable
    public static InteractionHand handHolding(Player p, GameItem.Kind... kinds) {
        for (InteractionHand h : InteractionHand.values()) {
            GameItem.Kind k = GameItem.kindOf(p.getItemInHand(h));
            if (k == null) continue;
            for (GameItem.Kind want : kinds) if (k == want) return h;
        }
        return null;
    }

    public static boolean holding(Player p, GameItem.Kind... kinds) {
        return handHolding(p, kinds) != null;
    }

    public static boolean flashlightOn(Player p) {
        return holding(p, GameItem.Kind.FLASHLIGHT_ON, GameItem.Kind.FLASHLIGHT_LOW);
    }

    public static boolean anyLight(Player p) {
        return holding(p, GameItem.Kind.FLASHLIGHT_ON, GameItem.Kind.FLASHLIGHT_LOW, GameItem.Kind.PHONE_LIGHT, GameItem.Kind.FLARE_LIT);
    }

    public static boolean flareLit(Player p) {
        return holding(p, GameItem.Kind.FLARE_LIT);
    }

    /** Replaces the item in a hand keeping the hand (flashlight on/off, phone modes). */
    public static void swap(Player p, InteractionHand hand, Item to) {
        ItemStack old = p.getItemInHand(hand);
        ItemStack fresh = new ItemStack(to, Math.max(1, old.getCount()));
        if (old.hasTag() && old.getTag() != null) fresh.setTag(old.getTag().copy());
        p.setItemInHand(hand, fresh);
    }

    /** Is the entity inside this player's flashlight / phone light beam? */
    public static boolean inBeam(ServerPlayer p, Entity e, double maxDist) {
        if (!anyLight(p)) return false;
        boolean wide = holding(p, GameItem.Kind.PHONE_LIGHT) && !flashlightOn(p);
        double range = flashlightOn(p) ? maxDist : Math.min(maxDist, 12.0);
        Vec3 eye = p.getEyePosition();
        Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
        double d = to.length();
        if (d > range || d < 0.01) return false;
        double cos = to.scale(1.0 / d).dot(p.getViewVector(1.0f));
        if (cos < (wide ? 0.80 : 0.91)) return false;
        return p.hasLineOfSight(e);
    }

    /** Is the player looking at the entity (in view, unobstructed)? */
    public static boolean looksAt(ServerPlayer p, Entity e, double minDot, double maxDist) {
        Vec3 eye = p.getEyePosition();
        Vec3 to = e.getEyePosition().subtract(eye);
        double d = to.length();
        if (d > maxDist || d < 0.01) return false;
        if (to.scale(1.0 / d).dot(p.getViewVector(1.0f)) < minDot) return false;
        return p.hasLineOfSight(e);
    }

    public static int areaAt(ServerPlayer p) {
        PoiLayout L = PoiLayout.get();
        if (L.inTunnel(p.getX(), p.getY(), p.getZ())) return TerrainData.AREA_TUNNEL;
        int a = TerrainData.get().area(p.getX(), p.getZ());
        if (a == TerrainData.AREA_TUNNEL) return TerrainData.AREA_OUT;   // on top of the ridge
        return a;
    }

    public static int phoneMinutes(GameData data) {
        return START_MINUTES + (int) (data.playTicks / 600L);
    }

    // ------------------------------------------------------------------------------------------------ start / leave
    public static void onLogin(ServerPlayer p) {
        PlayerState ps = state(p);
        GameData data = GameData.get(p.server);
        if (!data.started && data.ending == 0) {
            if (SounanConfig.autoStart()) startGame(p);
            return;
        }
        if (data.started && data.ending == 0 && !inGame(p) && !p.getPersistentData().contains("sounan_return")) {
            // somebody else started the night: join them at the checkpoint
            saveReturn(p);
            p.getInventory().clearContent();
            giveStartItems(p);
            if (!p.isCreative()) p.setGameMode(GameType.ADVENTURE);
            toCheckpoint(p, data);
        }
        syncState(p, ps, data);
    }

    public static void onLogout(ServerPlayer p) {
        savePlayer(p);
        FlashlightLight.remove(p);
        STATES.remove(p.getUUID());
    }

    public static void savePlayer(ServerPlayer p) {
        PlayerState ps = STATES.get(p.getUUID());
        if (ps != null) p.getPersistentData().put("sounan", ps.save());
    }

    public static void startGame(ServerPlayer p) {
        MinecraftServer server = p.server;
        ServerLevel level = mountain(server);
        if (level == null) {
            Sounan.LOGGER.error("[sounan] the mountain dimension is missing");
            return;
        }
        GameData data = GameData.get(server);
        if (!data.started || data.ending != 0) {
            boolean replay = data.ending != 0 || data.bagOpened;
            data.reset();
            data.started = true;
            data.setDirty();
            if (replay) resetWorldState(level);
        }
        if (!inGame(p) && !p.getPersistentData().contains("sounan_return")) saveReturn(p);
        p.getInventory().clearContent();
        giveStartItems(p);
        if (!p.isCreative()) p.setGameMode(GameType.ADVENTURE);
        PlayerState ps = new PlayerState();
        STATES.put(p.getUUID(), ps);
        PoiLayout L = PoiLayout.get();
        teleport(p, level, Vec3.atBottomCenterOf(L.start), L.startFacing.toYRot(), 20.0f);
        ps.safePos = Vec3.atBottomCenterOf(L.start);
        ps.safeYaw = L.startFacing.toYRot();
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        ps.introTicks = 0;
        ps.fear = 35.0f;
        event(p, EV_INTRO, 0, p.getGameProfile().getName());
        syncState(p, ps, data);
        Sounan.LOGGER.info("[sounan] {} is lost on the mountain", p.getGameProfile().getName());
    }

    /** A new night in a world that has already been played: everything the last night changed goes back. */
    public static void resetWorldState(ServerLevel level) {
        PoiLayout L = PoiLayout.get();
        BlockState bag = level.getBlockState(L.bag);
        if (bag.hasProperty(LostBagBlock.OPEN)) level.setBlock(L.bag, bag.setValue(LostBagBlock.OPEN, false), 3);
        BlockState anchor = level.getBlockState(L.ropeAnchor);
        if (anchor.hasProperty(RopeAnchorBlock.ROPED)) level.setBlock(L.ropeAnchor, anchor.setValue(RopeAnchorBlock.ROPED, false), 3);
        for (int y = L.ropeTopY; y >= L.ropeBottomY; y--) {
            BlockPos rp = new BlockPos(L.ropeX, y, L.ropeZ);
            if (level.getBlockState(rp).is(ModBlocks.CLIMBING_ROPE.get())) level.setBlock(rp, Blocks.AIR.defaultBlockState(), 3);
        }
        if (level.getBlockState(L.omiki).isAir()) level.setBlock(L.omiki, ModBlocks.OMIKI_BOTTLE.get().defaultBlockState(), 3);
        BlockState hok = level.getBlockState(L.hokora);
        if (hok.hasProperty(HokoraBlock.OFFERED)) level.setBlock(L.hokora, hok.setValue(HokoraBlock.OFFERED, false), 3);
        BlockState hook = level.getBlockState(L.hutKeyHook);
        if (hook.hasProperty(KeyHookBlock.HAS_KEY)) level.setBlock(L.hutKeyHook, hook.setValue(KeyHookBlock.HAS_KEY, true), 3);
        for (BlockPos g : new BlockPos[]{L.gate, L.gate.above()}) {
            BlockState gs = level.getBlockState(g);
            if (gs.getBlock() instanceof LockedGateBlock) {
                level.setBlock(g, gs.setValue(LockedGateBlock.LOCKED, true).setValue(net.minecraft.world.level.block.DoorBlock.OPEN, false), 3);
            }
        }
        for (BlockPos lp : L.tunnelLamps) setTunnelLamp(level, lp, false);
    }

    static void giveStartItems(ServerPlayer p) {
        p.getInventory().add(new ItemStack(ModItems.FLASHLIGHT.get()));
        p.getInventory().add(new ItemStack(ModItems.PHONE.get()));
    }

    public static void teleport(ServerPlayer p, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        p.stopRiding();
        p.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    public static void toCheckpoint(ServerPlayer p, GameData data) {
        ServerLevel level = mountain(p.server);
        if (level == null) return;
        Pois.Point cp = Pois.get().checkpoint(data.checkpoint);
        PoiLayout L = PoiLayout.get();
        Vec3 pos = cp != null ? cp.center() : Vec3.atBottomCenterOf(L.start);
        float yaw = cp != null ? cp.facing().toYRot() : L.startFacing.toYRot();
        teleport(p, level, pos, yaw, 10.0f);
        PlayerState ps = state(p);
        ps.safePos = pos;
        ps.safeYaw = yaw;
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
        p.getPersistentData().put("sounan_return", tag);
    }

    /** Back to the world they came from, with their own things. */
    public static void leaveGame(ServerPlayer p) {
        FlashlightLight.remove(p);
        Director.clearFor(p);
        CompoundTag tag = p.getPersistentData().getCompound("sounan_return");
        MinecraftServer server = p.server;
        ServerLevel target = server.overworld();
        double x, y, z;
        float yaw = p.getYRot();
        if (tag.contains("Dim")) {
            ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new ResourceLocation(tag.getString("Dim")));
            ServerLevel l = server.getLevel(key);
            if (l != null && l != mountain(server)) target = l;
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
        p.getPersistentData().remove("sounan_return");
        STATES.remove(p.getUUID());
        p.getPersistentData().remove("sounan");
        event(p, EV_LEFT, GameData.get(server).ending, "");
    }

    // ------------------------------------------------------------------------------------------------ tick
    public static void serverTick(MinecraftServer server) {
        THREAT.keySet().retainAll(STATES.keySet());
        ServerLevel level = mountain(server);
        if (level == null) return;
        GameData data = GameData.get(server);
        if (data.started && data.ending == 0 && !level.players().isEmpty() && !cinematic) data.playTicks++;
        for (ServerPlayer p : level.players()) {
            PlayerState ps = state(p);
            tickPlayer(level, p, ps, data);
        }
        FlashlightLight.tick(level);
        if (!cinematic) Director.tickLevel(level, data);
        THREAT.clear();
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        if (p.isSpectator()) return;
        PoiLayout L = PoiLayout.get();
        Pois pois = Pois.get();
        TerrainData terrain = TerrainData.get();
        if (p.tickCount % 100 == 0) {
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(5.0f);
        }
        if (++ps.saveTimer >= 200) {
            ps.saveTimer = 0;
            savePlayer(p);
        }
        if (ps.hintIn > 0 && --ps.hintIn == 0) event(p, EV_SUBTITLE, 0, "hint.sounan.controls");
        if (ps.introTicks >= 0) {
            ps.introTicks++;
            if (ps.introTicks == 20 * 15) introOver(p, ps);
            if (ps.introTicks < 20 * 15) {
                p.setDeltaMovement(Vec3.ZERO);
                syncTimer(p, ps, data);
                return;
            }
        }
        if (ps.endingTicks >= 0) {
            ps.endingTicks++;
            syncTimer(p, ps, data);
            return;
        }
        if (ps.captured) {
            tickCapture(level, p, ps, data);
            syncTimer(p, ps, data);
            return;
        }

        // ---- where am I
        int area = areaAt(p);
        if (area != ps.area) {
            ps.area = area;
            if (area > 0 && data.once("area_" + TerrainData.areaKey(area))) {
                event(p, EV_AREA, area, "");
                Director.onAreaEntered(level, p, ps, data, area);
            }
        }
        boolean fog = !data.offered && pois.inFogBand(p.getX(), p.getZ());
        boolean out = area == TerrainData.AREA_OUT && !p.isCreative();
        boolean fell = p.getY() < terrain.height(p.getBlockX(), p.getBlockZ()) - 6 && !L.inTunnel(p.getX(), p.getY(), p.getZ());
        if (fog) {
            if (ps.fogTicks++ == 0 && data.once("fog_first")) thought(p, "thought.sounan.fog");
        } else ps.fogTicks = 0;
        if ((out || fog) && !p.isCreative()) ps.outTicks++;
        else {
            ps.outTicks = 0;
            if (p.tickCount % 40 == 0 && p.onGround() && area > 0 && !fell) {
                ps.safePos = p.position();
                ps.safeYaw = p.getYRot();
            }
        }
        if (fell && !p.isCreative() && p.getY() < terrain.height(p.getBlockX(), p.getBlockZ()) - 10) loopBack(p, ps);
        else if (ps.outTicks > (fog ? 70 : 50)) loopBack(p, ps);
        else if (p.fallDistance > 7.0f && !p.onClimbable() && !p.isCreative()) {
            // a fall like that would end the night; the mountain gives you back instead
            if (data.once("fall_warning")) thought(p, "thought.sounan.fall");
            loopBack(p, ps);
        }

        // ---- body: stamina, noise
        tickBody(level, p, ps);

        // ---- light: flashlight and phone batteries, camera night vision, flare
        tickLights(level, p, ps, data);

        // ---- nerves
        tickFear(level, p, ps, data);

        // ---- checkpoints and the pylon signal
        tickPlaces(level, p, ps, data, L, pois);

        syncTimer(p, ps, data);
    }

    private static void introOver(ServerPlayer p, PlayerState ps) {
        ps.introTicks = -1;
        thought(p, "thought.sounan.wake");
        ps.hintIn = 70; // the controls a little later, once the title has gone
    }

    private static void tickBody(ServerLevel level, ServerPlayer p, PlayerState ps) {
        boolean sprinting = p.isSprinting();
        double dx = p.getX() - ps.lastX, dz = p.getZ() - ps.lastZ;
        double speed = Math.sqrt(dx * dx + dz * dz);
        ps.lastX = p.getX();
        ps.lastZ = p.getZ();
        if (speed < 0.01) ps.stillTicks++;
        else ps.stillTicks = 0;
        if (sprinting) ps.stamina = Math.max(0.0f, ps.stamina - 0.55f);
        else ps.stamina = Math.min(100.0f, ps.stamina + (speed > 0.05 ? 0.22f : 0.45f));
        if (ps.stamina <= 0.0f && ps.exhausted <= 0) {
            ps.exhausted = 90;
            worldSound(level, p.position(), ModSounds.PLAYER_BREATH_RUN.get(), 0.9f, 1.0f);
            NoiseSystem.emit(level, p.position(), 10.0f, NoiseSystem.Source.VOICE);
        }
        if (ps.exhausted > 0) {
            ps.exhausted--;
            p.setSprinting(false);
            if (p.tickCount % 20 == 0) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false, false));
        }
        if (p.tickCount % 10 == 0) {
            if (sprinting) NoiseSystem.emit(level, p.position(), 16.0f, NoiseSystem.Source.SPRINT);
            else if (speed > 0.06 && !p.isShiftKeyDown()) NoiseSystem.emit(level, p.position(), 5.0f, NoiseSystem.Source.STEP);
        }
        if (!p.onGround() && p.getDeltaMovement().y > 0.3 && !p.onClimbable()) {
            NoiseSystem.emit(level, p.position(), 9.0f, NoiseSystem.Source.STEP);
        }
        if (ps.fear >= 85.0f && p.tickCount % 20 == 0) NoiseSystem.emit(level, p.position(), 6.0f, NoiseSystem.Source.VOICE);
        if (ps.bellCooldown > 0) ps.bellCooldown--;
        if (ps.photoCooldown > 0) ps.photoCooldown--;
    }

    private static void tickLights(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        InteractionHand fh = handHolding(p, GameItem.Kind.FLASHLIGHT_ON, GameItem.Kind.FLASHLIGHT_LOW);
        if (fh != null) {
            ps.flash = Math.max(0.0f, ps.flash - 100.0f / (10 * 60 * 20));
            GameItem.Kind k = GameItem.kindOf(p.getItemInHand(fh));
            if (ps.flash <= 0.0f) {
                swap(p, fh, ModItems.FLASHLIGHT.get());
                worldSound(level, p.position(), ModSounds.ITEM_FLASHLIGHT_FLICKER.get(), 0.7f, 0.8f);
                thought(p, "thought.sounan.flash_dead");
                addFear(p, 10.0f);
            } else if (ps.flash < 15.0f && k == GameItem.Kind.FLASHLIGHT_ON) {
                swap(p, fh, ModItems.FLASHLIGHT_LOW.get());
                if (ps.lowWarned++ == 0) thought(p, "thought.sounan.flash_low");
            } else if (ps.flash >= 15.0f && k == GameItem.Kind.FLASHLIGHT_LOW) {
                swap(p, fh, ModItems.FLASHLIGHT_ON.get());
            }
        }
        float drain = 0.0f;
        boolean camera = holding(p, GameItem.Kind.PHONE_CAMERA);
        if (camera) drain += 1.0f / (20 * 20);
        if (holding(p, GameItem.Kind.PHONE_LIGHT)) drain += 1.0f / (12 * 20);
        if (ps.phoneOpen) drain += 1.0f / (40 * 20);
        if (drain > 0.0f && ps.phone > 0.0f) {
            ps.phone = Math.max(0.0f, ps.phone - drain);
            if (ps.phone <= 0.0f) {
                InteractionHand ph = handHolding(p, GameItem.Kind.PHONE_CAMERA, GameItem.Kind.PHONE_LIGHT);
                if (ph != null) swap(p, ph, ModItems.PHONE.get());
                privateSound(p, ModSounds.PHONE_DEAD.get(), p.getEyePosition(), 0.8f, 1.0f);
                thought(p, "thought.sounan.phone_dead");
                camera = false;
            } else if (ps.phone < 10.0f && p.tickCount % 600 == 0) {
                privateSound(p, ModSounds.PHONE_LOW_BATTERY.get(), p.getEyePosition(), 0.6f, 1.0f);
            }
        }
        if (camera) {
            MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
            if (nv == null || nv.getDuration() < 300) p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 420, 0, false, false, false));
        } else if (p.hasEffect(MobEffects.NIGHT_VISION)) {
            p.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    private static void tickFear(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        float threat = THREAT.getOrDefault(p.getUUID(), 0.0f);
        ps.danger += (threat - ps.danger) * (threat > ps.danger ? 0.25f : 0.03f);
        BlockPos eye = BlockPos.containing(p.getEyePosition());
        int blockLight = level.getBrightness(LightLayer.BLOCK, eye);
        boolean light = anyLight(p) || blockLight >= 7;
        float d = 0.0f;
        if (!light) d += 0.030f;
        else d -= 0.010f;
        d += ps.danger * 0.22f;
        if (ps.area == TerrainData.AREA_TUNNEL) d += 0.012f;
        float calm = 22.0f + data.captures * 4.0f;
        if (ps.fear > calm && light && ps.danger < 0.1f) d -= 0.018f;
        if (d > 0) d *= (float) SounanConfig.fearRate();
        ps.fear = Mth.clamp(ps.fear + d, 0.0f, 100.0f);
        if (ps.forceTicks > 0) {
            ps.forceTicks--;
            if (ps.forceFear >= 0) ps.fear = ps.forceFear;
            if (ps.forceDanger >= 0) ps.danger = ps.forceDanger;
        }
    }

    private static void tickPlaces(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data, PoiLayout L, Pois pois) {
        for (Pois.Point cp : pois.checkpoints) {
            if (cp.id().equals(data.checkpoint)) continue;
            if (p.distanceToSqr(cp.center()) > 4.5 * 4.5) continue;
            if (cp.id().equals("hut") && !data.keyTaken) continue;
            if (cp.id().equals("tunnel") && !data.keyTaken) continue;
            if (cp.id().equals("bag") && !data.bagOpened) continue;
            data.checkpoint = cp.id();
            data.setDirty();
            event(p, EV_CHECKPOINT, 0, cp.id());
            savePlayer(p);
        }
        Pois.Point here = pois.checkpoint(data.checkpoint);
        if (here != null && p.distanceToSqr(here.center()) < 5.0 * 5.0 && ps.danger < 0.2f) {
            ps.fear = Math.max(10.0f, ps.fear - 0.2f);
        }
        if (nearPylon(p, L)) {
            if (!data.signal) {
                data.signal = true;
                data.setDirty();
                event(p, EV_SIGNAL, data.captures, p.getGameProfile().getName());
                thought(p, "thought.sounan.signal");
            }
        }
    }

    public static boolean nearPylon(Player p, PoiLayout L) {
        double dx = p.getX() - (L.pylon.getX() + 0.5), dz = p.getZ() - (L.pylon.getZ() + 0.5);
        return dx * dx + dz * dz < 11.0 * 11.0 && p.getY() > L.pylon.getY() - 3;
    }

    /** Lost in the fog: you come out where you went in, facing the other way. */
    public static void loopBack(ServerPlayer p, PlayerState ps) {
        ServerLevel level = mountain(p.server);
        if (level == null) return;
        Vec3 target = ps.safePos != null ? ps.safePos : Vec3.atBottomCenterOf(PoiLayout.get().start);
        teleport(p, level, target, ps.safeYaw + 180.0f, 0.0f);
        ps.outTicks = 0;
        ps.fogTicks = 0;
        addFear(p, 9.0f);
        event(p, EV_LOOP, 0, "");
    }

    // ------------------------------------------------------------------------------------------------ capture
    /** A monster got you. Returns false if the omamori saved you. */
    public static boolean beginCapture(ServerPlayer p, int kind, @Nullable Entity monster) {
        PlayerState ps = state(p);
        if (ps.captured || !huntable(p)) return false;
        if (consume(p, ModItems.OMAMORI.get())) {
            event(p, EV_OMAMORI, kind, "");
            worldSound(p.level(), p.position(), ModSounds.ITEM_OMAMORI.get(), 1.0f, 1.0f);
            addFear(p, 25.0f);
            if (monster != null) monster.discard();
            Director.calmDown(p, 20 * 40);
            return false;
        }
        ps.captured = true;
        ps.captureTicks = 0;
        ps.captureKind = kind;
        ps.capturePos = p.position();
        ps.fear = 100.0f;
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, CAPTURE_TICKS + 40, 0, false, false, false));
        event(p, EV_JUMPSCARE, kind, "");
        FlashlightLight.remove(p);
        return true;
    }

    private static void tickCapture(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        ps.captureTicks++;
        if (ps.capturePos != null) {
            p.teleportTo(ps.capturePos.x, ps.capturePos.y, ps.capturePos.z);
            p.setDeltaMovement(Vec3.ZERO);
        }
        if (ps.captureTicks < CAPTURE_TICKS) return;
        data.captures++;
        data.setDirty();
        Director.clearFor(p);
        if (data.captures >= MAX_CAPTURES) {
            spiritedAway(p, ps, data);
            return;
        }
        toCheckpoint(p, data);
        ps.resetAfterCapture();
        p.removeEffect(MobEffects.DARKNESS);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false, false));
        event(p, EV_WAKE, data.captures, "");
        savePlayer(p);
    }

    // ------------------------------------------------------------------------------------------------ endings
    public static void startEnding(ServerPlayer p, int ending) {
        GameData data = GameData.get(p.server);
        if (data.ending != 0) return;
        data.ending = ending;
        data.setDirty();
        PlayerState ps = state(p);
        ps.endingTicks = 0;
        Director.clearFor(p);
        FlashlightLight.remove(p);
        event(p, EV_ENDING, ending, p.getGameProfile().getName() + "|" + data.captures + "|" + phoneMinutes(data));
    }

    private static void spiritedAway(ServerPlayer p, PlayerState ps, GameData data) {
        ps.captured = false;
        startEnding(p, 2);
    }

    public static void endingDone(ServerPlayer p) {
        PlayerState ps = state(p);
        if (ps.endingTicks < 0) return;
        leaveGame(p);
    }

    // ------------------------------------------------------------------------------------------------ items
    public static boolean useItem(ServerPlayer p, InteractionHand hand, GameItem.Kind kind, boolean sneak) {
        PlayerState ps = state(p);
        ServerLevel level = p.serverLevel();
        switch (kind) {
            case FLASHLIGHT -> {
                if (ps.flash <= 0.0f) {
                    worldSound(level, p.position(), ModSounds.ITEM_FLASHLIGHT_OFF.get(), 0.6f, 0.8f);
                    thought(p, "thought.sounan.flash_empty");
                    return true;
                }
                swap(p, hand, ps.flash < 15.0f ? ModItems.FLASHLIGHT_LOW.get() : ModItems.FLASHLIGHT_ON.get());
                worldSound(level, p.position(), ModSounds.ITEM_FLASHLIGHT_ON.get(), 0.6f, 1.0f);
                return true;
            }
            case FLASHLIGHT_ON, FLASHLIGHT_LOW -> {
                swap(p, hand, ModItems.FLASHLIGHT.get());
                worldSound(level, p.position(), ModSounds.ITEM_FLASHLIGHT_OFF.get(), 0.6f, 1.0f);
                return true;
            }
            case PHONE -> {
                if (!sneak) return false;
                if (ps.phone <= 0.0f) return false;
                swap(p, hand, ModItems.PHONE_LIGHT.get());
                privateSound(p, ModSounds.PHONE_TAP.get(), p.getEyePosition(), 0.5f, 1.0f);
                return true;
            }
            case PHONE_LIGHT -> {
                if (!sneak) return false;
                swap(p, hand, ModItems.PHONE.get());
                privateSound(p, ModSounds.PHONE_TAP.get(), p.getEyePosition(), 0.5f, 0.9f);
                return true;
            }
            case PHONE_CAMERA -> {
                if (sneak) {
                    swap(p, hand, ModItems.PHONE.get());
                    p.removeEffect(MobEffects.NIGHT_VISION);
                    return true;
                }
                if (ps.photoCooldown > 0 || ps.phone <= 0.0f) return false;
                ps.photoCooldown = 30;
                ps.phone = Math.max(0.0f, ps.phone - 0.5f);
                privateSound(p, ModSounds.PHONE_SHUTTER.get(), p.getEyePosition(), 0.8f, 1.0f);
                event(p, EV_FLASH, 0, "");
                Director.onPhoto(level, p);
                return true;
            }
            case BATTERY -> {
                if (ps.flash >= 98.0f) {
                    thought(p, "thought.sounan.battery_full");
                    return false;
                }
                ItemStack st = p.getItemInHand(hand);
                st.shrink(1);
                ps.flash = 100.0f;
                ps.lowWarned = 0;
                worldSound(level, p.position(), ModSounds.ITEM_BATTERY.get(), 0.8f, 1.0f);
                InteractionHand low = handHolding(p, GameItem.Kind.FLASHLIGHT_LOW);
                if (low != null) swap(p, low, ModItems.FLASHLIGHT_ON.get());
                thought(p, "thought.sounan.battery");
                return true;
            }
            case POWER_BANK -> {
                p.getItemInHand(hand).shrink(1);
                ps.phone = Math.min(100.0f, ps.phone + 60.0f);
                privateSound(p, ModSounds.PHONE_UNLOCK.get(), p.getEyePosition(), 0.7f, 1.0f);
                thought(p, "thought.sounan.charged");
                return true;
            }
            case BEAR_BELL -> {
                if (ps.bellCooldown > 0) return false;
                ps.bellCooldown = 60;
                worldSound(level, p.position(), ModSounds.ITEM_BEAR_BELL.get(), 1.0f, 1.0f);
                NoiseSystem.emit(level, p.position(), 30.0f, NoiseSystem.Source.BELL);
                p.getItemInHand(hand).hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(hand));
                Director.onBell(level, p);
                return true;
            }
            case FLARE -> {
                swap(p, hand, ModItems.FLARE_LIT.get());
                worldSound(level, p.position(), ModSounds.ITEM_FLARE_IGNITE.get(), 1.0f, 1.0f);
                Director.onFlare(level, p);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public static void flareBurnedOut(ServerPlayer p) {
        thought(p, "thought.sounan.flare_out");
    }

    // ------------------------------------------------------------------------------------------------ the phone (C2S)
    public static void phoneAction(ServerPlayer p, int action, int arg) {
        PlayerState ps = state(p);
        GameData data = GameData.get(p.server);
        switch (action) {
            case 0 -> ps.phoneOpen = arg != 0;
            case 1 -> { // camera app
                if (ps.phone <= 0.0f) return;
                InteractionHand h = handHolding(p, GameItem.Kind.PHONE, GameItem.Kind.PHONE_LIGHT);
                if (h != null) swap(p, h, ModItems.PHONE_CAMERA.get());
            }
            case 2 -> { // torch toggle
                if (ps.phone <= 0.0f) return;
                InteractionHand h = handHolding(p, GameItem.Kind.PHONE, GameItem.Kind.PHONE_LIGHT);
                if (h == null) return;
                boolean on = GameItem.kindOf(p.getItemInHand(h)) == GameItem.Kind.PHONE_LIGHT;
                swap(p, h, on ? ModItems.PHONE.get() : ModItems.PHONE_LIGHT.get());
            }
            case 3 -> { // call 110 / 119
                if (nearPylon(p, PoiLayout.get()) && ps.phone > 0.0f) {
                    data.called = true;
                    data.setDirty();
                    event(p, EV_CALL, arg, p.getGameProfile().getName() + "|" + data.captures);
                    Director.onCall(p.serverLevel(), p);
                } else {
                    event(p, EV_CALL, -1, "");
                }
            }
            case 4 -> ps.shaders = arg != 0;
            case 5 -> endingDone(p);
            case 6 -> { // the intro screen finished early
                if (ps.introTicks >= 0 && ps.introTicks < 20 * 15) ps.introTicks = 20 * 15 - 1;
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ blocks
    public static void interact(ServerPlayer p, BlockPos pos, String action) {
        ServerLevel level = p.serverLevel();
        GameData data = GameData.get(p.server);
        PoiLayout L = PoiLayout.get();
        BlockState state = level.getBlockState(pos);
        switch (action) {
            case "bag" -> {
                if (!pos.equals(L.bag)) {
                    thought(p, "thought.sounan.old_bag");
                    return;
                }
                if (data.bagOpened || (state.hasProperty(LostBagBlock.OPEN) && state.getValue(LostBagBlock.OPEN))) {
                    thought(p, "thought.sounan.bag_empty");
                    return;
                }
                data.bagOpened = true;
                data.checkpoint = "bag";
                data.setDirty();
                level.setBlock(pos, state.setValue(LostBagBlock.OPEN, true), 3);
                worldSound(level, Vec3.atCenterOf(pos), ModSounds.ITEM_BAG.get(), 0.9f, 1.0f);
                give(p, new ItemStack(ModItems.BATTERY.get(), 2));
                give(p, new ItemStack(ModItems.POWER_BANK.get()));
                give(p, new ItemStack(ModItems.BEAR_BELL.get()));
                give(p, new ItemStack(ModItems.ROPE.get()));
                give(p, new ItemStack(ModItems.TRAIL_MAP.get()));
                give(p, new ItemStack(ModItems.FLARE.get()));
                give(p, new ItemStack(ModItems.OMAMORI.get()));
                event(p, EV_CHECKPOINT, 1, "bag");
                thought(p, "thought.sounan.bag");
                addFear(p, -15.0f);
                Director.onBagOpened(level, p);
            }
            case "memo" -> openNote(p, L.memoId(pos));
            case "logbook" -> openNote(p, Notes.LOGBOOK);
            case "notice" -> openNote(p, pos.distManhattan(L.memorial) <= 2 ? Notes.MEMORIAL : Notes.RULES);
            case "sign" -> event(p, EV_SUBTITLE, 0, signText(pos));
            case "bus_stop" -> event(p, EV_SUBTITLE, 0, "sign.sounan.bus_stop");
            case "public_phone" -> Director.answerPayphone(level, p);
            case "hokora" -> {
                if (data.offered) {
                    thought(p, "thought.sounan.hokora_done");
                } else if (consume(p, ModItems.OMIKI.get())) {
                    data.offered = true;
                    data.checkpoint = "shrine";
                    data.setDirty();
                    if (state.hasProperty(HokoraBlock.OFFERED)) level.setBlock(pos, state.setValue(HokoraBlock.OFFERED, true), 3);
                    worldSound(level, Vec3.atCenterOf(pos), ModSounds.ITEM_OFFERING.get(), 1.0f, 1.0f);
                    event(p, EV_FOG_LIFT, 0, "");
                    thought(p, "thought.sounan.fog_lift");
                } else {
                    thought(p, data.notes.contains(Notes.SHRINE) ? "thought.sounan.hokora_need" : "thought.sounan.hokora");
                }
            }
            case "rope_anchor" -> {
                if (data.roped) return;
                if (consume(p, ModItems.ROPE.get())) {
                    data.roped = true;
                    data.setDirty();
                    if (state.hasProperty(RopeAnchorBlock.ROPED)) level.setBlock(pos, state.setValue(RopeAnchorBlock.ROPED, true), 3);
                    hangRope(level, L);
                    worldSound(level, Vec3.atCenterOf(pos), ModSounds.ITEM_ROPE.get(), 1.0f, 1.0f);
                    thought(p, "thought.sounan.roped");
                } else {
                    thought(p, "thought.sounan.need_rope");
                }
            }
            case "key_hook" -> {
                if (state.hasProperty(KeyHookBlock.HAS_KEY) && state.getValue(KeyHookBlock.HAS_KEY)) {
                    level.setBlock(pos, state.setValue(KeyHookBlock.HAS_KEY, false), 3);
                    give(p, new ItemStack(ModItems.HUT_KEY.get()));
                    data.keyTaken = true;
                    data.setDirty();
                    worldSound(level, Vec3.atCenterOf(pos), ModSounds.ITEM_KEY.get(), 0.9f, 1.0f);
                    NoiseSystem.emit(level, Vec3.atCenterOf(pos), 7.0f, NoiseSystem.Source.OTHER);
                    thought(p, "thought.sounan.key");
                    Director.onKeyTaken(level, p);
                }
            }
            case "gate" -> {
                if (data.gateUnlocked) return;
                if (consume(p, ModItems.HUT_KEY.get())) {
                    data.gateUnlocked = true;
                    data.setDirty();
                    LockedGateBlock.unlock(level, pos);
                    worldSound(level, Vec3.atCenterOf(pos), ModSounds.ITEM_UNLOCK.get(), 1.0f, 1.0f);
                    thought(p, "thought.sounan.unlocked");
                } else {
                    worldSound(level, Vec3.atCenterOf(pos), net.minecraft.sounds.SoundEvents.CHAIN_HIT, 1.0f, 0.8f);
                    thought(p, "thought.sounan.locked");
                }
            }
            case "omiki" -> {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                give(p, new ItemStack(ModItems.OMIKI.get()));
                data.omikiTaken = true;
                data.setDirty();
                worldSound(level, Vec3.atCenterOf(pos), ModSounds.ITEM_OFFERING.get(), 0.6f, 1.3f);
                NoiseSystem.emit(level, Vec3.atCenterOf(pos), 6.0f, NoiseSystem.Source.OTHER);
                thought(p, "thought.sounan.omiki");
            }
            case "car" -> {
                if (data.ending == 0) startEnding(p, 1);
            }
            default -> {
            }
        }
    }

    static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    public static void openNote(ServerPlayer p, int id) {
        GameData data = GameData.get(p.server);
        if (data.notes.add(id)) data.setDirty();
        worldSound(p.level(), p.position(), ModSounds.ITEM_PAPER.get(), 0.6f, 1.0f);
        ModNetwork.send(p, new S2C.Note(id, p.getGameProfile().getName() + "|" + data.captures + "|" + data.dayOffset()));
    }

    static String signText(BlockPos pos) {
        String best = "sign.sounan.generic";
        double bestD = 9.0;
        for (Pois.Point s : Pois.get().signs) {
            double d = s.pos().distSqr(pos);
            if (d < bestD) {
                bestD = d;
                best = "sign.sounan." + s.json().get("text").getAsString().replace("sign_", "");
            }
        }
        if (pos.getX() == 130 && pos.getZ() == 233) best = "sign.sounan.trailhead";
        return best;
    }

    /** The climbing rope down the cliff face below the stake. */
    public static void hangRope(ServerLevel level, PoiLayout L) {
        Direction f = L.ropeFacing;
        for (int y = L.ropeTopY; y >= L.ropeBottomY; y--) {
            BlockPos rp = new BlockPos(L.ropeX, y, L.ropeZ);
            BlockPos behind = rp.relative(f.getOpposite());
            if (!level.getBlockState(behind).isSolid()) level.setBlock(behind, ModBlocks.MOUNTAIN_ROCK.get().defaultBlockState(), 3);
            BlockState cur = level.getBlockState(rp);
            if (cur.isAir() || cur.canBeReplaced()) {
                level.setBlock(rp, ModBlocks.CLIMBING_ROPE.get().defaultBlockState().setValue(LadderBlock.FACING, f), 3);
            }
        }
    }

    /** Lights the tunnel lamps one after another (the climax) or puts them out. */
    public static void setTunnelLamp(ServerLevel level, BlockPos pos, boolean lit) {
        BlockState s = level.getBlockState(pos);
        if (s.hasProperty(TunnelLampBlock.LIT) && s.getValue(TunnelLampBlock.LIT) != lit) level.setBlock(pos, s.setValue(TunnelLampBlock.LIT, lit), 3);
    }

    // ------------------------------------------------------------------------------------------------ sync
    private static void syncTimer(ServerPlayer p, PlayerState ps, GameData data) {
        if (++ps.syncTimer >= 4) {
            ps.syncTimer = 0;
            syncState(p, ps, data);
        }
    }

    public static void syncState(ServerPlayer p, PlayerState ps, GameData data) {
        int flags = 0;
        if (inGame(p)) flags |= F_INGAME;
        if (nearPylon(p, PoiLayout.get())) flags |= F_SIGNAL;
        if (data.signal) flags |= F_MAP;
        if (ps.phone <= 0.0f) flags |= F_PHONE_DEAD;
        if (ps.captured) flags |= F_CAPTURED;
        if (ps.exhausted > 0) flags |= F_EXHAUSTED;
        if (data.offered) flags |= F_OFFERED;
        if (data.bagOpened) flags |= F_BAG;
        if (data.called) flags |= F_CALLED;
        if (data.climax) flags |= F_CLIMAX;
        if (ps.endingTicks >= 0) flags |= F_ENDING;
        if (data.keyTaken) flags |= F_KEY;
        if (flareLit(p)) flags |= F_FLARE;
        int notes = 0;
        for (int n : data.notes) notes |= 1 << Math.min(30, n);
        ModNetwork.send(p, new S2C.State(ps.fear, ps.danger, ps.stamina, ps.flash, ps.phone, flags, data.captures,
                Math.max(0, ps.area), phoneMinutes(data), data.dayOffset(), notes));
    }

    public static void onServerStopping(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) savePlayer(p);
        ServerLevel level = mountain(server);
        if (level != null) FlashlightLight.removeAll(level);
        STATES.clear();
        THREAT.clear();
        NoiseSystem.clear();
    }
}
