package com.frostbound.save;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** One-to-one conversion between the core's neutral save tree and NBT. */
public final class NbtTree {
    private NbtTree() {
    }

    public static CompoundTag toNbt(Map<String, Object> map) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Tag t = toTag(e.getValue());
            if (t != null) {
                tag.put(e.getKey(), t);
            }
        }
        return tag;
    }

    @SuppressWarnings("unchecked")
    private static Tag toTag(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Integer i) {
            return IntTag.valueOf(i);
        }
        if (o instanceof Long l) {
            return LongTag.valueOf(l);
        }
        if (o instanceof Double d) {
            return DoubleTag.valueOf(d);
        }
        if (o instanceof Float f) {
            return FloatTag.valueOf(f);
        }
        if (o instanceof Boolean b) {
            return ByteTag.valueOf(b);
        }
        if (o instanceof String s) {
            return StringTag.valueOf(s);
        }
        if (o instanceof Map<?, ?> m) {
            return toNbt((Map<String, Object>) m);
        }
        if (o instanceof List<?> list) {
            ListTag lt = new ListTag();
            byte type = -1;
            for (Object e : list) {
                Tag t = toTag(e);
                if (t == null) {
                    continue;
                }
                if (type == -1) {
                    type = t.getId();
                }
                if (t.getId() != type) {
                    t = StringTag.valueOf(String.valueOf(e));
                    if (type != Tag.TAG_STRING) {
                        continue;
                    }
                }
                lt.add(t);
            }
            return lt;
        }
        return StringTag.valueOf(String.valueOf(o));
    }

    public static Map<String, Object> fromNbt(CompoundTag tag) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : tag.getAllKeys()) {
            Object v = fromTag(tag.get(k));
            if (v != null) {
                m.put(k, v);
            }
        }
        return m;
    }

    private static Object fromTag(Tag t) {
        if (t == null) {
            return null;
        }
        if (t instanceof CompoundTag c) {
            return fromNbt(c);
        }
        if (t instanceof ListTag l) {
            List<Object> out = new ArrayList<>(l.size());
            for (Tag e : l) {
                Object v = fromTag(e);
                if (v != null) {
                    out.add(v);
                }
            }
            return out;
        }
        if (t instanceof IntArrayTag a) {
            List<Object> out = new ArrayList<>();
            for (int v : a.getAsIntArray()) {
                out.add(v);
            }
            return out;
        }
        if (t instanceof LongArrayTag a) {
            List<Object> out = new ArrayList<>();
            for (long v : a.getAsLongArray()) {
                out.add(v);
            }
            return out;
        }
        if (t instanceof StringTag s) {
            return s.getAsString();
        }
        if (t instanceof IntTag i) {
            return i.getAsInt();
        }
        if (t instanceof LongTag l) {
            return l.getAsLong();
        }
        if (t instanceof DoubleTag d) {
            return d.getAsDouble();
        }
        if (t instanceof FloatTag f) {
            return (double) f.getAsFloat();
        }
        if (t instanceof ByteTag b) {
            return (int) b.getAsByte();
        }
        if (t instanceof ShortTag s) {
            return (int) s.getAsShort();
        }
        if (t instanceof NumericTag n) {
            return n.getAsDouble();
        }
        return t.getAsString();
    }
}
