package com.eternalrealm.world.forge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * What has been built in アストリア (saved with the dimension as data/eternalrealm_astoria.dat): the capital and its
 * floor height, and every dungeon entrance with its floor height. Server thread only.
 */
public final class AstoriaWorldState extends SavedData {
    private static final String NAME = "eternalrealm_astoria";

    private boolean hubBuilt;
    private int hubGroundY;
    /** Floor height chosen for the capital before it is built (Integer.MIN_VALUE = not chosen yet). */
    private int plannedHubY = Integer.MIN_VALUE;
    private final Map<String, Integer> landmarks = new TreeMap<>();
    /** Minor waystones built so far: id -> floor height. */
    private final Map<String, Integer> minorWaystones = new TreeMap<>();
    /** Content hash of the blueprint capital that is fully built ("" = none yet). */
    private String capitalBuild = "";

    public AstoriaWorldState() {
    }

    public static AstoriaWorldState get(ServerLevel astoria) {
        return astoria.getDataStorage().computeIfAbsent(AstoriaWorldState::load, AstoriaWorldState::new, NAME);
    }

    static AstoriaWorldState load(CompoundTag tag) {
        AstoriaWorldState state = new AstoriaWorldState();
        state.hubBuilt = tag.getBoolean("hubBuilt");
        state.hubGroundY = tag.getInt("hubGroundY");
        state.plannedHubY = tag.contains("plannedHubY") ? tag.getInt("plannedHubY") : Integer.MIN_VALUE;
        CompoundTag built = tag.getCompound("landmarks");
        for (String id : built.getAllKeys()) {
            state.landmarks.put(id, built.getInt(id));
        }
        state.capitalBuild = tag.getString("capitalBuild");
        CompoundTag stones = tag.getCompound("minorWaystones");
        for (String id : stones.getAllKeys()) {
            state.minorWaystones.put(id, stones.getInt(id));
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("hubBuilt", hubBuilt);
        tag.putInt("hubGroundY", hubGroundY);
        if (plannedHubY != Integer.MIN_VALUE) {
            tag.putInt("plannedHubY", plannedHubY);
        }
        CompoundTag built = new CompoundTag();
        landmarks.forEach(built::putInt);
        tag.put("landmarks", built);
        tag.putString("capitalBuild", capitalBuild);
        CompoundTag stones = new CompoundTag();
        minorWaystones.forEach(stones::putInt);
        tag.put("minorWaystones", stones);
        return tag;
    }

    public boolean hubBuilt() {
        return hubBuilt;
    }

    public OptionalInt hubGroundY() {
        return hubBuilt ? OptionalInt.of(hubGroundY) : OptionalInt.empty();
    }

    public OptionalInt plannedHubGroundY() {
        return plannedHubY == Integer.MIN_VALUE ? OptionalInt.empty() : OptionalInt.of(plannedHubY);
    }

    void planHub(int groundY) {
        plannedHubY = groundY;
        setDirty();
    }

    void markHubBuilt(int groundY) {
        hubBuilt = true;
        hubGroundY = groundY;
        setDirty();
    }

    /** Content hash of the blueprint capital standing in this world ("" if none). */
    public String capitalBuild() {
        return capitalBuild;
    }

    /** Forget the built blueprint so the capital is built again (operator command). */
    public void resetCapitalBuild() {
        capitalBuild = "";
        setDirty();
    }

    public void markCapitalBuilt(String build, int groundY) {
        capitalBuild = build;
        hubBuilt = true;
        hubGroundY = groundY;
        setDirty();
    }

    public boolean landmarkBuilt(String dungeonId) {
        return landmarks.containsKey(dungeonId);
    }

    public OptionalInt landmarkGroundY(String dungeonId) {
        Integer y = landmarks.get(dungeonId);
        return y == null ? OptionalInt.empty() : OptionalInt.of(y);
    }

    void markLandmarkBuilt(String dungeonId, int groundY) {
        landmarks.put(dungeonId, groundY);
        setDirty();
    }

    public boolean minorWaystoneBuilt(String id) {
        return minorWaystones.containsKey(id);
    }

    void markMinorWaystoneBuilt(String id, int floorY) {
        minorWaystones.put(id, floorY);
        setDirty();
    }
}
