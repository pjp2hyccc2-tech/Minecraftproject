package com.clockworkdiver.story;

import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.clock.ClockType;
import com.clockworkdiver.clock.Defect;
import com.clockworkdiver.clock.DefectType;
import com.clockworkdiver.customer.CustomerProfile;

import java.util.List;

/**
 * One chapter of the story: who brings which clock, what is wrong inside it (with the twists of each dive),
 * what the player gets for it and where it happens. All text lives in the language files under
 * {@code story.clockworkdiver.<id>.*}.
 */
public final class StoryQuest {
    public static final int BONUS_NONE = 0;
    public static final int BONUS_STEAM = 1;
    public static final int BONUS_LENS = 2;
    public static final int BONUS_WRENCH = 3;

    /** One faulty part (or clue) of the quest's clock. */
    public record Part(DefectType type, String part, int mods, int timeLimit, int fragments, int boss) {}

    public final int number;
    public final int chapter;
    public final String id;
    public final CustomerProfile client;
    public final ClockType type;
    public final int level;
    public final int reward;
    public final int keepsake;
    public final int bonus;
    /** Takes place at the Great Clock tower instead of the shop. */
    public final boolean tower;
    public final List<Part> parts;
    /** Friends gathered around for the scene (tower plaza or the shop finale). */
    public final List<CustomerProfile> extras;

    StoryQuest(int number, int chapter, CustomerProfile client, ClockType type, int level, int reward, int keepsake, int bonus,
               boolean tower, List<CustomerProfile> extras, List<Part> parts) {
        this.number = number;
        this.chapter = chapter;
        this.id = String.format("q%02d", number);
        this.client = client;
        this.type = type;
        this.level = level;
        this.reward = reward;
        this.keepsake = keepsake;
        this.bonus = bonus;
        this.tower = tower;
        this.extras = extras;
        this.parts = parts;
    }

    public String key(String suffix) {
        return "story.clockworkdiver." + id + "." + suffix;
    }

    public String titleKey() {
        return key("title");
    }

    public int totalFragments() {
        int n = 0;
        for (Part p : parts) n += p.fragments();
        return n;
    }

    public boolean hasClue() {
        for (Part p : parts) if (p.type() == DefectType.CLUE) return true;
        return false;
    }

    /** First quest of its chapter? */
    public boolean opensChapter() {
        return number == 1 || StoryQuests.get(number - 2).chapter != chapter;
    }

    /** Last quest of its chapter? */
    public boolean closesChapter() {
        return number == StoryQuests.count() || StoryQuests.get(number).chapter != chapter;
    }

    public ClockData createClock(long commissionId) {
        ClockData c = new ClockData();
        c.type = type;
        c.profile = client;
        c.commissionId = commissionId;
        c.reward = reward;
        c.story = id;
        c.level = level;
        int frag = 0;
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            float[] a = type.anchors[i % type.anchors.length];
            Defect d = new Defect(i, p.type(), p.part(), a[0], a[1]);
            d.mods = p.mods();
            d.timeLimit = p.timeLimit();
            d.boss = p.boss();
            if (p.fragments() > 0) {
                d.fragStart = frag;
                d.fragCount = p.fragments();
                frag += p.fragments();
            }
            c.defects.add(d);
        }
        return c;
    }
}
