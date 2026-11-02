package com.meikon.game;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/** World-wide game progress (stored with the overworld). The mansion layout itself is a pure function of the seed. */
public final class GameData extends SavedData {
    private static final String NAME = "meikon_game";

    public boolean active;
    public boolean built;
    public long seed;
    public int bells;
    public int deaths;
    public int candleStep;
    public boolean kuraUnlocked;
    public boolean dollSolved;
    public boolean wellSolved;
    public boolean butsumaSolved;
    public int wellPulls;
    public int endings;
    public final Set<Integer> foundNotes = new HashSet<>();
    public final Set<Long> takenBells = new HashSet<>();
    public final Set<Long> searched = new HashSet<>();
    public final Set<Long> lettersTaken = new HashSet<>();

    public static GameData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(GameData::load, GameData::new, NAME);
    }

    public void resetProgress(long newSeed) {
        seed = newSeed;
        bells = 0;
        deaths = 0;
        candleStep = 0;
        kuraUnlocked = false;
        dollSolved = false;
        wellSolved = false;
        butsumaSolved = false;
        wellPulls = 0;
        foundNotes.clear();
        takenBells.clear();
        searched.clear();
        lettersTaken.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Active", active);
        tag.putBoolean("Built", built);
        tag.putLong("Seed", seed);
        tag.putInt("Bells", bells);
        tag.putInt("Deaths", deaths);
        tag.putInt("CandleStep", candleStep);
        tag.putBoolean("KuraUnlocked", kuraUnlocked);
        tag.putBoolean("DollSolved", dollSolved);
        tag.putBoolean("WellSolved", wellSolved);
        tag.putBoolean("ButsumaSolved", butsumaSolved);
        tag.putInt("WellPulls", wellPulls);
        tag.putInt("Endings", endings);
        tag.put("Notes", new IntArrayTag(foundNotes.stream().mapToInt(Integer::intValue).toArray()));
        tag.put("TakenBells", new LongArrayTag(takenBells.stream().mapToLong(Long::longValue).toArray()));
        tag.put("Searched", new LongArrayTag(searched.stream().mapToLong(Long::longValue).toArray()));
        tag.put("Letters", new LongArrayTag(lettersTaken.stream().mapToLong(Long::longValue).toArray()));
        return tag;
    }

    public static GameData load(CompoundTag tag) {
        GameData d = new GameData();
        d.active = tag.getBoolean("Active");
        d.built = tag.getBoolean("Built");
        d.seed = tag.getLong("Seed");
        d.bells = tag.getInt("Bells");
        d.deaths = tag.getInt("Deaths");
        d.candleStep = tag.getInt("CandleStep");
        d.kuraUnlocked = tag.getBoolean("KuraUnlocked");
        d.dollSolved = tag.getBoolean("DollSolved");
        d.wellSolved = tag.getBoolean("WellSolved");
        d.butsumaSolved = tag.getBoolean("ButsumaSolved");
        d.wellPulls = tag.getInt("WellPulls");
        d.endings = tag.getInt("Endings");
        for (int i : tag.getIntArray("Notes")) d.foundNotes.add(i);
        for (long l : tag.getLongArray("TakenBells")) d.takenBells.add(l);
        for (long l : tag.getLongArray("Searched")) d.searched.add(l);
        for (long l : tag.getLongArray("Letters")) d.lettersTaken.add(l);
        return d;
    }
}
