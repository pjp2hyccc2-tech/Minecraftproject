package com.frostbound.core.population;

import com.frostbound.core.util.Tree;
import java.util.Locale;
import java.util.Map;

/** One citizen's data. The NPC entity, when one exists, only mirrors this record. */
public final class Citizen {
    public enum Gender {
        MALE,
        FEMALE,
        OTHER
    }

    public enum Condition {
        HEALTHY,
        LIGHTLY_ILL,
        SEVERELY_ILL,
        FROSTBITE,
        INFECTED,
        RECOVERING;

        public boolean sick() {
            return this == LIGHTLY_ILL || this == SEVERELY_ILL || this == FROSTBITE || this == INFECTED;
        }

        public String key() {
            return "frostbound.condition." + name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Status {
        ACTIVE,
        EXPEDITION,
        DEAD
    }

    public static final int ADULT_AGE = 14;

    public final int id;
    public String name;
    public int age;
    public Gender gender;
    public Profession profession;
    public int homeId = -1;
    public int workId = -1;
    public int familyId = -1;
    public double health = 100;
    public double satiety = 80;
    public double bodyTemp = 36.8;
    public double fatigue = 10;
    public double hope = 55;
    public double discontent = 15;
    public Condition condition = Condition.HEALTHY;
    public Status status = Status.ACTIVE;
    public int sickHours;
    public int recoverHours;
    /** Skin variant for the NPC (0-3). */
    public int look;

    // Derived each hour
    public transient boolean atWork;
    public transient boolean treated;
    public transient int location = -1;

    public Citizen(int id) {
        this.id = id;
    }

    public boolean child() {
        return age < ADULT_AGE;
    }

    public boolean alive() {
        return status != Status.DEAD;
    }

    public boolean present() {
        return status == Status.ACTIVE;
    }

    /** 0 full, 1 normal, 2 hungry, 3 starving. */
    public int hungerBand() {
        if (satiety >= 75) {
            return 0;
        }
        if (satiety >= 40) {
            return 1;
        }
        return satiety >= 15 ? 2 : 3;
    }

    /** 0 comfortable, 1 cold, 2 very cold, 3 dangerous, 4 near freezing to death. */
    public int tempBand() {
        if (bodyTemp >= 36.0) {
            return 0;
        }
        if (bodyTemp >= 35.0) {
            return 1;
        }
        if (bodyTemp >= 33.5) {
            return 2;
        }
        return bodyTemp >= 32.0 ? 3 : 4;
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        m.put("id", id);
        m.put("name", name);
        m.put("age", age);
        m.put("gender", gender.name());
        m.put("prof", profession.name());
        m.put("home", homeId);
        m.put("work", workId);
        m.put("family", familyId);
        m.put("health", health);
        m.put("satiety", satiety);
        m.put("body", bodyTemp);
        m.put("fatigue", fatigue);
        m.put("hope", hope);
        m.put("disc", discontent);
        m.put("cond", condition.name());
        m.put("status", status.name());
        m.put("sickH", sickHours);
        m.put("recH", recoverHours);
        m.put("look", look);
        return m;
    }

    public static Citizen load(Map<String, Object> m) {
        int id = Tree.i(m, "id", -1);
        if (id < 0) {
            Tree.repaired("citizen without id");
            return null;
        }
        Citizen c = new Citizen(id);
        c.name = Tree.s(m, "name", "?");
        c.age = Tree.i(m, "age", 30, 1, 100);
        c.gender = Tree.e(m, "gender", Gender.class, Gender.OTHER);
        c.profession = Tree.e(m, "prof", Profession.class, Profession.WORKER);
        c.homeId = Tree.i(m, "home", -1);
        c.workId = Tree.i(m, "work", -1);
        c.familyId = Tree.i(m, "family", -1);
        c.health = Tree.d(m, "health", 100, 0, 100);
        c.satiety = Tree.d(m, "satiety", 80, 0, 100);
        c.bodyTemp = Tree.d(m, "body", 36.8, 20, 38);
        c.fatigue = Tree.d(m, "fatigue", 10, 0, 100);
        c.hope = Tree.d(m, "hope", 55, 0, 100);
        c.discontent = Tree.d(m, "disc", 15, 0, 100);
        c.condition = Tree.e(m, "cond", Condition.class, Condition.HEALTHY);
        c.status = Tree.e(m, "status", Status.class, Status.ACTIVE);
        c.sickHours = Tree.i(m, "sickH", 0, 0, 100_000);
        c.recoverHours = Tree.i(m, "recH", 0, 0, 1000);
        c.look = Tree.i(m, "look", 0, 0, 3);
        return c;
    }
}
