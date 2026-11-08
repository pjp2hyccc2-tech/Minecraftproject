package com.eternalrealm.boss.forge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Temporary scenery of boss attacks as {@code minecraft:block_display} entities: spikes and pillars bursting out of the
 * floor, meteors falling onto their marker, orbs and bombs waiting for their explosion. They never touch the world's
 * blocks, are animated through the display's interpolated transformation (as {@code /data merge} does) and are removed
 * when their time is up or the fight ends. Props found when a chunk loads are leftovers of a stopped server and are
 * dropped ({@link #onEntityJoin}).
 */
public final class BossProps {
    /** Tag on every prop entity. */
    public static final String TAG = "eternalrealm_boss_prop";
    /** Props alive at once per fight; further spawns are skipped (particles still show the attack). */
    private static final int MAX_PROPS = 200;
    private static final Map<String, BlockState> STATES = new HashMap<>();

    private record Step(long tick, CompoundTag transformation, int duration) {
    }

    private static final class Prop {
        final Entity entity;
        final String owner;
        final List<Step> steps = new ArrayList<>();
        final long removeAt;

        Prop(Entity entity, String owner, long removeAt) {
            this.entity = entity;
            this.owner = owner;
            this.removeAt = removeAt;
        }
    }

    private final ServerLevel level;
    private final List<Prop> props = new ArrayList<>();

    BossProps(ServerLevel level) {
        this.level = level;
    }

    // ------------------------------------------------------------------ kinds of props

    /** Bursts out of the floor at (x, y, z) in 3 ticks, stays {@code hold} ticks and sinks back. */
    void spike(String owner, BlockState state, double x, double y, double z, float width, float height, long now,
               int hold, boolean bright) {
        Prop prop = spawn(owner, state, x, y, z, bright, box(width, 0.05F, 0.0F, width), now + hold + 12);
        if (prop != null) {
            prop.steps.add(new Step(now + 1, box(width, height, 0.0F, width), 3));
            prop.steps.add(new Step(now + 4 + hold, box(width, 0.05F, -0.1F, width), 6));
        }
    }

    /** Hangs {@code height} blocks above (x, y, z) and falls in its last ticks to land at {@code impactTick}. */
    void meteor(String owner, BlockState state, double x, double y, double z, float size, long now, long impactTick) {
        float height = 22.0F;
        Prop prop = spawn(owner, state, x, y, z, true, box(size, size, height, size), impactTick + 1);
        if (prop != null) {
            int fall = (int) Math.max(4, Math.min(14, impactTick - now - 1));
            prop.steps.add(new Step(impactTick - fall, box(size, size, -size * 0.3F, size), fall));
        }
    }

    /** Hovers over (x, y, z) from now until {@code until}, growing in. */
    void orb(String owner, BlockState state, double x, double y, double z, float size, long now, long until) {
        Prop prop = spawn(owner, state, x, y, z, true, box(0.1F, 0.1F, 1.2F, 0.1F), until);
        if (prop != null) {
            prop.steps.add(new Step(now + 1, box(size, size, 1.2F - size * 0.5F + 0.05F, size), 6));
        }
    }

    /** Sits on the floor at (x, y, z) until {@code until}, swelling just before. */
    void bomb(String owner, BlockState state, double x, double y, double z, float size, long now, long until) {
        Prop prop = spawn(owner, state, x, y, z, false, box(size, size, 0.0F, size), until);
        if (prop != null && until - 6 > now) {
            float big = size * 1.25F;
            prop.steps.add(new Step(until - 6, box(big, big, 0.0F, big), 5));
        }
    }

    /** Removes the props of one owner (an ability that was called off). */
    void remove(String owner) {
        Iterator<Prop> iterator = props.iterator();
        while (iterator.hasNext()) {
            Prop prop = iterator.next();
            if (prop.owner.equals(owner)) {
                prop.entity.discard();
                iterator.remove();
            }
        }
    }

    /** Plays the scheduled animation steps and removes expired props. */
    void tick(long now) {
        Iterator<Prop> iterator = props.iterator();
        while (iterator.hasNext()) {
            Prop prop = iterator.next();
            if (prop.entity.isRemoved() || now >= prop.removeAt) {
                prop.entity.discard();
                iterator.remove();
                continue;
            }
            Iterator<Step> steps = prop.steps.iterator();
            while (steps.hasNext()) {
                Step step = steps.next();
                if (now >= step.tick()) {
                    animate(prop.entity, step.transformation(), step.duration());
                    steps.remove();
                }
            }
        }
    }

    /** Removes every prop (the fight ended). */
    void clear() {
        for (Prop prop : props) {
            prop.entity.discard();
        }
        props.clear();
    }

    int count() {
        return props.size();
    }

    // ------------------------------------------------------------------ display entities

    private Prop spawn(String owner, BlockState state, double x, double y, double z, boolean bright,
                       CompoundTag transformation, long removeAt) {
        if (props.size() >= MAX_PROPS) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        tag.put("block_state", NbtUtils.writeBlockState(state));
        tag.put("transformation", transformation);
        tag.putInt("interpolation_duration", 0);
        if (bright) {
            CompoundTag brightness = new CompoundTag();
            brightness.putInt("sky", 15);
            brightness.putInt("block", 15);
            tag.put("brightness", brightness);
        }
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG));
        tag.put("Tags", tags);
        Entity entity = EntityType.loadEntityRecursive(tag, level, created -> {
            created.moveTo(x, y, z, 0.0F, 0.0F);
            return created;
        });
        if (entity == null || !level.addFreshEntity(entity)) {
            return null;
        }
        Prop prop = new Prop(entity, owner, removeAt);
        props.add(prop);
        return prop;
    }

    /** Changes the display's transformation, interpolated by the clients over {@code duration} ticks. */
    private static void animate(Entity entity, CompoundTag transformation, int duration) {
        if (entity.isRemoved()) {
            return;
        }
        UUID id = entity.getUUID();
        CompoundTag data = entity.saveWithoutId(new CompoundTag());
        data.put("transformation", transformation);
        data.putInt("interpolation_duration", duration);
        data.putInt("start_interpolation", 0);
        entity.load(data);
        entity.setUUID(id);
    }

    /** A block model {@code width} wide, {@code height} high, raised by {@code lift}, centred on the entity. */
    private static CompoundTag box(float width, float height, float lift, float depth) {
        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floats(-width / 2.0F, lift, -depth / 2.0F));
        transformation.put("left_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        transformation.put("scale", floats(width, height, depth));
        transformation.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        return transformation;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }

    // ------------------------------------------------------------------ block states

    /** Parses {@code namespace:block[property=value,...]} (see AbilityLook); stone if unknown. */
    static BlockState state(String spec) {
        return STATES.computeIfAbsent(spec, BossProps::parse);
    }

    private static BlockState parse(String spec) {
        String id = spec;
        String properties = "";
        int bracket = spec.indexOf('[');
        if (bracket >= 0 && spec.endsWith("]")) {
            id = spec.substring(0, bracket);
            properties = spec.substring(bracket + 1, spec.length() - 1);
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        Block block = location == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(location);
        BlockState state = block.defaultBlockState();
        if (!properties.isEmpty()) {
            for (String pair : properties.split(",")) {
                String[] parts = pair.split("=", 2);
                Property<?> property = parts.length == 2 ? block.getStateDefinition().getProperty(parts[0]) : null;
                if (property != null) {
                    state = with(state, property, parts[1]);
                }
            }
        }
        return state.isAir() ? Blocks.STONE.defaultBlockState() : state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.isPresent() ? state.setValue(property, parsed.get()) : state;
    }

    // ------------------------------------------------------------------ leftovers

    /** Props saved with a chunk belong to a fight that no longer exists: they never come back. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk()
                && event.getEntity().getTags().contains(TAG)) {
            event.setCanceled(true);
        }
    }
}
