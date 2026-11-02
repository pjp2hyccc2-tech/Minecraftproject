package com.birdiecraft.course;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** World-wide course registry, stored with the overworld data. */
public final class GolfCourseData extends SavedData {
    private static final String NAME = "birdiecraft_courses";
    private final Map<String, Course> courses = new LinkedHashMap<>();

    public static GolfCourseData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(GolfCourseData::load, GolfCourseData::new, NAME);
    }

    public Course get(String id) {
        return courses.get(id);
    }

    public Collection<Course> all() {
        return courses.values();
    }

    public List<String> ids() {
        return new ArrayList<>(courses.keySet());
    }

    public void put(Course course) {
        courses.put(course.id, course);
        setDirty();
    }

    public boolean remove(String id) {
        boolean r = courses.remove(id) != null;
        if (r) setDirty();
        return r;
    }

    /** Finds the course built from a preset, if any. */
    public Course byPreset(String presetId) {
        for (Course c : courses.values()) {
            if (presetId.equals(c.presetId)) return c;
        }
        return null;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Course c : courses.values()) list.add(c.save());
        tag.put("Courses", list);
        return tag;
    }

    public static GolfCourseData load(CompoundTag tag) {
        GolfCourseData d = new GolfCourseData();
        ListTag list = tag.getList("Courses", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Course c = Course.load(list.getCompound(i));
            d.courses.put(c.id, c);
        }
        return d;
    }
}
