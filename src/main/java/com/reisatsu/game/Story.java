package com.reisatsu.game;

import com.reisatsu.Reisatsu;
import com.reisatsu.entity.Child;
import com.reisatsu.entity.Manager;
import com.reisatsu.entity.Medium;
import com.reisatsu.entity.MediumCar;
import com.reisatsu.entity.Spirit;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.entity.Waitress;
import com.reisatsu.item.GameItem;
import com.reisatsu.network.S2C;
import com.reisatsu.registry.ModItems;
import com.reisatsu.registry.ModParticles;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The night's story beats (docs/DESIGN.md §1 Flow): the intro by the medium's car, handing her the photos, closing
 * time, the exorcism at table 7, and the two endings.
 */
public final class Story {
    public static final int INTRO_TEXT = 220;
    public static final int INTRO_FADE = 40;
    /** The medium's lines in the parking lot and how long each stays (ticks). */
    private static final int[] INTRO_LINES = {66, 70, 74, 74, 80, 76, 82, 74, 82, 70};
    public static final int INTRO_END;
    private static final String TAG_PROP = "reisatsu_prop";
    private static final Map<UUID, Long> TALK_BUSY = new HashMap<>();

    // exorcism
    private static int exoTicks = -1;
    private static int exoStage;
    private static int exoStageAt;
    private static final List<Entity> TABLE_PHOTOS = new ArrayList<>();
    private static final List<BlockPos> CANDLES = new ArrayList<>();
    private static boolean doorOuter, doorInner;
    // closing time
    private static int closingTicks = -1;
    private static int pendingClosing = -1;
    private static int returnIn = -1;
    private static int exorcismIn = -1;

    static {
        int t = INTRO_TEXT + INTRO_FADE;
        for (int d : INTRO_LINES) t += d;
        INTRO_END = t + 10;
    }

    private Story() {}

    public static void reset() {
        exoTicks = -1;
        exoStage = 0;
        TABLE_PHOTOS.clear();
        CANDLES.clear();
        closingTicks = -1;
        pendingClosing = -1;
        returnIn = -1;
        exorcismIn = -1;
        TALK_BUSY.clear();
    }

    // ================================================================================================ dialogue
    /** Sends a spoken script to one player (subtitles; the medium's portrait when she speaks). */
    public static int say(ServerPlayer p, String speaker, int ticksPerLine, String... keys) {
        List<Integer> t = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) t.add(ticksPerLine);
        GameManager.send(p, new S2C.Dialogue(speaker, Arrays.asList(keys), t));
        return ticksPerLine * keys.length;
    }

    public static int say(ServerPlayer p, String speaker, List<String> keys, List<Integer> ticks) {
        GameManager.send(p, new S2C.Dialogue(speaker, keys, ticks));
        int sum = 0;
        for (int t : ticks) sum += t;
        return sum;
    }

    public static void skipDialogue(ServerPlayer p) {
        TALK_BUSY.remove(p.getUUID());
    }

    // ================================================================================================ the world at the start
    /** A new night: props of an earlier one removed, the film packs and the office key back in their places. */
    public static void resetWorld(ServerLevel level) {
        reset();
        Hiding.clearSeats(level);
        for (Entity e : level.getEntities((Entity) null, Spirits.AREA, en -> en.getTags().contains(TAG_PROP))) e.discard();
        // film pack 1 is in the register drawer and film pack 2 in a staff locker (GameFixtureListener gives them);
        // film pack 3 lies on the walk-in fridge's floor, the office key on the staff room table
        if (Layout.has("film_3")) {
            BlockPos at = Layout.marker("film_3").pos();
            pickup(level, new Vec3(at.getX() + 0.5, surfaceY(level, at), at.getZ() + 0.5), new ItemStack(ModItems.FILM_PACK.get()), "reisatsu_film_3");
        }
        if (Layout.has("key_office")) {
            BlockPos at = Layout.marker("key_office").pos();
            pickup(level, new Vec3(at.getX() + 0.5, surfaceY(level, at), at.getZ() + 0.5), new ItemStack(ModItems.OFFICE_KEY.get()), "reisatsu_office_key");
        }
    }

    /** The height something put down at this block rests at: on the block's own shape, or on the one below. */
    static double surfaceY(ServerLevel level, BlockPos pos) {
        net.minecraft.world.phys.shapes.VoxelShape own = level.getBlockState(pos).getCollisionShape(level, pos);
        if (!own.isEmpty() && own.max(net.minecraft.core.Direction.Axis.Y) < 1.0) return pos.getY() + own.max(net.minecraft.core.Direction.Axis.Y);
        if (!own.isEmpty()) return pos.getY() + 1.0;
        BlockPos below = pos.below();
        net.minecraft.world.phys.shapes.VoxelShape under = level.getBlockState(below).getCollisionShape(level, below);
        if (!under.isEmpty() && under.max(net.minecraft.core.Direction.Axis.Y) < 1.0) return below.getY() + under.max(net.minecraft.core.Direction.Axis.Y);
        return pos.getY();
    }

    private static void pickup(ServerLevel level, Vec3 at, ItemStack stack, String tag) {
        ItemEntity ie = new ItemEntity(level, at.x, at.y, at.z, stack, 0.0, 0.0, 0.0);
        ie.setNoGravity(true);
        ie.setUnlimitedLifetime();
        ie.addTag(tag);
        ie.addTag(TAG_PROP);
        level.addFreshEntity(ie);
    }

    // ================================================================================================ intro
    public static void startIntro(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        ps.introTicks = 0;
        ps.lockPos = p.position();
        MediumCar car = Spirits.car(level);
        if (car != null) car.setDoorOpen(true);
        GameManager.event(p, GameManager.EV_INTRO, data.introSeen ? 1 : 0, p.getGameProfile().getName());
    }

    static void tickIntro(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        int t = ++ps.introTicks;
        int dialogueAt = INTRO_TEXT + INTRO_FADE;
        if (t == dialogueAt) {
            List<String> keys = new ArrayList<>();
            List<Integer> ticks = new ArrayList<>();
            for (int i = 0; i < INTRO_LINES.length; i++) {
                keys.add("dialogue.reisatsu.intro." + (i + 1));
                ticks.add(INTRO_LINES[i]);
            }
            int total = say(p, "medium", keys, ticks);
            Medium m = Spirits.medium(level);
            if (m != null) m.talk(total);
        }
        // she hands things over as she speaks of them
        int at = dialogueAt;
        for (int i = 0; i < INTRO_LINES.length; i++) {
            if (t == at + 10) {
                switch (i) {
                    case 3 -> giveStart(p, 0);
                    case 7 -> giveStart(p, 2);
                    case 8 -> {
                        giveStart(p, 1);
                        giveStart(p, 3);
                    }
                    default -> {
                    }
                }
            }
            at += INTRO_LINES[i];
        }
        if (t >= INTRO_END) finishIntro(p, data);
    }

    private static void giveStart(ServerPlayer p, int what) {
        switch (what) {
            case 0 -> {
                if (GameManager.camera(p).isEmpty()) {
                    ItemStack cam = new ItemStack(ModItems.REISHA_CAMERA.get());
                    GameItem.setFilm(cam, GameItem.START_FILM);
                    put(p, 0, cam);
                }
            }
            case 1 -> {
                if (!GameManager.has(p, ModItems.FLASHLIGHT.get()) && !GameManager.has(p, ModItems.FLASHLIGHT_ON.get())) {
                    put(p, 1, new ItemStack(ModItems.FLASHLIGHT.get()));
                }
            }
            case 2 -> {
                if (!GameManager.has(p, ModItems.OFUDA.get())) put(p, 2, new ItemStack(ModItems.OFUDA.get(), 3));
            }
            case 3 -> {
                if (!GameManager.has(p, ModItems.SHOP_KEY.get())) put(p, 3, new ItemStack(ModItems.SHOP_KEY.get()));
            }
            default -> {
            }
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ITEM_KEY.get(), SoundSource.PLAYERS, what == 3 ? 0.6f : 0.0f, 1.0f);
    }

    private static void put(ServerPlayer p, int slot, ItemStack stack) {
        if (p.getInventory().getItem(slot).isEmpty()) p.getInventory().setItem(slot, stack);
        else GameManager.give(p, stack);
    }

    /** The end of the intro (or skipping it, or resuming a world saved during it). */
    public static void finishIntro(ServerPlayer p, GameData data) {
        PlayerState ps = GameManager.state(p);
        for (int i = 0; i < 4; i++) giveStart(p, i);
        ps.introTicks = -1;
        ps.lockPos = null;
        ps.hintIn = 50;
        boolean first = data.phase == GameData.PHASE_INTRO || data.phase == GameData.PHASE_IDLE;
        if (first) data.phase = GameData.PHASE_INVESTIGATE;
        data.introSeen = true;
        data.notes.add("report");
        data.notes.add("medium");
        data.setDirty();
        if (p.level() instanceof ServerLevel level) {
            MediumCar car = Spirits.car(level);
            if (car != null) car.setDoorOpen(false);
        }
        GameManager.event(p, GameManager.EV_OBJECTIVE, 0, "");
        Camera.syncAlbum(p);
        GameManager.syncState(p, ps, data);
    }

    public static void skipIntro(ServerPlayer p, PlayerState ps, GameData data) {
        if (ps.introTicks < 0) return;
        if (!data.introSeen && !p.isCreative() && ps.introTicks < INTRO_TEXT) return;
        GameManager.event(p, GameManager.EV_INTRO, -1, "");
        finishIntro(p, data);
    }

    // ================================================================================================ per tick
    static void tickPlayer(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        if (ps.introTicks >= 0) tickIntro(level, p, ps, data);
    }

    public static void tick(ServerLevel level, GameData data) {
        if (pendingClosing > 0 && --pendingClosing == 0) startClosing(level, data);
        if (closingTicks >= 0) tickClosing(level, data);
        if (returnIn > 0 && --returnIn == 0) toReturn(level, data);
        if (exorcismIn > 0 && --exorcismIn == 0) startExorcism(level, data);
        if (exoTicks >= 0) tickExorcism(level, data);
        if (data.phase == GameData.PHASE_EXORCISM && exoTicks < 0 && exorcismIn < 0) startExorcism(level, data); // resumed
        for (ServerPlayer p : GameManager.players(level)) {
            PlayerState ps = GameManager.state(p);
            if (ps.endingTicks >= 0 && data.ending == GameData.ENDING_BAD) tickBadEnding(level, p, ps, data);
        }
    }

    public static void onEnterRoom(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data, String from, String to) {
        if (data.phase != GameData.PHASE_INVESTIGATE && data.phase != GameData.PHASE_CLOSING) return;
        boolean customers = to.equals("dining") || to.equals("waiting") || to.equals("vestibule");
        if (customers && !data.isCaptured(SpiritKind.CHILD) && data.once("child_moui")) {
            // 「もういいかい」 from nowhere: the child's game begins
            GameManager.privateSound(p, ModSounds.CHILD_MOUI.get(), p.getEyePosition().add(level.random.nextGaussian() * 3, 1.5, level.random.nextGaussian() * 3), 0.8f, 1.0f);
            say(p, "child", 60, "dialogue.reisatsu.child.moui");
            if (Spirits.get(level, SpiritKind.CHILD) instanceof Child c) c.startGame(p);
            GameManager.addFear(p, 0.12f);
        }
        if (to.equals("fridge") && data.once("thought_fridge")) GameManager.event(p, GameManager.EV_THOUGHT, 0, "thought.reisatsu.fridge");
        if (to.equals("womens") && data.once("thought_womens")) GameManager.event(p, GameManager.EV_THOUGHT, 0, "thought.reisatsu.womens");
        if (to.equals("kitchen") && data.once("thought_kitchen")) GameManager.event(p, GameManager.EV_THOUGHT, 0, "thought.reisatsu.kitchen");
        if (to.equals("office") && data.once("thought_office")) GameManager.event(p, GameManager.EV_THOUGHT, 0, "thought.reisatsu.office");
    }

    // ================================================================================================ the medium
    public static void talk(ServerPlayer p, Medium m) {
        GameData data = GameData.get(p.server);
        PlayerState ps = GameManager.state(p);
        if (ps.introTicks >= 0 || ps.locked || ps.caught() || data.phase == GameData.PHASE_EXORCISM || data.phase == GameData.PHASE_ENDING) return;
        long now = p.serverLevel().getGameTime();
        Long busy = TALK_BUSY.get(p.getUUID());
        if (busy != null && busy > now) return;
        List<SpiritKind> handed = new ArrayList<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!GameItem.is(s, GameItem.Kind.PHOTO)) continue;
            SpiritKind k = GameItem.photoSpirit(s);
            if (k == null || !data.isCaptured(k)) continue;
            if (!data.isSubmitted(k) && !handed.contains(k)) handed.add(k);
            p.getInventory().setItem(i, ItemStack.EMPTY);
        }
        List<String> keys = new ArrayList<>();
        List<Integer> ticks = new ArrayList<>();
        if (!handed.isEmpty()) {
            for (SpiritKind k : handed) {
                data.submitted |= k.bit();
                for (int l = 1; l <= 4; l++) {
                    keys.add("dialogue.reisatsu.submit." + k.id + "." + l);
                    ticks.add(l == 1 ? 70 : 92);
                }
                GameManager.event(p, GameManager.EV_SUBMIT, k.ordinal(), "");
            }
            data.setDirty();
            if (data.allSubmitted()) {
                keys.add("dialogue.reisatsu.ready.1");
                ticks.add(70);
                keys.add("dialogue.reisatsu.ready.2");
                ticks.add(70);
            }
        } else if (data.allCaptured() && data.allSubmitted()) {
            keys.add("dialogue.reisatsu.ready.1");
            ticks.add(70);
            keys.add("dialogue.reisatsu.ready.2");
            ticks.add(70);
            exorcismIn = 150;
        } else {
            hint(data, keys, ticks, p.serverLevel());
        }
        int total = say(p, "medium", keys, ticks);
        m.talk(total);
        TALK_BUSY.put(p.getUUID(), now + Math.min(total, 200));
        if (!handed.isEmpty() && data.allSubmitted() && data.allCaptured()) exorcismIn = total + 10;
        Camera.syncAlbum(p);
        GameManager.syncState(p, ps, data);
    }

    private static void hint(GameData data, List<String> keys, List<Integer> ticks, ServerLevel level) {
        switch (data.phase) {
            case GameData.PHASE_CLOSING -> {
                keys.add("dialogue.reisatsu.hint.closing");
                ticks.add(90);
                keys.add("dialogue.reisatsu.hint.manager");
                ticks.add(100);
            }
            case GameData.PHASE_RETURN -> {
                keys.add("dialogue.reisatsu.hint.return");
                ticks.add(90);
            }
            default -> {
                int left = 5 - data.capturedCount(SpiritKind.FIVE);
                keys.add(left == 5 ? "dialogue.reisatsu.hint.start" : "dialogue.reisatsu.hint.left." + Math.max(1, left));
                ticks.add(80);
                List<SpiritKind> open = new ArrayList<>();
                for (SpiritKind k : SpiritKind.ALL) if (!data.isCaptured(k)) open.add(k);
                if (!open.isEmpty()) {
                    SpiritKind k = open.get(level.random.nextInt(open.size()));
                    keys.add("dialogue.reisatsu.hint." + k.id);
                    ticks.add(110);
                }
            }
        }
    }

    // ================================================================================================ captures, closing time
    static void onCaptured(ServerLevel level, ServerPlayer p, GameData data, SpiritKind kind) {
        GameManager.event(p, GameManager.EV_OBJECTIVE, 0, "");
        if (data.phase == GameData.PHASE_INVESTIGATE && data.capturedCount(SpiritKind.FIVE) == 5 && pendingClosing < 0 && closingTicks < 0) {
            pendingClosing = 20 * 5; // let the photo develop first
        }
        if (kind == SpiritKind.MANAGER && data.phase == GameData.PHASE_CLOSING) returnIn = 20 * 6;
    }

    public static void startClosing(ServerLevel level, GameData data) {
        if (data.happened("closing") && data.phase != GameData.PHASE_INVESTIGATE) return;
        data.once("closing");
        data.once("store_music");
        data.closingAt = data.playTicks;
        data.phase = GameData.PHASE_CLOSING;
        data.setDirty();
        closingTicks = 0;
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.inGame(p)) continue;
            GameManager.event(p, GameManager.EV_CLOSING, 0, "");
        }
        Reisatsu.LOGGER.info("[reisatsu] closing time");
    }

    private static void tickClosing(ServerLevel level, GameData data) {
        int t = ++closingTicks;
        if (t == 1) {
            for (ServerPlayer p : GameManager.players(level)) if (GameManager.inGame(p)) GameManager.privateSound(p, ModSounds.FIXTURE_PA_CHIME.get(), p.getEyePosition().add(0, 2, 0), 1.0f, 1.0f);
        }
        if (t == 30) {
            Fixtures.allLightsOn(level);
            Fixtures.setCallDisplay(level, 0, true);
        }
        if (t == 50) {
            for (ServerPlayer p : GameManager.players(level)) {
                if (!GameManager.inGame(p)) continue;
                GameManager.privateSound(p, ModSounds.MANAGER_CLOSED.get(), p.getEyePosition().add(0, 2, 0), 1.0f, 0.96f);
                say(p, "pa", 90, "dialogue.reisatsu.pa.closed");
            }
        }
        if (t == 120) Fixtures.setDevice(level, "store_music", true);
        if (t == 140) {
            if (Spirits.get(level, SpiritKind.MANAGER) instanceof Manager m) m.closingTime();
            else returnIn = 60;
            for (ServerPlayer p : GameManager.players(level)) if (GameManager.inGame(p)) GameManager.event(p, GameManager.EV_OBJECTIVE, 0, "");
            closingTicks = -1;
        }
    }

    /** Every spirit in a photo: back to the medium. */
    private static void toReturn(ServerLevel level, GameData data) {
        if (!data.allCaptured()) return;
        data.phase = GameData.PHASE_RETURN;
        data.setDirty();
        Fixtures.setDevice(level, "store_music", false);
        data.done.remove("store_music");
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.inGame(p)) continue;
            GameManager.event(p, GameManager.EV_OBJECTIVE, 0, "");
            GameManager.event(p, GameManager.EV_THOUGHT, 0, "thought.reisatsu.quiet");
        }
    }

    // ================================================================================================ the exorcism
    public static void startExorcism(ServerLevel level, GameData data) {
        data.phase = GameData.PHASE_EXORCISM;
        data.setDirty();
        exoTicks = 0;
        exoStage = 0;
        exoStageAt = 0;
        doorOuter = doorInner = false;
        Medium m = Spirits.medium(level);
        Layout.Marker spot = Layout.marker("exorcism_player");
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.inGame(p)) continue;
            PlayerState ps = GameManager.state(p);
            Hiding.exit(p, false);
            GameManager.lowerCamera(p);
            GameManager.event(p, GameManager.EV_FADE, 40, "");
            GameManager.teleport(p, level, spot.center(), spot.yaw(), 8.0f);
            ps.locked = true;
            ps.lockPos = spot.center();
            GameManager.event(p, GameManager.EV_EXORCISM, 0, "");
        }
        if (m != null) {
            m.scripted = true;
            m.walkTo(Layout.marker("exorcism_medium").center(), 0.075);
        }
        Fixtures.setDevice(level, "store_music", false);
        Reisatsu.LOGGER.info("[reisatsu] the exorcism begins");
    }

    private static void tickExorcism(ServerLevel level, GameData data) {
        int t = ++exoTicks;
        Medium m = Spirits.medium(level);
        Layout.Marker ms = Layout.marker("exorcism_medium");
        switch (exoStage) {
            case 0 -> { // she walks in; the doors open for her
                if (m != null) {
                    if (!doorOuter && m.position().distanceToSqr(Layout.marker("door_outer").center()) < 9) {
                        doorOuter = true;
                        Fixtures.openAutoDoor(level, "outer", 70);
                    }
                    if (!doorInner && m.position().distanceToSqr(Layout.marker("door_inner").center()) < 9) {
                        doorInner = true;
                        Fixtures.openAutoDoor(level, "inner", 70);
                    }
                }
                if (m == null || m.arrived() || t > 20 * 30) {
                    if (m != null) {
                        Vec3 c = ms.center();
                        m.moveTo(c.x, c.y, c.z, ms.yaw(), 0.0f);
                        m.faceYaw(ms.yaw());
                        m.setAnimPose(Medium.KNEEL);
                    }
                    layTable(level, data);
                    for (ServerPlayer p : GameManager.players(level)) {
                        if (!GameManager.inGame(p)) continue;
                        GameManager.event(p, GameManager.EV_EXORCISM, 1, "");
                        say(p, "medium", 80, "dialogue.reisatsu.exorcism.begin");
                    }
                    level.playSound(null, ms.center().x, ms.center().y + 1, ms.center().z, ModSounds.MEDIUM_CLAP.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
                    level.playSound(null, ms.center().x, ms.center().y + 1, ms.center().z, ModSounds.MEDIUM_BELL.get(), SoundSource.NEUTRAL, 1.2f, 1.0f);
                    exoStage = 1;
                    exoStageAt = t;
                }
            }
            case 1 -> { // one by one they stand at the table; the photo burns; they fade into light
                int local = t - exoStageAt;
                int i = local / 150;
                int k = local % 150;
                if (m != null && local == 40) m.setAnimPose(Medium.PRAY);
                if (i < SpiritKind.ALL.length) {
                    SpiritKind kind = SpiritKind.ALL[i];
                    List<Layout.Marker> spots = Layout.markers("exorcism_spirits");
                    Layout.Marker sm = spots.get(Math.min(i, spots.size() - 1));
                    if (k == 20) {
                        Spirit s = Spirits.spawnAt(level, kind, sm.center(), sm.yaw());
                        if (s != null) {
                            s.faceTowards(Layout.marker("table_7").center().add(0, 1, 0));
                            s.exorcism(i);
                        }
                        level.playSound(null, sm.center().x, sm.center().y + 1, sm.center().z, ModSounds.MEDIUM_BELL.get(), SoundSource.NEUTRAL, 0.9f, 1.0f);
                    }
                    if (k == 60) {
                        for (ServerPlayer p : GameManager.players(level)) {
                            if (GameManager.inGame(p)) say(p, "medium", 80, "dialogue.reisatsu.release." + kind.id);
                        }
                    }
                    if (k == 110) {
                        burnPhoto(level, i);
                        for (Spirit s : Spirits.all(level)) if (s.mode() == Spirit.Mode.EXORCISM && s.exorcismSlot() == i) s.release();
                        for (ServerPlayer p : GameManager.players(level)) if (GameManager.inGame(p)) GameManager.event(p, GameManager.EV_EXORCISM, 2, kind.id);
                    }
                } else {
                    exoStage = 2;
                    exoStageAt = t;
                }
            }
            case 2 -> { // the lights come on; the door opens one last time; the waitress thanks you; dawn
                int local = t - exoStageAt;
                if (local == 1) Fixtures.flickerRoom(level, "dining", 60);
                if (local == 60) Fixtures.allLightsOn(level);
                if (local == 100) Fixtures.openAutoDoor(level, "inner", 50);
                if (local == 130) Fixtures.openAutoDoor(level, "outer", 50);
                if (local == 175) {
                    for (ServerPlayer p : GameManager.players(level)) {
                        if (!GameManager.inGame(p)) continue;
                        GameManager.privateSound(p, ModSounds.WAITRESS_GREET.get(), Layout.marker("door_inner").center().add(0, 1.5, 0), 0.8f, 1.0f);
                        say(p, "waitress", 90, "dialogue.reisatsu.waitress.thanks");
                    }
                }
                if (local == 200 && m != null) m.setAnimPose(Medium.BOW);
                if (local == 280) {
                    clearTable(level);
                    for (ServerPlayer p : GameManager.players(level)) if (GameManager.inGame(p)) GameManager.event(p, GameManager.EV_DAWN, 0, "");
                }
                if (local == 420) {
                    exoTicks = -1;
                    data.phase = GameData.PHASE_ENDING;
                    data.ending = GameData.ENDING_GOOD;
                    data.setDirty();
                    for (ServerPlayer p : GameManager.players(level)) {
                        if (!GameManager.inGame(p)) continue;
                        PlayerState ps = GameManager.state(p);
                        ps.endingTicks = 0;
                        GameManager.event(p, GameManager.EV_ENDING, GameData.ENDING_GOOD, String.valueOf(data.ofudaUsed));
                    }
                    Reisatsu.LOGGER.info("[reisatsu] dawn: all six sent on (ofuda used {})", data.ofudaUsed);
                }
            }
            default -> {
            }
        }
    }

    /** Candles and the six photos on table 7. */
    private static void layTable(ServerLevel level, GameData data) {
        Layout.Marker tm = Layout.marker("table_7");
        // the booth table is two blocks long (aisle end at the marker, window end beyond); its top is at the block above
        net.minecraft.core.Direction toWindow = tm.facing().getOpposite();
        BlockPos windowTop = tm.pos().relative(toWindow).above();
        BlockState was = level.getBlockState(windowTop);
        if (was.isAir() || net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(was.getBlock()).getPath().equals("menu_stand")) {
            BlockState candle = Blocks.WHITE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4).setValue(CandleBlock.LIT, true);
            level.setBlock(windowTop, candle, 2 | 16);
            CANDLES.add(windowTop);
        }
        Vec3 centre = Vec3.atBottomCenterOf(tm.pos()).add(Vec3.atLowerCornerOf(toWindow.getNormal()).scale(0.45)).add(0, 1.02, 0);
        boolean alongX = toWindow.getAxis() == net.minecraft.core.Direction.Axis.X;
        for (int i = 0; i < SpiritKind.ALL.length; i++) {
            double across = (i % 3 - 1) * 0.30, along = (i / 3 - 0.5) * 0.36;
            double ox = alongX ? along : across, oz = alongX ? across : along;
            Entity d = photoDisplay(level, centre.add(ox, 0, oz), SpiritKind.ALL[i]);
            TABLE_PHOTOS.add(d);
        }
    }

    private static Entity photoDisplay(ServerLevel level, Vec3 at, SpiritKind kind) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:item_display");
        CompoundTag item = new CompoundTag();
        item.putString("id", "reisatsu:evidence_photo");
        item.putByte("Count", (byte) 1);
        CompoundTag itag = new CompoundTag();
        itag.putString("spirit", kind.id);
        item.put("tag", itag);
        tag.put("item", item);
        tag.putString("item_display", "fixed");
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG_PROP));
        tag.put("Tags", tags);
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", floats(0, 0, 0, 1));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        tr.put("translation", floats(0, 0, 0));
        tr.put("scale", floats(0.38f, 0.38f, 0.38f));
        tag.put("transformation", tr);
        Entity e = EntityType.loadEntityRecursive(tag, level, ent -> {
            ent.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 30 - 15, -90.0f);
            return ent;
        });
        if (e != null) level.addFreshEntity(e);
        return e;
    }

    private static ListTag floats(float... v) {
        ListTag l = new ListTag();
        for (float f : v) l.add(net.minecraft.nbt.FloatTag.valueOf(f));
        return l;
    }

    private static void burnPhoto(ServerLevel level, int i) {
        if (i < TABLE_PHOTOS.size() && TABLE_PHOTOS.get(i) != null) {
            Entity e = TABLE_PHOTOS.get(i);
            level.sendParticles(ModParticles.PAPER_ASH.get(), e.getX(), e.getY() + 0.1, e.getZ(), 18, 0.08, 0.1, 0.08, 0.02);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, e.getX(), e.getY() + 0.05, e.getZ(), 10, 0.08, 0.03, 0.08, 0.01);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.ITEM_OFUDA_BURN.get(), SoundSource.NEUTRAL, 0.9f, 1.0f);
            e.discard();
        }
    }

    private static void clearTable(ServerLevel level) {
        for (Entity e : TABLE_PHOTOS) if (e != null) e.discard();
        TABLE_PHOTOS.clear();
        for (BlockPos c : CANDLES) if (level.getBlockState(c).getBlock() instanceof CandleBlock) level.setBlock(c, Blocks.AIR.defaultBlockState(), 2 | 16);
        CANDLES.clear();
    }

    // ================================================================================================ the bad ending: 相席
    public static void startBadEnding(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        data.phase = GameData.PHASE_ENDING;
        data.ending = GameData.ENDING_BAD;
        data.setDirty();
        ps.endingTicks = 0;
        ps.locked = true;
        Reisatsu.LOGGER.info("[reisatsu] {} stays at table 7", p.getGameProfile().getName());
    }

    private static void tickBadEnding(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        int t = ps.endingTicks;
        Layout.Marker seat = Layout.marker("seat_7");
        if (t == 2) {
            p.removeEffect(MobEffects.DARKNESS);
            GameManager.event(p, GameManager.EV_FADE, 80, "");
            // seated on the booth bench: eyes at a sitting height
            Entity s = Hiding.seat(level, seat.center().add(0, -0.4, 0), seat.yaw());
            p.teleportTo(level, seat.center().x, seat.center().y, seat.center().z, seat.yaw(), 18.0f);
            if (s != null) p.startRiding(s, true);
            ps.lockPos = null;
            for (Spirit sp : Spirits.all(level)) if (sp.mode() != Spirit.Mode.BOUND) sp.discard();
        }
        if (t == 60) {
            // she serves from the aisle end of the table
            Layout.Marker tm = Layout.marker("table_7");
            Vec3 side = tm.center().add(Vec3.atLowerCornerOf(tm.facing().getNormal()).scale(0.95));
            Spirit w = Spirits.spawnAt(level, SpiritKind.WAITRESS, side, 0);
            if (w != null) {
                w.faceTowards(seat.center().add(0, 1.2, 0));
                w.debugShown = true;
                w.freeze(Waitress.SERVE);
            }
        }
        // the glass leaves her hand at SERVE tick 24
        if (t == 84) Fixtures.placeWaterGlass(level, 7);
        if (t == 85) level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.FIXTURE_GLASS_SET.get(), SoundSource.NEUTRAL, 0.9f, 1.0f);
        if (t == 110) {
            // 「いらっしゃいませ。……お一人様ですね」 (game.ending_bad carries the whole line)
            GameManager.privateSound(p, ModSounds.GAME_ENDING_BAD.get(), p.getEyePosition().add(0.6, 0.2, 0.0), 0.9f, 1.0f);
            say(p, "waitress", 110, "dialogue.reisatsu.waitress.single");
        }
        if (t == 240) Fixtures.ringCallButton(level, 7);
        if (t == 268) Fixtures.ringCallButton(level, 7);
        if (t == 300) GameManager.event(p, GameManager.EV_ENDING, GameData.ENDING_BAD, String.valueOf(data.ofudaUsed));
    }

    /** The ending screen and credits are over: the player goes home. */
    public static void endingDone(ServerPlayer p) {
        PlayerState ps = GameManager.state(p);
        GameData data = GameData.get(p.server);
        if (ps.endingTicks < 0 && data.phase != GameData.PHASE_ENDING) return;
        Entity v = p.getVehicle();
        p.stopRiding();
        if (v != null && v.getTags().contains(Hiding.SEAT_TAG)) v.discard();
        data.started = false;
        data.setDirty();
        p.getPersistentData().putBoolean("reisatsu_done", true);
        ps.endingTicks = -1;
        ps.locked = false;
        GameManager.leaveGame(p);
    }
}
