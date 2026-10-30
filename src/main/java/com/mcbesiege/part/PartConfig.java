package com.mcbesiege.part;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-block settings (key bindings, sliders, toggles) — the Besiege "key mapper" data. */
public final class PartConfig {
    public final PartType type;
    private final Map<String, int[]> keys = new LinkedHashMap<>();
    private final Map<String, Float> sliders = new LinkedHashMap<>();
    private final Map<String, Boolean> toggles = new LinkedHashMap<>();

    public PartConfig(PartType type) {
        this.type = type;
        for (PartType.KeySpec k : type.keys) keys.put(k.id(), k.defaults().clone());
        for (PartType.SliderSpec s : type.sliders) sliders.put(s.id(), s.def());
        for (PartType.ToggleSpec t : type.toggles) toggles.put(t.id(), t.def());
    }

    public static PartConfig of(PartType type, CompoundTag tag) {
        PartConfig c = new PartConfig(type);
        if (tag != null) c.load(tag);
        return c;
    }

    public void load(CompoundTag tag) {
        CompoundTag k = tag.getCompound("k");
        for (PartType.KeySpec spec : type.keys) {
            if (k.contains(spec.id(), Tag.TAG_INT_ARRAY)) setKeys(spec.id(), k.getIntArray(spec.id()));
        }
        CompoundTag s = tag.getCompound("s");
        for (PartType.SliderSpec spec : type.sliders) {
            if (s.contains(spec.id())) setSlider(spec.id(), s.getFloat(spec.id()));
        }
        CompoundTag t = tag.getCompound("t");
        for (PartType.ToggleSpec spec : type.toggles) {
            if (t.contains(spec.id())) setToggle(spec.id(), t.getBoolean(spec.id()));
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag k = new CompoundTag();
        keys.forEach((id, v) -> k.putIntArray(id, v));
        CompoundTag s = new CompoundTag();
        sliders.forEach(s::putFloat);
        CompoundTag t = new CompoundTag();
        toggles.forEach(t::putBoolean);
        tag.put("k", k);
        tag.put("s", s);
        tag.put("t", t);
        return tag;
    }

    public int[] keys(String id) {
        int[] v = keys.get(id);
        return v == null ? new int[0] : v;
    }

    public float slider(String id) {
        Float v = sliders.get(id);
        if (v != null) return v;
        PartType.SliderSpec s = type.slider(id);
        return s == null ? 0f : s.def();
    }

    public boolean toggle(String id) {
        Boolean v = toggles.get(id);
        if (v != null) return v;
        PartType.ToggleSpec s = type.toggle(id);
        return s != null && s.def();
    }

    public void setKeys(String id, int[] v) {
        if (type.key(id) == null) return;
        int n = Math.min(4, v.length);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = Keys.isReserved(v[i]) ? Keys.NONE : v[i];
        keys.put(id, out);
    }

    public void setSlider(String id, float v) {
        PartType.SliderSpec s = type.slider(id);
        if (s == null || Float.isNaN(v)) return;
        sliders.put(id, Math.max(s.min(), Math.min(s.max(), v)));
    }

    public void setToggle(String id, boolean v) {
        if (type.toggle(id) == null) return;
        toggles.put(id, v);
    }
}
