package com.clockworkdiver.story;

import com.clockworkdiver.clock.ClockType;
import com.clockworkdiver.clock.DefectType;
import com.clockworkdiver.customer.CustomerProfile;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.clockworkdiver.clock.Defect.BOSS_NORMAL;
import static com.clockworkdiver.clock.Defect.BOSS_QUEEN;
import static com.clockworkdiver.clock.Defect.BOSS_SHADE;
import static com.clockworkdiver.clock.Defect.BOSS_SHADOW;
import static com.clockworkdiver.clock.DefectType.CLUE;
import static com.clockworkdiver.clock.DefectType.DUST;
import static com.clockworkdiver.clock.DefectType.NEST;
import static com.clockworkdiver.clock.DefectType.RUST;
import static com.clockworkdiver.clock.DefectType.SCREW;
import static com.clockworkdiver.clock.DefectType.SPRING;
import static com.clockworkdiver.clock.DefectType.TEETH;
import static com.clockworkdiver.clock.DefectType.TUNING;
import static com.clockworkdiver.clock.DiveMods.DARK;
import static com.clockworkdiver.clock.DiveMods.ELITE;
import static com.clockworkdiver.clock.DiveMods.FAST;
import static com.clockworkdiver.clock.DiveMods.NO_VENTS;
import static com.clockworkdiver.clock.DiveMods.OFFBEAT;
import static com.clockworkdiver.clock.DiveMods.PRECISION;
import static com.clockworkdiver.clock.DiveMods.SWARM;
import static com.clockworkdiver.clock.DiveMods.TIMED;
import static com.clockworkdiver.customer.CustomerProfile.ADA;
import static com.clockworkdiver.customer.CustomerProfile.BOLT;
import static com.clockworkdiver.customer.CustomerProfile.CELES;
import static com.clockworkdiver.customer.CustomerProfile.ELIAS;
import static com.clockworkdiver.customer.CustomerProfile.GREGORY;
import static com.clockworkdiver.customer.CustomerProfile.LILY;
import static com.clockworkdiver.customer.CustomerProfile.PHANTOM;
import static com.clockworkdiver.customer.CustomerProfile.PIP;
import static com.clockworkdiver.customer.CustomerProfile.STRANGER;
import static com.clockworkdiver.customer.CustomerProfile.VIKTOR;

/**
 * 「時を継ぐ修理工」 — the twenty story commissions in five chapters.
 *
 * <ol>
 *   <li>歯車の街の新米時計師: odd black gears turn up inside the clocks the player repairs.</li>
 *   <li>灰色の影: the gears are Time Eater eggs, planted by a man in a grey coat.</li>
 *   <li>師の足跡: the vanished master Elias is alive, inside the Great Clock; the grey man is Viktor.</li>
 *   <li>狂い出す大時計: the Queen of Time Eaters wakes; Viktor learns what his daughter really wanted.</li>
 *   <li>時を継ぐ者: into the Great Clock, the Queen, the master's final exam and the ending.</li>
 * </ol>
 */
public final class StoryQuests {
    public static final int CHAPTERS = 5;

    /** Keepsake icons (item model predicate values / 20). */
    public static final int K_WHISTLE = 0, K_FLOWER = 1, K_STAR = 2, K_KEY = 3, K_PAPER = 4, K_FEATHER = 5, K_BELL = 6, K_GEAR = 7,
            K_LENS = 8, K_MUSIC = 9, K_GLOVE = 10, K_PENDULUM = 11, K_GLASS_FLOWER = 12, K_TOWER_KEY = 13, K_CROWN = 14, K_MEDAL = 15;
    public static final int KEEPSAKE_ICONS = 16;

    private static final List<StoryQuest> ALL = new ArrayList<>();

    static {
        // ---- Chapter 1: 歯車の街の新米時計師
        add(1, BOLT, ClockType.STEAM_TOWER, 1, 30, K_WHISTLE, StoryQuest.BONUS_NONE, false, List.of(),
                p(RUST, "center_wheel"), p(SCREW, "regulator_screw", TIMED, 240), clue());
        add(1, LILY, ClockType.CUCKOO, 1, 28, K_FLOWER, StoryQuest.BONUS_NONE, false, List.of(),
                p(TEETH, "escape_wheel"), frag(SPRING, "mainspring", 0, 3));
        add(1, CELES, ClockType.ASTRONOMICAL, 2, 34, K_STAR, StoryQuest.BONUS_NONE, false, List.of(),
                p(TUNING, "chime_comb"), p(TEETH, "hour_wheel"), clue());
        add(1, GREGORY, ClockType.POCKET_WATCH, 2, 40, K_KEY, StoryQuest.BONUS_NONE, false, List.of(),
                p(RUST, "fourth_wheel"), boss(NEST, "balance_wheel", DARK, 0, 2, BOSS_NORMAL));
        // ---- Chapter 2: 灰色の影
        add(2, ADA, ClockType.ORRERY, 2, 40, K_PAPER, StoryQuest.BONUS_STEAM, false, List.of(),
                p(DUST, "third_wheel", SWARM, 0), p(SPRING, "mainspring"));
        add(2, PHANTOM, ClockType.BIRDCAGE, 3, 46, K_FEATHER, StoryQuest.BONUS_NONE, false, List.of(),
                p(TEETH, "minute_wheel", ELITE, 0), p(SCREW, "case_screw", TIMED, 210), clue());
        add(2, PIP, ClockType.GRANDFATHER, 3, 44, K_BELL, StoryQuest.BONUS_NONE, false, List.of(),
                frag(TUNING, "chime_comb", FAST, 1), p(SCREW, "regulator_screw", FAST, 0));
        add(2, BOLT, ClockType.STEAM_TOWER, 3, 55, K_GEAR, StoryQuest.BONUS_NONE, false, List.of(),
                p(RUST, "barrel_gear", SWARM, 0), p(SPRING, "mainspring"), boss(NEST, "pallet_fork", 0, 0, 0, BOSS_SHADOW), clue());
        // ---- Chapter 3: 師の足跡
        add(3, CELES, ClockType.ORRERY, 3, 52, K_LENS, StoryQuest.BONUS_LENS, false, List.of(),
                p(TEETH, "hour_wheel", DARK, 0), frag(TUNING, "chime_comb", DARK, 2), p(SCREW, "bridge_screw"));
        add(3, STRANGER, ClockType.MUSIC_BOX, 3, 60, K_MUSIC, StoryQuest.BONUS_NONE, false, List.of(),
                frag(SPRING, "mainspring", 0, 2), frag(DUST, "third_wheel", 0, 2), clue());
        add(3, PHANTOM, ClockType.POCKET_WATCH, 4, 58, K_GLOVE, StoryQuest.BONUS_WRENCH, false, List.of(),
                p(RUST, "center_wheel", ELITE, 0), boss(NEST, "balance_wheel", TIMED, 300, 0, BOSS_SHADOW));
        add(3, ADA, ClockType.ASTRONOMICAL, 4, 60, K_GEAR, StoryQuest.BONUS_NONE, false, List.of(),
                p(TUNING, "chime_comb", OFFBEAT, 0), p(TEETH, "escape_wheel", OFFBEAT, 0), frag(DUST, "fourth_wheel", SWARM, 1));
        // ---- Chapter 4: 狂い出す大時計
        add(4, PIP, ClockType.GRANDFATHER, 4, 62, K_PENDULUM, StoryQuest.BONUS_NONE, false, List.of(),
                p(SCREW, "regulator_screw", FAST | TIMED, 240), p(SPRING, "hairspring", FAST | NO_VENTS, 0));
        add(4, LILY, ClockType.BIRDCAGE, 4, 64, K_GLASS_FLOWER, StoryQuest.BONUS_NONE, false, List.of(),
                p(RUST, "barrel_gear", SWARM, 0), p(TEETH, "minute_wheel"), boss(NEST, "pallet_fork", SWARM, 0, 0, BOSS_NORMAL));
        add(4, PHANTOM, ClockType.CUCKOO, 4, 66, K_PAPER, StoryQuest.BONUS_NONE, false, List.of(),
                p(TUNING, "chime_comb", OFFBEAT, 0), p(SCREW, "case_screw", OFFBEAT, 0), frag(DUST, "third_wheel", 0, 3));
        add(4, VIKTOR, ClockType.MUSIC_BOX, 5, 70, K_TOWER_KEY, StoryQuest.BONUS_NONE, false, List.of(),
                p(SPRING, "mainspring", DARK, 0), boss(NEST, "balance_wheel", 0, 0, 2, BOSS_SHADE));
        // ---- Chapter 5: 時を継ぐ者
        add(5, PIP, ClockType.GREAT_CLOCK, 5, 75, K_GEAR, StoryQuest.BONUS_NONE, true,
                List.of(BOLT, ADA, PHANTOM, CELES, LILY, GREGORY),
                p(RUST, "center_wheel", SWARM | ELITE, 0), p(TEETH, "escape_wheel", ELITE, 0), p(SCREW, "bridge_screw", SWARM, 0));
        add(5, PIP, ClockType.GREAT_CLOCK, 5, 80, K_BELL, StoryQuest.BONUS_NONE, true,
                List.of(BOLT, ADA, PHANTOM, CELES, LILY, GREGORY, VIKTOR),
                p(TUNING, "chime_comb", FAST, 0), p(SPRING, "mainspring", DARK | SWARM, 0), frag(DUST, "fourth_wheel", FAST, 2));
        add(5, PIP, ClockType.GREAT_CLOCK, 5, 100, K_CROWN, StoryQuest.BONUS_NONE, true,
                List.of(BOLT, ADA, PHANTOM, CELES, LILY, GREGORY, VIKTOR),
                boss(NEST, "pallet_fork", 0, 0, 0, BOSS_QUEEN));
        add(5, ELIAS, ClockType.GRANDFATHER, 4, 120, K_MEDAL, StoryQuest.BONUS_NONE, false,
                List.of(GREGORY, PIP, BOLT, ADA, LILY, CELES, PHANTOM, VIKTOR),
                p(SCREW, "regulator_screw", PRECISION, 0), p(TUNING, "chime_comb", PRECISION, 0), frag(SPRING, "mainspring", PRECISION, 3));
    }

    private StoryQuests() {}

    private static void add(int chapter, CustomerProfile client, ClockType type, int level, int reward, int keepsake, int bonus,
                            boolean tower, List<CustomerProfile> extras, StoryQuest.Part... parts) {
        ALL.add(new StoryQuest(ALL.size() + 1, chapter, client, type, level, reward, keepsake, bonus, tower, extras, List.of(parts)));
    }

    private static StoryQuest.Part p(DefectType type, String part) {
        return new StoryQuest.Part(type, part, 0, 0, 0, BOSS_NORMAL);
    }

    private static StoryQuest.Part p(DefectType type, String part, int mods, int timeLimit) {
        return new StoryQuest.Part(type, part, mods, timeLimit, 0, BOSS_NORMAL);
    }

    private static StoryQuest.Part frag(DefectType type, String part, int mods, int fragments) {
        return new StoryQuest.Part(type, part, mods, 0, fragments, BOSS_NORMAL);
    }

    private static StoryQuest.Part boss(DefectType type, String part, int mods, int timeLimit, int fragments, int boss) {
        return new StoryQuest.Part(type, part, mods, timeLimit, fragments, boss);
    }

    private static StoryQuest.Part clue() {
        return new StoryQuest.Part(CLUE, "clue", 0, 0, 0, BOSS_NORMAL);
    }

    public static int count() {
        return ALL.size();
    }

    /** By index 0..count-1. */
    public static StoryQuest get(int index) {
        return ALL.get(Math.max(0, Math.min(ALL.size() - 1, index)));
    }

    public static List<StoryQuest> all() {
        return ALL;
    }

    @Nullable
    public static StoryQuest byId(String id) {
        for (StoryQuest q : ALL) {
            if (q.id.equals(id)) return q;
        }
        return null;
    }

    public static List<StoryQuest> chapter(int chapter) {
        List<StoryQuest> out = new ArrayList<>();
        for (StoryQuest q : ALL) if (q.chapter == chapter) out.add(q);
        return out;
    }

    public static String chapterKey(int chapter) {
        return "story.clockworkdiver.chapter." + chapter;
    }
}
