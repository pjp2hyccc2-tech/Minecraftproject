package com.frostbound.core.city;

import com.frostbound.core.util.Tree;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Step-by-step hints for a new city. Steps advance when the player does the thing they describe. */
public final class Tutorial {
    public enum Step {
        WELCOME("welcome"),
        OPEN_MANAGEMENT("management"),
        ASSIGN_WORKERS("workers"),
        GENERATOR("generator"),
        BUILD_HOUSING("house"),
        ENACT_LAW("law"),
        START_RESEARCH("research"),
        DONE(null);

        final String flag;

        Step(String flag) {
            this.flag = flag;
        }

        public String key() {
            return "frostbound.tutorial." + name().toLowerCase(Locale.ROOT);
        }
    }

    private boolean enabled = true;
    private Step step = Step.WELCOME;
    private final Set<String> flags = new HashSet<>();

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            step = Step.DONE;
        }
    }

    public Step step() {
        return step;
    }

    public void flag(String flag) {
        flags.add(flag);
    }

    public void check() {
        while (enabled && step != Step.DONE && step.flag != null && flags.contains(step.flag)) {
            step = Step.values()[step.ordinal() + 1];
        }
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        m.put("enabled", enabled);
        m.put("step", step.name());
        m.put("flags", new java.util.ArrayList<Object>(flags));
        return m;
    }

    public void load(Map<String, Object> m) {
        enabled = Tree.b(m, "enabled", true);
        step = Tree.e(m, "step", Step.class, Step.DONE);
        flags.clear();
        flags.addAll(Tree.strings(m, "flags"));
    }
}
