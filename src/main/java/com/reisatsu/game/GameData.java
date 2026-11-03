package com.reisatsu.game;

import com.reisatsu.entity.SpiritKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The night's progress (stored with the overworld): save and quit resumes where you were. */
public final class GameData extends SavedData {
    private static final String NAME = "reisatsu_game";

    public static final int PHASE_IDLE = 0, PHASE_INTRO = 1, PHASE_INVESTIGATE = 2, PHASE_CLOSING = 3, PHASE_RETURN = 4,
            PHASE_EXORCISM = 5, PHASE_ENDING = 6;
    public static final int ENDING_NONE = 0, ENDING_GOOD = 1, ENDING_BAD = 2;
    public static final int RESULT_EMPTY = 0, RESULT_CAPTURED = 1, RESULT_FAILED = 2;

    /** One shot of the camera (kept in the album). spirit = ordinal or -1. */
    public record Photo(String id, int spirit, int stars, long time, int result, int ghost) {
        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Id", id);
            t.putInt("Spirit", spirit);
            t.putInt("Stars", stars);
            t.putLong("Time", time);
            t.putInt("Result", result);
            t.putInt("Ghost", ghost);
            return t;
        }

        public static Photo load(CompoundTag t) {
            return new Photo(t.getString("Id"), t.getInt("Spirit"), t.getInt("Stars"), t.getLong("Time"), t.getInt("Result"), t.getInt("Ghost"));
        }
    }

    public boolean started;
    public int phase = PHASE_IDLE;
    /** Spirits bound into photos (mask of SpiritKind bits). */
    public int captured;
    /** Photos handed to the medium. */
    public int submitted;
    public final List<Photo> photos = new ArrayList<>();
    public int ofudaUsed;
    /** 霊障 level 0..3: +1 each time you are caught. */
    public int reishou;
    public final Set<String> notes = new LinkedHashSet<>();
    /** Phenomena the player has witnessed (the report note's list). */
    public final Set<String> witnessed = new LinkedHashSet<>();
    public final Set<String> done = new HashSet<>();
    /** Film packs taken (bits 1..3), the office key taken. */
    public int filmTaken;
    public boolean officeKeyTaken;
    public long playTicks;
    public int ending = ENDING_NONE;
    public boolean introSeen;
    /** Play tick of the visitor's next arrival (-1: not scheduled yet). */
    public long visitorNext = -1;
    public int visitorArrivals;
    public long closingAt = -1;

    public static GameData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(GameData::load, GameData::new, NAME);
    }

    public boolean once(String key) {
        if (done.contains(key)) return false;
        done.add(key);
        setDirty();
        return true;
    }

    public boolean happened(String key) {
        return done.contains(key);
    }

    public boolean isCaptured(SpiritKind k) {
        return (captured & k.bit()) != 0;
    }

    public boolean isSubmitted(SpiritKind k) {
        return (submitted & k.bit()) != 0;
    }

    public int capturedCount(SpiritKind... kinds) {
        int n = 0;
        for (SpiritKind k : kinds) if (isCaptured(k)) n++;
        return n;
    }

    public boolean allCaptured() {
        return capturedCount(SpiritKind.ALL) == SpiritKind.ALL.length;
    }

    public boolean allSubmitted() {
        for (SpiritKind k : SpiritKind.ALL) if (!isSubmitted(k)) return false;
        return true;
    }

    /** A new night: everything but the "seen the intro once" flag goes back. */
    public void resetProgress() {
        started = false;
        phase = PHASE_IDLE;
        captured = 0;
        submitted = 0;
        photos.clear();
        ofudaUsed = 0;
        reishou = 0;
        notes.clear();
        witnessed.clear();
        done.clear();
        filmTaken = 0;
        officeKeyTaken = false;
        playTicks = 0;
        ending = ENDING_NONE;
        visitorNext = -1;
        visitorArrivals = 0;
        closingAt = -1;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Started", started);
        tag.putInt("Phase", phase);
        tag.putInt("Captured", captured);
        tag.putInt("Submitted", submitted);
        ListTag list = new ListTag();
        for (Photo p : photos) list.add(p.save());
        tag.put("Photos", list);
        tag.putInt("OfudaUsed", ofudaUsed);
        tag.putInt("Reishou", reishou);
        tag.put("Notes", strings(notes));
        tag.put("Witnessed", strings(witnessed));
        tag.put("Done", strings(done));
        tag.putInt("FilmTaken", filmTaken);
        tag.putBoolean("OfficeKey", officeKeyTaken);
        tag.putLong("PlayTicks", playTicks);
        tag.putInt("Ending", ending);
        tag.putBoolean("IntroSeen", introSeen);
        tag.putLong("VisitorNext", visitorNext);
        tag.putInt("VisitorArrivals", visitorArrivals);
        tag.putLong("ClosingAt", closingAt);
        return tag;
    }

    private static ListTag strings(Set<String> set) {
        ListTag l = new ListTag();
        for (String s : set) l.add(StringTag.valueOf(s));
        return l;
    }

    public static GameData load(CompoundTag tag) {
        GameData d = new GameData();
        d.started = tag.getBoolean("Started");
        d.phase = tag.getInt("Phase");
        d.captured = tag.getInt("Captured");
        d.submitted = tag.getInt("Submitted");
        for (Tag t : tag.getList("Photos", Tag.TAG_COMPOUND)) d.photos.add(Photo.load((CompoundTag) t));
        d.ofudaUsed = tag.getInt("OfudaUsed");
        d.reishou = tag.getInt("Reishou");
        for (Tag t : tag.getList("Notes", Tag.TAG_STRING)) d.notes.add(t.getAsString());
        for (Tag t : tag.getList("Witnessed", Tag.TAG_STRING)) d.witnessed.add(t.getAsString());
        for (Tag t : tag.getList("Done", Tag.TAG_STRING)) d.done.add(t.getAsString());
        d.filmTaken = tag.getInt("FilmTaken");
        d.officeKeyTaken = tag.getBoolean("OfficeKey");
        d.playTicks = tag.getLong("PlayTicks");
        d.ending = tag.getInt("Ending");
        d.introSeen = tag.getBoolean("IntroSeen");
        d.visitorNext = tag.contains("VisitorNext") ? tag.getLong("VisitorNext") : -1;
        d.visitorArrivals = tag.getInt("VisitorArrivals");
        d.closingAt = tag.contains("ClosingAt") ? tag.getLong("ClosingAt") : -1;
        return d;
    }
}
