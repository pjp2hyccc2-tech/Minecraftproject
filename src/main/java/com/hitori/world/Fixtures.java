package com.hitori.world;

import com.hitori.Hitori;
import com.hitori.block.BalconyDoorBlock;
import com.hitori.block.BathtubBlock;
import com.hitori.block.HitoriDoorBlock;
import com.hitori.block.PropBlock;
import com.hitori.block.Props;
import com.hitori.block.SlidingDoorBlock;
import com.hitori.registry.ModBlocks;
import com.hitori.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Everything the game (ritual, director, the doll) does to the apartment (docs/DESIGN.md §2/§3). Owner: world.
 * Safe to call any time on the apartment level; no-ops elsewhere. Rooms are the Layout room keys ("tatami", "bath", ...).
 * Calls made by the game play their sounds but do not report noises back through {@link FixtureEvents} (the game
 * knows); things players do in the world (switches, doors, taps...) do report them.
 */
public final class Fixtures {
    private Fixtures() {}

    static boolean here(ServerLevel level) {
        return level != null && level.dimension() == Layout.DIMENSION;
    }

    static WorldData data(ServerLevel level) {
        return WorldData.get(level);
    }

    static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos);
    }

    static void sound(ServerLevel level, BlockPos pos, Supplier<SoundEvent> ev, float volume, float pitch) {
        level.playSound(null, pos, ev.get(), SoundSource.BLOCKS, volume, pitch);
    }

    static boolean setBool(ServerLevel level, BlockPos pos, BooleanProperty prop, boolean value) {
        if (!loaded(level, pos)) return false;
        BlockState s = level.getBlockState(pos);
        if (!s.hasProperty(prop) || s.getValue(prop) == value) return false;
        level.setBlock(pos, s.setValue(prop, value), 3);
        return true;
    }

    static boolean setInt(ServerLevel level, BlockPos pos, IntegerProperty prop, int value) {
        if (!loaded(level, pos)) return false;
        BlockState s = level.getBlockState(pos);
        if (!s.hasProperty(prop) || s.getValue(prop) == value) return false;
        level.setBlock(pos, s.setValue(prop, value), 3);
        return true;
    }

    static boolean getBool(ServerLevel level, BlockPos pos, BooleanProperty prop) {
        if (!loaded(level, pos)) return false;
        BlockState s = level.getBlockState(pos);
        return s.hasProperty(prop) && s.getValue(prop);
    }

    static BlockPos at(String marker) {
        return Layout.marker(marker).pos();
    }

    // --- setup ----------------------------------------------------------------------------------------------------
    /** Makes sure the building exists and its block entities are created (call when a game starts). */
    public static void ensureBuilt(ServerLevel level) {
        if (!here(level)) return;
        for (int cx = -1; cx <= 2; cx++)
            for (int cz = -1; cz <= 1; cz++) level.getChunk(cx, cz);
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(ApartmentBuilder.X0, 68, 0), new BlockPos(ApartmentBuilder.X0 + 11, 73, 13))) {
            BlockState s = level.getBlockState(p);
            if (s.hasBlockEntity() && level.getBlockEntity(p) == null && s.getBlock() instanceof EntityBlock eb) {
                BlockEntity be = eb.newBlockEntity(p.immutable(), s);
                if (be != null) level.setBlockEntity(be);
            }
        }
        nameSign(level);
    }

    /** 「コーポ白樺」 on the sign over the mailboxes. */
    static void nameSign(ServerLevel level) {
        BlockPos p = ApartmentBuilder.NAME_SIGN;
        if (!loaded(level, p)) return;
        BlockState s = level.getBlockState(p);
        if (!(s.getBlock() instanceof net.minecraft.world.level.block.SignBlock)) return;
        BlockEntity be = level.getBlockEntity(p);
        if (be == null && s.getBlock() instanceof EntityBlock eb) {
            be = eb.newBlockEntity(p, s);
            if (be != null) level.setBlockEntity(be);
        }
        if (be instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
            net.minecraft.world.level.block.entity.SignText text = new net.minecraft.world.level.block.entity.SignText()
                    .setMessage(1, net.minecraft.network.chat.Component.literal("コーポ白樺"))
                    .setMessage(2, net.minecraft.network.chat.Component.literal("CORPO SHIRAKABA"))
                    .setColor(net.minecraft.world.item.DyeColor.LIGHT_GRAY);
            sign.setText(text, true);
            sign.setWaxed(true);
            sign.setChanged();
            level.sendBlockUpdated(p, s, s, 3);
        }
    }

    /** Restores room 203 to the evening's start state (lights on in the western room, TV off, bath empty, the plush on
     *  its shelf, the knife on its rack, every door shut, items back in place). The whole building is rebuilt. */
    public static void resetAll(ServerLevel level) {
        if (!here(level)) return;
        ensureBuilt(level);
        data(level).reset();
        WorldTicker.clear();
        LevelSink sink = new LevelSink(level, -12, -8, 48, 18);
        ApartmentBuilder.build(sink);
        sink.finish();
        for (String room : Layout.LIGHT_ROOMS) applyRoom(level, room);
        nameSign(level);
        Hitori.LOGGER.info("[hitori] apartment reset ({} blocks restored)", sink.changed);
    }

    // --- lights ---------------------------------------------------------------------------------------------------
    /** The room whose switch block this is (switch_<room> markers), or null. */
    public static String switchRoom(BlockPos pos) {
        for (String r : Layout.LIGHT_ROOMS) if (at("switch_" + r).equals(pos)) return r;
        return null;
    }

    public static void setLights(ServerLevel level, String room, boolean on) {
        if (!here(level) || room == null || !Layout.has("switch_" + room)) return;
        WorldData d = data(level);
        boolean changed = d.switchOn(room) != on;
        d.switches.put(room, on);
        d.setDirty();
        applyRoom(level, room);
        if (changed) {
            BlockPos sw = at("switch_" + room);
            sound(level, sw, "tatami".equals(room) ? ModSounds.FIXTURE_PULL_CORD : ModSounds.FIXTURE_SWITCH, 0.8f, on ? 1.0f : 0.9f);
            FixtureEvents.LISTENER.switched(level, room, on);
        }
    }

    /** A player flipped a switch (or pulled the cord). */
    public static void playerSwitch(ServerLevel level, String room, boolean on, BlockPos pos) {
        setLights(level, room, on);
    }

    /** The switch position of a room (on even with the breaker off). */
    public static boolean switchOn(ServerLevel level, String room) {
        return here(level) && data(level).switchOn(room);
    }

    /** True if the room's lamps are lit (switched on and the breaker on). */
    public static boolean lightsOn(ServerLevel level, String room) {
        if (!here(level)) return false;
        WorldData d = data(level);
        return d.breaker && d.switchOn(room);
    }

    /** The breaker in the genkan: everything electric off/on (the TV included). */
    public static void setBreaker(ServerLevel level, boolean on) {
        if (!here(level)) return;
        WorldData d = data(level);
        boolean changed = d.breaker != on;
        d.breaker = on;
        d.setDirty();
        for (String room : Layout.LIGHT_ROOMS) applyRoom(level, room);
        setBool(level, at("breaker"), Props.LIT, on);
        setBool(level, at("fridge"), Props.ON, on);
        if (!on) {
            setBool(level, at("tv"), Props.ON, false);
            setBool(level, at("desk_lamp"), Props.LIT, false);
            setBool(level, at("washing_machine"), Props.RUNNING, false);
            setBool(level, at("intercom"), Props.RINGING, false);
        } else {
            setBool(level, at("tv"), Props.ON, d.tvSwitch);
        }
        if (changed) {
            sound(level, at("breaker"), on ? ModSounds.FIXTURE_BREAKER_ON : ModSounds.FIXTURE_BREAKER_OFF, 1.0f, 1.0f);
            FixtureEvents.LISTENER.switched(level, "breaker", on);
        }
    }

    public static boolean breakerOn(ServerLevel level) {
        return !here(level) || data(level).breaker;
    }

    /** A light flickers for a while, even if switched off (霊障). */
    public static void flicker(ServerLevel level, String room, int ticks) {
        if (!here(level)) return;
        for (BlockPos p : lightsIn(level, room)) WorldTicker.flicker(level, p, ticks);
    }

    /** The hanging lamp in the tatami room swings for a while. */
    public static void swingLamp(ServerLevel level, int ticks) {
        if (!here(level)) return;
        WorldTicker.swing(level, ticks);
        sound(level, at("hanging_lamp"), ModSounds.FIXTURE_LAMP_SWING, 0.8f, 1.0f);
    }

    /** Light fixtures of a room (for the director and the shader). */
    public static List<BlockPos> lightsIn(ServerLevel level, String room) {
        List<BlockPos> out = new ArrayList<>();
        if (room == null || !Layout.has("lights_" + room)) return out;
        for (Layout.Marker m : Layout.markers("lights_" + room)) out.add(m.pos());
        return out;
    }

    /** The room of one of 203's lamps, or null for the lamps outside (always lit). */
    static String roomOfLamp(BlockPos p) {
        for (String r : Layout.LIGHT_ROOMS) for (Layout.Marker m : Layout.markers("lights_" + r)) if (m.pos().equals(p)) return r;
        return null;
    }

    /** Whether the lamp at p should be lit right now. */
    public static boolean shouldBeLit(ServerLevel level, BlockPos p) {
        String room = roomOfLamp(p);
        return room == null || lightsOn(level, room);
    }

    /** Puts the room's lamps and switch into the state of its switch and the breaker. */
    static void applyRoom(ServerLevel level, String room) {
        WorldData d = data(level);
        boolean lit = d.breaker && d.switchOn(room);
        for (BlockPos p : lightsIn(level, room)) {
            if (!WorldTicker.flickering(p)) setBool(level, p, Props.LIT, lit);
        }
        setBool(level, at("switch_" + room), Props.ON, d.switchOn(room));
    }

    // --- the TV ---------------------------------------------------------------------------------------------------
    /** The TV's own switch. Turning it on works even with the breaker off (the game may want that); the player's
     *  button does nothing then. */
    public static void setTv(ServerLevel level, boolean on) {
        if (!here(level)) return;
        WorldData d = data(level);
        d.tvSwitch = on;
        d.setDirty();
        BlockPos tv = at("tv");
        if (setBool(level, tv, Props.ON, on)) {
            if (on) sound(level, tv, ModSounds.FIXTURE_TV_ON, 1.0f, 1.0f);
            else sound(level, tv, ModSounds.FIXTURE_SWITCH, 0.8f, 0.6f);
            FixtureEvents.LISTENER.switched(level, "tv", on);
        }
    }

    /** The player pressed the TV's button. */
    public static void playerTv(ServerLevel level, BlockPos pos) {
        if (!here(level)) return;
        if (!breakerOn(level)) {
            sound(level, pos, ModSounds.FIXTURE_SWITCH, 0.6f, 0.6f);
            return;
        }
        setTv(level, !tvOn(level));
    }

    public static boolean tvOn(ServerLevel level) {
        return here(level) && getBool(level, at("tv"), Props.ON);
    }

    /** 0 static, 1 a dark room, 2 a face. */
    public static void setTvChannel(ServerLevel level, int channel) {
        if (!here(level)) return;
        BlockPos tv = at("tv");
        if (setInt(level, tv, Props.CHANNEL, Math.max(0, Math.min(2, channel))) && tvOn(level)) {
            sound(level, tv, ModSounds.FIXTURE_TV_CHANNEL, 1.0f, 1.0f);
        }
    }

    public static int tvChannel(ServerLevel level) {
        if (!here(level)) return 0;
        BlockState s = level.getBlockState(at("tv"));
        return s.hasProperty(Props.CHANNEL) ? s.getValue(Props.CHANNEL) : 0;
    }

    // --- water ----------------------------------------------------------------------------------------------------
    /** The bath tap (fills the tub over ~20 s while running). */
    public static void setBathTap(ServerLevel level, boolean running) {
        if (!here(level)) return;
        BlockPos tap = at("bath_tap");
        if (setBool(level, tap, Props.RUNNING, running)) {
            sound(level, tap, ModSounds.FIXTURE_CREAK, 0.3f, 1.7f);
            if (running) WorldTicker.startFilling(level);
        }
    }

    public static boolean bathTapRunning(ServerLevel level) {
        return here(level) && getBool(level, at("bath_tap"), Props.RUNNING);
    }

    /** 0 empty, 1 half, 2 full. */
    public static int bathWater(ServerLevel level) {
        if (!here(level)) return 0;
        BlockState s = level.getBlockState(at("bathtub"));
        return s.hasProperty(Props.WATER) ? s.getValue(Props.WATER) : 0;
    }

    public static void setBathWater(ServerLevel level, int water) {
        if (!here(level)) return;
        BlockPos tub = at("bathtub");
        BlockState s = level.getBlockState(tub);
        if (!(s.getBlock() instanceof BathtubBlock)) return;
        int w = Math.max(0, Math.min(2, water));
        BathtubBlock.setBoth(level, tub, w, s.getValue(Props.LID));
        WorldTicker.syncFill(w);
    }

    public static boolean bathLid(ServerLevel level) {
        return here(level) && getBool(level, at("bathtub"), Props.LID);
    }

    public static void setBathLid(ServerLevel level, boolean lid) {
        if (!here(level)) return;
        BathtubBlock.setBoth(level, at("bathtub"), bathWater(level), lid);
    }

    /** "kitchen", "washbasin" (and "bath": the tub's tap). */
    public static void setTap(ServerLevel level, String which, boolean running) {
        if (!here(level)) return;
        if ("bath".equals(which) || "bath_tap".equals(which)) {
            setBathTap(level, running);
            return;
        }
        BlockPos p = tapPos(which);
        if (p != null && setBool(level, p, Props.RUNNING, running)) sound(level, p, ModSounds.FIXTURE_CREAK, 0.3f, 1.8f);
    }

    public static boolean tapRunning(ServerLevel level, String which) {
        if (!here(level)) return false;
        if ("bath".equals(which) || "bath_tap".equals(which)) return bathTapRunning(level);
        BlockPos p = tapPos(which);
        return p != null && getBool(level, p, Props.RUNNING);
    }

    static BlockPos tapPos(String which) {
        return switch (which) {
            case "kitchen", "kitchen_sink", "kitchen_tap" -> at("kitchen_sink");
            case "washbasin" -> at("washbasin");
            default -> null;
        };
    }

    // --- doors ----------------------------------------------------------------------------------------------------
    /** The closet's fusuma: 0 shut, 1 crack, 2 open. */
    public static void setClosetFusuma(ServerLevel level, int open) {
        if (!here(level)) return;
        BlockPos p = at("closet_fusuma");
        int o = Math.max(0, Math.min(2, open));
        if (SlidingDoorBlock.setRow(level, p, o)) SlidingDoorBlock.slideSound(level, p, o, false);
    }

    public static int closetFusuma(ServerLevel level) {
        return here(level) ? SlidingDoorBlock.openOf(level, at("closet_fusuma")) : 0;
    }

    /** Doors by key: "front", "toilet", "washroom", "bath", "western", "tatami_dk", "tatami_hall", "western_closet",
     *  "balcony_tatami", "balcony_western". */
    public static void setDoor(ServerLevel level, String door, boolean open) {
        if (!here(level) || !Layout.has("door_" + door)) return;
        boolean sounded = false;
        for (Layout.Marker m : Layout.markers("door_" + door)) {
            BlockPos p = m.pos();
            if (!loaded(level, p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof HitoriDoorBlock) HitoriDoorBlock.setBoth(level, p, open, true);
            else if (s.getBlock() instanceof SlidingDoorBlock) {
                if (SlidingDoorBlock.setRow(level, p, open ? 2 : 0) && !sounded) {
                    SlidingDoorBlock.slideSound(level, p, open ? 2 : 0, false);
                    sounded = true;
                }
            } else if (s.getBlock() instanceof BalconyDoorBlock) {
                BalconyDoorBlock.set(level, p, open, !sounded);
                sounded = true;
            }
        }
    }

    public static boolean doorOpen(ServerLevel level, String door) {
        if (!here(level) || !Layout.has("door_" + door)) return false;
        for (Layout.Marker m : Layout.markers("door_" + door)) {
            BlockState s = level.getBlockState(m.pos());
            if (s.getBlock() instanceof HitoriDoorBlock && s.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) return true;
            if (s.getBlock() instanceof SlidingDoorBlock && s.getValue(Props.OPEN3) == 2) return true;
            if (s.getBlock() instanceof BalconyDoorBlock && s.getValue(Props.OPEN)) return true;
        }
        return false;
    }

    /** Draws or opens the curtain hanging in pos's column (y 70..72). */
    public static void setCurtainColumn(ServerLevel level, BlockPos pos, boolean closed) {
        if (!here(level)) return;
        boolean changed = false;
        for (int y = Layout.FEET_Y; y <= Layout.FEET_Y + 2; y++) changed |= setBool(level, new BlockPos(pos.getX(), y, pos.getZ()), Props.CLOSED, closed);
        if (changed) sound(level, pos, ModSounds.ITEM_PAPER, 0.5f, 0.6f);
    }

    /** The curtains of "tatami" or "western". */
    public static void setCurtains(ServerLevel level, String room, boolean closed) {
        if (!here(level) || !Layout.has("curtains_" + room)) return;
        for (Layout.Marker m : Layout.markers("curtains_" + room)) setCurtainColumn(level, m.pos(), closed);
    }

    // --- devices and the ritual's world items -----------------------------------------------------------------------
    /** "intercom" (ringing), "washing_machine", "fridge" (door open), "laptop" (screen on), "desk_lamp", "gas_stove",
     *  "tv", "kitchen_tap", "washbasin", "bath_tap". */
    public static void setDevice(ServerLevel level, String key, boolean on) {
        if (!here(level)) return;
        switch (key) {
            case "intercom" -> {
                BlockPos p = at("intercom");
                if (setBool(level, p, Props.RINGING, on) && on) {
                    sound(level, p, ModSounds.FIXTURE_INTERCOM, 1.0f, 1.0f);
                    WorldTicker.ringFor(level, 160);
                }
            }
            case "washing_machine" -> {
                BlockPos p = at("washing_machine");
                if (setBool(level, p, Props.RUNNING, on) && on) sound(level, p, ModSounds.FIXTURE_WASHING_START, 1.0f, 1.0f);
            }
            case "fridge" -> {
                BlockPos p = at("fridge");
                if (setBool(level, p, Props.OPEN, on)) {
                    sound(level, p, on ? ModSounds.DOOR_INTERIOR_OPEN : ModSounds.DOOR_INTERIOR_CLOSE, 0.5f, 1.45f);
                }
            }
            case "laptop" -> {
                BlockPos p = at("laptop");
                if (!level.getBlockState(p).is(ModBlocks.LAPTOP_BLOCK.get())) return;
                setBool(level, p, Props.OPEN, true);
                setBool(level, p, Props.ON, on);
            }
            case "desk_lamp" -> {
                BlockPos p = at("desk_lamp");
                boolean lit = on && breakerOn(level);
                if (setBool(level, p, Props.LIT, lit)) sound(level, p, ModSounds.FIXTURE_SWITCH, 0.6f, 1.2f);
                else if (on) sound(level, p, ModSounds.FIXTURE_SWITCH, 0.6f, 1.2f);
            }
            case "gas_stove" -> {
                BlockPos p = at("gas_stove");
                if (setBool(level, p, Props.LIT, on)) sound(level, p, on ? ModSounds.ITEM_LIGHTER : ModSounds.FIXTURE_SWITCH, 0.8f, on ? 0.8f : 0.7f);
            }
            case "tv" -> setTv(level, on);
            case "kitchen_tap" -> setTap(level, "kitchen", on);
            case "washbasin" -> setTap(level, "washbasin", on);
            case "bath_tap" -> setBathTap(level, on);
            default -> {
            }
        }
    }

    public static boolean deviceOn(ServerLevel level, String key) {
        if (!here(level)) return false;
        return switch (key) {
            case "intercom" -> getBool(level, at("intercom"), Props.RINGING);
            case "washing_machine" -> getBool(level, at("washing_machine"), Props.RUNNING);
            case "fridge" -> getBool(level, at("fridge"), Props.OPEN);
            case "laptop" -> getBool(level, at("laptop"), Props.ON);
            case "desk_lamp" -> getBool(level, at("desk_lamp"), Props.LIT);
            case "gas_stove" -> getBool(level, at("gas_stove"), Props.LIT);
            case "tv" -> tvOn(level);
            case "kitchen_tap" -> tapRunning(level, "kitchen");
            case "washbasin" -> tapRunning(level, "washbasin");
            case "bath_tap" -> bathTapRunning(level);
            default -> false;
        };
    }

    /** World items that leave their place when taken: "knife" (rack), "plush" (shelf), "lighter" (drawer), "laptop" (desk). */
    public static void setPresent(ServerLevel level, String key, boolean present) {
        if (!here(level)) return;
        WorldData d = data(level);
        d.present.put(key, present);
        d.setDirty();
        switch (key) {
            case "knife" -> setBool(level, at("knife"), Props.HAS_KNIFE, present);
            case "plush" -> setBool(level, at("plush_shelf"), Props.PRESENT, present);
            case "laptop" -> {
                BlockPos p = at("laptop");
                if (!loaded(level, p)) return;
                BlockState s = level.getBlockState(p);
                if (present && s.isAir()) {
                    level.setBlock(p, ModBlocks.LAPTOP_BLOCK.get().defaultBlockState().setValue(com.hitori.block.FacingBlock.FACING, Layout.marker("laptop").facing())
                            .setValue(Props.OPEN, true), 3);
                } else if (!present && s.is(ModBlocks.LAPTOP_BLOCK.get())) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            default -> {
            }
        }
    }

    public static boolean present(ServerLevel level, String key) {
        return !here(level) || data(level).isPresent(key);
    }

    /** Something falls (a dish breaks in the kitchen, a thing drops elsewhere) near pos. */
    public static void dropSomething(ServerLevel level, BlockPos pos) {
        if (!here(level)) return;
        Layout.Room r = Layout.roomAt(pos);
        boolean kitchen = r != null && r.key().equals("dk");
        sound(level, pos, kitchen ? ModSounds.FIXTURE_DISH_BREAK : ModSounds.FIXTURE_THING_FALL, 1.0f, 0.95f + level.random.nextFloat() * 0.1f);
        Vec3 c = Vec3.atCenterOf(pos);
        ItemStack bits = new ItemStack(kitchen ? Items.WHITE_STAINED_GLASS_PANE : Items.PAPER);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, bits), c.x, c.y, c.z, 12, 0.3, 0.2, 0.3, 0.12);
    }

    /** Wet marks where the doll went (they dry after two minutes). */
    public static void wetMark(ServerLevel level, BlockPos pos) {
        if (!here(level) || !loaded(level, pos)) return;
        BlockPos p = pos;
        for (int i = 0; i < 2 && !level.getBlockState(p).isAir(); i++) p = p.above();
        BlockState below = level.getBlockState(p.below());
        if (!level.getBlockState(p).isAir() || !below.isFaceSturdy(level, p.below(), Direction.UP)) return;
        level.setBlock(p, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        WorldTicker.wetMark(level, p, 2400);
    }

    // --- queries for the game ---------------------------------------------------------------------------------------
    /** The prop id ("crt_tv", "bathtub", ...) of the block at pos, or the block's registry path. */
    public static String propAt(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof PropBlock pb) return pb.id;
        var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(s.getBlock());
        return key == null ? "" : key.getPath();
    }

    /** Every lamp the world ticker keeps in step with its switch (203's) plus the lamps outside. */
    static List<BlockPos> allLamps() {
        List<BlockPos> l = new ArrayList<>(ApartmentBuilder.lamps203());
        for (int x0 : Site.UNIT_X0) {
            l.add(new BlockPos(x0 + 4, 72, -2));
            l.add(new BlockPos(x0 + 4, Layout.GENKAN_Y - 2, -2));
        }
        return l;
    }

    static boolean isLamp(Block b) {
        return b == ModBlocks.FLUORESCENT_RING.get() || b == ModBlocks.HANGING_LAMP.get();
    }
}
