package com.eternalrealm.estate.forge;

import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstatePlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What stands in the estates dimension, saved with its blocks ({@code data/eternalrealm_estates.dat} of the estates
 * level, written in the same world save as the chunks): which plots carry a finished build (kind, owner, design and
 * the blueprint's hash), and the keepsafe — things that had to leave an estate without a place to go (an estate
 * released with things still inside, a redesign into fewer containers), kept per owner until claimed. Server thread.
 */
public final class EstateWorldState extends SavedData {
    static final String NAME = "eternalrealm_estates";

    /**
     * A finished build on a plot.
     *
     * @param box   the world box the build occupies: min x, y, z, max x, y, z (inclusive)
     * @param entry where players arrive (world feet position x, y, z and yaw), from the blueprint it was built with
     */
    public record Built(String kind, UUID owner, String design, String build, int[] box, double[] entry) {
        public EstateKey key() {
            return new EstateKey(kind, owner, design);
        }

        public AABB aabb() {
            return new AABB(box[0], box[1], box[2], box[3] + 1, box[4] + 1, box[5] + 1);
        }
    }

    private final Map<EstatePlot, Built> built = new HashMap<>();
    private final Map<UUID, List<CompoundTag>> keepsafe = new LinkedHashMap<>();

    public EstateWorldState() {
    }

    public static EstateWorldState get(ServerLevel estates) {
        return estates.getDataStorage().computeIfAbsent(EstateWorldState::load, EstateWorldState::new, NAME);
    }

    static EstateWorldState load(CompoundTag tag) {
        EstateWorldState state = new EstateWorldState();
        for (Tag element : tag.getList("built", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            try {
                EstatePlot plot = new EstatePlot(entry.getInt("region"), entry.getInt("index"));
                int[] box = entry.getIntArray("box");
                if (box.length != 6) {
                    throw new IllegalArgumentException("box " + java.util.Arrays.toString(box));
                }
                ListTag at = entry.getList("entry", Tag.TAG_DOUBLE);
                if (at.size() != 4) {
                    throw new IllegalArgumentException("entry " + at);
                }
                double[] arrival = {at.getDouble(0), at.getDouble(1), at.getDouble(2), at.getDouble(3)};
                Built value = new Built(entry.getString("kind"), entry.getUUID("owner"), entry.getString("design"),
                        entry.getString("build"), box, arrival);
                value.key(); // validates the names
                state.built.put(plot, value);
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().error("Skipping a broken estate build record {}", entry, e);
            }
        }
        for (Tag element : tag.getList("keepsafe", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            if (!entry.hasUUID("owner")) {
                continue;
            }
            List<CompoundTag> items = new ArrayList<>();
            for (Tag item : entry.getList("items", Tag.TAG_COMPOUND)) {
                items.add(((CompoundTag) item).copy());
            }
            if (!items.isEmpty()) {
                state.keepsafe.put(entry.getUUID("owner"), items);
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag builds = new ListTag();
        for (Map.Entry<EstatePlot, Built> entry : built.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putInt("region", entry.getKey().region());
            value.putInt("index", entry.getKey().index());
            value.putString("kind", entry.getValue().kind());
            value.putUUID("owner", entry.getValue().owner());
            value.putString("design", entry.getValue().design());
            value.putString("build", entry.getValue().build());
            value.putIntArray("box", entry.getValue().box().clone());
            ListTag at = new ListTag();
            for (double v : entry.getValue().entry()) {
                at.add(net.minecraft.nbt.DoubleTag.valueOf(v));
            }
            value.put("entry", at);
            builds.add(value);
        }
        tag.put("built", builds);
        ListTag kept = new ListTag();
        for (Map.Entry<UUID, List<CompoundTag>> entry : keepsafe.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("owner", entry.getKey());
            ListTag items = new ListTag();
            for (CompoundTag item : entry.getValue()) {
                items.add(item.copy());
            }
            value.put("items", items);
            kept.add(value);
        }
        tag.put("keepsafe", kept);
        return tag;
    }

    // ------------------------------------------------------------------ builds

    public Optional<Built> built(EstatePlot plot) {
        return Optional.ofNullable(built.get(plot));
    }

    public Map<EstatePlot, Built> builtPlots() {
        return Collections.unmodifiableMap(new HashMap<>(built));
    }

    void markBuilt(EstatePlot plot, EstateKey key, String build, AABB box, double[] entry) {
        int[] bounds = {(int) Math.floor(box.minX), (int) Math.floor(box.minY), (int) Math.floor(box.minZ),
                (int) Math.ceil(box.maxX) - 1, (int) Math.ceil(box.maxY) - 1, (int) Math.ceil(box.maxZ) - 1};
        built.put(plot, new Built(key.kind(), key.owner(), key.design(), build, bounds, entry.clone()));
        setDirty();
    }

    void markWiped(EstatePlot plot) {
        if (built.remove(plot) != null) {
            setDirty();
        }
    }

    // ------------------------------------------------------------------ the keepsafe

    /** Copies of what is kept for {@code owner}, oldest first. */
    public List<ItemStack> keepsafe(UUID owner) {
        List<ItemStack> stacks = new ArrayList<>();
        for (CompoundTag tag : keepsafe.getOrDefault(owner, List.of())) {
            ItemStack stack = ItemStack.of(tag);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    /** Owners with something in the keepsafe. */
    public List<UUID> keepsafeOwners() {
        return List.copyOf(keepsafe.keySet());
    }

    void keep(UUID owner, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        keepsafe.computeIfAbsent(owner, id -> new ArrayList<>()).add(stack.save(new CompoundTag()));
        setDirty();
    }

    /** Replaces what is kept for {@code owner} (after a claim took some of it). */
    void setKeepsafe(UUID owner, List<ItemStack> stacks) {
        List<CompoundTag> tags = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                tags.add(stack.save(new CompoundTag()));
            }
        }
        if (tags.isEmpty()) {
            keepsafe.remove(owner);
        } else {
            keepsafe.put(owner, tags);
        }
        setDirty();
    }
}
