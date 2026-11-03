package com.hitori.game;

import com.hitori.Hitori;
import com.hitori.entity.OniDoll;
import com.hitori.item.GameItem;
import com.hitori.item.GameItem.Kind;
import com.hitori.registry.ModItems;
import com.hitori.registry.ModParticles;
import com.hitori.registry.ModSounds;
import com.hitori.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The procedure (docs/DESIGN.md §1): every step a real action in the apartment. Taking the plush, cutting it open at
 * the ちゃぶ台, rice, nail clippings, red thread; the bath, the salt water, the cameras, the TV; at 3:00 the name, the
 * three lines, the tub, the lights, the count with your eyes closed, the knife, the stab, 「次は〇〇が鬼だから」, the
 * mouthful of salt water and the closet; then finding it, spitting, 「私の勝ち」 ×3 and the fire.
 * <p>
 * Inputs: the world's interaction keys ({@link #interact}, through the fixture listener), item-on-thing clicks in the
 * apartment ({@link #rightClickBlock}, before the block itself is used), item uses ({@link #useItem}), the action key
 * R ({@link #actionKey}), the doll ({@link #interactDoll}), the naming field ({@link #named}). Long actions (cutting,
 * sewing, counting) hold you still for a few seconds with their sounds ({@link #tickPlayer}).
 */
public final class Ritual {
    public static final String TAG_ITEM = "hitori_item";
    /** Ticks between spoken lines (the voice must finish). */
    private static final int LINE_GAP = 46;

    private Ritual() {}

    // ================================================================================================ world reset
    /** A new night: items lying about from an earlier one are removed. */
    public static void resetWorld(ServerLevel level) {
        AABB area = Doll.AREA;
        for (Entity e : level.getEntities((Entity) null, area, en -> en.getTags().contains(TAG_ITEM) || en instanceof ItemEntity)) e.discard();
    }

    // ================================================================================================ helpers
    static GameData data(ServerPlayer p) {
        return GameData.get(p.server);
    }

    private static PlayerState ps(ServerPlayer p) {
        return GameManager.state(p);
    }

    /** Is the position at (or right next to) one of the markers of this name? */
    public static boolean near(BlockPos pos, String marker) {
        if (!Layout.has(marker)) return false;
        for (Layout.Marker m : Layout.markers(marker)) {
            BlockPos q = m.pos();
            if (Math.abs(q.getX() - pos.getX()) <= 1 && Math.abs(q.getZ() - pos.getZ()) <= 1 && Math.abs(q.getY() - pos.getY()) <= 1) return true;
        }
        return false;
    }

    /** Exactly on a marker (or on the block below it: things stand on furniture). */
    public static boolean on(BlockPos pos, String marker) {
        if (!Layout.has(marker)) return false;
        for (Layout.Marker m : Layout.markers(marker)) {
            if (m.pos().equals(pos) || m.pos().below().equals(pos)) return true;
        }
        return false;
    }

    private static double distTo(ServerPlayer p, String marker) {
        if (!Layout.has(marker)) return 99;
        double best = 99;
        for (Layout.Marker m : Layout.markers(marker)) best = Math.min(best, Math.sqrt(m.center().distanceToSqr(p.getX(), m.center().y, p.getZ())));
        return best;
    }

    private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
        GameManager.playerSound(p, s, vol, pitch);
    }

    private static void soundAt(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.BLOCKS, vol, pitch);
    }

    private static boolean giveOnce(ServerPlayer p, GameData d, String flag, Item item, int count, @Nullable String thought) {
        if (d.has(flag)) return false;
        d.flag(flag);
        GameManager.give(p, new ItemStack(item, count));
        sound(p, ModSounds.ITEM_PAPER.get(), 0.25f, 1.6f);
        if (thought != null) GameManager.thought(p, thought);
        return true;
    }

    /** The current step of phase 1 (they go in order), or null. */
    @Nullable
    public static Step current(GameData d) {
        if (d.phase == GameData.PHASE_RITUAL || d.phase == GameData.PHASE_HIDING) return Step.firstUndone(d, Step.RITUAL);
        if (d.phase == GameData.PHASE_FINISH) return Step.firstUndone(d, Step.FINISH);
        return null;
    }

    private static boolean prep(GameData d) {
        return d.phase == GameData.PHASE_PREP;
    }

    // ================================================================================================ the world's keys
    /** A player used a fixture of the apartment (docs §12 FixtureEvents keys). */
    public static void interact(ServerPlayer p, String key, BlockPos pos) {
        if (!GameManager.inGame(p)) return;
        GameData d = data(p);
        PlayerState ps = ps(p);
        if (ps.busy() || ps.caught() || ps.locked) return;
        ServerLevel level = p.serverLevel();
        switch (key) {
            case "plush_shelf" -> {
                if (giveOnce(p, d, "plush_taken", ModItems.PLUSH.get(), 1, "thought.hitori.plush")) {
                    Apt.setPresent(level, "plush", false);
                    complete(level, d, Step.PLUSH, p);
                }
            }
            case "sewing_box" -> {
                boolean a = giveOnce(p, d, "scissors_taken", ModItems.SCISSORS.get(), 1, null);
                boolean b = giveOnce(p, d, "thread_taken", ModItems.RED_THREAD.get(), 1, null);
                if (a || b) GameManager.thought(p, "thought.hitori.sewing_box");
            }
            case "nail_clipper" -> giveOnce(p, d, "clipper_taken", ModItems.NAIL_CLIPPER.get(), 1, "thought.hitori.clipper");
            case "rice" -> {
                ItemStack open = GameManager.find(p, ModItems.PLUSH_OPEN.get());
                if (!open.isEmpty()) startAction(p, "rice", 50);
                else giveOnce(p, d, "scoop_taken", ModItems.RICE_SCOOP.get(), 1, "thought.hitori.rice");
            }
            case "salt" -> {
                if (GameManager.has(p, ModItems.CUP_WATER.get())) startAction(p, "salt", 30);
                else giveOnce(p, d, "salt_taken", ModItems.SALT.get(), 1, "thought.hitori.salt");
            }
            case "cup", "kitchen_sink_cup" -> {
                if (!GameManager.has(p, ModItems.CUP_WATER.get()) && !GameManager.has(p, ModItems.SALT_WATER.get()) && d.flag("cup_taken")) {
                    GameManager.give(p, new ItemStack(ModItems.CUP_WATER.get()));
                    sound(p, ModSounds.ITEM_CUP.get(), 0.6f, 1.0f);
                    GameManager.thought(p, "thought.hitori.cup");
                }
            }
            case "knife_rack" -> {
                if (giveOnce(p, d, "knife_taken", ModItems.KNIFE.get(), 1, d.phase >= GameData.PHASE_RITUAL ? null : "thought.hitori.knife_early")) {
                    Apt.setPresent(level, "knife", false);
                }
            }
            case "camera_bag" -> {
                int left = 4 - d.cameras.size() - GameManager.count(p, ModItems.FIXED_CAMERA.get());
                if (!d.has("cameras_taken") && left > 0) {
                    d.flag("cameras_taken");
                    GameManager.give(p, new ItemStack(ModItems.FIXED_CAMERA.get(), left));
                    sound(p, ModSounds.CAMERA_PLACE.get(), 0.4f, 1.2f);
                    GameManager.thought(p, "thought.hitori.cameras");
                }
            }
            case "laptop" -> {
                if (giveOnce(p, d, "laptop_taken", ModItems.LAPTOP.get(), 1, "thought.hitori.laptop")) {
                    Apt.setPresent(level, "laptop", false);
                    Apt.setDevice(level, "laptop", false);
                }
            }
            case "lighter_drawer" -> giveOnce(p, d, "lighter_taken", ModItems.LIGHTER.get(), 1, "thought.hitori.lighter");
            case "printout" -> {
                giveOnce(p, d, "printout_taken", ModItems.PRINTOUT.get(), 1, null);
                GameManager.event(p, GameManager.EV_PRINTOUT, 0, "");
                readPrintout(p);
            }
            case "phone" -> giveOnce(p, d, "phone_taken", ModItems.PHONE.get(), 1, null);
            case "table" -> tableUse(p, d, ps);
            case "bathtub" -> tubUse(p, d, ps, level);
            case "kitchen_sink" -> sinkUse(p, d, ps, level);
            case "tv" -> {
                if (current(d) == Step.COUNT) GameManager.thought(p, "thought.hitori.count_hint");
            }
            case "peephole" -> GameManager.thought(p, d.ritualRunning() ? "thought.hitori.peephole_night" : "thought.hitori.peephole");
            case "intercom" -> GameManager.thought(p, "thought.hitori.intercom");
            default -> {
            }
        }
    }

    /**
     * A right click on a block in the apartment, before the block reacts: the item-on-thing actions of the ritual
     * (scissors at the table, the doll into the tub, rice into the plush, the lighter at the sink...). In the greybox
     * (no world blocks) it also stands in for the world's interaction keys. True = handled (the block is not used).
     */
    public static boolean rightClickBlock(ServerPlayer p, InteractionHand hand, BlockPos pos) {
        if (!GameManager.inGame(p) || hand != InteractionHand.MAIN_HAND) return false;
        GameData d = data(p);
        PlayerState ps = ps(p);
        if (ps.busy() || ps.caught() || ps.locked || ps.hiding()) return false;
        ServerLevel level = p.serverLevel();
        Kind held = GameItem.kindOf(p.getMainHandItem());
        if (near(pos, "table") && (held == Kind.SCISSORS || held == Kind.RITUAL_DOLL || held == Kind.PLUSH)) {
            tableUse(p, d, ps);
            return true;
        }
        if (on(pos, "bathtub") && (held == Kind.RITUAL_DOLL || held == Kind.LIGHTER)) {
            tubUse(p, d, ps, level);
            return true;
        }
        if (near(pos, "kitchen_sink") && held == Kind.LIGHTER) {
            sinkUse(p, d, ps, level);
            return true;
        }
        if (on(pos, "rice") && held == Kind.PLUSH_OPEN) {
            startAction(p, "rice", 50);
            return true;
        }
        if (on(pos, "salt") && held == Kind.CUP_WATER) {
            startAction(p, "salt", 30);
            return true;
        }
        if (!Apt.grey(level)) return false;
        // ---- the greybox: no world blocks tell us anything
        String key = greyKey(pos);
        if (key == null) return false;
        switch (key) {
            case "bath_tap" -> {
                boolean run = !Apt.bathTapRunning(level);
                Apt.setBathTap(level, run);
                soundAt(level, Vec3.atCenterOf(pos), ModSounds.FIXTURE_TAP_BATH.get(), 0.6f, 1.0f);
            }
            case "tv" -> {
                boolean on = !Apt.tvOn(level);
                Apt.setTv(level, on);
                if (on) soundAt(level, Vec3.atCenterOf(pos), ModSounds.FIXTURE_TV_ON.get(), 1.0f, 1.0f);
                GameFixtureListener.INSTANCE.switched(level, "tv", on);
            }
            case "breaker" -> {
                boolean on = !Apt.breakerOn(level);
                Apt.setBreaker(level, on);
                soundAt(level, Vec3.atCenterOf(pos), on ? ModSounds.FIXTURE_BREAKER_ON.get() : ModSounds.FIXTURE_BREAKER_OFF.get(), 1.0f, 1.0f);
                GameFixtureListener.INSTANCE.switched(level, "breaker", on);
            }
            case "closet_fusuma" -> Hiding.enter(p, Layout.marker("closet_hide").pos(), "oshiire");
            case "western_closet" -> Hiding.enter(p, Layout.marker("western_closet").pos(), "western");
            default -> {
                if (key.startsWith("switch_")) {
                    String room = key.substring(7);
                    boolean on = !Apt.lightsOn(level, room);
                    Apt.setLights(level, room, on);
                    soundAt(level, Vec3.atCenterOf(pos), ModSounds.FIXTURE_SWITCH.get(), 0.6f, 1.0f);
                    GameFixtureListener.INSTANCE.switched(level, room, on);
                } else {
                    interact(p, key, pos);
                }
            }
        }
        return true;
    }

    /** Greybox: which interaction a clicked block stands for. */
    @Nullable
    private static String greyKey(BlockPos pos) {
        String[][] map = {{"plush_shelf", "plush_shelf"}, {"scissors", "sewing_box"}, {"thread", "sewing_box"}, {"nail_clipper", "nail_clipper"},
                {"rice", "rice"}, {"salt", "salt"}, {"cup", "cup"}, {"knife", "knife_rack"}, {"camera_bag", "camera_bag"}, {"laptop", "laptop"},
                {"lighter", "lighter_drawer"}, {"printout", "printout"}, {"table", "table"}, {"bathtub", "bathtub"}, {"bath_tap", "bath_tap"},
                {"tv", "tv"}, {"breaker", "breaker"}, {"kitchen_sink", "kitchen_sink"}, {"closet_fusuma", "closet_fusuma"},
                {"western_closet", "western_closet"}};
        for (String[] m : map) if (on(pos, m[0])) return m[1];
        for (String r : Apt.LIT_ROOMS) if (on(pos, "switch_" + r)) return "switch_" + r;
        return null;
    }

    // ------------------------------------------------------------------------------------------------ the table, the tub, the sink
    private static void tableUse(ServerPlayer p, GameData d, PlayerState ps) {
        Kind held = GameItem.kindOf(p.getMainHandItem());
        if (held == Kind.SCISSORS || (held == Kind.PLUSH && GameManager.has(p, ModItems.SCISSORS.get()))) {
            if (!GameManager.has(p, ModItems.PLUSH.get())) {
                GameManager.thought(p, "thought.hitori.need_plush");
                return;
            }
            startAction(p, "cut", 60);
            return;
        }
        if (held == Kind.RITUAL_DOLL || GameManager.has(p, ModItems.RITUAL_DOLL.get())) {
            if (current(d) == Step.NAME) {
                GameManager.event(p, GameManager.EV_NAMING, 0, d.dollName);
                return;
            }
            if (prep(d)) GameManager.thought(p, "thought.hitori.wait_three");
            return;
        }
        if (prep(d) && !d.done(Step.CUT)) GameManager.thought(p, "thought.hitori.table");
    }

    private static void tubUse(ServerPlayer p, GameData d, PlayerState ps, ServerLevel level) {
        Kind held = GameItem.kindOf(p.getMainHandItem());
        if (held == Kind.RITUAL_DOLL && current(d) == Step.TUB) {
            if (Apt.bathWater(level) < 2) {
                GameManager.thought(p, "thought.hitori.tub_not_full");
                return;
            }
            p.getMainHandItem().shrink(1);
            Vec3 t = OniDoll.tubSpot(0);
            soundAt(level, t, ModSounds.RITUAL_SPLASH.get(), 1.0f, 1.0f);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, t.x, t.y + 0.3, t.z, 20, 0.3, 0.1, 0.3, 0.1);
            Doll.spawnInTub(level, d);
            complete(level, d, Step.TUB, p);
            return;
        }
        if (d.phase == GameData.PHASE_FINISH && d.done(Step.WIN)) {
            if (Apt.bathWater(level) > 0) {
                // pull the plug
                Apt.setBathWater(level, Math.max(0, Apt.bathWater(level) - 1));
                soundAt(level, OniDoll.tubSpot(0), ModSounds.FIXTURE_WATER_SLOSH.get(), 0.8f, 0.7f);
                GameManager.thought(p, Apt.bathWater(level) > 0 ? "thought.hitori.draining" : "thought.hitori.drained");
                return;
            }
            if (held == Kind.LIGHTER) burnAt(p, d, level, OniDoll.tubFloor(0));
            else if (GameManager.has(p, ModItems.RITUAL_DOLL.get())) GameManager.thought(p, "thought.hitori.need_lighter");
        }
    }

    private static void sinkUse(ServerPlayer p, GameData d, PlayerState ps, ServerLevel level) {
        if (d.phase != GameData.PHASE_FINISH || !d.done(Step.WIN)) return;
        Kind held = GameItem.kindOf(p.getMainHandItem());
        if (held == Kind.LIGHTER) {
            Layout.Marker m = Layout.marker("kitchen_sink");
            // on the sink's counter (14/16), lying on its back at the entity origin
            burnAt(p, d, level, new Vec3(m.pos().getX() + 0.5, m.pos().getY() + 14.0 / 16.0, m.pos().getZ() + 0.5));
        } else if (GameManager.has(p, ModItems.RITUAL_DOLL.get())) {
            GameManager.thought(p, "thought.hitori.need_lighter");
        }
    }

    private static void burnAt(ServerPlayer p, GameData d, ServerLevel level, Vec3 at) {
        ItemStack doll = GameManager.find(p, ModItems.RITUAL_DOLL.get());
        if (doll.isEmpty() || !GameItem.spat(doll)) {
            GameManager.thought(p, "thought.hitori.need_doll");
            return;
        }
        doll.shrink(1);
        sound(p, ModSounds.ITEM_LIGHTER.get(), 0.8f, 1.0f);
        OniDoll e = Doll.spawnAt(level, d, at, p.getYRot() + 180.0f);
        if (e != null) e.burn();
        GameManager.thought(p, "thought.hitori.burning");
        d.flag("burning");
    }

    // ================================================================================================ item uses
    public static boolean useItem(ServerPlayer p, InteractionHand hand, Kind kind) {
        GameData d = data(p);
        PlayerState ps = ps(p);
        ItemStack stack = p.getItemInHand(hand);
        switch (kind) {
            case SALT_WATER -> {
                return GameManager.sip(p, stack);
            }
            case CUP_WATER -> {
                if (GameManager.has(p, ModItems.SALT.get())) {
                    startAction(p, "salt", 30);
                    return true;
                }
                GameManager.thought(p, "thought.hitori.need_salt");
                return false;
            }
            case SALT -> {
                if (GameManager.has(p, ModItems.CUP_WATER.get())) {
                    startAction(p, "salt", 30);
                    return true;
                }
                GameManager.thought(p, "thought.hitori.need_cup");
                return false;
            }
            case NAIL_CLIPPER -> {
                ItemStack rice = GameManager.find(p, ModItems.PLUSH_RICE.get());
                if (rice.isEmpty()) {
                    GameManager.thought(p, d.done(Step.NAILS) ? "thought.hitori.nails_done" : "thought.hitori.nails_hint");
                    return false;
                }
                if (GameItem.hasNails(rice)) return false;
                startAction(p, "nails", 44);
                return true;
            }
            case RED_THREAD -> {
                ItemStack rice = GameManager.find(p, ModItems.PLUSH_RICE.get());
                if (rice.isEmpty()) {
                    GameManager.thought(p, "thought.hitori.sew_hint");
                    return false;
                }
                if (!GameItem.hasNails(rice)) {
                    GameManager.thought(p, "thought.hitori.sew_nails_first");
                    return false;
                }
                startAction(p, "sew", 100);
                return true;
            }
            case RICE_SCOOP -> {
                if (GameManager.has(p, ModItems.PLUSH_OPEN.get())) {
                    startAction(p, "rice", 50);
                    return true;
                }
                GameManager.thought(p, "thought.hitori.rice_hint");
                return false;
            }
            case LIGHTER -> {
                sound(p, ModSounds.ITEM_LIGHTER.get(), 0.6f, 1.0f);
                p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, p.getX() + p.getLookAngle().x * 0.5,
                        p.getEyeY() - 0.2, p.getZ() + p.getLookAngle().z * 0.5, 1, 0.0, 0.0, 0.0, 0.0);
                if (d.phase == GameData.PHASE_FINISH && d.done(Step.WIN) && GameManager.has(p, ModItems.RITUAL_DOLL.get())) {
                    if (distTo(p, "kitchen_sink") < 2.2) sinkUse(p, d, ps, p.serverLevel());
                    else if (distTo(p, "bathtub") < 2.2) tubUse(p, d, ps, p.serverLevel());
                    else GameManager.thought(p, "thought.hitori.burn_where");
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ================================================================================================ the action key (R)
    public static void actionKey(ServerPlayer p) {
        if (!GameManager.inGame(p)) return;
        GameData d = data(p);
        PlayerState ps = ps(p);
        if (ps.busy() || ps.caught() || ps.locked || ps.inApp()) return;
        ServerLevel level = p.serverLevel();
        long now = d.playTicks;
        Step cur = current(d);
        if (cur == null) return;
        // a spoken line is not cut off by the next one (the count and the spitting are not lines)
        boolean line = cur == Step.FIRST_ONI || cur == Step.NEXT_ONI || cur == Step.WIN;
        if (line && d.has("line_at") && now >= lastLine && now - lastLine < LINE_GAP) return;
        switch (cur) {
            case FIRST_ONI -> {
                if (!GameManager.holding(p, ModItems.RITUAL_DOLL.get())) {
                    GameManager.thought(p, "thought.hitori.hold_doll");
                    return;
                }
                speak(p, ps, "first_oni", ModSounds.VOICE_FIRST_ONI.get());
                d.firstOniSaid++;
                GameManager.say(p, "me", "say.hitori.first_oni", 60, d.dollName, String.valueOf(d.firstOniSaid));
                if (d.firstOniSaid >= 3) complete(level, d, Step.FIRST_ONI, p);
            }
            case COUNT -> {
                if (distTo(p, "tv") > 3.5) {
                    GameManager.thought(p, "thought.hitori.count_where");
                    return;
                }
                if (!Apt.tvOn(level) && !d.has("tv_self_pending")) {
                    GameManager.thought(p, "thought.hitori.tv_off");
                    return;
                }
                startAction(p, "count", 210);
            }
            case NEXT_ONI -> {
                speak(p, ps, "next_oni", ModSounds.VOICE_NEXT_ONI.get());
                GameManager.say(p, "me", "say.hitori.next_oni", 70, d.dollName);
                complete(level, d, Step.NEXT_ONI, p);
            }
            case SPIT -> {
                OniDoll doll = Doll.get(level);
                if (doll == null || doll.distanceTo(p) > 2.4 || !Watch.eyesOn(p, doll.position().add(0, 0.3, 0))) {
                    GameManager.thought(p, "thought.hitori.spit_where");
                    return;
                }
                if (ps.mouth <= 0.0f) {
                    GameManager.thought(p, "thought.hitori.spit_empty");
                    return;
                }
                sound(p, ModSounds.RITUAL_SPIT.get(), 1.0f, 1.0f);
                GameManager.loseWater(p, ps, "spit");
                doll.spatOn();
                lastLine = now;
                d.flag("line_at");
                GameManager.addFear(p, 0.2f);
                complete(level, d, Step.SPIT, p);
            }
            case WIN -> {
                speak(p, ps, "win", ModSounds.VOICE_WATASHI_KACHI.get());
                d.winSaid++;
                GameManager.say(p, "me", "say.hitori.win", 56, String.valueOf(d.winSaid));
                if (d.winSaid >= 3) complete(level, d, Step.WIN, p);
            }
            default -> {
            }
        }
    }

    private static long lastLine;
    private static int tvSelfIn = 100;

    /** A ritual line out loud: noise 10; with salt water in the mouth it is gone (a rule while hiding). */
    private static void speak(ServerPlayer p, PlayerState ps, String what, SoundEvent voice) {
        GameData d = data(p);
        lastLine = d.playTicks;
        d.flag("line_at");
        sound(p, voice, 1.0f, 1.0f);
        NoiseSystem.emit(p.serverLevel(), p.getEyePosition(), 10.0f, "speak", p);
        if (ps.mouth > 0.0f) GameManager.loseWater(p, ps, "speak");
    }

    // ================================================================================================ the doll
    /** A click on the doll: the stab (step 16), picking it up limp (phase 3). */
    public static boolean interactDoll(ServerPlayer p, InteractionHand hand, OniDoll doll) {
        if (!GameManager.inGame(p) || hand != InteractionHand.MAIN_HAND) return false;
        GameData d = data(p);
        PlayerState ps = ps(p);
        if (ps.busy() || ps.locked || ps.caught()) return false;
        ServerLevel level = p.serverLevel();
        if (current(d) == Step.STAB && GameManager.holding(p, ModItems.KNIFE.get()) && doll.state() == OniDoll.State.DORMANT) {
            GameManager.consume(p, ModItems.KNIFE.get());
            doll.setKnife(true);
            sound(p, ModSounds.VOICE_MITSUKETA.get(), 1.0f, 1.0f);
            level.playSound(null, doll.getX(), doll.getY() + 0.3, doll.getZ(), ModSounds.RITUAL_STAB.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
            level.sendParticles(ModParticles.RICE_GRAIN.get(), doll.getX(), doll.getY() + 0.35, doll.getZ(), 14, 0.12, 0.1, 0.12, 0.06);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, doll.getX(), doll.getY() + 0.3, doll.getZ(), 10, 0.2, 0.05, 0.2, 0.1);
            GameManager.say(p, "me", "say.hitori.mitsuketa", 60, d.dollName);
            NoiseSystem.emit(level, p.getEyePosition(), 10.0f, "speak", p);
            GameManager.event(p, GameManager.EV_SHAKE, 4, "");
            GameManager.addFear(p, 0.15f);
            lastLine = d.playTicks;
            d.flag("line_at");
            Recording.event(level, "stab", doll.position());
            complete(level, d, Step.STAB, p);
            return true;
        }
        if (d.phase == GameData.PHASE_FINISH && doll.state() == OniDoll.State.LIMP) {
            if (!d.done(Step.WIN)) {
                GameManager.thought(p, "thought.hitori.say_win");
                return true;
            }
            ItemStack item = new ItemStack(ModItems.RITUAL_DOLL.get());
            GameItem.setDollName(item, d.dollName);
            GameItem.setSpat(item);
            doll.discard();
            Doll.forget(d);
            GameManager.give(p, item);
            sound(p, ModSounds.DOLL_RICE.get(), 0.7f, 0.9f);
            GameManager.thought(p, "thought.hitori.carry");
            return true;
        }
        return false;
    }

    // ================================================================================================ the name
    public static void named(ServerPlayer p, String raw) {
        GameData d = data(p);
        if (current(d) != Step.NAME) return;
        String name = raw == null ? "" : raw.strip().replaceAll("[\\p{Cntrl}§]", "");
        if (name.length() > 12) name = name.substring(0, 12);
        if (name.isEmpty()) name = GameData.DEFAULT_NAME;
        d.dollName = name;
        d.setDirty();
        ItemStack doll = GameManager.find(p, ModItems.RITUAL_DOLL.get());
        if (!doll.isEmpty()) GameItem.setDollName(doll, name);
        GameManager.say(p, "me", "say.hitori.named", 70, name);
        complete(p.serverLevel(), d, Step.NAME, p);
    }

    public static void readPrintout(ServerPlayer p) {
        GameData d = data(p);
        if (d.flag("printout_read")) GameManager.event(p, GameManager.EV_HINT, 1, "");
    }

    // ================================================================================================ timed actions
    private static void startAction(ServerPlayer p, String id, int ticks) {
        PlayerState ps = ps(p);
        if (ps.busy()) return;
        ps.action = id;
        ps.actionTicks = 0;
        ps.actionLength = ticks;
        ps.lockPos = p.position();
        actionTick(p, ps, id, 0);
    }

    public static void tickPlayer(ServerLevel level, ServerPlayer p, PlayerState ps, GameData d) {
        if (ps.action == null) return;
        ps.actionTicks++;
        actionTick(p, ps, ps.action, ps.actionTicks);
        if (ps.actionTicks >= ps.actionLength) {
            String id = ps.action;
            ps.action = null;
            ps.lockPos = null;
            finishAction(level, p, ps, d, id);
        }
    }

    private static void actionTick(ServerPlayer p, PlayerState ps, String id, int t) {
        ServerLevel level = p.serverLevel();
        Vec3 hands = p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(0.0, -0.45, 0.0);
        switch (id) {
            case "cut" -> {
                if (t == 0) sound(p, ModSounds.RITUAL_CUT.get(), 0.9f, 1.0f);
                if (t == 30) sound(p, ModSounds.RITUAL_STUFFING.get(), 0.9f, 1.0f);
                if (t >= 25 && t % 5 == 0) level.sendParticles(ModParticles.STUFFING.get(), hands.x, hands.y, hands.z, 3, 0.12, 0.05, 0.12, 0.03);
            }
            case "rice" -> {
                if (t == 0) sound(p, ModSounds.RITUAL_RICE.get(), 0.9f, 1.0f);
                if (t % 4 == 0) level.sendParticles(ModParticles.RICE_GRAIN.get(), hands.x, hands.y + 0.15, hands.z, 4, 0.06, 0.02, 0.06, 0.01);
            }
            case "nails" -> {
                if (t == 0) sound(p, ModSounds.RITUAL_CLIP.get(), 0.8f, 1.0f);
            }
            case "sew" -> {
                if (t == 0 || t == 48) sound(p, ModSounds.RITUAL_SEW.get(), 0.8f, 0.95f + level.random.nextFloat() * 0.1f);
                if (t == 76) sound(p, ModSounds.RITUAL_THREAD_WIND.get(), 0.8f, 1.0f);
            }
            case "salt" -> {
                if (t == 0) sound(p, ModSounds.RITUAL_SALT_POUR.get(), 0.8f, 1.0f);
                if (t % 3 == 0) level.sendParticles(ModParticles.SALT_SPRAY.get(), hands.x, hands.y + 0.1, hands.z, 2, 0.03, 0.02, 0.03, 0.0);
            }
            case "count" -> countTick(level, p, ps, t);
            default -> {
            }
        }
    }

    /** Eyes closed at the TV: black, your counting voice; something moves in the water. */
    private static void countTick(ServerLevel level, ServerPlayer p, PlayerState ps, int t) {
        if (t == 0) {
            ps.eyesClosed = true;
            GameManager.event(p, GameManager.EV_EYES, 1, "");
            sound(p, ModSounds.VOICE_COUNT.get(), 1.0f, 1.0f);
            GameManager.say(p, "me", "say.hitori.count", 200);
        }
        Vec3 tub = OniDoll.tubSpot(0);
        if (t == 64 || t == 118 || t == 162) GameManager.privateSound(p, ModSounds.FIXTURE_WATER_SLOSH.get(), tub, 1.0f, 0.85f + level.random.nextFloat() * 0.2f);
        if (t == 150) {
            OniDoll doll = Doll.get(level);
            if (doll != null && doll.state() == OniDoll.State.DORMANT) doll.shiftInTub();
        }
        if (t == 186) GameManager.privateSound(p, ModSounds.DOLL_DRIP.get(), tub, 0.8f, 1.0f);
    }

    private static void finishAction(ServerLevel level, ServerPlayer p, PlayerState ps, GameData d, String id) {
        switch (id) {
            case "cut" -> {
                ItemStack plush = GameManager.find(p, ModItems.PLUSH.get());
                if (plush.isEmpty()) return;
                GameManager.transform(p, plush, ModItems.PLUSH_OPEN.get());
                complete(level, d, Step.CUT, p);
            }
            case "rice" -> {
                ItemStack open = GameManager.find(p, ModItems.PLUSH_OPEN.get());
                if (open.isEmpty()) return;
                GameManager.transform(p, open, ModItems.PLUSH_RICE.get());
                GameManager.consume(p, ModItems.RICE_SCOOP.get());
                complete(level, d, Step.RICE, p);
            }
            case "nails" -> {
                ItemStack rice = GameManager.find(p, ModItems.PLUSH_RICE.get());
                if (rice.isEmpty()) return;
                GameItem.setNails(rice);
                complete(level, d, Step.NAILS, p);
            }
            case "sew" -> {
                ItemStack rice = GameManager.find(p, ModItems.PLUSH_RICE.get());
                if (rice.isEmpty() || !GameItem.hasNails(rice)) return;
                ItemStack doll = GameManager.transform(p, rice, ModItems.RITUAL_DOLL.get());
                doll.removeTagKey("nails");
                GameManager.consume(p, ModItems.RED_THREAD.get());
                complete(level, d, Step.SEW, p);
            }
            case "salt" -> {
                ItemStack cup = GameManager.find(p, ModItems.CUP_WATER.get());
                if (cup.isEmpty()) return;
                ItemStack salted = GameManager.transform(p, cup, ModItems.SALT_WATER.get());
                GameItem.setSips(salted, GameItem.CUP_SIPS);
                GameManager.consume(p, ModItems.SALT.get());
                complete(level, d, Step.SALT, p);
            }
            case "count" -> {
                ps.eyesClosed = false;
                GameManager.event(p, GameManager.EV_EYES, 0, "");
                complete(level, d, Step.COUNT, p);
            }
            default -> {
            }
        }
    }

    // ================================================================================================ the steps
    /** A step is done: the tick, the next line on the HUD, and what follows from it. */
    public static void complete(ServerLevel level, GameData d, Step s, @Nullable ServerPlayer p) {
        if (d.done(s)) return;
        d.setDone(s, true);
        Hitori.LOGGER.info("[hitori] step {} done at {}", s.id, Clock.hhmm(d.clock));
        for (ServerPlayer q : GameManager.players(level)) {
            if (!GameManager.inGame(q)) continue;
            GameManager.event(q, GameManager.EV_STEP, s.ordinal(), "");
        }
        switch (s) {
            case NEXT_ONI -> {
                // it is the oni now
                d.phase = GameData.PHASE_HIDING;
                d.hideStartTick = d.playTicks;
                OniDoll doll = Doll.get(level);
                if (doll != null) doll.wake();
                if (p != null) {
                    GameManager.thought(p, "thought.hitori.run");
                    GameManager.addFear(p, 0.2f);
                }
                Recording.event(level, "oni", OniDoll.tubSpot(0));
            }
            case HIDE -> {
                if (p != null) GameManager.thought(p, "thought.hitori.hidden");
            }
            case BURN -> Endings.begin(level, p, GameData.ENDING_GOOD);
            default -> {
            }
        }
        d.setDirty();
    }

    /** Per tick: the steps that complete by themselves, three o'clock, dawn. */
    public static void tick(ServerLevel level, GameData d) {
        if (!d.started) return;
        switch (d.phase) {
            case GameData.PHASE_PREP -> tickPrep(level, d);
            case GameData.PHASE_RITUAL, GameData.PHASE_HIDING -> tickRitual(level, d);
            case GameData.PHASE_FINISH -> tickFinish(level, d);
            default -> {
            }
        }
        if (d.ritualRunning() && d.clock >= Clock.DAWN) {
            ServerPlayer p = firstPlayer(level);
            Endings.begin(level, p, GameData.ENDING_MIKAN);
        }
    }

    @Nullable
    static ServerPlayer firstPlayer(ServerLevel level) {
        for (ServerPlayer p : GameManager.players(level)) if (GameManager.inGame(p)) return p;
        return null;
    }

    private static void tickPrep(ServerLevel level, GameData d) {
        if (!d.done(Step.BATH) && Apt.bathWater(level) >= 2) complete(level, d, Step.BATH, null);
        if (!d.done(Step.TV) && Apt.tvOn(level)) complete(level, d, Step.TV, null);
        if (!d.done(Step.CAMERAS) && !d.cameras.isEmpty() && d.has("app_opened")) complete(level, d, Step.CAMERAS, null);
        // three o'clock
        if (Step.allDone(d, Step.PREP) && d.clock >= Clock.THREE) {
            d.phase = GameData.PHASE_RITUAL;
            d.ritualStartTick = d.playTicks;
            d.setDirty();
            for (ServerPlayer p : GameManager.players(level)) {
                if (!GameManager.inGame(p)) continue;
                GameManager.event(p, GameManager.EV_THREE, 0, "");
                GameManager.addFear(p, 0.15f);
                GameManager.say(p, "", "say.hitori.three_am", 80);
            }
            Hitori.LOGGER.info("[hitori] three o'clock: the ritual begins");
        }
    }

    private static void tickRitual(ServerLevel level, GameData d) {
        Step cur = current(d);
        if (cur == Step.LIGHTS && !Apt.anyLightOn(level)) complete(level, d, Step.LIGHTS, null);
        // the breaker took the TV with it: a moment of dark, then it comes on by itself
        if (d.phase == GameData.PHASE_RITUAL && d.done(Step.TV) && !Apt.tvOn(level) && !d.done(Step.COUNT)) {
            if (d.flag("tv_self_pending")) tvSelfIn = 100;
            if (--tvSelfIn <= 0 && d.has("tv_self_pending") && !d.has("tv_self_on")) {
                d.flag("tv_self_on");
                Apt.setTv(level, true);
                Vec3 tv = Layout.marker("tv").center().add(0, 0.6, 0);
                soundAt(level, tv, ModSounds.FIXTURE_TV_ON.get(), 1.0f, 0.9f);
                for (ServerPlayer p : GameManager.players(level)) {
                    if (!GameManager.inGame(p)) continue;
                    GameManager.thought(p, "thought.hitori.tv_self");
                    GameManager.addFear(p, 0.15f);
                }
            }
        }
        if (cur == Step.KNIFE) {
            for (ServerPlayer p : GameManager.players(level)) {
                if (!GameManager.inGame(p) || !GameManager.holding(p, ModItems.KNIFE.get())) continue;
                if (distTo(p, "bathtub") < 3.2) complete(level, d, Step.KNIFE, p);
            }
        }
        if (cur == Step.HIDE) {
            for (ServerPlayer p : GameManager.players(level)) {
                if (!GameManager.inGame(p)) continue;
                PlayerState ps = ps(p);
                if (ps.hiding() && ps.mouth > 0.0f) {
                    if ("western".equals(ps.hideSpot) && d.breakRule("closet")) GameManager.event(p, GameManager.EV_RULE, 3, "closet");
                    complete(level, d, Step.HIDE, p);
                } else if (ps.hiding() && ps.hiddenTicks == 40) {
                    GameManager.thought(p, "thought.hitori.need_water");
                }
            }
        }
    }

    private static void tickFinish(ServerLevel level, GameData d) {
        if (!d.done(Step.FIND)) {
            OniDoll doll = Doll.get(level);
            if (doll != null) {
                for (ServerPlayer p : GameManager.players(level)) {
                    if (!GameManager.inGame(p)) continue;
                    if (doll.distanceTo(p) < 2.6 && Watch.eyesOn(p, doll.position().add(0, 0.3, 0))) {
                        complete(level, d, Step.FIND, p);
                        GameManager.event(p, GameManager.EV_STINGER, 0, "");
                        GameManager.addFear(p, 0.2f);
                    }
                }
            }
        }
        // the fire has done its work
        if (d.has("burning") && !d.done(Step.BURN)) {
            OniDoll doll = Doll.get(level);
            if (doll == null || !doll.burning()) complete(level, d, Step.BURN, firstPlayer(level));
        }
    }

    /** The player left the closet: phase 3 begins (docs §1). */
    public static void leftCloset(ServerPlayer p) {
        GameData d = data(p);
        if (d.phase != GameData.PHASE_HIDING || !d.done(Step.HIDE)) return;
        d.phase = GameData.PHASE_FINISH;
        d.finishStartTick = d.playTicks;
        d.setDirty();
        OniDoll doll = Doll.get(p.serverLevel());
        if (doll != null) doll.startWaiting();
        GameManager.thought(p, d.clock < Clock.FOUR ? "thought.hitori.early_out" : "thought.hitori.find");
        Hitori.LOGGER.info("[hitori] phase 3 at {}", Clock.hhmm(d.clock));
    }

    /** A light was switched on during the ritual (§3.5: it sees you from afar). */
    public static void lightUsed(ServerLevel level, String room) {
        GameData d = GameData.get(level.getServer());
        if (!d.ritualRunning() || !d.done(Step.LIGHTS)) return;
        if (d.breakRule("lights")) {
            for (ServerPlayer p : GameManager.players(level)) if (GameManager.inGame(p)) GameManager.event(p, GameManager.EV_RULE, 4, "lights");
        }
    }

    static float clamp01(float v) {
        return Mth.clamp(v, 0.0f, 1.0f);
    }

    static List<ServerPlayer> inGamePlayers(ServerLevel level) {
        return GameManager.players(level).stream().filter(GameManager::inGame).toList();
    }
}
