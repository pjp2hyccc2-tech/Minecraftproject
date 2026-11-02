package com.sounan.game;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/** World-wide progress of the night on the mountain (stored with the overworld). */
public final class GameData extends SavedData {
    private static final String NAME = "sounan_game";

    public boolean started;
    public boolean bagOpened;
    public boolean roped;
    public boolean omikiTaken;
    public boolean offered;
    public boolean keyTaken;
    public boolean gateUnlocked;
    public boolean signal;
    public boolean called;
    public boolean climax;
    public int captures;
    /** 0 = still on the mountain, 1 = escaped, 2 = spirited away. */
    public int ending;
    public String checkpoint = "start";
    public long playTicks;
    public final Set<Integer> notes = new HashSet<>();
    public final Set<String> done = new HashSet<>();

    public static GameData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(GameData::load, GameData::new, NAME);
    }

    /** Has this one-off thing already happened? Marks it as happened. */
    public boolean once(String key) {
        if (done.contains(key)) return false;
        done.add(key);
        setDirty();
        return true;
    }

    public boolean happened(String key) {
        return done.contains(key);
    }

    public void reset() {
        started = false;
        bagOpened = roped = omikiTaken = offered = keyTaken = gateUnlocked = signal = called = climax = false;
        captures = 0;
        ending = 0;
        checkpoint = "start";
        playTicks = 0;
        notes.clear();
        done.clear();
        setDirty();
    }

    /** Days the phone's calendar has jumped because of the captures (神隠し time). */
    public int dayOffset() {
        return dayOffset(captures);
    }

    public static int dayOffset(int captures) {
        return switch (Math.min(captures, 5)) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 9;
            case 3 -> 70;
            case 4 -> 1165;
            default -> 2920;
        };
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Started", started);
        tag.putBoolean("Bag", bagOpened);
        tag.putBoolean("Roped", roped);
        tag.putBoolean("Omiki", omikiTaken);
        tag.putBoolean("Offered", offered);
        tag.putBoolean("Key", keyTaken);
        tag.putBoolean("Gate", gateUnlocked);
        tag.putBoolean("Signal", signal);
        tag.putBoolean("Called", called);
        tag.putBoolean("Climax", climax);
        tag.putInt("Captures", captures);
        tag.putInt("Ending", ending);
        tag.putString("Checkpoint", checkpoint);
        tag.putLong("PlayTicks", playTicks);
        tag.put("Notes", new IntArrayTag(notes.stream().mapToInt(Integer::intValue).toArray()));
        ListTag list = new ListTag();
        for (String s : done) list.add(StringTag.valueOf(s));
        tag.put("Done", list);
        return tag;
    }

    public static GameData load(CompoundTag tag) {
        GameData d = new GameData();
        d.started = tag.getBoolean("Started");
        d.bagOpened = tag.getBoolean("Bag");
        d.roped = tag.getBoolean("Roped");
        d.omikiTaken = tag.getBoolean("Omiki");
        d.offered = tag.getBoolean("Offered");
        d.keyTaken = tag.getBoolean("Key");
        d.gateUnlocked = tag.getBoolean("Gate");
        d.signal = tag.getBoolean("Signal");
        d.called = tag.getBoolean("Called");
        d.climax = tag.getBoolean("Climax");
        d.captures = tag.getInt("Captures");
        d.ending = tag.getInt("Ending");
        d.checkpoint = tag.contains("Checkpoint") ? tag.getString("Checkpoint") : "start";
        d.playTicks = tag.getLong("PlayTicks");
        for (int i : tag.getIntArray("Notes")) d.notes.add(i);
        for (Tag t : tag.getList("Done", Tag.TAG_STRING)) d.done.add(t.getAsString());
        return d;
    }
}
