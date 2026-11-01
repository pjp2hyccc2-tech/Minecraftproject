package com.frostbound.core.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Neutral save tree: maps of String to Integer, Long, Double, String, Boolean, List or Map.
 *
 * <p>The Forge layer converts this tree to NBT one to one. Readers never throw: missing or malformed
 * values fall back to defaults and out-of-range numbers are clamped, and every repair is recorded in
 * {@link #repairs()} so loading can report what it fixed.
 */
public final class Tree {
    private static final ThreadLocal<List<String>> REPAIRS = ThreadLocal.withInitial(ArrayList::new);

    private Tree() {
    }

    public static Map<String, Object> map() {
        return new LinkedHashMap<>();
    }

    /** Repairs recorded on this thread since the last {@link #clearRepairs()}. */
    public static List<String> repairs() {
        return REPAIRS.get();
    }

    public static void clearRepairs() {
        REPAIRS.get().clear();
    }

    public static void repaired(String what) {
        List<String> list = REPAIRS.get();
        if (list.size() < 200) {
            list.add(what);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> m, String k) {
        Object o = m == null ? null : m.get(k);
        return o instanceof Map ? (Map<String, Object>) o : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> m, String k) {
        Object o = m == null ? null : m.get(k);
        return o instanceof List ? (List<Object>) o : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> maps(Map<String, Object> m, String k) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list(m, k)) {
            if (o instanceof Map) {
                out.add((Map<String, Object>) o);
            }
        }
        return out;
    }

    public static List<String> strings(Map<String, Object> m, String k) {
        List<String> out = new ArrayList<>();
        for (Object o : list(m, k)) {
            if (o != null) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    public static List<Integer> ints(Map<String, Object> m, String k) {
        List<Integer> out = new ArrayList<>();
        for (Object o : list(m, k)) {
            if (o instanceof Number n) {
                out.add(n.intValue());
            }
        }
        return out;
    }

    public static int i(Map<String, Object> m, String k, int def) {
        Object o = m == null ? null : m.get(k);
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof Boolean b) {
            return b ? 1 : 0;
        }
        return def;
    }

    public static int i(Map<String, Object> m, String k, int def, int min, int max) {
        int v = i(m, k, def);
        if (v < min || v > max) {
            repaired(k + "=" + v);
            return Math.max(min, Math.min(max, v));
        }
        return v;
    }

    public static long l(Map<String, Object> m, String k, long def) {
        Object o = m == null ? null : m.get(k);
        return o instanceof Number n ? n.longValue() : def;
    }

    public static long l(Map<String, Object> m, String k, long def, long min, long max) {
        long v = l(m, k, def);
        if (v < min || v > max) {
            repaired(k + "=" + v);
            return Math.max(min, Math.min(max, v));
        }
        return v;
    }

    public static double d(Map<String, Object> m, String k, double def) {
        Object o = m == null ? null : m.get(k);
        if (o instanceof Number n) {
            double v = n.doubleValue();
            return Double.isFinite(v) ? v : def;
        }
        return def;
    }

    public static double d(Map<String, Object> m, String k, double def, double min, double max) {
        double v = d(m, k, def);
        if (v < min || v > max) {
            repaired(k + "=" + v);
            return Math.max(min, Math.min(max, v));
        }
        return v;
    }

    public static boolean b(Map<String, Object> m, String k, boolean def) {
        Object o = m == null ? null : m.get(k);
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.intValue() != 0;
        }
        return def;
    }

    public static String s(Map<String, Object> m, String k, String def) {
        Object o = m == null ? null : m.get(k);
        return o instanceof String s ? s : def;
    }

    public static <E extends Enum<E>> E e(Map<String, Object> m, String k, Class<E> type, E def) {
        String name = s(m, k, null);
        if (name == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ex) {
            repaired(k + "=" + name);
            return def;
        }
    }
}
