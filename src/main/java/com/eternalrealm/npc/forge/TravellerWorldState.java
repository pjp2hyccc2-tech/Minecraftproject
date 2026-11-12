package com.eternalrealm.npc.forge;

import com.eternalrealm.npc.Travellers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where each travelling adventurer is (saved with the realm's dimension as data/eternalrealm_travellers.dat), so
 * they go on from where they were when the world is opened again. Server thread only.
 */
public final class TravellerWorldState extends SavedData {
    private static final String NAME = "eternalrealm_travellers";

    private final Map<Travellers.Traveller, Travellers.Plan> plans = new EnumMap<>(Travellers.Traveller.class);

    public TravellerWorldState() {
    }

    public static TravellerWorldState get(ServerLevel realm) {
        return realm.getDataStorage().computeIfAbsent(TravellerWorldState::load, TravellerWorldState::new, NAME);
    }

    static TravellerWorldState load(CompoundTag tag) {
        TravellerWorldState state = new TravellerWorldState();
        for (Travellers.Traveller traveller : Travellers.Traveller.values()) {
            if (!tag.contains(traveller.id())) {
                continue;
            }
            CompoundTag plan = tag.getCompound(traveller.id());
            try {
                Travellers.Place place = Travellers.Place.valueOf(plan.getString("place"));
                state.plans.put(traveller, new Travellers.Plan(place, plan.getString("habitat"),
                        plan.getLong("until")));
            } catch (IllegalArgumentException e) {
                // an unreadable plan: a new one is made
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        plans.forEach((traveller, plan) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("place", plan.place().name());
            entry.putString("habitat", plan.habitat());
            entry.putLong("until", plan.until());
            tag.put(traveller.id(), entry);
        });
        return tag;
    }

    public Optional<Travellers.Plan> plan(Travellers.Traveller traveller) {
        return Optional.ofNullable(plans.get(traveller));
    }

    public void set(Travellers.Traveller traveller, Travellers.Plan plan) {
        plans.put(traveller, plan);
        setDirty();
    }
}
