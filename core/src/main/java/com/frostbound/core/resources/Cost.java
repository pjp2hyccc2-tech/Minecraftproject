package com.frostbound.core.resources;

import com.frostbound.core.util.Tree;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** An immutable bundle of whole resource units (a building cost, a reward, a supply list). */
public final class Cost {
    public static final Cost NONE = new Cost(new EnumMap<>(ResourceType.class));

    private final EnumMap<ResourceType, Integer> amounts;

    private Cost(EnumMap<ResourceType, Integer> amounts) {
        this.amounts = amounts;
    }

    public static Cost of(Object... pairs) {
        EnumMap<ResourceType, Integer> map = new EnumMap<>(ResourceType.class);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            ResourceType t = (ResourceType) pairs[i];
            int v = ((Number) pairs[i + 1]).intValue();
            if (v > 0) {
                map.merge(t, v, Integer::sum);
            }
        }
        return new Cost(map);
    }

    public int get(ResourceType t) {
        return amounts.getOrDefault(t, 0);
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    public Map<ResourceType, Integer> asMap() {
        return Collections.unmodifiableMap(amounts);
    }

    public Cost scaled(double factor) {
        EnumMap<ResourceType, Integer> map = new EnumMap<>(ResourceType.class);
        amounts.forEach((t, v) -> {
            int s = (int) Math.round(v * factor);
            if (s > 0) {
                map.put(t, s);
            }
        });
        return new Cost(map);
    }

    public Cost plus(Cost other) {
        EnumMap<ResourceType, Integer> map = new EnumMap<>(amounts);
        other.amounts.forEach((t, v) -> map.merge(t, v, Integer::sum));
        return new Cost(map);
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        amounts.forEach((t, v) -> m.put(t.id(), v));
        return m;
    }

    public static Cost load(Map<String, Object> m) {
        EnumMap<ResourceType, Integer> map = new EnumMap<>(ResourceType.class);
        for (ResourceType t : ResourceType.values()) {
            int v = Tree.i(m, t.id(), 0, 0, 1_000_000);
            if (v > 0) {
                map.put(t, v);
            }
        }
        return new Cost(map);
    }
}
