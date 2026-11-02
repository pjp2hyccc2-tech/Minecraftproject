package com.birdiecraft.course;

import com.birdiecraft.block.FlagColor;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** A playable course (preset, generated or hand built with the course wand). */
public final class Course {
    public final String id;
    public String displayName;
    /** Preset id when this course was built from one of the built-in designs, otherwise empty. */
    public String presetId = "";
    public ResourceKey<Level> dimension;
    public FlagColor flagColor = FlagColor.RED;
    public double windFactor = 1.0;
    public final List<Hole> holes = new ArrayList<>();

    public Course(String id, String displayName, ResourceKey<Level> dimension) {
        this.id = id;
        this.displayName = displayName;
        this.dimension = dimension;
    }

    public int totalPar() {
        int p = 0;
        for (Hole h : holes) p += h.par;
        return p;
    }

    public boolean isPreset() {
        return !presetId.isEmpty();
    }

    /** Name shown to players: translated for presets, literal otherwise. */
    public Component title() {
        if (isPreset()) return Component.translatable("preset.birdiecraft." + presetId);
        return Component.literal(displayName);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Id", id);
        t.putString("Name", displayName);
        t.putString("Preset", presetId);
        t.putString("Dim", dimension.location().toString());
        t.putInt("Flag", flagColor.ordinal());
        t.putDouble("Wind", windFactor);
        ListTag list = new ListTag();
        for (Hole h : holes) list.add(h.save());
        t.put("Holes", list);
        return t;
    }

    public static Course load(CompoundTag t) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(t.getString("Dim")));
        Course c = new Course(t.getString("Id"), t.getString("Name"), dim);
        c.presetId = t.getString("Preset");
        c.flagColor = FlagColor.byId(t.getInt("Flag"));
        c.windFactor = t.contains("Wind") ? t.getDouble("Wind") : 1.0;
        ListTag list = t.getList("Holes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) c.holes.add(Hole.load(list.getCompound(i)));
        return c;
    }

    /** Lower-case id safe for commands. */
    public static String sanitizeId(String name) {
        String s = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        if (s.isEmpty()) s = "course";
        if (s.length() > 32) s = s.substring(0, 32);
        return s;
    }
}
