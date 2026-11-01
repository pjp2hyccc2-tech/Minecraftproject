package com.frostbound.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A translatable message: a language key plus string arguments.
 *
 * <p>An argument that starts with {@code #} is itself a language key (for example a building name) and is
 * translated by the client before it is inserted.
 */
public record Msg(String key, List<String> args) {
    public Msg {
        args = List.copyOf(args);
    }

    public static Msg of(String key, Object... args) {
        List<String> list = new ArrayList<>(args.length);
        for (Object a : args) {
            list.add(String.valueOf(a));
        }
        return new Msg(key, list);
    }

    /** Argument that the client translates (a language key). */
    public static String tr(String key) {
        return "#" + key;
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        m.put("key", key);
        m.put("args", new ArrayList<Object>(args));
        return m;
    }

    public static Msg load(Map<String, Object> m) {
        return new Msg(Tree.s(m, "key", "frostbound.msg.unknown"), Tree.strings(m, "args"));
    }
}
