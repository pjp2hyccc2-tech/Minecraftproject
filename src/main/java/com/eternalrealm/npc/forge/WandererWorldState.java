package com.eternalrealm.npc.forge;

import com.eternalrealm.npc.Wanderers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where each wandering adventurer is (saved with the realm's dimension as data/eternalrealm_wanderers.dat), so they go
 * on from where they were when the world is opened again. Server thread only.
 */
public final class WandererWorldState extends SavedData {
    private static final String NAME = "eternalrealm_wanderers";

    private final Map<Wanderers.Wanderer, Wanderers.Plan> plans = new EnumMap<>(Wanderers.Wanderer.class);

    public WandererWorldState() {
    }

    public static WandererWorldState get(ServerLevel realm) {
        return realm.getDataStorage().computeIfAbsent(WandererWorldState::load, WandererWorldState::new, NAME);
    }

    static WandererWorldState load(CompoundTag tag) {
        WandererWorldState state = new WandererWorldState();
        for (Wanderers.Wanderer wanderer : Wanderers.Wanderer.values()) {
            if (!tag.contains(wanderer.id())) {
                continue;
            }
            CompoundTag plan = tag.getCompound(wanderer.id());
            try {
                Wanderers.Place place = Wanderers.Place.valueOf(plan.getString("place"));
                state.plans.put(wanderer, new Wanderers.Plan(place, plan.getString("habitat"), plan.getLong("until")));
            } catch (IllegalArgumentException e) {
                // an unreadable plan: a new one is made
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        plans.forEach((wanderer, plan) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("place", plan.place().name());
            entry.putString("habitat", plan.habitat());
            entry.putLong("until", plan.until());
            tag.put(wanderer.id(), entry);
        });
        return tag;
    }

    public Optional<Wanderers.Plan> plan(Wanderers.Wanderer wanderer) {
        return Optional.ofNullable(plans.get(wanderer));
    }

    public void set(Wanderers.Wanderer wanderer, Wanderers.Plan plan) {
        plans.put(wanderer, plan);
        setDirty();
    }
}
