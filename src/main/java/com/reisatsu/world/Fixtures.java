package com.reisatsu.world;

import com.reisatsu.block.FacingBlock;
import com.reisatsu.block.Props;
import com.reisatsu.block.RestaurantDoorBlock;
import com.reisatsu.block.SwingDoorBlock;
import com.reisatsu.block.entity.AutoDoorBlockEntity;
import com.reisatsu.block.entity.SwingDoorBlockEntity;
import com.reisatsu.registry.ModBlocks;
import com.reisatsu.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Everything the game (director, spirits, story) does to the restaurant (docs/DESIGN.md §3.12). Owner: world.
 * All methods are safe to call at any time on the restaurant level and do nothing elsewhere.
 * Doors are named "outer" / "inner"; tables are numbered 1..17 (marker table_n). Calls made by the game do not
 * report noises back through {@link FixtureEvents} (the game knows); things done by players and spirits in the
 * world (doors, buttons, chairs, shards...) do.
 */
public final class Fixtures {
    private Fixtures() {}

    static boolean here(ServerLevel level) {
        return level != null && level.dimension() == Layout.DIMENSION;
    }

    static WorldData data(ServerLevel level) {
        return WorldData.get(level);
    }

    static final BlockPos[] AUTO_DOORS = {new BlockPos(3, 65, 30), new BlockPos(4, 65, 30), new BlockPos(3, 65, 26), new BlockPos(4, 65, 26)};
    static final BlockPos[] SWING_DOORS = {new BlockPos(29, 65, 10), new BlockPos(30, 65, 10)};
    static final BlockPos STALL_3 = new BlockPos(8, 65, 21);

    static void sound(ServerLevel level, BlockPos pos, Supplier<SoundEvent> ev, float volume, float pitch) {
        level.playSound(null, pos, ev.get(), SoundSource.BLOCKS, volume, pitch);
    }

    static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos);
    }

    static void setBool(ServerLevel level, BlockPos pos, BooleanProperty prop, boolean value) {
        if (!loaded(level, pos)) return;
        BlockState s = level.getBlockState(pos);
        if (s.hasProperty(prop) && s.getValue(prop) != value) level.setBlock(pos, s.setValue(prop, value), 3);
    }

    // --- setup ------------------------------------------------------------------------------------------------
    /** Makes sure the restaurant is built and its block entities exist (call when a game starts). */
    public static void ensureBuilt(ServerLevel level) {
        if (!here(level)) return;
        for (int cx = -1; cx <= 4; cx++)
            for (int cz = -1; cz <= 3; cz++) level.getChunk(cx, cz);
        for (BlockPos p : AUTO_DOORS) ensureEntity(level, p);
        for (BlockPos p : SWING_DOORS) ensureEntity(level, p);
    }

    static void ensureEntity(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (!s.hasBlockEntity()) return;
        BlockEntity be = level.getBlockEntity(p);
        if (be == null) {
            be = ((net.minecraft.world.level.block.EntityBlock) s.getBlock()).newBlockEntity(p, s);
            if (be != null) level.setBlockEntity(be);
        }
        level.sendBlockUpdated(p, s, s, 3);
    }

    /** Restores the restaurant to its start state (doors shut, taps off, menus in their stands, circuits as at the start...). */
    public static void resetAll(ServerLevel level) {
        if (!here(level)) return;
        ensureBuilt(level);
        data(level).reset();
        WorldTicker.clear();
        LevelSink sink = new LevelSink(level, Site.PROP_X1 - 1, Site.PROP_Z1 - 1, Site.LOT_X2 + 1, Site.LOT_Z2 + 2);
        ExteriorBuilder.build(sink);
        RestaurantBuilder.build(sink);
        sink.finish();
        for (BlockPos p : AUTO_DOORS) {
            ensureEntity(level, p);
            if (level.getBlockEntity(p) instanceof AutoDoorBlockEntity be) be.forceClose(false);
        }
        for (BlockPos p : SWING_DOORS) ensureEntity(level, p);
        for (Circuit c : Circuit.values()) applyCircuit(level, c);
        com.reisatsu.Reisatsu.LOGGER.info("[reisatsu] restaurant reset ({} blocks restored)", sink.changed);
    }

    // --- automatic doors --------------------------------------------------------------------------------------
    static BlockPos doorPos(String door) {
        return Layout.marker("inner".equals(door) ? "door_inner" : "door_outer").pos();
    }

    @Nullable
    static AutoDoorBlockEntity doorEntity(ServerLevel level, String door) {
        BlockPos p = doorPos(door);
        if (!loaded(level, p)) return null;
        ensureEntity(level, p);
        return level.getBlockEntity(p) instanceof AutoDoorBlockEntity be ? be : null;
    }

    /** "outer" or "inner" for a panel of either pair. */
    public static String autoDoorName(BlockPos pos) {
        BlockPos inner = Layout.marker("door_inner").pos();
        return pos.getZ() == inner.getZ() ? "inner" : "outer";
    }

    /** Opens the door pair now (motor + chime) and closes it after holdTicks unless something stands in the sensor. */
    public static void openAutoDoor(ServerLevel level, String door, int holdTicks) {
        if (!here(level)) return;
        AutoDoorBlockEntity be = doorEntity(level, door);
        if (be != null) be.forceOpen(holdTicks);
    }

    public static boolean isAutoDoorOpen(ServerLevel level, String door) {
        if (!here(level)) return false;
        BlockState s = level.getBlockState(doorPos(door));
        return s.hasProperty(Props.OPEN) && s.getValue(Props.OPEN);
    }

    /** The key switch (and the DOOR circuit): when off the doors stay shut and ignore the sensor. */
    public static void setAutoDoorsEnabled(ServerLevel level, boolean on) {
        if (!here(level)) return;
        WorldData d = data(level);
        boolean changed = d.keySwitch != on;
        d.keySwitch = on;
        d.setDirty();
        BlockPos sw = Layout.marker("door_switch").pos();
        setBool(level, sw, Props.ON, on);
        if (changed) sound(level, sw, ModSounds.DOOR_SWITCH, 0.9f, on ? 1.0f : 0.85f);
    }

    public static boolean autoDoorsEnabled(ServerLevel level) {
        if (!here(level)) return false;
        WorldData d = data(level);
        return d.keySwitch && d.on(Circuit.DOOR) && !d.doorsLocked;
    }

    /** Locked doors stay shut whatever happens (the visitor's fury); unlocking restores the previous mode. */
    public static void setAutoDoorsLocked(ServerLevel level, boolean locked) {
        if (!here(level)) return;
        WorldData d = data(level);
        d.doorsLocked = locked;
        d.setDirty();
        if (locked) {
            for (String door : new String[]{"outer", "inner"}) {
                AutoDoorBlockEntity be = doorEntity(level, door);
                if (be != null) be.forceClose(true);
            }
        }
    }

    public static boolean autoDoorsLocked(ServerLevel level) {
        return here(level) && data(level).doorsLocked;
    }

    // --- water ------------------------------------------------------------------------------------------------
    public static void setTap(ServerLevel level, BlockPos pos, boolean running) {
        if (!here(level) || !loaded(level, pos)) return;
        BlockState s = level.getBlockState(pos);
        if (!s.hasProperty(Props.RUNNING) || s.getValue(Props.RUNNING) == running) return;
        level.setBlock(pos, s.setValue(Props.RUNNING, running), 3);
        sound(level, pos, running ? ModSounds.FIXTURE_FAUCET_ON : ModSounds.FIXTURE_FAUCET_OFF, 0.9f, 1.0f);
    }

    public static boolean isTapRunning(ServerLevel level, BlockPos pos) {
        if (!here(level) || !loaded(level, pos)) return false;
        BlockState s = level.getBlockState(pos);
        return s.hasProperty(Props.RUNNING) && s.getValue(Props.RUNNING);
    }

    // --- tables -----------------------------------------------------------------------------------------------
    /** The table-top block that holds the menu stand / the water glasses of table n. */
    public static BlockPos menuSlot(int table) {
        if (table <= 7) return new BlockPos(Layout.boothX(table), 66, 29);
        if (table <= 11) return new BlockPos(39, 66, Layout.eastBoothZ(table));
        return new BlockPos(Layout.centreX(table), 66, Layout.centreZ(table));
    }

    /** The call button of table n rings (chime, red LED) and the call display shows n. */
    public static void ringCallButton(ServerLevel level, int table) {
        if (!here(level) || table < 1 || table > 17) return;
        BlockPos b = Layout.marker("button_" + table).pos();
        setBool(level, b, Props.LIT, true);
        sound(level, b, ModSounds.FIXTURE_CALL_BUTTON, 1.0f, 1.0f);
        setCallDisplay(level, table, false);
        sound(level, Layout.marker("call_display").pos(), ModSounds.FIXTURE_CALL_DISPLAY, 1.0f, 1.0f);
    }

    /** A player (or a spirit, player null) pressed a call button block. */
    public static void pressCallButton(ServerLevel level, BlockPos pos, @Nullable Player player) {
        if (!here(level)) return;
        int n = Layout.tableAt(pos);
        if (n == 0) {
            setBool(level, pos, Props.LIT, true);
            sound(level, pos, ModSounds.FIXTURE_CALL_BUTTON, 1.0f, 1.0f);
            return;
        }
        ringCallButton(level, n);
        FixtureEvents.LISTENER.noise(level, Vec3.atCenterOf(pos), 16.0f, "call_button", player);
        if (player instanceof ServerPlayer sp) FixtureEvents.LISTENER.interact(sp, "call_button_" + n, pos);
    }

    /** Free floor cells (feet level, air or a decal over a solid floor) around a table, nearest first. */
    static List<BlockPos> floorAround(ServerLevel level, int table, int radius) {
        BlockPos t = Layout.marker("table_" + table).pos();
        List<BlockPos> out = new ArrayList<>();
        for (int r = 1; r <= radius; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos p = t.offset(dx, 0, dz);
                    if (!loaded(level, p)) continue;
                    BlockState s = level.getBlockState(p);
                    if ((s.isAir() || s.is(ModBlocks.PUDDLE.get())) && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) out.add(p);
                }
        return out;
    }

    /** The menus of table n fly off and scatter (particles, sound, scattered decals around the table). */
    public static void scatterMenus(ServerLevel level, int table) {
        if (!here(level) || table < 1 || table > 17) return;
        BlockPos slot = menuSlot(table);
        if (level.getBlockState(slot).is(ModBlocks.MENU_STAND.get())) level.setBlock(slot, Blocks.AIR.defaultBlockState(), 3);
        List<BlockPos> floor = floorAround(level, table, 2);
        int n = Math.min(floor.size(), 3 + level.random.nextInt(2));
        for (int i = 0; i < n; i++) {
            BlockPos p = floor.get(level.random.nextInt(Math.min(floor.size(), 8)));
            if (!level.getBlockState(p).isAir() && !level.getBlockState(p).is(ModBlocks.PUDDLE.get())) continue;
            Direction d = Direction.from2DDataValue(level.random.nextInt(4));
            level.setBlock(p, ModBlocks.MENU_SCATTERED.get().defaultBlockState().setValue(FacingBlock.FACING, d)
                    .setValue(Props.VARIANT, level.random.nextInt(4)), 3);
        }
        Vec3 c = Vec3.atCenterOf(slot);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)), c.x, c.y, c.z, 14, 0.4, 0.3, 0.4, 0.15);
        sound(level, slot, ModSounds.FIXTURE_MENU_SCATTER, 1.0f, 1.0f);
    }

    /** A glass of ice water appears on table n (the waitress serves). The menus are taken away while it is served. */
    public static void placeWaterGlass(ServerLevel level, int table) {
        if (!here(level) || table < 1 || table > 17) return;
        BlockPos slot = menuSlot(table);
        if (!loaded(level, slot)) return;
        BlockState s = level.getBlockState(slot);
        if (s.is(ModBlocks.WATER_GLASS.get())) {
            int g = s.getValue(Props.GLASSES);
            if (g < 4) level.setBlock(slot, s.setValue(Props.GLASSES, g + 1), 3);
        } else {
            Direction face = table <= 7 ? Direction.NORTH : table <= 11 ? Direction.WEST : Direction.NORTH;
            level.setBlock(slot, ModBlocks.WATER_GLASS.get().defaultBlockState().setValue(FacingBlock.FACING, face).setValue(Props.GLASSES, 1), 3);
        }
        sound(level, slot, ModSounds.FIXTURE_GLASS_SET, 0.8f, 1.0f);
    }

    /** Removes glasses / scattered menus around table n and puts its menus back. */
    public static void clearTable(ServerLevel level, int table) {
        if (!here(level) || table < 1 || table > 17) return;
        BlockPos slot = menuSlot(table);
        if (!loaded(level, slot)) return;
        Direction face = table <= 7 ? Direction.NORTH : table <= 11 ? Direction.WEST : Direction.NORTH;
        level.setBlock(slot, ModBlocks.MENU_STAND.get().defaultBlockState().setValue(FacingBlock.FACING, face), 3);
        setBool(level, Layout.marker("button_" + table).pos(), Props.LIT, false);
        BlockPos t = Layout.marker("table_" + table).pos();
        for (BlockPos p : BlockPos.betweenClosed(t.offset(-3, 0, -3), t.offset(3, 0, 3))) {
            BlockState s = level.getBlockState(p);
            if (s.is(ModBlocks.MENU_SCATTERED.get()) || s.is(ModBlocks.BROKEN_PLATE.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** Where the pulled-out chair of table n stands (for the booths: a chair appears at the end of the table). */
    static BlockPos chairOut(int table) {
        if (table <= 7) return new BlockPos(Layout.boothX(table), 65, 26);
        if (table <= 11) return new BlockPos(36, 65, Layout.eastBoothZ(table));
        return new BlockPos(Layout.centreX(table), 65, Layout.centreZ(table) - 2);
    }

    /** The chair at table n is pulled out (or put back). Table 7: a chair stands at the aisle end of the booth. */
    public static void pullOutChair(ServerLevel level, int table, boolean out) {
        if (!here(level) || table < 1 || table > 17) return;
        BlockPos o = chairOut(table);
        if (!loaded(level, o)) return;
        if (table <= 11) {
            Direction face = table <= 7 ? Direction.NORTH : Direction.EAST;
            if (out && level.getBlockState(o).isAir()) {
                level.setBlock(o, ModBlocks.RESTAURANT_CHAIR.get().defaultBlockState().setValue(FacingBlock.FACING, face), 3);
                sound(level, o, ModSounds.FIXTURE_CHAIR_SCRAPE, 0.9f, 1.0f);
            } else if (!out && level.getBlockState(o).is(ModBlocks.RESTAURANT_CHAIR.get())) {
                level.setBlock(o, Blocks.AIR.defaultBlockState(), 3);
                sound(level, o, ModSounds.FIXTURE_CHAIR_SCRAPE, 0.7f, 1.1f);
            }
            return;
        }
        BlockPos home = o.south();
        BlockPos from = out ? home : o, to = out ? o : home;
        BlockState c = level.getBlockState(from);
        if (c.is(ModBlocks.RESTAURANT_CHAIR.get()) && level.getBlockState(to).isAir()) {
            level.setBlock(to, c.setValue(Props.FALLEN, false), 3);
            level.setBlock(from, Blocks.AIR.defaultBlockState(), 3);
            sound(level, to, ModSounds.FIXTURE_CHAIR_SCRAPE, 0.9f, 1.0f);
        }
    }

    /** Slides (fall = false) or knocks over (fall = true) the chair at pos. */
    public static void knockChair(ServerLevel level, BlockPos chair, boolean fall) {
        if (!here(level) || !loaded(level, chair)) return;
        BlockState s = level.getBlockState(chair);
        if (!(s.is(ModBlocks.RESTAURANT_CHAIR.get()) || s.is(ModBlocks.FOLDING_CHAIR.get()))) return;
        if (fall) {
            if (s.hasProperty(Props.FALLEN) && !s.getValue(Props.FALLEN)) level.setBlock(chair, s.setValue(Props.FALLEN, true), 3);
            sound(level, chair, ModSounds.FIXTURE_CHAIR_FALL, 1.0f, 1.0f);
        } else {
            List<Direction> dirs = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
            java.util.Collections.shuffle(dirs, new java.util.Random(level.getGameTime() ^ chair.asLong()));
            for (Direction d : dirs) if (slideChair(level, chair, d, null)) return;
            sound(level, chair, ModSounds.FIXTURE_CHAIR_SCRAPE, 0.8f, 1.2f);
        }
    }

    /** Moves the chair one block (scrape, noise 10 when an entity bumped it). Returns false if blocked. */
    public static boolean slideChair(ServerLevel level, BlockPos chair, Direction dir, @Nullable Entity bumper) {
        BlockState s = level.getBlockState(chair);
        BlockPos to = chair.relative(dir);
        boolean free = loaded(level, to) && (level.getBlockState(to).isAir() || level.getBlockState(to).is(ModBlocks.PUDDLE.get()))
                && level.getBlockState(to.below()).isFaceSturdy(level, to.below(), Direction.UP);
        if (bumper != null) FixtureEvents.LISTENER.noise(level, Vec3.atCenterOf(chair), 10.0f, "chair", bumper);
        sound(level, chair, ModSounds.FIXTURE_CHAIR_SCRAPE, 1.0f, 0.95f + level.random.nextFloat() * 0.1f);
        if (!free) return false;
        level.setBlock(to, s, 3);
        level.setBlock(chair, Blocks.AIR.defaultBlockState(), 3);
        return true;
    }

    public static List<BlockPos> chairsNear(ServerLevel level, BlockPos pos, int radius) {
        List<BlockPos> out = new ArrayList<>();
        if (!here(level)) return out;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-radius, -1, -radius), pos.offset(radius, 1, radius))) {
            if (!loaded(level, p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.is(ModBlocks.RESTAURANT_CHAIR.get()) || s.is(ModBlocks.FOLDING_CHAIR.get())) out.add(p.immutable());
        }
        return out;
    }

    // --- power and lights -------------------------------------------------------------------------------------
    public static void setCircuit(ServerLevel level, Circuit circuit, boolean on) {
        if (!here(level) || circuit == null) return;
        WorldData d = data(level);
        boolean changed = d.on(circuit) != on;
        d.circuits.put(circuit, on);
        d.setDirty();
        applyCircuit(level, circuit);
        if (changed) {
            BlockPos panel = Layout.marker("breaker").pos();
            sound(level, panel, on ? ModSounds.FIXTURE_BREAKER_ON : ModSounds.FIXTURE_BREAKER_OFF, 1.0f, 1.0f);
            FixtureEvents.LISTENER.circuitChanged(level, circuit, on);
        }
    }

    public static boolean circuitOn(ServerLevel level, Circuit circuit) {
        if (!here(level)) return circuit == null || circuit.startOn();
        return data(level).on(circuit);
    }

    /** The circuit of a light position (null: the night circuit, always on). */
    @Nullable
    public static Circuit circuitOfLight(BlockPos p) {
        if (RestaurantBuilder.NIGHT_LIGHTS.contains(p)) return null;
        if (!Plan.inside(p.getX(), p.getZ())) return Circuit.SIGN;
        Layout.Room r = Layout.roomAt(new BlockPos(p.getX(), Layout.FEET_Y, p.getZ()));
        return r == null ? Circuit.SIGN : Circuit.ofRoom(r.key());
    }

    /** Whether the light at p should be lit right now (circuit on, not burst). */
    public static boolean shouldBeLit(ServerLevel level, BlockPos p) {
        WorldData d = data(level);
        if (d.burst.contains(p)) return false;
        Circuit c = circuitOfLight(p);
        return c == null || d.on(c);
    }

    static List<BlockPos> lightsOfCircuit(Circuit c) {
        List<BlockPos> out = new ArrayList<>();
        if (c == Circuit.SIGN) {
            out.addAll(ExteriorBuilder.signLights());
            return out;
        }
        for (BlockPos p : RestaurantBuilder.allLights()) if (circuitOfLight(p) == c) out.add(p);
        return out;
    }

    /** Puts every block of a circuit into the state its switch says. */
    static void applyCircuit(ServerLevel level, Circuit c) {
        WorldData d = data(level);
        boolean on = d.on(c);
        switch (c) {
            case CAMERAS -> {
                for (Layout.Marker m : Layout.markers("cameras")) {
                    setBool(level, m.pos(), Props.ON, on);
                    if (!on) setBool(level, m.pos(), Props.SEEN, false);
                }
                for (Layout.Marker m : Layout.markers("monitors")) {
                    setBool(level, m.pos(), Props.ON, on);
                    if (!on) setBool(level, m.pos(), Props.ALERT, false);
                }
            }
            case DOOR -> {
                if (!on) {
                    for (String door : new String[]{"outer", "inner"}) {
                        AutoDoorBlockEntity be = doorEntity(level, door);
                        if (be != null && be.isOpen()) be.forceClose(true);
                    }
                }
            }
            default -> {
                for (BlockPos p : lightsOfCircuit(c)) setBool(level, p, Props.LIT, on && !d.burst.contains(p));
                if (c == Circuit.KITCHEN) {
                    // the heat lamps over the pass come on with the kitchen
                    for (int x = 22; x <= 26; x++) setBool(level, new BlockPos(x, 65, 10), Props.LIT, on);
                }
            }
        }
    }

    /** One light flickers for a while (ticks). */
    public static void flicker(ServerLevel level, BlockPos light, int ticks) {
        if (!here(level)) return;
        WorldTicker.flicker(level, light, ticks);
        if (level.random.nextInt(2) == 0) sound(level, light, ModSounds.FIXTURE_LIGHT_FLICKER, 0.8f, 1.0f);
    }

    /** Every light of a room (Layout room key) flickers for a while. */
    public static void flickerRoom(ServerLevel level, String roomKey, int ticks) {
        if (!here(level)) return;
        List<BlockPos> lights = RestaurantBuilder.lightsOf(roomKey);
        for (BlockPos p : lights) WorldTicker.flicker(level, p, ticks);
        if (!lights.isEmpty()) sound(level, lights.get(0), ModSounds.FIXTURE_LIGHT_FLICKER, 1.0f, 1.0f);
    }

    /** A tube bursts: pop, glass, the light is dead until reset. */
    public static void burstLight(ServerLevel level, BlockPos light) {
        if (!here(level) || !loaded(level, light)) return;
        WorldData d = data(level);
        d.burst.add(light.immutable());
        d.setDirty();
        setBool(level, light, Props.LIT, false);
        sound(level, light, ModSounds.FIXTURE_LIGHT_BURST, 1.0f, 1.0f);
        Vec3 c = Vec3.atBottomCenterOf(light);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), c.x, c.y - 0.1, c.z, 24, 0.3, 0.05, 0.3, 0.05);
    }

    /** Closing time: every circuit on, every light lit. */
    public static void allLightsOn(ServerLevel level) {
        if (!here(level)) return;
        WorldData d = data(level);
        d.burst.clear();
        for (Circuit c : Circuit.values()) {
            boolean was = d.on(c);
            d.circuits.put(c, true);
            applyCircuit(level, c);
            if (!was) FixtureEvents.LISTENER.circuitChanged(level, c, true);
        }
        d.setDirty();
        for (BlockPos p : RestaurantBuilder.NIGHT_LIGHTS) setBool(level, p, Props.LIT, true);
        sound(level, Layout.marker("breaker").pos(), ModSounds.FIXTURE_BREAKER_ON, 1.0f, 0.9f);
    }

    /** Lights (ceiling lights, downlights, pendants) of a room, for the director. */
    public static List<BlockPos> lightsIn(ServerLevel level, String roomKey) {
        return RestaurantBuilder.lightsOf(roomKey);
    }

    // --- devices ----------------------------------------------------------------------------------------------
    static List<BlockPos> devicePositions(String key) {
        List<BlockPos> out = new ArrayList<>();
        switch (key) {
            case "tv" -> out.add(Layout.marker("tv").pos());
            case "drink_bar" -> Layout.markers("drink_bar").forEach(m -> out.add(m.pos()));
            case "hand_dryer_w" -> out.add(Layout.marker("hand_dryer_w").pos());
            case "hand_dryer_m" -> out.add(Layout.marker("hand_dryer_m").pos());
            case "dishwasher" -> out.add(Layout.marker("dishwasher").pos());
            case "fryer" -> Layout.markers("fryer").forEach(m -> out.add(m.pos()));
            case "gas_range" -> Layout.markers("gas_range").forEach(m -> out.add(m.pos()));
            case "pass_lamp" -> {
                for (int x = 22; x <= 26; x++) out.add(new BlockPos(x, 65, 10));
            }
            case "phone" -> out.add(Layout.marker("phone").pos());
            case "monitors" -> Layout.markers("monitors").forEach(m -> out.add(m.pos()));
            case "register_drawer" -> out.add(Layout.marker("register").pos());
            default -> {
            }
        }
        return out;
    }

    static BooleanProperty deviceProp(String key) {
        return switch (key) {
            case "tv", "fryer", "monitors" -> Props.ON;
            case "drink_bar" -> Props.POURING;
            case "hand_dryer_w", "hand_dryer_m", "dishwasher" -> Props.RUNNING;
            case "gas_range", "pass_lamp" -> Props.LIT;
            case "phone" -> Props.RINGING;
            case "register_drawer" -> Props.OPEN;
            default -> null;
        };
    }

    /** Devices by key: "tv", "drink_bar", "hand_dryer_w", "hand_dryer_m", "dishwasher", "fryer", "gas_range",
     *  "pass_lamp", "phone" (ringing), "monitors", "store_music" (the store speakers), "register_drawer". */
    public static void setDevice(ServerLevel level, String key, boolean on) {
        if (!here(level)) return;
        if ("store_music".equals(key)) {
            data(level).storeMusic = on;
            data(level).setDirty();
            return;
        }
        BooleanProperty prop = deviceProp(key);
        if (prop == null) return;
        List<BlockPos> list = devicePositions(key);
        boolean was = deviceOn(level, key);
        for (BlockPos p : list) setBool(level, p, prop, on);
        if (on && !was && !list.isEmpty()) {
            BlockPos p = list.get(0);
            switch (key) {
                case "gas_range" -> sound(level, p, ModSounds.FIXTURE_GAS_IGNITE, 1.0f, 1.0f);
                case "drink_bar" -> {
                    sound(level, p, ModSounds.FIXTURE_DRINK_POUR, 1.0f, 1.0f);
                    for (BlockPos q : list) WorldTicker.timer(level, q, Props.POURING, 70);
                }
                case "hand_dryer_w", "hand_dryer_m" -> WorldTicker.timer(level, p, Props.RUNNING, 100);
                case "register_drawer" -> sound(level, p, ModSounds.FIXTURE_REGISTER_DRAWER, 1.0f, 1.0f);
                case "tv" -> sound(level, p, ModSounds.FIXTURE_TV_STATIC, 0.6f, 1.0f);
                default -> {
                }
            }
        }
    }

    public static boolean deviceOn(ServerLevel level, String key) {
        if (!here(level)) return false;
        if ("store_music".equals(key)) return data(level).storeMusic;
        BooleanProperty prop = deviceProp(key);
        if (prop == null) return false;
        for (BlockPos p : devicePositions(key)) {
            if (!loaded(level, p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.hasProperty(prop) && s.getValue(prop)) return true;
        }
        return false;
    }

    /** The drink bar at pos pours for a while (a player pressed it, or the director). */
    public static void pour(ServerLevel level, BlockPos pos, int ticks) {
        setBool(level, pos, Props.POURING, true);
        sound(level, pos, ModSounds.FIXTURE_DRINK_POUR, 1.0f, 1.0f);
        WorldTicker.timer(level, pos, Props.POURING, ticks);
    }

    /** A hand dryer roars for five seconds. */
    public static void runHandDryer(ServerLevel level, BlockPos pos) {
        setBool(level, pos, Props.RUNNING, true);
        WorldTicker.timer(level, pos, Props.RUNNING, 100);
    }

    /** A locker door swings open and shut (someone stepped in or out). */
    public static void flapLocker(ServerLevel level, BlockPos lower) {
        setBool(level, lower, Props.OPEN, true);
        setBool(level, lower.above(), Props.OPEN, true);
        WorldTicker.timer(level, lower, Props.OPEN, 8);
        WorldTicker.timer(level, lower.above(), Props.OPEN, 8);
    }

    // --- misc -------------------------------------------------------------------------------------------------
    /** Call display: number 0 = blank; closed = shows 「閉店」. */
    public static void setCallDisplay(ServerLevel level, int number, boolean closed) {
        if (!here(level)) return;
        BlockPos p = Layout.marker("call_display").pos();
        if (!loaded(level, p)) return;
        BlockState s = level.getBlockState(p);
        if (!s.hasProperty(Props.NUMBER)) return;
        BlockState n = s.setValue(Props.NUMBER, Math.max(0, Math.min(17, number))).setValue(Props.CLOSED, closed);
        if (n != s) level.setBlock(p, n, 3);
    }

    /** A security camera's red LED (it sees you). */
    public static void setCameraSeen(ServerLevel level, BlockPos camera, boolean seen) {
        if (!here(level)) return;
        BlockState s = level.getBlockState(camera);
        if (seen && s.hasProperty(Props.ON) && !s.getValue(Props.ON)) return;
        setBool(level, camera, Props.SEEN, seen);
    }

    /** The office monitors' red REC frame. */
    public static void setMonitorsAlert(ServerLevel level, boolean alert) {
        if (!here(level)) return;
        for (Layout.Marker m : Layout.markers("monitors")) setBool(level, m.pos(), Props.ALERT, alert);
    }

    public static void crackMirror(ServerLevel level, boolean cracked) {
        if (!here(level)) return;
        for (Layout.Marker m : Layout.markers("mirror")) setBool(level, m.pos(), Props.CRACKED, cracked);
        if (cracked) {
            BlockPos p = Layout.marker("mirror").pos();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), p.getX() + 1.5, p.getY() + 1.0,
                    p.getZ() + 1.0, 30, 0.8, 0.5, 0.1, 0.05);
        }
    }

    /** Hand prints appear on the mirror for a while (wet smears, water running down the glass). */
    public static void handPrints(ServerLevel level) {
        if (!here(level)) return;
        WorldTicker.handPrints(level, 240);
    }

    /** A wet footprint (fades over ~2 min). */
    public static void wetFootprint(ServerLevel level, Vec3 pos, float yaw) {
        if (!here(level)) return;
        BlockPos p = BlockPos.containing(pos.x, pos.y + 0.05, pos.z);
        if (!loaded(level, p)) return;
        BlockState s = level.getBlockState(p);
        if (!s.isAir()) return;
        if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) return;
        level.setBlock(p, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        WorldTicker.footprint(level, p, 2400);
    }

    /** The stuffed bear in the kids' corner turns. */
    public static void turnBear(ServerLevel level, Direction facing) {
        if (!here(level) || facing.getAxis().isVertical()) return;
        BlockPos p = Layout.marker("stuffed_bear").pos();
        BlockState s = level.getBlockState(p);
        if (s.is(ModBlocks.STUFFED_BEAR.get()) && s.getValue(FacingBlock.FACING) != facing) level.setBlock(p, s.setValue(FacingBlock.FACING, facing), 3);
    }

    /** The A-frame board outside: 0 「本日のおすすめ」, 1 「まだ　いますか」. */
    public static void setMenuBoard(ServerLevel level, int message) {
        if (!here(level)) return;
        BlockPos p = Layout.marker("menu_board").pos();
        if (!loaded(level, p)) return;
        BlockState s = level.getBlockState(p);
        if (s.hasProperty(Props.MESSAGE)) level.setBlock(p, s.setValue(Props.MESSAGE, Math.max(0, Math.min(1, message))), 3);
    }

    /** A plate smashes here (sound, shards decal). */
    public static void smashPlate(ServerLevel level, BlockPos pos) {
        if (!here(level) || !loaded(level, pos)) return;
        BlockPos p = pos;
        for (int i = 0; i < 3 && !level.getBlockState(p).isAir(); i++) p = p.above();
        for (int i = 0; i < 3 && level.getBlockState(p.below()).isAir(); i++) p = p.below();
        if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
            level.setBlock(p, ModBlocks.BROKEN_PLATE.get().defaultBlockState().setValue(FacingBlock.FACING,
                    Direction.from2DDataValue(level.random.nextInt(4))), 3);
        }
        sound(level, p, ModSounds.FIXTURE_PLATE_BREAK, 1.2f, 0.95f + level.random.nextFloat() * 0.1f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WHITE_CONCRETE.defaultBlockState()), p.getX() + 0.5, p.getY() + 0.2,
                p.getZ() + 0.5, 16, 0.3, 0.1, 0.3, 0.1);
    }

    /** The third stall of the women's restroom: red 使用中 and locked. */
    public static void setStallOccupied(ServerLevel level, boolean occupied) {
        if (!here(level) || !loaded(level, STALL_3)) return;
        RestaurantDoorBlock.Stall.setOccupied(level, STALL_3, occupied);
    }

    /** Store PA / speakers: true = the store music plays from the ceiling speakers (the game picks the track). */
    public static List<BlockPos> speakers(ServerLevel level) {
        return List.of(new BlockPos(21, 69, 20), new BlockPos(31, 69, 20), new BlockPos(25, 69, 13), new BlockPos(36, 69, 24),
                new BlockPos(8, 69, 24), new BlockPos(4, 69, 28), new BlockPos(27, 69, 5));
    }

    // --- helpers for the world's own blocks ---------------------------------------------------------------------
    /** The note id of a note-holding block at pos (note_paper, the reservation book, notice and bulletin boards), or null. */
    @Nullable
    public static String noteAt(BlockPos pos) {
        for (var e : Layout.all().entrySet()) {
            if (!e.getKey().startsWith("note_")) continue;
            for (Layout.Marker m : e.getValue()) if (m.pos().equals(pos)) return e.getKey().substring(5);
        }
        return null;
    }

    /** True if the swing door leaf at pos (either half) is swung open. */
    public static boolean swingDoorOpen(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof SwingDoorBlock && s.getValue(Props.SWING) != 0;
    }

    static boolean isSwingEntity(BlockEntity be) {
        return be instanceof SwingDoorBlockEntity;
    }

    /** Lower block of a two-block thing. */
    static BlockPos lower(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return pos.below();
        return pos;
    }

    static boolean is(BlockState s, Block b) {
        return s.is(b);
    }
}
